# Wave 1 Scaffolding Plan — JFalkorDB Modernization

This is a **plan for review** (no functional code yet). It details the first wave of
[`project-modernizations.md`](project-modernizations.md) — the **Foundation** — so the maintainer can
review the approach before any implementation begins. It reflects the recorded decisions (D1–D7) and
the maintainer's Wave-1 preferences: **whole Wave 1** delivered as small PRs, **palantir-java-format**,
**split CI jobs**, and Spotless rolled out as **config first → dedicated reformat PR → then blocking**.

## What Wave 1 delivers

The substrate every later wave plugs into, with **zero functional/code changes**:

1. **Maven Wrapper + `Justfile` + CI-as-`just`** — reproducible builds and one source of truth for
   every check (`just` recipe = CI command).
2. **AI standards + `CONTRIBUTING.md` + `.editorconfig`** — engineering conventions that make future
   PRs (human or AI) land clean.
3. **Spotless (palantir-java-format)** — config + recipes, then a one-time whole-repo reformat, then a
   blocking format gate.

## Non-goals (explicitly deferred to later waves)

To keep Wave 1 low-risk and reviewable, it does **not** include: the JDK-21 `--release 8` move
(Wave 2), Testcontainers (Wave 2), other static analysis — Error Prone / SpotBugs / OWASP (Wave 3),
release-please (Wave 3), JMH benchmarks (Wave 2), parameter hardening (Wave 3), or any Loom/MRJAR work
(Wave 5). No dependency version changes. No changes to published artifact behavior.

## Constraints honored (and why they matter)

- **The JDK-8 publish path stays untouched.** Wave 1 keeps the current setup: PR build on **JDK 17**,
  publish (`snapshot.yml` / `version-and-release.yml`) on **JDK 8**. This is critical because
  `palantir-java-format` requires **Java 11+** to run. To guarantee it never executes on the JDK-8
  deploy, **Spotless lives in a `quality` Maven profile that is off by default** — only `just`/CI
  activate it with `-Pquality` on JDK 17. `mvn deploy` on JDK 8 never passes `-Pquality`, so Spotless
  is inert there. (This is the same "quality profile" discipline the master plan mandates, and exactly
  the class of breakage that hit us with junit 6 / equalsverifier 4.)
- **No functional code changes.** The only Java change in Wave 1 is the mechanical reformat (PR 4).
- **Reversible.** Every PR is small and independently revertible.
- **`just` = CI (PR checks in Wave 1).** Each **PR** CI job runs a single `just` recipe (the source of
  truth, reproducible locally). Wave 1 converts the **PR build** (`maven.yml`); the **publish**
  workflows (`snapshot.yml`, `version-and-release.yml`) keep their current commands and are converted
  to `just` recipes in **Wave 3** (with release-please) — deliberately not churning the fragile JDK-8
  publish path right before the JDK-21 move (Wave 2).

## PR breakdown

Wave 1 ships as **4 small PRs**, each green before the next:

| PR | Title | Scope | Risk |
| --- | --- | --- | --- |
| **1** | `build: add Maven Wrapper + Justfile; run CI via just` | `mvnw`, `Justfile` (build/test/verify + db helpers), refactor `maven.yml` to call `just verify` | Low |
| **2** | `docs: contributor + AI engineering guides` | `CONTRIBUTING.md`, `.github/copilot-instructions.md`, `AGENTS.md`, `CLAUDE.md`, `llms.txt`, `.coderabbit.yaml`, `.editorconfig` | Low |
| **3** | `build: add Spotless (palantir) config + recipes` | Spotless in the `quality` profile; `just fmt` / `fmt-check`; **no** blocking gate yet | Low |
| **4** | `style: apply palantir-java-format repo-wide + enforce` | mechanical whole-repo reformat + add the blocking `format` CI job | Low (large but mechanical diff) |

---

## PR 1 — Maven Wrapper + Justfile + CI-as-`just`

**What:** add the Maven Wrapper, a `Justfile` with a working subset of recipes, and refactor
`maven.yml` so the build job runs a `just` recipe.

