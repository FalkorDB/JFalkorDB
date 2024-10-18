package com.falkordb;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
 
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IterableTest {

    private GraphContextGenerator api;

    /**
    * Sets up the FalkorDB graph API for the 'social' graph before test execution.
    * 
    * This method is annotated with @Before, indicating it runs before each test method.
    * It initializes the 'api' field with a FalkorDB driver instance for the 'social' graph.
    * 
    * @throws RuntimeException if there's an error initializing the FalkorDB driver
    */
    @Before
    public void createApi() {
        api = FalkorDB.driver().graph("social");
    }

    /**
    * Performs cleanup operations after a test by deleting the graph and closing the API connection.
    * This method is annotated with @After, indicating it should be executed after each test method.
    */
    @After
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    /**
     * Tests the iteration over records in a ResultSet.
     * 
     * This method performs the following steps:
     * 1. Creates 51 nodes with properties using a Cypher query.
     * 2. Retrieves all nodes using another Cypher query.
     * 3. Iterates through the ResultSet to count the records.
     * 4. Asserts that each record is not null.
     * 5. Verifies that the count matches the size of the ResultSet.
     * 
     * @throws AssertionError if any assertion fails
     */
    @Test
    public void testRecordsIterator() {
        api.query("UNWIND(range(0,50)) as i CREATE(:N{i:i})");

        ResultSet rs = api.query("MATCH(n) RETURN n");
        int count = 0;
        for (Record record : rs) {
            assertNotNull(record);
            count++;
        }
        assertEquals(rs.size(), count);
    }

    /**
    * Tests the iteration over records in a ResultSet.
    * 
    * This method creates multiple nodes in the database, queries them,
    * and then verifies that the number of records returned matches
    * the expected count by iterating through the ResultSet.
    *
    * @throws <UNKNOWN> if there's an error in database operations
    */
    @Test
    public void testRecordsIterable() {
        api.query("UNWIND(range(0,50)) as i CREATE(:N{i:i})");

        ResultSet rs = api.query("MATCH(n) RETURN n");
        int count = 0;
        for (@SuppressWarnings("unused")
        Record row : rs) {
            count++;
        }
        assertEquals(rs.size(), count);
    }

    /**
    * Tests the functionality of Records iterator and Iterable interface.
    * This method creates a set of nodes, performs a query to retrieve them,
    * and then iterates over the result set to ensure proper functionality.
    * 
    * @throws AssertionError if any assertion fails during the test
    */
    @Test
    public void testRecordsIteratorAndIterable() {
        api.query("UNWIND(range(0,50)) as i CREATE(:N{i:i})");

        ResultSet rs = api.query("MATCH(n) RETURN n");
        rs.iterator().next();
        int count = 0;
        for (Record row : rs) {
            assertNotNull(row);
            count++;
        }
        assertEquals(rs.size(), count);
    }

}
