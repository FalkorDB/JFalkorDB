package com.falkordb.impl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.JedisClusterCRC16;

/**
 * {@link Connections} for a Redis Cluster: one {@link JedisPool} per primary, and a slot table that
 * says which primary owns which graph.
 *
 * <p>Every graph is a single key, so each command on a graph lands on exactly one node, and a pipeline
 * or {@code MULTI} transaction on a graph stays on that node's connection. That is why plain Jedis
 * connections are enough here and no cluster-wide client is needed: the slot table is the whole of
 * the routing.
 *
 * <p>The table is learned on first use from {@code CLUSTER SLOTS} — asking the known primaries first
 * and the startup nodes after — and relearned when {@link #topologyChanged()} reports a redirect or
 * an unreachable node. Pool sizing applies to each node separately.
 *
 * <p>Locking: refreshes take a {@link ReentrantLock} rather than a monitor, because they do network
 * I/O and a monitor would pin a virtual thread's carrier. Reads of the table take no lock.
 */
final class ClusterConnections implements Connections {

    private final Set<HostAndPort> startupNodes;
    private final GenericObjectPoolConfig<Jedis> poolConfig;
    private final JedisClientConfig nodeConfig;

    private final ConcurrentMap<HostAndPort, JedisPool> pools = new ConcurrentHashMap<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    /** The slot table, replaced wholesale on refresh; {@code null} until the first one succeeds. */
    private volatile HostAndPort @Nullable [] slotOwners;

    private volatile List<HostAndPort> primaries = Collections.emptyList();

    /** Bumped by every successful refresh, so concurrent callers can tell theirs is already done. */
    private final AtomicLong generation = new AtomicLong();

    /** Rotates through the primaries for commands that name no graph. */
    private final AtomicInteger nextPrimary = new AtomicInteger();

    private volatile boolean closed;

    /**
     * Creates the connection source. No I/O happens until the first connection is requested.
     *
     * @param startupNodes where to learn the topology from; at least one
     * @param poolConfig   commons-pool2 sizing applied to every node's pool
     * @param nodeConfig   client config for every node connection; must not select a database, which
     *                     a cluster does not support
     */
    ClusterConnections(
            Set<HostAndPort> startupNodes, GenericObjectPoolConfig<Jedis> poolConfig, JedisClientConfig nodeConfig) {
        this.startupNodes = startupNodes;
        this.poolConfig = poolConfig;
        this.nodeConfig = nodeConfig;
    }

    @Override
    public boolean isCluster() {
        return true;
    }

    @Override
    public Jedis get(@Nullable String routingKey) {
        return poolFor(routingKey == null ? anyPrimary() : ownerOf(routingKey)).getResource();
    }

    @Override
    public <T> List<T> onEachPrimary(Function<Jedis, T> operation) {
        ensureTopology();
        List<T> results = new ArrayList<>();
        for (HostAndPort node : primaries) {
            try (Jedis connection = poolFor(node).getResource()) {
                results.add(operation.apply(connection));
            }
        }
        return results;
    }

    @Override
    public void topologyChanged() {
        refresh(generation.get());
    }

    @Override
    public void close() {
        closed = true;
        for (JedisPool pool : pools.values()) {
            pool.close();
        }
        pools.clear();
    }

    private HostAndPort ownerOf(String graphId) {
        int slot = JedisClusterCRC16.getSlot(graphId);
        HostAndPort[] table = ensureTopology();
        HostAndPort owner = table[slot];
        if (owner == null) {
            // The slot was unassigned when we looked, which also happens mid-migration; look once more.
            table = refresh(generation.get());
            owner = table[slot];
        }
        if (owner == null) {
            throw new JedisClusterOperationException(
                    "No cluster node serves slot " + slot + " (graph " + graphId + ")");
        }
        return owner;
    }

    private HostAndPort anyPrimary() {
        ensureTopology();
        List<HostAndPort> known = primaries;
        // Round-robin rather than random: it spreads keyless work just as evenly, and needs no randomness.
        return known.get(Math.floorMod(nextPrimary.getAndIncrement(), known.size()));
    }

    private HostAndPort[] ensureTopology() {
        HostAndPort[] table = slotOwners;
        return table != null ? table : refresh(generation.get());
    }

    private JedisPool poolFor(HostAndPort node) {
        if (closed) {
            throw new IllegalStateException("the driver is closed");
        }
        return pools.computeIfAbsent(node, n -> new JedisPool(poolConfig, n, nodeConfig));
    }

    /**
     * Relearns the slot table from the first node that answers.
     *
     * @param seenGeneration the generation the caller observed; if another thread has refreshed since,
     *     that result is reused instead of asking the cluster again
     * @return the current slot table
     * @throws JedisClusterOperationException if no node can be reached or none reports a slot
     */
    private HostAndPort[] refresh(long seenGeneration) {
        refreshLock.lock();
        try {
            HostAndPort[] current = slotOwners;
            if (current != null && generation.get() != seenGeneration) {
                return current;
            }
            JedisException lastFailure = null;
            for (HostAndPort node : candidates()) {
                try (Jedis connection = new Jedis(node, nodeConfig)) {
                    HostAndPort[] table = Clusters.parseSlots(connection.clusterSlots(), node);
                    List<HostAndPort> owners = Clusters.primaries(table);
                    if (owners.isEmpty()) {
                        continue;
                    }
                    publish(table, owners);
                    return table;
                } catch (JedisException e) {
                    lastFailure = e;
                }
            }
            throw new JedisClusterOperationException(
                    "Could not learn the cluster topology from any of " + candidates(), lastFailure);
        } finally {
            refreshLock.unlock();
        }
    }

    private void publish(HostAndPort[] table, List<HostAndPort> owners) {
        slotOwners = table;
        primaries = owners;
        generation.incrementAndGet();
        Set<HostAndPort> live = new HashSet<>(owners);
        for (HostAndPort node : new ArrayList<>(pools.keySet())) {
            if (!live.contains(node)) {
                JedisPool gone = pools.remove(node);
                if (gone != null) {
                    gone.close();
                }
            }
        }
    }

    /** Known primaries first (they are the likeliest to be up to date), then the startup nodes. */
    private List<HostAndPort> candidates() {
        Set<HostAndPort> order = new LinkedHashSet<>(primaries);
        order.addAll(startupNodes);
        return new ArrayList<>(order);
    }
}
