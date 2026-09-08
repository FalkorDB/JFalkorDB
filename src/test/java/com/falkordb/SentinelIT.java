package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Connects through a real Redis Sentinel deployment.
 *
 * <p>The unit tests around {@code Sentinels} cover the parsing and the decision logic against
 * synthetic replies, which is where the interesting edge cases are, but they cannot show that the
 * driver actually reaches a master through a Sentinel: that depends on Jedis' {@code
 * JedisSentinelPool}, on the client configs the driver derives for the data and Sentinel connections,
 * and on Sentinel answering {@code INFO} and {@code SENTINEL MASTERS} the way detection assumes. This
 * test runs the whole path against a master, a replica and a Sentinel monitoring them.
 *
 * <p>Writing (rather than reading) through the resolved pool is the point of the assertions: the
 * topology contains a read-only replica, so a write proves the driver landed on the master
 * specifically, which a read would not.
 */
public class SentinelIT {

    private static final String MASTER_NETWORK_ALIAS = "master";
    private static final String MASTER_NAME = "mymaster";
    private static final int SENTINEL_PORT = 26379;
    private static final Duration SENTINEL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REACHABILITY_TIMEOUT = Duration.ofSeconds(2);

    private static Network network;
    private static GenericContainer<?> masterContainer;
    private static GenericContainer<?> replicaContainer;
    private static GenericContainer<?> sentinelContainer;

    private static String sentinelHost;
    private static int sentinelPort;

    @BeforeAll
    static void startSentinelDeployment() throws Exception {
        // Like ReplicaReadIT, this topology cannot be served by an external FALKORDB_HOST/FALKORDB_PORT
        // server: it needs three servers wired together on a shared network.
        Assumptions.assumeFalse(
                TestServer.isExternal(), "the Sentinel topology requires Testcontainers, not an external server");

        DockerImageName image = FalkorDbImage.resolve(FalkorDbImage.pickOverride(
                System.getProperty("FALKORDB_IMAGE"), () -> System.getenv("FALKORDB_IMAGE")));

        network = Network.newNetwork();
        masterContainer = new GenericContainer<>(image)
                .withExposedPorts(6379)
                .withNetwork(network)
                .withNetworkAliases(MASTER_NETWORK_ALIAS)
                .waitingFor(Wait.forListeningPort());
        masterContainer.start();

        replicaContainer = new GenericContainer<>(image)
                .withExposedPorts(6379)
                .withNetwork(network)
                .waitingFor(Wait.forListeningPort());
        replicaContainer.start();
        redisCli(replicaContainer, 6379, "REPLICAOF", MASTER_NETWORK_ALIAS, "6379");

        sentinelContainer = new GenericContainer<>(image)
                .withExposedPorts(SENTINEL_PORT)
                .withNetwork(network)
                // Sentinel rewrites its own configuration in place as it learns the topology, so the
                // file has to be writable and to live somewhere writable (`dir /tmp` below).
                .withCopyToContainer(Transferable.of(sentinelConfiguration(), 0666), "/tmp/sentinel.conf")
                // The image's entrypoint script starts a FalkorDB server and ignores the command, so it
                // has to be replaced outright rather than merely overridden with withCommand.
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("redis-sentinel"))
                .withCommand("/tmp/sentinel.conf")
                .waitingFor(Wait.forListeningPort());
        sentinelContainer.start();

        sentinelHost = sentinelContainer.getHost();
        sentinelPort = sentinelContainer.getMappedPort(SENTINEL_PORT);

