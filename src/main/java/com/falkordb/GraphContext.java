package com.falkordb;

/**
 * An interface for a FalkorDB graph with context.
 *
 * <p>A context runs all its commands on one connection, borrowed from the driver's connection pool
 * when the context is created. The connection stays bound to the context until {@link #close()} is
 * called, even if a command fails: after a command the server rejects (reported as a
 * {@link com.falkordb.exceptions.GraphException}) the context remains usable. Always close the context,
 * for example with try-with-resources, to return its connection to the pool:
 *
 * <pre>{@code
 * try (GraphContext context = driver.graph("social").getContext()) {
 *     context.query("CREATE (:person {name: 'roi'})");
 * }
 * }</pre>
 */
public interface GraphContext extends Graph {

    /**
     * Returns a Redis transactional object, over the connection context, with graph API capabilities
     * @return Redis transactional object, over the connection context, with graph API capabilities
     */
    GraphTransaction multi();

    /**
     * Returns a Redis pipeline object, over the connection context, with graph API capabilities
     * @return Redis pipeline object, over the connection context, with graph API capabilities
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
