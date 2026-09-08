package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;

/**
 * Unit tests for the two derived client configs Sentinel support needs: the long-lived connection to
 * the Sentinels (which carries the failover subscription) and the one-shot detection probe. No server
 * is required.
 */
class SentinelClientConfigTest {

    private static DefaultJedisClientConfig dataConfig(boolean ssl, int connectMillis, int socketMillis) {
        return DriverImpl.buildClientConfig("alice", "s3cret", ssl, connectMillis, socketMillis);
    }

    @Test
    void sentinelConfigReusesTheDataCredentialsWhenNoSentinelCredentialsAreSet() {
        DefaultJedisClientConfig data = dataConfig(false, 2000, 0);
        DefaultJedisClientConfig sentinel = DriverImpl.sentinelClientConfig(data, SentinelOptions.autoDetect());

        // With no Sentinel ACL of its own, the single-ACL deployment needs no extra configuration at
        // all (this is what falkordb-go does).
        assertEquals("alice", sentinel.getUser());
        assertEquals("s3cret", sentinel.getPassword());
        assertEquals(2000, sentinel.getConnectionTimeoutMillis());
    }

    @Test
    void sentinelConfigNeverCarriesADatabaseIndex() {
        // driver(URI) takes a database index from the URI's path, and Jedis sends SELECT for any
        // non-zero index on every connection it opens -- including the ones to the Sentinels, which
        // implement no SELECT. JedisSentinelPool reports the resulting error as the Sentinel being
        // down, so a healthy deployment would look dead. Reusing the data config verbatim here, which
        // is the obvious implementation, is exactly what causes that.
        DefaultJedisClientConfig data = DefaultJedisClientConfig.builder()
                .user("alice")
                .password("s3cret")
                .database(7)
                .build();

        assertEquals(
                0,
                DriverImpl.sentinelClientConfig(data, SentinelOptions.autoDetect())
                        .getDatabase());
        assertEquals(
                0,
                DriverImpl.probeClientConfig(data, SentinelOptions.autoDetect()).getDatabase(),
                "the probe talks to the same Sentinel");
    }

    @Test
    void sentinelConfigSubstitutesSentinelCredentialsButKeepsTheTransport() {
        DefaultJedisClientConfig data = dataConfig(true, 1500, 4000);
        SentinelOptions options = SentinelOptions.explicit("mymaster", Collections.singletonList("a:26379"))
                .withCredentials("sentinel-user", "sentinel-password");

        DefaultJedisClientConfig sentinel = DriverImpl.sentinelClientConfig(data, options);

        assertEquals("sentinel-user", sentinel.getUser());
        assertEquals("sentinel-password", sentinel.getPassword());
        assertEquals(1500, sentinel.getConnectionTimeoutMillis());
        assertEquals(4000, sentinel.getSocketTimeoutMillis());
        assertNotNull(sentinel.getSslOptions(), "TLS must carry over to the Sentinel connection");
    }

    @Test
    void sentinelConfigKeepsAnUnboundedReadDeadline() {
        // JedisSentinelPool's master listener holds a SUBSCRIBE open on each Sentinel to hear about
        // failovers. A read deadline here would tear that subscription down on a timer, so the
        // driver's default of "no deadline" must survive the credential substitution.
        DefaultJedisClientConfig data = dataConfig(false, 2000, DriverImpl.DEFAULT_SOCKET_TIMEOUT_MILLIS);
        SentinelOptions options = SentinelOptions.autoDetect().withCredentials("u", "p");

        assertEquals(0, DriverImpl.sentinelClientConfig(data, options).getSocketTimeoutMillis());
    }

    @Test
    void probeConfigBoundsAnOtherwiseUnlimitedRead() {
        // The probe is a single round-trip, so it must not inherit "no read deadline": an endpoint
        // that completes the handshake and then goes silent would otherwise hang the first query.
        DefaultJedisClientConfig data = dataConfig(false, 2000, DriverImpl.DEFAULT_SOCKET_TIMEOUT_MILLIS);

        DefaultJedisClientConfig probe = DriverImpl.probeClientConfig(data, SentinelOptions.autoDetect());

        assertEquals(DriverImpl.DEFAULT_SENTINEL_PROBE_TIMEOUT_MILLIS, probe.getSocketTimeoutMillis());
        assertEquals(2000, probe.getConnectionTimeoutMillis(), "the connect timeout is untouched");
    }

    @Test
    void probeConfigRespectsAnExplicitReadDeadline() {
        DefaultJedisClientConfig data = dataConfig(false, 2000, 7000);

        assertEquals(
                7000,
                DriverImpl.probeClientConfig(data, SentinelOptions.autoDetect()).getSocketTimeoutMillis(),
                "a caller who set a socket timeout has already chosen the bound");
    }

    @Test
    void probeConfigCarriesSentinelCredentialsAndTls() {
        DefaultJedisClientConfig data = dataConfig(true, 2000, 0);
        SentinelOptions options = SentinelOptions.autoDetect().withCredentials("sentinel-user", "sentinel-password");

        DefaultJedisClientConfig probe = DriverImpl.probeClientConfig(data, options);

        assertEquals("sentinel-user", probe.getUser());
        assertEquals("sentinel-password", probe.getPassword());
        assertNotNull(probe.getSslOptions());
    }

    @Test
    void probeConfigUsesTheDataCredentialsByDefault() {
        DefaultJedisClientConfig data = dataConfig(false, 2000, 0);

        DefaultJedisClientConfig probe = DriverImpl.probeClientConfig(data, SentinelOptions.autoDetect());

        assertEquals("alice", probe.getUser());
        assertEquals("s3cret", probe.getPassword());
        assertNull(probe.getSslOptions());
    }

    @Test
    void derivedConfigsStillPinRESP2() {
        // Same reasoning as DriverConfigTest#everyClientConfigPinsRESP2: the legacy Jedis class this
        // driver pools cannot speak RESP3, and these two configs are new ways to reach it.
        DefaultJedisClientConfig data = dataConfig(false, 2000, 0);
        SentinelOptions options = SentinelOptions.autoDetect().withCredentials("u", "p");

        assertFalse(DriverImpl.sentinelClientConfig(data, options).isAutoNegotiateProtocol());
        assertFalse(DriverImpl.probeClientConfig(data, options).isAutoNegotiateProtocol());
        assertFalse(
                DriverImpl.probeClientConfig(data, SentinelOptions.autoDetect()).isAutoNegotiateProtocol());
    }
}
