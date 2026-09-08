package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DriverEnvironment} — the environment-based resolution behind the no-arg
 * {@link FalkorDB#driver()}. These inject a fake {@code env} lookup instead of mutating the real
 * process environment (which Java has no clean, portable way to do), and never connect to a server:
 * driver construction is lazy (see {@link ConfigBuilderTest}), so no I/O happens here.
 */
class DriverEnvironmentTest {

    private static Function<String, String> env(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "env() takes alternating key/value arguments, so it needs an even number of them, but got "
                            + keyValuePairs.length);
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            values.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return values::get;
    }

    @Test
    void defaultsAreLocalhost6379() {
        // Pinned directly since a Driver does not expose the host/port it was built with.
        assertEquals("localhost", DriverEnvironment.DEFAULT_HOST);
        assertEquals(6379, DriverEnvironment.DEFAULT_PORT);
    }

    @Test
    void neitherSetUsesTheDefault() {
        assertDoesNotThrow(() -> DriverEnvironment.resolve(env()).close());
    }

    @Test
    void blankValuesAreTreatedAsUnset() {
        // A blank/whitespace-only value must fall back to the default rather than being treated as
        // "set", so it does not (for example) trip the host/port partial-configuration check.
        assertDoesNotThrow(() -> DriverEnvironment.resolve(env(
                        DriverEnvironment.URL_VAR, "  ",
                        DriverEnvironment.HOST_VAR, "",
                        DriverEnvironment.PORT_VAR, "   "))
                .close());
    }

    @Test
    void hostAndPortAreUsedWhenBothSet() {
        assertDoesNotThrow(() -> DriverEnvironment.resolve(
                        env(DriverEnvironment.HOST_VAR, "db.example.com", DriverEnvironment.PORT_VAR, "6380"))
                .close());
    }

    @Test
    void onlyHostSetThrows() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> DriverEnvironment.resolve(env(DriverEnvironment.HOST_VAR, "db.example.com")));
        assertTrue(e.getMessage().contains(DriverEnvironment.HOST_VAR));
        assertTrue(e.getMessage().contains(DriverEnvironment.PORT_VAR));
    }

    @Test
    void onlyPortSetThrows() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class, () -> DriverEnvironment.resolve(env(DriverEnvironment.PORT_VAR, "6380")));
        assertTrue(e.getMessage().contains(DriverEnvironment.HOST_VAR));
        assertTrue(e.getMessage().contains(DriverEnvironment.PORT_VAR));
    }

    @Test
    void invalidPortThrows() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> DriverEnvironment.resolve(
                        env(DriverEnvironment.HOST_VAR, "db.example.com", DriverEnvironment.PORT_VAR, "not-a-number")));
        assertTrue(e.getMessage().contains(DriverEnvironment.PORT_VAR));
        assertTrue(e.getMessage().contains("not-a-number"));
    }

    @Test
    void urlAloneIsUsed() {
        assertDoesNotThrow(
                () -> DriverEnvironment.resolve(env(DriverEnvironment.URL_VAR, "redis://db.example.com:6380"))
                        .close());
    }

    @Test
    void urlWithFalkorSchemeIsUsed() {
        assertDoesNotThrow(
                () -> DriverEnvironment.resolve(env(DriverEnvironment.URL_VAR, "falkor://db.example.com:6380"))
                        .close());
    }

    @Test
    void urlWithFalkorsSchemeIsUsed() {
        assertDoesNotThrow(
                () -> DriverEnvironment.resolve(env(DriverEnvironment.URL_VAR, "falkors://db.example.com:6380"))
                        .close());
    }

    @Test
    void urlWinsOverHostAndPort() {
        // The garbage FALKORDB_PORT must never be looked at: if the URL branch did not
        // short-circuit before the host/port branch, this would throw exactly like
        // invalidPortThrows() does.
        assertDoesNotThrow(() -> DriverEnvironment.resolve(env(
                        DriverEnvironment.URL_VAR, "redis://db.example.com:6380",
                        DriverEnvironment.HOST_VAR, "ignored.example.com",
                        DriverEnvironment.PORT_VAR, "not-a-number"))
                .close());
    }

    @Test
    void malformedUrlThrows() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> DriverEnvironment.resolve(env(DriverEnvironment.URL_VAR, "not a uri at all")));
        assertTrue(e.getMessage().contains(DriverEnvironment.URL_VAR));
        assertTrue(e.getMessage().contains("not a uri at all"));
    }

    @Test
    void unsupportedSchemeUrlThrows() {
        assertThrows(
                IllegalStateException.class,
                () -> DriverEnvironment.resolve(env(DriverEnvironment.URL_VAR, "http://db.example.com:6380")));
    }

    @Test
    void urlMissingPortThrows() {
        assertThrows(
                IllegalStateException.class,
                () -> DriverEnvironment.resolve(env(DriverEnvironment.URL_VAR, "redis://db.example.com")));
    }

    @Test
    void toConnectionUriAcceptsRedisScheme() {
        URI uri = DriverEnvironment.toConnectionUri("redis://db.example.com:6380");
        assertEquals("redis", uri.getScheme());
        assertEquals("db.example.com", uri.getHost());
        assertEquals(6380, uri.getPort());
    }

    @Test
    void toConnectionUriAcceptsRedissScheme() {
        URI uri = DriverEnvironment.toConnectionUri("rediss://db.example.com:6380");
        assertEquals("rediss", uri.getScheme());
    }

    @Test
    void toConnectionUriTranslatesFalkorScheme() {
        URI uri = DriverEnvironment.toConnectionUri("falkor://db.example.com:6380/2");
        assertEquals("redis", uri.getScheme());
        assertEquals("db.example.com", uri.getHost());
        assertEquals(6380, uri.getPort());
        assertEquals("/2", uri.getPath());
    }

    @Test
    void toConnectionUriTranslatesFalkorsScheme() {
        URI uri = DriverEnvironment.toConnectionUri("falkors://db.example.com:6380");
        assertEquals("rediss", uri.getScheme());
        assertEquals("db.example.com", uri.getHost());
        assertEquals(6380, uri.getPort());
    }

    @Test
    void toConnectionUriRejectsSyntaxError() {
        assertThrows(IllegalStateException.class, () -> DriverEnvironment.toConnectionUri("redis://[bad"));
    }

    @Test
    void toConnectionUriRejectsUnsupportedScheme() {
        assertThrows(
                IllegalStateException.class, () -> DriverEnvironment.toConnectionUri("http://db.example.com:6380"));
    }

    @Test
    void toConnectionUriRejectsMissingPort() {
        assertThrows(IllegalStateException.class, () -> DriverEnvironment.toConnectionUri("redis://db.example.com"));
    }

    @Test
    void toConnectionUriRedactsCredentialsOnSyntaxError() {
        String withCredentials = "redis://" + "cred" + "@[bad";
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> DriverEnvironment.toConnectionUri(withCredentials));
        assertTrue(e.getMessage().contains("<redacted>"));
        assertFalse(e.getMessage().contains("cred@"));
    }

    @Test
    void toConnectionUriRedactsCredentialsOnUnsupportedScheme() {
        String withCredentials = "http://" + "cred" + "@db.example.com:6380";
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> DriverEnvironment.toConnectionUri(withCredentials));
        assertTrue(e.getMessage().contains("<redacted>"));
        assertFalse(e.getMessage().contains("cred@"));
    }

    @Test
    void toConnectionUriRedactsCredentialsWhenTheSchemeIsMissing() {
        // A scheme-less value is the easy mistake to make, and URI parses it as an *opaque* URI
        // instead of rejecting it, so it reaches the "not valid" message with its credentials intact
        // unless redaction is anchored at the start of the value as well as at "://".
        String withCredentials = "user:" + "cred" + "@db.example.com:6379";
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> DriverEnvironment.toConnectionUri(withCredentials));
        assertTrue(e.getMessage().contains("<redacted>"));
        assertFalse(e.getMessage().contains("cred@"));
    }

    @Test
    void toConnectionUriKeepsCredentialsOutOfTheWholeCausalChain() {
        // Redacting only the top-level message is not enough: URISyntaxException quotes the raw input
        // in its own message, so chaining it as a cause would leak the credentials into any log or
        // crash report that prints the stack trace.
        String withCredentials = "redis://" + "cred" + "@[bad";
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> DriverEnvironment.toConnectionUri(withCredentials));
        assertFalse(stackTraceOf(e).contains("cred@"), "credentials leaked through the causal chain");
    }

    @Test
    void toConnectionUriStillExplainsWhyTheUriWasRejected() {
        // Dropping the cause must not cost the diagnosis: the value-free reason is carried over.
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> DriverEnvironment.toConnectionUri("redis://[bad"));
        assertTrue(e.getMessage().contains(DriverEnvironment.URL_VAR));
        assertTrue(
                e.getMessage().toLowerCase(Locale.ROOT).contains("bracket")
                        || e.getMessage().toLowerCase(Locale.ROOT).contains("illegal"),
                "expected the URISyntaxException reason to be carried over, but was: " + e.getMessage());
    }

    /** Renders {@code t} and its whole causal chain the way a crash report or log would. */
    private static String stackTraceOf(Throwable t) {
        StringWriter out = new StringWriter();
        try (PrintWriter writer = new PrintWriter(out)) {
            t.printStackTrace(writer);
        }
        return out.toString();
    }

    @Test
    void sentinelVariablesConfigureASentinelDeployment() {
        assertDoesNotThrow(() -> DriverEnvironment.resolve(env(
                        DriverEnvironment.SENTINEL_MASTER_VAR, "mymaster",
                        DriverEnvironment.SENTINELS_VAR, "a:26379,b:26379"))
                .close());
    }

    @Test
    void onlySentinelMasterSetThrows() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> DriverEnvironment.resolve(env(DriverEnvironment.SENTINEL_MASTER_VAR, "mymaster")));
        assertTrue(e.getMessage().contains(DriverEnvironment.SENTINELS_VAR), e.getMessage());
    }

    @Test
    void onlySentinelListSetThrows() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> DriverEnvironment.resolve(env(DriverEnvironment.SENTINELS_VAR, "a:26379")));
        assertTrue(e.getMessage().contains(DriverEnvironment.SENTINEL_MASTER_VAR), e.getMessage());
    }

    @Test
    void blankSentinelVariablesAreTreatedAsUnset() {
        // Same rule as the host/port pair: a whitespace-only value must not trip the pairing check.
        assertDoesNotThrow(() -> DriverEnvironment.resolve(env(
                        DriverEnvironment.SENTINEL_MASTER_VAR, "  ",
                        DriverEnvironment.SENTINELS_VAR, "   "))
                .close());
    }

    @Test
    void sentinelTakesPrecedenceOverUrlAndHostPort() {
        // A malformed URL alongside a valid Sentinel configuration must not be reached at all, which
        // is what proves the ordering rather than merely that both happen to work.
        assertDoesNotThrow(() -> DriverEnvironment.resolve(env(
                        DriverEnvironment.SENTINEL_MASTER_VAR, "mymaster",
                        DriverEnvironment.SENTINELS_VAR, "a:26379",
                        DriverEnvironment.URL_VAR, "not a uri at all",
                        DriverEnvironment.HOST_VAR, "db.example.com"))
                .close());
    }

    @Test
    void sentinelListIsSplitTrimmedAndTolerantOfEmptyEntries() {
        assertEquals(
                Arrays.asList("a:26379", "b:26380", "c:26381"),
                DriverEnvironment.parseSentinels(" a:26379 , b:26380,,c:26381 , "));
    }

    @Test
    void sentinelListWithNoAddressesThrows() {
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> DriverEnvironment.parseSentinels(",  ,"));
        assertTrue(e.getMessage().contains(DriverEnvironment.SENTINELS_VAR), e.getMessage());
    }

    @Test
    void malformedSentinelAddressIsRejected() {
        // Address validation is the builder's, so this also pins that the env path routes through it.
        assertThrows(
                IllegalArgumentException.class,
                () -> DriverEnvironment.resolve(env(
                        DriverEnvironment.SENTINEL_MASTER_VAR, "mymaster",
                        DriverEnvironment.SENTINELS_VAR, "no-port-here")));
    }
}
