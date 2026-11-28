package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.RedisProtocol;

/**
 * The schema hash FalkorDB 6.2 and later sends with a reply. Compact replies name labels, relationship types and
 * property keys by id, and a graph that is deleted and recreated reuses the ids for other names. With the hash, a
 * reply carries the names whenever the client's are stale, so a client decodes the recreated graph's names; an
 * older server sends no hash, and a client that has the old names cached keeps using them.
 *
 * <p>Each test asserts what each server version does, so the suite checks both on whichever server it runs against.
 */
public class SchemaHashIT {

    private static final String GRAPH = "schema-hash-it";

    private final List<AutoCloseable> toClose = new ArrayList<>();

    @AfterEach
    public void cleanUp() throws Exception {
        try (Driver driver = TestServer.driver()) {
            try {
                driver.graph(GRAPH).deleteGraph();
            } catch (RuntimeException alreadyGone) {
                // the test deleted it, or never created it
            }
        }
        Collections.reverse(toClose);
        for (AutoCloseable closeable : toClose) {
            closeable.close();
        }
    }

    @Test
    public void aClientDecodesAGraphAnotherClientRecreated() {
        Graph reader = graph(driver());
        Graph writer = graph(driver());
        assertDecodesTheRecreatedGraph(reader, writer, TestServer.sendsSchemaHash());
    }

    @Test
    public void aHandleDecodesAGraphAnotherHandleOfTheSameDriverRecreated() {
        Driver driver = driver();
        assertDecodesTheRecreatedGraph(graph(driver), graph(driver), TestServer.sendsSchemaHash());
    }

    @Test
    @SuppressWarnings("deprecation") // getConnection() is the only way to see the negotiated protocol
    public void aClientDecodesARecreatedGraphOverResp3() {
        Driver resp3 = track(
                FalkorDB.driver(URI.create("redis://" + TestServer.host() + ":" + TestServer.port() + "?protocol=3")));
        try (Jedis connection = resp3.getConnection()) {
            // RESP3 sends the schema element as a map rather than a flat list
            assertEquals(RedisProtocol.RESP3, connection.getConnection().getRedisProtocol());
        }
        assertDecodesTheRecreatedGraph(graph(resp3), graph(driver()), TestServer.sendsSchemaHash());
    }

    @Test
    public void anAsyncGraphDecodesARecreatedGraph() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        toClose.add(executor::shutdown);
        AsyncGraph reader = AsyncFalkorDB.wrap(graph(driver()), executor);
        Graph writer = graph(driver());

        writer.query("CREATE (:Person {name: 'Alice'})");
        assertEquals(
                "Person", onlyNode(reader.query("MATCH (n) RETURN n").get()).getLabel(0));
        recreateAsMovie(writer);

