package com.falkordb;

import java.io.Closeable;

import redis.clients.jedis.Jedis;

public interface Driver extends Closeable{
    GraphContextGenerator graph(String graphId);

    Jedis getConnection();
}
