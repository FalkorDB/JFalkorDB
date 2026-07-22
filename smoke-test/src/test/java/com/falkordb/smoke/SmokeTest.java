package com.falkordb.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.graph_entities.GraphEntity;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.UUID;
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
        // Unique per run so the smoke never touches a pre-existing graph (deterministic MATCH, and
        // deleteGraph() only ever removes this run's throwaway graph — safe against a shared server).
        String graphId = "jdk8-smoke-" + UUID.randomUUID();
        try (Driver driver = FalkorDB.driver()) { // no-arg => localhost:6379 default contract
            GraphContextGenerator graph = driver.graph(graphId);
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

    /**
     * Proves the shipped JSpecify nullability annotations load and are reflectively visible on a
     * JDK-8 runtime: {@code GraphEntity.getProperty(String)} carries a runtime-retained, TYPE_USE
     * {@code @Nullable} on its return. (We deliberately reflect on a member annotation, not on the
     * package {@code @NullMarked}, to avoid JSpecify's documented Java-8 {@code ElementType.MODULE}
     * reflection caveat.)
     */
    @Test
    void jspecifyNullableIsVisibleAtRuntimeOnJava8() throws Exception {
        Method getProperty = GraphEntity.class.getMethod("getProperty", String.class);
        AnnotatedType returnType = getProperty.getAnnotatedReturnType();
        boolean nullablePresent = false;
        for (Annotation annotation : returnType.getAnnotations()) {
            if ("org.jspecify.annotations.Nullable".equals(
                    annotation.annotationType().getName())) {
                nullablePresent = true;
                break;
            }
        }
        assertTrue(
                nullablePresent,
                "GraphEntity.getProperty(String) should carry a runtime-visible @Nullable on JDK 8");
    }
}
