package com.falkordb.pin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PinnedStackTraces}: which code a traced pin is attributed to. Needs no server or
 * Docker. The first two fixtures are verbatim from a JDK 21 {@code just pin-check} run with Jedis 8.0.2
 * and commons-pool2 2.13.1; the rest edit them to model the pins the check must still catch.
 */
class PinnedStackTracesTest {

    private static final String CLIENT = "com.falkordb.impl.";
    private static final String POOL = "org.apache.commons.pool2.";

    // A contended connection return: commons-pool2 2.13's returnObject holds synchronized (p) while
    // addFirst waits for the idle deque's lock (POOL-419).
    private static final String RETURN_ADD_FIRST = """
            VirtualThread[#918]/runnable@ForkJoinPool-1-worker-2 reason:MONITOR
                java.base/java.lang.VirtualThread$VThreadContinuation.onPinned(VirtualThread.java:199)
                java.base/jdk.internal.vm.Continuation.onPinned0(Continuation.java:393)
                java.base/java.lang.VirtualThread.park(VirtualThread.java:596)
                java.base/java.lang.System$2.parkVirtualThread(System.java:2643)
                java.base/jdk.internal.misc.VirtualThreads.park(VirtualThreads.java:54)
                java.base/java.util.concurrent.locks.LockSupport.park(LockSupport.java:219)
                java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:754)
                java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:990)
                java.base/java.util.concurrent.locks.ReentrantLock$Sync.lock(ReentrantLock.java:153)
                java.base/java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:322)
                org.apache.commons.pool2.impl.LinkedBlockingDeque.offerFirst(LinkedBlockingDeque.java:732)
                org.apache.commons.pool2.impl.LinkedBlockingDeque.addFirst(LinkedBlockingDeque.java:409)
                org.apache.commons.pool2.impl.GenericObjectPool.returnObject(GenericObjectPool.java:1085) <== monitors:1
                redis.clients.jedis.util.Pool.returnResource(Pool.java:51)
                redis.clients.jedis.JedisPool.returnResource(JedisPool.java:409)
                redis.clients.jedis.JedisPool.returnResource(JedisPool.java:24)
                redis.clients.jedis.Jedis.close(Jedis.java:398)
                com.falkordb.impl.api.GraphContextImpl.close(GraphContextImpl.java:394)
                com.falkordb.impl.api.GraphImpl.lambda$sendQuery$0(GraphImpl.java:42)
                com.falkordb.impl.api.DriverImpl.routed(DriverImpl.java:866)
                com.falkordb.impl.api.GraphImpl.sendQuery(GraphImpl.java:39)
                com.falkordb.impl.api.AbstractGraph.query(AbstractGraph.java:70)
                com.falkordb.pin.VirtualThreadPinningTest.lambda$runVirtualThreadQueries$2(VirtualThreadPinningTest.java:211)
                java.base/java.util.concurrent.CompletableFuture$AsyncRun.run(CompletableFuture.java:1804)
                java.base/java.util.concurrent.ThreadPerTaskExecutor$TaskRunner.run(ThreadPerTaskExecutor.java:314)
                java.base/java.lang.VirtualThread.run(VirtualThread.java:329)
            """;

