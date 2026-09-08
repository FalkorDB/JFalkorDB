package com.falkordb.impl.api;

import com.falkordb.Driver;
import com.falkordb.impl.ConnectionUris;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.SslOptions;
import redis.clients.jedis.exceptions.InvalidURIException;
import redis.clients.jedis.util.JedisURIHelper;
import redis.clients.jedis.util.Pool;
import redis.clients.jedis.util.SafeEncoder;

/**
 * A FalkorDB Driver for managing graphs and connections.
 */
public class DriverImpl implements Driver {

    /**
     * Socket (read) timeout applied to connections created by this driver: 0 means no
     * client-side deadline. Graph queries routinely exceed Jedis' default 2000ms socket
     * timeout (e.g. LOAD CSV, deep traversals), which would otherwise cut the connection
     * with a read timeout while the server keeps executing the query - so retrying could
     * duplicate writes. Query duration is governed by the server's TIMEOUT /
     * TIMEOUT_DEFAULT configuration (or a per-query timeout) instead, matching the other
     * FalkorDB clients. To use a different socket timeout, build your own pool and pass
     * it to {@link #DriverImpl(Pool)}. Also the default socket timeout applied by {@link #create}
     * when the caller does not specify one.
     */
    public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 0;

    /**
     * Default connection (connect) timeout in milliseconds applied by {@link #create} when the
     * caller does not specify one: Jedis' {@link Protocol#DEFAULT_TIMEOUT} (2000&nbsp;ms).
     */
    public static final int DEFAULT_CONNECTION_TIMEOUT_MILLIS = Protocol.DEFAULT_TIMEOUT;

    /** Default maximum pool size ({@code maxTotal}) applied by {@link #create}: commons-pool2's {@code 8}. */
    public static final int DEFAULT_POOL_MAX_TOTAL = GenericObjectPoolConfig.DEFAULT_MAX_TOTAL;

    /** Default maximum idle connections ({@code maxIdle}) applied by {@link #create}: commons-pool2's {@code 8}. */
    public static final int DEFAULT_POOL_MAX_IDLE = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;

    /**
     * Default pool borrow wait ({@code maxWait}) applied by {@link #create}: commons-pool2's
     * {@code -1ms}, i.e. wait indefinitely for a connection when the pool is exhausted.
     */
    public static final Duration DEFAULT_POOL_MAX_WAIT = GenericObjectPoolConfig.DEFAULT_MAX_WAIT;

    /**
     * Read deadline for the one-shot Sentinel probe when the driver's own socket timeout is the
     * default {@link #DEFAULT_SOCKET_TIMEOUT_MILLIS} (0 = no deadline). That default is right for
     * graph queries, which may legitimately run for minutes, but wrong for a probe: an endpoint that
     * completes the TCP handshake and then never answers would hang the first query forever. Jedis'
     * {@link Protocol#DEFAULT_TIMEOUT} is a deliberate reuse — the probe is a single round-trip, so
     * the same bound that is considered enough to establish a connection is enough to answer it.
     */
    public static final int DEFAULT_SENTINEL_PROBE_TIMEOUT_MILLIS = Protocol.DEFAULT_TIMEOUT;

    /**
     * Read deadline for the connections to the Sentinels themselves: none, whatever the data
     * connections use. Once discovery is done, the only thing that connection does is hold a
     * {@code SUBSCRIBE} open for {@code +switch-master}, so a socket timeout there bounds nothing —
     * it just expires the subscription on a timer. See {@link #sentinelClientConfig}.
     */
    public static final int SENTINEL_SOCKET_TIMEOUT_MILLIS = 0;

    /**
     * The pool actually in use. Resolved on first demand rather than in the constructor, because
     * Sentinel {@linkplain Sentinels#detect detection} needs a round-trip and driver creation has
     * always been I/O-free: a {@link JedisPool} connects lazily, so building a driver against a server
     * that is not up yet has always been legal and must stay that way. See {@link #pool()}.
     */
    private final AtomicReference<Pool<Jedis>> resolvedPool = new AtomicReference<>();

