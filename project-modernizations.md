# JFalkorDB Modernization Plan

> Filename note: intentionally named `project-modernizations.md` — the requested
> `project-modarnizations.md` was a typo, and other docs link to this filename, so it should not be
> renamed.

This plan proposes how to modernize **JFalkorDB** (the official Java client for FalkorDB) by
adopting the engineering practices already proven in
[`FalkorDB/falkordb-rs`](https://github.com/FalkorDB/falkordb-rs) and adapting them to the Java /
Maven ecosystem. It is organized by the areas requested: **development cycle**, **run CI checks
locally**, **AI standards**, **releases**, **testing (unit + system)**, **API**, and
**performance**, plus a phased rollout and the decisions we need to make.

Nothing here changes code yet — it is a plan to review and approve. Each item lists *what*, *why*,
the *Rust→Java adaptation*, and an *effort* estimate (S/M/L).

---

## Guiding principles

1. **One source of truth for every check.** A `Justfile` defines every dev-cycle action, and CI
   runs the *exact same* `just` recipe. If a check changes, the recipe and the workflow change
   together. (This is already the team's rule; we are formalizing it in the repo.)
2. **Backward compatibility is the invariant — the build JDK is not.** The *published artifact* must
   keep running on **Java 8** (many downstream consumers still require it). *Today* that is enforced
   by literally building/publishing on **JDK 8** (PR build on JDK 17), so any tool that only supports
   Java 11+ must run **only** in the JDK-17 quality gates, never in the JDK-8 publish/`test-compile`
   path — this is what bit us with `junit-jupiter` 6.x and `equalsverifier` 4.x this cycle. **§7 (now
   approved)** moves the build to **JDK 21 + `--release 8`** — which *keeps* the Java-8 runtime
   invariant (enforced by Animal Sniffer + Enforcer + a JDK-8 artifact smoke test), removes that
   fragility, and unlocks Loom/MRJAR. Until that move lands, the current JDK-8 build constraint (and
   the `junit`/`equalsverifier` pins) still holds.
3. **Definition of done = code + tests + docs + changelog, validated via `just`.**
4. **AI review is part of the gate:** resolve every Copilot *and* CodeRabbit thread (reply + mark
   resolved) before merge; never self-merge to `master` without explicit human approval.
5. **Incremental & reversible.** Land each area behind its own PR; gate risky/heavy static-analysis
   behind a Maven profile so it never destabilizes the release path.

---

## Current-state snapshot (baseline)

| Area | Today |
| --- | --- |
| Build | Maven, `maven.compiler.source/target = 8`; PR CI on JDK 17 (`mvn -B package`) |
| Publish | `snapshot.yml` (push→master, **JDK 8**) and `version-and-release.yml` (GitHub release, **JDK 8**) via `central-publishing-maven-plugin` |
| Tests | JUnit 5 (5.14.4), ~136 tests; connect to a **hardcoded `localhost:6379`** provided by a GitHub Actions *service container*; no Testcontainers |
| Coverage | JaCoCo 0.8.15 → Codecov (`codecov.yml`, 1% threshold) |
| Quality | No formatter, no in-build linter; DeepSource configured externally (`.deepsource.toml`); CodeQL + spellcheck workflows |
| Deps | Dependabot with Java-8 guards (ignore `junit-jupiter` & `equalsverifier` semver-major) |
| Releases | **Manual**: cut a GitHub Release → `version-and-release.yml` runs `mvn versions:set` + deploy; no CHANGELOG, no conventional-commit enforcement |
| Contributor docs | `README.md`, `CODE_OF_CONDUCT.md`; **no** `CONTRIBUTING.md`, `Justfile`, AI instructions, `CHANGELOG.md`, examples, or benchmarks |

## Reference model → Java adaptation

| falkordb-rs (Rust) | Purpose | JFalkorDB (Java) equivalent |
| --- | --- | --- |
| `Justfile` (`just`) | Dev-cycle automation = CI | **`Justfile` (`just`)** wrapping Maven — same tool |
| `cargo fmt` / `rustfmt.toml` | Formatting gate | **Spotless** + google-/palantir-java-format |
| `cargo clippy` | Lint gate | **Error Prone** (compile) + **SpotBugs**(+FindSecBugs); optionally Checkstyle/PMD |
| `cargo deny` (`deny.toml`) | License/advisory/ban audit | **OWASP dependency-check** + **license-maven-plugin** allowlist + `maven-enforcer` bans |
| `cargo doc` + doctests | Docs compile gate | `maven-javadoc-plugin` + **compiled `examples/`** (Java has no doctests) |
| `criterion` (`benches/`) + benchmark blog | Benchmarks + regression tracking | **JMH** module + **`github-action-benchmark`** per-PR-vs-`main` charts on GitHub Pages |
| `proptest` | Property tests | **jqwik** (Java-8 compatible) |
| `cargo-nextest` | Test runner | Surefire (unit) + **Failsafe** (integration `*IT`) |
| `release-plz` (`release-plz.toml`) | Automated release PRs + changelog | **release-please** (`release-type: maven`) |
| `semver_check` | API-break detection | **japicmp** or **revapi** Maven plugin |
| `cargo-rdme` (README from `//!`) | Docs/README sync | Manual README + compiled `examples/` (Java-8 Javadoc; `{@snippet}` is JDK 18+, so only after the JDK-21 build) |
| `llms.txt` | Machine-readable API for LLMs | **`llms.txt`** (same concept) |
| `.github/copilot-instructions.md` | AI agent conventions | **Same file** + `AGENTS.md`/`CLAUDE.md` |
| `.coderabbit.yaml` | CodeRabbit config | **Same file** |
| `tokio` async client + async `Stream` | Non-blocking API | **`CompletableFuture` facade** (Java-8 API) + **Project Loom** virtual threads via a **Multi-Release JAR** overlay; `Flow.Publisher` **streaming is future-gated** and ships in a **separate Java-9+ module**, not the MRJAR (see §7d) |
| Testcontainers-style Docker via `just db-*` | Deterministic server for tests | **Testcontainers-Java** + `just db-*` fallback |

---

## 1. Development cycle

**Goal:** a documented, conventional, low-friction loop from clone → change → green PR → release.

- **`CONTRIBUTING.md`** describing the loop, the `just` recipes, how to run unit vs. system tests,
  and the definition of done (mirrors falkordb-rs's). *(S)*
- **Conventional Commits** for PR titles (squash-merge uses the PR title as the commit subject,
  which release-please consumes). Enforce with a `pr-title` CI gate (e.g.
  `amannn/action-semantic-pull-request`) **and** a `just check-pr-title` recipe so it's reproducible
  locally. *(S)*
- **Branch naming**: `feat/…`, `fix/…`, `docs/…`, `ci/…`, `chore/…`. *(S)*
- **Definition of done** (encode in AI instructions + CONTRIBUTING):
  1. Design first for non-trivial work; rubber-duck the design.
  2. Implement **code + tests + docs (Javadoc/example)**; the changelog is generated by
     release-please from the Conventional-Commit subject (§4), so contributors do **not** hand-edit
     `CHANGELOG.md`.
  3. `just verify` green locally (all gates + system tests).
  4. Open PR on a conventional branch; keep docs in sync.
  5. Resolve **all** Copilot + CodeRabbit threads (reply + resolve).
  6. **Never self-merge to `master`** — get it green, then wait for human approval.
- **`.editorconfig`** for consistent whitespace across editors/IDEs. *(S)*

## 2. Run CI checks locally (`just` = CI)

**Goal:** every CI gate is a `just` recipe a contributor can run identically on their machine.

Add a root **`Justfile`** whose recipes wrap Maven, plus Docker helpers for a FalkorDB server.
CI workflows call the recipe (e.g. `run: just verify`), never a raw `mvn …`.

Proposed recipe set (sketch — verify commands during implementation):

```makefile
# Uses the Maven Wrapper (./mvnw) with every plugin version pinned in the POM for reproducibility.
# `verify` is THE aggregate gate; sub-recipes are focused local runs. CI calls `just ci` (no server)
# then `just verify` (server) + `just coverage` — recipes wrap the same Maven phases CI runs.
# All non-release recipes pass -Dgpg.skip=true (maven-gpg:sign is bound to verify; signing is release-only).

# Fast inner loop (no server): format-check + lint + compile
check:      ; ./mvnw -q -T1C -Pquality spotless:check compile

# No-server CI gates (build JDK): format, lint, static analysis, javadoc, examples, dep audit, api-diff
ci:         ; ./mvnw -q -Pquality -DskipTests -Dgpg.skip=true verify

# Full validation: unit (Surefire) + system (Failsafe *IT) + coverage + api-diff. THE aggregate gate.
verify:     ; ./mvnw -q -Pquality,it,coverage -Dgpg.skip=true verify

unit:       ; ./mvnw -q test                     # Surefire (unit *Test) only
system:     ; ./mvnw -q -Pit,quality -Dgpg.skip=true verify  # via verify so *IT compile & bind (never call failsafe:* directly)
coverage:   ; ./mvnw -q -Pit,coverage -Dgpg.skip=true verify # JaCoCo report bound AFTER Failsafe (merged unit+IT data)
fmt:        ; ./mvnw -q -Pquality spotless:apply
javadoc:    ; ./mvnw -q -Pquality javadoc:javadoc
deps-audit: ; ./mvnw -q -Pquality dependency-check:check   # pinned plugin; NVD key + cache configured
bench:      ; ./mvnw -q -Pbench compile exec:exec          # JMH (see §8)

# Docker FalkorDB helpers (fallback when a test doesn't manage its own Testcontainer)
db-up:      ; docker run -d --name jfalkordb-dev -p 6379:6379 falkordb/falkordb:<pinned-tag>
db-down:    ; docker rm -f jfalkordb-dev
db-populate:; # load shared fixture graph used by system tests
```

- **Recipe/gate correctness (must-haves):** `verify` is the *only* aggregate and always runs through
  Maven phases so `*IT` are compiled and bound — **never call `failsafe:*` goals directly** (they can
  run zero tests on a clean checkout); bind the JaCoCo **report after Failsafe** over merged unit+IT
  data; and configure **japicmp/revapi and `dependency-check` to fail the build** (their break/CVSS
  thresholds default to non-failing).
- **Reproducibility:** add the **Maven Wrapper** (`mvnw`) and **pin every plugin version**
  (`maven-compiler-plugin`, `-jar-`, `-failsafe-`, japicmp, spotbugs, spotless…) in the POM so
  `maven.compiler.release`/MRJAR behavior never depends on the host Maven. Pin `dependency-check` with
  an **NVD API key + local cache/mirror**, a suppression file, and an explicit failure threshold.
- **Tool ↔ build-JDK compatibility:** the latest **Error Prone** / google-java-format require a newer
  JDK than 17 — pin a version compatible with the current build JDK, or land the JDK-21 build (§7a)
  **before** enabling them.
- **Signing stays release-only:** the POM binds `maven-gpg-plugin:sign` to `verify`, so every
  non-release recipe passes **`-Dgpg.skip=true`** (as the snapshot job already does) — local/PR
  `verify` needs no GPG key; signing runs only in the release-deploy profile.

- **Key nuance (consistency with the JDK-8 constraint):** formatting/lint/static-analysis live in
  a **`quality` Maven profile** activated by the JDK-17 CI and `just` gates — **not** by default,
  so the JDK-8 `mvn deploy` never invokes Java-11+ tools. *(M)*
- **CI refactor:** split `maven.yml` into focused jobs that each run one recipe
  (`just check`, `just ci`, `just verify`, `just coverage`) — matching falkordb-rs's
  `pr-checks.yml`. Pin `just` via `taiki-e/install-action` or `extractions/setup-just`. *(M)*
- Keep the existing FalkorDB **service container** as the CI default initially; migrate system
  tests to **Testcontainers** (§5) so `just verify` works with zero manual setup locally. *(M)*

## 3. AI standards

**Goal:** encode conventions so AI agents (and humans) land changes clean on the first try.

- **`.github/copilot-instructions.md`** — the engineering playbook: the `just`=CI golden rule,
  definition of done, JDK-8 publish constraint, dependency pins (`junit-jupiter` 5.x,
  `equalsverifier` 3.x), AI-review-resolution rule, no-self-merge rule. Adapt from falkordb-rs. *(S)*
- **`AGENTS.md`** and **`CLAUDE.md`** — thin pointers to the copilot-instructions file so all agent
  tools converge on one source. *(S)*
- **`llms.txt`** — a machine-readable summary: one-paragraph overview, install snippet, core idioms
  ("always parameterize queries", "select a graph handle", result iteration), pitfalls, and a
  generated **Public API** list. Add a `just llms`/`just check-llms` recipe and a CI gate that
  fails if it drifts from the public API. *(M)*
- **`.coderabbit.yaml`** — configure CodeRabbit (e.g. path filters, disable false-positive
  pre-merge checks) so its reviews stay signal-rich. *(S)*
- Document the **"reply + resolve every AI thread"** and **"no self-merge"** rules explicitly. *(S)*

## 4. Releases

**Goal:** replace manual releases with automated, conventional-commit-driven releases + a changelog,
while preserving the existing JDK-8 Maven Central publish.

- **release-please** (`googleapis/release-please-action`, `release-type: maven`): reads Conventional
  Commits and maintains a **Release PR** that bumps the `pom.xml` version and **owns `CHANGELOG.md`**
  (generated from commits — don't also hand-write entries, or they duplicate). Merging it creates the
  tag + **GitHub Release**. *(M)*
- **Trigger caveat (important):** events raised by the default `GITHUB_TOKEN` (the Release
  release-please creates) **do not trigger** other workflows, so `version-and-release.yml`'s
  `release: published` job won't fire on its own. Either (a) run the deploy **inside** the
  release-please workflow when `release_created == true`, or (b) use a **GitHub App / PAT** so the
  Release event triggers the existing publisher. *(S)*
- **Snapshot-publish interaction:** release-please's Maven flow merges a **non-`-SNAPSHOT`** POM (and
  opens a follow-up snapshot-bump PR). Since `snapshot.yml` deploys on **every** master push, guard it
  to deploy **only when the evaluated version ends in `-SNAPSHOT`** (via `help:evaluate
  -Dexpression=project.version`) so a release commit doesn't attempt an unsigned/duplicate deploy or
  race the real release job. *(S)*
- **API-diff gate:** run **japicmp**/**revapi** (configured to *fail* on incompatible changes) so a
  public-API break is a conscious `feat!:`/major decision — the analog of falkordb-rs's `semver_check`.
  *(M)*
- Conventional-commit **PR-title gate** (from §1) ensures squash subjects feed release-please. *(S)*
- **Release-deploy determinism:** `version-and-release.yml` runs `clean deploy` (which re-runs tests)
  against the `edge` image — pin it to the released FalkorDB version, or adopt option (B)
  `-Dmaven.test.skip=true` on deploy, so a publish can't fail on an upstream image change. *(S)*

## 5. Testing — unit + system (enhancement focus)

**Goal:** grow both fast **unit** tests and realistic **system** tests, make them deterministic and
runnable anywhere, and measure their effectiveness — not just line coverage.

### 5a. Test taxonomy & runner split
- **Unit tests** (`*Test`, Surefire): pure, no server — entities (`Node`, `Edge`, `Point`,
  `Property`, `Path`, `Header`), exceptions, response/reply parsing, utils, config. #224 already
  expanded these; continue toward every public class.
- **System/integration tests** (`*IT`, **Failsafe**): end-to-end against a **real** FalkorDB. Split
  so unit tests stay fast and always run, while system tests run in the `verify`/`integration-test`
  phase. *(M)*

### 5b. Deterministic server via Testcontainers *(high value)*
- Replace the hardcoded `FalkorDB.driver()` → `localhost:6379` assumption with a
  **Testcontainers-Java** managed `falkordb/falkordb` container: tests start/stop the server
  themselves, so `mvn verify` / `just verify` works locally with **zero manual setup** and
  identically in CI. Testcontainers supports Java 8. Keep a `FALKORDB_HOST/PORT` override to reuse
  an external server (and the current CI service container) when desired. *(M)*
- Provide a shared **fixture loader** (a base test class / JUnit 5 extension) that seeds a known
  graph, mirroring falkordb-rs's IMDB fixture — so system tests are readable and isolated. *(M)*

### 5c. Broaden coverage & strengthen assertions
- **Parameterized tests** (`@ParameterizedTest`) for value/round-trip matrices (types, unicode,
  numeric boundaries, null handling). *(S)*
- **Property-based tests** with **jqwik** (Java-8 compatible): e.g. serialize→parse round-trips and
  **adversarial parameter-escaping invariants** that back the §6 parameter-hardening work (escaping is
  currently incomplete — see §6). *(M)*
- **Contract tests** with **equalsverifier** (already a dependency) for *every* value type's
  `equals`/`hashCode`. *(S)*
- **System scenarios to add:** transactions & pipelines, indexes/constraints, UDFs, large result
  sets & pagination, unicode/binary values, error paths & server errors, **timeout behavior**
  (the `CLIENT PAUSE` regression style added to #282), reconnection/pool exhaustion, and
  concurrency. *(M/L)*

### 5d. Compatibility matrices *(catch what single-config CI misses)*
- **FalkorDB version matrix** via Testcontainers image tag: **required** PR jobs pin a specific
  version/digest (so an upstream image change can't break a required check); run `edge`/`latest` as
  **scheduled, non-blocking canaries**. *(M)*
- **JDK runtime matrix (8, 11, 17, 21):** the artifact targets Java 8, so prove *the packaged artifact
  runs* on every supported JDK via a **small consumer smoke-test module** that depends on the built
  jar — **not** by running the full suite on JDK 8 (the suite may use Java-17 test tooling that can't
  run on 8). This is the check that would have caught this cycle's bug. *(M)*

### 5e. Test effectiveness
- **Mutation testing** with **PITest** (`pitest-maven`) on the pure-unit packages, behind a profile
  / scheduled job — measures whether tests actually catch regressions, not just execute lines. *(M)*
- Keep JaCoCo→Codecov; consider **per-package** coverage targets in `codecov.yml` once system tests
  land. *(S)*

## 6. API

**Goal:** make the client safer, more ergonomic, better documented, and API-stable — informed by
falkordb-rs's idioms.

- **Audit & harden the query-parameter path *before* advertising it as injection-safe.** Today
  `Utils.quoteString` escapes only `"` (not `\` or control characters) and parameter **keys** are
  interpolated unvalidated, so the current parameter path is **not** injection-proof. Harden it (a
  correct Cypher encoder that escapes backslash/control chars, key validation, a value-type
  whitelist, adversarial + jqwik property tests), **then** document `query(cypher, params)` and
  "never concatenate user input into Cypher" as the default in README, examples, `llms.txt`, and
  Javadoc. *(M)*
- **Fluent, documented config builder** for host/port, auth, TLS, pool sizing, and **timeouts**
  (surface the socket-timeout behavior clarified in #282). *(M)*
- **Nullability annotations** (JSpecify or JetBrains `@Nullable`/`@NonNull`) on the public API for
  better IDE/AI ergonomics and null-safety. *(M)*
- **Runnable `examples/`** (compiled in CI via `just`/the `quality` profile) — the Java analog of
  doctests; doubles as copy-paste docs and keeps snippets from rotting. Use **external compiled
  examples** (not Javadoc `{@snippet}`, which is JDK 18+ and would break the JDK-8/17 Javadoc build
  until §7a lands). *(M)*
- **Javadoc quality gate** (`javadoc:javadoc` with `-Xdoclint`) so public API stays documented;
  publish Javadoc to GitHub Pages (analog of falkordb-rs's `pages.yml`). *(M)*
- **API stability**: japicmp/revapi (from §4) + a documented semver policy. *(S)*
- **Non-blocking / async API & Project Loom:** see **§7** — a backward-compatible
  `CompletableFuture` facade plus virtual-thread support.
- **Longer-term (decide explicitly):** typed **row→POJO mapping** (the analog of falkordb-rs's
  `serde` result mapping). Large; propose as a follow-up RFC. *(L)*

## 7. Java runtime modernization & non-blocking APIs (Project Loom)

**Goal:** modernize *how* the client uses Java and offer non-blocking usage — **without breaking
Java-8 consumers**. The strategy leans into **Project Loom (virtual threads)** rather than a
reactive rewrite: keep the simple blocking API, make it virtual-thread-friendly, and add a thin
async facade.

### 7a. Modernize the build (APPROVED): JDK 21 + `--release 8`
- Move the PR **and** publish builds to **JDK 21** and compile the main artifact with
  **`maven.compiler.release = 8`** instead of `source/target = 8`. `--release 8` compiles against the
  *real* Java 8 API, so it **cannot** accidentally pull in a Java 11+ method (a stronger guarantee
  than today) while still emitting Java-8 bytecode. *(M)*
- Benefits: keeps the **Java-8 runtime invariant**, removes the "must build on JDK 8" fragility that
  broke the publish this cycle, and — because the deploy then runs on JDK 21 — dissolves the
  test-scope Java-8 constraint (§5's `-Dmaven.test.skip` / JUnit-6 questions). Use Maven
  **toolchains** if a specific JDK is pinned for signing. **Approved direction** (D6).
- **Caveat:** `--release 8` only constrains *our* source/bytecode — it does **not** verify that
  third-party dependencies are Java-8 classfiles. Enforce that with **two complementary** checks:
  **`animal-sniffer-maven-plugin`** (our code uses only the Java-8 *API* signature) **and** enforcer's
  **`enforceBytecodeVersion`** (every dependency classfile is ≤ Java-8 bytecode), plus the §5d
  packaged-artifact smoke test on JDK 8. *(S)*

### 7b. Backward-compatible higher-Java code: Multi-Release JAR (MRJAR)
- Ship a **single artifact** whose base classes are Java-8 bytecode, plus a `META-INF/versions/21/`
  overlay (manifest `Multi-Release: true`) compiled with `--release 21`. Java-8 consumers load the
  base; Java-21+ consumers automatically get the Loom-optimized implementations of the same classes.
  Build via a second `maven-compiler-plugin` execution for the overlay sources (build JDK ≥ 21).
  **MRJAR rule:** the overlay may differ only in *implementation* — the **public API must be identical
  and Java-8-expressible** across versions. A public method therefore cannot return a Java-9+ type
  (e.g. `Flow.Publisher`) only in the overlay; such APIs must live in the separate module below. *(M/L)*
- Simpler alternative if MRJAR overhead isn't wanted: a **separate optional module**
  (e.g. `jfalkordb-concurrent`) targeting Java 21, leaving the `jfalkordb` core on Java 8. *(M)*
- **Enforce MRJAR integrity** in CI: `jar --validate` the built artifact and smoke-test it on the
  **base (JDK 8)** and **overlay (JDK 21)** runtimes so the versions stay API-identical and both load. *(S)*

### 7c. Make the blocking client virtual-thread-friendly (the big Loom win)
- On JDK 21+, running the **existing blocking API on virtual threads**
  (`Executors.newVirtualThreadPerTaskExecutor()`) already scales to many concurrent queries at
  near-async efficiency — **no API change required of consumers**. The library's job is to **not pin**
  carrier threads:
  - Replace `synchronized` guarding blocking socket I/O with **`ReentrantLock`** (before JDK 24,
    `synchronized` held across a blocking call pins the carrier thread; **JEP 491**/JDK 24 removes
    this, but we still support 21–23). Audit our code **and** the hot Jedis / commons-pool2 paths;
    where pinning lives in Jedis, feed findings upstream and/or document mitigation. *(M)*
  - Add a JFR/`-Djdk.tracePinnedThreads` **pinning check** under a virtual-thread load test in CI. *(S)*
  - Document "virtual-thread executor + the blocking client" as the recommended concurrency model on
    JDK 21+. *(S)*
  - **Raise/expose the connection-pool ceiling:** commons-pool2's default `maxTotal` (**8**) caps real
    concurrency no matter how many virtual threads exist — surface pool sizing in the config builder
    (§6) and document tuning it for virtual-thread fan-out. *(S)*

### 7d. Thin non-blocking facade (`CompletableFuture`) — Java-8 compatible
- Offer an async view (e.g. `AsyncGraph`) returning **`CompletableFuture<ResultSet>`**, backed by a
  **pluggable `Executor`** with explicit **ownership, best-effort cancellation, timeouts, and bounded
  admission** (bounded by pool size — see §7c). This is an async **facade** that offloads the blocking
  call — it is **not** transport-level non-blocking I/O, and **cancelling the future cannot interrupt an
  in-flight blocking Jedis read**: treat cancellation as best-effort and rely on socket/command
  **timeouts** (the #282 knob) to bound a stuck call and reclaim its pool slot. `CompletableFuture` is
  **Java 8**, so the public API stays Java-8-safe. *(M)*
- The MRJAR Java-21 overlay may only swap the **default executor implementation** to
  virtual-thread-per-task (same public signature) — cheap-at-scale on modern JDKs, safe on old ones. *(M)*
- **Streaming is future-gated.** True `Flow.Publisher` streaming/backpressure needs (1) a **server-side
  cursor/paging protocol** — today a query fully materializes its rows before returning, so a publisher
  would merely re-emit an in-memory list — and (2) a **Java-9+ home** (it cannot be an MRJAR
  overlay-only public type; ship it in the separate `jfalkordb-concurrent` module / a Java-9-baseline
  artifact). Do **not** promise it on the Java-8 artifact. *(L)*
- Avoid JDK-21 **structured concurrency** here: `StructuredTaskScope` is a **preview** API
  (`--enable-preview`, tied to the exact JDK release) and is unsuitable for a library MRJAR overlay —
  use stable `CompletableFuture`/executors. *(note)*

### 7e. Explicitly out of scope
- A full **reactive/Netty** non-blocking rewrite: Loom makes it largely unnecessary, it would
  fragment the API, and it's a very large effort. Prefer virtual threads + the `CompletableFuture`
  facade.

**Net effect:** Java-8 users keep today's API unchanged; Java-21+ users get cheap massive concurrency
(virtual threads) and an async facade — all from **one backward-compatible artifact**.

## 8. Performance & regression radar (scheduled early — Wave 2)

**Goal:** make performance a **continuously visible** signal, not an afterthought — benchmark **every
PR against the `main` baseline with a chart**, so regressions are caught **while developing**. Modeled
on falkordb-rs's criterion suite, upgraded to per-PR tracking.

- **JMH** benchmarks in a dedicated `benchmarks/` module (or `-Pbench` profile) so JMH deps never
  touch the published artifact or the JDK-8 deploy path. Server-backed via Testcontainers/`db-*`:
  query throughput & latency, pipeline vs. single, parameter binding, result parsing, pool sizing. *(M)*
- **Per-PR benchmark-vs-`main`, with graphics.** Run JMH (JSON output) in CI and feed it to
  **`benchmark-action/github-action-benchmark`** (native JMH support): it stores the `main` baseline,
  **comments the PR with the comparison**, **alerts when a metric regresses** past a threshold, and
  **publishes an interactive trend chart to GitHub Pages**. *(M)*
- **Tame CI noise so the signal is trustworthy (important):** GitHub-hosted runners vary run-to-run,
  so (a) run the **PR and the `main` baseline in the same job/runner** and compare the *relative*
  delta; (b) use proper JMH **warmup/forks/iterations** and a **stable macro-throughput** metric (not
  micro-noise); (c) set a **generous alert threshold** (~1.5–2×) and keep the PR comment
  **informational** (warn, don't hard-block) — escalate to a **dedicated/self-hosted runner** only if
  hard gating is later wanted. *(M)*
- Recipes: `just bench` / `just bench-one <id>` / `just bench-baseline` (refresh the `main` baseline).
  *(S)*

---

## Cross-cutting: protecting the JDK-8 publish

Because `mvn deploy` on **JDK 8** still **compiles test sources**, every *test-scope* dependency
must be Java-8-compatible (this is why `junit-jupiter` is pinned to 5.x and `equalsverifier` to 3.x,
with Dependabot ignoring their majors). **Chosen path: (C)** below — move the build to JDK 21 +
`--release 8`; **(A)/(B) are interim only** until (C) lands (and the fallback if the JDK-21 move is
ever reverted):

- **(A) Keep the constraint** (default, lowest risk): new test tools (jqwik, Testcontainers, JMH)
  are all Java-8 compatible; keep verifying dependency bumps with a **JDK-8**
  `mvn -DskipTests -Dgpg.skip=true package` (add a `just verify-jdk8` recipe + optional CI job to
  automate the check we did by hand this cycle).
- **(B) Decouple the toolchain** (unlocks modern test libs): change the publish to
  **`-Dmaven.test.skip=true`** so the JDK-8 deploy stops compiling tests. Tests are already gated in
  the JDK-17 PR build, so the deploy only needs to build/sign/publish the main artifact (which stays
  Java-8). This would let the project adopt JUnit 6 etc. *Tradeoff:* the deploy no longer
  re-validates tests — acceptable because CI does. **Decision needed.**
- **(C) Modernize the build to JDK 21 + `--release 8`** (see §7a): the strategic fix — removes the
  test-scope Java-8 *compile* constraint (deploy runs on JDK 21), **subsuming (B)**, and enables the
  Loom/MRJAR work. It keeps *our* code Java-8-targeted but does **not** by itself prove Java-8 *runtime*
  compatibility — pair it with animal-sniffer/enforcer bytecode checks and the §5d artifact smoke test.
  Bigger change; recommended target end-state. **(A)/(B) are interim options only while (C) is
  deferred.**

Separately, **whether to raise the library's own Java baseline** (8 → 11/17) is a **downstream
breaking change** for consumers still on Java 8; recommend keeping Java 8 for the artifact for now
(only modernize the *toolchain*), and revisit with user data. **Decision needed.**

---

## Phased rollout

| Wave | Theme | Items | Risk |
| --- | --- | --- | --- |
| **1** | Foundation | Maven Wrapper, `Justfile` + CI-as-`just`, `CONTRIBUTING.md`, AI standards (`copilot-instructions.md`/`AGENTS.md`/`llms.txt`/`.coderabbit.yaml`), `.editorconfig`, Spotless | Low |
| **2** | Pivot + harness | **JDK 21 + `--release 8`** + Java-8 enforcement (smoke test, Animal Sniffer, Enforcer), Testcontainers + fixtures + Failsafe, **JMH + per-PR benchmark-vs-`main` charts** | Med |
| **3** | Correctness & gates | Parameter-path hardening (§6), Error Prone/SpotBugs/OWASP audit, releases (PR-title + API-diff → §4 safeguards → release-please) | Med |
| **4** | Depth & polish | test depth + JDK/FalkorDB matrices + PITest, API ergonomics (examples, nullability, config builder, Javadoc+Pages) | Med |
| **5** | Advanced (Loom) | de-pin + pinning check + pool sizing + `CompletableFuture` facade, MRJAR overlay; streaming later (separate module) | Med/High |

The **Recommended implementation order** below is the authoritative sequence (with rationale and
decision-dependencies). Waves are one or a few PRs each, green before the next; items **within** a
wave are independent PRs that can overlap. The Wave-1 `Justfile` is the end-state — the `quality`
profile is created in Wave 1 (for Spotless, with no lifecycle binding) and extended in Wave 3, while
the `it`/`coverage` profiles arrive across Waves 2–3, so it ships a working subset first.
**Parameter hardening (§6 / Wave 3) gates any injection-safety claim**, so `llms.txt` (Wave 1) must
carry the caveat until then.

## Decisions (recorded 2026-07-16, by @barakb)

| # | Decision | Chosen | Rationale |
| --- | --- | --- | --- |
| **D1** | Test/publish decoupling | **Resolved by D6** | The JDK-21 build removes the test-compile Java-8 constraint; (A)/(B) kept only as fallback. |
| **D2** | Minimum consumer Java baseline | **Stay on Java 8** (revisit at a major release) | Maximum reach (Jedis + many consumers still on 8); `--release 8` + MRJAR already give 21+ users Loom without dropping 8. |
| **D3** | Code formatter | **palantir-java-format** | Formats JFalkorDB's fluent/builder-heavy code more readably than google-java-format. |
| **D4** | Static-analysis depth | **Spotless + SpotBugs/FindSecBugs + Error Prone** (no Checkstyle/PMD) | High-signal bug + security + compile-time checks; Checkstyle/PMD are redundant with the formatter and noisy. |
| **D4b** | DeepSource | **Retire; consolidate into in-build `just` gates** | In-build gates are reproducible locally (the `just` = CI golden rule); DeepSource is not. |
| **D5** | Release automation | **release-please** | Conventional-commit-driven version + CHANGELOG + release PR; mirrors falkordb-rs's `release-plz`; integrates with the existing publish. |
| **D6** | Build JDK | **JDK 21 + `--release 8`** | Retires the JDK-8 build fragility, keeps the Java-8 artifact, unlocks Loom/MRJAR + modern tooling. |
| **D7** | Non-blocking distribution | **MRJAR (single artifact), implemented facade-first** | Best consumer UX (one artifact, auto-optimized on 21+); start with the Java-8 `CompletableFuture` facade + de-pinning, add the `versions/21` overlay when the auto-default is worth it; a separate module is reserved for real `Flow` streaming. |

All gating decisions are now locked, so the implementation order below can proceed without further
decision blockers.

## Success metrics

- `just verify` reproduces CI locally with zero manual server setup.
- New-contributor time-to-green-PR drops (documented loop + local gates).
- Coverage **and** mutation score trend up; system tests exercise real server behavior.
- Releases are one merged Release PR (changelog + version + publish) with no manual steps.
- Zero JDK-8 publish breakages from dependency bumps (guarded automatically).
- The CI virtual-thread **pinning test passes for our code paths** (JDK 21+) — any residual upstream
  Jedis/commons-pool2 pinning is tracked and mitigated — and a `CompletableFuture` async facade is
  available **without dropping Java-8 support**.

---

## Recommended implementation order

The "Phased rollout" table groups items by *theme*; this section is the recommended *execution
order* and the reasoning behind it. It optimizes for: **decide the things that change everything
first → lay the foundation everything plugs into → install a safety net → do one-time churn early →
unblock testing → fix correctness before advertising it → modernize the core as the pivot → leave the
largest, most speculative work for last.** Items inside a wave are largely independent PRs and can
overlap; the *wave* order encodes real dependencies.

### Wave 0 — Decisions (locked — see "Decisions (recorded)")
- **All gating decisions are made:** D6 build JDK = **JDK 21 + `--release 8`** (step 4); D3 formatter
  = **palantir-java-format** (step 3); D4 = **Spotless + SpotBugs/FindSecBugs + Error Prone**, retire
  DeepSource (step 8); D5 = **release-please** (step 9); D2 = **stay on Java 8**; D7 = **MRJAR,
  facade-first** (step 12). No decision blockers remain — proceed straight to Wave 1.
- **Contingency** (only if the JDK-21 move is ever reverted): keep the JDK-8 build with test-compat
  option **(A)** or **(B)** (§ Cross-cutting), pin quality tools to JDK-17-compatible versions, and
  ship Loom as a **`CompletableFuture` facade only** (no MRJAR/virtual-thread overlay).

### Wave 1 — Foundation (low risk, high leverage — start now)
1. **Maven Wrapper (`mvnw`) + `Justfile` + split CI into `just` recipes**. *Why here:* the wrapper
   makes builds reproducible from step 1 (recipes already call `./mvnw`), and the Justfile is the
   substrate every later gate plugs into — CI becomes reproducible locally on day one. Pin each plugin
   version as it is first introduced.
2. **AI standards + `CONTRIBUTING.md` + `.editorconfig`**. *Why here:* cheap, compounding leverage so
   every later PR (human or AI) lands cleaner — but keep the injection-safety caveat in `llms.txt`
   until step 7.
3. **Spotless formatting** (format only). *Why here:* a one-time whole-repo reformat is least
   disruptive done **before** more code lands.

### Wave 2 — Pivot to JDK 21 + test/bench harness (D6 approved)
4. **JDK 21 + `--release 8`** (the approved build direction) **with** the durable Java-8 guarantees in
   the *same* change: the **§5d packaged-artifact smoke test on JDK 8**, the **Java-8 API-signature
   check (Animal Sniffer)**, and the **dependency classfile-version check (Enforcer
   `enforceBytecodeVersion`)**. This **replaces** the build-on-JDK-8 constraint — no separate interim
   guard is needed; the existing dependency pins/ignores simply remain in force until this lands.
   *Why this early:* it's decided, retires the recurring publish fragility, and **unblocks** modern
   quality tools (step 8), JMH, and the Loom/MRJAR work.
5. **Testcontainers + fixtures + Failsafe split**. *Why here:* makes `just verify` work locally with
   zero manual server setup; it is the **server harness** that both system tests **and** the benchmarks
   need, and a prerequisite for the FalkorDB **version** matrix (§5d). *(The JDK-runtime matrix instead
   uses the packaged-artifact smoke test.)*
6. **Benchmark harness + per-PR regression radar (§8):** a JMH `benchmarks` module plus
   **`github-action-benchmark`** that benchmarks **every PR against the `main` baseline**, comments a
   **comparison + chart**, alerts on regression (generous threshold, informational), and publishes a
   **trend graph to GitHub Pages**. *Why this early:* it makes performance regressions visible
   **during** the heavier development of Waves 3–5, not after.

### Wave 3 — Correctness & gates
7. **Parameter-path hardening (§6)** — including its **own adversarial + jqwik escaping tests** (broader
   property testing stays in step 10). *Why here:* a security-correctness fix **and** a prerequisite for
   any injection-safety claim; land it (with its tests) before README/`llms.txt`/Javadoc advertise the
   parameter path as safe.
8. **Remaining quality gates** — Spotless + SpotBugs/FindSecBugs + Error Prone (no Checkstyle/PMD),
   OWASP dependency-check, and **retire DeepSource** (their latest versions need the JDK-21 build from
   step 4). *(D4 decided.)*
9. **Releases** (§4), in this sub-order: (a) conventional-commit **PR-title gate** + the
   **japicmp/revapi API-diff gate**; (b) all §4 workflow safeguards — token/trigger fix, the
   `snapshot.yml` `-SNAPSHOT`-only guard, deterministic release deploy; **then** (c) enable
   **release-please** + `CHANGELOG.md` **last**. *Why this order:* enabling release-please before the
   safeguards risks an unpublished or nondeterministic first automated release. *(D5: release-please.)*

### Wave 4 — Depth & polish
10. **Test depth + matrices + PITest**: broader parameterized/jqwik property tests, the JDK & FalkorDB
    matrices, and mutation testing (the parameter-specific escaping tests already landed in step 7).
11. **API ergonomics**: examples, nullability annotations, a config builder (timeouts + **pool
    sizing**), Javadoc + GitHub Pages — built on the now-safe parameter path.

### Wave 5 — Advanced (largest, most speculative — last)
12. **Project Loom** *(D2: Java 8; D7: MRJAR, facade-first)*: de-pinning audit + pinning CI check +
    pool-sizing config + the `CompletableFuture` async facade, then the **MRJAR** overlay. **Streaming
    (`Flow`)** comes later still, in a Java-9+ module, and only **after** a server-side cursor/paging
    protocol exists. *Why last:* biggest effort, depends on step 4, and the streaming piece needs
    protocol work that doesn't exist yet.

**Fastest path to visible value:** Wave 1 can merge this week — `just`-based local CI and AI-ready
conventions — followed immediately by the **JDK-21 pivot (step 4)** and the **per-PR benchmark radar
(step 6)**, so regression visibility exists early. **Highest-leverage single change:** step 4 (JDK 21
+ `--release 8`), which retires the recurring Java-8 publish fragility and unblocks the newer quality
tools (step 8) and the Loom/MRJAR work (Wave 5); much of Wave 4 (jqwik, API ergonomics, Javadoc) does
**not** depend on it and can proceed in parallel.
