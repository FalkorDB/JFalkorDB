package com.falkordb;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

public interface Graph extends Closeable {

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a result set
     */
    ResultSet query(String query);

    /**
     * Execute a Cypher read-only query.
     * @param query Cypher query
     * @return a result set
     */
    ResultSet readOnlyQuery(String query);

    /**
     * Execute a Cypher query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    ResultSet query(String query, long timeout);

    /**
     * Execute a Cypher read-only query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    ResultSet readOnlyQuery(String query, long timeout);

    /**
     * Executes a cypher query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a result set.
     */
    ResultSet query(String query, Map<String, Object> params);

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a result set.
     */
    ResultSet readOnlyQuery(String query, Map<String, Object> params);

    /**
     * Executes a cypher query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a result set.
     */
    ResultSet query(String query, Map<String, Object> params, long timeout);

    /**
     * Executes a cypher read-only query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a result set.
     */
    ResultSet readOnlyQuery(String query, Map<String, Object> params, long timeout);

    /**
     * Invokes stored procedures without arguments
     * @param procedure procedure name to invoke
     * @return result set with the procedure data
     */
    ResultSet callProcedure(String procedure);

    /**
     * Execute a Cypher query and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @return a result set with execution plan and performance metrics
     */
    ResultSet profile(String query);

    /**
     * Execute a Cypher query with parameters and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @param params parameters map
     * @return a result set with execution plan and performance metrics
     */
    ResultSet profile(String query, Map<String, Object> params);

    /**
     * Invokes stored procedure with arguments
     * @param procedure procedure name to invoke
     * @param args procedure arguments
     * @return result set with the procedure data
     */
    ResultSet callProcedure(String procedure, List<String> args);

    /**
     * Invoke a stored procedure
     * @param procedure - procedure to execute
     * @param args - procedure arguments
     * @param kwargs - procedure output arguments
     * @return result set with the procedure data
     */
    ResultSet callProcedure(String procedure, List<String> args  , Map<String, List<String>> kwargs);

    /**
     * Copies the graph
     * @param destinationGraphId duplicated graph name
     * @return copy running time statistics
     */
    String copyGraph(String destinationGraphId);

    /**
     * Deletes the entire graph
     * @return delete running time statistics
     */
    String deleteGraph();

    /**
     * Get the execution plan for a given query.
     * @param query Cypher query
     * @return execution plan as list of strings
     */
    List<String> explain(String query);

    /**
     * Get the execution plan for a given query with parameters.
     * @param query Cypher query
     * @param params parameters map
     * @return execution plan as list of strings
     */
    List<String> explain(String query, Map<String, Object> params);

    @Override
    void close();
}
