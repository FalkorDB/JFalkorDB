# Contributing to JFalkorDB

Thanks for contributing! This guide covers the local workflow and the `just` recipes that mirror CI.

## Prerequisites

- **JDK 21** (the build JDK for PRs, snapshots, and releases). The artifact still targets Java 8
  (compiled with `--release 8`); for day-to-day work you don't need a JDK 8 — only the
  `just verify-jdk8` runtime smoke uses one (`just verify-jdk <home>` runs the same smoke on any JDK;
  CI covers 8/11/17/21 via `smoke-jdk8` plus the `smoke-jdk` 11/17/21 matrix).
- **[`just`](https://github.com/casey/just)** — `brew install just`, `cargo install just`, or your
  package manager.
- **Docker** — for a local FalkorDB (the tests need a running server).
- Maven is provided by the wrapper (`./mvnw`); you don't need to install Maven.

## Development loop

This repo ships a [`just`](https://github.com/casey/just) file that mirrors the commands CI runs — it
is the recommended entry point. List every recipe:

```bash
just            # or: just --list
```

Common recipes:

```bash
just verify-local   # spin up FalkorDB (Docker), run the full build + tests, then tear it down
just verify         # same build + tests; auto-starts FalkorDB via Testcontainers (Docker)
just build          # compile + package, no tests
just test           # fast unit tests only (no server); system tests are *IT, run by just verify
just fmt            # apply palantir-java-format
just fmt-check      # check formatting
just lint           # static analysis: format check + SpotBugs/FindSecBugs + Error Prone (-Pquality)
just audit          # OWASP dependency-check CVE scan (slow; needs NVD_API_KEY; run by the audit workflow)
just api-diff       # public-API compatibility diff vs the last release (japicmp; the CI api-diff gate)
just api-surface    # full public-API surface vs the committed api/jfalkordb.api (inherited Jedis members included)
just api-surface-update  # regenerate api/jfalkordb.api after a reviewed API change
just api-surface-breaks origin/master  # removed/changed snapshot lines vs master (need the breaking-change label)
just db-up          # start a local FalkorDB container
just db-down        # stop it
just bench          # client load-sweep benchmark (client latency vs throughput; feeds the radar)
```

## Before opening a PR

- Run `just verify-local` and keep documentation in sync with your change.
- If you changed the public API, run `just api-surface-update` and commit `api/jfalkordb.api` with it.
  The snapshot lists every public member, including the ones `GraphPipeline` and `GraphTransaction`
  inherit from Jedis, so the PR diff shows exactly what callers gain or lose. A Jedis bump that
  changes that surface fails `build` and `api-diff` until the regenerated snapshot is pushed to its
  branch and reviewed; that is intended, so a dependency can't change our API unnoticed. If the
  snapshot diff removes or changes a line, callers can break: the `api-diff` check then also needs the
  `breaking-change` label and a `!` title.
- Use a **Conventional-Commit** branch and PR title (`feat:`, `fix:`, `perf:`, `build:`, `docs:`, `ci:`,
  `chore:`, `bench:`, `test:`); the PR title is checked by the **`PR title`** workflow.
  The type decides the release: `feat:` → minor, `fix:`/`perf:` → patch, a breaking change
  (`!` in the title or a `BREAKING CHANGE:` footer; the `breaking-change` label only approves the
  `api-diff` gate and requires a `!`-marked title) → minor before 1.0;
  the other types cut no release on their own.
- After opening the PR, **resolve every Copilot and CodeRabbit review thread** (reply + mark
  resolved).
- **Don't merge to `master` yourself** — a maintainer reviews and merges.

See [`.github/copilot-instructions.md`](.github/copilot-instructions.md) for the full engineering
conventions, including the Java-8 guarantee (JDK-21 build with `--release 8` plus the Java-8
guardrails).
