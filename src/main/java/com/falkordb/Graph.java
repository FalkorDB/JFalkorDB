package com.falkordb;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

import redis.clients.jedis.Jedis;

public interface Graph extends Closeable {

    static Graph with(Jedis jedis) {
      return new com.falkordb.impl.api.Graph(jedis);
    }

    /**
     * Execute a Cypher query.
     * @param graphId a graph to perform the query on
     * @param query Cypher query
     * @return a result set
     */
    ResultSet query(String graphId, String query);

    /**
     * Execute a Cypher read-only query.
     * @param graphId a graph to perform the query on
     * @param query Cypher query
     * @return a result set
     */
    ResultSet readOnlyQuery(String graphId, String query);

    /**
     * Execute a Cypher query with timeout.
     * @param graphId a graph to perform the query on
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    ResultSet query(String graphId, String query, long timeout);

    /**
     * Execute a Cypher read-only query with timeout.
     * @param graphId a graph to perform the query on
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    ResultSet readOnlyQuery(String graphId, String query, long timeout);

    /**
     * Executes a cypher query with parameters.
     * @param graphId a graph to perform the query on.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a result set.
     */
    ResultSet query(String graphId, String query, Map<String, Object> params);

    /**
     * Executes a cypher read-only query with parameters.
     * @param graphId a graph to perform the query on.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a result set.
     */
    ResultSet readOnlyQuery(String graphId, String query, Map<String, Object> params);

    /**
     * Executes a cypher query with parameters and timeout.
     * @param graphId a graph to perform the query on.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout
     * @return a result set.
     */
    ResultSet query(String graphId, String query, Map<String, Object> params, long timeout);

    /**
     * Executes a cypher read-only query with parameters and timeout.
     * @param graphId a graph to perform the query on.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout
     * @return a result set.
     */
    ResultSet readOnlyQuery(String graphId, String query, Map<String, Object> params, long timeout);

    /**
     * Invokes stored procedures without arguments
     * @param graphId a graph to perform the query on
     * @param procedure procedure name to invoke
     * @return result set with the procedure data
     */
    ResultSet callProcedure(String graphId, String procedure);

    /**
     * Invokes stored procedure with arguments
     * @param graphId a graph to perform the query on
     * @param procedure procedure name to invoke
     * @param args procedure arguments
     * @return result set with the procedure data
     */
    ResultSet callProcedure(String graphId, String procedure, List<String> args);

    /**
     * Invoke a stored procedure
     * @param graphId a graph to perform the query on
     * @param procedure - procedure to execute
     * @param args - procedure arguments
     * @param kwargs - procedure output arguments
     * @return result set with the procedure data
     */
    ResultSet callProcedure(String graphId, String procedure, List<String> args  , Map<String, List<String>> kwargs);

    /**
     * Deletes the entire graph
     * @param graphId graph to delete
     * @return delete running time statistics
     */
    String deleteGraph(String graphId);

    @Override
    void close();
}
