package com.falkordb.impl.api;

import java.net.URI;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;

import com.falkordb.Driver;

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
     * Closes the Jedis pool
     */
    @Override
    public void close() {
        pool.close();
    }
}
