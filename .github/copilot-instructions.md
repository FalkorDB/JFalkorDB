# Copilot / AI agent instructions for JFalkorDB

Guidance for GitHub Copilot and other AI agents (and human contributors) working in this repository.
It encodes the team's engineering conventions so changes land clean on the first try.

JFalkorDB is the official **Java client for FalkorDB** (a Redis-module graph database). It targets
**Java 8** and is published to Maven Central. The transport is Jedis (blocking), pooled via
commons-pool2.

## Golden rule: drive everything through `just`

For any action CI performs (build, test, coverage, format, …), run the **exact same `just` recipe CI
uses** for the standard PR gates rather than an ad-hoc `mvn …` (the JDK-8 dependency-verification
build and the publish workflows are documented exceptions). If a check needs changing, update the
`just` recipe **and** the CI workflow together so they stay identical. Run `just --list` to see every
recipe; recipes call the pinned Maven Wrapper (`./mvnw`).

| Recipe | Purpose |
| --- | --- |
| `just verify` | Build + tests + coverage (the CI `build` gate). Needs a FalkorDB on `:6379`. |
| `just verify-local` | Spin up a Dockerized FalkorDB, run `verify`, tear it down. |
| `just build` / `just test` | Compile-only / tests-only. |
| `just fmt` / `just fmt-check` | Apply / check palantir-java-format (runs in the `-Pquality` profile). |
| `just spellcheck` | Spellcheck the Markdown docs (the CI `spellcheck` gate). |
| `just db-up` / `just db-down` | Manage a local FalkorDB container. |

Tests connect to `localhost:6379`; prefer `just verify-local`, which manages Docker for you.

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

## Backward compatibility & the JDK-8 publish (important)

- The published artifact must keep running on **Java 8** (`maven.compiler.source/target = 8`). The PR
  build runs on **JDK 17**; the snapshot/release publish builds on **JDK 8**.
- Because `mvn deploy` on JDK 8 also **compiles test sources**, every test-scope dependency must be
  Java-8-compatible. This is why **`junit-jupiter` is pinned to 5.x** and **`equalsverifier` to 3.x**
  (Dependabot ignores their semver-major bumps). Verify dependency bumps with a JDK-8
  `mvn -DskipTests -Dgpg.skip=true package`, not just JDK 17.
- Any tool that needs Java 11+ (e.g. Spotless / palantir-java-format) must live in the
  **off-by-default `quality` Maven profile**, never on the default lifecycle, so it never runs on the
  JDK-8 deploy.

## Releasing

Cut a release by publishing a **GitHub Release tagged `vX.Y.Z`** on `master` (`version-and-release.yml`
derives the version from the tag and deploys to Maven Central with `autoPublish`). Afterward, bump the
pom to the next `-SNAPSHOT`. Use semver — a user-facing behavior change is at least a **minor** bump
(the project is pre-1.0).
