window.BENCHMARK_DATA = {
  "lastUpdate": 1784303708372,
  "repoUrl": "https://github.com/FalkorDB/JFalkorDB",
  "entries": {
    "Benchmark": [
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
          "id": "139b724cd3898d2a8605a56ab610562f7ad14efd",
          "message": "bench: JMH benchmarks + per-PR-vs-master regression radar (Wave 2, PR 7) (#314)\n\n* bench: add JMH benchmarks + per-PR-vs-master regression radar (Wave 2, PR 7)\n\nAdd a standalone, non-deployable benchmarks/ JMH module (JDK 21, shaded runnable\njar) with server-backed query benchmarks in AverageTime mode, run via\n`just bench` / `bench-one` / `bench-baseline`. It depends on the published core\njar and is never part of the main reactor or the shipped artifact.\n\nA new Benchmark workflow runs the suite and feeds\nbenchmark-action/github-action-benchmark: on master it stores the baseline and\npublishes a trend to gh-pages; on PRs it compares against that baseline and\ncomments/summarizes regressions. Informational only (fail-on-alert:false,\ngenerous 200% threshold), SHA-pinned. Benchmarks default to a pinned\nTestcontainers FalkorDB, or reuse one via FALKORDB_HOST/PORT.\n\nValidated locally on JDK 21: the shaded jar builds and runs against a server,\nemitting valid JMH JSON (pointMatch/rangeCount/createNode, us/op).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* bench: harden QueryBenchmark setup — NumberFormatException + idempotency (AI review)\n\nParse FALKORDB_PORT via a helper that rethrows a descriptive IllegalStateException\ninstead of an uncaught NumberFormatException (DeepSource), and best-effort\ndeleteGraph the `bench` graph before seeding so an aborted prior run against an\nexternal server can't duplicate nodes and skew timings (CodeRabbit).\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n* bench: pass root version to bench recipes + guard teardown cleanup (Copilot)\n\n`just bench`/`bench-one` now capture the root project.version and pass it as\n-Djfalkordb.version (like verify-jdk8), so benchmarks always resolve the\nchecked-out core rather than a hard-coded default. Guard deleteGraph() in\ntearDown so a failing delete can't skip closing the driver / stopping the\ncontainer.\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>\n\n---------\n\nCo-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>",
          "timestamp": "2026-07-17T18:53:32+03:00",
          "tree_id": "ec9fab97e9e0e1c6ac853f886da3add760f48aa3",
          "url": "https://github.com/FalkorDB/JFalkorDB/commit/139b724cd3898d2a8605a56ab610562f7ad14efd"
        },
        "date": 1784303707679,
        "tool": "jmh",
        "benches": [
          {
            "name": "com.falkordb.bench.QueryBenchmark.createNode",
            "value": 124.41378800329781,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "com.falkordb.bench.QueryBenchmark.pointMatch",
            "value": 228.51006677651017,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "com.falkordb.bench.QueryBenchmark.rangeCount",
            "value": 251.83560171518533,
            "unit": "us/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}