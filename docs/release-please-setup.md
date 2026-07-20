# Release automation setup (release-please GitHub App)

Releases are automated by [release-please](https://github.com/googleapis/release-please) — see
`.github/workflows/release-please.yml`. It maintains a **Release PR** that bumps the version and
`CHANGELOG.md`; merging that PR tags `vX.Y.Z`, creates the GitHub Release, and (via
`version-and-release.yml`) publishes to Maven Central.

release-please must author its PR/tag/Release with a **GitHub App installation token**, **not** the
workflow's default `GITHUB_TOKEN`. GitHub deliberately does **not** re-trigger workflows for events
created by `GITHUB_TOKEN`, so with the default token the Release PR would have **no required checks**
(and could never merge) and the published Release would **not** trigger the Maven Central publish.
A GitHub App token does not have this limitation.

> This is a **one-time** setup. Once the App exists, is installed, and the two secrets are present,
> release automation runs on every push to `master`.

## 1. Create the GitHub App

Preferably create it under the **FalkorDB organization** (Organization → Settings → Developer settings
→ GitHub Apps → **New GitHub App**), so it isn't tied to one person. A personal account works too.

Fill in:

- **GitHub App name:** e.g. `falkordb-release-please` (must be globally unique).
- **Homepage URL:** anything, e.g. `https://github.com/FalkorDB/JFalkorDB`.
- **Webhook:** **uncheck "Active"** — no webhook is needed.
- **Repository permissions** (leave everything else "No access"):
  - **Contents:** **Read and write** (create tags/releases, push the release commits).
  - **Pull requests:** **Read and write** (open/update the Release PR, set labels).
- **Where can this GitHub App be installed?** **Only on this account.**

Click **Create GitHub App**.

## 2. Record the App ID and generate a private key

On the App's settings page:

- Note the **App ID** (a number near the top).
- Under **Private keys**, click **Generate a private key**. A `.pem` file downloads — keep it safe;
  you'll paste its **full contents** (including the `-----BEGIN/END PRIVATE KEY-----` lines) into a
  secret below.

## 3. Install the App on the repository

- On the App's settings page, open **Install App** → install it on **`FalkorDB/JFalkorDB`**
  (choose **Only select repositories → JFalkorDB**).

## 4. Add the two repository secrets

In **`FalkorDB/JFalkorDB` → Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret name | Value |
| --- | --- |
| `RELEASE_PLEASE_APP_ID` | The **App ID** from step 2. |
| `RELEASE_PLEASE_APP_PRIVATE_KEY` | The **entire contents** of the `.pem` private key from step 2. |

> These are distinct from `CENTRAL_USERNAME`/`CENTRAL_TOKEN` (the Sonatype/Maven Central publishing
> credentials) — those authenticate `mvn deploy` to Central and have no authority over GitHub.

## 5. Verify

After the secrets exist, the next push to `master` runs the **release-please** workflow. On the first
run it should open a **Release PR** (titled like `chore(main): release 0.10.0`) that bumps
`pom.xml` and adds `CHANGELOG.md`. Merging that PR tags `v0.10.0`, creates the Release, and triggers
the Maven Central publish.

If the App token step fails, re-check that the App is **installed on the repo** and that both secrets
are set (the private key must be the complete `.pem`).

## Branch protection

Ensure the required status checks (`build`, `format`, `lint`, `smoke-jdk8`, `api-diff`, `PR title`)
apply to the Release PR — the App-token PR runs them normally. Do **not** require the push-only
`release-please` workflow itself as a status check.

## Bootstrap note (why the manifest starts at `0.9.1-SNAPSHOT`)

`.release-please-manifest.json` is seeded at **`0.9.1-SNAPSHOT`**, not `0.9.0`. The release-please `java`
strategy keeps a `-SNAPSHOT` between releases and, from a *release* baseline, would first want to open
a "snapshot bump" PR (downgrading `pom.xml` from `0.10.0-SNAPSHOT` to `0.9.1-SNAPSHOT`) before the real
release. Seeding the manifest at the post-`0.9.0` snapshot it *would* have created lets the first run
compute the real release directly: `0.9.1-SNAPSHOT` + the breaking `fix!` (with
`bump-minor-pre-major`) → **`0.10.0`** — verified by `release-please --dry-run`. After the first
release the manifest tracks real versions normally (`0.10.0` → `0.10.1-SNAPSHOT` → …). The only
cosmetic effect is that the **first** changelog compare link reads `v0.9.1-SNAPSHOT...v0.10.0`;
subsequent releases compare real tags.

