# JFalkorDB examples

Small, runnable examples of the JFalkorDB public API. This is a **standalone, non-deployable** Maven
module (not part of the main reactor or the published artifact); it is compiled in CI with
`--release 8` against the built `jfalkordb` jar so the examples stay valid as the API evolves.

## Examples

| Class | What it shows |
| --- | --- |
| [`QuickStart`](src/main/java/com/falkordb/examples/QuickStart.java) | Build a driver with `FalkorDB.builder()`, run queries, iterate results. |
| [`ConfiguredDriver`](src/main/java/com/falkordb/examples/ConfiguredDriver.java) | The full builder configuration surface — credentials, TLS, pool sizing, timeouts. |

## Build

From the repository root:

```sh
just examples
```

This installs the current `jfalkordb` jar and compiles this module against it.

## Run

`QuickStart` needs a FalkorDB reachable at `localhost:6379` (start one with `just db-up`). After
`just examples`, run a class against this module and its dependencies — the simplest way is via the
Exec plugin:

```sh
cd examples
../mvnw -q exec:java -Dexec.mainClass=com.falkordb.examples.QuickStart
# optional host/port:
../mvnw -q exec:java -Dexec.mainClass=com.falkordb.examples.QuickStart -Dexec.args="localhost 6379"
```

`ConfiguredDriver` only builds and closes a driver (the pool connects lazily), so it runs without a
server — it is there to show the configuration options.
