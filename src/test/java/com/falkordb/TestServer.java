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
 * <p><strong>External-server safety:</strong> some system tests mutate server-wide state (for example
 * {@code UdfTest} globally lists/deletes UDF libraries). When you point this at an external server via
 * the env override, use a <em>dedicated disposable</em> instance — never a shared or production
 * server. The default container path is always safe because the container is throwaway.
 */
public final class TestServer {

    // Pinned digest (v4.20.1) so the suite is reproducible; matches the smoke-jdk8 CI job's image.
    private static final DockerImageName IMAGE = DockerImageName.parse(
            "falkordb/falkordb@sha256:9042fdc4e53f5390ca5a3993aa71506523970efb40ffb9a98e6a4b1a9a4f8862");

    private static final String host;
    private static final int port;
    private static final boolean external;

    static {
        String envHost = System.getenv("FALKORDB_HOST");
        String envPort = System.getenv("FALKORDB_PORT");
        boolean hasHost = envHost != null && !envHost.isEmpty();
        boolean hasPort = envPort != null && !envPort.isEmpty();
        if (hasHost ^ hasPort) {
            throw new IllegalStateException(
                    "Set BOTH FALKORDB_HOST and FALKORDB_PORT to use an external FalkorDB, or neither.");
        }
        if (hasHost) {
            external = true;
            host = envHost;
            port = Integer.parseInt(envPort.trim());
        } else {
            external = false;
            GenericContainer<?> container =
                    new GenericContainer<>(IMAGE).withExposedPorts(6379).waitingFor(Wait.forListeningPort());
            container.start(); // shared for the whole JVM; Testcontainers' Ryuk stops it at exit
            host = container.getHost();
            port = container.getMappedPort(6379);
        }
    }

    private TestServer() {}

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

    /** A graph context on the shared test server; the caller is responsible for closing it. */
    public static GraphContextGenerator graph(String name) {
        return FalkorDB.driver(host, port).graph(name);
    }
}
