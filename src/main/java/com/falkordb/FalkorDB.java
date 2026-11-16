package com.falkordb;

import com.falkordb.impl.api.ClusterOptions;
import com.falkordb.impl.api.DriverImpl;
import com.falkordb.impl.api.PoolOptions;
import com.falkordb.impl.api.SentinelOptions;
import com.falkordb.impl.api.TlsOptions;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
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
     * Builder#autoDetectSentinel(boolean)} — and for Redis Cluster — see {@link
     * Builder#autoDetectCluster(boolean)}.
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
     * <p>{@code null} means "none" for either credential: a {@code null} {@code user} authenticates with the
     * password only (as the {@code default} user), and a {@code null} {@code password} sends no credentials at
     * all, so a {@code user} given without a password is ignored.
     *
     * @param host     host name
     * @param port     port number
     * @param user     username, or {@code null} for password-only authentication
     * @param password password, or {@code null} for no authentication
     * @return a new driver instance
     */
    public static Driver driver(String host, int port, @Nullable String user, final @Nullable String password) {
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
     * {@code driver(...)} factories that also exposes TLS, connection-pool sizing and connection checks,
     * timeouts, and the database index.
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
     *     .poolMinIdle(4)
     *     .poolTestWhileIdle(true)
     *     .connectionTimeout(Duration.ofSeconds(2))
     *     .build();
     * }</pre>
     *
     * <p>It covers what a hand-built Jedis pool passed to the deprecated {@code DriverImpl(Pool)} was used
     * for, without naming a Jedis type. To connect from a URI, keep using {@link #driver(URI)}.
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
        private @Nullable Boolean ssl;
        private @Nullable SSLContext sslContext;
        private @Nullable SSLParameters sslParameters;
        private @Nullable HostnameVerifier sslHostnameVerifier;
        private int database;
        private @Nullable Duration connectionTimeout;
        private @Nullable Duration socketTimeout;
        private @Nullable Integer poolMaxTotal;
        private @Nullable Integer poolMaxIdle;
        private @Nullable Duration poolMaxWait;
        private int poolMinIdle;
        private boolean poolTestOnBorrow;
        private boolean poolTestWhileIdle;
        private @Nullable String sentinelMasterName;
        private @Nullable List<String> sentinelAddresses;
        private @Nullable String sentinelUser;
        private @Nullable String sentinelPassword;
        private boolean autoDetectSentinel = true;
        private @Nullable List<String> clusterNodes;
        private boolean autoDetectCluster = true;

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
         * Selects the database (default {@code 0}), as the path of a {@code redis://host:port/2} URI does.
         *
         * <p>A Redis Cluster has a single database, so a non-zero index is rejected by {@link #build()} with
         * {@link #cluster(String...)}, and when the first connection finds the endpoint is a cluster. The
         * connections to Sentinels never select one; only the master's do.
         *
         * @param database database index; must not be negative
         * @return this builder
         */
        public Builder database(int database) {
            this.database = database;
            return this;
        }

        /**
         * Enables or disables TLS. Unset, TLS is on when {@link #sslContext}, {@link #sslParameters} or
         * {@link #sslHostnameVerifier} is set, and off otherwise.
         *
         * <p>{@code ssl(true)} alone uses the JVM's default trust store and verifies the server's hostname.
         * {@code ssl(false)} together with one of the settings above is a contradiction, which {@link
         * #build()} rejects.
         *
         * @param ssl whether to connect over TLS
         * @return this builder
         */
        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        /**
         * Connects over TLS with sockets from this {@link SSLContext}, which carries the trust material that
         * authenticates the server and, for mutual TLS, the key material that authenticates the client.
         * Setting it turns TLS on. Unset, the JVM default applies.
         *
         * <pre>{@code
         * KeyStore trustStore = KeyStore.getInstance("PKCS12");
         * try (InputStream in = Files.newInputStream(Paths.get("truststore.p12"))) {
         *     trustStore.load(in, trustStorePassword);
         * }
         * TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         * tmf.init(trustStore);
         * SSLContext context = SSLContext.getInstance("TLS");
         * context.init(null, tmf.getTrustManagers(), null);
         *
         * Driver driver = FalkorDB.builder().host("db.example.com").port(6380).sslContext(context).build();
         * }</pre>
         *
         * <p>The same TLS settings are used for every connection the driver opens: to the server, to each
         * Redis Cluster node, to the Sentinels and to the master they name. Cluster nodes and Sentinel masters
         * are usually reported by IP address, so for hostname verification to pass their certificates need IP
         * subject alternative names.
         *
         * @param sslContext the context to create TLS sockets from, or {@code null} to clear it
         * @return this builder
         */
        public Builder sslContext(@Nullable SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Applies these {@link SSLParameters} to every TLS socket: protocols, cipher suites, SNI server names
         * and the endpoint identification algorithm, which decides whether the server's hostname is verified.
         * Setting them turns TLS on. Unset, the defaults apply, which verify the hostname.
         *
         * <p>They are used as given, and {@code new SSLParameters()} has no endpoint identification algorithm,
         * which would mean no hostname verification. {@link #build()} therefore rejects parameters without one
         * unless an {@link #sslHostnameVerifier} does the checking: call {@code
         * setEndpointIdentificationAlgorithm("HTTPS")} to verify the hostname, or {@code
         * setEndpointIdentificationAlgorithm("")} to skip that check deliberately.
         *
         * <pre>{@code
         * SSLParameters parameters = new SSLParameters();
         * parameters.setProtocols(new String[] {"TLSv1.3"});
         * parameters.setEndpointIdentificationAlgorithm("HTTPS");
         * Driver driver = FalkorDB.builder().host("db.example.com").sslParameters(parameters).build();
         * }</pre>
         *
         * <p>The object is read again each time a connection opens, so don't change it after {@link #build()}.
         *
         * @param sslParameters the parameters for each TLS socket, or {@code null} to clear them
         * @return this builder
         */
        public Builder sslParameters(@Nullable SSLParameters sslParameters) {
            this.sslParameters = sslParameters;
            return this;
        }

        /**
         * Checks the server's identity with this {@link HostnameVerifier} once each TLS handshake has
         * completed, and refuses the connection if it returns {@code false}. Setting it turns TLS on.
         *
         * <p>It runs in addition to the endpoint identification of the {@linkplain #sslParameters
         * parameters}, not instead of it. For a verifier more lenient than the standard {@code "HTTPS"} check
         * to take effect, set the parameters' endpoint identification algorithm to {@code ""} as well.
         *
         * @param sslHostnameVerifier the extra check, or {@code null} to clear it
         * @return this builder
         */
        public Builder sslHostnameVerifier(@Nullable HostnameVerifier sslHostnameVerifier) {
            this.sslHostnameVerifier = sslHostnameVerifier;
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
         * Sets how many idle connections the pool keeps open, ready for use (default {@code 0}).
         *
         * <p>A background task tops the pool up every 30 seconds, starting 30 seconds after the driver's
         * first use, since the pool is created then; building the driver still opens no connection. With a
         * Redis Cluster it applies to each node's pool separately.
         *
         * @param poolMinIdle idle connections to keep; must be non-negative and no greater than the pool's
         *                    {@code maxIdle}
         * @return this builder
         */
        public Builder poolMinIdle(int poolMinIdle) {
            this.poolMinIdle = poolMinIdle;
            return this;
        }

        /**
         * Checks each connection with a {@code PING} before handing it out, and replaces one that fails
         * (default {@code false}).
         *
         * <p>It catches a connection the server or the network closed while it sat in the pool before a query
         * is sent on it, at the cost of one extra round-trip every time a connection is borrowed, which is once
         * per graph call. {@link #poolTestWhileIdle(boolean)} catches most of the same connections in the
         * background instead.
         *
         * <p>The {@code PING} waits at most the {@linkplain #socketTimeout(Duration) socket timeout}, or 2
         * seconds when none is set.
         *
         * @param poolTestOnBorrow whether to check a connection before each use
         * @return this builder
         */
        public Builder poolTestOnBorrow(boolean poolTestOnBorrow) {
            this.poolTestOnBorrow = poolTestOnBorrow;
            return this;
        }

        /**
         * Checks idle connections with a {@code PING} in the background every 30 seconds, and closes the ones
         * that fail (default {@code false}), so that a connection the server or the network dropped while idle,
         * for example after the server's idle {@code timeout} or a load balancer's, is not handed to a query.
         *
         * <p>The {@code PING} waits at most the {@linkplain #socketTimeout(Duration) socket timeout}, or 2
         * seconds when none is set. Idle connections are never closed merely for having been idle; {@link
         * #poolMaxIdle(int)} bounds how many are kept. The first check runs 30 seconds after the driver's first
         * use. With a Redis Cluster it applies to each node's pool separately.
         *
         * @param poolTestWhileIdle whether to check idle connections in the background
         * @return this builder
         */
        public Builder poolTestWhileIdle(boolean poolTestWhileIdle) {
            this.poolTestWhileIdle = poolTestWhileIdle;
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
         * <p>It limits how long each read of a reply waits for data, including the reply to a query sent with
         * a timeout, such as {@link Graph#query(String, long)}: that timeout limits how long the server runs
         * the query, not how long the client waits. The server sends nothing until a query has finished, so a
         * socket timeout shorter than the query's ends the wait first, though the server may still run the
         * query to the end.
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
         * <p>When enabled, the driver probes the configured host/port once with {@code INFO server}
         * the first time it is used; if that endpoint is a Sentinel, the driver resolves the single
         * master it monitors and connects to that instead, following failovers from then on. This
         * mirrors falkordb-py, falkordb-go and falkordb-ts, so the same address works across FalkorDB
         * clients. Like every other connection, the probe happens on first use rather than in
         * {@link #build()}, which performs no I/O.
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
         * Enables or disables Redis Cluster auto-detection (default {@code true}).
         *
         * <p>When enabled, the driver probes the configured host/port once with {@code INFO server}
         * the first time it is used; if that endpoint reports {@code redis_mode:cluster} (or {@code
         * server_mode:cluster}, as Valkey does), the driver learns the cluster's topology from it and
         * sends every graph command to the node that owns the graph, so the same address works for a
         * standalone server and a cluster. This mirrors
         * falkordb-py. Like every other connection, the probe happens on first use rather than in
         * {@link #build()}, which performs no I/O.
         *
         * <p>In cluster mode:
         *
         * <ul>
         *   <li>a graph lives on a single node, chosen by hashing its name, so queries, pipelines and
         *       transactions on one graph are served by that node. Copying a graph needs both names
         *       in the same hash slot, which a hash tag arranges ({@code {social}-a} and
         *       {@code {social}-b}); otherwise the server answers {@code CROSSSLOT};
         *   <li>{@link Driver#listGraphs()} returns the graphs of every primary, and the server-wide
         *       commands ({@code udfLoad}, {@code udfDelete}, {@code udfFlush}, {@code configSet}) run
         *       on every primary;
         *   <li>pool sizing applies to each node separately, and the connection settings are used for
         *       every node, so all nodes must accept the same credentials and TLS setting;
         *   <li>a cluster has a single database, so a database index, in a URI or set with {@link
         *       #database(int)}, is not supported;
         *   <li>when the cluster reshards or fails over, the driver follows the redirect and retries
         *       the command, up to three attempts.
         * </ul>
         *
         * <p>The probe is best-effort and costs one round-trip: an endpoint that cannot be reached, or
         * that refuses {@code INFO}, simply yields an ordinary direct connection. Switch this off to
         * skip the probe altogether. It is ignored when {@link #cluster(String...)} named the nodes
         * explicitly.
         *
         * @param autoDetectCluster whether to probe the endpoint for cluster mode
         * @return this builder
         */
        public Builder autoDetectCluster(boolean autoDetectCluster) {
            this.autoDetectCluster = autoDetectCluster;
            return this;
        }

        /**
         * Connects to a Redis Cluster through the given startup nodes, without probing.
         *
         * <p>Use this when {@linkplain #autoDetectCluster(boolean) auto-detection} is not enough: it
         * lists several nodes rather than a single seed, so the driver can still learn the topology
         * when one of them is down. Only some of the nodes need to be reachable. The host and port set
         * on this builder are ignored. See {@link #autoDetectCluster(boolean)} for how a driver behaves
         * in cluster mode.
         *
         * <pre>{@code
         * Driver driver = FalkorDB.builder()
         *     .cluster("node-a:6379", "node-b:6379", "node-c:6379")
         *     .build();
         * }</pre>
         *
         * @param startupNodes the cluster nodes in {@code host:port} form; at least one is required
         * @return this builder
         */
        public Builder cluster(String... startupNodes) {
            return cluster(startupNodes == null ? null : Arrays.asList(startupNodes));
        }

        /**
         * Connects to a Redis Cluster through the given startup nodes, without probing. The {@linkplain
         * #cluster(String...) varargs form} documents the behaviour.
         *
         * @param startupNodes the cluster nodes in {@code host:port} form; at least one is required
         * @return this builder
         */
        public Builder cluster(Collection<String> startupNodes) {
            // A null or empty list is still an explicit (and invalid) choice: build() rejects it rather than
            // silently falling back to auto-detection.
            this.clusterNodes = startupNodes == null ? new ArrayList<String>() : new ArrayList<>(startupNodes);
            return this;
        }

        /**
         * Validates the configuration and builds a driver. Unset options use the fixed defaults
         * documented on each setter, independently of the environment {@link FalkorDB#driver()} reads.
         * Range validation happens in {@link DriverImpl#create}.
         *
         * @return a new driver
         * @throws IllegalArgumentException if any option is out of range or the options contradict each other
         *     (see the individual setters)
         */
        public Driver build() {
            TlsOptions tls = TlsOptions.of(sslContext, sslParameters, sslHostnameVerifier);
            if (tls != null && Boolean.FALSE.equals(ssl)) {
                throw new IllegalArgumentException(
                        "ssl(false) contradicts sslContext, sslParameters or sslHostnameVerifier, which need TLS");
            }
            boolean useTls = ssl == null ? tls != null : ssl;
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
                    database,
                    useTls,
                    tls,
                    connectMillis,
                    socketMillis,
                    PoolOptions.of(maxTotal, maxIdle, poolMinIdle, maxWait, poolTestOnBorrow, poolTestWhileIdle),
                    sentinelOptions(),
                    clusterOptions());
        }

        /**
         * Resolves the three cluster states this builder can express: explicitly named startup nodes,
         * the default probe, or no cluster handling at all.
         */
        private ClusterOptions clusterOptions() {
            if (clusterNodes != null) {
                return ClusterOptions.explicit(clusterNodes);
            }
            return autoDetectCluster ? ClusterOptions.autoDetect() : ClusterOptions.disabled();
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
