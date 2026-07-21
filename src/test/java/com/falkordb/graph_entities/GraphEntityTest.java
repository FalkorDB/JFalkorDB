package com.falkordb.graph_entities;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

public class GraphEntityTest {

    @Test
    public void equalsHashCodeContract() {
        // Abstract base for Node/Edge with mutable fields and instanceof-based equals, so relax the
        // mutability/inheritance checks. Verifies the shared id + properties contract.
        EqualsVerifier.forClass(GraphEntity.class)
                .suppress(Warning.NONFINAL_FIELDS, Warning.STRICT_INHERITANCE)
                .verify();
    }
}
