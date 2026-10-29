package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.exceptions.GraphException;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * {@code explain}, {@code copyGraph} and {@code deleteGraph} report a server error as a
 * {@link GraphException}, as queries already did, on a {@link Graph} and on a {@link GraphContext} alike.
 *
 * <p>Only stable parts of the server's messages are checked: their wording differs between FalkorDB
 * versions.
 */
public class GraphCommandErrorIT {

    private GraphContextGenerator existing;
    private GraphContextGenerator missing;

    @BeforeEach
    public void createGraphs() {
        existing = TestServer.graph("graph-command-error-it");
        existing.query("CREATE ()");
        missing = TestServer.graph("graph-command-error-it-never-created");
    }

    @AfterEach
    public void deleteGraphs() {
        try {
            existing.deleteGraph();
        } finally {
            existing.close();
            missing.close();
        }
    }

    @Test
    public void anExplainWithASyntaxErrorThrowsAGraphException() {
        assertRejected(existing, g -> g.explain("MATCH (n RETURN n"), "Invalid input");
        assertRejected(
                existing, g -> g.explain("MATCH (n RETURN n", Collections.singletonMap("x", 1)), "Invalid input");
    }

    @Test
    public void copyingOrDeletingAGraphThatDoesNotExistThrowsAGraphException() {
        assertRejected(missing, g -> g.copyGraph("graph-command-error-it-copy"), "empty key");
        assertRejected(missing, Graph::deleteGraph, "empty key");
    }

    /** Runs the command on the graph and on a context of it, and checks both report the server's error. */
    private static void assertRejected(GraphContextGenerator graph, Consumer<Graph> command, String message) {
        assertGraphException(() -> command.accept(graph), message);
        try (GraphContext context = graph.getContext()) {
            assertGraphException(() -> command.accept(context), message);
        }
    }

    private static void assertGraphException(Runnable call, String message) {
        GraphException e = assertThrows(GraphException.class, call::run);
        assertTrue(e.getCause() instanceof JedisDataException, String.valueOf(e.getCause()));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }
}
