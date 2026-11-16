package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ServerMode}: reading the mode out of an {@code INFO server} reply from Redis
 * ({@code redis_mode}) and from Valkey ({@code server_mode}). No server is required.
 */
class ServerModeTest {

    /**
     * The head of a real {@code INFO server} reply from {@code falkordb/falkordb:edge} (Valkey 9.1.2),
     * which keeps {@code redis_version} for compatibility but names the mode {@code server_mode}.
     */
    static String valkeyInfo(String mode) {
        return "# Server\r\nredis_version:7.2.4\r\nserver_name:valkey\r\nvalkey_version:9.1.2\r\n"
                + "valkey_release_stage:ga\r\nredis_git_sha1:7f1dffed\r\nredis_git_dirty:1\r\n"
                + "redis_build_id:22ba26550f3540a2\r\nserver_mode:" + mode + "\r\n"
                + "os:Linux 7.0.14-linuxkit aarch64\r\narch_bits:64\r\n";
    }

    /** The head of a real {@code INFO server} reply from {@code falkordb/falkordb:latest} (Redis 8.10.2). */
    static String redisInfo(String mode) {
        return "# Server\r\nredis_version:8.10.2\r\nredis_git_sha1:498ecd0d\r\nredis_git_dirty:1\r\n"
                + "redis_build_id:34b3b3397048fc3f\r\nredis_mode:" + mode
                + "\r\nos:Linux 7.0.14-linuxkit aarch64\r\narch_bits:64\r\n";
    }

    @Test
    void readsRedisMode() {
        assertEquals("standalone", ServerMode.of(redisInfo("standalone")));
        assertEquals("cluster", ServerMode.of(redisInfo("cluster")));
        assertEquals("sentinel", ServerMode.of(redisInfo("sentinel")));
    }

    @Test
    void readsValkeyServerMode() {
        assertEquals("standalone", ServerMode.of(valkeyInfo("standalone")));
        assertEquals("cluster", ServerMode.of(valkeyInfo("cluster")));
        assertEquals("sentinel", ServerMode.of(valkeyInfo("sentinel")));
    }

    @Test
    void toleratesLineEndingsPaddingAndKeepsTheServersCase() {
        assertEquals("cluster", ServerMode.of("# Server\nserver_mode:cluster\n"));
        assertEquals("cluster", ServerMode.of("# Server\rserver_mode:cluster\r"));
        assertEquals("sentinel", ServerMode.of("# Server\r\n  server_mode: sentinel  \r\n"));
        assertEquals("CLUSTER", ServerMode.of("server_mode:CLUSTER"));
    }

    @Test
    void aReplyWithoutAModeHasNone() {
        assertNull(ServerMode.of(null));
        assertNull(ServerMode.of(""));
        assertNull(ServerMode.of("# Server\r\nredis_version:7.2.4\r\nserver_name:valkey\r\n"));
    }

    @Test
    void readsTheModeFieldRatherThanSearchingTheWholeReply() {
        assertEquals(
                "standalone", ServerMode.of("config_file:/etc/server_mode:cluster.conf\r\nserver_mode:standalone\r\n"));
        assertEquals(
                "standalone", ServerMode.of("config_file:/etc/redis_mode:sentinel.conf\r\nredis_mode:standalone\r\n"));
        assertNull(ServerMode.of("x_server_mode:cluster\r\n"), "only a line that starts with the field name counts");
    }

    @Test
    void theFirstModeLineWins() {
        assertEquals("standalone", ServerMode.of("server_mode:standalone\r\nredis_mode:cluster\r\n"));
        assertEquals("cluster", ServerMode.of("redis_mode:cluster\r\nserver_mode:standalone\r\n"));
    }
}
