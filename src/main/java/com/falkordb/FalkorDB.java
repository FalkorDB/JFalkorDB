package com.falkordb;

import com.falkordb.impl.api.DriverImpl;
import java.net.URI;
import java.time.Duration;

/**
 * FalkorDB driver factory
 */
public final class FalkorDB {

    private FalkorDB() {}

    /**
     * Creates a new driver instance
     *
     * @return a new driver instance
     */
    public static Driver driver() {
        return driver("localhost", 6379);
    }

    /**
     * Creates a new driver instance
     *
     * @param host host name
     * @param port port number
     * @return a new driver instance
     */
    public static Driver driver(String host, int port) {
        return new DriverImpl(host, port);
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
        return new DriverImpl(host, port, user, password);
    }

    /**
     * Creates a new driver instance
     *
     * @param uri server uri
     * @return a new driver instance
     */
    public static Driver driver(URI uri) {
        return new DriverImpl(uri);
    }

    /**
     * Starts building a driver with a fluent, discoverable configuration API — a superset of the
     * {@code driver(...)} factories that also exposes TLS, connection-pool sizing, and timeouts.
     *
     * <p>With no options set, {@link Builder#build()} produces a driver identical to {@link #driver()}
     * (host {@code localhost}, port {@code 6379}, no credentials, no TLS, Jedis' default 2000&nbsp;ms
     * connect timeout, no socket read deadline, and the default connection pool). For example:
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
     * <p>All options are optional; unset options fall back to the same defaults as {@link
     * FalkorDB#driver()}. Instances are not thread-safe and are intended to be configured and
     * {@linkplain #build() built} on a single thread. Validation happens in {@link #build()}.
     */
    public static final class Builder {

        private String host = "localhost";
        private int port = 6379;
        private String user;
        private String password;
        private boolean ssl;
        private Duration connectionTimeout;
        private Duration socketTimeout;
        private Integer poolMaxTotal;
        private Integer poolMaxIdle;
        private Duration poolMaxWait;

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
         * Validates the configuration and builds a driver. Unset options use the same defaults as
         * {@link FalkorDB#driver()}. Range validation happens in {@link DriverImpl#create}.
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
                    host, port, user, password, ssl, connectMillis, socketMillis, maxTotal, maxIdle, maxWait);
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
