package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    void aReadWhoseRelearningFailsTriesEachPrimaryOnce() {
        // Every node is down, so relearning fails too: the read tries each primary it knew once, not a second time.
        cluster = new FakeCluster(2, null);
        AtomicInteger borrows = new AtomicInteger();
        connections = new ClusterConnections(
                startup(cluster.addresses()),
                config(),
                target -> new JedisPool(new GenericObjectPoolConfig<Jedis>(), target, config()) {
                    @Override
                    public Jedis getResource() {
                        borrows.incrementAndGet();
                        return super.getResource();
                    }
                });
        connections.topologyChanged(); // learns both primaries
        cluster.node(0).close();
        cluster.node(1).close();

        JedisConnectionException e =
                assertThrows(JedisConnectionException.class, () -> connections.onAnyPrimary(c -> c.echo("who")));

        assertEquals(2, borrows.get(), "each primary was tried once");
        assertTrue(
                Arrays.stream(e.getSuppressed()).anyMatch(s -> s instanceof JedisClusterOperationException),
                "the failed relearning is attached: " + Arrays.toString(e.getSuppressed()));
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

    // --- a refresh while a pool is being borrowed from --------------------------------------------

    @Test
    void aRefreshDuringAReadOnAnyPrimaryKeepsThePoolItBorrowsFrom() {
        // A refresh that finds node 0 owning no slot runs while the read borrows from node 0's pool. Closing the
        // pool then would leave the read's connection out of a closed pool, and the next borrow would open another.
        cluster = new FakeCluster(2, null);
        Pools pools = new Pools(HostAndPort.from(cluster.node(0).address()), 1, () -> {
            cluster.assign(1, 1);
            connections.topologyChanged();
        });
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        String answeredBy = connections.onAnyPrimary(c -> c.echo("who")); // the round robin starts at node 0

        assertEquals("0", answeredBy);
        assertEquals(1, pools.made.size(), "the read kept the pool it was borrowing from");
        assertFalse(pools.made.get(0).isClosed());

        connections.topologyChanged();
        assertTrue(pools.made.get(0).isClosed(), "once idle, the pool went at the next refresh");
    }

    @Test
    void aRefreshDuringABorrowOfAnAskTargetKeepsItsPool() {
        // Node 1 owns no slot, like an empty node importing its first one, so a refresh closes its pool once idle.
        cluster = new FakeCluster(2, null);
        cluster.assign(0, 0);
        HostAndPort node1 = HostAndPort.from(cluster.node(1).address());
        Pools pools = new Pools(node1, 1, () -> connections.topologyChanged());
        connections = new ClusterConnections(startup(cluster.addresses()), config(), pools);

        try (Jedis connection = connections.connectionTo(node1, true)) {
            assertEquals("1", connection.echo("who"));
            assertEquals(1, pools.made.size());
            assertFalse(pools.made.get(0).isClosed(), "the refresh kept the pool the borrow was using");
        }
        assertEquals(1, cluster.node(1).count("ASKING"));

        connections.topologyChanged();
        assertTrue(pools.made.get(0).isClosed(), "once idle, the pool went at the next refresh");
    }

    @Test
    void aBorrowThatStartsWhileARefreshClosesThePoolMovesToANewOne() {
        // The refresh finds node 1's pool idle. A borrow that starts right then must not get a connection from the
        // pool the refresh is about to close: the next borrow would open a second pool to node 1, past its limit.
        cluster = new FakeCluster(2, null);
        cluster.assign(0, 0);
        HostAndPort node1 = HostAndPort.from(cluster.node(1).address());
        List<JedisPool> made = new ArrayList<>();
        AtomicReference<Jedis> racer = new AtomicReference<>();
        AtomicReference<RuntimeException> racerFailure = new AtomicReference<>();
        connections = new ClusterConnections(startup(cluster.addresses()), config(), target -> {
            if (!target.equals(node1)) {
                return new JedisPool(new GenericObjectPoolConfig<Jedis>(), target, config());
            }
            JedisPool pool = made.isEmpty()
                    ? new RacingPool(target, () -> {
                        try {
                            racer.set(connections.connectionTo(node1, false));
                        } catch (RuntimeException e) {
                            racerFailure.set(e);
                        }
                    })
                    : new JedisPool(new GenericObjectPoolConfig<Jedis>(), target, config());
            made.add(pool);
            return pool;
        });
        connections.topologyChanged(); // learns the topology
        connections.connectionTo(node1, false).close(); // makes node 1's pool, idle

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            RacingPool first = (RacingPool) made.get(0);
            first.armed = true;
            connections.topologyChanged(); // its idle check starts the racing borrow
            first.racer.join();
        });

        assertNull(racerFailure.get(), () -> "the racing borrow failed: " + racerFailure.get());
        try (Jedis connection = racer.get()) {
            assertEquals("1", connection.echo("who"));
            assertEquals(2, made.size(), "the borrow moved to a new pool");
            assertTrue(made.get(0).isClosed());
            assertEquals(0, made.get(0).getNumActive(), "no connection is out of the closed pool");
            assertEquals(1, made.get(1).getNumActive());
        }
    }

    // --- reads any primary can answer --------------------------------------------------------------

    @Test
    void aReadRetriesARestartedPrimaryOnANewConnection() throws Exception {
        // The only primary restarted: every connection its pool holds is gone, but the relearned topology still
        // names it, and a new connection reaches it.
        cluster = new FakeCluster(1, null);
        connect(startup(cluster.addresses()));
        try (Jedis first = connections.get(null);
                Jedis second = connections.get(null)) {
            assertEquals("0", first.echo("who"));
            assertEquals("0", second.echo("who"));
        } // two idle connections in the pool
        cluster.node(0).dropClients();

        assertEquals("0", connections.onAnyPrimary(c -> c.echo("who")));
        assertEquals(2, cluster.node(0).count("CLUSTER", "SLOTS"), "the topology was relearned once");

        // The failure dropped the pool's other broken connections too, so two reads at once both get working ones.
        try (Jedis first = connections.get(null);
                Jedis second = connections.get(null)) {
            assertEquals("0", first.echo("who"));
            assertEquals("0", second.echo("who"));
        }
    }

    @Test
    void aReadTriesADeadPrimaryOnceWhenAnotherAnswers() {
        // Node 1 is down but the topology still lists it, as before a failover completes. Each attempt on it can
        // cost a connect timeout, so a read that has another primary to try doesn't try node 1 again.
        cluster = new FakeCluster(2, null);
        HostAndPort node1 = HostAndPort.from(cluster.node(1).address());
        AtomicInteger borrowsFromNode1 = new AtomicInteger();
        connections = new ClusterConnections(startup(cluster.addresses()), config(), target -> {
            if (!target.equals(node1)) {
                return new JedisPool(new GenericObjectPoolConfig<Jedis>(), target, config());
            }
            return new JedisPool(new GenericObjectPoolConfig<Jedis>(), target, config()) {
                @Override
                public Jedis getResource() {
                    borrowsFromNode1.incrementAndGet();
                    return super.getResource();
                }
            };
        });
        connections.topologyChanged(); // learns both primaries
        cluster.node(1).close();

        for (int read = 1; read <= 4; read++) { // the round robin starts half of them at node 1
            int before = borrowsFromNode1.get();
            assertEquals("0", connections.onAnyPrimary(c -> c.echo("who")));
            assertTrue(borrowsFromNode1.get() - before <= 1, "node 1 was tried more than once in read " + read);
        }
        assertTrue(borrowsFromNode1.get() > 0, "no read started at node 1");
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
     * A pool whose idle check, once {@link #armed}, runs {@code race} on another thread and waits until it has
     * borrowed or is blocked starting its borrow, then answers with the count from before {@code race} ran. That
     * is the moment a refresh has found the pool idle and is about to close it.
     */
    private static final class RacingPool extends JedisPool {
        private final Runnable race;
        volatile boolean armed;
        /** The thread {@code race} runs on, once started. */
        volatile Thread racer;

        RacingPool(HostAndPort node, Runnable race) {
            super(new GenericObjectPoolConfig<Jedis>(), node, config());
            this.race = race;
        }

        @Override
        public int getNumActive() {
            int active = super.getNumActive();
            if (!armed) {
                return active;
            }
            armed = false;
            Thread racer = new Thread(race, "racing-borrow");
            this.racer = racer;
            racer.start();
            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            while (racer.isAlive() && !blockedOnANodePool(threads, racer)) {
                Thread.yield();
            }
            return active;
        }

        /** Whether {@code thread} waits for the monitor of the source's per-node pool. */
        private static boolean blockedOnANodePool(ThreadMXBean threads, Thread thread) {
            ThreadInfo info = threads.getThreadInfo(thread.getId());
            LockInfo lock = info == null ? null : info.getLockInfo();
            return info != null
                    && info.getThreadState() == Thread.State.BLOCKED
                    && lock != null
                    && lock.getClassName().equals(ClusterConnections.class.getName() + "$NodePool");
        }
    }

    /**
     * A pool that runs an action when it is first borrowed from: after the borrow has started, and before it
     * reaches commons-pool. That is where a refresh on another thread could close it.
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
