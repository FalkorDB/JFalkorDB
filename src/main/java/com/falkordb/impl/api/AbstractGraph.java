package com.falkordb.impl.api;

import com.falkordb.Graph;
import com.falkordb.ResultSet;
import com.falkordb.impl.Utils;
import java.util.List;
import java.util.Map;

/**
 * An abstract class to handle non implementation specific user requests
 */
public abstract class AbstractGraph implements Graph {

    /**
     * Sends a query to the redis graph. Implementation and context dependent
     * @param preparedQuery prepared query
     * @return Result set
     */
    protected abstract ResultSet sendQuery(String preparedQuery);

    /**
     * Sends a read-only query to the redis graph. Implementation and context dependent
     * @param preparedQuery prepared query
     * @return Result set
     */
    protected abstract ResultSet sendReadOnlyQuery(String preparedQuery);

    /**
     * Sends a query to the redis graph.Implementation and context dependent
     * @param preparedQuery prepared query
     * @param timeout timeout in milliseconds
     * @return Result set
     */
    protected abstract ResultSet sendQuery(String preparedQuery, long timeout);

    /**
     * Sends a read-query to the redis graph.Implementation and context dependent
     * @param preparedQuery prepared query
     * @param timeout timeout in milliseconds
     * @return Result set
     */
    protected abstract ResultSet sendReadOnlyQuery(String preparedQuery, long timeout);

    /**
     * Sends a profile query to the redis graph. Implementation and context dependent
     * @param preparedQuery prepared query
     * @return Result set with execution plan and metrics
     */
    protected abstract ResultSet sendProfile(String preparedQuery);
  
    /**
     * Sends an explain command. Implementation and context dependent
     * @param preparedQuery prepared query
     * @return execution plan as list of strings
     */
    protected abstract List<String> sendExplain(String preparedQuery);

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a result set
     */
    @Override    
    public ResultSet query(String query) {
        return sendQuery(query);
    }

    /**
     * Execute a Cypher read-only query.
     * @param query Cypher query
     * @return a result set
     */
    @Override
    public ResultSet readOnlyQuery(String query) {
        return sendReadOnlyQuery(query);
    }

    /**
     * Execute a Cypher query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    @Override
    public ResultSet query(String query, long timeout) {
        return sendQuery(query, timeout);
    }

    /**
     * Execute a Cypher read-only query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a result set
     */
    @Override
    public ResultSet readOnlyQuery(String query, long timeout) {
        return sendReadOnlyQuery(query, timeout);
    }

    /**
     * Executes a cypher query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a result set.
     */
    @Override
    public ResultSet query(String query, Map<String, Object> params) {
        String preparedQuery = Utils.prepareQuery(query, params);
        return sendQuery(preparedQuery);
    }

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return a result set.
     */
    @Override
    public ResultSet readOnlyQuery(String query, Map<String, Object> params) {
        String preparedQuery = Utils.prepareQuery(query, params);
        return sendReadOnlyQuery(preparedQuery);
    }

    /**
     * Executes a cypher query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a result set.
     */
    @Override
    public ResultSet query(String query, Map<String, Object> params, long timeout) {
        String preparedQuery = Utils.prepareQuery(query, params);
        return sendQuery(preparedQuery, timeout);
    }

    /**
     * Executes a cypher read-only query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return a result set.
     */
    @Override
    public ResultSet readOnlyQuery(String query, Map<String, Object> params, long timeout) {
        String preparedQuery = Utils.prepareQuery(query, params);
        return sendReadOnlyQuery(preparedQuery, timeout);
    }

    @Override
    public ResultSet callProcedure(String procedure){
        return callProcedure(procedure, Utils.DUMMY_LIST, Utils.DUMMY_MAP);
    }

    @Override
    public ResultSet callProcedure(String procedure, List<String> args){
        return callProcedure(procedure, args, Utils.DUMMY_MAP);
    }

    @Override
    public ResultSet callProcedure(String procedure, List<String> args  , Map<String, List<String>> kwargs){
        String preparedProcedure = Utils.prepareProcedure(procedure, args, kwargs);
        return query(preparedProcedure);
    }

    /**
     * Execute a Cypher query and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @return a result set with execution plan and performance metrics
     */
    @Override
    public ResultSet profile(String query) {
        return sendProfile(query);
    }

    /**
     * Execute a Cypher query with parameters and produce an execution plan augmented with metrics
     * for each operation's execution.
     * @param query Cypher query
     * @param params parameters map
     * @return a result set with execution plan and performance metrics
     */
    @Override
    public ResultSet profile(String query, Map<String, Object> params) {
        String preparedQuery = Utils.prepareQuery(query, params);
        return sendProfile(preparedQuery);
    }
  
    /**
     * Get the execution plan for a given query.
     * @param query Cypher query
     * @return execution plan as list of strings
     */
    @Override
    public List<String> explain(String query) {
        return sendExplain(query);
    }

    /**
     * Get the execution plan for a given query with parameters.
     * @param query Cypher query
     * @param params parameters map
     * @return execution plan as list of strings
     */
    @Override
    public List<String> explain(String query, Map<String, Object> params) {
        String preparedQuery = Utils.prepareQuery(query, params);
        return sendExplain(preparedQuery);
    }
}
