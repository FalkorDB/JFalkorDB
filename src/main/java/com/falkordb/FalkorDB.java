package com.falkordb;

import com.falkordb.impl.api.DriverImpl;
import com.falkordb.impl.api.SentinelOptions;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * FalkorDB driver factory
 */
public final class FalkorDB {

    private FalkorDB() {}

    /**
     * Creates a new driver instance, falling back to environment configuration when {@code
     * localhost:6379} isn't what you want. Resolved in this order:
     *
     * <ol>
     *   <li>{@code FALKORDB_SENTINEL_MASTER} together with {@code FALKORDB_SENTINELS} (a
     *       comma-separated list of {@code host:port} Sentinel addresses) — connects through the named
     *       Redis Sentinel deployment. Setting only one of the pair is rejected rather than silently
     *       ignored. Credentials are not configurable this way; use {@link #builder()} if you need
     *       them.
     *   <li>{@code FALKORDB_URL} — a full connection URI ({@code redis://} or {@code rediss://}, or
     *       the FalkorDB-branded {@code falkor://}/{@code falkors://} aliases for them); delegates to
     *       {@link #driver(URI)}, so it also carries credentials, a database index, and TLS.
     *   <li>{@code FALKORDB_HOST} together with {@code FALKORDB_PORT} — the convention already used
     *       by this project's own tests; delegates to {@link #driver(String, int)}. Setting only one
     *       of the pair is rejected rather than silently defaulting the other.
     *   <li>Neither set — the unchanged {@code localhost:6379} default.
     * </ol>
     *
     * <p>This environment fallback applies <strong>only</strong> to this no-arg overload: {@link
     * #driver(String, int)}, {@link #driver(String, int, String, String)} and {@link #driver(URI)}
     * always connect to exactly the arguments you pass them, and {@link #builder()} likewise ignores
     * the environment entirely — an unconfigured {@link Builder} still defaults to {@code
     * localhost:6379}, so {@code builder().build()} and {@code driver()} agree only when none of the
     * variables above is set. Set the host and port on the builder explicitly if you need it to
     * follow the environment.
     *
     * <p>Whichever endpoint is resolved, it is checked for Redis Sentinel — see {@link
     * Builder#autoDetectSentinel(boolean)}.
     *
     * @return a new driver instance
     * @throws IllegalStateException if {@code FALKORDB_URL} is set but malformed, if {@code
     *     FALKORDB_PORT} is set but not a valid integer, if exactly one of {@code
     *     FALKORDB_HOST}/{@code FALKORDB_PORT} is set, or if exactly one of {@code
     *     FALKORDB_SENTINEL_MASTER}/{@code FALKORDB_SENTINELS} is set
     */
    public static Driver driver() {
        return DriverEnvironment.resolve(System::getenv);
    }

    /**
     * Creates a new driver instance.
     *
     * <p>If {@code host}/{@code port} turns out to be a Redis Sentinel, the driver transparently
     * connects to the master it monitors instead, and follows failovers from then on — see {@link
     * Builder#autoDetectSentinel(boolean)}.
     *
     * @param host host name
     * @param port port number
     * @return a new driver instance
     */
    public static Driver driver(String host, int port) {
        return DriverImpl.connect(host, port, null, null, SentinelOptions.autoDetect());
    }

    /**
     * Creates a new driver instance
     *
     * @param host     host name
     * @param port     port number
     * @param user     username
     * @param password password
     * @return a new driver instance
     */
    public static Driver driver(String host, int port, String user, final String password) {
        return DriverImpl.connect(host, port, user, password, SentinelOptions.autoDetect());
    }

    /**
     * Creates a new driver instance
     *
     * @param uri server uri
     * @return a new driver instance
     */
    public static Driver driver(URI uri) {
        return DriverImpl.connect(uri, SentinelOptions.autoDetect());
    }

