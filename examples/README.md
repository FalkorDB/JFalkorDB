# JFalkorDB examples

Small, runnable examples of the JFalkorDB public API. This is a **standalone, non-deployable** Maven
module (not part of the main reactor or the published artifact); it is compiled in CI with
`--release 8` against the built `jfalkordb` jar so the examples stay valid as the API evolves.

## Examples

| Class | What it shows |
| --- | --- |
| [`QuickStart`](src/main/java/com/falkordb/examples/QuickStart.java) | Build a driver with `FalkorDB.builder()`, run queries, iterate results. |
| [`ConfiguredDriver`](src/main/java/com/falkordb/examples/ConfiguredDriver.java) | The full builder configuration surface — credentials, TLS, pool sizing, timeouts. |
| [`EnvironmentConfiguredDriver`](src/main/java/com/falkordb/examples/EnvironmentConfiguredDriver.java) | The no-arg `FalkorDB.driver()` picking up `FALKORDB_URL` / `FALKORDB_HOST`+`FALKORDB_PORT` from the environment. |

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
# examples/pom.xml pins a default jfalkordb.version that can lag the root project, so resolve the
# real one and pass it, exactly as `just examples` does:
version="$(../mvnw -q -DforceStdout -f ../pom.xml help:evaluate -Dexpression=project.version)"
../mvnw -q exec:java -Djfalkordb.version="$version" -Dexec.mainClass=com.falkordb.examples.QuickStart
# optional host/port:
../mvnw -q exec:java -Djfalkordb.version="$version" \
    -Dexec.mainClass=com.falkordb.examples.QuickStart -Dexec.args="localhost 6379"
```

`ConfiguredDriver` only builds and closes a driver (the pool connects lazily), so it runs without a
server — it is there to show the configuration options.

`EnvironmentConfiguredDriver` behaves like `QuickStart` but connects via the no-arg
`FalkorDB.driver()`, so it also needs a FalkorDB reachable at `localhost:6379` by default — or
export `FALKORDB_URL`, or both `FALKORDB_HOST` and `FALKORDB_PORT`, to point it elsewhere first:

```sh
../mvnw -q exec:java -Djfalkordb.version="$version" \
    -Dexec.mainClass=com.falkordb.examples.EnvironmentConfiguredDriver
```
