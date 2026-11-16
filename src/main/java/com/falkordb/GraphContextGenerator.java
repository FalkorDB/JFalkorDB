package com.falkordb;

/**
 * An interface for generating a FalkorDB graph with context.
 */
public interface GraphContextGenerator extends Graph {
    /**
     * Generate a connection bounded api
     * @return a connection bounded api
     * @throws com.falkordb.exceptions.GraphException if the server rejects the new connection, for example its
     *     {@code AUTH}
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if the server can't be reached or the connection
     *     fails
     */
    GraphContext getContext();
}
