# Migrating to JFalkorDB 0.17.0

This guide is for code that uses JFalkorDB 0.16.x. Release 0.17.0 is the first step of
[#400](https://github.com/FalkorDB/JFalkorDB/issues/400), which removes Jedis types from the public API:

- It adds replacements that name no Jedis type, and deprecates what they replace. **The deprecated API is
  removed in 0.18.0.**
- It changes a few runtime behaviors. Code that compiled against 0.16.x still compiles, and no dependency
  version changed, but the changes in [Runtime behavior changes](#runtime-behavior-changes) can be noticed.

To list what you use of the deprecated API, compile with deprecation warnings on: `javac -Xlint:deprecation`,
or `mvn compile -Dmaven.compiler.showDeprecation=true`.

## Checklist

1. Check the [runtime behavior changes](#runtime-behavior-changes) against your code, especially if you
   run FalkorDB on Valkey or compare exception classes.
2. Replace `pipelined()` with `pipeline()`, and `multi()` with `transaction()`.
3. Replace `getConnection()` and `new DriverImpl(pool)` with `FalkorDB.builder()`.
4. Catch `GraphException` and `FalkorDBConnectionException` instead of the Jedis exceptions they extend.

## Deprecated, and removed in 0.18.0

| Deprecated in 0.17.0 | Replacement |
| --- | --- |
| `GraphContext.pipelined()`, `GraphPipeline` | `GraphContext.pipeline()`, `GraphContext.Pipeline` |
| `GraphContext.multi()`, `GraphTransaction` | `GraphContext.transaction()`, `GraphContext.Transaction` |
| Jedis `Response<T>`, from the two types above | `com.falkordb.Reply<T>`, a `Supplier<T>` |
| The Jedis commands `GraphPipeline` and `GraphTransaction` inherit, such as `set` and `incr` | `sendCommand("SET", ...)`, or `sendBinaryCommand` for binary arguments |
| `Driver.getConnection()` | `driver.graph(...)` for graph commands, `FalkorDB.builder()` for the pool and TLS, a Redis client of your own for other commands ([below](#getconnection-and-driverimplpool)) |
| `DriverImpl(Pool<Jedis>)` | `FalkorDB.builder()`; to share a pool, share the `Driver`, which is thread-safe |

### Pipelines: `pipelined()` to `pipeline()`

The examples use a graph named `social`, so the other keys carry `{social}` as a hash tag and stay in
the slot of the graph on a Redis Cluster ([below](#transactions-are-not-database-transactions)).

```java
// Before (0.16.x)
try (GraphContext context = graph.getContext()) {
    GraphPipeline pipeline = context.pipelined();
    pipeline.set("{social}:visits", "0");
    Response<ResultSet> created = pipeline.query("CREATE (:Person {name: 'Alice'})");
    Response<Long> visits = pipeline.incr("{social}:visits");
    pipeline.sync();
    System.out.println(created.get().getStatistics().nodesCreated() + " node, " + visits.get() + " visits");
}
```

```java
// After (0.17.0)
try (GraphContext context = graph.getContext()) {
    GraphContext.Pipeline pipeline = context.pipeline();
    pipeline.sendCommand("SET", "{social}:visits", "0");
    Reply<ResultSet> created = pipeline.query("CREATE (:Person {name: 'Alice'})");
    Reply<Object> visits = pipeline.sendCommand("INCR", "{social}:visits"); // a Long
    pipeline.sync();
    System.out.println(created.get().getStatistics().nodesCreated() + " node, " + visits.get() + " visits");
}
```

### Transactions: `multi()` to `transaction()`

```java
// Before (0.16.x)
try (GraphContext context = graph.getContext()) {
    context.watch("social");
    GraphTransaction transaction = context.multi();
    Response<ResultSet> created = transaction.query("CREATE (:Person {name: 'Bob'})");
    transaction.incr("{social}:people");
    List<Object> results = transaction.exec();
    if (results != null) { // null: "social" changed after watch(), and nothing ran
        System.out.println(created.get().getStatistics().nodesCreated());
    }
}
```

```java
// After (0.17.0)
try (GraphContext context = graph.getContext()) {
    context.watch("social");
    GraphContext.Transaction transaction = context.transaction();
    Reply<ResultSet> created = transaction.query("CREATE (:Person {name: 'Bob'})");
    transaction.sendCommand("INCR", "{social}:people");
    List<Object> results = transaction.exec();
    if (results != null) { // null: "social" changed after watch(), and nothing ran
        System.out.println(created.get().getStatistics().nodesCreated());
    }
}
```

### `Response<T>` to `Reply<T>`

The graph methods keep their names and arguments; only the return type changes. As before, a pipeline sends each
command as it is queued, and reads the replies together in `sync()`, `syncAndReturnAll()` or `close()`;
`get()` doesn't read the reply off the connection, but decoding a
query reply can look up a label or property name the client hasn't cached: on the context's connection, or,
while a batch awaits replies, on a connection borrowed from the pool. What else differs:

| | `GraphPipeline` / `GraphTransaction` | `GraphContext.Pipeline` / `Transaction` |
| --- | --- | --- |
| Reply type | Jedis `Response<T>` | `Reply<T>`, which extends `Supplier<T>` |
| Other Redis commands | about a thousand typed Jedis methods, such as `hgetAll` | `sendCommand(name, args...)`, refused for some commands ([below](#stricter-rules-of-pipeline-and-transaction)) |
| Reply to `sendCommand` | n/a | JDK types. On the default RESP2 connection: `String` (`byte[]` from `sendBinaryCommand`), `Long`, `List`, or `null` for nil, so `HGETALL` is a `List` of fields and values. With `protocol=3` in the connection URI, a map is a `LinkedHashMap` (an empty one an empty `List`), and doubles, booleans and big numbers get their own types |
| An error reply | `JedisDataException` | `GraphException`, the same instance on every `get()` and in the returned list |
| `EXECABORT` from `exec()` | `JedisDataException` | `GraphException` |
| Connection failure | `JedisConnectionException` | `FalkorDBConnectionException` |
| `discard()` | returns `"OK"` | returns nothing |
| `waitReplicas` | inherited from Jedis | on `Pipeline` only |

A reply that is `ResultSet`, `String` or `List` keeps that type: `Response<ResultSet>` becomes
`Reply<ResultSet>`. A reply to `sendCommand` is a `Reply<Object>`: cast its value, for example
`(Long) visits.get()`.

If you implement `GraphContext` yourself, for example as a decorator or a test fake, `pipeline()` and
`transaction()` are default methods that throw `UnsupportedOperationException`. Override them.

### `getConnection()` and `DriverImpl(Pool)`

```java
// Before (0.16.x)
JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(64);
poolConfig.setMinIdle(4);
JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
        .password("secret")
        .database(2)
        .ssl(true)
        .sslSocketFactory(sslContext.getSocketFactory())
        .build();
Driver driver = new DriverImpl(new JedisPool(poolConfig, new HostAndPort("db.example.com", 6380), clientConfig));
```

```java
// After (0.17.0)
Driver driver = FalkorDB.builder()
        .host("db.example.com")
        .port(6380)
        .credentials("secret")
        .database(2)
        .sslContext(sslContext) // turns TLS on
        .poolMaxTotal(64)
        .poolMinIdle(4)
        .poolTestWhileIdle(true) // new JedisPoolConfig() turns this on; the builder doesn't
        .build();
```

The README has the [full table of Jedis settings](../README.md#migrating-from-a-hand-built-jedis-pool) and
their builder equivalents. One difference: `JedisPoolConfig` also closes connections that have been idle for
60 seconds, and the builder doesn't; `poolMaxIdle` bounds how many it keeps.

If you used `getConnection()`, or your own pool, for something else:

| You used it for | In 0.17.0 |
| --- | --- |
| Graph commands | `driver.graph(name)`, and `getContext()` to keep one connection |
| Redis commands next to graph commands, on one connection | `context.pipeline()` or `context.transaction()`, with `sendCommand` |
| Other Redis work: blocking commands, Pub/Sub, scripts, administration | A Redis client of your own. Declare its dependency yourself rather than rely on the Jedis that JFalkorDB brings in |
| Sharing one pool between components | Share the `Driver` |
| A client name, a credentials provider (rotating tokens), a host and port mapper, a custom socket factory, `testOnReturn` | Not in the builder yet. Keep the deprecated API on 0.17.x, and [open an issue](https://github.com/FalkorDB/JFalkorDB/issues) before 0.18.0 removes it |

## Runtime behavior changes

These compile unchanged and act differently.

| Change | 0.16.x | 0.17.0 | What to do |
| --- | --- | --- | --- |
| FalkorDB on Valkey reports `server_mode:cluster` or `server_mode:sentinel` in `INFO`, not `redis_mode:`, unless `extended-redis-compatibility` is on ([#532](https://github.com/FalkorDB/JFalkorDB/pull/532)) | Not detected: a plain direct connection | Detected, as for Redis: driven as a cluster (`listGraphs`, `configSet`, `udfLoad`, `udfDelete` and `udfFlush` run on every primary, `configGet`, `configGetAll` and `udfList` on any one; `CLUSTER SLOTS` must be allowed; every node must be reachable at the address it announces), or through the Sentinel, which throws `IllegalStateException` if it monitors no master or several | Nothing, if you want detection. Otherwise build the driver with `FalkorDB.builder()` and `autoDetectCluster(false)` or `autoDetectSentinel(false)`; the `FalkorDB.driver(...)` factories always detect |
| `multi()` or `pipelined()` on a closed context ([#530](https://github.com/FalkorDB/JFalkorDB/pull/530)) | Returned a batch on a connection that may already be lent to someone else | Throws `IllegalStateException` | Start batches before you close the context |
| Closing a context with a batch still open ([#530](https://github.com/FalkorDB/JFalkorDB/pull/530)) | The connection went back to the pool still inside `MULTI`, or with unread replies, and the next borrower read them as its own | An open transaction is discarded, and the replies a pipeline awaits are read, so `close()` waits for them. If several batches await replies, or ending one fails, the connection is closed instead of pooled | End each batch yourself, with `exec()`, `discard()`, `sync()` or `close()`. A reply of a transaction that closing discarded can't be read |
| Exception classes ([#535](https://github.com/FalkorDB/JFalkorDB/pull/535)) | `JedisConnectionException`; a plain `JedisDataException` from `Driver` methods, `watch` and `unwatch` | `FalkorDBConnectionException` and `GraphException`, which extend those | `catch` clauses keep matching. Replace `getClass() ==` checks with `instanceof`, and check the catch order (see [Exceptions](#exceptions)). Logs show the new class names |
| A reply in a batch that fails to decode ([#529](https://github.com/FalkorDB/JFalkorDB/pull/529)) | The first `get()` threw, later ones returned `null`. A failed schema lookup, such as an exhausted pool, escaped `syncAndReturnAll()` or `exec()` and lost every other reply | Every `get()` throws the same exception. The failure is that reply's element of the returned list, and the other replies are returned. This holds for a `RuntimeException`: an `Error` thrown while decoding escapes the read of the batch, and a `Reply` then throws that `Error`, or an `IllegalStateException` saying the reply couldn't be decoded | Check list elements with `instanceof Exception`, as for error replies |

### Stricter rules of `pipeline()` and `transaction()`

The deprecated `pipelined()` and `multi()` keep their 0.16.x behavior. The new types refuse what used to
corrupt the connection or run a command outside the batch:

| Situation | `pipelined()` / `multi()` | `pipeline()` / `transaction()` |
| --- | --- | --- |
| A context command (`query`, `explain`, `profile`, `copyGraph`, `deleteGraph`, `watch`, `unwatch`) while a pipeline awaits replies or a transaction is open | Runs, and may read the batch's reply as its own, or be queued inside its `MULTI` | `IllegalStateException` |
| Starting or using another pipeline or transaction on the context meanwhile | Same | `IllegalStateException` |
| Queuing after `exec()` or `discard()` | The command ran at once, outside the transaction | `IllegalStateException`. A transaction is used once |
| Queuing after the pipeline is closed, or after one of its reads failed | Allowed | `IllegalStateException`. After a failed read, closing the context closes its connection |
| Queuing after the context is closed | Allowed | `IllegalStateException` |
| A command listed below, which would break the batch, the connection or the schema cache | Allowed | `sendCommand` throws `IllegalArgumentException` and sends nothing |
| Reading a reply of a transaction that was discarded, aborted by `WATCH` or refused | `IllegalStateException`: "Please close pipeline or multi block" | `IllegalStateException` saying why, with the failure as its cause when there is one |

`sendCommand` refuses, ignoring case:

- commands the API runs itself: `MULTI`, `EXEC`, `DISCARD`, `WATCH`, `UNWATCH`, and every `GRAPH.*` command;
  `WAIT` too: on a pipeline use `Pipeline.waitReplicas`, and in a transaction it is refused as a blocking
  command, with no replacement;
- commands that delete or replace the graph key behind the schema cache: `DEL`, `UNLINK`, `GETDEL`, `RENAME`,
  `RENAMENX`, `MOVE`, `RESTORE` and `MIGRATE` of the graph key, `COPY` onto it, `FLUSHALL`, `FLUSHDB` and
  `SWAPDB` (use `deleteGraph()`);
- commands that change connection state or reply framing: `AUTH`, `HELLO`, `SELECT`, `CLIENT`, `RESET`,
  `QUIT`, `READONLY`, `READWRITE`, `ASKING`, `SCRIPT DEBUG`;
- commands that leave request and reply: `SUBSCRIBE` and the other subscribe commands, `MONITOR`, `SYNC`,
  `PSYNC`, `REPLCONF`;
- blocking commands: `BLPOP`, `BRPOP`, `BRPOPLPUSH`, `BLMOVE`, `BLMPOP`, `BZPOPMIN`, `BZPOPMAX`, `BZMPOP`,
  `WAITAOF`, `DEBUG SLEEP`, and `XREAD` or `XREADGROUP` with `BLOCK`.

Other commands are sent as they are. A write to the graph key that isn't listed, such as a `SET` of it or
its expiry, leaves the schema cache as it was, as a change by another client would.

The [`sendCommand` Javadoc](https://falkordb.github.io/JFalkorDB/dev/api/com/falkordb/GraphContext.Pipeline.html#sendCommand(java.lang.String,java.lang.String...))
has the details. Some of these may be allowed in a later release.

## Exceptions

| Failure | 0.16.x | 0.17.0 |
| --- | --- | --- |
| A connection can't be opened, breaks or times out, in a `Driver`, `Graph`, `GraphContextGenerator` (including `getContext()`) or `GraphContext` method | `JedisConnectionException` | `FalkorDBConnectionException`, which extends it |
| The server rejects a graph command: a query, a procedure call, `explain`, `profile`, `copyGraph`, `deleteGraph` | `GraphException` | `GraphException`, unchanged |
| The server rejects a `Driver` method (such as `configGet` of an unknown parameter), `watch`, `unwatch`, or an `AUTH` or `SELECT` while a connection opens, with a plain error (Jedis throws `JedisDataException` itself, not a subclass) | `JedisDataException` | `GraphException`, which extends it |
| An error Jedis has a subclass for, from a `Driver` method or while a connection opens, such as `JedisAccessControlException` for `WRONGPASS` or `NOPERM` | that subclass | unchanged |
| A pool that stays exhausted for `poolMaxWait` | `JedisException` | unchanged |
| A Redis Cluster none of whose nodes can be reached | `JedisClusterOperationException` | unchanged |
| Methods of the deprecated `GraphPipeline` and `GraphTransaction`, and commands on the `Jedis` from `getConnection()` | Jedis exceptions | mostly unchanged |
| Methods of `GraphContext.Pipeline` and `Transaction` | n/a | `GraphException` and `FalkorDBConnectionException` |

An `AsyncGraph` future completes exceptionally: `join()` throws `CompletionException` and `get()` throws
`ExecutionException`, whose cause is the exception above once the operation runs. If the executor rejects
the operation, the future fails with its `RejectedExecutionException` instead. A mapped exception keeps the message,
cause, suppressed exceptions and stack trace of the Jedis one; only its class changes.

How existing code behaves:

- `catch (JedisConnectionException e)`, `catch (JedisDataException e)` and `catch (JedisException e)` still
  match.
- `catch (GraphException e)` placed before `catch (JedisDataException e)` now also catches the `Driver`,
  `watch` and `unwatch` errors that the second clause used to get.
- `e.getClass() == JedisDataException.class`, or `== JedisConnectionException.class`, is now `false` for
  these failures.

Catch the client types now. In 0.18.0 they are planned to extend a new `FalkorDBException` instead of the
Jedis exceptions, so `catch (JedisDataException e)` and `catch (JedisConnectionException e)` then stop
matching them:

```java
// Before (0.16.x)
try {
    graph.query("MATCH (n) RETURN count(n)");
} catch (JedisConnectionException e) {
    // the server is unreachable, or the connection broke
} catch (JedisDataException e) {
    // the server rejected the command
}
```

```java
// After (0.17.0)
try {
    graph.query("MATCH (n) RETURN count(n)");
} catch (FalkorDBConnectionException e) {
    // the server is unreachable, or the connection broke; a write may or may not have run
} catch (GraphException e) {
    // the server rejected the command
}
```

The README section [Handling errors](../README.md#handling-errors) has more.

## Transactions are not database transactions

FalkorDB has no transactions of its own. `transaction()`, like `multi()` before it, is the `MULTI` and
`EXEC` of Redis (or Valkey) applied to graph commands:

- The queued commands run back to back at `exec()`, with no command of another client in between.
- Nothing is rolled back. If a queued query fails while it runs, the commands before it stay applied and the
  ones after it still run; the failure is its element of the list `exec()` returns. Only a command rejected
  while it is queued aborts everything (`EXECABORT`).
- Replies arrive only at `exec()`. Use `watch(...)` for optimistic concurrency.
- In a Redis Cluster every key must be in the hash slot of the graph. Tag the keys with the graph name,
  `{social}:people` for a graph named `social`; if the graph name already has a hash tag, as `tenant:{social}`
  does, reuse that tag. `CLUSTER KEYSLOT` shows the slot of a key.

For changes to a graph that must apply all together or not at all, use one Cypher query:

```java
// 0.16.x and 0.17.0
Map<String, Object> params = new HashMap<>();
params.put("from", 1);
params.put("to", 2);
params.put("amount", 100);
graph.query("MATCH (a:Account {id: $from}), (b:Account {id: $to})"
        + " SET a.balance = a.balance - $amount, b.balance = b.balance + $amount", params);
```

See the README section [Pipelines and transactions](../README.md#pipelines-and-transactions) and the
[`GraphContext.Transaction` Javadoc](https://falkordb.github.io/JFalkorDB/dev/api/com/falkordb/GraphContext.Transaction.html).

## Coming in 0.18.0

Planned in [#400](https://github.com/FalkorDB/JFalkorDB/issues/400):

- The deprecated API above is removed: `GraphPipeline`, `GraphTransaction`, `pipelined()`, `multi()`,
  `getConnection()` and `DriverImpl(Pool)`. So are the unchecked `Record.getValue` overloads deprecated in
  0.16.0.
- `GraphException` and `FalkorDBConnectionException` extend a new `FalkorDBException`, a `RuntimeException`,
  instead of the Jedis exceptions. Jedis stays a dependency and isn't shaded.
- No public signature names a Jedis type.
