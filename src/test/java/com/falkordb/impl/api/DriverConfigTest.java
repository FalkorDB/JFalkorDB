package com.falkordb.impl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;

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
}
