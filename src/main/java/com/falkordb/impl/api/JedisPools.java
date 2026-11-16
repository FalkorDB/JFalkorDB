package com.falkordb.impl.api;

import java.time.Duration;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;

/**
 * Builds the connection pools the driver serves graph commands from: one per standalone server, per cluster
 * node, or per Sentinel-managed master.
 *
 * <p>Two things differ from constructing the Jedis pools directly, both only visible once the pool's
 * connection checks or background task are switched on (see {@link PoolOptions}):
 *
 * <ul>
 *   <li>A connection check ({@code PING}) never waits longer than {@link #validationTimeoutMillis}. The
 *       driver's default socket timeout is "no deadline", which is right for graph queries but not for a
 *       check: commons-pool2 runs every pool's background checks on one thread shared by the whole JVM, so a
 *       {@code PING} on a half-open connection would stall them all, and {@code close()}, for as long as TCP
 *       takes to give up.
 *   <li>The background task starts only once the pool's constructor has returned. {@link JedisSentinelPool}
 *       starts it before it looks for the master, so a constructor that then fails, with every Sentinel down
 *       say, would leave a task behind that keeps opening connections, with the data credentials, to Jedis'
 *       placeholder address {@code localhost:6379}.
 * </ul>
 */
final class JedisPools {

    /**
     * Longest a connection check waits for its reply when the driver has no socket timeout: Jedis' {@link
     * Protocol#DEFAULT_TIMEOUT}, as for the Sentinel and cluster probes.
     */
    static final int DEFAULT_VALIDATION_TIMEOUT_MILLIS = Protocol.DEFAULT_TIMEOUT;

    private JedisPools() {}

    /**
     * A pool of connections to one server or cluster node.
     *
     * @param poolConfig sizing, checks and background task
     * @param node       the server
     * @param config     client config for its connections
     * @return a new pool; it connects lazily
     */
    static JedisPool direct(GenericObjectPoolConfig<Jedis> poolConfig, HostAndPort node, JedisClientConfig config) {
        return startMaintenanceAfter(poolConfig, safe -> new JedisPool(safe, new CheckedFactory(node, config)));
    }

    /**
     * A pool of connections to the master a Sentinel deployment names, following failovers.
     *
     * @param masterName     name of the monitored master
     * @param sentinels      the Sentinels to ask
     * @param poolConfig     sizing, checks and background task
     * @param dataConfig     client config for the connections to the master
     * @param sentinelConfig client config for the connections to the Sentinels
     * @return a new pool
     */
    static JedisSentinelPool sentinel(
            String masterName,
            Set<HostAndPort> sentinels,
            GenericObjectPoolConfig<Jedis> poolConfig,
            JedisClientConfig dataConfig,
            JedisClientConfig sentinelConfig) {
        return startMaintenanceAfter(
                poolConfig,
                safe -> new JedisSentinelPool(
                        masterName,
                        sentinels,
                        safe,
                        // JedisSentinelPool points the factory at the master it finds; until then it holds Jedis'
                        // own placeholder address, as JedisSentinelPool's other constructors do.
                        new CheckedFactory(new HostAndPort(Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT), dataConfig),
                        sentinelConfig));
    }

    /**
     * Constructs a pool with the background task off, then applies the full config, which starts it. A
     * constructor that throws therefore leaves no task behind.
     *
     * @param poolConfig the config the pool should end up with
     * @param construct  builds the pool from the config it is given
     * @param <P>        the pool type
     * @return the pool, configured with {@code poolConfig}
     */
    static <P extends GenericObjectPool<Jedis>> P startMaintenanceAfter(
            GenericObjectPoolConfig<Jedis> poolConfig, Function<GenericObjectPoolConfig<Jedis>, P> construct) {
        if (!poolConfig.getDurationBetweenEvictionRuns().isNegative()
                && !poolConfig.getDurationBetweenEvictionRuns().isZero()) {
            GenericObjectPoolConfig<Jedis> safe = poolConfig.clone();
            safe.setTimeBetweenEvictionRuns(Duration.ofMillis(-1));
            P pool = construct.apply(safe);
            pool.setConfig(poolConfig);
            return pool;
        }
        return construct.apply(poolConfig);
    }

    /**
     * The read deadline for a connection check: the driver's socket timeout when one is set, otherwise {@link
     * #DEFAULT_VALIDATION_TIMEOUT_MILLIS}.
     *
     * @param config the connections' client config
     * @return the deadline in milliseconds, always positive
     */
    static int validationTimeoutMillis(JedisClientConfig config) {
        int socketTimeout = config.getSocketTimeoutMillis();
        return socketTimeout > 0 ? socketTimeout : DEFAULT_VALIDATION_TIMEOUT_MILLIS;
    }

    /**
     * Jedis' connection factory with a read deadline on its connection check. Everything else, creating,
     * authenticating and destroying connections, is Jedis' own.
     */
    static final class CheckedFactory extends JedisFactory {

        private final int validationTimeoutMillis;

        CheckedFactory(HostAndPort node, JedisClientConfig config) {
            super(node, config);
            this.validationTimeoutMillis = validationTimeoutMillis(config);
        }

        @Override
        public boolean validateObject(PooledObject<Jedis> pooledJedis) {
            Connection connection = pooledJedis.getObject().getConnection();
            int previous = connection.getSoTimeout();
            try {
                connection.setSoTimeout(validationTimeoutMillis);
            } catch (RuntimeException e) {
                // The socket is already unusable; Jedis' own check would only report the same.
                return false;
            }
            boolean valid = super.validateObject(pooledJedis);
            if (valid) {
                try {
                    connection.setSoTimeout(previous);
                } catch (RuntimeException e) {
                    return false;
                }
            }
            // A connection that failed the check is destroyed, so its deadline no longer matters.
            return valid;
        }
    }
}
