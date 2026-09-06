package com.falkordb;

import java.net.URI;
import java.net.URISyntaxException;
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

    /** Default host used when neither {@link #URL_VAR} nor {@link #HOST_VAR}/{@link #PORT_VAR} is set. */
    static final String DEFAULT_HOST = "localhost";

    /** Default port used when neither {@link #URL_VAR} nor {@link #HOST_VAR}/{@link #PORT_VAR} is set. */
    static final int DEFAULT_PORT = 6379;

    /** Matches the credentials segment of a URI authority, so it can be redacted before logging. */
    private static final Pattern USERINFO_PREFIX = Pattern.compile("://[^/?#@]*@");

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
        String url = trimToNull(env.apply(URL_VAR));
        if (url != null) {
            URI uri = toConnectionUri(url);
            try {
                return FalkorDB.driver(uri);
            } catch (RuntimeException e) {
                throw new IllegalStateException(URL_VAR + " is not a valid connection URI: \"" + redact(url) + "\"", e);
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
            throw new IllegalStateException(URL_VAR + " is not a valid connection URI: \"" + redact(value) + "\"", e);
        }
        if (!JedisURIHelper.isValid(uri)) {
            throw new IllegalStateException(URL_VAR + " is not a valid connection URI: \"" + redact(value) + "\"");
        }
        return uri;
    }

    /** Replaces any {@code userinfo@} authority prefix in {@code value} with a fixed placeholder. */
    private static String redact(String value) {
        return USERINFO_PREFIX.matcher(value).replaceFirst("://<redacted>@");
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(PORT_VAR + " must be a valid integer, but was \"" + value + "\"", e);
        }
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
