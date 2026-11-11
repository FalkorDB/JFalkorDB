window.BENCHMARK_DATA = {
  "lastUpdate": 1794383797291,
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
          "id": "68d27a362340c47f0106586431908e9cdac78c81",
          "message": "fix: make Point equals/hashCode a consistent, drift-tolerant contract (#330)\n\n* fix: make Point equals/hashCode a consistent, drift-tolerant contract\n\nPoint.equals used an epsilon \"within tolerance\" check while hashCode hashed the\nexact doubles. That violates the equals/hashCode contract (equal points could hash\ndifferently) and epsilon equality is also non-transitive — equalsverifier rejects\nit.\n\nThe epsilon tolerance is intentional: FalkorDB stores point coordinates in single\nprecision, so a round-tripped point differs from the original by ~1e-5 (see\nGraphAPIIT#testGeoPointLatLon: 30.27822306 comes back as 30.2782230377197). So\n\"just drop epsilon / use exact equality\" is wrong — it would break that round-trip.\n\nFix: compare coordinates on a 1e-5 grid (quantize in both equals and hashCode).\nThis keeps the drift tolerance, is transitive, and is hashCode-consistent.\n\nTests: equalsverifier on Point, a single-precision-drift regression using the exact\nGraphAPIIT values, and a distinct-points check.\n\nWave 4 PR 12a. The equalsverifier tests for the other value types (Node/Edge/\nGraphEntity/Property) need only equalsverifier config (inheritance/finality), not\nfixes — they land in PR 12b.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: assert Point hashCode contract instead of a brittle magic value\n\nThe grid-based Point.hashCode() (commit 4fbd700) changed the raw hash,\nbreaking the hard-coded -132320535 assertion in assertTestGeoPoint.\nAssert that equal points share a hashCode (the actual contract) rather\nthan a magic constant that drifts with the implementation.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: reject non-finite Point coordinates and drop Markdown from comment\n\nAddress AI review on PR #330:\n- Math.round(NaN) == 0, so a Point with NaN/Infinity coordinates could\n  collide with grid cell 0 (compare equal to 0.0) - a regression vs the\n  old epsilon equals. Both constructors now reject non-finite coordinates\n  fail-fast via requireFinite(), so cell() only ever sees finite values.\n- Rephrase the grid comment without Markdown (**transitive**) so it reads\n  cleanly in source.\n\nAdds PointTest.rejectsNonFiniteCoordinates covering both constructors.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: reject null Point list elements and clarify grid comment\n\nAddress follow-up AI review on PR #330:\n- Point(List<Double>) unboxed elements before validating, so a null\n  element threw NullPointerException instead of the documented\n  IllegalArgumentException. Guard nulls explicitly so both constructors\n  fail fast with a consistent contract. Adds rejectsNullListElements.\n- Reword the grid comment: it claimed round-trip drift of \"up to ~1e-5\",\n  a bound the quantization doesn't strictly guarantee (boundary straddle).\n  Describe the mechanism and its trade-off instead of a numeric bound.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: align Point constructor Javadoc with actual validation\n\nAddress AI review on PR #330: the Javadoc said latitude/longitude \"must\"\nbe within [-90,90]/[-180,180], but only finiteness is enforced. Soften the\nrange wording to \"normally in the range ...\" and state \"Must be finite\" so\nthe docs match behavior. Range enforcement is intentionally out of scope\nfor this equals/hashCode fix.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T09:15:32+03:00",
          "tree_id": "7c6ce65e213e4897a531ffb3fb64cd625eaacfbf",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/68d27a362340c47f0106586431908e9cdac78c81"
        },
        "date": 1784614624438,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4169.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6827.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10691.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13507.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12929,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12850.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12778,
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
          "id": "42d3bc22e9287be70979d01c7902025844605f91",
          "message": "fix: align Property.hashCode with its Integer/Long-normalizing equals (#334)\n\nProperty.equals treats an Integer value as equal to the numerically-equal\nLong (valueEquals normalizes Integer->Long), but hashCode hashed the raw\nvalue. For negative values Integer.hashCode != Long.hashCode (e.g.\nInteger(-1)==-1 vs Long(-1L)==0), so equal Properties could hash\ndifferently - an equals/hashCode contract violation that breaks\nHashMap/HashSet lookups and which equalsverifier (Wave 4) rejects.\n\nApply the same normalization in hashCode via a shared normalizeValue()\nhelper. Adds a negative-value contract test and an EqualsVerifier check\n(NONFINAL_FIELDS/STRICT_INHERITANCE suppressed for this mutable,\ninstanceof-compared entity).\n\nWave 4 - PR 12a (Property), following #330 (Point).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T10:21:41+03:00",
          "tree_id": "80cec49fc04f79eb40fbda74dbb48c886da09b3f",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/42d3bc22e9287be70979d01c7902025844605f91"
        },
        "date": 1784618590056,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3606,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6310.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9005,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11739.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11337,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11208,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11226.667,
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
          "id": "f778d8b658a953232e3e1b8750d8fcadc9d95d18",
          "message": "ci: exclude auto-generated CHANGELOG.md from spellcheck (#335)\n\nrelease-please generates CHANGELOG.md from commit subjects + short SHAs\n(e.g. fdc, af, dae) and code identifiers (e.g. hashCode, getResponse,\nequalsverifier) - plus historical commit-message typos - none of which\nare meaningfully spellcheckable. This blocked the release PR (#331) and\nwould block every future release PR.\n\nExclude CHANGELOG.md from the pyspelling sources via the '|!' negation;\nREADME/CONTRIBUTING/docs prose is still checked. Unlike falkordb-rs\n(release-plz with a header-only, hand-written changelog that stays\nspellcheckable), JFalkorDB's changelog is machine-generated, so excluding\nit is the robust fix.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T10:36:09+03:00",
          "tree_id": "e45f3f2fd01d60dac1fad9a071a3ea38f6faa2d9",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f778d8b658a953232e3e1b8750d8fcadc9d95d18"
        },
        "date": 1784619464105,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4946,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 8428,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14037,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18141.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17069.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17015.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16952.667,
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
          "id": "62ca7b0c5ff62d95ccbd719ccbf1a1a0cf527f50",
          "message": "ci: don't fail the CodeRabbit check on review rate limit (#336)\n\nCodeRabbit's commit status has been showing up as a failing \"CodeRabbit\"\ncheck with \"Review rate limited\" on PRs (e.g. #335). A transient rate\nlimit is not a code problem and should not present as a failed check.\n\nSet reviews.fail_commit_status: false to override the org-level setting, so\nCodeRabbit no longer marks the commit status 'failure' when it cannot run a\nreview. Successful reviews still report via the normal pending -> success\ncommit status.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T10:54:19+03:00",
          "tree_id": "d79f2eb03091466000d96facc06d4cb929eb72bc",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/62ca7b0c5ff62d95ccbd719ccbf1a1a0cf527f50"
        },
        "date": 1784620555038,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3445,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6091.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9123.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11938.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11229,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11343.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11198.667,
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
          "id": "02e44bc17f28c5dd56c6cf64a080c3428f067567",
          "message": "test: EqualsVerifier contracts for all value-type implementations (#337)\n\nWave 4 - 12b (part 1). Adds an EqualsVerifier equals/hashCode contract\ntest for every value implementation that lacked one (Point/Property/Path\nalready had verifiers): Node, Edge, GraphEntity, StatisticsImpl,\nRecordImpl, HeaderImpl.\n\nAll test-only - no production changes. Suppressions are justified and do\nnot mask reachable bugs:\n- NONFINAL_FIELDS: these are mutable entities (setters/mutable state).\n- STRICT_INHERITANCE: Node/Edge have no subclasses and GraphEntity is\n  abstract, so the instanceof equals is symmetric for every reachable\n  concrete instance.\n- NULL_FIELDS (StatisticsImpl/HeaderImpl): the skipped fields are\n  final/constructor-initialized and never null for these internal,\n  driver-built result types.\n- withIgnoredFields(\"raw\") (HeaderImpl): equals and hashCode both use the\n  schema derived from raw (getSchemaTypes/getSchemaNames), so raw is\n  legitimately not part of identity.\n\njust test: 170 unit tests green (was 164). fmt-check + lint green.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T15:46:10+03:00",
          "tree_id": "bdfe9de6f4f606b53051a062ae48b5db6704a5f6",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/02e44bc17f28c5dd56c6cf64a080c3428f067567"
        },
        "date": 1784638060576,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3554.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6129,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8880.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11801,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11251.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11184.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11232.667,
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
          "id": "4e89e666278ceb9d59b063c2a0839c97a4c11e61",
          "message": "test: parameterized value/round-trip matrices (boundaries, unicode, collections) (#338)\n\n* test: parameterized value/round-trip matrices (boundaries, unicode, collections)\n\nWave 4 - 12b (part 2). Adds @ParameterizedTest round-trip matrices to\nParameterRoundTripIT, filling genuine gaps beyond the existing ad-hoc cases:\n- integer boundaries: 0, -1, Integer.MIN/MAX, Long.MIN/MAX\n- doubles: representative magnitudes/signs\n- unicode: accents, CJK, Nordic, ZWJ emoji, regional indicators, combining\n  marks, bidirectional scripts, astral-plane math alphanumerics\n- collections: empty list/map, nested, and null-inside-list\n\nValidated against a real FalkorDB (just db-up): 31 tests green. The double\nmatrix was tuned empirically: FalkorDB returns doubles with ~15 significant\ndigits and a finite range, so full-precision values (Math.PI) come back\nrounded and Double.MAX_VALUE overflows to Infinity - server-side numeric\nfidelity, not a client bug (documented in the provider).\n\njust fmt-check + lint green.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: assert nested-list element values, not just size\n\nAddress Copilot review on #338: the nested-collection round-trip test only\nchecked the inner list's size. Assert the actual normalized element values\n(1L, 2L, in order) so a corrupted/misordered round-trip is caught.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T16:08:32+03:00",
          "tree_id": "ffac78e926931f607cc64d06668349c74d48ec1f",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/4e89e666278ceb9d59b063c2a0839c97a4c11e61"
        },
        "date": 1784639416285,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4218,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6994.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10368,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 14030.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12862.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 13160.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 13136,
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
          "id": "fc5f95bc0a45599c6df7ad81eef77c378a7c689f",
          "message": "test: generative server round-trip property for params (jqwik) (#340)\n\n* test: generative server round-trip property for params (jqwik)\n\nWave 4 - 12b (part 3). Adds ParamRoundTripPropertyIT: jqwik properties that\nsend a *generated* value as a parameter to a real FalkorDB server and assert\nit comes back unchanged - the full serialize->server->deserialize round-trip.\n\nComplements the existing coverage:\n- ParameterRoundTripIT: fixed round-trip cases.\n- UtilsParamPropertyTest: generative but serialize-side only (well-formed\n  literal, no server).\n\nSo an escaping bug that emitted a well-formed but semantically wrong literal\nis now caught generatively end-to-end. Covers arbitrary strings (biased to\nbackslash/quote/control chars, excluding NUL/surrogates the encoder rejects)\nand arbitrary longs. Validated against a live FalkorDB (just db-up): both\nproperties green (300 + 200 tries). fmt-check + lint green.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: guard roundTrip() with hasNext() for a clear failure message\n\nAddress Copilot review on #340: roundTrip() called iterator().next()\ndirectly, which would throw a cryptic NoSuchElementException if a query\nunexpectedly returned no rows. Assert hasNext() first (mirroring\nParameterRoundTripIT.roundTrip) so the failure names the cause.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: robust @AfterProperty cleanup + accurate surrogate Javadoc\n\nAddress Copilot review on #340:\n- @AfterProperty now closes the client in a finally block (and nulls the\n  field) so a throwing deleteGraph() can't leak the connection.\n- Correct the safeStrings Javadoc: the encoder rejects NUL and *unpaired*\n  surrogates; valid surrogate pairs (emoji) are accepted and round-trip\n  (covered by ParameterRoundTripIT).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: rename to ParameterRoundTripPropertyIT and make it public\n\nAddress Copilot review on #340: match the sibling ITs' naming and\nvisibility - rename ParamRoundTripPropertyIT -> ParameterRoundTripPropertyIT\n(no 'Param' abbreviation, consistent with ParameterRoundTripIT) and declare\nthe class public like the other *IT classes for consistent test discovery.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T16:36:41+03:00",
          "tree_id": "d845be3be1fa1fee3e18f411e90f8c851b29d2e1",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/fc5f95bc0a45599c6df7ad81eef77c378a7c689f"
        },
        "date": 1784641104153,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3680.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6366,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8992.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12130.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11586.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11569.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11433,
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
          "id": "e57d45d9b26ef11855a814a7c1523227d599e502",
          "message": "ci: require a '!' title when the breaking-change label is present (#341)\n\nCloses the semver label<->commit gap flagged in Wave 3 (10c): the\nbreaking-change label approves an intentional public-API break in api-diff,\nbut release-please derives the version bump from the commit subject, so a\nbreaking-change-labelled fix:/feat: without '!' would still release as a\npatch/minor. Require the '!' marker whenever the label is present, and\nre-check on labeled/unlabeled.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T16:39:17+03:00",
          "tree_id": "a5d06439fbaa09afa7dbb3a834d57e845e2ac923",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/e57d45d9b26ef11855a814a7c1523227d599e502"
        },
        "date": 1784641253617,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3426,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5870.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8737.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11540,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10974.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11032,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11027,
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
          "id": "e4df9d500c54c7c574ed303d6d8ee20410cb0298",
          "message": "test: make the FalkorDB test image overridable via FALKORDB_IMAGE (#342)\n\n* test: make the FalkorDB test image overridable via FALKORDB_IMAGE\n\nWave 4 - 13a (foundation for the compatibility matrices). Extract the\ncontainer image resolution into FalkorDbImage.resolve(override): the trimmed\nFALKORDB_IMAGE system property or env var if non-blank, else the pinned\ndigest (DEFAULT). The result is marked asCompatibleSubstituteFor(\n\"falkordb/falkordb\") so Testcontainers accepts a custom tag/digest, and the\nparse is wrapped so a malformed value fails with a clear message instead of a\nraw ArrayIndexOutOfBoundsException.\n\nExtracted into its own class so the resolution is unit-testable without\ntriggering TestServer's static container start. Adds FalkorDbImageTest\n(default/blank, trimmed override, malformed) - runs without Docker.\n\nLets the suite matrix over FalkorDB versions (e.g.\n-DFALKORDB_IMAGE=falkordb/falkordb:edge); the CI version/JDK matrices that\nconsume it are the follow-up (13b).\n\njust test: 173 unit green; ConfigIT green via Testcontainers (default path);\nfmt-check + lint green.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: fix FALKORDB_IMAGE precedence so a blank property can't shadow env\n\nAddress Copilot review on #342: System.getProperty(key, envDefault) only\nuses the env default when the property is *absent*, so a blank property\n(-DFALKORDB_IMAGE=) returned \"\" and shadowed a non-blank env var, falling\nback to the default. Add FalkorDbImage.pickOverride(property, env): the\nproperty wins only when non-blank, else the env value. Adds precedence unit\ntests incl. a regression that a blank property no longer shadows an env image.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: read FALKORDB_IMAGE env lazily (Supplier), only when property is blank\n\nAddress Copilot review on #342: passing System.getenv(...) as an argument\nevaluated it eagerly even when the system property is set (and could throw\nSecurityException in restricted environments). pickOverride now takes a\nSupplier<String> for the env and calls it only when the property is blank.\nAdds a laziness test asserting the env supplier is never consulted when the\nproperty is set.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: trim FALKORDB_IMAGE override once in resolve()\n\nMinor cleanup: compute the trimmed override a single time instead of calling\ntrim() in both the blank-check and the assignment.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-21T17:05:59+03:00",
          "tree_id": "b18c601752d721c097bd89b87a5d9c1db8e92806",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/e4df9d500c54c7c574ed303d6d8ee20410cb0298"
        },
        "date": 1784642867661,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3435.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6033,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8928.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11995.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11128,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11306,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11346.667,
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
          "id": "2aa692a1e10beb068ec01ce8b38b34500d0dd6e5",
          "message": "ci: add a JDK runtime matrix (smoke-jdk 11/17/21) via generalized verify-jdk (#343)\n\n* ci: add a JDK runtime matrix (smoke-jdk 11/17/21) via generalized verify-jdk\n\nWave 4 - 13b (part 1). Generalize the Justfile verify-jdk8 recipe to\nverify-jdk <home> (JDK-agnostic; verify-jdk8 kept as a thin alias for the\nrequired smoke-jdk8 CI context) and add a smoke-jdk CI matrix that runs the\npackaged-artifact smoke on JDK 11/17/21 against the pinned FalkorDB. Proves\nthe Java-8 artifact loads and runs on newer JDK runtimes - the runtime\nguarantee the compile-time guards can't give.\n\nJDK 8 stays in the required smoke-jdk8 job (branch-protection context); once\nbranch protection requires smoke-jdk, 8 folds in and smoke-jdk8 retires.\n\nValidated locally: just verify-jdk on JDK 11 and verify-jdk8 (delegation) on\nJDK 8 both green against a local FalkorDB. Docs + spellcheck updated.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: correct the JDK-matrix coverage note in CONTRIBUTING\n\nAddress Copilot review on #343: the smoke-jdk matrix runs 11/17/21; JDK 8 is\ncovered by the separate required smoke-jdk8 job. Reword so CONTRIBUTING no\nlonger implies the smoke-jdk matrix itself covers 8.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T07:59:06+03:00",
          "tree_id": "70b3a52e03be0cc8de1cb25d3e21c94cb1b84149",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/2aa692a1e10beb068ec01ce8b38b34500d0dd6e5"
        },
        "date": 1784696437244,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3531.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6033.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9364,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11785.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11153.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11129,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11173.667,
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
          "id": "cec49ba3c99143a50aeff3e19eed8a8dc7489e2f",
          "message": "build: add PITest mutation testing (observability, scheduled) (#345)\n\nWave 4 - PR 14. Add pitest-maven 1.19.4 + pitest-junit5-plugin 1.2.3 to the\noff-by-default quality profile (unbound, like OWASP) - invoked via a new\n'just mutation' recipe and a scheduled/manual 'mutation' workflow, never a\nrequired gate.\n\nScoped to the pure-unit packages whose fast unit tests can actually kill\nmutants (com.falkordb.graph_entities.*, com.falkordb.impl.Utils*); *IT\n(server-backed) and the expensive jqwik property test (UtilsParamPropertyTest)\nare excluded so PIT doesn't discover + re-run them per mutation - so no\nFalkorDB server is needed.\n\nValidated locally: 'just mutation' green - 205 mutations, 193 killed (94%),\ntest strength 96%, line coverage 97%, no run/memory errors. 'just lint' still\ngreen (quality profile healthy). The scheduled job uploads the HTML report as\nan artifact.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T09:03:25+03:00",
          "tree_id": "e529170b709ef930ac75a70c3c86e8d2b498d524",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/cec49ba3c99143a50aeff3e19eed8a8dc7489e2f"
        },
        "date": 1784700306121,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3436.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6091.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9514.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12001.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11446.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11391,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11314,
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
          "id": "4dbc400bc3c76d3aa6ab55a10009f143f19c04bc",
          "message": "build: add a strict Javadoc gate (doclint=all) + fix public-API Javadoc (#346)\n\n* build: add a strict Javadoc gate (doclint=all) + fix public-API Javadoc\n\nWave 4 - PR 17 (part 1). Add a Javadoc gate in the off-by-default quality\nprofile: maven-javadoc-plugin with source 8, show=protected, doclint=all and\nfailOnWarnings=true, scoped to the public/protected API (com.falkordb.impl\nexcluded, matching the api-diff boundary). Invoked via a new 'just javadoc'\nrecipe and a new CI 'javadoc' job.\n\nTurning on the gate surfaced 40 doclint gaps on the public API (mostly\n'no main description' - block tags with no summary sentence - plus a missing\n@param <T> on Property). Fixed them all with concise summaries across\nProperty, Point, Node, Edge, GraphEntity, GraphException, ResultSet, Header,\nStatistics.\n\nValidated: 'just javadoc' green (0 warnings; the 41->0 progression proves the\ngate is active), 'just fmt-check' + 'just spellcheck' green. Documented\n'just javadoc' (and 'just mutation') in the recipe table.\n\nPages publishing (gh-pages Javadoc subdir, coordinated with the benchmark\npublisher) and the semver-policy doc are deliberate follow-ups (17b).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* build: drop javadoc <quiet> and fix 'to be add' typo\n\nAddress Copilot review on #346:\n- Remove <quiet>true</quiet> from the Javadoc gate so a doclint failure\n  prints full diagnostics in CI (its only benefit was less progress noise on\n  success, which doesn't matter).\n- Fix a pre-existing typo carried into the new summary: 'a label to be add'\n  -> 'a label to be added' (Node.addLabel).\n\njust javadoc still green (0 warnings); fmt-check green.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: correct getLabel @return (node label, not 'property label')\n\nAddress Copilot review on #346: Node.getLabel(int) returns a node label from\nthe labels list, but its @return said 'the property label'. Correct it to\n'the label at the given index'.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T09:45:07+03:00",
          "tree_id": "1f5e61139e4d36291f4717b0a2b4a26cef5bd042",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/4dbc400bc3c76d3aa6ab55a10009f143f19c04bc"
        },
        "date": 1784702806516,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3486,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5914,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8862,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11571.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10926.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10930.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10877.333,
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
          "id": "98568d6dda63d1bbb696c3f9e2a619ba08efbb4d",
          "message": "ci: publish Javadoc to gh-pages /dev/api + semver policy doc (#347)\n\n* ci: publish public-API Javadoc to gh-pages /dev/api + semver policy doc\n\nWave 4 - PR 17b (completes 17). On master pushes, generate the public-API\nJavadoc (the 'just javadoc' gate output) and publish it to the gh-pages\nbranch under /dev/api - the unreleased 'dev' docs.\n\nCoordinates with the benchmark publisher, which writes /dev/bench on the same\nbranch: this job only ever touches /dev/api (verified by a local dry-run that\nstages nothing under /dev/bench or the landing page) and pushes with a\nrebase-retry loop so a concurrent benchmark push can't race it. It does NOT\nswitch the Pages source (still the gh-pages branch), so it can't break the\nbenchmark site.\n\nAlso adds docs/semver-policy.md (references api-diff, the breaking-change\nlabel + PR-title '!' gate, javadoc, and release-please) and 'semver' to the\nspellcheck wordlist.\n\nValidated: 'just javadoc' generates target/reports/apidocs; workflow YAML\nvalid; publish script dry-run touches only /dev/api; 'just spellcheck' green.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: robust apidocs path + shallow gh-pages clone; fix semver attribution\n\nAddress Copilot review on #347:\n- Publish script accepts target/reports/apidocs OR target/site/apidocs\n  (javadoc output dir varies by Maven version), guarded by test -f.\n- Clone gh-pages shallow (--depth 1 --single-branch) instead of full history;\n  the rebase-retry still works (git deepens as needed).\n- semver-policy.md: the 0.x -> 0.(x+1) rule is the bump-minor-pre-major\n  convention configured in release-please, not SemVer clause 6 (which leaves\n  0.y.z unconstrained). Reword accordingly.\n\njust spellcheck green; workflow YAML valid.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: serialize all gh-pages publishers via a shared concurrency group\n\nAddress Copilot review on #347: the Javadoc publisher's rebase-retry only\nprotects its own push; the benchmark master publisher does a plain,\nnon-retrying git push (+ github-action-benchmark auto-push), so a concurrent\nJavadoc push could make the benchmark push fail non-fast-forward.\n\nPut both publish jobs in one shared job-level concurrency group\n'gh-pages-publish' with cancel-in-progress: false, so they queue and never\npush to gh-pages simultaneously. (Replaces the Javadoc workflow's own\nper-ref group.) The rebase-retry stays as defense-in-depth.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T10:57:48+03:00",
          "tree_id": "ce373f830a8a844dc0f5564e27577d0b63859fcf",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/98568d6dda63d1bbb696c3f9e2a619ba08efbb4d"
        },
        "date": 1784707158408,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5559.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9017.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13934.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17712.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17009.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16682.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16821.667,
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
          "id": "951295ce6b623b1ccae20042f24b21ad3820ed0a",
          "message": "docs: PR 15 plan (FalkorDB.builder() + JSpecify nullability) — for review (#348)\n\n* docs: PR 15 plan (FalkorDB.builder() + JSpecify nullability) — for review\n\nDetailed, rubber-duck-reviewed implementation plan for Wave 4 · PR 15:\na fluent `FalkorDB.builder()` covering the common connection options\n(host/port, auth, TLS, pool sizing incl. maxWait, connection/socket\ntimeouts) plus JSpecify per-member nullability on the public API.\n\nVerified against the pinned Jedis 7.5.3 / commons-pool2 2.12.1 surface.\nAdditive-only (api-diff stays green), Java-8-safe, split into 15a\n(builder) + 15b (JSpecify). Temporary artifact — reviewed but NOT\nmerged; deleted once PR 15 lands. Wordlist additions are scoped to this\ntemp branch (never merged), matching prior wave-plan PRs (#316, #329).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: address Copilot review on PR 15 plan (#282 clarity, no new public type)\n\n- §A(2): clarify Duration.ZERO — socketTimeout ZERO=0 is the #282 \"no read\n  deadline\" default; connectionTimeout defaults to 2000ms and ZERO (infinite\n  connect) is allowed but NOT the encouraged path.\n- §A(3)/§B/§D/§H: drop the public config-spec type. build() now passes the\n  Builder's resolved values directly to a public DriverImpl.create(...) factory\n  (a method on the already-public DriverImpl), so no brand-new public class\n  ships in the jar. Testable seam = package-private Builder getters + same-package\n  unit test.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T12:33:11+03:00",
          "tree_id": "0b9fff5c9080c8786b2772e9c10a90bcb62fb1ad",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/951295ce6b623b1ccae20042f24b21ad3820ed0a"
        },
        "date": 1784712914156,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 7373.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11798.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19028.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 26435.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 25996.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 24892.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 24858,
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
          "id": "59b0cced3faab043c43239ff426280ba4a0715fe",
          "message": "feat: add FalkorDB.builder() fluent configuration API (#349)\n\n* feat: add FalkorDB.builder() fluent configuration API\n\nAdds a discoverable, fluent FalkorDB.builder() covering the common\nconnection options — host/port, credentials (incl. password-only), TLS,\nconnection-pool sizing (maxTotal/maxIdle/maxWait), and connect/socket\ntimeouts as java.time.Duration. build() assembles them into the existing\nDriver via a new public DriverImpl.create(...) factory.\n\nAdditive only: the Driver interface and existing driver(...) factories are\nunchanged, so api-diff stays green. builder().build() with no options set\nproduces a driver identical to driver() (localhost:6379, no creds, no TLS,\n2000ms connect timeout, socket timeout 0 per #282, default 8-connection\npool) — locked by a defaults regression test.\n\nTLS maps through the non-deprecated SslOptions.defaults(); poolMaxWait uses\ncommons-pool2's native Duration semantics (negative = wait forever, ZERO =\nfail fast); connect/socket timeouts convert to int millis with overflow and\nsub-millisecond-rounding guards. Includes unit tests (config/pool mapping,\ndefaults, validation) and a Testcontainers IT.\n\nPart of Wave 4 (#332), PR 15a; JSpecify nullability follows as 15b.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: address Copilot review on PR 15a\n\n- DriverImpl.create(...) now validates its own arguments (host, port, pool\n  sizing, non-negative timeouts, non-null poolMaxWait) and throws\n  IllegalArgumentException, so the public factory is robust when called\n  directly instead of relying on downstream NPEs. Validation is centralized\n  there; Builder.build() only resolves defaults + converts Durations.\n- Fix toTimeoutMillis Javadoc: it never receives null (build() maps null to\n  the default), so it does not claim to reject null.\n- ConfigBuilderIT @AfterEach wraps cleanup in try/finally so driver.close()\n  always runs even if deleteGraph() throws (no pool leak / IT flakiness).\n- Add DriverConfigTest coverage for create() argument validation.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: trim host before use in DriverImpl.create (Copilot review)\n\ncreate() validated host with trim() but passed the original padded string\nto HostAndPort, so \" localhost \" passed validation yet could fail DNS\nresolution. Normalize the host once (trim) and use the trimmed value, and\ncover it with a builder test.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T13:52:24+03:00",
          "tree_id": "5476d0edda668e315f5c4c6a2b1e9cfdb7b9b43d",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/59b0cced3faab043c43239ff426280ba4a0715fe"
        },
        "date": 1784717653516,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4245.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6965.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10862,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13511,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12913,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12883.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12793.667,
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
          "id": "41ae17794b75d3f763f516d7338fd732dcd406c7",
          "message": "ci: make the PR benchmark a same-machine head-vs-base A/B (#350)\n\n* build: add same-machine bench-compare (script + just recipe)\n\n* ci: make the PR benchmark a same-machine head-vs-base A/B\n\nThe benchmark-pr job compared the PR against a stored gh-pages baseline that\nwas captured on a different hosted runner, so runner-speed variance produced\nfalse ~2x regression alerts (observed on #349). Replace that with a\nsame-machine A/B: benchmark the PR head and its base back-to-back on one\nrunner via 'just bench-compare' and compare the two directly, posting a\nsticky comparison comment. Regressions are reported but non-blocking; the\nmaster job keeps publishing the gh-pages trend/curve.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: harden bench-compare (self-review)\n\n- compare_bench.py: None-safe throughput/p50 value cells so a base/head\n  metric-name mismatch (e.g. a renamed metric) reports 'n/a' instead of\n  crashing with a TypeError.\n- benchmark.yml: make the sticky-comment write best-effort so a fork PR's\n  read-only GITHUB_TOKEN (403) does not fail the otherwise-green job (the\n  report is still in the job summary); guard on a non-empty file (-s).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: bench-compare temp-dir cleanup, clean-tree guard, docstring (Copilot review)\n\n- Justfile: trap a cleanup that restores the ref AND removes the mktemp dir\n  for the whole script (no more leaked temp dirs); require a fully clean tree\n  (drop --untracked-files=no) so a cross-ref checkout can't be blocked/clobbered.\n- compare_bench.py: docstring now matches behavior (report-only exit 0 by\n  default; exit 1 only with --fail-on-regression; exit 2 on no comparable data).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: bench-compare non-positive ratio guard + rename ratio column (Copilot)\n\n- compare_bench.py slowdown(): treat any missing OR non-positive base/head\n  value as non-comparable (return None -> 'n/a') for both throughput and\n  latency, so a 0 throughput / 0 percentile no longer yields a misleading 0x.\n- Rename the misnamed 'Δ' columns to 'ratio' (they show head/base ratios).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T14:48:51+03:00",
          "tree_id": "188643fdb414fc94699578140bf5f097f86eb7fb",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/41ae17794b75d3f763f516d7338fd732dcd406c7"
        },
        "date": 1784721047485,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4214.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6888.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10723.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13780,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12991,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12615.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12578.333,
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
          "id": "6812aa98ddf9db98995869e51fa1cc67e4e343f9",
          "message": "feat: add JSpecify nullability to the public API (#351)\n\n* feat: add JSpecify nullability to the public API\n\nAdds org.jspecify:jspecify 1.0.0 (compile scope, Java-8 bytecode) and marks\nthe three public API packages @NullMarked (com.falkordb, .graph_entities,\n.exceptions) via package-info.java, so every public type is non-null by\ndefault. Genuinely-nullable members are annotated @Nullable:\n\n- GraphEntity.getProperty (return), addProperty value param\n- Property name/value (fields, ctor, getters, setters)\n- Edge.relationshipType (field, getter, setter)\n- Record.getValue(int|String) (return)\n- Statistics.getStringValue + Statistics.Label.getEnum (returns)\n- GraphTransaction.exec (return — null on a WATCH-aborted MULTI/EXEC)\n- Driver.udfList libraryName (optional filter param)\n- GraphException message/cause ctor params\n\ncom.falkordb.impl stays unmarked (internal, api-diff-excluded). The shipped\nartifact remains Java-8: the javadoc gate pins release 11 so @NullMarked's\nJava-9 ElementType.MODULE @Target resolves (doc-only; compiler stays\nrelease 8, Enforcer + Animal Sniffer + the JDK-8 smoke unchanged), and the\ndefault compiler silences the spurious classfile warning. The smoke-test\nadds a JDK-8 reflection check that GraphEntity.getProperty carries a\nruntime-visible @Nullable.\n\nPart of Wave 4 (#332), PR 15b (completes item 15 with 15a #349).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: address Copilot + CodeRabbit review on 15b\n\n- GraphEntity.addProperty(Property): reject a null property name so it can\n  never leak a null key into the @NullMarked getEntityPropertyNames() (raised\n  by both reviewers). ResultSetImpl already setName(...)s before adding, so\n  deserialization is unaffected.\n- Document the newly-@Nullable contracts in Javadoc @return tags:\n  Property.getName/getValue, Edge.getRelationshipType, Record.getValue(int|\n  String), Statistics.Label.getEnum.\n- package-info.java (x3): reword to the JSpecify-accurate \"an unannotated type\n  usage is non-null\" (annotated @Nullable are the exceptions).\n\ngetProperty keeps its raw Property return: widening it to Property<?> trips the\napi-diff gate (METHOD_RETURN_TYPE_GENERICS_CHANGED), so that (source/binary-safe\nbut gate-flagged) generics polish is deferred to a breaking-change-labelled PR.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T16:00:41+03:00",
          "tree_id": "45cc4071543d0b9a33dd211d28ac9d512f1a28bb",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/6812aa98ddf9db98995869e51fa1cc67e4e343f9"
        },
        "date": 1784725343657,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3371.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6098,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9468.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11979,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11264.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11209.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11198.667,
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
          "id": "57b90f483e3236f74f8d410077ba16fe48645d81",
          "message": "docs: add runnable examples module (FalkorDB.builder()) (#352)\n\n* docs: add runnable examples module + finish Wave 4\n\nAdds a standalone, non-deployable examples/ Maven module with runnable\npublic-API examples that showcase FalkorDB.builder() (from 15a):\n- QuickStart: build a driver, run queries, iterate results\n- ConfiguredDriver: the full builder config surface (credentials, TLS, pool\n  sizing, timeouts)\n\nCompiled in CI with --release 8 against the built jar (new `examples` gate +\n`just examples` recipe) so the documented examples can't drift from the API.\nNot part of the reactor or the published artifact.\n\nWave-4 wrap-up: deletes the temporary pr15-config-builder-plan.md and prunes\nthe throwaway wordlist terms it added (spellcheck stays green).\n\nCloses #332.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: reword examples README to drop 'classpath' (Copilot review)\n\nexamples/README.md is not in the spellcheck scope (the pyspelling sources are\nroot '*.md' + 'docs/*.md', both non-recursive), so this never failed the gate,\nbut rewording removes the pruned term and future-proofs against a glob change.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: make QuickStart graph cleanup best-effort (Copilot review)\n\nWrap deleteGraph() in the finally so a cleanup failure can't mask a real\nerror thrown by the queries above — good practice to demonstrate in the\nexample.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T16:38:35+03:00",
          "tree_id": "5c08e325361a815de4f05cfe79c1b64ffd922ce9",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/57b90f483e3236f74f8d410077ba16fe48645d81"
        },
        "date": 1784727616868,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3273,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5965.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9548.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11752.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10988,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11229.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11176.333,
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
          "id": "d9bca2f708ac80adc7c0a8a90196d0fd1ce241d4",
          "message": "perf: de-pin GraphCacheList cache refresh for virtual threads (#354)\n\n* perf: de-pin GraphCacheList cache refresh for virtual threads\n\nReplace the `synchronized (refreshLock)` in GraphCacheList.getCachedData —\nheld across a blocking `graph.callProcedure(...)` — with a ReentrantLock\n(lock/finally-unlock, same double-checked refresh). On JDK 21-23 a\n`synchronized` monitor held across a blocking call pins the carrier thread,\nso many concurrent queries on virtual threads would not scale; a\nReentrantLock across the same call does not pin (and stays reentrant).\nclear() now takes the same lock so it can't interleave with an in-flight\nrefresh.\n\nThis is our only pinning site. Cold connection creation inside\ncommons-pool2's GenericObjectPool.create() is itself synchronized and\nremains an upstream pinning path (documented; mitigate by warming the pool).\n\nWave 5 (#333), Track 1 · PR A.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: fail fast on worker hang in GraphCacheList concurrency test\n\nBound the latch await and worker Thread.join() with a 10s timeout and\nassert each worker terminated, so a regression that deadlocks the refresh\npath fails the test fast instead of hanging the whole suite.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T18:11:37+03:00",
          "tree_id": "86aab86814146fec1da41313d539e151db2ccd96",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/d9bca2f708ac80adc7c0a8a90196d0fd1ce241d4"
        },
        "date": 1784733217854,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3692.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6307.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9374.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11874.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11328.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11234.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11312,
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
          "id": "f6410c3c18e82368630617bc9dbdfc409f90ee01",
          "message": "feat: add CompletableFuture async facade (AsyncGraph / AsyncFalkorDB) (#355)\n\n* feat: add CompletableFuture async facade (AsyncGraph / AsyncFalkorDB)\n\nAdd an optional asynchronous view over the synchronous blocking Graph API so\ncallers on JDK 21+ can fan the blocking client out over a virtual-thread (or any)\nexecutor while the client itself stays pure Java 8.\n\n- com.falkordb.AsyncGraph: public interface mirroring Graph's 17 query ops, each\n  returning CompletableFuture<T>, plus a graph() escape hatch that returns the\n  wrapped concurrency-safe GraphContextGenerator (never a connection-bound Graph).\n- com.falkordb.AsyncFalkorDB.wrap(GraphContextGenerator, Executor): factory. The\n  facade owns nothing (not Closeable); the caller owns the graph and the executor.\n- com.falkordb.impl.api.AsyncGraphImpl: internal impl via CompletableFuture.supplyAsync.\n\nPure Java 8 and additive (api-diff green); the caller supplies the JDK-21 executor,\nso no library type references Loom. Cancellation is best-effort but honors\ncancel-before-start; failures surface as the future completing exceptionally.\n\nTests: AsyncFalkorDBTest (unit) covers delegation of all 17 ops, exception\nunwrapping, cancel-before-start, null-arg rejection, and the graph() escape hatch\nwith hand-rolled fakes (no Mockito). AsyncFalkorDBIT (server) fans out over a fixed\nplatform-thread pool with a finite poolMaxWait — deliberately not virtual threads,\nsince cold commons-pool2 connection creation pins carriers on JDK 21-23 (that\npinning is characterized separately by the Wave-5 pinning check).\n\nWave 5 (#333) item: CompletableFuture async facade.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: honor ResultSet non-null contract and close the graph in async IT\n\n- FakeResultSet.getStatistics()/getHeader() now throw UnsupportedOperationException\n  instead of returning null, honoring the @NullMarked ResultSet contract (the fake\n  is an identity sentinel and these are never called).\n- AsyncFalkorDBIT.cleanup() now closes the GraphContextGenerator (clears its cache)\n  in addition to the Driver, matching the facade's caller-owns-the-graph contract;\n  ordered deleteGraph -> client.close -> executor.shutdownNow -> driver.close.\n\nAddresses Copilot review on #355.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: async facade returns a failed future on executor rejection; bound IT shutdown\n\n- AsyncGraphImpl now submits via a submit(Supplier<T>) helper that catches\n  RejectedExecutionException from CompletableFuture.supplyAsync and returns a\n  future completed exceptionally, so AsyncGraph methods always return a future\n  (honoring the exceptional-completion contract) even if the executor is\n  saturated or shut down. New unit test covers this (never reaches the graph).\n- AsyncFalkorDBIT.cleanup() now bounds executor.shutdownNow() with a\n  awaitTermination(10s) and restores the interrupt flag, so non-daemon pool\n  threads can't linger and hang the JVM.\n\nAddresses Copilot re-review on #355.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-22T21:06:11+03:00",
          "tree_id": "6b1abb19e6e922b5c0b88aa3bd708e34fbcc144f",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f6410c3c18e82368630617bc9dbdfc409f90ee01"
        },
        "date": 1784743669177,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3529,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5984,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8848,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11828.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11208.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11304.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11165.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "760374cdf0ec53d5e816e0a4cc63c48518726b5d",
          "message": "build(deps-dev): bump org.pitest:pitest-maven from 1.19.4 to 1.25.8 (#357)\n\nBumps [org.pitest:pitest-maven](https://github.com/hcoles/pitest) from 1.19.4 to 1.25.8.\n- [Release notes](https://github.com/hcoles/pitest/releases)\n- [Commits](https://github.com/hcoles/pitest/compare/1.19.4...1.25.8)\n\n---\nupdated-dependencies:\n- dependency-name: org.pitest:pitest-maven\n  dependency-version: 1.25.8\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-07-23T08:39:12+03:00",
          "tree_id": "d2cdea8a8a6ebc1d5751faff81022ddd787d5892",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/760374cdf0ec53d5e816e0a4cc63c48518726b5d"
        },
        "date": 1784785284193,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4353,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7215.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10720.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13711.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13169,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12939,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12934.667,
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
          "id": "08251f69b4f9286d50808edf8173445910ca1673",
          "message": "docs: document virtual-thread concurrency and pool tuning (Wave 5) (#358)\n\n* docs: document virtual-thread concurrency, pool sizing, and warm-up (Wave 5)\n\nAdd a 'Concurrency with Virtual Threads (JDK 21+)' README section covering the\nrecommended model (virtual-thread-per-task executor + the blocking client):\n- fanning driver.graph(id) out over virtual threads;\n- the optional AsyncGraph / AsyncFalkorDB.wrap CompletableFuture facade;\n- sizing the pool for fan-out (poolMaxTotal caps real concurrency; no built-in\n  admission bound; finite poolMaxWait/socketTimeout);\n- warming the pool to avoid JDK 21-23 cold-creation carrier pinning, requiring\n  both poolMaxTotal >= N and poolMaxIdle >= N, with the JEP 491 (JDK 24) note and\n  a pointer to the scheduled pinning check.\n\nCompletes the Wave 5 (Project Loom) documentation and pool-sizing items.\n\nCloses #333\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: make the virtual-thread snippets self-contained\n\nDeclare the Driver (and note shutdown/close) in the AsyncGraph and pool-warm-up\nsnippets so each compiles when copied on its own.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: correct AsyncGraph close guidance and warm-up shutdown\n\n- The facade caller closes the underlying Driver (which releases pooled\n  connections), not the GraphContextGenerator (whose close() only clears its\n  cache); reword accordingly.\n- Await pool termination in the warm-up example so all N connections are back in\n  the pool before the workload starts.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: bound the warm-up snippet's waits and check awaitTermination\n\nGive ready.await() a timeout and act on awaitTermination's result (shutdownNow on\ntimeout) so the illustrative warm-up can't block indefinitely.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: drain the virtual-thread executor before the pool closes in the fan-out example\n\nAwait executor termination after shutdown() so in-flight queries finish before the\ntry-with-resources closes the Driver/pool.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T09:26:19+03:00",
          "tree_id": "f2a26685cf066aa2b4d7869692ca514f5ecf8b62",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/08251f69b4f9286d50808edf8173445910ca1673"
        },
        "date": 1784788100946,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3513.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6188.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9329,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12198.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11537.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11588,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11595,
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
          "id": "f42a18017d5834165316cb080c55244178957218",
          "message": "ci: anchor release-please's first changelog to the v0.9.0 commit (#359)\n\nPin the package with last-release-sha = the v0.9.0 commit\n(23723b12ff353b57166aa2b0a3f01349d0eaacf9). Because the bootstrap manifest is\nseeded at 0.9.1-SNAPSHOT (there is no such tag), release-please's first run had\nno anchor and walked the entire history back to v0.1.0, listing ~24 already-shipped\nfixes/dependency bumps (2020-2024) under 0.10.0. With the anchor, the first\nchangelog spans only v0.9.0..HEAD (the real 0.10.0 changes). It is a no-op once a\nreal release tag exists. Document the anchor in the setup guide's bootstrap note.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T09:53:42+03:00",
          "tree_id": "1178f8de748e5af50d775a9b12024901b7cd9fb9",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f42a18017d5834165316cb080c55244178957218"
        },
        "date": 1784789738382,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5099.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9259.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13632.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18160,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17353.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16774.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16645.667,
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
          "id": "f6c11871a3414b226ad88f08bb2194d436150f16",
          "message": "ci: move release-please last-release-sha to top-level so the first changelog trims (#360)\n\nThe last-release-sha added in #359 was placed inside the package config, where\nrelease-please ignores it, so the first 0.10.0 changelog still walked all history\nback to v0.1.0 (36 entries). last-release-sha is a top-level manifest-config option;\nmoving it there anchors the first run to the v0.9.0 commit. Verified via\n`release-please --dry-run --target-branch`: 36 -> 13 entries, all post-v0.9.0\n(the ancient dependency bumps and typo'd commit messages are gone).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T10:19:45+03:00",
          "tree_id": "cc92175955b80b0ecb581c0bc4326f32a3ba35c5",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f6c11871a3414b226ad88f08bb2194d436150f16"
        },
        "date": 1784791378125,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4291.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7052.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10837.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 14155,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13317,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 13206.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 13218,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "67070e87ef18d62ff331e65f29f1a5fdfff8fe54",
          "message": "chore(master): release 0.10.0 (#331)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-07-23T10:26:55+03:00",
          "tree_id": "4625e2b3c8347f1be00315105f43217e2f1b0f70",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/67070e87ef18d62ff331e65f29f1a5fdfff8fe54"
        },
        "date": 1784791730354,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4088.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7223.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10867.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13722.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12761.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12974.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12944.333,
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
          "id": "584ea7a4ce317ccfcd914c503078e8d8be767577",
          "message": "build: bump api.diff.baseline to 0.10.0 (#362)\n\nNow that 0.10.0 is released to Maven Central, compare the public API against it\n(the documented post-release step) so the api-diff gate reflects the new baseline.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T11:53:08+03:00",
          "tree_id": "8192927ba44254839669c20b7465ef4fd8a04010",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/584ea7a4ce317ccfcd914c503078e8d8be767577"
        },
        "date": 1784796898801,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3444.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6115.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9259.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11531.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11075.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11043.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11103,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "de4ebc999cd0a127c7f5326eaf1cc0f1e30fe285",
          "message": "build(deps): bump github/codeql-action/init from 4.37.1 to 4.37.3 (#364)\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.37.1 to 4.37.3.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/7188fc363630916deb702c7fdcf4e481b751f97a...e4fba868fa4b1b91e1fdab776edc8cfbe6e9fb81)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.37.3\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-07-27T14:07:19+03:00",
          "tree_id": "a69bdb443ac73db28b6063e71adcba8ce505791b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/de4ebc999cd0a127c7f5326eaf1cc0f1e30fe285"
        },
        "date": 1785150527309,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4073.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6846.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10471,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13605,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12592.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12384.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12318.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "73c3e0319ad8e30c19514da5ddf79140d44e1976",
          "message": "build(deps): bump actions/checkout from 7.0.0 to 7.0.1 (#365)\n\nBumps [actions/checkout](https://github.com/actions/checkout) from 7.0.0 to 7.0.1.\n- [Release notes](https://github.com/actions/checkout/releases)\n- [Changelog](https://github.com/actions/checkout/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/actions/checkout/compare/9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0...3d3c42e5aac5ba805825da76410c181273ba90b1)\n\n---\nupdated-dependencies:\n- dependency-name: actions/checkout\n  dependency-version: 7.0.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-07-27T14:11:26+03:00",
          "tree_id": "58eab8b2c0f4ea2883776fbad4c878ae24c55d49",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/73c3e0319ad8e30c19514da5ddf79140d44e1976"
        },
        "date": 1785150809768,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3750.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6530.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9452.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12132.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11605.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11463.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11516.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "19bb0729ae89a21e035dbcd35d0c95915109786a",
          "message": "build(deps): bump github/codeql-action/analyze from 4.37.1 to 4.37.3 (#366)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.37.1 to 4.37.3.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/7188fc363630916deb702c7fdcf4e481b751f97a...e4fba868fa4b1b91e1fdab776edc8cfbe6e9fb81)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.37.3\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-07-27T14:20:15+03:00",
          "tree_id": "7522e4b1e0834a0f21177964551d41135078ea4e",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/19bb0729ae89a21e035dbcd35d0c95915109786a"
        },
        "date": 1785151301482,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3310.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5943.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8785,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11682.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11034,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11063.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10951,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c47b1cb0e9e8dacbc2d050f76428b53dd40679ac",
          "message": "build(deps): bump actions/upload-artifact from 4.6.2 to 7.0.1 (#367)\n\nBumps [actions/upload-artifact](https://github.com/actions/upload-artifact) from 4.6.2 to 7.0.1.\n- [Release notes](https://github.com/actions/upload-artifact/releases)\n- [Commits](https://github.com/actions/upload-artifact/compare/ea165f8d65b6e75b540449e92b4886f43607fa02...043fb46d1a93c77aae656e7c1c64a875d1fc6a0a)\n\n---\nupdated-dependencies:\n- dependency-name: actions/upload-artifact\n  dependency-version: 7.0.1\n  dependency-type: direct:production\n  update-type: version-update:semver-major\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-07-27T14:24:56+03:00",
          "tree_id": "23c1baf32251702ef63d4ee2cae6e1c1dbcedf52",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/c47b1cb0e9e8dacbc2d050f76428b53dd40679ac"
        },
        "date": 1785151615411,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4314.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6911.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10117,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13756,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13030.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12914.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12762.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "64fd405842c5349f04a9b5d0724ed8e7a7deb5b0",
          "message": "build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin (#368)\n\nBumps [com.diffplug.spotless:spotless-maven-plugin](https://github.com/diffplug/spotless) from 3.8.0 to 3.9.0.\n- [Release notes](https://github.com/diffplug/spotless/releases)\n- [Changelog](https://github.com/diffplug/spotless/blob/main/CHANGES.md)\n- [Commits](https://github.com/diffplug/spotless/compare/maven/3.8.0...maven/3.9.0)\n\n---\nupdated-dependencies:\n- dependency-name: com.diffplug.spotless:spotless-maven-plugin\n  dependency-version: 3.9.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-07-29T07:33:46+03:00",
          "tree_id": "d5b8c2cc947dc4f2f63a0023a3a3ff09f153f2b8",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/64fd405842c5349f04a9b5d0724ed8e7a7deb5b0"
        },
        "date": 1785299742110,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3689.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6487.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9442.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12140.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11631,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11578,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11411.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "0ee98516a76e863f160b52458279852364837b3f",
          "message": "build(deps): bump org.jspecify:jspecify from 1.0.0 to 1.0.1 (#369)\n\nBumps [org.jspecify:jspecify](https://github.com/jspecify/jspecify) from 1.0.0 to 1.0.1.\n- [Release notes](https://github.com/jspecify/jspecify/releases)\n- [Commits](https://github.com/jspecify/jspecify/compare/v1.0.0...v1.0.1)\n\n---\nupdated-dependencies:\n- dependency-name: org.jspecify:jspecify\n  dependency-version: 1.0.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-07-30T15:36:10+03:00",
          "tree_id": "f3896d7640350b69e5276db41ae2d9b286814494",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/0ee98516a76e863f160b52458279852364837b3f"
        },
        "date": 1785415104864,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5499.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10455.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 16945.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 21338.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 20337.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 20508,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 20574.667,
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
          "id": "144c5929b5c8ffd79b058f81b15c38ecf9052f95",
          "message": "build: combine Dependabot updates (setup-java, codeql action) (#373)\n\nCombines the Dependabot updates from #370, #371 and #372 into a\nsingle PR:\n- Bump actions/setup-java from 5.6.0 to 5.7.0 (all workflows)\n- Bump github/codeql-action init from 4.37.3 to 4.37.4\n- Bump github/codeql-action analyze from 4.37.3 to 4.37.4\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-03T10:57:53+03:00",
          "tree_id": "ef99f352686701b3f647591178aef21e3e718121",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/144c5929b5c8ffd79b058f81b15c38ecf9052f95"
        },
        "date": 1785744049679,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3497.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6274.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9149,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11964,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11380.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11227.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11217,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "42d2958234b1be5e0809e9abe171da940c61e29a",
          "message": "Remove redundant CODE_OF_CONDUCT.md in favour of the org-wide default (#374)",
          "timestamp": "2026-08-06T10:54:29+03:00",
          "tree_id": "f12ccad1d0d5d07576227963408d2e08a81cf775",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/42d2958234b1be5e0809e9abe171da940c61e29a"
        },
        "date": 1786002986471,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4245.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7092.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9965.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13498,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12556.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12337.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12787,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c21b86ab39d7abe10e995f07ccc76fc2b1327fcd",
          "message": "build(deps-dev): bump org.pitest:pitest-maven from 1.25.8 to 1.25.9 (#376)\n\nBumps [org.pitest:pitest-maven](https://github.com/hcoles/pitest) from 1.25.8 to 1.25.9.\n- [Release notes](https://github.com/hcoles/pitest/releases)\n- [Commits](https://github.com/hcoles/pitest/compare/1.25.8...1.25.9)\n\n---\nupdated-dependencies:\n- dependency-name: org.pitest:pitest-maven\n  dependency-version: 1.25.9\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-08-12T23:16:57+03:00",
          "tree_id": "dcc5060c8d8b693d2195c5ccc60e33cf5190e2ae",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/c21b86ab39d7abe10e995f07ccc76fc2b1327fcd"
        },
        "date": 1786566014740,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4193.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6834.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10637.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13855.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12962,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12864,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12834,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f1605be28d1c3f1445b520e74cdb6bec8ef07e2b",
          "message": "ci(maven): verify against latest on PRs, run the edge canary daily (#379)\n\n* ci(maven): verify against latest on PRs, run the edge canary daily\n\nThe required `build`/`smoke-*` jobs pin a FalkorDB digest (v4.20.1, the\nminimum supported version), so nothing in PR CI exercised the image most\nusers actually run. A new `verify-latest` job runs the full `just verify`\nsuite against `falkordb/falkordb:latest` on pull requests, pushes, tags\nand published releases, using the existing `FALKORDB_IMAGE` Testcontainers\noverride.\n\nThe version canary (edge + latest) moves from weekly to daily so\nregressions in unreleased FalkorDB builds surface within a day. Both\nremain non-blocking for the pinned-digest gates.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* refactor(ci): select the FalkorDB image via a FALKORDB_VERSION variable\n\nAligns with the other clients: the released-image gate reads a workflow-level\n`FALKORDB_VERSION` and derives `FALKORDB_IMAGE` from it, and the canary\nmatrixes over bare version tags instead of full image references.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: address review feedback on the FalkorDB image tag workflow\n\n- The `falkordb_image_tag` dispatch input defaulted to an empty string while its\n  description promised `latest`; it now defaults to `latest` so the form matches\n  the documented behaviour. Scheduled runs still resolve to `edge`, because\n  `schedule` events carry no inputs.\n- falkordb-ts and falkordb-php publish bare semver tags (`6.7.0`), which `v*`\n  never matched, so tagged releases skipped CI. Both patterns are now accepted.\n- falkordb-rs coverage had no tag trigger at all.\n- JFalkorDB documented a manual tag override that did not exist: `maven.yml` now\n  defines the dispatch input it reads.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: report scheduled edge failures as a tracking issue\n\nA daily cron has nobody watching it: GitHub only emails the user who last\ntouched the cron line, and only if that person has Actions notifications on. An\n`edge` regression would therefore have failed silently.\n\nScheduled runs that fail now open an issue titled \"CI is failing against\nfalkordb/falkordb:edge\", or comment on it if it is already open, so repository\nwatchers are notified through the normal issue flow.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* ci: post scheduled edge failures to Google Chat\n\nReplaces the tracking-issue notification with a Google Chat webhook post, read\nfrom the `GOOGLE_CHAT_WEBHOOK_URL` secret. The issue is kept only as a fallback\nfor when that secret is not configured, so the daily run can never fail\nsilently.\n\nThe message text is assembled with jq rather than string-interpolated, and no\n`${{ }}` expression is expanded into the shell.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-12T23:23:56+03:00",
          "tree_id": "ee44e6368325ffc02f4bc1067713e18152e106ca",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f1605be28d1c3f1445b520e74cdb6bec8ef07e2b"
        },
        "date": 1786566377380,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3764.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6606.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9315.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12486.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11542,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11787,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11751,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "e91cdbdd65c0b26be3bc4e3c9504a7c6c9d5160e",
          "message": "build(deps): bump github/codeql-action/analyze from 4.37.4 to 4.37.6 (#377)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.37.4 to 4.37.6.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/f205ea1c3313d32999d8d6a48b4f6530d4437b38...5595ccaf912efad79be6eef63a5619ff05969be3)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.37.6\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>",
          "timestamp": "2026-08-12T23:29:14+03:00",
          "tree_id": "1932ec08b676589ee83748c223b8a0361bb9daab",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/e91cdbdd65c0b26be3bc4e3c9504a7c6c9d5160e"
        },
        "date": 1786568434377,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3390.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5912.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8771,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11678.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11169,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11127.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11126.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f73cb61cc58b2c2793e3c3c305ffae64089a5816",
          "message": "ci: rename Google Chat webhook secret to DRIVERS_GOOGLE_CHAT_WEBHOOK_URL (#381)\n\nThe webhook posts CI failures for the FalkorDB client drivers, so name\nthe secret accordingly and keep it distinct from other Google Chat\nwebhooks used elsewhere in the organization.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-13T12:54:07+03:00",
          "tree_id": "37c1d8151134d3bbc817b38a0fbeb138cd15d52d",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f73cb61cc58b2c2793e3c3c305ffae64089a5816"
        },
        "date": 1786614942397,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5166.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9145,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14379,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18913.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17770,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17729.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 17652.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f197a4603aff67091da7986eede459b552b2a369",
          "message": "fix: four correctness bugs in reply parsing and procedure encoding (#383)\n\n* fix: decode the GRAPH.DELETE reply in transactional deleteGraph\n\nGraphTransactionImpl.deleteGraph() built its response with `return (String) o`,\nbut Jedis hands builders a byte[] for a bulk-string reply, so reading the\nresponse always threw `ClassCastException: [B cannot be cast to\njava.lang.String`. GraphPipelineImpl already uses BuilderFactory.STRING for the\nsame command; use it here too.\n\nNo test covered this path — TransactionIT.deleteGraph() is the @AfterEach\ncalling api.deleteGraph(), not the transactional overload — so add an IT that\nreads the response inside MULTI/EXEC.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: emit YIELD when callProcedure is given output columns\n\nUtils.prepareProcedure appended the kwargs entries straight after the closing\nparen with no keyword and no separator, so every\ncallProcedure(procedure, args, kwargs) call sent invalid Cypher. The server\nrejects it outright:\n\n    errMsg: Invalid input 'a': expected LOAD CSV\n    errCtx: CALL db.labels()label\n\nUtilsTest asserted the broken output verbatim, so the suite was protecting the\nbug; its expectations are corrected here and a server-backed IT now proves a\nkwargs call actually round-trips.\n\nAlso document the \"y\" kwargs key on the four public interfaces that expose the\noverload — it selects the YIELD columns, and was previously undiscoverable.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: give Statistics value equality and keep colons in values\n\nStatisticsImpl kept the raw List<byte[]> and included it in equals/hashCode.\nbyte[] inherits identity equals/hashCode, so two result sets carrying identical\nstatistics compared unequal and hashCode varied between instances — and that\nleaked into ResultSetImpl.equals/hashCode, making ResultSet unusable as a map\nkey. HeaderImpl already excludes its raw field for exactly this reason;\nEqualsVerifier missed it because it shallow-copies the same array instances.\n\nThe parse also split on every colon, so a statistic whose value contains one\n(\"Query internal execution time: 0:00.4\") produced three parts, failed the\nlength==2 guard, and was dropped silently. Only the first colon is a delimiter.\n\nParse once in the constructor into an unmodifiable map instead of lazily\nmutating a non-volatile EnumMap on first read: it removes the unsynchronized\nlazy init (ResultSets are handed across threads by AsyncGraph), removes the\nre-parse on every call when nothing matched, and makes the value semantics\nabove structural rather than something equals has to work around.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: handle an empty reply in the ResultSetImpl constructor\n\nThe run-time-error probe read get(rawResponse.size() - 1) before any empty\ncheck, so an empty array reply threw IndexOutOfBoundsException instead of\nproducing a result set — and left the deliberate rawResponse.isEmpty() handling\nfurther down unreachable as dead code.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5 (1M context) <noreply@anthropic.com>",
          "timestamp": "2026-08-13T15:18:21+03:00",
          "tree_id": "fd88ee72016d6b0cfcbf056451bf1716a43f34aa",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f197a4603aff67091da7986eede459b552b2a369"
        },
        "date": 1786623603123,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6115.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 12245.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 18989,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24375.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 23218,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23060.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 22859.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "377a2aa90d591b5eafa4249c03e4e766b387afbf",
          "message": "ci: use the shared report-scheduled-failure workflow (#382)\n\n* ci: use the shared report-scheduled-failure workflow\n\nEvery official FalkorDB client carries a byte-identical copy of this job, so\nany change to the alert has to be made in eight places. Call the reusable\nworkflow in FalkorDB/.github instead and keep only the gating that is specific\nto this repo.\n\nBehaviour is unchanged: the `github` context inside a called workflow belongs\nto the caller, so the repository, workflow name and run URL in the alert stay\nthe same.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix(ci): pin the shared reporter and pass only its declared secret\n\nReview feedback: `secrets: inherit` combined with a mutable `@main` ref would\nlet any later change to the shared workflow run with this repo's secrets. Pin\nto a reviewed commit SHA and map only DRIVERS_GOOGLE_CHAT_WEBHOOK_URL, which is\nthe single secret the workflow declares.\n\nPinning does not reintroduce the duplication this removed: Dependabot's\ngithub-actions ecosystem updates `jobs.<id>.uses` pins, and it is already\nenabled here.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix(ci): bump shared reporter pin to include the label fix\n\nPicks up FalkorDB/.github#13, which creates the tracking label only when it is\nmissing rather than overwriting a pre-existing label of the same name.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-13T15:24:23+03:00",
          "tree_id": "e2f06be873d8ae24057e743738021979d0d90be3",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/377a2aa90d591b5eafa4249c03e4e766b387afbf"
        },
        "date": 1786623954617,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7016,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10670,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13879.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13052.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12495.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12514.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "d921a1d4de3aa8659802b7374e33551202cc2bf6",
          "message": "chore(master): release 0.10.1-SNAPSHOT (#363)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-08-13T15:25:59+03:00",
          "tree_id": "5bc93b7a2d1cd57923da5b7cf7872e6f0351a8ff",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/d921a1d4de3aa8659802b7374e33551202cc2bf6"
        },
        "date": 1786624071691,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3645.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6269.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8973,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11667.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11308.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11179.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11197,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "5ad740955dba1cfa216b6d77c91c8759dbb90aeb",
          "message": "chore(master): release 0.10.1 (#384)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-08-13T15:43:15+03:00",
          "tree_id": "d3f392aa95f59ea819e96d42a5036008a4a16864",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/5ad740955dba1cfa216b6d77c91c8759dbb90aeb"
        },
        "date": 1786625113640,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4425.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7115.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10860.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13430,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12502,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12622,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12883,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "106679ad8ea29ee9de3c4726a5a6e5361e289459",
          "message": "chore(master): release 0.10.2-SNAPSHOT (#387)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-08-13T15:49:40+03:00",
          "tree_id": "eede3335ed62efbd311a2860c370a4305b045e53",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/106679ad8ea29ee9de3c4726a5a6e5361e289459"
        },
        "date": 1786625480537,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5695,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10085.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 16168,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 21543.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 20351.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 20009.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 19475.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "236e04d3e2f6734276a443fe9e7b1335563e9570",
          "message": "build: bump api.diff.baseline to 0.10.1 (#388)\n\nv0.10.1 is published on Maven Central, so the api-diff gate should compare\nagainst it rather than 0.10.0. This is the manual post-release step the pom\ncomment calls out (#362 did the same for 0.10.0).\n\nVerified locally: japicmp resolves jfalkordb-0.10.1.jar from Central and\nreports \"No changes.\" against master.\n\nCo-authored-by: Claude Opus 5 (1M context) <noreply@anthropic.com>",
          "timestamp": "2026-08-13T16:02:25+03:00",
          "tree_id": "f17b0cc335964aa69b8328b2b2f22edb7ae0b6d7",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/236e04d3e2f6734276a443fe9e7b1335563e9570"
        },
        "date": 1786626301793,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3663,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6333.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9563,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11887,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11362.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11279.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11274,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "34807727+Naseem77@users.noreply.github.com",
            "name": "Naseem Ali",
            "username": "Naseem77"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "495b4297271e64f360d77cfb047fa9fdeb378234",
          "message": "ci: alert on a cancelled scheduled run, not just a failed one (#394)\n\n`report-scheduled-failure` only fired on `failure`. A job stopped by a\ntimeout, or by GitHub's six hour ceiling, reports `cancelled`, so the\nalert was skipped in exactly the case it exists for: a nightly that hangs\nagainst a moving `edge` image.\n\nAlso caps the job at 30 minutes. Recent runs finish in under two, so a\nrun anywhere near the cap is stuck, and failing at 30 minutes reports a\nresult the same morning instead of holding a runner all day.\n\nThe `github.event_name == 'schedule'` guard is kept, so a force push that\ncancels an in flight PR run still does not raise an alert.",
          "timestamp": "2026-08-16T15:24:03+03:00",
          "tree_id": "78807b26d36c7f3e5833c11a6c131e73f9a0b523",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/495b4297271e64f360d77cfb047fa9fdeb378234"
        },
        "date": 1786883170143,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3613,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6166.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9503.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11959.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11404.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11334,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11243,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "74551043e507b62f419cba54825da0f5bba83389",
          "message": "test: stabilize GraphErrorIT across FalkorDB missing-parameter message variants (#391)\n\n* Initial plan\n\n* test: accept old and new missing-parameter messages\n\nCo-authored-by: gkorland <753206+gkorland@users.noreply.github.com>\n\n* test: match the missing-parameter message shape, not a bare substring\n\nThe helper accepted any message containing \"not found\", which is loose enough\nto pass on unrelated failures — \"Graph not found\" and \"Procedure '...' not\nfound\" are both real FalkorDB errors that would have satisfied it, so the test\ncould have gone green on a genuine regression rather than on the message it\nmeans to assert.\n\nMatching the shape \"Parameter <name> not found\" instead keeps the version\ntolerance while restoring the assertion's intent. Verified on the wire rather\nthan inferred:\n\n  latest -> \"Missing parameters\"\n  edge   -> \"Parameter param not found\"\n\nGraphErrorIT now passes against all three images the project tests: the pinned\nv4.20.1 default, latest, and edge (9/9 each; edge was 2 failures before).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: copilot-swe-agent[bot] <198982749+Copilot@users.noreply.github.com>\nCo-authored-by: gkorland <753206+gkorland@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-17T08:53:05+03:00",
          "tree_id": "7848d8b4afb6de42227aed8f86869327d6b501cc",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/74551043e507b62f419cba54825da0f5bba83389"
        },
        "date": 1786946100340,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5725,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9332.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13984.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17422,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17241.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16650.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16961.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "18bd956b2050bb32ffdbf19869b327b000bc1c17",
          "message": "build(deps): bump github/codeql-action/analyze from 4.37.6 to 4.37.7 (#399)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.37.6 to 4.37.7.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/5595ccaf912efad79be6eef63a5619ff05969be3...ff2f1c621b7f889edc0d3c761ac2e6a3f8cdb0dd)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.37.7\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>",
          "timestamp": "2026-08-17T09:19:45+03:00",
          "tree_id": "e1a298782a30b1eba926a0e8d5f3a5462ee6146b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/18bd956b2050bb32ffdbf19869b327b000bc1c17"
        },
        "date": 1786947725798,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3458.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5959,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9286,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11924.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11066,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11029.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11062,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6aacdfb39d6b55431d7a39b04f0eed652e4402f8",
          "message": "build(deps): bump redis.clients:jedis from 7.5.3 to 8.0.0 (#397)\n\n* build(deps): bump redis.clients:jedis from 7.5.3 to 8.0.0\n\nBumps [redis.clients:jedis](https://github.com/redis/jedis) from 7.5.3 to 8.0.0.\n- [Release notes](https://github.com/redis/jedis/releases)\n- [Commits](https://github.com/redis/jedis/compare/v7.5.3...v8.0.0)\n\n---\nupdated-dependencies:\n- dependency-name: redis.clients:jedis\n  dependency-version: 8.0.0\n  dependency-type: direct:production\n  update-type: version-update:semver-major\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n\n* fix(driver): pin RESP2 and silence the Jedis 8 auto-negotiation warning\n\nJedis 8 turns RESP3 protocol auto-negotiation on by default. The legacy\nJedis class this driver pools cannot speak RESP3: it ignores the flag,\nsilently stays on RESP2 and logs a warning for every connection it opens,\nwhich would land in every JFalkorDB user's logs after the 7.5.3 -> 8.0.0\nbump.\n\nPin autoNegotiateProtocol(false) in a single shared client-config builder\nso all three factories - driver(host, port), FalkorDB.builder() and\ndriver(URI) - agree on the wire protocol our reply parsing is written\nagainst.\n\ndriver(URI) now assembles that config explicitly instead of delegating to\nJedis' URI-based pool constructor, which offered no way to set the flag.\nHost, port, credentials, database index, TLS scheme and an explicit\nprotocol are still resolved from the URI, and an invalid URI still raises\nInvalidURIException with the same message.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-17T10:08:51+03:00",
          "tree_id": "302b18e6b1dc6b7245548abfeace3db0632b0503",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/6aacdfb39d6b55431d7a39b04f0eed652e4402f8"
        },
        "date": 1786950619351,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3820.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6522.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10243,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12727.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12034,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11929.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11973,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "41aeed7cda3cc9437d5f570633c9a0463fc0f8d7",
          "message": "build(deps): bump org.apache.maven:apache-maven from 3.9.9 to 3.9.16 (#396)\n\n* build(deps): bump org.apache.maven:apache-maven from 3.9.9 to 3.9.16\n\nBumps org.apache.maven:apache-maven from 3.9.9 to 3.9.16.\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven:apache-maven\n  dependency-version: 3.9.16\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n\n* build: keep the corrected mvnw.cmd out of the wrapper distribution bump\n\nDependabot regenerated mvnw.cmd from the wrapper scripts of\nwrapperVersion 3.3.2 while bumping only the Maven distribution, which\nthis repo does not want:\n\n- The blob was committed with CRLF endings. .gitattributes normalises\n  *.cmd to LF in the index and CRLF in the working tree, so every fresh\n  checkout of the branch was instantly dirty, and `just bench-compare`\n  aborts on a dirty tree - that is the benchmark-pr failure.\n- It reverted MVNW_REPO_PATTERN to the inverted 3.3.2 form, undoing the\n  corrected mvnd/maven lookup this repo already carries.\n\nThe wrapper scripts are pinned by wrapperVersion (unchanged at 3.3.2),\nso the distribution bump only needs maven-wrapper.properties.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-17T11:07:45+03:00",
          "tree_id": "ea3820219e3c3516fb9ac256f6e4b16d98aa8c65",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/41aeed7cda3cc9437d5f570633c9a0463fc0f8d7"
        },
        "date": 1786954153263,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3539,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6040.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9047,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12022,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11265.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11029.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11194,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "16598640b69929b0adf507dc7f4452ce37921a45",
          "message": "build(deps): bump org.apache.maven.wrapper:maven-wrapper from 3.3.2 to 3.3.4 (#395)\n\n* build(deps): bump org.apache.maven.wrapper:maven-wrapper\n\nBumps [org.apache.maven.wrapper:maven-wrapper](https://github.com/apache/maven-wrapper) from 3.3.2 to 3.3.4.\n- [Release notes](https://github.com/apache/maven-wrapper/releases)\n- [Commits](https://github.com/apache/maven-wrapper/compare/maven-wrapper-3.3.2...maven-wrapper-3.3.4)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.wrapper:maven-wrapper\n  dependency-version: 3.3.4\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n\n* build: store mvnw.cmd LF-normalised for the 3.3.4 wrapper bump\n\n.gitattributes marks *.cmd as `text eol=crlf`, i.e. LF in the index and\nCRLF in the working tree. The regenerated mvnw.cmd was committed with\nCRLF in the blob, so every fresh checkout was immediately dirty and\n`just bench-compare` aborted on its clean-tree guard - the benchmark-pr\nfailure. Renormalise the blob; the script contents are unchanged.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* build: guard mvnw.cmd against a null Target property\n\nmaven-wrapper 3.3.4 resolves a symlinked ~/.m2 with\n\n  if ((Get-Item $MAVEN_M2_PATH).Target[0] -eq $null) { ... }\n\nOn a plain directory PowerShell 5.1 usually returns an empty collection,\nso [0] is a safe $null - but where Target is strictly $null (32-bit\nPowerShell under the Local System account, i.e. the system-profile .m2 a\nJenkins Windows service uses) indexing it raises \"Cannot index into a\nnull array\". With $ErrorActionPreference = \"Stop\" that aborts the\nwrapper before Maven ever starts.\n\nThis applies upstream's own fix (apache/maven-wrapper#416, still\nunreleased - 3.3.4 is the latest) for apache/maven-wrapper#395. Verified\nunder PowerShell 7.4: the file parses with 0 errors, the old expression\nthrows for Target = $null and for a missing Target property, and the\nguarded version falls back to the plain path while staying identical for\nan empty collection and for a real symlink target.\n\nDrop this deviation once it ships upstream.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-17T11:26:48+03:00",
          "tree_id": "b90434c2c378c0f2acaf02dbd66e9a4b71715f76",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/16598640b69929b0adf507dc7f4452ce37921a45"
        },
        "date": 1786955325581,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3310,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6093.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9162,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12020.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11278.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11215.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11177.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "7e456e76e62d79e2e871130eb09fb4d5a3694d63",
          "message": "fix!: non-finite doubles, header thread-safety, and Record null/unknown-column handling (#390)\n\n* fix: parse FalkorDB's non-finite double spellings\n\nFalkorDB emits non-finite doubles the C way. Verified against a live server:\n\n    RETURN 1.0/0.0   -> type 5 (VALUE_DOUBLE), value \"inf\"\n    RETURN -1.0/0.0  -> \"-inf\"\n    RETURN 0.0/0.0   -> \"-nan\"\n\nDouble.parseDouble accepts none of those spellings — it wants \"Infinity\" and\n\"NaN\" — so any query producing a non-finite double escaped as an unwrapped\nNumberFormatException from query().\n\nNote the asymmetry this removes: Utils.appendNumber already rejects non-finite\ndoubles on the way in, while the read path crashed on them.\n\nThe fast path is unchanged (Double.parseDouble first); only its failure is\ninspected, and genuinely malformed input still rethrows the original exception.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: build the result-set header once, immutably\n\nTwo defects in HeaderImpl, both fixed by parsing in the constructor:\n\nLazy building was unsynchronized and buildSchema() appended without clearing,\nso concurrent first access double-populated the schema lists. The new test\ndemonstrates it emphatically — 8 racing threads produced [n, n, n, n, n, n, n, n]\nfor a single-column header. Reachable since AsyncGraph began handing a ResultSet\nto many threads; a reader could also observe an ArrayList mid-grow.\n\nbuildSchema also indexed ResultSetColumnTypes.values() with an unvalidated\nserver-supplied ordinal, so a newer server sending an unknown column type gave a\nbare ArrayIndexOutOfBoundsException. It now uses the same guarded lookup\nResultSetScalarTypes.getValue already performs for scalars, and values() is\nhoisted into a static array instead of being reallocated per row. The\n`if (type != null)` that followed was dead code — array indexing throws rather\nthan returning null.\n\nThe schema is now the object's entire state, so equals/hashCode no longer need\n`raw` excluded to behave.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: make Record report null values and unknown columns clearly\n\ngetString(int) called toString() on the value with no null check, so a NULL\ncolumn threw NullPointerException — even though getValue is documented @Nullable\nand returns null for the very same cell (`RETURN n.missingProp`). It now returns\nnull, and the Record interface documents that.\n\ngetValue(String)/getString(String) passed header.indexOf(key) straight to\nvalues.get(), so a typo or an AS-aliased column surfaced as\n\"IndexOutOfBoundsException: Index -1 out of bounds\" with no mention of the key.\nThey now throw IllegalArgumentException naming the missing key and listing the\navailable columns.\n\nBehaviour change on a public interface: the unknown-key case throws\nIllegalArgumentException instead of IndexOutOfBoundsException, and getString may\nnow return null instead of throwing. Both are documented on Record, and japicmp\nreports no API break.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* refactor: share one ResultSet builder per pipeline and transaction\n\nGraphPipelineImpl and GraphTransactionImpl each declared eight byte-identical\nanonymous Builder<ResultSet> implementations — 16 copies of the same three\nlines. Every new pipeline/transaction method copied the block again, and any fix\nto the response shape had to be applied 16 times.\n\nEach class now holds one shared builder field. This also normalises the\ninconsistent @SuppressWarnings(\"unchecked\") that some copies carried and others\n(the profile ones) did not.\n\nJedis Builder<T> is an abstract class, not a functional interface, so this is a\nfield holding an anonymous subclass rather than a lambda. graph/cache are read\ninside build() rather than captured, so the transaction's reassignable cache\nfield still resolves to its current value.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* build: declare Automatic-Module-Name for JPMS consumers\n\nWithout it a modular consumer writing `requires ...;` binds to a module name\nderived from the jar FILENAME, which shading or renaming silently breaks, and\nwhich could not be stabilised later without breaking everyone already relying on\nthe derived name.\n\nThis is a manifest entry, not a module-info, so it is safe at release 8 and\ncosts nothing at runtime. Verified in the packaged jar:\n\n    Automatic-Module-Name: com.falkordb\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: make HeaderImpl final now that its constructor can throw\n\nCI's SpotBugs gate caught this (CT_CONSTRUCTOR_THROW): parsing in the constructor\nmeans it can throw on a malformed reply, and a partially-constructed non-final\nclass stays reachable through a subclass finalizer.\n\nMaking the class final removes the vector rather than suppressing the warning.\nResultSetImpl is already final for the same reason, and nothing extends this\ninternal type.\n\nVerified locally: spotbugs:check now reports 0 bugs (SpotBugs runs on JDK 17 as\nlong as the classes are compiled outside the quality profile, which is what kept\nthis from being caught before pushing — only Error Prone needs JDK 21).\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: range-check column and scalar type ordinals before narrowing\n\nReview feedback from CodeRabbit on #390: intValue() keeps only the low 32 bits,\nso an out-of-range ordinal such as 4294967297 (2^32 + 1) wrapped to 1 and was\naccepted as COLUMN_SCALAR — a hole in exactly the case the guard was added for.\nThe reply's long is now range-checked before it is narrowed.\n\nThe same hole existed on the scalar path (ResultSetScalarTypes.getValue via\ngetValueTypeFromObject), which is the lookup this PR cited as its model, so it\ngets the same treatment rather than being left inconsistent. Its exception-driven\nbounds check is now an explicit range test.\n\nRegression tests cover the wrapping value and a negative ordinal on both paths.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: parse non-finite vecf32 elements, and keep the module name jfalkordb\n\nTwo review findings on #390, both verified before acting.\n\nvecf32 elements arrive with the same C spellings as scalar doubles, so the\nnon-finite fix stopped one type short. On the wire:\n\n    RETURN vecf32([1.0, 1.0/0.0, 0.0/0.0])  ->  type 12, elements \"1\", \"inf\", \"nan\"\n\nFloat.parseFloat rejects those exactly as Double.parseDouble rejects the scalar\nform, so the query failed with \"Invalid float value in vector data\". The\nnon-finite mapping is now shared between a parseDouble and a parseFloat helper;\nnarrowing is exact for these values ((float) Double.POSITIVE_INFINITY is\nFloat.POSITIVE_INFINITY, (float) Double.NaN is Float.NaN), and genuinely\nmalformed input still rethrows the original failure.\n\nThe Automatic-Module-Name value was a silent breaking change. Java already\nderives a name from the jar filename, so the published artifact is:\n\n    0.10.1        -> jfalkordb@0.10.1 automatic\n    with com.falkordb -> com.falkordb@0.10.2-SNAPSHOT automatic\n\nAny modular consumer with `requires jfalkordb;` would have failed resolution\nafter upgrading — shipped under a `fix:` title as a patch. Pinning `jfalkordb`\ninstead keeps the entire benefit (the name now survives a rename or shading)\nwith no break, so it stays a patch. Adopting reverse-DNS `com.falkordb` remains\npossible later as a deliberate minor bump with a migration note; the pom comment\nrecords that and warns against changing the value casually.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* docs!: document the unmodifiable schema/keys contract\n\nBREAKING CHANGE: three observable runtime behaviours differ from 0.10.1, none of\nwhich japicmp can see because no signature changed:\n\n  - Record.getValue(String)/getString(String) with an unknown column now throw\n    IllegalArgumentException instead of IndexOutOfBoundsException.\n  - Record.getString(int)/getString(String) return null for a NULL column\n    instead of throwing NullPointerException.\n  - Header.getSchemaNames(), Header.getSchemaTypes() and Record.keys() return\n    unmodifiable lists; mutating them throws UnsupportedOperationException.\n    0.10.1 handed back the internal ArrayList, so a caller could corrupt the\n    schema — and, because records share that list, every record in the result\n    set — in place.\n\nThe third was an unnoticed consequence of parsing the header eagerly rather than\na deliberate API decision. It is worth keeping (exposing the internal list was an\nencapsulation bug), but it has to be declared rather than shipped silently in a\npatch, which is what the repository's \"a user-facing break is at least a minor\nbump\" rule exists for.\n\nThe unmodifiable contract is now documented on the Header and Record interfaces,\nwhich previously said nothing about mutability, and locked in by tests.\n\nCo-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>\n\n* fix: declare Record.values() elements nullable\n\ncom.falkordb is @NullMarked, so `List<Object> values()` promised callers that\nevery element is non-null. A NULL column deserializes to null and is stored in\nthat list (ResultSetImpl.deserializeScalar returns null for VALUE_NULL), which\nGraphAPIIT.testNullGraphEntities already asserts, so the published nullness\nmetadata was untruthful: a JSpecify-aware caller got no warning before\ndereferencing an element with a real NPE path.\n\nThe return type is now List<@Nullable Object>, with the matching RecordImpl\nfield, constructor parameter and return type, and the ResultSetImpl row buffer\nthat holds the nulls. Type annotations leave the erasure and generic signature\nuntouched, so this is source- and binary-compatible; japicmp reports\n\"No changes.\" against 0.10.1.\n\nRecordImpl's getValue/getString overrides also carry the @Nullable their\ninterface already declared, which was previously only on the interface.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: prove keys() immutability on the parse path, not by assumption\n\nRecordImpl deliberately SHARES the schema list rather than copying it per row,\nso the unmodifiable keys() contract is upheld by whatever HeaderImpl hands it —\nnot by RecordImpl itself. The only test covering it constructed a RecordImpl\nwith a list the test had made unmodifiable, so it presupposed the precondition\ninstead of proving it, and would have stayed green had the parse path started\nhanding out a mutable list.\n\nThe new test builds a ResultSetImpl from a raw reply — the way every\nuser-visible record is built — and asserts both keys() and getSchemaNames()\nreject mutation. Watched failing first: reverting HeaderImpl's\nunmodifiableList wrapper turns it red (\"Expected UnsupportedOperationException\nto be thrown, but nothing was thrown\"), which the old test could not detect.\n\nA defensive copy in the constructor was considered and rejected: it would\nallocate a schema copy per row (rows x columns) on the parse hot path to guard\nan internal, api-diff-excluded class whose only two call sites already pass the\nshared unmodifiable list. The precondition is documented on the constructor\ninstead.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: name the header concurrency test for the eager build\n\nThe test was written against the lazy schema build and kept its name and\ncomment after HeaderImpl moved the work into the constructor, so both\ndescribed a \"first access\" build that no longer exists.\n\nIt still earns its place - it is the regression guard that would catch a\nreturn to an unsynchronized lazy build - so this renames it to say what\nit now verifies (concurrent reads observe the schema exactly once) and\nputs the old behaviour in the past tense.\n\nAddresses the review comment on HeaderImplTest.java:93.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: wait for the header concurrency test's workers to terminate\n\nshutdownNow() only requests cancellation, so the test returned while 8\nworkers were still winding down - 1600 tasks over 200 rounds - leaving\nthem to be reaped while the rest of the suite ran. Await termination and\nassert it, so a worker that fails to stop fails this test rather than\ndestabilising a later one.\n\nAlso replaces the \"Handle the exception appropriately\" placeholder in\ndeserializeVector: parseFloat now maps FalkorDB's C spellings of the\nnon-finite values, so the comment should say that anything still\nreaching that catch is genuinely malformed.\n\nAddresses the two suppressed review comments on HeaderImplTest.java:113\nand ResultSetImpl.java:394.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: state that Record.getValue does not check the requested type\n\ngetValue's <T> is inferred from the assignment and erased, so the cast\ninside the method verifies nothing - the compiler emits the checkcast in\nthe CALLER's frame:\n\n  invokevirtual RecordImpl.getValue:(I)Ljava/lang/Object;\n  checkcast     java/lang/String\n\nSo a wrong type throws ClassCastException in user code rather than at the\ncall into the client, and throws nothing at all while the value stays\nuntyped. That is the half worth writing down: an unnoticed mismatch\npropagates silently until something narrows it.\n\nDocuments both overloads on Record, notes it at the unchecked cast in\nRecordImpl, and pins both halves with a test so a future signature change\nhas to face the contract. Signature unchanged - Object or Class<T> would\nbreak every existing caller, which a documentation pass should not do.\n\nAddresses the review comment on RecordImpl.java:40.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Claude Opus 5 (1M context) <noreply@anthropic.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-17T13:29:57+03:00",
          "tree_id": "98df74a7af3b835b0d5f6b89c4306adab7f579ca",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7e456e76e62d79e2e871130eb09fb4d5a3694d63"
        },
        "date": 1786962703593,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3361.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6060.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8976,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11636,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11014.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10839,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10965.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "d8c47492b106039c167353a495f752544181befe",
          "message": "chore(master): release 0.11.0 (#389)\n\n* chore(master): release 0.11.0\n\n* docs(changelog): document all 0.11.0 behavioural changes\n\nrelease-please derives the changelog from the BREAKING CHANGE footer, but it\nonly captured that footer's first paragraph - so the generated entry was a\ndangling sentence ending in a colon that listed nothing:\n\n  * three observable runtime behaviours differ from 0.10.1, none of which\n    japicmp can see because no signature changed:\n\nThe bullets that followed the blank line were dropped. The footer also said\n\"three\": it was written before review surfaced two more behavioural changes\nand one binary one.\n\nReplaces the entry with the full set, each with its 0.10.1 behaviour, its\n0.11.0 behaviour and a migration:\n\n  - unknown column        IndexOutOfBoundsException -> IllegalArgumentException\n  - NULL column           NullPointerException      -> null\n  - schema / keys lists   mutable internal list     -> unmodifiable\n  - bad type ordinal      lazy AIOOBE               -> eager JedisDataException\n  - non-finite doubles    NumberFormatException     -> Infinity / NaN\n\nAlso records what an API-diff tool cannot: HeaderImpl is now final, which\nbreaks `extends HeaderImpl`, but lives in the internal com.falkordb.impl\npackage that japicmp excludes.\n\nAdds a Dependencies section for jedis 7.5.3 -> 8.0.0. release-please hides\nbuild(deps) commits, but a major transport bump reaches users transitively\nand is worth stating, along with the RESP2 pin that keeps protocol\nbehaviour identical to 0.10.1.\n\nCHANGELOG.md is excluded from the spellcheck gate by design, so no wordlist\nchange is needed.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs(readme): advertise the version this release actually publishes\n\nThe \"Official Releases\" snippet - the one users copy into their pom - still\npointed at 0.9.0, two releases behind: 0.10.0 and 0.10.1 both shipped without\nit being touched. The \"Snapshots\" snippet pointed at 0.10.0-SNAPSHOT, which\nwas never the current snapshot either (master was on 0.10.2-SNAPSHOT).\n\nPoints them at 0.11.0 and at 0.11.1-SNAPSHOT, the snapshot release-please\nopens immediately after this release publishes.\n\nNeither is bumped automatically today, which is why both drifted. release-please\ncan own them via an `x-release-please-version` marker in extra-files; that is a\nconfig change and is deliberately kept out of the release PR.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-17T13:45:21+03:00",
          "tree_id": "589dfda4176096ae5e317ed62a0b46192afc35a6",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/d8c47492b106039c167353a495f752544181befe"
        },
        "date": 1786963645007,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6309.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11919,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 18462.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24230,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 22976.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 22438.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 21937.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "ff276d14600e1dfa532f69358a7aa7464710962a",
          "message": "build(deps): bump redis.clients:jedis from 8.0.0 to 8.0.1 (#415)\n\nBumps [redis.clients:jedis](https://github.com/redis/jedis) from 8.0.0 to 8.0.1.\n- [Release notes](https://github.com/redis/jedis/releases)\n- [Commits](https://github.com/redis/jedis/compare/v8.0.0...v8.0.1)\n\n---\nupdated-dependencies:\n- dependency-name: redis.clients:jedis\n  dependency-version: 8.0.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T14:48:46+03:00",
          "tree_id": "3c4d55e8a87426aa3c3f0c9ae187a69ef4c1b5f0",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/ff276d14600e1dfa532f69358a7aa7464710962a"
        },
        "date": 1788263448499,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4794.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7574.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10960,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 14503.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13493,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 13199.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12984.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "43fe9c70234ca3a0b6663e7ad2a07fbc1101f249",
          "message": "build(deps): bump actions/setup-java from 5.7.0 to 6.0.0 (#413)\n\nBumps [actions/setup-java](https://github.com/actions/setup-java) from 5.7.0 to 6.0.0.\n- [Release notes](https://github.com/actions/setup-java/releases)\n- [Commits](https://github.com/actions/setup-java/compare/b6effb05e454b25005698d916606bdc6ffcbf961...dd06d9cba3e5552c54d9f8ea23572deb30010f7c)\n\n---\nupdated-dependencies:\n- dependency-name: actions/setup-java\n  dependency-version: 6.0.0\n  dependency-type: direct:production\n  update-type: version-update:semver-major\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T15:10:51+03:00",
          "tree_id": "91fa00f111b9828b2df6c2b1b70ee633b9b95687",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/43fe9c70234ca3a0b6663e7ad2a07fbc1101f249"
        },
        "date": 1788264761720,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3554.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6142,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9381,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11980,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11189.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11031,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11121.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c086058a584e82da634d9da68a5047c6697504ac",
          "message": "build(deps): bump github/codeql-action/init from 4.37.7 to 4.37.9 (#412)\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.37.7 to 4.37.9.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/ff2f1c621b7f889edc0d3c761ac2e6a3f8cdb0dd...cdf488f595d80d6e07e03d4674febd5ab45fa938)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.37.9\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T16:23:04+03:00",
          "tree_id": "7ac10928e7af7e466b9e8ef1198ecfb49c370c00",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/c086058a584e82da634d9da68a5047c6697504ac"
        },
        "date": 1788269099769,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3636.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6296.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9006.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12025.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11305.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11176,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11200.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "da7c82cadc6b41e155d69bf59c63a72ac9766d81",
          "message": "build(deps): bump github/codeql-action/analyze from 4.37.7 to 4.37.9 (#411)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.37.7 to 4.37.9.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/ff2f1c621b7f889edc0d3c761ac2e6a3f8cdb0dd...cdf488f595d80d6e07e03d4674febd5ab45fa938)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.37.9\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T16:25:51+03:00",
          "tree_id": "d53d2b0ba97f3e935d24c163d350e6014f2558cf",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/da7c82cadc6b41e155d69bf59c63a72ac9766d81"
        },
        "date": 1788269245302,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3597.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6100.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9366.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12014.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11310,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11215,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11206.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f8e054fd9c7e8ed65013b36834b0c20101a3bfa5",
          "message": "build(deps-dev): bump org.pitest:pitest-maven from 1.25.9 to 1.30.0 (#410)\n\nBumps [org.pitest:pitest-maven](https://github.com/hcoles/pitest) from 1.25.9 to 1.30.0.\n- [Release notes](https://github.com/hcoles/pitest/releases)\n- [Commits](https://github.com/hcoles/pitest/compare/1.25.9...1.30.0)\n\n---\nupdated-dependencies:\n- dependency-name: org.pitest:pitest-maven\n  dependency-version: 1.30.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T16:29:09+03:00",
          "tree_id": "c2a9a77aedb2deddef89335257a6cfe91341dbad",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f8e054fd9c7e8ed65013b36834b0c20101a3bfa5"
        },
        "date": 1788269444074,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3535.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6005.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9637.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11934,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11308,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11177.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11172,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "1a4cd7c1a9e5c234b99f920b2727550662870bc9",
          "message": "build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin (#409)\n\nBumps [com.diffplug.spotless:spotless-maven-plugin](https://github.com/diffplug/spotless) from 3.9.0 to 3.10.1.\n- [Release notes](https://github.com/diffplug/spotless/releases)\n- [Changelog](https://github.com/diffplug/spotless/blob/main/CHANGES.md)\n- [Commits](https://github.com/diffplug/spotless/compare/maven/3.9.0...maven/3.10.1)\n\n---\nupdated-dependencies:\n- dependency-name: com.diffplug.spotless:spotless-maven-plugin\n  dependency-version: 3.10.1\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T16:32:29+03:00",
          "tree_id": "a58a64730706523c390edc69bc68906252ab0776",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/1a4cd7c1a9e5c234b99f920b2727550662870bc9"
        },
        "date": 1788269707314,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5629.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9283.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13442.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18096.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16756,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16218.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16076,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "10114d13f8b6cb58ef29d91e6b6a1621e782e487",
          "message": "build(deps-dev): bump com.github.spotbugs:spotbugs-maven-plugin (#406)\n\nBumps [com.github.spotbugs:spotbugs-maven-plugin](https://github.com/spotbugs/spotbugs-maven-plugin) from 4.10.3.0 to 4.10.4.0.\n- [Release notes](https://github.com/spotbugs/spotbugs-maven-plugin/releases)\n- [Commits](https://github.com/spotbugs/spotbugs-maven-plugin/compare/spotbugs-maven-plugin-4.10.3.0...spotbugs-maven-plugin-4.10.4.0)\n\n---\nupdated-dependencies:\n- dependency-name: com.github.spotbugs:spotbugs-maven-plugin\n  dependency-version: 4.10.4.0\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T16:35:17+03:00",
          "tree_id": "cfab753ba91c46f376e18e05511db65093940626",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/10114d13f8b6cb58ef29d91e6b6a1621e782e487"
        },
        "date": 1788269819973,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5232.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9115.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14452.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 19060.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17664.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17767.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 17643.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "7c533d8030d417be610ab5e8f2f806f64993e03b",
          "message": "build(deps-dev): bump org.apache.maven.plugins:maven-jar-plugin (#404)\n\nBumps [org.apache.maven.plugins:maven-jar-plugin](https://github.com/apache/maven-jar-plugin) from 3.4.2 to 3.5.1.\n- [Release notes](https://github.com/apache/maven-jar-plugin/releases)\n- [Commits](https://github.com/apache/maven-jar-plugin/compare/maven-jar-plugin-3.4.2...maven-jar-plugin-3.5.1)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.plugins:maven-jar-plugin\n  dependency-version: 3.5.1\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T18:42:36+03:00",
          "tree_id": "d69334583e7aee46f29e9cf6952c3a813f016fb6",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7c533d8030d417be610ab5e8f2f806f64993e03b"
        },
        "date": 1788277484711,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3551.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6111.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9361.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11994,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11325.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11348.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11271.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "52e1281476d2cb4d25cfb25479fa22b27ffd14ad",
          "message": "test: cover list-of-maps bulk create with map query parameters (#417)\n\n* test: cover list-of-maps bulk create with map query parameters\n\nMap parameters have been encodable since the parameter-path hardening in\ne098807, but no test exercised the pattern issue #68 actually reported:\npassing a list of maps and consuming it with `UNWIND $props AS map\nSET n = map`.\n\nThat path is distinct from the existing mapRoundTrips coverage, where the\nmap is only echoed back by `RETURN $p`. Here the encoded map literal has to\nwork as a Cypher map expression used as a property source, so the test\npins behaviour the round-trip test cannot. It includes a key requiring\nbacktick-quoting and a value requiring escaping.\n\nVerified against a real server: the scenario passes, confirming #68 is\nalready implemented.\n\nCloses #68\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>\n\n* test: assert map keys don't leak between list elements\n\nAddress review feedback: the test asserted the properties each node\nshould have, but not the absence of those it shouldn't. An encoder that\nmerged or reused keys across list elements would have passed.\n\nAssert bob does not carry alice's `favourite quote` key, and pin the\nproperty count on both nodes. Verified the assertion is load-bearing by\nadding the key to bob, which fails with \"bob must not inherit alice's key\".\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-01T19:39:29+03:00",
          "tree_id": "aeecb3ea04b4648c4276ed16c15ede0e324c2b82",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/52e1281476d2cb4d25cfb25479fa22b27ffd14ad"
        },
        "date": 1788280906773,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5385.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9004.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14062.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 19076.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17788.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17303.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 17269,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "0d5a47e0c86c026b66c33950d472cb5295cb5634",
          "message": "docs: document that path node order is independent of edge direction (#418)\n\nInvestigating #393 showed the client behaves correctly: FalkorDB returns\npath nodes in traversal order while each edge keeps its stored direction,\nand JFalkorDB preserves both rather than reorienting edges. For a reverse\nor undirected match the two intentionally disagree, which is easy to\nmistake for a decoding bug.\n\nThe reported IllegalArgumentException came from the Neo4j driver's\nInternalPath, which requires each relationship to be oriented along the\ntraversal. That is a stricter contract than FalkorDB's, so an adapter has\nto flip the endpoints on a reversed step.\n\nNothing in this repository was wrong, but the contract was undocumented.\nSpell it out on Path (and on getNodes/getEdges), including what an adapter\nonto a stricter API must do, and add an integration test pinning the\nbehaviour so it cannot regress silently.\n\nReferences #393\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-01T19:51:40+03:00",
          "tree_id": "64aa81d5f1236feda1d01fc3d75c9653f9e378e7",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/0d5a47e0c86c026b66c33950d472cb5295cb5634"
        },
        "date": 1788281658903,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5838,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11867.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19418.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 26379.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 24443.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23385.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 23798.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "guy.lubovitch@gmail.com",
            "name": "Guy Lubovitch",
            "username": "dragnot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "1c50cab49a3a18f005bd0604715a42587e5ade0f",
          "message": "fix(cache): warm the graph cache with a read-only query so replica reads work (#414)\n\n* fix(cache): warm the graph cache with a read-only query\n\nAny readOnlyQuery returning a node, relationship or path fails against a\nreplica with \"READONLY You can't write against a read only replica.\"\n\nResponses are decoded in compact mode, so labels, relationship types and\nproperty keys arrive as integer ids. ResultSetImpl resolves them through\nGraphCache, which warmed itself via callProcedure. callProcedure routes\nthrough query, which issues GRAPH.QUERY, a write command that a replica\nrejects. The client was therefore issuing a write in order to decode a read.\n\ndb.labels, db.relationshipTypes and db.propertyKeys are all read-only, so\nsending the prepared procedure through readOnlyQuery serves them over\nGRAPH.RO_QUERY. Behaviour against a primary is unchanged.\n\nQueries returning only scalars were unaffected, and a warm cache hid the\nproblem, which is why this presented as intermittent.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* style: apply spotless formatting\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: read from a real replica in an integration test\n\nThe rest of the suite runs against a single server, where every command is\nlegal, so it could not observe the client against a read-only replica. That\ngap is why the cache warming write went unnoticed.\n\nStarts a primary and a replica on a shared network, points the replica at the\nprimary with REPLICAOF, waits for the link and for the graph to arrive, then\nreads through the client.\n\nReturning a path is the point of the test: it forces label and property-key\nresolution, which is what used to require a write. The scalar case is kept\nalongside it because it passes either way, and that asymmetry is exactly what\nmade the defect look intermittent.\n\nReverting the fix turns the path test red with the original READONLY error\nwhile the scalar test stays green.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test(replica): honour the external-server override and accept role:replica\n\nReplicaReadIT started its containers unconditionally, ignoring the\nFALKORDB_HOST/FALKORDB_PORT contract that TestServer documents. That broke\n`just verify-local`, which sets the override precisely because Testcontainers\ncannot reach Docker there — the test would try to build a two-server topology\nanyway and fail.\n\nAn external single server cannot host this topology: it needs two servers wired\nby REPLICAOF on a shared network. So skip rather than start containers behind\nthe user's back, matching the existing assumption in InstantiationIT. With the\noverride set the class now aborts in 0.03s without touching Docker, instead of\nrunning for ~10s.\n\nAlso accept `role:replica` alongside the legacy `role:slave` in the INFO\nreplication poll, so the wait survives a Redis-family image variant that adopts\nthe newer wording (FALKORDB_IMAGE matrixes over versions).\n`master_link_status:up` remains the substantive gate.\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\nCo-authored-by: Guy Korland <gkorland@gmail.com>",
          "timestamp": "2026-09-01T19:54:31+03:00",
          "tree_id": "9dc5da15938e962fe9d535152fa7c48dc52061e1",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/1c50cab49a3a18f005bd0604715a42587e5ade0f"
        },
        "date": 1788281795475,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5785,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10839.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19692,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 25688.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 24146.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23672.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 24097.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "7e0cfc7960be036b8634beeb5c47a566bb1db9b9",
          "message": "chore(master): release 0.11.1-SNAPSHOT (#402)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T19:58:47+03:00",
          "tree_id": "c7a7c5cc346031bc1e5b5901b5c7cc169d0f174f",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7e0cfc7960be036b8634beeb5c47a566bb1db9b9"
        },
        "date": 1788282029956,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3570.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6190.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9178.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11989.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11324.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11282.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11253.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "ae1c51c1b57db83e4d7538699be2ae052bf1f93a",
          "message": "build: bump api.diff.baseline to 0.11.0 (#403)\n\n0.11.0 is published to Maven Central, so the API-diff gate should now compare\nagainst it. Leaving the baseline at 0.10.1 would keep re-reporting the 0.11.0\ndelta on every future PR instead of diffing against what users can actually\nresolve.\n\nThis is the documented post-release step, matching #388 for 0.10.1.\n\nVerified: japicmp downloads jfalkordb-0.11.0.jar from Central and reports\n\"No changes.\" against master, which is the code 0.11.0 was cut from.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-01T20:01:56+03:00",
          "tree_id": "edd38267d977b02d7f11e3eb1769f8e256041be3",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/ae1c51c1b57db83e4d7538699be2ae052bf1f93a"
        },
        "date": 1788282245284,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6399.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11498.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 18008.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 25267,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 23552.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23224.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 23594.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "707f072c10b4aadbf5275c2a88d9f2c31cda9fcf",
          "message": "chore(master): release 0.11.1 (#419)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T20:05:38+03:00",
          "tree_id": "d8229caa16e3a21136553b835c9bbcff8c3b48eb",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/707f072c10b4aadbf5275c2a88d9f2c31cda9fcf"
        },
        "date": 1788282460702,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3638,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6116.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8457,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12060.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11299,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11174.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10997.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "066738e8c3b6247f4f5824c64225ca42cd3adf10",
          "message": "chore(master): release 0.11.2-SNAPSHOT (#420)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-01T20:23:24+03:00",
          "tree_id": "16f9ad63c1267496124689dc62381fa70747a3de",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/066738e8c3b6247f4f5824c64225ca42cd3adf10"
        },
        "date": 1788283532782,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3563.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6005.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9627.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11781,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11247,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11210,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11231.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "53bcc5f6b0afbdbf4a93ffb8ccc98f3a84c69d90",
          "message": "build: bump api.diff.baseline to 0.11.1 (#421)\n\n0.11.1 is published to Maven Central, so the API-diff gate should compare\nagainst it. Same post-release step as #388 and #403.\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-01T20:27:09+03:00",
          "tree_id": "1c360736fcded42794fa97c2f9e9975554c0dbf6",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/53bcc5f6b0afbdbf4a93ffb8ccc98f3a84c69d90"
        },
        "date": 1788283762305,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3571.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6163.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9408.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11831.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11080.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11070.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10963.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "b04b8972ee10fa2f1434788f06f68a9165b35031",
          "message": "build(deps): bump org.apache.maven.plugins:maven-compiler-plugin (#422)\n\nBumps [org.apache.maven.plugins:maven-compiler-plugin](https://github.com/apache/maven-compiler-plugin) from 3.15.0 to 3.16.0.\n- [Release notes](https://github.com/apache/maven-compiler-plugin/releases)\n- [Commits](https://github.com/apache/maven-compiler-plugin/compare/maven-compiler-plugin-3.15.0...maven-compiler-plugin-3.16.0)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.plugins:maven-compiler-plugin\n  dependency-version: 3.16.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-06T10:52:58+03:00",
          "tree_id": "8ce087500582c3c070f8cf5d74bdc30a17ebe093",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/b04b8972ee10fa2f1434788f06f68a9165b35031"
        },
        "date": 1788681303558,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4310.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7100,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10771.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13734,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12796,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12624,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12753.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "3b7a4046dc50a6552fbf8124ef73d57c2cdf65b5",
          "message": "build(deps-dev): bump org.apache.maven.plugins:maven-failsafe-plugin (#423)\n\nBumps [org.apache.maven.plugins:maven-failsafe-plugin](https://github.com/apache/maven-surefire) from 3.5.6 to 3.6.0.\n- [Release notes](https://github.com/apache/maven-surefire/releases)\n- [Commits](https://github.com/apache/maven-surefire/compare/surefire-3.5.6...surefire-3.6.0)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.plugins:maven-failsafe-plugin\n  dependency-version: 3.6.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-06T12:28:24+03:00",
          "tree_id": "cdf332293779730ee405346f29a049f89904d54e",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/3b7a4046dc50a6552fbf8124ef73d57c2cdf65b5"
        },
        "date": 1788687038142,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4473.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7234.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10399.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13912.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13084.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12933.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12880.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "9c65a821ee03b3a162f88961dc84d7e14fc569dc",
          "message": "build(deps-dev): bump org.apache.maven.plugins:maven-surefire-plugin (#424)\n\nBumps [org.apache.maven.plugins:maven-surefire-plugin](https://github.com/apache/maven-surefire) from 3.5.6 to 3.6.0.\n- [Release notes](https://github.com/apache/maven-surefire/releases)\n- [Commits](https://github.com/apache/maven-surefire/compare/surefire-3.5.6...surefire-3.6.0)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.plugins:maven-surefire-plugin\n  dependency-version: 3.6.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-06T12:37:08+03:00",
          "tree_id": "fe4e30fd3dfeb085ec5729e5df9026d66634cb74",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/9c65a821ee03b3a162f88961dc84d7e14fc569dc"
        },
        "date": 1788687557190,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3420,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6030.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8803.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11758,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11142.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10964,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10818.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "071b9aa40d5301e58b79ae1e6262716304adbc16",
          "message": "build(deps-dev): bump com.github.siom79.japicmp:japicmp-maven-plugin (#426)\n\nBumps [com.github.siom79.japicmp:japicmp-maven-plugin](https://github.com/siom79/japicmp) from 0.26.1 to 0.26.2.\n- [Release notes](https://github.com/siom79/japicmp/releases)\n- [Changelog](https://github.com/siom79/japicmp/blob/master/release.py)\n- [Commits](https://github.com/siom79/japicmp/compare/japicmp-base-0.26.1...japicmp-base-0.26.2)\n\n---\nupdated-dependencies:\n- dependency-name: com.github.siom79.japicmp:japicmp-maven-plugin\n  dependency-version: 0.26.2\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-08T09:46:38+03:00",
          "tree_id": "fba23716c757c70508004e9946246d66969a82b7",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/071b9aa40d5301e58b79ae1e6262716304adbc16"
        },
        "date": 1788850144358,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4241,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7120.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10217.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13775.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12941,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12817.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12820.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "a72d5fc70bada5edb81a42a06f641563fe3dee80",
          "message": "build(deps): bump advanced-security/maven-dependency-submission-action (#427)\n\nBumps [advanced-security/maven-dependency-submission-action](https://github.com/advanced-security/maven-dependency-submission-action) from 5.0.0 to 6.0.1.\n- [Release notes](https://github.com/advanced-security/maven-dependency-submission-action/releases)\n- [Commits](https://github.com/advanced-security/maven-dependency-submission-action/compare/b275d12641ac2d2108b2cbb7598b154ad2f2cee8...a64327a7329c9939cf675e458452febe1894a70c)\n\n---\nupdated-dependencies:\n- dependency-name: advanced-security/maven-dependency-submission-action\n  dependency-version: 6.0.1\n  dependency-type: direct:production\n  update-type: version-update:semver-major\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-08T09:52:13+03:00",
          "tree_id": "e21f8bf0e6ca1f84bc7d266385cd274ef6ad37d9",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/a72d5fc70bada5edb81a42a06f641563fe3dee80"
        },
        "date": 1788850506386,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5987,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 12200.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19924,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 26485.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 24844,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23897.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 23722.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "272c846a2597fd8ae35b28a9bb6381a2b029ee7b",
          "message": "build(deps-dev): bump org.slf4j:slf4j-simple from 2.0.18 to 2.0.19 (#428)\n\nBumps org.slf4j:slf4j-simple from 2.0.18 to 2.0.19.\n\n---\nupdated-dependencies:\n- dependency-name: org.slf4j:slf4j-simple\n  dependency-version: 2.0.19\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-08T10:20:59+03:00",
          "tree_id": "6182cf1903d9c06afd6a1e4ca7fa606b483ede58",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/272c846a2597fd8ae35b28a9bb6381a2b029ee7b"
        },
        "date": 1788852210020,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3421.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5878,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8682.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11719,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11155.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10977.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10988.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8949f8f053c2c98ab2b9598d7ea09e3632379f58",
          "message": "build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin (#429)\n\nBumps [com.diffplug.spotless:spotless-maven-plugin](https://github.com/diffplug/spotless) from 3.10.1 to 3.10.2.\n- [Release notes](https://github.com/diffplug/spotless/releases)\n- [Changelog](https://github.com/diffplug/spotless/blob/main/CHANGES.md)\n- [Commits](https://github.com/diffplug/spotless/compare/maven/3.10.1...maven/3.10.2)\n\n---\nupdated-dependencies:\n- dependency-name: com.diffplug.spotless:spotless-maven-plugin\n  dependency-version: 3.10.2\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-08T10:25:00+03:00",
          "tree_id": "db54cfe7121b655a8a2cfce6bbd0b6889a916e03",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/8949f8f053c2c98ab2b9598d7ea09e3632379f58"
        },
        "date": 1788852447276,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3640,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6172,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9612,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12024,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11341.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11358,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10977,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "5d3ee911a6ec11a409f6d7c28dde490cd276822a",
          "message": "build(deps-dev): bump com.github.spotbugs:spotbugs-maven-plugin (#430)\n\nBumps [com.github.spotbugs:spotbugs-maven-plugin](https://github.com/spotbugs/spotbugs-maven-plugin) from 4.10.4.0 to 4.10.4.1.\n- [Release notes](https://github.com/spotbugs/spotbugs-maven-plugin/releases)\n- [Commits](https://github.com/spotbugs/spotbugs-maven-plugin/compare/spotbugs-maven-plugin-4.10.4.0...spotbugs-maven-plugin-4.10.4.1)\n\n---\nupdated-dependencies:\n- dependency-name: com.github.spotbugs:spotbugs-maven-plugin\n  dependency-version: 4.10.4.1\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-08T10:29:04+03:00",
          "tree_id": "c99cad18a6e0dab9f6e87bf264ffe31d1e1ccf0b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/5d3ee911a6ec11a409f6d7c28dde490cd276822a"
        },
        "date": 1788852669506,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5445,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9021,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14231,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17079.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16431.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16174.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 15907.333,
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
          "id": "899622a05103393e4c18e39bdbb49e6ba15f93b2",
          "message": "build: let release-please own the version numbers so they stop drifting (#432)\n\n* build: let release-please own the version numbers so they stop drifting\n\nNothing owned the version numbers outside pom.xml, so they drifted\nquietly. Current state on master:\n\n  README \"Official Releases\"  0.11.0          latest release is 0.11.1\n  README \"Snapshots\"          0.11.1-SNAPSHOT master is 0.11.2-SNAPSHOT\n  examples/pom.xml            0.10.0-SNAPSHOT\n  smoke-test/pom.xml          0.10.0-SNAPSHOT\n  pin-check/pom.xml           0.10.0-SNAPSHOT\n  benchmarks/pom.xml          0.10.0-SNAPSHOT\n\nThe four sibling modules had the same stale default; #401 only spotted\nexamples/. Their defaults are overridden by `just`, so the staleness was\ninvisible there, but building a module directly silently resolved a\nclient two minor versions old.\n\nAdd README.md and the four module poms to release-please's extra-files\nand mark the line to rewrite with x-release-please-version.\n\nOne correction to the plan in the issue: the marker must NOT go on the\n\"Official Releases\" snippet. With release-type: java every release PR is\nfollowed by a snapshot PR, and Java.buildSnapshotPullRequest re-applies\nthe same extra-files updaters with the -SNAPSHOT version, so a marked\nline ends every cycle holding X.Y.Z-SNAPSHOT. That is right for the\nSnapshots block and the modules' defaults, which should track master's\nsnapshot, and wrong for the block users copy into their own pom.xml --\nit would leave them pointing at something never published to Maven\nCentral. Verified by running release-please 17.6.0's own Generic updater\nover these files for both PR versions.\n\nSo the release snippet stays hand-written and is guarded instead, which\nis the alternative the issue proposed: ReadmeVersionTest checks it\nagainst CHANGELOG.md, checks it never names a SNAPSHOT, and checks the\nmarked lines still track pom.xml so a broken marker fails loudly rather\nthan silently resuming the drift. It is a plain unit test, so it runs in\nthe existing build gate with no new tooling.\n\nThat test fails on the Release PR by design -- release-please has\nwritten the new version into CHANGELOG.md while the release snippet\nstill names the previous one -- which is the release-time check that\nmakes stale docs impossible to ship. Documented in\ndocs/release-please-setup.md.\n\nFixes #401\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: anchor README version lookup and assert the release-please contract\n\nTwo AI review findings on ReadmeVersionTest:\n\n- The `<version>` lookup matched the first one anywhere after the\n  heading, so a version mentioned in prose - or in a later section, had\n  a heading's own snippet ever been removed - would have been asserted\n  instead of the block users copy. It is now scoped to the heading's own\n  section and to the first fenced ```xml block in it that declares a\n  version. Not simply the first block: \"Snapshots\" opens with a\n  `<repositories>` snippet that has none.\n\n- The version comparisons only notice drift after it has happened. If an\n  `extra-files` entry or an `x-release-please-version` marker is deleted,\n  every version still agrees today and the tests stay green, while the\n  next release silently resumes the drift #401 was filed about. The new\n  test asserts that half of the contract directly: every marked file is\n  still listed as a \"generic\" extra-file, and README's snapshot snippet\n  still carries its marker.\n\nEach new assertion was checked by reintroducing the drift it guards\nagainst - dropping the README extra-files entry, retyping one to \"xml\",\nand removing README's marker - and confirming it fails.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* test: assert release-please's actual marker contract, not its formatting\n\nTwo more review findings, both on ReadmeVersionTest:\n\n- The marker check demanded exactly one space between the version tag\n  and the marker, which asserts formatting rather than behaviour. The\n  Generic updater is line-scoped: it rewrites the first semver-looking\n  string on any line carrying the marker, so neither the spacing nor\n  the marker's position on that line means anything to it. The check is\n  now line-scoped too, via a `lineDeclaring` helper shared with the\n  README snapshot assertion, which was looser still - it accepted the\n  marker anywhere in the fenced block, including a line release-please\n  would never look at.\n\n- `SECTION` was formatted with an unquoted heading in one place and a\n  quoted one in the other. Both call sites now go through\n  `xmlBlockUnderHeading`, so the heading is quoted once.\n\nChecked by reformatting a module POM to three spaces before the marker\n(still passes, as release-please would still update it) and by moving\nthe marker to its own line (now fails, as release-please would silently\nstop updating it).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-08T11:16:45+03:00",
          "tree_id": "e5ee5a70d0f06e8ecf80b8fe334589662b539699",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/899622a05103393e4c18e39bdbb49e6ba15f93b2"
        },
        "date": 1788855569214,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5571.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9758.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 15329.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 20636,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 18810,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 18749,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 18933,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "a43b9b0f3a241aff7b102a8000f35ecb39163802",
          "message": "chore(master): release 0.12.0 (#431)\n\n* chore(master): release 0.12.0\n\n* chore(master): point the README release snippet at 0.12.0\n\nThe \"Official Releases\" snippet is deliberately not marked with\nx-release-please-version (the snapshot PR that follows every release\nwould rewrite it to a version never published to Maven Central), so\nReadmeVersionTest fails on the release PR until it is updated by hand.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-08T11:27:19+03:00",
          "tree_id": "5813fba3860a64d141ced22a9f6e22b0a8e1625d",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/a43b9b0f3a241aff7b102a8000f35ecb39163802"
        },
        "date": 1788856187147,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3487,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6176.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8930,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11714.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11086.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11016.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10682.333,
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
          "id": "25cee11d8e25dcecd52ee9fc81dc2f8ad21e3f80",
          "message": "feat: add Redis Sentinel support (#434)\n\n* feat: add Redis Sentinel support\n\nPoint the driver at a Sentinel and it now discovers the master and\nfollows failovers, closing #46.\n\nDetection is automatic and needs no configuration: the endpoint is\nprobed with `INFO server`, and if it reports `redis_mode:sentinel` the\nmaster is taken from `SENTINEL MASTERS`. This is what falkordb-py,\nfalkordb-go and falkordb-ts already do, so a deployment behaves the same\nwhichever client talks to it. A Sentinel monitoring several masters is\nambiguous and reports an error naming the builder call that resolves it.\n\nDetection failures are deliberately non-fatal. An endpoint that cannot\nbe probed, or that refuses `INFO` by ACL, falls back to the direct pool\nevery previous release would have built, so the new default cannot break\nan existing deployment. `autoDetectSentinel(false)` skips the probe\nentirely.\n\nThe deployment can also be named explicitly, which is required when a\nSentinel monitors more than one master and useful for listing several\nSentinels for redundancy:\n\n    FalkorDB.builder().sentinel(\"mymaster\", \"a:26379\", \"b:26379\")\n\nSentinels often have their own ACL, so `sentinelCredentials(...)`\nconfigures them separately; without it the master's credentials are\nreused. `FALKORDB_SENTINEL_MASTER` and `FALKORDB_SENTINELS` configure\nthe same thing from the environment, taking precedence over\n`FALKORDB_URL` and `FALKORDB_HOST`/`FALKORDB_PORT`.\n\nThree details worth knowing when reading the diff:\n\n`DriverImpl` now resolves its pool on first use rather than in the\nconstructor. Driver construction has never performed I/O and the suite\nrelies on that, so the probe had to be deferred. The memoisation is a\nlock-free CAS rather than a `synchronized` block because holding a\nmonitor across network I/O would pin a virtual thread's carrier, which\nthe `pin-check` gate forbids.\n\nConnections to the Sentinels get their own client config rather than\nreusing the data one. It must not carry the database index a URI can\nsupply, since Jedis would then send `SELECT` to a Sentinel, which has no\nsuch command; `JedisSentinelPool` reports that error as the Sentinel\nbeing down, making a healthy deployment look dead. It must also keep an\nunbounded read deadline, because the pool holds a `SUBSCRIBE` open on\neach Sentinel to hear `+switch-master`. Bounding the read is instead the\njob of the separate one-shot probe config, so a silent endpoint cannot\nhang the first query.\n\nVerified against a real master/replica/Sentinel topology: auto-detection,\nexplicit configuration, the environment variables, the opt-out, an\nactual `SENTINEL FAILOVER` (the driver followed the promotion without\nbeing recreated), and the multi-master error.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: fail fast on a closed driver and correct stale Sentinel docs\n\nAddresses the review on #434.\n\nResolving the pool lazily left a hole: a driver closed before it was ever\nused would, on the next call, run the whole resolution -- Sentinel probe\nincluded -- only to hand back a pool it immediately closes. That is real\nnetwork I/O after close(), reported as a pool error rather than as the\nprogramming mistake it is. `pool()` now refuses to build anything once\nthe driver is closed, in the initial check and in the race with a\nconcurrent close(). A driver closed after being used keeps reporting\nthrough Jedis exactly as it always has.\n\nThe rest is documentation that the lazy refactor invalidated: three\nplaces still said the Sentinel probe happens at driver creation or in\nbuild(), when it now happens on first use.\n\nAlso pass --raw to redis-cli in SentinelIT. It already behaves that way\nwhen its output is not a terminal, which is the case here, but saying so\nmeans the address parsing does not depend on that detection.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: keep the resolved pool write-once and tighten the Sentinel health check\n\nThe close-before-first-use fix in 37d585c withdrew the pool from\n`resolvedPool` when the winner of the resolution race found the driver\nclosed. That reintroduced a null into a field the loser of that race\nreads unguarded, so a borrower could get a NullPointerException instead\nof a real error.\n\nRather than surviving the null with a retry, remove the mechanism that\ncreates it: `resolvedPool` is now write-once, going from null to a pool\nand never back. The loser can therefore read the winner's pool directly\nand can never see null. A pool closed underneath a caller stays\npublished, so the driver reports through Jedis -- exactly what already\nhappens to a driver closed after being used. Failing fast on a driver\nclosed *before* it is used, which is what 37d585c was for, is unchanged.\n\nAlso make `SentinelIT.awaitMonitoredMaster` parse the reply into fields\nand require `flags` to be exactly `master`. The old substring test\nmatched the master's *name* (`mymaster`), so it accepted a Sentinel that\nhad not converged yet, and would have accepted `master,disconnected`.\n\nSilence the lint warnings this feature introduced: seven missing Javadoc\nsummary fragments in SentinelOptions and three single-argument\nString.split calls.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: stop invalid connection URIs from leaking their credentials\n\nrequireValidUri reproduced Jedis' message verbatim, and that message\nformats the whole URI. A connection URI routinely carries a password in\nits userinfo, so rejecting one put that password into the exception\nmessage and every log line and crash report carrying it. The URIs most\nlikely to be rejected are hand-written ones, which is exactly where a\nhand-typed password lives.\n\nDriverEnvironment already refused to chain Jedis' exception for this\nreason and redacted the value itself, so the rule existed but only on\nthe environment path; the public FalkorDB.driver(URI) path still leaked.\nRather than copy the regex into a second file -- two copies of a rule\nlike this drift, and the copy that drifts is the one that leaks -- move\nit to com.falkordb.impl.ConnectionUris and have both paths call it.\n\nThe helper redacts the text rather than URI accessors on purpose: the\nvalues that most need redacting are the malformed ones, and a URI Java\ncannot parse into an authority still has the password in plain sight in\nits string form.\n\ncom.falkordb.impl is excluded from the javadoc and api-diff gates, so\nthis adds nothing to the public API surface.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: make ConnectionUris.redact null-tolerant on both overloads\n\nredact(String) threw a NullPointerException on null while redact(URI)\nreturned \"null\", and an overload that throws where its twin returns is a\ntrap for the next caller, who will not check which one they picked.\n\nNeither call site can pass null today, so this is about the invariant\nrather than a live bug: redaction only ever runs while something is\nalready going wrong, and throwing out of the code that builds an error\nmessage replaces a useful diagnostic with a useless one -- which is the\nreasoning already written on the URI overload.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: correct the grammar of the invalid-URI message\n\n\"Cannot open Redis connection due invalid URI\" reads as a typo because it is\none -- it was inherited verbatim from Jedis' JedisFactory, which this driver\nreproduced when it took over building the client config from Jedis' URI-based\npool constructor.\n\nFidelity to that wording is not worth keeping. The message already had to\ndiverge, because Jedis quotes the URI verbatim and this driver redacts the\ncredentials out of it first, so the two were never going to match anyway. No\ntest or document pins the string.\n\nThe javadoc claimed the message was Jedis', which is what made the typo look\ndeliberate; it now describes what the check stands in for instead.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: keep the Sentinel read deadline off whatever the data one is\n\nsentinelClientConfig() inherited the data connections' socket timeout. That\nconnection stops issuing requests once discovery is over: all it does from then\non is hold JedisSentinelPool's master listener SUBSCRIBEd to +switch-master. A\nsocket timeout of T therefore bounds nothing and instead expires the\nsubscription every T, and MasterListener answers a read timeout by logging a\nwarning and sleeping subscribeRetryWaitTimeMillis (5s) before resubscribing\n(JedisSentinelPool.MasterListener#run, jedis 8.0.1). A caller who set a socket\ntimeout for their graph queries got a permanent cycle of log noise and repeated\nwindows with nobody listening for failovers. The active refresh on reconnect\nmeans a promotion is still picked up eventually, so this degraded failover\ntracking rather than breaking it outright - which is exactly why it would not\nhave shown up in testing.\n\nThe default was already 0, so only explicitly configured timeouts were\naffected. Forcing it off does mean a Sentinel that completes the handshake and\nthen goes silent can hang initSentinels, but that exposure is unchanged for the\ndefault configuration, the connect timeout still bounds the connect, and\nauto-detection still runs its bounded probe first.\n\nprobeClientConfig() derived its bound from sentinelClientConfig()'s socket\ntimeout, so forcing that to 0 would have quietly downgraded every explicitly\nconfigured deadline to the probe default. It now reads the caller's choice off\nthe data config, which probeConfigRespectsAnExplicitReadDeadline pins.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* fix: redact a password that contains an unescaped at-sign\n\nUSERINFO_PREFIX excluded '@' from the segment it matched, so the match ended at\nthe *first* '@' in the authority. A password containing a literal '@' therefore\nkept everything after it: redis://someone:p@ss@localhost:6379 redacted to\n\"redis://<redacted>@ss@localhost:6379\", and rediss://u:p@w@d@host to\n\"rediss://<redacted>@w@d@host\" - the tail of the credential republished in the\nvery message the redaction exists to sanitise.\n\nSuch a value is also exactly what a URI parser rejects, so it is the input most\nlikely to reach an error message in the first place: the case that needed the\nredaction most was the case it half-performed.\n\nDropping '@' from the negated class makes the greedy match run to the last '@'\nin the authority instead of the first. /?# still bound it, so the path and\nquery are untouched - redis://host:6379/graph@2 stays intact, and an authority\nwith credentials followed by a path containing '@' redacts only the authority.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-08T16:11:53+03:00",
          "tree_id": "84eee3107c25a381161cb352078ea774d95de948",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/25cee11d8e25dcecd52ee9fc81dc2f8ad21e3f80"
        },
        "date": 1788873272747,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5626.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10216.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 15436.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 21209.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 20416.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 19337.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 19146.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f112f07600fc46d6e986d327d85f652e311ec273",
          "message": "build(deps): bump github/codeql-action/init from 4.37.9 to 4.38.0 (#436)\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.37.9 to 4.38.0.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/cdf488f595d80d6e07e03d4674febd5ab45fa938...b96794f015dfd88f77b49b1c93e0fa7110f94c63)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.38.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-15T06:48:12+03:00",
          "tree_id": "044574df8378feac2974e984f43f9f37620b9445",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f112f07600fc46d6e986d327d85f652e311ec273"
        },
        "date": 1789444206175,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5413.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9281,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14683,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 19086.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17885,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17815.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 17600,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "40ca25392d6b72d382d55b1bb2e33f3c74500bdf",
          "message": "build(deps): bump actions/setup-java from 6.0.0 to 6.0.1 (#437)\n\nBumps [actions/setup-java](https://github.com/actions/setup-java) from 6.0.0 to 6.0.1.\n- [Release notes](https://github.com/actions/setup-java/releases)\n- [Commits](https://github.com/actions/setup-java/compare/dd06d9cba3e5552c54d9f8ea23572deb30010f7c...de7274f081f381c8f8158605e0321c36c376e2e6)\n\n---\nupdated-dependencies:\n- dependency-name: actions/setup-java\n  dependency-version: 6.0.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-16T08:51:34+03:00",
          "tree_id": "c4eecccc16d27459c93322c28af01825c4dcc1c4",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/40ca25392d6b72d382d55b1bb2e33f3c74500bdf"
        },
        "date": 1789538042365,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3568,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6277,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9137.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12040.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11353.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11223,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11058.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "749c50315d80fc7f6c1f3f91bd1722d227ca742d",
          "message": "build(deps): bump github/codeql-action/analyze from 4.37.9 to 4.38.0 (#438)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.37.9 to 4.38.0.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/cdf488f595d80d6e07e03d4674febd5ab45fa938...b96794f015dfd88f77b49b1c93e0fa7110f94c63)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.38.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-16T08:57:05+03:00",
          "tree_id": "d290201724b242240fc18d6e8fd2fe6f3db6bb4a",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/749c50315d80fc7f6c1f3f91bd1722d227ca742d"
        },
        "date": 1789538371744,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3543.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6064,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9348.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11863.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11317.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11242.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11169,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "1a45098c4bdc0611e427976e64dd8d91797bb74f",
          "message": "build(deps-dev): bump org.codehaus.mojo:animal-sniffer-maven-plugin (#441)\n\nBumps [org.codehaus.mojo:animal-sniffer-maven-plugin](https://github.com/mojohaus/animal-sniffer) from 1.27 to 1.28.\n- [Release notes](https://github.com/mojohaus/animal-sniffer/releases)\n- [Commits](https://github.com/mojohaus/animal-sniffer/compare/animal-sniffer-1.27...1.28)\n\n---\nupdated-dependencies:\n- dependency-name: org.codehaus.mojo:animal-sniffer-maven-plugin\n  dependency-version: '1.28'\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-16T09:02:04+03:00",
          "tree_id": "3e337182375d091721099c949390fedd6877af4b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/1a45098c4bdc0611e427976e64dd8d91797bb74f"
        },
        "date": 1789538668515,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3485,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6056,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9094.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11762.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11113,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11039.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11061,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "4583ee2d11c1874c502d31ce9d27cbd6b83ac555",
          "message": "build(deps): bump org.codehaus.mojo:extra-enforcer-rules (#442)\n\nBumps [org.codehaus.mojo:extra-enforcer-rules](https://github.com/mojohaus/extra-enforcer-rules) from 1.12.0 to 1.12.1.\n- [Release notes](https://github.com/mojohaus/extra-enforcer-rules/releases)\n- [Commits](https://github.com/mojohaus/extra-enforcer-rules/compare/1.12.0...1.12.1)\n\n---\nupdated-dependencies:\n- dependency-name: org.codehaus.mojo:extra-enforcer-rules\n  dependency-version: 1.12.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-16T09:06:58+03:00",
          "tree_id": "644921fe1d432b0e290358c07aad276a77bda37a",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/4583ee2d11c1874c502d31ce9d27cbd6b83ac555"
        },
        "date": 1789538958607,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3627.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6227.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8994.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11939,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11070.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10818.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11161,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "26bf16f98254d0d3f2312a0999aeed5c69232e1a",
          "message": "test: fix edge canary Sentinel startup (#440)\n\n* Initial plan\n\n* test: run SentinelIT with Redis 8 sentinel mode\n\nThe edge image no longer includes the redis-sentinel wrapper. Start Sentinel through redis-server with the Sentinel configuration instead.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: copilot-swe-agent[bot] <198982749+Copilot@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-16T10:59:37+03:00",
          "tree_id": "35d257ed91eb9b9de39488f1af46349a028f6aa7",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/26bf16f98254d0d3f2312a0999aeed5c69232e1a"
        },
        "date": 1789545747231,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5471.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10685.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 18259.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24783.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 24004.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 24223.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 22731,
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
          "id": "b1b6add8d468f2a01b51b42babab2041c63f1eb7",
          "message": "docs: add a Redis Sentinel connection example (#435)\n\n* docs: add a Redis Sentinel connection example\n\nSentinel support landed in #434 and is documented in the top-level\nREADME, but examples/ had no counterpart — the convention there is one\nrunnable class per connection style, and Sentinel was the only one\nmissing.\n\nSentinelDriver covers the four ways the API is meant to be used:\nauto-detection (the default), naming a master explicitly, supplying\nseparate Sentinel credentials, and opting out with\nautoDetectSentinel(false).\n\nOnly the first block performs I/O, so the example runs against a plain\nFalkorDB with no Sentinel in sight — which is itself the point, since\nauto-detection determines for itself whether the address it was given is\na Sentinel or a server. The remaining blocks build and close drivers\naimed at addresses that need not exist, because building one performs no\nnetwork calls.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: make Sentinel example safe for existing databases\n\nUse a unique graph name for each run and clarify that auto-detection requires a single-master Sentinel.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-16T11:46:24+03:00",
          "tree_id": "0c57ac5b3e4042ce5598fc03feaa63c886f1c127",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/b1b6add8d468f2a01b51b42babab2041c63f1eb7"
        },
        "date": 1789548496397,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4298,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6764.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10736,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 13375,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12316,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12440,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 12532,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "81733927f801b13292f356e16c7b456184b6a273",
          "message": "chore(master): release 0.12.1-SNAPSHOT (#433)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-16T14:56:11+03:00",
          "tree_id": "299a44af343ec4a1de0b2360459acb9259a5ae09",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/81733927f801b13292f356e16c7b456184b6a273"
        },
        "date": 1789559928277,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3707,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6310,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9530,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12138,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11390.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11212.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11338.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "c2f61cc709417eeac5e15a7320fd329f5ceb8eae",
          "message": "build(deps): bump github/codeql-action/analyze from 4.38.0 to 4.38.1 (#444)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.38.0 to 4.38.1.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/b96794f015dfd88f77b49b1c93e0fa7110f94c63...1c5b675653bb5c22dbe9b12b556ec555138e09fd)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.38.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-22T09:40:22+03:00",
          "tree_id": "a14919204bce7680aef46a207cf7b04646043507",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/c2f61cc709417eeac5e15a7320fd329f5ceb8eae"
        },
        "date": 1790059356606,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3660,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6211.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9830,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11991,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11358,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11057.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11221,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "7dd80c667815446cd1bbc93540435372f3079456",
          "message": "build(deps): bump codecov/codecov-action from 7.0.0 to 7.1.1 (#445)\n\nBumps [codecov/codecov-action](https://github.com/codecov/codecov-action) from 7.0.0 to 7.1.1.\n- [Release notes](https://github.com/codecov/codecov-action/releases)\n- [Changelog](https://github.com/codecov/codecov-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/codecov/codecov-action/compare/fb8b3582c8e4def4969c97caa2f19720cb33a72f...303a32d7a59b442fa8d48b6a1cc6825c09c847a5)\n\n---\nupdated-dependencies:\n- dependency-name: codecov/codecov-action\n  dependency-version: 7.1.1\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-22T09:59:49+03:00",
          "tree_id": "261d068bc7415e2be3776ce7a8896ea3e33aca6d",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7dd80c667815446cd1bbc93540435372f3079456"
        },
        "date": 1790060489746,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 8640,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 14385.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 21044,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 27233.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 25850.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23197.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 23218,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "981827757c22d172f6899045178ed7fa9dd99439",
          "message": "build(deps): bump benchmark-action/github-action-benchmark (#446)\n\nBumps [benchmark-action/github-action-benchmark](https://github.com/benchmark-action/github-action-benchmark) from 1.22.1 to 1.22.2.\n- [Release notes](https://github.com/benchmark-action/github-action-benchmark/releases)\n- [Changelog](https://github.com/benchmark-action/github-action-benchmark/blob/master/CHANGELOG.md)\n- [Commits](https://github.com/benchmark-action/github-action-benchmark/compare/52576c92bccf6ac60c8223ec7eb2565637cae9ba...4322e5726e6334590d251fc4f92bec0efafc45dc)\n\n---\nupdated-dependencies:\n- dependency-name: benchmark-action/github-action-benchmark\n  dependency-version: 1.22.2\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-09-22T10:19:40+03:00",
          "tree_id": "a73dc311ca86580ca0339b2141b397316d54a5dd",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/981827757c22d172f6899045178ed7fa9dd99439"
        },
        "date": 1790061728302,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6112.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11940,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19896.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 26351,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 24066,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 25065.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 24702,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "69b91e547f9ad4d9f05dc153e6df6b1a128d3066",
          "message": "build(deps): bump github/codeql-action/init from 4.38.0 to 4.38.1 (#447)\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.38.0 to 4.38.1.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/b96794f015dfd88f77b49b1c93e0fa7110f94c63...1c5b675653bb5c22dbe9b12b556ec555138e09fd)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.38.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-09-22T10:31:54+03:00",
          "tree_id": "4f3c2db1ddc6494fef481a66d43f38d1788a6d21",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/69b91e547f9ad4d9f05dc153e6df6b1a128d3066"
        },
        "date": 1790062471738,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3615.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6045.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9356.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11783.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11206.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11089.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11045.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "1025f9ec86f56add219e9d13563decc35ed5ee33",
          "message": "chore(master): release 0.13.0 (#443)\n\n* chore(master): release 0.13.0\n\n* docs: point the Official Releases snippet at 0.13.0\n\nReadmeVersionTest.officialReleasesSnippetNamesTheLatestRelease fails on\nevery release PR by design: the \"Official Releases\" block carries no\nx-release-please-version marker (a marker would be rewritten to a\n-SNAPSHOT version by the snapshot PR that follows each release), so it\nmust be bumped by hand before shipping. See issue #401.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-09-23T09:13:18+03:00",
          "tree_id": "65bfc6062203e343701a57817fbc8275927c2a18",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/1025f9ec86f56add219e9d13563decc35ed5ee33"
        },
        "date": 1790144105190,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 9005.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 15517,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 21202.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 26920,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 25629,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 25478,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 25968,
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
          "id": "f83a9503b94913dbe5563c65c1b7e338acc95bbb",
          "message": "build(deps): combine open Dependabot updates (#455)\n\n* build(deps-dev): bump com.github.siom79.japicmp:japicmp-maven-plugin\n\nBumps [com.github.siom79.japicmp:japicmp-maven-plugin](https://github.com/siom79/japicmp) from 0.26.2 to 0.26.3.\n- [Release notes](https://github.com/siom79/japicmp/releases)\n- [Changelog](https://github.com/siom79/japicmp/blob/master/release.py)\n- [Commits](https://github.com/siom79/japicmp/compare/japicmp-base-0.26.2...japicmp-base-0.26.3)\n\n---\nupdated-dependencies:\n- dependency-name: com.github.siom79.japicmp:japicmp-maven-plugin\n  dependency-version: 0.26.3\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit bc028e348868872968991b24f6bd262dca108702)\n\n* build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin\n\nBumps [com.diffplug.spotless:spotless-maven-plugin](https://github.com/diffplug/spotless) from 3.10.2 to 3.10.3.\n- [Release notes](https://github.com/diffplug/spotless/releases)\n- [Changelog](https://github.com/diffplug/spotless/blob/main/CHANGES.md)\n- [Commits](https://github.com/diffplug/spotless/compare/maven/3.10.2...maven/3.10.3)\n\n---\nupdated-dependencies:\n- dependency-name: com.diffplug.spotless:spotless-maven-plugin\n  dependency-version: 3.10.3\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit 6a92cd2c043cb6e3201cdad7bbca2e535ca7764b)\n\n* build(deps): bump github/codeql-action/init from 4.38.1 to 4.38.2\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.38.1 to 4.38.2.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/1c5b675653bb5c22dbe9b12b556ec555138e09fd...2892aa5e19bbd11bc0cff5427e3b750a04d9e3c2)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.38.2\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit 1a7f73811ee6db03f36e5f87a7b055f153e5084d)\n\n* build(deps): bump github/codeql-action/analyze from 4.38.1 to 4.38.2\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.38.1 to 4.38.2.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/1c5b675653bb5c22dbe9b12b556ec555138e09fd...2892aa5e19bbd11bc0cff5427e3b750a04d9e3c2)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.38.2\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit 54d22aa7e76058c8484eb0e3a1268765674481ef)\n\n* build(deps-dev): bump org.slf4j:slf4j-simple from 2.0.19 to 2.0.20\n\nBumps org.slf4j:slf4j-simple from 2.0.19 to 2.0.20.\n\n---\nupdated-dependencies:\n- dependency-name: org.slf4j:slf4j-simple\n  dependency-version: 2.0.20\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit a242c77dac953fec07c9213c92d5144376eeeef3)\n\n* build(deps): bump org.apache.maven:apache-maven from 3.9.16 to 3.10.0\n\nTakes only the wrapper properties from #453. Dependabot's regenerated mvnw.cmd\nis left out: it drops the local null-Target guard (apache/maven-wrapper#395) and\nwas committed with CRLF in the index, which dirties the checkout under\n.gitattributes and made benchmark-pr fail.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-07T08:59:08+03:00",
          "tree_id": "cbf67e35cc12af8e7a1365079e72e1897e5ebf8b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f83a9503b94913dbe5563c65c1b7e338acc95bbb"
        },
        "date": 1791352896317,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3484,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5897,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8544.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11406.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10678.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10518.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10514.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "07ca494b28e168aff14c8472932920b0aea1221c",
          "message": "chore(master): release 0.13.1-SNAPSHOT (#448)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-07T09:04:49+03:00",
          "tree_id": "f73dc60d699b31230adb2bad8a568e8de0f9354b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/07ca494b28e168aff14c8472932920b0aea1221c"
        },
        "date": 1791353224836,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3605.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6278,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9360,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11815.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11172,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11062.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11069.333,
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
          "id": "34f62499b9c181bc6808cf13ca15ce7a59bf7ff8",
          "message": "fix(build): restore Maven 3.9.16 so snapshot deploys authenticate (#456)\n\nMaven 3.10.0 (from #455) with central-publishing-maven-plugin 0.11.0 sends\nsnapshot uploads with no credentials: the plugin never retries Central's 401\nwith the `central` server's username/token, so Publish Snapshot failed with\n\"HTTP Status: 401\" on 0.13.1-SNAPSHOT. Maven 3.9.16 retries with them.\n\nReproduced locally by pointing -DcentralSnapshotsUrl at a server that answers\n401 without an Authorization header: 3.9.16 retries every request with the\ncredentials, 3.10.0 gives up on the first metadata GET.\n\nHold apache-maven >= 3.10.0 in Dependabot until a release is verified the\nsame way, since PR CI never authenticates to Central and cannot catch this.\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-07T10:32:51+03:00",
          "tree_id": "6a385078854083c4e41807367457ab92847c791b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/34f62499b9c181bc6808cf13ca15ce7a59bf7ff8"
        },
        "date": 1791358613176,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3607.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6063.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9392.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11850,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10895,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10803.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10836.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "161713cb6e8e9e0520eb999cbefe80f2d6c3f947",
          "message": "chore(master): release 0.13.1 (#457)\n\n* chore(master): release 0.13.1\n\n* docs: point the Official Releases snippet at 0.13.1\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-07T11:41:42+03:00",
          "tree_id": "5abc066010875a05db8d931ff9a66e5ca4635df7",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/161713cb6e8e9e0520eb999cbefe80f2d6c3f947"
        },
        "date": 1791362926819,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5265.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 8868,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14367.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18412.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17313.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17297.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 17150,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "158375dfafca45a06402abc42097c6059f15b5c7",
          "message": "chore(master): release 0.13.2-SNAPSHOT (#458)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-07T11:52:13+03:00",
          "tree_id": "cb9fd1977a38e713e8b200ca275bfeb09545511e",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/158375dfafca45a06402abc42097c6059f15b5c7"
        },
        "date": 1791363292726,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3457.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6102,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9217.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11701.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11110,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11010.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10949.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "ebe7259248e2de5dc1d2e6dd3ca232e1ffed7b5f",
          "message": "build(deps): bump redis.clients:jedis from 8.0.1 to 8.0.2 (#459)\n\nBumps [redis.clients:jedis](https://github.com/redis/jedis) from 8.0.1 to 8.0.2.\n- [Release notes](https://github.com/redis/jedis/releases)\n- [Changelog](https://github.com/redis/jedis/blob/master/docs/release-process.md)\n- [Commits](https://github.com/redis/jedis/compare/v8.0.1...v8.0.2)\n\n---\nupdated-dependencies:\n- dependency-name: redis.clients:jedis\n  dependency-version: 8.0.2\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-12T16:33:27+03:00",
          "tree_id": "e93a040dbb84dd2463d616f7b918b920a08079dd",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/ebe7259248e2de5dc1d2e6dd3ca232e1ffed7b5f"
        },
        "date": 1791812162030,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3579.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5981.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9363,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11718.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10996.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10936.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10975,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "d321b6bf4e2898f9082313642df51408c9296ad7",
          "message": "build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin (#460)\n\nBumps [com.diffplug.spotless:spotless-maven-plugin](https://github.com/diffplug/spotless) from 3.10.3 to 3.10.4.\n- [Release notes](https://github.com/diffplug/spotless/releases)\n- [Changelog](https://github.com/diffplug/spotless/blob/main/CHANGES.md)\n- [Commits](https://github.com/diffplug/spotless/compare/maven/3.10.3...maven/3.10.4)\n\n---\nupdated-dependencies:\n- dependency-name: com.diffplug.spotless:spotless-maven-plugin\n  dependency-version: 3.10.4\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-12T16:38:05+03:00",
          "tree_id": "f2bed276963f9a0a4a4586edc6d7af8989b53690",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/d321b6bf4e2898f9082313642df51408c9296ad7"
        },
        "date": 1791812428620,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3427.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6133.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8872,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11759.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11051,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10850.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11005.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8141421ca9aa09ffbc4af92441259ee20d076c8e",
          "message": "build(deps): bump github/codeql-action/init from 4.38.2 to 4.38.3 (#461)\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.38.2 to 4.38.3.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/2892aa5e19bbd11bc0cff5427e3b750a04d9e3c2...24c54180a607b1449ed407dd24f251e4e9147c8d)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.38.3\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-12T16:42:24+03:00",
          "tree_id": "372a16478e5168dc63e2e041017fa1996567a4a1",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/8141421ca9aa09ffbc4af92441259ee20d076c8e"
        },
        "date": 1791812660121,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3618.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6039.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9760.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12169.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11344.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11326.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11138,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "14474c564a4325f2cf9c45d754a02d7705a2c7c5",
          "message": "build(deps): bump actions/upload-artifact from 7.0.1 to 7.0.2 (#462)\n\nBumps [actions/upload-artifact](https://github.com/actions/upload-artifact) from 7.0.1 to 7.0.2.\n- [Release notes](https://github.com/actions/upload-artifact/releases)\n- [Commits](https://github.com/actions/upload-artifact/compare/043fb46d1a93c77aae656e7c1c64a875d1fc6a0a...cf430e030ddbb5b0abf93d22962f4752f3646cd9)\n\n---\nupdated-dependencies:\n- dependency-name: actions/upload-artifact\n  dependency-version: 7.0.2\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-13T07:23:47+03:00",
          "tree_id": "11e69c25cc43da2b7123f5cac47d522464d10347",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/14474c564a4325f2cf9c45d754a02d7705a2c7c5"
        },
        "date": 1791865568973,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5723.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9350,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14063.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17777.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16746.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16565.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16584.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "75e666923e45aaa0e7de6037fb2e7c3968673716",
          "message": "build(deps): bump github/codeql-action/analyze from 4.38.2 to 4.38.3 (#463)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.38.2 to 4.38.3.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/2892aa5e19bbd11bc0cff5427e3b750a04d9e3c2...24c54180a607b1449ed407dd24f251e4e9147c8d)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.38.3\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-13T07:29:28+03:00",
          "tree_id": "b32761b505d5731a0fe669728a7eb087bbc1523e",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/75e666923e45aaa0e7de6037fb2e7c3968673716"
        },
        "date": 1791865888667,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3502,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5989,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9250.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11704,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10882,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10897.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10905.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "9b2967389a8574020f88b1694d162faecb7923c1",
          "message": "build(deps-dev): bump net.jqwik:jqwik from 1.10.1 to 1.10.2 (#464)\n\nBumps [net.jqwik:jqwik](https://github.com/jqwik-team/jqwik) from 1.10.1 to 1.10.2.\n- [Release notes](https://github.com/jqwik-team/jqwik/releases)\n- [Commits](https://github.com/jqwik-team/jqwik/compare/1.10.1...1.10.2)\n\n---\nupdated-dependencies:\n- dependency-name: net.jqwik:jqwik\n  dependency-version: 1.10.2\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-15T10:45:49+03:00",
          "tree_id": "d87ce8603b7a7b871454b40955a5169ea05f5e20",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/9b2967389a8574020f88b1694d162faecb7923c1"
        },
        "date": 1792050500816,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5646.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10507.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 15761.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 21329,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 20086.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 19655.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 19644.333,
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
          "id": "3e63628e4df0f54ef596b2e4debc53902e1046b7",
          "message": "ci: retry Maven downloads and warm the dependency cache (#465)\n\nThe daily canary failed after all 334 tests passed because maven-jar-plugin's\ndependency maven-archiver:3.6.6 could not be resolved, a transient Maven Central\nerror. Raise the resolver's HTTP retry count via .mvn/maven.config (Maven 3.9 uses\nthe native transport, so the wagon.* properties would be ignored) and resolve all\nplugins/dependencies up front with `just fetch-deps` in the verify jobs.\n\nCo-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-15T10:55:33+03:00",
          "tree_id": "18270472bd77be827c182ddc166102bd5d56e4d0",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/3e63628e4df0f54ef596b2e4debc53902e1046b7"
        },
        "date": 1792051045463,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5483,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9208.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13653.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18217,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16539.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16548.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16577.667,
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
          "id": "950c19c9c2fa3b5cafe0fa6f01bbcf4a2085392b",
          "message": "ci: release runtime dependency bumps via fix(deps) (#466)\n\nDependabot titled every bump build(deps), which release-please ignores, so\ndependency updates never produced a release PR. Use the fix prefix for runtime\ndependencies; development dependencies keep build(deps-dev).\n\nCo-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-15T11:03:32+03:00",
          "tree_id": "70044c94aee06d3bc3008576e0453b6d2a603759",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/950c19c9c2fa3b5cafe0fa6f01bbcf4a2085392b"
        },
        "date": 1792051520544,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3579,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6121,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9255,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11783,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10955,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10867.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10954,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "ad0d8501d589540c2d36016df7428cab9fe8611c",
          "message": "build(deps-dev): bump com.github.spotbugs:spotbugs-maven-plugin (#467)\n\nBumps [com.github.spotbugs:spotbugs-maven-plugin](https://github.com/spotbugs/spotbugs-maven-plugin) from 4.10.4.1 to 4.10.5.0.\n- [Release notes](https://github.com/spotbugs/spotbugs-maven-plugin/releases)\n- [Commits](https://github.com/spotbugs/spotbugs-maven-plugin/compare/spotbugs-maven-plugin-4.10.4.1...spotbugs-maven-plugin-4.10.5.0)\n\n---\nupdated-dependencies:\n- dependency-name: com.github.spotbugs:spotbugs-maven-plugin\n  dependency-version: 4.10.5.0\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-15T11:53:25+03:00",
          "tree_id": "2a1f6ad53aa039856d7dba9f582b017920a2b9d1",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/ad0d8501d589540c2d36016df7428cab9fe8611c"
        },
        "date": 1792054537748,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3374,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6040.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8709,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11557,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 10982.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10891,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10735.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "4849d63e329207a741002824ba0990db4579b65f",
          "message": "build(deps): bump actions/create-github-app-token from 3.2.0 to 3.3.0 (#468)\n\nBumps [actions/create-github-app-token](https://github.com/actions/create-github-app-token) from 3.2.0 to 3.3.0.\n- [Release notes](https://github.com/actions/create-github-app-token/releases)\n- [Changelog](https://github.com/actions/create-github-app-token/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/actions/create-github-app-token/compare/bcd2ba49218906704ab6c1aa796996da409d3eb1...64ab729a6294000f5aa4a4bc0b73d1f32bc45510)\n\n---\nupdated-dependencies:\n- dependency-name: actions/create-github-app-token\n  dependency-version: 3.3.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-15T12:05:54+03:00",
          "tree_id": "4bc6cd36400d6ddcd969fadb605bc82d48eb230b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/4849d63e329207a741002824ba0990db4579b65f"
        },
        "date": 1792055274888,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3491.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5993.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9224.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11642,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11109.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11005.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10980,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "gkorland@gmail.com",
            "name": "Guy Korland",
            "username": "gkorland"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "daae63d49a486b665be5dbf3cc1b506dda422825",
          "message": "docs: fix stale links (#470)\n\n- Update stale links: `master` links on repos whose default branch is now `main`, and the shut-down OSSRH snapshot repository (`oss.sonatype.org`) replaced with the Central Portal snapshot repository, which this project already publishes to.\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-18T07:51:42+03:00",
          "tree_id": "1aa1071bb3b5c235fa12c1fdc43abeddfd4b1df4",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/daae63d49a486b665be5dbf3cc1b506dda422825"
        },
        "date": 1792299220061,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3538,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5987.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9340.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11910.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11274,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11238.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11156.333,
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
          "id": "1d2fe4c4c6a89a309736f9528582a7adaa770475",
          "message": "feat: support Redis Cluster (#469)\n\n* feat: support Redis Cluster\n\nConnect to a FalkorDB deployment running as a Redis Cluster, following the\nPython client: the driver probes the endpoint with INFO server and, if it\nreports redis_mode:cluster, learns the slot layout with CLUSTER SLOTS and\nsends every graph command to the primary that owns the graph's hash slot.\n\n- Graph commands, pipelines and transactions go to the node owning the graph.\n- listGraphs() merges the graphs of every primary; udfLoad/udfDelete/udfFlush\n  and configSet run on every primary; configGet and udfList ask one node.\n- A MOVED/ASK redirect relearns the layout and retries (3 attempts); an\n  unreachable node relearns the layout but is not retried.\n- FalkorDB.builder() gains cluster(...) for explicit startup nodes and\n  autoDetectCluster(boolean) to skip the probe.\n- Adds ClusterOptions, Clusters, ClusterConnections and a Connections seam in\n  DriverImpl; the existing single-pool behaviour is unchanged.\n\nAdds unit tests against an in-process fake cluster, an integration test against\na real three-primary cluster, an example and README documentation.\n\nCloses #26\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n\n* fix: address review feedback on cluster support\n\nBound the topology-refresh read timeout, publish slot table and primaries as one\nsnapshot, relearn the topology after a failed cluster-wide call, keep the original\nfailure when a refresh also fails, reject a database index against a cluster, close\npools created during close(), and tighten docs, example and tests.\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n\n* test: raise patch coverage of cluster support\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n\n* docs: say which commit type causes which release\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n\n* fix: key ClusterIT owners by endpoint and tighten release-type docs\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n\n* docs: list test: among accepted PR title types\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-18T08:10:59+03:00",
          "tree_id": "10674f34f55fc64d88321267cc84c4240eb95df0",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/1d2fe4c4c6a89a309736f9528582a7adaa770475"
        },
        "date": 1792300406829,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5101,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 8954,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13479,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18175,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17343.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17085.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 17199,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "bce597db4bb5d669726e4c8fa25f6d48230f1715",
          "message": "chore(master): release 0.14.0 (#471)\n\n* chore(master): release 0.14.0\n\n* docs: point the Official Releases snippet at 0.14.0\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>\nCo-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-18T09:58:19+03:00",
          "tree_id": "25a27a34aa8b380daf4db250202f043e7a27c42b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/bce597db4bb5d669726e4c8fa25f6d48230f1715"
        },
        "date": 1792306848873,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3516.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6021,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9380,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11847.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11117,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11031.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11042.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "5d6366bf5b92a59ead0b6dee788b7e9c4fe72837",
          "message": "chore(master): release 0.14.1-SNAPSHOT (#472)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-18T11:24:40+03:00",
          "tree_id": "2091fc367f96b3ab752aae872d0f3390e98a834b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/5d6366bf5b92a59ead0b6dee788b7e9c4fe72837"
        },
        "date": 1792312025137,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3641,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6022.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9643.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11741.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11234,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11150,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11114.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f4cb57e005d38bcdbc039842e845d906218e8d18",
          "message": "build(deps-dev): bump org.pitest:pitest-maven from 1.30.0 to 1.30.1 (#473)\n\nBumps [org.pitest:pitest-maven](https://github.com/hcoles/pitest) from 1.30.0 to 1.30.1.\n- [Release notes](https://github.com/hcoles/pitest/releases)\n- [Commits](https://github.com/hcoles/pitest/compare/1.30.0...1.30.1)\n\n---\nupdated-dependencies:\n- dependency-name: org.pitest:pitest-maven\n  dependency-version: 1.30.1\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-19T09:29:03+03:00",
          "tree_id": "55cbc9a2a598e8a961bce12a9e299e08f8e94cf3",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f4cb57e005d38bcdbc039842e845d906218e8d18"
        },
        "date": 1792391508700,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3607.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6566.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9176.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12076,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11244.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11284.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11260.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "3d31265203fb8313733245a212505911d5c4e47d",
          "message": "build(deps-dev): bump org.sonatype.central:central-publishing-maven-plugin (#474)\n\nBumps [org.sonatype.central:central-publishing-maven-plugin](https://github.com/sonatype-nexus-community/central-publishing-maven-plugin) from 0.11.0 to 0.12.0.\n- [Release notes](https://github.com/sonatype-nexus-community/central-publishing-maven-plugin/releases)\n- [Commits](https://github.com/sonatype-nexus-community/central-publishing-maven-plugin/commits/release-0.12.0)\n\n---\nupdated-dependencies:\n- dependency-name: org.sonatype.central:central-publishing-maven-plugin\n  dependency-version: 0.12.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-19T09:41:33+03:00",
          "tree_id": "e08ecdc5214b274e206b4a5bbdd7e2bbea06690a",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/3d31265203fb8313733245a212505911d5c4e47d"
        },
        "date": 1792392254203,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3459.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5872.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9531.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11769.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11053.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10993,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11094.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "4498207571f60f439c50a341fd32a71c6140048e",
          "message": "build(deps): bump github/codeql-action/init from 4.38.3 to 4.39.0 (#475)\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.38.3 to 4.39.0.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/24c54180a607b1449ed407dd24f251e4e9147c8d...6f2ac81f5cd81fe351a197ba0b00d91d03cd2aee)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.39.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-19T09:57:37+03:00",
          "tree_id": "766a7bab932239dc2061032e881de0ea10663e78",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/4498207571f60f439c50a341fd32a71c6140048e"
        },
        "date": 1792393213360,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 8268.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 13648,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 20188.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 25798.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 24140.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 22217.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 23370.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "21416cc6ee8c8cdc5e426676195c738c2f2733ea",
          "message": "build(deps): bump actions/setup-java from 6.0.1 to 6.1.0 (#476)\n\nBumps [actions/setup-java](https://github.com/actions/setup-java) from 6.0.1 to 6.1.0.\n- [Release notes](https://github.com/actions/setup-java/releases)\n- [Commits](https://github.com/actions/setup-java/compare/de7274f081f381c8f8158605e0321c36c376e2e6...b843ebf9b913146b38da2eaa7aeddf76074ae1ad)\n\n---\nupdated-dependencies:\n- dependency-name: actions/setup-java\n  dependency-version: 6.1.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-19T10:28:31+03:00",
          "tree_id": "5fc3a3a24be25f6fde08106fc5e53f747cc47b00",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/21416cc6ee8c8cdc5e426676195c738c2f2733ea"
        },
        "date": 1792395260043,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3556,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6093,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8670.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11704,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11033.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11016.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10840,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6e8288fff81639ab75ec18e40c026952c09afee1",
          "message": "build(deps): bump github/codeql-action/analyze from 4.38.3 to 4.39.0 (#477)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.38.3 to 4.39.0.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/24c54180a607b1449ed407dd24f251e4e9147c8d...6f2ac81f5cd81fe351a197ba0b00d91d03cd2aee)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.39.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-19T11:09:22+03:00",
          "tree_id": "17b96d04c81a8634fb427baa01ffbb2f7edc7fd3",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/6e8288fff81639ab75ec18e40c026952c09afee1"
        },
        "date": 1792397614885,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3468,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6027.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9287.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11718,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11043.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10940.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10898,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "6c6ce64ca0f35169964a6c9408dcda55f7164b6e",
          "message": "build(deps-dev): bump com.github.siom79.japicmp:japicmp-maven-plugin (#478)\n\nBumps [com.github.siom79.japicmp:japicmp-maven-plugin](https://github.com/siom79/japicmp) from 0.26.3 to 0.26.5.\n- [Release notes](https://github.com/siom79/japicmp/releases)\n- [Changelog](https://github.com/siom79/japicmp/blob/master/release.py)\n- [Commits](https://github.com/siom79/japicmp/compare/japicmp-base-0.26.3...japicmp-base-0.26.5)\n\n---\nupdated-dependencies:\n- dependency-name: com.github.siom79.japicmp:japicmp-maven-plugin\n  dependency-version: 0.26.5\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-21T10:31:21+03:00",
          "tree_id": "be950b278363907f66c91834e621a93083d212a6",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/6c6ce64ca0f35169964a6c9408dcda55f7164b6e"
        },
        "date": 1792568019488,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 7994,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 12818.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19743,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24055,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 21881,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 21796.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 22163.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "e33592fb4a350e6f0fc150e5b05de94661286b9c",
          "message": "build(deps-dev): bump org.apache.maven.plugins:maven-surefire-plugin (#479)\n\nBumps [org.apache.maven.plugins:maven-surefire-plugin](https://github.com/apache/maven-surefire) from 3.6.0 to 3.6.1.\n- [Release notes](https://github.com/apache/maven-surefire/releases)\n- [Commits](https://github.com/apache/maven-surefire/compare/surefire-3.6.0...surefire-3.6.1)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.plugins:maven-surefire-plugin\n  dependency-version: 3.6.1\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-26T08:19:24+02:00",
          "tree_id": "1958eea335603d8276e338a688e54b1d9aecc6a8",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/e33592fb4a350e6f0fc150e5b05de94661286b9c"
        },
        "date": 1792995725013,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3571.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6222.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9444,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11937.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11390,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11191,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11147.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "7ee2556cfe907489a43807e874ade94b1c42417c",
          "message": "fix(deps): bump com.google.errorprone:error_prone_core (#480)\n\nBumps [com.google.errorprone:error_prone_core](https://github.com/google/error-prone) from 2.50.0 to 2.51.0.\n- [Release notes](https://github.com/google/error-prone/releases)\n- [Commits](https://github.com/google/error-prone/compare/v2.50.0...v2.51.0)\n\n---\nupdated-dependencies:\n- dependency-name: com.google.errorprone:error_prone_core\n  dependency-version: 2.51.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-26T08:22:50+02:00",
          "tree_id": "25e9b6d27145160b8678ece353979a10f8007f62",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7ee2556cfe907489a43807e874ade94b1c42417c"
        },
        "date": 1792995931414,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3268.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5909.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9287.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11710.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11022.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10999,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10971.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "8e3e9336288f62aa33bf4da7bb61a16a41a5a164",
          "message": "build(deps-dev): bump org.apache.maven.plugins:maven-failsafe-plugin (#481)\n\nBumps [org.apache.maven.plugins:maven-failsafe-plugin](https://github.com/apache/maven-surefire) from 3.6.0 to 3.6.1.\n- [Release notes](https://github.com/apache/maven-surefire/releases)\n- [Commits](https://github.com/apache/maven-surefire/compare/surefire-3.6.0...surefire-3.6.1)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.plugins:maven-failsafe-plugin\n  dependency-version: 3.6.1\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-10-26T08:28:41+02:00",
          "tree_id": "29b2d4220449cc097da57d6d62a6ef3443d6c78b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/8e3e9336288f62aa33bf4da7bb61a16a41a5a164"
        },
        "date": 1792996262654,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3415.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6119,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9296.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11727.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11095,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10939.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10825.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "400fdb55eb6e5ca10df2d2d03ae32f7527f2912a",
          "message": "build(deps): bump github/codeql-action/analyze from 4.39.0 to 4.39.1 (#482)\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.39.0 to 4.39.1.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/6f2ac81f5cd81fe351a197ba0b00d91d03cd2aee...9b3254085494b55a0f7c4c2b3d2d52cc712ce370)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.39.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-26T08:33:36+02:00",
          "tree_id": "009d9e2ef8561683cecbfcc0a7ce0a948ae46dcb",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/400fdb55eb6e5ca10df2d2d03ae32f7527f2912a"
        },
        "date": 1792996555102,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6180.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11645,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 18397.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24701.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 23798,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23006,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 22968,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "7524674a73f2c1e1ead185a15e99dba3150044be",
          "message": "build(deps): bump github/codeql-action/init from 4.39.0 to 4.39.1 (#483)\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.39.0 to 4.39.1.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/6f2ac81f5cd81fe351a197ba0b00d91d03cd2aee...9b3254085494b55a0f7c4c2b3d2d52cc712ce370)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.39.1\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-10-26T08:40:23+02:00",
          "tree_id": "5b6c08a28f182466ec919414df5f33229d718dce",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7524674a73f2c1e1ead185a15e99dba3150044be"
        },
        "date": 1792996968816,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3542,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 5952.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 8863.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11906.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11024.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11130,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11041.667,
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
          "id": "c8fe919b2ca6a0a45c7b95ec7e299ea054267bcf",
          "message": "docs: import the engineering conventions into CLAUDE.md (#494)\n\nCLAUDE.md only linked to .github/copilot-instructions.md. Claude Code\ndoesn't follow Markdown links when a session starts, so a session began\nwithout the conventions unless it happened to open the file. An @-import\nline makes Claude Code load the file into every session.\n\n\"github\" joins the spellcheck wordlist because the spellchecker reads the\nimport path as prose.\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-10-28T06:22:54+02:00",
          "tree_id": "c9d50f8675812f93f8d06be10b1c2f8d52d66d29",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/c8fe919b2ca6a0a45c7b95ec7e299ea054267bcf"
        },
        "date": 1793161538849,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3966.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6596.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9632,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11894.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11447.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 10955.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 10961,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "0116aadf10c62309a0daa6935852c709ab966789",
          "message": "chore(deps): bump org.jacoco:jacoco-maven-plugin from 0.8.15 to 0.8.16\n\nReviewed dependency update.\n\nCo-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-11-02T04:59:21+02:00",
          "tree_id": "635be422136fd29d7e3573eacb1e545f09554fd3",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/0116aadf10c62309a0daa6935852c709ab966789"
        },
        "date": 1793588519938,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5177,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 8912.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13403.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17861,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17109.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16948.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16754.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "b5a3a1cfca0b1b6adf8b3804d19dab58dc56ef21",
          "message": "build(deps-dev): bump org.apache.maven.plugins:maven-enforcer-plugin (#504)\n\nBumps [org.apache.maven.plugins:maven-enforcer-plugin](https://github.com/apache/maven-enforcer) from 3.6.3 to 3.7.0.\n- [Release notes](https://github.com/apache/maven-enforcer/releases)\n- [Commits](https://github.com/apache/maven-enforcer/compare/enforcer-3.6.3...enforcer-3.7.0)\n\n---\nupdated-dependencies:\n- dependency-name: org.apache.maven.plugins:maven-enforcer-plugin\n  dependency-version: 3.7.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-11-03T08:56:24+02:00",
          "tree_id": "e1c11eb705a4fcffbb5f497a7261999b965ee9a8",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/b5a3a1cfca0b1b6adf8b3804d19dab58dc56ef21"
        },
        "date": 1793689138919,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5951.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11158.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 17147.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 22332.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 20934,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 20763.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 20865.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "198982749+Copilot@users.noreply.github.com",
            "name": "Copilot",
            "username": "Copilot"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "a777901f513125b2378adfa5652073f334a0cc7e",
          "message": "fix: do not close the borrowed connection in GraphContext.deleteGraph() on error (#493)\n\n* Initial plan\n\n* fix: keep the borrowed connection bound to GraphContext when deleteGraph fails\n\nCo-authored-by: barakb <1074551+barakb@users.noreply.github.com>\n\n* docs: keep the GraphContext error-reporting note accurate for all commands\n\nCo-authored-by: barakb <1074551+barakb@users.noreply.github.com>\n\n---------\n\nCo-authored-by: copilot-swe-agent[bot] <198982749+Copilot@users.noreply.github.com>\nCo-authored-by: barakb <1074551+barakb@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>",
          "timestamp": "2026-11-03T09:05:04+02:00",
          "tree_id": "1a96a50a0b35bcfd0acffab309a65d7e3d3e0b68",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/a777901f513125b2378adfa5652073f334a0cc7e"
        },
        "date": 1793689642801,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6235.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9687.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14235.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18218.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16641.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16562,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16503.667,
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
          "id": "06327d476e038c80bc087bd4b0989693922bd32b",
          "message": "feat!: add configGetAll() and make configGet() reject the \"*\" wildcard (#495)\n\n* feat!: add configGetAll() and make configGet() reject the \"*\" wildcard\n\nGRAPH.CONFIG GET * answers with a list of [name, value] pairs, but\nconfigGet read every reply as a single pair and returned the second\npair's toString(), e.g. \"[[B@2133814f, 0]\".\n\n- Driver.configGetAll() returns every parameter as an unmodifiable map,\n  in the server's order, keyed by the server's upper-case names. It is\n  a default method that throws UnsupportedOperationException, so a class\n  implementing Driver elsewhere keeps compiling; DriverImpl implements it.\n- configGet rejects \"*\" with IllegalArgumentException before borrowing\n  a connection. A reply that still holds several parameters is rejected\n  the same way, and a single pair nested in a list is unwrapped.\n- The reply parser accepts only pairs of exactly two items whose name\n  and value are bulk strings, integers or strings, so a reply it doesn't\n  understand fails with JedisDataException instead of being half-read.\n- configGet and configSet reject a null name with\n  IllegalArgumentException before any I/O.\n\nBREAKING CHANGE: configGet(\"*\") now throws IllegalArgumentException\ninstead of returning a meaningless string. Use configGetAll() to read\nevery parameter.\nCloses #489\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: cover configGetAll() on a driver that doesn't implement it\n\nA Driver written outside the library before configGetAll() existed still\ncompiles, and calling the new method throws UnsupportedOperationException\nnaming the class. No test reached that default method.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T09:29:49+02:00",
          "tree_id": "c8fb8aea2cfe559139ac4c6edb587461c3c02c96",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/06327d476e038c80bc087bd4b0989693922bd32b"
        },
        "date": 1793691128573,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 8649.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 13206,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19993.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24206,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 22595.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 22426.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 22509.333,
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
          "id": "2608edb35cf1c0a337933744227812b1ecfe5895",
          "message": "fix!: replace stale entries and reject unknown ids in the graph schema cache (#496)\n\n* fix!: replace stale entries and reject unknown ids in the graph schema cache\n\nGraphCacheList refreshed its list on a miss but kept every entry it already\nhad and appended only the rest. After the graph was deleted and recreated\nelsewhere, the low ids went on decoding to the old graph's names. An id the\nfresh list still didn't reach, or a negative one, escaped as an\nIndexOutOfBoundsException, and so could a lookup that raced a clear().\n\nThe list is now an immutable snapshot, replaced as a whole under the refresh\nlock. A refresh replaces every entry, a reader checks the bounds and reads\nfrom one snapshot, and an unknown or negative id throws GraphException.\nResultSetImpl range-checks the ids as longs before narrowing them to int, as\nHeaderImpl already does for column types.\n\nBREAKING CHANGE: a reply that names a label, relationship type or property\nkey the graph doesn't list now fails with GraphException instead of\nIndexOutOfBoundsException, and an id beyond the int range fails instead of\nwrapping to another entry. GraphException is a JedisDataException, so\nGraphPipeline.syncAndReturnAll() and GraphTransaction.exec() now return it as\nthat command's result instead of throwing it.\n\nCloses #490\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: start clearing only once every reader is looking up\n\nWithout the latch the clear loop could finish before a reader ran, and the\ntest would pass without exercising the race.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: count a reader only once it has looked up while clear() runs\n\nThe readiness latch counted a reader down after its warm-up lookup, so a\nreader descheduled before its loop could sit out the 200 ms of clears.\nEach reader now counts down after finishing a lookup that began once the\nclearing had, and the clearing goes on until all four have (at most 10 s),\nwhich the test asserts.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T09:36:20+02:00",
          "tree_id": "8e731041e51c2c9abcb75513533ca07b1b243761",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/2608edb35cf1c0a337933744227812b1ecfe5895"
        },
        "date": 1793691509732,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 9068,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 14508.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 20694.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24872,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 23465.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 23211.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 23053.667,
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
          "id": "7bbce00f1f0536fca0aedd36a18fba7e6e284485",
          "message": "fix!: validate procedure names and YIELD columns in callProcedure (#497)\n\n* fix!: validate procedure names and YIELD columns in callProcedure\n\nUtils.prepareProcedure quoted the procedure's arguments but wrote the\nprocedure name and each YIELD column into the query as they were, so either\ncould add clauses: callProcedure(\"db.labels() YIELD label MATCH (n) DETACH\nDELETE n //\") deleted every node. A null name or column was written as\n\"null\", and a null args, kwargs or argument threw NullPointerException.\n\nThe name must now be Cypher identifiers joined by dots, such as db.labels or\ndb.idx.fulltext.queryNodes, and each YIELD column a single identifier.\nAnything else, or a null input, throws IllegalArgumentException before\nanything is sent or queued. Every procedure the server lists fits the rule.\nThe name is checked with a loop rather than a regex: Java matches a repeated\nregex group recursively, so a long dotted name would overflow the stack.\n\nBREAKING CHANGE: callProcedure rejects with IllegalArgumentException what it\nused to send: a backtick-quoted procedure name, a YIELD column with an AS\nalias or holding several columns (\"node, score\"), and anything that isn't\nidentifiers. A null procedure, args, kwargs, argument or column throws\nIllegalArgumentException instead of NullPointerException or sending \"null\".\nPass one column per list element, without aliases, and read results by\nthose names.\n\nCloses #487\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: spell out the identifier grammar callProcedure enforces\n\nName the ASCII rule, [A-Za-z_][A-Za-z0-9_]*, wherever callProcedure's checks\nare described, reword the llms.txt sentence, and wait for the async test's\nfuture with a timeout instead of an unbounded join().\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: say an async callProcedure fails with IllegalArgumentException when its task runs\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: say callProcedure checks a name's form, not which procedure runs\n\nA well-formed name can still call a procedure that changes the graph,\nsuch as db.idx.fulltext.drop. The README, llms.txt and the Javadoc of\nGraph.query(String, Map) now say so, and that names from untrusted input\nneed an allowlist.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T09:42:26+02:00",
          "tree_id": "cd383177a1b58c52662640b2e4ffa6fcbeb175c1",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7bbce00f1f0536fca0aedd36a18fba7e6e284485"
        },
        "date": 1793691873775,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4550.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7229.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 11156.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 14066.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13429.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 13204.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 13187,
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
          "id": "ce4794354bc2724247c54d7f71652abe35935203",
          "message": "fix!: keep the socket timeout for queries sent with a timeout, and reject negative timeouts (#498)\n\n* fix!: keep the socket timeout for queries sent with a timeout, and reject negative timeouts\n\nGraphContextImpl sent GRAPH.QUERY and GRAPH.RO_QUERY with a timeout through\nJedis' sendBlockingCommand, which is meant for BLPOP and the like: it turns\nthe socket timeout off for the read. Graph.query(q, timeout) and its three\nsiblings therefore waited forever for a dead or silent server, whatever\nsocket timeout the driver was configured with. They now use sendCommand, as\nevery other command does, so the configured socket timeout bounds the wait.\n\nA negative timeout was sent as it was, and the server took it as \"use the\ndefault timeout\". Every timeout overload of Graph, GraphContext,\nGraphPipeline and GraphTransaction now rejects it with\nIllegalArgumentException before anything is sent or queued.\n\nBREAKING CHANGE: a query sent with a timeout now also obeys the connection's\nsocket timeout. With the drivers FalkorDB.builder() builds nothing changes\nunless socketTimeout is set, but a pool passed to DriverImpl(Pool) gets\nJedis' 2000 ms default, so on such a pool query(q, 30_000) now fails with a\nread timeout after 2 s while the server may still run it. Set the socket\ntimeout above your longest query timeout, or to 0. A negative timeout throws\nIllegalArgumentException instead of running with the server's default.\n\nCloses #491\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: compare the sent-command count as a long\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: qualify the async and builder timeout notes\n\nSay that an async call's IllegalArgumentException applies when the task\nruns, and that FalkorDB.builder()'s Sentinel and cluster discovery requests\nkeep their 2000 ms deadline.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: a socket timeout limits each read and doesn't stop the query\n\n- The socket timeout limits how long each read waits for data, not the\n  whole reply. The server sends nothing until a query has finished, so\n  it still has to exceed the query timeout.\n- After a socket timeout the server may still run the query to the end,\n  so a write's outcome is unknown and a query that isn't idempotent\n  shouldn't be retried blindly.\n- The README said a FalkorDB.builder() driver's Sentinel and cluster\n  discovery requests use a 2000 ms deadline. Only the detection probes\n  do, and only when socketTimeout isn't set; the connections to the\n  Sentinels, which look up the master's address, never have one.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: send a valid timeout from a pipeline and a transaction\n\nThe pipeline and transaction timeout overloads were only called with a\nnegative timeout. Each of the eight now queues its query with\nTIMEOUT 1000, and the reply decodes after sync() or exec().\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T09:47:18+02:00",
          "tree_id": "713e1b3666d49adfd72710a3c26b4a831e0155a6",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/ce4794354bc2724247c54d7f71652abe35935203"
        },
        "date": 1793692182764,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5056,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 8838.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13803,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17726.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16889.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16780,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16302.667,
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
          "id": "259ba54f21d841da7101c71173805c3232823585",
          "message": "fix!: report explain, copyGraph and deleteGraph errors as GraphException (#499)\n\n* fix!: report explain, copyGraph and deleteGraph errors as GraphException\n\nGraph.explain() threw Jedis' raw JedisDataException when the server rejected\nthe query, while GraphContext.explain() wrapped it in GraphException, as\nevery query method does. copyGraph() and deleteGraph() wrapped nothing on\neither.\n\nGraphImpl now runs explain, copyGraph and deleteGraph through a short-lived\ncontext, as it already did for queries and profiles, and GraphContextImpl\nwraps copyGraph and deleteGraph errors like its other commands. deleteGraph\nno longer closes the context's connection when the server rejects it, which\nis the change open PR #493 makes for #485, byte for byte: with the\ndelegation, the old close would have returned the connection to the pool\ntwice. The GRAPH.EXPLAIN reply parser, copied into four classes (and into\nfour test helpers), is now one, ExplainPlans, which throws an error element\ninstead of turning it into a plan line.\n\nBREAKING CHANGE: explain(), copyGraph() and deleteGraph() on a Graph, and\ncopyGraph() and deleteGraph() on a GraphContext, throw GraphException for a\nserver error instead of the JedisDataException Jedis raised. GraphException\nextends JedisDataException, so catch (JedisDataException) still matches, but\na catch of a Jedis subclass such as JedisAccessControlException (NOPERM) no\nlonger does: look at getCause(). The message now starts with the cause's\nclass name, as for the other wrapped commands.\n\nCloses #488\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: check failed commands return their connection, and the message's cause prefix\n\nAlso narrow GraphException's Javadoc to the replies that really are reported\nas one.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: a GraphException from the connection isn't wrapped again\n\nGraphException is a JedisDataException, so copyGraph() and deleteGraph()\nrethrow one before wrapping the rest. No test reached that catch.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: add Javadoc to the helper this PR touches that had none\n\nCodeRabbit's Docstring Coverage check counts the functions a PR touches.\nExplainPlans.line had no Javadoc. The touched functions still without\none are test methods, which this repo names rather than documents.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: make every get() of a failed pipelined or transacted plan throw\n\nJedis builds a Response once and forgets an exception its builder\nthrew: later get() calls return null. syncAndReturnAll() and exec() call\nget() themselves, so the caller of a pipelined or transacted explain\nwhose plan held an error got null instead of the GraphException.\n\nExplainPlans.queue() now returns a response that keeps the failure and\nthrows it on every get(). The tests run real pipelines and transactions\nagainst a fake server.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: follow a redirect to the new owner for explain, copy and delete\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: answer EXEC without MULTI with an error in the explain fake server\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T10:51:30+02:00",
          "tree_id": "e244631178d83f523e8fd8a627e98348705047a7",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/259ba54f21d841da7101c71173805c3232823585"
        },
        "date": 1793695998780,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 8892.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 15064.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 19776.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 24381,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 23409.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 22248,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 22908.333,
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
          "id": "a5a14483d3d4ef2a10171c3b1b51d3efd30e1095",
          "message": "fix!: clear the schema cache when deleteGraph runs, and decode queued replies safely (#500)\n\n* fix!: clear the schema cache when deleteGraph runs, and decode queued replies safely\n\nPipelines and transactions build a graph reply only when it is read, which\ncan be long after the command ran. deleteGraph() cleared the schema cache\nwhen it was queued, so a reply queued before it was decoded with an empty\ncache and looked its ids up in whatever graph existed later, or failed. A\ndiscarded or aborted delete cleared it too. Lookups also always ran on the\ncontext's connection: after close() that connection may be lent to another\nthread, and while commands were in flight a lookup read one of their replies.\n\nThe delete's reply now clears the cache, so only a delete that ran does.\nEach deleteGraph() ends a segment of the queue; once the delete has run, the\nreplies before it are decoded with a frozen copy of the cache, and an id the\ncopy lacks is reported instead of looked up in another graph. After sync() a\nfailed delete re-opens its segment. Lookups run on the context's connection\nwhile it is open and idle, and otherwise on a connection borrowed from the\ngraph that created the context, borrowed before decoding so no thread waits\nfor the pool while holding the cache's lock. GraphContextImpl.close() is now\nidempotent.\n\nBREAKING CHANGE: a reply queued before deleteGraph() in the same pipeline or\ntransaction, needing an id the client hadn't cached, now fails with\nGraphException instead of being looked up after the delete, where it either\nfailed or silently got a recreated graph's names. syncAndReturnAll() and\nexec() decode before they learn whether the delete succeeded, so this also\napplies to a delete that fails there.\n\nCloses #486\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: borrow for a queued reply only when it needs a lookup\n\nWith a context holding its pool's only connection and a command still in\nflight, reading a reply borrowed a second connection before decoding,\neven for a scalar or a reply whose ids the cache knew, and waited forever.\nThe reply is now decoded first with a graph that refuses lookups, and only\na reply that needs one borrows, still before any cache lock is taken.\n\nAlso: GraphContextImpl.close() uses an atomic flag; a frozen cache is its\nown snapshot; a delete that ran drops its segment's forwarding link, so a\nreply held from an early batch doesn't keep later copies of the cache\nalive; the pipeline and transaction docs say what happens to replies before\na delete that fails; and the transaction IT checks the delete ran.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: look nothing up for a reply read while a delete after it is queued\n\nA reply from an earlier batch, read after deleteGraph() was queued but\nbefore it was synced, still looked its ids up. Jedis may already have\nflushed the delete, and a recreate after it, to the server, so the lookup\ncould find the new graph and silently return its names. While a segment's\ndelete is pending, its replies are now decoded with a copy of the cache. A\ndiscarded or aborted transaction re-opens the segments of its deletes,\nwhich never ran.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: freeze a queued reply's cache when its delete is queued\n\nThe copy of the cache that replies queued before a delete decode with was\ntaken when the delete ran, or at the first read while it was pending. By\nthen the shared cache may already hold a recreated graph's names: Jedis\ncan flush the delete and a recreate long before the replies are read, and\nany other context of the graph can look the new graph up meanwhile. The\ncopy is now taken when the delete is queued, the last moment it surely\nhasn't run. When the replies are read, a delete that failed, or a\ntransaction that was discarded or aborted, re-opens its segment; if\nreading them fails part way, so a delete may have run, the live cache is\ncleared.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: treat EXECABORT as a transaction that didn't run\n\nEXEC answered with an error (EXECABORT, after a command failed to queue)\nmeans the server ran none of the commands, so the transaction's deletes\nre-open their segments and the cache is kept; only a connection failure\nleaves it unknown whether they ran. Also document that a reply queued\nbetween two deletes still to run can't be decoded if it names any schema\nentry, and make the shared-cache test wait for the delete to have run, not\njust to have arrived.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: date the cache copy from when deleteGraph() is queued\n\nThe deleteGraph() Javadocs of GraphPipelineImpl and GraphTransactionImpl\nsaid the replies queued before the delete are decoded with the cache as\nit was when the batch was sent, or when exec() was called. The copy is\ntaken when the delete is queued (QueuedReplies.deleteQueued), as the\nGraphPipeline and GraphTransaction class docs say.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: cover every path the pipeline and transaction changes take\n\nJaCoCo showed 30 changed lines no test ran. New tests cover:\n- the public pipeline and transaction constructors that take a Graph;\n- all eight query overloads and both profile overloads of a pipeline and\n  of a transaction, before and after a deleteGraph();\n- sync() and syncAndReturnAll() failing part way, with and without a\n  delete queued;\n- a failed delete followed by one that ran, and two failed deletes;\n- a transaction that has ended no longer keeping lookups off its\n  context's connection, and a context made without a graph;\n- NoLookups, which only ever reports that a lookup is needed, and\n  FrozenGraphCache, which never changes.\n\nEach test fails when the line it covers is broken.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* refactor: link a segment forward only when its delete didn't run\n\nA segment kept a link to the next one from the moment its delete was\nqueued, and the delete's builder cut it once the delete ran. Only a\nsegment whose delete didn't run ever follows the link, so the queued\ndelete now remembers the next segment, and the segment points to it only\nthen. That drops the cast in deleteQueued(), its builder parameter, and\ntwo null checks that could never fail.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: clear the cache when EXEC or DISCARD fails after a refused MULTI\n\nCopilot found this in its review of b724af5. If the server refuses MULTI,\nas an ACL that denies it does, every command after it runs as it is\nsent, the delete included. EXEC then fails with \"EXEC without MULTI\" and\nDISCARD with \"DISCARD without MULTI\", and both were taken to mean that\nnothing ran, so the cache kept the deleted graph's names.\n\nOnly EXECABORT, or a DISCARD that succeeds, shows that nothing ran. Any\nother failure now counts as unknown: the live cache is cleared in case a\ndelete ran. The test server can now refuse MULTI.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: clear the cache when sending a deleteGraph() fails\n\nCodeRabbit found this in its review of 217c716. Jedis writes a command\nto the connection before it records its response. A failed send of\nGRAPH.DELETE made deleteGraph() throw before the delete was recorded, so\nneither sync() nor exec() could clear the cache, although the delete may\nhave reached the server and run. The pipeline and the transaction now\nclear the live cache when the send fails, as master did by clearing it\nwhenever a delete was queued.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: freeze a segment before its deleteGraph() is sent\n\nCodeRabbit and Copilot found this in their reviews of ad75124. A delete\nwhose send failed cleared the live cache but left its segment unfrozen.\nA pipeline reply that an earlier sync() had read but not built then\ndecoded with the live cache. If the delete had run and the graph was\nrecreated, the reply looked its ids up in the new graph.\n\nQueuedReplies now sends the delete itself: queueDelete() freezes the\nsegment first, the last moment the delete is sure not to have run, and\nthe segment stays frozen if the send fails. runDelete() does the same\nfor a delete that isn't queued, after exec(). A failed send still\nclears the live cache.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: add the two TransactionIT tests that issue #486 lists\n\ntestDiscardDeleteGraphDoesNotClearCache discards a queued deleteGraph(),\nthen reads a pipeline reply after another handle has deleted the graph,\nso the reply decodes only from names still cached. Master fails it with\n\"Invalid graph operation on empty key\": the discarded delete had cleared\nthe cache.\n\ntestResponseGetAfterContextClosedInTransaction reads a transaction's\nreply after its context is closed, while the context's connection is\nlent out again in pipeline mode, from a pool of one connection. Master\npasses it too, since exec() decodes every reply before it returns; it\nfails if exec()'s decode borrows a connection from the pool.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: add Javadoc to the helpers this PR touches that had none\n\nCodeRabbit's Docstring Coverage check counts the functions a PR touches.\nThese main-code helpers had no Javadoc. The touched functions still\nwithout one are test methods, which this repo names rather than\ndocuments.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: cover a pipeline decoding inside syncAndReturnAll on its context's connection\n\nCopilot asked whether a context can misjudge whether its connection has\nreplies outstanding. Each part of that check was mutated in turn. Every\nmutation that would treat a busy connection as idle fails an existing\ntest. Only the pipeline's !draining went uncaught. Without it, decoding\ninside syncAndReturnAll() borrows a second connection for a lookup,\nbecause Jedis still holds the later commands' responses. The new test\nfails without it.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: a delete the server rejects after the transaction ended leaves the cache alone\n\nCopilot asked whether a rejected deleteGraph() after exec() or\ndiscard() clears the shared schema cache. It doesn't. Outside MULTI,\nJedis 8.0.2's Transaction runs the command at once and keeps an error\nreply in the response instead of throwing it. So neither the delete's\nbuilder nor runDelete's catch runs. No test pinned that. The new one\nfails if such a delete throws its error at once. runDelete's Javadoc\nnow says so.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: say to sync or close a pipeline before dropping it\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: report a lookup a context without a pool can't run, instead of sending it\n\nA context built with the public constructor has no graph to borrow a\nconnection from. After close(), or while a pipeline or transaction on it\nawaits replies, a reply that needed a schema lookup still sent it on the\ncontext's connection: one already back in its pool, or one whose next\nreply belongs to a queued command. It now fails with a GraphException\ninstead; a reply that needs no lookup still decodes.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: decode a recreated node after a transaction deletes the graph\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T11:42:59+02:00",
          "tree_id": "7128aba80ecc222c4613ac1aa4cb36c3609b1b57",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/a5a14483d3d4ef2a10171c3b1b51d3efd30e1095"
        },
        "date": 1793699142561,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3723.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6914.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9732.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12084.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11526,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11374,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11393,
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
          "id": "142a846e6ea7a169e4de9cbbfe369d909bf4c038",
          "message": "fix!: follow ASK redirects, and move single-primary reads off a dead primary (#501)\n\n* fix!: follow ASK redirects, and move single-primary reads off a dead primary\n\nIn a Redis Cluster an ASK means the graph's slot is being migrated and the\ngraph has already moved, for this command only. routed() treated it like\nMOVED: it relearned the slot table, which still names the old owner until\nthe migration ends, and went back there, so every call during a migration\nfailed after three attempts. Now an ASK sends the next attempt to the node\nit names, after ASKING, without relearning. A context on that node sends\nASKING before every command, so the lookups that decode the reply are\naccepted too, and an ASK that names no host goes to the host that answered.\nEach attempt now borrows its connection through a Route that routed() hands\nit.\n\nA query that ran but whose reply hit a redirect while decoding (a schema\nlookup on a node that just lost the slot) was retried, applying a write\ntwice. It is now reported as a ReplyDecodeException, which routed() does not\nretry; a read-only query still is.\n\nudfList() and configGet() went to one primary chosen round-robin and failed\nwhenever it was down. They now try the next primary, relearning the topology\nonce, and give up if the relearning fails too.\n\nBREAKING CHANGE: a query or profile whose reply can't be decoded because a\nschema lookup was redirected now fails with a GraphException saying the\ncommand ran, instead of being run again.\n\nCloses #492\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: keep cluster reads and ASK retries going in more failure cases\n\n- onAnyPrimary still tries the other known primaries when relearning the\n  layout fails, since one may answer on a pooled connection.\n- The round-robin index is computed as a long, so a wrapped counter can't\n  skip a primary.\n- Borrowing from an ASK target retries on a fresh pool if a topology\n  refresh closed the old one in between.\n- A MOVED met while decoding still makes the driver relearn the slot table,\n  though the command isn't repeated.\n- An ASK attempt sends ASKING once per command: the route's ASKING covers\n  the context's first command.\n- The README says only a redirect on the command itself means it didn't\n  run, and the migration IT cleans up however far the migration got.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: close a fake server's late-accepted connections, and warm node 1 first\n\nFakeRedisServer.close() closed its listening socket, then the clients it\nknew. The socket can finish closing only when the acceptor wakes, and a\nconnection accepted in that window was never closed, so a \"closed\" node\nkept answering: readsMoveOnToAnotherPrimaryWhenOneIsDown failed that way\non CI. The acceptor now closes anything it accepts after close(), and\nclose() waits for it before closing the clients. The test also has node 1\nanswer once before it is closed.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: start the cluster tests only when every node sees the cluster as up\n\nThe IT cluster waited for node 7001 to report cluster_state:ok, but the\nother two nodes can still be converging then and answer CLUSTERDOWN. That\nfailed ClusterIT in the smoke-jdk job of #497 and #501. The start script\nnow waits for each node.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: cover an ASK target that answers MOVED\n\nThe importing node stopped importing between the ASK and the attempt\nsent to it, and a third node owns the slot now. The driver relearns the\nslot table and the next attempt reaches the new owner without ASKING.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: let a read on any primary borrow again when a refresh closed its pool\n\nCopilot found this in its reviews of 92223ab and 96635b6. onAnyPrimary\nborrowed with poolFor(node).getResource(). A refresh that closed the\nnode's pool in between made the borrow fail with a plain JedisException,\nwhich the read doesn't take for an unreachable node, so udfList() and\nconfigGet() failed instead of moving on. It now borrows through borrow(),\nas connectionTo() does.\n\nClusterConnections takes the way it makes a node's pool in a new\npackage-private constructor, so a test can run a refresh between the\nlookup and the borrow. New tests cover the read, an ASK target, the\nthree-attempt limit, a source closed during the borrow, an ASKING that\nfails, and a single source asked for another node. A read gives up only\nafter trying every primary, and a topology always has one, so the\n\"no primary is known\" branch could never run; it is an assertion now.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: cover decode-time redirects, udfList filters and redirect targets\n\n- A MOVED while decoding keeps a failed relearn attached, an ASK while\n  decoding is reported without relearning, and any other lookup error is\n  reported as it is.\n- udfList sends the filters it is given, and a reply that isn't a list\n  reads as no libraries.\n- A context made for an ASK attempt asks before every command after its\n  first, copies and deletes included.\n- redirectTarget follows a named host, sends a missing or unknown one to\n  the node that answered, and keeps it when that node is unknown.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: keep a slotless node's pool while its connections are out\n\nCopilot found this in its review of 551a067. A refresh closed the pool of\nevery node that owns no slot, and an empty node importing its first slot,\nthe target of an ASK, is one of them. Closing it while an ASK attempt\nstill held a connection made the next attempt open a new pool, so the\nnode's connections could exceed the per-node pool limit.\n\nA refresh now closes such a pool only when nothing is borrowed from it;\na busy one stays, and a later refresh closes it once it is idle. Closing\nis harmless when another thread already removed the pool, so the loop no\nlonger needs a branch for that.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: cover the failover that issue #492 describes\n\nudfListAndConfigGetRefreshTopologyWhenAPrimaryGoesDown closes one of two\nprimaries and gives its slots to the other. The first read that tries\nthe closed primary relearns the slot table, and no later udfList() or\nconfigGet() tries it again. Master fails it with a\nJedisConnectionException. It also fails if a relearned table serves only\nthe read that relearned it, which readsMoveOnToAnotherPrimaryWhenOneIsDown\nmisses.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: add Javadoc to the helpers this PR touches that had none\n\nCodeRabbit's Docstring Coverage check counts the functions a PR touches.\nThese main-code helpers had no Javadoc. The touched functions still\nwithout one are test methods, which this repo names rather than\ndocuments.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: never close a node's pool mid-borrow, and retry a restarted primary\n\n- The topology carries its generation, so a read takes both from one volatile read; a refresh in\n  between can no longer leave a stale view paired with a newer generation.\n- Each node's pool is a NodePool that counts the borrows in progress. A refresh retires and closes\n  it only while no borrow is in progress and none of its connections is out, and a borrow that\n  finds it retired moves to a new pool. Before, a borrow that completed between the idle check and\n  close() left a connection out of a closed pool, and the next borrow opened a second pool to the\n  node, past its limit.\n- onAnyPrimary drops a failing node's idle connections, and after a successful relearn gives each\n  primary one more try once all have been tried. A lone primary that restarted no longer fails\n  the read on a stale pooled socket, and a dead primary is still tried only once while another\n  can answer.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* fix: make the redirect the direct cause of ReplyDecodeException\n\nThe lookup's GraphException used to sit between them, so getCause() didn't tell a MOVED from an\nASK. It is now attached as suppressed.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: check the slot is empty before ClusterIT migrates it\n\nThe source refuses SETSLOT NODE while it holds another key in the slot, which would leave a shared\ncluster half migrated.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: pick the migration target by node id from the cluster topology\n\naGraphInTheMiddleOfAMigrationIsFollowedThroughAsk chose the target and\nsettled the slot by comparing the configured addresses with the ones\nCLUSTER SLOTS reports. With FALKORDB_CLUSTER_NODES set to one hostname\nseed, such as localhost:7002 when 7002 owns the slot, it picked the\nsource itself and failed with \"ERR I'm already the owner of hash slot\".\nIt now reads the primaries and their node ids from CLUSTER SLOTS, picks\nthe target before creating the graph, and creates the graph inside the\nblock that cleans up.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T12:56:29+02:00",
          "tree_id": "638b3be4ce812f65ed0ded25317a4d1e10938ddc",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/142a846e6ea7a169e4de9cbbfe369d909bf4c038"
        },
        "date": 1793703528961,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6729.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 11952.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 18119.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 23432.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 22391.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 21452.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 21766.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "4c84d399efe5d7eab40a49f1a6197c2ad815b0d8",
          "message": "chore(master): release 0.15.0 (#484)\n\n* chore(master): release 0.15.0\n\n* docs: point the Official Releases snippet at 0.15.0\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T13:05:25+02:00",
          "tree_id": "4e18d40322334fe07ede59d8cd07d581719bcc0d",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/4c84d399efe5d7eab40a49f1a6197c2ad815b0d8"
        },
        "date": 1793704079570,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3529.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6807.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9733,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11998,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11465.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11438.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11339.333,
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
          "id": "7c25866c1cbed139a26f534ba52eed7b4f5c85a2",
          "message": "test: read an unknown cluster host as the seed in ClusterIT (#507)\n\nThe migration test connected to each primary at the host CLUSTER SLOTS\nreports. A cluster that doesn't know a node's endpoint, such as one with\ncluster-preferred-endpoint-type unknown-endpoint, reports it as null,\nempty or ?, which the driver reads as the node that answered. The test\ntried to connect to it and failed with UnknownHostException. It now\nfalls back to the seed host the same way.\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T13:32:56+02:00",
          "tree_id": "fcd5a28b8a2945ec7af969180d9af20f79e69ec6",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/7c25866c1cbed139a26f534ba52eed7b4f5c85a2"
        },
        "date": 1793705722035,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6246.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10222.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14015.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17486,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16951.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16503,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16230.333,
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
          "id": "8414c04f4945070a46b1a3d0efe0e96710d99eb6",
          "message": "ci: retry dependency resolution and warm the Maven cache in every job (#506)\n\n* ci: retry dependency resolution and warm the Maven cache in every job\n\nThe v0.15.0 tag build failed in smoke-jdk8 with \"Could not find artifact\"\nfor jspecify, junit-jupiter, testcontainers and others, while the other\njobs of the same run resolved the same versions. Central answered 404,\nwhich the connector's retry handler (.mvn/maven.config) doesn't retry,\nand smoke-jdk8 had no warm-up step, so the build failed outright.\n\nfetch-deps now retries the whole resolution three times with a back-off,\npassing -U on a retry so Maven asks Central again instead of trusting the\n\"not found\" it just recorded. Every job that builds with Maven warms the\ncache with it first; lint, format and javadoc warm the -Pquality plugins.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* ci: pass fetch-deps arguments to Maven as given\n\n{{args}} pasted the arguments into the script as text, so one with a\nspace, such as --settings 'maven conf/settings.xml', reached Maven as\ntwo. The recipe now takes them as positional arguments and passes \"$@\".\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* ci: warm the help plugin and the smoke-test and examples plugins too\n\nfetch-deps warmed only the root build. verify-jdk and examples then\nresolved maven-help-plugin and the compiler 3.15.0 and surefire 3.5.6\nthat smoke-test/ and examples/ pin, outside any retry. The warm-up now\nresolves those as well, inside the retried attempt. It uses\ndependency:resolve-plugins for the two poms: go-offline would fetch their\ndependency on the jar this build installs, which may not be on Central,\nand dependency-plugin 3.7.0's go-offline ignores excludeGroupIds.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* ci: pass fetch-deps arguments to every Maven call of the warm-up\n\nOnly the root go-offline got them, so --settings or -Dmaven.repo.local\ndidn't reach the help plugin or the smoke-test and examples plugins.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-03T13:48:43+02:00",
          "tree_id": "92c7ae29ac2d84c2b1b4bf5302642d17f4ce0110",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/8414c04f4945070a46b1a3d0efe0e96710d99eb6"
        },
        "date": 1793706662275,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5955.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 10118,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14784.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18220.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17114.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16845.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 16690.667,
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
          "id": "a1a4a4b764df5e51c17cc414da0a76172bce2082",
          "message": "test: attribute virtual-thread pins to the code that holds the monitor (#517)\n\n* test: attribute virtual-thread pins to the code that holds the monitor\n\nThe weekly pin-check has failed since Jedis 8 brought in commons-pool2 2.13.\nIts GenericObjectPool.returnObject now holds synchronized (p) while it waits\nfor the idle deque's lock (POOL-419), so on JDK 21-23 every contended\nconnection return pins a carrier. The check blamed JFalkorDB because the\npinned stack runs through GraphContextImpl.close(), but a JFR stack doesn't\nsay which frame holds the monitor.\n\nRun the check's JVM with -Djdk.tracePinnedThreads=full, which marks the\nmonitor holders on each distinct pinned stack, and fail only when one of\nthem is in com.falkordb.impl. That replaces the create/makeObject name\nheuristic and still catches synchronized around our own close(). If pins on\nclient stacks can't be attributed (no trace, or a monitor stack with no\nmarked holder), the check fails instead of passing.\n\nDocument the contended-return pin in the README and run the workflow on PRs\nthat change the check.\n\nFixes #516\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: match each pinned JFR event to a traced stack\n\nThe JDK skips a pinned-thread print when System.out's lock is contended,\nso a traced client stack can't vouch for every client pin. Match each JFR\npin through com.falkordb.impl to a traced stack on their non-JDK frames\n(JFR also lists hidden lambda classes) and fail on any pin that matches\nnone. Document that the trace needs a plain PrintStream as System.out.\n\nAlso: flush the capture stream before restoring System.out, lock\nSYSTEM_OUT for the test, run the workflow on root pom.xml changes, and\nnarrow the README claims to the query path the check exercises and to the\npinning JEP 491 removes.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: document the pin-check helpers\n\nAdd Javadoc to the touched pin-check methods that branch or loop, and\nrewrap a class-Javadoc line left too long.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-09T08:29:35+02:00",
          "tree_id": "0f60c9358ce1f7913d72885d113446a27ac12310",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/a1a4a4b764df5e51c17cc414da0a76172bce2082"
        },
        "date": 1794205907747,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 5131.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 8351.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 13168,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 17470.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 16568,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 16160.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 15848.667,
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
          "id": "ea491207dfb761798f2a7286b0c877038c356eae",
          "message": "build(deps): combine open Dependabot updates (#518)\n\n* fix(deps): bump com.google.errorprone:error_prone_core\n\nBumps [com.google.errorprone:error_prone_core](https://github.com/google/error-prone) from 2.51.0 to 2.52.0.\n- [Release notes](https://github.com/google/error-prone/releases)\n- [Commits](https://github.com/google/error-prone/compare/v2.51.0...v2.52.0)\n\n---\nupdated-dependencies:\n- dependency-name: com.google.errorprone:error_prone_core\n  dependency-version: 2.52.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit fbdb9c0827d9cd162d717e04aaea4ad26d473d62)\n\n* build(deps-dev): bump com.diffplug.spotless:spotless-maven-plugin\n\nBumps [com.diffplug.spotless:spotless-maven-plugin](https://github.com/diffplug/spotless) from 3.10.4 to 3.11.0.\n- [Release notes](https://github.com/diffplug/spotless/releases)\n- [Changelog](https://github.com/diffplug/spotless/blob/main/CHANGES.md)\n- [Commits](https://github.com/diffplug/spotless/compare/maven/3.10.4...maven/3.11.0)\n\n---\nupdated-dependencies:\n- dependency-name: com.diffplug.spotless:spotless-maven-plugin\n  dependency-version: 3.11.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit a25d97dd27141a8cdf7c3ebcafde39bb1218bc05)\n\n* build(deps): bump amannn/action-semantic-pull-request\n\nBumps [amannn/action-semantic-pull-request](https://github.com/amannn/action-semantic-pull-request) from 6.1.1 to 6.1.2.\n- [Release notes](https://github.com/amannn/action-semantic-pull-request/releases)\n- [Changelog](https://github.com/amannn/action-semantic-pull-request/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/amannn/action-semantic-pull-request/compare/48f256284bd46cdaab1048c3721360e808335d50...2252a14e810aa9b85c2d560bd7070c802206ecf6)\n\n---\nupdated-dependencies:\n- dependency-name: amannn/action-semantic-pull-request\n  dependency-version: 6.1.2\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit 4ecf8c91e2054ca6dda82a50ccb59e43d4129d2b)\n\n* build(deps-dev): bump com.github.siom79.japicmp:japicmp-maven-plugin\n\nBumps [com.github.siom79.japicmp:japicmp-maven-plugin](https://github.com/siom79/japicmp) from 0.26.5 to 0.27.0.\n- [Release notes](https://github.com/siom79/japicmp/releases)\n- [Changelog](https://github.com/siom79/japicmp/blob/master/release.py)\n- [Commits](https://github.com/siom79/japicmp/compare/japicmp-base-0.26.5...japicmp-base-0.27.0)\n\n---\nupdated-dependencies:\n- dependency-name: com.github.siom79.japicmp:japicmp-maven-plugin\n  dependency-version: 0.27.0\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit 3e15c639993f849a0449c380dcd49beb65b5e56b)\n\n* build(deps): bump github/codeql-action/analyze from 4.39.1 to 4.39.3\n\nBumps [github/codeql-action/analyze](https://github.com/github/codeql-action) from 4.39.1 to 4.39.3.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/9b3254085494b55a0f7c4c2b3d2d52cc712ce370...33d7b85da1a04171e211befe2c739303b9a4c066)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/analyze\n  dependency-version: 4.39.3\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit c2f582a6e3824ae27f4a518c58c32f7fa2a9f4c3)\n\n* build(deps): bump github/codeql-action/init from 4.39.1 to 4.39.3\n\nBumps [github/codeql-action/init](https://github.com/github/codeql-action) from 4.39.1 to 4.39.3.\n- [Release notes](https://github.com/github/codeql-action/releases)\n- [Changelog](https://github.com/github/codeql-action/blob/main/CHANGELOG.md)\n- [Commits](https://github.com/github/codeql-action/compare/9b3254085494b55a0f7c4c2b3d2d52cc712ce370...33d7b85da1a04171e211befe2c739303b9a4c066)\n\n---\nupdated-dependencies:\n- dependency-name: github/codeql-action/init\n  dependency-version: 4.39.3\n  dependency-type: direct:production\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit 98f7931d5b7a270d32a5878825540e6888e0cd7b)\n\n* build(deps): bump actions/setup-java from 6.1.0 to 6.2.0\n\nBumps [actions/setup-java](https://github.com/actions/setup-java) from 6.1.0 to 6.2.0.\n- [Release notes](https://github.com/actions/setup-java/releases)\n- [Commits](https://github.com/actions/setup-java/compare/b843ebf9b913146b38da2eaa7aeddf76074ae1ad...77dd5b2ce225ea11469928ac317850245a74ae78)\n\n---\nupdated-dependencies:\n- dependency-name: actions/setup-java\n  dependency-version: 6.2.0\n  dependency-type: direct:production\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit ec85e3aeebabb91cf29a84ba975310013eb06f8a)\n\n* build(deps-dev): bump org.pitest:pitest-maven from 1.30.1 to 1.30.2\n\nBumps [org.pitest:pitest-maven](https://github.com/hcoles/pitest) from 1.30.1 to 1.30.2.\n- [Release notes](https://github.com/hcoles/pitest/releases)\n- [Commits](https://github.com/hcoles/pitest/compare/1.30.1...1.30.2)\n\n---\nupdated-dependencies:\n- dependency-name: org.pitest:pitest-maven\n  dependency-version: 1.30.2\n  dependency-type: direct:development\n  update-type: version-update:semver-patch\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\n(cherry picked from commit ca8e0bc3a749ce7e4d3b779076041fecfd3d3408)\n\n---------\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-11-09T08:38:33+02:00",
          "tree_id": "bd395ae8f6b343babe20a02de8c15f9477ea8a90",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/ea491207dfb761798f2a7286b0c877038c356eae"
        },
        "date": 1794206459141,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3792,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6504,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9762.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 11957,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11503.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11507.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11474.667,
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
          "id": "1ee2fd126dcb58e84770db1cb916edae0b0615a5",
          "message": "fix!: accept query parameter names that aren't Cypher identifiers (#519)\n\n* fix!: accept query parameter names that aren't Cypher identifiers\n\nThe client backtick-quotes every parameter name in the CYPHER header, so\nrequiring [A-Za-z_][A-Za-z0-9_]* was stricter than needed: a name such as\n1 couldn't be sent. Accept any name the quotes can hold, as the Python\nclient does, and use its messages: reject an empty name and a backtick,\nwhich FalkorDB can't escape inside a quoted name. Also reject what the\nserver can't use: a null key, a NUL character and an unpaired surrogate.\n\nOn FalkorDB 4.x the query must refer to such a name quoted, as $`1`.\n\nBREAKING CHANGE: names that used to throw IllegalArgumentException are now\nsent, and the messages for rejected names changed.\n\nFixes #502\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: keep the long parameter name within FalkorDB 6's 512-character limit\n\nfalkordb/falkordb:latest is now FalkorDB 6.0.4, which rejects identifiers\nlonger than 512 characters, so the 10,000-character name failed\nverify-latest. Use 512 characters, the longest name both 4.x and 6.x\naccept. Also say in the README and Javadoc that only 4.x needs $`1` (6.x\nparses an unquoted $1) and that 6.x limits a name to 512 characters, and\ndrop \"parameter names\" from the IDENTIFIER comment.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: mention FalkorDB 6's 512-character parameter name limit in the Javadoc\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: add Javadoc to appendParamName and the parameter-name test helpers\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: FalkorDB 6 limits a parameter name to 512 bytes of UTF-8, not characters\n\nProbed on falkordb/falkordb:latest (6.0.4): 512 ASCII characters, 256 é\nand 128 emoji (each 512 bytes) are accepted, and one more character of\neach is rejected. Say \"512 bytes of UTF-8\" in the Javadoc and README, and\nround-trip a 256-é name too.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: say the client sends a parameter name over 512 bytes and the server rejects it\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-09T10:14:35+02:00",
          "tree_id": "ca4555a5ba17fc2887c04b240147d4e49472515b",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/1ee2fd126dcb58e84770db1cb916edae0b0615a5"
        },
        "date": 1794212235583,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4002.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 7038.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9663.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12476,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11935.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11836.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11813,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "49699333+dependabot[bot]@users.noreply.github.com",
            "name": "dependabot[bot]",
            "username": "dependabot[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "fb0d39dc3302a99a68bd9ab7530f69028ea8123c",
          "message": "build(deps-dev): bump org.codehaus.mojo:animal-sniffer-maven-plugin (#521)\n\nBumps [org.codehaus.mojo:animal-sniffer-maven-plugin](https://github.com/mojohaus/animal-sniffer) from 1.28 to 1.29.\n- [Release notes](https://github.com/mojohaus/animal-sniffer/releases)\n- [Commits](https://github.com/mojohaus/animal-sniffer/compare/1.28...1.29)\n\n---\nupdated-dependencies:\n- dependency-name: org.codehaus.mojo:animal-sniffer-maven-plugin\n  dependency-version: '1.29'\n  dependency-type: direct:development\n  update-type: version-update:semver-minor\n...\n\nSigned-off-by: dependabot[bot] <support@github.com>\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>",
          "timestamp": "2026-11-10T08:59:34+02:00",
          "tree_id": "5e9214f4816e51de805fc2f2ef3aa2499f069169",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/fb0d39dc3302a99a68bd9ab7530f69028ea8123c"
        },
        "date": 1794294110526,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3729,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6712,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9499.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12560.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11852.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11839,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11800.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "neyazmaliyimm@gmail.com",
            "name": "qownz",
            "username": "qownz"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "f145e1f1d9da9dd42210df3628dd7ae89c80a7ab",
          "message": "perf: copy literal runs when encoding string query parameters (#520)\n\n* perf: copy literal runs when encoding string query parameters\n\nUtils.prepareQuery builds the \"CYPHER name=value ...\" header for every\nparameterized query. appendEscaped appended string values one character\nat a time. It now appends each run of characters that need no escaping\nwith a single StringBuilder.append(str, start, end). The escape list,\nNUL rejection and unpaired-surrogate rejection are unchanged, and the\nemitted Cypher is byte-identical.\n\nThread CPU time per call, median of three runs, old and new run\nalternately (JDK 21):\n\n  500-char string               3.58 us ->   1.40 us   (2.6x)\n  4 KB string                  30.38 us ->  10.05 us   (3.0x)\n  43 KB string                321.75 us -> 103.11 us   (3.1x)\n  typical two-parameter query   0.23 us ->   0.23 us   (unchanged)\n  1536-dimension float[]       428.5 us -> 431.8 us    (control)\n\nWall-clock JMH runs agreed in direction but ranged from 2.2x to 4.6x on\na loaded machine, so the CPU-time medians are the stable numbers.\nAllocation per call is unchanged, so the win is CPU only.\n\nA new property test compares the encoder with a character-by-character\nreference on generated strings with escapes, NULs and surrogates; unit\ntests pin the run boundaries around escapes.\n\n* test: cover surrogate pairs, rejections and long runs before an escape\n\nThe property test reused riskyStrings(), which never generates a NUL or a surrogate, so it never\nreached the encoder's surrogate-pair and rejection paths. It now draws from encoderStrings(), which\nmixes in well-formed pairs and, now and then, a NUL or a lone surrogate; its oracle keeps a pair\ntogether and expects the encoder to reject those values. About 40% of the generated values are\nrejected.\n\nA new unit test puts an escape between two 55,000-character runs. No test had a run longer than 48\ncharacters followed by an escape, so a bug that cut such a run short passed every test.\n\nappendEscaped gets a Javadoc comment.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Barak Bar Orion <barak.bar@gmail.com>\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-10T09:36:23+02:00",
          "tree_id": "4d4aef2d3e64b11206b9b534eaafe7922e7346c9",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f145e1f1d9da9dd42210df3628dd7ae89c80a7ab"
        },
        "date": 1794296301771,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3876.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6584.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9092.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12067,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11485,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11343,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11438.667,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "bfd68be460d094a1e4e3708f3d2412853420ca21",
          "message": "chore(master): release 0.15.1-SNAPSHOT (#505)\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>",
          "timestamp": "2026-11-10T09:40:12+02:00",
          "tree_id": "771d2685a4276acc45404b82a371d1ee1fbff9c8",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/bfd68be460d094a1e4e3708f3d2412853420ca21"
        },
        "date": 1794296527554,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3888.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6049,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9771.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12095.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11585.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11466,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11495,
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
          "id": "4f5b973b264b38b38460d7dee7e9f22d590a8a02",
          "message": "fix: correct the public API's nullability where null actually occurs (#524)\n\n* fix: correct the public API's nullability where null actually occurs\n\nThe public packages are @NullMarked, so an unannotated type already means non-null. This fixes the\nplaces where that default was wrong:\n\n- equals(Object) on GraphEntity, Node, Edge, Path, Point and Property takes @Nullable Object, as\n  Object.equals does.\n- Every query params map (Graph, AsyncGraph, GraphPipeline, GraphTransaction) is\n  Map<String, @Nullable Object>: a null value is documented and sent as Cypher null.\n- GraphPipeline.syncAndReturnAll() and GraphTransaction.exec() hold null for a nil reply.\n- FalkorDB.driver(host, port, user, password) takes null for either credential.\n- Javadoc: nulls inside map/list values (Record), null rejection (AsyncFalkorDB.wrap, Point).\n\nTests pin each annotation by reflection together with the runtime null it describes. Error Prone's\nEqualsMissingNullable now fails `just lint` for an unannotated equals in @NullMarked code.\nNo runtime behaviour changes.\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: WATCH the key in the aborted-transaction test\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-11T06:54:06+02:00",
          "tree_id": "65c22a63f99a97d03b3db3a0cf9c55bacdb198a0",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/4f5b973b264b38b38460d7dee7e9f22d590a8a02"
        },
        "date": 1794372967237,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 4676.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 8087.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 11047,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 14123.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 13192,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 13093,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 13019.333,
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
          "id": "557e416776e9710c7ac633f0d5aab881b061f90a",
          "message": "feat: add type-checked Record.getValue overloads, deprecate the unchecked ones (#523)\n\n* feat: add type-checked Record.getValue overloads, deprecate the unchecked ones\n\nRecord.getValue(int) and getValue(String) declare a type parameter that\nappears only in the return type, so the cast is erased and the compiler\nputs the checkcast in the caller's frame. A wrong type throws\nClassCastException in user code, or never, while the value stays untyped.\n\nAdd getValue(int, Class<T>) and getValue(String, Class<T>) as default\nmethods on Record, so existing implementers keep compiling. They look the\nvalue up through the existing methods, so an unknown index or key fails\nexactly as before. A null value is returned as null. A non-null value of\nanother type throws ClassCastException from the client, naming the\ncolumn's index and name, the value's class and the requested class, with\na hint that FalkorDB integers decode as Long and floats as Double when a\nnumeric type was asked for. A primitive class stands for its wrapper\n(long.class reads a Long); nothing is converted. A null type throws\nNullPointerException before the lookup.\n\nDeprecate the unchecked forms (removal planned for 1.0.0) and move the\nexamples and README snippet to the checked overloads. The tests keep\nusing the deprecated forms, including the #390 pin test, since they stay\nsupported until 1.0.0.\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: cover a negative index an odd Record accepts\n\nCodecov flagged the index >= 0 guard in the type-mismatch message as a\npartial branch: RecordImpl rejects a negative index before the check, but\nan implementer whose getValue(int) counts from the end does not.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: document the methods this change touches\n\nCodeRabbit's docstring-coverage pre-merge check reported 37.5% against\nits 80% threshold. Turn the tests' leading comments into Javadoc and\ndocument the remaining touched methods.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: document the two example main methods this change touches\n\nCodeRabbit's docstring-coverage check counted QuickStart.main and\nEnvironmentConfiguredDriver.main, whose result-reading lines now use the\nchecked getValue, and reported 75% against its 80% threshold.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-11T06:56:54+02:00",
          "tree_id": "8e1be424995a8ded05e9aa51fcda74b13ce5d101",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/557e416776e9710c7ac633f0d5aab881b061f90a"
        },
        "date": 1794373161489,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3908.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6735,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9577,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12524.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11808.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11728.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11797,
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
          "id": "9fdd19f12735fd2314809740a02eb05ea87dd86d",
          "message": "fix!: let Driver, GraphPipeline and GraphTransaction close without a checked exception (#522)\n\n* fix!: let Driver, GraphPipeline and GraphTransaction close without a checked exception\n\nDriver, GraphPipeline and GraphTransaction extended Closeable without\noverriding close(), so they inherited `close() throws IOException`, and\n`try (Driver d = FalkorDB.driver()) {}` didn't compile without a catch\n(IOException). Graph already overrode it. Each now declares `void close()`\nwith no throws clause; their implementations never threw one.\n\nThe examples no longer declare `throws Exception` / `throws IOException`\njust to close a driver. README and llms.txt close the driver with\ntry-with-resources, and AsyncFalkorDB documents that an AsyncGraph owns\nnothing: the caller closes the executor, then the driver.\n\nRefs #400\n\nBREAKING CHANGE: Driver.close(), GraphPipeline.close() and GraphTransaction.close() no longer declare IOException. Code that catches IOException around them, with nothing else in the try throwing it, no longer compiles (\"exception IOException is never thrown in body of corresponding try statement\"). That includes try (Driver d = ...) { ... } catch (IOException e), which was the only way to use try-with-resources with these types until now. Remove the catch, or catch a broader exception. A class implementing one of these interfaces whose close() declares throws IOException must drop the throws clause. A Mockito stub such as doThrow(new IOException()).when(driver).close() now fails with \"Checked exception is invalid for this method\": throw an unchecked exception instead. Compiled code keeps working: the change is binary compatible.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: qualify the executor-close and Driver.close() notes\n\nExecutorService.close() drops the queued tasks when the closing thread is\ninterrupted, and llms.txt pins a release whose Driver.close() still\ndeclares IOException.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: say that an interrupted executor close still waits for running queries\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-11T07:12:07+02:00",
          "tree_id": "14832e50fdd52bd9240516fc7a8762795f05ef4c",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/9fdd19f12735fd2314809740a02eb05ea87dd86d"
        },
        "date": 1794374076495,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 6126.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 9828.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 14218.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 18189,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 17468,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 17117,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 17273.333,
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
          "id": "d17412178448c7f02413b9d55d74e463bd8ac179",
          "message": "build: snapshot the full public API surface, members inherited from Jedis included (#526)\n\n* build: snapshot the full public API surface, members inherited from Jedis included\n\njapicmp (`just api-diff`) only compares the classes inside our jar, and our\nconfiguration resolves the baseline against the build's own Jedis, so the ~960\nmethods GraphPipeline and GraphTransaction inherit from Jedis, and the Jedis\nthat Driver.getConnection() returns, are invisible to it. Even with separate\nold/new classpaths it reports nothing for a removed or re-typed inherited\nmethod. That is how the Jedis 8 bump re-typed jsonNumIncrBy and removed\nJedis.aclLogBinary() unnoticed.\n\nAdd a committed snapshot of the whole surface, api/jfalkordb.api, generated by\nreflection (ApiSurface) and checked by ApiSurfaceTest: every public/protected\nmember of every exported type, inherited ones included, plus the public\ninstance members of the types our own signatures name (Jedis, Response). It\nfails with a per-type diff and the command that regenerates it\n(`just api-surface-update`). It runs in `just test` / `just verify`, and as a\nstep of the api-diff workflow via `just api-surface`.\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* build: catch bound and type-argument changes, and require approval for snapshot breaks\n\nReview fixes for the API surface gate:\n\n- Print the members of a raw generic supertype erased, so a change of a\n  type variable's bound (which changes the JVM signature) shows.\n- List transitive supertypes with the type arguments the type supplies, so\n  a change in them (an assignability break) shows.\n- Add `just api-surface-breaks <base_ref> [allow]`, run by the api-diff\n  workflow: a removed or changed snapshot line now needs the\n  `breaking-change` label (and so a `!` title), like a japicmp finding.\n- `just api-surface` cleans first, and neither recipe tolerates a missing\n  test any more.\n- Stop quoting an exact inherited-method count that goes stale.\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* build: fix field hiding, flag ambiguous fields and new abstract methods as breaks\n\nSecond round of review fixes for the API surface gate:\n\n- Let fields of every access take part in hiding, so a private field a\n  dependency adds to a superclass removes the inherited one from the\n  snapshot.\n- Apply \"class wins\" to methods only: a field inherited from both a\n  superclass and an interface is ambiguous, and is now marked so.\n- `just api-surface-breaks` also flags an abstract method added to an\n  existing exported interface or abstract class, which breaks\n  implementations (japicmp fails on that for our own classes too).\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: keep the ambiguous-field fixture off the constant-interface pattern\n\nGive the fixture interface a method (and make the fixture class abstract)\nso it isn't a constants-only interface, and mention abstract classes in\nCONTRIBUTING's note on new abstract methods.\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: state the API snapshot's deprecation and one-level coverage rules\n\nA change that only adds or removes @Deprecated is not a break, and\ndependency types are covered only one level deep (those our own\ndeclarations name), not the types their members return in turn.\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* docs: limit the abstract-method break rule to existing types in the docs\n\nSay that a new abstract method only counts as a break on an exported\ninterface or abstract class that already existed, and fix a garbled\nsentence in the semver policy.\n\nRefs #400\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* build: regenerate the API snapshot for #522, #523 and #524\n\nclose() is now redeclared on Driver, GraphPipeline and GraphTransaction\n(#522), and Record gains the typed getValue overloads while the\nunchecked ones are deprecated (#523).\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* build: carry #522's approved break in api-diff until it's released\n\nThe breaking-change label approves a break only on its own PR, and the\njapicmp baseline stays at the last release, so once #522 merged every\nlater PR's api-diff flagged its close() redeclarations again. List them\nas japicmp excludes (only those three methods: a new Driver method still\nfails), and add removing such entries to the post-release baseline bump.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: catch raw, outer-argument, inner-constructor and diamond-field changes in the API snapshot\n\nFour dependency changes that break callers left the snapshot unchanged:\n\n- A supertype going from raw Marker to Marker<Object>: both printed as\n  Marker<java.lang.Object>. Raw supertypes now print raw.\n- Outer<String>.Inner becoming Outer<Integer>.Inner: the outer class's\n  arguments were never bound, so Inner's T-returning methods printed T.\n  They're bound now, and the supertype line shows them.\n- An inner class constructor's generic parameters were all erased when\n  its generic signature left out the outer instance. Only that missing\n  parameter is filled in from the erased list now.\n- A field hidden along one path of a diamond but still inherited along\n  the other: hiding was resolved across the whole hierarchy at once, so\n  the ambiguity didn't show. It's resolved per path now.\n\nThe real snapshot is unchanged.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* build: fail api-surface-breaks on a base ref that doesn't resolve\n\nA typo or a shallow checkout without the base made it report \"nothing to\ncompare against\" and pass.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: keep the new API snapshot fixtures off the code-quality patterns\n\nThe diamond needs interfaces and the outer-argument and constructor\nfixtures need inner classes, so give the interfaces a method each and\nmake the inner classes use their outer instance (and the constructor its\nparameter) instead of reshaping what the tests check. Each still fails\nwithout the fix it covers.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: let the inner-constructor fixture use its outer instance outside the constructor\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n* test: don't recurse forever on an inner class extending its generic outer class\n\nclass Inner extends Outer<T> supplies Outer's T in terms of that same T,\nso substituting it looped until StackOverflowError (and Pair<A, B>'s\ninner `Swapped extends Pair<B, A>` would loop through A and B). A type\nvariable in scope in the described type's declaration is now substituted\nonce, where an inherited member names it, and what the type declares\nitself (its extends line, constructors, own fields and methods) keeps its\nvariables as they are.\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-11T09:39:02+02:00",
          "tree_id": "c79297535e4a8508284dacc1f969b8b2df421b55",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/d17412178448c7f02413b9d55d74e463bd8ac179"
        },
        "date": 1794382855365,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3907.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6643,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 10051.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12468,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 12085,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 12060,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11839.333,
            "unit": "ops/s"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "email": "307409954+falkordb-release-please[bot]@users.noreply.github.com",
            "name": "falkordb-release-please[bot]",
            "username": "falkordb-release-please[bot]"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "38526ed93783c4edffcac5da1aa859367e874dc8",
          "message": "chore(master): release 0.16.0 (#525)\n\n* chore(master): release 0.16.0\n\n* docs: point the Official Releases snippet at 0.16.0\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n\n---------\n\nCo-authored-by: falkordb-release-please[bot] <307409954+falkordb-release-please[bot]@users.noreply.github.com>\nCo-authored-by: Barak Bar Orion <barak.bar@falkordb.com>\nCo-authored-by: Claude Opus 5.5 <noreply@anthropic.com>",
          "timestamp": "2026-11-11T09:54:41+02:00",
          "tree_id": "3dc7c64a1193346ce11313489bd93295f269830f",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/38526ed93783c4edffcac5da1aa859367e874dc8"
        },
        "date": 1794383797248,
        "tool": "customBiggerIsBetter",
        "benches": [
          {
            "name": "throughput @load=1",
            "value": 3801,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=2",
            "value": 6654.333,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=4",
            "value": 9625.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=8",
            "value": 12074.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=16",
            "value": 11455.667,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=32",
            "value": 11416,
            "unit": "ops/s"
          },
          {
            "name": "throughput @load=64",
            "value": 11411.333,
            "unit": "ops/s"
          }
        ]
      }
    ]
  }
}