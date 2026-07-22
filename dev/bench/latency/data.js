window.BENCHMARK_DATA = {
  "lastUpdate": 1784743667439,
  "repoUrl": "https://github.com/FalkorDB/JFalkorDB",
  "entries": {
    "Client latency": [
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
        "date": 1784433713313,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 121.56,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 148.96,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 171.364,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 154.849,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 192.256,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 230.461,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 201.689,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 305.912,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 370.188,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 334.605,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 567.008,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 694.487,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 383.467,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 3724.63,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 8180.92,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 384.108,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 10306.553,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 23276.47,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 385.179,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 24001.2,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 54337.874,
            "unit": "us"
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
        "date": 1784468707183,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 160.968,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 204.442,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 259.032,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 203.48,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 252.539,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 305.732,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 265.192,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 422.214,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 553.378,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 426.201,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 734.026,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 908.834,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 501.831,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4714.322,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10514.496,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 499.399,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13378.604,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 29452.757,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 500.81,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 31072.665,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 68581.719,
            "unit": "us"
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
        "date": 1784482539549,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 161.769,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 198.674,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 235.739,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 203.751,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 245.433,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 270.445,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 262.124,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 406.452,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 497.347,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 420.363,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 718.624,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 899.482,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 496.156,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4811.778,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10256.888,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 497.798,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13548.689,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 29672.317,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 494.353,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 31393.966,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70201.445,
            "unit": "us"
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
        "date": 1784524360658,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 121.523,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 144.358,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 157.958,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 154.097,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 187.443,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 215.857,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 200.923,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 309.744,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 380.745,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 318.791,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 541.257,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 670.983,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 383.698,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 3573.384,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 7920.351,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 393.765,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 10109.794,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 22440.675,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 387.255,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 23604.198,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 53259.373,
            "unit": "us"
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
        "date": 1784536567469,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 199.206,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 242.947,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 305.857,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 228.911,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 272.274,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 312.69,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 295.276,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 455.649,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 555.207,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 489.081,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 823.962,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1022.436,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 582.768,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5431.833,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10962.72,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 577.54,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15161.71,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30482.395,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 578.951,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34883.236,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 74315.608,
            "unit": "us"
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
        "date": 1784552291305,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 167.376,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 201.927,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 245.351,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 201.817,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 241.856,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 262.811,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 268.709,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 436.362,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 545.195,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 424.426,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 721.082,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 887.547,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 502.209,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4769.748,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10253.851,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 501.685,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13236.221,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 27668.854,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 494.819,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 30264.871,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 66098.284,
            "unit": "us"
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
        "date": 1784553066013,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 108.79,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 136.621,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 157.384,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 121.776,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 148.782,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 164.077,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 145.009,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 238.67,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 312.505,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 229.093,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 399.066,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 502.568,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 273.251,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 2447.282,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 5508.991,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 279.338,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 7070.671,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 15501.983,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 267.256,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 16657.984,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 35248.28,
            "unit": "us"
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
        "date": 1784557767094,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 208.708,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 241.853,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 260.952,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 241.561,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 301.183,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 366.735,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 308.226,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 477.423,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 586.157,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 510.134,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 860.78,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1057.839,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 593.471,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5509.079,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10847.64,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 590.976,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15399.038,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31547.933,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 600.934,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35770.388,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 71572.653,
            "unit": "us"
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
        "date": 1784614622346,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 161.038,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 204.832,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 239.734,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 203.641,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 257.515,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 313.828,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 262.638,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 401.473,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 489.333,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 434.22,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 737.891,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 921.782,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 507.479,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4741.697,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 9820.237,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 514.24,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13241.279,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 28305.56,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 520.308,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 30006.196,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 62217.21,
            "unit": "us"
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
        "date": 1784618588282,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 196.27,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 237.157,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 274.647,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 229.1,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 273.786,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 318.44,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 308.394,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 508.637,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 654.613,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 513.397,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 854.62,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1037.614,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 594.856,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5343.641,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10732.347,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 599.843,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15146.433,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30884.141,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 600,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34873.299,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 71026.791,
            "unit": "us"
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
        "date": 1784619461851,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 134.564,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 164.796,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 192.528,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 163.337,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 195.867,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 220.816,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 186.838,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 287.204,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 351.183,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 304.305,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 521.082,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 653.32,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 375.797,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 3368.15,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 6385.732,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 373.497,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 9187.53,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 18005.376,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 373.566,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 20938.843,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 43224.165,
            "unit": "us"
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
        "date": 1784620552487,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 207.147,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 248.264,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 285.061,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 238.645,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 280.984,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 313.809,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 308.876,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 481.798,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 598.736,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 489.021,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 822.386,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1025.744,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 585.953,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5395.664,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11163.571,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 583.799,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14831.746,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30533.282,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 593.828,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35016.502,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70463.245,
            "unit": "us"
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
        "date": 1784638059063,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 199.475,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 239.15,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 264.717,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 241.272,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 276.258,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 299.693,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 310.263,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 517.111,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 677.092,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 497.905,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 838.915,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1030.054,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 589.877,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5351.464,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11226.964,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 591.461,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15172.567,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31052.293,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 588.505,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34765.335,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70096.237,
            "unit": "us"
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
        "date": 1784639414538,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 159.09,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 201.665,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 255.542,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 200.282,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 242.302,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 263.727,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 265.871,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 431.742,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 552.305,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 417.892,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 703.683,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 860.259,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 509.54,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4851.659,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 9946.589,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 496.7,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 12954.16,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 27555.848,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 498.979,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 30201.04,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 64779.81,
            "unit": "us"
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
        "date": 1784641100511,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 191.799,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 228.748,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 248.745,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 227.696,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 266.828,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 296.18,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 307.945,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 506.386,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 646.813,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 479.426,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 806.488,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1004.477,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 566.579,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5249.809,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10424.654,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 571.959,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13813.259,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 29476.433,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 578.301,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 33981.44,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 69243.076,
            "unit": "us"
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
        "date": 1784641251619,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 207.459,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 246.578,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 279.511,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 253.191,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 289.339,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 316.621,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 317.652,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 516.803,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 644.912,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 503.828,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 852.191,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1058.273,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 605.238,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5361.858,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11260.353,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 595.97,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15254.022,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31689.205,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 599.818,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35557.699,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70690.014,
            "unit": "us"
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
        "date": 1784642864867,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 210.089,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 243.261,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 272.035,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 240.206,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 283.709,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 318.331,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 313.521,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 497.764,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 619.991,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 495.961,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 823.138,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1011.228,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 597.98,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5329.255,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11062.625,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 580.558,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15165.3,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31423.279,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 580.146,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34780.957,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 73064.969,
            "unit": "us"
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
        "date": 1784696434711,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 201.275,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 243.344,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 274.691,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 242.954,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 287.176,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 332.5,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 301.272,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 461.362,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 562.009,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 502.016,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 841.859,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1032.386,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 598.237,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5379.573,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10886.679,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 595.058,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15338.28,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30692.6,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 594.097,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34527.962,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70278.616,
            "unit": "us"
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
        "date": 1784700304497,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 210.145,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 258.805,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 289.424,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 245.481,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 280.777,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 305.314,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 297.559,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 453.532,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 542.541,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 490.432,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 826.953,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1024.584,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 574.379,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5276.364,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10776.738,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 580.46,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14905.976,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30281.713,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 589.959,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34418.553,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 66321.367,
            "unit": "us"
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
        "date": 1784702804120,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 205.072,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 248.623,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 283.427,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 251.066,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 288.036,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 320.045,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 314.003,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 505.098,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 627.907,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 515.458,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 861.201,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1052.918,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 609.713,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5527.686,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10936.152,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 605.084,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15623.226,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31143.187,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 598.603,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 36123.097,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 76124.841,
            "unit": "us"
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
        "date": 1784707156068,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 120.559,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 149.871,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 175.709,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 157.623,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 191.983,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 227.116,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 200.566,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 303.899,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 370.376,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 334.404,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 567.187,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 702.918,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 390.417,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 3634.17,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 7484.739,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 395.683,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 10276.685,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 21742.859,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 389.805,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 23514.913,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 50498.961,
            "unit": "us"
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
        "date": 1784712912055,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 95.119,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 109.987,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 123.89,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 119.911,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 147.156,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 162.877,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 139.658,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 224.938,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 281.024,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 204.126,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 354.85,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 453.59,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 243.891,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 2190.605,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 4663.127,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 256.414,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 6182.347,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 13868.188,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 253.932,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 13385.716,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 28375.05,
            "unit": "us"
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
        "date": 1784717651346,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 159.078,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 191.887,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 244.047,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 201.633,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 243.877,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 266.724,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 258.768,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 395.544,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 491.073,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 426.12,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 745.512,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 931.371,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 508.265,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4817.094,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10011.237,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 508.135,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13368.002,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 27755.789,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 509.479,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 30548.355,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 65487.503,
            "unit": "us"
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
        "date": 1784721045821,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 159.745,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 204.281,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 243.939,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 205.542,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 247.094,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 270.538,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 262.275,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 402.24,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 484.573,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 423.634,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 725.032,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 902.54,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 505.85,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4828.166,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 9885.66,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 524.668,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13147.233,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 27574.711,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 528.478,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 29659.035,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 61539.822,
            "unit": "us"
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
        "date": 1784725340776,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 207.929,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 237.213,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 319.328,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 244.374,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 284.35,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 304.839,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 296.934,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 452.528,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 548.06,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 486.583,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 816.375,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1014.792,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 584.967,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5364.114,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10895.338,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 589.353,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15005.955,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30772.4,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 589.725,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34642.963,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70860.708,
            "unit": "us"
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
        "date": 1784727614512,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 215.601,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 244.245,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 284.82,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 250.024,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 283.328,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 300.298,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 297.824,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 445.157,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 534.725,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 499.288,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 845.363,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1047.72,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 594.897,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5552.596,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11312.812,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 588.885,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15326.914,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31473.32,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 593.985,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35239.579,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 69681.68,
            "unit": "us"
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
        "date": 1784733215803,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 191.847,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 231.702,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 261.357,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 229.508,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 272.34,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 305.459,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 297.862,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 463.956,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 585.632,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 496.065,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 835.559,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1027.602,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 581.959,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5372.155,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11034.718,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 592.968,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14970.83,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 29411.905,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 587.074,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34332.22,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 69462.948,
            "unit": "us"
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
        "date": 1784743666391,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 204.192,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 237.875,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 268.372,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 248.645,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 282.558,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 303.537,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 316.18,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 515.49,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 652.949,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 500.174,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 826.996,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1009.958,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 591.145,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5436.673,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10738.315,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 587.538,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15130.936,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30035.763,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 591.315,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35081.965,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 68488.504,
            "unit": "us"
          }
        ]
      }
    ]
  }
}