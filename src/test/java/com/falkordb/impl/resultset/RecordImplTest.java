package com.falkordb.impl.resultset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

public class RecordImplTest {

    @Test
    public void equalsHashCodeContract() {
        // Internal, mutable, non-final record type compared with instanceof; relax those checks.
        EqualsVerifier.forClass(RecordImpl.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    public void getStringReturnsNullForANullValue() {
        // A NULL column deserializes to null (`MATCH (n) RETURN n.missingProp`). getValue is documented
        // @Nullable, so getString must report the same absence rather than NPE on null.toString().
        RecordImpl record = new RecordImpl(Collections.singletonList("n"), Collections.singletonList(null));

        assertNull(record.getValue(0));
        assertNull(record.getString(0));
        assertNull(record.getString("n"));
    }

    @Test
    public void getStringStillRendersNonNullValues() {
        RecordImpl record = new RecordImpl(Arrays.asList("a", "b"), Arrays.asList((Object) 42L, "x"));

        assertEquals("42", record.getString(0));
        assertEquals("x", record.getString("b"));
    }

    @Test
    public void getValueDoesNotCheckTheRequestedType() {
        // <T> is inferred from the assignment and erased, so the unchecked cast inside getValue cannot
        // verify anything: the compiler inserts the checkcast at the CALLER. A wrong type therefore
        // fails in user code rather than here -- and does not fail at all while the value stays
        // untyped. Both halves are documented on Record#getValue; pin them so a future signature
        // change has to face the contract.
        RecordImpl record = new RecordImpl(Collections.singletonList("n"), Collections.singletonList((Object) 42L));

        assertThrows(ClassCastException.class, () -> {
            String misdeclared = record.getValue(0);
            assertNull(misdeclared, "unreachable: the checkcast throws before this runs");
        });
        assertThrows(ClassCastException.class, () -> {
            String misdeclared = record.getValue("n");
            assertNull(misdeclared, "unreachable: the checkcast throws before this runs");
        });

        // The silent half: no narrowing, no exception, wrong type never noticed.
        Object untyped = record.getValue(0);
        assertEquals(42L, untyped);
    }

    @Test
    public void valuesMayContainNullElements() {
        // A NULL column is stored as null, so values() is List<@Nullable Object>. The @NullMarked
        // package would otherwise promise callers that every element is non-null.
        RecordImpl record = new RecordImpl(Arrays.asList("a", "b"), Arrays.asList((Object) "x", null));

        List<@Nullable Object> values = record.values();

        assertEquals(2, values.size());
        assertEquals("x", values.get(0));
        assertNull(values.get(1));
    }

    @Test
    public void keysReflectsTheSchemaListItWasBuiltFrom() {
        // Records share the result set's schema list, which is now unmodifiable, so keys() is too.
        // Behaviour change vs 0.10.1, where mutating it corrupted every record in the result set.
        RecordImpl record = new RecordImpl(
                Collections.unmodifiableList(Collections.singletonList("name")), Collections.singletonList("a"));

        assertThrows(UnsupportedOperationException.class, () -> record.keys().add("injected"));
    }

    @Test
    public void unknownKeyIsReportedWithTheKeyName() {
        // header.indexOf(key) returns -1 for an unknown column, which used to reach values.get(-1) and
        // surface as "Index -1 out of bounds" with no mention of the offending key.
        RecordImpl record = new RecordImpl(Collections.singletonList("name"), Collections.singletonList("a"));

        IllegalArgumentException fromGetValue =
                assertThrows(IllegalArgumentException.class, () -> record.getValue("nmae"));
        assertTrue(fromGetValue.getMessage().contains("nmae"), "message must name the missing key");

        IllegalArgumentException fromGetString =
                assertThrows(IllegalArgumentException.class, () -> record.getString("nmae"));
        assertTrue(fromGetString.getMessage().contains("nmae"), "message must name the missing key");
    }
}