    // The same return, waiting for the deque's lock in the maxIdle check instead.
    private static final String RETURN_SIZE = """
            VirtualThread[#850]/runnable@ForkJoinPool-1-worker-2 reason:MONITOR
                java.base/java.lang.VirtualThread$VThreadContinuation.onPinned(VirtualThread.java:199)
                java.base/jdk.internal.vm.Continuation.onPinned0(Continuation.java:393)
                java.base/java.lang.VirtualThread.park(VirtualThread.java:596)
                java.base/java.lang.System$2.parkVirtualThread(System.java:2643)
                java.base/jdk.internal.misc.VirtualThreads.park(VirtualThreads.java:54)
                java.base/java.util.concurrent.locks.LockSupport.park(LockSupport.java:219)
                java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:754)
                java.base/java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:990)
                java.base/java.util.concurrent.locks.ReentrantLock$Sync.lock(ReentrantLock.java:153)
                java.base/java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:322)
                org.apache.commons.pool2.impl.LinkedBlockingDeque.size(LinkedBlockingDeque.java:1282)
                org.apache.commons.pool2.impl.GenericObjectPool.returnObject(GenericObjectPool.java:1072) <== monitors:1
                redis.clients.jedis.util.Pool.returnResource(Pool.java:51)
                redis.clients.jedis.JedisPool.returnResource(JedisPool.java:409)
                redis.clients.jedis.JedisPool.returnResource(JedisPool.java:24)
                redis.clients.jedis.Jedis.close(Jedis.java:398)
                com.falkordb.impl.api.GraphContextImpl.close(GraphContextImpl.java:394)
                com.falkordb.impl.api.GraphImpl.lambda$sendQuery$0(GraphImpl.java:42)
                com.falkordb.impl.api.DriverImpl.routed(DriverImpl.java:866)
                com.falkordb.impl.api.GraphImpl.sendQuery(GraphImpl.java:39)
                com.falkordb.impl.api.AbstractGraph.query(AbstractGraph.java:70)
                com.falkordb.pin.VirtualThreadPinningTest.lambda$runVirtualThreadQueries$2(VirtualThreadPinningTest.java:211)
                java.base/java.util.concurrent.CompletableFuture$AsyncRun.run(CompletableFuture.java:1804)
                java.base/java.util.concurrent.ThreadPerTaskExecutor$TaskRunner.run(ThreadPerTaskExecutor.java:314)
                java.base/java.lang.VirtualThread.run(VirtualThread.java:329)
            """;

    // A query whose socket read runs under a monitor our code holds: the regression the check exists for.
    private static final String CLIENT_MONITOR_ACROSS_READ = """
            VirtualThread[#41]/runnable@ForkJoinPool-1-worker-3 reason:MONITOR
                java.base/java.lang.VirtualThread$VThreadContinuation.onPinned(VirtualThread.java:199)
                java.base/jdk.internal.vm.Continuation.onPinned0(Continuation.java:393)
                java.base/java.lang.VirtualThread.park(VirtualThread.java:596)
                java.base/sun.nio.ch.Poller.pollIndirect(Poller.java:139)
                java.base/sun.nio.ch.NioSocketImpl.park(NioSocketImpl.java:191)
                java.base/sun.nio.ch.NioSocketImpl.implRead(NioSocketImpl.java:309)
                java.base/java.net.Socket$SocketInputStream.read(Socket.java:1099)
                redis.clients.jedis.util.RedisInputStream.ensureFill(RedisInputStream.java:248)
                redis.clients.jedis.Protocol.read(Protocol.java:238)
                redis.clients.jedis.Connection.getOne(Connection.java:400)
                com.falkordb.impl.api.GraphContextImpl.query(GraphContextImpl.java:120) <== monitors:1
                com.falkordb.impl.api.GraphImpl.lambda$sendQuery$0(GraphImpl.java:42)
                java.base/java.lang.VirtualThread.run(VirtualThread.java:329)
            """;

    /** Both observed return variants (waiting in {@code addFirst} or in {@code size}) are commons-pool2's pin. */
    @Test
    void contendedReturnIsHeldByThePoolNotTheClient() {
        for (String trace : List.of(RETURN_ADD_FIRST, RETURN_SIZE)) {
            List<PinnedStackTraces.Stack> stacks = PinnedStackTraces.parse(trace);
            assertEquals(1, stacks.size());
            PinnedStackTraces.Stack stack = stacks.get(0);
            assertEquals("MONITOR", stack.reason());
            assertEquals(
                    List.of("org.apache.commons.pool2.impl.GenericObjectPool.returnObject"),
                    stack.monitorHolders().stream()
                            .map(PinnedStackTraces.Frame::qualifiedName)
                            .toList());
            assertTrue(stack.runsThrough(CLIENT), "the stack does pass through our close()");
            assertFalse(stack.monitorHeldIn(CLIENT), "but our code holds no monitor on it");
            assertTrue(stack.monitorHeldIn(POOL));
        }
    }

