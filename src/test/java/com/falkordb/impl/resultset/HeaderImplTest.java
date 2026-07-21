package com.falkordb.impl.resultset;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class HeaderImplTest {

    @Test
    public void equalsHashCodeContract() {
        // equals/hashCode compare the schema derived from `raw` (getSchemaTypes/getSchemaNames), not the
        // raw source list, so `raw` is ignored here. The lazily-built schema lists are always
        // constructor-initialized, so NULL_FIELDS is safe.
        EqualsVerifier.forClass(HeaderImpl.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE, Warning.NULL_FIELDS)
                .withIgnoredFields("raw")
                .verify();
    }
}
