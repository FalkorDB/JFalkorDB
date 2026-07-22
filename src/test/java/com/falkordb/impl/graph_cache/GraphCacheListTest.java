package com.falkordb.impl.graph_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Graph;
import com.falkordb.Header;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.Statistics;
import com.falkordb.impl.resultset.RecordImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GraphCacheList}'s cache-refresh behavior — in particular that the
 * double-checked refresh runs the (blocking) procedure exactly once under concurrency after the
 * de-pinning change from a {@code synchronized} block to a {@link java.util.concurrent.locks.ReentrantLock}.
 */
class GraphCacheListTest {

    @Test
    void returnsProcedureValuesAndRefreshesOnce() {
        CountingGraph graph = new CountingGraph("a", "b", "c");
        GraphCacheList cache = new GraphCacheList("db.labels");

        assertEquals("a", cache.getCachedData(0, graph));
        assertEquals("b", cache.getCachedData(1, graph));
        assertEquals("c", cache.getCachedData(2, graph));
        assertEquals(1, graph.calls.get(), "cached indices should not re-run the procedure");
    }

    @Test
    void concurrentGetForAFreshIndexRefreshesExactlyOnce() throws InterruptedException {
        CountingGraph graph = new CountingGraph("a", "b", "c");
        GraphCacheList cache = new GraphCacheList("db.labels");

        int threads = 16;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                results.add(cache.getCachedData(0, graph));
            });
            workers.add(t);
            t.start();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "workers did not reach the start line in time");
        go.countDown(); // release all workers at once to race the refresh
        for (Thread t : workers) {
            t.join(TimeUnit.SECONDS.toMillis(10));
            assertFalse(t.isAlive(), "a worker did not finish in time — possible deadlock in the refresh path");
        }

        assertEquals(1, graph.calls.get(), "the double-checked refresh must run the procedure once");
        assertEquals(threads, results.size());
        for (String r : results) {
            assertEquals("a", r);
        }
    }

    @Test
    void clearForcesARefreshOnNextGet() {
        CountingGraph graph = new CountingGraph("a", "b", "c");
        GraphCacheList cache = new GraphCacheList("db.labels");

        assertEquals("a", cache.getCachedData(0, graph));
        assertEquals(1, graph.calls.get());

        cache.clear();
        assertEquals("a", cache.getCachedData(0, graph));
        assertEquals(2, graph.calls.get(), "after clear the cache must re-fetch");
    }

    /** A {@link Graph} that only implements {@code callProcedure(String)}, counting invocations. */
    private static final class CountingGraph implements Graph {
        final AtomicInteger calls = new AtomicInteger();
        private final List<String> values;

        CountingGraph(String... values) {
            this.values = new ArrayList<>();
            Collections.addAll(this.values, values);
        }

        @Override
        public ResultSet callProcedure(String procedure) {
            calls.incrementAndGet();
            List<Record> records = new ArrayList<>();
            for (String v : values) {
                records.add(new RecordImpl(Collections.singletonList("col"), Collections.singletonList(v)));
            }
            return new ListResultSet(records);
        }

        // --- unused Graph operations ---
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
        public ResultSet query(String query, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet readOnlyQuery(String query, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet query(String query, Map<String, Object> params, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet readOnlyQuery(String query, Map<String, Object> params, long timeout) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet profile(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet profile(String query, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet callProcedure(String procedure, List<String> args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResultSet callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs) {
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
        public List<String> explain(String query, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // no-op
        }
    }

    /** A minimal {@link ResultSet} backed by a list of records (only iteration is exercised). */
    private static final class ListResultSet implements ResultSet {
        private final List<Record> records;

        ListResultSet(List<Record> records) {
            this.records = records;
        }

        @Override
        public Iterator<Record> iterator() {
            return records.iterator();
        }

        @Override
        public int size() {
            return records.size();
        }

        @Override
        public Statistics getStatistics() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Header getHeader() {
            throw new UnsupportedOperationException();
        }
    }
}
