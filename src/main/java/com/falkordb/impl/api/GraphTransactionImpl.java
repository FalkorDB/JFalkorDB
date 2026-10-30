package com.falkordb.impl.api;

import com.falkordb.Graph;
import com.falkordb.ResultSet;
import com.falkordb.impl.Utils;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.*;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * This class is extending Jedis Transaction
 *
 * <p>A transaction is used once: {@code GraphContext.multi()} starts it, and {@link #exec()} or
 * {@link #discard()} ends it. Starting it again with {@code multi()} is not supported.
 */
public class GraphTransactionImpl extends Transaction implements com.falkordb.GraphTransaction {

    private final String graphId;
    private final QueuedReplies replies;
    /** True until exec() or discard(): the context sends MULTI as soon as it creates the transaction. */
    private boolean queuing = true;
    /** True while exec() reads and decodes the replies, so none is outstanding on the connection. */
    private boolean draining;

    /**
     * Creates a new transaction for a given graph.
     * @param connection the connection to use
     * @param graph the graph to use
     * @param cache the graph cache to use
     * @param graphId the graph ID to use
     */
    public GraphTransactionImpl(Connection connection, Graph graph, GraphCache cache, String graphId) {
        this(connection, (reply, replyCache) -> new ResultSetImpl(reply, graph, replyCache), cache, graphId);
    }

    /**
     * Creates a new transaction whose replies {@code decoder} decodes, given the cache each one is decoded with.
     */
    GraphTransactionImpl(
            Connection connection,
            BiFunction<List<Object>, GraphCache, ResultSet> decoder,
            GraphCache cache,
            String graphId) {
        // init as in Jedis
        super(connection, false);
        this.graphId = graphId;
        this.replies = new QueuedReplies(decoder, cache, () -> queuing && !draining);
    }

    /** How this transaction decodes its replies; the context that created it watches it through this. */
    QueuedReplies replies() {
        return replies;
    }

    /**
     * Appends a command to the transaction.
     * @param protocolCommand the command to append
     * @param arguments the command arguments
     * @param builder the response builder
     * @param <T> the response type
     * @return the response
     */
    protected <T> Response<T> appendWithResponse(
            ProtocolCommand protocolCommand, List<Object> arguments, Builder<T> builder) {
        CommandArguments commandArguments = new CommandArguments(protocolCommand);
        arguments.forEach(commandArguments::add);
        return this.appendCommand(new CommandObject<>(commandArguments, builder));
    }

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query) {
        return appendWithResponse(
                GraphCommand.QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING), replies.resultSet());
    }

    /**
     * Execute a Cypher read-oly query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query) {
        return appendWithResponse(
                GraphCommand.RO_QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING), replies.resultSet());
    }

    /**
     * Execute a Cypher query with timeout.
     *
     * NOTE: timeout is simply sent to DB. Socket timeout will not be changed.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query, long timeout) {
        return appendWithResponse(
                GraphCommand.QUERY,
                Arrays.asList(graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING, Long.toString(timeout)),
                replies.resultSet());
    }

    /**
     * Execute a Cypher read-only query with timeout.
     *
     * NOTE: timeout is simply sent to DB. Socket timeout will not be changed.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query, long timeout) {
        return appendWithResponse(
                GraphCommand.RO_QUERY,
                Arrays.asList(graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING, Long.toString(timeout)),
                replies.resultSet());
    }

    /**
     * Executes a cypher query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query, Map<String, Object> params) {
        return appendWithResponse(
                GraphCommand.QUERY,
                Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING),
                replies.resultSet());
    }

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params) {
        return appendWithResponse(
                GraphCommand.RO_QUERY,
                Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING),
                replies.resultSet());
    }

    /**
     * Executes a cypher query with parameters and timeout.
     *
     * NOTE: timeout is simply sent to DB. Socket timeout will not be changed.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query, Map<String, Object> params, long timeout) {
        return appendWithResponse(
                GraphCommand.QUERY,
                Arrays.asList(
                        graphId,
                        Utils.prepareQuery(query, params),
                        Utils.COMPACT_STRING,
                        Utils.TIMEOUT_STRING,
                        Long.toString(timeout)),
                replies.resultSet());
    }

    /**
     * Executes a cypher read-only query with parameters and timeout.
     *
     * NOTE: timeout is simply sent to DB. Socket timeout will not be changed.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params, long timeout) {
        return appendWithResponse(
                GraphCommand.RO_QUERY,
                Arrays.asList(
                        graphId,
                        Utils.prepareQuery(query, params),
                        Utils.COMPACT_STRING,
                        Utils.TIMEOUT_STRING,
                        Long.toString(timeout)),
                replies.resultSet());
    }

    /**
     * Invokes stored procedures without arguments, in multi/exec context
     * @param procedure procedure name to invoke
     * @return response with result set with the procedure data
     */
    @Override
    public Response<ResultSet> callProcedure(String procedure) {
        return callProcedure(procedure, Utils.DUMMY_LIST, Utils.DUMMY_MAP);
    }

    /**
     * Invokes stored procedure with arguments, in multi/exec context
     * @param procedure procedure name to invoke
     * @param args procedure arguments
     * @return response with result set with the procedure data
     */
    @Override
    public Response<ResultSet> callProcedure(String procedure, List<String> args) {
        return callProcedure(procedure, args, Utils.DUMMY_MAP);
    }

    /**
     * Invoke a stored procedure, in multi/exec context
     * @param procedure - procedure to execute
     * @param args - procedure arguments
     * @param kwargs - procedure output arguments
     * @return response with result set with the procedure data
     */
    @Override
    public Response<ResultSet> callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs) {
        String preparedProcedure = Utils.prepareProcedure(procedure, args, kwargs);
        return query(preparedProcedure);
    }

    /**
     * Execute a Cypher query and produce an execution plan augmented with metrics
     * for each operation's execution, in multi/exec context.
     * @param query Cypher query
     * @return a response which builds result set with execution plan and performance metrics
     */
    @Override
    public Response<ResultSet> profile(String query) {
        return appendWithResponse(
                GraphCommand.PROFILE, Arrays.asList(graphId, query, Utils.COMPACT_STRING), replies.resultSet());
    }

    /**
     * Execute a Cypher query with parameters and produce an execution plan augmented with metrics
     * for each operation's execution, in multi/exec context.
     * @param query Cypher query
     * @param params parameters map
     * @return a response which builds result set with execution plan and performance metrics
     */
    @Override
    public Response<ResultSet> profile(String query, Map<String, Object> params) {
        return appendWithResponse(
                GraphCommand.PROFILE,
                Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING),
                replies.resultSet());
    }

    // Disabled due to bug in FalkorDB caused by using transactions in conjunction with graph copy
    /**
     * Copies the graph, in multi/exec context
     * @param destinationGraphId duplicated graph name
     * @return response with the copy running time statistics
     */
    /* @Override
    public Response<String> copyGraph(String destinationGraphId) {
        client.sendCommand(GraphCommand.COPY, graphId, destinationGraphId);
        return enqueResponse(BuilderFactory.STRING);
    } */

    /**
     * Deletes the entire graph, in multi/exec context. The client's schema cache is cleared when {@link #exec()}
     * has run the delete, not when it is queued, and not at all if the transaction is discarded or aborted.
     * Replies queued before it are decoded with the cache as it was when the delete was queued.
     * @return response with the deletion running time statistics
     */
    @Override
    public Response<String> deleteGraph() {
        // GRAPH.DELETE replies with a bulk string, which Jedis hands to the builder as a byte[] — casting it to
        // String throws, so the builder decodes it, as the pipeline's does.
        Function<Builder<String>, Response<String>> send =
                builder -> appendWithResponse(GraphCommand.DELETE, Arrays.asList(graphId), builder);
        // after exec() or discard() Jedis runs a command at once instead of queuing it
        return queuing ? replies.queueDelete(send) : replies.runDelete(send);
    }

    /**
     * Runs the transaction, then reads and decodes every reply, in order.
     * @return the decoded replies, an error in place of each command that failed, or {@code null} if the
     *     transaction was aborted because a watched key changed
     */
    @Override
    public @Nullable List<Object> exec() {
        draining = true;
        boolean ran = false;
        boolean refused = false;
        List<Object> results = null;
        try {
            results = super.exec();
            ran = true;
            return results;
        } catch (JedisDataException e) {
            // EXECABORT: a command failed to queue, so the server ran none of them. Any other error, such as EXEC
            // without MULTI after the server refused MULTI, can follow commands that ran as they were sent.
            refused = String.valueOf(e.getMessage()).startsWith("EXECABORT");
            throw e;
        } finally {
            draining = false;
            queuing = false;
            if (refused || (ran && results == null)) {
                replies.discarded(); // refused, or aborted because a watched key changed: nothing ran
            } else if (ran) {
                replies.read();
            } else {
                replies.unknown(); // EXEC may have run before the connection failed
            }
        }
    }

    /**
     * Drops the queued commands without running them; a queued {@link #deleteGraph()} then leaves the schema
     * cache alone. If {@code DISCARD} fails, as when the server refused {@code MULTI} and ran each command as it
     * was sent, the cache is cleared in case a delete ran.
     * @return "OK"
     */
    @Override
    public String discard() {
        boolean dropped = false;
        try {
            String reply = super.discard();
            dropped = true;
            return reply;
        } finally {
            queuing = false;
            if (dropped) {
                replies.discarded();
            } else {
                replies.unknown();
            }
        }
    }

    /**
     * Get the execution plan for a given query, in multi/exec context
     * @param query Cypher query
     * @return response with the execution plan as list of strings
     */
    @Override
    public Response<List<String>> explain(String query) {
        return explain(query, Collections.emptyMap());
    }

    /**
     * Get the execution plan for a given query with parameters, in multi/exec context
     * @param query Cypher query
     * @param params parameters map
     * @return response with the execution plan as list of strings
     */
    @Override
    @SuppressWarnings("unchecked")
    public Response<List<String>> explain(String query, Map<String, Object> params) {
        return appendWithResponse(
                GraphCommand.EXPLAIN,
                Arrays.asList(graphId, Utils.prepareQuery(query, params)),
                new Builder<List<String>>() {
                    @Override
                    public List<String> build(Object o) {
                        // GRAPH.EXPLAIN returns an array of byte arrays, convert to list of strings
                        if (o instanceof List) {
                            List<Object> responseList = (List<Object>) o;
                            List<String> result = new ArrayList<>(responseList.size());
                            for (Object item : responseList) {
                                if (item instanceof byte[]) {
                                    result.add(SafeEncoder.encode((byte[]) item));
                                } else {
                                    result.add(item.toString());
                                }
                            }
                            return result;
                        } else {
                            // Fallback for unexpected response format
                            return Arrays.asList(SafeEncoder.encode((byte[]) o));
                        }
                    }
                });
    }
}
