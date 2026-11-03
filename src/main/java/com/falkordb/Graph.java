package com.falkordb;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * An interface for a FalkorDB graph.
 */
public interface Graph extends Closeable {

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a result set
     */
    ResultSet query(String query);

    /**
     * Execute a Cypher read-only query.
     * @param query Cypher query
     * @return a result set
     */
    ResultSet readOnlyQuery(String query);

    /**
     * Execute a Cypher query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    ResultSet query(String query, long timeout);

    /**
     * Execute a Cypher read-only query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    ResultSet readOnlyQuery(String query, long timeout);

    /**
     * Executes a cypher query with parameters.
     *
     * <p>Parameter <em>values</em> are safely encoded as Cypher literals, so caller-supplied values
     * cannot break out and inject Cypher — always pass untrusted input as a parameter rather than
     * concatenating it into {@code query}. Values must be an encodable type (null, String, Character,
     * Boolean, a boxed integer or floating-point number, a {@link java.math.BigInteger} within signed
     * 64-bit range, or an array/List/Map of such values, where every Map key is a String); other types
     * (including {@link java.math.BigDecimal}), out-of-range integers, non-finite floating-point
     * values, cyclic containers, non-String map keys, invalid parameter names, and a null
     * {@code query} or {@code params} are rejected with {@link IllegalArgumentException}. This does not
     * cover the query text itself or dynamic labels/identifiers. (The form of procedure names is
     * checked by {@link #callProcedure(String, List, Map)}, but not which procedure they name.)
     *
     * @param query Cypher query. Must not be {@code null}.
     * @param params parameters map. Must not be {@code null}.
     * @return a result set.
     */
    ResultSet query(String query, Map<String, Object> params);

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a result set.
     */
    ResultSet readOnlyQuery(String query, Map<String, Object> params);

    /**
     * Executes a cypher query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a result set.
     */
    ResultSet query(String query, Map<String, Object> params, long timeout);

    /**
     * Executes a cypher read-only query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    ResultSet readOnlyQuery(String query, Map<String, Object> params, long timeout);

    /**
     * Invokes stored procedures without arguments
     * @param procedure procedure name to invoke: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*})
     *     joined by dots, such as {@code db.labels}
     * @return result set with the procedure data
     * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name
     * @throws com.falkordb.exceptions.GraphException if the server rejects the call
     */
    ResultSet callProcedure(String procedure);

    /**
     * Execute a Cypher query and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @return a result set with execution plan and performance metrics
     */
    ResultSet profile(String query);

    /**
     * Execute a Cypher query with parameters and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @param params parameters map
     * @return a result set with execution plan and performance metrics
     */
    ResultSet profile(String query, Map<String, Object> params);

    /**
     * Invokes stored procedure with arguments
     * @param procedure procedure name to invoke: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*})
     *     joined by dots, such as {@code db.labels}
     * @param args procedure arguments, sent as string literals
     * @return result set with the procedure data
     * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, or
     *     {@code args} or one of its elements is null
     * @throws com.falkordb.exceptions.GraphException if the server rejects the call
     */
    ResultSet callProcedure(String procedure, List<String> args);

    /**
     * Invoke a stored procedure
     * @param procedure - procedure name to invoke: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*})
     *     joined by dots, such as {@code db.labels}
     * @param args - procedure arguments, sent as string literals
     * @param kwargs - procedure output arguments: the {@code "y"} entry lists the output
     *     columns to bind with Cypher's {@code YIELD} clause, each a Cypher identifier ({@code
     *     [A-Za-z_][A-Za-z0-9_]*}, without an {@code AS} alias)
     * @return result set with the procedure data
     * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, if
     *     {@code args}, {@code kwargs} or an argument is null, or if a {@code YIELD} column is not a
     *     Cypher identifier
     * @throws com.falkordb.exceptions.GraphException if the server rejects the call
     */
    ResultSet callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs);

    /**
     * Copies the graph
     * @param destinationGraphId duplicated graph name
     * @return copy running time statistics
     */
    String copyGraph(String destinationGraphId);

    /**
     * Deletes the entire graph
     * @return delete running time statistics
     */
    String deleteGraph();

    /**
     * Get the execution plan for a given query.
     * @param query Cypher query
     * @return execution plan as list of strings
     */
    List<String> explain(String query);

    /**
     * Get the execution plan for a given query with parameters.
     * @param query Cypher query
     * @param params parameters map
     * @return execution plan as list of strings
     */
    List<String> explain(String query, Map<String, Object> params);

    @Override
    void close();
}
