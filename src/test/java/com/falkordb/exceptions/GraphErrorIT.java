package com.falkordb.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.TestServer;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

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
        try (GraphContext c = api.getContext()) {
            GraphException exception = assertThrows(GraphException.class, () -> c.query("RETURN toUpper(5)"));
            assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
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
        try (GraphContext c = api.getContext()) {
            GraphException exception =
                    assertThrows(GraphException.class, () -> c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)"));
            assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
    }

    @Test
    public void testContextExceptionFlow() {

        try (GraphContext c = api.getContext()) {
            try {
                // Issue a query that causes a compile-time error
                c.query("RETURN toUpper(5)");
            } catch (Exception e) {
                Assertions.assertEquals(GraphException.class, e.getClass());
                Assertions.assertTrue(
                        e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
            }

            // On contexted api usage, connection should stay open
            try {
                // Issue a query that causes a compile-time error
                c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)");
            } catch (Exception e) {
                Assertions.assertEquals(GraphException.class, e.getClass());
                Assertions.assertTrue(
                        e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
            }
        }
    }

    @Test
    public void testContextRemainsUsableAfterFailedDeleteGraph() throws IOException {
        // A single-connection pool: the context holds the only connection, so whether it is returned to
        // the pool early, or more than once, shows in what the pool lends out next.
        try (Driver driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .poolMaxTotal(1)
                .poolMaxIdle(1)
                .poolMaxWait(Duration.ZERO)
                .build()) {
            long connectionId;
            try (Jedis connection = driver.getConnection()) {
                connectionId = connection.clientId();
            }

            try (GraphContext c = driver.graph("graph-error-it-missing").getContext()) {
                GraphException exception = assertThrows(GraphException.class, c::deleteGraph);
                assertTrue(exception.getMessage().contains("empty key"), exception.getMessage());
                // the failed delete must leave the connection with the context, not back in the pool
                assertThrows(JedisException.class, driver::getConnection);

                assertEquals(1, c.query("CREATE (:Person)").getStatistics().nodesCreated());
                c.deleteGraph();
            }

            // closing the context returned the connection once and intact: returning it a second time
            // would have disconnected it, and the pool would lend out a new connection instead
            try (Jedis connection = driver.getConnection()) {
                assertEquals(connectionId, connection.clientId());
            }
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
