package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.falkordb.Graph;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** The graph a reply is first decoded with, to find out whether decoding it needs a schema lookup. */
class NoLookupsTest {

    @Test
    void everyCommandStopsWithNeededInsteadOfBeingSent() {
        Graph graph = NoLookups.GRAPH;
        List<Executable> commands = Arrays.asList(
                () -> graph.query("CALL db.labels()"),
                () -> graph.readOnlyQuery("CALL db.labels()"),
                () -> graph.query("CALL db.labels()", 1000L),
                () -> graph.readOnlyQuery("CALL db.labels()", 1000L),
                () -> graph.profile("RETURN 1"),
                () -> graph.explain("RETURN 1"),
                () -> graph.copyGraph("copy"),
                graph::deleteGraph);

        for (Executable command : commands) {
            NoLookups.Needed needed = assertThrows(NoLookups.Needed.class, command);
            assertEquals(0, needed.getStackTrace().length, "caught right away, so it records no stack trace");
        }
    }

    @Test
    void closingItReleasesNothing() throws Exception {
        // It is shared and holds no connection, so closing it must neither fail nor stop it from working.
        NoLookups.GRAPH.close();

        assertThrows(NoLookups.Needed.class, () -> NoLookups.GRAPH.readOnlyQuery("CALL db.labels()"));
    }
}
