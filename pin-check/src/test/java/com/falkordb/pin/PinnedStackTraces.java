package com.falkordb.pin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the stacks the JDK prints under {@code -Djdk.tracePinnedThreads=full} (JDK 21–23), which mark
 * each frame that holds a monitor with {@code <== monitors:N}. Unlike a {@code jdk.VirtualThreadPinned}
 * JFR stack, this says <em>who</em> holds the monitor that pins the carrier, so a pin can be attributed
 * to our code or to a dependency without guessing from frame names.
 *
 * <p>The JDK prints each distinct pinned stack once per JVM, so the trace lists the distinct stacks, not
 * one per pin. Lines that are not part of a trace (other output on {@code System.out}) are ignored.
 */
final class PinnedStackTraces {

    // VirtualThread[#918]/runnable@ForkJoinPool-1-worker-2 reason:MONITOR
    // VirtualThread[#21,worker]/runnable@ForkJoinPool-1-worker-1 reason:NATIVE
    private static final Pattern HEADER = Pattern.compile("^VirtualThread\\[#\\d+[,\\]].*\\breason:(\\w+)\\s*$");
    //     org.apache.commons.pool2.impl.GenericObjectPool.returnObject(GenericObjectPool.java:1085) <== monitors:1
    //     java.base/java.lang.VirtualThread.park(VirtualThread.java:596)
    //     app//com.example.Foo.bar(Foo.java:10), lib@1.0/com.example.Baz.qux(Native Method)
    // The optional prefix is the class loader and/or module that StackTraceElement prints.
    private static final Pattern FRAME =
            Pattern.compile("^\\s+(?:\\S*?/)?([\\w.$]+)\\.([\\w$<>]+)\\(.*\\)(?:\\s+<== monitors:(\\d+))?\\s*$");

    private PinnedStackTraces() {}

    /** One frame of a traced stack: its class, method, and how many monitors it holds. */
    record Frame(String className, String method, int monitors) {
        String qualifiedName() {
            return className + "." + method;
        }
    }

    /** One traced pinned stack, top frame first. */
    record Stack(String reason, List<Frame> frames) {

        /** The frames that hold at least one monitor, top first. */
        List<Frame> monitorHolders() {
            List<Frame> holders = new ArrayList<>();
            for (Frame frame : frames) {
                if (frame.monitors() > 0) {
                    holders.add(frame);
                }
            }
            return holders;
        }

        /** Whether a frame in {@code packagePrefix} holds a monitor, i.e. that code pins the carrier. */
        boolean monitorHeldIn(String packagePrefix) {
            for (Frame frame : monitorHolders()) {
                if (frame.className().startsWith(packagePrefix)) {
                    return true;
                }
            }
            return false;
        }

        /** Whether any frame of the stack is in {@code packagePrefix}. */
        boolean runsThrough(String packagePrefix) {
            for (Frame frame : frames) {
                if (frame.className().startsWith(packagePrefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Parses every traced stack in {@code output}, in the order printed. */
    static List<Stack> parse(String output) {
        List<Stack> stacks = new ArrayList<>();
        String reason = null;
        List<Frame> frames = null;
        for (String line : output.split("\\R")) {
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                if (frames != null) {
                    stacks.add(new Stack(reason, Collections.unmodifiableList(frames)));
                }
                reason = header.group(1);
                frames = new ArrayList<>();
                continue;
            }
            // Other output can share System.out with the trace. Skip it rather than end the stack on it,
            // so a stray line can't cut off the frames below it, including a monitor holder.
            Matcher frame = frames == null ? null : FRAME.matcher(line);
            if (frame != null && frame.matches()) {
                int monitors = frame.group(3) == null ? 0 : Integer.parseInt(frame.group(3));
                frames.add(new Frame(frame.group(1), frame.group(2), monitors));
            }
        }
        if (frames != null) {
            stacks.add(new Stack(reason, Collections.unmodifiableList(frames)));
        }
        return stacks;
    }
}