    @Test
    void parsesEveryFrameWithModulePrefixesStripped() {
        PinnedStackTraces.Stack stack = PinnedStackTraces.parse(RETURN_SIZE).get(0);
        assertEquals(25, stack.frames().size());
        assertEquals(
                new PinnedStackTraces.Frame("java.lang.VirtualThread$VThreadContinuation", "onPinned", 0),
                stack.frames().get(0));
        assertEquals(
                new PinnedStackTraces.Frame("com.falkordb.impl.api.GraphImpl", "lambda$sendQuery$0", 0),
                stack.frames().get(17));
    }

    @Test
    void clientMonitorAcrossSocketReadIsAttributedToTheClient() {
        PinnedStackTraces.Stack stack =
                PinnedStackTraces.parse(CLIENT_MONITOR_ACROSS_READ).get(0);
        assertTrue(stack.monitorHeldIn(CLIENT));
        assertFalse(stack.monitorHeldIn(POOL));
    }

    @Test
    void synchronizedCloseIsAttributedToTheClientEvenThoughThePoolBlocks() {
        // A frame-name heuristic ("blocked inside the pool's return") would blame commons-pool2 here; the
        // monitor marks show our close() holds a monitor too.
        String trace = RETURN_ADD_FIRST.replace(
                "GraphContextImpl.close(GraphContextImpl.java:394)",
                "GraphContextImpl.close(GraphContextImpl.java:394) <== monitors:1");
        PinnedStackTraces.Stack stack = PinnedStackTraces.parse(trace).get(0);
        assertTrue(stack.monitorHeldIn(CLIENT));
        assertEquals(2, stack.monitorHolders().size());
    }

    @Test
    void poolMonitorAcrossSocketIoIsNotAttributedToTheClient() {
        // commons-pool2 2.13 also destroys a returned connection (pool closed, or maxIdle reached) inside
        // synchronized (p); Jedis then does socket I/O under the pool's monitor. That is upstream too.
        String trace = CLIENT_MONITOR_ACROSS_READ
                .replace(" <== monitors:1", "")
                .replace("    com.falkordb.impl.api.GraphContextImpl.query(GraphContextImpl.java:120)\n", """
                            redis.clients.jedis.JedisFactory.destroyObject(JedisFactory.java:160)
                            org.apache.commons.pool2.impl.GenericObjectPool.destroy(GenericObjectPool.java:636)
                            org.apache.commons.pool2.impl.GenericObjectPool.returnObject(GenericObjectPool.java:1074) <== monitors:1
                            redis.clients.jedis.Jedis.close(Jedis.java:398)
                            com.falkordb.impl.api.GraphContextImpl.close(GraphContextImpl.java:394)
                        """);
        PinnedStackTraces.Stack stack = PinnedStackTraces.parse(trace).get(0);
        assertEquals(
                List.of("org.apache.commons.pool2.impl.GenericObjectPool.returnObject"),
                stack.monitorHolders().stream()
                        .map(PinnedStackTraces.Frame::qualifiedName)
                        .toList());
        assertFalse(stack.monitorHeldIn(CLIENT));
    }