**Why:**
- *Maven Wrapper* pins the exact Maven version so builds are reproducible on every machine and in CI
  (removes "works on my Maven" drift; the master plan requires plugin/tool pinning).
- *Justfile* is the single entry point and the source of truth the master plan is built around — CI
  runs the same recipe a contributor runs.

**How:**
- Generate the wrapper with a **pinned** plugin + Maven version:
  `mvn -N org.apache.maven.plugins:maven-wrapper-plugin:3.3.2:wrapper -Dmaven=3.9.9` → commits `mvnw`,
  `mvnw.cmd`, `.mvn/wrapper/`. All recipes call `./mvnw`.
- Proposed **end-of-Wave-1** `Justfile` (recipes are added across PRs 1 and 3; `fmt*` land in PR 3):

```makefile
set shell := ["bash", "-uc"]

image := "falkordb/falkordb:edge"
container := "jfalkordb-dev"
port := "6379"

# Default: list recipes.
default:
    @just --list

# --- Static, no server (Spotless lands in PR 3, inside the `quality` profile) ---
fmt:        ; ./mvnw -q -Pquality spotless:apply
fmt-check:  ; ./mvnw -q -Pquality spotless:check

# --- Build & test ---
# Tests currently connect to a FalkorDB on localhost:6379 (Testcontainers arrives in Wave 2);
# use `just verify-local` to have Docker managed for you, or start your own server.
build:      ; ./mvnw -q -DskipTests -Dgpg.skip=true package
test:       ; ./mvnw -q -Dgpg.skip=true test
# Functional aggregate: compile + test + JaCoCo. It does NOT run Spotless — formatting has no
# lifecycle binding (see PR 3), so `spotless:check` runs only via `just fmt-check` / the `format` CI
# job. (The master plan's fuller `verify` adds the `it`/`coverage` profiles in Waves 2–3.)
verify:     ; ./mvnw -q -Dgpg.skip=true verify

# --- Dockerized FalkorDB for local runs (readiness-probed, always torn down) ---
db-up:
    #!/usr/bin/env bash
    set -euo pipefail
    docker rm -f {{container}} >/dev/null 2>&1 || true
    docker run -d --name {{container}} -p {{port}}:6379 {{image}} >/dev/null
    for i in $(seq 1 30); do docker exec {{container}} redis-cli ping 2>/dev/null | grep -q PONG && exit 0; sleep 1; done
    echo "FalkorDB did not become ready" >&2; exit 1
db-down:    ; -docker rm -f {{container}}
# Full local run; tears the server down even if tests fail.
verify-local:
    #!/usr/bin/env bash
    set -euo pipefail
    just db-up
    trap 'just db-down' EXIT
    just verify
```

- `-Dgpg.skip=true` on every non-release recipe: `maven-gpg-plugin:sign` is bound to the `verify`
  phase, so local/PR runs must skip it (only the release deploy signs). Matches the current
  `snapshot.yml`.
- **CI refactor (`maven.yml`)** — one job that runs the recipe, keeping the current JDK-17 build, the
  FalkorDB **service container**, Codecov upload and dependency submission unchanged:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    services:
      falkordb: { image: falkordb/falkordb:edge, ports: ["6379:6379"] }
    steps:
      - uses: actions/checkout@…  # v7, persist-credentials: false
      - uses: actions/setup-java@…  # v5, java-version 17, temurin, cache maven
      - uses: extractions/setup-just@<pinned-sha>  # v3, with: just-version: <pinned>
      - run: just verify
      - name: Upload coverage reports to Codecov      # unchanged
        uses: codecov/codecov-action@…
      - name: Maven Dependency Tree Dependency Submission   # unchanged
        uses: advanced-security/maven-dependency-submission-action@…
