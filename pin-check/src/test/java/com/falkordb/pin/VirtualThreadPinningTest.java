package com.falkordb.pin;

import static org.junit.jupiter.api.Assertions.fail;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Wave-5 (Project Loom, #333) guard: proves the blocking client does not pin carrier threads on its
 * hot path when driven from virtual threads on JDK 21+.
 *
 * <p><strong>Strategy — "warm-pool zero-pins".</strong> Cold connection creation in commons-pool2
 * ({@code GenericObjectPool.create()} is {@code synchronized}) is a KNOWN upstream carrier pin, so we
 * first warm the pool on <em>platform</em> threads (borrow-and-hold N, then release, leaving N idle),
 * then run a virtual-thread query workload over the now-warm pool under a JFR recording. On a warm
 * pool there is no cold {@code create()}, and our query path plus Jedis I/O are monitor-free, so a
 * {@code jdk.VirtualThreadPinned} event whose stack runs through our client internals
 * ({@code com.falkordb.impl}) — and is not the upstream pool {@code create()}/{@code makeObject} pin —
 * means our code regressed (e.g. a {@code synchronized} reintroduced across a blocking call). The
 * upstream cold-creation pin is documented and mitigated (pool warm-up) rather than asserted here; pins
 * that do not involve our code (pure JDK/driver frames, or a pool {@code create()}) are reported but
 * not failed.
 *
 * <p>The check is future-proof across the JEP 491 (JDK 24) monitor change: after JDK 24
 * {@code synchronized} no longer pins, so {@code jdk.VirtualThreadPinned} only fires for native/VM
 * frames, which our pure-Java code cannot produce — the zero-pins expectation still holds.
 *
 * <p>Uses a pinned Testcontainers FalkorDB by default, or an external one via {@code FALKORDB_HOST} /
 * {@code FALKORDB_PORT}. Run via {@code just pin-check}; wired as the scheduled {@code pin-check} CI job.
 */
class VirtualThreadPinningTest {

    // Pinned digest (v4.20.1) — matches the smoke / benchmark harness images.
    private static final DockerImageName IMAGE = DockerImageName.parse(
            "falkordb/falkordb@sha256:9042fdc4e53f5390ca5a3993aa71506523970efb40ffb9a98e6a4b1a9a4f8862");

    private static final String CLIENT_PACKAGE = "com.falkordb.impl."; // the client internals that do blocking I/O
    private static final String POOL_PACKAGE = "org.apache.commons.pool2.";
    private static final String GRAPH = "pin-check";
    // Warmed connections == measured virtual-thread concurrency. Kept modest and well under FalkorDB's
    // MAX_QUEUED_QUERIES (default 25) so a burst never exceeds the server's pending-query limit — the
    // blocking pool caps in-flight server queries at POOL, and pin detection (a JFR event per pinned
    // carrier) does not need high concurrency to fire.
    private static final int POOL = 16;
    private static final int WARMUP_QUERIES = 64; // load/init classes so one-time class-init pins aren't measured
    private static final int MEASURED_QUERIES = 1024; // virtual-thread queries over the warm pool
    private static final Duration WORKLOAD_TIMEOUT = Duration.ofSeconds(120);

    @Test
    void hotPathDoesNotPinVirtualThreads() throws Exception {
        GenericContainer<?> container = null;
        String host;
        int port;
        String envHost = System.getenv("FALKORDB_HOST");
        String envPort = System.getenv("FALKORDB_PORT");
        if (envHost != null && !envHost.isEmpty() && envPort != null && !envPort.isEmpty()) {
            host = envHost;
            port = Integer.parseInt(envPort.trim());
        } else {
            container = new GenericContainer<>(IMAGE).withExposedPorts(6379).waitingFor(Wait.forListeningPort());
            container.start();
            host = container.getHost();
            port = container.getMappedPort(6379);
        }

        try (Driver driver = FalkorDB.builder()
                .host(host)
                .port(port)
                .poolMaxTotal(POOL)
                .poolMaxIdle(POOL)
                .poolMaxWait(Duration.ofSeconds(30))
                .connectionTimeout(Duration.ofSeconds(10))
                .socketTimeout(Duration.ofSeconds(30))
                .build()) {
            GraphContextGenerator graph = driver.graph(GRAPH);
            try {
                graph.query("RETURN 1"); // ensure the graph key exists

                warmPool(driver, POOL); // create N connections on PLATFORM threads (cold create off the vthread path)
                runVirtualThreadQueries(graph, WARMUP_QUERIES); // warm the code path so class-init pins aren't measured

                List<RecordedEvent> pins = recordPinning(() -> runVirtualThreadQueries(graph, MEASURED_QUERIES));

                List<RecordedEvent> ours = new ArrayList<>();
                for (RecordedEvent pin : pins) {
                    if (isClientRegression(pin)) {
                        ours.add(pin);
                    }
                }
                if (!ours.isEmpty()) {
                    fail(describe(ours, pins));
                }
                System.out.printf(
                        "pin-check OK: %d jdk.VirtualThreadPinned event(s) total, 0 attributable to %s over %d virtual-thread queries%n",
                        pins.size(), CLIENT_PACKAGE, MEASURED_QUERIES);
            } finally {
                graph.deleteGraph();
            }
        } finally {
            if (container != null) {
                container.stop();
            }
        }
    }

    /**
     * Borrows {@code n} connections simultaneously on platform threads — forcing the cold, pinning
     * commons-pool2 {@code create()} to happen here, off the virtual-thread path — then releases them
     * so the pool holds {@code n} idle connections for the measured phase.
     */
    private static void warmPool(Driver driver, int n) throws InterruptedException {
        GraphContextGenerator graph = driver.graph(GRAPH);
        ExecutorService platform = Executors.newFixedThreadPool(n);
        CountDownLatch borrowed = new CountDownLatch(n);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int i = 0; i < n; i++) {
                platform.execute(() -> {
                    try (GraphContext ctx = graph.getContext()) {
                        ctx.query("RETURN 1"); // materialize the connection
                        borrowed.countDown();
                        release.await(); // hold it so all N are open at once => N distinct connections created
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            if (!borrowed.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool warm-up did not borrow " + n + " connections in time");
            }
        } finally {
            release.countDown();
            platform.shutdown();
            if (!platform.awaitTermination(30, TimeUnit.SECONDS)) {
                platform.shutdownNow();
            }
        }
    }

    /** Runs {@code count} {@code RETURN 1} queries, one per virtual thread, over the shared warm pool. */
    private static void runVirtualThreadQueries(GraphContextGenerator graph, int count) {
        // NOTE: do not use try-with-resources here — ExecutorService.close() calls shutdown() (which does
        // NOT interrupt running tasks) then awaits termination for ~1 day, so a straggler stuck in a
        // blocking read would outlive the get(...) timeout and hang the run. shutdownNow() in the finally
        // interrupts stragglers instead (a virtual thread's socket read unblocks on interrupt), so the
        // timeout is a real bound.
        ExecutorService vthreads = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<?>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                futures.add(CompletableFuture.runAsync(() -> graph.query("RETURN 1"), vthreads));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(WORKLOAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("virtual-thread query workload failed", e);
        } finally {
            vthreads.shutdownNow();
            try {
                vthreads.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Records {@code jdk.VirtualThreadPinned} events (any duration) emitted while {@code workload} runs. */
    private static List<RecordedEvent> recordPinning(Runnable workload) throws Exception {
        Path jfr = Files.createTempFile("jfalkordb-pin-check", ".jfr");
        List<RecordedEvent> pins = new ArrayList<>();
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ofMillis(0)).withStackTrace();
            recording.start();
            workload.run();
            recording.stop();
            recording.dump(jfr);
            try (RecordingFile file = new RecordingFile(jfr)) {
                while (file.hasMoreEvents()) {
                    RecordedEvent event = file.readEvent();
                    if ("jdk.VirtualThreadPinned".equals(event.getEventType().getName())) {
                        pins.add(event);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(jfr);
        }
        return pins;
    }

    /**
     * A pin is a JFalkorDB regression iff its stack runs through our client internals
     * ({@link #CLIENT_PACKAGE}) but is <em>not</em> the known upstream commons-pool2 cold-creation pin
     * ({@code GenericObjectPool.create}/{@code makeObject}). Warming the pool normally prevents cold
     * creation during measurement, but a mid-workload connection drop could still trigger one whose
     * stack passes through our {@code getConnection}; excluding the pool {@code create}/{@code makeObject}
     * frame keeps that upstream pin from being misattributed to us while still failing on a genuine
     * {@code synchronized}-across-blocking regression in our code.
     */
    private static boolean isClientRegression(RecordedEvent pin) {
        RecordedStackTrace stack = pin.getStackTrace();
        if (stack == null) {
            return false;
        }
        boolean throughClient = false;
        boolean upstreamPoolCreate = false;
        for (RecordedFrame frame : stack.getFrames()) {
            RecordedMethod method = frame.getMethod();
            if (method == null || method.getType() == null) {
                continue;
            }
            String className = method.getType().getName();
            if (className.startsWith(CLIENT_PACKAGE)) {
                throughClient = true;
            }
            if (className.startsWith(POOL_PACKAGE)
                    && ("create".equals(method.getName()) || "makeObject".equals(method.getName()))) {
                upstreamPoolCreate = true;
            }
        }
        return throughClient && !upstreamPoolCreate;
    }

    private static String describe(List<RecordedEvent> ours, List<RecordedEvent> all) {
        StringBuilder message = new StringBuilder()
                .append("Virtual-thread pinning detected on the JFalkorDB hot path: ")
                .append(ours.size())
                .append(" of ")
                .append(all.size())
                .append(" jdk.VirtualThreadPinned event(s) run through ")
                .append(CLIENT_PACKAGE)
                .append(" (the pool was warmed, so this indicates our code held a monitor across a blocking call):\n");
        for (RecordedEvent pin : ours) {
            message.append("  pinned for ").append(pin.getDuration().toMillis()).append(" ms:\n");
            RecordedStackTrace stack = pin.getStackTrace();
            if (stack != null) {
                for (RecordedFrame frame : stack.getFrames()) {
                    RecordedMethod method = frame.getMethod();
                    if (method != null && method.getType() != null) {
                        message.append("      at ")
                                .append(method.getType().getName())
                                .append('.')
                                .append(method.getName())
                                .append(" (line ")
                                .append(frame.getLineNumber())
                                .append(")\n");
                    }
                }
            }
        }
        return message.toString();
    }
}
