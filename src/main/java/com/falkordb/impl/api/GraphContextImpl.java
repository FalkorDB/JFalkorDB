package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import com.falkordb.ResultSet;
import com.falkordb.exceptions.GraphException;
import com.falkordb.impl.Utils;
import com.falkordb.impl.graph_cache.GraphCache;
import com.falkordb.impl.resultset.ResultSetImpl;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

/**
 * An implementation of GraphContext. Allows sending Graph and some Redis commands,
 * within a specific connection context.
 *
 * <p>The context owns its connection for its whole lifetime: the connection stays bound to it even
 * after a command fails, and only {@link #close()} releases it.
 */
public class GraphContextImpl extends AbstractGraph implements GraphContext {

    private final Jedis connection;
    private final String graphId;
    private GraphCache cache;
    // The routed call that made this context uses it on its own thread. The two flags are volatile only because
    // the context is also read from other threads (see closed), so a reader there sees their latest value.
    /** Set by {@link #askBeforeEachCommand()}. */
    private volatile boolean asking;
    /** Whether the connection came with {@code ASKING} already sent, which covers the first command. */
    private volatile boolean firstCommandAsked;
    /** The graph that created this context, which borrows a connection per query; null if built directly. */
    private final @Nullable GraphContextGenerator poolGraph;
    /**
     * The pipelines and transactions created on this context's connection. Held weakly, so the ones a caller
     * drops don't accumulate.
     */
    private final Set<QueuedReplies> connectionUsers = Collections.newSetFromMap(new WeakHashMap<>());

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Generates a new instance with a specific Jedis connection. Its pipelines and transactions have no other
     * connection to look a schema name up on, so a reply that needs one after this context is closed, or while
     * one of them awaits replies, fails with a {@link GraphException} (see {@link #decodeQueued}).
     * @param connection Jedis connection
     * @param cache GraphCache
     * @param graphId graph id
     */
    public GraphContextImpl(Jedis connection, GraphCache cache, String graphId) {
        this(connection, null, cache, graphId);
    }

    /**
     * A context created by a graph that borrows a connection per query. Pipelines and transactions on this
     * context fall back on that graph to decode their replies (see {@link #decodeQueued}).
     */
    GraphContextImpl(Jedis connection, @Nullable GraphContextGenerator poolGraph, GraphCache cache, String graphId) {
        this.connection = connection;
        this.graphId = graphId;
        this.cache = cache;
        this.poolGraph = poolGraph;
    }

    /**
     * Decodes a reply that a pipeline or transaction on this context read. Decoding may look up a label,
     * property-key or relationship-type id the cache doesn't hold.
     *
     * <p>While this context is open and no pipeline or transaction on it awaits a reply, the lookup runs on
     * this context's connection, which costs no extra connection. Otherwise a reply that needs a lookup is
     * decoded with a context of its own, borrowed from the graph that created this one; one that needs none
     * borrows nothing, so a context holding its pool's only connection can still read it. After {@link #close()} this
     * context's connection may already be lent to another thread, and while replies are outstanding a lookup
     * on it would read one of them instead of its own. That connection is borrowed before decoding starts,
     * because decoding can refresh the cache under its lock, and a thread must not wait for the pool while it
     * holds that lock: the threads that hold the pool's connections may be waiting for the lock.
     *
     * <p>A context built without that graph has no connection to borrow, so a reply that needs a lookup then
     * fails with a {@link GraphException} instead.
     *
     * <p>Before {@link #close()}, replies are expected to be read on the thread that uses this context, like
     * every other call on it.
     *
     * @throws GraphException if the reply needs a lookup that can't run on this context's connection and this
     *     context was built without a graph to borrow another from
     */
    ResultSet decodeQueued(List<Object> reply, GraphCache replyCache) {
        boolean isClosed = closed.get();
        if (!(isClosed || connectionAwaitsReplies())) {
            return new ResultSetImpl(reply, this, replyCache);
        }
        try {
            // Most replies need no lookup, so try without one first: then only a reply that does borrows a
            // connection. A context holding its pool's only connection can't borrow another.
            return new ResultSetImpl(reply, NoLookups.GRAPH, replyCache);
        } catch (NoLookups.Needed lookupNeeded) {
            GraphContextGenerator pool = poolGraph;
            if (pool == null) {
                throw new GraphException("Can't look up a schema name to decode this reply: the context "
                        + (isClosed ? "is closed" : "has a pipeline or transaction that awaits replies")
                        + ", and it was built without a graph to borrow another connection from");
            }
            try (GraphContext own = pool.getContext()) {
                return new ResultSetImpl(reply, own, replyCache);
            }
        }
    }

    /** Whether a pipeline or transaction opened on this context still awaits replies on the context's connection. */
    private boolean connectionAwaitsReplies() {
        for (QueuedReplies user : connectionUsers) {
            if (user.awaitsReplies()) {
                return true;
            }
        }
        return false;
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

    /**
     * Sends {@code ASKING} before a graph command once {@link #askBeforeEachCommand()} was called, except before the
     * first command: the route sent its {@code ASKING} when it lent the connection.
     */
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
            List<Object> rawResponse = (List<Object>) connection.sendCommand(
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
            List<Object> rawResponse = (List<Object>) connection.sendCommand(
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
                new GraphTransactionImpl(connection.getConnection(), this::decodeQueued, this.cache, this.graphId);
        transaction.multi();
        connectionUsers.add(transaction.replies());
        return transaction;
    }

    /**
     * Creates a new GraphPipeline pipeline object
     * @return new GraphPipeline
     */
    @Override
    public GraphPipeline pipelined() {
        GraphPipelineImpl pipeline =
                new GraphPipelineImpl(connection.getConnection(), this::decodeQueued, this.cache, this.graphId);
        connectionUsers.add(pipeline.replies());
        return pipeline;
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
        askIfImporting();
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
        askIfImporting();
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
        askIfImporting();
        try {
            return ExplainPlans.lines(connection.sendCommand(GraphCommand.EXPLAIN, graphId, preparedQuery));
        } catch (GraphException ge) {
            throw ge;
        } catch (JedisDataException de) {
            throw new GraphException(de);
        }
    }

    /**
     * Releases the context's connection, returning it to its pool when it was borrowed from one.
     * This is the only method that releases the connection.
     */
    @Override
    public void close() {
        // Once only: a second close() would hand the connection to the pool again.
        if (closed.compareAndSet(false, true)) {
            this.connection.close();
        }
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
