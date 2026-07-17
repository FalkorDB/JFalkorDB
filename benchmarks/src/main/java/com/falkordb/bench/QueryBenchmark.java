package com.falkordb.bench;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Server-backed JMH benchmarks for the JFalkorDB client.
 *
 * <p>Uses {@link Mode#AverageTime} (smaller = better) so {@code benchmark-action/github-action-benchmark}
 * reads the metric direction correctly (it treats all JMH results as smaller-is-better).
 *
 * <p>By default it starts a pinned Testcontainers-managed FalkorDB; set both {@code FALKORDB_HOST} and
 * {@code FALKORDB_PORT} to reuse an external server (how {@code just bench} runs locally).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QueryBenchmark {

    // Pinned digest (v4.20.1) — matches the smoke-jdk8 / test harness images for reproducibility.
    private static final DockerImageName IMAGE = DockerImageName.parse(
            "falkordb/falkordb@sha256:9042fdc4e53f5390ca5a3993aa71506523970efb40ffb9a98e6a4b1a9a4f8862");

    private GenericContainer<?> container;
    private Driver driver;
    private GraphContextGenerator graph;

    @Setup(Level.Trial)
    public void setup() {
        String host = System.getenv("FALKORDB_HOST");
        String port = System.getenv("FALKORDB_PORT");
        if (host != null && !host.isEmpty() && port != null && !port.isEmpty()) {
            driver = FalkorDB.driver(host, Integer.parseInt(port.trim()));
        } else {
            container = new GenericContainer<>(IMAGE)
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());
            container.start();
            driver = FalkorDB.driver(container.getHost(), container.getMappedPort(6379));
        }
        graph = driver.graph("bench");
        graph.query("UNWIND range(0, 999) AS i CREATE (:N {id: i})");
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (graph != null) {
            graph.deleteGraph();
        }
        if (driver != null) {
            try {
                driver.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        if (container != null) {
            container.stop();
        }
    }

    @Benchmark
    public Object pointMatch() {
        return graph.query("MATCH (n:N {id: 42}) RETURN n");
    }

    @Benchmark
    public Object rangeCount() {
        return graph.query("MATCH (n:N) WHERE n.id < 500 RETURN count(n)");
    }

    @Benchmark
    public Object createNode() {
        return graph.query("CREATE (:M {v: 1})");
    }
}
