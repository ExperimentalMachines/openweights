#!/usr/bin/env python3
"""One table across model families: the same second turn kept and reset, per runtime and phone.

Usage: families.py
Reads <phone>/<run>.coldwarm.jsonl for the runs named in RUNS and prints, per run: completed
pairs, warm hits, the retained and reset prefill medians, the reset arm's token count (the
runtime's position from the log for ExecuTorch, llama.cpp's own count for llama.cpp), the per-pair
ratio median and range, whether the replies matched, and the warmest turn start.
"""
import json
import os
import statistics as st

from summarise import read_log

PHONES = {"SC-51E": "Galaxy S24", "pa3q": "Galaxy S25 Ultra", "tokay": "Pixel 9",
          "gts10pwifi": "Galaxy Tab S10+", "poco": "POCO X8 Pro Max"}
RUNS = [
    ("Llama 3.2 1B", "ExecuTorch 8da4w-gptq 4k", "greedy-llama32-1b"),
    ("Llama 3.2 1B", "llama.cpp Q4_0", "lc-coldwarm-Llama-3.2-1B-Instruct-Q4_0"),
    ("Llama 3.2 3B", "llama.cpp Q4_0", "lc-coldwarm-Llama-3.2-3B-Instruct-Q4_0"),
    ("Gemma 3 1B", "ExecuTorch INT8-INT4 (1k window, 690-token prompt)", "greedy-gemma3-1b-int8-s28"),
    ("Gemma 3 1B", "llama.cpp Q4_K_M", "lc-coldwarm-gemma-3-1b-it-Q4_K_M"),
    ("Gemma 3n E2B", "llama.cpp Q4_K_M", "lc-coldwarm-gemma-3n-E2B-it-Q4_K_M"),
    ("LFM2.5 1.2B", "llama.cpp QAD-Q4_0", "lc-coldwarm-LFM2.5-1.2B-Instruct-QAD-Q4_0"),
    ("Qwen3 1.7B", "llama.cpp Q8_0", "lc-coldwarm-Qwen3-1.7B-Q8_0"),
]
HERE = os.path.dirname(os.path.abspath(__file__))


def summary(path):
    rows = [json.loads(line) for line in open(path) if line.strip()]
    # A pair counts only with exactly one row per arm, a positive timing in each, and, for a
    # reset-first pair, a turn one that came out the same when written again. Anything else is
    # reported, never silently dropped.
    arms = {}
    for r in rows:
        arms.setdefault(r["pair"], []).append(r)
    bad = sorted(p for p, rs in arms.items()
                 if sorted(r["arm"] for r in rs) != ["cold", "warm"]
                 or any(r["prefill_ms"] <= 0 for r in rs)
                 or any(r.get("same_turn_one") is False for r in rs))
    warm = {r["pair"]: r for r in rows if r["arm"] == "warm" and r["pair"] not in bad}
    cold = {r["pair"]: r for r in rows if r["arm"] == "cold" and r["pair"] not in bad}
    pairs = sorted(set(warm) & set(cold))
    if not pairs:
        return None
    hits = [p for p in pairs if warm[p]["cached_tokens"] > 0]
    ratios = [cold[p]["prefill_ms"] / warm[p]["prefill_ms"] for p in hits]
    same = sum(warm[p]["reply"] == cold[p]["reply"] for p in pairs)
    hottest = max(r["cpu_c_before"] for r in rows)
    w = [warm[p]["prefill_ms"] for p in hits]
    c = [cold[p]["prefill_ms"] for p in pairs]
    # ExecuTorch rows carry the engine's estimate; its log has the runtime's own position.
    # llama.cpp rows carry llama.cpp's own count, and its log has no position line.
    totals, _, _ = read_log(path.replace(".coldwarm.jsonl", ".log"))
    positions = [totals[(p, "cold")] for p in pairs if (p, "cold") in totals]
    llama = cold[pairs[0]].get("runtime") == "llama.cpp"
    # ExecuTorch without a runtime position has no exact count: say so rather than substitute.
    tokens = cold[pairs[0]]["prompt_tokens"] if llama else (
        int(st.median(positions)) if positions else None)
    return {
        "pairs": len(pairs), "hits": len(hits), "warm": st.median(w) if w else None,
        "cold": st.median(c), "tokens": tokens,
        "ratio": st.median(ratios) if ratios else None,
        "lo": min(ratios) if ratios else None, "hi": max(ratios) if ratios else None,
        "same": same, "hottest": hottest, "bad": bad,
        "cold_reused": sum(cold[p]["cached_tokens"] > 0 for p in pairs),
    }


def main():
    print("| Model | Runtime | Phone | Pairs, reuse | Retained, median | Reset, median | Ratio [range] "
          "| Same reply | Warmest start |")
    print("|---|---|---|---|---|---|---|---|---|")
    for model, runtime, run in RUNS:
        for phone, name in PHONES.items():
            path = os.path.join(HERE, phone, run + ".coldwarm.jsonl")
            if not os.path.exists(path):
                continue
            s = summary(path)
            if s is None:
                print(f"| {model} | {runtime} | {name} | no completed pairs | | | | | |")
                continue
            ratio = f"{s['ratio']:.1f}x [{s['lo']:.1f}-{s['hi']:.1f}]" if s["ratio"] else "none"
            tokens = f"{s['tokens']} tokens" if s["tokens"] is not None else "no runtime position"
            print(f"| {model} | {runtime} | {name} | {s['pairs']}, {s['hits']} | {s['warm']:.0f} ms "
                  f"| {s['cold']:.0f} ms, {tokens} | {ratio} | {s['same']} of {s['pairs']} "
                  f"| {s['hottest']:.0f} C |")
            if s["bad"] or s["cold_reused"]:
                print(f"<!-- {run} on {name}: refused pairs {s['bad']}, "
                      f"reset arms that reused a cache: {s['cold_reused']} -->")


if __name__ == "__main__":
    main()
