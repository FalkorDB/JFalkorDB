package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FalkorDB#builder()} validation and wiring. These do not connect to a server:
 * {@code build()} assembles a lazy {@link redis.clients.jedis.JedisPool}, so no I/O happens until a
 * connection is actually borrowed.
 */
class ConfigBuilderTest {

    @Test
    void buildWithDefaultsReturnsDriver() throws IOException {
        try (Driver driver = FalkorDB.builder().build()) {
            assertNotNull(driver);
        }
    }

    @Test
    void buildWithFullConfigurationSucceeds() {
        assertDoesNotThrow(() -> {
            try (Driver driver = FalkorDB.builder()
                    .host("db.example.com")
                    .port(6380)
                    .credentials("user", "password")
                    .ssl(true)
                    .poolMaxTotal(64)
                    .poolMaxIdle(16)
                    .poolMaxWait(Duration.ofSeconds(30))
                    .connectionTimeout(Duration.ofSeconds(2))
                    .socketTimeout(Duration.ofSeconds(5))
                    .build()) {
                assertNotNull(driver);
            }
        });
    }

    @Test
    void settersReturnSameBuilder() {
        FalkorDB.Builder builder = FalkorDB.builder();
        assertSame(builder, builder.host("h"));
        assertSame(builder, builder.port(6379));
        assertSame(builder, builder.credentials("u", "p"));
        assertSame(builder, builder.credentials("p"));
        assertSame(builder, builder.ssl(true));
        assertSame(builder, builder.poolMaxTotal(8));
        assertSame(builder, builder.poolMaxIdle(8));
        assertSame(builder, builder.poolMaxWait(Duration.ZERO));
        assertSame(builder, builder.connectionTimeout(Duration.ZERO));
        assertSame(builder, builder.socketTimeout(Duration.ZERO));
    }

    @Test
    void rejectsNullOrBlankHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().host(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().host("   ").build());
    }

    @Test
    void acceptsWhitespacePaddedHost() {
        // A padded host is trimmed rather than rejected (and is not passed padded on to DNS lookup).
        assertDoesNotThrow(() -> FalkorDB.builder().host(" localhost ").build().close());
    }

    @Test
    void rejectsPortOutOfRange() {
        assertThrows(
                IllegalArgumentException.class, () -> FalkorDB.builder().port(0).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().port(-1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().port(65536).build());
    }

    @Test
    void acceptsPortBoundaries() {
        assertDoesNotThrow(() -> FalkorDB.builder().port(1).build().close());
        assertDoesNotThrow(() -> FalkorDB.builder().port(65535).build().close());
    }

    @Test
    void rejectsInvalidPoolSizing() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().poolMaxTotal(0).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().poolMaxTotal(-1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().poolMaxIdle(-1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().poolMaxTotal(4).poolMaxIdle(8).build());
    }

    @Test
    void acceptsMaxIdleEqualToMaxTotal() {
        assertDoesNotThrow(
                () -> FalkorDB.builder().poolMaxTotal(8).poolMaxIdle(8).build().close());
    }

    @Test
    void rejectsNegativeTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> FalkorDB.builder()
                .connectionTimeout(Duration.ofMillis(-1))
                .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().socketTimeout(Duration.ofMillis(-1)).build());
    }

    @Test
    void rejectsTimeoutAboveIntMax() {
        assertThrows(IllegalArgumentException.class, () -> FalkorDB.builder()
                .connectionTimeout(Duration.ofMillis((long) Integer.MAX_VALUE + 1))
                .build());
    }

    @Test
    void rejectsExtremeTimeoutWithIllegalArgumentException() {
        // A Duration so large that Duration.toMillis() would itself overflow must still surface the
        // documented IllegalArgumentException, not an ArithmeticException.
        assertThrows(IllegalArgumentException.class, () -> FalkorDB.builder()
                .connectionTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                .build());
    }

    @Test
    void acceptsZeroTimeoutsAndNegativePoolWait() {
        // Zero connect/socket timeouts (socket ZERO = no read deadline, #282) and a negative
        // poolMaxWait (wait indefinitely) are all valid.
        assertDoesNotThrow(() -> FalkorDB.builder()
                .connectionTimeout(Duration.ZERO)
                .socketTimeout(Duration.ZERO)
                .poolMaxWait(Duration.ofMillis(-1))
                .build()
                .close());
    }

    @Test
    void acceptsSubMillisecondTimeout() {
        // A positive sub-millisecond timeout must round up to 1ms (not collapse to 0 = infinite).
        assertDoesNotThrow(() -> FalkorDB.builder()
                .connectionTimeout(Duration.ofNanos(500_000))
                .build()
                .close());
    }
}