```

**Options considered:**
- *Task runner:* **`just`** (chosen — team standard, mirrors falkordb-rs, cross-platform) vs. `make`
  (tab-sensitive, less ergonomic) vs. shell scripts (no discoverability/`--list`) vs. Maven-only (no
  single local/CI entry point).
- *Wrapper vs. system Maven:* **wrapper** (chosen — reproducible, pins version) vs. relying on the
  runner's Maven (drift risk; the master plan explicitly calls for the wrapper).
- *CI shape now:* keep the **single build job** in PR 1 (only the build+test gate exists yet); the
  **`format`** job is added in PR 4, and `quality`/`it`/`coverage` jobs arrive in later waves — so the
  "split into focused `just`-recipe jobs" grows as gates appear, rather than creating empty jobs now.

**Validation:** CI green; `just verify` locally (with a FalkorDB on 6379) reproduces the CI build.

---

## PR 2 — AI standards + CONTRIBUTING + .editorconfig

**What:** the engineering-conventions docs. No build changes.

**Files & why:**
- **`.github/copilot-instructions.md`** — the playbook: the `just` = CI golden rule; definition of
  done (design → rubber-duck → code+tests+docs → `just verify` → PR → resolve **all** Copilot +
  CodeRabbit threads → **never self-merge to `master`**); the current **JDK-8 publish constraint**
  (until Wave 2); the dependency pins (`junit-jupiter` 5.x, `equalsverifier` 3.x); and a pointer to the
  recorded decisions in `project-modernizations.md`.
- **`AGENTS.md`** and **`CLAUDE.md`** — thin files that point to `copilot-instructions.md` so every
  agent tool converges on one source.
- **`llms.txt`** — hand-written machine-readable summary: one-line overview, install snippet
  (`com.falkordb:jfalkordb:0.9.0`), core idioms (build a driver, `graph("g")`, run a `query`, iterate
  the `ResultSet`), and an explicit **pitfall** that the query-parameter path is **not yet guaranteed
  injection-safe** (hardened in Wave 3 / §6) — so it must not advertise safety. **No auto-generated API
  list yet** (that generator is deferred; it needs the API to settle).
- **`.coderabbit.yaml`** — minimal CodeRabbit config (enable reviews; leave room to silence
  false-positive checks later), so its reviews stay high-signal.
- **`.editorconfig`** — baseline whitespace rules aligned to the chosen formatter, e.g.:

```ini
root = true
[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
# palantir-java-format uses 4-space indentation
[*.java]
indent_style = space
indent_size = 4
# pom.xml is tab-indented today; keep it to avoid churn (Spotless doesn't touch XML)
[*.xml]
indent_style = tab
[*.{yml,yaml,json,md}]
indent_style = space
indent_size = 2
```

**Options considered:**
- *Which AI files:* ship `copilot-instructions.md` as the canonical source + thin `AGENTS.md`/`CLAUDE.md`
  pointers (chosen — one source, many tools) vs. duplicating content per tool (drifts).
- *`llms.txt` API list:* **hand-write now, automate later** (chosen — the public API is still churning;
  a generator is Wave-4+ work) vs. build the generator now (premature).

**Validation:** spellcheck workflow passes on the new Markdown (add any new proper nouns to
`.github/wordlist.txt`); links resolve.

---

## PR 3 — Spotless (palantir-java-format): config + recipes, not yet enforced

**What:** add Spotless configured with `palantir-java-format` **inside a new `quality` profile**, plus
the `just fmt` / `just fmt-check` recipes. **No blocking gate and no reformat yet.**

**Why palantir (D3):** it formats JFalkorDB's fluent/builder-heavy code and long chains more readably
than google-java-format, and the codebase is already largely **4-space** indented, so palantir's
4-space style yields a **smaller** reformat diff than google's 2-space.

**Why a `quality` profile:** so a **Java-11+** formatter can never run on the **JDK-8** deploy (see
Constraints). This profile is created here and extended with Error Prone/SpotBugs/OWASP in Wave 3.

**How (proposed pom sketch):**

```xml
<profiles>
  <profile>
    <id>quality</id>
    <build>
      <plugins>
        <plugin>
          <groupId>com.diffplug.spotless</groupId>
          <artifactId>spotless-maven-plugin</artifactId>
          <version><!-- pinned --></version>
          <configuration>
            <java>
              <palantirJavaFormat/>
              <removeUnusedImports/>
              <importOrder/>
            </java>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

**Why it can't self-fail:** the Spotless config has **no `<executions>` binding**, so `spotless:check`
is never attached to a lifecycle phase — `mvn verify` (and the JDK-8 `mvn deploy`) never run it. It
executes *only* when explicitly invoked by `just fmt-check` (and the `format` CI job added in PR 4). So
PR 3 ships config + recipes with the tree still unformatted and stays green; the maintainer can run
`just fmt-check` to preview the diff. Enforcement flips on in PR 4.

**Options considered:** enforce format-check as blocking immediately (rejected — self-fails until
reformat) vs. warn-only first (viable, but the maintainer chose the cleaner **config → dedicated
reformat → blocking** path) vs. config+reformat+enforce in one PR (rejected — mixes a huge mechanical
diff with the config change, hurting review).

**Validation:** `just fmt-check` runs and reports the pending diff; `just fmt` applies it locally; the
build is otherwise unchanged and green.

---

## PR 4 — Whole-repo reformat + enforce

**What:** run `just fmt` (palantir reformat across the repo) as **one mechanical commit**, and add the
blocking **`format`** CI job that runs `just fmt-check`.

**Why here / how:** with config already in place (PR 3), this isolates the large-but-mechanical diff
into its own reviewable PR, then makes formatting a required gate going forward — including **adding
`format` to `master` branch protection** (today only `build` is required) so the gate actually blocks
merges.

```yaml
  format:                      # added to the PR-checks workflow
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@…   # persist-credentials: false
      - uses: actions/setup-java@… # java-version 17
      - uses: extractions/setup-just@<pinned-sha>  # v3
      - run: just fmt-check
```

**Risks & mitigations:**
- *Churns `git blame`* → do it as a single, reviewer-obvious "reformat only" commit; add it to
  `.git-blame-ignore-revs` so blame skips it.
- *Conflicts with open branches* → land Wave 1 while few PRs are open (the master plan sequences the
  reformat early for exactly this reason); rebase in-flight branches with `just fmt` right after.
- *Formatter needs Java 11+* → runs only in the JDK-17 `format` job / `quality` profile, never on the
  JDK-8 deploy.

**Validation:** the `format` job is green on the reformatted tree; a deliberately mis-formatted change
fails `just fmt-check` (confirming the gate bites).

---

## Cross-cutting risks & mitigations

| Risk | Mitigation |
| --- | --- |
| A Java-11+ tool runs on the JDK-8 deploy and breaks publish | Spotless lives in the off-by-default `quality` profile; the deploy never passes `-Pquality`. |
| Contributors don't have `just` | CI installs it via an action; `CONTRIBUTING.md` documents `brew`/`cargo`/package installs. |
| Reformat conflicts / blame noise | Single mechanical commit + `.git-blame-ignore-revs`; land early. |
| `just verify` needs a server locally | `just verify-local` / `db-up` manage Docker; CI uses the existing service container (Testcontainers replaces it in Wave 2). |
| Recipes drift from CI | Every CI job runs a `just` recipe verbatim (the golden rule); no raw `mvn` in workflows. |

## Definition of done for Wave 1

- `./mvnw` present; `just --list` shows the recipes; **the PR build runs only via `just` recipes**
  (publish workflows are converted in Wave 3).
- `just verify-local` (Docker-managed) or `just verify` (with a FalkorDB on 6379) reproduces the CI
  build locally.
- Contributor + AI docs merged; spellcheck green.
- Repo is palantir-formatted and the **`format` gate is blocking**.
- **Unchanged:** published-artifact behavior, dependency versions, and the JDK-17-build / JDK-8-publish
  topology (the JDK-21 move is Wave 2).

## Open questions for the reviewer

1. **Reformat timing** — OK to land PR 4 immediately after PR 3, or hold it until in-flight branches
   (e.g. #162, #69) merge?
2. **Pins** — I'll pin Maven **3.9.9** (matches current local Maven), the wrapper plugin, and
   `extractions/setup-just` (SHA + `just-version`); say if you prefer other versions.
