package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.graph_cache.GraphCache;
import java.util.List;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;

class GraphContextImplTest {

    /** FalkorDB's reply to deleting a graph that does not exist. */
    private static final String EMPTY_KEY = "ERR Invalid graph operation on empty key";

    /** The reply to a write that created one node: statistics only, no result rows. */
    private static final String WRITE_REPLY =
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.bulk("Nodes created: 1")));

    @Test
    void aFailedDeleteKeepsTheConnectionBoundToTheContext() {
        try (FakeRedisServer server = new FakeRedisServer(GraphContextImplTest::answer);
                JedisPool pool = new JedisPool("127.0.0.1", server.port())) {
            Jedis connection = pool.getResource();
            GraphContextImpl context = new GraphContextImpl(connection, new GraphCache(), "missing");

            assertThrows(JedisDataException.class, context::deleteGraph);
            assertEquals(1, pool.getNumActive(), "a failed command must not return the connection to the pool");

            // the context keeps working on its connection
            assertEquals(1, context.query("CREATE ()").getStatistics().nodesCreated());

            context.close();
            assertEquals(0, pool.getNumActive());
            assertEquals(1, pool.getNumIdle());
            // returning the connection a second time would have disconnected it
            assertTrue(connection.isConnected(), "close() must return the connection to the pool exactly once");
        }
    }

    @Test
    void aFailedDeleteIsReportedAsAGraphException() {
        try (FakeRedisServer server = new FakeRedisServer(GraphContextImplTest::answer);
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "missing")) {
            GraphException error = assertThrows(GraphException.class, context::deleteGraph);
            assertTrue(error.getMessage().contains(EMPTY_KEY), error.getMessage());
        }
    }

    private static String answer(List<String> command) {
        if (FakeRedisServer.startsWith(command, "GRAPH.DELETE")) {
            return FakeRedisServer.error(EMPTY_KEY);
        }
        if (FakeRedisServer.startsWith(command, "GRAPH.QUERY")) {
            return WRITE_REPLY;
        }
        return FakeRedisServer.ok();
    }
}
