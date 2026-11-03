package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Response;

/**
 * The client-side schema cache against a real server. Compact replies name labels by their position in
 * {@code db.labels()}, and every {@code driver.graph(name)} handle keeps its own cache, so a graph that
 * another handle deletes and recreates changes those positions under this handle.
 */
public class GraphCacheIT {

    private static final String GRAPH = "graph-cache-it";

    private GraphContextGenerator reader;
    private GraphContextGenerator writer;

    @BeforeEach
    public void createHandles() {
        reader = TestServer.graph(GRAPH);
        writer = TestServer.graph(GRAPH);
    }

    @AfterEach
    public void deleteGraph() {
        try {
            writer.deleteGraph();
        } finally {
            reader.close();
            writer.close();
        }
    }

    @Test
    public void aRefreshReplacesLabelsOfARecreatedGraph() {
        writer.query("CREATE (:Old)");
        assertEquals("Old", onlyNode(reader.query("MATCH (n) RETURN n")).getLabel(0));

        recreateWith("New0", "New1");

        // id 1 is not cached, so the reader refreshes its labels ...
        assertEquals("New1", onlyNode(reader.query("MATCH (n:New1) RETURN n")).getLabel(0));
        // ... and the refresh replaced id 0, which used to decode as the old graph's label
        assertEquals("New0", onlyNode(reader.query("MATCH (n:New0) RETURN n")).getLabel(0));
    }

    @Test
    public void aReplyNamingALabelTheGraphNoLongerListsFailsWithAGraphException() {
        writer.query("CREATE (:L0)");
        writer.query("CREATE (:L1)");
        writer.query("CREATE (:L2)");

        try (GraphContext context = reader.getContext()) {
            GraphPipeline pipeline = context.pipelined();
            // sync() reads the reply without decoding it, so it is decoded after the graph changes
            Response<ResultSet> reply = pipeline.query("MATCH (n:L2) RETURN n");
            pipeline.sync();

            recreateWith("Only");

            GraphException e = assertThrows(GraphException.class, reply::get);
            assertTrue(e.getMessage().contains("label id 2"), e.getMessage());
        }
    }

    /** Deletes the graph through the writer and recreates it with one node per label, in order. */
    private void recreateWith(String... labels) {
        writer.deleteGraph();
        for (String label : labels) {
            // one query per label, so the server numbers them in this order
            writer.query("CREATE (:" + label + ")");
        }
    }

    private static Node onlyNode(ResultSet resultSet) {
        assertEquals(1, resultSet.size());
        return (Node) resultSet.iterator().next().getValue(0);
    }
}
