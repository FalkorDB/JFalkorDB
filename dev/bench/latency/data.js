window.BENCHMARK_DATA = {
  "lastUpdate": 1788269098069,
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
        "date": 1784785282635,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 156.232,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 186.506,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 205.864,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 195.43,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 233.481,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 256.219,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 256.77,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 408.415,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 505.599,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 419.651,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 725.625,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 911.609,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 508.057,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4481.685,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 9819.187,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 514.822,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 12628.26,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 26013.388,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 511.402,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 29570.052,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 61984.255,
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
          "id": "08251f69b4f9286d50808edf8173445910ca1673",
          "message": "docs: document virtual-thread concurrency and pool tuning (Wave 5) (#358)\n\n* docs: document virtual-thread concurrency, pool sizing, and warm-up (Wave 5)\n\nAdd a 'Concurrency with Virtual Threads (JDK 21+)' README section covering the\nrecommended model (virtual-thread-per-task executor + the blocking client):\n- fanning driver.graph(id) out over virtual threads;\n- the optional AsyncGraph / AsyncFalkorDB.wrap CompletableFuture facade;\n- sizing the pool for fan-out (poolMaxTotal caps real concurrency; no built-in\n  admission bound; finite poolMaxWait/socketTimeout);\n- warming the pool to avoid JDK 21-23 cold-creation carrier pinning, requiring\n  both poolMaxTotal >= N and poolMaxIdle >= N, with the JEP 491 (JDK 24) note and\n  a pointer to the scheduled pinning check.\n\nCompletes the Wave 5 (Project Loom) documentation and pool-sizing items.\n\nCloses #333\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: make the virtual-thread snippets self-contained\n\nDeclare the Driver (and note shutdown/close) in the AsyncGraph and pool-warm-up\nsnippets so each compiles when copied on its own.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: correct AsyncGraph close guidance and warm-up shutdown\n\n- The facade caller closes the underlying Driver (which releases pooled\n  connections), not the GraphContextGenerator (whose close() only clears its\n  cache); reword accordingly.\n- Await pool termination in the warm-up example so all N connections are back in\n  the pool before the workload starts.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: bound the warm-up snippet's waits and check awaitTermination\n\nGive ready.await() a timeout and act on awaitTermination's result (shutdownNow on\ntimeout) so the illustrative warm-up can't block indefinitely.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* docs: drain the virtual-thread executor before the pool closes in the fan-out example\n\nAwait executor termination after shutdown() so in-flight queries finish before the\ntry-with-resources closes the Driver/pool.\n\nAddresses Copilot review on #358.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T09:26:19+03:00",
          "tree_id": "f2a26685cf066aa2b4d7869692ca514f5ecf8b62",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/08251f69b4f9286d50808edf8173445910ca1673"
        },
        "date": 1784788098897,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 195.387,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 239.961,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 276.821,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 230.963,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 274.562,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 303.52,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 299.47,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 465.886,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 569.362,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 481.615,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 810.944,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1003.966,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 569.39,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5320.626,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10947.717,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 566.111,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14681.163,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30667.857,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 568.238,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34117.066,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 68777.541,
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
          "id": "f42a18017d5834165316cb080c55244178957218",
          "message": "ci: anchor release-please's first changelog to the v0.9.0 commit (#359)\n\nPin the package with last-release-sha = the v0.9.0 commit\n(23723b12ff353b57166aa2b0a3f01349d0eaacf9). Because the bootstrap manifest is\nseeded at 0.9.1-SNAPSHOT (there is no such tag), release-please's first run had\nno anchor and walked the entire history back to v0.1.0, listing ~24 already-shipped\nfixes/dependency bumps (2020-2024) under 0.10.0. With the anchor, the first\nchangelog spans only v0.9.0..HEAD (the real 0.10.0 changes). It is a no-op once a\nreal release tag exists. Document the anchor in the setup guide's bootstrap note.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T09:53:42+03:00",
          "tree_id": "1178f8de748e5af50d775a9b12024901b7cd9fb9",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f42a18017d5834165316cb080c55244178957218"
        },
        "date": 1784789735733,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 128.333,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 148.223,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 189.865,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 152.148,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 185.403,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 209.014,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 203.416,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 328.625,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 412.311,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 321.164,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 554.976,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 691.731,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 377.018,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 3467.559,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 7415.751,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 394.913,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 9513.173,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 20889.781,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 415.384,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 18877.617,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 40187.499,
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
          "id": "f6c11871a3414b226ad88f08bb2194d436150f16",
          "message": "ci: move release-please last-release-sha to top-level so the first changelog trims (#360)\n\nThe last-release-sha added in #359 was placed inside the package config, where\nrelease-please ignores it, so the first 0.10.0 changelog still walked all history\nback to v0.1.0 (36 entries). last-release-sha is a top-level manifest-config option;\nmoving it there anchors the first run to the v0.9.0 commit. Verified via\n`release-please --dry-run --target-branch`: 36 -> 13 entries, all post-v0.9.0\n(the ancient dependency bumps and typo'd commit messages are gone).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T10:19:45+03:00",
          "tree_id": "cc92175955b80b0ecb581c0bc4326f32a3ba35c5",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/f6c11871a3414b226ad88f08bb2194d436150f16"
        },
        "date": 1784791376398,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 156.512,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 191.895,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 210.942,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 196.082,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 243.431,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 306.164,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 256.681,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 399.303,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 488.705,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 413.794,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 704.946,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 887.818,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 490.378,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4621.508,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 9632.082,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 493.042,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13039.927,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 27709.123,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 495.065,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 29795.19,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 62774.775,
            "unit": "us"
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
        "date": 1784791728595,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 163.921,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 197.662,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 243.779,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 193.576,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 234.031,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 256.399,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 254.986,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 399.809,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 493.79,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 429.013,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 735.536,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 918.786,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 518.675,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4661.612,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10427.292,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 512.286,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 12059.816,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 25493.533,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 513.689,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 27441.978,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 58182.031,
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
          "id": "584ea7a4ce317ccfcd914c503078e8d8be767577",
          "message": "build: bump api.diff.baseline to 0.10.0 (#362)\n\nNow that 0.10.0 is released to Maven Central, compare the public API against it\n(the documented post-release step) so the api-diff gate reflects the new baseline.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-23T11:53:08+03:00",
          "tree_id": "8192927ba44254839669c20b7465ef4fd8a04010",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/584ea7a4ce317ccfcd914c503078e8d8be767577"
        },
        "date": 1784796896887,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 209.833,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 245.46,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 269.084,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 237.926,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 280.005,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 317.677,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 305.533,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 464.993,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 566.602,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 511.699,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 859.381,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1059.575,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 596.018,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5495.65,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10924.586,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 604.518,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15081.538,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 29868.154,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 599.003,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34894.654,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 71302.683,
            "unit": "us"
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
        "date": 1785150525695,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 169.249,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 200.154,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 221.144,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 206.963,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 247.483,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 269.295,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 267.442,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 420.536,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 532.189,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 431.082,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 728.427,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 901.932,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 521.283,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4929.46,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10038.041,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 541.163,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13665.684,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 27740.898,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 536.487,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 30606.494,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 64378.483,
            "unit": "us"
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
        "date": 1785150807022,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 185.538,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 224.149,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 250.569,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 220.111,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 262.15,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 306.802,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 293.529,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 468.235,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 579.655,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 485.749,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 815.836,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1003.096,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 567.429,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5195.235,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10768.63,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 573.813,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14676.261,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 29977.492,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 573.148,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34015.965,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 67121.079,
            "unit": "us"
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
        "date": 1785151299872,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 225.164,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 257.223,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 284.706,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 250.271,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 284.486,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 307.789,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 313.41,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 516.471,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 647.809,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 502.227,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 848.617,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1063.521,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 599.568,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5429.984,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10825.703,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 598.826,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15214.443,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31040.03,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 600.07,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35187.611,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 71503.441,
            "unit": "us"
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
        "date": 1785151613548,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 158.128,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 187.322,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 272.802,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 204.348,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 244.919,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 269.166,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 272.179,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 448.406,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 573.943,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 423.848,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 723.103,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 895.188,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 504.991,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4697.004,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 9624.179,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 505.463,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13288.932,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 28387.898,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 510.508,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 30753.661,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 66200.575,
            "unit": "us"
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
        "date": 1785299740437,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 192.941,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 227.235,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 253.564,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 222.386,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 263.355,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 295.615,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 295.983,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 467.854,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 582.016,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 481.38,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 820.645,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1033.052,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 565.026,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5231.055,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10781.299,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 565.497,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14909.684,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30885.664,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 579.343,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34236.069,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 69850.834,
            "unit": "us"
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
        "date": 1785415102914,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 123.434,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 151.949,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 175.515,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 132.492,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 160.032,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 178.064,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 158.418,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 245.488,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 302.586,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 262.034,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 443.481,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 560.199,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 312.862,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 2650.241,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 5833.901,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 311.234,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 6996.589,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 15178.872,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 326.827,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 14097.707,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 31797.538,
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
          "id": "144c5929b5c8ffd79b058f81b15c38ecf9052f95",
          "message": "build: combine Dependabot updates (setup-java, codeql action) (#373)\n\nCombines the Dependabot updates from #370, #371 and #372 into a\nsingle PR:\n- Bump actions/setup-java from 5.6.0 to 5.7.0 (all workflows)\n- Bump github/codeql-action init from 4.37.3 to 4.37.4\n- Bump github/codeql-action analyze from 4.37.3 to 4.37.4\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-08-03T10:57:53+03:00",
          "tree_id": "ef99f352686701b3f647591178aef21e3e718121",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/144c5929b5c8ffd79b058f81b15c38ecf9052f95"
        },
        "date": 1785744012648,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 203.089,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 245.888,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 282.775,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 229.858,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 272.953,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 314.357,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 301.28,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 484.542,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 605.307,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 493.76,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 831.509,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1021.381,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 575.812,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5222.492,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10741.971,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 590.188,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14890.045,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30654.155,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 591.622,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 34963.961,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70440.476,
            "unit": "us"
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
        "date": 1786002984361,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 156.777,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 188.273,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 209.145,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 193.04,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 235.029,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 255.87,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 267.573,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 452.171,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 580.734,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 429.716,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 736.458,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 900.644,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 514.554,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4774.333,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10430.456,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 541.715,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 12897.764,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 27527.528,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 507.524,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 27020.179,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 57282.505,
            "unit": "us"
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
        "date": 1786566011459,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 161.082,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 196.565,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 227.893,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 205.169,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 249.726,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 288.114,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 261.984,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 402.966,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 493.944,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 417.939,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 710.298,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 866.423,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 506.202,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4730.965,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10285.439,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 507.663,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13368.236,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 28339.074,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 509.006,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 30854.428,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 65260.878,
            "unit": "us"
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
        "date": 1786566374568,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 188.432,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 220.914,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 244.428,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 218.298,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 256.21,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 287.231,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 297.035,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 484.737,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 605.552,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 472.484,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 794.687,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 971.917,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 567.692,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5328.496,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10998.877,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 558.674,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14549.942,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30038.088,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 564.005,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 33555.504,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 68000.578,
            "unit": "us"
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
        "date": 1786568431171,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 210.244,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 242.004,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 263.123,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 248.436,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 287.397,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 309.59,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 310.281,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 501.169,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 616.074,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 492.333,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 826.028,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1012.638,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 581.101,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5414.353,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10529.983,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 584.615,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14964.268,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 30076.162,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 583.693,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35051.74,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 70157.725,
            "unit": "us"
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
        "date": 1786614939452,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 131.095,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 158.387,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 179.469,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 147.219,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 176.064,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 200.833,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 181.31,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 278.242,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 341.911,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 293.001,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 502.556,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 618.204,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 358.976,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 3210.303,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 6164.001,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 359.546,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 8753.182,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 17318.805,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 357.543,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 19847.758,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 39186.445,
            "unit": "us"
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
        "date": 1786623600569,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 111.201,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 140.658,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 159.467,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 109.634,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 136.982,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 157.417,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 141.19,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 217.468,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 265.334,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 224.568,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 399.756,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 510.267,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 267.089,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 2626.804,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 6156.459,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 267.514,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 7393.052,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 17271.756,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 268.503,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 16914.291,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 41137.487,
            "unit": "us"
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
        "date": 1786623951673,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 157.605,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 187.91,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 222.722,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 199.687,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 247.689,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 296.882,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 262.671,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 411.423,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 510.41,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 423.719,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 726.842,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 914.111,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 500.767,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4899.386,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11313.744,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 528.026,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 12867.321,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 29362.461,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 543.99,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 27671.908,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 60394.688,
            "unit": "us"
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
        "date": 1786624069771,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 197.211,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 240.881,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 266.74,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 232.82,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 275.597,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 309.349,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 309.761,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 497.907,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 621.475,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 506.421,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 859.383,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1072.653,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 578.074,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5568.599,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 12196.894,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 582.514,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15649.159,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 34705.204,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 583.845,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35415.816,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 79543.986,
            "unit": "us"
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
        "date": 1786625111300,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 154.618,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 188.348,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 217.06,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 197.311,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 237.751,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 258.993,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 259.622,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 404.948,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 503.173,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 431.166,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 749.818,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 927.861,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 519.588,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4995.708,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11376.766,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 522.391,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 12974.159,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31103.046,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 507.9,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 28632.776,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 65283.243,
            "unit": "us"
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
        "date": 1786625477655,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 119.68,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 147.663,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 173.102,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 138.339,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 166.938,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 183.017,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 165.282,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 261.935,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 339.974,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 256.242,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 438.968,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 562.949,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 304.332,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 2864.987,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 6589.627,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 317.29,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 7885.695,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 17711.005,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 321.337,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 18122.527,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 41093.161,
            "unit": "us"
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
        "date": 1786626298565,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 195.294,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 224.889,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 247.322,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 230.019,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 270.303,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 300.529,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 296.713,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 453.658,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 548.072,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 506.044,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 841.43,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1036.644,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 574.411,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5472.552,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 12347.631,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 573.66,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15523.095,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 33501.978,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 576.856,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35216.429,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 78974.034,
            "unit": "us"
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
        "date": 1786883167423,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 200.332,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 232.574,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 248.994,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 232.181,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 290.22,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 345.904,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 297.083,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 456.309,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 553.793,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 492.086,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 824.145,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1018.175,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 577.104,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5433.594,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11837.053,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 574.289,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15384.343,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 33762.213,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 578.857,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35672.489,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 79158.118,
            "unit": "us"
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
        "date": 1786946098161,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 119.55,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 140.542,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 180.042,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 153.481,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 184.799,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 204.759,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 201.169,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 314.376,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 395.397,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 341.456,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 578.073,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 711.413,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 379.683,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 3303.867,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 8625.992,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 389.619,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 9783.725,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 23193.923,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 394.696,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 19491.013,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 47351.338,
            "unit": "us"
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
        "date": 1786947722966,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 208.539,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 242.964,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 277.428,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 250.338,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 291.445,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 319.185,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 305.161,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 466.671,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 562.452,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 492.6,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 841.726,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1036.597,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 593.547,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5612.139,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 12257.845,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 599.31,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15629.651,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 34777.568,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 582.628,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35945.618,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 80810.659,
            "unit": "us"
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
        "date": 1786950617052,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 185.278,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 223.77,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 251.342,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 229.02,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 265.908,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 285.062,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 279.706,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 418.858,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 504.006,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 459.172,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 775.365,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 984.586,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 530.926,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5304.348,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11886.038,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 542.137,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 14796.275,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 33214.096,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 534.394,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 33347.011,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 76217.648,
            "unit": "us"
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
        "date": 1786954151227,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 206.719,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 242.116,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 259.379,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 249.23,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 282.557,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 302.76,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 310.765,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 500.003,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 637.062,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 490.446,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 822.053,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1007.924,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 575.365,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5495.207,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11965.92,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 585.612,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15675.212,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 34619.597,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 577.469,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35787.745,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 78107.49,
            "unit": "us"
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
        "date": 1786955322829,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 215.844,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 244.337,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 307.255,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 240.289,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 280.712,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 314.619,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 306.823,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 476.052,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 587.588,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 491.789,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 824.53,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1005.165,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 574.404,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5645.176,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 12001.781,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 575.405,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15563.632,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 34720.345,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 574.584,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35737.121,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 81273.528,
            "unit": "us"
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
        "date": 1786962701657,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 218.148,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 259.464,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 287.527,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 240.208,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 286.274,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 326.642,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 312.091,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 497.739,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 612.913,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 505.002,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 857.753,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1058.457,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 593.468,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5715.88,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 12185.068,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 602.164,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15924.279,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 35404.573,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 594.079,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 36254.662,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 81188.495,
            "unit": "us"
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
        "date": 1786963641858,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 108.912,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 138.794,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 157.122,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 112.577,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 142.09,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 162.918,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 144.955,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 225.541,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 277.285,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 226.696,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 395.587,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 503.467,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 268.637,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 2509.02,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 5944.659,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 273.167,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 7592.706,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 17091.252,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 281.148,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 16731.451,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 41284.719,
            "unit": "us"
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
        "date": 1788263446262,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 143.082,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 182.25,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 223.752,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 187.517,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 227.977,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 251.963,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 254.517,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 401.055,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 495.525,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 404.618,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 690.002,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 856.369,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 477.267,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 4712.174,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 10631.074,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 484.868,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 13568.69,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 31738.23,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 482.455,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 31512.454,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 77335.27,
            "unit": "us"
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
        "date": 1788264759617,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 202.249,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 243.275,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 266.849,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 237.275,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 279.543,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 311.582,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 301.266,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 459.667,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 556.71,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 491.722,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 822.762,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1004.102,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 577.252,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5613.532,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 12266.915,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 586.89,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15962.657,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 34064.366,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 581.507,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 36114.906,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 77206.003,
            "unit": "us"
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
        "date": 1788269097462,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "client_p50 @load=1",
            "value": 195.634,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=1",
            "value": 236.001,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=1",
            "value": 272.598,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=2",
            "value": 231.112,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=2",
            "value": 276.215,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=2",
            "value": 313.381,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=4",
            "value": 305.44,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=4",
            "value": 496.507,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=4",
            "value": 625.266,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=8",
            "value": 491.279,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=8",
            "value": 828.276,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=8",
            "value": 1025.856,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=16",
            "value": 571.096,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=16",
            "value": 5693.97,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=16",
            "value": 11661.89,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=32",
            "value": 581.414,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=32",
            "value": 15503.404,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=32",
            "value": 33388.155,
            "unit": "us"
          },
          {
            "name": "client_p50 @load=64",
            "value": 582.827,
            "unit": "us"
          },
          {
            "name": "client_p95 @load=64",
            "value": 35322.94,
            "unit": "us"
          },
          {
            "name": "client_p99 @load=64",
            "value": 75546.373,
            "unit": "us"
          }
        ]
      }
    ]
  }
}