package com.falkordb.impl.api;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;

public class Driver implements com.falkordb.Driver {

    private final Pool<Jedis> pool;

    /**
     * Creates a client running on the local machine
     */
    public Driver() {
        this("localhost", 6379);
    }

    /**
     * Creates a client running on the specific host/post
     *
     * @param host Redis host
     * @param port Redis port
     */
    public Driver(String host, int port) {
        this.pool = new JedisPool(host, port);
    }

    @Override
    public Graph graph(String graphId) {
        return new Graph(this, graphId);
    }

    @Override
    public Jedis getConnection() {
        return pool.getResource();
    }

    /**
     * Closes the Jedis pool
     */
    @Override
    public void close() {
        pool.close();
    }
}
