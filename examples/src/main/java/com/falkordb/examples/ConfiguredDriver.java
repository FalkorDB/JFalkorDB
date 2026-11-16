package com.falkordb.examples;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Shows the full {@link FalkorDB#builder()} configuration surface — credentials, the database, TLS,
 * connection-pool sizing and connection checks, and timeouts — for tuning a driver to a real deployment.
 * It replaces building a Jedis pool by hand and passing it to the deprecated {@code DriverImpl(Pool)}.
 *
 * <p>This example only <em>builds</em> and closes the driver (the pool connects lazily on first use),
 * so it needs no running server; it exists to demonstrate the configuration options rather than to
 * run a query. Swap in your own endpoint and then use {@code driver.graph("...").query("...")} as in
 * {@link QuickStart}.
 */
public final class ConfiguredDriver {

    private ConfiguredDriver() {}

    public static void main(String[] args) throws NoSuchAlgorithmException {
        // TLS with protocols restricted; the parameters must say whether to verify the hostname ("HTTPS").
        SSLParameters tls = new SSLParameters();
        tls.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        tls.setEndpointIdentificationAlgorithm("HTTPS");
        try (Driver driver = FalkorDB.builder()
                .host("db.example.com")
                .port(6380)
                .credentials("default", "s3cret") // or .credentials("s3cret") for password-only auth
                .database(0) // the database index, as in a redis://host:port/0 URI
                .sslContext(SSLContext.getDefault()) // connect over TLS; use your own context for a private CA
                .sslParameters(tls)
                .poolMaxTotal(64) // maximum pooled connections (a good ceiling for high fan-out)
                .poolMaxIdle(16) // idle connections kept
                .poolMinIdle(4) // idle connections kept open and ready
                .poolTestWhileIdle(true) // PING idle connections every 30 s and replace dead ones
                .poolMaxWait(Duration.ofSeconds(30)) // fail fast if the pool stays exhausted for 30s
                .connectionTimeout(Duration.ofSeconds(2)) // time to establish a TCP/TLS connection
                .socketTimeout(Duration.ZERO) // no read deadline — the server governs query duration
                .build()) {
            System.out.println("Configured driver built: " + driver.getClass().getSimpleName());
        }
    }
}
