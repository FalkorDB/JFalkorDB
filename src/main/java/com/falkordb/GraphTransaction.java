package com.falkordb;

import redis.clients.jedis.Response;
import redis.clients.jedis.commands.BasicRedisPipeline;
import redis.clients.jedis.commands.BinaryRedisPipeline;
import redis.clients.jedis.commands.BinaryScriptingCommandsPipeline;
import redis.clients.jedis.commands.ClusterPipeline;
import redis.clients.jedis.commands.MultiKeyBinaryRedisPipeline;
import redis.clients.jedis.commands.MultiKeyCommandsPipeline;
import redis.clients.jedis.commands.RedisPipeline;
import redis.clients.jedis.commands.ScriptingCommandsPipeline;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * An interface which aligned to Jedis transactional interface
 */
public interface GraphTransaction extends
        MultiKeyBinaryRedisPipeline,
        MultiKeyCommandsPipeline, ClusterPipeline,
        BinaryScriptingCommandsPipeline, ScriptingCommandsPipeline,
        BasicRedisPipeline, BinaryRedisPipeline, RedisPipeline, Closeable {

    /**
     * Execute a Cypher query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query);

    /**
     * Execute a Cypher read-only query.
     * @param query Cypher query
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query);

    /**
     * Execute a Cypher query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query, long timeout);

    /**
     * Execute a Cypher read-only query with timeout.
     * @param query Cypher query
     * @param timeout timeout in milliseconds
     * @return a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query, long timeout);

    /**
     * Executes a cypher query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query, Map<String, Object> params);

    /**
     * Executes a cypher read-only query with parameters.
     * @param query Cypher query.
     * @param params parameters map.
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params);

    /**
     * Executes a cypher query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> query(String query, Map<String, Object> params, long timeout);

    /**
     * Executes a cypher read-only query with parameters and timeout.
     * @param query Cypher query.
     * @param params parameters map.
     * @param timeout timeout in milliseconds
     * @return  a response which builds the result set with the query answer.
     */
    Response<ResultSet> readOnlyQuery(String query, Map<String, Object> params, long timeout);

    /**
     * Invokes stored procedures without arguments
     * @param procedure procedure name to invoke
     * @return a response which builds result set with the procedure data
     */
    Response<ResultSet> callProcedure(String procedure);

    /**
     * Invokes stored procedure with arguments
     * @param procedure procedure name to invoke
     * @param args procedure arguments
     * @return a response which builds result set with the procedure data
     */
    Response<ResultSet> callProcedure(String procedure, List<String> args);

    /**
     * Invoke a stored procedure
     * @param procedure - procedure to execute
     * @param args - procedure arguments
     * @param kwargs - procedure output arguments
     * @return a response which builds result set with the procedure data
     */
    Response<ResultSet> callProcedure(String procedure, List<String> args  , Map<String, List<String>> kwargs);

    // Disabled due to bug in FalkorDB caused by using transactions in conjunction with graph copy
    /**
     * Copies the graph
     * @param destinationGraphId duplicated graph name
     * @return a response which builds the copy running time statistics
     */
    // Response<String> copyGraph(String destinationGraphId);

    /**
     * Deletes the entire graph
     * @return a response which builds the delete running time statistics
     */
    Response<String> deleteGraph();


    /**
     * executes the transaction
     * @return a list of the executed transaction commands answers, in case of successful transaction, null otherwise
     */
    List<Object> exec();

    /**
     * If object is in transaction mode,
     * flushes all previously queued commands in a transaction and restores the connection state to normal
     */
    void clear();

    /**
     * Executes the transaction and returns a list of the executed transaction commands answers
     * @return a list of the executed transaction commands answers
     */
    List<Response<?>> execGetResponse();

    /**
     * Flushes all previously queued commands in a transaction and restores the connection state to normal
     * @return "OK" if the transaction was successfully discarded, otherwise an exception is thrown
     */
    String discard();
}
