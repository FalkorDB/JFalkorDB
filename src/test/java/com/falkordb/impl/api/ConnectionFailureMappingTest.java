package com.falkordb.impl.api;

import static com.falkordb.impl.api.FakeRedisServer.array;
import static com.falkordb.impl.api.FakeRedisServer.bulk;
import static com.falkordb.impl.api.FakeRedisServer.integer;
import static com.falkordb.impl.api.FakeRedisServer.ok;
import static com.falkordb.impl.api.FakeRedisServer.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.AsyncFalkorDB;
import com.falkordb.AsyncGraph;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.exceptions.FalkorDBConnectionException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisException;

/**
 * A connection failure that escapes a public {@code Driver}, {@code Graph}, {@code GraphContext} or {@code
 * AsyncGraph} method is a {@link FalkorDBConnectionException}, with Jedis' message, cause and suppressed
 * exceptions; failures to get a connection that Jedis doesn't report as a connection failure are left as they are.
 */
class ConnectionFailureMappingTest {

    /** Every {@link Graph} method that sends a command, each run once on the graph it is given. */
    private static final List<Consumer<Graph>> GRAPH_CALLS = Arrays.asList(
            g -> g.query("RETURN 1"),
            g -> g.readOnlyQuery("RETURN 1"),
            g -> g.query("RETURN 1", 100L),
            g -> g.readOnlyQuery("RETURN 1", 100L),
            g -> g.query("RETURN $x", Collections.singletonMap("x", 1)),
            g -> g.readOnlyQuery("RETURN $x", Collections.singletonMap("x", 1)),
            g -> g.query("RETURN $x", Collections.singletonMap("x", 1), 100L),
            g -> g.readOnlyQuery("RETURN $x", Collections.singletonMap("x", 1), 100L),
            g -> g.callProcedure("db.labels"),
            g -> g.callProcedure("db.labels", Collections.emptyList()),
            g -> g.callProcedure("db.labels", Collections.emptyList(), Collections.emptyMap()),
            g -> g.profile("RETURN 1"),
            g -> g.profile("RETURN $x", Collections.singletonMap("x", 1)),
            g -> g.explain("RETURN 1"),
            g -> g.explain("RETURN $x", Collections.singletonMap("x", 1)),
            g -> g.copyGraph("copy"),
            Graph::deleteGraph);

