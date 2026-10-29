package com.falkordb.impl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisConnectionException;
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
    /** Like the node config but with a read deadline, so a silent node cannot hold the refresh lock forever. */
    private final DefaultJedisClientConfig refreshConfig;
    /** Makes the connection pool for a node. */
    private final Function<HostAndPort, JedisPool> newPool;

    private final ConcurrentMap<HostAndPort, JedisPool> pools = new ConcurrentHashMap<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    /** The slot table and primaries, replaced wholesale on refresh; {@code null} until the first succeeds. */
    private volatile @Nullable Topology topology;

    /** One consistent view of the cluster, so readers never see a slot table and primaries from different refreshes. */
    private static final class Topology {
        final HostAndPort[] slotOwners;
        final List<HostAndPort> primaries;

        Topology(HostAndPort[] slotOwners, List<HostAndPort> primaries) {
            this.slotOwners = slotOwners;
            this.primaries = primaries;
        }
    }

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
            Set<HostAndPort> startupNodes,
            GenericObjectPoolConfig<Jedis> poolConfig,
            DefaultJedisClientConfig nodeConfig) {
        this(startupNodes, nodeConfig, node -> new JedisPool(poolConfig, node, nodeConfig));
    }

    /**
     * Creates the connection source with its own way of making a node's pool, so that a test can act between a
     * pool being looked up and being borrowed from. No I/O happens until the first connection is requested.
     *
     * @param startupNodes where to learn the topology from; at least one
     * @param nodeConfig   client config the topology is learned with; must not select a database
     * @param newPool      makes the connection pool for a node
     */
    ClusterConnections(
            Set<HostAndPort> startupNodes,
            DefaultJedisClientConfig nodeConfig,
            Function<HostAndPort, JedisPool> newPool) {
        this.startupNodes = startupNodes;
        this.refreshConfig = DriverImpl.clusterProbeConfig(nodeConfig);
        this.newPool = newPool;
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
        Topology view = ensureTopology();
        long seen = generation.get();
        List<T> results = new ArrayList<>();
        try {
            for (HostAndPort node : view.primaries) {
                try (Jedis connection = poolFor(node).getResource()) {
                    results.add(operation.apply(connection));
                }
            }
        } catch (JedisConnectionException e) {
            // A dead primary may have been replaced; relearn so the next call does not hit the stale list.
            try {
                refresh(seen);
            } catch (JedisClusterOperationException refreshFailure) {
                e.addSuppressed(refreshFailure);
            }
            throw e;
        }
        return results;
    }

    @Override
    public void topologyChanged() {
        refresh(generation.get());
    }

    /**
     * Tries the primaries in turn, starting with the next one in round-robin order, until one answers. When
     * one can't be reached, the topology is relearned once, so a primary that failed over is replaced, and the
     * read moves on to a primary it hasn't tried yet. If the relearning fails, the read still tries the other
     * primaries it already knew, without relearning again: a primary that refuses new connections may still
     * answer on one its pool already holds. The read fails only when no primary answers, with the first failure
     * and the others, the failed relearning included, suppressed in it.
     */
    @Override
    public <T> T onAnyPrimary(Function<Jedis, T> read) {
        Topology view = ensureTopology();
        long seen = generation.get();
        int start = nextPrimary.getAndIncrement();
        Set<HostAndPort> tried = new HashSet<>();
        JedisConnectionException failure = null;
        boolean relearned = false;
        while (true) {
            HostAndPort node = untried(view.primaries, start, tried);
            if (node == null) {
                // Every primary was tried and failed: a topology always has one, and each failure was kept.
                throw Objects.requireNonNull(failure);
            }
            tried.add(node);
            try (Jedis connection = borrow(node)) {
                return read.apply(connection);
            } catch (JedisConnectionException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
                if (!relearned) {
                    relearned = true;
                    try {
                        view = refresh(seen);
                    } catch (JedisClusterOperationException refreshFailure) {
                        failure.addSuppressed(refreshFailure);
                    }
                }
            }
        }
    }

    /** The first primary not in {@code tried}, going round from position {@code start}. */
    static @Nullable HostAndPort untried(List<HostAndPort> primaries, int start, Set<HostAndPort> tried) {
        for (int i = 0; i < primaries.size(); i++) {
            // in long: start + i could wrap past Integer.MAX_VALUE and visit one primary twice, another never
            HostAndPort node = primaries.get((int) Math.floorMod((long) start + i, (long) primaries.size()));
            if (!tried.contains(node)) {
                return node;
            }
        }
        return null;
    }

    @Override
    public HostAndPort nodeOf(String routingKey) {
        return ownerOf(routingKey);
    }

    @Override
    public Jedis connectionTo(HostAndPort node, boolean asking) {
        Jedis connection = borrow(node);
        if (asking) {
            try {
                connection.asking();
            } catch (RuntimeException e) {
                connection.close();
                throw e;
            }
        }
        return connection;
    }

    /** The number of topologies learned so far; pass it to {@link #topologyChanged(long)}. */
    long generation() {
        return generation.get();
    }

    /** Relearns the topology unless another caller has already done so since {@code seenGeneration}. */
    void topologyChanged(long seenGeneration) {
        refresh(seenGeneration);
    }

    @Override
    public void close() {
        closed = true;
        for (JedisPool pool : pools.values()) {
            pool.close();
        }
        pools.clear();
    }

    /**
     * Borrows a connection to {@code node}. A refresh closes the idle pools of nodes that serve no slot: a primary
     * that was replaced, or an empty node importing its first slot, the target of an {@code ASK}. If a refresh closed
     * the pool between looking it up and borrowing from it, the borrow is retried on a new one. Jedis reports the closed
     * pool as a plain {@link JedisException}, which callers would otherwise not take for an unreachable node.
     */
    private Jedis borrow(HostAndPort node) {
        for (int attempt = 1; ; attempt++) {
            JedisPool pool = poolFor(node);
            try {
                return pool.getResource();
            } catch (JedisException e) {
                if (!pool.isClosed() || closed || attempt >= 3) {
                    throw e;
                }
            }
        }
    }

    private HostAndPort ownerOf(String graphId) {
        int slot = JedisClusterCRC16.getSlot(graphId);
        HostAndPort owner = ensureTopology().slotOwners[slot];
        if (owner == null) {
            // The slot was unassigned when we looked, which also happens mid-migration; look once more.
            owner = refresh(generation.get()).slotOwners[slot];
        }
        if (owner == null) {
            throw new JedisClusterOperationException(
                    "No cluster node serves slot " + slot + " (graph " + graphId + ")");
        }
        return owner;
    }

    private HostAndPort anyPrimary() {
        List<HostAndPort> known = ensureTopology().primaries;
        // Round-robin rather than random: it spreads keyless work just as evenly, and needs no randomness.
        return known.get(Math.floorMod(nextPrimary.getAndIncrement(), known.size()));
    }

    private Topology ensureTopology() {
        Topology current = topology;
        return current != null ? current : refresh(generation.get());
    }

    /**
     * Returns the pool of {@code node}, creating it on first use.
     *
     * @throws IllegalStateException if the driver is closed
     */
    private JedisPool poolFor(HostAndPort node) {
        if (closed) {
            throw new IllegalStateException("the driver is closed");
        }
        JedisPool pool = pools.computeIfAbsent(node, newPool);
        if (closed) {
            // close() may have swept the map before this pool was added; do not leak it.
            pools.remove(node, pool);
            pool.close();
            throw new IllegalStateException("the driver is closed");
        }
        return pool;
    }

    /**
     * Relearns the slot table from the first node that answers.
     *
     * @param seenGeneration the generation the caller observed; if another thread has refreshed since,
     *     that result is reused instead of asking the cluster again
     * @return the current topology
     * @throws JedisClusterOperationException if no node can be reached or none reports a slot
     */
    private Topology refresh(long seenGeneration) {
        refreshLock.lock();
        try {
            Topology current = topology;
            if (current != null && generation.get() != seenGeneration) {
                return current;
            }
            JedisException lastFailure = null;
            for (HostAndPort node : candidates()) {
                try (Jedis connection = new Jedis(node, refreshConfig)) {
                    HostAndPort[] table = Clusters.parseSlots(connection.clusterSlots(), node);
                    List<HostAndPort> owners = Clusters.primaries(table);
                    if (owners.isEmpty()) {
                        continue;
                    }
                    return publish(table, owners);
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

    /**
     * Makes a slot table just learned the current topology, as a new {@linkplain #generation() generation}, and
     * closes the pools of nodes that own no slot in it, unless some of their connections are out.
     */
    private Topology publish(HostAndPort[] table, List<HostAndPort> owners) {
        Topology fresh = new Topology(table, owners);
        topology = fresh;
        generation.incrementAndGet();
        Set<HostAndPort> live = new HashSet<>(owners);
        for (Map.Entry<HostAndPort, JedisPool> entry : pools.entrySet()) {
            JedisPool pool = entry.getValue();
            // A node that serves no slot can still be in use: an empty node importing its first slot is the target
            // of every ASK for it. Its pool stays while connections are out, so they and the next ones share its
            // limit, and goes at a later refresh once it is idle.
            if (!live.contains(entry.getKey()) && pool.getNumActive() == 0) {
                pools.remove(entry.getKey(), pool);
                pool.close();
            }
        }
        return fresh;
    }

    private List<HostAndPort> currentPrimaries() {
        Topology current = topology;
        return current == null ? Collections.emptyList() : current.primaries;
    }

    /** Known primaries first (they are the likeliest to be up to date), then the startup nodes. */
    private List<HostAndPort> candidates() {
        Set<HostAndPort> order = new LinkedHashSet<>(currentPrimaries());
        order.addAll(startupNodes);
        return new ArrayList<>(order);
    }
}
