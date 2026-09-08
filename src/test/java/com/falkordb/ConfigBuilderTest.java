package com.falkordb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
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

    @Test
    void sentinelSettersReturnSameBuilder() {
        FalkorDB.Builder builder = FalkorDB.builder();
        assertSame(builder, builder.sentinel("mymaster", "a:26379"));
        assertSame(builder, builder.sentinel("mymaster", Collections.singletonList("a:26379")));
        assertSame(builder, builder.sentinelCredentials("u", "p"));
        assertSame(builder, builder.sentinelCredentials("p"));
        assertSame(builder, builder.autoDetectSentinel(false));
    }

    @Test
    void buildsAgainstAnExplicitSentinelDeployment() {
        assertDoesNotThrow(() -> {
            try (Driver driver = FalkorDB.builder()
                    .sentinel("mymaster", "sentinel-a:26379", "sentinel-b:26379", "sentinel-c:26379")
                    .credentials("user", "password")
                    .sentinelCredentials("sentinel-user", "sentinel-password")
                    .build()) {
                assertNotNull(driver);
            }
        });
    }

    @Test
    void sentinelBuildStaysLazy() {
        // The whole point of resolving the pool on first use: naming a deployment that does not exist
        // must not cost a DNS lookup or a connection attempt, exactly as a plain host/port build does
        // not. If this ever regresses it will hang for the connect timeout rather than fail outright,
        // so the assertion is on elapsed time.
        long startedAt = System.nanoTime();
        assertDoesNotThrow(() -> FalkorDB.builder()
                .sentinel("mymaster", "sentinel-a.invalid:26379")
                .build()
                .close());
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(elapsedMillis < 1000, "building a driver must not connect, but took " + elapsedMillis + "ms");
    }

    @Test
    void autoDetectingBuildStaysLazyToo() {
        long startedAt = System.nanoTime();
        assertDoesNotThrow(
                () -> FalkorDB.builder().host("db.invalid").port(6380).build().close());
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(
                elapsedMillis < 1000, "Sentinel detection must not run at build(), but took " + elapsedMillis + "ms");
    }

    @Test
    void rejectsASentinelWithoutAMasterName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().sentinel(null, "a:26379").build());
    }

    @Test
    void rejectsASentinelWithoutAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().sentinel("mymaster").build());
        assertThrows(IllegalArgumentException.class, () -> FalkorDB.builder()
                .sentinel("mymaster", (java.util.Collection<String>) null)
                .build());
    }

    @Test
    void rejectsAMalformedSentinelAddress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FalkorDB.builder().sentinel("mymaster", "no-port-here").build());
    }

    @Test
    void autoDetectionCanBeSwitchedOff() {
        assertDoesNotThrow(
                () -> FalkorDB.builder().autoDetectSentinel(false).build().close());
    }

    @Test
    void aDriverClosedBeforeUseFailsFastInsteadOfConnecting() {
        // Resolving the pool lazily means a closed-but-never-used driver could otherwise run the whole
        // resolution -- Sentinel probe included -- just to hand back a pool it immediately closes.
        // That would be real network I/O after close(), surfacing as a pool error rather than as the
        // programming mistake it is. The host is unroutable, so the elapsed time also shows that no
        // connection was attempted.
        Driver driver = FalkorDB.builder().host("db.invalid").build();
        assertDoesNotThrow(driver::close);

        long startedAt = System.nanoTime();
        assertThrows(IllegalStateException.class, driver::getConnection);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(elapsedMillis < 1000, "a closed driver must not connect, but took " + elapsedMillis + "ms");
    }

    @Test
    void closingADriverTwiceIsHarmless() {
        Driver driver = FalkorDB.builder().host("db.invalid").build();
        assertDoesNotThrow(driver::close);
        assertDoesNotThrow(driver::close);
    }
}
