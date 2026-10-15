package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;

class ClustersTest {

    private static final HostAndPort ANSWERER = new HostAndPort("seed.example", 7000);

    // --- isClusterInfo ---------------------------------------------------------------------------

    @Test
    void recognisesClusterMode() {
        assertTrue(Clusters.isClusterInfo("# Server\r\nredis_version:7.2.0\r\nredis_mode:cluster\r\nos:Linux\r\n"));
        assertTrue(Clusters.isClusterInfo("redis_mode:CLUSTER"));
        assertTrue(Clusters.isClusterInfo("# Server\nredis_mode: cluster \n"));
    }

    @Test
    void otherModesAreNotACluster() {
        assertFalse(Clusters.isClusterInfo("redis_mode:standalone\r\n"));
        assertFalse(Clusters.isClusterInfo("redis_mode:sentinel\r\n"));
        assertFalse(Clusters.isClusterInfo(""));
        assertFalse(Clusters.isClusterInfo(null));
    }

    @Test
    void aClusterWordElsewhereInTheReplyIsNotTheMode() {
        assertFalse(Clusters.isClusterInfo("config_file:/etc/redis_mode:cluster.conf\r\nredis_mode:standalone\r\n"));
        assertFalse(Clusters.isClusterInfo("cluster_enabled:1\r\n"));
    }

    // --- parseNode -------------------------------------------------------------------------------

    @Test
    void parsesNodeAddresses() {
        assertEquals(new HostAndPort("node-a", 6379), Clusters.parseNode("node-a:6379"));
        assertEquals(new HostAndPort("node-a", 6379), Clusters.parseNode("  node-a:6379  "));
        assertEquals(6380, Clusters.parseNode("[::1]:6380").getPort());
    }

    @Test
    void rejectsMalformedNodeAddresses() {
        for (String bad : Arrays.asList(null, "", "   ", "nohost", ":6379", "host:0", "host:70000", "host:abc")) {
            assertThrows(IllegalArgumentException.class, () -> Clusters.parseNode(bad), String.valueOf(bad));
        }
    }

