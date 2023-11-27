package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCaches;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.Pool;
import redis.clients.jedis.util.SafeEncoder;

/**
 *
 */
public class Graph extends AbstractGraph implements GraphContextGenerator {

    private final Pool<Jedis> pool;
    private final Jedis jedis;
    private final GraphCaches caches = new GraphCaches();

    /**
     * Creates a client running on the local machine

     */
    public Graph() {
        this("localhost", 6379);
    }

    /**
     * Creates a client running on the specific host/post
     *
     * @param host Redis host
     * @param port Redis port
     */
    public Graph(String host, int port) {
        this(new JedisPool(host, port));
    }

    /**
     * Creates a client using provided Jedis pool
     *
     * @param pool bring your own Jedis pool
     */
    public Graph(Pool<Jedis> pool) {
        this.pool = pool;
        this.jedis = null;
    }

    public Graph(Jedis jedis) {
        this.jedis = jedis;
        this.pool = null;
    }

    /**
     * Overrides the abstract function. Gets and returns a Jedis connection from the Jedis pool
     * @return a Jedis connection
     */
    @Override
    protected Jedis getConnection() {
        return jedis != null ? jedis : pool.getResource();
    }

    /**
     * Overrides the abstract function.
     * Sends the query from any Jedis connection received from the Jedis pool and closes it once done
     * @param graphId graph to be queried
     * @param preparedQuery prepared query
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String graphId, String preparedQuery){
        try (ContextedGraph contextedGraph = new ContextedGraph(getConnection())) {
            contextedGraph.setGraphCaches(caches);
            return contextedGraph.sendQuery(graphId, preparedQuery);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the read-only query from any Jedis connection received from the Jedis pool and closes it once done
     * @param graphId graph to be queried
     * @param preparedQuery prepared query
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendReadOnlyQuery(String graphId, String preparedQuery){
        try (ContextedGraph contextedGraph = new ContextedGraph(getConnection())) {
            contextedGraph.setGraphCaches(caches);
            return contextedGraph.sendReadOnlyQuery(graphId, preparedQuery);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the query from any Jedis connection received from the Jedis pool and closes it once done
     * @param graphId graph to be queried
     * @param preparedQuery prepared query
     * @param timeout
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String graphId, String preparedQuery, long timeout){
        try (ContextedGraph contextedGraph = new ContextedGraph(getConnection())) {
            contextedGraph.setGraphCaches(caches);
            return contextedGraph.sendQuery(graphId, preparedQuery, timeout);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the read-only query from any Jedis connection received from the Jedis pool and closes it once done
     * @param graphId graph to be queried
     * @param preparedQuery prepared query
     * @param timeout
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendReadOnlyQuery(String graphId, String preparedQuery, long timeout){
        try (ContextedGraph contextedGraph = new ContextedGraph(getConnection())) {
            contextedGraph.setGraphCaches(caches);
            return contextedGraph.sendReadOnlyQuery(graphId, preparedQuery, timeout);
        }
    }

    /**
     * Closes the Jedis pool
     */
    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
        if (jedis != null) {
            jedis.close();
        }
    }

    /**
     * Deletes the entire graph
     * @param graphId graph to delete
     * @return delete running time statistics
     */
    @Override
    public String deleteGraph(String graphId) {
        try (Jedis conn = getConnection()) {
            Object response = conn.sendCommand(GraphCommand.DELETE, graphId);
            //clear local state
            caches.removeGraphCache(graphId);
            return SafeEncoder.encode((byte[]) response);
        }
    }

    /**
     * Returns a new ContextedGraph bounded to a Jedis connection from the Jedis pool
     * @return ContextedGraph
     */
    @Override
    public GraphContext getContext() {
        ContextedGraph contextedGraph =  new ContextedGraph(getConnection());
        contextedGraph.setGraphCaches(this.caches);
        return contextedGraph;
    }
}
