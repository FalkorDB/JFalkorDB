package com.falkordb.impl.api;

import com.falkordb.Graph;
import com.falkordb.ResultSet;
import com.falkordb.impl.Utils;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import redis.clients.jedis.*;
import redis.clients.jedis.commands.ProtocolCommand;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This class is extending Jedis Pipeline
 */
public class GraphPipelineImpl extends Pipeline implements com.falkordb.GraphPipeline {

    private final Graph graph;
    private GraphCache cache;
    private final String graphId;

    public GraphPipelineImpl(Connection connection, Graph graph, GraphCache cache, String graphId){
        super(connection);
        this.graph = graph;
        this.cache = cache;
        this.graphId = graphId;
    }

    protected <T> Response<T> appendWithResponse(ProtocolCommand protocolCommand, List<Object> arguments, Builder<T> builder) {
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
        return appendWithResponse(GraphCommand.QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
    }

    /**
     * Execute a Cypher read-oly query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query) {
        return appendWithResponse(GraphCommand.RO_QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
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
        return appendWithResponse(GraphCommand.QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING,
                Long.toString(timeout)), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
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
        return appendWithResponse(GraphCommand.RO_QUERY, Arrays.asList(graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING,
                Long.toString(timeout)), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
    }

    /**
     * Executes a cypher query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return  a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query, Map<String, Object> params) {
        return appendWithResponse(GraphCommand.QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
    }

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return  a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params) {
        return appendWithResponse(GraphCommand.RO_QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
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
        return appendWithResponse(GraphCommand.QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING, Utils.TIMEOUT_STRING,
                Long.toString(timeout)), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
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
        return appendWithResponse(GraphCommand.RO_QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING, Utils.TIMEOUT_STRING,
                Long.toString(timeout)), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
    }

    /**
     * Invokes stored procedures without arguments
     * @param procedure procedure name to invoke
     * @return response with result set with the procedure data
     */
    @Override
    public Response<ResultSet> callProcedure(String procedure){
        return callProcedure(procedure, Utils.DUMMY_LIST, Utils.DUMMY_MAP);
    }

    /**
     * Invokes stored procedure with arguments
     * @param procedure procedure name to invoke
     * @param args procedure arguments
     * @return response with result set with the procedure data
     */
    @Override
    public Response<ResultSet> callProcedure(String procedure, List<String> args  ){
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
    public Response<ResultSet> callProcedure(String procedure, List<String> args,
                                                  Map<String, List<String>> kwargs) {
        String preparedProcedure = Utils.prepareProcedure(procedure, args, kwargs);
        return query(preparedProcedure);
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
     * Deletes the entire graph
     * @return response with the deletion running time statistics
     */
    @Override
    public Response<String> deleteGraph(){
        try {
            return appendWithResponse(GraphCommand.DELETE, Arrays.asList(graphId), BuilderFactory.STRING);
        } finally {
            cache.clear();
        }

    }
}
