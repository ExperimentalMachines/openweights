#!/usr/bin/env python3
"""Summarise WarmPrefixEval#aFreshChatReadColdAndRestored rows: <run>.restore.jsonl [...]

The restored arm warmed the head and held a two-turn conversation before the timed fresh chat;
the reset-cache arm is an engine that never warmed. Times are time to first token.
"""
import json
import statistics as st
import sys

for path in sys.argv[1:]:
    rows = [json.loads(line) for line in open(path) if line.strip()]
    print(f"== {path}: {len(rows)} rows")
    if not rows:
        print("  no rows: read the log; the test skips a model whose warm found no head, and an empty file can also mean the run failed")
        continue
    pairs = sorted({x["pair"] for x in rows})
    bad = [p for p in pairs if sorted(x["arm"] for x in rows if x["pair"] == p) != ["cold", "restored"]]
    bad += [x["pair"] for x in rows if x["ttft_ms"] <= 0]
    if bad:
        print(f"  REFUSED pairs: {sorted(set(bad))}")
    pairs = [p for p in pairs if p not in bad]
    for arm in ("restored", "cold"):
        r = [x for x in rows if x["arm"] == arm and x["pair"] in pairs]
        tt = [x["ttft_ms"] for x in r]
        print(
            f"  {arm}: n={len(r)} read {sorted({x['prompt_tokens'] for x in r})} "
            f"cached {sorted({x['cached_tokens'] for x in r})} snapshot {max(x['snapshot_bytes'] for x in r) // 1024} KiB | "
            f"ttft median {st.median(tt):.0f} ms [{min(tt)}-{max(tt)}] | "
            f"cpu {min(x['cpu_c_before'] for x in r):.1f}-{max(x['cpu_c_before'] for x in r):.1f} C "
            f"screen {sum(x['screen_on'] for x in r)}/{len(r)}"
        )
    ratio = {}
    same = 0
    for p in pairs:
        w = next(x for x in rows if x["pair"] == p and x["arm"] == "restored")
        c = next(x for x in rows if x["pair"] == p and x["arm"] == "cold")
        ratio[p] = (c["ttft_ms"] / w["ttft_ms"], w["order"])
        same += w["reply"] == c["reply"]
    v = [x[0] for x in ratio.values()]
    print(f"  cold/restored ttft per pair: median {st.median(v):.1f}x [{min(v):.1f}-{max(v):.1f}]; replies identical {same}/{len(pairs)}")
    for order in sorted({x[1] for x in ratio.values()}):
        g = [x[0] for x in ratio.values() if x[1] == order]
        print(f"    {order}: {len(g)} pairs, median {st.median(g):.1f}x")
    print(f"  replies seen: {sorted({x['reply'][:40] for x in rows})}")
