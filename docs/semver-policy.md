# Versioning &amp; API-compatibility policy

JFalkorDB follows [Semantic Versioning](https://semver.org). The project has **not yet reached 1.0**;
while it is in `0.x`, a **breaking change bumps the minor version** (`0.x` &rarr; `0.(x+1)`) &mdash; the
`bump-minor-pre-major` convention configured in release-please. (Semver itself leaves `0.y.z`
unconstrained: during initial development anything may change.)

## The public API

The **public/protected API** is everything **outside** `com.falkordb.impl`. `com.falkordb.impl` is
internal and may change at any time &mdash; do not depend on it.

## How compatibility is enforced (automatically)

- **`api-diff`** (japicmp, `just api-diff`) &mdash; a per-PR gate that diffs the built jar against the
  last release on Maven Central and **fails on any binary- or source-incompatible** change to the
  public/protected API. An intentional, reviewed break is approved with the **`breaking-change`** PR
  label; the PR title must then carry a `!` (for example `fix!:` / `feat!:`), which the **PR-title**
  gate enforces so the release is versioned correctly.
- **`api-surface`** (`just api-surface`, also part of `just test`) &mdash; japicmp compares only the
  classes inside our jar, so it can't see a dependency change the methods we inherit from it (about
  960 each on `GraphPipeline` and `GraphTransaction`, from Jedis) or the Jedis types our signatures
  return. This gate compares the full surface, those members included, with the committed
  `api/jfalkordb.api`; a change is approved by regenerating it (`just api-surface-update`) in the PR,
  where the diff is reviewed. A dependency bump that removes or changes a member there is a break for
  our users even though none of our code changed.
- **`javadoc`** (`just javadoc`) &mdash; a strict `doclint` gate that keeps the public API documented.

## Releases

Releases are automated by **release-please**: conventional-commit merges to `master` accrue into a
Release PR that bumps the version and `CHANGELOG.md`; merging that PR tags `vX.Y.Z` and publishes to
Maven Central. A `feat:` / `fix!:` / breaking change therefore maps to the correct semver bump
automatically.
