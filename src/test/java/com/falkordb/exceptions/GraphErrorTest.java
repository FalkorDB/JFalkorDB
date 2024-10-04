package com.falkordb.exceptions;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;

public class GraphErrorTest {

    private GraphContextGenerator api;

    /**
     * Creates and initializes a FalkorDB graph API for testing.
     * 
     * @throws AssertionError if the graph creation query fails
     */
    @Before
    ```
    /**
     * Deletes the graph and closes the API connection after test execution.
     * 
     * This method is annotated with @After, indicating it runs after each test method.
     * It performs cleanup operations by deleting the graph and closing the API connection.
     * 
     * @throws Exception if an error occurs during graph deletion or API closure
     */
    ```    public void createApi() {
        api = FalkorDB.driver().graph("social");
        Assert.assertNotNull(api.query("CREATE (:person{mixed_prop: 'strval'}), (:person{mixed_prop: 50})"));
    }

    @After
    public void deleteGraph() throws Exception{
/**
* Tests the syntax error reporting functionality of the graph API.
* 
* This test verifies that a GraphException is thrown when an invalid query is executed,
* specifically when trying to use the toUpper() function with an integer argument.
* 
* @return void
* @throws GraphException When the query contains a syntax error
*/

        api.deleteGraph();
        api.close();
    }

    @Test
    public void testSyntaxErrorReporting() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("RETURN toUpper(5)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    /**
    * Tests the runtime error reporting functionality of the graph API.
    * 
    * @throws GraphException When a type mismatch occurs during query execution
    * @return void This method doesn't return anything
    */
    @Test
    public void testRuntimeErrorReporting() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    /**
    * Tests exception handling in the API for invalid queries.
    * 
    * @return void
    */
    @Test
    public void testExceptionFlow() {

        try {
            // Issue a query that causes a compile-time error
            api.query("RETURN toUpper(5)");
        } catch (Exception e) {
            Assert.assertEquals(GraphException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }

        // On general api usage, user should get a new connection

        try {
            // Issue a query that causes a compile-time error
            api.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)");
        } catch (Exception e) {
            /**
             * Tests the reporting of syntax errors in graph queries.
             * 
             * This test method verifies that the graph context correctly throws a GraphException
             * when an invalid query is executed, specifically checking for proper type mismatch reporting.
             * 
             * @param None
             * @return None
             * @throws GraphException when the query contains a syntax error
             */
            Assert.assertEquals(GraphException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
    }

    @Test
    public void testContextSyntaxErrorReporting() {
        GraphContext c = api.getContext();

        ```
        /**
         * Tests the syntax error reporting for missing parameters in a query.
         * 
         * This test method verifies that when a query is executed with a missing parameter,
         * a GraphException is thrown with an appropriate error message.
         *
         * @throws GraphException If the query execution fails as expected
         */
        ```        GraphException exception = assertThrows(GraphException.class,
                () -> c.query("RETURN toUpper(5)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    ```
    /**
     * Tests the runtime error reporting functionality of the GraphContext.
     * 
     * This test method verifies that a GraphException is thrown when attempting to
     * execute a query with a type mismatch, and checks if the error message
     * contains the expected content.
     * 
     * @return void This method doesn't return anything
     /**
     * Tests the exception handling flow in a GraphContext.
     * 
     * This test method verifies that appropriate exceptions are thrown and handled
     * for invalid queries in a GraphContext. It checks both compile-time errors
     * and type mismatches in query execution.
     *
     * @return void This method doesn't return anything
     */
     * @throws GraphException if the query execution fails due to a type mismatch
     */
    ```    @Test
    public void testMissingParametersSyntaxErrorReporting() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("RETURN $param"));
        assertTrue(exception.getMessage().contains("Missing parameters"));
    }

    /**
    * Tests the reporting of syntax errors for missing parameters in a query.
    *
    * @throws GraphException Expected to be thrown when a parameter is missing
    */
    @Test
    public void testMissingParametersSyntaxErrorReporting2() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("RETURN $param", new HashMap<>()));
        assertTrue(exception.getMessage().contains("Missing parameters"));
    }

    @Test
    public void testContextRuntimeErrorReporting() {
        GraphContext c = api.getContext();

        GraphException exception = assertThrows(GraphException.class,
                () -> c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    @Test
    public void testContextExceptionFlow() {

        GraphContext c = api.getContext();
        try {
            // Issue a query that causes a compile-time error
            c.query("RETURN toUpper(5)");
        } catch (Exception e) {
            Assert.assertEquals(GraphException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }

        /**
         * Tests the behavior of the API when a query times out.
         * 
         * @throws GraphException When the query execution exceeds the specified timeout
         * @return void This test method doesn't return a value
         */
        // On contexted api usage, connection should stay open
        try {
            // Issue a query that causes a compile-time error
            c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)");
        } catch (Exception e) {
            Assert.assertEquals(GraphException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
    }

    @Test
    public void timeoutException() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("UNWIND range(0,100000) AS x WITH x AS x WHERE x = 10000 RETURN x", 1L));
        assertTrue(exception.getMessage().contains("Query timed out"));
    }
}
