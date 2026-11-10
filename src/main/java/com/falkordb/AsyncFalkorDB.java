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
 * try (Driver driver = FalkorDB.driver();
 *         ExecutorService vthreads = Executors.newVirtualThreadPerTaskExecutor()) { // JDK 21+
 *     AsyncGraph async = AsyncFalkorDB.wrap(driver.graph("social"), vthreads);
 *     CompletableFuture<ResultSet> f = async.query("MATCH (n) RETURN count(n)");
 *     // ...
 * } // closes the executor first, which waits for its queued queries, then the driver
 * }</pre>
 *
 * <p>If the closing thread is interrupted while it waits, {@code ExecutorService.close()} drops the queued
 * operations that haven't started: they never run, and their futures never complete. It still waits for the
 * running ones, so a blocking query holds up closing until it ends or times out.
 *
 * <p>An {@link AsyncGraph} owns nothing, so there is nothing to close on it: the caller keeps ownership of the
 * graph, its driver and the executor, and closes them. Shut the executor down first, so that no queued
 * operation runs on a closed driver, then close the driver. Before JDK 19 an {@code ExecutorService} isn't
 * {@code AutoCloseable}, so call {@code shutdown()} and {@code awaitTermination(...)} before closing the
 * driver.
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
