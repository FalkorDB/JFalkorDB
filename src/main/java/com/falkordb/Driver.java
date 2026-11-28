package com.falkordb;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Jedis;

/**
 * An interface which aligned to FalkorDB Driver interface
 *
 * <p>The methods of a driver, and of the graphs and contexts it creates, report a failed connection as a {@link
 * com.falkordb.exceptions.FalkorDBConnectionException} and a command the server rejects as a {@link
 * com.falkordb.exceptions.GraphException}. Both extend the Jedis exceptions earlier versions threw, so existing
 * {@code catch} clauses keep matching. A few failures are still Jedis' own: a pool that stays exhausted ({@code
 * JedisException}), a Redis Cluster whose topology can't be learned ({@code JedisClusterOperationException}), and,
 * from a driver method, an error Jedis has a subclass for, such as {@code JedisAccessControlException} (a graph
 * command wraps that in a {@code GraphException} as its cause). The methods of {@link GraphPipeline} and {@link
 * GraphTransaction}, and commands on the {@code Jedis} that {@link #getConnection()} returns, report what Jedis
 * raises.
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
     * @deprecated since 0.17.0, and to be removed in 0.18.0, because it exposes the Jedis transport. Run
     *     graph commands through {@link #graph(String)}. Configure the connection pool, connection checks,
     *     database and TLS with {@link FalkorDB#builder()}. Run non-graph Redis commands with a Redis client
     *     of your own.
     * @return a underline connection to the database
     * @throws com.falkordb.exceptions.GraphException if the server rejects the new connection with a plain error,
     *     for example its {@code AUTH}; a {@code WRONGPASS} or {@code NOPERM} is Jedis' {@code
     *     JedisAccessControlException}
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    @Deprecated
    Jedis getConnection();

    /**
     * Lists all available graphs in the database
     *
     * @return a list of graph names
     * @throws com.falkordb.exceptions.GraphException if the server rejects the command
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    List<String> listGraphs();

    /**
     * Loads a User Defined Function (UDF) library.
     *
     * @param libraryName The name of the UDF library
     * @param script The JavaScript code containing the UDF functions
     * @param replace Whether to replace an existing library with the same name
     * @return true if the library was loaded successfully
     * @throws com.falkordb.exceptions.GraphException if the server rejects the command
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    boolean udfLoad(String libraryName, String script, boolean replace);

    /**
     * Lists all loaded UDF libraries.
     *
     * <p>Each library is a flat list of alternating keys and values, such as {@code library_name}, its name,
     * {@code functions} and a list of their names, with strings as byte arrays. The shape is the same on every
     * server version and protocol: a library FalkorDB 4.x sends as a RESP3 map is turned into that list.
     *
     * @return a list of UDF library information
     * @throws com.falkordb.exceptions.GraphException if the server rejects the command
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    List<Object> udfList();

    /**
     * Lists UDF libraries with optional filters. Each library has the shape {@link #udfList()} describes, plus the
     * code when {@code withCode} asks for it.
     *
     * @param libraryName Optional library name to filter results
     * @param withCode Whether to include the code in the response
     * @return a list of UDF library information
     * @throws com.falkordb.exceptions.GraphException if the server rejects the command
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    List<Object> udfList(@Nullable String libraryName, boolean withCode);

    /**
     * Flushes all loaded UDF libraries.
     *
     * @return true if libraries were flushed successfully
     * @throws com.falkordb.exceptions.GraphException if the server rejects the command
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    boolean udfFlush();

    /**
     * Deletes a specific UDF library.
     *
     * @param libraryName The name of the library to delete
     * @return true if the library was deleted successfully
     * @throws com.falkordb.exceptions.GraphException if the server rejects the command
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     */
    boolean udfDelete(String libraryName);

    /**
     * Gets the value of a FalkorDB configuration parameter.
     *
     * <p>Reads one parameter, whose name is matched case-insensitively. The {@code "*"} wildcard names
     * every parameter, so it is rejected here; {@link #configGetAll()} reads them all.
     *
     * @param name The configuration parameter name (e.g., "RESULTSET_SIZE")
     * @return The value of the configuration parameter as a String
     * @throws IllegalArgumentException if {@code name} is null or {@code "*"}, or the server answers with
     *     more than one parameter
     * @throws com.falkordb.exceptions.GraphException if the server rejects the name, for example because
     *     there is no such parameter
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     * @see <a href="https://docs.falkordb.com/commands/graph.config-get.html">GRAPH.CONFIG GET</a>
     */
    String configGet(String name);

    /**
     * Gets every FalkorDB configuration parameter, as {@code GRAPH.CONFIG GET *} reports them.
     *
     * <p>The map is in the order the server lists the parameters. It is keyed by their names as the
     * server spells them, in upper case (e.g., "RESULTSET_SIZE"), so unlike {@link #configGet(String)}
     * a lookup in it is case-sensitive. Each value is the string {@code configGet} returns for that
     * parameter. The map is unmodifiable: mutating it throws {@link UnsupportedOperationException}.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}, so that a class
     * implementing this interface outside this library keeps compiling. Every driver this library
     * creates implements it.
     *
     * @return every configuration parameter's value, by name
     * @throws UnsupportedOperationException if this driver does not implement it
     * @throws com.falkordb.exceptions.GraphException if the server rejects the command
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     * @see <a href="https://docs.falkordb.com/commands/graph.config-get.html">GRAPH.CONFIG GET</a>
     */
    default Map<String, String> configGetAll() {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement configGetAll()");
    }

    /**
     * Sets the value of a FalkorDB configuration parameter.
     *
     * @param name The configuration parameter name (e.g., "RESULTSET_SIZE")
     * @param value The value to set
     * @return true if the configuration was set successfully
     * @throws IllegalArgumentException if {@code name} is null
     * @throws com.falkordb.exceptions.GraphException if the server rejects the name or value
     * @throws com.falkordb.exceptions.FalkorDBConnectionException if a connection to the server can't be opened, or
     *     fails while the command is sent or its reply read
     * @see <a href="https://docs.falkordb.com/commands/graph.config-set.html">GRAPH.CONFIG SET</a>
     */
    boolean configSet(String name, Object value);

    /**
     * Closes the driver and releases its pooled connections. Closing a driver that is already closed has no
     * effect.
     *
     * <p>Unlike {@link Closeable#close()}, this throws no checked exception, so a driver opened in a
     * try-with-resources statement needs no {@code catch (IOException)}.
     */
    @Override
    void close();
}
