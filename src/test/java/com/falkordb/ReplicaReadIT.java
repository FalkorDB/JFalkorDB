package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Reads from a real replica.
 *
 * <p>The rest of the suite runs against a single server, where every command is legal, so it cannot
 * observe how the client behaves against a read-only replica. That gap hid a defect in which any
 * {@link Graph#readOnlyQuery(String)} returning a node, a relationship or a path failed with {@code
 * READONLY You can't write against a read only replica}: responses are decoded in compact mode, so
 * label and property-key ids had to be resolved through {@code GraphCache}, which warmed itself with
 * {@code GRAPH.QUERY}, a write.
 *
 * <p>Returning a path here is therefore the point of the test. A scalar projection never populates
 * the cache and passes even against a buggy client.
 */
public class ReplicaReadIT {

    private static final String PRIMARY_NETWORK_ALIAS = "primary";
    private static final Duration REPLICATION_TIMEOUT = Duration.ofSeconds(30);
    private static final String GRAPH_NAME = "replica-read-it";

    private static Network network;
    private static GenericContainer<?> primaryContainer;
    private static GenericContainer<?> replicaContainer;
    private static Driver primaryDriver;
    private static Driver replicaDriver;

    @BeforeAll
    static void startPrimaryAndReplica() throws Exception {
        DockerImageName image = FalkorDbImage.resolve(FalkorDbImage.pickOverride(
                System.getProperty("FALKORDB_IMAGE"), () -> System.getenv("FALKORDB_IMAGE")));

        network = Network.newNetwork();
        primaryContainer = new GenericContainer<>(image)
                .withExposedPorts(6379)
                .withNetwork(network)
                .withNetworkAliases(PRIMARY_NETWORK_ALIAS)
                .waitingFor(Wait.forListeningPort());
        primaryContainer.start();

        replicaContainer = new GenericContainer<>(image)
                .withExposedPorts(6379)
                .withNetwork(network)
                .waitingFor(Wait.forListeningPort());
        replicaContainer.start();

        // The alias is resolved inside the Docker network by the replica itself.
        redisCli(replicaContainer, "REPLICAOF", PRIMARY_NETWORK_ALIAS, "6379");
        awaitReplicationLink();

        primaryDriver = FalkorDB.driver(primaryContainer.getHost(), primaryContainer.getMappedPort(6379));
        replicaDriver = FalkorDB.driver(replicaContainer.getHost(), replicaContainer.getMappedPort(6379));

        primaryDriver
                .graph(GRAPH_NAME)
                .query("CREATE (:Location {locationId:'L1'})-[:SCHEDULED]->(:Location {locationId:'L2'})");
        awaitGraphOnReplica();
    }

    @AfterAll
    static void stopPrimaryAndReplica() {
        closeQuietly(primaryDriver);
        closeQuietly(replicaDriver);
        if (replicaContainer != null) {
            replicaContainer.stop();
        }
        if (primaryContainer != null) {
            primaryContainer.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @Test
    public void readOnlyQueryReturningAPathSucceedsAgainstAReplica() {
        // A cold cache is what exercises the label/property-key resolution path.
        try (Graph replica = replicaDriver.graph(GRAPH_NAME)) {
            ResultSet resultSet = replica.readOnlyQuery(
                    "MATCH p = (:Location {locationId:$from})-[:SCHEDULED]->(:Location) RETURN p",
                    Collections.singletonMap("from", "L1"));

            assertEquals(1, resultSet.size());
            Object value = resultSet.iterator().next().getValue(0);
            assertTrue(value instanceof Path, "expected a Path, got " + value);

            Path path = (Path) value;
            assertEquals(1, path.length());
            // Resolving these names is exactly what used to require a write command.
            Node origin = path.getNodes().get(0);
            assertEquals("Location", origin.getLabel(0));
            assertNotNull(origin.getProperty("locationId"));
            assertEquals("L1", origin.getProperty("locationId").getValue());
            assertEquals("SCHEDULED", path.getEdges().get(0).getRelationshipType());
        }
    }

    @Test
    public void readOnlyQueryReturningScalarsAlsoSucceedsAgainstAReplica() {
        try (Graph replica = replicaDriver.graph(GRAPH_NAME)) {
            Map<String, Object> parameters = Collections.singletonMap("from", "L1");
            ResultSet resultSet = replica.readOnlyQuery(
                    "MATCH (a:Location {locationId:$from})-[:SCHEDULED]->(b:Location) RETURN b.locationId", parameters);

            assertEquals(1, resultSet.size());
            Object value = resultSet.iterator().next().getValue(0);
            assertEquals("L2", value);
        }
    }

    private static void awaitReplicationLink() throws Exception {
        long deadline = System.nanoTime() + REPLICATION_TIMEOUT.toNanos();
        String lastSeen = "";
        while (System.nanoTime() < deadline) {
            lastSeen = redisCli(replicaContainer, "INFO", "replication");
            if (lastSeen.contains("role:slave") && lastSeen.contains("master_link_status:up")) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("replica never linked to the primary. Last INFO replication:\n" + lastSeen);
    }

    private static void awaitGraphOnReplica() throws Exception {
        long deadline = System.nanoTime() + REPLICATION_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            String keys = redisCli(replicaContainer, "EXISTS", GRAPH_NAME);
            if ("1".equals(keys.trim())) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("the graph never replicated to the replica");
    }

    private static String redisCli(GenericContainer<?> container, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "redis-cli";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Container.ExecResult result = container.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("redis-cli failed: " + result.getStderr());
        }
        return result.getStdout();
    }

    private static void closeQuietly(Driver driver) {
        if (driver == null) {
            return;
        }
        try {
            driver.close();
        } catch (Exception ignored) {
            // best-effort cleanup between test classes
        }
    }
}
