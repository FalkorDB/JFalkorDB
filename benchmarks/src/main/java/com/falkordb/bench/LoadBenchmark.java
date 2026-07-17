package com.falkordb.bench;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import com.falkordb.ResultSet;
import com.falkordb.Statistics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Client-overhead load sweep for the JFalkorDB client.
 *
 * <p>For each concurrency level it runs a representative query from N worker threads sharing one
 * {@link Driver} (connection pool) and measures, per operation, the <b>client latency = total
 * round-trip minus the server's reported internal execution time</b> ({@code
 * QUERY_INTERNAL_EXECUTION_TIME}). Subtracting the server time isolates what the client library
 * actually costs — connection/thread management, pooling, serialization — which is what differs
 * between a connection pool, a single non-blocking connection, and Project-Loom virtual threads.
 * A single command is dominated by server execution, so only a load sweep reveals these effects.
 *
 * <p>Outputs two {@code github-action-benchmark} custom-format files under {@code benchmarks/target}:
 * {@code bench-latency.json} (client p50/p95/p99, smaller-is-better) and {@code bench-throughput.json}
 * (ops/s, bigger-is-better).
 *
 * <p>Config via system properties: {@code bench.loads} (default {@code 1,2,4,8,16,32,64}),
 * {@code bench.warmupMs} (2000), {@code bench.measureMs} (3000). Uses a pinned Testcontainers
 * FalkorDB by default, or an external one via {@code FALKORDB_HOST}/{@code FALKORDB_PORT}.
 */
public final class LoadBenchmark {

    // Pinned digest (v4.20.1) — matches the smoke-jdk8 / test harness images.
    private static final DockerImageName IMAGE = DockerImageName.parse(
            "falkordb/falkordb@sha256:9042fdc4e53f5390ca5a3993aa71506523970efb40ffb9a98e6a4b1a9a4f8862");

    private static final String GRAPH = "bench";
    private static final int SEED_NODES = 1000;

    private LoadBenchmark() {}

    public static void main(String[] args) throws Exception {
        int[] loads = parseLoads(System.getProperty("bench.loads", "1,2,4,8,16,32,64"));
        long warmupMs = Long.getLong("bench.warmupMs", 2000L);
        long measureMs = Long.getLong("bench.measureMs", 3000L);

        GenericContainer<?> container = null;
        String host;
        int port;
        String envHost = System.getenv("FALKORDB_HOST");
        String envPort = System.getenv("FALKORDB_PORT");
        if (envHost != null && !envHost.isEmpty() && envPort != null && !envPort.isEmpty()) {
            host = envHost;
            port = parsePort(envPort);
        } else {
            container = new GenericContainer<>(IMAGE)
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());
            container.start();
            host = container.getHost();
            port = container.getMappedPort(6379);
        }

