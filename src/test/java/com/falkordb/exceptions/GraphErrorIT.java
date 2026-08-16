package com.falkordb.exceptions;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.TestServer;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GraphErrorIT {

    // FalkorDB reworded this error: v4.20.1 and `latest` say "Missing parameters", while `edge`
    // names the offending parameter — verified on the wire:
    //   latest -> "Missing parameters"
    //   edge   -> "Parameter param not found"
    // Matching the SHAPE rather than a bare "not found" substring keeps the assertion honest: the
    // looser form would also pass on any unrelated error that happens to contain those words.
    private static final Pattern MISSING_PARAMETER = Pattern.compile("Parameter\\s+\\S+\\s+not found");

    private GraphContextGenerator api;

    @BeforeEach
    public void createApi() {
        api = TestServer.graph("social");
        Assertions.assertNotNull(api.query("CREATE (:person{mixed_prop: 'strval'}), (:person{mixed_prop: 50})"));
    }

    @AfterEach
    public void deleteGraph() throws Exception {

        api.deleteGraph();
        api.close();
    }

    @Test
    public void testSyntaxErrorReporting() {
        GraphException exception = assertThrows(GraphException.class, () -> api.query("RETURN toUpper(5)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    @Test
    public void testRuntimeErrorReporting() {
        GraphException exception =
                assertThrows(GraphException.class, () -> api.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    @Test
    public void testExceptionFlow() {

        try {
            // Issue a query that causes a compile-time error
            api.query("RETURN toUpper(5)");
        } catch (Exception e) {
            Assertions.assertEquals(GraphException.class, e.getClass());
            Assertions.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }

        // On general api usage, user should get a new connection

        try {
            // Issue a query that causes a compile-time error
            api.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)");
        } catch (Exception e) {
            Assertions.assertEquals(GraphException.class, e.getClass());
            Assertions.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
    }

    @Test
    public void testContextSyntaxErrorReporting() {
        GraphContext c = api.getContext();

        GraphException exception = assertThrows(GraphException.class, () -> c.query("RETURN toUpper(5)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    @Test
    public void testMissingParametersSyntaxErrorReporting() {
        GraphException exception = assertThrows(GraphException.class, () -> api.query("RETURN $param"));
        assertMissingParameterMessage(exception);
    }

    @Test
    public void testMissingParametersSyntaxErrorReporting2() {
        GraphException exception =
                assertThrows(GraphException.class, () -> api.query("RETURN $param", new HashMap<>()));
        assertMissingParameterMessage(exception);
    }

    private static void assertMissingParameterMessage(GraphException exception) {
        String message = exception.getMessage();
        assertTrue(
                message.contains("Missing parameters")
                        || MISSING_PARAMETER.matcher(message).find(),
                "Unexpected missing-parameter message: " + message);
    }

    @Test
    public void testContextRuntimeErrorReporting() {
        GraphContext c = api.getContext();

        GraphException exception =
                assertThrows(GraphException.class, () -> c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    @Test
    public void testContextExceptionFlow() {

        GraphContext c = api.getContext();
        try {
            // Issue a query that causes a compile-time error
            c.query("RETURN toUpper(5)");
        } catch (Exception e) {
            Assertions.assertEquals(GraphException.class, e.getClass());
            Assertions.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }

        // On contexted api usage, connection should stay open
        try {
            // Issue a query that causes a compile-time error
            c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)");
        } catch (Exception e) {
            Assertions.assertEquals(GraphException.class, e.getClass());
            Assertions.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
    }

    @Test
    public void timeoutException() {
        GraphException exception = assertThrows(
                GraphException.class,
                () -> api.query("UNWIND range(0,100000) AS x WITH x AS x WHERE x = 10000 RETURN x", 1L));
        assertTrue(exception.getMessage().contains("Query timed out"));
    }
}