    /** Builds the pool the first time one is needed. */
    private final Supplier<Pool<Jedis>> poolFactory;

    /** Set by {@link #close()}, so a closed driver never silently rebuilds its pool. */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates a client running on the specific host/port
     *
     * @param host Server host
     * @param port Server port
     */
    public DriverImpl(String host, int port) {
        this(new JedisPool(new HostAndPort(host, port), clientConfig(null, null)));
    }

    /**
     * Creates a client running on the specific host/port
     *
     * @param host     Server host
     * @param port     Server port
     * @param user     username
     * @param password password
     */
    public DriverImpl(String host, int port, String user, final String password) {
        this(new JedisPool(new HostAndPort(host, port), clientConfig(user, password)));
    }

    /**
     * Creates a client using the specific uri
     *
     * @param uri server uri
     */
    public DriverImpl(URI uri) {
        this(uriPool(uri));
    }

    /**
     * Builds the pool behind {@link #DriverImpl(URI)}. Host, port, credentials, database index and
     * TLS are taken from the URI exactly as Jedis' own URI constructor would resolve them; going
     * through an explicit {@link DefaultJedisClientConfig} (instead of Jedis' URI-based pool
     * constructor) is what lets this factory share {@link #buildClientConfigBuilder}'s RESP2 pin
     * with every other factory.
     */
    private static JedisPool uriPool(URI uri) {
        requireValidUri(uri);
        return new JedisPool(
                new GenericObjectPoolConfig<Jedis>(), JedisURIHelper.getHostAndPort(uri), uriClientConfig(uri));
    }

    /**
     * Rejects a URI Jedis would not accept, standing in for the check Jedis' own URI-based pool
     * constructor performed before this driver assembled the client config itself.
     */
    private static void requireValidUri(URI uri) {
        if (!JedisURIHelper.isValid(uri)) {
            // Deliberately not Jedis' wording. Its message quotes the URI verbatim, which puts any
            // embedded password into the message and every stack trace that carries it -- same
            // reasoning as DriverEnvironment: the rejected value is worth reporting, its credentials
            // are not. Since the text has to change anyway, it is also spelled "due to" rather than
            // reproducing the "due invalid URI" of the original.
            throw new InvalidURIException(
                    String.format("Cannot open Redis connection due to invalid URI. %s", ConnectionUris.redact(uri)));
        }
    }

    /**
     * Builds the client configuration used by {@link #DriverImpl(URI)}: JFalkorDB's timeout defaults
     * plus the credentials, database index, TLS scheme and explicit protocol carried by the URI.
     *
     * @param uri a URI already validated by {@link JedisURIHelper#isValid}
     * @return the client config to connect with
     */
    static DefaultJedisClientConfig uriClientConfig(URI uri) {
        DefaultJedisClientConfig.Builder builder = buildClientConfigBuilder(
                        JedisURIHelper.getUser(uri),
                        JedisURIHelper.getPassword(uri),
                        JedisURIHelper.isRedisSSLScheme(uri),
                        DEFAULT_CONNECTION_TIMEOUT_MILLIS,
                        DEFAULT_SOCKET_TIMEOUT_MILLIS)
                .database(JedisURIHelper.getDBIndex(uri))
                .protocol(JedisURIHelper.getRedisProtocol(uri));
        return builder.build();
    }

    /**
     * Connects to a seed host/port, transparently switching to Redis Sentinel per {@code sentinel}.
     *
     * <p>This is what the {@code com.falkordb.FalkorDB.driver(...)} factories call, and it is where
     * Sentinel auto-detection lives. The {@link #DriverImpl(String, int) constructors} deliberately do
     * not probe: they are pure wiring around a lazily-connecting {@link JedisPool}, and callers that
     * hold one directly have already chosen their endpoint.
     *
     * @param host     server host
     * @param port     server port
     * @param user     username, or {@code null} for none
     * @param password password, or {@code null} for none
     * @param sentinel how to treat the endpoint with respect to Sentinel
     * @return a new driver, backed by a direct or a failover-aware pool as {@code sentinel} dictates
     */
    public static Driver connect(
            String host, int port, @Nullable String user, @Nullable String password, SentinelOptions sentinel) {
        DefaultJedisClientConfig dataConfig = clientConfig(user, password);
        HostAndPort seed = new HostAndPort(host, port);
        return new DriverImpl(() -> resolvePool(seed, new GenericObjectPoolConfig<Jedis>(), dataConfig, sentinel));
    }

