package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.graph_cache.GraphCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Builder;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Explain replies read through a context, a pipeline and a transaction, with real Jedis {@link Response}s.
 * A plan that holds an error must fail every retrieval: Jedis builds a response once and forgets an
 * exception its builder threw, and {@code syncAndReturnAll()} and {@code exec()} make that first
 * retrieval themselves.
 */
class ExplainResponseTest {

    private static final String PLAN_ERROR = "ERR plan failed";

    /** A plan whose second element is an error rather than a line. */
    private static final String FAILED_PLAN =
            FakeRedisServer.array(FakeRedisServer.bulk("Results"), FakeRedisServer.error(PLAN_ERROR));

    private static final String GOOD_PLAN =
            FakeRedisServer.array(FakeRedisServer.bulk("Results"), FakeRedisServer.bulk("    Project"));

    @Test
    void aContextReportsAnErrorInsideThePlanAsAGraphException() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(FAILED_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphException error = assertThrows(GraphException.class, () -> context.explain("RETURN 1"));
            assertPlanError(error);
        }
    }

    @Test
    void aPipelinedPlanWithAnErrorFailsEveryGetAfterSync() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(FAILED_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphPipeline pipeline = context.pipelined();
            Response<List<String>> plan = pipeline.explain("RETURN 1");
            pipeline.sync();

            assertPlanError(assertThrows(GraphException.class, plan::get));
            assertPlanError(assertThrows(GraphException.class, plan::get));
        }
    }

    @Test
    void aPipelinedPlanWithAnErrorFailsEveryGetAfterSyncAndReturnAll() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(FAILED_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphPipeline pipeline = context.pipelined();
            Response<List<String>> plan = pipeline.explain("RETURN 1");
            List<Object> all = pipeline.syncAndReturnAll();

            assertEquals(1, all.size());
            GraphException returned = assertInstanceOf(GraphException.class, all.get(0));
            assertPlanError(returned);
            assertSame(returned, assertThrows(GraphException.class, plan::get));
            assertSame(returned, assertThrows(GraphException.class, plan::get));
        }
    }

    @Test
    void aTransactedPlanWithAnErrorFailsEveryGetAfterExec() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(FAILED_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphTransaction transaction = context.multi();
            Response<List<String>> plan = transaction.explain("RETURN 1");
            List<Object> all = transaction.exec();

            assertEquals(1, all.size());
            GraphException returned = assertInstanceOf(GraphException.class, all.get(0));
            assertPlanError(returned);
            assertSame(returned, assertThrows(GraphException.class, plan::get));
            assertSame(returned, assertThrows(GraphException.class, plan::get));
        }
    }

    @Test
    void aTransactionUsedAfterExecFailsEveryGetToo() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(FAILED_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphTransaction transaction = context.multi();
            transaction.exec();

            // outside MULTI the command runs at once instead of being queued
            Response<List<String>> plan = transaction.explain("RETURN $x", Collections.singletonMap("x", 1));
            GraphException first = assertThrows(GraphException.class, plan::get);
            assertPlanError(first);
            assertSame(first, assertThrows(GraphException.class, plan::get));
        }
    }

    @Test
    void aPipelinedAndATransactedPlanReturnTheirLinesOnEveryGet() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(GOOD_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            List<String> expected = Arrays.asList("Results", "    Project");

            GraphPipeline pipeline = context.pipelined();
            Response<List<String>> pipelined = pipeline.explain("RETURN 1");
            assertEquals(Arrays.asList(expected), pipeline.syncAndReturnAll());
            assertEquals(expected, pipelined.get());
            assertEquals(expected, pipelined.get());

            GraphTransaction transaction = context.multi();
            Response<List<String>> transacted = transaction.explain("RETURN 1");
            assertEquals(Arrays.asList(expected), transaction.exec());
            assertEquals(expected, transacted.get());
            assertEquals(expected, transacted.get());
        }
    }

    @Test
    void aPlanRejectedOutrightIsStillTheServerError() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(FakeRedisServer.error(PLAN_ERROR)));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphPipeline pipeline = context.pipelined();
            Response<List<String>> plan = pipeline.explain("RETURN 1");
            pipeline.sync();

            // a reply that is an error as a whole never reaches the builder: Jedis keeps and rethrows it
            JedisDataException first = assertThrows(JedisDataException.class, plan::get);
            assertEquals(PLAN_ERROR, first.getMessage());
            assertSame(first, assertThrows(JedisDataException.class, plan::get));
        }
    }

    @Test
    void getBeforeSyncStillSaysTheResponseIsNotReady() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(GOOD_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphPipeline pipeline = context.pipelined();
            Response<List<String>> plan = pipeline.explain("RETURN 1");
            assertThrows(IllegalStateException.class, plan::get);
            pipeline.sync();
            assertEquals(Arrays.asList("Results", "    Project"), plan.get());
        }
    }

    @Test
    void setAndSetDependencyReachTheQueuedResponse() {
        try (FakeRedisServer server = new FakeRedisServer(new Answers(GOOD_PLAN));
                JedisPool pool = new JedisPool("127.0.0.1", server.port());
                GraphContextImpl context = new GraphContextImpl(pool.getResource(), new GraphCache(), "g")) {
            GraphPipeline pipeline = context.pipelined();
            Response<List<String>> plan = pipeline.explain("RETURN 1");

            AtomicBoolean dependencyBuilt = new AtomicBoolean();
            Response<String> dependency = new Response<>(new Builder<String>() {
                @Override
                public String build(Object data) {
                    dependencyBuilt.set(true);
                    return String.valueOf(data);
                }
            });
            dependency.set("ready");
            plan.setDependency(dependency);

            // a reply set by hand is read like one the pipeline set
            plan.set(Collections.singletonList(SafeEncoder.encode("Results")));
            assertEquals(Collections.singletonList("Results"), plan.get());
            assertTrue(dependencyBuilt.get(), "reading the plan builds its dependency first");
            pipeline.sync();
        }
    }

    private static void assertPlanError(GraphException error) {
        assertInstanceOf(JedisDataException.class, error.getCause());
        assertTrue(error.getMessage().contains(PLAN_ERROR), error.getMessage());
    }

    /** Answers {@code GRAPH.EXPLAIN} with a fixed reply, queuing it between {@code MULTI} and {@code EXEC}. */
    private static final class Answers implements java.util.function.Function<List<String>, String> {

        private final String explain;
        private List<String> queued;

        Answers(String explain) {
            this.explain = explain;
        }

        @Override
        public synchronized String apply(List<String> command) {
            if (FakeRedisServer.startsWith(command, "MULTI")) {
                queued = new ArrayList<>();
                return FakeRedisServer.ok();
            }
            if (FakeRedisServer.startsWith(command, "EXEC")) {
                String reply = FakeRedisServer.array(queued.toArray(new String[0]));
                queued = null;
                return reply;
            }
            String reply = FakeRedisServer.startsWith(command, "GRAPH.EXPLAIN") ? explain : FakeRedisServer.ok();
            if (queued != null) {
                queued.add(reply);
                return "+QUEUED\r\n";
            }
            return reply;
        }
    }
}
