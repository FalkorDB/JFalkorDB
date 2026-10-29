package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

class ClusterConnectionsTest {

    private FakeCluster cluster;
    private ClusterConnections connections;

    @AfterEach
    void tearDown() {
        if (connections != null) {
            connections.close();
        }
        if (cluster != null) {
            cluster.close();
        }
    }

    private static DefaultJedisClientConfig config() {
        return DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(1000)
                .socketTimeoutMillis(2000)
                .autoNegotiateProtocol(false)
                .build();
    }

    private static Set<HostAndPort> startup(List<String> addresses) {
        Set<HostAndPort> nodes = new LinkedHashSet<>();
        for (String address : addresses) {
            nodes.add(HostAndPort.from(address));
        }
        return nodes;
    }

    private ClusterConnections connect(Set<HostAndPort> startupNodes) {
        connections = new ClusterConnections(startupNodes, new GenericObjectPoolConfig<Jedis>(), config());
        return connections;
    }

    /** Which fake node a connection landed on. */
    private static String nodeOf(Jedis connection) {
        try (Jedis c = connection) {
            return c.echo("who");
        }
    }

    @Test
    void sendsEachGraphToTheNodeThatOwnsItsSlot() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));

        assertEquals("0", nodeOf(connections.get(FakeCluster.lowGraph())));
        assertEquals("1", nodeOf(connections.get(FakeCluster.highGraph())));
    }

    @Test
    void aSingleSeedIsEnoughToLearnTheWholeTopology() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses().subList(0, 1)));

        assertEquals("1", nodeOf(connections.get(FakeCluster.highGraph())));
        assertEquals("0", nodeOf(connections.get(FakeCluster.lowGraph())));
    }

    @Test
    void theTopologyIsLearnedOnceNotPerCommand() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));

        for (int i = 0; i < 20; i++) {
            nodeOf(connections.get(FakeCluster.lowGraph()));
            nodeOf(connections.get(FakeCluster.highGraph()));
        }

        long lookups =
                cluster.node(0).count("CLUSTER", "SLOTS") + cluster.node(1).count("CLUSTER", "SLOTS");
        assertEquals(1, lookups);
    }

    @Test
    void noRoutingKeyMeansAnyPrimary() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));

        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < 200 && seen.size() < 2; i++) {
            seen.add(nodeOf(connections.get(null)));
        }
        assertEquals(new LinkedHashSet<>(Arrays.asList("0", "1")), seen);
    }

    @Test
    void runsAnOperationOnEveryPrimaryOnce() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));

        List<String> visited = connections.onEachPrimary(c -> c.echo("who"));

        assertEquals(Arrays.asList("0", "1"), visited);
    }

    @Test
    void aNodeOwningSeveralRangesIsVisitedOnce() {
        cluster = new FakeCluster(2, null);
        cluster.assign(1, 1);
        connect(startup(cluster.addresses()));

        assertEquals(Collections.singletonList("1"), connections.onEachPrimary(c -> c.echo("who")));
    }

    @Test
    void relearnsWhenTheTopologyChanges() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));
        assertEquals("0", nodeOf(connections.get(FakeCluster.lowGraph())));

        cluster.assign(1, 0); // the two halves swap owners
        connections.topologyChanged();

        assertEquals("1", nodeOf(connections.get(FakeCluster.lowGraph())));
        assertEquals("0", nodeOf(connections.get(FakeCluster.highGraph())));
    }

    @Test
    void relearningFallsBackToTheStartupNodesWhenAPrimaryIsGone() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));
        nodeOf(connections.get(FakeCluster.lowGraph()));

        cluster.node(0).close();
        cluster.assign(1, 1); // the survivor now owns everything
        connections.topologyChanged();

        assertEquals("1", nodeOf(connections.get(FakeCluster.lowGraph())));
    }

    @Test
    void concurrentRelearnsAreCoalesced() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));
        nodeOf(connections.get(FakeCluster.lowGraph()));
        long before =
                cluster.node(0).count("CLUSTER", "SLOTS") + cluster.node(1).count("CLUSTER", "SLOTS");

        // Two callers that both saw the same generation: only the first should hit the cluster.
        long seen = connections.generation();
        Thread[] callers = new Thread[2];
        for (int i = 0; i < callers.length; i++) {
            callers[i] = new Thread(() -> connections.topologyChanged(seen));
            callers[i].start();
        }
        for (Thread caller : callers) {
            try {
                caller.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        long after = cluster.node(0).count("CLUSTER", "SLOTS") + cluster.node(1).count("CLUSTER", "SLOTS");
        assertEquals(before + 1, after);
    }

    @Test
    void nodesReportedWithoutAHostAreReachedAtTheAddressThatAnswered() {
        cluster = new FakeCluster(1, null);
        cluster.reportEmptyHosts(true);
        connect(startup(cluster.addresses()));

        assertEquals("0", nodeOf(connections.get(FakeCluster.highGraph())));
    }

    @Test
    void aDeadPrimaryInAClusterWideCallTriggersARelearn() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));
        assertEquals(2, connections.onEachPrimary(c -> c.echo("x")).size());
        long before = cluster.node(1).count("CLUSTER", "SLOTS");

        cluster.node(0).close();
        assertThrows(JedisConnectionException.class, () -> connections.onEachPrimary(c -> c.echo("x")));

        assertTrue(cluster.node(1).count("CLUSTER", "SLOTS") > before, "the survivor was asked for the layout");
    }

    @Test
    void aClusterWideCallKeepsItsFailureWhenTheRelearnFailsToo() {
        cluster = new FakeCluster(1, null);
        connect(startup(cluster.addresses()));
        connections.onEachPrimary(c -> c.echo("x"));

        cluster.node(0).close();
        JedisConnectionException e =
                assertThrows(JedisConnectionException.class, () -> connections.onEachPrimary(c -> c.echo("x")));

        assertTrue(e.getSuppressed().length > 0);
    }

    @Test
    void noConnectionIsHandedOutOnceClosed() {
        cluster = new FakeCluster(1, null);
        connect(startup(cluster.addresses()));
        connections.get("g").close();
        connections.close();

        assertThrows(IllegalStateException.class, () -> connections.get("g"));
    }

    @Test
    void aSingleSourceIgnoresTopologyChanges() {
        Connections.Single single = new Connections.Single(new JedisPool("127.0.0.1", 1));
        single.topologyChanged();
        single.close();
    }

    @Test
    void aSingleSourceHasNoOtherNodeToConnectTo() {
        Connections.Single single = new Connections.Single(new JedisPool("127.0.0.1", 1));
        try {
            assertNull(single.nodeOf("graph"));
            UnsupportedOperationException e = assertThrows(
                    UnsupportedOperationException.class,
                    () -> single.connectionTo(HostAndPort.from("127.0.0.1:1"), false));
            assertEquals("only a Redis Cluster has several nodes", e.getMessage());
        } finally {
            single.close();
        }
    }

    @Test
    void aRefreshKeepsThePoolOfANodeWithoutSlotsWhileItsConnectionsAreOut() {
        // Node 1 owns no slot, like an empty node importing its first one. Closing its pool while an ASK attempt
        // holds a connection would make the next attempt open another pool, past the node's limit.
        cluster = new FakeCluster(2, null);
        cluster.assign(0, 0);
        HostAndPort node1 = HostAndPort.from(cluster.node(1).address());
        Pools pools = new Pools(node1, 0, () -> {});
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        try (Jedis held = connections.connectionTo(node1, true)) {
            connections.topologyChanged();
            try (Jedis next = connections.connectionTo(node1, true)) {
                assertEquals("1", next.echo("who"));
            }
            assertEquals(1, pools.made.size(), "both borrows shared the node's pool");
            assertFalse(pools.made.get(0).isClosed());
        }

        connections.topologyChanged();
        assertTrue(pools.made.get(0).isClosed(), "once idle, the pool went at the next refresh");
    }

    @Test
    void aConnectionWhoseAskingFailsGoesBackToItsPool() {
        cluster = new FakeCluster(
                2,
                (node, command) -> node == 1 && FakeRedisServer.startsWith(command, "ASKING")
                        ? FakeRedisServer.error("ERR not now")
                        : null);
        cluster.assign(0, 0);
        HostAndPort node1 = HostAndPort.from(cluster.node(1).address());
        Pools pools = new Pools(node1, 0, () -> {});
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        assertThrows(JedisDataException.class, () -> connections.connectionTo(node1, true));

        assertEquals(1, pools.made.size());
        assertEquals(0, pools.made.get(0).getNumActive(), "the connection was returned");
    }

    @Test
    void failsClearlyWhenNoStartupNodeAnswers() {
        connect(startup(Arrays.asList("127.0.0.1:1", "127.0.0.1:2")));

        JedisClusterOperationException e =
                assertThrows(JedisClusterOperationException.class, () -> connections.get("graph"));
        assertTrue(e.getMessage().contains("127.0.0.1:1"), e.getMessage());
    }

    @Test
    void failsClearlyWhenTheClusterAssignsNoSlots() {
        try (FakeRedisServer empty = new FakeRedisServer(c ->
                FakeRedisServer.startsWith(c, "CLUSTER", "SLOTS") ? FakeRedisServer.array() : FakeRedisServer.ok())) {
            connect(startup(Collections.singletonList(empty.address())));
            assertThrows(JedisClusterOperationException.class, () -> connections.get("graph"));
        }
    }

    @Test
    void anUnassignedSlotIsReportedNotGuessed() {
        // Only the lower half is assigned; the server needs its own port for the reply.
        FakeRedisServer[] holder = new FakeRedisServer[1];
        holder[0] = new FakeRedisServer(c -> FakeRedisServer.startsWith(c, "CLUSTER", "SLOTS")
                ? FakeRedisServer.array(FakeRedisServer.slotRange(0, 8191, "127.0.0.1", holder[0].port()))
                : FakeRedisServer.ok());
        try {
            connect(startup(Collections.singletonList(holder[0].address())));
            JedisClusterOperationException e =
                    assertThrows(JedisClusterOperationException.class, () -> connections.get(FakeCluster.highGraph()));
            assertTrue(e.getMessage().contains("No cluster node serves slot"), e.getMessage());
        } finally {
            holder[0].close();
        }
    }

    @Test
    void aClosedSourceHandsOutNothing() {
        cluster = new FakeCluster(2, null);
        connect(startup(cluster.addresses()));
        nodeOf(connections.get(FakeCluster.lowGraph()));

        connections.close();

        assertThrows(IllegalStateException.class, () -> connections.get(FakeCluster.lowGraph()));
    }

    @Test
    void identifiesItselfAsACluster() {
        connect(startup(Collections.singletonList("127.0.0.1:1")));
        assertTrue(connections.isCluster());
    }

    @Test
    void creatingTheSourceDoesNoIo() {
        connect(startup(Collections.singletonList("127.0.0.1:1")));
        // No exception, and nothing to connect to: construction is lazy.
        connections.close();
    }

    // --- a refresh that closes a pool between looking it up and borrowing from it -----------------

    @Test
    void aReadOnAnyPrimaryBorrowsFromANewPoolWhenARefreshClosedTheOneItGot() {
        // A refresh that finds node 0 owning no slot closes node 0's pool after the read has looked it up.
        // Jedis reports borrowing from a closed pool as a plain JedisException, not as an unreachable node.
        cluster = new FakeCluster(2, null);
        Pools pools = new Pools(HostAndPort.from(cluster.node(0).address()), 1, () -> {
            cluster.assign(1, 1);
            connections.topologyChanged();
        });
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        String answeredBy = connections.onAnyPrimary(c -> c.echo("who")); // the round robin starts at node 0

        assertEquals("0", answeredBy, "the read borrowed from a new pool to the node it had picked");
        assertEquals(2, pools.made.size());
        assertTrue(pools.made.get(0).isClosed(), "the refresh closed the pool the read had looked up");
    }

    @Test
    void anAskTargetIsBorrowedFromANewPoolWhenARefreshClosedTheOneItGot() {
        // Node 1 owns no slot, like an empty node importing its first one, so every refresh closes its pool.
        cluster = new FakeCluster(2, null);
        cluster.assign(0, 0);
        HostAndPort node1 = HostAndPort.from(cluster.node(1).address());
        Pools pools = new Pools(node1, 1, () -> connections.topologyChanged());
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        try (Jedis connection = connections.connectionTo(node1, true)) {
            assertEquals("1", connection.echo("who"));
        }

        assertEquals(2, pools.made.size());
        assertTrue(pools.made.get(0).isClosed(), "the refresh closed the pool the borrow had looked up");
        assertEquals(1, cluster.node(1).count("ASKING"));
    }

    @Test
    void aBorrowGivesUpWhenEveryNewPoolIsClosedToo() {
        cluster = new FakeCluster(2, null);
        cluster.assign(0, 0);
        HostAndPort node1 = HostAndPort.from(cluster.node(1).address());
        Pools pools = new Pools(node1, Integer.MAX_VALUE, () -> connections.topologyChanged());
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        JedisException e = assertTimeoutPreemptively(
                Duration.ofSeconds(10),
                () -> assertThrows(JedisException.class, () -> connections.connectionTo(node1, false)));

        assertTrue(e.getCause() instanceof IllegalStateException, "commons-pool's closed pool: " + e.getCause());
        assertEquals(3, pools.made.size(), "three pools, then the borrow gave up");
    }

    @Test
    void aBorrowIsNotRetriedOnceTheSourceIsClosed() {
        cluster = new FakeCluster(2, null);
        Pools pools = new Pools(HostAndPort.from(cluster.node(0).address()), 1, () -> connections.close());
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        // The closed pool's own failure, not the "driver is closed" a retry would run into.
        assertThrows(JedisException.class, () -> connections.onAnyPrimary(c -> c.echo("who")));

        assertEquals(1, pools.made.size(), "no pool was made after the source was closed");
    }

    /**
     * Makes the node pools: plain ones, except that the first {@code interrupted} pools for {@code node} run
     * {@code beforeFirstBorrow} when they are first borrowed from. It records the pools it makes for {@code node}.
     */
    private static final class Pools implements Function<HostAndPort, JedisPool> {
        private final HostAndPort node;
        private final int interrupted;
        private final Runnable beforeFirstBorrow;
        final List<JedisPool> made = new ArrayList<>();

        Pools(HostAndPort node, int interrupted, Runnable beforeFirstBorrow) {
            this.node = node;
            this.interrupted = interrupted;
            this.beforeFirstBorrow = beforeFirstBorrow;
        }

        @Override
        public JedisPool apply(HostAndPort target) {
            if (!target.equals(node)) {
                return new JedisPool(new GenericObjectPoolConfig<Jedis>(), target, config());
            }
            JedisPool pool = made.size() < interrupted
                    ? new InterruptedPool(target, beforeFirstBorrow)
                    : new JedisPool(new GenericObjectPoolConfig<Jedis>(), target, config());
            made.add(pool);
            return pool;
        }
    }

    /**
     * A pool that runs an action when it is first borrowed from: after the source has looked it up, and before
     * the borrow reaches commons-pool. That is where a refresh on another thread can close it.
     */
    private static final class InterruptedPool extends JedisPool {
        private final Runnable beforeFirstBorrow;
        private boolean borrowed;

        InterruptedPool(HostAndPort node, Runnable beforeFirstBorrow) {
            super(new GenericObjectPoolConfig<Jedis>(), node, config());
            this.beforeFirstBorrow = beforeFirstBorrow;
        }

        @Override
        public Jedis getResource() {
            if (!borrowed) {
                borrowed = true;
                beforeFirstBorrow.run();
            }
            return super.getResource();
        }
    }
}
