package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCache;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Response;

/**
 * Closing a {@link GraphContext} hands its connection back to the pool, which lends it to the next borrower. That
 * borrower must get a clean connection, whatever the context's transactions and pipelines left on it: not one still
 * inside {@code MULTI}, not one with replies nobody read, and not one still watching keys.
 *
 * <p>Each test runs on a pool of one connection, so the next borrower gets the very connection the context had,
 * unless the pool destroyed it. The fake server keeps each connection's {@code MULTI} and {@code WATCH} state the way
 * Redis does.
 */
class GraphContextCloseTest {

    /** The reply to a write: statistics only, no result rows. */
    private static final String WRITE_REPLY =
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.bulk("Nodes created: 1")));

    /** The reply to {@code MATCH (n) RETURN n}: one node, whose label has id 0. */
    private static final String NODE_REPLY = FakeRedisServer.array(
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.integer(1), FakeRedisServer.bulk("n"))),
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.array(
                    FakeRedisServer.integer(8), // VALUE_NODE: id 7, label 0, no properties
                    FakeRedisServer.array(
                            FakeRedisServer.integer(7),
                            FakeRedisServer.array(FakeRedisServer.integer(0)),
                            FakeRedisServer.array())))),
            FakeRedisServer.array(FakeRedisServer.bulk("Cached execution: 0")));

    /** The state of each connection the server accepted, oldest first. */
    private final List<ConnectionState> connections = new CopyOnWriteArrayList<>();

    private FakeRedisServer server;
    private Driver driver;
    /** Lends the pool's only connection to the next borrower. */
    private Supplier<Jedis> nextBorrower;

    @BeforeEach
    void setUp() {
        server = new FakeRedisServer(() -> {
            ConnectionState state = new ConnectionState();
            connections.add(state);
            return state;
        });
        driver = FalkorDB.builder()
                .host("127.0.0.1")
                .port(server.port())
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .poolMaxTotal(1)
                .poolMaxIdle(1)
                .build();
        nextBorrower = driver::getConnection;
    }

    @AfterEach
    void tearDown() {
        driver.close();
        server.close();
    }

    @Test
    void closingMidTransactionDiscardsIt() {
        GraphContext context = driver.graph("g").getContext();
        GraphTransaction transaction = context.multi();
        transaction.query("CREATE ()");
        context.close();

        assertEquals(1, server.count("DISCARD"), "close() must discard the open transaction");
        assertNextBorrowerGetsTheSameCleanConnection();
        assertNotNull(transaction); // held until here, so close() found it however it tracks transactions
    }

    @Test
    void closingWithAnUnsyncedPipelineReadsItsReplies() {
        GraphContext context = driver.graph("g").getContext();
        GraphPipeline pipeline = context.pipelined();
        Response<String> set = pipeline.set("key", "value");
        Response<ResultSet> created = pipeline.query("CREATE ()");
        context.close();

        assertNextBorrowerGetsTheSameCleanConnection();
        // close() read the replies, so they can be read now
        assertEquals("OK", set.get());
        assertEquals(1, created.get().getStatistics().nodesCreated());
    }

    @Test
    void closingEndsEveryBatchTheCallerStillHolds() {
        GraphContext context = driver.graph("g").getContext();
        GraphTransaction transaction = context.multi();
        transaction.set("key", "value");
        GraphPipeline later = context.pipelined(); // created after the transaction, so the transaction isn't the latest
        context.close();

        assertEquals(1, server.count("DISCARD"), "close() must discard a transaction created before the latest batch");
        assertNextBorrowerGetsTheSameCleanConnection();
        assertNotNull(transaction);
        assertNotNull(later);
    }

    @Test
    void closingEndsTheLatestBatchEvenIfTheCallerDroppedIt() {
        GraphContext context = driver.graph("g").getContext();
        WeakReference<GraphTransaction> dropped = openTransaction(context);
        collectUnlessHeld(dropped);
        context.close();

        assertEquals(1, server.count("DISCARD"), "close() must discard the transaction the caller dropped");
        assertNextBorrowerGetsTheSameCleanConnection();
    }

    @Test
    void closingEndsAnUnfinishedEarlierBatchEvenIfTheCallerDroppedIt() {
        GraphContext context = driver.graph("g").getContext();
        WeakReference<GraphPipeline> dropped = unsyncedPipeline(context);
        GraphPipeline later = context.pipelined(); // the dropped pipeline still awaits its reply
        collectUnlessHeld(dropped);
        context.close();

        assertNextBorrowerGetsTheSameCleanConnection();
        assertNotNull(later);
    }

    @Test
    void closingEndsAnEarlierBatchUsedAgainAfterALaterOneWasCreated() {
        GraphContext context = driver.graph("g").getContext();
        GraphPipeline earlier = context.pipelined();
        earlier.set("key", "value");
        earlier.sync();
        GraphPipeline later = context.pipelined(); // the earlier one has nothing pending now
        WeakReference<GraphPipeline> dropped = queueAndDrop(earlier);
        earlier = null; // dropped: an exception skipped its sync()
        collectUnlessHeld(dropped);
        context.close();

        assertNextBorrowerGetsTheSameCleanConnection();
        assertNotNull(later);
    }

    @Test
    void closingDropsTheConnectionIfAnOlderBatchWasCollected() {
        try (JedisPool pool = new JedisPool(poolOfOne(), "127.0.0.1", server.port())) {
            nextBorrower = pool::getResource;
            CountingCache cache = new CountingCache();
            GraphContextImpl context = new GraphContextImpl(pool.getResource(), cache, "g");
            WeakReference<GraphPipeline> dropped = unsyncedPipeline(context);
            for (int i = 0; i < GraphContextImpl.HELD_BATCHES; i++) {
                context.pipelined(); // the dropped pipeline is no longer among the ones the context holds
            }
            collect(dropped);
            context.close();

            // The context can't know what the collected pipeline left on the connection, so the pool drops it.
            assertNextBorrowerGetsANewCleanConnection();
            assertEquals(1, cache.clears, "the collected pipeline may have deleted the graph");
        }
    }

    @Test
    void closingWithSeveralBatchesAwaitingRepliesDropsTheConnection() {
        GraphContext context = driver.graph("g").getContext();
        GraphPipeline pipeline = context.pipelined();
        pipeline.set("key", "value");
        GraphTransaction transaction = context.multi(); // its replies follow the pipeline's
        transaction.set("key", "value");
        context.close();

        // No order of reading hands each batch its own replies, so close() reads none and the pool drops the
        // connection: the next borrower gets a new one.
        assertEquals(0, server.count("DISCARD"), "close() must not read replies it can't attribute");
        assertNextBorrowerGetsANewCleanConnection();
        assertNotNull(pipeline);
        assertNotNull(transaction);
    }

    @Test
    void closingReadsTheRepliesAwaitedBeforeCleaningUpAnythingElse() {
        GraphContext context = driver.graph("g").getContext();
        GraphTransaction transaction = context.multi();
        transaction.exec();
        ((GraphTransactionImpl) transaction).watch("key"); // close() unwatches it, a command of its own
        GraphPipeline pipeline = context.pipelined();
        Response<ResultSet> created = pipeline.query("CREATE ()");
        context.close();

        // Unwatching first would have read the pipeline's reply as UNWATCH's, and failed.
        assertEquals(1, created.get().getStatistics().nodesCreated());
        assertFalse(connections.get(0).watching, "close() must UNWATCH what the transaction watched");
        assertNextBorrowerGetsTheSameCleanConnection();
    }

    @Test
    void closingWithSeveralBatchesAwaitingRepliesClearsTheCacheIfOneQueuedADelete() {
        try (JedisPool pool = new JedisPool(poolOfOne(), "127.0.0.1", server.port())) {
            nextBorrower = pool::getResource;
            CountingCache cache = new CountingCache();
            GraphContextImpl context = new GraphContextImpl(pool.getResource(), cache, "g");
            GraphPipeline pipeline = context.pipelined();
            pipeline.deleteGraph(); // sent: it may run, but its reply is never read
            GraphTransaction transaction = context.multi();
            transaction.set("key", "value");
            context.close();

            assertEquals(1, cache.clears, "the abandoned delete may have run");
            assertNextBorrowerGetsANewCleanConnection();
            assertNotNull(pipeline);
            assertNotNull(transaction);
        }
    }

    @Test
    void closingUnwatchesWhatTheTransactionWatchedAfterExec() {
        GraphContext context = driver.graph("g").getContext();
        GraphTransaction transaction = context.multi();
        transaction.exec();
        // GraphTransactionImpl is a Jedis Transaction, whose WATCH Jedis' pool doesn't see. After exec() it runs at
        // once.
        ((GraphTransactionImpl) transaction).watch("key");
        context.close();

        assertFalse(connections.get(0).watching, "close() must UNWATCH what the transaction watched");
        assertNextBorrowerGetsTheSameCleanConnection();
    }

    /**
     * A guard rather than a fix: a key the context itself watched is already unwatched when the connection goes back
     * to the pool, by Jedis' {@code JedisPool.returnResource()}. That is why {@code close()} sends no {@code UNWATCH}
     * of its own. This keeps failing if the connection source ever stops doing it.
     */
    @Test
    void closingUnwatchesWhatTheContextWatched() {
        GraphContext context = driver.graph("g").getContext();
        context.watch("key");
        context.close();

        assertFalse(connections.get(0).watching, "the context's WATCH must not outlive it");
        assertNextBorrowerGetsTheSameCleanConnection();
    }

    /** A guard: batches that were ended already cost {@code close()} no command. */
    @Test
    void closingSendsNothingForBatchesAlreadyEnded() {
        GraphContext context = driver.graph("g").getContext();
        GraphTransaction transaction = context.multi();
        transaction.set("key", "value");
        transaction.exec();
        GraphPipeline pipeline = context.pipelined();
        pipeline.set("key", "value");
        pipeline.sync();
        int sent = server.received().size();
        context.close();

        assertEquals(sent, server.received().size(), "close() sent " + server.received());
    }

    @Test
    void aClosedContextRefusesNewBatches() {
        GraphContext context = driver.graph("g").getContext();
        context.close();

        IllegalStateException multi = assertThrows(IllegalStateException.class, context::multi);
        assertEquals("the context is closed", multi.getMessage());
        IllegalStateException pipelined = assertThrows(IllegalStateException.class, context::pipelined);
        assertEquals("the context is closed", pipelined.getMessage());
        assertEquals(0, server.count("MULTI"), "a closed context must not send MULTI on a connection it gave back");
        assertNextBorrowerGetsTheSameCleanConnection();
    }

    @Test
    void aConnectionThatCannotBeCleanedIsNotLentAgain() {
        GraphContext context = driver.graph("g").getContext();
        GraphTransaction transaction = context.multi();
        transaction.set("key", "value");
        server.dropClients(); // the DISCARD close() sends can't get a reply
        context.close(); // and close() still doesn't throw

        assertNextBorrowerGetsANewCleanConnection();
        assertNotNull(transaction);
    }

    @Test
    void closingAfterAFailedSyncAndReturnAllDropsTheConnection() {
        // No read deadline, as the driver's default: Jedis' own default of 2 s would end the wait.
        try (JedisPool pool = new JedisPool(poolOfOne(), "127.0.0.1", server.port(), 0)) {
            nextBorrower = pool::getResource;
            GraphContextImpl context = new GraphContextImpl(pool.getResource(), new FailingCache(), "g");
            GraphPipeline pipeline = context.pipelined();
            pipeline.query("MATCH (n) RETURN n"); // a node, whose label decoding looks up
            pipeline.query("CREATE ()");
            // Jedis reads both replies, then fails building the first: the second response stays queued, with no
            // reply left on the connection for it.
            assertThrows(LookupFailed.class, pipeline::syncAndReturnAll);

            // So reading for the queued response would wait forever.
            assertTimeoutPreemptively(
                    Duration.ofSeconds(10), context::close, "close() waited for a reply that never comes");
            assertNextBorrowerGetsANewCleanConnection();
        }
    }

    @Test
    void closingAPipelineAfterAFailedSyncAndReturnAllDoesNotWait() {
        // No read deadline, as the driver's default.
        try (JedisPool pool = new JedisPool(poolOfOne(), "127.0.0.1", server.port(), 0)) {
            nextBorrower = pool::getResource;
            GraphContextImpl context = new GraphContextImpl(pool.getResource(), new FailingCache(), "g");
            GraphPipeline pipeline = context.pipelined();
            pipeline.query("MATCH (n) RETURN n");
            pipeline.query("CREATE ()");
            assertThrows(LookupFailed.class, pipeline::syncAndReturnAll);

            // As try-with-resources on the pipeline does, before the context's own close().
            assertTimeoutPreemptively(
                    Duration.ofSeconds(10), pipeline::close, "the pipeline waited for a reply that never comes");
            context.close();
            assertNextBorrowerGetsANewCleanConnection();
        }
    }

    /**
     * A guard: a delete queued behind a reply that fails to decode may have run, whether {@code syncAndReturnAll()}
     * throws (the failure escapes Jedis' loop, leaving the delete's response unbuilt) or keeps the failure in its
     * result. Neither that nor {@code close()} may leave the schema cache as it was.
     */
    @Test
    void aDeleteQueuedBehindAReplyThatFailsToDecodeClearsTheCache() {
        try (JedisPool pool = new JedisPool(poolOfOne(), "127.0.0.1", server.port(), 0)) {
            nextBorrower = pool::getResource;
            CountingCache cache = new CountingCache();
            GraphContextImpl context = new GraphContextImpl(pool.getResource(), cache, "g");
            GraphPipeline pipeline = context.pipelined();
            pipeline.query("RETURN 'malformed'"); // answered with an integer, which fails to decode
            pipeline.deleteGraph(); // runs on the server
            try {
                pipeline.syncAndReturnAll();
            } catch (RuntimeException failedPartWay) {
                // the decode failure escaped; the delete's response was never built
            }
            assertTimeoutPreemptively(Duration.ofSeconds(10), context::close);

            assertEquals(1, cache.clears, "the delete may have run");
            assertNextBorrowerGetsACleanConnection();
        }
    }

    @Test
    void endingABatchThatFailsAfterReadingADeleteClearsTheCache() {
        try (JedisPool pool = new JedisPool(poolOfOne(), "127.0.0.1", server.port())) {
            nextBorrower = pool::getResource;
            CountingCache cache = new CountingCache();
            GraphContextImpl context = new GraphContextImpl(pool.getResource(), cache, "g");
            GraphPipeline pipeline = context.pipelined();
            pipeline.deleteGraph();
            // The delete ran, but its reply is out of step: recording that it ran fails after the reply was read.
            connections.get(0).deleteReply = FakeRedisServer.integer(1);
            context.close();

            assertEquals(1, cache.clears, "the delete may have run");
            assertNextBorrowerGetsACleanConnection(); // a new one, unless the failure was kept in the reply
            assertNotNull(pipeline);
        }
    }

    @Test
    void aReplyOutOfStepWhileCleaningDropsTheConnection() {
        GraphContext context = driver.graph("g").getContext();
        GraphTransaction transaction = context.multi();
        transaction.set("key", "value");
        // DISCARD answered with an integer: Jedis fails reading it with a ClassCastException, not a JedisException.
        connections.get(0).discardReply = FakeRedisServer.integer(1);
        context.close(); // doesn't throw

        assertNextBorrowerGetsANewCleanConnection();
        assertNotNull(transaction);
    }

    /** The next borrower gets the connection the context had, and it answers that borrower's own command. */
    private void assertNextBorrowerGetsTheSameCleanConnection() {
        assertNextBorrowerGetsACleanConnection();
        assertEquals(1, connections.size(), "the pool replaced the connection instead of lending it again");
    }

    /** The pool destroyed the context's connection, and the next borrower gets a new one that answers it. */
    private void assertNextBorrowerGetsANewCleanConnection() {
        assertNextBorrowerGetsACleanConnection();
        assertEquals(2, connections.size(), "the pool lent the context's connection again");
    }

    /** The next borrower's own command gets its own reply, not one left on the connection or a {@code QUEUED}. */
    private void assertNextBorrowerGetsACleanConnection() {
        try (Jedis next = nextBorrower.get()) {
            assertEquals("next", next.echo("next"), "the next borrower read a reply that wasn't its own");
        }
        ConnectionState latest = connections.get(connections.size() - 1);
        assertFalse(latest.inMulti, "the next borrower's command was queued in a transaction");
    }

    /** Opens a transaction with a queued command, and keeps only a weak reference to it. */
    private static WeakReference<GraphTransaction> openTransaction(GraphContext context) {
        GraphTransaction transaction = context.multi();
        transaction.set("key", "value");
        return new WeakReference<>(transaction);
    }

    /** Opens a pipeline with a queued command, and keeps only a weak reference to it. */
    private static WeakReference<GraphPipeline> unsyncedPipeline(GraphContext context) {
        GraphPipeline pipeline = context.pipelined();
        pipeline.set("key", "value");
        return new WeakReference<>(pipeline);
    }

    /** Queues a command on {@code pipeline}, and keeps only a weak reference to it. */
    private static WeakReference<GraphPipeline> queueAndDrop(GraphPipeline pipeline) {
        pipeline.set("key", "value");
        return new WeakReference<>(pipeline);
    }

    /** Collects what {@code dropped} refers to, which nothing holds any more. */
    private static void collect(WeakReference<?> dropped) {
        for (int i = 0; i < 10 && dropped.get() != null; i++) {
            System.gc();
        }
        assertNull(dropped.get(), "the dropped batch wasn't collected, so this test would prove nothing");
    }

    private static GenericObjectPoolConfig<Jedis> poolOfOne() {
        GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(1);
        config.setMaxIdle(1);
        config.setMaxWait(Duration.ofSeconds(5));
        return config;
    }

    /** Thrown by {@link FailingCache}: an {@link Error}, which decoding lets through as Jedis' batch loops do. */
    private static final class LookupFailed extends Error {
        private static final long serialVersionUID = 1L;

        LookupFailed() {
            super("the label lookup failed");
        }
    }

    /** A cache whose label lookup fails, so a reply that names a label fails to decode. */
    private static final class FailingCache extends GraphCache {
        @Override
        public String getLabel(int index, com.falkordb.Graph graph) {
            throw new LookupFailed();
        }
    }

    /** A cache that counts how often it is cleared. */
    private static final class CountingCache extends GraphCache {
        int clears;

        @Override
        public void clear() {
            clears++;
            super.clear();
        }
    }

    /**
     * Gives the collector the chance to take what {@code dropped} refers to, which the context must not let it: a
     * context that holds it only weakly loses it here.
     */
    private static void collectUnlessHeld(WeakReference<?> dropped) {
        // Without a collection, a context that held the batch only weakly would pass too: make sure one ran.
        WeakReference<Object> unreachable = new WeakReference<>(new Object());
        for (int i = 0; i < 10 && (unreachable.get() != null || dropped.get() != null); i++) {
            System.gc();
        }
        assertNull(unreachable.get(), "no garbage collection ran, so this test would prove nothing");
    }

    /**
     * Answers one connection's commands the way Redis does: {@code MULTI} queues what follows until {@code EXEC} or
     * {@code DISCARD}, and {@code WATCH} holds until {@code UNWATCH}, {@code EXEC} or {@code DISCARD}.
     */
    private static final class ConnectionState implements Function<List<String>, String> {
        volatile boolean inMulti;
        volatile boolean watching;
        /** What {@code DISCARD} answers in a transaction; {@code +OK} unless a test makes it misbehave. */
        volatile String discardReply = FakeRedisServer.ok();
        /** What {@code GRAPH.DELETE} answers; statistics unless a test makes it misbehave. */
        volatile String deleteReply = FakeRedisServer.bulk("Graph removed, internal execution time: 0.1 milliseconds");

        private final List<String> queued = new ArrayList<>();

        /** Answers one command, queuing it instead while the connection is inside {@code MULTI}. */
        @Override
        public synchronized String apply(List<String> command) {
            String name = command.get(0).toUpperCase(Locale.ROOT);
            switch (name) {
                case "MULTI":
                    if (inMulti) {
                        return FakeRedisServer.error("ERR MULTI calls can not be nested");
                    }
                    inMulti = true;
                    return FakeRedisServer.ok();
                case "EXEC":
                    if (!inMulti) {
                        return FakeRedisServer.error("ERR EXEC without MULTI");
                    }
                    String replies = FakeRedisServer.array(queued.toArray(new String[0]));
                    endTransaction();
                    return replies;
                case "DISCARD":
                    if (!inMulti) {
                        return FakeRedisServer.error("ERR DISCARD without MULTI");
                    }
                    endTransaction();
                    return discardReply;
                case "WATCH":
                    if (inMulti) {
                        return FakeRedisServer.error("ERR WATCH inside MULTI is not allowed");
                    }
                    watching = true;
                    return FakeRedisServer.ok();
                case "UNWATCH":
                    watching = false;
                    return FakeRedisServer.ok();
                default:
                    String reply = "GRAPH.DELETE".equals(name) ? deleteReply : answer(name, command);
                    if (inMulti) {
                        queued.add(reply);
                        return "+QUEUED\r\n";
                    }
                    return reply;
            }
        }

        /** {@code EXEC} or {@code DISCARD}: the connection leaves {@code MULTI}, and its watches end. */
        private void endTransaction() {
            inMulti = false;
            watching = false;
            queued.clear();
        }

        /** The reply to a command outside {@code MULTI}. */
        private static String answer(String name, List<String> command) {
            switch (name) {
                case "ECHO":
                    return FakeRedisServer.bulk(command.get(1));
                case "PING":
                    return "+PONG\r\n";
                case "GRAPH.QUERY":
                    if (command.get(2).contains("malformed")) {
                        return FakeRedisServer.integer(1); // where a graph reply belongs
                    }
                    return command.get(2).startsWith("MATCH") ? NODE_REPLY : WRITE_REPLY;
                default:
                    return FakeRedisServer.ok();
            }
        }
    }
}
