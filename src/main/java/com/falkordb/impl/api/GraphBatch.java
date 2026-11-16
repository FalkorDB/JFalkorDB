package com.falkordb.impl.api;

import com.falkordb.ResultSet;
import java.io.Closeable;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Builder;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * What {@link GraphPipelineImpl} and {@link GraphTransactionImpl} share, which {@link ContextBatch} builds the
 * Jedis-free {@code GraphContext.Pipeline} and {@code GraphContext.Transaction} on: the graph commands, a raw
 * command, and how far the replies have been read.
 *
 * <p>Every command these methods queue is counted by {@link #appended()}, so a reply's position tells whether it
 * has been read.
 */
interface GraphBatch extends Closeable {

    Response<ResultSet> query(String query);

    Response<ResultSet> query(String query, long timeout);

    Response<ResultSet> query(String query, Map<String, @Nullable Object> params);

    Response<ResultSet> query(String query, Map<String, @Nullable Object> params, long timeout);

    Response<ResultSet> readOnlyQuery(String query);

    Response<ResultSet> readOnlyQuery(String query, long timeout);

    Response<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params);

    Response<ResultSet> readOnlyQuery(String query, Map<String, @Nullable Object> params, long timeout);

    Response<ResultSet> callProcedure(String procedure);

    Response<ResultSet> callProcedure(String procedure, List<String> args);

    Response<ResultSet> callProcedure(String procedure, List<String> args, Map<String, List<String>> kwargs);

    Response<ResultSet> profile(String query);

    Response<ResultSet> profile(String query, Map<String, @Nullable Object> params);

    Response<List<String>> explain(String query);

    Response<List<String>> explain(String query, Map<String, @Nullable Object> params);

    Response<String> deleteGraph();

    /**
     * Queues a command.
     *
     * @param <T> the decoded reply
     * @param command the command
     * @param arguments its arguments
     * @param builder decodes its reply
     * @return its response
     */
    <T> Response<T> appendWithResponse(ProtocolCommand command, List<Object> arguments, Builder<T> builder);

    /** The name of the graph, which is its key. */
    String graphId();

    /** How many commands {@link #appendWithResponse} queued, the ones the graph methods queue included. */
    long appended();

    /** How many of the {@link #appended()} commands have had their replies read, oldest first. */
    long read();

    /** Why a reply that hasn't been read can't be read now. */
    String unread();

    /** The exception that made the read or the transaction fail, which is why a reply can't be read; else null. */
    @Nullable
    Throwable unreadCause();
}
