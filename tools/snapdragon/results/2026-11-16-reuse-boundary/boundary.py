#!/usr/bin/env python3
"""Summarise ReuseBoundary rows: per run and case, how turn one ended, what the case arm read and
kept against the reset arm, and whether the replies matched.

Usage: boundary.py <device>/<run>.boundary.jsonl ...
"""
import json
import statistics as st
import sys

CASES = ["end", "budget", "cancel", "edit"]


def summary(path):
    rows = [json.loads(line) for line in open(path) if line.strip()]
    # A pair whose turn one did not end as its case names, or whose case arm wrote turn one again
    # and got something else, compares nothing: both of its rows are left out and counted.
    bad = {(r["case"], r["rep"]) for r in rows if r["arm"] == r["case"] and (
        r.get("exercised") is False or r["same_turn_one"] is False or
        (r.get("regenerated_reason") not in (None, "", r["turn_one_reason"])))}
    excluded = len(bad)
    rows = [r for r in rows if (r["case"], r["rep"]) not in bad]
    out = []
    for case in CASES:
        arm = [r for r in rows if r["case"] == case and r["arm"] == case]
        reset = [r for r in rows if r["case"] == case and r["arm"] == "reset"]
        if not arm:
            continue
        by_rep = {r["rep"]: r for r in reset}
        same = sum(r["reply"] == by_rep[r["rep"]]["reply"] for r in arm if r["rep"] in by_rep)
        ratios = [by_rep[r["rep"]]["prefill_ms"] / r["prefill_ms"] for r in arm
                  if r["rep"] in by_rep and r["prefill_ms"] > 0]
        out.append({
            "case": case,
            "turn_one": sorted({r["turn_one_reason"] for r in arm}),
            "turn_one_tokens": sorted({r["turn_one_tokens"] for r in arm}),
            # Only a case arm run second writes turn one again; the others have nothing to compare.
            "rewritten": [r["same_turn_one"] for r in arm if r["order"].startswith("reset")],
            "exercised": sum(r.get("exercised", True) for r in arm),
            "n": len(arm),
            "read": sorted({r["prompt_tokens"] for r in arm}),
            "kept": sorted({r["cached_tokens"] for r in arm}),
            "prefill": st.median(r["prefill_ms"] for r in arm),
            "reset_read": sorted({r["prompt_tokens"] for r in reset}),
            "reset_kept": sorted({r["cached_tokens"] for r in reset}),
            "reset_prefill": st.median(r["prefill_ms"] for r in reset),
            "ratio": st.median(ratios) if ratios else None,
            "same_reply": same,
            "record": sorted({r["record"].split(",")[0].split(" at ")[0] for r in arm if r["record"]}),
            "hottest": max(r["cpu_c_before"] for r in rows if r["case"] == case),
        })
    return out, excluded


if __name__ == "__main__":
    for path in sys.argv[1:]:
        print(f"== {path}")
        rows_out, excluded = summary(path)
        print(f"  pairs left out (turn one not as its case, or written again differently): {excluded}")
        for s in rows_out:
            ratio = f"{s['ratio']:.1f}x" if s["ratio"] else "n/a"
            print(f"  {s['case']:6} turn one {s['turn_one']} {s['turn_one_tokens']} tok, exercised {s['exercised']}/{s['n']},"
                  f" rewritten the same {sum(s['rewritten'])}/{len(s['rewritten'])}"
                  f" | case read {s['read']} kept {s['kept']} {s['prefill']:.0f} ms"
                  f" | reset read {s['reset_read']} kept {s['reset_kept']} {s['reset_prefill']:.0f} ms"
                  f" | {ratio} | same reply {s['same_reply']}/{s['n']} | record {s['record']} | max {s['hottest']:.0f} C")
