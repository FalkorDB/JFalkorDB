package com.falkordb.impl.api;

import static com.falkordb.impl.api.FakeRedisServer.array;
import static com.falkordb.impl.api.FakeRedisServer.bulk;
import static com.falkordb.impl.api.FakeRedisServer.integer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.GraphContext;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * How pipelines and transactions on a context decode their replies: which connection looks up a label the
 * cache lacks, and which cache a reply queued before {@code deleteGraph()} is decoded with.
 */
class QueuedRepliesTest {

    private Server handler;
    /** Serves the context's own connection. */
    private FakeRedisServer server;
    /** Serves the pool of the graph that created the context, which has a single connection. */
    private FakeRedisServer poolServer;

    private JedisPool pool;
    private JedisPool graphPool;
    private DriverImpl driver;
    private CountingCache cache;
    private GraphContextImpl context;

    @BeforeEach
    void setUp() {
        handler = new Server();
        server = new FakeRedisServer(handler);
        poolServer = new FakeRedisServer(handler);
        pool = new JedisPool("127.0.0.1", server.port());
        JedisPoolConfig oneConnection = new JedisPoolConfig();
        oneConnection.setMaxTotal(1);
        oneConnection.setMaxWait(Duration.ofSeconds(30));
        graphPool = new JedisPool(oneConnection, "127.0.0.1", poolServer.port());
        driver = new DriverImpl(graphPool);
        cache = new CountingCache();
        context = new GraphContextImpl(pool.getResource(), driver.graph("g"), cache, "g");
    }

    @AfterEach
    void tearDown() {
        context.close();
        pool.close();
        driver.close();
        server.close();
        poolServer.close();
    }

    // --- which connection looks labels up ---------------------------------------------------------

    @Test
    void whileTheContextIsOpenAndIdleALookupUsesItsConnection() {
        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> reply = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();

        assertEquals("Person", label(reply));
        assertEquals(1, server.count("GRAPH.RO_QUERY"), "looked up on the context's connection");
        assertEquals(0, poolServer.received().size(), "borrowed no other connection");
    }

    @Test
    void afterTheContextIsClosedALookupBorrowsFromThePool() {
        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> reply = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        context.close();

        // The closed context's connection is back in its pool and may be lent to another thread.
        assertEquals("Person", label(reply));
        assertEquals(1, poolServer.count("GRAPH.RO_QUERY"));
        assertEquals(0, server.count("GRAPH.RO_QUERY"));
    }

    @Test
    void whileAnotherCommandAwaitsItsReplyALookupBorrowsFromThePool() {
        // A lookup on the connection would read the reply of a command that is still outstanding.
        GraphPipeline pipeline = context.pipelined();

        Response<ResultSet> first = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        pipeline.set("k", "v");
        assertEquals("Person", label(first));
        assertEquals(1, poolServer.count("GRAPH.RO_QUERY"), "the same pipeline has a command queued");
        pipeline.sync();

        cache.clear();
        Response<ResultSet> second = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        GraphPipeline other = context.pipelined();
        other.set("k", "v");
        assertEquals("Person", label(second));
        assertEquals(2, poolServer.count("GRAPH.RO_QUERY"), "another pipeline on the connection has a command queued");
        other.sync();

        cache.clear();
        Response<ResultSet> third = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        GraphTransaction transaction = context.multi();
        transaction.set("k", "v");
        assertEquals("Person", label(third));
        assertEquals(3, poolServer.count("GRAPH.RO_QUERY"), "a transaction on the connection is inside MULTI");
        transaction.discard();

        assertEquals(0, server.count("GRAPH.RO_QUERY"), "nothing was looked up on the busy connection");
    }

    @Test
    void aTransactionLooksUpThroughItsContextWhileExecDecodes() {
        GraphTransaction transaction = context.multi();
        Response<ResultSet> reply = transaction.query("MATCH (n) RETURN n");
        transaction.exec();

        assertEquals("Person", label(reply));
        assertEquals(1, server.count("GRAPH.RO_QUERY"));
        assertEquals(0, poolServer.received().size());
    }

