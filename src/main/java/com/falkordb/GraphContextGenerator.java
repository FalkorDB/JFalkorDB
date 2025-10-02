package com.falkordb;

/**
 * An interface for generating a FalkorDB graph with context.
 */
public interface GraphContextGenerator extends Graph {
    /**
     * Generate a connection bounded api
     * @return a connection bounded api
     */
    GraphContext getContext();
}