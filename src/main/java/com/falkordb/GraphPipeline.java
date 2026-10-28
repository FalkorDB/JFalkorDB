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
 *
 * <p>A graph reply is decoded when it is read: by {@link #syncAndReturnAll()}, or by {@code Response.get()}
 * after {@link #sync()}, which may be after the context that created the pipeline is closed. Decoding can
 * need a label, property key or relationship type the client hasn't seen yet. That lookup runs on the
 * context's connection while the context is open and no pipeline or transaction on it awaits a reply;
 * otherwise it borrows a connection from the driver's pool, so reading a reply while more commands are
 * queued can wait for a free connection.
 *
 * <p>{@link #deleteGraph()} clears the client's schema cache once the delete has run, not when it is queued.
 * Once it has run, a reply queued before it can't look an id up any more: the graph that sent the reply is
 * gone, and a lookup would find whatever graph has the name by then. So the reply is decoded with what the
 * cache held when the delete was queued, and an id the cache lacked is reported as a
 * {@link com.falkordb.exceptions.GraphException}.
 * {@link #syncAndReturnAll()} decodes every reply before it learns whether the delete succeeded, so it does the
 * same for a delete that fails; after {@link #sync()}, the replies before a delete that failed are decoded as
 * usual.
 *
 * <p>Like the context that created it, a pipeline is meant for one thread: until that context is closed,
 * read the replies on the thread that uses the context.
 *
 * <p>A {@link com.falkordb.exceptions.GraphException} while decoding a reply in {@link #syncAndReturnAll()}
 * becomes that reply's element of the returned list, like an error reply. Jedis does not keep it, so reading
 * the same {@code Response} again returns {@code null}.
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
     * @param procedure procedure name to invoke
     * @return a response which builds result set with the procedure data
     */
    Response<ResultSet> callProcedure(String procedure);

    /**
     * Invokes stored procedure with arguments
     * @param procedure procedure name to invoke
     * @param args procedure arguments
     * @return a response which builds result set with the procedure data
     */
    Response<ResultSet> callProcedure(String procedure, List<String> args);

    /**
     * Invoke a stored procedure
     * @param procedure - procedure to execute
     * @param args - procedure arguments
     * @param kwargs - procedure output arguments: the {@code "y"} entry lists the output
     *     columns to bind with Cypher's {@code YIELD} clause
     * @return a response which builds result set with the procedure data
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
     * Deletes the entire graph. The client's schema cache is cleared once the delete has run; see the class
     * documentation for how replies queued before it are decoded.
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
     * Synchronize pipeline by reading and decoding all responses. The pipeline can be used again afterwards.
     * Whenever possible try to avoid using this version and use Pipeline.sync() as it won't go through all the
     * responses and generate the right response type (usually it is a waste of time).
     * @return A list of all the responses in the order you executed them.
     */
    List<Object> syncAndReturnAll();

    /**
     * Synchronize pipeline by reading all responses. The pipeline can be used again afterwards. In order to
     * get return values from pipelined commands, capture the different Response&lt;?&gt; of the
     * commands you execute; each is decoded when it is read.
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
