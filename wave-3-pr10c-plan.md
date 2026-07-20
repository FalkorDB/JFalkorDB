# Wave 3 — PR 10c implementation plan: enable release-please

> Temporary planning artifact. Reviewed, **not merged** — deleted once PR 10c lands. Do not
> reference it from permanent project files.
>
> This revision incorporates a rubber-duck review that corrected several release-please specifics
> (verified against release-please **v17.6.0** docs and `release-please-action` **v5.0.0**).

## Goal

Replace the manual "publish a GitHub Release by hand" flow with **conventional-commit-driven,
changelog-backed releases** via [release-please], preserving the JDK-8 Maven Central publish
(`version-and-release.yml`, hardened in 10b) and the Wave-3 gates.

Lands **last**, after 10a (#319) and 10b (#325) — both merged.

## Target flow

1. Conventional-commit PRs squash-merge to `master` (the 10a `PR title` gate keeps subjects conventional).
2. **release-please** (own workflow, run under a **GitHub App token**) maintains a **Release PR** that
   bumps the root `pom.xml` `<version>` and updates `CHANGELOG.md`.
3. Merging the Release PR **creates tag `vX.Y.Z` + the GitHub Release**. Created with the **App token**
   (not the default `GITHUB_TOKEN`), it **fires** `version-and-release.yml`, which builds + signs +
   deploys to Central (the 10b hardened deploy).
4. release-please then opens a follow-up **SNAPSHOT** PR (`autorelease: snapshot`) bumping the POM to
   the next `-SNAPSHOT`.

## Why a GitHub App token is mandatory

A Release/PR created with the workflow's default `GITHUB_TOKEN` **does not trigger further workflows**
(GitHub loop-prevention). That would (a) stop `version-and-release.yml` (`on: release: published`)
from ever firing for a release-please release → nothing publishes, and (b) leave the **required
checks** absent on the release-please **Release PR** → it can't satisfy branch protection to merge.

**Confirmed:** a Release created via a GitHub **App installation token** *does* trigger
`release: published`. App perms needed: **Contents: write** + **Pull requests: write** (covers tag,
release, PR, labels, comments; there is no separate `releases` permission). Install on
`FalkorDB/JFalkorDB` only; no Administration/Actions scope.

### Auth wiring — needs your setup

- Action `actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1 # v3.2.0` mints a
  short-lived installation token from an **App ID + private key**.
- **Secrets to create:** `RELEASE_PLEASE_APP_ID` + `RELEASE_PLEASE_APP_PRIVATE_KEY`.
  > ⚠️ You answered with a single `RELEASE_PLEASE_TOKEN`, but a GitHub **App** needs the **two**
  > secrets above. If you'd instead store one **fine-grained PAT** as `RELEASE_PLEASE_TOKEN` (Contents
  > + PRs read/write) and skip the App, we drop `create-github-app-token` and pass
  > `token: ${{ secrets.RELEASE_PLEASE_TOKEN }}` directly. **Please confirm App vs PAT** — the plan
  > assumes the App (your selection).

## Files in this PR

| File | Change |
| --- | --- |
| `release-please-config.json` | **New.** Manifest config (`release-type: java` + `pom` extra-file). |
| `.release-please-manifest.json` | **New.** `{ ".": "0.9.0" }`. |
| `.github/workflows/release-please.yml` | **New.** Mints the App token; runs `release-please-action` (v5, pinned). |
| `.github/workflows/version-and-release.yml` | Automatic path: assert `tag == v${project.version}` (no `set-version`). `workflow_dispatch` recovery path keeps `set-version`. |
| `.github/copilot-instructions.md`, `CONTRIBUTING.md` | Document the release-please flow + the manual `api.diff.baseline` bump. |
| `.github/wordlist.txt` | `release-please`, `changelog`, etc. |

## `release-please-config.json` (corrected)

```json
{
  "$schema": "https://raw.githubusercontent.com/googleapis/release-please/v17.6.0/schemas/config.json",
  "always-update": true,
  "packages": {
    ".": {
      "release-type": "java",
      "bump-minor-pre-major": true,
      "extra-files": [
        { "type": "pom", "path": "pom.xml" }
      ]
    }
  }
}
```

- **`release-type: java` + a `pom` `extra-files` updater.** Per the v17.6.0 docs, the `java` strategy
  *"does not update any files on its own"* — it manages the release/SNAPSHOT **lifecycle** but relies
  on `extra-files`. The `pom` updater rewrites `/project/version` (namespace-independent
  `local-name()` match; no xpath/namespace config needed). We do **not** use `maven` because it
  *"updates all found `pom.xml` files (recursively)"* — that would bump the independent `smoke-test/`
  and `benchmarks/` harnesses (they stay `1.0-SNAPSHOT`; only the root POM is listed).
- **`bump-minor-pre-major: true`** — a below-1.0 breaking change bumps `0.x` → `0.(x+1)`.
- **`always-update: true`** — refresh the Release PR even when only "hidden" (non-release) commits
  land, so it stays current under the "require branches up to date" branch protection (else a
  maintainer must click "Update branch").
