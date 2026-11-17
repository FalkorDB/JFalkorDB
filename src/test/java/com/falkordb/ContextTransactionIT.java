package com.falkordb;

import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link GraphContext.Transaction} against a FalkorDB server: what {@link TransactionIT} checks for the deprecated
 * one.
 */
public class ContextTransactionIT {

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
    public void graphAndOtherCommandsMixInOneTransaction() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Transaction transaction = c.transaction();
            Reply<Object> set = transaction.sendCommand("SET", "x", "1");
            Reply<ResultSet> a = transaction.query("CREATE (:Person {name:'a'})");
            Reply<Object> incr = transaction.sendCommand("INCR", "x");
            Reply<Object> get = transaction.sendCommand("GET", "x");
            Reply<ResultSet> match = transaction.query("MATCH (n:Person{name:'a'}) RETURN n");
            Reply<ResultSet> labels = transaction.callProcedure("db.labels");
            List<Object> results = transaction.exec();

            Assertions.assertEquals(6, results.size());
            Assertions.assertEquals("OK", set.get());
            Assertions.assertEquals(1, a.get().getStatistics().nodesCreated());
            Assertions.assertSame(a.get(), results.get(1));
            Assertions.assertEquals(2L, incr.get());
            Assertions.assertEquals("2", get.get());
            Node node = match.get().iterator().next().getValue("n", Node.class);
            Assertions.assertEquals("Person", node.getLabel(0));
            Assertions.assertEquals("Person", labels.get().iterator().next().getString("label"));
            Assertions.assertThrows(UnsupportedOperationException.class, () -> results.add("x"));
        }
    }

    @Test
    public void aWatchedKeyThatChangedAbortsTheTransaction() {
        api.query("CREATE (:Person {name:'a'})");
        try (GraphContext c1 = api.getContext();
                GraphContext c2 = api.getContext()) {
            c1.watch("social");
            GraphContext.Transaction transaction = c1.transaction();
            Reply<ResultSet> created = transaction.query("CREATE (:Person {name:'b'})");
            c2.query("CREATE (:Person {name:'c'})"); // changes the watched key

            Assertions.assertNull(transaction.exec());
            IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, created::get);
            Assertions.assertTrue(e.getMessage().contains("aborted"), e.getMessage());
            Assertions.assertThrows(IllegalStateException.class, () -> transaction.query("CREATE ()"));
            Long people = c1.query("MATCH (n:Person) RETURN count(n)")
                    .iterator()
                    .next()
                    .getValue(0, Long.class);
            Assertions.assertEquals(2L, people.longValue(), "the aborted transaction created nothing");
        }
    }

    @Test
    public void aWatchedKeyThatWasOnlyReadDoesntAbortTheTransaction() {
        api.query("CREATE (:Person {name:'a'})");
        try (GraphContext c1 = api.getContext();
                GraphContext c2 = api.getContext()) {
            c1.watch("social");
            GraphContext.Transaction transaction = c1.transaction();
            Map<String, Object> params = new HashMap<>();
            params.put("name", "b");
            Reply<ResultSet> created = transaction.query("CREATE (:Person {name:$name})", params);
            c2.query("MATCH (n) RETURN n");

            Assertions.assertNotNull(transaction.exec());
            Assertions.assertEquals(1, created.get().getStatistics().nodesCreated());
        }
    }

    @Test
    public void aQueryThatFailsWhileItRunsRollsNothingBack() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Transaction transaction = c.transaction();
            Reply<ResultSet> before = transaction.query("CREATE (:Person {name:'before'})");
            Reply<ResultSet> failing = transaction.query("MATCH (n:Person) RETURN n.name, 1 / 0"); // fails when run
            Reply<ResultSet> after = transaction.query("CREATE (:Person {name:'after'})");
            List<Object> results = transaction.exec();

            Assertions.assertNotNull(results, "a query that fails while it runs doesn't abort the transaction");
            GraphException e = Assertions.assertThrows(GraphException.class, failing::get);
            Assertions.assertSame(e, results.get(1));
            Assertions.assertEquals(1, before.get().getStatistics().nodesCreated());
            Assertions.assertEquals(1, after.get().getStatistics().nodesCreated());
            ResultSet names = c.query("MATCH (n:Person) RETURN n.name ORDER BY n.name");
            Assertions.assertEquals(2, names.size(), "the writes around the failed query stay applied");
        }
    }

    @Test
    public void aCommandThatFailsToQueueRefusesTheTransaction() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Transaction transaction = c.transaction();
            Reply<ResultSet> created = transaction.query("CREATE (:Person)");
            transaction.sendCommand("SET", "only-a-key"); // wrong number of arguments: refused when queued

            GraphException e = Assertions.assertThrows(GraphException.class, transaction::exec);
            Assertions.assertTrue(e.getMessage().startsWith("EXECABORT"), e.getMessage());
            IllegalStateException unread = Assertions.assertThrows(IllegalStateException.class, created::get);
            Assertions.assertTrue(unread.getMessage().contains("refused"), unread.getMessage());
            Long nodes = c.query("MATCH (n) RETURN count(n)").iterator().next().getValue(0, Long.class);
            Assertions.assertEquals(0L, nodes.longValue(), "nothing ran");
        }
    }

    @Test
    public void aCommandThatFailsWhenItRunsIsAGraphExceptionInTheList() {
        try (GraphContext c = api.getContext()) {
            GraphContext.Transaction transaction = c.transaction();
            transaction.sendCommand("SET", "x", "not a number");
            Reply<Object> incr = transaction.sendCommand("INCR", "x");
            Reply<ResultSet> created = transaction.query("CREATE (:Person)");
            List<Object> results = transaction.exec();

            GraphException e = Assertions.assertThrows(GraphException.class, incr::get);
            Assertions.assertSame(e, results.get(1));
            Assertions.assertEquals(1, created.get().getStatistics().nodesCreated());
        }
    }

    @Test
    public void aQueryBeforeADeleteIsDecodedWithTheGraphItCameFrom() {
        api.query("CREATE (:Person {name:'a'})");
        api.query("MATCH (n:Person) RETURN n"); // caches the label and the property key
        try (GraphContext c = api.getContext()) {
            GraphContext.Transaction transaction = c.transaction();
            Reply<ResultSet> before = transaction.query("MATCH (n:Person) RETURN n");
            Reply<String> deleted = transaction.deleteGraph();
            Reply<ResultSet> after = transaction.query("CREATE (n:Animal {title:'b'}) RETURN n");
            transaction.exec();

            Node person = before.get().iterator().next().getValue(0, Node.class);
            Assertions.assertEquals("Person", person.getLabel(0));
            Assertions.assertEquals("a", person.getProperty("name").getValue());
            Assertions.assertNotNull(deleted.get());
            Node animal = after.get().iterator().next().getValue(0, Node.class);
            Assertions.assertEquals("Animal", animal.getLabel(0));
            Assertions.assertEquals("b", animal.getProperty("title").getValue());
        }
        // the delete cleared the cache, so the recreated graph decodes with its own names
        Node animal = api.query("MATCH (n) RETURN n").iterator().next().getValue(0, Node.class);
        Assertions.assertEquals("Animal", animal.getLabel(0));
    }

    @Test
    public void aDiscardedDeleteDoesntClearTheCache() {
        api.query("CREATE (:Person {name:'a'})");
        GraphContextGenerator graph = TestServer.graph("social"); // its own cache
        try {
            graph.query("MATCH (n:Person) RETURN n"); // caches the label and the property key
            try (GraphContext c = graph.getContext()) {
                GraphContext.Transaction transaction = c.transaction();
                Reply<String> deleted = transaction.deleteGraph();
                transaction.discard();
                Assertions.assertThrows(IllegalStateException.class, deleted::get);

                GraphContext.Pipeline pipeline = c.pipeline();
                Reply<ResultSet> reply = pipeline.query("MATCH (n:Person) RETURN n");
                pipeline.sync();
                api.deleteGraph(); // nothing left to look the names up in: only the cache has them
                Node node = reply.get().iterator().next().getValue(0, Node.class);
                Assertions.assertEquals("Person", node.getLabel(0));
            }
        } finally {
            graph.close();
        }
        api.query("CREATE (:Person)"); // for deleteGraph() in tearDown
    }

    @Test
    public void aReplyCanBeReadAfterItsContextIsClosed() {
        try (Driver driver = poolOfOne()) {
            GraphContextGenerator graph = driver.graph("social"); // its own, empty cache
            Reply<ResultSet> reply;
            try (GraphContext c = graph.getContext()) {
                GraphContext.Transaction transaction = c.transaction();
                reply = transaction.query("CREATE (n:TxLabel {k:'v'}) RETURN n");
                transaction.exec();
            }
            Node node = reply.get().iterator().next().getValue(0, Node.class);
            Assertions.assertEquals("TxLabel", node.getLabel(0));
            Assertions.assertEquals("v", node.getProperty("k").getValue());
        }
    }

    @Test
    public void closingAContextMidTransactionReturnsACleanConnection() {
        api.query("CREATE (:Person {name:'kept'})");
        try (Driver driver = poolOfOne()) {
            GraphContextGenerator graph = driver.graph("social");
            Reply<ResultSet> discarded;
            try (GraphContext c = graph.getContext()) {
                discarded = c.transaction().query("CREATE (:Person {name:'discarded'})");
            } // closed without exec() or discard()

            try (GraphContext c = graph.getContext()) {
                ResultSet resultSet = c.query("MATCH (n:Person) RETURN n.name");
                Assertions.assertEquals(1, resultSet.size());
                Assertions.assertEquals("kept", resultSet.iterator().next().getString(0));
            }
            Assertions.assertThrows(IllegalStateException.class, discarded::get);
        }
    }

    @Test
    public void closingATransactionDiscardsIt() {
        try (GraphContext c = api.getContext()) {
            try (GraphContext.Transaction transaction = c.transaction()) {
                transaction.query("CREATE (:Person {name:'discarded'})");
            }
            Long nodes = c.query("MATCH (n) RETURN count(n)").iterator().next().getValue(0, Long.class);
            Assertions.assertEquals(0L, nodes.longValue());
        }
        api.query("CREATE (:Person)"); // for deleteGraph() in tearDown
    }

    @Test
    public void readOnlyQueriesProfileAndExplain() {
        api.query("CREATE (:Person {name:'a'})");
        try (GraphContext c = api.getContext()) {
            GraphContext.Transaction transaction = c.transaction();
            Map<String, Object> params = new HashMap<>();
            params.put("name", "a");
            Reply<ResultSet> plain = transaction.readOnlyQuery("MATCH (n:Person) RETURN n.name");
            Reply<ResultSet> withParams =
                    transaction.readOnlyQuery("MATCH (n:Person{name:$name}) RETURN n.name", params, 1000);
            Reply<ResultSet> profile = transaction.profile("MATCH (n:Person) RETURN n");
            Reply<List<String>> plan = transaction.explain("MATCH (n:Person) WHERE n.name = $name RETURN n", params);
            transaction.exec();

            for (Reply<ResultSet> reply : Arrays.asList(plain, withParams)) {
                Assertions.assertEquals("a", reply.get().iterator().next().getString(0));
            }
            Assertions.assertTrue(profile.get().size() > 0);
            Assertions.assertFalse(plan.get().isEmpty());
        }
    }

    @Test
    public void anInvalidProcedureCallQueuesNothing() {
        api.query("CREATE (:Person)");
        try (GraphContext c = api.getContext()) {
            GraphContext.Transaction transaction = c.transaction();
            transaction.callProcedure("db.labels");
            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> transaction.callProcedure("db.labels() YIELD label MATCH (n) DETACH DELETE n //"));
            transaction.query("MATCH (n) RETURN count(n)");

            List<Object> results = transaction.exec();
            Assertions.assertEquals(2, results.size());
        }
    }

    /** A driver whose pool has one connection, so the next context gets the connection the last one closed. */
    private static Driver poolOfOne() {
        return FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .poolMaxTotal(1)
                .poolMaxIdle(1)
                .poolMaxWait(Duration.ofSeconds(5))
                .build();
    }
}
