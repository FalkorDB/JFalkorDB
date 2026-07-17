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

# Spellcheck the Markdown docs (CI gate). Needs `pyspelling` + `aspell` (see CONTRIBUTING.md).
spellcheck:
    pyspelling -c .github/spellcheck-settings.yml -n Markdown

# Compile + package without tests (no server needed).
build:
    ./mvnw -B -DskipTests -Dgpg.skip=true package

# Run the test suite. Requires a FalkorDB on {{port}} (use `just verify-local`, or `just db-up`).
test:
    ./mvnw -B -Dgpg.skip=true test

# Full build + tests + coverage (JaCoCo) — the aggregate gate CI runs. Requires a server.
verify:
    ./mvnw -B -Dgpg.skip=true verify

# Prove the PUBLISHED artifact runs on a Java-8 runtime: build+install the jar on the build JDK,
# then compile+run the standalone JDK-8 runtime smoke against it (pinned to the root project's
# version). Pass the JDK-8 home, and run a FalkorDB on {{port}} first. CI provides both; locally
# e.g. `just db-up && just verify-jdk8 ~/.sdkman/candidates/java/8.0.492-zulu`.
verify-jdk8 jdk8_home:
    #!/usr/bin/env bash
    set -euo pipefail
    ./mvnw -B -DskipTests -Dgpg.skip=true install
    version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
    JAVA_HOME="{{jdk8_home}}" ./mvnw -B -f smoke-test/pom.xml -Djfalkordb.version="$version" test

# --- Publish (run by the snapshot/release CI workflows; not for day-to-day local use) ---

# Pre-fetch dependencies (snapshot workflow warm-up).
fetch-deps:
    ./mvnw -B -q dependency:go-offline

# Set the project version (release workflow, from the git tag).
set-version version:
    ./mvnw -B versions:set -DnewVersion={{version}} -DgenerateBackupPoms=false

# Deploy a -SNAPSHOT to Maven Central (no signing, no tests).
deploy-snapshot:
    ./mvnw -B -DskipTests -Dgpg.skip=true deploy

# Build, sign, test, and deploy a release to Maven Central.
deploy-release:
    ./mvnw -B --no-transfer-progress clean deploy

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

# Manage Docker and run the full `verify` locally; tears the server down even on failure.
verify-local:
    #!/usr/bin/env bash
    set -euo pipefail
    trap 'just db-down' EXIT
    just db-up
    just verify
