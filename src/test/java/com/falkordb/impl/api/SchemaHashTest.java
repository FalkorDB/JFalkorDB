package com.falkordb.impl.api;

import static com.falkordb.impl.api.FakeRedisServer.array;
import static com.falkordb.impl.api.FakeRedisServer.bulk;
import static com.falkordb.impl.api.FakeRedisServer.integer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Reply;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.graph_entities.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The schema hash end to end, against a fake server that answers as FalkorDB 6.2+ does: a query sends the hash of
 * the names it has cached, and a reply carries the names when that hash isn't the graph's, or only the hash when it
 * is. The client never asks for the names separately.
 */
class SchemaHashTest {

    private static final String MATCH = "MATCH (n) RETURN n";

    @Test
    void aQuerySendsZeroThenTheHashTheServerSent() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");

            assertNode(graph.query(MATCH), "Person", "name");
            assertNode(graph.readOnlyQuery(MATCH), "Person", "name");

            assertEquals(Arrays.asList("0", "42"), server.sentHashes());
            assertEquals(Arrays.asList(true, false), server.sentNames(), "only the first reply needed the names");
            assertEquals(0, server.lookups(), "no db.labels() or db.propertyKeys()");
        }
    }

    @Test
    void everyQueryOverloadSendsTheHashLast() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");
            for (Consumer<Graph> query : everyQuery()) {
                query.accept(graph);
                try (GraphContext context = graph.getContext()) {
                    query.accept(context);
                }
            }
            for (List<String> command : server.graphQueries()) {
                assertEquals("SCHEMA_HASH", command.get(command.size() - 2), command.toString());
                assertTrue(command.size() <= 8, "the server takes at most 8 words: " + command);
            }
            assertEquals(2 * everyQuery().size(), server.graphQueries().size());
        }
    }

    @Test
    void aRecreatedGraphIsDecodedWithTheNamesTheReplyCarries() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");
            assertNode(graph.query(MATCH), "Person", "name");

            server.recreate(7L, "Movie", "title");

            assertNode(graph.query(MATCH), "Movie", "title");
            assertNode(graph.query(MATCH), "Movie", "title");
            assertEquals(Arrays.asList("0", "42", "7"), server.sentHashes());
            assertEquals(0, server.lookups());
        }
    }

    @Test
    void aGraphRecreatedWithAPrefixOfItsNamesIsCachedAgain() throws Exception {
        // The recreated graph's names start like the old ones, as a replica that is behind would send. A rule that
        // kept the cached names then would send the old hash, and get the names back, on every query from then on.
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name", "age");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");
            assertNode(graph.query(MATCH), "Person", "name");

            server.recreate(7L, "Person", "name");

            for (int i = 0; i < 3; i++) {
                assertNode(graph.query(MATCH), "Person", "name");
            }
            assertEquals(Arrays.asList("0", "42", "7", "7"), server.sentHashes());
            assertEquals(Arrays.asList(true, true, false, false), server.sentNames());
            assertEquals(0, server.lookups());
        }
    }

    @Test
    void aPipelineDecodesWithTheHashItSent() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");
            graph.query(MATCH);
            try (GraphContext context = graph.getContext()) {
                GraphContext.Pipeline pipeline = context.pipeline();
                Reply<ResultSet> first = pipeline.query(MATCH);
                Reply<ResultSet> second = pipeline.readOnlyQuery(MATCH);
                pipeline.sync();
                assertNode(first.get(), "Person", "name");
                assertNode(second.get(), "Person", "name");
            }
            assertEquals(Arrays.asList("0", "42", "42"), server.sentHashes());
            assertEquals(0, server.lookups());
        }
    }

    @Test
    void profileAndExplainSendNoHash() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");
            graph.profile(MATCH);
            graph.explain(MATCH);
            List<List<String>> planCommands = server.received().stream()
                    .filter(c -> FakeRedisServer.startsWith(c, "GRAPH.PROFILE")
                            || FakeRedisServer.startsWith(c, "GRAPH.EXPLAIN"))
                    .collect(Collectors.toList());
            assertEquals(2, planCommands.size());
            for (List<String> command : planCommands) {
                assertFalse(command.contains("SCHEMA_HASH"), command.toString());
            }
        }
    }

    @Test
    void switchedOffNoHashIsSentAndNamesAreLookedUp() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name");
                Driver driver = driver(server, false)) {
            assertNode(driver.graph("g").query(MATCH), "Person", "name");

            assertEquals(Collections.emptyList(), server.sentHashes());
            assertEquals(2, server.lookups(), "db.labels() and db.propertyKeys()");
            for (List<String> command : server.received()) {
                assertFalse(command.contains("SCHEMA_HASH"), command.toString());
            }
        }
    }

    @Test
    void anOlderServerIgnoresTheHashAndNamesAreLookedUp() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(false, 42L, "Person", "name");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");
            assertNode(graph.query(MATCH), "Person", "name");
            assertNode(graph.query(MATCH), "Person", "name");

            // the lookups' own queries carry the hash too; an older server ignores it everywhere
            assertTrue(
                    server.sentHashes().stream().allMatch("0"::equals),
                    server.sentHashes().toString());
            assertEquals(2, server.lookups(), "looked up once, then cached");
        }
    }

    @Test
    void aHashOnlyReplyThatIsNotTheHashSentIsReported() throws Exception {
        try (FakeSchemaServer server = new FakeSchemaServer(true, 42L, "Person", "name");
                Driver driver = driver(server, true)) {
            GraphContextGenerator graph = driver.graph("g");
            graph.query(MATCH);
            server.neverSendsNames = true;
            server.recreate(7L, "Movie", "title");

            GraphException e = assertThrows(GraphException.class, () -> graph.query(MATCH));
            assertTrue(e.getMessage().startsWith("The query ran"), e.getMessage());
        }
    }

    private static List<Consumer<Graph>> everyQuery() {
        return Arrays.asList(
                g -> g.query(MATCH),
                g -> g.readOnlyQuery(MATCH),
                g -> g.query(MATCH, 1000L),
                g -> g.readOnlyQuery(MATCH, 1000L),
                g -> g.query(MATCH, Collections.singletonMap("x", 1)),
                g -> g.readOnlyQuery(MATCH, Collections.singletonMap("x", 1)),
                g -> g.query(MATCH, Collections.singletonMap("x", 1), 1000L),
                g -> g.readOnlyQuery(MATCH, Collections.singletonMap("x", 1), 1000L),
                g -> g.callProcedure("db.labels"));
    }

    private static void assertNode(ResultSet resultSet, String label, String key) {
        Node node = resultSet.iterator().next().getValue(0, Node.class);
        assertEquals(label, node.getLabel(0));
        assertEquals("v", node.getProperty(key).getValue());
    }

    private static Driver driver(FakeSchemaServer server, boolean schemaHash) {
        return FalkorDB.builder()
                .host("127.0.0.1")
                .port(server.port())
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .schemaHash(schemaHash)
                .build();
    }

    /**
     * A graph with one node, of label id 0 and property key id 0 = "v", answered as a FalkorDB 6.2+ server does, or
     * as an older one that ignores {@code SCHEMA_HASH}.
     */
    private static final class FakeSchemaServer implements AutoCloseable {

        private final FakeRedisServer server = new FakeRedisServer(this::answer);

        private final boolean sendsSchema;
        private volatile long hash;
        private volatile String label;
        /** The property keys, in id order; the node has the first one. */
        private volatile List<String> keys;
        /** Answers with the graph's hash alone, even when the query sent another, as a server with a bug would. */
        volatile boolean neverSendsNames;

        private final List<Boolean> sentNames = Collections.synchronizedList(new ArrayList<>());

        FakeSchemaServer(boolean sendsSchema, long hash, String label, String... keys) {
            this.sendsSchema = sendsSchema;
            recreate(hash, label, keys);
        }

        void recreate(long newHash, String newLabel, String... newKeys) {
            hash = newHash;
            label = newLabel;
            keys = Arrays.asList(newKeys);
        }

        int port() {
            return server.port();
        }

        List<List<String>> received() {
            return server.received();
        }

        @Override
        public void close() {
            server.close();
        }

        private String answer(List<String> command) {
            if (!(startsWith(command, "GRAPH.QUERY") || startsWith(command, "GRAPH.RO_QUERY"))) {
                if (startsWith(command, "GRAPH.PROFILE")) {
                    return array(bulk("Results | Records produced: 1"));
                }
                if (startsWith(command, "GRAPH.EXPLAIN")) {
                    return array(bulk("Results"));
                }
                return FakeRedisServer.ok();
            }
            String query = command.get(2);
            String rows;
            String header;
            if (query.startsWith("CALL db.")) {
                header = array(array(integer(1), bulk("name")));
                List<String> names = query.startsWith("CALL db.labels") ? Collections.singletonList(label) : keys;
                rows = array(names.stream()
                        .map(name -> array(array(integer(2), bulk(name))))
                        .toArray(String[]::new));
            } else {
                header = array(array(integer(1), bulk("n")));
                String property = array(integer(0), integer(2), bulk("v")); // key 0, VALUE_STRING
                String node = array(integer(8), array(integer(7), array(integer(0)), array(property)));
                rows = array(array(node));
            }
            String stats = array(bulk("Cached execution: 0"));
            int at = command.indexOf("SCHEMA_HASH");
            if (!sendsSchema || at < 0) {
                return array(header, rows, stats);
            }
            long sent = Long.parseLong(command.get(at + 1));
            boolean names = sent != hash && !neverSendsNames;
            sentNames.add(names);
            String element = names
                    ? array(
                            bulk("hash"),
                            integer(hash),
                            bulk("labels"),
                            array(bulk(label)),
                            bulk("relationshipTypes"),
                            array(),
                            bulk("propertyKeys"),
                            array(keys.stream().map(FakeRedisServer::bulk).toArray(String[]::new)))
                    : array(bulk("hash"), integer(hash));
            return array(header, rows, stats, element);
        }

        /** The graph queries the client sent, lookups excluded. */
        List<List<String>> graphQueries() {
            return received().stream()
                    .filter(c -> startsWith(c, "GRAPH.QUERY") || startsWith(c, "GRAPH.RO_QUERY"))
                    .filter(c -> !isLookup(c))
                    .collect(Collectors.toList());
        }

        /** The hashes the graph queries sent, in order. */
        List<String> sentHashes() {
            return graphQueries().stream()
                    .filter(c -> c.contains("SCHEMA_HASH"))
                    .map(c -> c.get(c.indexOf("SCHEMA_HASH") + 1))
                    .collect(Collectors.toList());
        }

        /** Whether each reply that answered a hash carried the names. */
        List<Boolean> sentNames() {
            return sentNames;
        }

        /** How many {@code db.labels()} / {@code db.propertyKeys()} lookups the client sent. */
        long lookups() {
            return received().stream().filter(FakeSchemaServer::isLookup).count();
        }

        /** A lookup is read-only; {@code callProcedure("db.labels")} sends GRAPH.QUERY. */
        private static boolean isLookup(List<String> command) {
            return startsWith(command, "GRAPH.RO_QUERY") && command.get(2).startsWith("CALL db.");
        }

        private static boolean startsWith(List<String> command, String prefix) {
            return FakeRedisServer.startsWith(command, prefix);
        }
    }
}
