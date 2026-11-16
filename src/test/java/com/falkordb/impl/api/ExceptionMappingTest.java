package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.exceptions.FalkorDBConnectionException;
import com.falkordb.exceptions.GraphException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.exceptions.JedisAccessControlException;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.exceptions.JedisMovedDataException;

/** The rules {@link ExceptionMapping} applies to what escapes a public method. */
class ExceptionMappingTest {

    @Test
    void aConnectionFailureBecomesTheClientsOwnTypeAndKeepsEverythingElse() {
        SocketTimeoutException timeout = new SocketTimeoutException("Read timed out");
        JedisConnectionException original = new JedisConnectionException(timeout);
        ConnectException suppressed = new ConnectException("Connection refused");
        original.addSuppressed(suppressed);

        RuntimeException mapped = ExceptionMapping.map(original);

        assertEquals(FalkorDBConnectionException.class, mapped.getClass());
        assertKept(original, mapped);
        assertSame(timeout, mapped.getCause());
    }

    @Test
    void aConnectionFailureWithoutACauseStillHasNone() {
        // "Failed to connect to host:port." has no cause, only the refused attempts as suppressed exceptions.
        JedisConnectionException original = new JedisConnectionException("Failed to connect to 127.0.0.1:1.");
        original.addSuppressed(new ConnectException("Connection refused"));

        RuntimeException mapped = ExceptionMapping.map(original);

        assertEquals(FalkorDBConnectionException.class, mapped.getClass());
        assertKept(original, mapped);
        assertNull(mapped.getCause());
        // left unset, as on the original, so it can still be set
        RuntimeException later = new RuntimeException("later");
        mapped.initCause(later);
        assertSame(later, mapped.getCause());
    }

    @Test
    void aPlainServerErrorBecomesAGraphExceptionWithTheServersMessage() {
        JedisDataException original = new JedisDataException("ERR Unknown configuration field");

        RuntimeException mapped = ExceptionMapping.map(original);

        assertEquals(GraphException.class, mapped.getClass());
        assertKept(original, mapped);
        assertEquals("ERR Unknown configuration field", mapped.getMessage());
        assertNull(mapped.getCause());
    }

    @Test
    void aServerErrorWithACauseKeepsIt() {
        IllegalStateException cause = new IllegalStateException("cause");
        JedisDataException original = new JedisDataException("ERR x", cause);

        RuntimeException mapped = ExceptionMapping.map(original);

        assertEquals(GraphException.class, mapped.getClass());
        assertSame(cause, mapped.getCause());
    }

    @Test
    void subclassesAndEverythingElsePassThroughAsTheSameInstance() {
        // A catch for any of these would stop matching if it were mapped.
        List<RuntimeException> untouched = Arrays.asList(
                new JedisAccessControlException("NOPERM no permissions"),
                new JedisMovedDataException("MOVED 1 h:1", new HostAndPort("h", 1), 1),
                new GraphException("ERR already ours"),
                new FalkorDBConnectionException("already ours"),
                new JedisClusterOperationException("Could not learn the cluster topology"),
                new JedisException(
                        "Could not get a resource from the pool", new NoSuchElementException("Pool exhausted")),
                new JedisException(
                        "Could not get a resource from the pool", new IllegalStateException("Pool not open")),
                new IllegalStateException("the driver is closed"),
                new IllegalArgumentException("bad"));
        for (RuntimeException failure : untouched) {
            assertSame(failure, ExceptionMapping.map(failure), failure.toString());
        }
    }

    @Test
    void mappingTwiceChangesNothing() {
        RuntimeException once = ExceptionMapping.map(new JedisConnectionException("down"));
        assertSame(once, ExceptionMapping.map(once));
    }

    @Test
    void mappedRunsTheCommandAndMapsWhatItThrows() {
        assertEquals("ok", ExceptionMapping.mapped(() -> "ok"));
        FalkorDBConnectionException e = assertThrowsExactly(
                FalkorDBConnectionException.class,
                () -> ExceptionMapping.mapped(() -> {
                    throw new JedisConnectionException("down");
                }));
        assertEquals("down", e.getMessage());
    }

    @Test
    void recognisesAConnectionFailureOfEitherType() {
        assertTrue(ExceptionMapping.isConnectionFailure(new JedisConnectionException("down")));
        assertTrue(ExceptionMapping.isConnectionFailure(new FalkorDBConnectionException("down")));
        assertFalse(ExceptionMapping.isConnectionFailure(new JedisDataException("ERR")));
        assertFalse(ExceptionMapping.isConnectionFailure(new JedisException("pool")));
    }

    private static void assertKept(RuntimeException original, RuntimeException mapped) {
        assertEquals(original.getMessage(), mapped.getMessage());
        assertSame(original.getCause(), mapped.getCause());
        assertArrayEquals(original.getStackTrace(), mapped.getStackTrace());
        assertArrayEquals(original.getSuppressed(), mapped.getSuppressed());
    }
}
