package com.falkordb;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Test-only endpoint for a three-primary FalkorDB Redis Cluster.
 *
 * <p>By default it starts a single Testcontainers-managed container that runs three cluster nodes on
 * ports {@value #FIRST_PORT}-{@code 7003} and publishes them on the same host ports. The ports have to
 * match inside and outside the container because a cluster tells clients to reconnect to the addresses
 * the nodes announce, so they cannot be mapped to dynamic ones. Set {@code FALKORDB_CLUSTER_NODES} (a
 * comma-separated list of {@code host:port}) to use an already-running cluster instead; no container
 * is started then.
 *
 * <p>The cluster tests mutate server-wide state (UDF libraries, configuration), so an external cluster
 * must be a disposable one.
 */
final class ClusterTestServer {

    static final int FIRST_PORT = 7001;
    private static final int NODES = 3;
    private static final String READY = "CLUSTER_READY";

    private static final List<String> nodes;

    static {
        String external = System.getenv("FALKORDB_CLUSTER_NODES");
        if (external != null && !external.trim().isEmpty()) {
            nodes = Arrays.asList(external.trim().split("\\s*,\\s*"));
        } else {
            GenericContainer<?> container = new GenericContainer<>(FalkorDbImage.resolve(FalkorDbImage.pickOverride(
                            System.getProperty("FALKORDB_IMAGE"), () -> System.getenv("FALKORDB_IMAGE"))))
                    .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh", "-c", startScript()))
                    .waitingFor(Wait.forLogMessage(".*" + READY + ".*", 1))
                    .withStartupTimeout(Duration.ofMinutes(2));
            // Fixed host ports on purpose, see the class comment. One call: it replaces earlier bindings.
            List<String> bindings = new ArrayList<>();
            for (int i = 0; i < NODES; i++) {
                int port = FIRST_PORT + i;
                bindings.add(port + ":" + port);
                bindings.add((port + 10000) + ":" + (port + 10000));
            }
            container.setPortBindings(bindings);
            container.start(); // shared for the whole JVM; Testcontainers' Ryuk stops it at exit
            nodes = Arrays.asList("127.0.0.1:7001", "127.0.0.1:7002", "127.0.0.1:7003");
        }
    }

    private ClusterTestServer() {}

    /** Every node of the cluster in {@code host:port} form. */
    static List<String> nodes() {
        return nodes;
    }

    /** One node, to use as the single seed a user would normally configure. */
    static String seed() {
        return nodes.get(0);
    }

    static String seedHost() {
        return seed().substring(0, seed().lastIndexOf(':'));
    }

    static int seedPort() {
        String seed = seed();
        String port = seed.substring(seed.lastIndexOf(':') + 1);
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("cluster seed has no valid port: " + seed, e);
        }
    }

    private static String startScript() {
        StringBuilder script = new StringBuilder();
        StringBuilder members = new StringBuilder();
        for (int i = 0; i < NODES; i++) {
            int port = FIRST_PORT + i;
            script.append("redis-server --port ")
                    .append(port)
                    .append(" --cluster-enabled yes --cluster-announce-ip 127.0.0.1 --cluster-announce-port ")
                    .append(port)
                    .append(" --cluster-announce-bus-port ")
                    .append(port + 10000)
                    .append(" --cluster-config-file /tmp/nodes-")
                    .append(port)
                    .append(".conf --cluster-node-timeout 5000 --dir /tmp")
                    .append(" --loadmodule /var/lib/falkordb/bin/falkordb.so --daemonize yes; ");
            members.append(" 127.0.0.1:").append(port);
        }
        script.append("sleep 2; redis-cli -p ")
                .append(FIRST_PORT)
                .append(" --cluster create")
                .append(members)
                .append(" --cluster-yes; ")
                .append("until redis-cli -p ")
                .append(FIRST_PORT)
                .append(" cluster info | grep -q cluster_state:ok; do sleep 1; done; echo ")
                .append(READY)
                .append("; tail -f /dev/null");
        return script.toString();
    }
}
