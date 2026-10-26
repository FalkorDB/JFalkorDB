package com.falkordb.impl.api;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * Unit tests for DriverImpl configGet/configSet response parsing logic.
 * Tests the parseConfigGetResponse and parseConfigSetResponse methods directly.
 */
public class DriverImplConfigTest {

    private DriverImpl driverImpl;

    @BeforeEach
    public void setUp() {
        driverImpl = new DriverImpl("localhost", 6379);
    }

    @Test
    public void testParseConfigGetResponseWithByteArrayValue() {
        List<Object> response = Arrays.asList(SafeEncoder.encode("RESULTSET_SIZE"), SafeEncoder.encode("100"));
        String result = driverImpl.parseConfigGetResponse(response);
        Assertions.assertEquals("100", result);
    }

    @Test
    public void testParseConfigGetResponseWithLongValue() {
        List<Object> response = Arrays.asList(SafeEncoder.encode("RESULTSET_SIZE"), Long.valueOf(100));
        String result = driverImpl.parseConfigGetResponse(response);
        Assertions.assertEquals("100", result);
    }

    @Test
    public void testParseConfigGetResponseWithNullValue() {
        List<Object> response = Arrays.asList(SafeEncoder.encode("RESULTSET_SIZE"), null);
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(response);
        });
    }

    @Test
    public void testParseConfigGetResponseWithEmptyList() {
        List<Object> response = Collections.emptyList();
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(response);
        });
    }

    @Test
    public void testParseConfigGetResponseWithSingleElementList() {
        List<Object> response = Collections.singletonList(SafeEncoder.encode("RESULTSET_SIZE"));
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(response);
        });
    }

    @Test
    public void testParseConfigGetResponseWithNonListResponse() {
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse("not a list");
        });
    }

    @Test
    public void testParseConfigGetResponseWithNull() {
        Assertions.assertThrows(JedisDataException.class, () -> {
            driverImpl.parseConfigGetResponse(null);
        });
    }

    @Test
    public void testParseConfigGetResponseRejectsSeveralParameters() {
        // GRAPH.CONFIG GET * answers with a list of pairs. Reading list.get(1) used to return the
        // second pair's toString(), such as "[[B@3f99bd52, 4]".
        List<Object> response = Arrays.asList(pair("CACHE_SIZE", 25L), pair("THREAD_COUNT", 4L));
        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class, () -> driverImpl.parseConfigGetResponse(response));
        Assertions.assertTrue(e.getMessage().contains("returned 2"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("configGetAll()"), e.getMessage());
    }

    @Test
    public void testParseConfigGetResponseUnwrapsASingleNestedPair() {
        List<Object> response = Collections.singletonList(pair("RESULTSET_SIZE", 100L));
        Assertions.assertEquals("100", driverImpl.parseConfigGetResponse(response));
    }

    @Test
    public void testParseConfigGetResponseRejectsMoreThanAPair() {
        List<Object> response = Arrays.asList(SafeEncoder.encode("RESULTSET_SIZE"), 100L, 200L);
        Assertions.assertThrows(JedisDataException.class, () -> driverImpl.parseConfigGetResponse(response));
    }

    @Test
    public void testParseConfigGetResponseRejectsAValueThatIsNotText() {
        // Jedis puts an error nested in an array into the list as an exception object. Its toString()
        // must not come back as the value, and neither may a nested list's.
        Assertions.assertThrows(
                JedisDataException.class,
                () -> driverImpl.parseConfigGetResponse(
                        pair("RESULTSET_SIZE", new JedisDataException("ERR nested error"))));
        Assertions.assertThrows(
                JedisDataException.class,
                () -> driverImpl.parseConfigGetResponse(pair("RESULTSET_SIZE", Collections.singletonList(100L))));
    }

    @Test
    public void testParseConfigGetAllResponseReadsEveryParameterInOrder() {
        List<Object> response = Arrays.asList(
                pair("TIMEOUT", 1000L),
                pair("IMPORT_FOLDER", SafeEncoder.encode("/var/lib/FalkorDB/import/")),
                pair("CMD_INFO", "yes"));

        Map<String, String> parameters = driverImpl.parseConfigGetAllResponse(response);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("TIMEOUT", "1000");
        expected.put("IMPORT_FOLDER", "/var/lib/FalkorDB/import/");
        expected.put("CMD_INFO", "yes");
        Assertions.assertEquals(expected, parameters);
        Assertions.assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(parameters.keySet()));
    }

    @Test
    public void testParseConfigGetAllResponseIsUnmodifiable() {
        Map<String, String> parameters =
                driverImpl.parseConfigGetAllResponse(Collections.singletonList(pair("TIMEOUT", 1000L)));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> parameters.put("TIMEOUT", "0"));
    }

    @Test
    public void testParseConfigGetAllResponseReadsASingleParameter() {
        Assertions.assertEquals(
                Collections.singletonMap("RESULTSET_SIZE", "100"),
                driverImpl.parseConfigGetAllResponse(pair("RESULTSET_SIZE", 100L)));
    }

    @Test
    public void testParseConfigGetAllResponseWithEmptyList() {
        Assertions.assertTrue(
                driverImpl.parseConfigGetAllResponse(Collections.emptyList()).isEmpty());
    }

    @Test
    public void testParseConfigGetAllResponseRejectsMalformedResponses() {
        List<Object> malformed = Arrays.asList(
                null,
                "not a list",
                // a pair, then a bare name
                Arrays.asList(pair("TIMEOUT", 1000L), SafeEncoder.encode("CACHE_SIZE")),
                // a pair without a value, and one with an extra item
                Collections.singletonList(Collections.singletonList(SafeEncoder.encode("TIMEOUT"))),
                Collections.singletonList(Arrays.asList(SafeEncoder.encode("TIMEOUT"), 1000L, 0L)),
                // a null value, and a null name
                Collections.singletonList(pair("TIMEOUT", null)),
                Collections.singletonList(Arrays.asList(null, 1000L)),
                // the same parameter twice
                Arrays.asList(pair("TIMEOUT", 1000L), pair("TIMEOUT", 0L)));
        for (int i = 0; i < malformed.size(); i++) {
            Object response = malformed.get(i);
            Assertions.assertThrows(
                    JedisDataException.class,
                    () -> driverImpl.parseConfigGetAllResponse(response),
                    "malformed response #" + i);
        }
    }

    @Test
    public void testConfigGetAndConfigSetRejectANullNameBeforeConnecting() throws Exception {
        // FalkorDB.driver() resolves its connections lazily, probing the server on first use, so an
        // empty command log shows the name was checked before anything was sent.
        try (FakeRedisServer server = new FakeRedisServer(command -> FakeRedisServer.ok());
                Driver driver = FalkorDB.driver("127.0.0.1", server.port())) {
            IllegalArgumentException get =
                    Assertions.assertThrows(IllegalArgumentException.class, () -> driver.configGet(null));
            Assertions.assertEquals("name must not be null", get.getMessage());
            IllegalArgumentException set =
                    Assertions.assertThrows(IllegalArgumentException.class, () -> driver.configSet(null, 100));
            Assertions.assertEquals("name must not be null", set.getMessage());
            Assertions.assertEquals(Collections.emptyList(), server.received());
        }
    }

    @Test
    public void testConfigGetRejectsTheWildcardBeforeConnecting() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(command -> FakeRedisServer.ok());
                Driver driver = FalkorDB.driver("127.0.0.1", server.port())) {
            IllegalArgumentException e =
                    Assertions.assertThrows(IllegalArgumentException.class, () -> driver.configGet("*"));
            Assertions.assertTrue(e.getMessage().contains("configGetAll()"), e.getMessage());
            Assertions.assertEquals(Collections.emptyList(), server.received());
        }
    }

    @Test
    public void testConfigGetAllReadsTheServersReply() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(DriverImplConfigTest::configServer);
                Driver driver = directDriver(server)) {
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("RESULTSET_SIZE", "10000");
            expected.put("IMPORT_FOLDER", "/var/lib/FalkorDB/import/");

            Assertions.assertEquals(expected, driver.configGetAll());
            Assertions.assertEquals("10000", driver.configGet("RESULTSET_SIZE"));
            Assertions.assertEquals(1, server.count("GRAPH.CONFIG", "GET", "*"));
        }
    }

    @Test
    public void testConfigGetRejectsAReplyWithSeveralParameters() throws Exception {
        // Only "*" is a wildcard today, and configGet refuses it before sending. Should the server ever
        // match other patterns, the shape of its reply still gives a multi-parameter answer away.
        try (FakeRedisServer server = new FakeRedisServer(command -> FakeRedisServer.startsWith(command, "GRAPH.CONFIG")
                        ? configServer(Arrays.asList("GRAPH.CONFIG", "GET", "*"))
                        : FakeRedisServer.ok());
                Driver driver = directDriver(server)) {
            IllegalArgumentException e =
                    Assertions.assertThrows(IllegalArgumentException.class, () -> driver.configGet("RESULTSET*"));
            Assertions.assertTrue(e.getMessage().contains("returned 2"), e.getMessage());
        }
    }

    @Test
    public void testParseConfigSetResponseWithOkBytes() {
        boolean result = driverImpl.parseConfigSetResponse(SafeEncoder.encode("OK"));
        Assertions.assertTrue(result);
    }

    @Test
    public void testParseConfigSetResponseWithOkString() {
        boolean result = driverImpl.parseConfigSetResponse("OK");
        Assertions.assertTrue(result);
    }

    @Test
    public void testParseConfigSetResponseWithNull() {
        boolean result = driverImpl.parseConfigSetResponse(null);
        Assertions.assertFalse(result);
    }

    @Test
    public void testParseConfigSetResponseWithErrorString() {
        boolean result = driverImpl.parseConfigSetResponse("ERR");
        Assertions.assertFalse(result);
    }

    @Test
    public void testParseConfigSetResponseCaseInsensitive() {
        Assertions.assertTrue(driverImpl.parseConfigSetResponse(SafeEncoder.encode("ok")));
        Assertions.assertTrue(driverImpl.parseConfigSetResponse(SafeEncoder.encode("Ok")));
        Assertions.assertTrue(driverImpl.parseConfigSetResponse(SafeEncoder.encode("OK")));
    }

    /** A [name, value] pair as Jedis decodes it: the name is a bulk string, so it arrives as bytes. */
    private static List<Object> pair(String name, Object value) {
        return Arrays.asList(SafeEncoder.encode(name), value);
    }

    /**
     * Answers like FalkorDB: GRAPH.CONFIG GET * with one [name, value] pair per parameter, and a single
     * name with a flat [name, value]. Values are integers, or bulk strings for paths.
     */
    private static String configServer(List<String> command) {
        String resultSetSize =
                FakeRedisServer.array(FakeRedisServer.bulk("RESULTSET_SIZE"), FakeRedisServer.integer(10000));
        if (FakeRedisServer.startsWith(command, "GRAPH.CONFIG", "GET", "*")) {
            return FakeRedisServer.array(
                    resultSetSize,
                    FakeRedisServer.array(
                            FakeRedisServer.bulk("IMPORT_FOLDER"), FakeRedisServer.bulk("/var/lib/FalkorDB/import/")));
        }
        if (FakeRedisServer.startsWith(command, "GRAPH.CONFIG", "GET", "RESULTSET_SIZE")) {
            return resultSetSize;
        }
        return FakeRedisServer.ok();
    }

    /** A driver that connects straight to {@code server}, without probing for Sentinel or a cluster. */
    private static Driver directDriver(FakeRedisServer server) {
        return DriverImpl.connect(
                "127.0.0.1", server.port(), null, null, SentinelOptions.disabled(), ClusterOptions.disabled());
    }
}
