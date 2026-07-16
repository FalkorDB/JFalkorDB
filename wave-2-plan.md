# Wave 2 — Pivot to JDK 21 + test/bench harness (scaffolding plan)

> **Status: plan for review — no implementation until approved.** This is the detailed plan for
> **Wave 2** of the [modernization plan](project-modernizations.md); it implements that plan's
> recommended-order **steps 4–6** (decision **D6** — JDK 21 + `--release 8` — is already approved).
> Like the Wave 1 plan, this doc is a temporary planning artifact and will be closed/deleted once
> Wave 2 lands; it is **not** referenced from any permanent project file.
>
> The **default branch is `master`** (not `main`); every "baseline" below means `master`.

## Scope

Wave 2 is the **pivot**. It moves the build to **JDK 21** while keeping a **Java-8 artifact** (via
`--release 8`), then stands up the two harnesses everything later depends on: a **Testcontainers**-
managed FalkorDB for system tests, and a **per-PR JMH benchmark radar**. Three PRs, each green before
the next starts:

| PR | Title | What | Risk |
| --- | --- | --- | --- |
| **5** | `build: pivot to JDK 21 + --release 8 (+ Java-8 guardrails)` | JDK 21 in every workflow (publish workflows converted to `just`); `maven.compiler.release=8`; Animal Sniffer + Enforcer bytecode check + a JDK-8 **artifact** smoke test (new required check) | Med |
| **6** | `test: Testcontainers server + fixtures + Failsafe *IT split` | container-managed FalkorDB behind a **test-only** endpoint factory; `*IT` on Failsafe; JaCoCo report moved to `verify`; shared fixture loader; `it` profile | Med |
| **7** | `bench: JMH (standalone) + per-PR-vs-master regression radar` | standalone `benchmarks/` project (never shipped); `github-action-benchmark`; read-only PR comment + Pages trend published only from `master` | Med |

### Explicitly NOT in Wave 2 (and where it lives)
- **Unpinning** `junit-jupiter`/`equalsverifier` or relaxing their Dependabot ignores — *possible once
  PR 5 lands, but deferred to a dedicated follow-up* to keep PR 5's blast radius small.
- **Error Prone / SpotBugs / FindSecBugs / OWASP** and **retiring DeepSource** — **Wave 3** (their
  current versions need the JDK-21 build PR 5 delivers).
- **Parameter-path hardening (§6)** and **releases / release-please (§4)** — **Wave 3**.
- **FalkorDB-version matrix, JDK-runtime matrix, PITest** — **Wave 4** (PR 6 is their prerequisite).
- **MRJAR overlay, Loom virtual-thread work, `CompletableFuture` facade** — **Wave 5**.

### Ordering & dependencies
**PR 5 → PR 6 → PR 7**, but the coupling is *soft*, not absolute:
- Testcontainers **1.x is Java-8 compatible**, so PR 6 could technically precede PR 5. We still do PR 5
  first because it is the approved pivot, it moves the deploy to JDK 21 (so newer *test-dependency
  bytecode* stops being a publish hazard), and it installs the guardrails everything else relies on.
- PR 7's benchmarks run against the PR 6 server harness, so PR 7 follows PR 6.

---

## PR 5 — Pivot the build to JDK 21 + `--release 8`

**What**
1. **Compiler:** replace `maven.compiler.source=8` + `maven.compiler.target=8` with
   **`maven.compiler.release=8`** (compiles main code against the *real* Java-8 API, still emits
   class-file version 52). Pin **maven-compiler-plugin ≥ 3.13** explicitly.
   - *Note:* `release` also governs **test** compilation. We **keep test sources Java-8-source** for
     now (no `maven.compiler.testRelease` bump) — the constraint that actually dissolves is **test-
     *dependency* bytecode**, because the deploy runs on JDK 21 and no longer needs test deps to be
     Java-8 classfiles. (Allowing Java-11+ *test source* is a separate, later choice.)