    @Test
    void aDecodeWaitingForThePoolHoldsNoCacheLock() throws Exception {
        // Decoding refreshes the cache under its lock. A decode that waited for the pool while holding that
        // lock would block every thread that holds a pooled connection and needs the lock: a deadlock.
        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> reply = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        context.close();

        ExecutorService decoding = Executors.newSingleThreadExecutor();
        try {
            Future<String> decoded;
            try (Jedis held = graphPool.getResource()) { // the graph's only connection
                decoded = decoding.submit(() -> label(reply));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (graphPool.getNumWaiters() == 0) {
                    assertTrue(System.nanoTime() < deadline, "the decode never waited for the pool");
                    Thread.sleep(5);
                }
                // The decode is waiting for a connection, and the lock of the label list is free.
                assertEquals(
                        "Person",
                        assertTimeoutPreemptively(
                                Duration.ofSeconds(5),
                                () -> cache.getLabel(0, new LabelSource("Person")),
                                "the cache's lock is held by the decode waiting for the pool"));
            }
            assertEquals("Person", decoded.get(10, TimeUnit.SECONDS));
        } finally {
            decoding.shutdownNow();
        }
    }

    @Test
    void aReplyThatNeedsNoLookupBorrowsNothing() {
        // The context holds its pool's only connection and a command is still queued on it, so no other
        // connection can be had. A scalar, or an entity whose label the cache knows, must still decode.
        JedisPoolConfig oneConnection = new JedisPoolConfig();
        oneConnection.setMaxTotal(1);
        oneConnection.setMaxWait(Duration.ofSeconds(2));
        try (JedisPool single = new JedisPool(oneConnection, "127.0.0.1", server.port());
                DriverImpl singleDriver = new DriverImpl(single)) {
            try (GraphContext own = singleDriver.graph("g").getContext()) {
                own.query("MATCH (n) RETURN n"); // learns the label, on the context's connection
                GraphPipeline pipeline = own.pipelined();
                Response<ResultSet> scalar = pipeline.query("RETURN 1");
                Response<ResultSet> node = pipeline.query("MATCH (n) RETURN n");
                pipeline.sync();
                pipeline.set("k", "v");

                Long one = scalar.get().iterator().next().getValue(0);
                assertEquals(1L, one.longValue());
                assertEquals("Person", label(node));
                pipeline.sync();
            }
        }
    }

    // --- when deleteGraph clears the cache --------------------------------------------------------

    @Test
    void aPipelineClearsTheCacheWhenTheDeleteRunsNotWhenItIsQueued() {
        GraphPipeline pipeline = context.pipelined();
        pipeline.deleteGraph();
        assertEquals(0, cache.clears, "queued, not run");

        pipeline.sync();
        assertEquals(1, cache.clears, "ran, although its reply was never read");
    }

    @Test
    void closingAPipelineRunsItsDelete() throws IOException {
        GraphPipeline pipeline = context.pipelined();
        pipeline.deleteGraph();
        pipeline.close();

        assertEquals(1, cache.clears);
    }

    @Test
    void aTransactionClearsTheCacheOnlyWhenExecRunsTheDelete() {
        GraphTransaction discarded = context.multi();
        discarded.deleteGraph();
        discarded.discard();
        assertEquals(0, cache.clears, "a discarded delete never ran");

        handler.abortExec = true;
        GraphTransaction aborted = context.multi();
        aborted.deleteGraph();
        assertNull(aborted.exec(), "a watched key changed");
        assertEquals(0, cache.clears, "an aborted delete never ran");
        handler.abortExec = false;

        handler.deleteReply = FakeRedisServer.error("ERR Invalid graph operation on empty key");
        GraphTransaction failed = context.multi();
        failed.deleteGraph();
        failed.exec();
        assertEquals(0, cache.clears, "a failed delete deleted nothing");

        handler.deleteReply = bulk("Graph removed");
        GraphTransaction executed = context.multi();
        executed.deleteGraph();
        executed.exec();
        assertEquals(1, cache.clears);

        // After exec() Jedis runs a command at once: the delete runs, and clears the cache, right away.
        executed.deleteGraph();
        assertEquals(2, cache.clears);
    }

    // --- which cache decodes a reply queued before a delete ----------------------------------------

