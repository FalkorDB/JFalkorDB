# Copilot / AI agent instructions for JFalkorDB

Guidance for GitHub Copilot and other AI agents (and human contributors) working in this repository.
It encodes the team's engineering conventions so changes land clean on the first try.

JFalkorDB is the official **Java client for FalkorDB** (a Redis-module graph database). It targets
**Java 8** and is published to Maven Central. The transport is Jedis (blocking), pooled via
commons-pool2.

## Golden rule: drive everything through `just`

For any action CI performs (build, test, coverage, format, …), run the **exact same `just` recipe CI
uses** rather than an ad-hoc `mvn …` — this now includes the publish workflows, which call
`just deploy-snapshot` / `just deploy-release`. If a check needs changing, update the
`just` recipe **and** the CI workflow together so they stay identical. Run `just --list` to see every
recipe; recipes call the pinned Maven Wrapper (`./mvnw`).

| Recipe | Purpose |
| --- | --- |
| `just verify` | Build + tests + coverage (the CI `build` gate). Auto-starts FalkorDB via Testcontainers (Docker). |
| `just verify-local` | Run `verify` against a `just db-up` server (reused via `FALKORDB_HOST/PORT`), then tear it down — handy when Testcontainers can't reach your Docker. |
| `just build` / `just test` | Compile-only / fast **unit** tests only (`*Test`, no server). |
| `just fmt` / `just fmt-check` | Apply / check palantir-java-format (runs in the `-Pquality` profile). |
| `just lint` | Static analysis (the CI `lint` gate): format check + **SpotBugs/FindSecBugs** + **Error Prone**, all in the off-by-default `-Pquality` profile. |
| `just audit` | OWASP dependency-check CVE scan of the shipped deps. Slow + needs `NVD_API_KEY`; run by the scheduled/manual **`audit`** workflow, not on every PR. |
| `just mutation` | PITest mutation testing of the pure-unit packages (observability, **not** a gate); run by the scheduled/manual **`mutation`** workflow, in the off-by-default `-Pquality` profile. |
| `just pin-check` | Virtual-thread **pinning check** (Wave 5 / Project Loom): warms the pool, then runs a virtual-thread workload under JFR and fails if any `jdk.VirtualThreadPinned` event runs through `com.falkordb` (i.e. we pin carriers). Needs JDK 21+; run by the scheduled/manual **`pin-check`** workflow, not on every PR. |
| `just api-diff` | Public-API compatibility diff vs the last release on Maven Central (japicmp, the CI **`api-diff`** gate): fails on binary/source-incompatible changes to the public/protected API (`com.falkordb.impl` excluded), in the off-by-default `-Pquality` profile. Approve a reviewed break with the `breaking-change` PR label. |
| `just javadoc` | Javadoc gate (the CI **`javadoc`** gate): strict `doclint=all` + `failOnWarnings` on the public/protected API (`com.falkordb.impl` excluded), in the off-by-default `-Pquality` profile. |
| `just spellcheck` | Spellcheck the Markdown docs (the CI `spellcheck` gate). |
| `just examples` | Compile the standalone `examples/` module (runnable public-API examples) with `--release 8` against the built jar — the CI **`examples`** gate. Run one via the Exec plugin (see `examples/README.md`). |
| `just db-up` / `just db-down` | Manage a local FalkorDB container. |
| `just bench` / `just bench-one <loads>` | Client load-sweep benchmark — client latency (total − server) vs throughput across concurrency levels; feeds the `master` gh-pages trend radar + Pages curve. |
| `just bench-compare <base_ref>` | Same-machine A/B: benchmark HEAD vs `<base_ref>` back-to-back on one machine and compare (the CI **`benchmark-pr`** job). Cancels hosted-runner speed variance, unlike the cross-runner stored-baseline radar. Needs a clean tree + a server (like `just bench`). |

Server-backed **system tests are `*IT`** (run by Failsafe in `verify`) and start a FalkorDB
automatically via **Testcontainers** (Docker required) — no manual server setup; set both
`FALKORDB_HOST`/`FALKORDB_PORT` (or use `just verify-local`) to reuse an existing server instead.
Pure **unit tests are `*Test`** (Surefire, no server) and run in `just test`. Name new tests
accordingly, and use the `TestServer` helper for server access.

