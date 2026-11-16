package com.falkordb.examples;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Reply;
import com.falkordb.ResultSet;
import java.util.List;

/**
 * Pipelines and transactions on a {@link GraphContext}: graph queries and other Redis commands queued together, each
 * returning a {@link Reply} that is read once the batch has run.
 *
 * <p>Needs a FalkorDB reachable at the given host/port (defaults to {@code localhost:6379}; start one with
 * {@code just db-up}). Optional args: {@code <host> <port>}.
 */
public final class PipelineAndTransaction {

    private PipelineAndTransaction() {}

    /**
     * Fills a graph with a pipeline, then updates it in a transaction that runs only if nobody changed the graph
     * since it was watched, and deletes the graph.
     *
     * @param args optional {@code <host> <port>}, defaulting to {@code localhost 6379}
     */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port;
        try {
            port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;
        } catch (NumberFormatException e) {
            System.err.println("Usage: PipelineAndTransaction [<host> [<port>]]; not a port: " + args[1]);
            return;
        }

        try (Driver driver = FalkorDB.builder().host(host).port(port).build()) {
            GraphContextGenerator graph = driver.graph("pipeline-example");
            // A context keeps one connection, which its pipelines and transactions use.
            try (GraphContext context = graph.getContext()) {
                // A pipeline sends each command as it is queued, and reads the replies together.
                GraphContext.Pipeline pipeline = context.pipeline();
                Reply<ResultSet> alice = pipeline.query("CREATE (:Person {name: 'Alice'})");
                pipeline.query("CREATE (:Person {name: 'Bob'})");
                Reply<Object> counter = pipeline.sendCommand("INCR", "pipeline-example-people"); // a Long
                pipeline.sync();
                System.out.println(alice.get().getStatistics().nodesCreated() + " node; counter at " + counter.get());

                // A transaction runs its commands at once, or not at all if a watched key changed.
                context.watch("pipeline-example");
                GraphContext.Transaction transaction = context.transaction();
                Reply<ResultSet> friends = transaction.query(
                        "MATCH (a:Person {name: 'Alice'}), (b:Person {name: 'Bob'}) CREATE (a)-[:KNOWS]->(b)");
                transaction.sendCommand("INCR", "pipeline-example-people");
                List<Object> results = transaction.exec();
                if (results == null) {
                    System.out.println("Someone changed the graph since watch(): nothing ran");
                } else {
                    System.out.println(friends.get().getStatistics().relationshipsCreated() + " relationship");
                }

                // Every Reply<?> from here on is read after sync(); try-with-resources syncs a pipeline too.
                try (GraphContext.Pipeline cleanup = context.pipeline()) {
                    cleanup.sendCommand("UNLINK", "pipeline-example-people");
                    cleanup.deleteGraph(); // DEL of the graph's key is refused: deleteGraph() keeps the cache right
                }
            }
        }
    }
}
