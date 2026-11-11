package com.falkordb;

/**
 * An interface for a FalkorDB graph with context.
 *
 * <p>A context runs all its commands on one connection, borrowed from the driver's connection pool
 * when the context is created. The connection stays bound to the context until {@link #close()} is
 * called, even if a command fails: after the server rejects a command, such as a query that fails with
 * a {@link com.falkordb.exceptions.GraphException}, the context remains usable. Always close the
 * context, for example with try-with-resources, to return its connection to the pool:
 *
 * <pre>{@code
 * try (GraphContext context = driver.graph("social").getContext()) {
 *     context.query("CREATE (:person {name: 'roi'})");
 * }
 * }</pre>
 *
 * <p>Closing the context also ends what its transactions and pipelines left on the connection, so the next user
 * of the connection gets it clean: a transaction still open is discarded, and the replies a pipeline still awaits
 * are read, which waits for them to arrive. A {@code Response} of a transaction discarded this way can't be read.
 * If more than one transaction or pipeline still awaits replies, or ending one fails, the connection is closed
 * instead of being returned to the pool, and the {@code Response}s still waiting for its replies can't be read
 * either. The same happens when a pipeline's last {@code sync()} or {@code syncAndReturnAll()} failed, and when a
 * transaction or pipeline the caller dropped was garbage-collected before the context closed. The context holds
 * its latest 128 transactions and pipelines, so only an older one can be collected; what it left on the connection
 * is then unknown, and the graph's schema cache is cleared too, in case it deleted the graph. Like every other call
 * on the context, close it on the thread that uses the context.
 */
public interface GraphContext extends Graph {

    /**
     * Returns a Redis transactional object, over the connection context, with graph API capabilities
     * @return Redis transactional object, over the connection context, with graph API capabilities
     * @throws IllegalStateException if the context is closed
     */
    GraphTransaction multi();

    /**
     * Returns a Redis pipeline object, over the connection context, with graph API capabilities
     * @return Redis pipeline object, over the connection context, with graph API capabilities
     * @throws IllegalStateException if the context is closed
     */
    GraphPipeline pipelined();

    /**
     * Perform watch over given Redis keys (Graphs names)
     * @param keys Redis keys (Graphs names)
     * @return "OK"
     */
    String watch(String... keys);

    /**
     * Removes watch from all keys
     * @return "OK"
     */
    String unwatch();
}
