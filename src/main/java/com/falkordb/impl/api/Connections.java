package com.falkordb.impl.api;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.Pool;

/**
 * Where a {@link DriverImpl} gets its connections from: one pool for a standalone or Sentinel-managed
 * server, or a set of per-node pools for a Redis Cluster.
 *
 * <p>The routing key is a graph name. A cluster hashes it to a slot and serves the connection from the
 * node that owns that slot; every other deployment ignores it.
 */
interface Connections {

    /**
     * Borrows a connection.
     *
     * @param routingKey the graph the connection will be used for, or {@code null} for no particular
     *     one (a cluster then picks any node)
     * @return a connection to close after use
     */
    Jedis get(@Nullable String routingKey);

    /**
     * Whether this is a Redis Cluster, where server-wide commands must reach every primary.
     *
     * @return {@code true} for a cluster
     */
    default boolean isCluster() {
        return false;
    }

    /**
     * Runs {@code operation} once on every primary and returns the results in node order. A
     * non-cluster deployment has a single node, so the operation runs once.
     *
     * @param operation the work to run on a borrowed connection
     * @param <T> the result type
     * @return one result per primary
     */
    default <T> List<T> onEachPrimary(Function<Jedis, T> operation) {
        try (Jedis connection = get(null)) {
            return Collections.singletonList(operation.apply(connection));
        }
    }

    /**
     * Tells the connection source that the server redirected a command or a node was unreachable, so
     * it can re-learn which node owns which slot. A no-op outside a cluster.
     */
    default void topologyChanged() {}

    /** Releases every pooled connection. */
    void close();

    /** The {@link Connections} of a standalone or Sentinel-managed server: a single Jedis pool. */
    final class Single implements Connections {

        private final Pool<Jedis> pool;

        Single(Pool<Jedis> pool) {
            this.pool = pool;
        }

        @Override
        public Jedis get(@Nullable String routingKey) {
            return pool.getResource();
        }

        @Override
        public void close() {
            pool.close();
        }
    }
}