    @Test
    void theErrorNamesTheOffendingAddress() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> Clusters.parseNode("not-an-address"));
        assertTrue(e.getMessage().contains("not-an-address"), e.getMessage());
    }

    // --- parseSlots ------------------------------------------------------------------------------

    private static List<Object> entry(long first, long last, Object... nodes) {
        List<Object> entry = new ArrayList<>();
        entry.add(first);
        entry.add(last);
        entry.addAll(Arrays.asList(nodes));
        return entry;
    }

    private static List<Object> node(Object host, long port) {
        return new ArrayList<>(Arrays.asList(host, port, "id".getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void mapsEverySlotToItsPrimary() {
        List<Object> reply = Arrays.asList(
                entry(0, 5460, node(bytes("10.0.0.1"), 7001), node(bytes("10.0.0.4"), 7004)),
                entry(5461, 10922, node(bytes("10.0.0.2"), 7002)),
                entry(10923, 16383, node(bytes("10.0.0.3"), 7003)));

        HostAndPort[] table = Clusters.parseSlots(reply, ANSWERER);

        assertEquals(Clusters.SLOT_COUNT, table.length);
        assertEquals(new HostAndPort("10.0.0.1", 7001), table[0]);
        assertEquals(new HostAndPort("10.0.0.1", 7001), table[5460]);
        assertEquals(new HostAndPort("10.0.0.2", 7002), table[5461]);
        assertEquals(new HostAndPort("10.0.0.2", 7002), table[10922]);
        assertEquals(new HostAndPort("10.0.0.3", 7003), table[10923]);
        assertEquals(new HostAndPort("10.0.0.3", 7003), table[16383]);
    }

    @Test
    void replicasAreNeverRoutedTo() {
        List<Object> reply =
                Collections.singletonList(entry(0, 16383, node(bytes("primary"), 7001), node(bytes("replica"), 7002)));
        for (HostAndPort owner : Clusters.parseSlots(reply, ANSWERER)) {
            assertEquals(new HostAndPort("primary", 7001), owner);
        }
    }

    @Test
    void aNodeWithoutAHostIsReachedAtTheAnswerersAddress() {
        for (Object missing : Arrays.asList(bytes(""), "", bytes("?"), null)) {
            List<Object> reply = Collections.singletonList(entry(0, 16383, node(missing, 7005)));
            assertEquals(new HostAndPort("seed.example", 7005), Clusters.parseSlots(reply, ANSWERER)[100]);
        }
    }

    @Test
    void hostsMayArriveAsStrings() {
        List<Object> reply = Collections.singletonList(entry(0, 16383, node("string-host", 7001)));
        assertEquals(new HostAndPort("string-host", 7001), Clusters.parseSlots(reply, ANSWERER)[0]);
    }

    @Test
    void slotsNoEntryCoversStayUnassigned() {
        List<Object> reply = Collections.singletonList(entry(100, 200, node(bytes("n"), 7001)));
        HostAndPort[] table = Clusters.parseSlots(reply, ANSWERER);
        assertNull(table[99]);
        assertEquals(new HostAndPort("n", 7001), table[100]);
        assertEquals(new HostAndPort("n", 7001), table[200]);
        assertNull(table[201]);
    }

    @Test
    void rangesAreClampedToTheSlotSpace() {
        List<Object> reply = Collections.singletonList(entry(-5, 99999, node(bytes("n"), 7001)));
        HostAndPort[] table = Clusters.parseSlots(reply, ANSWERER);
        assertEquals(new HostAndPort("n", 7001), table[0]);
        assertEquals(new HostAndPort("n", 7001), table[16383]);
    }

    @Test
    void malformedEntriesAreSkippedRatherThanFailingTheRefresh() {
        List<Object> reply = new ArrayList<>();
        reply.add("not a list");
        reply.add(Arrays.asList(1L, 2L)); // no node
        reply.add(Arrays.asList("a", "b", node(bytes("n"), 1))); // range bounds are not numbers
        reply.add(Arrays.asList(0L, 10L, "not a node"));
        reply.add(Arrays.asList(0L, 10L, Collections.singletonList(bytes("n")))); // node without a port
        reply.add(Arrays.asList(0L, 10L, node(bytes("n"), 0))); // port out of range
        reply.add(Arrays.asList(0L, 10L, node(bytes("n"), 70000)));
        reply.add(entry(20, 30, node(bytes("good"), 7001)));

        HostAndPort[] table = Clusters.parseSlots(reply, ANSWERER);

        assertNull(table[0]);
        assertEquals(new HostAndPort("good", 7001), table[25]);
    }

    @Test
    void aMissingReplyYieldsAnEmptyTable() {
        assertEquals(0, Clusters.primaries(Clusters.parseSlots(null, ANSWERER)).size());
        assertEquals(
                0,
                Clusters.primaries(Clusters.parseSlots(Collections.emptyList(), ANSWERER))
                        .size());
    }

    // --- primaries -------------------------------------------------------------------------------

    @Test
    void listsEachPrimaryOnceInSlotOrder() {
        List<Object> reply = Arrays.asList(
                entry(0, 100, node(bytes("a"), 1)),
                entry(101, 200, node(bytes("b"), 2)),
                entry(201, 300, node(bytes("a"), 1)));
        assertEquals(
                Arrays.asList(new HostAndPort("a", 1), new HostAndPort("b", 2)),
                Clusters.primaries(Clusters.parseSlots(reply, ANSWERER)));
    }

    // --- detect ----------------------------------------------------------------------------------

    private static DefaultJedisClientConfig probe() {
        return DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(1000)
                .socketTimeoutMillis(2000)
                .autoNegotiateProtocol(false)
                .build();
    }

    @Test
    void detectsAClusterNode() {
        try (FakeRedisServer node = new FakeRedisServer(c -> FakeRedisServer.startsWith(c, "INFO")
                ? FakeRedisServer.bulk("# Server\r\nredis_mode:cluster\r\n")
                : FakeRedisServer.ok())) {
            assertTrue(Clusters.detect(new HostAndPort("127.0.0.1", node.port()), probe()));
            assertEquals(1, node.count("INFO", "server"));
        }
    }

    @Test
    void aStandaloneServerIsNotACluster() {
        try (FakeRedisServer node = new FakeRedisServer(c -> FakeRedisServer.startsWith(c, "INFO")
                ? FakeRedisServer.bulk("# Server\r\nredis_mode:standalone\r\n")
                : FakeRedisServer.ok())) {
            assertFalse(Clusters.detect(new HostAndPort("127.0.0.1", node.port()), probe()));
        }
    }

    @Test
    void anUnreachableEndpointIsNotACluster() {
        assertFalse(Clusters.detect(new HostAndPort("127.0.0.1", 1), probe()));
    }

    @Test
    void anEndpointThatRefusesInfoIsNotACluster() {
        try (FakeRedisServer node = new FakeRedisServer(c -> FakeRedisServer.startsWith(c, "INFO")
                ? FakeRedisServer.error("NOPERM this user has no permissions to run the 'info' command")
                : FakeRedisServer.ok())) {
            assertFalse(Clusters.detect(new HostAndPort("127.0.0.1", node.port()), probe()));
        }
    }

    @Test
    void aSlotTableIsAlwaysTheFullSlotCount() {
        assertArrayEquals(new HostAndPort[Clusters.SLOT_COUNT], Clusters.parseSlots(null, ANSWERER));
    }
}
