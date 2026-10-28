package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.AsyncFalkorDB;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Queries sent with a timeout. The timeout is for the server; the driver's socket timeout still bounds the
 * client's wait for the reply, and a negative timeout is rejected before anything is sent.
 */
class QueryTimeoutTest {

    /** The reply to a write that created one node: statistics only, no result rows. */
    private static final String WRITE_REPLY =
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.bulk("Nodes created: 1")));

    private static final Map<String, Object> PARAMS = Collections.singletonMap("x", 1);

    @Test
    void theSocketTimeoutStillBoundsTheWaitForAReply() throws Exception {
        // The server never answers a graph query. Before, a query with a timeout was read with no socket
        // timeout at all (Jedis' sendBlockingCommand), so each of these calls hung forever.
        try (FakeRedisServer server = new FakeRedisServer(QueryTimeoutTest::neverAnswerGraphQueries);
                Driver driver = driver(server, Duration.ofMillis(200))) {
            GraphContextGenerator graph = driver.graph("g");
            List<Consumer<Graph>> calls = timeoutCalls(50L);
            for (Consumer<Graph> call : calls) {
                assertReadTimesOut(() -> call.accept(graph));
                // a fresh context each time: the read timeout leaves the connection broken
                assertReadTimesOut(() -> {
                    try (GraphContext context = graph.getContext()) {
                        call.accept(context);
                    }
                });
            }
            long sent = server.received().stream()
                    .filter(c -> FakeRedisServer.startsWith(c, "GRAPH.QUERY")
                            || FakeRedisServer.startsWith(c, "GRAPH.RO_QUERY"))
                    .filter(c -> c.containsAll(Arrays.asList("TIMEOUT", "50")))
                    .count();
            assertEquals(2L * calls.size(), sent);
        }
    }

    @Test
    void aNegativeTimeoutIsRejectedBeforeAnythingIsSent() throws Exception {
        // The server would take TIMEOUT -1 as "use the default timeout" and run the query.
        try (FakeRedisServer server = new FakeRedisServer(QueryTimeoutTest::answerGraphQueries);
                Driver driver = driver(server, Duration.ZERO)) {
            GraphContextGenerator graph = driver.graph("g");
            for (Consumer<Graph> call : timeoutCalls(-1L)) {
                assertNegativeTimeoutRejected(() -> call.accept(graph));
                try (GraphContext context = graph.getContext()) {
                    assertNegativeTimeoutRejected(() -> call.accept(context));
                }
            }
            assertEquals(0, graphCommands(server));

            try (GraphContext context = graph.getContext()) {
                GraphPipeline pipeline = context.pipelined();
                assertNegativeTimeoutRejected(() -> pipeline.query("RETURN 1", -1L));
                assertNegativeTimeoutRejected(() -> pipeline.readOnlyQuery("RETURN 1", -1L));
                assertNegativeTimeoutRejected(() -> pipeline.query("RETURN $x", PARAMS, -1L));
                assertNegativeTimeoutRejected(() -> pipeline.readOnlyQuery("RETURN $x", PARAMS, -1L));
                // a queued command would be read back here, so an empty result means nothing was queued
                assertTrue(pipeline.syncAndReturnAll().isEmpty());
            }

            try (GraphContext context = graph.getContext()) {
                GraphTransaction transaction = context.multi();
                assertNegativeTimeoutRejected(() -> transaction.query("RETURN 1", -1L));
                assertNegativeTimeoutRejected(() -> transaction.readOnlyQuery("RETURN 1", -1L));
                assertNegativeTimeoutRejected(() -> transaction.query("RETURN $x", PARAMS, -1L));
                assertNegativeTimeoutRejected(() -> transaction.readOnlyQuery("RETURN $x", PARAMS, -1L));
                // DISCARD flushes whatever the transaction buffered after MULTI
                transaction.close();
            }
            assertEquals(1, server.count("MULTI"));
            assertEquals(1, server.count("DISCARD"));
            assertEquals(0, graphCommands(server));
        }
    }

    @Test
    void aNegativeTimeoutFailsAnAsyncFuture() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (FakeRedisServer server = new FakeRedisServer(QueryTimeoutTest::answerGraphQueries);
                Driver driver = driver(server, Duration.ZERO)) {
            CompletionException e =
                    assertThrows(CompletionException.class, () -> AsyncFalkorDB.wrap(driver.graph("g"), executor)
                            .query("RETURN 1", -1L)
                            .join());
            assertTrue(e.getCause() instanceof IllegalArgumentException, String.valueOf(e.getCause()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aZeroTimeoutIsSent() throws Exception {
        // 0 sets no timeout of the query's own, so the server's default applies.
        try (FakeRedisServer server = new FakeRedisServer(QueryTimeoutTest::answerGraphQueries);
                Driver driver = driver(server, Duration.ZERO)) {
            assertEquals(
                    1, driver.graph("g").query("CREATE ()", 0L).getStatistics().nodesCreated());
            assertTrue(server.received().stream()
                    .anyMatch(c -> FakeRedisServer.startsWith(c, "GRAPH.QUERY")
                            && c.containsAll(Arrays.asList("TIMEOUT", "0"))));
        }
    }

    /** The four timeout overloads of {@link Graph}, each with the given timeout. */
    private static List<Consumer<Graph>> timeoutCalls(long timeout) {
        return Arrays.asList(
                g -> g.query("RETURN 1", timeout),
                g -> g.readOnlyQuery("RETURN 1", timeout),
                g -> g.query("RETURN $x", PARAMS, timeout),
                g -> g.readOnlyQuery("RETURN $x", PARAMS, timeout));
    }

    private static void assertReadTimesOut(Runnable call) {
        JedisConnectionException e = assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> assertThrows(JedisConnectionException.class, call::run),
                "the call ignored the socket timeout");
        assertTrue(e.getCause() instanceof SocketTimeoutException, String.valueOf(e.getCause()));
    }

    private static void assertNegativeTimeoutRejected(Runnable call) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call::run);
        assertEquals("timeout must not be negative, but was -1", e.getMessage());
    }

    private static long graphCommands(FakeRedisServer server) {
        return server.received().stream()
                .filter(c -> !c.isEmpty() && c.get(0).toUpperCase(Locale.ROOT).startsWith("GRAPH."))
                .count();
    }

    private static Driver driver(FakeRedisServer server, Duration socketTimeout) {
        return FalkorDB.builder()
                .host("127.0.0.1")
                .port(server.port())
                .socketTimeout(socketTimeout)
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .build();
    }

    private static String neverAnswerGraphQueries(List<String> command) {
        if (FakeRedisServer.startsWith(command, "GRAPH.QUERY")
                || FakeRedisServer.startsWith(command, "GRAPH.RO_QUERY")) {
            return ""; // no reply at all
        }
        return FakeRedisServer.ok();
    }

    private static String answerGraphQueries(List<String> command) {
        if (FakeRedisServer.startsWith(command, "GRAPH.QUERY")
                || FakeRedisServer.startsWith(command, "GRAPH.RO_QUERY")) {
            return WRITE_REPLY;
        }
        return FakeRedisServer.ok();
    }
}
