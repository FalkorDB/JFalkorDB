package com.falkordb.pin;

import static org.junit.jupiter.api.Assertions.fail;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Wave-5 (Project Loom, #333) guard: proves the blocking client does not pin carrier threads on its
 * hot path when driven from virtual threads on JDK 21+.
 *
 * <p><strong>Strategy — attribute every pin to the code that holds the monitor.</strong> We warm the
 * pool on <em>platform</em> threads (borrow-and-hold N, then release, leaving N idle), so the
 * {@code synchronized} cold {@code GenericObjectPool.create()} of commons-pool2 runs off the measured
 * path, then run a virtual-thread query workload over the warm pool under a JFR recording. JFR counts
 * the {@code jdk.VirtualThreadPinned} events, but its stacks don't say which frame holds the monitor
 * that pins the carrier. So the JVM also runs with {@code -Djdk.tracePinnedThreads=full} (set in
 * {@code pom.xml}), which prints each distinct pinned stack with its monitor holders marked, and
 * {@link PinnedStackTraces} parses that. The check fails when a {@code com.falkordb.impl} frame holds a
 * monitor on a pinned stack, i.e. our code regressed (e.g. a {@code synchronized} reintroduced across a
 * blocking call). Pins whose monitor a dependency holds are reported but not failed. The known one is
 * commons-pool2 2.13+ (Jedis 8), whose {@code GenericObjectPool.returnObject} holds {@code synchronized
 * (p)} (POOL-419) while it waits for the idle deque's lock, so every contended connection return pins.
 *
 * <p>The trace is the only attribution, so each JFR pin on our stacks must match a traced stack (compared
 * on their non-JDK frames); a pin that matches none fails the check rather than passing unattributed.
 * {@code jdk.tracePinnedThreads} exists on JDK 21–23 only; from JDK 24 (JEP 491) {@code synchronized} no
 * longer pins, and a remaining pin (a native frame) through our code
 * fails here too, as our pure-Java code shouldn't produce one.
 *
 * <p>Uses a pinned Testcontainers FalkorDB by default, or an external one via {@code FALKORDB_HOST} /
 * {@code FALKORDB_PORT}. Run via {@code just pin-check}; wired as the scheduled {@code pin-check} CI job.
 */
class VirtualThreadPinningTest {

    // Pinned digest (v4.20.1) — matches the smoke / benchmark harness images.
    private static final DockerImageName IMAGE = DockerImageName.parse(
            "falkordb/falkordb@sha256:9042fdc4e53f5390ca5a3993aa71506523970efb40ffb9a98e6a4b1a9a4f8862");

    private static final String CLIENT_PACKAGE = "com.falkordb.impl."; // the client internals that do blocking I/O
    // Unique per run so pointing at an external/shared server never deletes a pre-existing graph.
    private static final String GRAPH = "pin-check-" + UUID.randomUUID();
    // Warmed connections == measured virtual-thread concurrency. Kept modest and well under FalkorDB's
    // MAX_QUEUED_QUERIES (default 25) so a burst never exceeds the server's pending-query limit — the
    // blocking pool caps in-flight server queries at POOL, and pin detection (a JFR event per pinned
    // carrier) does not need high concurrency to fire.
    private static final int POOL = 16;
    // Loads/inits classes so one-time class-init pins aren't counted. The pinned-thread trace still covers
    // the warm-up, since the JVM prints each distinct stack only once: a monitor our code holds is a
    // regression in either phase.
    private static final int WARMUP_QUERIES = 64;
    private static final int MEASURED_QUERIES = 1024; // virtual-thread queries over the warm pool
    private static final Duration WORKLOAD_TIMEOUT = Duration.ofSeconds(120);

    /**
     * Runs the virtual-thread workload over a warm pool and fails if a pinned stack has a monitor held by
     * {@link #CLIENT_PACKAGE}, or if a pin through it can't be attributed (see the class Javadoc).
     */
    @Test
    @ResourceLock(Resources.SYSTEM_OUT) // replaces System.out to capture the pinned-thread trace
    void hotPathDoesNotPinVirtualThreads() throws Exception {
        GenericContainer<?> container = null;
        String host;
        int port;
        String envHost = System.getenv("FALKORDB_HOST");
        String envPort = System.getenv("FALKORDB_PORT");
        boolean hasHost = envHost != null && !envHost.isEmpty();
        boolean hasPort = envPort != null && !envPort.isEmpty();
        if (hasHost != hasPort) {
            throw new IllegalStateException(
                    "Set both FALKORDB_HOST and FALKORDB_PORT to use an external server, or neither to use Testcontainers");
        }
        if (hasHost) {
            host = envHost;
            port = parsePort(envPort);
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

                List<RecordedEvent> pins;
                // The JVM prints each distinct pinned stack once, so capture from before the first
                // virtual thread: a stack first printed during warm-up is not printed again. It must be a
                // plain PrintStream: the JDK prints the trace only when System.out's lock is its internal
                // lock, which Surefire's own PrintStream subclass doesn't use.
                ByteArrayOutputStream trace = new ByteArrayOutputStream();
                PrintStream stdout = System.out;
                System.setOut(new PrintStream(new TeeOutputStream(stdout, trace), true, StandardCharsets.UTF_8));
                try {
                    // Create N connections on PLATFORM threads (cold create off the vthread path), then warm
                    // the code path so class-init pins aren't counted.
                    warmPool(driver, POOL);
                    runVirtualThreadQueries(graph, WARMUP_QUERIES);
                    pins = recordPinning(() -> runVirtualThreadQueries(graph, MEASURED_QUERIES));
                } finally {
                    System.out.flush();
                    System.setOut(stdout);
                }
                List<PinnedStackTraces.Stack> traced = PinnedStackTraces.parse(trace.toString(StandardCharsets.UTF_8));

                List<RecordedEvent> throughClient = new ArrayList<>();
                for (RecordedEvent pin : pins) {
                    if (runsThroughClient(pin)) {
                        throughClient.add(pin);
                    }
                }
                List<PinnedStackTraces.Stack> ours = new ArrayList<>();
                for (PinnedStackTraces.Stack stack : traced) {
                    if (stack.monitorHeldIn(CLIENT_PACKAGE)) {
                        ours.add(stack);
                    }
                }
                if (!ours.isEmpty()) {
                    fail(describeOurs(ours, pins.size()));
                }
                // Fail safe: a pin we can't attribute fails the check rather than passing. Each pin JFR saw
                // through our code must match a traced stack (the JVM can skip a print under contention),
                // and every monitor stack must name its monitor holder (else the trace format has drifted
                // from what the parser reads).
                List<RecordedEvent> unattributed = new ArrayList<>();
                for (RecordedEvent pin : throughClient) {
                    List<String> appFrames = PinnedStackTraces.appFrames(frameNames(pin));
                    boolean truncated = pin.getStackTrace().isTruncated();
                    if (traced.stream().noneMatch(stack -> PinnedStackTraces.matches(appFrames, truncated, stack))) {
                        unattributed.add(pin);
                    }
                }
                if (!unattributed.isEmpty()) {
                    fail(describeUnattributed(unattributed, throughClient.size(), pins.size()));
                }
                for (PinnedStackTraces.Stack stack : traced) {
                    if ("MONITOR".equals(stack.reason())
                            && stack.monitorHolders().isEmpty()) {
                        fail("A pinned-thread trace with reason MONITOR marks no frame as holding a monitor, so the"
                                + " pin can't be attributed; has the trace format changed? Its frames: "
                                + stack.frames());
                    }
                }
                System.out.printf(
                        "pin-check OK: %d jdk.VirtualThreadPinned event(s) over %d virtual-thread queries, %d on stacks"
                                + " through %s, none with a monitor held there. Monitors held on the %d distinct traced"
                                + " stack(s): %s (commons-pool2 %s)%n",
                        pins.size(),
                        MEASURED_QUERIES,
                        throughClient.size(),
                        CLIENT_PACKAGE,
                        traced.size(),
                        monitorHolders(traced),
                        GenericObjectPool.class.getPackage().getImplementationVersion());
            } finally {
                graph.deleteGraph();
            }
        } finally {
            if (container != null) {
                container.stop();
            }
        }
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("FALKORDB_PORT must be between 1 and 65535, but was: " + value);
            }
            return port;
        } catch (NumberFormatException notANumber) {
            throw new IllegalStateException("FALKORDB_PORT must be a number, but was: " + value, notANumber);
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int i = 0; i < n; i++) {
                platform.execute(() -> {
                    try (GraphContext ctx = graph.getContext()) {
                        ctx.query("RETURN 1"); // materialize the connection
                        borrowed.countDown();
                        release.await(); // hold it so all N are open at once => N distinct connections created
                    } catch (InterruptedException interrupted) {
                        // Only reachable at release.await() — i.e. after this worker already created its
                        // connection and counted the latch down (borrowed.await guarantees N distinct
                        // connections were held simultaneously before we proceed). The connection is
                        // returned to the pool, which retains it (poolMaxIdle == N, evictor disabled), so
                        // the pool stays warm; hence the interrupt is not a warm-up failure.
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException workerFailure) {
                        // execute() would otherwise only route this to the thread's uncaught handler,
                        // turning a real warm-up failure into an opaque 60s timeout — capture it and
                        // release the latch so we fail fast with the actual cause.
                        failure.compareAndSet(null, workerFailure);
                        borrowed.countDown();
                    }
                });
            }
            if (!borrowed.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "pool warm-up did not borrow " + n + " connections in time", failure.get());
            }
            Throwable warmUpFailure = failure.get();
            if (warmUpFailure != null) {
                throw new IllegalStateException("pool warm-up failed to borrow " + n + " connections", warmUpFailure);
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

    /** Whether a JFR pin's stack runs through our client internals ({@link #CLIENT_PACKAGE}). */
    private static boolean runsThroughClient(RecordedEvent pin) {
        RecordedStackTrace stack = pin.getStackTrace();
        if (stack == null) {
            return false;
        }
        for (RecordedFrame frame : stack.getFrames()) {
            RecordedMethod method = frame.getMethod();
            if (method != null
                    && method.getType() != null
                    && method.getType().getName().startsWith(CLIENT_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    /** A JFR pin's frames as {@code class.method}, top first. */
    private static List<String> frameNames(RecordedEvent pin) {
        List<String> names = new ArrayList<>();
        for (RecordedFrame frame : pin.getStackTrace().getFrames()) {
            RecordedMethod method = frame.getMethod();
            if (method != null && method.getType() != null) {
                names.add(method.getType().getName() + "." + method.getName());
            }
        }
        return names;
    }

    /** The distinct frames that hold a monitor on any traced stack, e.g. {@code [GenericObjectPool.returnObject]}. */
    private static Set<String> monitorHolders(List<PinnedStackTraces.Stack> traced) {
        Set<String> holders = new TreeSet<>();
        for (PinnedStackTraces.Stack stack : traced) {
            for (PinnedStackTraces.Frame frame : stack.monitorHolders()) {
                holders.add(frame.qualifiedName());
            }
        }
        return holders;
    }

    /** The failure message for pins whose monitor our code holds, listing each stack with its holders marked. */
    private static String describeOurs(List<PinnedStackTraces.Stack> ours, int pins) {
        StringBuilder message = new StringBuilder()
                .append("Virtual-thread pinning caused by JFalkorDB: ")
                .append(ours.size())
                .append(" distinct pinned stack(s) traced during the run (warm-up included; JFR counted ")
                .append(pins)
                .append(" jdk.VirtualThreadPinned event(s) in the measured phase) have a ")
                .append(CLIENT_PACKAGE)
                .append(" frame holding a monitor across a blocking call (marked <== monitors):\n");
        for (PinnedStackTraces.Stack stack : ours) {
            message.append("  reason ").append(stack.reason()).append(":\n");
            for (PinnedStackTraces.Frame frame : stack.frames()) {
                message.append("      at ").append(frame.qualifiedName());
                if (frame.monitors() > 0) {
                    message.append(" <== monitors:").append(frame.monitors());
                }
                message.append('\n');
            }
        }
        return message.toString();
    }

    /** The failure message for pins through our code that match no traced stack, with their JFR stacks. */
    private static String describeUnattributed(List<RecordedEvent> unattributed, int throughClient, int pins) {
        StringBuilder message = new StringBuilder()
                .append(unattributed.size())
                .append(" of the ")
                .append(throughClient)
                .append(" jdk.VirtualThreadPinned event(s) through ")
                .append(CLIENT_PACKAGE)
                .append(" (")
                .append(pins)
                .append(" in all) match no pinned-thread trace, so nothing says which code holds the monitor.")
                .append(" Run on JDK 21-23 with -Djdk.tracePinnedThreads=full (jdk.tracePinnedThreads is ")
                .append(System.getProperty("jdk.tracePinnedThreads"))
                .append(", Java ")
                .append(Runtime.version())
                .append("). The unmatched JFR stacks:\n");
        appendJfrStacks(message, unattributed);
        return message.toString();
    }

    /** Appends each JFR pin's duration and stack, top frame first, to {@code message}. */
    private static void appendJfrStacks(StringBuilder message, List<RecordedEvent> pins) {
        for (RecordedEvent pin : pins) {
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
    }

    /**
     * Writes to two streams, so the pinned-thread trace is captured and still shown in the build output.
     * Unsynchronized: the {@link PrintStream} wrapping it already serializes writes.
     */
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            first.write(b, off, len);
            second.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
