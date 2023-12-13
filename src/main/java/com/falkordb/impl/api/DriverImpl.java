package com.falkordb.impl.api;

import java.net.URI;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;

import com.falkordb.Driver;

public class DriverImpl implements Driver {

    private final Pool<Jedis> pool;

    /**
     * Creates a client running on the specific host/post
     *
     * @param host Server host
     * @param port Server port
     */
    public DriverImpl(String host, int port) {
        this.pool = new JedisPool(host, port);
    }

    /**
     * Creates a client running on the specific host/post
     *
     * @param host     Server host
     * @param port     Server port
     * @param user     username
     * @param password password
     */
    public DriverImpl(String host, int port, String user, final String password) {
        this.pool = new JedisPool(host, port, user, password);
    }

    /**
     * Creates a client using the specific uri
     *
     * @param uri server uri
     */
    public DriverImpl(URI uri) {
        this.pool = new JedisPool(uri);
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
     * Closes the Jedis pool
     */
    @Override
    public void close() {
        pool.close();
    }
}