## Definition of done

1. **Design first** for non-trivial work, and **rubber-duck review** the design before coding.
2. **Implement** the change with **code + tests + docs** (Javadoc / an example where it helps).
3. **Validate locally via `just`** — `just verify` (or `just verify-local`) green, and **`just
   spellcheck`** whenever you touch Markdown (new technical terms go in `.github/wordlist.txt`).
4. Open a PR on a **Conventional-Commit** branch (`feat:` / `fix:` / `build:` / `docs:` / `ci:` /
   `chore:` / `bench:`); the PR **title** must be a Conventional Commit (enforced by the **`PR
   title`** check).
5. **Resolve every AI review thread** — Copilot **and** CodeRabbit — reply *and* mark resolved —
   before merge.
6. **Never self-merge to `master`.** Open the PR, get it green, and wait for a maintainer to approve
   and merge.

## Backward compatibility & the Java-8 guarantee (important)

- The published artifact must keep running on **Java 8**, but the build now runs on **JDK 21**
  (PR, snapshot, and release) compiling with **`maven.compiler.release = 8`** — it emits Java-8
  bytecode (class-file 52) while compiling against the real Java-8 API.
- Four automated guardrails keep the Java-8 promise (all reachable via `just`):
  - **`--release 8`** — our source compiles against the Java-8 API and emits class-file 52.
  - **Animal Sniffer** (`java18` signature, at `verify`) — our main code calls only the Java-8 API.
  - **Enforcer `enforceBytecodeVersion`** (`maxJdkVersion=1.8`, test scope excluded, at `validate`) —
    every **shipped** dependency is Java-8 bytecode.
  - **`just verify-jdk8`** (the `smoke-jdk8` CI job) — runs the packaged jar + full runtime graph on a
    real **JDK 8** against FalkorDB via the no-arg `driver()`, proving Java-8 *runtime* compatibility.
    The generalized **`just verify-jdk <home>`** runs that same smoke on any JDK, and the **`smoke-jdk`**
    CI matrix exercises **JDK 11/17/21** too (8 stays in `smoke-jdk8` for its branch-protection context).
- **`junit-jupiter` stays on 5.x** and **`equalsverifier` on 3.x** (Dependabot ignores their
  semver-major bumps). The JDK-21 build *could* now compile their 6.x/4.x, but raising them is a
  deliberate later change, not automatic — so keep the pins for now.
- Any Java-11+ tool (e.g. Spotless / palantir-java-format, SpotBugs/FindSecBugs, Error Prone, OWASP
  dependency-check, japicmp) still lives in the **off-by-default `quality` Maven profile**, kept as a
  separate explicit gate off the aggregate lifecycle (`just lint` / `just audit` / `just api-diff`).
  Static analysis is in-build and reproducible; there is no external DeepSource integration.
- The **`api-diff`** gate (japicmp, `just api-diff`) is a **PR-only** check that diffs the built jar
  against the last release on Maven Central and fails on public/protected-API breaks
  (`com.falkordb.impl` is internal and excluded); approve a reviewed, intentional break with the
  `breaking-change` PR label. A user-facing break is at least a **minor** bump (the project is
  pre-1.0).

## Releasing

Releases are automated by **release-please** (`release-please.yml`, run under a GitHub **App** token —
see `docs/release-please-setup.md`). Conventional-commit merges to `master` accrue into a **Release
PR** that bumps the root `pom.xml` `<version>` and `CHANGELOG.md`. **Merging that Release PR** tags
`vX.Y.Z`, creates the GitHub Release, and (via `version-and-release.yml`) deploys to Maven Central;
release-please then opens the next `-SNAPSHOT` PR. `bump-minor-pre-major` means a pre-1.0
breaking change bumps `0.x` → `0.(x+1)` (semver §6). After a release publishes, bump the
`api.diff.baseline` property to the just-released version (a manual step for now, so the **`api-diff`**
gate compares against the new release; a safe post-publish automation is a possible later enhancement).
`version-and-release.yml` also has a `workflow_dispatch(tag)` recovery path to re-deploy a specific
tag manually.
