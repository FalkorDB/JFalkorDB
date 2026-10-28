package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.Utils;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import java.util.ArrayList;
import java.util.Arrays;
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
    /** Set by {@link #askBeforeEachCommand()}. */
    private boolean asking;
    /** Whether the connection came with {@code ASKING} already sent, which covers the first command. */
    private boolean firstCommandAsked;

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
     * Makes this context send {@code ASKING} before every graph command. A routed command that follows an
     * {@code ASK} redirect runs on the node importing the graph's slot, which accepts a command for it only right
     * after {@code ASKING}; the reply's schema lookups are commands too.
     */
    void askBeforeEachCommand() {
        asking = true;
        firstCommandAsked = true; // the route sent ASKING when it lent the connection
    }

    private void askIfImporting() {
        if (!asking) {
            return;
        }
        if (firstCommandAsked) {
            firstCommandAsked = false;
        } else {
            connection.asking();
        }
    }

    /**
     * Decodes the reply of a command that may have written, a query or a profile. If a lookup the decoding needs
     * is redirected, the command has still run: the failure is reported as a {@link ReplyDecodeException}, which
     * {@link DriverImpl#routed} doesn't retry, rather than as a redirect it would.
     */
    private ResultSet decodeRan(List<Object> reply) {
        try {
            return new ResultSetImpl(reply, this, this.cache);
        } catch (GraphException e) {
            if (DriverImpl.isRedirect(e)) {
                throw new ReplyDecodeException(e);
            }
            throw e;
        }
    }

    /**
     * Sends the query over the instance only connection
     * @param preparedQuery prepared query
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String preparedQuery) {
        askIfImporting();
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>)
                    connection.sendCommand(GraphCommand.QUERY, graphId, preparedQuery, Utils.COMPACT_STRING);
            return decodeRan(rawResponse);
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
        askIfImporting();
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
        askIfImporting();
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>) connection.sendBlockingCommand(
                    GraphCommand.QUERY,
                    graphId,
                    preparedQuery,
                    Utils.COMPACT_STRING,
                    Utils.TIMEOUT_STRING,
                    Long.toString(timeout));
            return decodeRan(rawResponse);
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
        askIfImporting();
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
        askIfImporting();
        try {
            @SuppressWarnings("unchecked")
            List<Object> rawResponse = (List<Object>)
                    connection.sendCommand(GraphCommand.PROFILE, graphId, preparedQuery, Utils.COMPACT_STRING);
            return decodeRan(rawResponse);
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
     */
    @Override
    public String copyGraph(String destinationGraphId) {
        askIfImporting();
        Object response = connection.sendCommand(GraphCommand.COPY, graphId, destinationGraphId);
        return SafeEncoder.encode((byte[]) response);
    }

    /**
     * Deletes the entire graph
     * @return delete running time statistics
     */
    @Override
    public String deleteGraph() {
        askIfImporting();
        Object response;
        try {
            response = connection.sendCommand(GraphCommand.DELETE, graphId);
        } catch (Exception e) {
            connection.close();
            throw e;
        }
        // clear local state
        this.cache.clear();
        // caches.removeGraphCache(graphId);
        return SafeEncoder.encode((byte[]) response);
    }

    /**
     * Sends an explain command using GRAPH.EXPLAIN
     *
     * @param preparedQuery prepared query
     * @return execution plan as list of strings
     */
    @Override
    @SuppressWarnings("unchecked")
    protected List<String> sendExplain(String preparedQuery) {
        askIfImporting();
        try {
            Object response = connection.sendCommand(GraphCommand.EXPLAIN, graphId, preparedQuery);

            // GRAPH.EXPLAIN returns an array of byte arrays, convert to list of strings
            if (response instanceof List) {
                List<Object> responseList = (List<Object>) response;
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
                return Arrays.asList(SafeEncoder.encode((byte[]) response));
            }
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
