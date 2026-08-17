package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.InvalidURIException;

/**
 * Unit tests for the {@code FalkorDB.builder()} wiring in {@link DriverImpl}: how resolved settings
 * map onto the Jedis client config and the commons-pool2 pool config, and that the builder defaults
 * reproduce the {@code driver()} configuration exactly. No server is required.
 */
class DriverConfigTest {

    @Test
    void clientConfigDefaultsMatchDriverFactory() {
        DefaultJedisClientConfig config = DriverImpl.buildClientConfig(
                null,
                null,
                false,
                DriverImpl.DEFAULT_CONNECTION_TIMEOUT_MILLIS,
                DriverImpl.DEFAULT_SOCKET_TIMEOUT_MILLIS);

        assertEquals(2000, config.getConnectionTimeoutMillis(), "default connect timeout");
        assertEquals(0, config.getSocketTimeoutMillis(), "default socket timeout (#282: no read deadline)");
        assertNull(config.getUser(), "no user by default");
        assertNull(config.getPassword(), "no password by default");
        assertNull(config.getSslOptions(), "no TLS by default");
    }

    @Test
    void legacyClientConfigEqualsBuilderDefaults() {
        // driver(host, port) resolves through clientConfig(null, null); assert it is field-for-field
        // identical to the builder's all-defaults client config, so builder().build() == driver().
        DefaultJedisClientConfig legacy = DriverImpl.clientConfig(null, null);
        DefaultJedisClientConfig builderDefaults = DriverImpl.buildClientConfig(
                null,
                null,
                false,
                DriverImpl.DEFAULT_CONNECTION_TIMEOUT_MILLIS,
                DriverImpl.DEFAULT_SOCKET_TIMEOUT_MILLIS);

        assertEquals(legacy.getConnectionTimeoutMillis(), builderDefaults.getConnectionTimeoutMillis());
        assertEquals(legacy.getSocketTimeoutMillis(), builderDefaults.getSocketTimeoutMillis());
        assertEquals(legacy.getUser(), builderDefaults.getUser());
        assertEquals(legacy.getPassword(), builderDefaults.getPassword());
        assertEquals(legacy.getSslOptions(), builderDefaults.getSslOptions());
    }

    @Test
    void clientConfigMapsCredentialsSslAndTimeouts() {
        DefaultJedisClientConfig config = DriverImpl.buildClientConfig("alice", "s3cret", true, 1500, 4000);

        assertEquals("alice", config.getUser());
        assertEquals("s3cret", config.getPassword());
        assertEquals(1500, config.getConnectionTimeoutMillis());
        assertEquals(4000, config.getSocketTimeoutMillis());
        assertNotNull(config.getSslOptions(), "ssl=true should map to non-null SslOptions (modern TLS path)");
    }

    @Test
    void clientConfigSupportsPasswordOnly() {
        DefaultJedisClientConfig config = DriverImpl.buildClientConfig(null, "s3cret", false, 2000, 0);

        assertNull(config.getUser(), "password-only auth has no username");
        assertEquals("s3cret", config.getPassword());
    }

    @Test
    void poolConfigDefaultsMatchDriverFactory() {
        GenericObjectPoolConfig<Jedis> builderDefaults = DriverImpl.buildPoolConfig(
                DriverImpl.DEFAULT_POOL_MAX_TOTAL, DriverImpl.DEFAULT_POOL_MAX_IDLE, DriverImpl.DEFAULT_POOL_MAX_WAIT);
        // The host/port factories rely on the JedisPool's own default GenericObjectPoolConfig.
        GenericObjectPoolConfig<Jedis> factoryDefaults = new GenericObjectPoolConfig<>();

        assertEquals(factoryDefaults.getMaxTotal(), builderDefaults.getMaxTotal(), "default maxTotal");
        assertEquals(factoryDefaults.getMaxIdle(), builderDefaults.getMaxIdle(), "default maxIdle");
        assertEquals(factoryDefaults.getMaxWaitDuration(), builderDefaults.getMaxWaitDuration(), "default maxWait");
        assertEquals(8, builderDefaults.getMaxTotal());
        assertEquals(8, builderDefaults.getMaxIdle());
        assertEquals(Duration.ofMillis(-1), builderDefaults.getMaxWaitDuration());
    }

