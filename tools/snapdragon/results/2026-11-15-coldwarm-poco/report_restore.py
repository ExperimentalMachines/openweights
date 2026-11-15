#!/usr/bin/env python3
"""Summarise WarmPrefixEval#aFreshChatReadColdAndRestored rows: <stem>.restore.jsonl [...]"""
import json
import statistics as st
import sys

for path in sys.argv[1:]:
    rows = [json.loads(line) for line in open(path) if line.strip()]
    print(f"== {path}: {len(rows)} rows")
    for arm in ("restored", "cold"):
        r = [x for x in rows if x["arm"] == arm]
        tt = [x["ttft_ms"] for x in r]
        print(
            f"  {arm}: n={len(r)} prompt {st.median([x['prompt_tokens'] for x in r]):.0f} "
            f"cached {st.median([x['cached_tokens'] for x in r]):.0f} snapshot {max(x['snapshot_bytes'] for x in r) // 1024} KiB | "
            f"ttft median {st.median(tt):.0f} ms [{min(tt)}-{max(tt)}] | prefill median {st.median([x['prefill_ms'] for x in r]):.0f} ms | "
            f"cpu {min(x['cpu_c_before'] for x in r):.1f}-{max(x['cpu_c_before'] for x in r):.1f} C screen {sum(x['screen_on'] for x in r)}/{len(r)}"
        )
    ratios, same = [], 0
    for p in sorted({x["pair"] for x in rows}):
        w = next(x for x in rows if x["pair"] == p and x["arm"] == "restored")
        c = next(x for x in rows if x["pair"] == p and x["arm"] == "cold")
        ratios.append(c["ttft_ms"] / max(w["ttft_ms"], 1))
        same += w["reply"] == c["reply"]
    print(f"  cold/restored ttft per pair: median x{st.median(ratios):.1f} [{min(ratios):.1f}-{max(ratios):.1f}]; replies identical {same}/{len(ratios)}")
