[![license](https://img.shields.io/github/license/FalkorDB/JFalkorDB.svg)](https://github.com/FalkorDB/JFalkorDB/blob/master/LICENSE)
[![Release](https://img.shields.io/github/release/FalkorDB/JFalkorDB.svg)](https://github.com/FalkorDB/JFalkorDB/releases/latest)
[![Maven Central Version](https://img.shields.io/maven-central/v/com.falkordb/jfalkordb)](https://central.sonatype.com/artifact/com.falkordb/jfalkordb)
[![Javadocs](https://www.javadoc.io/badge/com.falkordb/jfalkordb.svg)](https://www.javadoc.io/doc/com.falkordb/jfalkordb)
[![Codecov](https://codecov.io/gh/FalkorDB/JFalkorDB/branch/master/graph/badge.svg)](https://codecov.io/gh/FalkorDB/JFalkorDB)
[![Benchmarks](https://img.shields.io/badge/benchmarks-trends-blue)](https://falkordb.github.io/JFalkorDB/dev/bench/)
[![Known Vulnerabilities](https://snyk.io/test/github/FalkorDB/JFalkorDB/badge.svg?targetFile=pom.xml)](https://snyk.io/test/github/FalkorDB/JFalkorDB?targetFile=pom.xml)

[![Discord](https://img.shields.io/discord/1146782921294884966?style=flat-square)](https://discord.gg/6M4QwDXn2w)
[![Discuss the project](https://img.shields.io/badge/discussions-FalkorDB-brightgreen.svg)](https://github.com/FalkorDB/FalkorDB/discussions)

# JFalkorDB

[![Try Free](https://img.shields.io/badge/Try%20Free-FalkorDB%20Cloud-FF8101?labelColor=FDE900&style=for-the-badge&link=https://app.falkordb.cloud)](https://app.falkordb.cloud)

FalkorDB Java client

## Official Releases

<!-- The version below is NOT auto-updated: release-please would rewrite it to a -SNAPSHOT (see
     release-please-config.json). ReadmeVersionTest keeps it honest against CHANGELOG.md instead. -->

```xml
  <dependencies>
    <dependency>
      <groupId>com.falkordb</groupId>
      <artifactId>jfalkordb</artifactId>
      <version>0.16.0</version>
    </dependency>
  </dependencies>
```

## Snapshots

```xml
  <repositories>
    <repository>
      <id>central-snapshots</id>
      <url>https://central.sonatype.com/repository/maven-snapshots/</url>
      <releases><enabled>false</enabled></releases>
      <snapshots><enabled>true</enabled></snapshots>
    </repository>
  </repositories>
```

and

```xml
  <dependencies>
    <dependency>
      <groupId>com.falkordb</groupId>
      <artifactId>jfalkordb</artifactId>
      <version>0.16.0</version> <!-- x-release-please-version -->
    </dependency>
  </dependencies>
```

## Example: Using the Java Client

```java
package com.falkordb;

import com.falkordb.graph_entities.Edge;
import com.falkordb.graph_entities.Node;
import com.falkordb.graph_entities.Path;
import com.falkordb.Graph;
import com.falkordb.GraphContext;
import com.falkordb.GraphContextGenerator;
import com.falkordb.GraphTransaction;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import com.falkordb.FalkorDB;
import com.falkordb.Driver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphExample {
    public static void main(String[] args) {

        // the driver owns the connection pool: try-with-resources closes it (no checked exception to catch)
        try (Driver driver = FalkorDB.driver()) {
            // general context api. Not bound to graph key or connection
            Graph graph = driver.graph("social");

            Map<String, Object> params = new HashMap<>();
            params.put("age", 30);
            params.put("name", "amit");

            // send queries to a specific graph called "social"
            graph.query("CREATE (:person{name:'roi',age:32})");
            graph.query("CREATE (:person{name:$name,age:$age})", params);
            graph.query("MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)");

            ResultSet resultSet = graph.query("MATCH (a:person)-[r:knows]->(b:person) RETURN a, r, b");

            // iterate over result set       
            for(Record record: resultSet) {
                // get values
                Node n = record.getValue("a", Node.class);
                Edge e = record.getValue("r", Edge.class);

                //print record
                System.out.println("Node: " + n + ", Edge: " + e);
            }

            resultSet = graph.query("MATCH p = (:person)-[:knows]->(:person) RETURN p");
            for(Record record: resultSet) {
                Path p = record.getValue("p", Path.class);

                // More path API at Javadoc.
                System.out.println(p.nodeCount());
            }

            // delete graph
            graph.deleteGraph();

            GraphContextGenerator contextGraph = driver.graph("contextSocial");
            // get connection context - closable object
            try(GraphContext context = contextGraph.getContext()) {
                context.query("CREATE (:person{name:'roi',age:32})");
                context.query("MATCH (a:person), (b:person) WHERE (a.name = 'roi' AND b.name='amit') CREATE (a)-[:knows]->(b)");
                // WATCH/MULTI/EXEC
                context.watch("contextSocial");

                GraphTransaction t = context.multi();
                t.query("MATCH (a:person)-[r:knows]->(b:person{name:$name,age:$age}) RETURN a, r, b", params);
                // support for Redis/Jedis native commands in transaction
                t.set("x", "1");
                t.get("x");
                // get multi/exec results
                List<Object> execResults =  t.exec();
                System.out.println(execResults.toString());

                context.deleteGraph();
            }
        }
    }
}
```

## Configuring the driver from the environment

`FalkorDB.driver()` — the no-arg factory used above — connects to `localhost:6379` by default, but
first checks for connection settings in the environment so the same artifact can be deployed across
dev/CI/staging/prod without hand-rolled env-var plumbing. Resolution order:

1. `FALKORDB_SENTINEL_MASTER` together with `FALKORDB_SENTINELS` — connect through a
   [Redis Sentinel](#high-availability-with-redis-sentinel) deployment. `FALKORDB_SENTINELS` is a
   comma-separated list of `host:port` Sentinel addresses. Setting only one of the pair throws
   `IllegalStateException`.
2. `FALKORDB_URL` — a full connection URI (`redis://`/`rediss://`, or the FalkorDB-branded
   `falkor://`/`falkors://` aliases for them), covering host, port, credentials, and TLS in one
   value; delegates to `FalkorDB.driver(URI)`.
3. `FALKORDB_HOST` together with `FALKORDB_PORT` — delegates to `FalkorDB.driver(host, port)`.
   Setting only one of the pair throws `IllegalStateException` rather than silently defaulting the
   other.
4. None set — the unchanged `localhost:6379` default.

```bash
export FALKORDB_URL=redis://<user>:<password>@db.example.com:6379
# or, equivalently:
export FALKORDB_HOST=db.example.com
export FALKORDB_PORT=6379
# or, to go through Sentinel:
export FALKORDB_SENTINEL_MASTER=mymaster
export FALKORDB_SENTINELS=sentinel-a:26379,sentinel-b:26379,sentinel-c:26379
```

```java
// picks up the environment settings above, or falls back to localhost:6379
Driver driver = FalkorDB.driver();
```

This fallback applies **only** to the no-arg `driver()` overload — `driver(host, port)`,
`driver(host, port, user, password)`, `driver(URI)`, and `builder()` always connect to exactly the
arguments you pass them and never consult the environment.

## High availability with Redis Sentinel

[Redis Sentinel](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/) monitors a
master and its replicas and promotes a replica when the master fails. Point the driver at a Sentinel
and it discovers the current master, then follows failovers for the lifetime of the driver — queries
issued after a promotion go to the new master without recreating the driver.

Nothing needs configuring in the common case. Every factory method probes the endpoint it is given,
and if that endpoint turns out to be a Sentinel it resolves the master automatically:

```java
// sentinel-a is a Sentinel, not a FalkorDB server — the driver works this out for itself
try (Driver driver = FalkorDB.driver("sentinel-a", 26379)) {
    Graph graph = driver.graph("social");
    graph.query("CREATE (:Person {name:'Alice'})");
}
```

This matches how the other FalkorDB clients behave, so the same deployment works whichever language
a service is written in. An endpoint that is an ordinary FalkorDB server is used directly, exactly as
in every previous release; an endpoint that cannot be probed at all also falls back to a direct
connection, so nothing that worked before starts failing.

Name the deployment explicitly when a Sentinel monitors more than one master (auto-detection cannot
guess which one you want and will tell you so), when you want to list several Sentinels for
redundancy, or simply to skip the probe:

```java
try (Driver driver = FalkorDB.builder()
        .sentinel("mymaster", "sentinel-a:26379", "sentinel-b:26379", "sentinel-c:26379")
        .build()) {
    driver.graph("social").query("CREATE (:Person {name:'Alice'})");
}
```

Credentials given with `credentials(...)` are used for the master. Sentinels frequently have their
own ACLs, so they can be authenticated separately; when `sentinelCredentials(...)` is omitted the
Sentinel connections reuse the master's credentials:

```java
Driver driver = FalkorDB.builder()
        .sentinel("mymaster", "sentinel-a:26379")
        .credentials("app-user", "app-password")
        .sentinelCredentials("sentinel-user", "sentinel-password")
        .build();
```

Auto-detection costs one `INFO` command on the first connection. To skip it — for instance when the
address is known to be a plain server and its ACL forbids `INFO` — turn it off:

```java
Driver driver = FalkorDB.builder().host("db.example.com").autoDetectSentinel(false).build();
```

Building a driver still performs no I/O: the probe, like the first connection, happens when the
driver is first used.

## Redis Cluster

A [Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/) spreads its
keys over several primaries. A graph is a single key, so each graph lives on one node, chosen by
hashing its name. Point the driver at any node of the cluster and it learns the layout, then sends
every command to the node that owns the graph, so no query is answered with a `MOVED` redirect.

Nothing needs configuring in the common case. Every factory method probes the endpoint it is given,
and if that endpoint is a cluster node (`redis_mode:cluster`) the driver switches to cluster mode on
its own, as the Python client does:

```java
// node-a is one node of a cluster; the driver finds the others
try (Driver driver = FalkorDB.driver("node-a", 6379)) {
    Graph graph = driver.graph("social");
    graph.query("CREATE (:Person {name:'Alice'})");
}
```

An endpoint that is an ordinary FalkorDB server is used directly, exactly as in every previous
release, and an endpoint that cannot be probed also falls back to a direct connection.

List several startup nodes when you want the driver to cope with one of them being down, or to skip
the probe. Only some of the nodes need to be reachable, because the whole layout is learned from
whichever one answers first:

```java
try (Driver driver = FalkorDB.builder()
        .cluster("node-a:6379", "node-b:6379", "node-c:6379")
        .credentials("app-user", "app-password")
        .build()) {
    driver.graph("social").query("CREATE (:Person {name:'Alice'})");
}
```

### What changes in cluster mode

- **Graph commands** (`query`, `readOnlyQuery`, `profile`, `explain`, `copyGraph`, `deleteGraph`,
  pipelines and transactions) go to the node that owns the graph. A pipeline or transaction stays on
  that node, so it can only touch that one graph. Plain Redis keys used inside one belong to other
  nodes unless they share the graph's hash tag.
- **`listGraphs()`** returns the graphs of every primary, without duplicates.
- **`udfLoad`, `udfDelete`, `udfFlush` and `configSet`** run on every primary, because libraries and
  configuration are per node. They return `true` only if every primary accepted the command, and an
  error from any primary is thrown.
- **`udfList`, `configGet` and `configGetAll`** are answered by any one primary. If it refuses the connection or drops it,
  the driver relearns the layout once and asks another primary. A primary that hangs without answering
  still holds the call up, unless a socket timeout is set.
- **`getConnection()`** returns a connection to an arbitrary primary. Use it for commands that do not
  concern a particular graph.
- **Copying a graph** needs both names in the same hash slot. A hash tag does that:
  `driver.graph("{social}-a").copyGraph("{social}-b")`. Otherwise the server answers `CROSSSLOT`.
- **Resharding and failover** are followed for single graph calls (`query`, `readOnlyQuery`,
  `profile`, `explain`, `copyGraph`, `deleteGraph`). When a node answers that a graph has moved
  (`MOVED`), the driver relearns the layout and repeats the command, up to three attempts. While a
  graph's slot is being migrated, the node giving it up answers `ASK` once the graph has gone: the driver
  then repeats the command on the node the `ASK` names, preceded by `ASKING`, without relearning the
  layout, which still names the old node until the migration ends. `copyGraph` needs both graphs on one
  node, so during a migration it can fail with `TRYAGAIN`, which is reported rather than retried.
  Pipelines, transactions and `getContext()` hold a connection to the node that owned the graph when
  they were created, so during resharding they can fail with a redirect; create them again to pick up
  the new layout. A redirect on the command means it did not run, so repeating it cannot apply a write
  twice. A query that ran but whose reply hit a redirect while being decoded (decoding can look labels
  up on the node) is reported instead, not repeated; a read-only query is repeated. If a node cannot be
  reached the layout is relearned too, but the command is reported as failed, because it may have run
  before the connection dropped.
- **Pool sizing** (`poolMaxTotal`, `poolMaxIdle`, `poolMaxWait`) applies to each node separately.
- **Credentials and TLS** are the same for every node. A database index is not supported, since a
  cluster has a single database.
- Replicas are not read from: reads and writes both go to the primaries.

Auto-detection costs one `INFO` command on the first connection. To skip it, turn it off:

```java
Driver driver = FalkorDB.builder().host("db.example.com").autoDetectCluster(false).build();
```

Building a driver still performs no I/O: the probe, like the first connection, happens when the
driver is first used.

## Query parameters

Always pass values as **parameters** rather than concatenating them into the Cypher string. Parameter
values are safely encoded as Cypher literals, so caller-supplied input cannot break out of the literal
and inject Cypher:

```java
// safe — the value is encoded, never interpolated into the query text
graph.query("MATCH (p:person {name: $name}) RETURN p", Collections.singletonMap("name", untrustedInput));
```

Values must be an encodable type (null, `String`, `Character`, `Boolean`, a boxed integer or
floating-point number, a `BigInteger` within signed 64-bit range, or an array/`List`/`Map` of such
values, where every `Map` key is a `String`); unsupported types (including `BigDecimal`), out-of-range
integers, non-finite floating-point values, cyclic containers, non-String map keys, and a parameter
name that is `null`, empty or contains a backtick, a NUL character or an unpaired surrogate are rejected
with `IllegalArgumentException`.
As in the Python client, a parameter name needn't be a Cypher identifier, because the client
backtick-quotes every name. In the query, refer to a name that isn't an identifier backtick-quoted,
as ``$`a b` ``. FalkorDB 6.x also accepts an unquoted `$1`, but 4.x needs ``$`1` ``. FalkorDB 6.x
limits a name to 512 bytes of UTF-8, which is 512 ASCII characters but only 256 characters such as `é`.
The client doesn't check that limit: it sends a longer name, and the server rejects the query.
This safety covers parameter **values** only — it does not extend to the query text or to dynamic
labels/identifiers, so never build those from untrusted input.

`callProcedure` checks the names it writes into the query itself. The procedure name must be
identifiers joined by dots, such as `db.labels` or `db.idx.fulltext.queryNodes`, and each `YIELD`
column a single identifier, where an identifier is ASCII letters, digits and underscores, not starting
with a digit (`[A-Za-z_][A-Za-z0-9_]*`). Quoted names and `AS` aliases are not accepted. Anything else,
or a `null` argument, is rejected with `IllegalArgumentException` before anything is sent. The
procedure's arguments are encoded as string literals, like parameter values.
The check is about form only: it doesn't limit which procedure runs, and some change the graph, such
as `db.idx.fulltext.drop`. If the procedure name comes from untrusted input, check it against a list
of the procedures you allow.

## Configuring Connection Pool

You can customize the connection pool to optimize performance and resource usage. JFalkorDB uses [Jedis](https://github.com/redis/jedis) internally, which provides comprehensive pool configuration options.

**Pool Configuration Guidelines:**
- `maxTotal`: Maximum number of connections (size according to your application's concurrency needs)
- `maxIdle`: Maximum idle connections kept in the pool (recommended: `maxTotal / 4` to balance resource usage and responsiveness; increase for applications with high traffic variability)
- `minIdle`: Minimum idle connections to keep ready (recommended: `maxIdle / 4` for steady performance)
- `maxWait`: Maximum time to wait for a connection when pool is exhausted

### Basic Connection Pool Configuration

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.time.Duration;

// Create a custom pool configuration
JedisPoolConfig poolConfig = new JedisPoolConfig();

// Maximum number of connections in the pool
poolConfig.setMaxTotal(128);

// Maximum number of idle connections in the pool
// Recommended: Set to maxTotal / 4 for optimal resource usage
poolConfig.setMaxIdle(32);  // 128 / 4 = 32

// Minimum number of idle connections in the pool
poolConfig.setMinIdle(8);

// Maximum time to wait for a connection (ms)
poolConfig.setMaxWait(Duration.ofSeconds(30));

// Test connections before borrowing from pool
poolConfig.setTestOnBorrow(true);

// Test connections when returning to pool
poolConfig.setTestOnReturn(true);

// Test idle connections in the pool
poolConfig.setTestWhileIdle(true);

// Create a JedisPool with custom configuration
JedisPool jedisPool = new JedisPool(poolConfig, "localhost", 6379);

// Create the driver with the custom pool; closing the driver closes the pool
try (Driver driver = new DriverImpl(jedisPool)) {
    // Use the driver
    // ... your code here ...
}
```

### Connection Pool Configuration with Authentication

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;

JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(64);
poolConfig.setMaxIdle(16);  // 64 / 4 = 16
poolConfig.setMinIdle(4);

// Configure authentication
JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
    .user("default")        // username
    .password("your-password") // password
    .build();

// Create pool with authentication and custom pool config
HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
JedisPool jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

Driver driver = new DriverImpl(jedisPool);
```

### Advanced Pool Configuration with Timeouts

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import java.time.Duration;

JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(128);
poolConfig.setMaxIdle(32);  // 128 / 4 = 32
poolConfig.setMinIdle(8);
poolConfig.setMaxWait(Duration.ofSeconds(30));

// Configure connection and socket timeouts
JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
    .connectionTimeoutMillis(2000)  // Connection timeout: time to establish connection
    .socketTimeoutMillis(5000)      // Socket timeout: time to wait for response
    .user("default")                // Username for authentication
    .password("your-password")      // Password for authentication
    .build();

// Create pool with advanced configuration
HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
JedisPool jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

Driver driver = new DriverImpl(jedisPool);
```

The socket timeout limits how long each read of a reply waits for data, and that includes the reply to
a query sent with a timeout, such as `graph.query(query, 30_000)`. That timeout limits how long the
server runs the query, not how long the client waits for it. The server sends nothing until the query
has finished, so keep the socket timeout longer than the query timeouts you pass, or set it to `0` for
no read deadline. A socket timeout doesn't stop the query either: the server may still run it to the
end, so after one you can't tell whether a write was applied, and retrying a query that isn't
idempotent can apply it twice.

A pool built without a socket timeout, like the first two examples above, gets Jedis' default of
2000 ms. A driver built with `FalkorDB.builder()` sets no read deadline unless you set `socketTimeout`,
with two exceptions. The one-shot probes that detect a Sentinel or a Redis Cluster wait at most
2000 ms when `socketTimeout` isn't set. The connections to the Sentinels themselves, which look up the
master's address and listen for failovers, never have a read deadline.

### Connection Pool Configuration with SSL/TLS

```java
import com.falkordb.Driver;
import com.falkordb.impl.api.DriverImpl;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import javax.net.ssl.SSLSocketFactory;

JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(64);
poolConfig.setMaxIdle(16);  // 64 / 4 = 16
poolConfig.setMinIdle(4);

// Configure SSL connection
JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
    .ssl(true)
    .sslSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault())
    .user("default")
    .password("your-password")
    .build();

HostAndPort hostAndPort = new HostAndPort("your-server.com", 6380);
JedisPool jedisPool = new JedisPool(poolConfig, hostAndPort, clientConfig);

Driver driver = new DriverImpl(jedisPool);
```

For more information about Jedis pool configuration options, see the [Jedis documentation](https://github.com/redis/jedis).

## Concurrency with Virtual Threads (JDK 21+)

JFalkorDB is a **blocking** client, which makes it an excellent fit for **Java 21+ virtual threads**:
run each query on its own virtual thread and let many of them block concurrently at near-async
throughput — with **no API change**. The library itself stays Java 8; you supply the executor, so this
is purely a consumer-side choice.

### Fan the blocking client out over virtual threads

`driver.graph(id)` returns a pool-per-call `GraphContextGenerator` that is safe to call concurrently, so
you can share one instance across many virtual threads:

```java
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.GraphContextGenerator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

int concurrency = 64;
try (Driver driver = FalkorDB.builder().host("localhost").port(6379)
        .poolMaxTotal(concurrency)   // real concurrency is bounded by the pool, not the thread count
        .poolMaxIdle(concurrency)
        .build()) {
    GraphContextGenerator graph = driver.graph("social");
    ExecutorService vthreads = Executors.newVirtualThreadPerTaskExecutor(); // JDK 21+
    try {
        for (int i = 0; i < 1000; i++) {
            int id = i;
            vthreads.submit(() -> graph.query("MATCH (n) WHERE n.id = $id RETURN n",
                    java.util.Collections.singletonMap("id", id)));
        }
    } finally {
        vthreads.shutdown();
        vthreads.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES); // drain in-flight queries before the pool closes
    }
}
```

### `AsyncGraph` — an optional `CompletableFuture` facade

If you prefer `CompletableFuture`s, wrap the graph with `AsyncFalkorDB.wrap(graph, executor)`. Every
method mirrors the synchronous `Graph` operation but runs on your executor and returns a future:

```java
import com.falkordb.AsyncFalkorDB;
import com.falkordb.AsyncGraph;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.ResultSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

try (Driver driver = FalkorDB.builder().host("localhost").port(6379).build();
        ExecutorService vthreads = Executors.newVirtualThreadPerTaskExecutor()) { // JDK 21+
    AsyncGraph async = AsyncFalkorDB.wrap(driver.graph("social"), vthreads);

    CompletableFuture<ResultSet> future = async.query("MATCH (n) RETURN count(n)");
    ResultSet result = future.join(); // or compose with thenApply/thenCompose
} // closes the executor first, which waits for its queued queries, then the driver
```

The facade **owns nothing** — it is not `Closeable`; the caller shuts the executor down and closes the
underlying `Driver` (which releases the pooled connections) when done, in that order. Since JDK 19 an
`ExecutorService` is `AutoCloseable`, so listing it after the driver in one try-with-resources does
both. Closing the executor waits for the queued queries. If the closing thread is interrupted, the
queries that haven't started never run and their futures never complete, but closing still waits for
the running ones, until each ends or times out. Failures surface as the future completing exceptionally
(`join()` throws `CompletionException`, `get()` throws `ExecutionException`).
Cancellation is best-effort: cancelling before a task starts skips the query, but an in-flight blocking
read cannot be interrupted — bound it with `socketTimeout` instead (see below).

### Size the pool for fan-out

Real database concurrency is bounded by the **connection pool**, not by how many virtual threads you
create: at most `poolMaxTotal` queries run against the server at once, and the default is small
(commons-pool2's default `maxTotal` is **8**). Size `poolMaxTotal` to your target concurrency `N`:

- There is **no built-in admission bound** — a virtual-thread executor accepts unbounded tasks while DB
  concurrency stays pool-limited. Add your own `Semaphore` if you need to cap in-flight work.
- Set a **finite `poolMaxWait`** so a saturated pool fails fast (throws) instead of blocking forever;
  set a finite **`socketTimeout`** so a stuck read cannot hang a worker.

### Warm the pool to avoid cold-start pinning

On **JDK 21–23**, creating a brand-new pooled connection briefly **pins** the carrier thread (the
underlying commons-pool2 `create()` is `synchronized`), so a burst of first-time connections under a
virtual-thread fan-out can momentarily starve carriers. Avoid it by **warming the pool up front** —
borrow and hold `N` connections once (on platform threads) so the pool creates them before the workload,
sizing **both** `poolMaxTotal >= N` **and** `poolMaxIdle >= N` so they are retained as idle:

```java
// Warm N connections before the virtual-thread workload (poolMaxTotal == poolMaxIdle == N).
Driver driver = FalkorDB.builder().host("localhost").port(6379)
        .poolMaxTotal(64).poolMaxIdle(64).build();
GraphContextGenerator graph = driver.graph("social");
int n = 64;
var pool = Executors.newFixedThreadPool(n); // platform threads: cold creation must not pin virtual threads
var release = new java.util.concurrent.CountDownLatch(1);
var ready = new java.util.concurrent.CountDownLatch(n);
for (int i = 0; i < n; i++) {
    pool.submit(() -> {
        try (var ctx = graph.getContext()) { // borrows a connection
            ctx.query("RETURN 1");
            ready.countDown();
            release.await();                 // hold so all N are open at once => N distinct connections
        }
        return null;
    });
}
ready.await(30, java.util.concurrent.TimeUnit.SECONDS); // (bound the waits and check results in production)
release.countDown();
pool.shutdown();
if (!pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) { // wait for connections to return
    pool.shutdownNow();
}
```

### Contended connection returns pin on JDK 21–23

Warming doesn't cover one more pin on JDK 21–23. Jedis 8 uses commons-pool2 2.13+, and its
`GenericObjectPool.returnObject` holds a `synchronized` block while it waits for the idle-connection
queue's lock ([POOL-419](https://issues.apache.org/jira/browse/POOL-419)). So whenever two virtual
threads return connections at the same moment, the one that waits pins its carrier. The pinning check's
workload, 1,024 virtual-thread queries over 16 pooled connections, produced 26 such pins on a CI runner
and up to 76 on a laptop, each lasting 0–8 ms. Each pin is short, but a pinned carrier runs no other virtual
thread meanwhile, and more simultaneous returns mean more pins.

On the query path, the client's own code holds no monitor across a blocking call. A CI **pinning
check** runs a virtual-thread query load against a standalone server on JDK 21, finds which code holds
the monitor on each pinned stack, and fails if that code is in `com.falkordb.impl`. It doesn't exercise
cluster topology refreshes. **JDK 24+ removes carrier pinning caused by `synchronized`**
([JEP 491](https://openjdk.org/jeps/491)), so both the warm-up and the return pin matter only on JDK
21–23. A virtual thread can still pin on JDK 24+ when it blocks inside native code or a callback from it,
which JFalkorDB doesn't do. For a heavy virtual-thread workload, prefer JDK 24 or later.

## Benchmarks

Continuous **client load-sweep** benchmarks run on every push to `master` and on pull requests
targeting `master`. They measure the **client-side** cost — total round-trip *minus* the server's
reported internal execution time — as concurrency (and therefore throughput) increases, so
connection/thread-management effects are visible. Interactive charts are published to GitHub Pages:

**📈 [JFalkorDB benchmarks](https://falkordb.github.io/JFalkorDB/dev/bench/)** — a latency-vs-throughput
saturation curve plus per-PR latency & throughput trends.

Run them locally with `just bench` (see [CONTRIBUTING.md](CONTRIBUTING.md)).

## License

JFalkorDB is licensed under the [BSD-3-Clause license ](https://github.com/FalkorDB/JFalkorDB/blob/master/LICENSE).
