package com.falkordb;

import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link GraphContext.Pipeline} against a FalkorDB server: what {@link PipelineIT} checks for the deprecated one. */
public class ContextPipelineIT {

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
    public void graphAndOtherCommandsMixInOneBatch() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            Reply<Object> set = pipeline.sendCommand("SET", "x", "1");
            Reply<ResultSet> a = pipeline.query("CREATE (:Person {name:'a'})");
            pipeline.query("CREATE (:Person {name:'b'})");
            Reply<Object> incr = pipeline.sendCommand("INCR", "x");
            Reply<Object> get = pipeline.sendCommand("GET", "x");
            Reply<Object> missing = pipeline.sendCommand("GET", "pipeline-it-missing");
            Reply<ResultSet> match = pipeline.query("MATCH (n:Person{name:'a'}) RETURN n");
            Reply<ResultSet> labels = pipeline.callProcedure("db.labels");
            List<Object> results = pipeline.syncAndReturnAll();

            Assertions.assertEquals(8, results.size());
            Assertions.assertEquals("OK", set.get());
            Assertions.assertSame(set.get(), results.get(0));
            Assertions.assertEquals(1, a.get().getStatistics().nodesCreated());
            Assertions.assertSame(a.get(), results.get(1));
            Assertions.assertEquals(2L, incr.get());
            Assertions.assertEquals("2", get.get());
            Assertions.assertNull(missing.get());
            Assertions.assertNull(results.get(5));
            Node node = match.get().iterator().next().getValue("n", Node.class);
            Assertions.assertEquals("Person", node.getLabel(0));
            Assertions.assertEquals("a", node.getProperty("name").getValue());
            Assertions.assertEquals("Person", labels.get().iterator().next().getString("label"));
        }
    }

    @Test
    public void aServerErrorIsAGraphExceptionAndTheOtherRepliesAreRead() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            pipeline.sendCommand("SET", "x", "not a number");
            Reply<Object> incr = pipeline.sendCommand("INCR", "x");
            Reply<ResultSet> bad = pipeline.query("THIS IS NOT CYPHER");
            Reply<ResultSet> good = pipeline.query("CREATE (:Person)");
            List<Object> results = pipeline.syncAndReturnAll();

            GraphException notANumber = Assertions.assertThrows(GraphException.class, incr::get);
            Assertions.assertSame(notANumber, results.get(1));
            Assertions.assertSame(Assertions.assertThrows(GraphException.class, bad::get), results.get(2));
            Assertions.assertEquals(1, good.get().getStatistics().nodesCreated());
        }
    }

    @Test
    public void binaryValuesRoundTrip() {
        byte[] value = {(byte) 0xff, 0, (byte) 0xc3, 'x'}; // not UTF-8
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            pipeline.sendBinaryCommand("SET", bytes("pipeline-it-binary"), value);
            Reply<Object> get = pipeline.sendBinaryCommand("GET", bytes("pipeline-it-binary"));
            pipeline.sendCommand("DEL", "pipeline-it-binary");
            pipeline.query("CREATE ()"); // for deleteGraph() in tearDown
            pipeline.sync();

            Assertions.assertArrayEquals(value, (byte[]) get.get());
        }
    }

    @Test
    public void aReplyCanBeReadAfterItsContextIsClosed() {
        try (Driver driver = poolOf(2)) {
            GraphContextGenerator graph = driver.graph("social"); // its own, empty cache
            Reply<ResultSet> reply;
            try (GraphContext c = graph.getContext()) {
                GraphContext.Pipeline pipeline = c.pipeline();
                reply = pipeline.query("CREATE (n:PostCloseLabel {k:'v'}) RETURN n");
                pipeline.sync();
            }
            // decoding needs the label and the property key, which it looks up on a connection of its own
            Node node = reply.get().iterator().next().getValue(0, Node.class);
            Assertions.assertEquals("PostCloseLabel", node.getLabel(0));
            Assertions.assertEquals("v", node.getProperty("k").getValue());
        }
    }

    @Test
    public void closingAContextWithAnUnreadPipelineReturnsACleanConnection() {
        try (Driver driver = poolOf(1)) {
            GraphContextGenerator graph = driver.graph("social");
            Reply<ResultSet> created;
            try (GraphContext c = graph.getContext()) {
                created = c.pipeline().query("CREATE (:Person {name:'unsynced'})");
            } // closed without sync()

            // The pool's only connection again: its query must read its own reply, not the pipeline's.
            try (GraphContext c = graph.getContext()) {
                ResultSet resultSet = c.query("MATCH (n:Person) RETURN n.name");
                Assertions.assertEquals(1, resultSet.size());
                Assertions.assertEquals("unsynced", resultSet.iterator().next().getString(0));
            }
            Assertions.assertEquals(1, created.get().getStatistics().nodesCreated());
        }
    }

    @Test
    public void aQueryBeforeADeleteIsDecodedWithTheGraphItCameFrom() {
        api.query("CREATE (:Person {name:'a'})");
        api.query("MATCH (n:Person) RETURN n"); // caches the label and the property key
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            Reply<ResultSet> before = pipeline.query("MATCH (n:Person) RETURN n");
            Reply<String> deleted = pipeline.deleteGraph();
            Reply<ResultSet> after = pipeline.query("CREATE (n:Animal {kind:'cat'}) RETURN n");
            pipeline.sync();

            Node person = before.get().iterator().next().getValue(0, Node.class);
            Assertions.assertEquals("Person", person.getLabel(0));
            Assertions.assertEquals("a", person.getProperty("name").getValue());
            Assertions.assertNotNull(deleted.get());
            Node animal = after.get().iterator().next().getValue(0, Node.class);
            Assertions.assertEquals("Animal", animal.getLabel(0));
            Assertions.assertEquals("cat", animal.getProperty("kind").getValue());
        }
    }

    @Test
    public void aColdCacheReplyQueuedBeforeADeleteIsDecodedOrReportedRatherThanMisread() {
        // FalkorDB 6.2+ sends the reply's names with it, so it decodes as the graph was; with an older server the
        // id can only be looked up in the recreated graph, so the reply is reported.
        api.query("CREATE (:Person {name:'a'})");
        GraphContextGenerator cold = TestServer.graph("social"); // its own, empty cache
        try (GraphContext c = cold.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            Reply<ResultSet> before = pipeline.query("MATCH (n:Person) RETURN n");
            pipeline.deleteGraph();
            Reply<ResultSet> after = pipeline.query("CREATE (n:Animal {kind:'cat'}) RETURN n");
            List<Object> results = pipeline.syncAndReturnAll();

            if (TestServer.sendsSchemaHash()) {
                Node person = before.get().iterator().next().getValue(0, Node.class);
                Assertions.assertEquals("Person", person.getLabel(0));
                Assertions.assertSame(before.get(), results.get(0));
            } else {
                GraphException e = Assertions.assertThrows(GraphException.class, before::get);
                Assertions.assertSame(e, results.get(0));
                Assertions.assertSame(e, Assertions.assertThrows(GraphException.class, before::get));
            }
            Node animal = after.get().iterator().next().getValue(0, Node.class);
            Assertions.assertEquals("Animal", animal.getLabel(0));
        } finally {
            cold.close();
        }
    }

    @Test
    public void aReplyIsReadWhileATransactionIsOpenOnTheSameContext() {
        api.query("CREATE (:Person {name:'a'})");
        try (Driver driver = poolOf(8)) {
            GraphContextGenerator cold = driver.graph("social"); // its own, empty cache
            try (GraphContext c = cold.getContext()) {
                GraphContext.Pipeline pipeline = c.pipeline();
                Reply<ResultSet> reply = pipeline.query("MATCH (n:Person) RETURN n");
                pipeline.sync();

                // The connection is inside MULTI, so the label is looked up on another one.
                GraphContext.Transaction transaction = c.transaction();
                transaction.sendCommand("SET", "x", "1");
                Node node = reply.get().iterator().next().getValue(0, Node.class);
                Assertions.assertEquals("Person", node.getLabel(0));
                Assertions.assertEquals(Collections.singletonList("OK"), transaction.exec());
            }
        }
    }

    @Test
    public void anInvalidProcedureCallQueuesNothing() {
        api.query("CREATE (:Person)");
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            pipeline.callProcedure("db.labels");
            Assertions.assertThrows(
                    IllegalArgumentException.class, () -> pipeline.callProcedure("db.labels; MATCH (n) DELETE n"));
            pipeline.query("MATCH (n) RETURN count(n)");

            List<Object> results = pipeline.syncAndReturnAll();
            Assertions.assertEquals(2, results.size());
            Long nodes = ((ResultSet) results.get(1)).iterator().next().getValue(0, Long.class);
            Assertions.assertEquals(1L, nodes.longValue());
        }
    }

    @Test
    public void readOnlyQueriesWithAndWithoutParametersAndTimeouts() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            pipeline.query("CREATE (:Person {name:'a'})");
            Map<String, Object> params = new HashMap<>();
            params.put("name", "a");
            Reply<ResultSet> plain = pipeline.readOnlyQuery("MATCH (n:Person) RETURN n.name");
            Reply<ResultSet> withParams = pipeline.readOnlyQuery("MATCH (n:Person{name:$name}) RETURN n.name", params);
            Reply<ResultSet> withTimeout = pipeline.readOnlyQuery("MATCH (n:Person) RETURN n.name", 1000);
            Reply<ResultSet> withBoth =
                    pipeline.readOnlyQuery("MATCH (n:Person{name:$name}) RETURN n.name", params, 1000);
            Reply<ResultSet> write = pipeline.readOnlyQuery("CREATE (:Person)");
            pipeline.sync();

            for (Reply<ResultSet> reply : Arrays.asList(plain, withParams, withTimeout, withBoth)) {
                Assertions.assertEquals("a", reply.get().iterator().next().getString(0));
            }
            Assertions.assertThrows(GraphException.class, write::get); // a write in a read-only query
        }
    }

    @Test
    public void profile() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            pipeline.query("CREATE (:person{name:'alice',age:30})");
            Reply<ResultSet> profile = pipeline.profile("MATCH (a:person) WHERE (a.name = 'alice') RETURN a.age");
            Map<String, Object> params = new HashMap<>();
            params.put("name", "alice");
            Reply<ResultSet> withParams = pipeline.profile("MATCH (a:person) WHERE (a.name = $name) RETURN a", params);
            pipeline.sync();

            for (Reply<ResultSet> reply : Arrays.asList(profile, withParams)) {
                ResultSet plan = reply.get();
                Assertions.assertTrue(plan.size() > 0, "a profile lists the plan's operations");
                Iterator<Record> operations = plan.iterator();
                Assertions.assertTrue(operations.next().size() > 0);
            }
        }
    }

    @Test
    public void explain() {
        try (GraphContext c = api.getContext()) {
            c.query("CREATE (:Person {name:'Bob'})");
            GraphContext.Pipeline pipeline = c.pipeline();
            Reply<List<String>> plan = pipeline.explain("MATCH (p:Person) RETURN p");
            Map<String, Object> params = new HashMap<>();
            params.put("name", "Bob");
            Reply<List<String>> withParams = pipeline.explain("MATCH (p:Person) WHERE p.name = $name RETURN p", params);
            Reply<List<String>> bad = pipeline.explain("THIS IS NOT CYPHER");
            pipeline.sync();

            Assertions.assertFalse(plan.get().isEmpty());
            Assertions.assertFalse(withParams.get().isEmpty());
            Assertions.assertThrows(GraphException.class, bad::get);
        }
    }

    @Test
    public void copyGraph() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            pipeline.query("CREATE (:person{name:'roi',age:32})-[:knows]->(:person{name:'amit',age:30})");
            Reply<String> copied = pipeline.copyGraph("social-copied-ctx");
            pipeline.sync();
            Assertions.assertNotNull(copied.get());
        }
        GraphContextGenerator copy = TestServer.graph("social-copied-ctx");
        try {
            ResultSet edges = copy.query("MATCH (p:person)-[:knows]->(p2:person) RETURN p.name, p2.name");
            Record record = edges.iterator().next();
            Assertions.assertEquals("roi", record.getString(0));
            Assertions.assertEquals("amit", record.getString(1));
        } finally {
            copy.deleteGraph();
            copy.close();
        }
    }

    @Test
    public void waitReplicasRepliesWithTheReplicasReached() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            pipeline.query("CREATE ()");
            Reply<Long> reached = pipeline.waitReplicas(0, 100);
            pipeline.sync();
            Assertions.assertTrue(reached.get() >= 0);
        }
    }

    @Test
    public void refusedCommandsAreNotSent() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Pipeline pipeline = c.pipeline();
            Assertions.assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand("SELECT", "1"));
            Assertions.assertThrows(IllegalArgumentException.class, () -> pipeline.sendCommand("DEL", "social"));
            Assertions.assertThrows(
                    IllegalArgumentException.class, () -> pipeline.sendCommand("GRAPH.QUERY", "social", "CREATE ()"));
            Reply<ResultSet> created = pipeline.query("CREATE (:Person)");
            pipeline.sync();
            Assertions.assertEquals(1, created.get().getStatistics().nodesCreated());
            // still on database 0, with the graph in place
            Assertions.assertEquals(
                    1L, c.query("MATCH (n) RETURN count(n)").iterator().next().getValue(0, Long.class));
        }
    }

    private static Driver poolOf(int connections) {
        return FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .poolMaxTotal(connections)
                .poolMaxIdle(connections)
                .poolMaxWait(Duration.ofSeconds(5))
                .build();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
