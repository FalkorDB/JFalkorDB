package com.falkordb.impl.api;

import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;

/**
 * Resolved connection-pool settings for a driver, as gathered by {@code com.falkordb.FalkorDB.builder()}.
 *
 * <p>Internal wiring, like {@link SentinelOptions}: it keeps {@link DriverImpl#create} from growing a run of
 * positional {@code int}/{@code boolean} parameters that would compile just as happily when two were
 * swapped, and it is public only because the {@code com.falkordb} factories live in another package. Users
 * configure these through {@code FalkorDB.builder()}.
 *
 * <p>Instances are immutable and valid: {@link #of} rejects an inconsistent combination.
 */
public final class PoolOptions {

    /**
     * How often the pool's background task runs when {@link #minIdle()} or {@link #testWhileIdle()} needs
     * it: every 30 seconds, as Jedis' {@code JedisPoolConfig} does.
     */
    public static final Duration MAINTENANCE_INTERVAL = Duration.ofSeconds(30);

    private final int maxTotal;
    private final int maxIdle;
    private final int minIdle;
    private final Duration maxWait;
    private final boolean testOnBorrow;
    private final boolean testWhileIdle;

    private PoolOptions(
            int maxTotal, int maxIdle, int minIdle, Duration maxWait, boolean testOnBorrow, boolean testWhileIdle) {
        this.maxTotal = maxTotal;
        this.maxIdle = maxIdle;
        this.minIdle = minIdle;
        this.maxWait = maxWait;
        this.testOnBorrow = testOnBorrow;
        this.testWhileIdle = testWhileIdle;
    }

    /**
     * The defaults every factory has always used: commons-pool2's sizing, an unbounded borrow wait, and no
     * connection checks.
     *
     * @return the default options
     */
    public static PoolOptions defaults() {
        return of(
                DriverImpl.DEFAULT_POOL_MAX_TOTAL,
                DriverImpl.DEFAULT_POOL_MAX_IDLE,
                0,
                DriverImpl.DEFAULT_POOL_MAX_WAIT,
                false,
                false);
    }

    /**
     * Validates and captures pool settings.
     *
     * @param maxTotal      maximum number of connections; at least {@code 1}
     * @param maxIdle       maximum idle connections kept; in {@code [0, maxTotal]}
     * @param minIdle       idle connections kept open; in {@code [0, maxIdle]}
     * @param maxWait       longest wait for a connection when the pool is exhausted (negative = wait
     *                      indefinitely, {@link Duration#ZERO} = fail fast); not {@code null}
     * @param testOnBorrow  whether to check a connection with {@code PING} before handing it out
     * @param testWhileIdle whether to check idle connections with {@code PING} in the background
     * @return the options
     * @throws IllegalArgumentException if a value is out of range or {@code maxWait} is {@code null}
     */
    public static PoolOptions of(
            int maxTotal, int maxIdle, int minIdle, Duration maxWait, boolean testOnBorrow, boolean testWhileIdle) {
        if (maxTotal < 1) {
            throw new IllegalArgumentException("poolMaxTotal must be at least 1, but was " + maxTotal);
        }
        if (maxIdle < 0) {
            throw new IllegalArgumentException("poolMaxIdle must not be negative, but was " + maxIdle);
        }
        if (maxIdle > maxTotal) {
            throw new IllegalArgumentException(
                    "poolMaxIdle (" + maxIdle + ") must not exceed poolMaxTotal (" + maxTotal + ")");
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException("poolMinIdle must not be negative, but was " + minIdle);
        }
        // commons-pool2 would silently cap it at maxIdle instead; say so rather than keep fewer connections
        // open than the caller asked for.
        if (minIdle > maxIdle) {
            throw new IllegalArgumentException(
                    "poolMinIdle (" + minIdle + ") must not exceed poolMaxIdle (" + maxIdle + ")");
        }
        if (maxWait == null) {
            throw new IllegalArgumentException("poolMaxWait must not be null");
        }
        return new PoolOptions(maxTotal, maxIdle, minIdle, maxWait, testOnBorrow, testWhileIdle);
    }

    /** @return the maximum number of connections */
    public int maxTotal() {
        return maxTotal;
    }

    /** @return the maximum number of idle connections kept */
    public int maxIdle() {
        return maxIdle;
    }

    /** @return the number of idle connections kept open */
    public int minIdle() {
        return minIdle;
    }

    /** @return the longest wait for a connection when the pool is exhausted */
    public Duration maxWait() {
        return maxWait;
    }

    /** @return whether a connection is checked before it is handed out */
    public boolean testOnBorrow() {
        return testOnBorrow;
    }

    /** @return whether idle connections are checked in the background */
    public boolean testWhileIdle() {
        return testWhileIdle;
    }

    /**
     * Whether the pool needs commons-pool2's background task (its "evictor"): only it tops the pool up to
     * {@link #minIdle()} and checks idle connections.
     *
     * @return {@code true} if the background task must run
     */
    boolean needsMaintenance() {
        return minIdle > 0 || testWhileIdle;
    }

    /**
     * Builds the commons-pool2 config. With the defaults it is the plain {@link GenericObjectPoolConfig} every
     * earlier release used, background task off. When the task is needed it runs every {@link
     * #MAINTENANCE_INTERVAL}, checks every idle connection, and never closes a connection for having been idle
     * a long time: commons-pool2 would otherwise close those idle for 30 minutes (and {@code JedisPoolConfig}
     * for 60 seconds), a policy nobody asked for by turning on a check.
     *
     * @return a new pool config
     */
    GenericObjectPoolConfig<Jedis> toPoolConfig() {
        GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWait(maxWait);
        config.setTestOnBorrow(testOnBorrow);
        config.setTestWhileIdle(testWhileIdle);
        if (needsMaintenance()) {
            config.setTimeBetweenEvictionRuns(MAINTENANCE_INTERVAL);
            config.setNumTestsPerEvictionRun(-1);
            config.setMinEvictableIdleDuration(Duration.ofMillis(-1));
        }
        return config;
    }
}
