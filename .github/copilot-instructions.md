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
| `just build` / `just test` | Compile-only / tests-only. |
| `just fmt` / `just fmt-check` | Apply / check palantir-java-format (runs in the `-Pquality` profile). |
| `just spellcheck` | Spellcheck the Markdown docs (the CI `spellcheck` gate). |
| `just db-up` / `just db-down` | Manage a local FalkorDB container. |

Tests start a FalkorDB automatically via **Testcontainers** (Docker required) — no manual server
setup. Set both `FALKORDB_HOST`/`FALKORDB_PORT` (or use `just verify-local`) to reuse an existing
server instead.

## Definition of done

1. **Design first** for non-trivial work, and **rubber-duck review** the design before coding.
2. **Implement** the change with **code + tests + docs** (Javadoc / an example where it helps).
3. **Validate locally via `just`** — `just verify` (or `just verify-local`) green, and **`just
   spellcheck`** whenever you touch Markdown (new technical terms go in `.github/wordlist.txt`).
4. Open a PR on a **Conventional-Commit** branch (`feat:` / `fix:` / `build:` / `docs:` / `ci:` /
   `chore:`); the PR **title** must be a Conventional Commit.
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
- **`junit-jupiter` stays on 5.x** and **`equalsverifier` on 3.x** (Dependabot ignores their
  semver-major bumps). The JDK-21 build *could* now compile their 6.x/4.x, but raising them is a
  deliberate later change, not automatic — so keep the pins for now.
- Any Java-11+ tool (e.g. Spotless / palantir-java-format) still lives in the **off-by-default
  `quality` Maven profile**, kept as a separate explicit gate off the aggregate lifecycle.

## Releasing

Cut a release by publishing a **GitHub Release tagged `vX.Y.Z`** on `master` (`version-and-release.yml`
derives the version from the tag and deploys to Maven Central with `autoPublish`). Afterward, bump the
pom to the next `-SNAPSHOT`. Use semver — a user-facing behavior change is at least a **minor** bump
(the project is pre-1.0).
