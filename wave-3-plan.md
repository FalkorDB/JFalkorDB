# Wave 3 — Correctness & gates (scaffolding plan)

> **Status: plan for review — no implementation until approved.** This is the detailed plan for
> **Wave 3** of the overall JFalkorDB modernization effort; it implements that effort's
> recommended-order **steps 7–9**. Like the Wave 1 and Wave 2 plans, this doc is a temporary planning
> artifact and will be closed/deleted once Wave 3 lands; it is **not** referenced from any permanent
> project file.
>
> The **default branch is `master`** (not `main`); every "baseline" below means `master`.
>
> **Prerequisite (met):** Wave 2 has landed — JDK 21 + `--release 8` (#302), Testcontainers + `*IT`
> split (#312/#313), and the per-PR benchmark radar (#314/#315). Wave 3's newer quality tools and
> API-diff plugin need that JDK-21 build, so it is unblocked.

## Scope

Wave 3 turns the pivoted build into a **correctness-and-safety gate**. It has three themes, in the
recommended execution order:

1. **Parameter-path hardening (§6)** — make the query-parameter path actually injection-safe *before*
   any doc advertises it as such. Security-correctness.
2. **Remaining quality gates (D4/D4b)** — add SpotBugs/FindSecBugs + Error Prone + OWASP
   dependency-check (in the off-by-default `quality` profile / dedicated CI jobs) and **retire
   DeepSource** in favor of in-build `just` gates.
3. **Releases (§4)** — a conventional-commit **PR-title** gate + a **japicmp API-diff** gate, the §4
   **workflow safeguards**, then **release-please** + `CHANGELOG.md`.

| PR | Title | What | Risk |
| --- | --- | --- | --- |
| **8** | `fix!: harden the Cypher parameter path (escaping + key validation + type whitelist) + tests` | Correct Cypher string encoder (escape `\`, server-verified escapes, **not** `\uXXXX`), parameter-**key** validation (+ `CYPHER`-prefix guard), a wire-accurate value-**type whitelist**, adversarial + **jqwik** + **server round-trip `*IT`**; **then** update README/Javadoc/`llms.txt`. `!` = breaking behavioral change | Med |
| **9** | `build: quality gates — SpotBugs/FindSecBugs + Error Prone + OWASP; retire DeepSource` | Extend the `quality` profile + `just lint`/`just audit` + CI jobs; delete `.deepsource.toml` | Med |
| **10a** | `ci: conventional-commit PR-title gate + japicmp API-diff gate` | Squash-only PR-title lint (explicit types incl. `bench`) + public-API break gate (baseline `0.9.0`, `impl` excluded) | Low/Med |
| **10b** | `ci: release workflow safeguards (snapshot -SNAPSHOT guard + deterministic deploy + trigger)` | `snapshot.yml` `-SNAPSHOT`-only guard; pin/skip-tests on release deploy; App/PAT release trigger | Med |
| **10c** | `release: enable release-please + CHANGELOG` | `release-please` (manifest, bootstrap `0.9.0` → `release-as 0.10.0`, App/PAT) owns version + `CHANGELOG.md`; handle 3 POMs | Med |

### Explicitly NOT in Wave 3 (and where it lives)
- **Broader property/parameterized tests, jqwik beyond parameter escaping, the JDK & FalkorDB
  matrices, PITest** — **Wave 4** (only the parameter-escaping property tests land here, in PR 8).
- **API ergonomics** — config builder (timeouts + pool sizing), nullability annotations, compiled
  `examples/`, Javadoc **Pages** publishing — **Wave 4**. PR 8 documents the safe parameter path in
  README/Javadoc/`llms.txt` **only**; the compiled `examples/` module is Wave 4.
- **MRJAR overlay, Loom virtual-thread work, `CompletableFuture` facade** — **Wave 5**.
- **Spotless** already shipped in Wave 1's `quality` profile; Wave 3 **extends** that same profile
  (it does not re-introduce formatting).

### Ordering & dependencies
- **PR 8 first (or at least before any "safe to parameterize" doc):** it is a security-correctness fix
  *and* the prerequisite for the injection-safety claim. The `llms.txt` caveat (line ~31: "not yet
  guaranteed injection-safe") and README/Javadoc wording are only relaxed **inside PR 8**, after the
  code is correct.
- **PR 9 is independent** and can overlap PR 8; it only depends on the **JDK-21 build** (Wave 2, done)
  because the current SpotBugs/Error Prone/OWASP versions need it.
- **Releases are strictly ordered: 10a → 10b → 10c.** Enabling **release-please (10c) before** the
  workflow safeguards (10b) risks an unpublished or nondeterministic first automated release, so 10c
  lands **last**. 10a/10b can overlap PR 8/PR 9.

---

## PR 8 — Harden the Cypher parameter path (§6)

**Problem (today).** In `com.falkordb.impl.Utils`:
- `quoteString` escapes **only** `"` (`str.replace("\"", "\\\"")`) — it does **not** escape backslash
  `\` or control characters, so a value like `foo\` becomes `"foo\"` and the trailing `\"` escapes the
  **closing** quote → the rest of the value is parsed as Cypher (**injection**). *(The `ESCAPE_CHYPER`
  commons-text translator declared in the static block is **dead code** — `quoteString` never uses
  it.)*
- `prepareQuery` interpolates parameter **keys** without validation (`sb.append(key)`), so a crafted key is
  injected verbatim into the `CYPHER k=v …` header.
- `valueToString` has **no type whitelist**: anything not String/Character/array/List falls through to
  `value.toString()`, so a `Map`, POJO, or `Double` (`NaN`/`Infinity`) is emitted raw.

> **Guiding rule (from review): don't invent the grammar — verify it.** The exact set of escapes and
> the key/number rules FalkorDB accepts must be **pinned by a server round-trip `*IT`** against the
> Wave-2 `falkordb/falkordb@sha256:…` digest (not by a locally-assumed grammar). The encoder below is
> the *starting hypothesis*; PR 8 confirms/derives the real rules empirically and encodes them.

**What**
1. **Correct Cypher string encoder** (replaces `quoteString`; delete the unused `ESCAPE_CHYPER`):
   - Escape **backslash first** (`\` → `\\`), **then** the double quote (`"` → `\"`).
   - Use only escapes FalkorDB actually decodes (verify against the server; v4.x is understood to
     support `\a \b \f \n \r \t \v` + slash/quote). **Do not** emit `\uXXXX` — FalkorDB may preserve
     unknown `\u` sequences **literally**, corrupting the value. Emit all other (non-control) code
     points **raw UTF-8**.
   - **Reject** `NUL` (unrepresentable) and **unpaired surrogates** with a descriptive exception.
2. **Parameter-key validation + safe emission:** validate each key as a bare identifier
   (`^[A-Za-z_][A-Za-z0-9_]*$`) **and** handle a server-parser subtlety — FalkorDB strips a
   case-insensitive leading `CYPHER` token, so keys like `CYPHER`/`CYPHERx` can be misparsed even
   though they match the regex. Mitigate by **backtick-quoting** the emitted parameter name (the
   approach `falkordb-py` uses) **or** additionally rejecting a `(?i)^CYPHER` prefix — decide via a
   server round-trip test. Reject empty/backtick/NUL keys.
3. **Value-type whitelist** in `valueToString`, matched to the **wire format** (verified by round-trip):
   - `null` → `null`; `Boolean` → `true`/`false`; `String`/`Character` → encoded string literal.
   - **Integers** are signed **64-bit** on the server: allow `Byte`/`Short`/`Integer`/`Long`; for
     `BigInteger` **require it fits in `long`** (else reject). Decimals are **`double`**: allow
     `Float`/`Double` (reject non-finite `NaN`/`Infinity`), and **reject `BigDecimal`** (or explicitly
     document the lossy `double` conversion) rather than emitting an unparseable literal. Format
     **locale-independently** (`Locale.ROOT`).
   - **Arrays** (`Object[]` **and** primitive arrays) and **`List`s** of allowed values → `[…]`
     (recursive); **Cypher maps** (`Map` with **String** keys) → `{key: value, …}` with the same key
     rules + recursive value encoding (reject non-String map keys). **Guard against cyclic
     references** (bounded depth / identity set) so a self-referential collection can't infinite-loop.
   - **Reject** every other type with a clear exception instead of a silent `toString()`.
4. **Tests** (this PR carries them — "non-trivial changes ship with tests"):
   - **Adversarial unit tests** (`*Test`, Surefire — pure, no server): values with `"`, `\`, trailing
     `\`, control chars, Unicode incl. astral/surrogate pairs, `NUL`; breakout attempts; invalid keys
     (spaces, operators, empty, leading digit); **`CYPHER`/`CYPHERx` keys asserted per the chosen
     policy** (rejected if we reject the prefix; round-tripped if we backtick-quote); rejected values
     (POJO, non-finite double, out-of-`long` `BigInteger`, non-String map key, **cyclic `List`, `Map`,
     and `Object[]`**).
   - **jqwik property tests** (Java-8 compatible; add `net.jqwik:jqwik` **test** scope): for any
     generated valid string, the encoded literal opens/closes with an **unescaped** `"` and contains
     no other unescaped `"`.
   - **Server round-trip `*IT`** (Failsafe, pinned FalkorDB): `RETURN $p` (and list/map/number-boundary
     params) returns each input under **normalized semantic equality** — the driver canonicalizes on
     the way back (`Integer`→`Long`, `Character`→`String`, arrays→`List`, `Float`→`Double`), so the
     test compares against the **expected canonical form**, not `.equals` on the original object. This
     is the check that validates the *real* grammar rather than an invented one.
5. **Then document** (only after the code is correct, and **scoped precisely** — the safe claim covers
   parameter **values**, *not* raw query text, dynamic **identifiers**, or **procedure names**):
   update README ("always parameterize values / never concatenate user input into Cypher"), Javadoc on
   the parameterized overloads across **`Graph`, `GraphPipeline`, and `GraphTransaction`**, and
   **remove the `llms.txt` injection-safety caveat** (line ~31). The compiled `examples/` module stays
   **Wave 4**.

**Why here.** Security-correctness fix and prerequisite for any injection-safety claim; land it (with
its tests) before README/`llms.txt`/Javadoc advertise the parameter path as safe.

**Compatibility & housekeeping.**
- Encoder + key/type validation are **behavioral**: inputs that were previously accepted (and silently
  corrupted) now throw. That is the intended fix, but it is a **breaking behavioral change**, so PR 8
  is titled with a `!` (see the semver policy in PR 10a) and documents the stricter contract.
- Keep `prepareProcedure` consistent with the same encoder and add a **regression test** for it.
- If deleting `ESCAPE_CHYPER` leaves **`commons-text`** unused, remove that runtime dependency (verify
  no other usage first) — it also trims the shipped dependency graph the Enforcer checks.

**Key snippet** (starting hypothesis only — the exact escape set, control-char handling, and surrogate
policy are pinned by the round-trip `*IT`; the loop below shows only the settled cases):
```java
// escape order matters: backslash first, then quote; NO \\uXXXX (FalkorDB may keep it literal).
// Control chars beyond the ones below, and unpaired-surrogate rejection, are added per the *IT.
private static String quoteString(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
            case '\\': sb.append("\\\\"); break;
            case '"':  sb.append("\\\""); break;
            case '\n': sb.append("\\n");  break;
            case '\r': sb.append("\\r");  break;
            case '\t': sb.append("\\t");  break;
            case '\b': sb.append("\\b");  break;
            case '\f': sb.append("\\f");  break;
            case '\0': throw new IllegalArgumentException("NUL is not representable in a Cypher string");
            default:   sb.append(c); // else raw; the *IT decides remaining controls + surrogate checks
        }
    }
    return sb.append('"').toString();
}

private static final java.util.regex.Pattern PARAM_KEY = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
```

---

## PR 9 — Quality gates (SpotBugs/FindSecBugs + Error Prone + OWASP; retire DeepSource)

**What**
1. **Extend the off-by-default `quality` profile** (Java-11+ tooling stays out of the aggregate
   lifecycle, preserving the Java-8 guarantee — same rule Spotless already follows):
   - **SpotBugs + FindSecBugs:** `spotbugs-maven-plugin` with the `findsecbugs-plugin` dependency;
     bind `check` to `verify` *within the profile*; fail on high-priority findings; seed an
     `spotbugs-exclude.xml` for known false positives.
   - **Error Prone:** via `maven-compiler-plugin` `annotationProcessorPaths` +
     `-XDcompilePolicy=simple -Xplugin:ErrorProne …` (needs the **JDK-21 build**, Wave 2 — done).
     On JDK 16+ Error Prone needs the `--add-exports/--add-opens jdk.compiler` flags and a **forked**
     compiler; keep all of that **inside the `quality` profile** so the normal `--release 8` build is
     untouched. Start with the default bug-pattern set at `ERROR`, allow-listing as needed to stay
     green.
   - **OWASP dependency-check:** `dependency-check-maven` (`check`), fail above a defined **CVSS
     threshold**. It is **slow and needs an NVD API key** (+ a cached data dir and an update-failure
     policy and a suppression file), so it lives in a **dedicated scheduled + manual workflow of its
     own** — *not* added as a `schedule:` to `maven.yml` (which would put every `maven.yml` job on a
     timer) and *not* in the inner loop.
2. **`just` recipes = CI:** add `just lint` (Spotless-check + SpotBugs/FindSecBugs + Error Prone via
   `-Pquality`) and `just audit` (OWASP); add a **`lint`** job to `maven.yml`, and put the **`audit`**
   job in its **own scheduled + `workflow_dispatch` workflow** (not a `schedule:` on `maven.yml`). Both
   call exactly those recipes.
3. **Retire DeepSource (D4b):** delete `.deepsource.toml`, **disable the external DeepSource
   integration/app** (only after the in-build replacement is green so there's no coverage gap), remove
   any DeepSource badge/CI hook, and update `.github/copilot-instructions.md`/`CONTRIBUTING.md` to say
   analysis now lives in the reproducible in-build `just` gates.

**Why.** High-signal bug + security + compile-time checks that are locally reproducible (the `just`=CI
rule); their current versions need the JDK-21 build Wave 2 delivered. *(D4 / D4b.)*

---

## PR 10 — Releases (§4), in three sequenced steps

> **Order is a safety property:** land the **gates (10a)** and **safeguards (10b)** *before*
> automating releases (**10c**), so the first automated release can't publish nondeterministically or
> fail to publish.

### PR 10a — PR-title gate + API-diff gate
- **Conventional-commit PR-title lint** (pinned action, e.g. `amannn/action-semantic-pull-request`).
  Because a title gate only constrains history if the **squash subject** is the PR title, this PR also
  **enforces squash-only merges** and sets the repository **`squash_merge_commit_title = PR_TITLE`**
  setting (the action validates the PR title; the repo setting is what makes the squash commit *use*
  that title) — otherwise rebase/merge commits bypass it. **Configure the allowed types explicitly** —
  the repo uses `bench:` and will use `release:`/`build:`/`ci:`, none of which are in the action's
  default set, and must include **`chore`** (the release-please PRs are titled `chore(main): release …`).
  Map each type to a release-please changelog section (or use `perf(bench):`/`chore(release):`).
- **japicmp API-diff gate:** `japicmp-maven-plugin` comparing the built jar against the **last
  released** artifact, configured to **fail** on binary/source-incompatible **public** API changes.
  Specifics this PR must nail down: **baseline = `0.9.0`** (the Central artifact; fail loudly if the
  baseline can't be resolved rather than passing vacuously); **exclude internal packages**
  (`com.falkordb.impl.*`) from "public API"; and a **reviewed break-approval** path (an explicit
  allow/override that still publishes the diff report) so an intentional `feat!:`/`fix!:` break can
  merge. Keep japicmp **unbound** from the normal `verify`/`deploy` lifecycle (its own `just api-diff`
  + CI job), so it never blocks a release deploy.

### PR 10b — Release-workflow safeguards
- **`snapshot.yml` `-SNAPSHOT`-only guard:** deploy only when `help:evaluate
  -Dexpression=project.version` ends in `-SNAPSHOT`, so a release commit on `master` can't trigger an
  unsigned/duplicate snapshot deploy.
- **Deterministic release deploy (chosen design):** `version-and-release.yml` `clean deploy` re-runs
  tests against the server image (currently `edge`) and mutates the POM with `versions:set`. **Adopt
  skip-tests-on-deploy** (`-Dmaven.test.skip=true`) — the required PR CI (Testcontainers `*IT` on the
  pinned digest) has already validated the commit, so the deploy shouldn't re-run tests against a
  moving image at all; *(the pinned-digest approach is the fallback if we ever want tests on deploy)*.
  Provide a **`workflow_dispatch` with a required `tag` input** so a publish can be re-run manually for
  a given tag (don't gate the deploy job *only* on `release_created`, which can't be retried).
- **Release trigger via App/PAT (not just an alternative):** a Release **and** the release/snapshot
  **PRs** created by the default `GITHUB_TOKEN` trigger **no** workflows — so `release: published`
  won't fire **and** required `build`/`lint`/`api-diff`/title checks would be **absent** on the
  release-please PR, blocking its merge. Wave 3 therefore standardizes on a **GitHub App installation
  token (or PAT)** for release-please (pinned action SHA, explicit `contents`/`pull-requests`
  permissions). Inline deployment alone does **not** solve bot-PR CI.

### PR 10c — Enable release-please
- Add `release-please` (`googleapis/release-please-action`, pinned SHA) driven by an **App/PAT** (from
  10b). Use **manifest configuration** so the first automated release bootstraps correctly against the
  existing history: set the **previous version `0.9.0`** + **bootstrap-sha** (the `v0.9.0` release
  commit), a **one-time `release-as: 0.10.0`** (master is `0.10.0-SNAPSHOT`; the `v0.9.0` tag's POM
  says `0.8.0-SNAPSHOT`, so the migration must be explicit, not inferred), the existing **`vX.Y.Z` tag
  format**, and **`"bump-minor-pre-major": true`** (so a below-1.0 breaking change bumps `0.x` → `0.(x+1)`
  rather than to `1.0.0`; master §6 semver). Remove the `release-as` override after the first release.
- **Multi-module versions:** `release-type: maven` recursively rewrites **all three** POMs
  (`pom.xml`, `smoke-test/pom.xml`, `benchmarks/pom.xml` — the latter two are independent
  `1.0-SNAPSHOT`) and won't touch their `jfalkordb.version` **properties**. **Dry-run this** and choose:
  the `java` strategy with explicit `extra-files`, or deliberately accept synchronized harness versions
  **and** update their `jfalkordb.version` properties (+ any README/`llms.txt` version fields).
- Once release-please owns the version, **remove `set-version`** from the release workflow; have the
  deploy **check out the created tag** and assert **tag == `v${project.version}`** (the tag is
  `vX.Y.Z`, the POM version is `X.Y.Z`). Merging the Release PR tags +
  creates the GitHub Release, which (via the 10b App/PAT trigger) runs the existing Maven Central
  publish and opens the follow-up `-SNAPSHOT`-bump PR.
- **Land last** — after 10a/10b are green on `master`.

**Why (whole section).** Replaces manual releases with conventional-commit-driven, changelog-backed
releases while preserving the JDK-8 Maven Central publish, and makes a public-API break an explicit
decision. *(D5: release-please.)*

---

## Sequencing & definition of done (per PR)
- Each PR is **green before the next** (`just verify` / `just verify-local` + `just spellcheck` on any
  Markdown), on a Conventional-Commit branch with a Conventional-Commit **PR title**.
- **New required checks** introduced here (`lint`, `api-diff`, PR-title) follow the same discipline
  Wave 2 used: land the job → confirm its exact status-check context → add it to branch protection
  **after** it exists. Don't rename the existing `build`/`format` contexts. Note the **PR-title** check
  can only be observed on a **PR** (not a `master` push), so confirm its context on the *next* PR and
  make sure the workflow listens to `pull_request` `opened`/`edited`/`synchronize`.
- Every Copilot **and** CodeRabbit review thread is replied-to **and** resolved before merge; **no
  self-merge to `master`** — a maintainer approves and merges.
- This planning doc is deleted/closed once Wave 3 lands.
