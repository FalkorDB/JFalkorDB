package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Replies the server sends differently over RESP2 and RESP3 read the same through the client. Each test runs over
 * both protocols, whatever {@code FALKORDB_PROTOCOL} says, so a plain {@code just verify} covers both; the rest of the
 * suite runs over the protocol {@link TestProtocol} names, which CI runs once per protocol.
 *
 * <p>RESP3 sends a {@code vecf32} element as a native double, and FalkorDB 4.x sends its on/off configuration
 * parameters as booleans and each UDF library as a map, where RESP2 sends text, integers and flat lists.
 */
public class ProtocolIT {

    private static final String GRAPH = "protocol-it";
    private static final String LIBRARY = "protocol_it_lib";

    private final List<Driver> drivers = new ArrayList<>();

    @AfterEach
    public void cleanUp() throws Exception {
        try (Driver driver = TestServer.driver()) {
            try {
                driver.graph(GRAPH).deleteGraph();
            } catch (RuntimeException alreadyGone) {
                // the test didn't create it
            }
            try {
                driver.udfDelete(LIBRARY);
            } catch (RuntimeException notLoaded) {
                // the test didn't load it
            }
        }
        for (Driver driver : drivers) {
            driver.close();
        }
    }

    @Test
    public void theSuiteSpeaksTheProtocolItNamesAndTheBuilderSpeaksResp2() throws Exception {
        // TestProtocol.driver asserts the negotiated protocol, so this fails if FALKORDB_PROTOCOL is not honoured
        try (Driver suite = TestServer.driver();
                Driver builder = FalkorDB.builder()
                        .host(TestServer.host())
                        .port(TestServer.port())
                        .build()) {
            assertSpeaks(TestProtocol.protocol(), suite);
            // FalkorDB.builder() can't choose the protocol: a test built on it covers RESP2 under either value
            TestProtocol.assertResp2(builder);
        }
    }

    @ParameterizedTest
    @EnumSource(RedisProtocol.class)
    public void aVectorReadsTheSameAsAValueAPropertyAndInAPath(RedisProtocol protocol) {
        GraphContextGenerator graph = driver(protocol).graph(GRAPH);
        graph.query("CREATE (:Doc {embedding: vecf32([1.5, 2.5])})-[:NEXT]->(:Doc {embedding: vecf32([-0.5])})");
        List<Float> first = Arrays.asList(1.5f, 2.5f);

        assertEquals(
                first,
                graph.query("RETURN vecf32([1.5, 2.5])").iterator().next().getValue(0, List.class));
        assertEquals(first, embedding(graph.readOnlyQuery("MATCH (d:Doc)-[:NEXT]->() RETURN d")));
        Path path = graph.query("MATCH p = (:Doc)-[:NEXT]->(:Doc) RETURN p")
                .iterator()
                .next()
                .getValue(0, Path.class);
        assertEquals(first, path.getNode(0).getProperty("embedding").getValue());
        assertEquals(
                Arrays.asList(-0.5f), path.getNode(1).getProperty("embedding").getValue());

        // non-finite elements: text spelled the C way over RESP2, native doubles over RESP3
        List<?> nonFinite = graph.query("RETURN vecf32([1.0/0.0, -1.0/0.0])")
                .iterator()
                .next()
                .getValue(0, List.class);
        assertEquals(Arrays.asList(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY), nonFinite);
    }

    @ParameterizedTest
    @EnumSource(RedisProtocol.class)
    public void aVectorReadsTheSameInAPipelineAndATransaction(RedisProtocol protocol) {
        GraphContextGenerator graph = driver(protocol).graph(GRAPH);
        graph.query("CREATE (:Doc {embedding: vecf32([1.5, 2.5])})");
        List<Float> expected = Arrays.asList(1.5f, 2.5f);
        try (GraphContext context = graph.getContext()) {
            GraphContext.Pipeline pipeline = context.pipeline();
            Reply<ResultSet> piped = pipeline.query("MATCH (d:Doc) RETURN d");
            pipeline.sync();
            assertEquals(expected, embedding(piped.get()));

            GraphContext.Transaction transaction = context.transaction();
            Reply<ResultSet> transacted = transaction.readOnlyQuery("MATCH (d:Doc) RETURN d");
            transaction.exec();
            assertEquals(expected, embedding(transacted.get()));
        }
    }

