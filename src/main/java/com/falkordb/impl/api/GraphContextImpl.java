package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.Utils;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * An implementation of GraphContext. Allows sending Graph and some Redis commands,
 * within a specific connection context
 */
public class GraphContextImpl extends AbstractGraph implements GraphContext {

    private final Jedis connection;
    private final String graphId;
    private GraphCache cache;

    /**
     * Generates a new instance with a specific Jedis connection
     * @param connection Jedis connection
     * @param cache GraphCache
     * @param graphId graph id
     */
    public GraphContextImpl(Jedis connection, GraphCache cache, String graphId) {
        this.connection = connection;
        this.graphId = graphId;
        this.cache = cache;
    }

    /**
     * Sends the query over the instance only connection
     * @param preparedQuery prepared query
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String preparedQuery) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>)
                    connection.sendCommand(GraphCommand.QUERY, graphId, preparedQuery, Utils.COMPACT_STRING);
            return new ResultSetImpl(rawResponse, this, this.cache);
        } catch (GraphException rt) {
            throw rt;
        } catch (JedisDataException j) {
            throw new GraphException(j);
        }
    }

    /**
     * Sends the read-only query over the instance only connection
     * @param preparedQuery prepared query
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendReadOnlyQuery(String preparedQuery) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>)
                    connection.sendCommand(GraphCommand.RO_QUERY, graphId, preparedQuery, Utils.COMPACT_STRING);
            return new ResultSetImpl(rawResponse, this, this.cache);
        } catch (GraphException ge) {
            throw ge;
        } catch (JedisDataException de) {
            throw new GraphException(de);
        }
    }

    /**
     * Sends the query over the instance only connection
     * @param preparedQuery prepared query
     * @param timeout timeout in milliseconds
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String preparedQuery, long timeout) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>) connection.sendBlockingCommand(
                    GraphCommand.QUERY,
                    graphId,
                    preparedQuery,
                    Utils.COMPACT_STRING,
                    Utils.TIMEOUT_STRING,
                    Long.toString(timeout));
            return new ResultSetImpl(rawResponse, this, this.cache);
        } catch (GraphException rt) {
            throw rt;
        } catch (JedisDataException j) {
            throw new GraphException(j);
        }
    }

    /**
     * Sends the read-only query over the instance only connection
     * @param preparedQuery prepared query
     * @param timeout timeout in milliseconds
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendReadOnlyQuery(String preparedQuery, long timeout) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>) connection.sendBlockingCommand(
                    GraphCommand.RO_QUERY,
                    graphId,
                    preparedQuery,
                    Utils.COMPACT_STRING,
                    Utils.TIMEOUT_STRING,
                    Long.toString(timeout));
            return new ResultSetImpl(rawResponse, this, this.cache);
        } catch (GraphException ge) {
            throw ge;
        } catch (JedisDataException de) {
            throw new GraphException(de);
        }
    }

    /**
     * Sends the profile query over the instance only connection
     * @param preparedQuery prepared query
     * @return Result set with execution plan and performance metrics
     */
    @Override
    protected ResultSet sendProfile(String preparedQuery) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>)
                    connection.sendCommand(GraphCommand.PROFILE, graphId, preparedQuery, Utils.COMPACT_STRING);
            return new ResultSetImpl(rawResponse, this, this.cache);
        } catch (GraphException ge) {
            throw ge;
        } catch (JedisDataException de) {
            throw new GraphException(de);
        }
    }

    /**
     * Creates a new GraphTransaction transactional object
     * @return new GraphTransaction
     */
    @Override
    public GraphTransaction multi() {
        GraphTransactionImpl transaction =
                new GraphTransactionImpl(connection.getConnection(), this, this.cache, this.graphId);
        transaction.multi();
        return transaction;
    }

    /**
     * Creates a new GraphPipeline pipeline object
     * @return new GraphPipeline
     */
    @Override
    public GraphPipeline pipelined() {
        return new GraphPipelineImpl(connection.getConnection(), this, this.cache, this.graphId);
    }

    /**
     * Perfrom watch over given Redis keys
     * @param keys keys to watch
     * @return "OK"
     */
    @Override
    public String watch(String... keys) {
        return connection.watch(keys);
    }

    /**
     * Removes watch from all keys
     * @return "OK"
     */
    @Override
    public String unwatch() {
        return connection.unwatch();
    }

    /**
     * Copies the graph
     * @param destinationGraphId duplicated graph name
     * @return copy running time statistics
     * @throws GraphException if the server rejects the command, e.g. because the graph does not exist
     */
    @Override
    public String copyGraph(String destinationGraphId) {
        Object response;
        try {
            response = connection.sendCommand(GraphCommand.COPY, graphId, destinationGraphId);
        } catch (GraphException ge) {
            throw ge;
        } catch (JedisDataException de) {
            throw new GraphException(de);
        }
        return SafeEncoder.encode((byte[]) response);
    }

    /**
     * Deletes the entire graph. If the server rejects the command, the connection stays bound to this
     * context, which remains usable until it is closed.
     * @return delete running time statistics
     * @throws GraphException if the server rejects the command, e.g. because the graph does not exist
     */
    @Override
    public String deleteGraph() {
        Object response;
        try {
            response = connection.sendCommand(GraphCommand.DELETE, graphId);
        } catch (GraphException ge) {
            throw ge;
        } catch (JedisDataException de) {
            throw new GraphException(de);
        }
        // clear local state
        this.cache.clear();
        return SafeEncoder.encode((byte[]) response);
    }

    /**
     * Sends an explain command using GRAPH.EXPLAIN
     *
     * @param preparedQuery prepared query
     * @return execution plan as list of strings
     */
    @Override
    protected List<String> sendExplain(String preparedQuery) {
        try {
            return ExplainPlans.lines(connection.sendCommand(GraphCommand.EXPLAIN, graphId, preparedQuery));
        } catch (GraphException ge) {
            throw ge;
        } catch (JedisDataException de) {
            throw new GraphException(de);
        }
    }

    /**
     * closes the Jedis connection
     */
    @Override
    public void close() {
        this.connection.close();
    }

    @Override
    public int hashCode() {
        return this.connection.hashCode();
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }

        if (!(o instanceof GraphContextImpl)) {
            return false;
        }

        final GraphContextImpl other = (GraphContextImpl) o;
        return this.connection == other.connection;
    }
}
