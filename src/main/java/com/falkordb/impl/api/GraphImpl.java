package com.falkordb.impl.api;

import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.ResultSet;
import com.falkordb.impl.graph_cache.GraphCache;
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
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                return contextedGraph.query(preparedQuery);
            }
        });
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
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                return contextedGraph.readOnlyQuery(preparedQuery);
            }
        });
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
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                return contextedGraph.query(preparedQuery, timeout);
            }
        });
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
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                return contextedGraph.readOnlyQuery(preparedQuery, timeout);
            }
        });
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
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                return contextedGraph.profile(preparedQuery);
            }
        });
    }

    /**
     * Copies the graph from a connection received from the Jedis pool, and returns it once done.
     *
     * @param destinationGraphId duplicated graph name
     * @return copy running time statistics
     */
    @Override
    public String copyGraph(String destinationGraphId) {
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                return contextedGraph.copyGraph(destinationGraphId);
            }
        });
    }

    /**
     * Deletes the entire graph from a connection received from the Jedis pool, and returns it once done.
     *
     * @return delete running time statistics
     */
    @Override
    public String deleteGraph() {
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                // the context shares this graph's cache, and clears it once the delete succeeds
                return contextedGraph.deleteGraph();
            }
        });
    }

    /**
     * Sends an explain command using GRAPH.EXPLAIN, from a connection received from the Jedis pool, and
     * returns it once done.
     *
     * @param preparedQuery prepared query
     * @return execution plan as list of strings
     */
    @Override
    protected List<String> sendExplain(String preparedQuery) {
        return driver.routed(() -> {
            try (GraphContext contextedGraph = newContext()) {
                // explain(String) sends the query as it is, so it isn't prepared a second time
                return contextedGraph.explain(preparedQuery);
            }
        });
    }

    /**
     * Returns a new ContextedGraph bounded to a Jedis connection from the Jedis
     * pool.
     *
     * <p>In a Redis Cluster the context stays on the node that owned the graph when it was created, and
     * its queries are not retried after a redirect; create a new context after a reshard or failover.
     *
     * @return ContextedGraph
     */
    @Override
    public GraphContext getContext() {
        return newContext();
    }

    /** A context on a connection to the node that serves this graph (any node outside a cluster). */
    private GraphContext newContext() {
        return new GraphContextImpl(driver.getConnection(this.graphId), this.cache, this.graphId);
    }

    @Override
    public void close() {
        this.cache.clear();
    }
}
