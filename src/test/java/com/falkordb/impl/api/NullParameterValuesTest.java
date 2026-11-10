package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.AsyncGraph;
import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A query parameter's value may be null, which is sent as Cypher {@code null}, so every {@code params} map in the
 * query API is a {@code Map<String, @Nullable Object>}. This test pins the annotation on all of them, and what a
 * null value really sends, against an in-process fake server.
 */
class NullParameterValuesTest {

    private static final String QUERY_REPLY =
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.bulk("Query internal execution time: 1")));

    @Test
    void everyParamsMapTakesNullValues() {
        List<Method> checked = new ArrayList<>();
        for (Class<?> type :
                new Class<?>[] {Graph.class, AsyncGraph.class, GraphPipeline.class, GraphTransaction.class}) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i] == Map.class && "params".equals(paramName(method, i))) {
                        AnnotatedType value = ((AnnotatedParameterizedType) method.getAnnotatedParameterTypes()[i])
                                .getAnnotatedActualTypeArguments()[1];
                        assertTrue(
                                value.isAnnotationPresent(Nullable.class),
                                method + ": params values should be @Nullable");
                        checked.add(method);
                    }
                }
            }
        }
        // query x2, readOnlyQuery x2, profile and explain, on each of the four interfaces.
        assertEquals(24, checked.size(), "params overloads found: " + checked);
    }

    @Test
    void aNullValueIsSentAsCypherNull() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(command ->
                        FakeRedisServer.startsWith(command, "GRAPH.QUERY") ? QUERY_REPLY : FakeRedisServer.ok());
                Driver driver = DriverImpl.connect(
                        "127.0.0.1",
                        server.port(),
                        null,
                        null,
                        SentinelOptions.disabled(),
                        ClusterOptions.disabled())) {
            Map<String, @Nullable Object> params = Collections.singletonMap("x", null);
            driver.graph("g").query("RETURN $x", params);

            List<String> query = server.received().stream()
                    .filter(command -> FakeRedisServer.startsWith(command, "GRAPH.QUERY"))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
            assertEquals("CYPHER `x`=null RETURN $x", query.get(2));
        }
    }

    /**
     * The interfaces are compiled without {@code -parameters}, so the name comes from the position: the
     * {@code params} map is the only {@code Map} parameter after the query, while {@code kwargs} follows a list.
     */
    private static String paramName(Method method, int index) {
        Class<?>[] parameters = method.getParameterTypes();
        return index > 0 && parameters[index - 1] == String.class ? "params" : "other";
    }
}
