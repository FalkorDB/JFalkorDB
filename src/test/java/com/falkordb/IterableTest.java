package com.falkordb;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
 
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IterableTest {

    private GraphContextGenerator api;

    /**
     * Sets up the FalkorDB graph API for social network testing.
     * 
     * This method is executed before each test case to initialize the graph API.
     * It creates a new graph named "social" using the FalkorDB driver.
     * 
     * @return void
     */
    @Before
    public void createApi() {
        api = FalkorDB.driver().graph("social");
    }

    ```
    /**
     * Deletes the graph and closes the API connection after test execution.
     * 
     * This method is annotated with @After, indicating it runs after each test method.
     * It performs cleanup by deleting the graph and closing the API connection.
     * 
     * @throws RuntimeException if there's an error during graph deletion or API closure
     */
    ```
    @After
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    /**
     * Tests the functionality of iterating through records in a ResultSet.
     * 
     * This test method creates a set of nodes, queries them, and then iterates
     * through the results to ensure proper functionality of the ResultSet iterator.
     * 
     * @return void This method doesn't return anything
     */
    @Test
    public void testRecordsIterator() {
        api.query("UNWIND(range(0,50)) as i CREATE(:N{i:i})");

        ResultSet rs = api.query("MATCH(n) RETURN n");
        /**
         * Tests the iterable behavior of ResultSet records.
         * 
         * This test method creates a series of nodes, queries them, and verifies
         * that the ResultSet can be properly iterated over using a for-each loop.
         * It also checks if the number of iterated records matches the size reported
         * by the ResultSet.
         * 
         * @param None
         * @return void
         */
        int count = 0;
        for (Record record : rs) {
            assertNotNull(record);
            count++;
        }
        assertEquals(rs.size(), count);
    }

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
    * Tests the functionality of ResultSet's iterator and iterable interfaces.
    * 
    * @return void
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
