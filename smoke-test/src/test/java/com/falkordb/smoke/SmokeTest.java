package com.falkordb.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * Runs the published {@code jfalkordb} artifact on a Java-8 runtime against a real FalkorDB on
 * {@code localhost:6379}, exercising the no-arg {@link FalkorDB#driver()} default contract end to
 * end (connect -&gt; query -&gt; read -&gt; close).
 *
 * <p>This is the Java-8 <em>runtime</em> guarantee that the compile-time guards ({@code release 8},
 * Animal Sniffer, Enforcer) cannot give: it proves the packaged jar plus its full runtime
 * dependency graph actually loads and works on JDK 8.
 */
class SmokeTest {

    @Test
    void connectQueryClose() throws Exception {
        try (Driver driver = FalkorDB.driver()) { // no-arg => localhost:6379 default contract
            GraphContextGenerator graph = driver.graph("jdk8-smoke");
            try {
                ResultSet created = graph.query("CREATE (:N {v: 1})");
                assertEquals(1, created.getStatistics().nodesCreated());

                ResultSet rs = graph.query("MATCH (n:N) RETURN n.v");
                Record row = rs.iterator().next();
                assertNotNull(row);
                assertEquals(1L, ((Number) row.getValue(0)).longValue());
            } finally {
                graph.deleteGraph();
            }
        }
    }
}
