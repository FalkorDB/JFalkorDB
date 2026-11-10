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

# Full public-API surface gate (no server): checks that api/jfalkordb.api still lists every
# public/protected member of the exported types, the ones inherited from Jedis included, plus the
# public members of the Jedis types our signatures return (Jedis, Response). japicmp only compares our
# own classes, so a Jedis bump can change that surface without it noticing; this can't miss it. Also
# runs in `just test` / `just verify` (it is ApiSurfaceTest); the CI `api-diff` workflow runs it as a
# separate step. A mismatch is fixed by `just api-surface-update` and committing, never by a label.
# `clean` first, so the class of a deleted source can't keep the old surface alive.
api-surface:
    ./mvnw -B -Dgpg.skip=true -Dtest=ApiSurfaceTest clean test

# Regenerate api/jfalkordb.api after a reviewed API change (ours or a dependency's); commit the result
# so the change is reviewed in the PR diff. `clean` first, so classes of deleted sources can't linger.
api-surface-update:
    ./mvnw -B -Dgpg.skip=true -Dtest=ApiSurfaceTest -Dapi.surface.update=true clean test

# Fail when api/jfalkordb.api breaks code compiled against <base_ref>'s, even when a dependency bump is
# the cause: a removed or changed line (a member, supertype or declaration callers rely on), or a new
# abstract method on an existing exported interface or abstract class (implementations break; japicmp fails on
# that too). Other additions and deprecations pass. Approve a reviewed break by passing `allow` as the second argument,
# which the CI `api-diff` job does when the PR has the `breaking-change` label (the same approval as
# japicmp, so the title also needs a `!`). Compares committed snapshots only; `just api-surface` checks
# the snapshot matches the code. e.g. `just api-surface-breaks origin/master`.
api-surface-breaks base_ref approval="":
    #!/usr/bin/env bash
    set -euo pipefail
    if ! git cat-file -e "{{base_ref}}:api/jfalkordb.api" 2>/dev/null; then
        echo "{{base_ref}} has no api/jfalkordb.api yet; nothing to compare against."
        exit 0
    fi
    base="$(mktemp)"
    trap 'rm -f "$base"' EXIT
    git show "{{base_ref}}:api/jfalkordb.api" > "$base"
    ./mvnw -B -q -Dgpg.skip=true -DskipTests test-compile
    java -cp target/test-classes:target/classes com.falkordb.ApiSurface --breaks "$base" api/jfalkordb.api {{approval}}

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

# Compile the runnable examples (examples/, standalone, release 8) against the freshly-built jfalkordb
# jar — the CI `examples` gate; keeps the documented examples valid as the API evolves. Run one with
# the Exec plugin (needs `just db-up`) — pass the root version, since examples/pom.xml pins a default
# that can lag behind it; see examples/README.md for the copy-pasteable form.
examples:
    #!/usr/bin/env bash
    set -euo pipefail
    ./mvnw -B -q -DskipTests -Dgpg.skip=true install
    version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
    ./mvnw -B -f examples/pom.xml -Djfalkordb.version="$version" clean compile

# Virtual-thread pinning check (Wave 5 / Project Loom; standalone module in pin-check/, never shipped).
# Warms the connection pool, then runs a virtual-thread query workload over it under a JFR recording,
# with -Djdk.tracePinnedThreads=full marking which frames hold the monitor on each pinned stack. FAILS if
# one of them is in our com.falkordb.impl client internals (i.e. we pin carriers); pins whose monitor a
# dependency holds (commons-pool2 2.13+ returning a connection under contention) are reported, not failed.
# Needs JDK 21-23 for the attribution. Starts a Testcontainers FalkorDB by default, or set FALKORDB_HOST/FALKORDB_PORT to reuse
# one — e.g. `just db-up && FALKORDB_HOST=localhost FALKORDB_PORT=6379 just pin-check`. The scheduled
# `pin-check` CI job runs this.
pin-check:
    #!/usr/bin/env bash
    set -euo pipefail
    ./mvnw -B -q -DskipTests -Dgpg.skip=true install
    version="$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)"
    ./mvnw -B -f pin-check/pom.xml -Djfalkordb.version="$version" test

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

