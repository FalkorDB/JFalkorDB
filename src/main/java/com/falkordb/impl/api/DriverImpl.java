package com.falkordb.impl.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;
import redis.clients.jedis.util.SafeEncoder;

import com.falkordb.Driver;

/**
 * A FalkorDB Driver for managing graphs and connections.
 */
public class DriverImpl implements Driver {

    private final Pool<Jedis> pool;

    /**
     * Creates a client running on the specific host/port
     *
     * @param host Server host
     * @param port Server port
     */
    public DriverImpl(String host, int port) {
        this(new JedisPool(host, port));
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
        this(new JedisPool(host, port, user, password));
    }

    /**
     * Creates a client using the specific uri
     *
     * @param uri server uri
     */
    public DriverImpl(URI uri) {
        this(new JedisPool(uri));
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
                response = conn.sendCommand(GraphCommand.UDF_LOAD, "LOAD", "REPLACE", libraryName, script);
            } else {
                response = conn.sendCommand(GraphCommand.UDF_LOAD, "LOAD", libraryName, script);
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
            Object response = conn.sendCommand(GraphCommand.UDF_LIST, "LIST");
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
                response = conn.sendCommand(GraphCommand.UDF_LIST, "LIST", libraryName, "WITHCODE");
            } else if (libraryName != null) {
                response = conn.sendCommand(GraphCommand.UDF_LIST, "LIST", libraryName);
            } else if (withCode) {
                response = conn.sendCommand(GraphCommand.UDF_LIST, "LIST", "WITHCODE");
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
            Object response = conn.sendCommand(GraphCommand.UDF_FLUSH, "FLUSH");
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
            Object response = conn.sendCommand(GraphCommand.UDF_DELETE, "DELETE", libraryName);
            
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