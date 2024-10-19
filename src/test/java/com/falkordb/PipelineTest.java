package com.falkordb;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import com.falkordb.impl.resultset.ResultSetImpl;

public class PipelineTest {

    private GraphContextGenerator api;

    /**
     * Initializes the FalkorDB API for the "social" graph.
     * This method is annotated with @Before, indicating it should be executed before test methods.
     */
    @Before
    public void createApi() {
        api = FalkorDB.driver().graph("social");

    }

    /**
     * Deletes the graph and closes the API connection.
     * This method is annotated with @After, indicating it should be executed after test methods.
     * 
     * @throws RuntimeException if there's an error during graph deletion or API closure
     */    @After
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    /**
    * Executes a series of Redis and Graph operations in a pipelined manner and verifies the results.
    * 
    * This test method performs the following operations:
    * 1. Sets a Redis key-value pair
    * 2. Creates two Person nodes in the graph
    * 3. Increments a numeric value
    * 4. Retrieves a value
    * 5. Queries the graph for a specific node
    * 6. Calls a procedure to get all labels
    * 
    * After executing these operations, it validates the results to ensure correct behavior
    * of the pipelined execution and proper integration between Redis and Graph operations.
    * 
    * @throws Exception if any error occurs during the execution of the test
    */    @Test
    public void testSync() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();
            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            pipeline.incr("x");
            pipeline.get("x");
            pipeline.query("MATCH (n:Person{name:'a'}) RETURN n");
            pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            // Redis set command
            Assert.assertEquals(String.class, results.get(0).getClass());
            Assert.assertEquals("OK", results.get(0));

            // Redis graph command
            Assert.assertEquals(ResultSetImpl.class, results.get(1).getClass());
            ResultSet resultSet = (ResultSet) results.get(1);
            Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

            Assert.assertEquals(ResultSetImpl.class, results.get(2).getClass());
            resultSet = (ResultSet) results.get(2);
            Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

            // Redis incr command
            Assert.assertEquals(Long.class, results.get(3).getClass());
            Assert.assertEquals(2L, results.get(3));

            // Redis get command
            Assert.assertEquals(String.class, results.get(4).getClass());
            Assert.assertEquals("2", results.get(4));

            // Graph query result
            Assert.assertEquals(ResultSetImpl.class, results.get(5).getClass());
            resultSet = (ResultSet) results.get(5);

            Assert.assertNotNull(resultSet.getHeader());
            Header header = resultSet.getHeader();

            List<String> schemaNames = header.getSchemaNames();
            Assert.assertNotNull(schemaNames);
            Assert.assertEquals(1, schemaNames.size());
            Assert.assertEquals("n", schemaNames.get(0));

            Property<String> nameProperty = new Property<>("name", "a");