    /**
     * Starts building a driver with a fluent, discoverable configuration API — a superset of the
     * {@code driver(...)} factories that also exposes TLS, connection-pool sizing, and timeouts.
     *
     * <p>With no options set, {@link Builder#build()} produces a driver pointing at host {@code
     * localhost}, port {@code 6379}, with no credentials, no TLS, Jedis' default 2000&nbsp;ms connect
     * timeout, no socket read deadline, and the default connection pool. Unlike {@link #driver()},
     * the builder never consults {@code FALKORDB_URL}/{@code FALKORDB_HOST}/{@code FALKORDB_PORT} —
     * these defaults are fixed. For example:
     *
     * <pre>{@code
     * Driver driver = FalkorDB.builder()
     *     .host("db.example.com")
     *     .port(6380)
     *     .credentials("user", "password")
     *     .ssl(true)
     *     .poolMaxTotal(64)
     *     .connectionTimeout(Duration.ofSeconds(2))
     *     .build();
     * }</pre>
     *
     * <p>To connect from a URI, keep using {@link #driver(URI)}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for a {@link Driver}, created via {@link FalkorDB#builder()}.
     *
     * <p>All options are optional; unset options fall back to the fixed defaults documented on each
     * setter ({@code localhost:6379}, no credentials, no TLS), never to the environment {@link
     * FalkorDB#driver()} consults. Instances are not thread-safe and are intended to be configured
     * and {@linkplain #build() built} on a single thread. Validation happens in {@link #build()}.
     */
    public static final class Builder {

        private String host = "localhost";
        private int port = 6379;
        private @Nullable String user;
        private @Nullable String password;
        private boolean ssl;
        private @Nullable Duration connectionTimeout;
        private @Nullable Duration socketTimeout;
        private @Nullable Integer poolMaxTotal;
        private @Nullable Integer poolMaxIdle;
        private @Nullable Duration poolMaxWait;
        private @Nullable String sentinelMasterName;
        private @Nullable List<String> sentinelAddresses;
        private @Nullable String sentinelUser;
        private @Nullable String sentinelPassword;
        private boolean autoDetectSentinel = true;

        private Builder() {}

        /**
         * Sets the server host (default {@code "localhost"}).
         *
         * @param host server host; must not be {@code null} or blank
         * @return this builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the server port (default {@code 6379}).
         *
         * @param port server port; must be in {@code [1, 65535]}
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the username and password used to authenticate.
         *
         * @param user     username
         * @param password password
         * @return this builder
         */
        public Builder credentials(String user, String password) {
            this.user = user;
            this.password = password;
            return this;
        }

        /**
         * Sets a password for password-only ({@code default} user) authentication, clearing any
         * previously set username.
         *
         * @param password password
         * @return this builder
         */
        public Builder credentials(String password) {
            this.user = null;
            this.password = password;
            return this;
        }

        /**
         * Enables or disables TLS (default {@code false}).
         *
         * @param ssl whether to connect over TLS
         * @return this builder
         */
        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        /**
         * Sets the maximum connection-pool size (default {@code 8}).
         *
         * @param poolMaxTotal maximum number of connections; must be at least {@code 1}
         * @return this builder
         */
        public Builder poolMaxTotal(int poolMaxTotal) {
            this.poolMaxTotal = poolMaxTotal;
            return this;
        }

        /**
         * Sets the maximum number of idle connections kept in the pool (default {@code 8}).
         *
         * @param poolMaxIdle maximum idle connections; must be non-negative and no greater than the
         *                    pool's {@code maxTotal}
         * @return this builder
         */
        public Builder poolMaxIdle(int poolMaxIdle) {
            this.poolMaxIdle = poolMaxIdle;
            return this;
        }

        /**
         * Sets the maximum time to wait for a connection when the pool is exhausted (default: wait
         * indefinitely). A negative duration waits indefinitely; {@link Duration#ZERO} fails fast.
         *
         * @param poolMaxWait maximum borrow wait
         * @return this builder
         */
        public Builder poolMaxWait(Duration poolMaxWait) {
            this.poolMaxWait = poolMaxWait;
            return this;
        }

