package com.falkordb.impl.graph_cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Graph;
import com.falkordb.exceptions.GraphException;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** The copy of a schema cache that decodes the replies queued before a {@code deleteGraph()}. */
class FrozenGraphCacheTest {

    /** A copy never asks a graph, so it is given none. */
    private static final Graph NO_GRAPH = null;

    private final GraphCache copy = new FrozenGraphCache(
            new SchemaNames(
                    null,
                    Collections.singletonList("Person"),
                    Collections.singletonList("KNOWS"),
                    Collections.singletonList("name")),
            FrozenGraphCache.DELETED);

    @Test
    void looksEachKindOfIdUpInWhatItHolds() {
        assertEquals("Person", copy.getLabel(0, NO_GRAPH));
        assertEquals("name", copy.getPropertyName(0, NO_GRAPH));
        assertEquals("KNOWS", copy.getRelationshipType(0, NO_GRAPH));
    }

    @Test
    void reportsAnIdItDoesNotHold() {
        GraphException unknown = assertThrows(GraphException.class, () -> copy.getRelationshipType(1, NO_GRAPH));
        assertTrue(unknown.getMessage().startsWith("Cannot decode relationship type id 1:"), unknown.getMessage());

        GraphException negative = assertThrows(GraphException.class, () -> copy.getLabel(-1, NO_GRAPH));
        assertTrue(negative.getMessage().startsWith("Cannot decode label id -1:"), negative.getMessage());
    }

    @Test
    void neverSendsAndNeverKeepsNamesAReplyCarried() {
        assertNull(copy.request());
        GraphCache decoded = copy.decodeWith(
                SchemaReply.parse(SchemaReplyTest.resp2(
                        "hash", 9L,
                        "labels", SchemaReplyTest.strings("Other"),
                        "relationshipTypes", SchemaReplyTest.strings(),
                        "propertyKeys", SchemaReplyTest.strings())),
                null);
        assertEquals("Other", decoded.getLabel(0, NO_GRAPH));
        assertEquals("Person", copy.getLabel(0, NO_GRAPH));
    }

    @Test
    void neverChanges() {
        assertSame(copy, copy.snapshot(), "a copy is its own snapshot");

        copy.clear();

        assertEquals("Person", copy.getLabel(0, NO_GRAPH));
        assertEquals("name", copy.getPropertyName(0, NO_GRAPH));
        assertEquals("KNOWS", copy.getRelationshipType(0, NO_GRAPH));
    }
}
