package com.falkordb.impl.api;

import com.falkordb.Driver;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.SslOptions;
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

    private final Pool<Jedis> pool;

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
        this(new JedisPool(
                new GenericObjectPoolConfig<Jedis>(), uri, Protocol.DEFAULT_TIMEOUT, DEFAULT_SOCKET_TIMEOUT_MILLIS));
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
        return new DriverImpl(new JedisPool(
                buildPoolConfig(poolMaxTotal, poolMaxIdle, poolMaxWait),
                new HostAndPort(normalizedHost, port),
                buildClientConfig(user, password, ssl, connectionTimeoutMillis, socketTimeoutMillis)));
    }

    /**
     * Builds a Jedis client config from resolved settings. TLS is enabled through the non-deprecated
     * {@link SslOptions#defaults()} path rather than the deprecated {@code ssl(boolean)} setter.
     */
    static DefaultJedisClientConfig buildClientConfig(
            String user, String password, boolean ssl, int connectionTimeoutMillis, int socketTimeoutMillis) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(connectionTimeoutMillis)
                .socketTimeoutMillis(socketTimeoutMillis);
        if (user != null) {
            builder.user(user);
        }
        if (password != null) {
            builder.password(password);
        }
        if (ssl) {
            builder.sslOptions(SslOptions.defaults());
        }
        return builder.build();
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
        this.pool = pool;
    }

    @Override
    public GraphImpl graph(String graphId) {
        return new GraphImpl(this, graphId);
    }

    @Override
    public Jedis getConnection() {
        return pool.getResource();
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
     * Closes the Jedis pool
     */
    @Override
    public void close() {
        pool.close();
    }
}
