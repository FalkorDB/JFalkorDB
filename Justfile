# Justfile — dev-cycle automation for JFalkorDB. Run `just` (or `just --list`) to see recipes.
# Every CI job runs one of these recipes, so they reproduce CI locally. Recipes call ./mvnw
# (the pinned Maven Wrapper). Formatting runs in the off-by-default `quality` profile (Spotless).

set shell := ["bash", "-uc"]

# FalkorDB image/container used by the db-* helpers, and the port the tests connect to.
image := "falkordb/falkordb:edge"
container := "jfalkordb-dev"
port := "6379"

# Default: list all recipes.
default:
    @just --list

# --- Format (no server; runs in the off-by-default `quality` profile) ---

# Apply palantir-java-format + import cleanup across the codebase.
fmt:
    ./mvnw -B -Pquality spotless:apply

# Check formatting without modifying files. This is the CI `format` gate.
fmt-check:
    ./mvnw -B -Pquality spotless:check

# Static analysis (no server): format check + SpotBugs/FindSecBugs + Error Prone, all in the
# off-by-default `quality` profile. This is the CI `lint` gate. `clean` forces a full recompile so
# Error Prone always runs (incremental compilation would otherwise skip up-to-date sources).
lint:
    ./mvnw -B -Pquality -Dgpg.skip=true clean spotless:check test-compile spotbugs:check

# Dependency CVE scan (OWASP dependency-check). Slow + wants an NVD API key: set NVD_API_KEY in the
# environment (the scheduled/manual `audit` workflow provides it). Not part of `verify`.
audit:
    ./mvnw -B -Pquality -DskipTests -Dgpg.skip=true org.owasp:dependency-check-maven:check

# Mutation testing (PITest) — OBSERVABILITY only, never a gate. Runs under the off-by-default
# `quality` profile against the pure-unit packages (server-free, so no FalkorDB needed); HTML/XML
# report in `target/pit-reports`. Run by the scheduled/manual `mutation` workflow.
mutation:
    ./mvnw -B -Pquality -Dgpg.skip=true test-compile org.pitest:pitest-maven:mutationCoverage

# Javadoc gate: strict doclint (`all`) on the public/protected API, failing on ANY warning. This is
# the CI `javadoc` gate. `com.falkordb.impl` is internal and excluded (matching `api-diff`).
javadoc:
    ./mvnw -B -Pquality -Dgpg.skip=true javadoc:javadoc

# Public-API compatibility diff (japicmp): package the jar, then compare it against the last release
# on Maven Central and fail on binary/source-incompatible PUBLIC-API changes (`com.falkordb.impl` is
# internal and excluded). This is the CI `api-diff` gate. A reviewed, intentional break is approved
# with `just api-diff -Dapi.diff.fail=false` (the CI job passes this when the PR carries the
# `breaking-change` label). Not part of `verify`.
api-diff *ARGS:
    ./mvnw -B -Pquality -DskipTests -Dgpg.skip=true package com.github.siom79.japicmp:japicmp-maven-plugin:cmp {{ARGS}}

# Spellcheck the Markdown docs (CI gate). Needs `pyspelling` + `aspell` (see CONTRIBUTING.md).
spellcheck:
    pyspelling -c .github/spellcheck-settings.yml -n Markdown

# Compile + package without tests (no server needed).
build:
    ./mvnw -B -DskipTests -Dgpg.skip=true package

# Run the fast unit tests only (Surefire, *Test — no server needed). System tests are *IT
# and run under Failsafe in `just verify` / `just verify-local`.
test:
    ./mvnw -B -Dgpg.skip=true test

# Full build + tests + coverage (JaCoCo) — the aggregate gate CI runs. Tests start a FalkorDB
# automatically via Testcontainers (Docker required); no manual server needed.
verify:
    ./mvnw -B -Dgpg.skip=true verify

# Prove the PUBLISHED artifact runs on a given JDK runtime: build+install the jar on the build JDK,
# then compile (with `release 8`) + run the standalone runtime smoke against it on the JDK at
# `jdk_home` (pinned to the root project's version). Proves the Java-8 artifact loads and works on
# that runtime. Pass the target JDK home, and run a FalkorDB on {{port}} first. CI provides both;
# locally e.g. `just db-up && just verify-jdk ~/.sdkman/candidates/java/11.0.24-tem`.
verify-jdk jdk_home:
    #!/usr/bin/env bash
    set -euo pipefail
    ./mvnw -B -DskipTests -Dgpg.skip=true install
    version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
    JAVA_HOME="{{jdk_home}}" ./mvnw -B -f smoke-test/pom.xml -Djfalkordb.version="$version" test

