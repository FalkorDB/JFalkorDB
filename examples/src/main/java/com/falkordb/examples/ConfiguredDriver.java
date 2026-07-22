package com.falkordb.examples;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import java.time.Duration;

/**
 * Shows the full {@link FalkorDB#builder()} configuration surface — credentials, TLS, connection-pool
 * sizing, and timeouts — for tuning a driver to a real deployment.
 *
 * <p>This example only <em>builds</em> and closes the driver (the pool connects lazily on first use),
 * so it needs no running server; it exists to demonstrate the configuration options rather than to
 * run a query. Swap in your own endpoint and then use {@code driver.graph("...").query("...")} as in
 * {@link QuickStart}.
 */
public final class ConfiguredDriver {

    private ConfiguredDriver() {}

    public static void main(String[] args) throws Exception {
        try (Driver driver = FalkorDB.builder()
                .host("db.example.com")
                .port(6380)
                .credentials("default", "s3cret") // or .credentials("s3cret") for password-only auth
                .ssl(true) // connect over TLS
                .poolMaxTotal(64) // maximum pooled connections (a good ceiling for high fan-out)
                .poolMaxIdle(16) // idle connections kept ready
                .poolMaxWait(Duration.ofSeconds(30)) // fail fast if the pool stays exhausted for 30s
                .connectionTimeout(Duration.ofSeconds(2)) // time to establish a TCP/TLS connection
                .socketTimeout(Duration.ZERO) // no read deadline — the server governs query duration
                .build()) {
            System.out.println("Configured driver built: " + driver.getClass().getSimpleName());
        }
    }
}
