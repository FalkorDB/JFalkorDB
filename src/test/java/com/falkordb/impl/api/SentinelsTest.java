package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Unit tests for {@link Sentinels}: recognising a Sentinel from its {@code INFO server} reply, picking
 * the master out of a {@code SENTINEL MASTERS} reply, parsing {@code host:port} addresses, and the
 * probe ({@link Sentinels#probeMasterName}) against a {@link FakeRedisServer}. No real server is
 * required; building the failover-aware pool is covered end-to-end by {@code SentinelIT}.
 */
class SentinelsTest {

    /** A realistic (abridged) `INFO server` reply, which Redis terminates with CRLF. */
    private static String infoWithMode(String mode) {
        return "# Server\r\nredis_version:7.4.0\r\nredis_mode:" + mode + "\r\nos:Linux\r\narch_bits:64\r\n";
    }

    @Test
    void recognisesSentinelMode() {
        assertTrue(Sentinels.isSentinelInfo(infoWithMode("sentinel")));
    }

    @Test
    void rejectsStandaloneAndClusterModes() {
        assertFalse(Sentinels.isSentinelInfo(infoWithMode("standalone")));
        assertFalse(Sentinels.isSentinelInfo(infoWithMode("cluster")));
    }

    @Test
    void recognisesValkeySentinelMode() {
        // Valkey names the field server_mode unless extended-redis-compatibility is on (default off).
        assertTrue(Sentinels.isSentinelInfo(ServerModeTest.valkeyInfo("sentinel")));
        assertTrue(Sentinels.isSentinelInfo("# Server\nserver_mode:SENTINEL\n"));
    }

    @Test
    void rejectsValkeyStandaloneAndClusterModes() {
        assertFalse(Sentinels.isSentinelInfo(ServerModeTest.valkeyInfo("standalone")));
        assertFalse(Sentinels.isSentinelInfo(ServerModeTest.valkeyInfo("cluster")));
        assertFalse(
                Sentinels.isSentinelInfo("config_file:/etc/server_mode:sentinel.conf\r\nserver_mode:standalone\r\n"));
    }

    @Test
    void rejectsNullOrEmptyInfo() {
        assertFalse(Sentinels.isSentinelInfo(null));
        assertFalse(Sentinels.isSentinelInfo(""));
        assertFalse(Sentinels.isSentinelInfo("# Server\r\nredis_version:7.4.0\r\n"));
    }

    @Test
    void readsTheModeFieldRatherThanSearchingTheWholeReply() {
        // The reason this is parsed line-wise: a substring search for "redis_mode:sentinel" would be
        // fooled by any other field whose *value* happens to contain it, and config_file is a real
        // field carrying an operator-chosen path.
        String info = "# Server\r\nconfig_file:/etc/redis_mode:sentinel.conf\r\nredis_mode:standalone\r\n";
        assertFalse(Sentinels.isSentinelInfo(info), "a path that merely contains the marker is not the mode");
    }

    @Test
    void toleratesLfLineEndingsAndPadding() {
        assertTrue(Sentinels.isSentinelInfo("# Server\nredis_mode:sentinel\n"));
        assertTrue(Sentinels.isSentinelInfo("# Server\r\n  redis_mode:sentinel  \r\n"));
        assertTrue(Sentinels.isSentinelInfo("redis_mode:SENTINEL"));
    }

    @Test
    void takesTheNameOfTheSoleMonitoredMaster() {
        assertEquals("mymaster", Sentinels.soleMasterName(masters("mymaster")));
    }

    @Test
    void trimsTheMasterName() {
        assertEquals("mymaster", Sentinels.soleMasterName(masters("  mymaster  ")));
    }

    @Test
    void rejectsAnAmbiguousSentinel() {
        // Matches falkordb-py/go/ts, which all refuse to guess. The message must point at the way out.
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> Sentinels.soleMasterName(masters("first", "second")));
        assertTrue(thrown.getMessage().contains("first"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("second"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("sentinel(masterName"), thrown.getMessage());
    }

    @Test
    void rejectsASentinelMonitoringNothing() {
        assertThrows(IllegalStateException.class, () -> Sentinels.soleMasterName(Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> Sentinels.soleMasterName(null));
    }

    @Test
    void rejectsAMasterWithoutAUsableName() {
        assertThrows(
                IllegalStateException.class,
                () -> Sentinels.soleMasterName(Collections.singletonList(new HashMap<String, String>())));
        assertThrows(IllegalStateException.class, () -> Sentinels.soleMasterName(masters("   ")));
    }

    @Test
    void parsesHostPortAddresses() {
        HostAndPort parsed = Sentinels.parseAddress("sentinel.example.com:26379");
        assertEquals("sentinel.example.com", parsed.getHost());
        assertEquals(26379, parsed.getPort());
    }

    @Test
    void parsesBracketedIpv6Addresses() {
        assertEquals(26379, Sentinels.parseAddress("[::1]:26379").getPort());
    }

    @Test
    void trimsAddressPadding() {
        assertEquals("host", Sentinels.parseAddress("  host:26379  ").getHost());
    }

    @Test
    void rejectsMalformedAddresses() {
        // Each of these fails somewhere different inside Jedis' parser (or passes it and fails our own
        // range check), so all of them are pinned rather than just a representative one.
        for (String malformed : Arrays.asList("localhost", "localhost:abc", ":26379", "", "   ")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Sentinels.parseAddress(malformed),
                    "expected \"" + malformed + "\" to be rejected");
        }
        assertThrows(IllegalArgumentException.class, () -> Sentinels.parseAddress(null));
    }

    @Test
    void rejectsOutOfRangePorts() {
        assertThrows(IllegalArgumentException.class, () -> Sentinels.parseAddress("localhost:0"));
        assertThrows(IllegalArgumentException.class, () -> Sentinels.parseAddress("localhost:99999"));
    }

    @Test
    void parsesAddressListsInOrder() {
        Set<HostAndPort> parsed = Sentinels.parseAddresses(Arrays.asList("a:26379", "b:26380", "c:26381"));

        List<String> hosts = new ArrayList<>();
        for (HostAndPort address : parsed) {
            hosts.add(address.getHost());
        }
        assertLinesMatch(Arrays.asList("a", "b", "c"), hosts, "the caller's order must be preserved");
    }

    @Test
    void rejectsAnEmptyAddressList() {
        assertThrows(IllegalArgumentException.class, () -> Sentinels.parseAddresses(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> Sentinels.parseAddresses(null));
    }

    // --- probeMasterName ---------------------------------------------------------------------------

    private static DefaultJedisClientConfig probe() {
        return DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(1000)
                .socketTimeoutMillis(2000)
                .autoNegotiateProtocol(false)
                .build();
    }

    /** The raw {@code SENTINEL MASTERS} reply for one master, as a Sentinel sends it over RESP2. */
    private static final String ONE_MASTER = FakeRedisServer.array(FakeRedisServer.array(
            FakeRedisServer.bulk("name"),
            FakeRedisServer.bulk("mymaster"),
            FakeRedisServer.bulk("ip"),
            FakeRedisServer.bulk("127.0.0.1"),
            FakeRedisServer.bulk("port"),
            FakeRedisServer.bulk("6379")));

    /** A fake endpoint answering {@code INFO} with {@code info} and {@code SENTINEL MASTERS} with {@code masters}. */
    private static FakeRedisServer endpoint(String info, String masters) {
        return new FakeRedisServer(c -> {
            if (FakeRedisServer.startsWith(c, "INFO")) {
                return FakeRedisServer.bulk(info);
            }
            if (FakeRedisServer.startsWith(c, "SENTINEL", "MASTERS")) {
                return masters;
            }
            return FakeRedisServer.ok();
        });
    }

    private static HostAndPort at(FakeRedisServer server) {
        return new HostAndPort("127.0.0.1", server.port());
    }

    @Test
    void aValkeySentinelYieldsTheMasterItMonitors() {
        try (FakeRedisServer sentinel = endpoint(ServerModeTest.valkeyInfo("sentinel"), ONE_MASTER)) {
            assertEquals("mymaster", Sentinels.probeMasterName(at(sentinel), probe()));
            assertEquals(1, sentinel.count("INFO", "server"));
            assertEquals(1, sentinel.count("SENTINEL", "MASTERS"));
        }
    }

    @Test
    void aRedisSentinelYieldsTheMasterItMonitors() {
        try (FakeRedisServer sentinel = endpoint(ServerModeTest.redisInfo("sentinel"), ONE_MASTER)) {
            assertEquals("mymaster", Sentinels.probeMasterName(at(sentinel), probe()));
        }
    }

    @Test
    void aValkeyDataNodeIsNotASentinelAndIsNeverAskedForMasters() {
        for (String mode : Arrays.asList("standalone", "cluster")) {
            try (FakeRedisServer node = endpoint(ServerModeTest.valkeyInfo(mode), ONE_MASTER)) {
                assertNull(Sentinels.probeMasterName(at(node), probe()), mode);
                assertEquals(0, node.count("SENTINEL"), mode);
            }
        }
    }

    @Test
    void aRedisDataNodeIsNotASentinel() {
        try (FakeRedisServer node = endpoint(ServerModeTest.redisInfo("standalone"), ONE_MASTER)) {
            assertNull(Sentinels.probeMasterName(at(node), probe()));
            assertEquals(0, node.count("SENTINEL"));
        }
    }

    @Test
    void anUnreachableEndpointIsNotASentinel() {
        assertNull(Sentinels.probeMasterName(new HostAndPort("127.0.0.1", 1), probe()));
    }

    @Test
    void anEndpointThatRefusesInfoIsNotASentinel() {
        try (FakeRedisServer node = new FakeRedisServer(c -> FakeRedisServer.startsWith(c, "INFO")
                ? FakeRedisServer.error("NOPERM this user has no permissions to run the 'info' command")
                : FakeRedisServer.ok())) {
            assertNull(Sentinels.probeMasterName(at(node), probe()));
        }
    }

    @Test
    void aSentinelMonitoringNoMasterIsReported() {
        try (FakeRedisServer sentinel = endpoint(ServerModeTest.valkeyInfo("sentinel"), FakeRedisServer.array())) {
            assertThrows(IllegalStateException.class, () -> Sentinels.probeMasterName(at(sentinel), probe()));
        }
    }

    @Test
    void aSentinelThatRefusesSentinelMastersIsReportedNotIgnored() {
        // Once the endpoint has said it is a Sentinel, a direct connection would fail on every graph
        // command, so a refusal propagates rather than falling back.
        try (FakeRedisServer sentinel = endpoint(
                ServerModeTest.valkeyInfo("sentinel"),
                FakeRedisServer.error("NOPERM this user has no permissions to run the 'sentinel|masters' command"))) {
            assertThrows(JedisDataException.class, () -> Sentinels.probeMasterName(at(sentinel), probe()));
        }
    }

    /** Builds a {@code SENTINEL MASTERS}-shaped reply naming each given master. */
    private static List<Map<String, String>> masters(String... names) {
        List<Map<String, String>> masters = new ArrayList<>();
        for (String name : names) {
            Map<String, String> master = new LinkedHashMap<>();
            master.put("name", name);
            master.put("ip", "10.0.0.1");
            master.put("port", "6379");
            masters.add(master);
        }
        return masters;
    }
}
