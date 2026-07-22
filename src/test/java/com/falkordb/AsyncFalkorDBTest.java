package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncFalkorDBTest {

    private static final Executor DIRECT = Runnable::run;

    private final FakeGraph graph = new FakeGraph();

    private void assertDelegates(CompletableFuture<?> future, Object expected, String method, Object... args) {
        assertSame(expected, future.join(), method + " result should propagate from the wrapped graph");
        assertEquals(method, graph.lastMethod);
        assertArrayEquals(args, graph.lastArgs);
    }

    @Test
    void wrapRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> AsyncFalkorDB.wrap(null, DIRECT));
        assertThrows(NullPointerException.class, () -> AsyncFalkorDB.wrap(graph, null));
    }

    @Test
    void graphReturnsTheWrappedInstance() {
        assertSame(graph, AsyncFalkorDB.wrap(graph, DIRECT).graph());
    }

    @Test
    void everyOperationDelegatesToTheWrappedGraph() {
        AsyncGraph async = AsyncFalkorDB.wrap(graph, DIRECT);
        Map<String, Object> params = Collections.singletonMap("k", "v");
        List<String> args = Collections.singletonList("a");
        Map<String, List<String>> kwargs = Collections.singletonMap("y", Collections.singletonList("z"));

        assertDelegates(async.query("q"), FakeGraph.RS, "query", "q");
        assertDelegates(async.readOnlyQuery("q"), FakeGraph.RS, "readOnlyQuery", "q");
        assertDelegates(async.query("q", 5L), FakeGraph.RS, "query", "q", 5L);
        assertDelegates(async.readOnlyQuery("q", 5L), FakeGraph.RS, "readOnlyQuery", "q", 5L);
        assertDelegates(async.query("q", params), FakeGraph.RS, "query", "q", params);
        assertDelegates(async.readOnlyQuery("q", params), FakeGraph.RS, "readOnlyQuery", "q", params);
        assertDelegates(async.query("q", params, 5L), FakeGraph.RS, "query", "q", params, 5L);
        assertDelegates(async.readOnlyQuery("q", params, 5L), FakeGraph.RS, "readOnlyQuery", "q", params, 5L);
        assertDelegates(async.callProcedure("p"), FakeGraph.RS, "callProcedure", "p");
        assertDelegates(async.profile("q"), FakeGraph.RS, "profile", "q");
        assertDelegates(async.profile("q", params), FakeGraph.RS, "profile", "q", params);
        assertDelegates(async.callProcedure("p", args), FakeGraph.RS, "callProcedure", "p", args);
        assertDelegates(async.callProcedure("p", args, kwargs), FakeGraph.RS, "callProcedure", "p", args, kwargs);
        assertDelegates(async.copyGraph("dst"), "OK", "copyGraph", "dst");
        assertDelegates(async.deleteGraph(), "OK", "deleteGraph");
        assertDelegates(async.explain("q"), FakeGraph.PLAN, "explain", "q");
        assertDelegates(async.explain("q", params), FakeGraph.PLAN, "explain", "q", params);

        assertEquals(17, graph.calls.get());
    }

    @Test
    void failuresSurfaceAsExceptionalCompletion() {
        graph.toThrow = new IllegalStateException("boom");
        CompletableFuture<ResultSet> future = AsyncFalkorDB.wrap(graph, DIRECT).query("q");

        CompletionException joined = assertThrows(CompletionException.class, future::join);
        assertSame(graph.toThrow, joined.getCause());
        ExecutionException got = assertThrows(ExecutionException.class, future::get);
        assertSame(graph.toThrow, got.getCause());
    }

    @Test
    void cancellingBeforeStartSkipsTheQuery() {
        ManualExecutor executor = new ManualExecutor();
        CompletableFuture<ResultSet> future =
                AsyncFalkorDB.wrap(graph, executor).query("q");

        assertEquals(1, executor.pending(), "task should be queued, not yet run");
        assertTrue(future.cancel(true));
        executor.runAll();

        assertTrue(future.isCancelled());
        assertEquals(0, graph.calls.get(), "a cancelled-before-start query must never reach the graph");
    }

    /** Executor that queues tasks and only runs them on demand, to observe cancel-before-start. */
    private static final class ManualExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pending() {
            return tasks.size();
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    /** Recording {@link GraphContextGenerator} fake; no Mockito on the classpath. */
    private static final class FakeGraph implements GraphContextGenerator {
        static final ResultSet RS = new FakeResultSet();
        static final List<String> PLAN = Collections.singletonList("PLAN");

        final AtomicInteger calls = new AtomicInteger();
        volatile String lastMethod;
        volatile Object[] lastArgs;
        volatile RuntimeException toThrow;

        private <T> T record(String method, T result, Object... args) {
            calls.incrementAndGet();
            lastMethod = method;
            lastArgs = args;
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }

        @Override
        public ResultSet query(String query) {
            return record("query", RS, query);
        }

        @Override
        public ResultSet readOnlyQuery(String query) {
            return record("readOnlyQuery", RS, query);
        }

        @Override
        public ResultSet query(String query, long timeout) {
            return record("query", RS, query, timeout);
        }

        @Override
        public ResultSet readOnlyQuery(String query, long timeout) {
            return record("readOnlyQuery", RS, query, timeout);
        }

        @Override
        public ResultSet query(String query, Map<String, Object> params) {
            return record("query", RS, query, params);
        }

        @Override
        public ResultSet readOnlyQuery(String query, Map<String, Object> params) {
            return record("readOnlyQuery", RS, query, params);
        }

        @Override
        public ResultSet query(String query, Map<String, Object> params, long timeout) {
            return record("query", RS, query, params, timeout);
        }

        @Override
        public ResultSet readOnlyQuery(String query, Map<String, Object> params, long timeout) {
            return record("readOnlyQuery", RS, query, params, timeout);
        }

        @Override
        public ResultSet callProcedure(String procedure) {
            return record("callProcedure", RS, procedure);
        }

        @Override
        public ResultSet profile(String query) {
            return record("profile", RS, query);
        }

        @Override
        public ResultSet profile(String query, Map<String, Object> params) {
            return record("profile", RS, query, params);
        }

        @Override
        public ResultSet callProcedure(String procedure, List<String> args) {
            return record("callProcedure", RS, procedure, args);
        }

        @Override
        public ResultSet callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs) {
            return record("callProcedure", RS, procedure, args, kwargs);
        }

        @Override
        public String copyGraph(String destinationGraphId) {
            return record("copyGraph", "OK", destinationGraphId);
        }

        @Override
        public String deleteGraph() {
            return record("deleteGraph", "OK");
        }

        @Override
        public List<String> explain(String query) {
            return record("explain", PLAN, query);
        }

        @Override
        public List<String> explain(String query, Map<String, Object> params) {
            return record("explain", PLAN, query, params);
        }

        @Override
        public GraphContext getContext() {
            throw new UnsupportedOperationException("not used by the async facade");
        }

        @Override
        public void close() {
            calls.incrementAndGet();
            lastMethod = "close";
        }
    }

    private static final class FakeResultSet implements ResultSet {
        @Override
        public Iterator<Record> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Statistics getStatistics() {
            throw new UnsupportedOperationException("not used by the async facade tests");
        }

        @Override
        public Header getHeader() {
            throw new UnsupportedOperationException("not used by the async facade tests");
        }
    }
}
