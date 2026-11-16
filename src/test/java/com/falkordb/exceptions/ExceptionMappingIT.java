package com.falkordb.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContext;
import com.falkordb.TestServer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ClientKillParams;

/**
 * Against a real server: a plain error reply from a {@code Driver} method is a {@link GraphException} with the
 * server's message, and a connection that breaks under a {@code GraphContext} is a {@link
 * FalkorDBConnectionException}.
 */
class ExceptionMappingIT {

    @Test
    void anUnknownConfigurationParameterIsAGraphExceptionWithTheServersMessage() {
        try (Driver driver = TestServer.driver()) {
            GraphException e =
                    assertThrowsExactly(GraphException.class, () -> driver.configGet("INVALID_CONFIG_PARAM_XYZ"));
            assertTrue(e.getMessage().contains("Unknown configuration field"), e.getMessage());

            e = assertThrowsExactly(GraphException.class, () -> driver.configSet("INVALID_CONFIG_PARAM_XYZ", 1));
            assertTrue(e.getMessage().contains("Unknown configuration field"), e.getMessage());
        }
    }

    @Test
    void deletingAMissingUdfLibraryIsAGraphException() {
        try (Driver driver = TestServer.driver()) {
            GraphException e = assertThrowsExactly(
                    GraphException.class, () -> driver.udfDelete("exception_mapping_it_no_such_library"));
            assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
        }
    }

    @Test
    void aPasswordTheServerDoesNotExpectIsAGraphException() {
        // The server has no password, so AUTH fails with a plain error while the connection is opened.
        try (Driver driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .credentials("not-the-password")
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .build()) {
            GraphException e = assertThrowsExactly(GraphException.class, driver::listGraphs);
            assertTrue(e.getMessage().contains("AUTH"), e.getMessage());
            assertThrowsExactly(GraphException.class, () -> driver.graph("exception_mapping_it")
                    .query("RETURN 1"));
        }
    }

    @Test
    void aContextWhoseConnectionTheServerClosedFailsWithAConnectionException() {
        try (Driver driver = FalkorDB.builder()
                .host(TestServer.host())
                .port(TestServer.port())
                .poolMaxTotal(1)
                .poolMaxIdle(1)
                .poolMaxWait(Duration.ofSeconds(5))
                .autoDetectSentinel(false)
                .autoDetectCluster(false)
                .build()) {
            long clientId;
            try (Jedis connection = driver.getConnection()) {
                clientId = connection.clientId();
            }
            // a pool of one, so the context gets the connection just returned
            try (GraphContext context = driver.graph("exception_mapping_it").getContext();
                    Jedis admin = new Jedis(TestServer.host(), TestServer.port())) {
                assertEquals(1L, admin.clientKill(new ClientKillParams().id(Long.toString(clientId))));

                assertThrowsExactly(FalkorDBConnectionException.class, () -> context.query("RETURN 1"));
            }
        }
    }
}
