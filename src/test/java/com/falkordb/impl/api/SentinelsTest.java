package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
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
import redis.clients.jedis.HostAndPort;

/**
 * Unit tests for {@link Sentinels} — the pure half of Sentinel support: recognising a Sentinel from
 * its {@code INFO server} reply, picking the master out of a {@code SENTINEL MASTERS} reply, and
 * parsing {@code host:port} addresses. No server is required; the probe itself is covered end-to-end
 * by {@code SentinelIT}.
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
