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
    * Sets up the FalkorDB graph API for the "social" graph before test execution.
    * This method is annotated with @Before, indicating it runs before each test method.
    * It initializes the 'api' field with a new FalkorDB graph driver instance for the "social" graph.
    */
    @Before
    public void createApi(){
        api = FalkorDB.driver().graph("social");
    }

    /**
    * Deletes the graph and closes the API connection.
    * This method is annotated with @After, indicating it should be executed after each test method.
    */
    @After
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    /**
    * Performs a multi-execution test on a GraphContext using various Redis and graph operations.
    * This method tests the functionality of GraphTransaction, including setting values,
    * executing graph queries, incrementing values, retrieving values, and calling procedures.
    * It also verifies the correctness of the results returned from these operations.
    * 
    * @throws Exception if any error occurs during the execution of the test
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
            Assert.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assert.assertFalse(iterator.hasNext());
            Assert.assertEquals(Arrays.asList("label"), record.keys());
            Assert.assertEquals("Person", record.getValue("label"));
        }
    }

    /**
    * Executes a test for writing a transaction with watch functionality.
    * 
    * This method tests the behavior of write transactions in a multi-context scenario
    * where one context is watching a specific label. It creates two graph contexts,
    * sets up a watch on one context, initiates a multi-transaction, and performs
    * write operations on both contexts. Finally, it verifies the transaction execution
    * and closes both contexts.
    * 
    * @throws Exception If an error occurs during the test execution
    */
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
    }

    /**
    * Tests the read transaction watch functionality in a multi-context scenario.
    * 
    * This method creates two separate graph contexts, performs various operations
    * including creating nodes, watching for changes, executing multi-transactions,
    * and querying the graph. It also verifies the behavior of these operations.
    * 
    * @return void
    * @throws AssertionError if any of the assertions fail
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

    /**
    * Tests the execution of multiple commands in a single transaction, including read-only queries.
    * This method verifies the behavior of GraphTransaction's multi-command execution,
    * including set operations, graph creation, read-only queries, and procedure calls.
    * 
    * @return void This method doesn't return a value
    *
    * @throws Exception If any error occurs during the execution of the test
    */
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
