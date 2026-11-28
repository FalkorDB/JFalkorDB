package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.util.JedisURIHelper;

/** The test-protocol helper itself: no server needed. */
class TestProtocolTest {

    @Test
    void readsTheProtocolFromThePropertyThenTheEnvironment() {
        assertEquals(RedisProtocol.RESP2, TestProtocol.parse(null, null));
        assertEquals(RedisProtocol.RESP2, TestProtocol.parse(" ", ""));
        assertEquals(RedisProtocol.RESP3, TestProtocol.parse(null, "resp3"));
        assertEquals(RedisProtocol.RESP3, TestProtocol.parse("RESP3", "resp2"));
        assertEquals(RedisProtocol.RESP2, TestProtocol.parse("2", "resp3"));
        assertEquals(RedisProtocol.RESP3, TestProtocol.parse(" 3 ", null));
    }

    @Test
    void aValueThatNamesNoProtocolFailsRatherThanTestingResp2Twice() {
        assertThrows(IllegalStateException.class, () -> TestProtocol.parse("resp4", null));
        assertThrows(IllegalStateException.class, () -> TestProtocol.parse(null, "rsp3"));
    }

    @Test
    void aUriKeepsTheHostPortAndProtocolOfEveryKindOfHost() {
        assertUri("localhost", "redis://localhost:6379?protocol=3");
        assertUri("127.0.0.1", "redis://127.0.0.1:6379?protocol=3");
        // an IPv6 literal needs brackets in a URI, or the host can't be told from the port
        assertUri("::1", "redis://[::1]:6379?protocol=3");
        assertUri("[::1]", "redis://[::1]:6379?protocol=3");
    }

    private static void assertUri(String host, String expected) {
        URI uri = TestProtocol.uri(host, 6379, RedisProtocol.RESP3);
        assertEquals(expected, uri.toString());
        HostAndPort parsed = JedisURIHelper.getHostAndPort(uri);
        assertEquals(6379, parsed.getPort());
        assertEquals(RedisProtocol.RESP3, JedisURIHelper.getRedisProtocol(uri));
    }
}