    /**
     * Connects to a seed URI, transparently switching to Redis Sentinel per {@code sentinel}. The
     * URI's credentials, database index and TLS scheme are carried over to the master connections.
     *
     * @param uri      server uri
     * @param sentinel how to treat the endpoint with respect to Sentinel
     * @return a new driver, backed by a direct or a failover-aware pool as {@code sentinel} dictates
     * @throws InvalidURIException if the URI is not a valid Redis connection URI
     */
    public static Driver connect(URI uri, SentinelOptions sentinel) {
        requireValidUri(uri);
        HostAndPort seed = JedisURIHelper.getHostAndPort(uri);
        DefaultJedisClientConfig dataConfig = uriClientConfig(uri);
        return new DriverImpl(() -> resolvePool(seed, new GenericObjectPoolConfig<Jedis>(), dataConfig, sentinel));
    }

    /**
     * Chooses the pool a seed endpoint should be served by.
     *
     * <p>Explicit configuration wins outright and never probes — the caller has already said what the
     * deployment looks like, and can list more Sentinels than a single seed could reveal. Otherwise
     * the endpoint is probed only if auto-detection is on, and anything less than a positive
     * identification leaves us on the direct pool that every previous release would have built.
     */
    private static Pool<Jedis> resolvePool(
            HostAndPort seed,
            GenericObjectPoolConfig<Jedis> poolConfig,
            DefaultJedisClientConfig dataConfig,
            SentinelOptions sentinel) {
        if (sentinel.isExplicit()) {
            return Sentinels.pool(
                    sentinel.masterName(),
                    sentinel.addresses(),
                    poolConfig,
                    dataConfig,
                    sentinelClientConfig(dataConfig, sentinel));
        }
        if (sentinel.isAutoDetect()) {
            Pool<Jedis> detected = Sentinels.detect(
                    seed,
                    probeClientConfig(dataConfig, sentinel),
                    poolConfig,
                    dataConfig,
                    sentinelClientConfig(dataConfig, sentinel));
            if (detected != null) {
                return detected;
            }
        }
        return new JedisPool(poolConfig, seed, dataConfig);
    }

    /**
     * The config used for connections to the Sentinels themselves: the data connections' transport
     * settings (TLS and timeouts) with the Sentinel credentials substituted when they were given.
     *
     * <p>Rebuilt rather than reused even when the credentials are identical, because the data config
     * may carry a database index — {@code driver(URI)} takes one from the URI's path — and Jedis
     * issues {@code SELECT} for any non-zero index on every connection it opens, including the ones to
     * the Sentinels. Sentinel implements no {@code SELECT}, and {@code JedisSentinelPool} reports the
     * resulting error as the Sentinel being unreachable, so a perfectly healthy deployment would look
     * dead. A Sentinel has no keyspace to select in any case.
     *
     * <p>The read deadline is forced off — {@link #SENTINEL_SOCKET_TIMEOUT_MILLIS} — rather than
     * inherited. Once discovery is over, the only thing this connection does is hold {@link
     * JedisSentinelPool}'s master listener {@code SUBSCRIBE}d to {@code +switch-master}, so a socket
     * timeout of {@code T} bounds no request: it expires the subscription every {@code T}, and the
     * listener responds by logging a warning and sleeping five seconds before resubscribing. A data
     * socket timeout would therefore buy nothing and cost a permanent cycle of log noise and windows
     * with nobody listening for failovers. Bounding the read is the one-shot {@linkplain
     * #probeClientConfig probe}'s job, not this connection's.
     */
    static DefaultJedisClientConfig sentinelClientConfig(
            DefaultJedisClientConfig dataConfig, SentinelOptions sentinel) {
        // Without Sentinel credentials, reuse the data ones as falkordb-go does, so the common
        // single-ACL deployment needs no extra configuration.
        boolean ownCredentials = sentinel.hasCredentials();
        return buildClientConfig(
                ownCredentials ? sentinel.user() : dataConfig.getUser(),
                ownCredentials ? sentinel.password() : dataConfig.getPassword(),
                dataConfig.getSslOptions() != null,
                dataConfig.getConnectionTimeoutMillis(),
                SENTINEL_SOCKET_TIMEOUT_MILLIS);
    }

