package com.falkordb.examples;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;

/**
 * Minimal quick-start: build a driver with {@link FalkorDB#builder()}, run a couple of queries, and
 * read the results.
 *
 * <p>Needs a FalkorDB reachable at the given host/port (defaults to {@code localhost:6379}; start one
 * with {@code just db-up}). Build the examples with {@code just examples}, then run this class — see
 * {@code examples/README.md}. Optional args: {@code <host> <port>}.
 */
public final class QuickStart {

    private QuickStart() {}

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        // FalkorDB.builder() is a fluent superset of FalkorDB.driver(host, port); with no other
        // options set it produces exactly the same driver as the plain factory.
        try (Driver driver = FalkorDB.builder().host(host).port(port).build()) {
            GraphContextGenerator graph = driver.graph("social");
            try {
                graph.query("CREATE (:Person {name: 'Alice', age: 32}), (:Person {name: 'Bob', age: 47})");

                ResultSet people = graph.query("MATCH (p:Person) RETURN p.name, p.age ORDER BY p.age");
                for (Record record : people) {
                    System.out.println(record.getString("p.name") + " is " + record.getValue("p.age"));
                }
            } finally {
                // Best-effort cleanup: don't let a failure here mask a real error from the queries above.
                try {
                    graph.deleteGraph(); // remove this example's throwaway graph
                } catch (RuntimeException cleanupError) {
                    System.err.println("Failed to delete example graph: " + cleanupError.getMessage());
                }
            }
        }
    }
}