            Node expectedNode = new Node();
            expectedNode.setId(0);
            expectedNode.addLabel("Person");
            expectedNode.addProperty(nameProperty);
            // see that the result were pulled from the right graph
            Assert.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assert.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("n"), record.keys());
            Assert.assertEquals(expectedNode, record.getValue("n"));

            Assert.assertEquals(ResultSetImpl.class, results.get(6).getClass());
            resultSet = (ResultSet) results.get(6);

            Assert.assertNotNull(resultSet.getHeader());
            header = resultSet.getHeader();

            schemaNames = header.getSchemaNames();
            Assert.assertNotNull(schemaNames);
            Assert.assertEquals(1, schemaNames.size());
            Assert.assertEquals("label", schemaNames.get(0));

            Assert.assertEquals(1, resultSet.size());

            iterator = resultSet.iterator();
            Assert.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("label"), record.keys());
            Assert.assertEquals("Person", record.getValue("label"));
        }
    }

    /**
    * Tests the functionality of read-only queries in a graph pipeline.
    * 
    * This method verifies the behavior of various operations in a graph pipeline,
    * including setting values, creating nodes, executing read-only queries,
    * and calling procedures. It checks the correctness of the results returned
    * by these operations.
    * 
    * @return void This method does not return a value.
    * @throws Exception If any unexpected error occurs during the test execution.
    */
    @Test
    public void testReadOnlyQueries() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();

            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            pipeline.readOnlyQuery("MATCH (n:Person{name:'a'}) RETURN n");
            pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            // Redis set command
            Assert.assertEquals(String.class, results.get(0).getClass());
            Assert.assertEquals("OK", results.get(0));

            // Redis graph command
            Assert.assertEquals(ResultSetImpl.class, results.get(1).getClass());
            ResultSet resultSet = (ResultSet) results.get(1);
            Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

            Assert.assertEquals(ResultSetImpl.class, results.get(2).getClass());
            resultSet = (ResultSet) results.get(2);
            Assert.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assert.assertEquals(1, resultSet.getStatistics().propertiesSet());

            // Graph read-only query result
            Assert.assertEquals(ResultSetImpl.class, results.get(4).getClass());
            resultSet = (ResultSet) results.get(3);

            Assert.assertNotNull(resultSet.getHeader());
            Header header = resultSet.getHeader();

            List<String> schemaNames = header.getSchemaNames();
            Assert.assertNotNull(schemaNames);
            Assert.assertEquals(1, schemaNames.size());
            Assert.assertEquals("n", schemaNames.get(0));

            Property<String> nameProperty = new Property<>("name", "a");

            Node expectedNode = new Node();
            expectedNode.setId(0);
            expectedNode.addLabel("Person");
            expectedNode.addProperty(nameProperty);
            // see that the result were pulled from the right graph
            Assert.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assert.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("n"), record.keys());
            Assert.assertEquals(expectedNode, record.getValue("n"));

            Assert.assertEquals(ResultSetImpl.class, results.get(4).getClass());
            resultSet = (ResultSet) results.get(4);

            Assert.assertNotNull(resultSet.getHeader());
            header = resultSet.getHeader();

            schemaNames = header.getSchemaNames();
            Assert.assertNotNull(schemaNames);
            Assert.assertEquals(1, schemaNames.size());
            Assert.assertEquals("label", schemaNames.get(0));

            Assert.assertEquals(1, resultSet.size());

            iterator = resultSet.iterator();
            Assert.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("label"), record.keys());
            Assert.assertEquals("Person", record.getValue("label"));
        }
    }

    /**
    * Executes a series of graph operations and tests the waitReplicas functionality.
    * 
    * This test method performs the following steps:
    * 1. Creates a GraphContext
    * 2. Initializes a GraphPipeline
    * 3. Sets a key-value pair
    * 4. Creates two Person nodes
    * 5. Calls waitReplicas method
    * 6. Synchronizes the pipeline and retrieves results
    * 7. Asserts the expected result of waitReplicas
    * 
    * @throws Exception If an error occurs during graph operations
    * @return void This method doesn't return anything
    */
    @Test
    public void testWaitReplicas() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();
            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            pipeline.waitReplicas(0, 100L);
            List<Object> results = pipeline.syncAndReturnAll();
            Assert.assertEquals(0L, results.get(3));
        }
    }

    /**
    * Tests the graph copy functionality by creating a sample graph, copying it, and verifying the contents.
    * 
    * This method performs the following steps:
    * 1. Creates a sample graph with person nodes and relationships
    * 2. Copies the graph to a new graph named "social-copied"
    * 3. Compares the contents of the original and copied graphs
    * 4. Cleans up by deleting the copied graph
    * 
    * @throws Exception if any database operations or assertions fail
    */
    @Test
    public void testGraphCopy() {
        Iterator<Record> originalResultSetIterator;
        try (GraphContext c = api.getContext()) {
            // Create sample data and copy the graph
            GraphPipeline pipeline = c.pipelined();
            pipeline.query("CREATE (:person{name:'roi',age:32})-[:knows]->(:person{name:'amit',age:30})");
            pipeline.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            pipeline.copyGraph("social-copied");
            List<Object> results = pipeline.syncAndReturnAll();

            ResultSet originalResultSet = (ResultSet) results.get(1);
            originalResultSetIterator = originalResultSet.iterator();
        }

        GraphContextGenerator api2 = FalkorDB.driver().graph("social-copied");
        try {
            // Compare graph contents
            ResultSet copiedResultSet = api2.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            Iterator<Record> copiedResultSetIterator = copiedResultSet.iterator();
            while (originalResultSetIterator.hasNext()) {
                Assert.assertTrue(copiedResultSetIterator.hasNext());
                Assert.assertEquals(originalResultSetIterator.next(), copiedResultSetIterator.next());
            }
        } finally {
            // Cleanup
            api2.deleteGraph();
            api2.close();
        }
    }
}
