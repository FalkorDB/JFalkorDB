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

    public interface Bounded<T extends Number> {
        T get();
    }

    @SuppressWarnings("rawtypes")
    public interface RawBounded extends Bounded {}

    @Test
    void erasesTheMembersOfARawSupertype() {
        // Through a raw supertype, get() returns the erasure of T. Printing "T get()" would hide a
        // change of T's bound, which changes the JVM signature callers are compiled against.
        assertEquals(
                Arrays.asList("method public abstract java.lang.Number get()"),
                members(ApiSurface.describe(RawBounded.class, false), "method "));
    }

    public interface Marker<T> {}

    public interface StringMarked extends Marker<String> {}

    public interface Marked extends StringMarked {}

    @Test
    void listsTheTypeArgumentsOfTransitiveSupertypes() {
        // Whether Marked can be assigned to a Marker<String> depends on the argument, not the class.
        assertEquals(
                Arrays.asList(
                        "extends com.falkordb.ApiSurfaceTest$StringMarked",
                        "supertype com.falkordb.ApiSurfaceTest$Marker<java.lang.String>",
                        "supertype com.falkordb.ApiSurfaceTest$StringMarked"),
                members(ApiSurface.describe(Marked.class, false), "extends ", "supertype "));
    }

    @SuppressWarnings("rawtypes")
    public interface RawMarked extends Marker {}

    public interface ObjectMarked extends Marker<Object> {}

    @Test
    void keepsARawSupertypeRaw() {
        // A RawMarked converts to a Marker<String> (unchecked), an ObjectMarked doesn't: a dependency
        // going from one to the other must change the line.
        assertEquals(
                Arrays.asList("supertype com.falkordb.ApiSurfaceTest$Marker"),
                members(ApiSurface.describe(RawMarked.class, false), "supertype "));
        assertEquals(
                Arrays.asList("supertype com.falkordb.ApiSurfaceTest$Marker<java.lang.Object>"),
                members(ApiSurface.describe(ObjectMarked.class, false), "supertype "));
    }

    public static class Outer<T> {
        T value;

        public class Inner {
            public T get() {
                return value;
            }
        }
    }

    public static class StringInner extends Outer<String>.Inner {
        public StringInner(Outer<String> outer) {
            outer.super();
        }
    }

    public static class InheritsStringInner extends StringInner {
        public InheritsStringInner(Outer<String> outer) {
            super(outer);
        }
    }

    @Test
    void resolvesTheTypeArgumentsOfAnOuterClass() {
        // get() returns Outer's T, which StringInner supplies through Outer<String>.Inner: callers
        // expecting a String break if that becomes Outer<Integer>.Inner.
        String description = ApiSurface.describe(InheritsStringInner.class, false);
        assertEquals(
                Arrays.asList(
                        "supertype com.falkordb.ApiSurfaceTest$Outer<java.lang.String>$Inner",
                        "supertype com.falkordb.ApiSurfaceTest$StringInner"),
                members(description, "supertype "));
        assertEquals(
                Arrays.asList("method public java.lang.String get() // erased: java.lang.Object get()"),
                members(description, "method "));
    }

    public static class Holder {
        public class Values {
            private final List<String> values;

            public Values(List<String> values) {
                this.values = values;
            }

            public List<String> values() {
                return values;
            }

            public Holder holder() {
                return Holder.this;
            }
        }
    }

    @Test
    void keepsTheGenericParametersOfAnInnerClassConstructor() {
        assertEquals(
                Arrays.asList("constructor public com.falkordb.ApiSurfaceTest$Holder$Values("
                        + "com.falkordb.ApiSurfaceTest$Holder, java.util.List<java.lang.String>)"),
                members(ApiSurface.describe(Holder.Values.class, false), "constructor "));
    }

    public interface Root {
        int VALUE = 1;

        int root();
    }

    public interface Left extends Root {
        int VALUE = 2;

        int left();
    }

    public interface Right extends Root {}

    public interface Diamond extends Left, Right {}

    @Test
    void aFieldHiddenOnOneInheritancePathIsStillInheritedOnAnother() {
        // Diamond.VALUE doesn't compile: Left.VALUE hides Root.VALUE only along Left, and Right still
        // passes Root.VALUE down.
        assertEquals(
                Arrays.asList("field public static final int VALUE // ambiguous: 2 declarations"),
                members(ApiSurface.describe(Diamond.class, false), "field "));
    }

    public static class Base {
        public int value;
    }

    @SuppressWarnings({"HidingField", "UnusedVariable"})
    public static class Middle extends Base {
        private int value;
    }

    public static class Client extends Middle {}

    @Test
    void aPrivateFieldHidesTheSameNamedFieldsAboveIt() {
        // Client has no accessible `value`: Middle's private one hides Base's, so code compiled
        // against Client.value breaks when a dependency adds Middle.value.
        assertEquals(Arrays.asList(), members(ApiSurface.describe(Client.class, false), "field "));
    }

    public interface Limits {
        int MAX = 1;

        int limit();
    }

    public static class Defaults {
        public static final int MAX = 2;
    }

    public abstract static class Settings extends Defaults implements Limits {}

    @Test
    void marksAFieldInheritedFromBothASuperclassAndAnInterfaceAsAmbiguous() {
        // Settings.MAX doesn't compile: unlike methods, a superclass field doesn't win over an
        // interface one.
        assertEquals(
                Arrays.asList("field public static final int MAX // ambiguous: 2 declarations"),
                members(ApiSurface.describe(Settings.class, false), "field "));
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
                        + "  supertype java.lang.Iterable<java.lang.String>\n"
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
                Arrays.asList(
                        "extends java.lang.Enum<com.falkordb.ApiSurfaceTest$Color>",
                        "supertype java.lang.Enum<com.falkordb.ApiSurfaceTest$Color>"),
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
        assertEquals(
                Arrays.asList(
                        "com.falkordb.GraphPipeline",
                        "  - method public abstract java.lang.Object jsonNumIncrBy(java.lang.String, double)",
                        "redis.clients.jedis.Jedis",
                        "  - method public java.util.List<byte[]> aclLogBinary()"),
                ApiSurface.breaks(before, after),
                "removed and changed lines break callers; additions and deprecations don't");
        assertEquals(Arrays.asList(), ApiSurface.breaks(after, after + "redis.clients.jedis.Response\n  type x\n"));
    }

    @Test
    void anAbstractMethodAddedToAnExportedInterfaceIsABreak() {
        String before = "## Exported types (com.falkordb)\n\n"
                + "com.falkordb.GraphPipeline\n"
                + "  type public interface com.falkordb.GraphPipeline\n"
                + "  method public abstract java.lang.Object a()\n"
                + "\n## Types reachable from the signatures exported types declare\n\n"
                + "redis.clients.jedis.Response\n"
                + "  type public class redis.clients.jedis.Response\n";
        String after = "## Exported types (com.falkordb)\n\n"
                + "com.falkordb.GraphPipeline\n"
                + "  type public interface com.falkordb.GraphPipeline\n"
                + "  method public abstract java.lang.Object a()\n"
                + "  method public abstract java.lang.Object b()\n"
                + "  method public default java.lang.Object c()\n"
                + "  method public static java.lang.Object d()\n"
                + "com.falkordb.Fresh\n"
                + "  type public interface com.falkordb.Fresh\n"
                + "  method public abstract java.lang.Object e()\n"
                + "\n## Types reachable from the signatures exported types declare\n\n"
                + "redis.clients.jedis.Response\n"
                + "  type public class redis.clients.jedis.Response\n"
                + "  method public abstract java.lang.Object f()\n";
        // Only b() breaks: c() and d() need no implementation, Fresh has no implementations yet, and
        // callers don't implement a reachable type.
        assertEquals(
                Arrays.asList(
                        "com.falkordb.GraphPipeline",
                        "  + method public abstract java.lang.Object b()  (new abstract method: implementations break)"),
                ApiSurface.breaks(before, after));
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
