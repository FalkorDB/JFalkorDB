package com.falkordb.examples;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shows connecting to a <a
 * href="https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/">Redis Cluster</a>.
 * A cluster spreads its keys over several primaries, and a graph is one key, so each graph lives on
 * one node chosen by hashing its name. The driver learns the layout from whichever node it is given
 * and sends every command to the node that owns the graph, so no query is answered with a {@code
 * MOVED} redirect and a resharded or failed-over cluster is followed.
 *
 * <p>The blocks below are the whole surface:
 *
 * <ol>
 *   <li><b>Auto-detection</b> — the common case, and the only block here that runs queries. Every
 *       factory method probes the endpoint it is given with {@code INFO server}; if it reports
 *       {@code redis_mode:cluster}, the driver switches to cluster mode. Nothing needs configuring,
 *       and an endpoint that is an ordinary FalkorDB server is used directly exactly as in earlier
 *       releases.
 *   <li><b>Explicit startup nodes</b> — name several nodes so the driver still finds the cluster when
 *       one of them is down, and skip the probe.
 *   <li><b>Cluster-wide commands</b> — listing graphs and loading UDF libraries cover every
 *       primary, not just one.
 *   <li><b>Opting out</b> — skips the probe for an address known to be a plain server, or whose ACL
 *       forbids {@code INFO}.
 * </ol>
 *
 * <p>Blocks 2 and 4 only build and close a driver. That runs anywhere, with or without a cluster
 * reachable, because building a driver performs no I/O — the probe happens on first use, like the
 * first connection — so they are here to show the configuration, as {@link ConfiguredDriver} is.
 *
 * <p>Blocks 1 and 3 need something to talk to. Pass {@code <host> <port>} to point them at any node
 * of a cluster (or at a plain server, which is the point of auto-detection); they default to {@code
 * localhost:6379}, which {@code just db-up} provides. Build the examples with {@code just
 * examples}, then run this class — see {@code examples/README.md}.
 */
public final class ClusterDriver {

    /** Enough graphs that, hashed by name, they spread over every primary of a small cluster. */
    private static final int GRAPHS = 12;

    private ClusterDriver() {}

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        queryThroughAutoDetectedEndpoint(host, port);
        showExplicitStartupNodes();
        showClusterWideCommands(host, port);
        showAutoDetectionTurnedOff();
    }

    /**
     * The headline case: an ordinary factory call that works out for itself whether {@code
     * host:port} is a cluster node or a plain FalkorDB server. Each graph below may live on a
     * different node; the same code runs unchanged either way.
     */
    private static void queryThroughAutoDetectedEndpoint(String host, int port) throws IOException {
        System.out.println("Connecting to " + host + ":" + port + " (cluster node or server - detected automatically)");

        String run = UUID.randomUUID().toString();
        try (Driver driver = FalkorDB.driver(host, port)) {
            List<GraphContextGenerator> graphs = new ArrayList<>();
            try {
                for (int i = 0; i < GRAPHS; i++) {
                    GraphContextGenerator graph = driver.graph("cluster-example-" + run + "-" + i);
                    graphs.add(graph);
                    graph.query("CREATE (:Person {name: 'Person " + i + "', age: " + (20 + i) + "})");
                }

                for (GraphContextGenerator graph : graphs) {
                    ResultSet people = graph.readOnlyQuery("MATCH (p:Person) RETURN p.name, p.age");
                    for (Record record : people) {
                        System.out.println(record.getString("p.name") + " is " + record.getValue("p.age"));
                    }
                }
            } finally {
                for (GraphContextGenerator graph : graphs) {
                    // Best-effort cleanup: don't let a failure here mask a real error from the queries above.
                    try {
                        graph.deleteGraph(); // remove this example's throwaway graphs
                    } catch (RuntimeException cleanupError) {
                        System.err.println("Failed to delete example graph: " + cleanupError.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Naming several nodes lets the driver learn the layout from whichever answers first, so one node
     * being down at start-up does not matter.
     */
    private static void showExplicitStartupNodes() throws IOException {
        try (Driver driver = FalkorDB.builder()
                .cluster("node-a:6379", "node-b:6379", "node-c:6379")
                .credentials("app-user", "app-password")
                .build()) {
            System.out.println("Built a driver for a cluster with three startup nodes: "
                    + driver.getClass().getSimpleName());
        }
    }

    /**
     * Server-wide state is per node, so these reach every primary: {@code listGraphs()} merges the
     * graphs each one holds, and {@code udfLoad} / {@code udfDelete} / {@code udfFlush} /
     * {@code configSet} run on all of them.
     */
    private static void showClusterWideCommands(String host, int port) throws IOException {
        try (Driver driver = FalkorDB.driver(host, port)) {
            System.out.println("Graphs on every primary: " + driver.listGraphs());
        }
    }

    /** Auto-detection costs one {@code INFO} on the first connection; this skips it. */
    private static void showAutoDetectionTurnedOff() throws IOException {
        try (Driver driver =
                FalkorDB.builder().host("db.example.com").autoDetectCluster(false).build()) {
            System.out.println("Built a driver that connects directly, without probing for a cluster: "
                    + driver.getClass().getSimpleName());
        }
    }
}
