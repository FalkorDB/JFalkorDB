window.BENCHMARK_DATA = {
  "lastUpdate": 1784557770720,
  "repoUrl": "https://github.com/FalkorDB/JFalkorDB",
  "entries": {
    "Throughput": [
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f239295d0f2568955c7fd786b8c2e74623b20c5a",
          "message": "bench: client load-sweep (client latency vs throughput) + Pages charts (#315)\n\n* docs: link the JMH benchmark trends (GitHub Pages) from the README\n\nAdd a Benchmarks badge + section pointing to the GitHub Pages trend chart\npublished by the Wave 2 benchmark radar:\nhttps://falkordb.github.io/JFalkorDB/dev/bench/\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: clarify benchmarks run on PRs targeting master (CodeRabbit)\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* bench: measure client latency (total − server) across a load sweep + curve\n\nReplace the single-op JMH benchmark with a client load-sweep harness that\nisolates client-side cost — total round-trip minus the server's reported\ninternal execution time (QUERY_INTERNAL_EXECUTION_TIME) — across concurrency\nlevels {1,2,4,8,16,32,64}. A single command is mostly server time, so only a\nload sweep reveals connection/thread-management effects (pool contention today;\nnon-blocking / Project-Loom variants later). Per level it reports client latency\np50/p95/p99 and throughput.\n\nRadar: two github-action-benchmark suites — client latency (smaller-is-better)\nand throughput (bigger-is-better) — plus a published latency-vs-throughput\nsaturation curve (Chart.js) and a landing page on GitHub Pages (master only).\nInformational, SHA-pinned. `just bench` runs the sweep; `just bench-one \"1,8,64\"`\nruns specific levels. Drops the JMH deps; the module stays standalone/non-deployable.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci(bench): skip-fetch-gh-pages on the second radar step to avoid the double-fetch\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* bench: harden the load harness (locale, validation, start-gate, failures) + tests\n\nAddress AI review on the new load benchmark:\n- Serialize JSON with Locale.ROOT so non-EN locales can't emit comma decimals\n  (invalid JSON for github-action-benchmark / the Pages chart).\n- Validate inputs: bench.loads (positive ints, non-empty), bench.measureMs (>0),\n  bench.warmupMs (>=0) — no more NaN throughput / meaningless zero metrics.\n- Release all workers from a start gate and start timing only once every worker\n  is ready, so high-concurrency phases don't ramp up gradually and bias\n  throughput and the saturation curve.\n- Propagate worker exceptions (fail the run instead of publishing partial/zero\n  metrics on a silent worker failure).\n- Use a unique per-run graph name (bench.graph overridable) so pointing at an\n  external server can't delete a pre-existing graph.\n- Add SRI + crossorigin to the Chart.js CDN <script> in curve.html.\n- Add LoadBenchmarkTest: percentile math, parseLoads validation, locale-safe JSON.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* bench: fail fast when the server execution time can't be parsed (Copilot)\n\nserverNanos previously returned 0 on any missing/unparseable value, which would\nsilently turn client latency back into full round-trip latency and publish\nmisleading metrics if the server ever changed the QUERY_INTERNAL_EXECUTION_TIME\nformat. Throw a descriptive IllegalStateException instead — it propagates via the\nworker failure path, so the run fails loudly rather than reporting wrong numbers.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test(bench): unit-test parseServerNanos for valid + all bad-input cases\n\nExtract server-time parsing into parseServerNanos(String) and cover it:\n- valid: \"0.5 milliseconds\", \"2 milliseconds\", \"0.25\" (no unit), surrounding\n  whitespace, and a realistic \"0.229334 milliseconds\";\n- fail-fast (IllegalStateException) for null, \"\", whitespace-only, a unit-only\n  string, non-numeric tokens, malformed numbers (\"1.2.3\"), and \"0x10\".\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* bench: use a constant parameterized point-lookup in the hot loop (Copilot)\n\nConcatenating the random id into the Cypher text on every iteration built a\ndistinct query per sample, adding Java-side string work and forcing the server\nto re-parse/re-plan each query. That parse/plan cost isn't in\nQUERY_INTERNAL_EXECUTION_TIME, so it inflated the reported client latency.\n\nUse a constant query text (\"MATCH (n:N {id: $id}) RETURN n.id\") with a reusable\nparams map so the server reuses its cached plan and the timed sample no longer\npays for per-iteration string building.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* perf(bench): parse server time without a per-sample regex (CodeRabbit)\n\nparseServerNanos ran split(\"\\\\s+\") on every measured sample; the multi-char\nregex bypasses String.split's fast-path and recompiles a Pattern each call,\nadding GC/CPU noise to the load generator's hot loop (and it trimmed twice).\n\nExtract the leading numeric token with a manual whitespace scan instead: trims\nonce, allocates at most one substring, and keeps the any-whitespace separator\nbehavior so every existing parseServerNanos test still passes.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix(bench): stop container on driver-init failure; validate server time unit (Copilot)\n\nTwo Copilot findings on the load harness:\n\n- If FalkorDB.driver(host, port) threw, the just-started Testcontainers\n  container was never stopped (creation sat outside the try/finally), leaking a\n  running container. Move driver creation inside the try and null-guard cleanup\n  so container.stop() always runs.\n\n- parseServerNanos always treated the numeric token as milliseconds without\n  checking the unit, so a server unit change (seconds/microseconds) would\n  silently miscompute client latency. Validate the suffix is empty or a\n  millisecond spelling (ms/millisecond/milliseconds) and fail fast otherwise.\n  Added tests for accepted spellings and rejected units.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs(bench): correct the LOOKUP_QUERY comment about client string building (Copilot)\n\nThe comment claimed each sample 'doesn't pay for Cypher string building', but\nGraph.query(String, Map) -> Utils.prepareQuery still builds a 'CYPHER id=<v>\n<body>' string every call. Reword to reflect reality: the win is a constant\nquery *body* so the server reuses its cached plan (vs re-parsing/re-planning a\ndistinct query per iteration); the client-side param serialization is legitimate\ncost the benchmark is meant to measure.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci(bench): drop contents:write on PR benchmark runs (least privilege) (Copilot)\n\nThe single benchmark job granted contents:write to every run, including\npull_request runs that execute untrusted PR code via 'just bench' (the PR's own\nJustfile) — a privilege-escalation risk for same-repo PR branches. Since GitHub\nActions doesn't allow expressions in 'permissions', split by event:\n\n- benchmark-pr (pull_request): contents:read + pull-requests:write — only\n  compares against the master baseline and comments regressions; never pushes.\n- benchmark-master (push): contents:write — stores the baseline/trend and\n  publishes the curve to gh-pages.\n\nBoth run the same 'just bench' recipe; behavior per event is unchanged.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix(bench): reject non-finite/negative server times in parseServerNanos (Copilot)\n\nDouble.parseDouble accepts NaN/Infinity/negative tokens, and casting them to\nnanoseconds yields 0 or garbage — silently corrupting the client-latency metric\n(server=0 collapses it back to full round-trip; huge/negative values distort it).\nValidate the parsed value is finite and non-negative and fail fast otherwise.\nAdded tests covering NaN, Infinity, -Infinity, negative, and overflow (1e999)\ntokens with and without a unit suffix.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs(bench): drop stale JMH references from Justfile + benchmarks POM (Copilot)\n\nThis PR replaced the JMH benchmark with the client load-sweep harness, but the\n'just bench' section header/comment and the benchmarks POM header still called it\na 'JMH benchmark harness' and referenced JMH deps. Reword both to describe the\nload-sweep harness and the latency/throughput/curve JSON it emits, so module docs\nmatch reality.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* perf(bench): avoid per-iteration Integer autoboxing; guard sample-count overflow (Copilot)\n\n- The measured loop autoboxed a fresh Integer per iteration for the id param;\n  that allocation/GC isn't client-library cost and adds noise at high throughput.\n  Pre-box the ids once (IDS) and index into them so no boxing happens per sample.\n\n- summarize accumulated the total sample count in an int used for array sizing;\n  a long measureMs / high throughput could overflow it into a negative array size\n  and wrong throughput. Accumulate in a long and fail fast if it exceeds what a\n  single Java array can hold.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-19T07:00:13+03:00",
          "tree_id": "8928a0b71a7109dfbc0c54872700b2b928ad1151",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f239295d0f2568955c7fd786b8c2e74623b20c5a"
        },
        "date": 1784433715561,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5628.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9033.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13970.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17622.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17072,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16895.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16782.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "e0988073a54dc5119dad6a10a7baadd4e03827a3",
          "message": "fix!: harden the Cypher parameter path against injection (#317)\n\n* fix!: harden the Cypher parameter path against injection\n\nUtils.quoteString previously escaped only the double quote (not backslash), left\nparameter names unvalidated, and fell back to value.toString() for unknown types\n— so a value ending in a backslash (or a crafted key/type) could break out of the\nCYPHER header and inject Cypher.\n\nRework the encoder against the empirically-verified FalkorDB grammar:\n- escape backslash and double quote (plus the whitespace escapes the server\n  decodes); emit other characters raw; reject NUL and unpaired surrogates. Never\n  emit \\uXXXX (FalkorDB keeps unknown escapes literal).\n- validate parameter names as identifiers and backtick-quote them, so a name that\n  is a CYPHER-prefix keyword can't be misparsed (binds to the bare $name).\n- whitelist value types (null/Boolean/String/Character/int64 integers/finite\n  float-double/array/List/Map); reject BigDecimal, out-of-range integers,\n  non-finite floats, and arbitrary objects instead of a silent toString().\n- backtick-quote non-identifier map keys (reject keys with a backtick, NUL, or\n  unpaired surrogate); guard against cyclic containers.\n\nAdds adversarial unit tests, a jqwik property (no value can terminate the literal\nearly), and a server round-trip IT (round-trip fidelity + injection-inertness,\nverified against the pinned FalkorDB). Documents the guarantee in README, llms.txt,\nand the parameterized-query Javadoc, and drops the now-unused commons-text\ndependency. BREAKING: previously-accepted malformed inputs now throw.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* refactor: extract numeric encoding into appendNumber to shorten the type-dispatch chain (CodeQL)\n\nGroups the Byte/Short/Integer/Long/BigInteger/Float/Double handling into a single\nNumber branch + helper, shortening appendValue's instanceof chain flagged by\nCodeQL. Behavior is unchanged (BigDecimal and foreign Number subtypes are still\nrejected).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: sanitize param name/key in error messages; document BigInteger + cyclic rejection (Copilot)\n\n- Exception messages for an invalid parameter name / map key now render the raw\n  value via safeDisplay (control chars escaped, length bounded), preventing\n  log-forging if a caller logs the message. Adds a regression test.\n- Graph Javadoc + README now state that BigInteger (within signed 64-bit range)\n  is accepted and that BigDecimal and cyclic containers are rejected.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: escape C1 controls and line/paragraph separators in error messages (Copilot)\n\nsafeDisplay only escaped C0 controls and DEL; broaden it to all control characters\nvia Character.isISOControl (covers C0, DEL, and C1 incl. U+0085 NEL) plus the\nUnicode line/paragraph separators U+2028/U+2029, none of which should be able to\nforge or split a log line. Extends the regression test accordingly.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: use BigInteger.longValueExact for the signed-64-bit range check (Copilot)\n\nbitLength() > 63 is an awkward proxy for 'fits in a long'. Use longValueExact()\n(Java 8), which throws for any value outside [Long.MIN_VALUE, Long.MAX_VALUE] —\nunambiguously correct for both signs. Extends testNumericBounds with Long.MIN_VALUE\n(in range) and negative/positive out-of-range BigIntegers.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: complete parameterized-query Javadoc + surrogate-safe safeDisplay truncation (Copilot)\n\n- GraphPipeline/GraphTransaction query(String, Map) Javadoc now match Graph's:\n  note cyclic-container rejection and the values-only safety caveat.\n- safeDisplay keeps valid surrogate pairs together and escapes unpaired surrogates\n  (including one split at the 64-code-unit truncation boundary), so an exception\n  message can't contain a lone surrogate. Adds a boundary regression test.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: reject null query/params in prepareQuery with a clear message (Copilot)\n\nPreviously a null params threw an opaque NPE and a null query was silently\nappended as the literal 'null' and sent to the server. Validate both up front\nwith IllegalArgumentException. Adds a regression test.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: state that Map parameter keys must be Strings (CodeRabbit)\n\nREADME + Graph/GraphPipeline/GraphTransaction Javadoc now document that Map values\nmust have String keys, and that non-String map keys are rejected.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: document null query/params rejection on parameterized query Javadoc (Copilot)\n\nGraph/GraphPipeline/GraphTransaction query(String, Map) Javadoc now state that a\nnull query or params is rejected with IllegalArgumentException (and mark both\n@params must-not-be-null).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* perf: report bitLength, not the full value, for out-of-range BigInteger (Copilot)\n\nA multi-million-digit BigInteger would otherwise materialize an enormous decimal\nstring just to build the rejection message. Include bi.bitLength() instead; adds\na bounded-message test.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: cover remaining Utils parameter-encoder branches (100% coverage)\n\nAdd unit tests for the encoder edge cases that were uncovered: Byte/Short and\nfinite Float/Double params; null parameter name and null/NUL/lone-surrogate map\nkeys; a value ending in a lone high surrogate; a valid-emoji map key; and\nsafeDisplay escaping unpaired surrogates and the U+2029 separator in error\nmessages. Utils is now at 100% line/branch coverage.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-19T16:43:38+03:00",
          "tree_id": "bef0c56d48c4bfd9a27323c9bea2ec1ad309df66",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/e0988073a54dc5119dad6a10a7baadd4e03827a3"
        },
        "date": 1784468708660,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4181.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6822,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10428.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13680.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13000,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12921,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12928.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "651f985573e4cd423c8bad5abe911ec073ab7751",
          "message": "build: add SpotBugs/FindSecBugs + Error Prone + OWASP quality gates; retire DeepSource (#318)\n\n* build: add SpotBugs/FindSecBugs + Error Prone + OWASP quality gates; retire DeepSource\n\nWave 3 PR 9. Extends the off-by-default quality profile with three static-analysis\ntools (kept off the default lifecycle so the Java-8 artifact is unaffected):\n\n- SpotBugs + FindSecBugs (threshold Medium for security coverage; EI_EXPOSE_REP\n  defensive-copy noise excluded via spotbugs-exclude.xml), bound to verify in-profile.\n- Error Prone (error_prone_core, forked compiler + JDK-21 add-exports/add-opens).\n- OWASP dependency-check (unbound; failBuildOnCVSS=7; NVD key via env var), run by a\n  dedicated scheduled/manual audit workflow.\n\nAdds 'just lint' (Spotless-check + SpotBugs/FindSecBugs + Error Prone) and 'just\naudit' (OWASP), a 'lint' CI job in maven.yml, and .github/workflows/audit.yml.\nRetires DeepSource (deletes .deepsource.toml; documents in-build gates in\ncopilot-instructions/CONTRIBUTING). Fixes the findings the tools surfaced:\nUtils.DUMMY_LIST/DUMMY_MAP immutable; GraphCacheList locks on a private object;\nResultSetImpl final (CT_CONSTRUCTOR_THROW); test '1 << 40' -> '1L << 40';\nArrays.asList(byte[]) -> Collections.<Object>singletonList(...).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci(audit): bucket the OWASP NVD cache key by ISO week, not run_id (Copilot)\n\nA github.run_id key never hits exactly, so actions/cache re-saved a new entry on\nevery run. Key on runner.os + ISO week instead: re-runs/dispatches within a week\nreuse it exactly, it rotates weekly, and restore-keys still seeds from the prior\nweek's data — keeping the NVD cache fresh without unbounded growth.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-19T20:34:06+03:00",
          "tree_id": "c8cde3447c3e3e09a0703a16c1e6590679915849",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/651f985573e4cd423c8bad5abe911ec073ab7751"
        },
        "date": 1784482541070,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4188,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6888.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10651.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13841.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13089.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 13028.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 13031.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "04e2bf918f375b38d5934f52353dd8722e2a5337",
          "message": "ci: add PR-title lint + japicmp public-API diff gates (#319)\n\n* ci: add PR-title lint + japicmp public-API diff gates\n\nTwo Wave-3 release gates, both off the default lifecycle so the Java-8 artifact and\n`just verify` are untouched:\n\n- PR-title lint (.github/workflows/pr-title.yml): amannn/action-semantic-pull-request\n  (pinned v6.1.1) enforces a Conventional-Commit PR title on pull_request events.\n\n- API-diff gate (japicmp): compares the built jar against the last release\n  (`api.diff.baseline`, currently 0.9.0) on Maven Central and fails on binary/source-\n  incompatible public/protected API changes; `com.falkordb.impl` and its subpackages\n  are internal and excluded. Declared unbound in the off-by-default `-Pquality`\n  profile; run via `just api-diff` and the dedicated PR-only `api-diff` workflow. A\n  reviewed, intentional break is approved with the `breaking-change` PR label\n  (labeled/unlabeled re-run the gate without a new commit).\n\nDocs (CONTRIBUTING, copilot-instructions) and the wordlist updated.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: say \"public/protected API\" for the api-diff gate scope\n\nThe japicmp gate uses accessModifier=protected, so it covers public AND protected\nAPI. Make the copilot-instructions recipe-table row and breaking-change guidance\nconsistent with that (addresses CodeRabbit review on #319).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-20T08:11:12+03:00",
          "tree_id": "a3e86a7d0b2523c780fe5775fc0154813a877185",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/04e2bf918f375b38d5934f52353dd8722e2a5337"
        },
        "date": 1784524363115,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5666,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9148,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13924.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18370,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17108,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16670.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16717.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6c65f034d12a92b5cd8137bb213886529c82ab5b",
          "message": "ci: harden release + snapshot publish workflows (#325)\n\n* ci: harden release + snapshot publish workflows\n\n- snapshot.yml: add a `check-version` guard job that only runs the Deploy\n  Snapshot job when the POM is a -SNAPSHOT (read via `just project-version`), so\n  a release-version commit on a deploy branch (e.g. a future release-please PR)\n  can't trigger an unsigned/duplicate snapshot publish.\n- version-and-release.yml: add `workflow_dispatch(tag)` as a retriable recovery\n  path; strictly validate the tag (`vX.Y.Z`); pin the release checkout to the\n  immutable commit (`github.sha`) and the dispatch to `refs/tags/<tag>`; drop the\n  moving-image FalkorDB service container.\n- deploy-release: skip tests (`-Dmaven.test.skip=true`) — the required PR CI\n  already validated the commit against the pinned FalkorDB digest, so the deploy\n  must not re-run *IT against a moving image; the Java-8 guardrails still run and\n  there is no JaCoCo coverage check to trip. Add a `just project-version` recipe.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: harden publish workflows per review (regex + least-privilege)\n\n- version-and-release.yml: validate the release tag with bash `[[ =~ ]]` instead\n  of `grep -Eq`, so a multi-line workflow_dispatch input can't slip a valid first\n  line past validation (grep matches line-by-line). Closes a newline-injection\n  path into `just set-version`.\n- Add `permissions: contents: read` to snapshot.yml and version-and-release.yml\n  (the deploys authenticate to Maven Central with their own secrets, not the\n  GITHUB_TOKEN) — least privilege, addresses CodeRabbit/zizmor.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: defense-in-depth for set-version quoting + error-log escaping\n\nAddress Copilot review on #325:\n- Justfile `set-version`: wrap the version in just's `quote(...)` so it is always\n  passed to Maven as a single shell-literal argument — protects any future/manual\n  caller regardless of workflow-side tag validation.\n- version-and-release.yml: escape the untrusted tag with `printf %q` in the\n  invalid-tag `::error::` message so a multi-line workflow_dispatch input can't\n  inject `::workflow::` log commands.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: escape project.version in snapshot guard logs\n\nAddress Copilot review on #325: the `check-version` guard echoed the raw POM\n`project.version` to stdout and into a `::notice::` command. Use `printf %q` so a\nvalue with an embedded newline can't inject `::workflow::` log commands — same\nhardening as the release workflow's `::error::` path.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-20T11:34:29+03:00",
          "tree_id": "143b1ef10abb71da8118f757dc7210e5965166a7",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/6c65f034d12a92b5cd8137bb213886529c82ab5b"
        },
        "date": 1784536569853,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3561,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6276.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9494,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11956.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11394.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11321.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11349.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "1b85df1253e5e248d0552352da4d812be24d4ac7",
          "message": "ci: enable release-please for automated releases (#327)\n\n* ci: enable release-please for automated releases\n\nAutomate releases (conventional-commit-driven, changelog-backed) while preserving\nthe hardened JDK-8 Maven Central publish and the -SNAPSHOT lifecycle:\n\n- release-please-config.json: release-type `java` + a `pom` extra-files updater\n  (the java strategy updates no files itself; `maven` would recurse into the\n  harness POMs) + bump-minor-pre-major + always-update.\n- .release-please-manifest.json seeded at 0.9.1-SNAPSHOT so the first run computes\n  the real 0.10.0 release directly (from a 0.9.0 release baseline the java strategy\n  would first open a snapshot-bump PR). Verified with `release-please --dry-run`:\n  first Release PR = 0.10.0, root pom.xml bumped, harness POMs untouched.\n- .github/workflows/release-please.yml: mints a GitHub App token\n  (create-github-app-token v3.2.0) so the bot's Release PR/Release trigger required\n  CI + the publish; runs release-please-action v5.\n- version-and-release.yml: on the automatic release path assert tag ==\n  v${project.version} (release-please already set the POM) instead of set-version;\n  keep set-version on the workflow_dispatch recovery path.\n- docs/release-please-setup.md: one-time GitHub App setup + bootstrap notes.\n- copilot-instructions Releasing section rewritten for the release-please flow.\n\napi.diff.baseline and the README/llms.txt version strings are intentionally NOT\nrelease-please-managed (auto-bumping the baseline would deadlock the fail-loud\napi-diff gate against an unpublished artifact); the baseline stays a documented\npost-publish step.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: fix release-please setup doc (master branch, lint-title check)\n\nAddress Copilot review on #327:\n- Example Release PR title uses chore(master), not chore(main) (default branch).\n- Branch-protection list uses the actual check context lint-title, not 'PR title'.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-20T15:56:45+03:00",
          "tree_id": "ca79709730d07b0f1e78c916953100759653e3cf",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/1b85df1253e5e248d0552352da4d812be24d4ac7"
        },
        "date": 1784552293329,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4003,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6976.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10307,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13879.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12992.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 13021.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 13081.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "9ceb32bfb1714e9bd4c4ce0e07cc572bddf08829",
          "message": "docs: Wave 3 PR 10c plan (enable release-please) (#326)\n\nReviewed plan doc for the release-please enablement (impl in #327). Temporary artifact.",
          "timestamp": "2026-07-20T16:09:32+03:00",
          "tree_id": "99aa6920c04684c385945d13517fe7aa125fa01c",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/9ceb32bfb1714e9bd4c4ce0e07cc572bddf08829"
        },
        "date": 1784553068293,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6289.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11393.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 17859.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24048,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 23121,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 22616,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 22940.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "barak.bar@gmail.com",
            "name": "Barak Bar Orion",
            "username": "barakb"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "73a955c8749aafc1cc47ef325c6376915e905a1f",
          "message": "build(deps): combined Dependabot bumps (testcontainers 2, Error Prone 2.50, SpotBugs, FindSecBugs, jqwik) (#328)\n\nConsolidates #320-#324; adds the EP 2.50 JDK-21 compiler flag; bumps testcontainers to 2.0.5 in root + benchmarks. All gates green.",
          "timestamp": "2026-07-20T17:27:50+03:00",
          "tree_id": "591daa22b6c670d04978ab0900ac7cb434b0ad48",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/73a955c8749aafc1cc47ef325c6376915e905a1f"
        },
        "date": 1784557770701,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3468.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5915.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9054.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11541,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11144.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11109.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10973.667,
            "unit": "ops/s"
          }
        ]
      }
    ]
  }
}