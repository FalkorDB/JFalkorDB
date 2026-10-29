package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Graph;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.graph_cache.GraphCache;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * A graph command the server rejects is reported as a {@link GraphException} wrapping Jedis' exception,
 * whether it runs on a {@code Graph}, which borrows a connection per call, or on a {@code GraphContext}.
 */
class GraphErrorWrappingTest {

    /** FalkorDB's reply to a graph command on a graph that does not exist. */
    private static final String EMPTY_KEY = "ERR Invalid graph operation on empty key";

    /** The commands this suite covers, each run once on the graph it is given. */
    private static final List<Consumer<Graph>> COMMANDS = Arrays.asList(
            g -> g.explain("RETURN 1"),
            g -> g.explain("RETURN $x", Collections.singletonMap("x", 1)),
            g -> g.copyGraph("copy"),
            Graph::deleteGraph);

    @Test
    void rejectedCommandsThrowAGraphExceptionWrappingTheServersError() {
        try (FakeRedisServer server = new FakeRedisServer(GraphErrorWrappingTest::rejectGraphCommands);
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                DriverImpl driver = new DriverImpl(pool)) {
            GraphContextGenerator graph = driver.graph("missing");
            for (Consumer<Graph> command : COMMANDS) {
                assertWrapped(() -> command.accept(graph));
                try (GraphContext context = graph.getContext()) {
                    assertWrapped(() -> command.accept(context));
                }
            }
            assertEquals(0, pool.getNumActive(), "every failed command returned its connection");
            assertEquals(
                    2L * COMMANDS.size(),
                    server.received().stream()
                            .filter(c -> FakeRedisServer.startsWith(c, "GRAPH.EXPLAIN")
                                    || FakeRedisServer.startsWith(c, "GRAPH.COPY")
                                    || FakeRedisServer.startsWith(c, "GRAPH.DELETE"))
                            .count());
        }
    }

    @Test
    void aFailedDeleteReturnsTheBorrowedConnectionToThePoolOnce() {
        try (FakeRedisServer server = new FakeRedisServer(GraphErrorWrappingTest::rejectGraphCommands);
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                DriverImpl driver = new DriverImpl(pool)) {
            assertThrows(GraphException.class, () -> driver.graph("missing").deleteGraph());

            assertEquals(0, pool.getNumActive());
            assertEquals(1, pool.getNumIdle());
            // returning it a second time would have disconnected the pooled connection
            try (Jedis connection = pool.getResource()) {
                assertTrue(connection.isConnected());
            }
        }
    }

    @Test
    void aFailedDeleteInAContextKeepsTheConnectionWithTheContext() {
        try (FakeRedisServer server = new FakeRedisServer(GraphErrorWrappingTest::rejectGraphCommands);
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                DriverImpl driver = new DriverImpl(pool)) {
            try (GraphContext context = driver.graph("missing").getContext()) {
                assertThrows(GraphException.class, context::deleteGraph);
                assertEquals(1, pool.getNumActive(), "the context still owns its connection");
            }
            assertEquals(0, pool.getNumActive());
            assertEquals(1, pool.getNumIdle());
        }
    }

    @Test
    void aGraphExceptionFromTheConnectionIsNotWrappedAgain() {
        // GraphException is itself a JedisDataException, so wrapping every JedisDataException the connection
        // throws would nest one GraphException in another.
        GraphException rejection = new GraphException("ERR rejected");
        Jedis connection = new Jedis() {
            @Override
            public Object sendCommand(ProtocolCommand cmd, String... args) {
                throw rejection;
            }
        };
        try (GraphContext context = new GraphContextImpl(connection, new GraphCache(), "g")) {
            assertSame(rejection, assertThrows(GraphException.class, () -> context.copyGraph("copy")));
            assertSame(rejection, assertThrows(GraphException.class, context::deleteGraph));
        }
    }

    private static void assertWrapped(Runnable command) {
        GraphException e = assertThrows(GraphException.class, command::run);
        assertTrue(e.getCause() instanceof JedisDataException, String.valueOf(e.getCause()));
        assertEquals(EMPTY_KEY, e.getCause().getMessage());
        // the message names the cause's class, as for the commands that were already wrapped
        assertEquals(JedisDataException.class.getName() + ": " + EMPTY_KEY, e.getMessage());
    }

    private static String rejectGraphCommands(List<String> command) {
        if (FakeRedisServer.startsWith(command, "GRAPH.EXPLAIN")
                || FakeRedisServer.startsWith(command, "GRAPH.COPY")
                || FakeRedisServer.startsWith(command, "GRAPH.DELETE")) {
            return FakeRedisServer.error(EMPTY_KEY);
        }
        return FakeRedisServer.ok();
    }
}
