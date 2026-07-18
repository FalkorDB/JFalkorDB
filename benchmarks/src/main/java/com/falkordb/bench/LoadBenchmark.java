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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>Outputs three files under {@code benchmarks/target}: {@code bench-latency.json} (client
 * p50/p95/p99, smaller-is-better) and {@code bench-throughput.json} (ops/s, bigger-is-better) for the
 * github-action-benchmark radar, plus {@code bench-curve.json} (per-thread-setting throughput +
 * latencies) for the latency-vs-throughput curve page.
 *
 * <p>Config via system properties: {@code bench.loads} (default {@code 1,2,4,8,16,32,64}, all
 * positive), {@code bench.warmupMs} (2000, &ge;0), {@code bench.measureMs} (3000, &gt;0),
 * {@code bench.graph} (default a unique per-run name). Uses a pinned Testcontainers FalkorDB by
 * default, or an external one via {@code FALKORDB_HOST}/{@code FALKORDB_PORT}.
 */
public final class LoadBenchmark {

    // Pinned digest (v4.20.1) — matches the smoke-jdk8 / test harness images.
    private static final DockerImageName IMAGE = DockerImageName.parse(
            "falkordb/falkordb@sha256:9042fdc4e53f5390ca5a3993aa71506523970efb40ffb9a98e6a4b1a9a4f8862");

    // Unique per run by default, so pointing at an external server never touches a pre-existing graph.
    private static final String GRAPH =
            System.getProperty("bench.graph", "jfalkordb_bench_" + UUID.randomUUID().toString().replace("-", ""));
    private static final int SEED_NODES = 1000;

    // Parameterized point-lookup with a constant query body: the id is passed as a parameter instead
    // of being concatenated into the Cypher text, so the server can reuse one cached plan across
    // iterations rather than re-parsing/re-planning a distinct query each time — that server-side
    // parse/plan cost isn't in QUERY_INTERNAL_EXECUTION_TIME and would otherwise inflate the reported
    // client latency. The client still serializes the id into a "CYPHER id=<v> <body>" string per
    // call (Utils.prepareQuery); that's legitimate client-side cost this benchmark is meant to measure.
    private static final String LOOKUP_QUERY = "MATCH (n:N {id: $id}) RETURN n.id";

    private LoadBenchmark() {}

    public static void main(String[] args) throws Exception {
        int[] loads = parseLoads(System.getProperty("bench.loads", "1,2,4,8,16,32,64"));
        long warmupMs = requireNonNegative("bench.warmupMs", Long.getLong("bench.warmupMs", 2000L));
        long measureMs = requirePositive("bench.measureMs", Long.getLong("bench.measureMs", 3000L));

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

        Driver driver = null;
        List<Metric> latency = new ArrayList<>();
        List<Metric> throughput = new ArrayList<>();
        List<CurveRow> curve = new ArrayList<>();
        try {
            driver = FalkorDB.driver(host, port);
            seed(driver);
            for (int load : loads) {
                phase(driver, load, warmupMs, false); // warmup (discarded)
                List<Worker> workers = phase(driver, load, measureMs, true); // measured
                LoadResult r = summarize(workers, measureMs);
                System.out.printf(
                        Locale.ROOT,
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
            if (driver != null) {
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

    /**
     * Runs {@code load} worker threads for {@code durationMs} and returns them (with samples when
     * recording). All workers are released from a start gate simultaneously so high-concurrency phases
     * don't ramp up gradually (which would bias throughput and the saturation curve); any worker
     * failure is propagated so the run fails instead of publishing partial metrics.
     */
    private static List<Worker> phase(Driver driver, int load, long durationMs, boolean record)
            throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(load);
        CountDownLatch go = new CountDownLatch(1);
        long[] deadlineNanos = new long[1];
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Worker> workers = new ArrayList<>(load);
        List<Thread> threads = new ArrayList<>(load);
        for (int i = 0; i < load; i++) {
            Worker w = new Worker(driver, ready, go, deadlineNanos, record, failure);
            workers.add(w);
            threads.add(new Thread(w, "bench-" + load + "-" + i));
        }
        for (Thread t : threads) {
            t.start();
        }
        ready.await(); // every worker is constructed and parked at the gate
        deadlineNanos[0] = System.nanoTime() + durationMs * 1_000_000L;
        go.countDown(); // release all workers together; timing starts now
        for (Thread t : threads) {
            t.join();
        }
        Throwable f = failure.get();
        if (f != null) {
            throw new IllegalStateException("benchmark worker failed at load=" + load, f);
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
                total, throughput, percentileUs(all, 50), percentileUs(all, 95), percentileUs(all, 99));
    }

    static double percentileUs(long[] sortedNanos, double p) {
        if (sortedNanos.length == 0) {
            return 0.0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sortedNanos.length) - 1;
        idx = Math.max(0, Math.min(sortedNanos.length - 1, idx));
        return sortedNanos[idx] / 1000.0; // ns -> us
    }

    /**
     * Reads and parses the server's internal execution time. Fails fast (rather than returning 0) if
     * the value is missing or unparseable — otherwise the client-latency metric would silently
     * collapse back into full round-trip latency and publish misleading results.
     */
    private static long serverNanos(ResultSet rs) {
        return parseServerNanos(
                rs.getStatistics().getStringValue(Statistics.Label.QUERY_INTERNAL_EXECUTION_TIME));
    }

    /** Parses the server internal execution time ("0.23 milliseconds") into nanoseconds. */
    static long parseServerNanos(String value) {
        if (value == null) {
            throw new IllegalStateException(
                    "server did not report QUERY_INTERNAL_EXECUTION_TIME; cannot isolate client latency");
        }
        // Extract the leading numeric token by hand — this runs on every measured sample, so a
        // regex split (which recompiles a Pattern each call) would add GC/CPU noise to the hot loop.
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException(
                    "server did not report QUERY_INTERNAL_EXECUTION_TIME; cannot isolate client latency");
        }
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        String token = trimmed.substring(0, end);
        String unit = trimmed.substring(end).trim();
        if (!unit.isEmpty() && !isMilliseconds(unit)) {
            throw new IllegalStateException("QUERY_INTERNAL_EXECUTION_TIME reported an unexpected unit \"" + unit
                    + "\" (expected milliseconds); the server format may have changed");
        }
        try {
            double ms = Double.parseDouble(token);
            if (!Double.isFinite(ms) || ms < 0) {
                throw new IllegalStateException("QUERY_INTERNAL_EXECUTION_TIME reported a non-finite or negative value \""
                        + value + "\"; cannot isolate client latency");
            }
            return (long) (ms * 1_000_000.0);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "could not parse QUERY_INTERNAL_EXECUTION_TIME \"" + value
                            + "\" — the server format may have changed",
                    e);
        }
    }

    private static boolean isMilliseconds(String unit) {
        return unit.equalsIgnoreCase("ms")
                || unit.equalsIgnoreCase("millisecond")
                || unit.equalsIgnoreCase("milliseconds");
    }

    static int[] parseLoads(String csv) {
        String[] parts = csv.split(",");
        List<Integer> loads = new ArrayList<>();
        for (String part : parts) {
            String tok = part.trim();
            if (tok.isEmpty()) {
                continue;
            }
            int v;
            try {
                v = Integer.parseInt(tok);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bench.loads has a non-integer value: \"" + tok + "\"", e);
            }
            if (v <= 0) {
                throw new IllegalArgumentException("bench.loads must be positive, got " + v);
            }
            loads.add(v);
        }
        if (loads.isEmpty()) {
            throw new IllegalArgumentException("bench.loads must contain at least one positive integer");
        }
        int[] out = new int[loads.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = loads.get(i);
        }
        return out;
    }

    private static long requirePositive(String name, long v) {
        if (v <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + v);
        }
        return v;
    }

