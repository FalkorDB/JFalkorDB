# JFalkorDB Wave 4 — Depth & polish: implementation plan

> Temporary planning artifact. Reviewed for approval; deleted after the wave lands. Do not reference
> it from permanent project files.
>
> This revision folds in a rubber-duck review that checked each item against the actual codebase.

## Goal

Wave 4 = the master plan's **steps 10 + 11** — deepen test *effectiveness* (not just line coverage),
prove cross-environment compatibility, and polish the public API + docs. It builds on Waves 1–3.

## What already landed (do NOT redo — verified in-repo)

- Testcontainers server + **`TestServer`** helper; `*Test`/`*IT` split; `equalsverifier` is a
  dependency (used in `PathTest`).
- **Existing `*IT` scenarios already cover:** transactions (`TransactionIT`), pipelines
  (`PipelineIT`), UDFs (`UdfIT`), config (`ConfigIT`), iteration (`IterableIT`), list-graphs, graph
  API + **concurrency** + the **`CLIENT PAUSE` timeout** regression (`GraphAPIIT`), instantiation,
  parameter round-trips (`ParameterRoundTripIT`). Wave 4 targets **gaps only**.
- Parameter hardening + jqwik **escaping** properties (Wave 3 PR 8); japicmp `api-diff`, PR-title
  gate, release-please (Wave 3 10a/10c); the JDK-8 consumer **`smoke-test/`** module + `verify-jdk8`.
- The required `verify`/`build` job **and** `smoke-jdk8` already run against the **pinned FalkorDB
  digest** (`TestServer` `IMAGE` constant).

## Sub-PR sequence (proposed PRs 12–18)

Each is its own Conventional-Commit PR, green via `just`, AI-reviewed, not self-merged.

### PR 12a — equality-contract **fixes** (`fix:`) *(prereq for 12b)*
Adding `equalsverifier` for every value type will **expose real defects**, so fix them first, as
explicit `fix:` PRs (each is a user-visible behavior change → api-diff still passes; semver-minor):
- **`Point`**: `equals` uses epsilon (`1e-5`) but `hashCode` hashes exact doubles → equal points can
  hash differently (contract violation). Decide: drop epsilon from `equals`, or quantize both.
- **`Property`**: equality normalizes but `hashCode` doesn't (asymmetric). Align them.
- Audit the other value types the same way before writing their verifiers.

### PR 12b — Test depth (§5c) *(test-only, after 12a)*
- **Inventory first**, then fill genuine gaps only. Candidate gaps (confirm before writing):
  indexes/constraints, large result sets with Cypher **`SKIP`/`LIMIT`** (there is no cursor/paging
  API — word it as such), unicode values, server-error paths not yet asserted, reconnection / **pool
  exhaustion** (needs the PR-15 config to set pool size), broader concurrency.
- **`@ParameterizedTest`** value/round-trip matrices (types, unicode, numeric boundaries, nulls).
- **`equalsverifier` for every value *implementation*** (`Node`, `Edge`, `Point`, `Property`,
  `Path`, `GraphEntity`, `StatisticsImpl`, result/record impls — **not** the `Header`/`Record`/
  `ResultSet` *interfaces*).
- Broader **jqwik** serialize→parse / round-trip properties (Wave 3 only did parameter escaping).
- A shared **fixture loader** (JUnit 5 extension) seeding a known graph — **land the extension before**
  the tests that use it.

### PR 13 — Compatibility matrices (§5d) *(needs a small test-harness code change)*
- **`TestServer` image override:** the image is a hardcoded pinned digest; add a **validated
  `FALKORDB_IMAGE` env/property override** (default = the pinned digest) so the harness can matrix over
  tags. This is a code change, not CI-only.
- **FalkorDB version matrix:** keep the **pinned digest** as the *required* job; define the **fixed
  min + current supported** versions and test them (required); run **`edge`/`latest` as scheduled,
  non-blocking canaries** (matrix via the new override).
- **JDK runtime matrix (8, 11, 17, 21):** run the **`smoke-test` consumer module against the built
  jar on each JDK** (generalize `verify-jdk8` → a `verify-jdk <N>` recipe + CI matrix), not the full
  suite on JDK 8. Keep the existing `smoke-jdk8` context until branch protection is updated.

### PR 14 — Mutation testing / PITest (§5e) *(observability, scheduled)*
- Add **`pitest-maven` + `pitest-junit5-plugin`** (required for JUnit 5) in the off-by-default
  `quality` profile; pin compatible versions. Configure **`targetClasses`** = pure-unit packages
  **and** `targetTests`/`excludedTestClasses` to **exclude `*IT` and the expensive jqwik property
  test** (PIT discovers all tests otherwise). `just mutation` recipe + a **scheduled** CI job
  (observability, not a required gate) — or a path-filtered PR check on unit-only changes.

