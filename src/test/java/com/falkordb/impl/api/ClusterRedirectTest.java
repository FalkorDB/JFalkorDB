package com.falkordb.impl.api;

import static com.falkordb.impl.api.FakeRedisServer.array;
import static com.falkordb.impl.api.FakeRedisServer.bulk;
import static com.falkordb.impl.api.FakeRedisServer.error;
import static com.falkordb.impl.api.FakeRedisServer.integer;
import static com.falkordb.impl.api.FakeRedisServer.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import com.falkordb.impl.graph_cache.GraphCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisAskDataException;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * How the driver follows an {@code ASK} redirect, treats a redirect met while decoding a reply, and fails a
 * single-primary read over to another primary.
 */
class ClusterRedirectTest {

    /** Whether the client sent ASKING on this connection since its last graph command (one thread each). */
    private static final ThreadLocal<Boolean> ASKED = ThreadLocal.withInitial(() -> false);

    private static final String GRAPH = FakeCluster.lowGraph(); // node 0 owns its slot

    private FakeCluster cluster;
    private Driver driver;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.close();
        }
        if (cluster != null) {
            cluster.close();
        }
    }

    private void connect() {
        driver = DriverImpl.connect(
                "127.0.0.1",
                cluster.node(0).port(),
                null,
                null,
                SentinelOptions.disabled(),
                ClusterOptions.autoDetect());
    }

    // --- ASK --------------------------------------------------------------------------------------

    /**
     * Node 1 is importing the graph's slot from node 0, and the graph has already moved: node 0 answers every
     * graph command with ASK, and node 1 serves one only right after ASKING, as Redis does, else MOVED back.
     */
    private String migrating(int node, List<String> command) {
        if (startsWith(command, "ASKING")) {
            ASKED.set(true);
            return FakeRedisServer.ok();
        }
        if (!command.get(0).toUpperCase(java.util.Locale.ROOT).startsWith("GRAPH.")) {
            return null;
        }
        if (node == 0) {
            return error("ASK 100 " + cluster.node(1).address());
        }
        boolean asked = ASKED.get();
        ASKED.set(false);
        return asked
                ? graphReply(command)
                : error("MOVED 100 " + cluster.node(0).address());
    }

    @Test
    void anAskIsFollowedWithAskingAndWithoutRelearningTheSlots() {
        cluster = new FakeCluster(2, this::migrating);
        connect();

        ResultSet result = driver.graph(GRAPH).query("MATCH (n) RETURN n");

        // the reply's label had to be looked up on node 1 too, which also needed ASKING
        assertEquals("Person", ((Node) result.iterator().next().getValue(0)).getLabel(0));
        assertEquals(1, cluster.node(0).count("GRAPH.QUERY"));
        assertEquals(1, cluster.node(1).count("GRAPH.QUERY"));
        assertEquals(1, cluster.node(1).count("GRAPH.RO_QUERY"));
        assertEveryGraphCommandFollowsAsking(cluster.node(1));
        assertEquals(2, cluster.node(1).count("ASKING"), "one ASKING per graph command");
        assertEquals(1, slotTableReads(), "the slot table was read once, when the driver started");
    }

    @Test
    void readsCopiesDeletesAndExplainsFollowAnAskToo() {
        cluster = new FakeCluster(2, this::migrating);
        connect();

        driver.graph(GRAPH).readOnlyQuery("MATCH (n) RETURN n");
        driver.graph(GRAPH).profile("MATCH (n) RETURN n");
        driver.graph(GRAPH).explain("MATCH (n) RETURN n");
        driver.graph(GRAPH).copyGraph(GRAPH + "-copy");
        driver.graph(GRAPH).deleteGraph();

        for (String command : new String[] {"GRAPH.PROFILE", "GRAPH.EXPLAIN", "GRAPH.COPY", "GRAPH.DELETE"}) {
            assertEquals(1, cluster.node(1).count(command), command);
        }
        assertEveryGraphCommandFollowsAsking(cluster.node(1));
        assertEquals(1, slotTableReads());
    }

    @Test
    void anAskWithAnEmptyHostGoesToTheHostThatAnswered() {
        // what a node configured with cluster-preferred-endpoint-type unknown-endpoint sends
        assertAskWithHostReachesTheImportingNode("");
    }

    @Test
    void anAskWithAnUnknownHostGoesToTheHostThatAnswered() {
        // what a node configured with cluster-preferred-endpoint-type hostname, but no hostname, sends
        assertAskWithHostReachesTheImportingNode("?");
    }

    private void assertAskWithHostReachesTheImportingNode(String host) {
        cluster = new FakeCluster(2, (node, command) -> {
            String reply = migrating(node, command);
            return reply != null && node == 0
                    ? error("ASK 100 " + host + ":" + cluster.node(1).port())
                    : reply;
        });
        connect();

        driver.graph(GRAPH).query("MATCH (n) RETURN n");

        assertEquals(1, cluster.node(1).count("GRAPH.QUERY"));
    }

    @Test
    void anAskTargetThatKeepsRedirectingIsGivenUpOn() {
        cluster = new FakeCluster(2, (node, command) -> {
            if (startsWith(command, "ASKING")) {
                return FakeRedisServer.ok();
            }
            return startsWith(command, "GRAPH.QUERY")
                    ? error("ASK 100 " + cluster.node(1).address())
                    : null;
        });
        connect();

        GraphException e =
                assertThrows(GraphException.class, () -> driver.graph(GRAPH).query("RETURN 1"));

        assertTrue(e.getCause() instanceof JedisAskDataException, String.valueOf(e.getCause()));
        assertEquals(1, cluster.node(0).count("GRAPH.QUERY"));
        assertEquals(DriverImpl.CLUSTER_REDIRECT_ATTEMPTS - 1, cluster.node(1).count("GRAPH.QUERY"));
        assertEveryGraphCommandFollowsAsking(cluster.node(1));
        assertEquals(1, slotTableReads());
    }

    @Test
    void aMovedFromTheAskTargetRelearnsTheSlotTable() {
        // Node 1 stopped importing the slot between the ASK and the attempt it sent there, and node 2 owns the
        // slot now, which only the slot table can tell.
        AtomicInteger targetQueries = new AtomicInteger();
        cluster = new FakeCluster(3, (node, command) -> {
            if (startsWith(command, "GRAPH.QUERY")) {
                if (node == 0) {
                    return error("ASK 100 " + cluster.node(1).address());
                }
                if (node == 1 && targetQueries.getAndIncrement() == 0) {
                    cluster.assign(2, 1);
                    return error("MOVED 100 " + cluster.node(2).address());
                }
                return graphReply(command);
            }
            return startsWith(command, "GRAPH.RO_QUERY") ? graphReply(command) : null;
        });
        connect();

        ResultSet result = driver.graph(GRAPH).query("MATCH (n) RETURN n");

        assertEquals("Person", ((Node) result.iterator().next().getValue(0)).getLabel(0));
        assertEquals(1, cluster.node(0).count("GRAPH.QUERY"));
        assertEquals(1, cluster.node(1).count("GRAPH.QUERY"));
        assertEquals(1, cluster.node(2).count("GRAPH.QUERY"));
        assertEveryGraphCommandFollowsAsking(cluster.node(1));
        assertEquals(0, cluster.node(2).count("ASKING"), "the attempt after the MOVED is not an ASK attempt");
        assertEquals(2, slotTableReads(), "the MOVED made the driver relearn the slot table");
    }

    @Test
    void aContextForAnAskAttemptAsksBeforeEachCommandAfterItsFirst() {
        // Copies, deletes and explains on a Graph go out on the route's own connection, so no driver test reaches
        // them on a context made for an ASK attempt; a context asks before them all the same.
        try (FakeRedisServer node = new FakeRedisServer(command -> startsWith(command, "GRAPH.EXPLAIN")
                        ? array(bulk("Results"))
                        : startsWith(command, "GRAPH.") ? bulk("done in 0.1 ms") : FakeRedisServer.ok());
                GraphContextImpl context =
                        new GraphContextImpl(new Jedis("127.0.0.1", node.port()), new GraphCache(), GRAPH)) {
            context.askBeforeEachCommand();

            context.explain("RETURN 1"); // the route sent ASKING with the connection
            context.copyGraph(GRAPH + "-copy");
            context.deleteGraph();

            List<String> sent = new ArrayList<>();
            for (List<String> command : node.received()) {
                String name = command.get(0).toUpperCase(Locale.ROOT);
                if (name.startsWith("GRAPH.") || name.equals("ASKING")) {
                    sent.add(name);
                }
            }
            assertEquals(Arrays.asList("GRAPH.EXPLAIN", "ASKING", "GRAPH.COPY", "ASKING", "GRAPH.DELETE"), sent);
        }
    }

    // --- a redirect while decoding ----------------------------------------------------------------

    @Test
    void aRedirectWhileDecodingAQueryIsReportedNotRetried() {
        // The query ran on node 0, but the slot moved before its labels were looked up. Running the query
        // again could apply a write twice.
        cluster = new FakeCluster(2, (node, command) -> {
            if (startsWith(command, "GRAPH.QUERY")) {
                return nodeReply();
            }
            if (startsWith(command, "GRAPH.RO_QUERY")) {
                return error("MOVED 100 " + cluster.node(1).address());
            }
            return null;
        });
        connect();

        GraphException e =
                assertThrows(GraphException.class, () -> driver.graph(GRAPH).query("CREATE (n:Person) RETURN n"));

        assertTrue(e.getMessage().startsWith("The command ran, but its reply could not be decoded"), e.getMessage());
        assertTrue(DriverImpl.isRedirect(e), "the redirect stays the cause");
        assertEquals(1, cluster.node(0).count("GRAPH.QUERY"), "the query ran once");
        assertEquals(2, slotTableReads(), "the MOVED still made the driver relearn the slot table");
    }

    @Test
    void aRedirectWhileDecodingAReadIsRetried() {
        // Running a read again is harmless, so it is retried like any other redirect.
        AtomicInteger lookups = new AtomicInteger();
        cluster = new FakeCluster(2, (node, command) -> {
            if (!startsWith(command, "GRAPH.RO_QUERY")) {
                return null;
            }
            if (!command.get(2).equals("CALL db.labels()")) {
                return nodeReply();
            }
            return lookups.getAndIncrement() == 0
                    ? error("MOVED 100 " + cluster.node(0).address())
                    : labelsReply();
        });
        connect();

        ResultSet result = driver.graph(GRAPH).readOnlyQuery("MATCH (n) RETURN n");

        assertEquals("Person", ((Node) result.iterator().next().getValue(0)).getLabel(0));
        long reads = cluster.node(0).received().stream()
                .filter(c -> startsWith(c, "GRAPH.RO_QUERY") && c.get(2).equals("MATCH (n) RETURN n"))
                .count();
        assertEquals(2, reads, "the read ran again");
    }

    @Test
    void aRedirectWhileDecodingAQueryKeepsAFailedRelearnWithIt() {
        cluster = new FakeCluster(2, (node, command) -> {
            if (startsWith(command, "GRAPH.QUERY")) {
                return nodeReply();
            }
            if (startsWith(command, "GRAPH.RO_QUERY")) {
                return error("MOVED 100 " + cluster.node(1).address());
            }
            return null;
        });
        connect();
        driver.udfFlush(); // learns the slot table
        cluster.refuseSlotQueries(true);

        GraphException e =
                assertThrows(GraphException.class, () -> driver.graph(GRAPH).query("CREATE (n:Person) RETURN n"));

        assertTrue(e.getMessage().startsWith("The command ran, but its reply could not be decoded"), e.getMessage());
        assertTrue(
                Arrays.stream(e.getSuppressed()).anyMatch(s -> s instanceof JedisClusterOperationException),
                "the failed relearn is attached: " + Arrays.toString(e.getSuppressed()));
        assertEquals(1, cluster.node(0).count("GRAPH.QUERY"), "the query ran once");
    }

    @Test
    void anAskWhileDecodingAQueryIsReportedWithoutRelearning() {
        // An ASK leaves the slot table as it is: the slot still belongs to the node that answered.
        cluster = new FakeCluster(2, (node, command) -> {
            if (startsWith(command, "GRAPH.QUERY")) {
                return nodeReply();
            }
            if (startsWith(command, "GRAPH.RO_QUERY")) {
                return error("ASK 100 " + cluster.node(1).address());
            }
            return null;
        });
        connect();

        GraphException e =
                assertThrows(GraphException.class, () -> driver.graph(GRAPH).query("CREATE (n:Person) RETURN n"));

        assertTrue(e.getMessage().startsWith("The command ran, but its reply could not be decoded"), e.getMessage());
        assertTrue(DriverImpl.askRedirect(e) != null, "the ASK stays the cause");
        assertEquals(1, cluster.node(0).count("GRAPH.QUERY"), "the query ran once");
        assertEquals(1, slotTableReads(), "the slot table was not relearned");
    }

    @Test
    void anErrorWhileDecodingAQueryThatIsNotARedirectIsReportedAsItIs() {
        cluster = new FakeCluster(2, (node, command) -> {
            if (startsWith(command, "GRAPH.QUERY")) {
                return nodeReply();
            }
            if (startsWith(command, "GRAPH.RO_QUERY")) {
                return error("ERR lookup failed");
            }
            return null;
        });
        connect();

        GraphException e =
                assertThrows(GraphException.class, () -> driver.graph(GRAPH).query("CREATE (n:Person) RETURN n"));

        assertFalse(e instanceof ReplyDecodeException, "only a redirect needs telling apart from one: " + e);
        assertTrue(e.getMessage().endsWith("ERR lookup failed"), e.getMessage());
        assertEquals(1, cluster.node(0).count("GRAPH.QUERY"), "the query ran once");
    }

    // --- reads any primary can answer --------------------------------------------------------------

    @Test
    void readsMoveOnToAnotherPrimaryWhenOneIsDown() {
        cluster = new FakeCluster(2, (node, command) -> {
            if (startsWith(command, "GRAPH.UDF", "LIST")) {
                return array(bulk("library-" + node));
            }
            if (startsWith(command, "GRAPH.CONFIG", "GET")) {
                return array(bulk("RESULTSET_SIZE"), integer(1000));
            }
            return null;
        });
        connect();
        driver.udfList(); // learns the two primaries, starting at node 0
        assertEquals(Collections.singletonList("library-1"), names(driver.udfList())); // node 1 answers
        cluster.node(1).close(); // the slot table still lists node 1

        // Round-robin starts each call at the next primary, so half of these start at the dead one.
        for (int i = 0; i < 4; i++) {
            assertEquals(Collections.singletonList("library-0"), names(driver.udfList()));
            assertEquals(Collections.singletonList("library-0"), names(driver.udfList("library-0", true)));
            assertEquals("1000", driver.configGet("RESULTSET_SIZE"));
        }
        assertTrue(cluster.node(0).count("CLUSTER", "SLOTS") > 1, "the slot table was relearned");
    }

    @Test
    void udfListSendsTheFiltersItIsGiven() {
        // Each node answers with the arguments that followed GRAPH.UDF LIST.
        cluster = new FakeCluster(
                2,
                (node, command) -> startsWith(command, "GRAPH.UDF", "LIST")
                        ? array(bulk(String.join(" ", command.subList(2, command.size()))))
                        : null);
        connect();

        assertEquals(Collections.singletonList(""), names(driver.udfList(null, false)));
        assertEquals(Collections.singletonList("WITHCODE"), names(driver.udfList(null, true)));
        assertEquals(Collections.singletonList("lib"), names(driver.udfList("lib", false)));
        assertEquals(Collections.singletonList("lib WITHCODE"), names(driver.udfList("lib", true)));
    }

    @Test
    void aUdfListReplyThatIsNotAListReadsAsNoLibraries() {
        cluster = new FakeCluster(2, (node, command) -> startsWith(command, "GRAPH.UDF", "LIST") ? "$-1\r\n" : null);
        connect();

        assertEquals(Collections.emptyList(), driver.udfList());
        assertEquals(Collections.emptyList(), driver.udfList("lib", true));
    }

    @Test
    void whenRelearningFailsTheOtherKnownPrimariesAreStillTried() {
        // Node 1 can't help relearn the layout, but it can still answer the read itself.
        cluster = new FakeCluster(
                2, (node, command) -> startsWith(command, "GRAPH.UDF", "LIST") ? array(bulk("library-" + node)) : null);
        connect();
        driver.udfList(); // learns the two primaries
        cluster.node(0).close();
        cluster.refuseSlotQueries(true);

        for (int i = 0; i < 4; i++) {
            assertEquals(Collections.singletonList("library-1"), names(driver.udfList()));
        }
    }

    @Test
    void theRoundRobinVisitsEveryPrimaryWhenItsCounterWraps() {
        List<HostAndPort> primaries =
                Arrays.asList(new HostAndPort("a", 1), new HostAndPort("b", 2), new HostAndPort("c", 3));
        Set<HostAndPort> tried = new HashSet<>();
        for (int i = 0; i < primaries.size(); i++) {
            tried.add(ClusterConnections.untried(primaries, Integer.MAX_VALUE, tried));
        }
        assertEquals(new HashSet<>(primaries), tried);
        assertNull(ClusterConnections.untried(primaries, Integer.MAX_VALUE, tried));
    }

    @Test
    void aClusterThatIsDownFailsAReadAfterOneRelearn() {
        cluster = new FakeCluster(2, (node, command) -> null);
        connect();
        driver.udfFlush(); // learns the two primaries
        cluster.node(0).close();
        cluster.node(1).close();

        JedisConnectionException e = assertThrows(JedisConnectionException.class, () -> driver.udfList());

        assertTrue(
                Arrays.stream(e.getSuppressed()).anyMatch(s -> s instanceof JedisClusterOperationException),
                "the failed relearn is attached: " + Arrays.toString(e.getSuppressed()));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private long slotTableReads() {
        long reads = 0;
        for (int i = 0; i < cluster.size(); i++) {
            reads += cluster.node(i).count("CLUSTER", "SLOTS");
        }
        return reads;
    }

    private static void assertEveryGraphCommandFollowsAsking(FakeRedisServer node) {
        List<List<String>> received = node.received();
        boolean any = false;
        for (int i = 0; i < received.size(); i++) {
            if (received.get(i).get(0).toUpperCase(java.util.Locale.ROOT).startsWith("GRAPH.")) {
                any = true;
                assertTrue(i > 0 && startsWith(received.get(i - 1), "ASKING"), "no ASKING before " + received.get(i));
            }
        }
        assertTrue(any, "the node received no graph command");
    }

    private static List<String> names(List<Object> libraries) {
        List<String> names = new ArrayList<>();
        for (Object library : libraries) {
            names.add(redis.clients.jedis.util.SafeEncoder.encode((byte[]) library));
        }
        return names;
    }

    private static String graphReply(List<String> command) {
        if (startsWith(command, "GRAPH.RO_QUERY") && command.get(2).equals("CALL db.labels()")) {
            return labelsReply();
        }
        if (startsWith(command, "GRAPH.QUERY")
                || startsWith(command, "GRAPH.RO_QUERY")
                || startsWith(command, "GRAPH.PROFILE")) {
            return nodeReply();
        }
        if (startsWith(command, "GRAPH.EXPLAIN")) {
            return array(bulk("Results"));
        }
        return bulk("done in 0.1 ms");
    }

    /** A compact reply with one node labeled with label id 0. */
    private static String nodeReply() {
        String header = array(array(integer(1), bulk("n")));
        String node = array(integer(8), array(integer(7), array(integer(0)), array()));
        return array(header, array(array(node)), array(bulk("Cached execution: 0")));
    }

    /** The reply to {@code CALL db.labels()}: one label, Person. */
    private static String labelsReply() {
        String header = array(array(integer(1), bulk("label")));
        return array(header, array(array(array(integer(2), bulk("Person")))), array(bulk("Cached execution: 0")));
    }
}
