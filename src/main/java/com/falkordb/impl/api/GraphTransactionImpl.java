package com.falkordb.impl.api;

import com.falkordb.Graph;
import com.falkordb.ResultSet;
import com.falkordb.impl.Utils;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.*;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * This class is extending Jedis Transaction
 *
 * <p>A transaction is used once: {@code GraphContext.multi()} starts it, and {@link #exec()} or
 * {@link #discard()} ends it. Starting it again with {@code multi()} is not supported.
 */
@SuppressWarnings("deprecation") // implements the deprecated GraphTransaction until 0.18.0 removes it
public class GraphTransactionImpl extends Transaction implements com.falkordb.GraphTransaction, GraphBatch {

    private final String graphId;
    private final QueuedReplies replies;
    /** True until exec() or discard(): the context sends MULTI as soon as it creates the transaction. */
    private boolean queuing = true;
    /** True while exec() reads and decodes the replies, so none is outstanding on the connection. */
    private boolean draining;
    /** How the transaction ended, or {@link Outcome#QUEUING} until it does. */
    private Outcome outcome = Outcome.QUEUING;
    /** What made exec() or discard() fail, if one did. */
    private @Nullable Throwable endedBy;
    /** How many commands {@link #appendWithResponse} queued. */
    private long appended;
    /** How many of them have had their replies read: all of them once {@link #exec()} ran the transaction. */
    private long readUpTo;

    /** How a transaction ended, which tells why the reply to a command it queued can't be read. */
    enum Outcome {
        QUEUING("the transaction hasn't run yet: call exec() first"),
        RAN("the command was queued after the transaction ran"),
        ABORTED("the transaction was aborted, because a key it watched changed, so the command didn't run"),
        DISCARDED("the transaction was discarded, so the command didn't run"),
        REFUSED("the server refused the transaction (EXECABORT), so the command didn't run"),
        FAILED("exec() or discard() failed, so whether the command ran is unknown");

        final String unread;

        Outcome(String unread) {
            this.unread = unread;
        }
    }

    /**
     * Creates a new transaction for a given graph.
     * @param connection the connection to use
     * @param graph the graph to use
     * @param cache the graph cache to use
     * @param graphId the graph ID to use
     */
    public GraphTransactionImpl(Connection connection, Graph graph, GraphCache cache, String graphId) {
        this(
                connection,
                (reply, replyCache, sent) -> new ResultSetImpl(reply, graph, replyCache, sent),
                cache,
                graphId);
    }

    /**
     * Creates a new transaction whose replies {@code decoder} decodes, given the cache each one is decoded with.
     */
    GraphTransactionImpl(Connection connection, ReplyDecoder decoder, GraphCache cache, String graphId) {
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
    @Override
    public <T> Response<T> appendWithResponse(
            ProtocolCommand protocolCommand, List<Object> arguments, Builder<T> builder) {
        CommandArguments commandArguments = new CommandArguments(protocolCommand);
        arguments.forEach(commandArguments::add);
        Response<T> response = this.appendCommand(new CommandObject<>(commandArguments, builder));
        appended++;
        return response;
    }

    @Override
    public String graphId() {
        return graphId;
    }

    @Override
    public long appended() {
        return appended;
    }

    @Override
    public long read() {
        return readUpTo;
    }

    @Override
    public @Nullable Throwable unreadCause() {
        return endedBy;
    }

    @Override
    public String unread() {
        return outcome.unread;
    }

    /** Whether commands are still queued inside {@code MULTI}: neither {@link #exec()} nor {@link #discard()} ran. */
    boolean queuing() {
        return queuing;
    }

    /**
     * Queues a graph command whose reply is a result set, decoded when it is read. A reply that fails to decode fails
     * every read of its response with the same exception (see {@link QueuedReply}).
     */
    private Response<ResultSet> queueResultSet(ProtocolCommand command, List<Object> arguments) {
        // a PROFILE reply carries no schema element, and needs none: its rows are plan lines
        return replies.queueResultSet(
                command != GraphCommand.PROFILE,
                arguments,
                (sentArguments, builder) -> appendWithResponse(command, sentArguments, builder));
    }

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query) {
        return queueResultSet(GraphCommand.QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING));
    }

    /**
     * Execute a Cypher read-oly query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query) {
        return queueResultSet(GraphCommand.RO_QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING));
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
        Utils.checkTimeout(timeout);
        return queueResultSet(
                GraphCommand.QUERY,
                Arrays.asList(graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING, Long.toString(timeout)));
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
        Utils.checkTimeout(timeout);
        return queueResultSet(
                GraphCommand.RO_QUERY,
                Arrays.asList(graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING, Long.toString(timeout)));
    }

    /**
     * Executes a cypher query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query, Map<String, Object> params) {
        return queueResultSet(
                GraphCommand.QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING));
    }

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params) {
        return queueResultSet(
                GraphCommand.RO_QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING));
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
        Utils.checkTimeout(timeout);
        return queueResultSet(
                GraphCommand.QUERY,
                Arrays.asList(
                        graphId,
                        Utils.prepareQuery(query, params),
                        Utils.COMPACT_STRING,
                        Utils.TIMEOUT_STRING,
                        Long.toString(timeout)));
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
        Utils.checkTimeout(timeout);
        return queueResultSet(
                GraphCommand.RO_QUERY,
                Arrays.asList(
                        graphId,
                        Utils.prepareQuery(query, params),
                        Utils.COMPACT_STRING,
                        Utils.TIMEOUT_STRING,
                        Long.toString(timeout)));
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
        return queueResultSet(GraphCommand.PROFILE, Arrays.asList(graphId, query, Utils.COMPACT_STRING));
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
        return queueResultSet(
                GraphCommand.PROFILE, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING));
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
     * @return the decoded replies, an error in place of each command that failed or whose reply failed to decode,
     *     or {@code null} if the transaction was aborted because a watched key changed
     */
    @Override
    public @Nullable List<Object> exec() {
        draining = true;
        boolean ran = false;
        boolean refused = false;
        List<Object> results = null;
        try {
            results = super.exec();
            QueuedReplyFailure.unwrap(results);
            ran = true;
            return results;
        } catch (JedisDataException e) {
            endedBy = e;
            // EXECABORT: a command failed to queue, so the server ran none of them. Any other error, such as EXEC
            // without MULTI after the server refused MULTI, can follow commands that ran as they were sent.
            refused = String.valueOf(e.getMessage()).startsWith("EXECABORT");
            throw e;
        } catch (RuntimeException | Error e) {
            endedBy = e;
            throw e;
        } finally {
            draining = false;
            queuing = false;
            if (refused || (ran && results == null)) {
                outcome = refused ? Outcome.REFUSED : Outcome.ABORTED;
                replies.discarded(); // refused, or aborted because a watched key changed: nothing ran
            } else if (ran) {
                outcome = Outcome.RAN;
                readUpTo = appended;
                replies.read();
            } else {
                outcome = Outcome.FAILED;
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
        } catch (RuntimeException | Error e) {
            endedBy = e;
            throw e;
        } finally {
            queuing = false;
            if (dropped) {
                outcome = Outcome.DISCARDED;
                replies.discarded();
            } else {
                outcome = Outcome.FAILED;
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
    public Response<List<String>> explain(String query, Map<String, Object> params) {
        List<Object> args = Arrays.asList(graphId, Utils.prepareQuery(query, params));
        return ExplainPlans.queue(builder -> appendWithResponse(GraphCommand.EXPLAIN, args, builder));
    }
}