2. **Workflows → JDK 21, all via `just`:** `maven.yml` (`build` + `format`, 17 → 21). Convert the
   publish workflows — which today call **raw `mvn`** (`snapshot.yml` `mvn … deploy`;
   `version-and-release.yml`) — to **`just` recipes** (e.g. `just deploy-snapshot` / `just deploy-release`),
   honoring the `just`=CI golden rule, and run them on JDK 21. Pin the **CodeQL** `Analyze (java)` job
   to **JDK 21** and replace **autobuild** with a manual `just` build so it matches the real build.
3. **Java-8 guardrails, in the *same* PR** — each proves a **different**, narrow thing; together they
   replace the old "build on JDK 8" habit:
   - **Animal Sniffer** (`animal-sniffer-maven-plugin`, signature
     `org.codehaus.mojo.signature:java18:1.0`) — proves **our main classes** call only the Java-8
     **API**. *(Does not inspect dependency implementations.)*
   - **Enforcer `enforceBytecodeVersion`** (`extra-enforcer-rules`, `maxJdkVersion=1.8`,
     `<ignoredScopes><ignoredScope>test</ignoredScope></ignoredScopes>`) — checks the **base**
     classfiles of **shipped** (compile/runtime) dependencies are ≤ Java-8 bytecode. *(Default
     `strict=false` skips `module-info` and MRJAR `META-INF/versions/**` entries — e.g. `commons-text`
     ships a Java-9 `module-info.class`, harmless on Java 8 — so this checks the **base** classfiles,
     not literally every entry. It also doesn't prove Java-8 bytecode avoids a Java-9+ API — that's
     Animal Sniffer's job for our code.)*
   - **JDK-8 artifact smoke test (§5d):** an **isolated downstream consumer** (a standalone project,
     **not** a reactor module) that resolves the **packaged jar + its full runtime dependency graph**
     and performs a **real connect → query → read result → close** cycle under a **JDK 8 runtime**,
     against a **pinned** `falkordb/falkordb@sha256:…` server on **localhost:6379**. Have the consumer
     use the **no-arg `FalkorDB.driver()`** so this required check *also* verifies the published
     **localhost:6379 default contract** at runtime (a construction-only unit test can't). Run it as a
     **separate CI job** that downloads/consumes the built artifact (simpler and more honest than
     rebuilding the root in a JDK matrix, which could resolve `target/classes` instead of the jar).
     This proves the **exercised connect/query path** runs on Java 8 — a *runtime* guarantee the other
     two checks don't give.
     - **Honest scope:** this smoke test would **not** have caught *this cycle's* break (that was a
       *test-compile* failure on JDK 8, which the JDK-21 pivot removes outright); it guards the
       **different** risk that a shipped change stops *running* on Java 8. It exercises the smoke
       consumer's code path, not every method in the artifact.
4. **Make the Java-8 guarantee actually gate merges:** the smoke job is a **new required status
   check**. Preserve the existing required contexts **`build`** and **`format`** (don't rename them —
   e.g. don't silently turn `build` into a matrix that changes its context) and **add the smoke
   check to branch protection atomically** with the PR, or the guarantee is non-blocking.
5. **Update the durable docs that currently assert "build on JDK 8":** `copilot-instructions.md`,
   `CONTRIBUTING.md`, `AGENTS.md`, the `Justfile` comments, the **quality-profile comment in
   `pom.xml`** (it currently references avoiding the "JDK-8 publish build"), and the **rationale** in
   `.github/dependabot.yml` (the pins remain, but "because the deploy compiles tests on JDK 8" is no
   longer the reason). Add a `just verify-jdk8` recipe (build on 21 → run the artifact smoke on 8).

**Why here:** it's the approved (D6) pivot — retires the recurring publish fragility, and *unblocks*
the Wave 3 quality tools, JMH, and the Wave 5 Loom/MRJAR work.

**Key snippets** (versions pinned at implementation time):

```xml
<properties>
  <maven.compiler.release>8</maven.compiler.release>
  <!-- remove maven.compiler.source / maven.compiler.target -->
</properties>
```
```xml
<!-- our main code uses only the Java-8 API -->
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>animal-sniffer-maven-plugin</artifactId>
  <configuration>
    <signature>
      <groupId>org.codehaus.mojo.signature</groupId>
      <artifactId>java18</artifactId><version>1.0</version>
    </signature>
  </configuration>
  <executions><execution><phase>verify</phase><goals><goal>check</goal></goals></execution></executions>
</plugin>
<!-- every SHIPPED dependency classfile is <= Java-8 bytecode (test scope excluded) -->
<plugin>
  <artifactId>maven-enforcer-plugin</artifactId>
  <dependencies><dependency>
    <groupId>org.codehaus.mojo</groupId><artifactId>extra-enforcer-rules</artifactId>
  </dependency></dependencies>
  <executions><execution><id>enforce-bytecode-8</id><goals><goal>enforce</goal></goals>
    <configuration><rules><enforceBytecodeVersion>
      <maxJdkVersion>1.8</maxJdkVersion>
      <ignoredScopes><ignoredScope>test</ignoredScope></ignoredScopes>
    </enforceBytecodeVersion></rules></configuration>
  </execution></executions>
</plugin>
```

**Risks & mitigations**
- *A plugin/step misbehaves on JDK 21 (gpg sign, central-publishing, jacoco, surefire)* → all current
  versions support 21; validate `just verify` on JDK 21 locally. The snapshot path uses
  `-Dgpg.skip=true`, so it does **not** exercise signing — validate the **signing** step with a
  **`clean verify` + GPG enabled** on JDK 21 (`maven-gpg:sign` is bound to `verify`). **Never** run the
  live `deploy`/release recipe to "test" it: Central is `autoPublish=true`, so a throwaway-tag deploy
  would actually **publish**. If the assembled Central bundle must be exercised, do it behind an
  explicit no-upload/`skipPublishing` mechanism.
- *`--release 8` now rejects a call that compiled before* → that call used a Java-9+ API on Java-8
  bytecode — a **latent bug**; fix the call (the guard working as intended).
- *Enforcer flags a shipped dependency as > Java-8 bytecode* → a real Java-8 **runtime** risk; pin or
  exclude it. The rule is scoped to compile/runtime so test tooling never trips it.
- *Signing JDK* → **Maven toolchains are not a signing mechanism** (GPG runs as an external `gpg`
  process; toolchains only affect toolchain-aware plugins). Sign+publish directly under JDK 21; only
  if some plugin genuinely needed another JVM would we use a separate Maven invocation.
- *Required-check drift* → keep `build`/`format` context names; add the smoke check to branch
  protection in lockstep with merging PR 5.

**Validation:** `just verify` green on JDK 21; the **JDK-8 artifact smoke** job green (packaged jar +
full runtime graph, real query cycle via no-arg `driver()`, on JDK 8); the **signing** step validated
via `clean verify` + GPG on JDK 21; a deliberately-added Java-11 API call fails at **compile** under
`--release 8` (Animal Sniffer additionally catches Java-8-*API*-signature misuse that `--release` may
not — best exercised with a dedicated compiled fixture); and a deliberately-added Java-11-bytecode
*runtime* dep fails Enforcer.

---

## PR 6 — Testcontainers server + fixtures + Failsafe `*IT` split

**What**
- Add **Testcontainers-Java** (test scope; **1.x is Java-8 compatible**) managing a `falkordb/falkordb`
  container.
- **Do not touch the public API.** `FalkorDB.driver()` (no-arg) is a published overload that defaults
  to `localhost:6379`; production code must keep that contract. Put the container wiring in a
  **test-only endpoint factory / JUnit 5 extension** that exposes the container host/port, and convert
  ordinary integration tests to **`driver(host, port)`**. Keep the zero-arg `driver()` behavior covered
  by a **unit test** (and, if desired, a dedicated localhost/service-container compatibility IT) rather
  than by repointing production defaults.
- **Endpoint override:** honor `FALKORDB_HOST`/`FALKORDB_PORT` **only in the test factory**; resolve
  the override **before** constructing a container (so an external server is used *instead of* Docker),
  require **both** host and port when overriding, and add a **readiness probe** before tests run.
  **Safety:** some ITs mutate server-wide state (e.g. `UdfTest` globally lists/deletes UDF libraries),
  so an external override must point at a **dedicated disposable** server and require an explicit
  **destructive-test opt-in** — otherwise **skip globally-mutating ITs** under an external override.
  The default Testcontainers path is safe (the container is disposable).
- **Container lifecycle:** use a **manual singleton** (started once, reused, JVM-shutdown cleanup via
  Ryuk) **or** a custom root-scoped JUnit extension — **not** a per-class `@Container` (which would
  start/stop per test class and fight the singleton). **Pin the image tag/digest** for required jobs
  (`edge`/`latest` become scheduled canaries in Wave 4).
- **Fixture loader:** a shared helper that seeds a known IMDB-style graph so system tests are readable
  and isolated.
- **Runner split:** end-to-end tests become **`*IT`** on **Failsafe** (`integration-test`/`verify`);
  pure unit `*Test` stay fast on **Surefire**. Introduce the `it` Maven profile and wire `just verify`
  (unit + IT, zero manual setup). **Note:** Maven compiles `*IT` **sources** during `test-compile`
  even when Failsafe is inactive, so Testcontainers must be on the **test-compile** classpath in every
  build.
- **Fix JaCoCo** (currently `report` is bound to **`test`**, which runs *before* Failsafe, so IT
  coverage would be lost, and two JVMs can clobber one `.exec`): move the **report to `verify`** and
  either use a single **append-enabled** exec file or **separate unit/IT exec files + `jacoco:merge`**.
- **CI/recipes:** exercise **Testcontainers in required CI** (Docker is available on the ubuntu
  runners) so the new lifecycle is actually validated — either drop the `build` job's FalkorDB
  **service container**, or keep it only where the JDK-8/default-localhost smoke needs it. **Redefine
  `just verify-local`** so it does **not** start a server and *then* let `just verify` start a second
  one (today `verify-local` brings up its own server); make the override/lifecycle coherent.

**Why here:** it's the server harness both system tests **and** the benchmarks need, makes
`just verify` reproducible locally with zero setup, and is the prerequisite for the Wave 4
FalkorDB-version matrix.

**Risks & mitigations**
- *Testcontainers' transitive test deps aren't Java-8 bytecode* → they're **test scope**; the deploy
  compiles tests on JDK 21 post-PR-5 and Enforcer excludes test scope (a reason PR 5 goes first).
- *Docker-in-CI cost/flakiness* → singleton reused container + Ryuk; pinned image for required jobs.
- *Local dev needs Docker* → documented in `CONTRIBUTING.md`; the `FALKORDB_HOST/PORT` override and
  `just db-up` fallback cover Docker-less/remote setups.
- *Accidentally changing public behavior* → the override lives only in the test factory; production
  `driver()` is untouched — its no-arg **runtime** contract is proven by PR 5's JDK-8 smoke (which
  calls `driver()`), with a construction unit test as the fast local guard.

**Validation:** `just verify` runs unit + `*IT` green **with no manual server**, locally and in CI;
JaCoCo coverage includes IT (report at `verify`); the fixture loader seeds/isolates; the
`FALKORDB_HOST/PORT` override reuses an external server; the zero-arg `driver()` contract test still
passes.

---

## PR 7 — JMH benchmarks (standalone) + per-PR-vs-`master` radar (§8)

**What**
- A **standalone `benchmarks/` Maven project** (its own `pom.xml`, invoked with
  `./mvnw -f benchmarks/pom.xml`, targeting **JDK 21**, marked **non-deployable**
  `maven.deploy.skip=true`) — **not** a reactor module of the root (the root **is** the published jar;
  a reactor child could inherit GPG/Central-publishing/`release=8`/Enforcer and either get signed &
  deployed or fail enforcement on modern benchmark deps). It consumes the built core jar as a normal
  dependency. Extend `fmt-check`/Dependabot to cover it.
- **Reusing PR 6 fixtures:** a separate project can't see `src/test/java`, so it **can't reuse the
  Testcontainers lifecycle/factory** — only fixture **data** is shareable via resources. Either share
  the fixture **data** and give the benchmarks their **own thin container harness**, or commit to a
  **non-published test-support artifact** carrying *both* the harness code and the resources. *Don't*
  attach/publish a core `test-jar` just for benchmarks.
- Benchmarks (server-backed via the PR 6 Testcontainers harness): query throughput & latency, pipeline
  vs. single, parameter binding, result parsing, pool sizing.
- **Per-PR radar with `benchmark-action/github-action-benchmark`** (pin by full **SHA**):
  - **JMH metric direction:** the action currently treats *all* JMH results as **smaller-is-better**,
    which **inverts throughput**. Track metrics as **time/op** (e.g. `AverageTime`, ns/op), **or**
    split results and feed throughput via `customBiggerIsBetter` and latencies via
    `customSmallerIsBetter`. Decide one convention and keep it.
  - **Compare the right artifacts (avoid A-vs-A):** `master` and the PR share the same `-SNAPSHOT`
    GAV, so a naive resolve can pick whichever jar last landed in `~/.m2` (or the published snapshot)
    for *both* sides. Build the **exact base (`master`) and head SHAs**, stage each **jar + POM** under
    **isolated local repos / unique synthetic versions**, **disable remote snapshot resolution**, and
    verify checksums, then run the **same harness** against each.
  - **Same-runner pairing:** GitHub-hosted runners vary, so run **both** sides **on the same runner**
    with **identical conditions** (same harness, **fresh/reset** container + fixtures, alternating/
    A-B-A ordering so warmed caches don't masquerade as deltas). **Aggregate the repetitions locally**
    into **one baseline + one head** result, then feed those to the action via
    **`external-data-json-path`** — `github-action-benchmark` only compares a suite against the
    **immediately preceding stored** suite; it does **not** aggregate A/B/A itself.
  - **Output reality:** the action's PR **comment is a comparison *table*, not a chart**; the
    **interactive chart** is the **trend page on GitHub Pages**.
- **Trust & permissions (important):** do **not** run PR-authored benchmark code with `contents:write`
  or push `gh-pages` from a PR (unsafe; fork-PR tokens also can't post reviews). Use a **read-only**
  PR job that emits a **job-summary** (or a trusted **`workflow_run`** reporter to post the comment),
  and **publish the Pages trend only from `master`/scheduled runs** with scoped write permission and
  **concurrency** protection. The repo has **no `gh-pages` branch or Pages** today — enabling it is
  part of this PR (or explicitly deferred; see the Decisions section). Keep alerts **informational**
  (~1.5–2× threshold, warn-not-block).
- Recipes: `just bench`, `just bench-one <id>`, `just bench-baseline`.

**Why here:** it makes performance regressions visible **during** the heavier Wave 3–5 work, not after.

**Risks & mitigations**
- *Runner variance* → same-runner relative comparison + identical conditions + macro-throughput metric
  + generous threshold + informational gating (escalate to a self-hosted runner only if hard gating is
  later wanted).
- *JMH deps leaking into the artifact/deploy* → the benchmarks project is **standalone and
  non-deployable**; the core build never references it, so the published jar is unaffected by
  construction. *(Prefer verifying this via the core artifact's coordinates + dependency graph + jar
  contents; a naive "byte-identical" diff is unreliable without reproducible-build timestamp config.)*
- *Unsafe PR execution / missing perms* → read-only PR job + `workflow_run`/summary reporting; Pages
  writes only from `master`; action pinned by SHA.

**Validation:** a PR job produces a JMH-vs-`master` comparison (table/summary) with correct metric
direction; an intentional slowdown trips the (informational) alert; the trend chart publishes to Pages
**from `master`**; the core published jar's coordinates/dependencies/contents are unchanged by the
benchmarks project's presence.

---

## Cross-cutting risks & mitigations

| Risk | Mitigation |
| --- | --- |
| The JDK-21 build breaks the publish | PR 5 validates `just verify` on JDK 21 **and** the real **release signing** path (snapshot uses `gpg.skip`, so it doesn't); pins/ignores stay until PR 5 lands. |
| A Java-8 **runtime** regression slips in | The §5d smoke runs the **packaged jar + full runtime graph** on **JDK 8** (real query cycle); Animal Sniffer catches Java-9+ **API** use in our code; Enforcer catches Java-9+ **dependency** bytecode. |
| A guarantee is added but doesn't gate | The smoke job becomes a **required** check; `build`/`format` context names are preserved; branch protection updated atomically with PR 5. |
| Recipes drift from CI | Publish workflows are converted to `just`; CodeQL uses a manual `just` build; **no raw `mvn` remains** in any workflow. |
| Failsafe silently drops IT coverage or clobbers `.exec` | JaCoCo report moved to `verify`; append-enabled or merged exec files. |
| Public API/behavior changes via the test override | Override lives only in the **test** endpoint factory; production `driver()` untouched; zero-arg contract test retained. |
| Benchmark noise / unsafe PR execution / metric inversion | Same-runner relative comparison; read-only PR job + Pages-from-`master`; explicit JMH metric-direction convention; SHA-pinned action. |

## Definition of done for Wave 2

- Build **and** publish run on **JDK 21** (publish workflows converted to `just`; CodeQL on 21 with a
  manual build); the artifact is still **Java-8 bytecode** (`--release 8`) and is **proven to run on
  JDK 8** by a **required** artifact smoke check; Animal Sniffer + Enforcer are green gates.
- `just verify` runs unit + `*IT` against a **Testcontainers-managed** FalkorDB with **zero manual
  setup**, locally and in required CI; **JaCoCo coverage includes IT**; the public `driver()` contract
  is unchanged.
- Every PR gets a **JMH-vs-`master` comparison** (correct metric direction) via a **read-only** job; a
  **trend chart publishes to Pages from `master`**; alerts are informational.
- **Unchanged:** the artifact's **Java-8 runtime contract**, **public API**, and **dependency
  versions** (unpinning is a deliberate later follow-up).
- The durable docs (`copilot-instructions.md`, `CONTRIBUTING.md`, `AGENTS.md`, `Justfile` comments,
  `.github/dependabot.yml` rationale) reflect the **JDK-21-build / `--release 8`** reality and the
  automated Java-8 guardrails.

## Decisions (defaults chosen — confirm or override)

The plan body and Definition of Done assume the **default** in each item below; these are my
recommendations, surfaced so you can override before implementation. Overriding one only adjusts that
item's scope, not the rest of the plan.

1. **Release-signing validation** — **default:** validate signing with **`clean verify` + GPG on
   JDK 21** (never a live deploy — Central is `autoPublish=true`). Override if you prefer a different
   pre-release signing check.
2. **Unpin timing** — **default:** keep the `junit-jupiter`/`equalsverifier` pins (+ Dependabot
   ignores) through Wave 2 and unpin in a dedicated follow-up. Override to unpin inside PR 5.
3. **Test-source level** — **default:** keep **test sources at Java 8** (no `maven.compiler.testRelease`
   bump). Override to allow Java-11+ test source now that the build is JDK 21.
4. **CI server for tests** — **default:** run required CI against a **Testcontainers-managed** server
   (local/CI parity, and it actually exercises the new lifecycle). Override to keep the GitHub
   **service container**.
5. **GitHub Pages for benchmarks** — **default:** enable a `gh-pages` trend site (published only from
   `master`) in PR 7. Override to keep benchmarks **PR-summary-only** until later.
6. **Benchmark gating** — **default:** **informational** (~1.5–2× warn-only) on shared runners.
   Override to invest in a **self-hosted runner** for hard gating.
