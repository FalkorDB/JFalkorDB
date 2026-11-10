package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.GraphPipeline;
import com.falkordb.GraphTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link Driver}, {@link GraphPipeline} and {@link GraphTransaction} close without a checked exception, so
 * try-with-resources needs no {@code catch (IOException)}.
 *
 * <p>The test methods declare no {@code throws} clause: that this file compiles is the main assertion. Before
 * these interfaces overrode {@code close()}, each try-with-resources here failed to compile with "unreported
 * exception java.io.IOException". The assertions check that the close each block makes really runs.
 */
class CloseWithoutCheckedExceptionTest {

    /** The reply to a write: statistics only, no result rows. */
    private static final String WRITE_REPLY =
            FakeRedisServer.array(FakeRedisServer.array(FakeRedisServer.bulk("Nodes created: 1")));

    private FakeRedisServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    /** A driver for {@code port} that connects straight to it, without probing for a Sentinel or a cluster. */
    private static Driver driver(int port) {
        return FalkorDB.builder()
                .host("127.0.0.1")
                .port(port)
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .build();
    }

    @Test
    void driverClosesInTryWithResources() {
        Driver closed;
        // Building a driver performs no I/O, so nothing needs to listen on the port.
        try (Driver driver = driver(1)) {
            closed = driver;
        }

        closed.close(); // closing again is harmless
        IllegalStateException e = assertThrows(
                IllegalStateException.class, () -> closed.graph("g").query("RETURN 1"));
        assertEquals("the driver is closed", e.getMessage());
    }

    @Test
    void pipelineClosesInTryWithResources() {
        server = new FakeRedisServer(
                c -> FakeRedisServer.startsWith(c, "GRAPH.QUERY") ? WRITE_REPLY : FakeRedisServer.ok());

        try (Driver driver = driver(server.port());
                GraphContext context = driver.graph("g").getContext();
                GraphPipeline pipeline = context.pipelined()) {
            pipeline.query("CREATE ()");
            assertEquals(0, server.count("GRAPH.QUERY"), "a pipeline sends nothing until it is synced or closed");
        }

        assertEquals(1, server.count("GRAPH.QUERY"), "closing the pipeline sent its queued query");
    }

    @Test
    void transactionClosesInTryWithResources() {
        server = new FakeRedisServer(
                c -> FakeRedisServer.startsWith(c, "GRAPH.QUERY") ? "+QUEUED\r\n" : FakeRedisServer.ok());

        try (Driver driver = driver(server.port());
                GraphContext context = driver.graph("g").getContext();
                GraphTransaction transaction = context.multi()) {
            transaction.query("CREATE ()");
        }

        assertEquals(1, server.count("MULTI"));
        assertEquals(1, server.count("DISCARD"), "closing the transaction without exec() discarded it");
        assertEquals(0, server.count("EXEC"));
    }
}