    @Test
    void coldCreateIsNotAttributedToTheClient() {
        // Borrowing from a cold pool connects inside commons-pool2's create(), which holds a monitor.
        String trace = """
                VirtualThread[#33]/runnable@ForkJoinPool-1-worker-1 reason:MONITOR
                    java.base/java.lang.VirtualThread$VThreadContinuation.onPinned(VirtualThread.java:199)
                    java.base/jdk.internal.vm.Continuation.onPinned0(Continuation.java:393)
                    java.base/java.lang.VirtualThread.park(VirtualThread.java:596)
                    java.base/sun.nio.ch.NioSocketImpl.park(NioSocketImpl.java:191)
                    java.base/sun.nio.ch.NioSocketImpl.connect(NioSocketImpl.java:592)
                    java.base/java.net.Socket.connect(Socket.java:751)
                    redis.clients.jedis.DefaultJedisSocketFactory.connectToFirstSuccessfulHost(DefaultJedisSocketFactory.java:73)
                    redis.clients.jedis.DefaultJedisSocketFactory.createSocket(DefaultJedisSocketFactory.java:107)
                    redis.clients.jedis.Connection.connect(Connection.java:229)
                    redis.clients.jedis.JedisFactory.makeObject(JedisFactory.java:181)
                    org.apache.commons.pool2.impl.GenericObjectPool.create(GenericObjectPool.java:571) <== monitors:1
                    org.apache.commons.pool2.impl.GenericObjectPool.borrowObject(GenericObjectPool.java:298)
                    redis.clients.jedis.util.Pool.getResource(Pool.java:38)
                    redis.clients.jedis.JedisPool.getResource(JedisPool.java:378)
                    com.falkordb.impl.api.DriverImpl$Attempt.connection(DriverImpl.java:930)
                    com.falkordb.impl.api.GraphImpl.lambda$sendQuery$0(GraphImpl.java:41)
                    java.base/java.lang.VirtualThread.run(VirtualThread.java:329)
                """;
        PinnedStackTraces.Stack stack = PinnedStackTraces.parse(trace).get(0);
        assertEquals(17, stack.frames().size());
        assertTrue(stack.runsThrough(CLIENT));
        assertFalse(stack.monitorHeldIn(CLIENT));
        assertTrue(stack.monitorHeldIn(POOL));
    }

    @Test
    void parsesNamedThreadsLoaderAndModulePrefixesAndUnusualSources() {
        // StackTraceElement prints a class loader and/or module before the class, and a thread name in
        // the header when the virtual thread has one. A frame skipped over its prefix could hide a holder.
        String trace = """
                VirtualThread[#21,query-worker]/runnable@ForkJoinPool-1-worker-1 reason:NATIVE
                    java.base/java.lang.VirtualThread.park(VirtualThread.java:596)
                    lib@1.2.3/com.example.Native.call(Native Method)
                    app//com.falkordb.impl.api.Hidden.lambda$run$0(Unknown Source) <== monitors:2
                    loader/mod@1.0/com.falkordb.impl.api.Ctor.<init>(Ctor.java:12)
                """;
        List<PinnedStackTraces.Stack> stacks = PinnedStackTraces.parse(trace);
        assertEquals(1, stacks.size());
        PinnedStackTraces.Stack stack = stacks.get(0);
        assertEquals("NATIVE", stack.reason());
        assertEquals(
                List.of(
                        new PinnedStackTraces.Frame("java.lang.VirtualThread", "park", 0),
                        new PinnedStackTraces.Frame("com.example.Native", "call", 0),
                        new PinnedStackTraces.Frame("com.falkordb.impl.api.Hidden", "lambda$run$0", 2),
                        new PinnedStackTraces.Frame("com.falkordb.impl.api.Ctor", "<init>", 0)),
                stack.frames());
        assertTrue(stack.monitorHeldIn(CLIENT));
    }

    @Test
    void parsesSeveralStacksInterleavedWithOtherOutput() {
        String output = "pin-check warm-up done\n"
                + RETURN_ADD_FIRST
                + "SLF4J: Defaulting to no-operation (NOP) logger implementation\n"
                + CLIENT_MONITOR_ACROSS_READ
                + RETURN_SIZE.replace(
                        "    redis.clients.jedis.util.Pool.returnResource(Pool.java:51)\n",
                        "    redis.clients.jedis.util.Pool.returnResource(Pool.java:51)\nunrelated output\n");
        List<PinnedStackTraces.Stack> stacks = PinnedStackTraces.parse(output);
        assertEquals(3, stacks.size());
        assertFalse(stacks.get(0).monitorHeldIn(CLIENT));
        assertTrue(stacks.get(1).monitorHeldIn(CLIENT));
        // A stray line inside a stack doesn't cut off the frames below it.
        assertEquals(25, stacks.get(2).frames().size());
    }

