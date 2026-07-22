package com.falkordb;

import com.falkordb.impl.api.AsyncGraphImpl;
import java.util.concurrent.Executor;

/**
 * Factory for {@link AsyncGraph}, an asynchronous view over a synchronous {@link Graph}.
 *
 * <p>The client stays a blocking, Java-8 client; asynchrony is provided entirely by the
 * caller-supplied {@link Executor}. On JDK 21+ pass {@code Executors.newVirtualThreadPerTaskExecutor()}
 * to fan a blocking graph out over many virtual threads:
 *
 * <pre>{@code
 * Driver driver = FalkorDB.driver();
 * ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor(); // JDK 21+
 * AsyncGraph async = AsyncFalkorDB.wrap(driver.graph("social"), pool);
 * CompletableFuture<ResultSet> f = async.query("MATCH (n) RETURN count(n)");
 * // ... on shutdown: drain outstanding futures, then pool.shutdown() and driver.close()
 * }</pre>
 */
public final class AsyncFalkorDB {

    private AsyncFalkorDB() {}

    /**
     * Wraps a synchronous graph as an {@link AsyncGraph} whose operations run on {@code executor}.
     *
     * <p>Use a pool-per-call {@code driver.graph(id)} (a {@link GraphContextGenerator}), which is safe
     * for concurrent calls; do not wrap a connection-bound {@link GraphContext}. The returned facade
     * owns neither {@code graph} nor {@code executor}: the caller drains outstanding futures, shuts the
     * executor down, then closes the graph and driver.
     *
     * @param graph    the synchronous graph to wrap (typically {@code driver.graph(id)})
     * @param executor the executor each operation runs on (e.g. a virtual-thread executor on JDK 21+)
     * @return an asynchronous view over {@code graph}
     */
    public static AsyncGraph wrap(GraphContextGenerator graph, Executor executor) {
        return new AsyncGraphImpl(graph, executor);
    }
}
