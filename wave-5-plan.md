# Wave 5 — Project Loom — implementation plan

> **Temporary planning artifact** for Wave 5 (tracked by #333). Reviewed for approval; **NOT merged** —
> deleted once Wave 5 lands. Not referenced from permanent project files.
>
> Master-plan step 12 / §7. The advanced, largest, most speculative wave, intentionally last.
> This revision folds in three parallel investigations (de-pinning audit, async-facade design, MRJAR
> feasibility) **and** a rubber-duck review that corrected several of them against the actual repo.

## Goal — the "big Loom win"

On **JDK 21+**, running the **existing blocking API on virtual threads**
(`Executors.newVirtualThreadPerTaskExecutor()`) scales to many concurrent queries at near-async
efficiency, **with no API change required of consumers** — *provided the library does not needlessly pin
carrier threads*. Wave 5 removes the one pinning site we own, documents the residual upstream one, adds
a thin caller-owned-executor `CompletableFuture` facade, and keeps a **single Java-8 artifact**. **Java-8
support is retained throughout.**

## Hard constraints

- **Java-8 base stays.** Base classes remain Java-8 bytecode (`--release 8`, Animal Sniffer `java18`,
  Enforcer `maxJdkVersion=1.8`, JDK-8 smoke) — unchanged.
- **Additive / `api-diff` green.** New public types only; no changes to `Driver`/`Graph`.
- **Release-8 test compile.** `src/test/java` compiles at `release 8`, so tests **cannot** reference
  JDK-21 APIs (`newVirtualThreadPerTaskExecutor`, JFR) directly — use **reflection**, or put JDK-21/
  Java-8-runtime coverage in the standalone **`smoke-test`** module (its own JDK, external server).
- **No preview APIs** in shipped code (no `StructuredTaskScope`).
- **Streaming deferred** (needs a server cursor protocol + a Java-9+ home).

## Parallelization & dependency graph (corrected)

The rubber-duck review showed the original "four independent tracks" was too optimistic. The honest
structure is **two primary parallel tracks**, with docs trailing and the MRJAR **deferred**:

```
Track 1 (de-pinning):   A  →  B          (A depinned first; B's pinning harness must also cover cold
                                          pool creation, so A+B are stacked, not parallel to each other)
Track 2 (async facade): D                (independent of Track 1; pure Java-8, additive)
Then:                   C  (docs)         after A/B land (don't recommend Loom while the pool caveat is
                                          undocumented) — small, can overlap D's review
Deferred:               E, F (MRJAR)      not worth this wave (see §Scope); F also gates D's API shape
```

- **Parallel now:** **Track 1 (A→B)** and **Track 2 (D)** — different files, independently
  api-diff-green, each off `master`.
- **C** (docs) lands after A/B (its guidance depends on the corrected audit).
- **B and E** would both touch `pom.xml`/`Justfile`/`.github/workflows/maven.yml`; **D and E** may both
  extend `smoke-test` — another reason to defer E and avoid three-way infra contention.
- **F's** executor-ownership decision (below) must be **settled before D freezes its public API**, even
  though F's *implementation* would follow E.

## Sub-PRs

### A. De-pin our code + document the upstream pool hazard (7c) — Track 1
**Our code (the fix):** `impl/graph_cache/GraphCacheList.java:34` `getCachedData(int, Graph)` holds
`synchronized (refreshLock)` across a blocking `graph.callProcedure(...)` — the **only** `synchronized`
in `src/main/java/com/falkordb/**` (grep-verified). Replace `refreshLock` with a
`java.util.concurrent.locks.ReentrantLock` (`lock()` … `finally { unlock(); }`), keeping the
double-checked refresh (`CopyOnWriteArrayList` gives the needed visibility). Do **not** vaguely "hoist"
the fetch outside the lock — that can duplicate appends; if optimized, split *fetch* from *locked
publication*. Also either serialize `clear()` (line 59) with the refresh lock or **document concurrent
close/delete during a refresh as unsupported**.

**Upstream (document + scope, NOT a one-class win):** the hot path is **not** fully pin-free as an
earlier audit claimed. Jedis **7.5.3** resolves **commons-pool2 2.12.1** (verified via
`dependency:tree`), whose `GenericObjectPool.create()` uses `synchronized (makeObjectCountLock)` +
`Object.wait()` and `DefaultPooledObject.allocate()` is synchronized — so **cold connection creation /
replenishment can capture a carrier thread** (steady-state exhausted-pool waiting uses the
lock/condition deque and is fine; `Jedis` `Connection`/`Protocol` send/read are monitor-free). This is
**upstream, not ours**. Mitigations to document (and/or feed upstream): **warm the pool up front** —
since the builder exposes `poolMaxIdle` but **not** `minIdle`/`preparePool` (15a deferred `minIdle`, as
it needs an evictor thread), use a documented **borrow-and-hold** step (concurrently borrow then return
N connections before the workload, with `poolMaxIdle ≥ N` so they're retained), so connections aren't
created on the hot path under load; rely on JDK-21 carrier **compensation** (it adds carriers when one
blocks in `Object.wait()`, and JEP 491/JDK 24 removes the pin entirely); and set a finite `poolMaxWait`.
A commons-pool2 version bump alone does **not** fix it. *(Optional: A could instead add a supported
warm-up — expose `minIdle` + evictor, or a `preparePool()` — see open questions.)*

### B. Pinning CI check (7c) — Track 1, after A
A JFR / `jdk.VirtualThreadPinned` (`-Djdk.tracePinnedThreads`) check under a virtual-thread load test
that **fails on pins originating in `com.falkordb` frames**. It **must exercise cold pool creation and
replenishment** (not just steady state) so it actually reflects real workloads, and must **not blanket
allow-list** the commons-pool2 path — instead assert *our* frames are pin-free and record the upstream
`create()` pin as a known, documented item (note `Object.wait()` may not even surface as
`VirtualThreadPinned` because the JDK compensates by adding a carrier). Runs on JDK 21+ against a
Testcontainers FalkorDB; wired as a `just` recipe + a (likely **scheduled**, not per-PR) CI job since
vthread load tests are timing-sensitive on shared runners.

### C. Docs: concurrency model + pool-sizing tuning (7c) — after A/B
Document, in `README.md`/`docs/`, the recommended JDK-21+ model ("a virtual-thread-per-task executor +
the blocking client"), how to **size the pool** for fan-out (`poolMaxTotal` — Wave-4 15a already exposes
it; default `maxTotal=8` caps real concurrency), **and** — from A — to **warm the pool** with a
borrow-and-hold step (sizing `poolMaxIdle` to the warmed count, since `minIdle` isn't exposed) and a
finite `poolMaxWait` to keep cold-creation off the hot path. Do not publish this before A/B land.

### D. `CompletableFuture` async facade (7d) — Track 2, pure Java-8
New **public** types, pure Java-8, additive (api-diff green), **no MRJAR overlay**:
- **`com.falkordb.AsyncGraph`** — an interface mirroring the 17 query ops of `Graph`, each returning
  `CompletableFuture<T>`, plus a `graph()` escape hatch. **Not** mirrored: `close()` and the stateful,
  connection-bound context/transaction/pipeline APIs.
- **Entry point takes a concurrent-safe graph, not raw `Graph`.** `GraphContext extends Graph` but is
  **connection-bound** (one `Jedis`); wrapping it for concurrent submission would corrupt the protocol.
  The pool-per-call, concurrency-safe type is **`GraphContextGenerator`** (`Driver.graph(id)` →
  `GraphImpl`). So: **`AsyncFalkorDB.wrap(GraphContextGenerator, Executor)`** (recommended over
  `Driver.async(...)`, which would modify `Driver`). Caller supplies **and owns** both the graph and the
  `Executor`.
- **`com.falkordb.impl.api.AsyncGraphImpl`** (internal, api-diff-excluded) —
  `CompletableFuture.supplyAsync(() -> graph.query(...), executor)`.

Semantics to document precisely (rubber-duck-corrected):
- **Pure Java-8:** the caller passes the JDK-21 virtual-thread executor; the library never references it.
  Avoid `orTimeout`/`completeOnTimeout`/`delayedExecutor` (Java 9+) inside the facade.
- **No built-in backpressure** (the earlier "bounded admission" was overstated): the pool caps *active
  borrowed connections* (`min` with executor parallelism), **not** the number of submitted tasks — a
  virtual-thread executor accepts unbounded tasks; a finite `poolMaxWait` bounds wait *duration*, not
  waiter *count*. Document "active DB concurrency is pool-limited; there is no built-in admission
  bound," and, if bounded rejection is wanted later, define it explicitly (convert
  `RejectedExecutionException` into an exceptionally-completed future).
- **Cancellation is best-effort:** `cancel()` frees the caller but does **not** interrupt an in-flight
  read **and does not remove the queued runnable**; rely on per-query/server timeouts or a finite
  `socketTimeout` (#282) to reclaim a stuck worker/connection.
- **Mutable-arg hazard:** the `Map`/`List` params are read *later* on the worker thread — document that
  callers must not mutate them until completion (or the impl snapshots them).
- **Exceptions:** blocking `GraphException`/`IllegalArgumentException` surface as the future completing
  exceptionally — `join()` throws `CompletionException`, `get()` throws `ExecutionException` (unwrap via
  `getCause()`). `ResultSet` is fully materialized before completion (`ResultSetImpl`), so no partial
  reads leak.
- **F-ownership must be decided now** (see F): if F stays, ship D's interface as
  **`ManagedAsyncGraph extends AsyncGraph, java.io.Closeable`** (or an equivalent seam) from the start so
  a later default-executor variant doesn't have to change D's public API (japicmp won't catch a D↔F
  incompatibility — the baseline predates both).

Tests: unit (each async op delegates on the supplied executor; exceptions unwrap; cancel-before-start
never issues the query) using **reflection** to obtain a virtual-thread executor (release-8 test
compile); Java-8 runtime coverage in `smoke-test`; a JDK-21 IT for the vthread fan-out.

### E. MRJAR `versions/21` overlay infrastructure (7b) — **deferred (see §Scope)**
If pursued, prove it with a **no-op overlay first**: an extra `maven-compiler-plugin` execution compiles
`src/main/java21` with `<release>21</release>` (per-execution `<compileSourceRoots>`, **not**
`build-helper add-source`) into a **separate** dir (`target/classes-java21`), staged into
`target/mrjar/` for packaging with `Multi-Release: true` (pin the currently-undeclared
`maven-jar-plugin`). The separate dir is required because **Animal Sniffer scans `target/classes`
recursively** (incl. `META-INF/versions/*`) and would fail `java18` on Loom APIs. CI: `jar --validate`
(JDK 21) + the existing `smoke-jdk8`/`smoke-jdk`-21 legs asserting the loaded variant (class-file major
**52** vs **65**, via reflection).

**Correction:** japicmp is **not** MR-aware — the `api-diff` gate points at the packaged jar and japicmp
0.26.1 enumerates all `JarFile` entries (base **and** overlay), so **prototype `just api-diff` against a
real MRJAR** rather than assuming it's unaffected (it likely still passes because `jar --validate`
requires identical exported APIs, but verify). Enforcer (deps-only) and javadoc (source-roots) are
genuinely unaffected. If MRJAR ships, add a **JDK-25** runtime smoke (the matrix currently ends at 21).

### F. Facade Java-21 default-executor overlay (7b + 7d) — **deferred with E**
A `versions/21` variant whose *default* executor is virtual-thread-per-task (bounded pool on the Java-8
base). This makes the facade **create + own** an executor → it must expose lifecycle
(`ManagedAsyncGraph`/`close()`) with a **bounded** base default, decided in **D**. Implement as an
**internal executor-provider** overlaid class, not a duplicated public `AsyncFalkorDB`.

## Deferred (out of Wave 5's committed scope)
- **Streaming (`Flow.Publisher`)** — needs a server-side cursor/paging protocol (today rows are fully
  materialized) and a Java-9+ home; ship later in a separate `jfalkordb-concurrent` module.
- A full **reactive/Netty** rewrite (§7e).

## Validation (via `just` = CI)
Per PR: `just verify` · `just lint` · `just fmt-check` · `just api-diff` (green — additive) ·
`just javadoc` · `just verify-jdk8` (+ `smoke-jdk`). New: the **pinning check** (B). If E ships:
`jar --validate` + 8/21(/25) smoke + a **prototyped** `api-diff` against the MRJAR. Java-8 guardrails
stay green throughout.

## Risks & mitigations

| Risk | Mitigation |
| --- | --- |
| Cold-creation pinning is **upstream** (commons-pool2 2.12.1 `create()`/`allocate()`), not ours | Fix our one site (A); document + warm the pool (borrow-and-hold, sized by `poolMaxIdle`) + finite `poolMaxWait`; rely on JDK-21 carrier compensation / JEP 491; feed upstream. B tests cold creation. |
| Facade wraps a connection-bound `GraphContext` and corrupts the protocol | Entry point takes **`GraphContextGenerator`** (pool-per-call), not raw `Graph`; document close/delete must not race outstanding ops. |
| Tests can't use JDK-21 APIs (release-8 test compile) | Reflection for the virtual executor; JDK-21/Java-8 coverage in `smoke-test`. |
| Adding F later breaks D's API (japicmp can't see it) | Decide F now; ship `ManagedAsyncGraph extends AsyncGraph, Closeable` up front, or drop F. |
| japicmp isn't MR-aware | Prototype `api-diff` against a real MRJAR before relying on it. |
| "Bounded admission" over-promised | Document "pool-limited, no built-in backpressure"; add explicit rejection only if designed. |
| MRJAR complexity ≫ payoff | **Defer E/F** — D already enables virtual threads with a caller-owned executor. |

## Open questions

1. **Async entry type** — `AsyncFalkorDB.wrap(GraphContextGenerator, Executor)` (recommended,
   concurrency-safe), or introduce a dedicated concurrent-safe graph interface?
2. **F / MRJAR** — **defer E + F** this wave (recommended: D needs no overlay; F's only payoff is a
   default executor with an ownership cost)? If kept, ship `ManagedAsyncGraph` in **D** now.
3. **Upstream pool pinning / warm-up** — is the documented **borrow-and-hold** warm-up (sized by
   `poolMaxIdle`) enough, or should A add a **supported** warm-up (expose `minIdle` + an evictor, or a
   `preparePool()`), and/or raise the pinning upstream / evaluate a commons-pool2 bump?
4. **Pinning-check gate** — required PR gate or scheduled (vthread load tests are runner-timing
   sensitive)?
5. **PR granularity** — Track 1 as one PR (A+B) or two stacked; C folded into A/B?
