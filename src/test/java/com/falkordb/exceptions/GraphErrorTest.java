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
    * Sets up the test environment by creating a FalkorDB graph API instance and initializing test data.
    * 
    * This method is annotated with @Before, indicating it runs before each test method.
    * It creates a graph named "social" and adds two person nodes with different property types.
    * 
    * @throws AssertionError if the graph creation query fails
    */
    @Before
    public void createApi() {
        api = FalkorDB.driver().graph("social");
        Assert.assertNotNull(api.query("CREATE (:person{mixed_prop: 'strval'}), (:person{mixed_prop: 50})"));
    }

    /**
    * Deletes the graph and closes the API connection after test execution.
    * 
    * This method is annotated with @After, indicating it runs after each test method.
    * It performs cleanup by deleting the graph and closing the API connection.
    * 
    * @throws Exception if an error occurs during graph deletion or API closure
    */
    @After
    public void deleteGraph() throws Exception{

        api.deleteGraph();
        api.close();
    }

    /**
    * Tests the syntax error reporting functionality of the API.
    * 
    * This method verifies that when an invalid query is submitted to the API,
    * it throws a GraphException with an appropriate error message.
    * 
    * @throws GraphException if the query syntax is invalid
    */
    @Test
    public void testSyntaxErrorReporting() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("RETURN toUpper(5)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    /**
    * Tests the runtime error reporting functionality of the API.
    * 
    * This method verifies that the API correctly throws a GraphException
    * when attempting to use the toUpper() function on a non-string property.
    * It also checks that the exception message contains the expected error description.
    * 
    * @throws GraphException if the query execution fails due to a type mismatch
    */
    @Test
    public void testRuntimeErrorReporting() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    /**
     * Tests exception handling for invalid queries in the API.
     * 
     * This method verifies that the API properly throws GraphExceptions
     * when encountering compile-time errors in queries. It tests two scenarios:
     * 1. Attempting to use toUpper() function with an integer.
     * 2. Trying to use toUpper() on a property that may contain non-string values.
     * 
     * @throws GraphException when a query contains a compile-time error
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
            Assert.assertEquals(GraphException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
    }

    /**
    * Tests error reporting for context syntax errors.
    * 
    * This method verifies that the GraphContext correctly throws a GraphException
    * when an invalid query is executed. Specifically, it checks if the error message
    * contains the expected type mismatch information.
    * 
    * @throws GraphException if the query syntax is invalid
    */
    @Test
    public void testContextSyntaxErrorReporting() {
        GraphContext c = api.getContext();

        GraphException exception = assertThrows(GraphException.class,
                () -> c.query("RETURN toUpper(5)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    /**
    * Tests the error reporting for missing parameters in a graph query.
    * 
    * This method verifies that when a query is executed with missing parameters,
    * a GraphException is thrown with an appropriate error message.
    * 
    * @throws GraphException if the query execution fails due to missing parameters
    */
    @Test
    public void testMissingParametersSyntaxErrorReporting() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("RETURN $param"));
        assertTrue(exception.getMessage().contains("Missing parameters"));
    }

    /**
     * Tests the error reporting for missing parameters in a graph query.
     * 
     * This method verifies that when a query is executed with missing parameters,
     * a GraphException is thrown with an appropriate error message.
     * 
     * @throws GraphException if the query execution fails due to missing parameters
     */    @Test
    public void testMissingParametersSyntaxErrorReporting2() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("RETURN $param", new HashMap<>()));
        assertTrue(exception.getMessage().contains("Missing parameters"));
    }

    /**
    * Tests the runtime error reporting in the GraphContext query execution.
    * 
    * This method verifies that when an invalid query is executed, a GraphException
    * is thrown with an appropriate error message indicating a type mismatch.
    * 
    * @throws GraphException if the query execution fails due to a type mismatch
    */
    @Test
    public void testContextRuntimeErrorReporting() {
        GraphContext c = api.getContext();

        GraphException exception = assertThrows(GraphException.class,
                () -> c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)"));
        assertTrue(exception.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
    }

    /**
     * Tests the exception handling in a GraphContext when executing invalid queries.
     * 
     * This method verifies that:
     * 1. A query with a compile-time error throws a GraphException with the correct error message.
     * 2. The GraphContext remains open after catching an exception, allowing subsequent queries.
     * 3. A second query with a different compile-time error also throws a GraphException with the appropriate message.
     * 
     * @throws Exception if an unexpected error occurs during the test
     */    @Test
    public void testContextExceptionFlow() {

        GraphContext c = api.getContext();
        try {
            // Issue a query that causes a compile-time error
            c.query("RETURN toUpper(5)");
        } catch (Exception e) {
            Assert.assertEquals(GraphException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }

        // On contexted api usage, connection should stay open
        try {
            // Issue a query that causes a compile-time error
            c.query("MATCH (p:person) RETURN toUpper(p.mixed_prop)");
        } catch (Exception e) {
            Assert.assertEquals(GraphException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Type mismatch: expected String or Null but was Integer"));
        }
    }

    /**
     * Tests if a timeout exception is thrown when a query execution exceeds the specified time limit.
     * 
     * This method verifies that a GraphException is thrown when a long-running query
     * is executed with a short timeout. It also checks if the exception message
     * contains the expected "Query timed out" text.
     * 
     * @throws GraphException When the query execution time exceeds the specified timeout
     */
    @Test
    public void timeoutException() {
        GraphException exception = assertThrows(GraphException.class,
                () -> api.query("UNWIND range(0,100000) AS x WITH x AS x WHERE x = 10000 RETURN x", 1L));
        assertTrue(exception.getMessage().contains("Query timed out"));
    }
}
