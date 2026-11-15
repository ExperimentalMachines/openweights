#!/usr/bin/env python3
"""Summarise the cold and warm rows of ExecuTorchOnDeviceTest#theSecondTurnReadColdAndWarm.

Usage: report.py <stem>.coldwarm.jsonl [...]
Per model: each arm's median and range of prefill and time to first token, the per-pair
cold/warm ratio, the temperatures, and where the two arms' greedy replies first differ.
"""
import json
import statistics as st
import sys


def first_difference(a, b):
    for i, (x, y) in enumerate(zip(a, b)):
        if x != y:
            return i
    return None if len(a) == len(b) else min(len(a), len(b))


for path in sys.argv[1:]:
    rows = [json.loads(line) for line in open(path) if line.strip()]
    print(f"== {path.rsplit('/', 1)[-1]}: {len(rows)} rows")
    misses = {x["pair"] for x in rows if x["arm"] == "warm" and x["cached_tokens"] == 0}
    if misses:
        print(f"  warm arm found nothing to extend in pairs {sorted(misses)}; those pairs are left out below")
    redone = {x["pair"] for x in rows if x["arm"] == "warm" and x.get("same_turn_one") is False}
    if redone:
        print(f"  turn one came out different when written again in pairs {sorted(redone)}; left out below")
    halves = {p for p in {x["pair"] for x in rows} if sorted(x["arm"] for x in rows if x["pair"] == p) != ["cold", "warm"]}
    if halves:
        print(f"  pairs without exactly one warm and one cold row: {sorted(halves)}; left out below")
    rows = [x for x in rows if x["pair"] not in misses | redone | halves]
    for arm in ("warm", "cold"):
        r = [x for x in rows if x["arm"] == arm]
        if not r:
            continue
        pf = [x["prefill_ms"] for x in r]
        tt = [x["ttft_ms"] for x in r]
        print(
            f"  {arm}: n={len(r)} fed {st.median([x['prompt_tokens'] for x in r]):.0f} "
            f"cached {st.median([x['cached_tokens'] for x in r]):.0f} | prefill median {st.median(pf):.0f} ms "
            f"[{min(pf)}-{max(pf)}] | ttft median {st.median(tt):.0f} ms [{min(tt)}-{max(tt)}] | "
            f"cpu at start {min(x['cpu_c_before'] for x in r):.1f}-{max(x['cpu_c_before'] for x in r):.1f} C, "
            f"screen on {sum(x['screen_on'] for x in r)}/{len(r)}"
        )
    ratios, ttft_ratios, same, diffs = [], [], 0, []
    for p in sorted({x["pair"] for x in rows}):
        w = next((x for x in rows if x["pair"] == p and x["arm"] == "warm"), None)
        c = next((x for x in rows if x["pair"] == p and x["arm"] == "cold"), None)
        if not (w and c):
            continue
        ratios.append(c["prefill_ms"] / max(w["prefill_ms"], 1))
        ttft_ratios.append(c["ttft_ms"] / max(w["ttft_ms"], 1))
        d = first_difference(w["reply"], c["reply"])
        if d is None:
            same += 1
        else:
            diffs.append(d)
        print(
            f"  pair {p}: warm {w['prefill_ms']} ms, cold {c['prefill_ms']} ms, "
            f"x{ratios[-1]:.1f}; replies {'identical' if d is None else f'differ at char {d}'}"
        )
        if d is not None:
            print(f"    warm: {w['reply'][max(0, d - 40):d + 60]!r}")
            print(f"    cold: {c['reply'][max(0, d - 40):d + 60]!r}")
    if ratios:
        print(
            f"  cold/warm prefill: median x{st.median(ratios):.1f} [{min(ratios):.1f}-{max(ratios):.1f}]; "
            f"ttft median x{st.median(ttft_ratios):.1f}; replies identical in {same}/{len(ratios)} pairs"
        )
