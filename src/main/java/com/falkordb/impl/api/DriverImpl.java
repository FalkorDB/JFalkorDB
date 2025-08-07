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