package com.falkordb.impl.resultset;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class RecordImplTest {

    @Test
    public void equalsHashCodeContract() {
        // Internal, mutable, non-final record type compared with instanceof; relax those checks.
        EqualsVerifier.forClass(RecordImpl.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }
}
