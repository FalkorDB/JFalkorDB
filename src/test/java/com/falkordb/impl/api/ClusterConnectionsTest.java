package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisConnectionException;

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
}
