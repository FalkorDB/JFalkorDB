package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.Reply;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * {@link GraphContext.Pipeline}, {@link GraphContext.Transaction} and their {@link Reply}s, against a fake server
 * that answers the way Redis does. The pool has one connection, so a context holds the only one.
 */
class ContextBatchTest {

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

    private final List<Connection> connections = Collections.synchronizedList(new ArrayList<>());
    private FakeRedisServer server;
    private Driver driver;
    private GraphContext context;

    @BeforeEach
    void setUp() {
        server = new FakeRedisServer(() -> {
            Connection connection = new Connection();
            connections.add(connection);
            return connection;
        });
        driver = FalkorDB.builder()
                .host("127.0.0.1")
                .port(server.port())
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .poolMaxTotal(1)
                .poolMaxIdle(1)
                .build();
        context = driver.graph("g").getContext();
    }

    @AfterEach
    void tearDown() {
        context.close();
        driver.close();
        server.close();
    }

    // --- Reply semantics -----------------------------------------------------------------------------------------

    @Test
    void aPipelineReplyCantBeReadBeforeTheReplyIsRead() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<ResultSet> created = pipeline.query("CREATE ()");

        IllegalStateException e = assertThrows(IllegalStateException.class, created::get);
        assertTrue(e.getMessage().contains("sync()"), e.getMessage());
        pipeline.sync();
        assertEquals(1, created.get().getStatistics().nodesCreated());
    }

    @Test
    void aReplyQueuedAfterASyncWaitsForTheNextOne() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> first = pipeline.sendCommand("SET", "k", "v");
        pipeline.sync();
        Reply<Object> second = pipeline.sendCommand("GET", "k");

        assertEquals("OK", first.get());
        assertThrows(IllegalStateException.class, second::get);
        assertEquals(Collections.singletonList("v"), pipeline.syncAndReturnAll());
        assertEquals("v", second.get());
    }

    @Test
    void aTransactionReplyCantBeReadBeforeExec() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<ResultSet> created = transaction.query("CREATE ()");

        IllegalStateException e = assertThrows(IllegalStateException.class, created::get);
        assertTrue(e.getMessage().contains("exec()"), e.getMessage());
        assertEquals(1, transaction.exec().size());
        assertEquals(1, created.get().getStatistics().nodesCreated());
    }

    @Test
    void aReplyOfAnAbortedTransactionSaysSo() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<ResultSet> created = transaction.query("CREATE ()");
        connections.get(0).abortExec = true; // as when a watched key changed

        assertNull(transaction.exec());
        IllegalStateException e = assertThrows(IllegalStateException.class, created::get);
        assertTrue(e.getMessage().contains("aborted"), e.getMessage());
    }

    @Test
    void aReplyOfADiscardedTransactionSaysSo() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<Object> set = transaction.sendCommand("SET", "k", "v");
        transaction.discard();

        IllegalStateException e = assertThrows(IllegalStateException.class, set::get);
        assertTrue(e.getMessage().contains("discarded"), e.getMessage());
    }

    @Test
    void aRefusedTransactionThrowsAGraphExceptionAndItsRepliesSayNothingRan() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<Object> set = transaction.sendCommand("SET", "k", "v");
        connections.get(0).refuseExec = true;

        GraphException e = assertThrows(GraphException.class, transaction::exec);
        assertTrue(e.getMessage().startsWith("EXECABORT"), e.getMessage());
        IllegalStateException unread = assertThrows(IllegalStateException.class, set::get);
        assertTrue(unread.getMessage().contains("refused"), unread.getMessage());
    }

    @Test
    void aServerErrorIsTheSameGraphExceptionOnEveryReadAndInTheList() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> failed = pipeline.sendCommand("FAIL");
        List<Object> results = pipeline.syncAndReturnAll();

        GraphException first = assertThrows(GraphException.class, failed::get);
        assertEquals("ERR failed on purpose", first.getMessage());
        assertInstanceOf(JedisDataException.class, first.getCause());
        assertSame(first, assertThrows(GraphException.class, failed::get));
        assertSame(first, results.get(0));
    }

    @Test
    void aServerErrorInATransactionIsTheSameGraphExceptionOnEveryReadAndInTheList() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<Object> failed = transaction.sendCommand("FAIL");
        List<Object> results = transaction.exec();

        GraphException first = assertThrows(GraphException.class, failed::get);
        assertSame(first, assertThrows(GraphException.class, failed::get));
        assertSame(first, results.get(0));
    }

    @Test
    void aGraphReplyThatFailsToDecodeThrowsTheSameExceptionOnEveryReadAndIsInTheList() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<ResultSet> malformed = pipeline.query("CREATE malformed");
        Reply<Object> after = pipeline.sendCommand("SET", "k", "v");
        List<Object> results = pipeline.syncAndReturnAll();

        RuntimeException first = assertThrows(RuntimeException.class, malformed::get);
        assertSame(first, assertThrows(RuntimeException.class, malformed::get));
        assertSame(first, results.get(0));
        assertEquals("OK", after.get(), "the replies after it are still read");
        assertEquals("OK", results.get(1));
    }

    @Test
    void aGraphReplyThatFailsToDecodeInATransactionThrowsTheSameExceptionOnEveryRead() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<ResultSet> malformed = transaction.query("CREATE malformed");
        List<Object> results = transaction.exec();

        RuntimeException first = assertThrows(RuntimeException.class, malformed::get);
        assertSame(first, assertThrows(RuntimeException.class, malformed::get));
        assertSame(first, results.get(0));
    }

    @Test
    void sendCommandDecodesTheReplyShapes() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> status = pipeline.sendCommand("SET", "k", "v");
        Reply<Object> bulk = pipeline.sendCommand("GET", "k");
        Reply<Object> nil = pipeline.sendCommand("GET", "missing");
        Reply<Object> integer = pipeline.sendCommand("INCR", "n");
        Reply<Object> array = pipeline.sendCommand("LRANGE", "list", "0", "-1");
        Reply<Object> nilArray = pipeline.sendCommand("NILARRAY");
        pipeline.sync();

        assertEquals("OK", status.get());
        assertEquals("v", bulk.get());
        assertNull(nil.get());
        assertEquals(1L, integer.get());
        List<?> elements = (List<?>) array.get();
        assertEquals(Arrays.asList("a", 2L, null, Arrays.asList("é")), elements.subList(0, 4));
        GraphException nested = assertInstanceOf(GraphException.class, elements.get(4));
        assertEquals("ERR nested", nested.getMessage());
        assertNull(nilArray.get());
    }

    @Test
    void sendBinaryCommandKeepsStringsAsBytes() {
        GraphContext.Transaction transaction = context.transaction();
        byte[] value = {(byte) 0xff, 0, 'x'};
        Reply<Object> set = transaction.sendBinaryCommand("SET", "k".getBytes(StandardCharsets.UTF_8), value);
        Reply<Object> array = transaction.sendBinaryCommand("LRANGE", bytes("list"), bytes("0"), bytes("-1"));
        transaction.exec();

        assertArrayEquals(bytes("OK"), (byte[]) set.get());
        List<?> elements = (List<?>) array.get();
        assertArrayEquals(bytes("a"), (byte[]) elements.get(0));
        assertArrayEquals(bytes("é"), (byte[]) ((List<?>) elements.get(3)).get(0));
        List<String> sent = server.received().get(server.received().size() - 3); // SET, LRANGE, EXEC
        assertEquals("SET", sent.get(0));
        assertEquals(3, sent.size());
    }

    @Test
    void waitReplicasRepliesWithACount() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Long> acknowledged = pipeline.waitReplicas(1, 100);
        pipeline.sync();

        assertEquals(0L, acknowledged.get());
        assertEquals(Arrays.asList("WAIT", "1", "100"), last("WAIT"));
    }

    @Test
    void explainRepliesWithThePlanLines() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<List<String>> plan = pipeline.explain("MATCH (n) RETURN n");
        pipeline.sync();

        assertEquals(Arrays.asList("Results", "    Project"), plan.get());
    }

    // --- After the batch or the context ended --------------------------------------------------------------------

    @Test
    void aTransactionTakesNoCommandAfterExecOrDiscard() {
        GraphContext.Transaction ran = context.transaction();
        ran.exec();
        int sent = server.received().size();
        assertThrows(IllegalStateException.class, () -> ran.sendCommand("SET", "k", "v"));
        assertThrows(IllegalStateException.class, () -> ran.query("CREATE ()"));
        assertThrows(IllegalStateException.class, ran::deleteGraph);
        assertThrows(IllegalStateException.class, ran::exec);
        assertThrows(IllegalStateException.class, ran::discard);
        assertEquals(sent, server.received().size(), "an ended transaction sent " + server.received());

        GraphContext.Transaction discarded = context.transaction();
        discarded.discard();
        assertThrows(IllegalStateException.class, () -> discarded.sendCommand("SET", "k", "v"));
        assertThrows(IllegalStateException.class, discarded::exec);
        discarded.close(); // nothing to do
        assertEquals(sent + 2, server.received().size(), "only MULTI and DISCARD: " + server.received());
    }

    @Test
    void nothingCanBeQueuedOnceTheContextIsClosed() {
        GraphContext.Pipeline pipeline = context.pipeline();
        GraphContext.Transaction transaction = context.transaction();
        transaction.close(); // so the pipeline is the only one that could await replies
        context.close();
        int sent = server.received().size();

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> pipeline.query("CREATE ()"));
        assertEquals("the context is closed", e.getMessage());
        assertThrows(IllegalStateException.class, () -> pipeline.sendCommand("SET", "k", "v"));
        assertThrows(IllegalStateException.class, () -> transaction.sendCommand("SET", "k", "v"));
        assertThrows(IllegalStateException.class, transaction::exec);
        assertEquals(sent, server.received().size(), "sent on a connection given back: " + server.received());
    }

    @Test
    void aPipelineTheContextsCloseReadCanStillBeRead() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> set = pipeline.sendCommand("SET", "k", "v");
        context.close();

        assertEquals("OK", set.get());
        pipeline.sync(); // nothing left to read
        assertEquals(Collections.emptyList(), pipeline.syncAndReturnAll());
        pipeline.close();
    }

    @Test
    void aTransactionTheContextsCloseDiscardedSaysSo() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<Object> set = transaction.sendCommand("SET", "k", "v");
        context.close();

        IllegalStateException e = assertThrows(IllegalStateException.class, set::get);
        assertTrue(e.getMessage().contains("discarded"), e.getMessage());
        IllegalStateException exec = assertThrows(IllegalStateException.class, transaction::exec);
        assertEquals("the context is closed", exec.getMessage());
        transaction.close(); // nothing to do
        assertEquals(1, server.count("DISCARD"));
    }

    @Test
    void aPipelineWhoseRepliesTheContextCouldntReadSaysSo() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> set = pipeline.sendCommand("SET", "k", "v");
        com.falkordb.GraphPipeline old = context.pipelined(); // the deprecated API isn't kept from interleaving
        old.set("k", "v"); // two batches await replies: the context's close reads none
        context.close();

        IllegalStateException e = assertThrows(IllegalStateException.class, set::get);
        assertEquals("the context was closed before this reply was read", e.getMessage());
        assertThrows(IllegalStateException.class, pipeline::sync);
        assertThrows(IllegalStateException.class, pipeline::syncAndReturnAll);
        pipeline.close(); // nothing to do
        assertTrue(old != null);
    }

    @Test
    void closingAPipelineReadsItsReplies() {
        Reply<Object> set;
        try (GraphContext.Pipeline pipeline = context.pipeline()) {
            set = pipeline.sendCommand("SET", "k", "v");
        }
        assertEquals("OK", set.get());
    }

    @Test
    void closingAnOpenTransactionDiscardsIt() {
        try (GraphContext.Transaction transaction = context.transaction()) {
            transaction.sendCommand("SET", "k", "v");
        }
        assertEquals(1, server.count("DISCARD"));
    }

    @Test
    void aFailedReadSaysSo() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> set = pipeline.sendCommand("SET", "k", "v");
        server.dropClients();

        assertThrows(RuntimeException.class, pipeline::sync);
        IllegalStateException e = assertThrows(IllegalStateException.class, set::get);
        assertTrue(e.getMessage().contains("failed"), e.getMessage());
    }

    // --- One batch at a time on the connection -----------------------------------------------------------------

    @Test
    void noOtherBatchStartsWhileAPipelineAwaitsReplies() {
        GraphContext.Pipeline pipeline = context.pipeline();
        pipeline.sendCommand("SET", "k", "v");

        assertThrows(IllegalStateException.class, context::pipeline);
        assertThrows(IllegalStateException.class, context::transaction);
        pipeline.sync();
        assertTrue(context.pipeline() != null, "nothing awaits replies after the read");
    }

    @Test
    void noOtherBatchStartsWhileATransactionIsOpen() {
        GraphContext.Transaction transaction = context.transaction();

        IllegalStateException e = assertThrows(IllegalStateException.class, context::transaction);
        assertTrue(e.getMessage().contains("awaits replies"), e.getMessage());
        assertThrows(IllegalStateException.class, context::pipeline);
        transaction.exec();
        context.transaction().discard();
        assertEquals(2, server.count("MULTI"), "a refused transaction must not send MULTI");
    }

    @Test
    void theContextRunsNoCommandWhileABatchAwaitsReplies() {
        GraphContext.Transaction transaction = context.transaction();
        int sent = server.received().size();

        assertThrows(IllegalStateException.class, () -> context.query("CREATE ()"));
        assertThrows(IllegalStateException.class, () -> context.readOnlyQuery("MATCH (n) RETURN n"));
        assertThrows(IllegalStateException.class, () -> context.explain("MATCH (n) RETURN n"));
        assertThrows(IllegalStateException.class, () -> context.profile("MATCH (n) RETURN n"));
        assertThrows(IllegalStateException.class, () -> context.copyGraph("other"));
        assertThrows(IllegalStateException.class, context::deleteGraph);
        assertThrows(IllegalStateException.class, () -> context.watch("k"));
        assertThrows(IllegalStateException.class, context::unwatch);
        assertEquals(sent, server.received().size(), "sent inside the transaction: " + server.received());
        transaction.exec();

        GraphContext.Pipeline pipeline = context.pipeline();
        pipeline.sendCommand("SET", "k", "v");
        assertThrows(IllegalStateException.class, () -> context.query("CREATE ()"));
        pipeline.sync();
        assertEquals(1, context.query("CREATE ()").getStatistics().nodesCreated());
    }

    @SuppressWarnings("deprecation")
    @Test
    void aBatchIsntUsedWhileADeprecatedOneAwaitsReplies() {
        GraphContext.Pipeline pipeline = context.pipeline();
        com.falkordb.GraphPipeline old = context.pipelined();
        old.set("k", "v");

        assertThrows(IllegalStateException.class, () -> pipeline.sendCommand("SET", "k", "v"));
        pipeline.sync(); // nothing of its own to read, so nothing to wait for
        old.sync();
        Reply<Object> set = pipeline.sendCommand("SET", "k", "v");
        pipeline.sync();
        assertEquals("OK", set.get());
    }

    // --- A pipeline that failed, or was closed -------------------------------------------------------------------

    @Test
    void aPipelineWhoseReadFailedCantBeUsedAndItsCloseDropsTheConnection() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> set = pipeline.sendCommand("SET", "k", "v");
        server.dropClients();
        RuntimeException failure = assertThrows(RuntimeException.class, pipeline::sync);

        IllegalStateException unread = assertThrows(IllegalStateException.class, set::get);
        assertSame(failure, unread.getCause());
        IllegalStateException queue =
                assertThrows(IllegalStateException.class, () -> pipeline.sendCommand("SET", "k", "v"));
        assertSame(failure, queue.getCause());
        assertThrows(IllegalStateException.class, pipeline::sync);
        assertThrows(IllegalStateException.class, pipeline::syncAndReturnAll);
        pipeline.close();
        context.close();

        try (GraphContext next = driver.graph("g").getContext()) {
            assertEquals(1, next.query("CREATE ()").getStatistics().nodesCreated());
        }
        assertEquals(2, connections.size(), "the pool must destroy the connection whose state is unknown");
    }

    @Test
    void aClosedPipelineCantBeUsedAndClosingItAgainSendsNothing() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> set = pipeline.sendCommand("SET", "k", "v");
        pipeline.close();
        int sent = server.received().size();

        assertEquals("OK", set.get());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> pipeline.query("CREATE ()"));
        assertEquals("the pipeline is closed", e.getMessage());
        assertThrows(IllegalStateException.class, pipeline::sync);
        pipeline.close();
        assertEquals(sent, server.received().size());
    }

    @Test
    void closingADiscardedTransactionSendsNothing() {
        GraphContext.Transaction transaction = context.transaction();
        transaction.discard();
        int sent = server.received().size();
        transaction.close();
        assertEquals(sent, server.received().size());
    }

    @Test
    void aRefusedTransactionKeepsTheServersErrorAsTheCause() {
        GraphContext.Transaction transaction = context.transaction();
        Reply<Object> set = transaction.sendCommand("SET", "k", "v");
        connections.get(0).refuseExec = true;

        GraphException e = assertThrows(GraphException.class, transaction::exec);
        JedisDataException cause = assertInstanceOf(JedisDataException.class, e.getCause());
        assertSame(cause, assertThrows(IllegalStateException.class, set::get).getCause());
    }

    @Test
    void aDecodeFailureThatIsAJedisDataExceptionIsThrownAsItIs() {
        JedisDataException busy = new JedisDataException("BUSY the lookup's server is busy");
        try (redis.clients.jedis.JedisPool pool = new redis.clients.jedis.JedisPool("127.0.0.1", server.port())) {
            GraphContextImpl failing = new GraphContextImpl(pool.getResource(), new BusyCache(busy), "g");
            GraphContext.Pipeline pipeline = failing.pipeline();
            Reply<ResultSet> node = pipeline.query("MATCH (n) RETURN n");
            List<Object> results = pipeline.syncAndReturnAll();

            // what decoding threw, not taken for the server's error reply and made a GraphException
            assertSame(busy, assertThrows(JedisDataException.class, node::get));
            assertSame(busy, results.get(0));
            failing.close();
        }
    }

    @Test
    void aReadThatThrewAnErrorDoesntReturnItsRepliesAgain() {
        try (redis.clients.jedis.JedisPool pool = new redis.clients.jedis.JedisPool("127.0.0.1", server.port())) {
            GraphContextImpl failing = new GraphContextImpl(pool.getResource(), new FailingCache(), "g");
            GraphContext.Pipeline pipeline = failing.pipeline();
            pipeline.query("MATCH (n) RETURN n");
            assertThrows(LookupFailed.class, pipeline::syncAndReturnAll); // read, then failed to decode

            pipeline.sendCommand("SET", "k", "v");
            assertEquals(Collections.singletonList("OK"), pipeline.syncAndReturnAll());
            failing.close();
        }
    }

    @Test
    void aReplyWhoseDecodingThrewAnErrorIsNeverNull() {
        try (redis.clients.jedis.JedisPool pool = new redis.clients.jedis.JedisPool("127.0.0.1", server.port())) {
            GraphContextImpl failing = new GraphContextImpl(pool.getResource(), new FailingCache(), "g");
            GraphContext.Pipeline pipeline = failing.pipeline();
            Reply<ResultSet> node = pipeline.query("MATCH (n) RETURN n");
            pipeline.sync();

            LookupFailed first = assertThrows(LookupFailed.class, node::get); // an Error, which Jedis forgets
            assertSame(first, assertThrows(LookupFailed.class, node::get));
            failing.close();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void aDroppedPipelineThatAwaitsRepliesIsntCollected() {
        queueAndDropThePipeline();
        for (int i = 0; i <= GraphContextImpl.HELD_BATCHES; i++) {
            context.pipelined(); // the deprecated API may start batches: the dropped one is no longer among the latest
        }
        WeakReference<Object> unreachable = new WeakReference<>(new Object());
        for (int i = 0; i < 10 && unreachable.get() != null; i++) {
            System.gc();
        }

        // Collected, it would look done, and its reply would be read as the next batch's.
        assertThrows(IllegalStateException.class, context::pipeline);
        assertThrows(IllegalStateException.class, () -> context.query("CREATE ()"));
    }

    @Test
    void aReplyKeepsItsPipelineForTheContextsCloseToRead() {
        Reply<Object> set = queueAndDropThePipeline();
        WeakReference<Object> unreachable = new WeakReference<>(new Object());
        for (int i = 0; i < 10 && unreachable.get() != null; i++) {
            System.gc();
        }
        context.close();

        assertEquals("OK", set.get());
    }

    private Reply<Object> queueAndDropThePipeline() {
        return context.pipeline().sendCommand("SET", "k", "v");
    }

    @Test
    void sendCommandDecodesResp3Shapes() {
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> map = pipeline.sendCommand("RESP3MAP");
        Reply<Object> scalars = pipeline.sendCommand("RESP3SCALARS");
        Reply<Object> binaryMap = pipeline.sendBinaryCommand("RESP3MAP");
        pipeline.sync();

        Map<Object, Object> expected = new LinkedHashMap<>();
        expected.put("a", 1L);
        expected.put("b", Arrays.asList("x"));
        assertEquals(expected, map.get());
        assertEquals(LinkedHashMap.class, map.get().getClass());
        assertEquals(Arrays.asList(1.5, true, new BigInteger("12345678901234567890"), "verbatim"), scalars.get());
        Map<?, ?> binary = (Map<?, ?>) binaryMap.get();
        assertArrayEquals(bytes("a"), (byte[]) binary.keySet().iterator().next());
    }

    @Test
    void theGuardIgnoresTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR")); // where "multi".toUpperCase() is "MULTİ"
        try {
            GraphContext.Pipeline pipeline = context.pipeline();
            assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand("multi"));
            assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand("client", "list"));
        } finally {
            Locale.setDefault(saved);
        }
    }

    // --- The guard -----------------------------------------------------------------------------------------------

    static Stream<Arguments> refused() {
        return Stream.of(
                        "MULTI",
                        "EXEC",
                        "DISCARD",
                        "WATCH k",
                        "UNWATCH",
                        "WAIT 1 0",
                        "GRAPH.QUERY g CREATE()",
                        "GRAPH.DELETE g",
                        "GRAPH.LIST",
                        "AUTH user password",
                        "HELLO 3",
                        "SELECT 1",
                        "CLIENT REPLY OFF",
                        "CLIENT SETNAME x",
                        "RESET",
                        "QUIT",
                        "READONLY",
                        "READWRITE",
                        "ASKING",
                        "SUBSCRIBE c",
                        "PSUBSCRIBE c*",
                        "SSUBSCRIBE c",
                        "UNSUBSCRIBE",
                        "PUNSUBSCRIBE",
                        "SUNSUBSCRIBE",
                        "MONITOR",
                        "SYNC",
                        "PSYNC ? -1",
                        "REPLCONF listening-port 1",
                        "BLPOP k 0",
                        "BRPOP k 0",
                        "BRPOPLPUSH a b 0",
                        "BLMOVE a b LEFT RIGHT 0",
                        "BLMPOP 0 1 k LEFT",
                        "BZPOPMIN k 0",
                        "BZPOPMAX k 0",
                        "BZMPOP 0 1 k MIN",
                        "WAITAOF 1 0 0",
                        "XREAD BLOCK 0 STREAMS k $",
                        "XREAD COUNT 1 BLOCK 0 STREAMS k $",
                        "XREADGROUP GROUP g c BLOCK 0 STREAMS k >",
                        "XREADGROUP GROUP g c COUNT 1 block 0 STREAMS k >",
                        "XREADGROUP GROUP STREAMS c BLOCK 0 STREAMS k >", // a group named STREAMS
                        "XREADGROUP GROUP g c NOACK CLAIM 10 BLOCK 0 STREAMS k >",
                        "XREADGROUP GROUP g c FUTUREOPTION BLOCK 0 STREAMS k >", // unparsed: BLOCK before STREAMS
                        "DEL a g",
                        "UNLINK g",
                        "GETDEL g",
                        "RENAME g other",
                        "RENAMENX other g",
                        "MOVE g 1",
                        "COPY other g REPLACE",
                        "RESTORE g 0 payload REPLACE",
                        "MIGRATE h 1 g 0 0",
                        "MIGRATE h 1 \"\" 0 0 KEYS a g",
                        "FLUSHALL",
                        "FLUSHDB",
                        "SWAPDB 0 1",
                        "SCRIPT DEBUG YES",
                        "MIGRATE h 1 \"\" 0 0 AUTH2 u KEYS KEYS g")
                .flatMap(command -> Stream.of(command, command.toLowerCase(Locale.ROOT)))
                .flatMap(command -> Stream.of(Arguments.of(command, true), Arguments.of(command, false)));
    }

    @ParameterizedTest
    @MethodSource("refused")
    void sendCommandRefusesWhatWouldBreakTheBatchOrTheConnection(String command, boolean pipelined) {
        List<String> words = Arrays.asList(command.split(" "));
        String[] args = words.subList(1, words.size()).toArray(new String[0]);
        int before = server.received().size();
        GraphContext.Pipeline pipeline = pipelined ? context.pipeline() : null;
        GraphContext.Transaction transaction = pipelined ? null : context.transaction();

        IllegalArgumentException text = assertThrows(IllegalArgumentException.class, () -> {
            if (pipelined) {
                pipeline.sendCommand(words.get(0), args);
            } else {
                transaction.sendCommand(words.get(0), args);
            }
        });
        assertTrue(text.getMessage().startsWith(words.get(0) + " "), text.getMessage());
        byte[][] binaryArgs = Arrays.stream(args).map(ContextBatchTest::bytes).toArray(byte[][]::new);
        assertThrows(IllegalArgumentException.class, () -> {
            if (pipelined) {
                pipeline.sendBinaryCommand(words.get(0), binaryArgs);
            } else {
                transaction.sendBinaryCommand(words.get(0), binaryArgs);
            }
        });
        if (pipelined) {
            pipeline.sync();
        } else {
            transaction.discard();
        }
        List<List<String>> received = server.received();
        assertEquals(
                pipelined
                        ? Collections.emptyList()
                        : Arrays.asList(Collections.singletonList("MULTI"), Collections.singletonList("DISCARD")),
                received.subList(before, received.size()),
                "a refused command was sent");
    }

    static Stream<String> allowed() {
        return Stream.of(
                "XREAD STREAMS BLOCK 0", // a key named BLOCK
                "XREAD COUNT 1 STREAMS k 0",
                "XREADGROUP GROUP BLOCK BLOCK STREAMS k >", // a group and a consumer named BLOCK
                "XREADGROUP GROUP g c COUNT BLOCK STREAMS k >", // not a count, but the server's to reject
                "GRAPHS",
                "MULTİ", // not MULTI for Redis, which folds ASCII letters only
                "DEL other",
                "SCRIPT FLUSH",
                "MIGRATE h 1 \"\" 0 0 AUTH KEYS KEYS other", // a password named KEYS
                "MIGRATE h 1 other 0 0 AUTH KEYS g", // a password named KEYS, before a word that is the graph's name
                "MIGRATE h 1 other 0 0 AUTH2 KEYS g", // a user named KEYS, and a password that is the graph's name
                "COPY g other",
                "MIGRATE h 1 other 0 0",
                "PING");
    }

    @ParameterizedTest
    @MethodSource("allowed")
    void sendCommandSendsEverythingElse(String command) {
        List<String> words = Arrays.asList(command.split(" "));
        GraphContext.Pipeline pipeline = context.pipeline();
        Reply<Object> reply = pipeline.sendCommand(
                words.get(0), words.subList(1, words.size()).toArray(new String[0]));
        pipeline.sync();

        assertEquals(words, server.received().get(server.received().size() - 1));
        assertEquals("OK", reply.get());
    }

    @Test
    void sendCommandRejectsMissingArguments() {
        GraphContext.Pipeline pipeline = context.pipeline();
        int sent = server.received().size();

        assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand(null));
        assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand(""));
        assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand("SET", (String[]) null));
        assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand("SET", "k", null));
        assertThrows(IllegalArgumentException.class, () -> pipeline.sendBinaryCommand("SET", bytes("k"), null));
        assertEquals(sent, server.received().size());
    }

    // --- GraphContext's default methods --------------------------------------------------------------------------

    @Test
    void aGraphContextOfAnotherImplementationDoesntSupportTheNewBatches() {
        GraphContext foreign = new ForeignContext();
        assertThrows(UnsupportedOperationException.class, foreign::pipeline);
        assertThrows(UnsupportedOperationException.class, foreign::transaction);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private List<String> last(String command) {
        List<List<String>> received = server.received();
        for (int i = received.size() - 1; i >= 0; i--) {
            if (received.get(i).get(0).equalsIgnoreCase(command)) {
                return received.get(i);
            }
        }
        throw new AssertionError(command + " wasn't sent: " + received);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Answers one connection's commands the way Redis does, as far as these tests need: {@code MULTI} queues what
     * follows until {@code EXEC} or {@code DISCARD}.
     */
    private static final class Connection implements Function<List<String>, String> {
        volatile boolean abortExec;
        volatile boolean refuseExec;

        private boolean inMulti;
        private final List<String> queued = new ArrayList<>();

        @Override
        public synchronized String apply(List<String> command) {
            String name = command.get(0).toUpperCase(Locale.ROOT);
            switch (name) {
                case "MULTI":
                    inMulti = true;
                    return FakeRedisServer.ok();
                case "EXEC":
                    inMulti = false;
                    String replies = refuseExec
                            ? FakeRedisServer.error("EXECABORT Transaction discarded because of previous errors.")
                            : abortExec ? "*-1\r\n" : FakeRedisServer.array(queued.toArray(new String[0]));
                    queued.clear();
                    return replies;
                case "DISCARD":
                    inMulti = false;
                    queued.clear();
                    return FakeRedisServer.ok();
                default:
                    String reply = answer(name, command);
                    if (inMulti) {
                        queued.add(reply);
                        return "+QUEUED\r\n";
                    }
                    return reply;
            }
        }

        private static String answer(String name, List<String> command) {
            switch (name) {
                case "GET":
                    return command.get(1).equals("missing") ? "$-1\r\n" : FakeRedisServer.bulk("v");
                case "INCR":
                    return FakeRedisServer.integer(1);
                case "WAIT":
                    return FakeRedisServer.integer(0);
                case "LRANGE":
                    return FakeRedisServer.array(
                            FakeRedisServer.bulk("a"),
                            FakeRedisServer.integer(2),
                            "$-1\r\n",
                            FakeRedisServer.array(FakeRedisServer.bulk("é")),
                            FakeRedisServer.error("ERR nested"));
                case "NILARRAY":
                    return "*-1\r\n";
                case "RESP3MAP":
                    return "%2\r\n" + FakeRedisServer.bulk("a") + FakeRedisServer.integer(1) + FakeRedisServer.bulk("b")
                            + "~1\r\n" + FakeRedisServer.bulk("x");
                case "RESP3SCALARS":
                    return "*4\r\n,1.5\r\n#t\r\n(12345678901234567890\r\n=12\r\ntxt:verbatim\r\n";
                case "FAIL":
                    return FakeRedisServer.error("ERR failed on purpose");
                case "GRAPH.QUERY":
                    if (command.get(2).contains("malformed")) {
                        return FakeRedisServer.integer(1); // where a graph reply belongs
                    }
                    return command.get(2).startsWith("MATCH") ? NODE_REPLY : WRITE_REPLY;
                case "GRAPH.EXPLAIN":
                    return FakeRedisServer.array(FakeRedisServer.bulk("Results"), FakeRedisServer.bulk("    Project"));
                default:
                    return FakeRedisServer.ok();
            }
        }
    }

    /** Thrown by {@link FailingCache}: an {@link Error}, which decoding doesn't keep. */
    private static final class LookupFailed extends Error {
        private static final long serialVersionUID = 1L;

        LookupFailed() {
            super("the label lookup failed");
        }
    }

    /** A cache whose label lookup fails, so a reply that names a label fails to decode. */
    private static final class FailingCache extends com.falkordb.impl.graph_cache.GraphCache {
        @Override
        public String getLabel(int index, com.falkordb.Graph graph) {
            throw new LookupFailed();
        }
    }

    /** A cache whose label lookup fails with the given exception. */
    private static final class BusyCache extends com.falkordb.impl.graph_cache.GraphCache {
        private final RuntimeException failure;

        BusyCache(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public String getLabel(int index, com.falkordb.Graph graph) {
            throw failure;
        }
    }

    /** A {@link GraphContext} that isn't the driver's, written before {@code pipeline()} and {@code transaction()}. */
    @SuppressWarnings("deprecation")
    private static final class ForeignContext implements GraphContext {
        @Override
        public com.falkordb.GraphTransaction multi() {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.falkordb.GraphPipeline pipelined() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String watch(String... keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String unwatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet query(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet readOnlyQuery(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet query(String query, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet readOnlyQuery(String query, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet query(String query, java.util.Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet readOnlyQuery(String query, java.util.Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet query(String query, java.util.Map<String, Object> params, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet readOnlyQuery(String query, java.util.Map<String, Object> params, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet callProcedure(String procedure) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet callProcedure(String procedure, List<String> args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet callProcedure(
                String procedure, List<String> args, java.util.Map<String, List<String>> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet profile(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet profile(String query, java.util.Map<String, Object> params) {
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
        public List<String> explain(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> explain(String query, java.util.Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }
}
