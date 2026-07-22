#!/usr/bin/env python3
"""Compare two runs of the JFalkorDB client load-sweep benchmark (see LoadBenchmark.java).

Both runs are expected to come from the *same machine* (e.g. `just bench-compare`), so absolute
hardware speed cancels out and the head/base ratio is a meaningful client-overhead delta rather than
runner-to-runner noise. Reads the JSON the benchmark harness emits (a flat array of
{"name","unit","value"} objects) for base and head, and reports per-load ratios.

Gating (what can flag a regression) is deliberately limited to the *stable* signals — throughput and
client p50 — because the high-load p95/p99 tails are dominated by pool-contention jitter and are
reported for information only.

Usage:
  compare_bench.py BASE_LAT HEAD_LAT BASE_TPUT HEAD_TPUT [--threshold 1.25] [--base-label ..] [--head-label ..]

Exit code: 0 if no gated regression beyond the threshold, 1 otherwise.
"""
import argparse
import json
import re
import sys

LOAD_RE = re.compile(r"@load=(\d+)")


def load_metrics(path):
    """Return {load:int -> {metric_key:str -> value:float}} parsed from a harness JSON file."""
    with open(path, encoding="utf-8") as fh:
        rows = json.load(fh)
    out = {}
    for row in rows:
        name = row["name"]
        m = LOAD_RE.search(name)
        if not m:
            continue
        load = int(m.group(1))
        key = name.split("@load=")[0].strip()  # e.g. "client_p50", "throughput"
        out.setdefault(load, {})[key] = float(row["value"])
    return out


def fmt_ratio(ratio):
    return "n/a" if ratio is None else f"{ratio:.2f}x"


def fmt_num(value, fmt):
    return "n/a" if value is None else format(value, fmt)


def fmt_pair(base, head, fmt):
    return f"{fmt_num(base, fmt)} → {fmt_num(head, fmt)}"


def slowdown(base, head, bigger_is_better):
    """Ratio >1 == head is worse than base. None when it cannot be computed."""
    if base is None or head is None:
        return None
    if bigger_is_better:
        # throughput: worse means head < base
        return None if head <= 0 else base / head
    # latency: worse means head > base
    return None if base <= 0 else head / base


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("base_latency")
    ap.add_argument("head_latency")
    ap.add_argument("base_throughput")
    ap.add_argument("head_throughput")
    ap.add_argument("--threshold", type=float, default=1.25,
                    help="flag a regression when a gated metric is this many times worse (default 1.25 = 25%%)")
    ap.add_argument("--base-label", default="base")
    ap.add_argument("--head-label", default="head")
    ap.add_argument("--fail-on-regression", action="store_true",
                    help="exit non-zero when a gated regression is found (default: report only, exit 0)")
    args = ap.parse_args()

    if args.threshold <= 1.0:
        ap.error("--threshold must be > 1.0")

    base_lat = load_metrics(args.base_latency)
    head_lat = load_metrics(args.head_latency)
    base_tp = load_metrics(args.base_throughput)
    head_tp = load_metrics(args.head_throughput)

    loads = sorted(set(base_lat) & set(head_lat) & set(base_tp) & set(head_tp))
    if not loads:
        print("bench-compare: no comparable load levels found in the two runs", file=sys.stderr)
        return 2

    lines = []
    lines.append("<!-- bench-compare -->")
    lines.append("## 📊 Client benchmark — same-machine A/B")
    lines.append("")
    lines.append(
        f"`{args.head_label}` vs `{args.base_label}`, benchmarked back-to-back on the **same runner** "
        f"(so runner speed cancels out). A gated ratio `> {args.threshold:.2f}x` means the change is "
        f"that much slower. Throughput & p50 are gated; p95/p99 are shown for context only "
        "(high-load tails are noisy).")
    lines.append("")
    lines.append("| load | throughput (ops/s) base→head | Δ | p50 (µs) base→head | Δ | p95 Δ | p99 Δ |")
    lines.append("|--:|--|--:|--|--:|--:|--:|")

    regressions = []
    for load in loads:
        b_tp = base_tp[load].get("throughput")
        h_tp = head_tp[load].get("throughput")
        b_p50 = base_lat[load].get("client_p50")
        h_p50 = head_lat[load].get("client_p50")
        b_p95 = base_lat[load].get("client_p95")
        h_p95 = head_lat[load].get("client_p95")
        b_p99 = base_lat[load].get("client_p99")
        h_p99 = head_lat[load].get("client_p99")

        tp_slow = slowdown(b_tp, h_tp, bigger_is_better=True)
        p50_slow = slowdown(b_p50, h_p50, bigger_is_better=False)
        p95_slow = slowdown(b_p95, h_p95, bigger_is_better=False)
        p99_slow = slowdown(b_p99, h_p99, bigger_is_better=False)

        flagged = []
        if tp_slow is not None and tp_slow > args.threshold:
            flagged.append(f"throughput {tp_slow:.2f}x")
        if p50_slow is not None and p50_slow > args.threshold:
            flagged.append(f"p50 {p50_slow:.2f}x")
        if flagged:
            regressions.append(f"load={load}: " + ", ".join(flagged))

        mark = " ⚠️" if flagged else ""
        lines.append(
            f"| {load} | {fmt_pair(b_tp, h_tp, '.0f')} | {fmt_ratio(tp_slow)}{mark} "
            f"| {fmt_pair(b_p50, h_p50, '.1f')} | {fmt_ratio(p50_slow)}{mark} "
            f"| {fmt_ratio(p95_slow)} | {fmt_ratio(p99_slow)} |")

    lines.append("")
    if regressions:
        lines.append(f"### ⚠️ Possible regression (> {args.threshold:.2f}x on a gated metric)")
        for r in regressions:
            lines.append(f"- {r}")
    else:
        lines.append(f"### ✅ No regression beyond {args.threshold:.2f}x on any gated metric (throughput, p50).")

    report = "\n".join(lines)
    print(report)
    return 1 if (regressions and args.fail_on_regression) else 0


if __name__ == "__main__":
    sys.exit(main())
