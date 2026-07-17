# Contributing to JFalkorDB

Thanks for contributing! This guide covers the local workflow and the `just` recipes that mirror CI.

## Prerequisites

- **JDK 21** (the build JDK for PRs, snapshots, and releases). The artifact still targets Java 8
  (compiled with `--release 8`); for day-to-day work you don't need a JDK 8 — only the
  `just verify-jdk8` runtime smoke uses one.
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
just db-up          # start a local FalkorDB container
just db-down        # stop it
just bench          # run the JMH benchmarks (writes JSON for the per-PR-vs-master radar)
```

## Before opening a PR

- Run `just verify-local` and keep documentation in sync with your change.
- Use a **Conventional-Commit** branch and PR title (`feat:`, `fix:`, `build:`, `docs:`, `ci:`,
  `chore:`).
- After opening the PR, **resolve every Copilot and CodeRabbit review thread** (reply + mark
  resolved).
- **Don't merge to `master` yourself** — a maintainer reviews and merges.

See [`.github/copilot-instructions.md`](.github/copilot-instructions.md) for the full engineering
conventions, including the Java-8 guarantee (JDK-21 build with `--release 8` plus the Java-8
guardrails).