# Java-8 runtime smoke — the required `smoke-jdk8` CI context; a thin alias for `verify-jdk`.
# e.g. `just db-up && just verify-jdk8 ~/.sdkman/candidates/java/8.0.492-zulu`.
verify-jdk8 jdk8_home:
    just verify-jdk "{{jdk8_home}}"

# --- Benchmarks (client load-sweep; standalone module in benchmarks/, never shipped) ---

# Build + run the client load-sweep benchmark, writing the latency/throughput/curve JSON for the
# per-PR radar + Pages curve. Starts a Testcontainers FalkorDB by default, or set
# FALKORDB_HOST/FALKORDB_PORT to reuse one — locally e.g.
# `just db-up && FALKORDB_HOST=localhost FALKORDB_PORT=6379 just bench`.
bench:
    #!/usr/bin/env bash
    set -euo pipefail
    ./mvnw -B -q -DskipTests -Dgpg.skip=true install
    version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
    ./mvnw -B -q -f benchmarks/pom.xml -Djfalkordb.version="$version" clean package
    java -jar benchmarks/target/benchmarks.jar

# Run the load sweep for specific concurrency levels only, e.g. `just bench-one "1,8,64"`.
bench-one loads:
    #!/usr/bin/env bash
    set -euo pipefail
    ./mvnw -B -q -DskipTests -Dgpg.skip=true install
    version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
    ./mvnw -B -q -f benchmarks/pom.xml -Djfalkordb.version="$version" clean package
    java -Dbench.loads="{{loads}}" -jar benchmarks/target/benchmarks.jar

# Refresh the local baseline JSON (CI stores the master baseline to gh-pages automatically).
bench-baseline:
    just bench

# --- Publish (run by the snapshot/release CI workflows; not for day-to-day local use) ---

# Pre-fetch dependencies (snapshot workflow warm-up).
fetch-deps:
    ./mvnw -B -q dependency:go-offline

# Set the project version (release workflow, from the git tag). `quote(...)` shell-escapes the value
# so it is always passed to Maven as a single literal argument (defense-in-depth for any caller).
set-version version:
    ./mvnw -B versions:set -DnewVersion={{ quote(version) }} -DgenerateBackupPoms=false

# Print the resolved project version (used by the snapshot-publish -SNAPSHOT guard in CI). The
# `-q -DforceStdout` form yields an [INFO]-free, parseable single line.
project-version:
    @./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version

# Deploy a -SNAPSHOT to Maven Central (no signing, no tests).
deploy-snapshot:
    ./mvnw -B -DskipTests -Dgpg.skip=true deploy

# Build, sign, and deploy a release to Maven Central. Skips tests on purpose: the required PR CI
# already validated the commit (Testcontainers *IT on the pinned FalkorDB digest), so the deploy
# must not re-run tests against a moving image. The Java-8 guardrails (enforcer + animal-sniffer)
# still run, and there is no JaCoCo coverage `check` to trip on the missing test data.
deploy-release:
    ./mvnw -B --no-transfer-progress -Dmaven.test.skip=true clean deploy

# --- Dockerized FalkorDB for local runs (readiness-probed) ---

# Start a FalkorDB container and wait until it answers PING.
db-up:
    #!/usr/bin/env bash
    set -euo pipefail
    docker rm -f {{container}} >/dev/null 2>&1 || true
    docker run -d --name {{container}} -p {{port}}:6379 {{image}} >/dev/null
    for _ in $(seq 1 30); do
        if docker exec {{container}} redis-cli ping 2>/dev/null | grep -q PONG; then exit 0; fi
        sleep 1
    done
    echo "FalkorDB did not become ready in time" >&2
    exit 1

# Stop and remove the FalkorDB container.
db-down:
    -docker rm -f {{container}}

# Manage Docker and run the full `verify` locally against that server, reusing it via the
# FALKORDB_HOST/PORT override so the tests don't also start a Testcontainers server; tears the
# server down even on failure. Handy when Testcontainers can't reach your local Docker.
verify-local:
    #!/usr/bin/env bash
    set -euo pipefail
    trap 'just db-down' EXIT
    just db-up
    FALKORDB_HOST=localhost FALKORDB_PORT={{port}} just verify
