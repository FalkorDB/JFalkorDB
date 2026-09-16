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
      <version>0.12.0</version>
    </dependency>
  </dependencies>
```

## Snapshots

```xml
  <repositories>
    <repository>
      <id>snapshots-repo</id>
      <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    </repository>
  </repositories>
```

and

```xml
  <dependencies>
    <dependency>
      <groupId>com.falkordb</groupId>
      <artifactId>jfalkordb</artifactId>
      <version>0.12.1-SNAPSHOT</version> <!-- x-release-please-version -->
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

        // general context api. Not bound to graph key or connection
        Driver driver = FalkorDB.driver();
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
            Node n = record.getValue("a");
            Edge e =  record.getValue("r");

            //print record
            System.out.println("Node: " + n + ", Edge: " + e);
        }

        resultSet = graph.query("MATCH p = (:person)-[:knows]->(:person) RETURN p");
        for(Record record: resultSet) {
            Path p = record.getValue("p");

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
integers, non-finite floating-point values, cyclic containers, non-String map keys, and invalid
parameter names are rejected with `IllegalArgumentException`.
This safety covers parameter **values** only — it does not extend to the query text, dynamic
labels/identifiers, or procedure names, so never build those from untrusted input.

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

// Create the driver with the custom pool
Driver driver = new DriverImpl(jedisPool);

// Use the driver
// ... your code here ...

// Don't forget to close the driver when done
driver.close();
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

Driver driver = FalkorDB.builder().host("localhost").port(6379).build();
ExecutorService vthreads = Executors.newVirtualThreadPerTaskExecutor(); // JDK 21+
AsyncGraph async = AsyncFalkorDB.wrap(driver.graph("social"), vthreads);

CompletableFuture<ResultSet> future = async.query("MATCH (n) RETURN count(n)");
ResultSet result = future.join(); // or compose with thenApply/thenCompose

// on shutdown: drain outstanding futures, then vthreads.shutdown() and driver.close()
```

The facade **owns nothing** — it is not `Closeable`; the caller shuts the executor down and closes the
underlying `Driver` (which releases the pooled connections) when done. Failures surface as the future
completing exceptionally (`join()` throws `CompletionException`, `get()` throws `ExecutionException`).
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

The client's own code holds no lock across a blocking call — a scheduled CI **pinning check** verifies no
carrier pin originates in `com.falkordb.impl` under a virtual-thread load — and **JDK 24+ removes carrier
pinning for `synchronized` entirely** ([JEP 491](https://openjdk.org/jeps/491)), so this warm-up matters
only on JDK 21–23.

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
