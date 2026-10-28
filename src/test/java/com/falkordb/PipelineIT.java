package com.falkordb;

import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Property;
import com.falkordb.impl.resultset.ResultSetImpl;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

public class PipelineIT {

    private GraphContextGenerator api;

    @BeforeEach
    public void createApi() {
        api = TestServer.graph("social");
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
    public void testResponseGetAfterContextClosed() throws IOException {
        // A reply is decoded when it is read, after its context is closed here. Decoding needs the label and
        // the property key, but the context's connection is back in the pool. Lent out again and put in
        // pipeline mode, it makes Jedis refuse any stray command sent on it.
        try (Driver driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .poolMaxTotal(2)
                .poolMaxIdle(2)
                .build()) {
            GraphContextGenerator graph = driver.graph("social"); // its own, empty cache
            Response<ResultSet> reply;
            try (GraphContext c = graph.getContext()) {
                GraphPipeline pipeline = c.pipelined();
                reply = pipeline.query("CREATE (n:PostCloseLabel {k:'v'}) RETURN n");
                pipeline.sync();
            }
            try (Jedis lentOut = driver.getConnection()) { // the pool lends the connection returned last
                Pipeline busy = lentOut.pipelined();
                busy.get("pipeline-it-busy");

                Node node = (Node) reply.get().iterator().next().getValue(0);
                Assertions.assertEquals("PostCloseLabel", node.getLabel(0));
                Assertions.assertEquals("v", node.getProperty("k").getValue());
                busy.sync();
            }
        }
    }

    @Test
    public void testQueryFollowedByDeleteGraphInSamePipeline() {
        api.query("CREATE (:Person {name:'a'})");
        api.query("MATCH (n:Person) RETURN n"); // caches the label and the property key
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();
            pipeline.query("MATCH (n:Person) RETURN n");
            pipeline.deleteGraph();
            List<Object> results = pipeline.syncAndReturnAll();

            Node node = (Node) ((ResultSet) results.get(0)).iterator().next().getValue(0);
            Assertions.assertEquals("Person", node.getLabel(0));
            Assertions.assertEquals("a", node.getProperty("name").getValue());
        }

        // the same with sync(), reading the reply afterwards
        api.query("CREATE (:Person {name:'b'})");
        api.query("MATCH (n:Person) RETURN n");
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();
            Response<ResultSet> reply = pipeline.query("MATCH (n:Person) RETURN n");
            pipeline.deleteGraph();
            pipeline.sync();

            Node node = (Node) reply.get().iterator().next().getValue(0);
            Assertions.assertEquals("Person", node.getLabel(0));
            Assertions.assertEquals("b", node.getProperty("name").getValue());
        }

        // sync() ran the delete, so the cache was cleared: a recreated graph decodes with its own names
        api.query("CREATE (:Animal {kind:'cat'})");
        Node animal = (Node) api.query("MATCH (n) RETURN n").iterator().next().getValue(0);
        Assertions.assertEquals("Animal", animal.getLabel(0));
        Assertions.assertEquals("cat", animal.getProperty("kind").getValue());
    }

    @Test
    public void testAColdCacheReplyQueuedBeforeDeleteGraphIsReportedRatherThanMisread() {
        // The issue's example, with nothing cached: the reply before the delete can't be decoded any more.
        // Looking its label id up after the batch would find the recreated graph's label, so it is reported.
        api.query("CREATE (:Person {name:'a'})");
        GraphContextGenerator cold = TestServer.graph("social"); // its own, empty cache
        try (GraphContext c = cold.getContext()) {
            GraphPipeline pipeline = c.pipelined();
            pipeline.query("MATCH (n:Person) RETURN n");
            pipeline.deleteGraph();
            pipeline.query("CREATE (n:Animal {kind:'cat'}) RETURN n");
            List<Object> results = pipeline.syncAndReturnAll();

            Assertions.assertTrue(results.get(0) instanceof GraphException, String.valueOf(results.get(0)));
            Node animal = (Node) ((ResultSet) results.get(2)).iterator().next().getValue(0);
            Assertions.assertEquals("Animal", animal.getLabel(0));
        } finally {
            cold.close();
        }
    }

    @Test
    public void testReadingAReplyWhileATransactionIsOpenOnTheSameContext() throws IOException {
        api.query("CREATE (:Person {name:'a'})");
        // A driver of its own: a lookup sent on the connection by mistake would leave it out of step, and the
        // shared pool would hand that connection to the next test.
        try (Driver driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .build()) {
            GraphContextGenerator cold = driver.graph("social"); // its own, empty cache
            try (GraphContext c = cold.getContext()) {
                GraphPipeline pipeline = c.pipelined();
                Response<ResultSet> reply = pipeline.query("MATCH (n:Person) RETURN n");
                pipeline.sync();

                // The connection is inside MULTI, so the label can't be looked up on it.
                GraphTransaction transaction = c.multi();
                transaction.set("x", "1");
                Node node = (Node) reply.get().iterator().next().getValue(0);
                Assertions.assertEquals("Person", node.getLabel(0));
                Assertions.assertEquals("a", node.getProperty("name").getValue());

                Assertions.assertEquals(Collections.singletonList("OK"), transaction.exec());
            }
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
    public void testProfile() {
        try (GraphContext c = api.getContext()) {
            GraphPipeline pipeline = c.pipelined();
            pipeline.query("CREATE (:person{name:'alice',age:30})");
            pipeline.query("CREATE (:person{name:'bob',age:25})");
            pipeline.profile("MATCH (a:person) WHERE (a.name = 'alice') RETURN a.age");
            List<Object> results = pipeline.syncAndReturnAll();

            // Check that profile result is not null and has expected structure
            ResultSet profileResult = (ResultSet) results.get(2);
            Assertions.assertNotNull(profileResult);

            // Verify profile result contains execution plan operations
            Assertions.assertTrue(profileResult.size() > 0, "Profile result should contain execution plan operations");

            // Verify profile result has a header with columns
            Header header = profileResult.getHeader();
            Assertions.assertNotNull(header, "Profile result should have a header");
            Assertions.assertTrue(header.getSchemaNames().size() > 0, "Profile result header should have columns");

            // Verify profile result contains execution plan data
            Iterator<Record> iterator = profileResult.iterator();
            Assertions.assertTrue(iterator.hasNext(), "Profile result should have execution plan operations");
            Record record = iterator.next();
            Assertions.assertNotNull(record, "Profile result record should not be null");
            Assertions.assertTrue(record.size() > 0, "Profile result record should have values");

            // Verify profile result has statistics (execution metrics)
            Assertions.assertNotNull(profileResult.getStatistics(), "Profile result should have statistics");
        }
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

        GraphContextGenerator api2 = TestServer.graph("social-copied");
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
    public void testExplainInPipeline() {
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
            Assertions.assertTrue(results.get(0) instanceof List);
            @SuppressWarnings("unchecked")
            List<String> explainResult1 = (List<String>) results.get(0);
            Assertions.assertNotNull(explainResult1);
            Assertions.assertFalse(explainResult1.isEmpty());

            Assertions.assertTrue(results.get(1) instanceof List);
            @SuppressWarnings("unchecked")
            List<String> explainResult2 = (List<String>) results.get(1);
            Assertions.assertNotNull(explainResult2);
            Assertions.assertFalse(explainResult2.isEmpty());
        }
    }
}