    @Test
    public void everyConfigurationParameterReadsTheSameOverBothProtocols() {
        // FalkorDB 4.x sends ASYNC_DELETE, CMD_INFO and DELAY_INDEXING as :1 / :0 over RESP2 and as booleans over RESP3
        Map<String, String> resp2 = driver(RedisProtocol.RESP2).configGetAll();
        Map<String, String> resp3 = driver(RedisProtocol.RESP3).configGetAll();

        assertFalse(resp2.isEmpty());
        assertEquals(resp2, resp3);
        // the parameters 4.x sends as booleans over RESP3; 6.x has the same names and sends integers
        for (String name : Arrays.asList("ASYNC_DELETE", "CMD_INFO", "DELAY_INDEXING")) {
            String value = driver(RedisProtocol.RESP3).configGet(name);
            assertEquals(resp2.get(name), value, name);
            assertTrue(value.equals("0") || value.equals("1"), name + " = " + value);
        }
    }

    @Test
    public void aUdfLibraryListsTheSameOverBothProtocols() {
        Driver resp2 = driver(RedisProtocol.RESP2);
        resp2.udfLoad(LIBRARY, "function hello() { return 'hi'; } falkor.register('hello', hello);", true);

        // FalkorDB 4.x sends each library as a map over RESP3, and as a flat list otherwise
        Object overResp2 = texts(resp2.udfList(LIBRARY, false));
        Object overResp3 = texts(driver(RedisProtocol.RESP3).udfList(LIBRARY, false));

        assertEquals(overResp2, overResp3);
        List<?> library = (List<?>) ((List<?>) overResp3).get(0);
        assertTrue(library.indexOf("library_name") % 2 == 0, library.toString());
        assertEquals(LIBRARY, library.get(library.indexOf("library_name") + 1));
    }

    @Test
    public void everyKindOfValueReadsTheSameOverBothProtocols() {
        String query = "RETURN 1, 1.5, true, 'a', null, [1, 2.5, false], {a: 1, b: 2.5},"
                + " point({latitude: 1.5, longitude: 2.5}), date('2020-01-02'), localtime('10:11:12'),"
                + " localdatetime('2020-01-02T10:11:12'), duration('P1D')";
        Record overResp2 =
                driver(RedisProtocol.RESP2).graph(GRAPH).query(query).iterator().next();
        Record overResp3 =
                driver(RedisProtocol.RESP3).graph(GRAPH).query(query).iterator().next();

        assertEquals(overResp2.values(), overResp3.values());
    }

    /** A driver for the test server speaking {@code protocol}, checked. */
    private Driver driver(RedisProtocol protocol) {
        Driver driver = FalkorDB.driver(TestProtocol.uri(TestServer.host(), TestServer.port(), protocol));
        drivers.add(driver);
        assertSpeaks(protocol, driver);
        return driver;
    }

    @SuppressWarnings("deprecation") // getConnection() is the only way to see the negotiated protocol
    private static void assertSpeaks(RedisProtocol expected, Driver driver) {
        try (redis.clients.jedis.Jedis connection = driver.getConnection()) {
            RedisProtocol negotiated = connection.getConnection().getRedisProtocol();
            // a connection that asked for no protocol reports none: it speaks RESP2
            assertEquals(expected, negotiated == null ? RedisProtocol.RESP2 : negotiated);
        }
    }

    private static Object embedding(ResultSet resultSet) {
        return resultSet
                .iterator()
                .next()
                .getValue(0, Node.class)
                .getProperty("embedding")
                .getValue();
    }

    /** A reply with its bulk strings decoded, for comparing. */
    private static Object texts(Object reply) {
        if (reply instanceof byte[]) {
            return SafeEncoder.encode((byte[]) reply);
        }
        if (reply instanceof List<?>) {
            List<Object> decoded = new ArrayList<>();
            for (Object item : (List<?>) reply) {
                decoded.add(texts(item));
            }
            return decoded;
        }
        return reply;
    }
}
