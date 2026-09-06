package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashMap;
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
}