    /**
     * The config used for the one-shot detection probe: the Sentinel config with a read deadline
     * forced on, so an endpoint that accepts connections but never answers cannot hang driver
     * creation. An explicitly configured socket timeout is respected; the driver's default of "no
     * deadline" is replaced by {@link #DEFAULT_SENTINEL_PROBE_TIMEOUT_MILLIS}.
     */
    static DefaultJedisClientConfig probeClientConfig(DefaultJedisClientConfig dataConfig, SentinelOptions sentinel) {
        DefaultJedisClientConfig base = sentinelClientConfig(dataConfig, sentinel);
        // Read the caller's choice off dataConfig, not off base: base's deadline is forced off for
        // the subscription's sake, so deriving from it would silently ignore an explicit timeout.
        int socketTimeoutMillis = dataConfig.getSocketTimeoutMillis() > 0
                ? dataConfig.getSocketTimeoutMillis()
                : DEFAULT_SENTINEL_PROBE_TIMEOUT_MILLIS;
        return buildClientConfig(
                base.getUser(),
                base.getPassword(),
                base.getSslOptions() != null,
                base.getConnectionTimeoutMillis(),
                socketTimeoutMillis);
    }

    /**
     * Builds the client configuration used by the legacy host/port factories: Jedis' default
     * connection timeout, no socket (read) timeout (see {@link #DEFAULT_SOCKET_TIMEOUT_MILLIS}),
     * and the given credentials when provided. Delegates to {@link #buildClientConfig} so the
     * {@code driver(host, port[, user, password])} factories and the {@code FalkorDB.builder()}
     * defaults resolve to an identical configuration.
     */
    static DefaultJedisClientConfig clientConfig(String user, final String password) {
        return buildClientConfig(
                user, password, false, DEFAULT_CONNECTION_TIMEOUT_MILLIS, DEFAULT_SOCKET_TIMEOUT_MILLIS);
    }

    /**
     * Creates a driver from already-resolved connection settings (used by {@code FalkorDB.builder()}).
     *
     * <p>Validates its arguments, then assembles a Jedis client config (credentials, optional TLS, and
     * the two timeouts) and a commons-pool2 pool config (sizing plus borrow-wait), and wraps a {@link
     * JedisPool} built from them. This is internal wiring — prefer {@code com.falkordb.FalkorDB.builder()}
     * over calling it directly.
     *
     * @param host                     server host
     * @param port                     server port
     * @param user                     username, or {@code null} for none
     * @param password                 password, or {@code null} for none
     * @param ssl                      whether to connect over TLS
     * @param connectionTimeoutMillis  connection (connect) timeout in milliseconds
     * @param socketTimeoutMillis      socket (read) timeout in milliseconds ({@code 0} = no deadline)
     * @param poolMaxTotal             maximum pool size
     * @param poolMaxIdle              maximum idle connections in the pool
     * @param poolMaxWait              maximum time to wait for a connection when the pool is exhausted
     *                                 (negative = wait indefinitely, {@link Duration#ZERO} = fail fast)
     * @return a new driver backed by the assembled pool
     * @throws IllegalArgumentException if {@code host} is null/blank, {@code port} is outside
     *                                  {@code [1, 65535]}, the pool sizing is invalid, a timeout is
     *                                  negative, or {@code poolMaxWait} is null
     */
    public static Driver create(
            String host,
            int port,
            String user,
            String password,
            boolean ssl,
            int connectionTimeoutMillis,
            int socketTimeoutMillis,
            int poolMaxTotal,
            int poolMaxIdle,
            Duration poolMaxWait) {
        return create(
                host,
                port,
                user,
                password,
                ssl,
                connectionTimeoutMillis,
                socketTimeoutMillis,
                poolMaxTotal,
                poolMaxIdle,
                poolMaxWait,
                SentinelOptions.autoDetect());
    }

