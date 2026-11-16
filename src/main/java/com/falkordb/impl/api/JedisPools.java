package com.falkordb.impl.api;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultJedisSocketFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Builds the connection pools the driver serves graph commands from: one per standalone server, per cluster
 * node, or per Sentinel-managed master.
 *
 * <p>Two things differ from constructing the Jedis pools directly, both only visible once the pool's
 * connection checks or background task are switched on (see {@link PoolOptions}):
 *
 * <ul>
 *   <li>Opening a pooled connection (the TLS handshake and the {@code AUTH}, {@code CLIENT SETINFO} and
 *       {@code SELECT} replies) and checking one ({@code PING}) never wait longer than {@link
 *       #validationTimeoutMillis} for a reply. The driver's default socket timeout is "no deadline", which is
 *       right for graph queries but not here: commons-pool2 opens the {@code minIdle} connections and runs the
 *       checks of every pool on one thread shared by the whole JVM, so a server that accepts connections but
 *       never answers would stall all of them, and {@code close()}, for as long as TCP takes to give up.
 *       Once a connection is open it gets the configured socket timeout back.
 *   <li>The background task starts only once the pool's constructor has returned. {@link JedisSentinelPool}
 *       starts it before it looks for the master, so a constructor that then fails, with every Sentinel down
 *       say, would leave a task behind that keeps opening connections, with the data credentials, to Jedis'
 *       placeholder address {@code localhost:6379}.
 * </ul>
 */
final class JedisPools {

    /**
     * Longest opening or checking a connection waits for a reply when the driver has no socket timeout: Jedis'
     * {@link Protocol#DEFAULT_TIMEOUT}, as for the Sentinel and cluster probes.
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
     * The read deadline while a pooled connection is opened or checked: the driver's socket timeout when one is
     * set, otherwise {@link #DEFAULT_VALIDATION_TIMEOUT_MILLIS}.
     *
     * @param config the connections' client config
     * @return the deadline in milliseconds, always positive
     */
    static int validationTimeoutMillis(JedisClientConfig config) {
        int socketTimeout = config.getSocketTimeoutMillis();
        return socketTimeout > 0 ? socketTimeout : DEFAULT_VALIDATION_TIMEOUT_MILLIS;
    }

    /**
     * Jedis' connection factory with a read deadline on opening a connection and on its connection check.
     * Everything else, authenticating, selecting the database and destroying connections, is Jedis' own.
     */
    static final class CheckedFactory extends JedisFactory {

        private final int validationTimeoutMillis;
        private final int socketTimeoutMillis;

        CheckedFactory(HostAndPort node, JedisClientConfig config) {
            super(new BoundedSockets(node, config), config);
            this.validationTimeoutMillis = validationTimeoutMillis(config);
            this.socketTimeoutMillis = config.getSocketTimeoutMillis();
        }

        @Override
        public PooledObject<Jedis> makeObject() throws Exception {
            PooledObject<Jedis> created;
            BoundedSockets.OPENING.set(Boolean.TRUE);
            try {
                created = super.makeObject();
            } finally {
                BoundedSockets.OPENING.remove();
            }
            Jedis jedis = created.getObject();
            try {
                jedis.getConnection().setSoTimeout(socketTimeoutMillis);
            } catch (RuntimeException e) {
                jedis.close();
                throw e;
            }
            return created;
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

    /**
     * Jedis' socket factory, with the read deadline bounded while {@link CheckedFactory#makeObject} opens a
     * connection, TLS handshake included. A socket opened at any other time, when Jedis reconnects a connection
     * it had closed, gets the configured socket timeout, so a graph query on it keeps having no deadline when
     * none is configured.
     *
     * <p>It stays a {@link DefaultJedisSocketFactory}: {@link JedisSentinelPool} re-points that type at the
     * master it finds, and refuses any other.
     */
    static final class BoundedSockets extends DefaultJedisSocketFactory {

        /** Set while the current thread runs {@link CheckedFactory#makeObject}. */
        static final ThreadLocal<Boolean> OPENING = new ThreadLocal<>();

        private final int socketTimeoutMillis;

        @SuppressWarnings("deprecation") // JedisFactory builds its own socket factory with this constructor
        BoundedSockets(HostAndPort node, JedisClientConfig config) {
            super(
                    node,
                    DefaultJedisClientConfig.builder()
                            .from(config)
                            .socketTimeoutMillis(validationTimeoutMillis(config))
                            .build());
            this.socketTimeoutMillis = config.getSocketTimeoutMillis();
        }

        @Override
        public Socket createSocket() {
            Socket socket = super.createSocket();
            if (OPENING.get() == null) {
                try {
                    socket.setSoTimeout(socketTimeoutMillis);
                } catch (SocketException e) {
                    try {
                        socket.close();
                    } catch (IOException suppressed) {
                        e.addSuppressed(suppressed);
                    }
                    throw new JedisConnectionException(e);
                }
            }
            return socket;
        }
    }
}
