package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.Pool;

/**
 * End-to-end wiring of the {@code FalkorDB.builder()} options that replace a hand-built Jedis pool: that the
 * pool options reach the pool the driver borrows from, and that custom TLS reaches every connection the driver
 * opens. No FalkorDB is required: TLS is observed through a {@link RecordingSslContext} in front of a loopback
 * server that accepts and closes connections.
 */
@SuppressWarnings("deprecation") // reads the legacy TLS fields of the Jedis config, where custom TLS goes
class BuilderWiringTest {

    private static final HostnameVerifier ACCEPT_ALL = (host, session) -> true;

    private static Pool<Jedis> poolOf(Driver driver) {
        return ((Connections.Single) ((DriverImpl) driver).pool()).pool();
    }

    private static FalkorDB.Builder direct(FakeServer server) {
        return FalkorDB.builder()
                .host(server.host())
                .port(server.port())
                .autoDetectSentinel(false)
                .autoDetectCluster(false);
    }

    // --- pool options -------------------------------------------------------------------------------------

    @Test
    void poolOptionsReachThePoolTheDriverBorrowsFrom() throws Exception {
        try (FakeServer server = new FakeServer(FakeServer.Mode.CLOSE);
                Driver driver = direct(server)
                        .poolMaxTotal(16)
                        .poolMaxIdle(8)
                        .poolMinIdle(2)
                        .poolTestOnBorrow(true)
                        .poolTestWhileIdle(true)
                        .poolMaxWait(Duration.ofSeconds(3))
                        .build()) {
            Pool<Jedis> pool = poolOf(driver);

            assertEquals(16, pool.getMaxTotal());
            assertEquals(8, pool.getMaxIdle());
            assertEquals(2, pool.getMinIdle());
            assertTrue(pool.getTestOnBorrow());
            assertTrue(pool.getTestWhileIdle());
            assertEquals(Duration.ofSeconds(3), pool.getMaxWaitDuration());
            assertEquals(Duration.ofSeconds(30), pool.getDurationBetweenEvictionRuns());
        }
    }

    @Test
    void anUnconfiguredPoolHasNoChecksAndNoBackgroundTask() throws Exception {
        try (FakeServer server = new FakeServer(FakeServer.Mode.CLOSE);
                Driver driver = direct(server).build()) {
            Pool<Jedis> pool = poolOf(driver);

            assertEquals(0, pool.getMinIdle());
            assertFalse(pool.getTestOnBorrow());
            assertFalse(pool.getTestWhileIdle());
            assertTrue(pool.getDurationBetweenEvictionRuns().isNegative());
        }
    }

    // --- custom TLS reaches every connection --------------------------------------------------------------

    @Test
    void theSslContextIsUsedForTheDataConnections() throws Exception {
        RecordingSslContext tls = RecordingSslContext.create();
        try (FakeServer server = new FakeServer(FakeServer.Mode.CLOSE);
                Driver driver = direct(server).sslContext(tls).build()) {
            assertThrows(JedisException.class, () -> driver.graph("g").query("RETURN 1"));

            assertEquals(Collections.singletonList(server.address()), tls.requested());
        }
    }

    @Test
    void theSslContextIsUsedForTheSentinelAndClusterProbesToo() throws Exception {
        RecordingSslContext tls = RecordingSslContext.create();
        try (FakeServer server = new FakeServer(FakeServer.Mode.CLOSE);
                Driver driver = FalkorDB.builder()
                        .host(server.host())
                        .port(server.port())
                        .sslContext(tls)
                        .build()) {
            assertThrows(JedisException.class, () -> driver.graph("g").query("RETURN 1"));

            // The Sentinel probe, the cluster probe, then the data connection; a probe that can't connect
            // falls back to a direct connection.
            assertEquals(
                    Arrays.asList(server.address(), server.address(), server.address()),
                    tls.requested(),
                    "every probe must use the caller's TLS, not the JVM default");
        }
    }

    @Test
    void theSslContextIsUsedForTheSentinels() throws Exception {
        RecordingSslContext tls = RecordingSslContext.create();
        try (FakeServer sentinel = new FakeServer(FakeServer.Mode.CLOSE);
                Driver driver = FalkorDB.builder()
                        .sentinel("mymaster", sentinel.address())
                        .sslContext(tls)
                        .build()) {
            assertThrows(JedisException.class, () -> driver.graph("g").query("RETURN 1"));

            assertFalse(tls.requested().isEmpty(), "the connection to the Sentinel must use the caller's TLS");
            assertTrue(
                    tls.requested().stream().allMatch(sentinel.address()::equals),
                    tls.requested().toString());
        }
    }