    @Test
    void aReplyQueuedBeforeADeleteIsDecodedWithTheCacheAsItWasWhenTheDeleteRan() {
        warmCache("Person");
        handler.labels = Collections.singletonList("Animal"); // the graph as the pipeline recreates it

        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> before = pipeline.query("MATCH (n) RETURN n");
        pipeline.deleteGraph();
        Response<ResultSet> after = pipeline.query("CREATE (n:Animal) RETURN n");
        pipeline.sync();

        assertEquals("Person", label(before), "decoded with the deleted graph's labels");
        assertEquals("Animal", label(after), "decoded with the recreated graph's labels");
    }

    @Test
    void aReplyFromAnEarlierBatchIsDecodedWithTheCacheOfTheGraphThatSentIt() {
        warmCache("Person");
        handler.labels = Collections.singletonList("Animal");

        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> earlier = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        pipeline.deleteGraph();
        Response<ResultSet> later = pipeline.query("CREATE (n:Animal) RETURN n");
        pipeline.sync();

        assertEquals("Person", label(earlier));
        assertEquals("Animal", label(later));
    }

    @Test
    void aReplyBetweenTwoDeletesCanOnlyUseWhatWasCached() {
        // The graph between the deletes existed only while the batch ran, and the client cached none of it.
        warmCache("Person");
        handler.labels = Collections.singletonList("Animal");

        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> before = pipeline.query("MATCH (n) RETURN n");
        pipeline.deleteGraph();
        Response<ResultSet> between = pipeline.query("CREATE (n:B) RETURN n");
        pipeline.deleteGraph();
        Response<ResultSet> after = pipeline.query("CREATE (n:Animal) RETURN n");
        pipeline.sync();

        assertEquals("Person", label(before));
        GraphException e = assertThrows(GraphException.class, between::get);
        assertTrue(e.getMessage().contains("label id 0"), e.getMessage());
        assertEquals("Animal", label(after));
    }

    @Test
    void aLabelTheCacheLackedIsReportedRatherThanLookedUpInAnotherGraph() {
        // Nothing cached: the reply queued before the delete can't be decoded, and looking its id up would
        // find the recreated graph's label instead.
        handler.labels = Collections.singletonList("Animal");

        GraphPipeline pipeline = context.pipelined();
        pipeline.query("MATCH (n) RETURN n");
        pipeline.deleteGraph();
        pipeline.query("CREATE (n:B) RETURN n");
        pipeline.deleteGraph();
        pipeline.query("CREATE (n:Animal) RETURN n");
        List<Object> results = pipeline.syncAndReturnAll();

        assertTrue(results.get(0) instanceof GraphException, String.valueOf(results.get(0)));
        assertTrue(((GraphException) results.get(0)).getMessage().contains("label id 0"));
        assertTrue(results.get(2) instanceof GraphException, "the graph between the deletes existed only briefly");
        assertEquals(
                "Animal", ((Node) ((ResultSet) results.get(4)).iterator().next().getValue(0)).getLabel(0));
        assertEquals(1, server.count("GRAPH.RO_QUERY"), "only the reply after the last delete looked up");
    }

    @Test
    void aReplyReadWhileADeleteIsQueuedLooksNothingUp() throws InterruptedException {
        // A reply from an earlier batch, read after deleteGraph() and a recreate were queued but before they are
        // synced. Jedis may have flushed them to the server already, so the graph may be the new one by then.
        handler.labelsAfterDelete = Collections.singletonList("Animal");
        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> earlier = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        pipeline.deleteGraph();
        // larger than Jedis' output buffer, so the delete before it is flushed to the server
        String padding = String.join("", Collections.nCopies(10_000, "x"));
        pipeline.query("CREATE (n:Animal {padding:'" + padding + "'}) RETURN n");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (server.count("GRAPH.DELETE") == 0) {
            assertTrue(System.nanoTime() < deadline, "the delete never reached the server");
            Thread.sleep(5);
        }

        GraphException e = assertThrows(GraphException.class, () -> label(earlier));
        assertTrue(e.getMessage().contains("label id 0"), e.getMessage());
        pipeline.sync();
    }

