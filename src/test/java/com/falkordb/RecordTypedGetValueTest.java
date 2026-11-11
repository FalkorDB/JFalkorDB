package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.impl.resultset.RecordImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@link Record#getValue(int, Class)} and {@link Record#getValue(String, Class)}: the checked
 * overloads that replace the unchecked {@code <T>} forms (#400). In {@code com.falkordb} so it can
 * see the package-private {@link RecordValues} and build an odd {@link Record} implementation.
 */
public class RecordTypedGetValueTest {

    private static final List<String> KEYS = Arrays.asList("n", "s", "missing", "flag", "score", "list");

    private static RecordImpl record() {
        return new RecordImpl(
                Collections.unmodifiableList(KEYS), Arrays.asList(42L, "x", null, true, 1.5, Arrays.asList(1L, 2L)));
    }

    /** A value of the requested class is returned as is, by index and by key. */
    @Test
    public void returnsAMatchingValueByIndexAndByKey() {
        RecordImpl record = record();

        Long byIndex = record.getValue(0, Long.class);
        String byKey = record.getValue("s", String.class);

        assertEquals(Long.valueOf(42L), byIndex);
        assertEquals("x", byKey);
    }

    /** The check is instanceof, so a supertype or a raw container class matches. */
    @Test
    public void aSupertypeMatches() {
        RecordImpl record = record();

        assertEquals(42L, record.getValue(0, Number.class));
        assertEquals("x", record.getValue("s", Object.class));
        assertEquals(Arrays.asList(1L, 2L), record.getValue("list", List.class));
    }

    /** A null column is returned as null without a type check, even for a primitive class. */
    @Test
    public void aNullValueIsNullForAnyType() {
        RecordImpl record = record();

        assertNull(record.getValue(2, String.class));
        assertNull(record.getValue("missing", Long.class));
        assertNull(record.getValue("missing", long.class));
        assertNull(record.getValue(2, void.class));
    }

    /**
     * A wrong type throws from the call itself, even when the result is assigned to Object. That is
     * the difference from the deprecated form: assigning to Object narrows nothing, so the bare
     * getValue never throws here (RecordImplTest.getValueDoesNotCheckTheRequestedType pins that).
     */
    @Test
    public void aWrongTypeThrowsFromTheCallEvenWhenTheResultStaysUntyped() {
        RecordImpl record = record();

        ClassCastException byIndex = assertThrows(ClassCastException.class, () -> {
            Object untyped = record.getValue(0, String.class);
            assertNull(untyped, "unreachable: the call itself throws");
        });
        assertEquals("Column 0 (\"n\") holds java.lang.Long, not the requested java.lang.String", byIndex.getMessage());

        ClassCastException byKey = assertThrows(ClassCastException.class, () -> {
            Object untyped = record.getValue("s", Long.class);
            assertNull(untyped, "unreachable: the call itself throws");
        });
        assertEquals("Column 1 (\"s\") holds java.lang.String, not the requested java.lang.Long", byKey.getMessage());
    }

    /**
     * With the deprecated form the checkcast is compiled into the caller, so the top frame is the
     * caller's. Here the top frame is the client's own check.
     */
    @Test
    public void theExceptionIsRaisedInsideTheClient() {
        ClassCastException thrown = assertThrows(ClassCastException.class, () -> record().getValue(0, String.class));

        assertEquals(RecordValues.class.getName(), thrown.getStackTrace()[0].getClassName());
    }

    /** Asking for another numeric type names the types FalkorDB integers and floats decode to. */
    @Test
    public void aWrongNumericTypeHintsAtTheDecodedTypes() {
        RecordImpl record = record();

        ClassCastException integer = assertThrows(ClassCastException.class, () -> record.getValue("n", Integer.class));
        assertEquals(
                "Column 0 (\"n\") holds java.lang.Long, not the requested java.lang.Integer"
                        + "; FalkorDB integers decode as java.lang.Long and floats as java.lang.Double",
                integer.getMessage());

        ClassCastException string = assertThrows(ClassCastException.class, () -> record.getValue("n", String.class));
        assertFalse(string.getMessage().contains("FalkorDB integers"), "no numeric hint for a non-numeric type");
    }

    /** A primitive class reads its wrapper: long.class reads a Long. */
    @Test
    public void aPrimitiveClassStandsForItsWrapper() {
        RecordImpl record = record();

        Long n = record.getValue(0, long.class);
        Boolean flag = record.getValue("flag", boolean.class);
        Double score = record.getValue("score", double.class);

        assertEquals(Long.valueOf(42L), n);
        assertTrue(flag);
        assertEquals(Double.valueOf(1.5), score);
    }

    /** A primitive class is not a conversion: int.class on a Long throws, as does void.class. */
    @Test
    public void aPrimitiveClassIsNotAConversion() {
        RecordImpl record = record();

        ClassCastException narrowed = assertThrows(ClassCastException.class, () -> record.getValue(0, int.class));
        assertEquals(
                "Column 0 (\"n\") holds java.lang.Long, not the requested int (as java.lang.Integer)"
                        + "; FalkorDB integers decode as java.lang.Long and floats as java.lang.Double",
                narrowed.getMessage());

        ClassCastException voided = assertThrows(ClassCastException.class, () -> record.getValue("s", void.class));
        assertEquals(
                "Column 1 (\"s\") holds java.lang.String, not the requested void (as java.lang.Void)",
                voided.getMessage());
    }

    /** A bad index throws the same exception, with the same message, as the deprecated form. */
    @Test
    @SuppressWarnings("deprecation") // compares against the deprecated form on purpose
    public void anUnknownIndexFailsExactlyAsTheUncheckedForm() {
        RecordImpl record = record();

        RuntimeException unchecked = assertThrows(RuntimeException.class, () -> record.getValue(99));
        RuntimeException checked = assertThrows(RuntimeException.class, () -> record.getValue(99, Long.class));

        assertSame(unchecked.getClass(), checked.getClass());
        assertEquals(unchecked.getMessage(), checked.getMessage());
        assertTrue(checked instanceof IndexOutOfBoundsException, "RecordImpl reports a bad index as IOOBE");
    }

    /** An unknown key throws the same exception, with the same message, as the deprecated form. */
    @Test
    @SuppressWarnings("deprecation") // compares against the deprecated form on purpose
    public void anUnknownKeyFailsExactlyAsTheUncheckedForm() {
        RecordImpl record = record();

        IllegalArgumentException unchecked =
                assertThrows(IllegalArgumentException.class, () -> record.getValue("nmae"));
        IllegalArgumentException checked =
                assertThrows(IllegalArgumentException.class, () -> record.getValue("nmae", Long.class));

        assertEquals(unchecked.getMessage(), checked.getMessage());
    }

    /**
     * Passing null where @NullMarked forbids it is the point; the index and key are both unknown,
     * so the order of the checks shows.
     */
    @Test
    public void aNullTypeIsRejectedBeforeTheLookup() {
        RecordImpl record = record();
        Class<Long> none = null;

        NullPointerException byIndex = assertThrows(NullPointerException.class, () -> record.getValue(99, none));
        assertEquals("type must not be null", byIndex.getMessage());
        NullPointerException byKey = assertThrows(NullPointerException.class, () -> record.getValue("nmae", none));
        assertEquals("type must not be null", byKey.getMessage());
    }

    /**
     * An implementer's keys() may be shorter than its values, or its key lookup may not be exact
     * keys() matching: report what is known rather than a wrong index or name.
     */
    @Test
    public void theMessageLeavesOutAColumnTheRecordCannotResolve() {
        Record odd = new OddRecord();

        ClassCastException byIndex = assertThrows(ClassCastException.class, () -> odd.getValue(1, String.class));
        assertEquals("Column 1 holds java.lang.Long, not the requested java.lang.String", byIndex.getMessage());

        ClassCastException byKey = assertThrows(ClassCastException.class, () -> odd.getValue("A", String.class));
        assertEquals("Column \"A\" holds java.lang.Long, not the requested java.lang.String", byKey.getMessage());

        ClassCastException fromTheEnd = assertThrows(ClassCastException.class, () -> odd.getValue(-1, String.class));
        assertEquals("Column holds java.lang.Long, not the requested java.lang.String", fromTheEnd.getMessage());
    }

    /** One key ("a", matched case-insensitively) over two values; a negative index counts from the end. */
    private static final class OddRecord implements Record {
        private final List<@Nullable Object> values = Arrays.asList(1L, 2L);

        /** Reads by position; a negative index counts from the end, which RecordImpl rejects. */
        @Override
        @SuppressWarnings({"deprecation", "unchecked", "TypeParameterUnusedInFormals"})
        public <T> @Nullable T getValue(int index) {
            return (T) values.get(index < 0 ? values.size() + index : index);
        }

        /** Matches "a" case-insensitively, so keys().indexOf("A") misses a column the lookup finds. */
        @Override
        @SuppressWarnings({"deprecation", "unchecked", "TypeParameterUnusedInFormals"})
        public <T> @Nullable T getValue(String key) {
            return (T) values.get(key.equalsIgnoreCase("a") ? 0 : -1);
        }

        @Override
        public @Nullable String getString(int index) {
            return String.valueOf(values.get(index));
        }

        @Override
        public @Nullable String getString(String key) {
            return String.valueOf((Object) getValue(key, Object.class));
        }

        @Override
        public List<String> keys() {
            return Collections.singletonList("a");
        }

        @Override
        public List<@Nullable Object> values() {
            return values;
        }

        @Override
        public boolean containsKey(String key) {
            return key.equalsIgnoreCase("a");
        }

        @Override
        public int size() {
            return values.size();
        }
    }
}
