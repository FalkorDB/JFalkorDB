package com.falkordb.examples;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;

/**
 * Shows the no-arg {@link FalkorDB#driver()} picking up connection settings from the environment
 * instead of hard-coding {@code localhost:6379}.
 *
 * <p>Resolution order: {@code FALKORDB_URL} (a full connection URI, also accepting the
 * FalkorDB-branded {@code falkor://}/{@code falkors://} aliases), else {@code FALKORDB_HOST}
 * together with {@code FALKORDB_PORT}, else the unchanged {@code localhost:6379} default — see
 * {@link FalkorDB#driver()} for the full contract. Only this no-arg overload consults the
 * environment; {@link FalkorDB#driver(String, int)}, {@link FalkorDB#driver(java.net.URI)}, and
 * {@link FalkorDB#builder()} always connect to exactly the arguments you pass them.
 *
 * <p>Needs a FalkorDB reachable at whichever of those resolves (defaults to {@code localhost:6379};
 * start one with {@code just db-up}). Build the examples with {@code just examples}, then run this
 * class — see {@code examples/README.md}.
 */
public final class EnvironmentConfiguredDriver {

    private EnvironmentConfiguredDriver() {}

    public static void main(String[] args) throws Exception {
        // Picks up FALKORDB_URL, or FALKORDB_HOST + FALKORDB_PORT, or falls back to localhost:6379.
        try (Driver driver = FalkorDB.driver()) {
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
