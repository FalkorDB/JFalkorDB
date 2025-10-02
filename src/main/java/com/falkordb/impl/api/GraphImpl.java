package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.SafeEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An implementation of GraphContextGenerator. 
 */
public class GraphImpl extends AbstractGraph implements GraphContextGenerator {

    private final DriverImpl driver;
    private final String graphId;
    private final GraphCache cache;

    /**
     * Creates a client running on the local machine
     * @param driver driver connection
     * @param graphId graph id
     */
    public GraphImpl(DriverImpl driver, String graphId) {
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
        try (GraphContext contextedGraph = new GraphContextImpl(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.query(preparedQuery);
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
        try (GraphContext contextedGraph = new GraphContextImpl(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.readOnlyQuery(preparedQuery);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the query from any Jedis connection received from the Jedis pool and
     * closes it once done
     * 
     * @param preparedQuery prepared query
     * @param timeout timeout in milliseconds
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendQuery(String preparedQuery, long timeout) {
        try (GraphContext contextedGraph = new GraphContextImpl(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.query(preparedQuery, timeout);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the read-only query from any Jedis connection received from the Jedis
     * pool and closes it once done
     * 
     * @param preparedQuery prepared query
     * @param timeout timeout in milliseconds
     * @return Result set with the query answer
     */
    @Override
    protected ResultSet sendReadOnlyQuery(String preparedQuery, long timeout) {
        try (GraphContext contextedGraph = new GraphContextImpl(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.readOnlyQuery(preparedQuery, timeout);
        }
    }

    /**
     * Overrides the abstract function.
     * Sends the profile query from any Jedis connection received from the Jedis
     * pool and closes it once done
     * 
     * @param preparedQuery prepared query
     * @return Result set with execution plan and performance metrics
     */
    @Override
    protected ResultSet sendProfile(String preparedQuery) {
        try (GraphContext contextedGraph = new GraphContextImpl(driver.getConnection(), this.cache, this.graphId)) {
            return contextedGraph.profile(preparedQuery);
        }
    }

    /**
     * Copies the graph
     *
     * @param destinationGraphId duplicated graph name
     * @return copy running time statistics
     */
    @Override
    public String copyGraph(String destinationGraphId) {
        try (Jedis conn = driver.getConnection()) {
            Object response = conn.sendCommand(GraphCommand.COPY, graphId, destinationGraphId);
            return SafeEncoder.encode((byte[]) response);
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
     * Sends an explain command using GRAPH.EXPLAIN
     * 
     * @param preparedQuery prepared query
     * @return execution plan as list of strings
     */
    @Override
    @SuppressWarnings("unchecked")
    protected List<String> sendExplain(String preparedQuery) {
        try (Jedis conn = driver.getConnection()) {
            Object response = conn.sendCommand(GraphCommand.EXPLAIN, graphId, preparedQuery);
            
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
        return new GraphContextImpl(driver.getConnection(), this.cache, this.graphId);
    }

    @Override
    public void close() {
        this.cache.clear();
    }
}
