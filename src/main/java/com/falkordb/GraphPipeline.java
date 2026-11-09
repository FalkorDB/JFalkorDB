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
 * {@link com.falkordb.exceptions.GraphException}. A reply queued between two deletes that are both still to run
 * comes from a graph that exists only in between, which the client has never read, so it can't be decoded if it
 * names any label, property key or relationship type.
 * {@link #syncAndReturnAll()} decodes every reply before it learns whether the delete succeeded, so it does the
 * same for a delete that fails; after {@link #sync()}, the replies before a delete that failed are decoded as
 * usual.
 *
 * <p>Like the context that created it, a pipeline is meant for one thread: until that context is closed,
 * read the replies on the thread that uses the context.
 *
 * <p>Call {@link #sync()}, {@link #syncAndReturnAll()} or {@link #close()} before dropping a pipeline that has
 * queued commands. Their replies stay unread on the context's connection, and the next command sent on it, by the
 * context or by a schema lookup, reads one of them as its own.
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
     *
     * <p>The timeout is sent with the query and limits how long the server runs it; {@code 0} sets none, so
     * the server's default ({@code TIMEOUT_DEFAULT}) applies. It doesn't change how long the client waits
     * for the replies in {@link #sync()} or {@link #syncAndReturnAll()}, which a socket (read) timeout on
     * the connection can still end: see {@link Graph#query(String, long)}.
     *
     * @param query Cypher query
     * @param timeout timeout in milliseconds; not negative
     * @return a response which builds the result set with the query answer.
     * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
     */
    Response<ResultSet> query(String query, long timeout);

    /**
     * Execute a Cypher read-only query with timeout. The timeout works as for {@link #query(String, long)}.
     * @param query Cypher query
     * @param timeout timeout in milliseconds; not negative
     * @return a response which builds the result set with the query answer.
     * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
     */
    Response<ResultSet> readOnlyQuery(String query, long timeout);

    /**
     * Executes a cypher query with parameters.
     *
     * <p>Parameter <em>values</em> are safely encoded as Cypher literals, so caller-supplied values
     * cannot break out and inject Cypher — always pass untrusted input as a parameter rather than
     * concatenating it into {@code query}. Unsupported value types (including
     * {@link java.math.BigDecimal}), out-of-range integers, non-finite floating-point values, cyclic
     * containers, non-String map keys, a parameter name that is null, empty or contains a backtick, a NUL
     * character or an unpaired surrogate, and a null {@code query} or {@code params} are rejected with
     * {@link IllegalArgumentException}. A name needn't be a Cypher identifier: refer to one such as
     * {@code 1} as {@code $`1`}. This safety covers parameter values only — not the query text itself,
     * dynamic labels/identifiers, or procedure names.
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
     * Executes a cypher query with parameters and timeout. The timeout works as for {@link #query(String,
     * long)}.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds; not negative
     * @return  a response which builds the result set with the query answer.
     * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
     */
    Response<ResultSet> query(String query, Map<String, Object> params, long timeout);

    /**
     * Executes a cypher read-only query with parameters and timeout. The timeout works as for {@link
     * #query(String, long)}.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds; not negative
     * @return  a response which builds the result set with the query answer.
     * @throws IllegalArgumentException if {@code timeout} is negative; nothing is queued then
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
     * Deletes the entire graph. The client's schema cache is cleared once the delete has run; see the class
     * documentation for how replies queued before it are decoded.
     * @return a response which builds the delete running time statistics
     */
    Response<String> deleteGraph();

    /**
     * Get the execution plan for a given query.
     * @param query Cypher query
     * @return a response which builds the execution plan as list of strings. Its {@code get()} throws a
     *     {@link com.falkordb.exceptions.GraphException} each time it is called if the plan holds an error, and
     *     Jedis' {@code JedisDataException} if the server rejected the command.
     */
    Response<List<String>> explain(String query);

    /**
     * Get the execution plan for a given query with parameters.
     * @param query Cypher query
     * @param params parameters map
     * @return a response which builds the execution plan as list of strings. Its {@code get()} throws a
     *     {@link com.falkordb.exceptions.GraphException} each time it is called if the plan holds an error, and
     *     Jedis' {@code JedisDataException} if the server rejected the command.
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
