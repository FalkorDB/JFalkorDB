package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.ClientSetInfoConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

/**
 * The ways {@link JedisPools} differs from constructing Jedis pools directly: opening and checking a connection
 * wait a bounded time, and the background task starts only after the pool's constructor returned. No FalkorDB
 * is required.
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
                // Jedis connects in its constructor, so this exercises the PING, not the "not connected" shortcut.
                assertTrue(jedis.getConnection().isConnected());
                boolean valid = assertTimeoutPreemptively(
                        Duration.ofSeconds(10), () -> factory.validateObject(new DefaultPooledObject<>(jedis)));
                assertFalse(valid, "a connection that does not answer PING is not valid");
            }
        }
    }

    @Test
    void aPassedCheckRestoresTheConnectionsOwnReadDeadline() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(JedisPoolsTest::answer)) {
            HostAndPort node = new HostAndPort("127.0.0.1", server.port());
            DefaultJedisClientConfig noDeadline = config(0);
            JedisPools.CheckedFactory factory = new JedisPools.CheckedFactory(node, noDeadline);
            try (Jedis jedis = new Jedis(node, noDeadline)) {
                assertTrue(jedis.getConnection().isConnected());
                assertTrue(factory.validateObject(new DefaultPooledObject<>(jedis)));
                assertEquals(1, server.count("PING"));
                assertEquals(
                        0,
                        jedis.getConnection().getSoTimeout(),
                        "graph queries on this connection must keep having no read deadline");
            }
        }
    }

    /** Answers PING with PONG and everything else, the connection setup included, with OK. */
    private static String answer(List<String> command) {
        return FakeRedisServer.startsWith(command, "PING") ? "+PONG\r\n" : FakeRedisServer.ok();
    }

    /** A config whose connection setup waits for replies: AUTH and CLIENT SETINFO, with no read deadline. */
    private static DefaultJedisClientConfig authenticating() {
        return DefaultJedisClientConfig.builder()
                .password("s3cret")
                .socketTimeoutMillis(0)
                .build();
    }

    @Test
    void openingAConnectionToAServerThatNeverAnswersGivesUpInsteadOfHanging() throws Exception {
        // Opening the poolMinIdle connections runs on the same JVM-wide thread as the checks, so a server that
        // accepts the connection but never answers AUTH would stall it just the same.
        try (FakeServer silent = new FakeServer(FakeServer.Mode.SILENT)) {
            JedisPools.CheckedFactory factory =
                    new JedisPools.CheckedFactory(new HostAndPort(silent.host(), silent.port()), authenticating());

            assertTimeoutPreemptively(
                    Duration.ofSeconds(10), () -> assertThrows(JedisConnectionException.class, factory::makeObject));
        }
    }

    @Test
    void toppingUpToMinIdleOnAServerThatNeverAnswersGivesUpInsteadOfHanging() throws Exception {
        // ensureMinIdle() is what the background task runs every 30 s; preparePool() runs it now.
        try (FakeServer silent = new FakeServer(FakeServer.Mode.SILENT)) {
            JedisPool pool = JedisPools.direct(
                    PoolOptions.of(8, 8, 1, Duration.ofMillis(-1), false, false).toPoolConfig(),
                    new HostAndPort(silent.host(), silent.port()),
                    authenticating());
            try {
                assertTimeoutPreemptively(
                        Duration.ofSeconds(10), () -> assertThrows(JedisConnectionException.class, pool::preparePool));
            } finally {
                pool.close();
            }
        }
    }

    @Test
    void anOpenedConnectionGetsTheConfiguredReadDeadlineBack() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(JedisPoolsTest::answer)) {
            JedisPools.CheckedFactory factory =
                    new JedisPools.CheckedFactory(new HostAndPort("127.0.0.1", server.port()), authenticating());
            Jedis jedis = factory.makeObject().getObject();
            try {
                assertEquals(1, server.count("AUTH"), "the setup ran");
                assertEquals(0, jedis.getConnection().getSoTimeout(), "graph queries keep having no read deadline");

                // A reconnect opens a socket outside the setup, and must not keep the setup's deadline either.
                jedis.disconnect();
                assertEquals("PONG", jedis.ping());
                assertEquals(0, jedis.getConnection().getSoTimeout());
            } finally {
                jedis.close();
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
    void aConstructorThatFailsLeavesNoBackgroundTaskBehind() throws Exception {
        // JedisSentinelPool starts the task in its super constructor and only then looks for the master; when
        // that fails, a running task would keep opening connections to localhost:6379 with the data credentials.
        GenericObjectPoolConfig<Jedis> wanted =
                PoolOptions.of(8, 8, 2, Duration.ofMillis(-1), false, true).toPoolConfig();
        int before = scheduledMaintenanceTasks();
        try (FakeServer deadSentinel = new FakeServer(FakeServer.Mode.CLOSE)) {
            JedisException failure = assertThrows(
                    JedisException.class,
                    () -> JedisPools.sentinel(
                            "mymaster",
                            Collections.singleton(new HostAndPort(deadSentinel.host(), deadSentinel.port())),
                            wanted,
                            config(0),
                            config(0)));
            assertTrue(failure.getMessage().contains("sentinels down"), failure.getMessage());
        }

        assertEquals(before, scheduledMaintenanceTasks(), "the failed pool's background task is still scheduled");
    }

    /**
     * How many pools' background tasks commons-pool2 has scheduled on its shared thread. A pool that is never
     * closed keeps its entry until the task next runs and finds the pool collected, 30 s later here, so a leak
     * shows up as one more.
     */
    private static int scheduledMaintenanceTasks() throws Exception {
        Method count =
                Class.forName("org.apache.commons.pool2.impl.EvictionTimer").getDeclaredMethod("getNumTasks");
        count.setAccessible(true);
        return (Integer) count.invoke(null);
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