        awaitMonitoredMaster();
    }

    @AfterAll
    static void stopSentinelDeployment() {
        stopQuietly(sentinelContainer);
        stopQuietly(replicaContainer);
        stopQuietly(masterContainer);
        if (network != null) {
            network.close();
        }
    }

    /**
     * {@code resolve-hostnames} lets the deployment be described by its Docker network alias instead of
     * an address that is only known once the container is running. The quorum is 1 because there is a
     * single Sentinel here; failover behaviour is Sentinel's business, not the driver's.
     */
    private static String sentinelConfiguration() {
        return "port " + SENTINEL_PORT + "\n"
                + "dir /tmp\n"
                + "sentinel resolve-hostnames yes\n"
                + "sentinel monitor " + MASTER_NAME + " " + MASTER_NETWORK_ALIAS + " 6379 1\n"
                + "sentinel down-after-milliseconds " + MASTER_NAME + " 5000\n"
                + "sentinel failover-timeout " + MASTER_NAME + " 10000\n";
    }

    @Test
    public void pointingTheDriverAtASentinelFindsTheMaster() throws Exception {
        assumeMasterIsReachableFromHere();

        // No Sentinel configuration at all: just the address of a Sentinel, which is the whole point of
        // auto-detection and matches how falkordb-py, falkordb-go and falkordb-ts behave.
        try (Driver driver = FalkorDB.driver(sentinelHost, sentinelPort)) {
            assertWritesReachTheMaster(driver, "sentinel-it-auto");
        }
    }

    @Test
    public void anExplicitlyConfiguredSentinelFindsTheMaster() throws Exception {
        assumeMasterIsReachableFromHere();

        try (Driver driver = FalkorDB.builder()
                .sentinel(MASTER_NAME, sentinelHost + ":" + sentinelPort)
                .build()) {
            assertWritesReachTheMaster(driver, "sentinel-it-explicit");
        }
    }

    @Test
    public void theSentinelEnvironmentVariablesConfigureADriver() throws Exception {
        assumeMasterIsReachableFromHere();

        Map<String, String> environment = new HashMap<>();
        environment.put(DriverEnvironment.SENTINEL_MASTER_VAR, MASTER_NAME);
        environment.put(DriverEnvironment.SENTINELS_VAR, sentinelHost + ":" + sentinelPort);

        try (Driver driver = DriverEnvironment.resolve(environment::get)) {
            assertWritesReachTheMaster(driver, "sentinel-it-env");
        }
    }

    @Test
    public void autoDetectionCanBeTurnedOff() throws Exception {
        // The opt-out has to be observable, otherwise it is decoration. With detection disabled the
        // driver talks to the Sentinel directly, and a Sentinel does not implement GRAPH.QUERY — so the
        // query must fail. This is also the behaviour every release before Sentinel support had.
        try (Driver driver = FalkorDB.builder()
                .host(sentinelHost)
                .port(sentinelPort)
                .autoDetectSentinel(false)
                .build()) {
            try (Graph graph = driver.graph("sentinel-it-optout")) {
                assertThrows(RuntimeException.class, () -> graph.query("CREATE (:N {v:1})"));
            }
        }
    }

    /**
     * Writes through {@code driver} and confirms on the master container itself that the graph landed
     * there. Checking the key on the master, rather than reading it back through the same driver, is
     * what actually pins the routing: a read would be satisfied by whichever server the driver happened
     * to pick.
     */
    private static void assertWritesReachTheMaster(Driver driver, String graphName) {
        try (Graph graph = driver.graph(graphName)) {
            ResultSet created =
                    graph.query("CREATE (:Sentinel {name:$name})", Collections.singletonMap("name", graphName));
            assertEquals(1, created.getStatistics().nodesCreated());

            ResultSet read = graph.readOnlyQuery("MATCH (n:Sentinel) RETURN n.name");
            assertEquals(1, read.size());
            assertEquals(graphName, read.iterator().next().getValue(0));
        }

        try {
            assertEquals(
                    "1",
                    redisCli(masterContainer, 6379, "EXISTS", graphName).trim(),
                    "the graph was not written to the master");
        } catch (Exception e) {
            throw new IllegalStateException("could not check the master for " + graphName, e);
        }
    }

    /**
     * Sentinel reports the master by its address on the Docker network. That address is routable from
     * the host on Linux (where CI runs this for real) but not through the VM that backs Docker Desktop
     * on macOS or Windows, where no amount of port mapping helps because the address comes from the
     * Sentinel rather than from Testcontainers. Rather than silently passing a test that never
     * connected, skip when the address cannot be reached.
     */
    private static void assumeMasterIsReachableFromHere() {
        String address;
        try {
            address = redisCli(sentinelContainer, SENTINEL_PORT, "SENTINEL", "get-master-addr-by-name", MASTER_NAME);
        } catch (Exception e) {
            throw new IllegalStateException("could not ask the Sentinel for the master address", e);
        }
        String[] lines = address.trim().split("\\s+", -1);
        assertTrue(lines.length >= 2, "unexpected Sentinel reply: " + address);

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(lines[0], Integer.parseInt(lines[1])), (int) REACHABILITY_TIMEOUT.toMillis());
        } catch (IOException | RuntimeException unreachable) {
            Assumptions.abort("the Sentinel-reported master address " + lines[0] + ":" + lines[1]
                    + " is not routable from this host (expected on Docker Desktop; CI runs this on Linux)");
        }
    }

    private static void awaitMonitoredMaster() throws Exception {
        long deadline = System.nanoTime() + SENTINEL_TIMEOUT.toNanos();
        String lastSeen = "";
        while (System.nanoTime() < deadline) {
            lastSeen = redisCli(sentinelContainer, SENTINEL_PORT, "SENTINEL", "master", MASTER_NAME);
            // Exactly "master": the reply also contains the master's *name*, so a substring test would
            // match `mymaster`, and `flags` degrades to values like `master,disconnected` or
            // `s_down,master` while Sentinel is still making up its mind about a master it has just
            // been pointed at. Only a bare `master` means it is usable.
            if ("master".equals(fieldsOf(lastSeen).get("flags"))) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("the Sentinel never reported a healthy master. Last reply:\n" + lastSeen);
    }

    /** Reads a {@code SENTINEL master} reply, which {@code --raw} renders as alternating field/value lines. */
    private static Map<String, String> fieldsOf(String reply) {
        String[] lines = reply.split("\n", -1);
        Map<String, String> fields = new HashMap<>();
        for (int i = 0; i + 1 < lines.length; i += 2) {
            fields.put(lines[i].trim(), lines[i + 1].trim());
        }
        return fields;
    }

    private static String redisCli(GenericContainer<?> container, int port, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 4];
        command[0] = "redis-cli";
        command[1] = "-p";
        command[2] = Integer.toString(port);
        // --raw keeps multi-bulk replies as plain newline-separated values. redis-cli already does
        // that when its output is not a terminal, which is the case here, but saying so explicitly
        // means the parsing below does not silently depend on that detection.
        command[3] = "--raw";
        System.arraycopy(arguments, 0, command, 4, arguments.length);
        Container.ExecResult result = container.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("redis-cli failed: " + result.getStderr());
        }
        return result.getStdout();
    }

    private static void stopQuietly(GenericContainer<?> container) {
        if (container != null) {
            container.stop();
        }
    }
}
