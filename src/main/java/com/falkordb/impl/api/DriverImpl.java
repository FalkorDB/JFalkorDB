package com.falkordb.impl.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.util.Pool;
import redis.clients.jedis.util.SafeEncoder;

import com.falkordb.Driver;

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
     * it to {@link #DriverImpl(Pool)}.
     */
    private static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 0;

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
        this(new JedisPool(new GenericObjectPoolConfig<Jedis>(), uri,
                Protocol.DEFAULT_TIMEOUT, DEFAULT_SOCKET_TIMEOUT_MILLIS));
    }

    /**
     * Builds the client configuration used by this driver: Jedis' default connection
     * timeout, no socket (read) timeout (see {@link #DEFAULT_SOCKET_TIMEOUT_MILLIS}),
     * and the given credentials when provided.
     */
    private static DefaultJedisClientConfig clientConfig(String user, final String password) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(Protocol.DEFAULT_TIMEOUT)
                .socketTimeoutMillis(DEFAULT_SOCKET_TIMEOUT_MILLIS);
        if (user != null) {
            builder.user(user);
        }
        if (password != null) {
            builder.password(password);
        }
        return builder.build();
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
        throw new redis.clients.jedis.exceptions.JedisDataException(
                "Unexpected response format from GRAPH.CONFIG GET");
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