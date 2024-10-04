package com.falkordb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import com.falkordb.impl.resultset.ResultSetImpl;

public class TransactionTest {

    private GraphContextGenerator api;

    public TransactionTest() {
    }

    /**
     * Creates a FalkorDB graph API instance for the "social" graph before test execution.
     * 
     * This method is annotated with @Before, indicating it runs before each test method.
     * It initializes the 'api' field with a new FalkorDB graph driver instance for the "social" graph.
     * 
     * @return void
     */
    @Before
    public void createApi(){
        api = FalkorDB.driver().graph("social");
    }

    /**
     * Cleans up resources by deleting the graph and closing the API connection after test execution.
     * 
     * This method is annotated with @After, indicating it will be executed after each test method.
     * 
     * @throws <UNKNOWN> if there's an error during graph deletion or API closure
     */
    @After
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    /**
    * Tests the multi-execution functionality of a GraphTransaction.
    * 
    * This test method performs multiple operations in a single transaction,
    * including Redis commands and graph queries, and verifies the results.
    * 
    * @return void
    */
    @Test
    public void testMultiExec(){
        try (GraphContext c = api.getContext()) {
            GraphTransaction transaction = c.multi();

            transaction.set("x", "1");
            transaction.query("CREATE (:Person {name:'a'})");
            transaction.query("CREATE (:Person {name:'b'})");
            transaction.incr("x");
            transaction.get("x");
            transaction.query("MATCH (n:Person{name:'a'}) RETURN n");
            transaction.callProcedure("db.labels");
            List<Object> results = transaction.exec();

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
            ```
            /**
             * Tests the behavior of a write transaction with watch functionality.
             * 
             * This test method verifies the following:
             * 1. Creation of multiple graph contexts
             * 2. Setting up a watch on a specific label
             * 3. Executing a multi-transaction query
             * 4. Concurrent query execution in different contexts
             * 5. Verification of transaction execution result
             * 6. Proper closing of graph contexts
             * 
             * @return void This method doesn't return anything
             */
            ```
            Assert.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("label"), record.keys());
            Assert.assertEquals("Person", record.getValue("label"));
        }
    }

    @Test
    public void testWriteTransactionWatch(){

        GraphContext c1 = api.getContext();
        GraphContext c2 = api.getContext();

        c1.watch("social");
        GraphTransaction t1 = c1.multi();


        t1.query("CREATE (:Person {name:'a'})");
        c2.query("CREATE (:Person {name:'b'})");
        List<Object> returnValue = t1.exec();
        Assert.assertNull(returnValue);
        c1.close();
        c2.close();
    /**
    * Tests multiple executions with read-only queries in a graph transaction.
    * 
    * This test method performs the following operations:
    * 1. Creates a multi-transaction in a GraphContext.
    * 2. Sets a key-value pair.
    * 3. Executes two CREATE queries to add Person nodes.
    * 4. Executes a read-only query to match a Person node.
    * 5. Calls a procedure to get database labels.
    * 6. Executes the transaction and validates the results.
    * 
    * @return void This method doesn't return anything.
    */
    }

    /**
    * Tests the read transaction watch functionality in a multi-context environment.
    * 
    * @param None This method doesn't take any parameters as it's a JUnit test method.
    * @return void This method doesn't return anything as it's a test method.
    */
    @Test
    public void testReadTransactionWatch(){

        GraphContext c1 = api.getContext();
        GraphContext c2 = api.getContext();
        Assert.assertNotEquals(c1, c2);
        c1.query("CREATE (:Person {name:'a'})");
        c1.watch("social");
        GraphTransaction t1 = c1.multi();

        Map<String, Object> params = new HashMap<>();
        params.put("name", 'b');
        t1.query("CREATE (:Person {name:$name})", params);
        c2.query("MATCH (n) return n");
        List<Object> returnValue = t1.exec();

        Assert.assertNotNull(returnValue);
        c1.close();
        c2.close();
    }

    @Test
    public void testMultiExecWithReadOnlyQueries(){
        try (GraphContext c = api.getContext()) {
            GraphTransaction transaction = c.multi();

            transaction.set("x", "1");
            transaction.query("CREATE (:Person {name:'a'})");
            transaction.query("CREATE (:Person {name:'b'})");
            transaction.readOnlyQuery("MATCH (n:Person{name:'a'}) RETURN n");
            transaction.callProcedure("db.labels");
            List<Object> results = transaction.exec();

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

            Assert.assertEquals(ResultSetImpl.class, results.get(4).getClass());

            // Graph read-only query result
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

    // Disabled due to bug in FalkorDB caused by using transactions in conjunction with graph copy
    /* @Test
    public void testGraphCopy() {
        Iterator<Record> originalResultSetIterator;
        try (GraphContext c = api.getContext()) {
            // Create sample data and copy the graph
            GraphTransaction transaction = c.multi();
            transaction.query("CREATE (:person{name:'roi',age:32})-[:knows]->(:person{name:'amit',age:30})");
            transaction.query("MATCH (p:person)-[rel:knows]->(p2:person) RETURN p,rel,p2");
            transaction.copyGraph("social-copied");
            List<Object> results = transaction.exec();

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
    } */
}