    private static long requireNonNegative(String name, long v) {
        if (v < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + v);
        }
        return v;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("FALKORDB_PORT must be a valid integer, but was \"" + value + "\"", e);
        }
    }

    static void writeJson(Path path, List<Metric> metrics) throws IOException {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < metrics.size(); i++) {
            Metric m = metrics.get(i);
            sb.append("  {\"name\": \"")
                    .append(m.name)
                    .append("\", \"unit\": \"")
                    .append(m.unit)
                    .append("\", \"value\": ")
                    .append(String.format(Locale.ROOT, "%.3f", m.value))
                    .append(i < metrics.size() - 1 ? "},\n" : "}\n");
        }
        sb.append("]\n");
        Files.createDirectories(path.getParent());
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the per-thread-setting latency-vs-throughput curve consumed by curve.html. */
    static void writeCurveJson(Path path, List<CurveRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            CurveRow r = rows.get(i);
            sb.append(String.format(
                    Locale.ROOT,
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
        private final CountDownLatch ready;
        private final CountDownLatch go;
        private final long[] deadlineNanos;
        private final boolean record;
        private final AtomicReference<Throwable> failure;
        private long[] samples = new long[1024];
        private int count;

        Worker(
                Driver driver,
                CountDownLatch ready,
                CountDownLatch go,
                long[] deadlineNanos,
                boolean record,
                AtomicReference<Throwable> failure) {
            this.graph = driver.graph(GRAPH);
            this.ready = ready;
            this.go = go;
            this.deadlineNanos = deadlineNanos;
            this.record = record;
            this.failure = failure;
        }

        @Override
        public void run() {
            try {
                Map<String, Object> params = new HashMap<>(2);
                ready.countDown();
                go.await();
                long end = deadlineNanos[0];
                while (System.nanoTime() < end) {
                    params.put("id", ThreadLocalRandom.current().nextInt(SEED_NODES));
                    long start = System.nanoTime();
                    ResultSet rs = graph.query(LOOKUP_QUERY, params);
                    long totalNs = System.nanoTime() - start;
                    if (record) {
                        long clientNs = Math.max(0L, totalNs - serverNanos(rs));
                        if (count == samples.length) {
                            samples = Arrays.copyOf(samples, samples.length * 2);
                        }
                        samples[count++] = clientNs;
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
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

    static final class Metric {
        final String name;
        final String unit;
        final double value;

        Metric(String name, String unit, double value) {
            this.name = name;
            this.unit = unit;
            this.value = value;
        }
    }

    static final class CurveRow {
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
