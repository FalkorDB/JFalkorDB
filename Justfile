# Justfile — dev-cycle automation for JFalkorDB. Run `just` (or `just --list`) to see recipes.
# Every CI job runs one of these recipes, so they reproduce CI locally. Recipes call ./mvnw
# (the pinned Maven Wrapper). Formatting/quality recipes arrive in a later PR (Spotless).

set shell := ["bash", "-uc"]

# FalkorDB image/container used by the db-* helpers, and the port the tests connect to.
image := "falkordb/falkordb:edge"
container := "jfalkordb-dev"
port := "6379"

# Default: list all recipes.
default:
    @just --list

# Compile + package without tests (no server needed).
build:
    ./mvnw -B -DskipTests -Dgpg.skip=true package

# Run the test suite. Requires a FalkorDB on {{port}} (use `just verify-local`, or `just db-up`).
test:
    ./mvnw -B -Dgpg.skip=true test

# Full build + tests + coverage (JaCoCo) — the aggregate gate CI runs. Requires a server.
verify:
    ./mvnw -B -Dgpg.skip=true verify

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
    just db-up
    trap 'just db-down' EXIT
    just verify
