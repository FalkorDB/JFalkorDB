# Contributing to JFalkorDB

Thanks for contributing! This guide covers the local workflow and the `just` recipes that mirror CI.

## Prerequisites

- **JDK 17** (the PR build JDK). The artifact targets Java 8, but you don't need a JDK 8 for
  day-to-day work.
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
just verify         # same build + tests, against a FalkorDB you already run on localhost:6379
just build          # compile + package, no tests
just test           # tests only (needs a server)
just fmt            # apply palantir-java-format
just fmt-check      # check formatting
just db-up          # start a local FalkorDB container
just db-down        # stop it
```

## Before opening a PR

- Run `just verify-local` and keep documentation in sync with your change.
- Use a **Conventional-Commit** branch and PR title (`feat:`, `fix:`, `build:`, `docs:`, `ci:`,
  `chore:`).
- After opening the PR, **resolve every Copilot and CodeRabbit review thread** (reply + mark
  resolved).
- **Don't merge to `master` yourself** — a maintainer reviews and merges.

See [`.github/copilot-instructions.md`](.github/copilot-instructions.md) for the full engineering
conventions, including the Java-8 / JDK-8-publish constraints.
