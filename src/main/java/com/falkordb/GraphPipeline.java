package com.falkordb;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.PipelineBinaryCommands;
import redis.clients.jedis.commands.PipelineCommands;
import redis.clients.jedis.commands.RedisModulePipelineCommands;

/**
 * An interface which aligned to Jedis Pipeline interface
 */
public interface GraphPipeline
        extends PipelineCommands, PipelineBinaryCommands, RedisModulePipelineCommands, Closeable {

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query);

    /**
     * Execute a Cypher read-only query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query);

    /**
     * Execute a Cypher query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query, long timeout);

    /**
     * Execute a Cypher read-only query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query, long timeout);

    /**
     * Executes a cypher query with parameters.
     *
     * <p>Parameter <em>values</em> are safely encoded as Cypher literals, so caller-supplied values
     * cannot break out and inject Cypher — always pass untrusted input as a parameter rather than
     * concatenating it into {@code query}. Unsupported value types (including
     * {@link java.math.BigDecimal}), out-of-range integers, non-finite floating-point values, cyclic
     * containers, non-String map keys, invalid parameter names, and a null {@code query} or
     * {@code params} are rejected with {@link IllegalArgumentException}. This safety covers parameter
     * values only — not the query text itself, dynamic labels/identifiers, or procedure names.
     *
     * @param query Cypher query. Must not be {@code null}.
     * @param params parameters map. Must not be {@code null}.
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query, Map<String, Object> params);

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params);

    /**
     * Executes a cypher query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query, Map<String, Object> params, long timeout);

    /**
     * Executes a cypher read-only query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params, long timeout);

    /**
     * Invokes stored procedures without arguments
     *
     * <p>The call is checked before it is queued, so a rejected call queues nothing.
     *
     * @param procedure procedure name to invoke: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*})
     *     joined by dots, such as {@code db.labels}
     * @return a response which builds result set with the procedure data
     * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name
     */
    Response<ResultSet> callProcedure(String procedure);

    /**
     * Invokes stored procedure with arguments
     *
     * <p>The call is checked before it is queued, so a rejected call queues nothing.
     *
     * @param procedure procedure name to invoke: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*})
     *     joined by dots, such as {@code db.labels}
     * @param args procedure arguments, sent as string literals
     * @return a response which builds result set with the procedure data
     * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, or
     *     {@code args} or one of its elements is null
     */
    Response<ResultSet> callProcedure(String procedure, List<String> args);

    /**
     * Invoke a stored procedure
     *
     * <p>The call is checked before it is queued, so a rejected call queues nothing.
     *
     * @param procedure - procedure name to invoke: identifiers ({@code [A-Za-z_][A-Za-z0-9_]*})
     *     joined by dots, such as {@code db.labels}
     * @param args - procedure arguments, sent as string literals
     * @param kwargs - procedure output arguments: the {@code "y"} entry lists the output
     *     columns to bind with Cypher's {@code YIELD} clause, each a Cypher identifier ({@code
     *     [A-Za-z_][A-Za-z0-9_]*}, without an {@code AS} alias)
     * @return a response which builds result set with the procedure data
     * @throws IllegalArgumentException if {@code procedure} is null or not a valid procedure name, if
     *     {@code args}, {@code kwargs} or an argument is null, or if a {@code YIELD} column is not a
     *     Cypher identifier
     */
    Response<ResultSet> callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs);

    /**
     * Execute a Cypher query and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @return a response which builds result set with execution plan and performance metrics
     */
    Response<ResultSet> profile(String query);

    /**
     * Execute a Cypher query with parameters and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @param params parameters map
     * @return a response which builds result set with execution plan and performance metrics
     */
    Response<ResultSet> profile(String query, Map<String, Object> params);

    /**
     * Copies the graph
     * @param destinationGraphId duplicated graph name
     * @return a response which builds the copy running time statistics
     */
    Response<String> copyGraph(String destinationGraphId);

    /**
     * Deletes the entire graph
     * @return a response which builds the delete running time statistics
     */
    Response<String> deleteGraph();

    /**
     * Get the execution plan for a given query.
     * @param query Cypher query
     * @return a response which builds the execution plan as list of strings
     */
    Response<List<String>> explain(String query);

    /**
     * Get the execution plan for a given query with parameters.
     * @param query Cypher query
     * @param params parameters map
     * @return a response which builds the execution plan as list of strings
     */
    Response<List<String>> explain(String query, Map<String, Object> params);

    /**
     * Synchronize pipeline by reading all responses. This operation close the pipeline. Whenever
     * possible try to avoid using this version and use Pipeline.sync() as it won't go through all the
     * responses and generate the right response type (usually it is a waste of time).
     * @return A list of all the responses in the order you executed them.
     */
    List<Object> syncAndReturnAll();

    /**
     * Synchronize pipeline by reading all responses. This operation close the pipeline. In order to
     * get return values from pipelined commands, capture the different Response&lt;?&gt; of the
     * commands you execute.
     */
    void sync();

    /**
     * Blocks until all the previous write commands are successfully transferred and acknowledged by
     * at least the specified number of replicas. If the timeout, specified in milliseconds, is
     * reached, the command returns even if the specified number of replicas were not yet reached.
     * @param replicas successfully transferred and acknowledged by at least the specified number of
     *          replicas
     * @param timeout the time to block in milliseconds, a timeout of 0 means to block forever
     * @return the number of replicas reached by all the writes performed in the context of the
     *         current connection
     */
    Response<Long> waitReplicas(int replicas, long timeout);
}