        Node node = onlyNode(reader.query("MATCH (n) RETURN n").get());
        assertNode(node, TestServer.sendsSchemaHash());
    }

    @Test
    public void switchedOffTheClientKeepsItsCachedNamesOnEveryServer() {
        Driver withoutHash = track(FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .schemaHash(false)
                .build());
        assertDecodesTheRecreatedGraph(graph(withoutHash), graph(driver()), false);
    }

    @Test
    public void aGraphRecreatedWithTheSameNamesDecodesAsBefore() {
        Graph reader = graph(driver());
        Graph writer = graph(driver());
        writer.query("CREATE (:Person {name: 'Alice'})");
        assertEquals(
                "Alice",
                onlyNode(reader.query("MATCH (n) RETURN n")).getProperty("name").getValue());

        writer.deleteGraph();
        writer.query("CREATE (:Person {name: 'Bob'})");

        Node node = onlyNode(reader.query("MATCH (n) RETURN n"));
        assertEquals("Person", node.getLabel(0));
        assertEquals("Bob", node.getProperty("name").getValue());
    }

    @Test
    public void aWriteThatAddsNamesDecodesTheEntityItReturns() {
        Graph graph = graph(driver());
        graph.query("CREATE (:Seed {seed: 1})");
        graph.query("MATCH (n) RETURN n"); // something cached

        ResultSet created = graph.query("CREATE (a:Author {name: 'Ann'})-[w:WROTE {year: 2020}]->(b:Book) RETURN a, w");

        Record record = created.iterator().next();
        Node author = record.getValue(0, Node.class);
        Edge wrote = record.getValue(1, Edge.class);
        assertEquals("Author", author.getLabel(0));
        assertEquals("Ann", author.getProperty("name").getValue());
        assertEquals("WROTE", wrote.getRelationshipType());
        assertEquals(2020L, wrote.getProperty("year").getValue());
        // and a read after it, which the server answers with the hash only
        assertEquals(
                "Author", onlyNode(graph.query("MATCH (n:Author) RETURN n")).getLabel(0));
    }

    @Test
    public void aPipelineDecodesTheNamesAWriteInItAdded() {
        GraphContextGenerator graph = graph(driver());
        graph.query("CREATE (:Seed)");
        graph.query("MATCH (n) RETURN n");
        try (GraphContext context = graph.getContext()) {
            GraphContext.Pipeline pipeline = context.pipeline();
            Reply<ResultSet> write = pipeline.query("CREATE (n:Added {key: 'v'}) RETURN n");
            Reply<ResultSet> read = pipeline.readOnlyQuery("MATCH (n:Added) RETURN n");
            pipeline.sync();
            assertEquals("Added", onlyNode(write.get()).getLabel(0));
            Node node = onlyNode(read.get());
            assertEquals("Added", node.getLabel(0));
            assertEquals("v", node.getProperty("key").getValue());
        }
    }

    @Test
    public void aTransactionDecodesAGraphAnotherClientRecreated() {
        GraphContextGenerator reader = graph(driver());
        Graph writer = graph(driver());
        writer.query("CREATE (:Person {name: 'Alice'})");
        assertEquals("Person", onlyNode(reader.query("MATCH (n) RETURN n")).getLabel(0));
        recreateAsMovie(writer);

        try (GraphContext context = reader.getContext()) {
            GraphContext.Transaction transaction = context.transaction();
            Reply<ResultSet> read = transaction.query("MATCH (n) RETURN n");
            transaction.exec();
            assertNode(onlyNode(read.get()), TestServer.sendsSchemaHash());
        }
    }

    @Test
    public void aProcedureReplyDecodes() {
        Graph graph = graph(driver());
        graph.query("CREATE (:A), (:B)");
        List<String> labels = new ArrayList<>();
        for (Record record : graph.callProcedure("db.labels")) {
            labels.add(record.getString(0));
        }
        assertEquals(java.util.Arrays.asList("A", "B"), labels);
    }

    @Test
    public void concurrentReadersDecodeEveryNameAWriterAdds() throws Exception {
        GraphContextGenerator graph = graph(driver());
        Graph writer = graph(driver());
        writer.query("CREATE (:L0 {p0: 0})");
        int labels = 20;
        int readers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        toClose.add(pool::shutdownNow);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int r = 0; r < readers; r++) {
            results.add(pool.submit(() -> {
                start.await();
                int checked = 0;
                for (int round = 0; round < 30; round++) {
                    for (Record record : graph.readOnlyQuery("MATCH (n) RETURN n")) {
                        Node node = record.getValue(0, Node.class);
                        // node i has label Li and one property, pi = i
                        String label = node.getLabel(0);
                        long i = Long.parseLong(label.substring(1));
                        assertEquals(i, node.getProperty("p" + i).getValue(), label);
                        checked++;
                    }
                }
                return checked;
            }));
        }
        start.countDown();
        for (int i = 1; i < labels; i++) {
            writer.query("CREATE (:L" + i + " {p" + i + ": " + i + "})");
        }
        for (Future<Integer> result : results) {
            assertTrue(result.get(2, TimeUnit.MINUTES) > 0);
        }
        Node last = onlyNode(graph.query("MATCH (n:L" + (labels - 1) + ") RETURN n"));
        assertEquals((long) labels - 1, last.getProperty("p" + (labels - 1)).getValue());
    }

    /**
     * The reader caches (:Person {name}), the writer recreates the graph as (:Movie {title}), and the reader reads it
     * again: with the schema hash it gets the new names; without, the ones it cached, which the new graph's ids
     * decode to.
     */
    private static void assertDecodesTheRecreatedGraph(Graph reader, Graph writer, boolean decodesNewNames) {
        writer.query("CREATE (:Person {name: 'Alice'})");
        Node before = onlyNode(reader.query("MATCH (n) RETURN n"));
        assertEquals("Person", before.getLabel(0));
        assertEquals("Alice", before.getProperty("name").getValue());

        recreateAsMovie(writer);

        assertNode(onlyNode(reader.query("MATCH (n) RETURN n")), decodesNewNames);
    }

    private static void recreateAsMovie(Graph writer) {
        writer.deleteGraph();
        writer.query("CREATE (:Movie {title: 'Heat'})");
    }

    private static void assertNode(Node node, boolean decodesNewNames) {
        if (decodesNewNames) {
            assertEquals("Movie", node.getLabel(0));
            assertEquals("Heat", node.getProperty("title").getValue());
        } else {
            // the ids of (:Movie {title}) decoded with the cached names of (:Person {name})
            assertEquals("Person", node.getLabel(0));
            assertEquals("Heat", node.getProperty("name").getValue());
        }
    }

    private Driver driver() {
        return track(TestServer.driver());
    }

    private <T extends AutoCloseable> T track(T closeable) {
        toClose.add(closeable);
        return closeable;
    }

    private GraphContextGenerator graph(Driver driver) {
        return track(driver.graph(GRAPH));
    }

    private static Node onlyNode(ResultSet resultSet) {
        assertEquals(1, resultSet.size());
        return resultSet.iterator().next().getValue(0, Node.class);
    }
}
