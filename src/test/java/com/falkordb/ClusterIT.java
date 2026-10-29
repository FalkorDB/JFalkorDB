package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisMovedDataException;
import redis.clients.jedis.util.JedisClusterCRC16;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Runs the driver against a real three-primary Redis Cluster.
 *
 * <p>Graph names hash to different slots, so a driver that did not follow the cluster topology would
 * hit {@code MOVED} errors on the graphs owned by nodes other than the one it connected to. Every test
 * therefore touches many graphs, and {@link #graphsSpanEveryPrimary} checks the names used really do
 * land on all three nodes.
 */
public class ClusterIT {

    private static final int GRAPHS = 24;
    private static final String PREFIX = "cluster-it-";

    private Driver driver;

    @BeforeEach
    public void setUp() {
        driver = FalkorDB.driver(ClusterTestServer.seedHost(), ClusterTestServer.seedPort());
    }

    @AfterEach
    public void tearDown() throws Exception {
        for (int i = 0; i < GRAPHS; i++) {
            try {
                driver.graph(PREFIX + i).deleteGraph();
            } catch (RuntimeException ignored) {
                // the graph was never created
            }
        }
        driver.close();
    }

    @Test
    public void graphsSpanEveryPrimary() {
        List<Object[]> ranges = new ArrayList<>(); // {start, end, owner "host:port"}
        List<Object> owned;
        try (Jedis node = new Jedis(ClusterTestServer.seedHost(), ClusterTestServer.seedPort())) {
            owned = node.clusterSlots();
        }
        for (Object range : owned) {
            List<?> entry = (List<?>) range;
            List<?> primary = (List<?>) entry.get(2);
            Object host = primary.get(0);
            String owner = (host instanceof byte[] ? SafeEncoder.encode((byte[]) host) : String.valueOf(host)) + ":"
                    + primary.get(1);
            ranges.add(new Object[] {entry.get(0), entry.get(1), owner});
        }
        Set<Object> owners = new HashSet<>();
        for (int i = 0; i < GRAPHS; i++) {
            int slot = JedisClusterCRC16.getSlot(PREFIX + i);
            for (Object[] range : ranges) {
                if (slot >= (Long) range[0] && slot <= (Long) range[1]) {
                    owners.add(range[2]);
                }
            }
        }
        assertEquals(3, owners.size(), "the test graph names must land on all three primaries");
    }

    @Test
    public void queriesReachTheNodeThatOwnsTheGraphWhateverTheSeedIs() {
        for (int i = 0; i < GRAPHS; i++) {
            GraphContextGenerator graph = driver.graph(PREFIX + i);
            graph.query("CREATE (:Person {id: " + i + "})");
            ResultSet read = graph.readOnlyQuery("MATCH (p:Person) RETURN p.id");
            assertEquals(1, read.size());
            assertEquals(Long.valueOf(i), read.iterator().next().getValue(0));
        }
    }

    @Test
    public void explicitStartupNodesBehaveLikeAutoDetection() throws Exception {
        try (Driver explicit =
                FalkorDB.builder().cluster(ClusterTestServer.nodes()).build()) {
            for (int i = 0; i < GRAPHS; i++) {
                explicit.graph(PREFIX + i).query("CREATE (:Person {id: " + i + "})");
            }
            for (int i = 0; i < GRAPHS; i++) {
                ResultSet read = driver.graph(PREFIX + i).readOnlyQuery("MATCH (p:Person) RETURN count(p)");
                assertEquals(1L, (long) read.iterator().next().<Long>getValue(0));
            }
        }
    }

    @Test
    public void explicitStartupNodesTolerateAnUnreachableOne() throws Exception {
        List<String> nodes = new ArrayList<>();
        nodes.add("127.0.0.1:1"); // nothing listens here
        nodes.addAll(ClusterTestServer.nodes());
        try (Driver explicit = FalkorDB.builder().cluster(nodes).build()) {
            explicit.graph(PREFIX + 0).query("CREATE (:Person)");
        }
    }

    @Test
    public void autoDetectionCanBeSwitchedOff() throws Exception {
        // With detection off the driver is a plain single-node client, so a graph that the seed node
        // does not own is answered with a redirect, surfaced as a GraphException.
        try (Driver direct = FalkorDB.builder()
                .host(ClusterTestServer.seedHost())
                .port(ClusterTestServer.seedPort())
                .autoDetectCluster(false)
                .build()) {
            boolean redirected = false;
            for (int i = 0; i < GRAPHS && !redirected; i++) {
                try {
                    direct.graph(PREFIX + i).query("CREATE (:Person)");
                } catch (GraphException e) {
                    assertTrue(e.getCause() instanceof JedisMovedDataException, String.valueOf(e.getCause()));
                    redirected = true;
                }
            }
            assertTrue(redirected, "a plain connection to one node must be redirected for some graph");
        }
    }

    @Test
    public void pipelinesAndTransactionsStayOnTheGraphsNode() {
        for (int i = 0; i < GRAPHS; i++) {
            GraphContextGenerator graph = driver.graph(PREFIX + i);
            try (GraphContext context = graph.getContext()) {
                GraphPipeline pipeline = context.pipelined();
                pipeline.query("CREATE (:Person {id: 1})");
                pipeline.query("CREATE (:Person {id: 2})");
                pipeline.readOnlyQuery("MATCH (p:Person) RETURN count(p)");
                List<Object> results = pipeline.syncAndReturnAll();
                assertEquals(3, results.size());
                assertEquals(2L, (long)
                        ((ResultSet) results.get(2)).iterator().next().<Long>getValue(0));
            }
            try (GraphContext context = graph.getContext()) {
                GraphTransaction transaction = context.multi();
                transaction.query("CREATE (:Person {id: 3})");
                transaction.readOnlyQuery("MATCH (p:Person) RETURN count(p)");
                List<Object> results = transaction.exec();
                assertEquals(2, results.size());
                assertEquals(3L, (long)
                        ((ResultSet) results.get(1)).iterator().next().<Long>getValue(0));
            }
        }
    }

    @Test
    public void listGraphsCoversEveryPrimary() {
        for (int i = 0; i < GRAPHS; i++) {
            driver.graph(PREFIX + i).query("CREATE (:Person)");
        }
        List<String> listed = driver.listGraphs();
        for (int i = 0; i < GRAPHS; i++) {
            assertTrue(listed.contains(PREFIX + i), PREFIX + i + " missing from " + listed);
        }
        assertEquals(new HashSet<>(listed).size(), listed.size(), "no graph is listed twice");
    }

    @Test
    public void copyGraphWorksWithinOneSlot() {
        // A hash tag pins both names to one slot, which is what GRAPH.COPY needs in a cluster.
        GraphContextGenerator source = driver.graph("{cluster-it-copy}-a");
        try {
            source.query("CREATE (:Person {name: 'copied'})");
            source.copyGraph("{cluster-it-copy}-b");
            ResultSet read = driver.graph("{cluster-it-copy}-b").readOnlyQuery("MATCH (p:Person) RETURN p.name");
            assertEquals("copied", read.iterator().next().getValue(0));
        } finally {
            source.deleteGraph();
            driver.graph("{cluster-it-copy}-b").deleteGraph();
        }
    }

    @Test
    public void explainAndProfileAreRouted() {
        for (int i = 0; i < GRAPHS; i++) {
            GraphContextGenerator graph = driver.graph(PREFIX + i);
            graph.query("CREATE (:Person)");
            assertFalse(graph.explain("MATCH (p:Person) RETURN p").isEmpty());
            assertNotNull(graph.profile("MATCH (p:Person) RETURN p"));
        }
    }

    @Test
    public void configurationIsAppliedToEveryPrimary() {
        String original = driver.configGet("RESULTSET_SIZE");
        try {
            assertTrue(driver.configSet("RESULTSET_SIZE", 4321));
            for (String node : ClusterTestServer.nodes()) {
                HostAndPort address = HostAndPort.from(node);
                try (Jedis direct = new Jedis(address)) {
                    List<?> reply = (List<?>) direct.sendCommand(raw("GRAPH.CONFIG"), "GET", "RESULTSET_SIZE");
                    assertEquals("4321", String.valueOf(reply.get(1)), node);
                }
            }
        } finally {
            driver.configSet("RESULTSET_SIZE", original);
        }
    }

    @Test
    public void udfLibrariesAreLoadedOnEveryPrimary() {
        String script = "function add(a, b) { return a + b; }\nfalkor.register('add', add);\n";
        try {
            assertTrue(driver.udfLoad("cluster_it_lib", script, true));
            for (String node : ClusterTestServer.nodes()) {
                try (Jedis direct = new Jedis(HostAndPort.from(node))) {
                    Object reply = direct.sendCommand(raw("GRAPH.UDF"), "LIST");
                    assertTrue(String.valueOf(asStrings(reply)).contains("cluster_it_lib"), node);
                }
            }
            assertTrue(driver.udfDelete("cluster_it_lib"));
            for (String node : ClusterTestServer.nodes()) {
                try (Jedis direct = new Jedis(HostAndPort.from(node))) {
                    Object reply = direct.sendCommand(raw("GRAPH.UDF"), "LIST");
                    assertFalse(String.valueOf(asStrings(reply)).contains("cluster_it_lib"), node);
                }
            }
            assertTrue(driver.udfLoad("cluster_it_lib", script, true));
            assertTrue(driver.udfFlush());
            assertTrue(driver.udfList().isEmpty());
        } finally {
            driver.udfFlush();
        }
    }

    @Test
    public void aGraphInTheMiddleOfAMigrationIsFollowedThroughAsk() {
        // A real slot migration, stopped halfway: the graph has moved to the importing node, but the slot still
        // belongs to the node it is leaving, which answers ASK. Decoding the reply looks labels up, and those
        // lookups need ASKING on the importing node too.
        String graph = "{" + PREFIX + "migrating}";
        int slot = JedisClusterCRC16.getSlot(graph);
        for (int i = 0; i < GRAPHS; i++) {
            assertTrue(
                    JedisClusterCRC16.getSlot(PREFIX + i) != slot,
                    "the migrated slot must not move other tests' graphs");
        }
        driver.graph(graph).query("CREATE (:Person {name:'a'})");
        HostAndPort source = ownerOf(slot);
        HostAndPort target = null;
        for (String address : ClusterTestServer.nodes()) {
            HostAndPort node = HostAndPort.from(address);
            if (!node.equals(source)) {
                target = node;
                break;
            }
        }
        assertNotNull(target);

        try (Jedis from = new Jedis(source);
                Jedis to = new Jedis(target)) {
            String sourceId = from.clusterMyId();
            String targetId = to.clusterMyId();
            boolean migrated = false;
            try {
                to.clusterSetSlotImporting(slot, sourceId);
                from.clusterSetSlotMigrating(slot, targetId);
                assertEquals("OK", from.migrate(target.getHost(), target.getPort(), graph, 0, 5000));
                migrated = true;

                GraphContextGenerator fresh = driver.graph(graph); // its own, empty cache
                Node node = (Node)
                        fresh.query("MATCH (n) RETURN n").iterator().next().getValue(0);
                assertEquals("Person", node.getLabel(0));
                assertEquals("a", node.getProperty("name").getValue());

                fresh.query("CREATE (:Person {name:'b'})");
                Long nodes = fresh.readOnlyQuery("MATCH (n) RETURN count(n)")
                        .iterator()
                        .next()
                        .getValue(0);
                assertEquals(2L, nodes.longValue());
            } finally {
                // Leave a settled cluster for the other tests, however far the migration got.
                if (migrated) {
                    // the graph is on the target: give it the slot everywhere
                    to.clusterSetSlotNode(slot, targetId);
                    from.clusterSetSlotNode(slot, targetId);
                    for (String address : ClusterTestServer.nodes()) {
                        HostAndPort other = HostAndPort.from(address);
                        if (!other.equals(source) && !other.equals(target)) {
                            try (Jedis node = new Jedis(other)) {
                                node.clusterSetSlotNode(slot, targetId);
                            }
                        }
                    }
                } else {
                    // the graph never left the source: call the migration off
                    to.clusterSetSlotStable(slot);
                    from.clusterSetSlotStable(slot);
                }
                driver.graph(graph).deleteGraph();
            }
        }
    }

    /** The primary that owns {@code slot}, as the cluster reports it. */
    private static HostAndPort ownerOf(int slot) {
        try (Jedis node = new Jedis(ClusterTestServer.seedHost(), ClusterTestServer.seedPort())) {
            for (Object range : node.clusterSlots()) {
                List<?> entry = (List<?>) range;
                if (slot >= (Long) entry.get(0) && slot <= (Long) entry.get(1)) {
                    List<?> primary = (List<?>) entry.get(2);
                    Object host = primary.get(0);
                    String name = host instanceof byte[] ? SafeEncoder.encode((byte[]) host) : String.valueOf(host);
                    return new HostAndPort(name, ((Long) primary.get(1)).intValue());
                }
            }
        }
        throw new AssertionError("no node owns slot " + slot);
    }

    @Test
    public void rawConnectionReachesSomeNode() {
        try (Jedis connection = driver.getConnection()) {
            assertEquals("PONG", connection.ping());
        }
    }

    private static ProtocolCommand raw(String command) {
        return () -> command.getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> asStrings(Object reply) {
        List<String> out = new ArrayList<>();
        if (reply instanceof List<?>) {
            for (Object item : (List<?>) reply) {
                out.addAll(asStrings(item));
            }
        } else if (reply instanceof byte[]) {
            out.add(new String((byte[]) reply, StandardCharsets.UTF_8));
        } else if (reply != null) {
            out.add(reply.toString());
        }
        return out;
    }
}
