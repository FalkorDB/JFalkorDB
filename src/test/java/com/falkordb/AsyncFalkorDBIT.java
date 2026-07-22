package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * System tests for {@link AsyncFalkorDB} against a real FalkorDB server (via {@link TestServer}).
 *
 * <p>Fans the blocking client out over a fixed <em>platform</em>-thread pool to validate the facade's
 * delegation and concurrent fan-out independently of the thread model. It deliberately does
 * <strong>not</strong> use a virtual-thread executor: on JDK 21–23 cold connection creation in
 * commons-pool2 ({@code GenericObjectPool.create()} is {@code synchronized}) pins the carrier thread, and
 * a creation storm from many virtual threads can starve the carrier pool. Virtual-thread pinning is
 * characterized separately by the Wave-5 pinning check; the finite {@code poolMaxWait} below keeps a
 * mis-sized pool from hanging the suite.
 */
public class AsyncFalkorDBIT {

    private static final int FAN_OUT = 64;
    private static final int POOL = 16;

    private Driver driver;
    private GraphContextGenerator client;
    private ExecutorService executor;

    private AsyncGraph asyncClient() {
        driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .poolMaxTotal(POOL)
                .poolMaxWait(Duration.ofSeconds(20))
                .build();
        client = driver.graph("async-it");
        executor = newExecutor();
        return AsyncFalkorDB.wrap(client, executor);
    }

    @Test
    public void fansOutConcurrentQueries() throws Exception {
        AsyncGraph async = asyncClient();

        List<CompletableFuture<ResultSet>> futures = new ArrayList<>(FAN_OUT);
        for (int i = 0; i < FAN_OUT; i++) {
            Map<String, Object> params = Collections.singletonMap("i", i);
            futures.add(async.query("CREATE (:Item {i:$i})", params));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        for (CompletableFuture<ResultSet> future : futures) {
            assertEquals(1, future.join().getStatistics().nodesCreated());
        }
        ResultSet count = async.readOnlyQuery("MATCH (n:Item) RETURN count(n)").get(30, TimeUnit.SECONDS);
        Long total = count.iterator().next().getValue(0);
        assertEquals(FAN_OUT, total.longValue());
    }

    @Test
    public void serverErrorsCompleteTheFutureExceptionally() {
        AsyncGraph async = asyncClient();

        CompletableFuture<ResultSet> future = async.query("THIS IS NOT CYPHER");

        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof RuntimeException, "server error should surface as the cause");
    }

    @AfterEach
    public void cleanup() throws IOException {
        try {
            if (client != null) {
                client.deleteGraph();
            }
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } finally {
                if (executor != null) {
                    executor.shutdownNow();
                }
                if (driver != null) {
                    driver.close();
                }
            }
        }
    }

    private static ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(POOL);
    }
}
