package com.falkordb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.util.JedisURIHelper;

/**
 * Resolves the connection settings for the no-arg {@link FalkorDB#driver()} from the environment.
 *
 * <p>Extracted from {@link FalkorDB} so the resolution logic is unit-testable without mutating the
 * real process environment: tests inject a fake {@code env} lookup instead of {@link System#getenv}.
 * See {@link FalkorDB#driver()} for the documented resolution order, precedence, and exceptions.
 */
final class DriverEnvironment {

    /** Full connection URI env var; see {@link FalkorDB#driver()}. */
    static final String URL_VAR = "FALKORDB_URL";

    /** Server host env var; must be set together with {@link #PORT_VAR}. */
    static final String HOST_VAR = "FALKORDB_HOST";

    /** Server port env var; must be set together with {@link #HOST_VAR}. */
    static final String PORT_VAR = "FALKORDB_PORT";

    /** Sentinel master-name env var; must be set together with {@link #SENTINELS_VAR}. */
    static final String SENTINEL_MASTER_VAR = "FALKORDB_SENTINEL_MASTER";

    /**
     * Comma-separated {@code host:port} Sentinel addresses; must be set together with {@link
     * #SENTINEL_MASTER_VAR}.
     */
    static final String SENTINELS_VAR = "FALKORDB_SENTINELS";

    /** Default host used when neither {@link #URL_VAR} nor {@link #HOST_VAR}/{@link #PORT_VAR} is set. */
    static final String DEFAULT_HOST = "localhost";

    /** Default port used when neither {@link #URL_VAR} nor {@link #HOST_VAR}/{@link #PORT_VAR} is set. */
    static final int DEFAULT_PORT = 6379;

    /**
     * Matches the credentials segment of a URI authority, so it can be redacted before logging.
     * Anchored at either a {@code ://} scheme separator or the very start of the value, so a
     * scheme-less mistake like {@code user:password@host:6379} — which {@link URI} happily parses as
     * an opaque URI rather than rejecting — is redacted just like a well-formed {@code redis://} URL.
     */
    private static final Pattern USERINFO_PREFIX = Pattern.compile("(^|://)[^/?#@]*@");

    private DriverEnvironment() {}

    /**
     * Resolves the no-arg {@code driver()} target from {@code env} and creates it.
     *
     * @param env looks up an environment variable by name (e.g. {@code System::getenv}), returning
     *     {@code null} for an unset variable
     * @return a new driver, per the resolution order documented on {@link FalkorDB#driver()}
     * @throws IllegalStateException if {@code FALKORDB_URL} is set but malformed, if {@code
     *     FALKORDB_PORT} is set but not a valid integer, or if exactly one of {@code
     *     FALKORDB_HOST}/{@code FALKORDB_PORT} is set
     */
    static Driver resolve(Function<String, @Nullable String> env) {
        String sentinelMaster = trimToNull(env.apply(SENTINEL_MASTER_VAR));
        String sentinels = trimToNull(env.apply(SENTINELS_VAR));
        if ((sentinelMaster != null) != (sentinels != null)) {
            throw new IllegalStateException("Set BOTH " + SENTINEL_MASTER_VAR + " and " + SENTINELS_VAR
                    + " to configure FalkorDB.driver() against a Sentinel deployment, or neither.");
        }
        if (sentinelMaster != null) {
            return FalkorDB.builder()
                    .sentinel(sentinelMaster, parseSentinels(sentinels))
                    .build();
        }
        String url = trimToNull(env.apply(URL_VAR));
        if (url != null) {
            URI uri = toConnectionUri(url);
            try {
                return FalkorDB.driver(uri);
            } catch (RuntimeException e) {
                // Same reasoning as in toConnectionUri: Jedis' InvalidURIException formats the whole
                // URI (credentials included) into its message, so chaining it would undo redact().
                // The exception's type is a safe breadcrumb; its message is not.
                throw new IllegalStateException(URL_VAR + " is not a valid connection URI: \"" + redact(url)
                        + "\" (rejected by " + e.getClass().getSimpleName() + ")");
            }
        }
        String host = trimToNull(env.apply(HOST_VAR));
        String port = trimToNull(env.apply(PORT_VAR));
        if ((host != null) != (port != null)) {
            throw new IllegalStateException("Set BOTH " + HOST_VAR + " and " + PORT_VAR
                    + " to configure FalkorDB.driver() from the environment, or neither.");
        }
        return host != null ? FalkorDB.driver(host, parsePort(port)) : FalkorDB.driver(DEFAULT_HOST, DEFAULT_PORT);
    }

    /**
     * Parses a {@code FALKORDB_URL} value into a connection {@link URI}: translates the
     * FalkorDB-branded {@code falkor://}/{@code falkors://} aliases to {@code redis://}/{@code
     * rediss://} (matching the other FalkorDB clients), then validates it the same way {@link
     * FalkorDB#driver(URI)} does. A pure function so it is directly unit-testable; any failure — a
     * syntax error, an unsupported scheme, or a missing host/port — is reported as an {@link
     * IllegalStateException} naming {@link #URL_VAR} and the offending value, with any embedded
     * credentials redacted so they never end up in a log or a crash report.
     */
    static URI toConnectionUri(String value) {
        String normalized = value;
        if (normalized.startsWith("falkor://")) {
            normalized = "redis://" + normalized.substring("falkor://".length());
        } else if (normalized.startsWith("falkors://")) {
            normalized = "rediss://" + normalized.substring("falkors://".length());
        }
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException e) {
            // Deliberately not chained as the cause: URISyntaxException.getMessage() quotes the raw
            // input, which would put the credentials we just redacted straight back into the stack
            // trace. getReason() is the value-free half of it, so carry that instead.
            throw new IllegalStateException(
                    URL_VAR + " is not a valid connection URI: \"" + redact(value) + "\" (" + e.getReason() + ")");
        }
        if (!JedisURIHelper.isValid(uri)) {
            throw new IllegalStateException(URL_VAR + " is not a valid connection URI: \"" + redact(value) + "\"");
        }
        return uri;
    }

    /**
     * Replaces any {@code userinfo@} credentials segment in {@code value} with a fixed placeholder,
     * keeping the {@code ://} separator (or the start of the value) that anchored it.
     */
    private static String redact(String value) {
        return USERINFO_PREFIX.matcher(value).replaceAll("$1<redacted>@");
    }

    /**
     * Splits a comma-separated {@link #SENTINELS_VAR} value into {@code host:port} addresses, ignoring
     * empty entries so a trailing comma is harmless. The addresses themselves are validated later, by
     * the builder, so the error message for a malformed one is the same however it was configured.
     *
     * @param value the raw variable value, already trimmed to non-null
     * @return the listed addresses, in order
     * @throws IllegalStateException if the value lists no address at all
     */
    static List<String> parseSentinels(String value) {
        List<String> addresses = new ArrayList<>();
        for (String candidate : value.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                addresses.add(trimmed);
            }
        }
        if (addresses.isEmpty()) {
            throw new IllegalStateException(
                    SENTINELS_VAR + " must list at least one host:port address, but was \"" + value + "\"");
        }
        return addresses;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(PORT_VAR + " must be a valid integer, but was \"" + value + "\"", e);
        }
    }

    /**
     * Normalizes an environment value to {@code null} when it is unset, empty, or whitespace-only, so
     * a blank variable is treated exactly like an unset one. Package-private so tests that need to
     * skip when the ambient environment configures {@code driver()} can apply the very same rule
     * rather than re-implementing (and drifting from) it.
     */
    static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