    @Test
    void aReplyIsNotDecodedWithNamesTheCacheLearnedAfterItsDeleteWasQueued() throws InterruptedException {
        // The delete and a recreate reach the server, and another context of the same graph then reads the new
        // graph, teaching the shared cache its labels. The reply queued before the delete still must not use them.
        handler.labelsAfterDelete = Collections.singletonList("Animal");
        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> earlier = pipeline.query("MATCH (n) RETURN n");
        pipeline.sync();
        pipeline.deleteGraph();
        String padding = String.join("", Collections.nCopies(10_000, "x"));
        pipeline.query("CREATE (n:Animal {padding:'" + padding + "'}) RETURN n");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        // The server records a command before it handles it, so wait for the handler to have run the delete.
        while (!handler.labels.equals(Collections.singletonList("Animal"))) {
            assertTrue(System.nanoTime() < deadline, "the delete never ran on the server");
            Thread.sleep(5);
        }
        try (Jedis other = pool.getResource()) {
            GraphContextImpl second = new GraphContextImpl(other, cache, "g");
            assertEquals(
                    "Animal",
                    ((Node) second.query("MATCH (n) RETURN n").iterator().next().getValue(0)).getLabel(0));
        }

        GraphException e = assertThrows(GraphException.class, () -> label(earlier));
        assertTrue(e.getMessage().contains("label id 0"), e.getMessage());
        pipeline.sync();
    }

    @Test
    void anExecTheServerRefusesLeavesTheCacheAlone() {
        // EXECABORT: a command failed to queue, so the server ran none of them.
        handler.refuseExec = true;
        GraphTransaction transaction = context.multi();
        transaction.deleteGraph();
        JedisDataException e = assertThrows(JedisDataException.class, transaction::exec);
        assertTrue(e.getMessage().startsWith("EXECABORT"), e.getMessage());
        assertEquals(0, cache.clears);
    }

    @Test
    void anExecThatFailsPartWayClearsTheCache() {
        // The connection broke while EXEC was answered, so the delete may have run.
        warmCache("Person");
        handler.breakExec = true;
        GraphTransaction transaction = context.multi();
        transaction.deleteGraph();
        assertThrows(JedisConnectionException.class, transaction::exec);
        assertEquals(1, cache.clears);
    }

    @Test
    void afterSyncARepliesQueuedBeforeAFailedDeleteIsDecodedAsUsual() {
        // The graph was not deleted, so its labels can still be looked up.
        handler.deleteReply =
                FakeRedisServer.error("NOPERM this user has no permissions to run the 'graph.DELETE' command");

        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> before = pipeline.query("MATCH (n) RETURN n");
        Response<String> delete = pipeline.deleteGraph();
        pipeline.sync();

        assertThrows(JedisDataException.class, delete::get);
        assertEquals("Person", label(before));
        assertEquals(0, cache.clears);
    }

