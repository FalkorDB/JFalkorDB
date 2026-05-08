package com.falkordb;

import java.io.Closeable;
import java.util.List;

import redis.clients.jedis.Jedis;

/**
 * An interface which aligned to FalkorDB Driver interface
 */
public interface Driver extends Closeable {

    /**
     * Returns a selected Graph
     * 
     * @param graphId Graph name
     * @return a selected Graph
     */
    GraphContextGenerator graph(String graphId);

    /**
     * Returns a underline connection to the database
     * 
     * @return a underline connection to the database
     */
    Jedis getConnection();

    /**
     * Lists all available graphs in the database
     * 
     * @return a list of graph names
     */
    List<String> listGraphs();

    /**
     * Loads a User Defined Function (UDF) library.
     * 
     * @param libraryName The name of the UDF library
     * @param script The JavaScript code containing the UDF functions
     * @param replace Whether to replace an existing library with the same name
     * @return true if the library was loaded successfully
     */
    boolean udfLoad(String libraryName, String script, boolean replace);

    /**
     * Lists all loaded UDF libraries.
     * 
     * @return a list of UDF library information
     */
    List<Object> udfList();

    /**
     * Lists UDF libraries with optional filters.
     * 
     * @param libraryName Optional library name to filter results
     * @param withCode Whether to include the code in the response
     * @return a list of UDF library information
     */
    List<Object> udfList(String libraryName, boolean withCode);

    /**
     * Flushes all loaded UDF libraries.
     * 
     * @return true if libraries were flushed successfully
     */
    boolean udfFlush();

    /**
     * Deletes a specific UDF library.
     * 
     * @param libraryName The name of the library to delete
     * @return true if the library was deleted successfully
     */
    boolean udfDelete(String libraryName);

    /**
     * Gets the value of a FalkorDB configuration parameter.
     * 
     * @param name The configuration parameter name (e.g., "RESULTSET_SIZE")
     * @return The value of the configuration parameter as a String
     * @see <a href="https://docs.falkordb.com/commands/graph.config-get.html">GRAPH.CONFIG GET</a>
     */
    String configGet(String name);

    /**
     * Sets the value of a FalkorDB configuration parameter.
     * 
     * @param name The configuration parameter name (e.g., "RESULTSET_SIZE")
     * @param value The value to set
     * @return true if the configuration was set successfully
     * @see <a href="https://docs.falkordb.com/commands/graph.config-set.html">GRAPH.CONFIG SET</a>
     */
    boolean configSet(String name, Object value);
}
