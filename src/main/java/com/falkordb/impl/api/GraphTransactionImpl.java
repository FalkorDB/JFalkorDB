package com.falkordb.impl.api;

import com.falkordb.Graph;
import com.falkordb.ResultSet;
import com.falkordb.impl.Utils;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import redis.clients.jedis.Builder;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.Client;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.util.List;
import java.util.Map;

/**
 * This class is extending Jedis Transaction
 */
public class GraphTransactionImpl extends Transaction
        implements com.falkordb.GraphTransaction {

    private final Graph graph;
    private final String graphId;
    private GraphCache cache;

    public GraphTransactionImpl(Client client, Graph graph, GraphCache cache, String graphId) {
        // init as in Jedis
        super(client);
        this.graph = graph;
        this.graphId = graphId;
        this.cache = cache;
    }

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    @Override
    public Response<ResultSet> query(String query) {
        client.sendCommand(GraphCommand.QUERY, graphId, query, Utils.COMPACT_STRING);
        return getResponse(new Builder<ResultSet>() {
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
        client.sendCommand(GraphCommand.RO_QUERY, graphId, query, Utils.COMPACT_STRING);
        return getResponse(new Builder<ResultSet>() {
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
        client.sendCommand(GraphCommand.QUERY, graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING,
                Long.toString(timeout));
        return getResponse(new Builder<ResultSet>() {
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
        client.sendCommand(GraphCommand.RO_QUERY, graphId, query, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING,
                Long.toString(timeout));
        return getResponse(new Builder<ResultSet>() {
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
        String preparedQuery = Utils.prepareQuery(query, params);
        client.sendCommand(GraphCommand.QUERY, graphId, preparedQuery, Utils.COMPACT_STRING);
        return getResponse(new Builder<ResultSet>() {
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
        String preparedQuery = Utils.prepareQuery(query, params);
        client.sendCommand(GraphCommand.RO_QUERY, graphId, preparedQuery, Utils.COMPACT_STRING);
        return getResponse(new Builder<ResultSet>() {
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
        String preparedQuery = Utils.prepareQuery(query, params);
        client.sendCommand(GraphCommand.QUERY, graphId, preparedQuery, Utils.COMPACT_STRING, Utils.TIMEOUT_STRING,
                Long.toString(timeout));
        return getResponse(new Builder<ResultSet>() {
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
        String preparedQuery = Utils.prepareQuery(query, params);
        client.sendCommand(GraphCommand.RO_QUERY, graphId, preparedQuery, Utils.COMPACT_STRING,
                Utils.TIMEOUT_STRING, Long.toString(timeout));
        return getResponse(new Builder<ResultSet>() {
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
     * Deletes the entire graph, in multi/exec context
     * @return response with the deletion running time statistics
     */
    @Override
    public Response<String> deleteGraph() {
        client.sendCommand(GraphCommand.DELETE, graphId);
        Response<String> response = getResponse(BuilderFactory.STRING);
        cache.clear();
        return response;
    }
}
