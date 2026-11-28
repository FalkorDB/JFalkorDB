package com.falkordb;

import java.net.URI;
import java.util.Locale;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.RedisProtocol;

/**
 * The RESP protocol the server-backed tests speak, from {@code FALKORDB_PROTOCOL} (system property or environment
 * variable): {@code resp2}, the default, or {@code resp3}. CI runs the suite once per protocol.
 *
 * <p>Only {@link FalkorDB#driver(URI)} can choose the protocol, with {@code ?protocol=3}: {@code driver(host, port)}
 * and {@link FalkorDB#builder()} always speak RESP2. So {@link #driver} builds from a URI under RESP3; {@link
 * TestServer}'s drivers come from it, and so do the ones {@code ClusterIT}, {@code ReplicaReadIT} and {@code
 * SentinelIT} build for their own servers.
 *
 * <p>A test that needs a builder option covers RESP2 only, whatever this says, and wraps its driver in {@link
 * #assertResp2}: {@code AsyncFalkorDBIT}, {@code ConfigBuilderIT}, {@code ContextPipelineIT}, {@code
 * ContextTransactionIT}, {@code PipelineIT}, {@code TransactionIT}, {@code SchemaHashIT} (the switch-off case), and
 * the explicit-cluster and explicit-Sentinel cases of {@code ClusterIT} and {@code SentinelIT}. Tests of how the
 * factories themselves connect stay as they are, also on RESP2: {@code InstantiationIT}, {@code ExceptionMappingIT},
 * {@code GraphErrorIT}, and the unreachable-node and Sentinel opt-out cases of {@code ClusterIT} and {@code
 * SentinelIT}.
 */
public final class TestProtocol {

    private static final RedisProtocol PROTOCOL =
            parse(System.getProperty("FALKORDB_PROTOCOL"), System.getenv("FALKORDB_PROTOCOL"));

    private TestProtocol() {}

    /**
     * Reads the protocol: the system property if non-blank, else the environment variable, else RESP2.
     *
     * @throws IllegalStateException for a value that names no protocol, so a typo can't quietly test RESP2 twice
     */
    static RedisProtocol parse(String property, String env) {
        String value = property != null && !property.trim().isEmpty() ? property : env;
        if (value == null || value.trim().isEmpty()) {
            return RedisProtocol.RESP2;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "resp2":
            case "2":
                return RedisProtocol.RESP2;
            case "resp3":
            case "3":
                return RedisProtocol.RESP3;
            default:
                throw new IllegalStateException("FALKORDB_PROTOCOL must be resp2 or resp3, but was \"" + value + "\"");
        }
    }

    /** The protocol the suite speaks. */
    public static RedisProtocol protocol() {
        return PROTOCOL;
    }

    /** Whether the suite speaks RESP3. */
    public static boolean isResp3() {
        return PROTOCOL == RedisProtocol.RESP3;
    }

    /** A {@code redis://} URI for the server that asks for the suite's protocol. */
    public static URI uri(String host, int port) {
        return URI.create("redis://" + host + ":" + port + (isResp3() ? "?protocol=3" : ""));
    }

    /**
     * A driver for the server that speaks the suite's protocol, checked: a driver that negotiated another protocol
     * fails here rather than quietly testing the wrong one.
     */
    public static Driver driver(String host, int port) {
        Driver driver = isResp3() ? FalkorDB.driver(uri(host, port)) : FalkorDB.driver(host, port);
        try {
            assertSpeaks(driver, PROTOCOL);
        } catch (RuntimeException | AssertionError e) {
            try {
                driver.close();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        return driver;
    }

    /**
     * Asserts that a driver a test built with {@link FalkorDB#builder()}, which can't choose the protocol, speaks
     * RESP2: such a test covers RESP2 only, under either value of {@code FALKORDB_PROTOCOL}.
     */
    public static Driver assertResp2(Driver driver) {
        assertSpeaks(driver, RedisProtocol.RESP2);
        return driver;
    }

    @SuppressWarnings("deprecation") // getConnection() is the only way to see the negotiated protocol
    private static void assertSpeaks(Driver driver, RedisProtocol expected) {
        try (Jedis connection = driver.getConnection()) {
            RedisProtocol negotiated = connection.getConnection().getRedisProtocol();
            // A connection that asked for no protocol reports none or RESP2; either way it speaks RESP2.
            RedisProtocol actual = negotiated == null ? RedisProtocol.RESP2 : negotiated;
            if (actual != expected) {
                throw new AssertionError("the driver speaks " + actual + ", but the test expects " + expected);
            }
        }
    }
}