    /** Every {@link Driver} method that talks to the server. */
    private static final List<Consumer<Driver>> DRIVER_CALLS = Arrays.asList(
            d -> d.getConnection().close(),
            d -> ((DriverImpl) d).getConnection("g").close(),
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
    void aServerThatIsDownFailsEveryMethodWithAConnectionException() throws Exception {
        int port = deadPort();
        try (Driver driver = direct(port)) {
            for (Consumer<Driver> call : DRIVER_CALLS) {
                assertRefused(port, () -> call.accept(driver));
            }
            GraphContextGenerator graph = driver.graph("g");
            for (Consumer<Graph> call : GRAPH_CALLS) {
                assertRefused(port, () -> call.accept(graph));
            }
            assertRefused(port, graph::getContext);
        }
    }

    @Test
    void anAsyncGraphCompletesWithTheConnectionException() throws Exception {
        int port = deadPort();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Driver driver = direct(port)) {
            AsyncGraph async = AsyncFalkorDB.wrap(driver.graph("g"), executor);
            CompletionException e = assertThrows(
                    CompletionException.class, () -> async.query("RETURN 1").join());
            assertNotNull(e.getCause());
            assertEquals(FalkorDBConnectionException.class, e.getCause().getClass());
            assertEquals(
                    "Failed to connect to 127.0.0.1:" + port + ".", e.getCause().getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aConnectionThatBreaksMidCommandFailsWithAConnectionException() {
        AtomicReference<FakeRedisServer> self = new AtomicReference<>();
        try (FakeRedisServer server = new FakeRedisServer(c -> {
                    if (c.get(0).toUpperCase(Locale.ROOT).startsWith("GRAPH.")
                            || startsWith(c, "WATCH")
                            || startsWith(c, "UNWATCH")
                            || startsWith(c, "MULTI")) {
                        self.get().dropClients();
                        return "";
                    }
                    return ok();
                });
                Driver driver = direct(server.port())) {
            self.set(server);
            GraphContextGenerator graph = driver.graph("g");
            for (Consumer<Graph> call : GRAPH_CALLS) {
                assertBroken(() -> call.accept(graph));
                assertBroken(() -> {
                    try (GraphContext context = graph.getContext()) {
                        call.accept(context);
                    }
                });
            }
            List<Consumer<GraphContext>> contextCalls = Arrays.asList(c -> c.watch("g"), GraphContext::unwatch);
            for (Consumer<GraphContext> call : contextCalls) {
                assertBroken(() -> {
                    try (GraphContext context = graph.getContext()) {
                        call.accept(context);
                    }
                });
            }
            // MULTI's reply is read with EXEC's, so multi() fails only when it can't send: on a connection that broke
            try (GraphContext context = graph.getContext()) {
                assertBroken(() -> context.watch("g"));
                assertBroken(context::multi);
            }
            assertBroken(driver::listGraphs);
        }
    }

    @Test
    void aLookupThatLosesItsConnectionWhileDecodingAReplyFailsWithAConnectionException() {
        // The query ran; only the label lookup its reply needs failed. That is a connection failure, not a reply the
        // client could not decode.
        AtomicReference<FakeRedisServer> self = new AtomicReference<>();
        try (FakeRedisServer server = new FakeRedisServer(c -> {
                    if (startsWith(c, "GRAPH.RO_QUERY") && c.get(2).equals("CALL db.labels()")) {
                        self.get().dropClients();
                        return "";
                    }
                    if (startsWith(c, "GRAPH.QUERY")) {
                        return nodeReply();
                    }
                    return ok();
                });
                Driver driver = direct(server.port())) {
            self.set(server);
            GraphContextGenerator graph = driver.graph("g");
            assertBroken(() -> graph.query("MATCH (n) RETURN n"));
            assertBroken(() -> {
                try (GraphContext context = graph.getContext()) {
                    context.query("MATCH (n) RETURN n");
                }
            });
        }
    }

    @Test
    void anExplicitSentinelThatIsDownFailsWithAConnectionException() throws Exception {
        int port = deadPort();
        try (Driver driver = FalkorDB.builder()
                .sentinel("mymaster", "127.0.0.1:" + port)
                .connectionTimeout(Duration.ofSeconds(1))
                .build()) {
            FalkorDBConnectionException e = assertThrowsExactly(
                    FalkorDBConnectionException.class, () -> driver.graph("g").query("RETURN 1"));
            assertTrue(e.getMessage().startsWith("All sentinels down"), e.getMessage());
            e = assertThrowsExactly(FalkorDBConnectionException.class, driver::listGraphs);
            assertTrue(e.getMessage().startsWith("All sentinels down"), e.getMessage());
        }
    }

    @Test
    void aClusterThatCannotBeReachedStillThrowsTheClusterOperationException() throws Exception {
        // Guard: a JedisClusterOperationException is not a JedisConnectionException, so it can't become one without
        // breaking a catch for it.
        int port = deadPort();
        try (Driver driver = FalkorDB.builder()
                .cluster("127.0.0.1:" + port)
                .connectionTimeout(Duration.ofSeconds(1))
                .build()) {
            assertThrowsExactly(JedisClusterOperationException.class, driver::listGraphs);
            assertThrowsExactly(JedisClusterOperationException.class, () -> driver.graph("g")
                    .query("RETURN 1"));
        }
    }

    @Test
    void anExhaustedPoolStillThrowsJedisPlainException() {
        // Guard: no connection became free in time, which is not a connection failure.
        try (FakeRedisServer server = new FakeRedisServer(c -> ok());
                Driver driver = FalkorDB.builder()
                        .host("127.0.0.1")
                        .port(server.port())
                        .poolMaxTotal(1)
                        .poolMaxIdle(1)
                        .poolMaxWait(Duration.ZERO)
                        .autoDetectSentinel(false)
                        .autoDetectCluster(false)
                        .build();
                GraphContext held = driver.graph("g").getContext()) {
            assertNotNull(held);
            JedisException e = assertThrowsExactly(JedisException.class, driver::listGraphs);
            assertTrue(e.getCause() instanceof NoSuchElementException, String.valueOf(e.getCause()));
            e = assertThrowsExactly(
                    JedisException.class, () -> driver.graph("g").query("RETURN 1"));
            assertTrue(e.getCause() instanceof NoSuchElementException, String.valueOf(e.getCause()));
        }
    }

    @Test
    void aDriverClosedAfterUseStillReportsTheClosedPoolAsBefore() {
        // Guard: using a closed driver is a programming error, not a connection failure.
        try (FakeRedisServer server = new FakeRedisServer(c -> startsWith(c, "GRAPH.LIST") ? array() : ok())) {
            Driver used = direct(server.port());
            assertEquals(Collections.emptyList(), used.listGraphs());
            used.close();
            JedisException e = assertThrowsExactly(JedisException.class, used::listGraphs);
            assertTrue(e.getCause() instanceof IllegalStateException, String.valueOf(e.getCause()));

            Driver unused = direct(server.port());
            unused.close();
            assertThrowsExactly(IllegalStateException.class, unused::listGraphs);
        }
    }

    private static void assertRefused(int port, Executable call) {
        FalkorDBConnectionException e = assertThrowsExactly(FalkorDBConnectionException.class, call);
        // Jedis' own message, and the refused attempt it records as a suppressed exception
        assertEquals("Failed to connect to 127.0.0.1:" + port + ".", e.getMessage());
        assertTrue(
                Arrays.stream(e.getSuppressed()).anyMatch(s -> s instanceof ConnectException),
                Arrays.toString(e.getSuppressed()));
    }

    private static void assertBroken(Executable call) {
        FalkorDBConnectionException e = assertThrowsExactly(FalkorDBConnectionException.class, call);
        assertNotNull(e.getMessage());
    }

    /** A port nothing listens on. */
    private static int deadPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}))) {
            return socket.getLocalPort();
        }
    }

    /** A driver on a single server, with no Sentinel or cluster probe. */
    private static Driver direct(int port) {
        return FalkorDB.builder()
                .host("127.0.0.1")
                .port(port)
                .connectionTimeout(Duration.ofSeconds(1))
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .build();
    }

    /** A compact reply with one node labeled with label id 0, which the client has to look up. */
    private static String nodeReply() {
        String header = array(array(integer(1), bulk("n")));
        String node = array(integer(8), array(integer(7), array(integer(0)), array()));
        return array(header, array(array(node)), array(bulk("Cached execution: 0")));
    }
}