- **`.release-please-manifest.json` = `{ ".": "0.9.0" }`.**
- **No `release-as`, no `bootstrap-sha`.** Post-0.9.0 history contains a `fix!` (PR 8) and no other
  minor/major triggers, so with `bump-minor-pre-major` the **natural first release is exactly
  `0.10.0`** (verified: `git log v0.9.0..master` → one `fix!`, the rest `build`/`ci`/`test`/`chore`/`docs`/`bench`).
  The existing `v0.9.0` tag/release makes release-please anchor at that commit, so `bootstrap-sha`
  would be ignored, and dropping config `release-as` avoids a hazardous "remember to remove it later"
  follow-up. *(If a future release ever needs forcing, use a one-time `Release-As: x.y.z` commit
  footer, never config.)*

### ⚠️ First-run SNAPSHOT-lifecycle hazard (must clear via dry-run)

The `java` strategy runs its **SNAPSHOT lifecycle before** normal version calculation. Our manifest
says `0.9.0` (a *release*, not a snapshot), and the manual bump to `0.10.0-SNAPSHOT` (merged PR #294)
was titled `chore: bump development version to 0.10.0-SNAPSHOT after 0.9.0 release` — **not**
the release-please recognized `chore(master): release 0.10.0-SNAPSHOT` form. So release-please may not
know a post-0.9.0 snapshot bump already happened and could make its **first** PR a
**`0.9.1-SNAPSHOT`** snapshot bump (rewriting the POM *downward*) instead of the intended **0.10.0
Release PR**.

**Hard gate:** the dry-run **must** show a pending **0.10.0 release** (not an `autorelease: snapshot`
PR). If it shows a snapshot, apply one remediation and re-run the dry-run until it's a 0.10.0 release:

1. **Seed a recognized marker** — a tiny commit/PR whose squash title is exactly
   `chore(master): release 0.10.0-SNAPSHOT`, so release-please records that the 0.10.0-SNAPSHOT bump
   already occurred (keeps the normal snapshot lifecycle going forward — **preferred**, since 10b
   deliberately keeps snapshot publishing).
2. **`"skip-snapshot": true`** — disable snapshot PRs entirely. Simpler, but master then sits at the
   *release* version between releases and **no `-SNAPSHOT` is published** between releases (the 10b
   guard would skip them). Only choose this if we accept dropping inter-release snapshots.
- **Post-release SNAPSHOT** bump will be `0.10.1-SNAPSHOT` (patch), created as a separate
  `autorelease: snapshot` PR.

## `release-please.yml` (shape)

```yaml
name: release-please
on:
  push:
    branches: [ master ]
permissions:
  contents: read            # write scope comes from the App token, not GITHUB_TOKEN
jobs:
  release-please:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/create-github-app-token@bcd2ba4… # v3.2.0
        id: app-token
        with:
          app-id: ${{ secrets.RELEASE_PLEASE_APP_ID }}
          private-key: ${{ secrets.RELEASE_PLEASE_APP_PRIVATE_KEY }}
      - uses: googleapis/release-please-action@45996ed1f6d02564a971a2fa1b5860e934307cf7 # v5.0.0
        with:
          token: ${{ steps.app-token.outputs.token }}
          config-file: release-please-config.json
          manifest-file: .release-please-manifest.json
```

## `version-and-release.yml` changes (resolve the 10b/set-version overlap)

- **Automatic release** (`release: published`): the tag's commit already has the correct POM version
  (the release-please `pom` updater set it in the Release PR). So **remove `set-version`** and instead
  **assert** `"v$(just project-version)" == "$tag"`; fail loudly on mismatch (a bad/hand-made tag
  can't publish a mismatched artifact).
- **`workflow_dispatch` recovery**: **keeps `just set-version "$version"`** — this path may re-deploy
  an arbitrary (possibly predating release-please) tag whose POM predates the version, so it deliberately
  forces the POM. (Resolves the plan-v1 contradiction: `set-version` stays, but only on the manual
  path; the automatic path asserts instead of mutating.)

## `api.diff.baseline` — do NOT let release-please manage it

The first plan proposed auto-bumping `api.diff.baseline` via release-please. **Rejected** — it
deadlocks the 10a gate:

- Setting the baseline to `0.10.0` inside the **Release PR** points japicmp at an artifact that **does
  not exist on Central yet**; with our `ignoreMissingOldVersion=false` (fail-loud) the `api-diff`
  check **fails to resolve the baseline** — it is *not* a passing no-op.
- The `java` strategy also re-applies `extra-files` in the **SNAPSHOT PR**, which would set the
  baseline to `0.10.1-SNAPSHOT` (not a released artifact).

**Decision for 10c:** keep `api.diff.baseline` a **documented manual release step** (already written
into `copilot-instructions.md` → Releasing by 10b: bump it to the just-released version **after**
publish). The `api-diff` gate stays correct throughout:

- On feature PRs and on the Release PR, `api-diff` compares the built jar against the **still-published
  `0.9.0`** and passes (master's public/protected API is unchanged vs 0.9.0 — verified in 10a, so the
  `fix!` needs **no** `breaking-change` label on the Release PR).
- After `0.10.0` publishes, the maintainer (or a later dedicated **post-publish** automation) bumps the
  baseline to `0.10.0`. *(Fully automating this safely means a post-deploy PR opened with the App token
  — noted as a future enhancement, out of 10c scope, so we don't overload the release-enablement PR.)*

I will soften the `copilot-instructions.md` wording that currently says a Wave-3 PR "automates" the
bump, to "is a documented release step (post-publish automation is a possible future enhancement)".

## Doc version strings (`README.md` / `llms.txt`) — out of 10c scope

Auto-bumping the `0.9.0` install snippets via release-please markers is **awkward**: the release-only
marker (`x-release-please-released-version`) would have to sit **inside a fenced `xml` code block**,
where an HTML/XML comment renders **visibly** in the README. Rather than pollute the docs, the
release-version snippet stays a **manual one-line edit on release** (rare, low-effort), noted in the
release checklist. *(Revisit later if desired.)*

## Consistency gap to note (not necessarily fixed in 10c)

release-please derives the bump from the **squash commit/PR title**, **not** the 10a `breaking-change`
label. So a public-API break that is merged as `fix:` (label-approved for `api-diff`) would only get a
**patch** bump and no "breaking" changelog entry. **Recommended follow-up:** have the `PR title` gate
**require a `!`** (e.g. `fix!:`) whenever the `breaking-change` label is present (validate on
`labeled`/`unlabeled`). Flagged here; can be a tiny separate PR.

## Dry-run / validation checklist (during the impl PR, before requesting review)

1. Validate `release-please-config.json` against the v17.6.0 schema; run `release-please` in
   **`--dry-run`** (pin the CLI to `release-please@17.6.0`, not `main`).
2. **HARD GATE — first PR must be the `0.10.0` *release*, not a snapshot.** Confirm the dry-run's
   Release PR is **version `0.10.0`** (label `autorelease: pending`), **not** a `0.9.1-SNAPSHOT`
   `autorelease: snapshot` PR (see the SNAPSHOT-lifecycle hazard above). Also confirm the root
   `pom.xml` `<version>` is rewritten, **harness POMs untouched**, and `CHANGELOG.md` covers post-0.9.0
   history. If it's a snapshot, apply a remediation (marker commit / `skip-snapshot`) and re-run.
3. `just fmt-check`, `just spellcheck` (add new terms), `just api-diff` still green; all workflow YAML
   parses.

## Operational steps (you)

1. **Create the GitHub App** (or decide PAT — see auth note), install on `FalkorDB/JFalkorDB`, add the
   secret(s).
2. **Branch protection** (exact contexts): master currently requires only `build`, `format`,
   `smoke-jdk8`. Before the first Release PR merges, also require **`lint`**, **`api-diff`**, and
   **`PR title`** (a.k.a. `lint-title`) — these are the gates the Release PR must pass. **Do not**
   require the push-only `release-please` workflow itself.
3. **After the first `0.10.0` release**, bump `api.diff.baseline` → `0.10.0` (manual step) and, if you
   want, we add the post-publish automation then.

## Risks & mitigations

| Risk | Mitigation |
| --- | --- |
| **First run makes a `0.9.1-SNAPSHOT` bump, not the `0.10.0` release** (unrecognized manual snapshot bump) | **Hard dry-run gate**; remediate with a `chore(master): release 0.10.0-SNAPSHOT` marker commit (preferred) or `skip-snapshot: true`. |
| App token missing/misscoped → Release PR has no CI, or release doesn't publish | Verify perms on the first Release PR before merge; documented App scopes. |
| `java` doesn't bump the POM (docs-confirmed) | Explicit `pom` `extra-files` updater; dry-run asserts the POM changed. |
| `maven` would bump harness POMs | Use `java` + single-file `extra-files`; harnesses never listed. |
| `api.diff.baseline` deadlock | Not release-please-managed; bumped **post-publish** (manual now). |
| Natural bump ≠ 0.10.0 | Verified from history; dry-run re-confirms before merge. |
| Release PR blocked by absent/stale required checks | App-token PR runs them; `always-update: true`; align branch protection first. |

## Definition of done

- Dry-run shows a correct **0.10.0** Release PR (POM bumped, harnesses untouched, changelog sane).
- Merging it tags + releases and (via the App token) triggers the hardened Central publish **and the
  artifact resolves on Central** (not merely that the event fired).
- All Wave-3 gates green; docs + wordlist updated; AI review resolved; **not self-merged**.
- Post-release: bump `api.diff.baseline`; optionally add the consistency `!`-gate and baseline
  post-publish automation as small follow-ups.
