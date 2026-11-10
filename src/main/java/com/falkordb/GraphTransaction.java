package com.falkordb;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.PipelineBinaryCommands;
import redis.clients.jedis.commands.PipelineCommands;
import redis.clients.jedis.commands.RedisModulePipelineCommands;

/**
 * An interface which aligned to Jedis transactional interface
 *
 * <p>{@link #exec()} decodes every graph reply before it returns. {@link #deleteGraph()} clears the client's
 * schema cache when {@code exec()} has run the delete: not when it is queued, and not at all if the
 * transaction is discarded or aborted. Once the delete has run, a reply queued before it can't look an id up
 * any more: the graph that sent the reply is gone, and a lookup would find whatever graph has the name by then.
 * So the reply is decoded with what the cache held when the delete was queued, and an id the cache lacked is
 * reported as a {@link com.falkordb.exceptions.GraphException}. A reply queued between two deletes comes from a
 * graph that exists only in between, which the client has never read, so it can't be decoded if it names any
 * label, property key or relationship type. {@code exec()} decodes the replies before it
 * learns whether the delete succeeded, so it does the same for a delete that fails.
 *
 * <p>A {@link com.falkordb.exceptions.GraphException} while decoding a reply becomes that reply's element of
 * the list {@code exec()} returns, like an error reply. Jedis does not keep it, so reading the same
 * {@code Response} afterwards returns {@code null}.
 */
public interface GraphTransaction
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
     * for the replies in {@link #exec()}, which a socket (read) timeout on the connection can still end: see
     * {@link Graph#query(String, long)}.
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
     * {@link IllegalArgumentException}. A name needn't be a Cypher identifier; on FalkorDB 4.x, refer to
     * one such as {@code 1} as {@code $`1`} (6.x also accepts {@code $1}). FalkorDB 6.x limits a name to
     * 512 bytes of UTF-8 (so 256 characters such as {@code é}): the client sends a longer one, and the
     * server rejects it. This safety covers
     * parameter values only — not the query text itself, dynamic labels/identifiers, or procedure names.
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

    // Disabled due to bug in FalkorDB caused by using transactions in conjunction with graph copy
    /**
     * Copies the graph
     * @return a response which builds the copy running time statistics
     */
    // Response<String> copyGraph(String destinationGraphId);

    /**
     * Deletes the entire graph. The client's schema cache is cleared when {@link #exec()} has run the delete;
     * see the class documentation.
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
     * executes the transaction
     * @return a list of the executed transaction commands answers, in case of successful transaction, null otherwise
     */
    @Nullable
    List<Object> exec();

    /**
     * If object is in transaction mode,
     * flushes all previously queued commands in a transaction and restores the connection state to normal
     */
    void clear();

    /**
     * Flushes all previously queued commands in a transaction and restores the connection state to normal
     * @return "OK" if the transaction was successfully discarded, otherwise an exception is thrown
     */
    String discard();

    /**
     * Discards the transaction if it is still open, that is unless {@link #exec()} or {@link #discard()} has
     * ended it. The connection of the context that created the transaction stays open.
     *
     * <p>Unlike {@link Closeable#close()}, this throws no checked exception, so a transaction opened in a
     * try-with-resources statement needs no {@code catch (IOException)}.
     */
    @Override
    void close();
}
