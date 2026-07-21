package com.falkordb;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Test-only endpoint factory for a FalkorDB server.
 *
 * <p>By default it starts a single, shared Testcontainers-managed {@code falkordb/falkordb} container
 * (on a dynamic port), reused across the whole test suite and torn down at JVM exit — so {@code just
 * verify} needs zero manual server setup. Set <strong>both</strong> {@code FALKORDB_HOST} and {@code
 * FALKORDB_PORT} to reuse an already-running server instead (no container is started); setting only
 * one is rejected.
 *
 * <p>Set {@code FALKORDB_IMAGE} (system property or env var, e.g. {@code falkordb/falkordb:edge}) to
 * run the container against a specific image/tag — used to matrix over FalkorDB versions; unset/blank
 * uses the pinned {@link FalkorDbImage#DEFAULT} digest. Ignored when the {@code FALKORDB_HOST}/{@code
 * FALKORDB_PORT} external override is in effect.
 *
 * <p><strong>External-server safety:</strong> some system tests mutate server-wide state (for example
 * {@code UdfIT} globally lists/deletes UDF libraries). When you point this at an external server via
 * the env override, use a <em>dedicated disposable</em> instance — never a shared or production
 * server. The default container path is always safe because the container is throwaway.
 */
public final class TestServer {

    private static final String host;
    private static final int port;
    private static final boolean external;

    // One driver/pool shared by all graph() callers, closed once at JVM shutdown (see graph()).
    private static Driver sharedDriver;

    static {
        String envHost = System.getenv("FALKORDB_HOST");
        String envPort = System.getenv("FALKORDB_PORT");
        boolean hasHost = envHost != null && !envHost.isEmpty();
        boolean hasPort = envPort != null && !envPort.isEmpty();
        if (hasHost != hasPort) {
            throw new IllegalStateException(
                    "Set BOTH FALKORDB_HOST and FALKORDB_PORT to use an external FalkorDB, or neither.");
        }
        if (hasHost && hasPort) {
            external = true;
            host = envHost;
            port = parsePort(envPort);
        } else {
            external = false;
            DockerImageName image = FalkorDbImage.resolve(
                    FalkorDbImage.pickOverride(System.getProperty("FALKORDB_IMAGE"), System.getenv("FALKORDB_IMAGE")));
            GenericContainer<?> container =
                    new GenericContainer<>(image).withExposedPorts(6379).waitingFor(Wait.forListeningPort());
            container.start(); // shared for the whole JVM; Testcontainers' Ryuk stops it at exit
            host = container.getHost();
            port = container.getMappedPort(6379);
        }
    }

    private TestServer() {}

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("FALKORDB_PORT must be a valid integer, but was \"" + value + "\"", e);
        }
    }

    /** Host of the shared test server (container host, or the {@code FALKORDB_HOST} override). */
    public static String host() {
        return host;
    }

    /** Port of the shared test server (mapped container port, or the {@code FALKORDB_PORT} override). */
    public static int port() {
        return port;
    }

    /** {@code true} when tests run against an external (env-override) server rather than a container. */
    public static boolean isExternal() {
        return external;
    }

    /** A new driver connected to the shared test server; the caller is responsible for closing it. */
    public static Driver driver() {
        return FalkorDB.driver(host, port);
    }

    /**
     * A graph context on the shared test server, backed by a single suite-wide driver/pool that is
     * closed once at JVM shutdown. Closing the returned graph only clears its cache (it does not own
     * the driver), so callers must not close the underlying driver — this is what keeps each call
     * from leaking a {@code JedisPool}.
     */
    public static GraphContextGenerator graph(String name) {
        return sharedDriver().graph(name);
    }

    private static synchronized Driver sharedDriver() {
        if (sharedDriver == null) {
            sharedDriver = FalkorDB.driver(host, port);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    sharedDriver.close();
                } catch (Exception ignored) {
                    // best-effort cleanup at JVM exit
                }
            }));
        }
        return sharedDriver;
    }
}
