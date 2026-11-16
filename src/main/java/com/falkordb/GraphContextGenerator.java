package com.falkordb;

/**
 * An interface for generating a FalkorDB graph with context.
 */
public interface GraphContextGenerator extends Graph {
    /**
     * Generate a connection bounded api
     * @return a connection bounded api
     * @throws com.falkordb.exceptions.GraphException if the server rejects the new connection with a plain error,
     *     for example its {@code AUTH}; a {@code WRONGPASS} or {@code NOPERM} is Jedis' {@code
     *     JedisAccessControlException}
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    GraphContext getContext();
}