    @Test
    void aTransactionDecodesTheRepliesQueuedBeforeItsDeleteWithTheCacheAsItWasWhenTheDeleteWasQueued() {
        warmCache("Person");
        handler.labels = Collections.singletonList("Animal");

        GraphTransaction transaction = context.multi();
        Response<ResultSet> before = transaction.query("MATCH (n) RETURN n");
        transaction.deleteGraph();
        Response<ResultSet> after = transaction.query("CREATE (n:Animal) RETURN n");
        transaction.exec();

        assertEquals("Person", label(before));
        assertEquals("Animal", label(after));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private void warmCache(String label) {
        assertEquals(label, cache.getLabel(0, new LabelSource(label)));
    }

    private static String label(Response<ResultSet> reply) {
        return ((Node) reply.get().iterator().next().getValue(0)).getLabel(0);
    }

    /** A {@link GraphCache} that counts how often it is cleared. */
    private static final class CountingCache extends GraphCache {
        int clears;

        @Override
        public void clear() {
            clears++;
            super.clear();
        }
    }

    /**
     * Answers like FalkorDB: a node labeled with id 0 for any query, the {@link #labels} for {@code CALL
     * db.labels()}, and {@code QUEUED} inside {@code MULTI} until {@code EXEC} returns the replies. Each client
     * connection is served on its own thread, hence the thread-local transaction.
     */
    private static final class Server implements Function<List<String>, String> {
        volatile List<String> labels = Collections.singletonList("Person");
        volatile String deleteReply = bulk("Graph removed");
        /** Answer {@code EXEC} as when a watched key changed. */
        volatile boolean abortExec;
        /** The {@link #labels} once a delete has run, standing for a graph recreated right after it. */
        volatile List<String> labelsAfterDelete;
        /** Answer {@code EXEC} with something that isn't RESP, which breaks the connection. */
        volatile boolean breakExec;
        /** Answer {@code EXEC} with {@code EXECABORT}, as after a command that failed to queue. */
        volatile boolean refuseExec;

        private final ThreadLocal<List<String>> transaction = new ThreadLocal<>();

        @Override
        public String apply(List<String> command) {
            if (FakeRedisServer.startsWith(command, "MULTI")) {
                transaction.set(new ArrayList<>());
                return FakeRedisServer.ok();
            }
            if (FakeRedisServer.startsWith(command, "EXEC")) {
                List<String> replies = transaction.get();
                transaction.remove();
                if (breakExec) {
                    return "?\r\n";
                }
                if (refuseExec) {
                    return FakeRedisServer.error("EXECABORT Transaction discarded because of previous errors.");
                }
                return abortExec ? "*-1\r\n" : array(replies.toArray(new String[0]));
            }
            if (FakeRedisServer.startsWith(command, "DISCARD")) {
                transaction.remove();
                return FakeRedisServer.ok();
            }
            String reply = reply(command);
            List<String> queued = transaction.get();
            if (queued != null) {
                queued.add(reply);
                return "+QUEUED\r\n";
            }
            return reply;
        }

        private String reply(List<String> command) {
            if (FakeRedisServer.startsWith(command, "GRAPH.QUERY")
                    && command.get(2).equals("RETURN 1")) {
                String header = array(array(integer(1), bulk("1")));
                String one = array(integer(3), integer(1)); // VALUE_INTEGER
                return array(header, array(array(one)), array(bulk("Cached execution: 0")));
            }
            if (FakeRedisServer.startsWith(command, "GRAPH.QUERY")) {
                String header = array(array(integer(1), bulk("n")));
                String node = array(integer(8), array(integer(7), array(integer(0)), array())); // VALUE_NODE, label 0
                return array(header, array(array(node)), array(bulk("Cached execution: 0")));
            }
            if (FakeRedisServer.startsWith(command, "GRAPH.RO_QUERY")) {
                List<String> rows = new ArrayList<>();
                for (String label : labels) {
                    rows.add(array(array(integer(2), bulk(label)))); // VALUE_STRING
                }
                String header = array(array(integer(1), bulk("label")));
                return array(header, array(rows.toArray(new String[0])), array(bulk("Cached execution: 0")));
            }
            if (FakeRedisServer.startsWith(command, "GRAPH.DELETE")) {
                if (labelsAfterDelete != null) {
                    labels = labelsAfterDelete;
                }
                return deleteReply;
            }
            return FakeRedisServer.ok();
        }
    }

    /** A graph that answers {@code CALL db.labels()} without a server, to fill the cache. */
    private static final class LabelSource extends AbstractGraph {
        private final List<String> labels;

        LabelSource(String... labels) {
            this.labels = Arrays.asList(labels);
        }

        @Override
        protected ResultSet sendReadOnlyQuery(String preparedQuery) {
            assertEquals("CALL db.labels()", preparedQuery);
            List<List<Object>> rows = new ArrayList<>();
            for (String label : labels) {
                rows.add(Collections.singletonList(Arrays.asList(2L, SafeEncoder.encode(label))));
            }
            List<Object> header = Collections.singletonList(Arrays.asList(1L, SafeEncoder.encode("label")));
            return new ResultSetImpl(Arrays.asList(header, rows, new ArrayList<>()), this, new GraphCache());
        }

        @Override
        protected ResultSet sendQuery(String preparedQuery) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ResultSet sendQuery(String preparedQuery, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ResultSet sendReadOnlyQuery(String preparedQuery, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ResultSet sendProfile(String preparedQuery) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected List<String> sendExplain(String preparedQuery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String copyGraph(String destinationGraphId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String deleteGraph() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }
}
