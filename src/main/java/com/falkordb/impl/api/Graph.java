package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.SafeEncoder;

/**
 *
 */
public class Graph extends AbstractGraph implements GraphContextGenerator {

    private final Driver driver;
    private final String graphId;
    private final GraphCache cache;

    /**
     * Creates a client running on the local machine
     */
    public Graph(Driver driver, String graphId) {
        this.driver = driver;
        this.graphId = graphId;
        this.cache = new GraphCache();
    }

    /**
     * Overrides the abstract function.
     * Sends the query from any Jedis connection received from the Jedis pool and
     * closes it once done
     * 
     * @param preparedQuery prepared query
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String preparedQuery) {
        try (ContextedGraph contextedGraph = new ContextedGraph(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.sendQuery(preparedQuery);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the read-only query from any Jedis connection received from the Jedis
     * pool and closes it once done
     * 
     * @param preparedQuery prepared query
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendReadOnlyQuery(String preparedQuery) {
        try (ContextedGraph contextedGraph = new ContextedGraph(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.sendReadOnlyQuery(preparedQuery);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the query from any Jedis connection received from the Jedis pool and
     * closes it once done
     * 
     * @param preparedQuery prepared query
     * @param timeout
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String preparedQuery, long timeout) {
        try (ContextedGraph contextedGraph = new ContextedGraph(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.sendQuery(preparedQuery, timeout);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the read-only query from any Jedis connection received from the Jedis
     * pool and closes it once done
     * 
     * @param preparedQuery prepared query
     * @param timeout
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendReadOnlyQuery(String preparedQuery, long timeout) {
        try (ContextedGraph contextedGraph = new ContextedGraph(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.sendReadOnlyQuery(preparedQuery, timeout);
        }
    }

    /**
     * Deletes the entire graph
     * 
     * @return delete running time statistics
     */
    @Override
    public String deleteGraph() {
        try (Jedis conn = driver.getConnection()) {
            Object response = conn.sendCommand(GraphCommand.DELETE, graphId);
            // clear local state
            cache.clear();
            return SafeEncoder.encode((byte[]) response);
        }
    }

    /**
     * Returns a new ContextedGraph bounded to a Jedis connection from the Jedis
     * pool
     * 
     * @return ContextedGraph
     */
    @Override
    public GraphContext getContext() {
        return new ContextedGraph(driver.getConnection(), this.cache, this.graphId);
    }

    @Override
    public void close() {
        this.cache.clear();
    }
}
