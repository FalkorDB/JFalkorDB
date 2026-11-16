package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.ClientSetInfoConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

/**
 * The two ways {@link JedisPools} differs from constructing Jedis pools directly: a bounded connection check,
 * and a background task that starts only after the pool's constructor returned. No FalkorDB is required.
 */
class JedisPoolsTest {

    private static DefaultJedisClientConfig config(int socketTimeoutMillis) {
        // No CLIENT SETINFO, so opening the connection sends nothing and the check's PING is its first read.
        return DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(socketTimeoutMillis)
                .clientSetInfoConfig(ClientSetInfoConfig.DISABLED)
                .build();
    }

    @Test
    void aCheckOnAConnectionThatNeverAnswersGivesUpInsteadOfHanging() throws Exception {
        // The driver's default socket timeout is "no deadline". Unbounded, this PING would hold commons-pool2's
        // one evictor thread, shared by every pool in the JVM, and close(), until TCP gave up.
        try (FakeServer silent = new FakeServer(FakeServer.Mode.SILENT)) {
            HostAndPort node = new HostAndPort(silent.host(), silent.port());
            DefaultJedisClientConfig noDeadline = config(0);
            JedisPools.CheckedFactory factory = new JedisPools.CheckedFactory(node, noDeadline);
            try (Jedis jedis = new Jedis(node, noDeadline)) {
                boolean valid = assertTimeoutPreemptively(
                        Duration.ofSeconds(10), () -> factory.validateObject(new DefaultPooledObject<>(jedis)));
                assertFalse(valid, "a connection that does not answer PING is not valid");
            }
        }
    }

    @Test
    void aPassedCheckRestoresTheConnectionsOwnReadDeadline() throws Exception {
        try (FakeServer pong = new FakeServer(FakeServer.Mode.ANSWER)) {
            HostAndPort node = new HostAndPort(pong.host(), pong.port());
            DefaultJedisClientConfig noDeadline = config(0);
            JedisPools.CheckedFactory factory = new JedisPools.CheckedFactory(node, noDeadline);
            try (Jedis jedis = new Jedis(node, noDeadline)) {
                assertTrue(factory.validateObject(new DefaultPooledObject<>(jedis)));
                assertEquals(
                        0,
                        jedis.getConnection().getSoTimeout(),
                        "graph queries on this connection must keep having no read deadline");
            }
        }
    }

    @Test
    void theCheckWaitsForTheSocketTimeoutWhenThereIsOne() {
        assertEquals(500, JedisPools.validationTimeoutMillis(config(500)));
        assertEquals(JedisPools.DEFAULT_VALIDATION_TIMEOUT_MILLIS, JedisPools.validationTimeoutMillis(config(0)));
        assertEquals(2000, JedisPools.DEFAULT_VALIDATION_TIMEOUT_MILLIS);
    }

    @Test
    void theBackgroundTaskStartsOnlyOnceThePoolIsConstructed() {
        GenericObjectPoolConfig<Jedis> wanted =
                PoolOptions.of(8, 8, 2, Duration.ofMillis(-1), false, true).toPoolConfig();
        List<GenericObjectPoolConfig<Jedis>> seen = new ArrayList<>();

        JedisPool pool = JedisPools.startMaintenanceAfter(wanted, config -> {
            seen.add(config);
            return new JedisPool(config, new HostAndPort("127.0.0.1", 1), config(0));
        });
        try {
            assertTrue(
                    seen.get(0).getDurationBetweenEvictionRuns().isNegative(),
                    "the constructor must run with the background task off");
            assertEquals(Duration.ofSeconds(30), pool.getDurationBetweenEvictionRuns(), "then it is switched on");
            assertEquals(2, pool.getMinIdle());
            assertTrue(pool.getTestWhileIdle());
            assertEquals(Duration.ofSeconds(30), wanted.getDurationBetweenEvictionRuns(), "the caller's config");
        } finally {
            pool.close();
        }
    }

    @Test
    void aConstructorThatFailsLeavesNoBackgroundTaskBehind() {
        // JedisSentinelPool starts the task in its super constructor and only then looks for the master; when
        // that fails, a running task would keep opening connections to localhost:6379 with the data credentials.
        GenericObjectPoolConfig<Jedis> wanted =
                PoolOptions.of(8, 8, 2, Duration.ofMillis(-1), false, true).toPoolConfig();
        List<GenericObjectPoolConfig<Jedis>> seen = new ArrayList<>();

        assertThrows(
                JedisException.class,
                () -> JedisPools.startMaintenanceAfter(wanted, config -> {
                    seen.add(config);
                    throw new JedisException("All sentinels down");
                }));
        assertTrue(seen.get(0).getDurationBetweenEvictionRuns().isNegative());
    }

    @Test
    void withoutABackgroundTaskTheConfigIsPassedThroughUnchanged() {
        GenericObjectPoolConfig<Jedis> plain = PoolOptions.defaults().toPoolConfig();
        List<GenericObjectPoolConfig<Jedis>> seen = new ArrayList<>();

        JedisPool pool = JedisPools.startMaintenanceAfter(plain, config -> {
            seen.add(config);
            return new JedisPool(config, new HostAndPort("127.0.0.1", 1), config(0));
        });
        pool.close();
        assertSame(plain, seen.get(0));
    }
}
