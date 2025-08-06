package com.falkordb;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import com.falkordb.impl.resultset.ResultSetImpl;

public class PipelineTest {

    private GraphContextGenerator api;

    @BeforeEach
    public void createApi() {
        api = FalkorDB.driver().graph("social");

    }

    @AfterEach
    public void deleteGraph() {
        api.deleteGraph();
        api.close();
    }

    @Test
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
    public void testReadOnlyQueries() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();

            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            pipeline.readOnlyQuery("MATCH (n:Person{name:'a'}) RETURN n");
            pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            verifyReadOnlyQueryResults(results);
        }
    }

    @Test
    public void testReadOnlyQueriesWithParameters() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();

            pipeline.set("x", "1");
            pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            Map<String, Object> params = new HashMap<>();
            params.put("name", "a");
            pipeline.readOnlyQuery("MATCH (n:Person{name:$name}) RETURN n", params);
            pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            verifyReadOnlyQueryResults(results);
        }
    }

    protected void verifyReadOnlyQueryResults(List<Object> results) {
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

        // Graph read-only query result
        Assertions.assertEquals(ResultSetImpl.class, results.get(3).getClass());
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
                Assertions.assertTrue(copiedResultSetIterator.hasNext());
                Assertions.assertEquals(originalResultSetIterator.next(), copiedResultSetIterator.next());
            }
        } finally {
            // Cleanup
            api2.deleteGraph();
            api2.close();
        }
    }

    @Test
    public void testExplainInPipeline(){
        try (GraphContext c = api.getContext()) {
            // Create some test data first
            c.query("CREATE (:Person {name:'Bob'})");
            
            GraphPipeline pipeline = c.pipelined();
            pipeline.explain("MATCH (p:Person) RETURN p");
            
            Map<String, Object> params = new HashMap<>();
            params.put("name", "Bob");
            pipeline.explain("MATCH (p:Person) WHERE p.name = $name RETURN p", params);
            
            List<Object> results = pipeline.syncAndReturnAll();
            
            // Check explain results
            Assertions.assertEquals(List.class, results.get(0).getClass());
            @SuppressWarnings("unchecked")
            List<String> explainResult1 = (List<String>) results.get(0);
            Assertions.assertNotNull(explainResult1);
            Assertions.assertFalse(explainResult1.isEmpty());
            
            Assertions.assertEquals(List.class, results.get(1).getClass());
            @SuppressWarnings("unchecked")
            List<String> explainResult2 = (List<String>) results.get(1);
            Assertions.assertNotNull(explainResult2);
            Assertions.assertFalse(explainResult2.isEmpty());
        }
    }
}
