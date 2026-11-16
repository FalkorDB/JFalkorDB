package com.falkordb.impl.api;

import static com.falkordb.impl.api.FakeRedisServer.bulk;
import static com.falkordb.impl.api.FakeRedisServer.error;
import static com.falkordb.impl.api.FakeRedisServer.ok;
import static com.falkordb.impl.api.FakeRedisServer.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.exceptions.GraphException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import redis.clients.jedis.exceptions.JedisAccessControlException;

/**
 * A plain error reply that escapes a public {@code Driver} or {@code GraphContext} method, or a {@code Graph}
 * method before its command is sent, is a {@link GraphException} with the server's message; an error Jedis has a
 * subclass for is left as it is.
 */
class ServerErrorMappingTest {

    private static final String ERROR = "ERR something went wrong";

    /** Every {@link Driver} method that sends a command. */
    private static final List<Consumer<Driver>> DRIVER_CALLS = Arrays.asList(
            Driver::listGraphs,
            d -> d.udfLoad("lib", "script", false),
            d -> d.udfLoad("lib", "script", true),
            Driver::udfList,
            d -> d.udfList("lib", true),
            Driver::udfFlush,
            d -> d.udfDelete("lib"),
            d -> d.configGet("RESULTSET_SIZE"),
            Driver::configGetAll,
            d -> d.configSet("RESULTSET_SIZE", 100));

    @Test
    void aDriverMethodTheServerRejectsThrowsAGraphExceptionWithTheServersMessage() {
        try (FakeRedisServer server = new FakeRedisServer(rejecting("GRAPH.", ERROR));
                Driver driver = direct(server)) {
            for (Consumer<Driver> call : DRIVER_CALLS) {
                assertServerError(ERROR, () -> call.accept(driver));
            }
        }
    }

    @Test
    void anErrorJedisHasASubclassForIsLeftAsItIs() {
        // Guard: a catch for JedisAccessControlException must keep matching.
        try (FakeRedisServer server = new FakeRedisServer(rejecting("GRAPH.", "NOPERM this user has no permissions"));
                Driver driver = direct(server)) {
            for (Consumer<Driver> call : DRIVER_CALLS) {
                assertThrowsExactly(JedisAccessControlException.class, () -> call.accept(driver));
            }
        }
    }

    @Test
    void aConfigReplyTheClientCannotReadIsAGraphException() {
        try (FakeRedisServer server = new FakeRedisServer(c -> startsWith(c, "GRAPH.CONFIG") ? bulk("odd") : ok());
                Driver driver = direct(server)) {
            assertServerError("Unexpected response format from GRAPH.CONFIG GET", () -> driver.configGet("X"));
            assertServerError("Unexpected response format from GRAPH.CONFIG GET", driver::configGetAll);
        }
    }

    @Test
    void aContextCommandTheServerRejectsThrowsAGraphException() {
        try (FakeRedisServer server = new FakeRedisServer(c -> startsWith(c, "WATCH") || startsWith(c, "UNWATCH")
                        ? error("ERR WATCH inside MULTI is not allowed")
                        : ok());
                Driver driver = direct(server)) {
            GraphContextGenerator graph = driver.graph("g");
            List<Consumer<GraphContext>> calls = Arrays.asList(c -> c.watch("g"), GraphContext::unwatch);
            for (Consumer<GraphContext> call : calls) {
                try (GraphContext context = graph.getContext()) {
                    assertServerError("ERR WATCH inside MULTI is not allowed", () -> call.accept(context));
                }
            }
        }
    }

    @Test
    void anErrorWhileOpeningTheConnectionIsAGraphException() {
        // The server rejects AUTH with a plain error, before any graph command is sent.
        String noPassword = "ERR AUTH <password> called without any password configured for the default user";
        try (FakeRedisServer server = new FakeRedisServer(c -> startsWith(c, "AUTH") ? error(noPassword) : ok());
                Driver driver = FalkorDB.builder()
                        .host("127.0.0.1")
                        .port(server.port())
                        .credentials("secret")
                        .connectionTimeout(Duration.ofSeconds(1))
                        .autoDetectSentinel(false)
                        .autoDetectCluster(false)
                        .build()) {
            assertServerError(noPassword, driver::listGraphs);
            assertServerError(noPassword, () -> driver.graph("g").query("RETURN 1"));
            assertServerError(noPassword, () -> driver.graph("g").getContext());
        }
    }

    @Test
    void aSentinelThatRefusesToListItsMastersIsAGraphException() {
        // The endpoint names itself a Sentinel, then answers SENTINEL MASTERS with a plain error.
        String refused = "ERR unknown subcommand 'MASTERS'";
        try (FakeRedisServer server = new FakeRedisServer(c -> {
                    if (startsWith(c, "INFO")) {
                        return bulk("# Server\r\nredis_mode:sentinel\r\n");
                    }
                    return startsWith(c, "SENTINEL", "MASTERS") ? error(refused) : ok();
                });
                Driver driver = FalkorDB.builder()
                        .host("127.0.0.1")
                        .port(server.port())
                        .connectionTimeout(Duration.ofSeconds(1))
                        .autoDetectCluster(false)
                        .build()) {
            assertServerError(refused, () -> driver.graph("g").query("RETURN 1"));
            assertServerError(refused, driver::listGraphs);
        }
    }

    private static void assertServerError(String message, Executable call) {
        GraphException e = assertThrowsExactly(GraphException.class, call);
        assertEquals(message, e.getMessage());
        assertNull(e.getCause());
    }

    private static Function<List<String>, String> rejecting(String prefix, String message) {
        return c -> c.get(0).toUpperCase(Locale.ROOT).startsWith(prefix) ? error(message) : ok();
    }

    private static Driver direct(FakeRedisServer server) {
        return FalkorDB.builder()
                .host("127.0.0.1")
                .port(server.port())
                .connectionTimeout(Duration.ofSeconds(1))
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .build();
    }
}