    /**
     * Creates a driver from already-resolved connection settings, including how to treat Redis
     * Sentinel. This is the overload {@code FalkorDB.builder()} actually calls; the {@linkplain
     * #create(String, int, String, String, boolean, int, int, int, int, Duration) shorter one}
     * delegates here with {@linkplain SentinelOptions#autoDetect() auto-detection}, which is the
     * builder's default.
     *
     * @param host                     server host (the seed endpoint, ignored when {@code sentinel} is
     *                                 {@linkplain SentinelOptions#explicit explicit}, though still
     *                                 range-checked)
     * @param port                     server port
     * @param user                     username, or {@code null} for none
     * @param password                 password, or {@code null} for none
     * @param ssl                      whether to connect over TLS
     * @param connectionTimeoutMillis  connection (connect) timeout in milliseconds
     * @param socketTimeoutMillis      socket (read) timeout in milliseconds ({@code 0} = no deadline)
     * @param poolMaxTotal             maximum pool size
     * @param poolMaxIdle              maximum idle connections in the pool
     * @param poolMaxWait              maximum time to wait for a connection when the pool is exhausted
     * @param sentinel                 how to treat the endpoint with respect to Sentinel
     * @return a new driver backed by the assembled pool
     * @throws IllegalArgumentException if {@code host} is null/blank, {@code port} is outside
     *                                  {@code [1, 65535]}, the pool sizing is invalid, a timeout is
     *                                  negative, {@code poolMaxWait} is null, or {@code sentinel} is
     *                                  null or carries an unparseable address
     */
    public static Driver create(
            String host,
            int port,
            String user,
            String password,
            boolean ssl,
            int connectionTimeoutMillis,
            int socketTimeoutMillis,
            int poolMaxTotal,
            int poolMaxIdle,
            Duration poolMaxWait,
            SentinelOptions sentinel) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        String normalizedHost = host.trim();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be in [1, 65535], but was " + port);
        }
        if (poolMaxTotal < 1) {
            throw new IllegalArgumentException("poolMaxTotal must be at least 1, but was " + poolMaxTotal);
        }
        if (poolMaxIdle < 0) {
            throw new IllegalArgumentException("poolMaxIdle must not be negative, but was " + poolMaxIdle);
        }
        if (poolMaxIdle > poolMaxTotal) {
            throw new IllegalArgumentException(
                    "poolMaxIdle (" + poolMaxIdle + ") must not exceed poolMaxTotal (" + poolMaxTotal + ")");
        }
        if (connectionTimeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "connectionTimeoutMillis must not be negative, but was " + connectionTimeoutMillis);
        }
        if (socketTimeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "socketTimeoutMillis must not be negative, but was " + socketTimeoutMillis);
        }
        if (poolMaxWait == null) {
            throw new IllegalArgumentException("poolMaxWait must not be null");
        }
        if (sentinel == null) {
            throw new IllegalArgumentException("sentinel must not be null");
        }
        HostAndPort seed = new HostAndPort(normalizedHost, port);
        GenericObjectPoolConfig<Jedis> poolConfig = buildPoolConfig(poolMaxTotal, poolMaxIdle, poolMaxWait);
        DefaultJedisClientConfig dataConfig =
                buildClientConfig(user, password, ssl, connectionTimeoutMillis, socketTimeoutMillis);
        return new DriverImpl(() -> resolvePool(seed, poolConfig, dataConfig, sentinel));
    }

    /**
     * Builds a Jedis client config from resolved settings. TLS is enabled through the non-deprecated
     * {@link SslOptions#defaults()} path rather than the deprecated {@code ssl(boolean)} setter.
     */
    static DefaultJedisClientConfig buildClientConfig(
            String user, String password, boolean ssl, int connectionTimeoutMillis, int socketTimeoutMillis) {
        return buildClientConfigBuilder(user, password, ssl, connectionTimeoutMillis, socketTimeoutMillis)
                .build();
    }

    /**
     * Shared assembly for every client config this driver builds, so the host/port, builder and URI
     * factories cannot drift apart.
     *
     * <p>Protocol auto-negotiation is switched off deliberately. Jedis 8 turned it on by default, but
     * the legacy {@link Jedis} class this driver pools cannot speak RESP3: it ignores the flag,
     * silently stays on RESP2 and logs a warning for every connection it opens. JFalkorDB's reply
     * parsing is written against those RESP2 shapes, so pinning the flag off keeps the wire protocol
     * explicit and keeps the warning out of our users' logs.
     */
    private static DefaultJedisClientConfig.Builder buildClientConfigBuilder(
            String user, String password, boolean ssl, int connectionTimeoutMillis, int socketTimeoutMillis) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(connectionTimeoutMillis)
                .socketTimeoutMillis(socketTimeoutMillis)
                .autoNegotiateProtocol(false);
        if (user != null) {
            builder.user(user);
        }
        if (password != null) {
            builder.password(password);
        }
        if (ssl) {
            builder.sslOptions(SslOptions.defaults());
        }
        return builder;
    }

    /**
     * Builds a commons-pool2 config from resolved sizing settings. {@code maxWait} is passed through
     * as a {@link Duration} with commons-pool2's native semantics (negative = wait indefinitely,
     * {@link Duration#ZERO} = fail fast when exhausted).
     */
    static GenericObjectPoolConfig<Jedis> buildPoolConfig(int maxTotal, int maxIdle, Duration maxWait) {
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMaxWait(maxWait);
        return poolConfig;
    }

    /**
     * Creates a client wrapping existing JedisPool
     * Should be used when you need to share the same pool between different clients
     *
     * Notice: might be changed in the future
     *
     * @param pool jedis pool to wrap
     */
    public DriverImpl(Pool<Jedis> pool) {
        this.poolFactory = () -> pool;
        this.resolvedPool.set(pool);
    }

    /**
     * Creates a client whose pool is built on first use, so that Sentinel detection — which needs a
     * round-trip — never happens while merely constructing a driver.
     *
     * @param poolFactory builds the pool the first time a connection is borrowed
     */
    private DriverImpl(Supplier<Pool<Jedis>> poolFactory) {
        this.poolFactory = poolFactory;
    }

    /**
     * The pool, building it on first demand.
     *
     * <p>Lock-free on purpose. The obvious {@code synchronized} memoisation would hold a monitor
     * across the Sentinel probe's network I/O, which pins a virtual thread's carrier — exactly what
     * the {@code pin-check} gate exists to prevent. Two threads racing here therefore both build a
     * pool and one is discarded, which is cheap and happens at most once per driver.
     *
     * <p>A closed driver never builds a pool. Without that check, borrowing from a driver that was
     * closed before it was ever used would run the whole resolution — Sentinel probe included — only
     * to hand back a pool it immediately closes: real network I/O after {@code close()}, reported as
     * an exhausted-pool error rather than as the programming mistake it is. (A driver closed *after*
     * being used keeps reporting that through Jedis, as it always has.)
     *
     * <p>{@code resolvedPool} is write-once: it goes from null to a pool and never back. That is what
     * lets the loser of the race read the winner's pool without a retry and without ever seeing null.
     * A pool closed underneath us stays published — the driver is closed, so reporting that through
     * Jedis is the same thing that happens to a driver closed after being used.
     */
    private Pool<Jedis> pool() {
        Pool<Jedis> existing = resolvedPool.get();
        if (existing != null) {
            return existing;
        }
        requireOpen();
        Pool<Jedis> created = poolFactory.get();
        if (!resolvedPool.compareAndSet(null, created)) {
            closeQuietly(created);
            return resolvedPool.get();
        }
        if (closed.get()) {
            // close() ran while this pool was being built. If it read resolvedPool before the CAS
            // above it saw nothing to close, so closing here is what prevents a leak; if it read
            // after, it closed this pool already and doing so again is harmless.
            closeQuietly(created);
        }
        return created;
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("the driver is closed");
        }
    }

    private static void closeQuietly(Pool<Jedis> pool) {
        try {
            pool.close();
        } catch (RuntimeException ignored) {
            // Best-effort disposal of a pool that lost the creation race or was closed concurrently.
        }
    }

    @Override
    public GraphImpl graph(String graphId) {
        return new GraphImpl(this, graphId);
    }

    @Override
    public Jedis getConnection() {
        return pool().getResource();
    }

    /**
     * Lists all graphs in the database
     *
     * @return a list of graph names
     */
    @Override
    public List<String> listGraphs() {
        try (Jedis conn = getConnection()) {
            Object response = conn.sendCommand(GraphCommand.LIST);
            return parseListResponse(response);
        }
    }

    /**
     * Loads a User Defined Function (UDF) library.
     *
     * @param libraryName The name of the UDF library
     * @param script The JavaScript code containing the UDF functions
     * @param replace Whether to replace an existing library with the same name
     * @return true if the library was loaded successfully
     * @throws redis.clients.jedis.exceptions.JedisDataException if loading fails
     */
    @Override
    public boolean udfLoad(String libraryName, String script, boolean replace) {
        try (Jedis conn = getConnection()) {
            Object response;
            if (replace) {
                response = conn.sendCommand(GraphCommand.UDF, "LOAD", "REPLACE", libraryName, script);
            } else {
                response = conn.sendCommand(GraphCommand.UDF, "LOAD", libraryName, script);
            }
            // Validate response
            if (response == null) {
                return false;
            }
            String status;
            if (response instanceof byte[]) {
                status = SafeEncoder.encode((byte[]) response);
            } else {
                status = response.toString();
            }
            return "OK".equalsIgnoreCase(status);
        }
    }

    /**
     * Lists all loaded UDF libraries.
     *
     * @return a list of UDF library information
     */
    @Override
    public List<Object> udfList() {
        try (Jedis conn = getConnection()) {
            Object response = conn.sendCommand(GraphCommand.UDF, "LIST");
            if (response instanceof List<?>) {
                return (List<Object>) response;
            }
            return new ArrayList<>();
        }
    }

    /**
     * Lists UDF libraries with optional filters.
     *
     * @param libraryName Optional library name to filter results
     * @param withCode Whether to include the code in the response
     * @return a list of UDF library information
     */
    @Override
    public List<Object> udfList(String libraryName, boolean withCode) {
        try (Jedis conn = getConnection()) {
            Object response;
            if (libraryName != null && withCode) {
                response = conn.sendCommand(GraphCommand.UDF, "LIST", libraryName, "WITHCODE");
            } else if (libraryName != null) {
                response = conn.sendCommand(GraphCommand.UDF, "LIST", libraryName);
            } else if (withCode) {
                response = conn.sendCommand(GraphCommand.UDF, "LIST", "WITHCODE");
            } else {
                return udfList();
            }

            if (response instanceof List<?>) {
                return (List<Object>) response;
            }
            return new ArrayList<>();
        }
    }

    /**
     * Flushes all loaded UDF libraries.
     *
     * @return true if libraries were flushed successfully
     * @throws redis.clients.jedis.exceptions.JedisDataException if flushing fails
     */
    @Override
    public boolean udfFlush() {
        try (Jedis conn = getConnection()) {
            Object response = conn.sendCommand(GraphCommand.UDF, "FLUSH");
            // Validate response
            if (response == null) {
                return false;
            }
            String status;
            if (response instanceof byte[]) {
                status = SafeEncoder.encode((byte[]) response);
            } else {
                status = response.toString();
            }
            return "OK".equalsIgnoreCase(status);
        }
    }

    /**
     * Deletes a specific UDF library.
     *
     * @param libraryName The name of the library to delete
     * @return true if the library was deleted successfully
     * @throws redis.clients.jedis.exceptions.JedisDataException if deletion fails (e.g., library doesn't exist)
     */
    @Override
    public boolean udfDelete(String libraryName) {
        try (Jedis conn = getConnection()) {
            Object response = conn.sendCommand(GraphCommand.UDF, "DELETE", libraryName);

            if (response == null) {
                return false;
            }

            if (response instanceof Long) {
                return ((Long) response) > 0;
            }

            if (response instanceof String) {
                return "OK".equalsIgnoreCase((String) response);
            }

            if (response instanceof byte[]) {
                String decoded = SafeEncoder.encode((byte[]) response);
                return "OK".equalsIgnoreCase(decoded);
            }

            // Unknown response type: conservatively report failure
            return false;
        }
    }

    /**
     * Gets the value of a FalkorDB configuration parameter.
     *
     * @param name The configuration parameter name (e.g., "RESULTSET_SIZE")
     * @return The value of the configuration parameter as a String
     * @throws redis.clients.jedis.exceptions.JedisDataException if the configuration parameter is invalid
     */
    @Override
    public String configGet(String name) {
        try (Jedis conn = getConnection()) {
            Object response = conn.sendCommand(GraphCommand.CONFIG, "GET", name);
            return parseConfigGetResponse(response);
        }
    }

    /**
     * Sets the value of a FalkorDB configuration parameter.
     *
     * @param name The configuration parameter name (e.g., "RESULTSET_SIZE")
     * @param value The value to set
     * @return true if the configuration was set successfully
     * @throws redis.clients.jedis.exceptions.JedisDataException if setting fails (e.g., invalid parameter)
     */
    @Override
    public boolean configSet(String name, Object value) {
        try (Jedis conn = getConnection()) {
            Object response = conn.sendCommand(GraphCommand.CONFIG, "SET", name, String.valueOf(value));
            return parseConfigSetResponse(response);
        }
    }

    /**
     * Parses the response from GRAPH.CONFIG GET command.
     * Response format: [name, value]
     *
     * @param response the raw response from Redis
     * @return the configuration value as a String
     * @throws JedisDataException if the response format is unexpected
     */
    String parseConfigGetResponse(Object response) {
        if (response instanceof List<?>) {
            List<?> list = (List<?>) response;
            if (list.size() >= 2) {
                Object value = list.get(1);
                if (value instanceof byte[]) {
                    return SafeEncoder.encode((byte[]) value);
                } else if (value != null) {
                    return value.toString();
                }
            }
        }
        throw new redis.clients.jedis.exceptions.JedisDataException("Unexpected response format from GRAPH.CONFIG GET");
    }

    /**
     * Parses the response from GRAPH.CONFIG SET command.
     *
     * @param response the raw response from Redis
     * @return true if the response indicates success ("OK")
     */
    boolean parseConfigSetResponse(Object response) {
        if (response == null) {
            return false;
        }
        String status;
        if (response instanceof byte[]) {
            status = SafeEncoder.encode((byte[]) response);
        } else {
            status = response.toString();
        }
        return "OK".equalsIgnoreCase(status);
    }

    /**
     * Parses the response from GRAPH.LIST command
     *
     * @param response the raw response from Redis
     * @return a list of graph names
     */
    List<String> parseListResponse(Object response) {
        List<String> graphNames = new ArrayList<>();

        if (response instanceof List<?>) {
            List<?> list = (List<?>) response;
            for (Object item : list) {
                if (item instanceof byte[]) {
                    graphNames.add(SafeEncoder.encode((byte[]) item));
                } else if (item instanceof String) {
                    graphNames.add((String) item);
                }
            }
        }

        return graphNames;
    }

    /**
     * Closes the Jedis pool. A driver whose pool was never built closes without building one, so
     * creating and discarding a driver still performs no I/O.
     */
    @Override
    public void close() {
        closed.set(true);
        Pool<Jedis> existing = resolvedPool.get();
        if (existing != null) {
            existing.close();
        }
    }
}
