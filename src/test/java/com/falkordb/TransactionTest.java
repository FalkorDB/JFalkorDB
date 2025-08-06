package com.falkordb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import com.falkordb.impl.resultset.ResultSetImpl;

public class TransactionTest {

    private GraphContextGenerator api;

    public TransactionTest() {
    }

    @BeforeEach
    public void createApi(){
        api = FalkorDB.driver().graph("social");
    }

    @AfterEach
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

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
            Assertions.assertEquals(String.class, results.get(0).getClass());
            Assertions.assertEquals("OK", results.get(0));

            // Redis graph command
            Assertions.assertEquals(ResultSetImpl.class, results.get(1).getClass());
            ResultSet resultSet = (ResultSet) results.get(1);
            Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assertions.assertEquals(1, resultSet.getStatistics().propertiesSet());


            Assertions.assertEquals(ResultSetImpl.class, results.get(2).getClass());
            resultSet = (ResultSet) results.get(2);
            Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assertions.assertEquals(1, resultSet.getStatistics().propertiesSet());

            // Redis incr command
            Assertions.assertEquals(Long.class, results.get(3).getClass());
            Assertions.assertEquals(2L, results.get(3));

            // Redis get command
            Assertions.assertEquals(String.class, results.get(4).getClass());
            Assertions.assertEquals("2", results.get(4));

            // Graph query result
            Assertions.assertEquals(ResultSetImpl.class, results.get(5).getClass());
            resultSet = (ResultSet) results.get(5);

            Assertions.assertNotNull(resultSet.getHeader());
            Header header = resultSet.getHeader();


            List<String> schemaNames = header.getSchemaNames();
            Assertions.assertNotNull(schemaNames);
            Assertions.assertEquals(1, schemaNames.size());
            Assertions.assertEquals("n", schemaNames.get(0));

            Property<String> nameProperty = new Property<>("name", "a");

            Node expectedNode = new Node();
            expectedNode.setId(0);
            expectedNode.addLabel("Person");
            expectedNode.addProperty(nameProperty);
            // see that the result were pulled from the right graph
            Assertions.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assertions.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assertions.assertFalse(iterator.hasNext());
            Assertions.assertEquals(Arrays.asList("n"), record.keys());
            Assertions.assertEquals(expectedNode, record.getValue("n"));

            Assertions.assertEquals(ResultSetImpl.class, results.get(6).getClass());
            resultSet = (ResultSet) results.get(6);

            Assertions.assertNotNull(resultSet.getHeader());
            header = resultSet.getHeader();


            schemaNames = header.getSchemaNames();
            Assertions.assertNotNull(schemaNames);
            Assertions.assertEquals(1, schemaNames.size());
            Assertions.assertEquals("label", schemaNames.get(0));

            Assertions.assertEquals(1, resultSet.size());

            iterator = resultSet.iterator();
            Assertions.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assertions.assertFalse(iterator.hasNext());
            Assertions.assertEquals(Arrays.asList("label"), record.keys());
            Assertions.assertEquals("Person", record.getValue("label"));
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
        Assertions.assertNull(returnValue);
        c1.close();
        c2.close();
    }

    @Test
    public void testReadTransactionWatch(){

        GraphContext c1 = api.getContext();
        GraphContext c2 = api.getContext();
        Assertions.assertNotEquals(c1, c2);
        c1.query("CREATE (:Person {name:'a'})");
        c1.watch("social");
        GraphTransaction t1 = c1.multi();

        Map<String, Object> params = new HashMap<>();
        params.put("name", 'b');
        t1.query("CREATE (:Person {name:$name})", params);
        c2.query("MATCH (n) return n");
        List<Object> returnValue = t1.exec();

        Assertions.assertNotNull(returnValue);
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
            Assertions.assertEquals(String.class, results.get(0).getClass());
            Assertions.assertEquals("OK", results.get(0));

            // Redis graph command
            Assertions.assertEquals(ResultSetImpl.class, results.get(1).getClass());
            ResultSet resultSet = (ResultSet) results.get(1);
            Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assertions.assertEquals(1, resultSet.getStatistics().propertiesSet());


            Assertions.assertEquals(ResultSetImpl.class, results.get(2).getClass());
            resultSet = (ResultSet) results.get(2);
            Assertions.assertEquals(1, resultSet.getStatistics().nodesCreated());
            Assertions.assertEquals(1, resultSet.getStatistics().propertiesSet());

            Assertions.assertEquals(ResultSetImpl.class, results.get(4).getClass());

            // Graph read-only query result
            resultSet = (ResultSet) results.get(3);

            Assertions.assertNotNull(resultSet.getHeader());
            Header header = resultSet.getHeader();

            List<String> schemaNames = header.getSchemaNames();
            Assertions.assertNotNull(schemaNames);
            Assertions.assertEquals(1, schemaNames.size());
            Assertions.assertEquals("n", schemaNames.get(0));

            Property<String> nameProperty = new Property<>("name", "a");

            Node expectedNode = new Node();
            expectedNode.setId(0);
            expectedNode.addLabel("Person");
            expectedNode.addProperty(nameProperty);
            // see that the result were pulled from the right graph
            Assertions.assertEquals(1, resultSet.size());

            Iterator<Record> iterator = resultSet.iterator();
            Assertions.assertTrue(iterator.hasNext());
            Record record = iterator.next();
            Assertions.assertFalse(iterator.hasNext());
            Assertions.assertEquals(Arrays.asList("n"), record.keys());
            Assertions.assertEquals(expectedNode, record.getValue("n"));

            Assertions.assertEquals(ResultSetImpl.class, results.get(4).getClass());
            resultSet = (ResultSet) results.get(4);

            Assertions.assertNotNull(resultSet.getHeader());
            header = resultSet.getHeader();

            schemaNames = header.getSchemaNames();
            Assertions.assertNotNull(schemaNames);
            Assertions.assertEquals(1, schemaNames.size());
            Assertions.assertEquals("label", schemaNames.get(0));

            Assertions.assertEquals(1, resultSet.size());

            iterator = resultSet.iterator();
            Assertions.assertTrue(iterator.hasNext());
            record = iterator.next();
            Assertions.assertFalse(iterator.hasNext());
            Assertions.assertEquals(Arrays.asList("label"), record.keys());
            Assertions.assertEquals("Person", record.getValue("label"));
        }
    }

    @Test
    public void testProfile(){
        try (GraphContext c = api.getContext()) {
            GraphTransaction transaction = c.multi();

            transaction.query("CREATE (:Person {name:'alice'})");
            transaction.profile("MATCH (n:Person{name:'alice'}) RETURN n");
            List<Object> results = transaction.exec();

            // Profile result
            Assertions.assertEquals(ResultSetImpl.class, results.get(1).getClass());
            ResultSet profileResult = (ResultSet) results.get(1);
            Assertions.assertNotNull(profileResult);
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
                Assertions.assertTrue(copiedResultSetIterator.hasNext());
                Assertions.assertEquals(originalResultSetIterator.next(), copiedResultSetIterator.next());
            }
        } finally {
            // Cleanup
            api2.deleteGraph();
            api2.close();
        }
    } */
}
