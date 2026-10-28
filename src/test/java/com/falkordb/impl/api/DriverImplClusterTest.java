package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.exceptions.GraphException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisAskDataException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisMovedDataException;

class DriverImplClusterTest {

    /** The reply to a write that created one node: statistics only, no result rows. */
    private static final String WRITE_REPLY =
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.bulk("Nodes created: 1")));

    private FakeCluster cluster;
    private FakeRedisServer standalone;
    private Driver driver;

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.close();
        }
        if (cluster != null) {
            cluster.close();
        }
        if (standalone != null) {
            standalone.close();
        }
    }

    private static String queryReply(int node, List<String> command) {
        return FakeRedisServer.startsWith(command, "GRAPH.QUERY") ? WRITE_REPLY : null;
    }

    private static int graphQueries(FakeRedisServer node) {
        return (int) node.count("GRAPH.QUERY");
    }

    private Driver connectToSeed(FakeRedisServer seed, ClusterOptions options) {
        driver = DriverImpl.connect("127.0.0.1", seed.port(), null, null, SentinelOptions.disabled(), options);
        return driver;
    }

    // --- detection -------------------------------------------------------------------------------

    @Test
    void aSeedThatReportsClusterModeIsTreatedAsACluster() {
        cluster = new FakeCluster(2, DriverImplClusterTest::queryReply);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        driver.graph(FakeCluster.lowGraph()).query("CREATE ()");
        driver.graph(FakeCluster.highGraph()).query("CREATE ()");

        assertEquals(1, graphQueries(cluster.node(0)));
        assertEquals(1, graphQueries(cluster.node(1)), "the graph owned by the other node was routed there");
        assertEquals(1, cluster.node(0).count("INFO", "server"));
    }

    @Test
    void aDatabaseIndexIsRejectedWhenTheEndpointIsACluster() {
        cluster = new FakeCluster(1, DriverImplClusterTest::queryReply);
        driver = DriverImpl.connect(
                java.net.URI.create("redis://127.0.0.1:" + cluster.node(0).port() + "/3"),
                SentinelOptions.disabled(),
                ClusterOptions.autoDetect());

        assertThrows(IllegalArgumentException.class, () -> driver.graph("g").query("CREATE ()"));
    }

    @Test
    void aFailedTopologyRefreshDoesNotHideTheOriginalFailure() {
        cluster = new FakeCluster(1, DriverImplClusterTest::queryReply);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());
        driver.graph(FakeCluster.lowGraph()).query("CREATE ()");

        cluster.node(0).close();

        JedisConnectionException e =
                assertThrows(JedisConnectionException.class, () -> driver.graph(FakeCluster.lowGraph())
                        .query("CREATE ()"));
        assertTrue(e.getSuppressed().length > 0, "the refresh failure is attached, not thrown instead");
    }

    @Test
    void aStandaloneSeedKeepsTheDirectConnection() {
        standalone = new FakeRedisServer(c -> {
            if (FakeRedisServer.startsWith(c, "INFO")) {
                return FakeRedisServer.bulk("redis_mode:standalone\r\n");
            }
            return FakeRedisServer.startsWith(c, "GRAPH.QUERY") ? WRITE_REPLY : FakeRedisServer.ok();
        });
        connectToSeed(standalone, ClusterOptions.autoDetect());

        driver.graph("g").query("CREATE ()");

        assertEquals(1, graphQueries(standalone));
        assertEquals(0, standalone.count("CLUSTER"));
    }

    @Test
    void switchingDetectionOffNeverProbes() {
        cluster = new FakeCluster(2, DriverImplClusterTest::queryReply);
        connectToSeed(cluster.node(0), ClusterOptions.disabled());

        driver.graph(FakeCluster.highGraph()).query("CREATE ()");

        assertEquals(1, graphQueries(cluster.node(0)), "a plain connection sends everything to the seed");
        assertEquals(0, graphQueries(cluster.node(1)));
        assertEquals(0, cluster.node(0).count("INFO"));
        assertEquals(0, cluster.node(0).count("CLUSTER"));
    }

    @Test
    void explicitStartupNodesSkipTheProbe() {
        cluster = new FakeCluster(2, DriverImplClusterTest::queryReply);
        driver = DriverImpl.connect(
                "unused.invalid",
                6379,
                null,
                null,
                SentinelOptions.autoDetect(),
                ClusterOptions.explicit(cluster.addresses()));

        driver.graph(FakeCluster.highGraph()).query("CREATE ()");

        assertEquals(1, graphQueries(cluster.node(1)));
        assertEquals(0, cluster.node(0).count("INFO"));
        assertEquals(0, cluster.node(1).count("INFO"));
    }

    @Test
    void creatingTheDriverDoesNoIo() {
        cluster = new FakeCluster(2, DriverImplClusterTest::queryReply);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertTrue(cluster.node(0).received().isEmpty());
    }

    // --- create() validation ---------------------------------------------------------------------

    private static Driver create(SentinelOptions sentinel, ClusterOptions cluster) {
        return DriverImpl.create(
                "localhost", 6379, null, null, false, 2000, 0, 8, 8, Duration.ofSeconds(1), sentinel, cluster);
    }

    @Test
    void aDeploymentCannotBeBothASentinelAndACluster() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> create(
                        SentinelOptions.explicit("mymaster", Arrays.asList("s:26379")),
                        ClusterOptions.explicit(Arrays.asList("n:6379"))));
        assertTrue(e.getMessage().contains("not both"), e.getMessage());
    }

    @Test
    void clusterOptionsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> create(SentinelOptions.autoDetect(), null));
    }

    @Test
    void explicitClusterNodesWithAutoDetectedSentinelAreAccepted() throws Exception {
        create(SentinelOptions.autoDetect(), ClusterOptions.explicit(Arrays.asList("n:6379")))
                .close();
    }

    // --- client configs --------------------------------------------------------------------------

    @Test
    void nodeConnectionsKeepCredentialsAndTimeoutsButNotTheDatabase() {
        DefaultJedisClientConfig data = DefaultJedisClientConfig.builder()
                .user("u")
                .password("p")
                .database(3)
                .connectionTimeoutMillis(1234)
                .socketTimeoutMillis(5678)
                .build();

        DefaultJedisClientConfig node = DriverImpl.clusterNodeConfig(data);

        assertEquals("u", node.getUser());
        assertEquals("p", node.getPassword());
        assertEquals(1234, node.getConnectionTimeoutMillis());
        assertEquals(5678, node.getSocketTimeoutMillis());
        assertEquals(0, node.getDatabase(), "a cluster has no databases to select");
    }

    @Test
    void nodeConnectionsKeepTls() {
        DefaultJedisClientConfig data = DriverImpl.buildClientConfig("u", "p", true, 1000, 0);
        assertTrue(DriverImpl.clusterNodeConfig(data).getSslOptions() != null);
        assertTrue(DriverImpl.clusterProbeConfig(data).getSslOptions() != null);
        assertFalse(DriverImpl.clusterNodeConfig(DriverImpl.buildClientConfig(null, null, false, 1000, 0))
                        .getSslOptions()
                != null);
    }

    @Test
    void theProbeGetsABoundedReadTimeoutWhenTheDriverHasNone() {
        DefaultJedisClientConfig data = DriverImpl.buildClientConfig(null, null, false, 1000, 0);
        assertEquals(
                DriverImpl.DEFAULT_CLUSTER_PROBE_TIMEOUT_MILLIS,
                DriverImpl.clusterProbeConfig(data).getSocketTimeoutMillis());
    }

    @Test
    void theProbeKeepsAnExplicitReadTimeout() {
        DefaultJedisClientConfig data = DriverImpl.buildClientConfig("u", "p", false, 1000, 7000);
        DefaultJedisClientConfig probe = DriverImpl.clusterProbeConfig(data);
        assertEquals(7000, probe.getSocketTimeoutMillis());
        assertEquals("u", probe.getUser());
        assertEquals("p", probe.getPassword());
    }

    // --- redirects -------------------------------------------------------------------------------

    @Test
    void aRedirectRelearnsTheTopologyAndRetriesOnTheNewOwner() {
        AtomicInteger firstNodeAsked = new AtomicInteger();
        cluster = new FakeCluster(2, (node, command) -> {
            if (!FakeRedisServer.startsWith(command, "GRAPH.QUERY")) {
                return null;
            }
            if (node == 0 && firstNodeAsked.getAndIncrement() == 0) {
                // The graph has just moved to node 1.
                cluster.assign(1, 0);
                return FakeRedisServer.error("MOVED 100 " + cluster.node(1).address());
            }
            return WRITE_REPLY;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        driver.graph(FakeCluster.lowGraph()).query("CREATE ()");

        assertEquals(1, graphQueries(cluster.node(0)), "tried the old owner once");
        assertEquals(1, graphQueries(cluster.node(1)), "then followed the redirect");
    }

    @Test
    void aGraphThatKeepsRedirectingIsGivenUpOn() {
        cluster = new FakeCluster(
                2,
                (node, command) -> FakeRedisServer.startsWith(command, "GRAPH.QUERY")
                        ? FakeRedisServer.error("MOVED 100 " + cluster.node(1).address())
                        : null);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        GraphException e = assertThrows(
                GraphException.class, () -> driver.graph(FakeCluster.lowGraph()).query("CREATE ()"));

        assertTrue(e.getCause() instanceof JedisMovedDataException, String.valueOf(e.getCause()));
        assertEquals(DriverImpl.CLUSTER_REDIRECT_ATTEMPTS, graphQueries(cluster.node(0)));
    }

    @Test
    void aFailureThatIsNotARedirectIsNotRetried() {
        cluster = new FakeCluster(
                2,
                (node, command) -> FakeRedisServer.startsWith(command, "GRAPH.QUERY")
                        ? FakeRedisServer.error("ERR Invalid input")
                        : null);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertThrows(
                GraphException.class, () -> driver.graph(FakeCluster.lowGraph()).query("nonsense"));

        assertEquals(1, graphQueries(cluster.node(0)));
    }

    @Test
    void readOnlyQueriesProfilesAndExplainsAreRetriedToo() {
        AtomicInteger asked = new AtomicInteger();
        cluster = new FakeCluster(2, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.RO_QUERY")
                    || FakeRedisServer.startsWith(command, "GRAPH.PROFILE")
                    || FakeRedisServer.startsWith(command, "GRAPH.EXPLAIN")) {
                if (node == 0 && asked.getAndIncrement() % 2 == 0) {
                    return FakeRedisServer.error("MOVED 100 " + cluster.node(0).address());
                }
                return FakeRedisServer.startsWith(command, "GRAPH.EXPLAIN")
                        ? FakeRedisServer.array(FakeRedisServer.bulk("Results"))
                        : WRITE_REPLY;
            }
            return null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());
        String graph = FakeCluster.lowGraph();

        driver.graph(graph).readOnlyQuery("RETURN 1");
        driver.graph(graph).profile("RETURN 1");
        assertEquals(Arrays.asList("Results"), driver.graph(graph).explain("RETURN 1"));
    }

    @Test
    void readOnlyQueriesWithATimeoutAreRoutedToo() {
        cluster = new FakeCluster(
                2, (node, command) -> FakeRedisServer.startsWith(command, "GRAPH.RO_QUERY") ? WRITE_REPLY : null);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        driver.graph(FakeCluster.highGraph()).readOnlyQuery("RETURN 1", 500L);

        assertEquals(1, cluster.node(1).count("GRAPH.RO_QUERY"));
    }

    @Test
    void aDriverCanBeBuiltOnAnyConnectionSource() throws Exception {
        cluster = new FakeCluster(1, DriverImplClusterTest::queryReply);
        ClusterConnections connections = new ClusterConnections(
                java.util.Collections.singleton(HostAndPort.from(cluster.node(0).address())),
                new org.apache.commons.pool2.impl.GenericObjectPoolConfig<Jedis>(),
                DefaultJedisClientConfig.builder().build());
        driver = new DriverImpl(connections);

        driver.graph("g").query("CREATE ()");

        assertEquals(1, graphQueries(cluster.node(0)));
    }

    @Test
    void explainPlansWithNonTextLinesOrAScalarReplyAreStillReturned() {
        cluster = new FakeCluster(1, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.EXPLAIN")) {
                return command.get(2).equals("lines")
                        ? FakeRedisServer.array(FakeRedisServer.integer(7))
                        : FakeRedisServer.bulk("Results");
            }
            return null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertEquals(Arrays.asList("7"), driver.graph("g").explain("lines"));
        assertEquals(Arrays.asList("Results"), driver.graph("g").explain("scalar"));
    }

    @Test
    void unusualUdfRepliesAreReportedAsFailure() {
        cluster = new FakeCluster(1, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.UDF", "FLUSH")) {
                return "$-1\r\n";
            }
            if (FakeRedisServer.startsWith(command, "GRAPH.UDF", "LOAD")) {
                return FakeRedisServer.integer(1);
            }
            if (FakeRedisServer.startsWith(command, "GRAPH.UDF", "DELETE")) {
                return command.get(2).equals("missing") ? "$-1\r\n" : FakeRedisServer.array();
            }
            return null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertFalse(driver.udfFlush());
        assertFalse(driver.udfLoad("lib", "script", false));
        assertFalse(driver.udfDelete("missing"));
        assertFalse(driver.udfDelete("lib"));
    }

    @Test
    void copyAndDeleteAreRoutedAndRetried() {
        AtomicInteger asked = new AtomicInteger();
        cluster = new FakeCluster(2, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.COPY")
                    || FakeRedisServer.startsWith(command, "GRAPH.DELETE")) {
                if (asked.getAndIncrement() % 2 == 0) {
                    return FakeRedisServer.error("MOVED 100 " + cluster.node(0).address());
                }
                return FakeRedisServer.bulk("Graph removed, internal execution time: 0.1 ms");
            }
            return null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());
        String graph = FakeCluster.lowGraph();

        driver.graph(graph).copyGraph("copy");
        driver.graph(graph).deleteGraph();

        assertEquals(4, cluster.node(0).count("GRAPH.COPY") + cluster.node(0).count("GRAPH.DELETE"));
    }

    @Test
    void explainCopyAndDeleteThatKeepRedirectingEndInAGraphException() {
        // The redirect is still recognised inside the GraphException, so each command is retried before
        // the last redirect is reported.
        cluster = new FakeCluster(2, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.EXPLAIN")
                    || FakeRedisServer.startsWith(command, "GRAPH.COPY")
                    || FakeRedisServer.startsWith(command, "GRAPH.DELETE")) {
                return FakeRedisServer.error("MOVED 100 " + cluster.node(0).address());
            }
            return null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());
        String graph = FakeCluster.lowGraph();

        Map<String, Runnable> commands = new LinkedHashMap<>();
        commands.put("GRAPH.EXPLAIN", () -> driver.graph(graph).explain("RETURN 1"));
        commands.put("GRAPH.COPY", () -> driver.graph(graph).copyGraph("copy"));
        commands.put("GRAPH.DELETE", () -> driver.graph(graph).deleteGraph());
        for (Map.Entry<String, Runnable> command : commands.entrySet()) {
            GraphException e = assertThrows(GraphException.class, command.getValue()::run);
            assertTrue(e.getCause() instanceof JedisMovedDataException, String.valueOf(e.getCause()));
            assertEquals(
                    DriverImpl.CLUSTER_REDIRECT_ATTEMPTS, cluster.node(0).count(command.getKey()), command.getKey());
        }
    }

    // --- routed() --------------------------------------------------------------------------------

    /** A cluster {@link Connections} that records how it was told about topology changes. */
    private static final class RecordingConnections implements Connections {
        final AtomicInteger topologyChanges = new AtomicInteger();
        final boolean cluster;

        RecordingConnections(boolean cluster) {
            this.cluster = cluster;
        }

        @Override
        public Jedis get(String routingKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCluster() {
            return cluster;
        }

        @Override
        public void topologyChanged() {
            topologyChanges.incrementAndGet();
        }

        @Override
        public void close() {}
    }

    private static GraphException moved() {
        return new GraphException(new JedisMovedDataException("MOVED", new HostAndPort("h", 1), 1));
    }

    @Test
    void outsideAClusterAnOperationRunsOnceWhateverHappens() {
        RecordingConnections connections = new RecordingConnections(false);
        DriverImpl driverImpl = new DriverImpl(connections);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(
                GraphException.class,
                () -> driverImpl.routed(() -> {
                    calls.incrementAndGet();
                    throw moved();
                }));

        assertEquals(1, calls.get());
        assertEquals(0, connections.topologyChanges.get());
    }

    @Test
    void inAClusterARedirectIsRetriedAfterRelearning() {
        RecordingConnections connections = new RecordingConnections(true);
        DriverImpl driverImpl = new DriverImpl(connections);
        AtomicInteger calls = new AtomicInteger();

        String result = driverImpl.routed(() -> {
            if (calls.incrementAndGet() < 3) {
                throw moved();
            }
            return "done";
        });

        assertEquals("done", result);
        assertEquals(3, calls.get());
        assertEquals(2, connections.topologyChanges.get());
    }

    @Test
    void aBareRedirectionExceptionCountsToo() {
        RecordingConnections connections = new RecordingConnections(true);
        DriverImpl driverImpl = new DriverImpl(connections);
        AtomicInteger calls = new AtomicInteger();

        driverImpl.routed(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new JedisMovedDataException("MOVED", new HostAndPort("h", 1), 1);
            }
            return "ok";
        });

        assertEquals(2, calls.get());
    }

    @Test
    void theLastRedirectIsReportedAfterTheAttemptsRunOut() {
        RecordingConnections connections = new RecordingConnections(true);
        DriverImpl driverImpl = new DriverImpl(connections);
        AtomicInteger calls = new AtomicInteger();
        GraphException failure = moved();

        GraphException thrown = assertThrows(
                GraphException.class,
                () -> driverImpl.routed(() -> {
                    calls.incrementAndGet();
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(DriverImpl.CLUSTER_REDIRECT_ATTEMPTS, calls.get());
        assertEquals(DriverImpl.CLUSTER_REDIRECT_ATTEMPTS, connections.topologyChanges.get());
    }

    @Test
    void anUnreachableNodeRelearnsTheTopologyButIsNotRetried() {
        // The command may have run before the connection dropped, and a graph write must not be repeated.
        RecordingConnections connections = new RecordingConnections(true);
        DriverImpl driverImpl = new DriverImpl(connections);
        AtomicInteger calls = new AtomicInteger();

        assertThrows(
                JedisConnectionException.class,
                () -> driverImpl.routed(() -> {
                    calls.incrementAndGet();
                    throw new JedisConnectionException("node down");
                }));

        assertEquals(1, calls.get());
        assertEquals(1, connections.topologyChanges.get());
    }

    @Test
    void otherFailuresPassThroughUntouched() {
        RecordingConnections connections = new RecordingConnections(true);
        DriverImpl driverImpl = new DriverImpl(connections);

        assertThrows(
                JedisDataException.class,
                () -> driverImpl.routed(() -> {
                    throw new JedisDataException("ERR");
                }));

        assertEquals(0, connections.topologyChanges.get());
    }

    @Test
    void recognisesRedirectsAnywhereInTheCauseChain() {
        JedisMovedDataException moved = new JedisMovedDataException("MOVED", new HostAndPort("h", 1), 1);
        assertTrue(DriverImpl.isRedirect(moved));
        assertTrue(DriverImpl.isRedirect(new GraphException(moved)));
        assertTrue(DriverImpl.isRedirect(new RuntimeException(new GraphException(moved))));
        JedisAskDataException ask = new JedisAskDataException("ASK", new HostAndPort("h", 1), 1);
        assertTrue(DriverImpl.isRedirect(new GraphException(ask)));
        assertFalse(DriverImpl.isRedirect(new GraphException(new JedisDataException("ERR"))));
        assertFalse(DriverImpl.isRedirect(new RuntimeException()));
    }

    // --- server-wide commands --------------------------------------------------------------------

    @Test
    void listGraphsMergesTheGraphsOfEveryPrimary() {
        cluster = new FakeCluster(2, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.LIST")) {
                return node == 0
                        ? FakeRedisServer.array(FakeRedisServer.bulk("a"), FakeRedisServer.bulk("shared"))
                        : FakeRedisServer.array(FakeRedisServer.bulk("b"), FakeRedisServer.bulk("shared"));
            }
            return null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertEquals(Arrays.asList("a", "shared", "b"), driver.listGraphs());
    }

    @Test
    void udfLibrariesAreLoadedAndRemovedOnEveryPrimary() {
        cluster = new FakeCluster(
                3, (node, command) -> FakeRedisServer.startsWith(command, "GRAPH.UDF") ? FakeRedisServer.ok() : null);
        driver = DriverImpl.connect(
                "unused.invalid",
                6379,
                null,
                null,
                SentinelOptions.disabled(),
                ClusterOptions.explicit(cluster.addresses()));

        // Three nodes, but the fake layout only hands slots to two of them; every owner is visited.
        assertTrue(driver.udfLoad("lib", "script", false));
        assertTrue(driver.udfLoad("lib", "script", true));
        assertTrue(driver.udfDelete("lib"));
        assertTrue(driver.udfFlush());

        for (int node : new int[] {0, 1}) {
            assertEquals(2, cluster.node(node).count("GRAPH.UDF", "LOAD"), "node " + node);
            assertEquals(1, cluster.node(node).count("GRAPH.UDF", "DELETE"), "node " + node);
            assertEquals(1, cluster.node(node).count("GRAPH.UDF", "FLUSH"), "node " + node);
        }
    }

    @Test
    void aFailureOnOnePrimaryIsNotHidden() {
        cluster = new FakeCluster(2, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.UDF", "LOAD")) {
                return node == 1 ? FakeRedisServer.error("ERR library already exists") : FakeRedisServer.ok();
            }
            return null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertThrows(JedisDataException.class, () -> driver.udfLoad("lib", "script", false));
    }

    @Test
    void aLibraryTheNodesDisagreeAboutIsReportedAsNotDeleted() {
        cluster = new FakeCluster(
                2,
                (node, command) -> FakeRedisServer.startsWith(command, "GRAPH.UDF", "DELETE")
                        ? FakeRedisServer.integer(node == 0 ? 1 : 0)
                        : null);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertFalse(driver.udfDelete("lib"));
    }

    @Test
    void configIsSetOnEveryPrimaryAndReadFromOne() {
        cluster = new FakeCluster(2, (node, command) -> {
            if (FakeRedisServer.startsWith(command, "GRAPH.CONFIG", "GET")) {
                return FakeRedisServer.array(FakeRedisServer.bulk("RESULTSET_SIZE"), FakeRedisServer.integer(100));
            }
            return FakeRedisServer.startsWith(command, "GRAPH.CONFIG", "SET") ? FakeRedisServer.ok() : null;
        });
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertTrue(driver.configSet("RESULTSET_SIZE", 100));
        assertEquals("100", driver.configGet("RESULTSET_SIZE"));

        assertEquals(1, cluster.node(0).count("GRAPH.CONFIG", "SET"));
        assertEquals(1, cluster.node(1).count("GRAPH.CONFIG", "SET"));
        assertEquals(
                1,
                cluster.node(0).count("GRAPH.CONFIG", "GET") + cluster.node(1).count("GRAPH.CONFIG", "GET"));
    }

    @Test
    void udfListsAreReadFromOneNode() {
        cluster = new FakeCluster(
                2,
                (node, command) ->
                        FakeRedisServer.startsWith(command, "GRAPH.UDF", "LIST") ? FakeRedisServer.array() : null);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        assertTrue(driver.udfList().isEmpty());
        assertTrue(driver.udfList("lib", true).isEmpty());
        assertEquals(
                2, cluster.node(0).count("GRAPH.UDF", "LIST") + cluster.node(1).count("GRAPH.UDF", "LIST"));
    }

    @Test
    void theRawConnectionIsABorrowedConnectionToSomeNode() {
        cluster = new FakeCluster(2, null);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());

        try (Jedis connection = driver.getConnection()) {
            assertEquals("PONG", connection.ping());
        }
    }

    @Test
    void aClosedClusterDriverRefusesFurtherWork() throws Exception {
        cluster = new FakeCluster(2, DriverImplClusterTest::queryReply);
        connectToSeed(cluster.node(0), ClusterOptions.autoDetect());
        driver.graph(FakeCluster.lowGraph()).query("CREATE ()");

        driver.close();

        assertThrows(IllegalStateException.class, () -> driver.graph(FakeCluster.lowGraph())
                .query("CREATE ()"));
    }
}