# Same-machine A/B: benchmark HEAD vs <base_ref> back-to-back on THIS machine, then compare — so
# absolute runner speed cancels out (unlike the stored-baseline radar, which compares across
# different hosted runners and therefore emits false ~2x alerts). This is exactly what the
# `benchmark-pr` CI job runs. Needs a clean working tree (it checks out <base_ref> and back) and a
# server like the other bench recipes, e.g.
# `just db-up && FALKORDB_HOST=localhost FALKORDB_PORT=6379 just bench-compare origin/master`.
bench-compare base_ref threshold="1.25" loads="1,2,4,8,16,32,64":
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -n "$(git status --porcelain)" ]; then
        echo "bench-compare needs a clean working tree (it checks out {{base_ref}} and back); commit or stash first." >&2
        exit 1
    fi
    orig_ref="$(git symbolic-ref -q --short HEAD || git rev-parse HEAD)"
    base_sha="$(git rev-parse --short "{{base_ref}}")"
    out="$(mktemp -d)"
    # Always restore the original ref and remove the temp dir, even if a bench/checkout fails midway.
    cleanup() { git checkout -q "$orig_ref" 2>/dev/null || true; rm -rf "$out"; }
    trap cleanup EXIT
    echo "==> benchmarking HEAD ($orig_ref)"
    just bench-one "{{loads}}"
    cp benchmarks/target/bench-latency.json "$out/head-latency.json"
    cp benchmarks/target/bench-throughput.json "$out/head-throughput.json"
    echo "==> benchmarking base ({{base_ref}} = $base_sha)"
    git checkout -q "{{base_ref}}"
    just bench-one "{{loads}}"
    cp benchmarks/target/bench-latency.json "$out/base-latency.json"
    cp benchmarks/target/bench-throughput.json "$out/base-throughput.json"
    git checkout -q "$orig_ref"  # back on HEAD so the comparator below is the current one
    echo "==> comparing head ($orig_ref) vs base ({{base_ref}}), threshold {{threshold}}x"
    mkdir -p benchmarks/target
    python3 benchmarks/compare_bench.py \
        "$out/base-latency.json" "$out/head-latency.json" \
        "$out/base-throughput.json" "$out/head-throughput.json" \
        --threshold "{{threshold}}" --base-label "{{base_ref}}" --head-label "$orig_ref" \
        | tee benchmarks/target/bench-compare.md

# --- Publish (run by the snapshot/release CI workflows; not for day-to-day local use) ---

# Pre-fetch every plugin and dependency (the CI warm-up), so a transient Maven Central error fails here
# rather than midway through a build. That covers the root build, the help plugin the recipes read the
# version with, and the plugins smoke-test/ and examples/ pin; their dependency on the jar this build
# installs isn't fetched, since it may not be on Central yet. The retries in .mvn/maven.config cover I/O
# errors, but not a 404 from a Central mirror that briefly lacks files, so the whole warm-up is retried.
# A retry passes -U: without it Maven trusts the "not found" it just recorded and doesn't ask Central
# again. Arguments go to every Maven call as given, e.g. `just fetch-deps -Pquality` for the lint plugins.
[positional-arguments]
fetch-deps *args:
    #!/usr/bin/env bash
    set -euo pipefail
    warm() {
        local args=(${maven_args[@]+"${maven_args[@]}"} "$@")
        ./mvnw -B -q ${args[@]+"${args[@]}"} dependency:go-offline || return 1
        ./mvnw -B -q ${args[@]+"${args[@]}"} -DforceStdout help:evaluate -Dexpression=project.version >/dev/null || return 1
        for pom in smoke-test/pom.xml examples/pom.xml; do
            ./mvnw -B -q ${args[@]+"${args[@]}"} -f "$pom" dependency:resolve-plugins || return 1
        done
    }
    maven_args=("$@")
    for attempt in 1 2 3; do
        if [ "$attempt" -eq 1 ]; then
            if warm; then exit 0; fi
        elif warm -U; then
            exit 0
        fi
        if [ "$attempt" -lt 3 ]; then
            echo "Dependency resolution failed (attempt $attempt of 3); retrying in $((attempt * 20))s" >&2
            sleep $((attempt * 20))
        fi
    done
    exit 1

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
