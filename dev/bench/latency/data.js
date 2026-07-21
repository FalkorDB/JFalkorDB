window.BENCHMARK_DATA = {
  "lastUpdate": 1784639414875,
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
      }
    ]
  }
}