package com.falkordb.impl.graph_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Graph;
import com.falkordb.Header;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.Statistics;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.resultset.RecordImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        GraphCacheList cache = new GraphCacheList("db.labels", "label");

        assertEquals("a", cache.getCachedData(0, graph));
        assertEquals("b", cache.getCachedData(1, graph));
        assertEquals("c", cache.getCachedData(2, graph));
        assertEquals(1, graph.calls.get(), "cached indices should not re-run the procedure");
    }

    @Test
    void concurrentGetForAFreshIndexRefreshesExactlyOnce() throws InterruptedException {
        CountingGraph graph = new CountingGraph("a", "b", "c");
        GraphCacheList cache = new GraphCacheList("db.labels", "label");

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
    void warmsTheCacheWithAReadOnlyQuerySoReplicasAcceptIt() {
        CountingGraph graph = new CountingGraph("a", "b", "c");
        GraphCacheList cache = new GraphCacheList("db.labels", "label");

        assertEquals("a", cache.getCachedData(0, graph));

        assertEquals(1, graph.calls.get(), "the cache must warm through readOnlyQuery");
        assertEquals(
                Collections.singletonList("CALL db.labels()"),
                graph.issuedQueries,
                "the warming call must be the prepared procedure, sent as a read-only query");
    }

    @Test
    void clearForcesARefreshOnNextGet() {
        CountingGraph graph = new CountingGraph("a", "b", "c");
        GraphCacheList cache = new GraphCacheList("db.labels", "label");

        assertEquals("a", cache.getCachedData(0, graph));
        assertEquals(1, graph.calls.get());

        cache.clear();
        assertEquals("a", cache.getCachedData(0, graph));
        assertEquals(2, graph.calls.get(), "after clear the cache must re-fetch");
    }

    @Test
    void aRefreshReplacesEntriesFromARecreatedGraph() {
        // The graph was deleted and recreated elsewhere (another client, or another handle with its own
        // cache) with different labels. A refresh used to append only the entries past the cached ones,
        // so id 0 kept decoding to the old graph's label.
        CountingGraph graph = new CountingGraph("Old0");
        GraphCacheList cache = new GraphCacheList("db.labels", "label");
        assertEquals("Old0", cache.getCachedData(0, graph));

        graph.values = Arrays.asList("New0", "New1");
        assertEquals("New1", cache.getCachedData(1, graph));
        assertEquals("New0", cache.getCachedData(0, graph));
        assertEquals(2, graph.calls.get());
    }

    @Test
    void anIdTheGraphDoesNotListThrowsAGraphException() {
        CountingGraph graph = new CountingGraph("a");
        GraphCacheList cache = new GraphCacheList("db.labels", "label");

        GraphException e = assertThrows(GraphException.class, () -> cache.getCachedData(5, graph));
        assertTrue(e.getMessage().contains("label id 5"), e.getMessage());
        assertTrue(e.getMessage().contains("db.labels() lists 1"), e.getMessage());

        // the refresh that found the id missing is kept, so a listed id needs no further query
        assertEquals("a", cache.getCachedData(0, graph));
        assertEquals(1, graph.calls.get());
    }

    @Test
    void aNegativeIdThrowsAGraphExceptionWithoutQuerying() {
        CountingGraph graph = new CountingGraph("a");
        GraphCacheList cache = new GraphCacheList("db.labels", "label");

        GraphException e = assertThrows(GraphException.class, () -> cache.getCachedData(-1, graph));
        assertEquals("Invalid label id -1", e.getMessage());
        assertEquals(0, graph.calls.get());
    }

    @Test
    void aConcurrentClearNeverFailsALookup() throws InterruptedException {
        // Every clear() makes the next lookup refresh, so the readers race clears and refreshes alike.
        // Before the list was replaced as a whole, a clear() that landed between a reader's bounds check
        // and its get() threw IndexOutOfBoundsException for an id that was cached a moment earlier.
        CountingGraph graph = new CountingGraph("a", "b", "c");
        graph.recordQueries = false;
        GraphCacheList cache = new GraphCacheList("db.labels", "label");
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        int readers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        try {
            for (int i = 0; i < readers; i++) {
                pool.execute(() -> {
                    try {
                        while (!stop.get()) {
                            assertEquals("c", cache.getCachedData(2, graph));
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(200);
            while (System.nanoTime() < deadline && failure.get() == null) {
                cache.clear();
            }
        } finally {
            stop.set(true);
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "a reader did not stop in time");
        }
        assertNull(failure.get(), () -> "a lookup failed while the cache was being cleared: " + failure.get());
    }

    /**
     * A {@link Graph} that only implements {@code readOnlyQuery(String)}, counting invocations.
     *
     * {@code callProcedure} deliberately fails. Cache warming must never route through it, because
     * it issues GRAPH.QUERY, which a replica rejects with READONLY.
     */
    private static final class CountingGraph implements Graph {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> issuedQueries = Collections.synchronizedList(new ArrayList<>());
        /** What the procedure returns; replace it to stand for a graph that was recreated. */
        volatile List<String> values;
        /** Off for stress tests, which would otherwise grow {@link #issuedQueries} without bound. */
        volatile boolean recordQueries = true;

        CountingGraph(String... values) {
            this.values = Arrays.asList(values);
        }

        @Override
        public ResultSet readOnlyQuery(String query) {
            calls.incrementAndGet();
            if (recordQueries) {
                issuedQueries.add(query);
            }
            List<Record> records = new ArrayList<>();
            for (String v : values) {
                records.add(new RecordImpl(Collections.singletonList("col"), Collections.singletonList(v)));
            }
            return new ListResultSet(records);
        }

        @Override
        public ResultSet callProcedure(String procedure) {
            throw new AssertionError("cache warming must not use callProcedure: it issues GRAPH.QUERY, "
                    + "which a replica rejects with READONLY");
        }

        // --- unused Graph operations ---
        @Override
        public ResultSet query(String query) {
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
