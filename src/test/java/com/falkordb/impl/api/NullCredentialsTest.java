package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@link FalkorDB#driver(String, int, String, String)} takes {@code null} for "none": a null username is how this
 * factory authenticates with a password only, and {@code driver(host, port, null, null)} (used by InstantiationIT)
 * sends no credentials. So both parameters are {@code @Nullable}. This test pins the annotations and what null
 * really does on the wire, against an in-process fake server.
 */
class NullCredentialsTest {

    @Test
    void driverFactoryTakesNullCredentials() throws Exception {
        Method driver = FalkorDB.class.getMethod("driver", String.class, int.class, String.class, String.class);
        for (int index : new int[] {2, 3}) {
            assertTrue(
                    driver.getAnnotatedParameterTypes()[index].isAnnotationPresent(Nullable.class),
                    "driver(host, port, user, password) parameter " + index + " should be @Nullable");
        }

        assertEquals(
                Arrays.asList("AUTH", "s3cret"),
                authSent(server -> FalkorDB.driver("127.0.0.1", server.port(), null, "s3cret")),
                "a null user sends a password-only AUTH");
        assertNull(
                authSent(server -> FalkorDB.driver("127.0.0.1", server.port(), null, null)),
                "null for both sends no AUTH");
        // Documented on the factory: Jedis sends no AUTH without a password, so a user alone is ignored.
        assertNull(
                authSent(server -> FalkorDB.driver("127.0.0.1", server.port(), "alice", null)),
                "a user without a password sends no AUTH");
    }

    /**
     * Connects with the driver {@code connect} makes, borrows one connection, and returns the AUTH it sent, or null
     * if none. The factory probes the endpoint for Sentinel and cluster mode first, on connections of its own that
     * authenticate the same way, so every AUTH must be the same one.
     */
    private static @Nullable List<String> authSent(Function<FakeRedisServer, Driver> connect) throws IOException {
        List<List<String>> auths;
        try (FakeRedisServer server = new FakeRedisServer(command -> FakeRedisServer.ok());
                Driver driver = connect.apply(server)) {
            driver.getConnection().close();
            auths = server.received().stream()
                    .filter(command -> FakeRedisServer.startsWith(command, "AUTH"))
                    .distinct()
                    .collect(Collectors.toList());
        }
        assertTrue(auths.size() <= 1, "one kind of AUTH expected, got " + auths);
        return auths.isEmpty() ? null : auths.get(0);
    }
}
