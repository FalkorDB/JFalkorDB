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
import redis.clients.jedis.*;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

/**
 * This class is extending Jedis Pipeline
 */
public class GraphPipelineImpl extends Pipeline implements com.falkordb.GraphPipeline {

    private final String graphId;
    private final QueuedReplies replies;
    /** True while sync() or syncAndReturnAll() reads the replies, so none is outstanding on the connection. */
    private boolean draining;

    /**
     * Creates a new pipeline for a given graph.
     * @param connection the connection to use
     * @param graph the graph to use
     * @param cache the graph cache to use
     * @param graphId the graph ID to use
     */
    public GraphPipelineImpl(Connection connection, Graph graph, GraphCache cache, String graphId) {
        this(connection, (reply, replyCache) -> new ResultSetImpl(reply, graph, replyCache), cache, graphId);
    }

    /**
     * Creates a new pipeline whose replies {@code decoder} decodes, given the cache each one is decoded with.
     */
    GraphPipelineImpl(
            Connection connection,
            BiFunction<List<Object>, GraphCache, ResultSet> decoder,
            GraphCache cache,
            String graphId) {
        super(connection);
        this.graphId = graphId;
        this.replies = new QueuedReplies(decoder, cache, () -> hasPipelinedResponse() && !draining);
    }

    /** How this pipeline decodes its replies; the context that created it watches it through this. */
    QueuedReplies replies() {
        return replies;
    }

    /**
     * Appends a command to the pipeline.
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
     * @return  a response which builds the result set with the query answer.
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
     * @return  a response which builds the result set with the query answer.
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
     * timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return  a response which builds the result set with the query answer.
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
     * timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return  a response which builds the result set with the query answer.
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
     * Invokes stored procedures without arguments
     * @param procedure procedure name to invoke
     * @return response with result set with the procedure data
     */
    @Override
    public Response<ResultSet> callProcedure(String procedure) {
        return callProcedure(procedure, Utils.DUMMY_LIST, Utils.DUMMY_MAP);
    }

    /**
     * Invokes stored procedure with arguments
     * @param procedure procedure name to invoke
     * @param args procedure arguments
     * @return response with result set with the procedure data
     */
    @Override
    public Response<ResultSet> callProcedure(String procedure, List<String> args) {
        return callProcedure(procedure, args, Utils.DUMMY_MAP);
    }

    /**
     * Invoke a stored procedure
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
     * for each operation's execution.
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
     * for each operation's execution.
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

    /**
     * Copies the graph
     * @param destinationGraphId duplicated graph name
     * @return response with the copy running time statistics
     */
    @Override
    public Response<String> copyGraph(String destinationGraphId) {
        return appendWithResponse(GraphCommand.COPY, Arrays.asList(graphId, destinationGraphId), BuilderFactory.STRING);
    }

    /**
     * Deletes the entire graph. The client's schema cache is cleared once the delete has run, when its reply is
     * read: replies queued before it are still decoded with the cache as it was when the batch was sent.
     * @return response with the deletion running time statistics
     */
    @Override
    public Response<String> deleteGraph() {
        Builder<String> builder = replies.delete();
        return replies.deleteQueued(appendWithResponse(GraphCommand.DELETE, Arrays.asList(graphId), builder), builder);
    }

    /**
     * Reads the replies to every command queued so far, without decoding them; each is decoded when its
     * {@code Response} is read. A {@link #deleteGraph()} that ran clears the schema cache now.
     */
    @Override
    public void sync() {
        draining = true;
        try {
            super.sync();
        } finally {
            draining = false;
        }
        replies.synced();
    }

    /**
     * Reads and decodes the replies to every command queued so far, in order.
     * @return the decoded replies, an error in place of each command that failed
     */
    @Override
    public List<Object> syncAndReturnAll() {
        replies.decodingAll();
        draining = true;
        try {
            return super.syncAndReturnAll();
        } finally {
            draining = false;
            replies.done();
        }
    }

    /**
     * Get the execution plan for a given query
     * @param query Cypher query
     * @return response with the execution plan as list of strings
     */
    @Override
    public Response<List<String>> explain(String query) {
        return explain(query, Collections.emptyMap());
    }

    /**
     * Get the execution plan for a given query with parameters
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
