package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The full public API surface gate: {@code api/jfalkordb.api} must describe the API the code has,
 * including every member inherited from Jedis and the public members of the Jedis types our own
 * signatures return. See {@link ApiSurface} for what is captured and why japicmp can't do this.
 *
 * <p>A failure lists what changed. If the change is intended, run {@code just api-surface-update}
 * and commit the regenerated snapshot, so the change is reviewed in the pull request. A Jedis bump
 * that changes what our users can call fails here until that is done, which is the point.
 */
class ApiSurfaceTest {

    private static final int MAX_LINES_SHOWN = 150;

    @Test
    void snapshotDescribesTheApi() throws IOException {
        Path basedir = Paths.get(System.getProperty("basedir", "."));
        Path snapshot = basedir.resolve(ApiSurface.SNAPSHOT);
        String actual = ApiSurface.render();
        if (Boolean.getBoolean("api.surface.update")) {
            Files.createDirectories(snapshot.getParent());
            Files.write(snapshot, actual.getBytes(StandardCharsets.UTF_8));
            return;
        }
        Path generated = basedir.resolve("target/api-surface/jfalkordb.api");
        Files.createDirectories(generated.getParent());
        Files.write(generated, actual.getBytes(StandardCharsets.UTF_8));
        if (!Files.exists(snapshot)) {
            fail(ApiSurface.SNAPSHOT + " is missing; create it with `" + ApiSurface.UPDATE_COMMAND + "`.");
        }
        String expected = new String(Files.readAllBytes(snapshot), StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (expected.equals(actual)) {
            return;
        }
        List<String> changes = ApiSurface.diff(expected, actual);
        Path diff = generated.resolveSibling("diff.txt");
        Files.write(diff, String.join("\n", changes).concat("\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder message = new StringBuilder("The public API surface no longer matches ")
                .append(ApiSurface.SNAPSHOT)
                .append(" (- only in the snapshot, + only in the code, ~ deprecation changed):\n");
        changes.stream().limit(MAX_LINES_SHOWN).forEach(line -> message.append(line)
                .append('\n'));
        if (changes.size() > MAX_LINES_SHOWN) {
            message.append("... ")
                    .append(changes.size() - MAX_LINES_SHOWN)
                    .append(" more lines in ")
                    .append(diff)
                    .append('\n');
        }
        if (changes.isEmpty()) {
            message.append("(only comments, order or blank lines differ)\n");
        }
        message.append("If this change is intended, regenerate the snapshot with `")
                .append(ApiSurface.UPDATE_COMMAND)
                .append("` and commit ")
                .append(ApiSurface.SNAPSHOT)
                .append(" so the change is reviewed. A removed or changed line breaks callers.");
        fail(message.toString());
    }

    @Test
    void theSurfaceCoversInheritedJedisMembersAndReachableJedisTypes() throws IOException {
        String surface = ApiSurface.render();
        String pipeline = block(surface, GraphPipeline.class.getName());
        assertTrue(
                pipeline.contains(" jsonNumIncrBy(java.lang.String, redis.clients.jedis.json.Path2, "),
                "GraphPipeline lists the methods it inherits from Jedis:\n" + pipeline);
        assertTrue(
                surface.contains("\nredis.clients.jedis.Jedis\n"),
                "Driver.getConnection() returns Jedis, so its surface is listed");
        assertFalse(surface.contains("\ncom.falkordb.impl."), "com.falkordb.impl is internal");
    }

    // --- the rules, on small fixtures ---

    public interface Commands {
        Object incr(String key, double by);
    }

    public interface Pipeline extends Commands {
        Object query(String q);
    }

    @Test
    void listsInheritedMethods() {
        assertEquals(
                "com.falkordb.ApiSurfaceTest$Pipeline\n"
                        + "  type public interface com.falkordb.ApiSurfaceTest$Pipeline\n"
                        + "  extends com.falkordb.ApiSurfaceTest$Commands\n"
                        + "  supertype com.falkordb.ApiSurfaceTest$Commands\n"
                        + "  method public abstract java.lang.Object incr(java.lang.String, double)\n"
                        + "  method public abstract java.lang.Object query(java.lang.String)\n",
                ApiSurface.describe(Pipeline.class, false));
    }

    public interface Narrowed extends Commands {
        @Override
        String incr(String key, double by);
    }

    @Test
    void listsAnOverriddenMethodOnceWithTheMostSpecificSignature() {
        assertEquals(
                Arrays.asList("method public abstract java.lang.String incr(java.lang.String, double)"),
                members(ApiSurface.describe(Narrowed.class, false), "method "));
    }

    public interface Source<T> {
        T get();

        void set(T value);
    }

    public interface LongSource extends Source<Long> {
        @Override
        void set(Long value);
    }

    @Test
    void replacesTheTypeVariablesOfAGenericSupertype() {
        assertEquals(
                Arrays.asList(
                        "method public abstract java.lang.Long get() // erased: java.lang.Object get()",
                        "method public abstract void set(java.lang.Long)"),
                members(ApiSurface.describe(LongSource.class, false), "method "));
    }

    public interface WithFactory {
        static WithFactory create() {
            return null;
        }
    }

    public interface Strings extends WithFactory, Iterable<String> {
        @Deprecated
        default int count() {
            return 0;
        }
    }

    @Test
    void leavesOutStaticInterfaceMethodsAndJdkMembersButKeepsJdkSupertypes() {
        assertEquals(
                "com.falkordb.ApiSurfaceTest$Strings\n"
                        + "  type public interface com.falkordb.ApiSurfaceTest$Strings\n"
                        + "  extends com.falkordb.ApiSurfaceTest$WithFactory\n"
                        + "  extends java.lang.Iterable<java.lang.String>\n"
                        + "  supertype com.falkordb.ApiSurfaceTest$WithFactory\n"
                        + "  supertype java.lang.Iterable\n"
                        + "  method @Deprecated public default int count()\n",
                ApiSurface.describe(Strings.class, false));
    }

    public enum Color {
        RED
    }

    @Test
    void doesNotClimbAboveJdkSupertypes() {
        // java.lang.Enum implements Constable only since JDK 12: listing it would make the snapshot
        // depend on the JDK that generated it.
        assertEquals(
                Arrays.asList("extends java.lang.Enum<com.falkordb.ApiSurfaceTest$Color>", "supertype java.lang.Enum"),
                members(ApiSurface.describe(Color.class, false), "extends ", "supertype "));
    }

    public static class Connection {
        public static final int PORT = 6379;
        protected int state;

        public Connection() {}

        public static Connection open() {
            return new Connection();
        }

        protected void hook() {}

        public String ping() throws IOException {
            return "PONG";
        }
    }

    @Test
    void listsEveryVisibleMemberOfAnExportedType() {
        assertEquals(
                Arrays.asList(
                        "field public static final int PORT",
                        "field protected int state",
                        "constructor public com.falkordb.ApiSurfaceTest$Connection()",
                        "method protected void hook()",
                        "method public static com.falkordb.ApiSurfaceTest$Connection open()",
                        "method public java.lang.String ping() throws java.io.IOException"),
                members(ApiSurface.describe(Connection.class, false), "field ", "constructor ", "method "));
    }

    @Test
    void listsOnlyThePublicInstanceMembersOfAReachableType() {
        assertEquals(
                Arrays.asList("method public java.lang.String ping() throws java.io.IOException"),
                members(ApiSurface.describe(Connection.class, true), "field ", "constructor ", "method "));
    }

    @Test
    void diffGroupsChangesByType() {
        String before = "# comment\n\n"
                + "com.falkordb.GraphPipeline\n"
                + "  method public abstract java.lang.Object jsonNumIncrBy(java.lang.String, double)\n"
                + "  method public abstract java.lang.Object keep()\n"
                + "  method public abstract java.lang.Object old()\n"
                + "redis.clients.jedis.Jedis\n"
                + "  method public java.util.List<byte[]> aclLogBinary()\n";
        String after = "# another comment\r\n"
                + "com.falkordb.GraphPipeline\r\n"
                + "  method public abstract java.lang.Object jsonNumIncrBy(java.lang.String, java.lang.Number)\r\n"
                + "  method public abstract java.lang.Object keep()\r\n"
                + "  method @Deprecated public abstract java.lang.Object old()\r\n"
                + "redis.clients.jedis.Jedis\r\n";
        assertEquals(
                Arrays.asList(
                        "com.falkordb.GraphPipeline",
                        "  - method public abstract java.lang.Object jsonNumIncrBy(java.lang.String, double)",
                        "  ~ method @Deprecated public abstract java.lang.Object old()  (now deprecated)",
                        "  + method public abstract java.lang.Object jsonNumIncrBy(java.lang.String, java.lang.Number)",
                        "redis.clients.jedis.Jedis",
                        "  - method public java.util.List<byte[]> aclLogBinary()"),
                ApiSurface.diff(before, after));
        assertEquals(Arrays.asList(), ApiSurface.diff(before, "# only the comments differ\n" + before));
    }

    private static String block(String surface, String type) {
        int start = surface.indexOf("\n" + type + "\n");
        assertTrue(start >= 0, type + " is in the surface");
        int end = surface.indexOf("\n\n", start + 1);
        return surface.substring(start + 1, end < 0 ? surface.length() : end + 1);
    }

    private static List<String> members(String description, String... prefixes) {
        List<String> lines = new ArrayList<>();
        for (String raw : ApiSurface.lines(description)) {
            String line = raw.trim();
            for (String prefix : prefixes) {
                if (line.startsWith(prefix)) {
                    lines.add(line);
                    break;
                }
            }
        }
        return lines;
    }
}
