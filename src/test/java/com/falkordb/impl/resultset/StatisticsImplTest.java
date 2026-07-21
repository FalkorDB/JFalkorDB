package com.falkordb.impl.resultset;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class StatisticsImplTest {

    @Test
    public void equalsHashCodeContract() {
        // StatisticsImpl is an internal, mutable, non-final result type compared with instanceof. Its
        // lazily-built statistics map is always constructor-initialized (never null in practice), so
        // NULL_FIELDS is safe.
        EqualsVerifier.forClass(StatisticsImpl.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE, Warning.NULL_FIELDS)
                .verify();
    }
}