### PR 15 — Fluent config builder + nullability (§6) *(the one real API-design PR)*
- **`FalkorDB.builder()`** — a **new, distinct entry point** (host/port, auth, TLS, **pool sizing**,
  **timeouts** per #282). Keep existing factories; **no ambiguous `driver(Config)` overloads and no
  new abstract interface methods**, so `api-diff` stays green (additive/binary-compatible).
- **Nullability annotations (JSpecify — Java-8 compatible):** **audit** actual nullable behavior + generic
  bounds per member; do **not** blanket-`@NullMarked`. JSpecify becomes a **shipped runtime
  dependency** → run the **JDK-8 smoke** to confirm. (Binary-compatible, but source/tooling-contract
  affecting — hence per-member.)
- Include Javadoc for the new API (see PR 17 gate — land the Javadoc gate **before/within** this PR).

### PR 16 — Runnable examples module (§6) *(depends on PR 15)*
- A **standalone, non-reactor, non-deployable** `examples/` module compiled in CI with **`--release
  8`** (like `smoke-test`/`benchmarks`), using the PR-15 builder. External compiled examples (not
  Javadoc `{@snippet}`, which is JDK 18+). `just examples` recipe + CI compile job.

### PR 17 — Javadoc gate + Pages (§6) *(resolve the existing Pages publisher conflict)*
- **Javadoc gate:** `javadoc:javadoc` with **`source/release 8`, `doclint=all`, `failOnWarnings=true`**
  (not `-Xdoclint` alone), aligned with the `api-diff` public-API boundary; fix existing doclint gaps.
- **Pages — coordinate with `benchmark.yml`** (which already publishes to the **`gh-pages` branch**):
  do **not** switch Pages source to "GitHub Actions" (breaks benchmarks). Instead publish Javadoc into
  a **`gh-pages` subdirectory** (e.g. **`/dev/api`** for master/unreleased) with **shared
  `concurrency`** to avoid races; publish **versioned/stable** docs from releases. (Alternative:
  migrate benchmarks + Javadoc into one Pages artifact — larger.)
- Short **semver policy doc** referencing `api-diff` + release-please.

### PR 18 — Close the semver label↔commit gap (small `ci:`) *(can land early)*
- Today a `breaking-change`-labelled **`fix:`** passes `api-diff` and would release as a **patch**.
  Make the **PR-title gate require a `!`** (e.g. `fix!:`) whenever the `breaking-change` label is
  present, and trigger on **`labeled`/`unlabeled`** so it re-checks. Closes the gap flagged in 10c.

## Sequencing & dependencies
`18 (tiny) + 12a (fixes) → 12b (tests) → 13 (matrices) → 14 (PITest) → 15 (builder+nullability) → 17
(Javadoc gate, with/just-before 15) → 16 (examples, needs 15)`. Pool-exhaustion/timeout tests (12b)
also depend on the PR-15 config. 12a/13/14/18 are low-risk; 15 is the one design-heavy PR.

## Cross-cutting / risks

| Risk | Mitigation |
| --- | --- |
| `equalsverifier` exposes real equals/hashCode bugs | PR 12a fixes them first as explicit `fix:` PRs. |
| `TestServer` image is hardcoded | PR 13 adds a validated `FALKORDB_IMAGE` override (default pinned). |
| PITest discovers `*IT`/jqwik (slow) | Explicit `targetTests`/`excludedTestClasses`; scheduled only. |
| Config builder breaks `api-diff` | Additive `FalkorDB.builder()` only; no overloads/new interface methods. |
| JSpecify shipped dep / blanket nullability | Per-member audit; JDK-8 smoke; JSpecify is Java-8 compatible. |
| Javadoc→Pages races with benchmark `gh-pages` | Subdirectory + shared `concurrency`; don't switch Pages source. |
| Doclint flags many gaps | `release 8` + `failOnWarnings`; scope to public API; fix incrementally. |

## Operational (you) steps
- **GitHub Pages** already serves benchmarks from `gh-pages`; PR 17 adds a Javadoc subdirectory (no
  source switch). Confirm Pages stays on the `gh-pages` branch source.
- Update **branch protection** to require the new **JDK-runtime-matrix**, **examples**, and **Javadoc**
  checks (keep `smoke-jdk8` until then); keep **canaries + PITest non-required**.

## Definition of done (per PR)
Design-first + rubber-duck for the non-trivial PRs (13 matrices, 15 API); **code + tests + docs**;
**`just verify`** (+ targeted recipes) green; **`just spellcheck`** on Markdown; Conventional-Commit
PR; **all AI review resolved**; **not self-merged**. Open to reprioritizing the order.