        /**
         * Sets the connection (connect) timeout (default 2000&nbsp;ms). {@link Duration#ZERO} means an
         * infinite connect wait, which is allowed but not recommended.
         *
         * @param connectionTimeout connect timeout; must not be negative
         * @return this builder
         */
        public Builder connectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        /**
         * Sets the socket (read) timeout (default {@link Duration#ZERO} = no read deadline, so a
         * long-running server query is never cut off client-side).
         *
         * @param socketTimeout read timeout; must not be negative
         * @return this builder
         */
        public Builder socketTimeout(Duration socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        /**
         * Connects through a Redis Sentinel deployment, naming the monitored master explicitly.
         *
         * <p>Use this when {@linkplain #autoDetectSentinel(boolean) auto-detection} is not enough: it
         * lists every Sentinel rather than a single seed, so the driver can still find the master when
         * one Sentinel is down, and it names the master, so it works with a Sentinel monitoring more
         * than one. Setting it makes {@link #host(String)} and {@link #port(int)} irrelevant — the
         * master's address comes from the Sentinels — and suppresses probing entirely.
         *
         * <pre>{@code
         * Driver driver = FalkorDB.builder()
         *     .sentinel("mymaster", "sentinel-a:26379", "sentinel-b:26379", "sentinel-c:26379")
         *     .credentials("user", "password")
         *     .build();
         * }</pre>
         *
         * @param masterName name of the monitored master, as given to {@code sentinel monitor}
         * @param sentinels  the Sentinel endpoints in {@code host:port} form; at least one is required
         * @return this builder
         */
        public Builder sentinel(String masterName, String... sentinels) {
            return sentinel(masterName, sentinels == null ? null : Arrays.asList(sentinels));
        }

        /**
         * Connects through a Redis Sentinel deployment, naming the monitored master explicitly. The
         * {@linkplain #sentinel(String, String...) varargs form} documents the behaviour.
         *
         * @param masterName name of the monitored master, as given to {@code sentinel monitor}
         * @param sentinels  the Sentinel endpoints in {@code host:port} form; at least one is required
         * @return this builder
         */
        public Builder sentinel(String masterName, Collection<String> sentinels) {
            this.sentinelMasterName = masterName;
            this.sentinelAddresses = sentinels == null ? null : new ArrayList<>(sentinels);
            return this;
        }

        /**
         * Sets the username and password used to authenticate to the Sentinels themselves, which
         * commonly carry ACLs of their own. Unset, the {@linkplain #credentials(String, String) data
         * credentials} are reused.
         *
         * @param user     Sentinel username
         * @param password Sentinel password
         * @return this builder
         */
        public Builder sentinelCredentials(String user, String password) {
            this.sentinelUser = user;
            this.sentinelPassword = password;
            return this;
        }

        /**
         * Sets a password for password-only ({@code default} user) authentication to the Sentinels,
         * clearing any previously set Sentinel username.
         *
         * @param password Sentinel password
         * @return this builder
         */
        public Builder sentinelCredentials(String password) {
            this.sentinelUser = null;
            this.sentinelPassword = password;
            return this;
        }

        /**
         * Enables or disables Sentinel auto-detection (default {@code true}).
         *
         * <p>When enabled, {@link #build()} probes the configured host/port once with {@code INFO
         * server}; if that endpoint is a Sentinel, the driver resolves the single master it monitors
         * and connects to that instead, following failovers from then on. This mirrors falkordb-py,
         * falkordb-go and falkordb-ts, so the same address works across FalkorDB clients.
         *
         * <p>The probe is best-effort and costs one round-trip: an endpoint that cannot be reached, or
         * that refuses {@code INFO}, simply yields an ordinary direct connection whose failure surfaces
         * on first use — the behaviour of every release before Sentinel support. Switch this off to
         * skip the probe altogether when you know you are not talking to a Sentinel. It is ignored when
         * {@link #sentinel(String, String...)} named a deployment explicitly.
         *
         * @param autoDetectSentinel whether to probe the endpoint for Sentinel mode
         * @return this builder
         */
        public Builder autoDetectSentinel(boolean autoDetectSentinel) {
            this.autoDetectSentinel = autoDetectSentinel;
            return this;
        }

        /**
         * Validates the configuration and builds a driver. Unset options use the fixed defaults
         * documented on each setter, independently of the environment {@link FalkorDB#driver()} reads.
         * Range validation happens in {@link DriverImpl#create}.
         *
         * @return a new driver
         * @throws IllegalArgumentException if any option is out of range (see the individual setters)
         */
        public Driver build() {
            int maxTotal = poolMaxTotal == null ? DriverImpl.DEFAULT_POOL_MAX_TOTAL : poolMaxTotal;
            int maxIdle = poolMaxIdle == null ? DriverImpl.DEFAULT_POOL_MAX_IDLE : poolMaxIdle;
            Duration maxWait = poolMaxWait == null ? DriverImpl.DEFAULT_POOL_MAX_WAIT : poolMaxWait;
            int connectMillis = connectionTimeout == null
                    ? DriverImpl.DEFAULT_CONNECTION_TIMEOUT_MILLIS
                    : toTimeoutMillis(connectionTimeout, "connectionTimeout");
            int socketMillis = socketTimeout == null
                    ? DriverImpl.DEFAULT_SOCKET_TIMEOUT_MILLIS
                    : toTimeoutMillis(socketTimeout, "socketTimeout");
            return DriverImpl.create(
                    host,
                    port,
                    user,
                    password,
                    ssl,
                    connectMillis,
                    socketMillis,
                    maxTotal,
                    maxIdle,
                    maxWait,
                    sentinelOptions());
        }

        /**
         * Resolves the three Sentinel states this builder can express: an explicitly named deployment,
         * the default probe, or no Sentinel handling at all. Sentinel credentials apply to all three,
         * since even an auto-detected Sentinel may need its own ACL.
         */
        private SentinelOptions sentinelOptions() {
            SentinelOptions options;
            if (sentinelMasterName != null || sentinelAddresses != null) {
                options = SentinelOptions.explicit(sentinelMasterName, sentinelAddresses);
            } else if (autoDetectSentinel) {
                options = SentinelOptions.autoDetect();
            } else {
                options = SentinelOptions.disabled();
            }
            if (sentinelUser != null || sentinelPassword != null) {
                options = options.withCredentials(sentinelUser, sentinelPassword);
            }
            return options;
        }

        /**
         * Converts a Jedis timeout {@link Duration} to non-negative {@code int} milliseconds: rejects
         * negative values and values above {@link Integer#MAX_VALUE} ms, and rounds a positive
         * sub-millisecond duration up to {@code 1} ms so it never collapses to {@code 0} ("infinite").
         * A {@code null} duration never reaches here — {@link #build()} maps it to the default instead.
         */
        private static int toTimeoutMillis(Duration duration, String name) {
            if (duration.isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative, but was " + duration);
            }
            // Guard the seconds component before Duration.toMillis(), which itself throws
            // ArithmeticException (not IllegalArgumentException) once the value overflows a long.
            if (duration.getSeconds() > Integer.MAX_VALUE / 1000L) {
                throw new IllegalArgumentException(
                        name + " must not exceed " + Integer.MAX_VALUE + " ms, but was " + duration);
            }
            long millis = duration.toMillis();
            if (millis > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        name + " must not exceed " + Integer.MAX_VALUE + " ms, but was " + millis + " ms");
            }
            if (millis == 0 && !duration.isZero()) {
                return 1;
            }
            return (int) millis;
        }
    }
}
