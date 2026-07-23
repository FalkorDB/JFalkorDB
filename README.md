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

```xml
  <dependencies>
    <dependency>
      <groupId>com.falkordb</groupId>
      <artifactId>jfalkordb</artifactId>
      <version>0.9.0</version>
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
      <version>0.10.0-SNAPSHOT</version>
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
        vthreads.shutdown(); // then awaitTermination(...) to drain in-flight queries
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