    @Test
    void poolConfigMapsSizingAndWait() {
        GenericObjectPoolConfig<Jedis> config = DriverImpl.buildPoolConfig(64, 16, Duration.ofSeconds(30));

        assertEquals(64, config.getMaxTotal());
        assertEquals(16, config.getMaxIdle());
        assertEquals(Duration.ofSeconds(30), config.getMaxWaitDuration());
    }

    @Test
    void createValidatesItsArguments() {
        // create() is a public boundary (used by FalkorDB.builder()); it must reject invalid input
        // with IllegalArgumentException rather than surfacing a downstream NPE/other exception.
        assertThrows(
                IllegalArgumentException.class,
                () -> DriverImpl.create(null, 6379, null, null, false, 2000, 0, 8, 8, Duration.ofMillis(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> DriverImpl.create("localhost", 0, null, null, false, 2000, 0, 8, 8, Duration.ofMillis(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> DriverImpl.create("localhost", 6379, null, null, false, 2000, 0, 0, 8, Duration.ofMillis(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> DriverImpl.create("localhost", 6379, null, null, false, 2000, 0, 8, 8, null));
    }

    @Test
    void everyClientConfigPinsRESP2() {
        // Jedis 8 enables RESP3 auto-negotiation by default, but the legacy Jedis class this driver
        // pools cannot speak RESP3: it ignores the flag and logs a warning per connection. Every
        // factory must therefore hand Jedis a config with auto-negotiation switched off.
        assertFalse(
                DriverImpl.clientConfig(null, null).isAutoNegotiateProtocol(),
                "driver(host, port) must not auto-negotiate the protocol");
        assertFalse(
                DriverImpl.buildClientConfig("alice", "s3cret", true, 1500, 4000)
                        .isAutoNegotiateProtocol(),
                "builder() must not auto-negotiate the protocol");
        assertFalse(
                DriverImpl.uriClientConfig(URI.create("redis://localhost:6379")).isAutoNegotiateProtocol(),
                "driver(URI) must not auto-negotiate the protocol");
    }

    @Test
    void uriClientConfigMapsCredentialsDatabaseAndTimeouts() {
        DefaultJedisClientConfig config = DriverImpl.uriClientConfig(URI.create("redis://alice:s3cret@host:6379/3"));

        assertEquals("alice", config.getUser());
        assertEquals("s3cret", config.getPassword());
        assertEquals(3, config.getDatabase(), "database index from the URI path");
        assertNull(config.getSslOptions(), "redis:// is not TLS");
        assertEquals(
                DriverImpl.DEFAULT_CONNECTION_TIMEOUT_MILLIS,
                config.getConnectionTimeoutMillis(),
                "driver(URI) keeps the shared connect-timeout default");
        assertEquals(
                DriverImpl.DEFAULT_SOCKET_TIMEOUT_MILLIS,
                config.getSocketTimeoutMillis(),
                "driver(URI) keeps the shared socket-timeout default (#282: no read deadline)");
    }

    @Test
    void uriClientConfigEnablesTlsForRedissScheme() {
        DefaultJedisClientConfig config = DriverImpl.uriClientConfig(URI.create("rediss://host:6379"));

        assertNotNull(config.getSslOptions(), "rediss:// must enable TLS");
        assertEquals(0, config.getDatabase(), "no database index in the URI means database 0");
    }

    @Test
    void uriDriverRejectsAnInvalidUri() {
        // Preserves the exception Jedis' own URI-based pool constructor raised before we assembled
        // the client config ourselves.
        assertThrows(InvalidURIException.class, () -> new DriverImpl(URI.create("http://localhost:6379")));
    }
}