        Driver driver = FalkorDB.driver(host, port);
        List<Metric> latency = new ArrayList<>();
        List<Metric> throughput = new ArrayList<>();
        List<CurveRow> curve = new ArrayList<>();
        try {
            seed(driver);
            for (int load : loads) {
                phase(driver, load, warmupMs, false); // warmup (discarded)
                List<Worker> workers = phase(driver, load, measureMs, true); // measured
                LoadResult r = summarize(workers, measureMs);
                System.out.printf(
                        "load=%-3d ops=%-8d throughput=%9.0f ops/s   client p50=%7.1f p95=%7.1f p99=%7.1f us%n",
                        load, r.ops, r.throughput, r.p50Us, r.p95Us, r.p99Us);
                latency.add(new Metric("client_p50 @load=" + load, "us", r.p50Us));
                latency.add(new Metric("client_p95 @load=" + load, "us", r.p95Us));
                latency.add(new Metric("client_p99 @load=" + load, "us", r.p99Us));
                throughput.add(new Metric("throughput @load=" + load, "ops/s", r.throughput));
                curve.add(new CurveRow(load, r.throughput, r.p50Us, r.p95Us, r.p99Us));
            }
            writeJson(Paths.get("benchmarks", "target", "bench-latency.json"), latency);
            writeJson(Paths.get("benchmarks", "target", "bench-throughput.json"), throughput);
            writeCurveJson(Paths.get("benchmarks", "target", "bench-curve.json"), curve);
        } finally {
            try {
                driver.graph(GRAPH).deleteGraph();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
            try {
                driver.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
            if (container != null) {
                container.stop();
            }
        }
    }

    private static void seed(Driver driver) {
        GraphContextGenerator graph = driver.graph(GRAPH);
        try {
            graph.deleteGraph();
        } catch (Exception ignored) {
            // graph didn't exist
        }
        graph.query("UNWIND range(0, " + (SEED_NODES - 1) + ") AS i CREATE (:N {id: i})");
        try {
            graph.query("CREATE INDEX FOR (n:N) ON (n.id)"); // fast point lookup -> tiny server time
        } catch (Exception ignored) {
            // index may already exist or be unsupported — fine, the scan is still cheap
        }
    }

    /** Runs {@code load} worker threads for {@code durationMs}; returns them (with samples if recording). */
    private static List<Worker> phase(Driver driver, int load, long durationMs, boolean record)
            throws InterruptedException {
        long endNanos = System.nanoTime() + durationMs * 1_000_000L;
        List<Worker> workers = new ArrayList<>(load);
        List<Thread> threads = new ArrayList<>(load);
        for (int i = 0; i < load; i++) {
            Worker w = new Worker(driver, endNanos, record);
            workers.add(w);
            threads.add(new Thread(w, "bench-" + load + "-" + i));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        return workers;
    }

    private static LoadResult summarize(List<Worker> workers, long measureMs) {
        int total = 0;
        for (Worker w : workers) {
            total += w.count;
        }
        long[] all = new long[total];
        int off = 0;
        for (Worker w : workers) {
            System.arraycopy(w.samples, 0, all, off, w.count);
            off += w.count;
        }
        Arrays.sort(all);
        double throughput = total / (measureMs / 1000.0);
        return new LoadResult(
                total,
                throughput,
                percentileUs(all, 50),
                percentileUs(all, 95),
                percentileUs(all, 99));
    }

    private static double percentileUs(long[] sortedNanos, double p) {
        if (sortedNanos.length == 0) {
            return 0.0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sortedNanos.length) - 1;
        idx = Math.max(0, Math.min(sortedNanos.length - 1, idx));
        return sortedNanos[idx] / 1000.0; // ns -> us
    }

    /** Parses the server's internal execution time ("0.23 milliseconds") into nanoseconds. */
    private static long serverNanos(ResultSet rs) {
        String v = rs.getStatistics().getStringValue(Statistics.Label.QUERY_INTERNAL_EXECUTION_TIME);
        if (v == null || v.isEmpty()) {
            return 0L;
        }
        try {
            double ms = Double.parseDouble(v.trim().split("\\s+")[0]);
            return (long) (ms * 1_000_000.0);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static int[] parseLoads(String csv) {
        String[] parts = csv.split(",");
        int[] loads = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            loads[i] = Integer.parseInt(parts[i].trim());
        }
        return loads;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("FALKORDB_PORT must be a valid integer, but was \"" + value + "\"", e);
        }
    }

    private static void writeJson(Path path, List<Metric> metrics) throws IOException {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < metrics.size(); i++) {
            Metric m = metrics.get(i);
            sb.append("  {\"name\": \"")
                    .append(m.name)
                    .append("\", \"unit\": \"")
                    .append(m.unit)
                    .append("\", \"value\": ")
                    .append(String.format("%.3f", m.value))
                    .append(i < metrics.size() - 1 ? "},\n" : "}\n");
        }
        sb.append("]\n");
        Files.createDirectories(path.getParent());
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the per-thread-setting latency-vs-throughput curve consumed by curve.html. */
    private static void writeCurveJson(Path path, List<CurveRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            CurveRow r = rows.get(i);
            sb.append(String.format(
                    "  {\"threads\": %d, \"throughput\": %.3f, \"p50\": %.3f, \"p95\": %.3f, \"p99\": %.3f}%s%n",
                    r.threads, r.throughput, r.p50Us, r.p95Us, r.p99Us, i < rows.size() - 1 ? "," : ""));
        }
        sb.append("]\n");
        Files.createDirectories(path.getParent());
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** A worker that issues point-lookup queries and records client latency (total - server). */
    private static final class Worker implements Runnable {
        private final GraphContextGenerator graph;
        private final long endNanos;
        private final boolean record;
        private long[] samples = new long[1024];
        private int count;

        Worker(Driver driver, long endNanos, boolean record) {
            this.graph = driver.graph(GRAPH);
            this.endNanos = endNanos;
            this.record = record;
        }

        @Override
        public void run() {
            while (System.nanoTime() < endNanos) {
                int id = ThreadLocalRandom.current().nextInt(SEED_NODES);
                long start = System.nanoTime();
                ResultSet rs = graph.query("MATCH (n:N {id: " + id + "}) RETURN n.id");
                long totalNs = System.nanoTime() - start;
                if (record) {
                    long clientNs = Math.max(0L, totalNs - serverNanos(rs));
                    if (count == samples.length) {
                        samples = Arrays.copyOf(samples, samples.length * 2);
                    }
                    samples[count++] = clientNs;
                }
            }
        }
    }

    private static final class LoadResult {
        final long ops;
        final double throughput;
        final double p50Us;
        final double p95Us;
        final double p99Us;

        LoadResult(long ops, double throughput, double p50Us, double p95Us, double p99Us) {
            this.ops = ops;
            this.throughput = throughput;
            this.p50Us = p50Us;
            this.p95Us = p95Us;
            this.p99Us = p99Us;
        }
    }

    private static final class Metric {
        final String name;
        final String unit;
        final double value;

        Metric(String name, String unit, double value) {
            this.name = name;
            this.unit = unit;
            this.value = value;
        }
    }

    private static final class CurveRow {
        final int threads;
        final double throughput;
        final double p50Us;
        final double p95Us;
        final double p99Us;

        CurveRow(int threads, double throughput, double p50Us, double p95Us, double p99Us) {
            this.threads = threads;
            this.throughput = throughput;
            this.p50Us = p50Us;
            this.p95Us = p95Us;
            this.p99Us = p99Us;
        }
    }
}