    @Test
    void monitorStackWithoutMarksHasNoHolders() {
        // The caller fails the check on this: a MONITOR pin always has a holder, so a trace without one
        // means the format has drifted from what the parser reads.
        PinnedStackTraces.Stack stack = PinnedStackTraces.parse(RETURN_SIZE.replace(" <== monitors:1", ""))
                .get(0);
        assertEquals("MONITOR", stack.reason());
        assertTrue(stack.monitorHolders().isEmpty());
        assertFalse(stack.monitorHeldIn(POOL));
    }

    @Test
    void jfrPinMatchesTheTracedStackOnItsApplicationFrames() {
        // JFR starts the stack at different JDK frames and lists the hidden lambda classes the trace omits.
        List<String> jfrSize = List.of(
                "java.lang.VirtualThread.parkOnCarrierThread",
                "java.lang.VirtualThread.park",
                "java.util.concurrent.locks.ReentrantLock.lock",
                "org.apache.commons.pool2.impl.LinkedBlockingDeque.size",
                "org.apache.commons.pool2.impl.GenericObjectPool.returnObject",
                "redis.clients.jedis.util.Pool.returnResource",
                "redis.clients.jedis.JedisPool.returnResource",
                "redis.clients.jedis.JedisPool.returnResource",
                "redis.clients.jedis.Jedis.close",
                "com.falkordb.impl.api.GraphContextImpl.close",
                "com.falkordb.impl.api.GraphImpl.lambda$sendQuery$0",
                "com.falkordb.impl.api.GraphImpl$$Lambda.0x00007fd0d82cdd40.apply",
                "com.falkordb.impl.api.DriverImpl.routed",
                "com.falkordb.impl.api.GraphImpl.sendQuery",
                "com.falkordb.impl.api.AbstractGraph.query",
                "com.falkordb.pin.VirtualThreadPinningTest.lambda$runVirtualThreadQueries$2",
                "com.falkordb.pin.VirtualThreadPinningTest$$Lambda.0x00007fd0d8320000.run",
                "java.util.concurrent.CompletableFuture$AsyncRun.run",
                "java.lang.VirtualThread.run");
        List<String> app = PinnedStackTraces.appFrames(jfrSize);
        assertEquals("org.apache.commons.pool2.impl.LinkedBlockingDeque.size", app.get(0));
        assertFalse(app.stream().anyMatch(name -> name.contains("$$Lambda")));

        PinnedStackTraces.Stack sizeStack = PinnedStackTraces.parse(RETURN_SIZE).get(0);
        PinnedStackTraces.Stack addFirstStack =
                PinnedStackTraces.parse(RETURN_ADD_FIRST).get(0);
        assertTrue(PinnedStackTraces.matches(app, false, sizeStack));
        // The addFirst trace is a different stack, so it can't vouch for this pin.
        assertFalse(PinnedStackTraces.matches(app, false, addFirstStack));
        // Nor can a trace of some other client stack.
        assertFalse(PinnedStackTraces.matches(
                app, false, PinnedStackTraces.parse(CLIENT_MONITOR_ACROSS_READ).get(0)));
    }

    @Test
    void truncatedJfrPinMatchesATracedStackThatStartsWithItsFrames() {
        PinnedStackTraces.Stack stack = PinnedStackTraces.parse(RETURN_SIZE).get(0);
        List<String> top = stack.appFrames().subList(0, 5);
        assertTrue(PinnedStackTraces.matches(top, true, stack));
        assertFalse(PinnedStackTraces.matches(top, false, stack), "an untruncated stack must match exactly");
        List<String> otherTop =
                PinnedStackTraces.parse(RETURN_ADD_FIRST).get(0).appFrames().subList(0, 5);
        assertFalse(PinnedStackTraces.matches(otherTop, true, stack));
    }

    @Test
    void outputWithoutATraceHasNoStacks() {
        assertTrue(PinnedStackTraces.parse("").isEmpty());
        assertTrue(PinnedStackTraces.parse(
                        "pin-check OK\n    at com.falkordb.impl.api.GraphImpl.query(GraphImpl.java:1)\n")
                .isEmpty());
    }
}
