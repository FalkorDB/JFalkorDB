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
 * This class is extending Jedis Transaction
 */
public class GraphTransactionImpl extends Transaction implements com.falkordb.GraphTransaction {

    private final Graph graph;
    private final String graphId;
    private GraphCache cache;

    public GraphTransactionImpl(Connection connection, Graph graph, GraphCache cache, String graphId) {
        // init as in Jedis
        super(connection, false);
        this.graph = graph;
        this.graphId = graphId;
        this.cache = cache;
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
     * @return a response which builds the result set with the query answer.
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
     * @return a response which builds the result set with the query answer.
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
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query, Map<String, Object> params, long timeout) {
        return appendWithResponse(GraphCommand.QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING,
                Utils.TIMEOUT_STRING, Long.toString(timeout)), new Builder<ResultSet>() {
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
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params, long timeout) {
        return appendWithResponse(GraphCommand.RO_QUERY, Arrays.asList(graphId, Utils.prepareQuery(query, params), Utils.COMPACT_STRING,
                Utils.TIMEOUT_STRING, Long.toString(timeout)), new Builder<ResultSet>() {
            @SuppressWarnings("unchecked")
            @Override
            public ResultSet build(Object o) {
                return new ResultSetImpl((List<Object>) o, graph, cache);
            }
        });
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
    public Response<ResultSet> callProcedure(String procedure, List<String> args,
            Map<String, List<String>> kwargs) {
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
        return appendWithResponse(GraphCommand.PROFILE, Arrays.asList(graphId, query), new Builder<ResultSet>() {
            @Override
            public ResultSet build(Object data) {
                return new ResultSetImpl((List<Object>) data, graph, cache);
            }
        });
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
        return appendWithResponse(GraphCommand.PROFILE, Arrays.asList(graphId, Utils.prepareQuery(query, params)), new Builder<ResultSet>() {
            @Override
            public ResultSet build(Object data) {
                return new ResultSetImpl((List<Object>) data, graph, cache);
            }
        });
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
     * Deletes the entire graph, in multi/exec context
     * @return response with the deletion running time statistics
     */
    @Override
    public Response<String> deleteGraph() {
        try {
            return appendWithResponse(GraphCommand.DELETE, Arrays.asList(graphId), new Builder<String>() {
                @Override
                public String build(Object o) {
                    return (String) o;
                }
            });
        } finally {
            cache.clear();
        }
    }
}