    @Test
    void theSslContextIsUsedToLearnAClustersTopology() throws Exception {
        RecordingSslContext tls = RecordingSslContext.create();
        try (FakeServer node = new FakeServer(FakeServer.Mode.CLOSE);
                Driver driver = FalkorDB.builder()
                        .cluster(node.address())
                        .sslContext(tls)
                        .build()) {
            assertThrows(JedisException.class, () -> driver.graph("g").query("RETURN 1"));

            assertFalse(tls.requested().isEmpty(), "the topology connection must use the caller's TLS");
            assertTrue(
                    tls.requested().stream().allMatch(node.address()::equals),
                    tls.requested().toString());
        }
    }

    // --- custom TLS in the Jedis client configs -----------------------------------------------------------

    private static DefaultJedisClientConfig dataConfig(TlsOptions tls) {
        return DriverImpl.buildClientConfig("alice", "s3cret", true, tls, 1500, 0);
    }

    @Test
    void plainSslKeepsJedisDefaultTlsOptions() {
        DefaultJedisClientConfig config = DriverImpl.buildClientConfig("alice", "s3cret", true, null, 1500, 0);

        assertNotNull(config.getSslOptions(), "ssl(true) alone is unchanged: SslOptions.defaults()");
        assertFalse(config.isSsl(), "and none of the legacy fields");
        assertNull(config.getSslSocketFactory());
        assertNull(config.getSslParameters());
        assertNull(config.getHostnameVerifier());
    }

    @Test
    void aCustomContextAndParametersTakeJedisLegacyTlsPath() {
        RecordingSslContext context = RecordingSslContext.create();
        SSLParameters parameters = new SSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        DefaultJedisClientConfig config = dataConfig(TlsOptions.of(context, parameters, ACCEPT_ALL));

        // SslOptions would take precedence over, and so silently ignore, the custom settings.
        assertNull(config.getSslOptions());
        assertTrue(config.isSsl(), "the legacy fields only take effect with isSsl()");
        assertSame(context.factory(), config.getSslSocketFactory());
        assertSame(parameters, config.getSslParameters());
        assertSame(ACCEPT_ALL, config.getHostnameVerifier());
    }

    @Test
    void aVerifierAloneKeepsJedisDefaultTrustAndKeyMaterial() {
        DefaultJedisClientConfig config = dataConfig(TlsOptions.of(null, null, ACCEPT_ALL));

        assertNotNull(config.getSslOptions());
        assertFalse(config.isSsl());
        assertSame(ACCEPT_ALL, config.getHostnameVerifier());
    }

    @Test
    void everyDerivedConfigKeepsTheCustomTls() {
        RecordingSslContext context = RecordingSslContext.create();
        SSLParameters parameters = new SSLParameters();
        parameters.setEndpointIdentificationAlgorithm("");
        DefaultJedisClientConfig data = dataConfig(TlsOptions.of(context, parameters, ACCEPT_ALL));
        SentinelOptions sentinel = SentinelOptions.autoDetect();

        List<DefaultJedisClientConfig> derived = Arrays.asList(
                DriverImpl.clusterNodeConfig(data),
                DriverImpl.clusterProbeConfig(data),
                // ClusterConnections derives its topology config from the node config.
                DriverImpl.clusterProbeConfig(DriverImpl.clusterNodeConfig(data)),
                DriverImpl.sentinelClientConfig(data, sentinel),
                DriverImpl.probeClientConfig(data, sentinel));
        for (DefaultJedisClientConfig config : derived) {
            assertNull(config.getSslOptions());
            assertTrue(config.isSsl());
            assertSame(context.factory(), config.getSslSocketFactory());
            assertSame(parameters, config.getSslParameters());
            assertSame(ACCEPT_ALL, config.getHostnameVerifier());
        }
    }

    @Test
    void everyDerivedConfigKeepsAVerifierOnTopOfTheDefaults() {
        DefaultJedisClientConfig data = dataConfig(TlsOptions.of(null, null, ACCEPT_ALL));

        for (DefaultJedisClientConfig config : Arrays.asList(
                DriverImpl.clusterNodeConfig(data),
                DriverImpl.clusterProbeConfig(data),
                DriverImpl.sentinelClientConfig(data, SentinelOptions.autoDetect()),
                DriverImpl.probeClientConfig(data, SentinelOptions.autoDetect()))) {
            assertNotNull(config.getSslOptions());
            assertSame(ACCEPT_ALL, config.getHostnameVerifier());
        }
    }

    // --- database -----------------------------------------------------------------------------------------

    @Test
    void theDatabaseIsSelectedOnTheDataConnections() throws Exception {
        try (FakeServer server = new FakeServer(FakeServer.Mode.ANSWER);
                Driver driver = direct(server).database(3).build()) {
            // The fake server answers the query with +OK, which is no result set.
            assertThrows(RuntimeException.class, () -> driver.graph("g").query("RETURN 1"));

            assertTrue(server.received().contains("SELECT\r\n$1\r\n3\r\n"), server.received());
        }
    }
}
