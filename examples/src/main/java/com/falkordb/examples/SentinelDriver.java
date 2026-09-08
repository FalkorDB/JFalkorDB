package com.falkordb.examples;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import java.io.IOException;

/**
 * Shows connecting through a <a
 * href="https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/">Redis Sentinel</a>
 * deployment, which monitors a master and its replicas and promotes a replica when the master
 * fails. The driver resolves the current master and then follows failovers for its own lifetime, so
 * queries issued after a promotion reach the new master without the driver being recreated.
 *
 * <p>The four blocks below are the whole surface:
 *
 * <ol>
 *   <li><b>Auto-detection</b> — the common case, and the only block here that runs a query. Every
 *       factory method probes the endpoint it is given with {@code INFO server}; if it turns out to
 *       be a Sentinel, the master is resolved from it. Nothing needs configuring, and an endpoint
 *       that is an ordinary FalkorDB server is used directly exactly as in earlier releases.
 *   <li><b>Explicit configuration</b> — required when one Sentinel monitors several masters, since
 *       auto-detection cannot guess which one is wanted (it says so rather than choosing), and
 *       useful for naming several Sentinels for redundancy.
 *   <li><b>Separate Sentinel credentials</b> — Sentinels frequently have their own ACL. Omit them
 *       and the Sentinel connections reuse the master's credentials.
 *   <li><b>Opting out</b> — skips the probe for an address known to be a plain server, or whose ACL
 *       forbids {@code INFO}.
 * </ol>
 *
 * <p>Blocks 2-4 only build and close a driver. That runs anywhere, with or without a Sentinel
 * reachable, because building a driver performs no I/O — the probe happens on first use, like the
 * first connection — so they are here to show the configuration, as {@link ConfiguredDriver} is.
 *
 * <p>Block 1 does need something to talk to. Pass {@code <host> <port>} to point it at a Sentinel
 * (or at a plain server, which is the point of auto-detection); it defaults to {@code
 * localhost:6379}, which {@code just db-up} provides. Build the examples with {@code just
 * examples}, then run this class — see {@code examples/README.md}.
 */
public final class SentinelDriver {

    private SentinelDriver() {}

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        queryThroughAutoDetectedEndpoint(host, port);
        showExplicitSentinelConfiguration();
        showSeparateSentinelCredentials();
        showAutoDetectionTurnedOff();
    }

    /**
     * The headline case: an ordinary factory call that works out for itself whether {@code
     * host:port} is a Sentinel or a FalkorDB server.
     */
    private static void queryThroughAutoDetectedEndpoint(String host, int port) throws IOException {
        System.out.println("Connecting to " + host + ":" + port + " (Sentinel or server - detected automatically)");

        try (Driver driver = FalkorDB.driver(host, port)) {
            GraphContextGenerator graph = driver.graph("social");
            try {
                graph.query("CREATE (:Person {name: 'Alice', age: 32}), (:Person {name: 'Bob', age: 47})");

                ResultSet people = graph.query("MATCH (p:Person) RETURN p.name, p.age ORDER BY p.age");
                for (Record record : people) {
                    System.out.println(record.getString("p.name") + " is " + record.getValue("p.age"));
                }
            } finally {
                // Best-effort cleanup: don't let a failure here mask a real error from the queries above.
                try {
                    graph.deleteGraph(); // remove this example's throwaway graph
                } catch (RuntimeException cleanupError) {
                    System.err.println("Failed to delete example graph: " + cleanupError.getMessage());
                }
            }
        }
    }

    /** Naming the deployment, which is obligatory when a Sentinel monitors more than one master. */
    private static void showExplicitSentinelConfiguration() throws IOException {
        try (Driver driver = FalkorDB.builder()
                .sentinel("mymaster", "sentinel-a:26379", "sentinel-b:26379", "sentinel-c:26379")
                .build()) {
            System.out.println("Built a driver for master 'mymaster' via three Sentinels: "
                    + driver.getClass().getSimpleName());
        }
    }

    /** {@code credentials(...)} authenticates the master; the Sentinels can differ. */
    private static void showSeparateSentinelCredentials() throws IOException {
        try (Driver driver = FalkorDB.builder()
                .sentinel("mymaster", "sentinel-a:26379")
                .credentials("app-user", "app-password")
                .sentinelCredentials("sentinel-user", "sentinel-password")
                .build()) {
            System.out.println("Built a driver authenticating to the master and the Sentinels separately: "
                    + driver.getClass().getSimpleName());
        }
    }

    /** Auto-detection costs one {@code INFO} on the first connection; this skips it. */
    private static void showAutoDetectionTurnedOff() throws IOException {
        try (Driver driver =
                FalkorDB.builder().host("db.example.com").autoDetectSentinel(false).build()) {
            System.out.println("Built a driver that connects directly, without probing for a Sentinel: "
                    + driver.getClass().getSimpleName());
        }
    }
}
