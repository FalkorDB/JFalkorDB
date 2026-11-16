package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

/** How {@link PoolOptions} maps onto commons-pool2's config. No server is required. */
class PoolOptionsTest {

    private static PoolOptions options(int minIdle, boolean testOnBorrow, boolean testWhileIdle) {
        return PoolOptions.of(8, 8, minIdle, Duration.ofMillis(-1), testOnBorrow, testWhileIdle);
    }

    @Test
    void defaultsAreThePlainPoolConfigEveryEarlierReleaseUsed() {
        GenericObjectPoolConfig<Jedis> config = PoolOptions.defaults().toPoolConfig();
        GenericObjectPoolConfig<Jedis> plain = new GenericObjectPoolConfig<>();

        assertEquals(plain.getMaxTotal(), config.getMaxTotal());
        assertEquals(plain.getMaxIdle(), config.getMaxIdle());
        assertEquals(plain.getMinIdle(), config.getMinIdle());
        assertEquals(plain.getMaxWaitDuration(), config.getMaxWaitDuration());
        assertEquals(plain.getTestOnBorrow(), config.getTestOnBorrow());
        assertEquals(plain.getTestWhileIdle(), config.getTestWhileIdle());
        assertEquals(plain.getDurationBetweenEvictionRuns(), config.getDurationBetweenEvictionRuns());
        assertEquals(plain.getNumTestsPerEvictionRun(), config.getNumTestsPerEvictionRun());
        assertEquals(plain.getMinEvictableIdleDuration(), config.getMinEvictableIdleDuration());
        assertTrue(
                config.getDurationBetweenEvictionRuns().isNegative(),
                "without minIdle or testWhileIdle, no background task runs");
    }

    @Test
    void minIdleReachesThePoolAndTurnsOnTheBackgroundTask() {
        GenericObjectPoolConfig<Jedis> config = options(3, false, false).toPoolConfig();

        assertEquals(3, config.getMinIdle());
        assertFalse(config.getTestWhileIdle());
        assertBackgroundTaskOn(config);
    }

    @Test
    void testWhileIdleReachesThePoolAndTurnsOnTheBackgroundTask() {
        GenericObjectPoolConfig<Jedis> config = options(0, false, true).toPoolConfig();

        assertTrue(config.getTestWhileIdle());
        assertEquals(0, config.getMinIdle());
        assertBackgroundTaskOn(config);
    }

    @Test
    void testOnBorrowReachesThePoolWithoutABackgroundTask() {
        GenericObjectPoolConfig<Jedis> config = options(0, true, false).toPoolConfig();

        assertTrue(config.getTestOnBorrow());
        assertTrue(config.getDurationBetweenEvictionRuns().isNegative(), "checking on borrow needs no task");
    }

    private static void assertBackgroundTaskOn(GenericObjectPoolConfig<Jedis> config) {
        assertEquals(Duration.ofSeconds(30), config.getDurationBetweenEvictionRuns(), "JedisPoolConfig's interval");
        assertEquals(-1, config.getNumTestsPerEvictionRun(), "every idle connection, each run");
        // Not commons-pool2's 30 minutes or JedisPoolConfig's 60 seconds: turning a check on must not also close
        // connections for having been idle.
        assertTrue(config.getMinEvictableIdleDuration().isNegative(), "never closed for idling");
        assertTrue(config.getSoftMinEvictableIdleDuration().isNegative(), "never closed for idling");
    }

    @Test
    void rejectsAnInconsistentPool() {
        assertThrows(IllegalArgumentException.class, () -> PoolOptions.of(0, 0, 0, Duration.ZERO, false, false));
        assertThrows(IllegalArgumentException.class, () -> PoolOptions.of(8, -1, 0, Duration.ZERO, false, false));
        assertThrows(IllegalArgumentException.class, () -> PoolOptions.of(4, 8, 0, Duration.ZERO, false, false));
        assertThrows(IllegalArgumentException.class, () -> PoolOptions.of(8, 8, -1, Duration.ZERO, false, false));
        assertThrows(IllegalArgumentException.class, () -> PoolOptions.of(8, 4, 5, Duration.ZERO, false, false));
        assertThrows(IllegalArgumentException.class, () -> PoolOptions.of(8, 8, 0, null, false, false));
    }

    @Test
    void acceptsMinIdleEqualToMaxIdle() {
        assertEquals(4, PoolOptions.of(8, 4, 4, Duration.ZERO, false, false).minIdle());
    }
}
