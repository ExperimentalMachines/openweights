#!/usr/bin/env python3
"""One summary block per ExecuTorch cold-versus-warm run.

Usage: summarise.py <run>.coldwarm.jsonl [...]
Reads the rows and, beside each, <run>.log (the test's and the runtime's log lines) for the
runtime's own token positions and for which turn-one replies the runtime ended itself.
Refuses pairs that are incomplete, duplicated or have a non-positive timing, and says so.
"""
import json
import re
import statistics as st
import sys


def med(values):
    return st.median(values) if values else float("nan")


def span(values, fmt="{:.0f}"):
    return f"{fmt.format(min(values))}-{fmt.format(max(values))}" if values else "n/a"


def read_log(path):
    """Exact token totals per timed turn, and per pair whether the runtime ended turn one."""
    totals, ended, tails = {}, {}, {}
    position, reached, last_reached = None, False, False
    try:
        lines = open(path, errors="replace").read().splitlines()
    except OSError:
        return totals, ended, tails
    for line in lines:
        m = re.search(r"given pos_ (\d+), num_prompt_tokens (\d+)", line)
        if m:
            position = int(m.group(1))
        # The QNN runner reports its whole prompt instead.
        m = re.search(r"Prompt Processor: total (\d+) prompt tokens", line)
        if m:
            position = int(m.group(1))
        if "Reached to the end of generation" in line:
            reached = True
        if re.search(r"Prompt Tokens: \d+\s+Generated Tokens: \d+", line):
            last_reached, reached = reached, False
        m = re.search(r"coldwarm pair=(\d+) turn one: .*?tail=(\[.*\])\s*$", line)
        if m:
            tails[int(m.group(1))] = m.group(2)
        m = re.search(r"coldwarm pair=(\d+) turn one:", line)
        if m:
            ended[int(m.group(1))] = last_reached
        m = re.search(r"coldwarm pair=(\d+) arm=(\w+) ", line)
        if m and position is not None:
            totals[(int(m.group(1)), m.group(2))] = position
            position = None
    return totals, ended, tails


def skipped(path):
    try:
        return sum("skipped: turn one left no room" in line for line in open(path, errors="replace"))
    except OSError:
        return 0


def summarise(path):
    rows = [json.loads(line) for line in open(path) if line.strip()]
    totals, ended, tails = read_log(path.replace(".coldwarm.jsonl", ".log"))
    print(f"== {path}: {len(rows)} rows")
    if not rows:
        return
    temperature = rows[0].get("temperature")
    print(f"  sampling: {'greedy' if temperature == 0 else 'sampled (engine default)'}")
    pairs = sorted({r["pair"] for r in rows})
    bad = [p for p in pairs if sorted(r["arm"] for r in rows if r["pair"] == p) != ["cold", "warm"]]
    bad += [r["pair"] for r in rows if r["prefill_ms"] <= 0 or r["ttft_ms"] <= 0]
    if bad:
        print(f"  REFUSED pairs (incomplete, duplicated or non-positive timing): {sorted(set(bad))}")
    pairs = [p for p in pairs if p not in bad]
    warm = {p: next(r for r in rows if r["pair"] == p and r["arm"] == "warm") for p in pairs}
    cold = {p: next(r for r in rows if r["pair"] == p and r["arm"] == "cold") for p in pairs}
    redone = [p for p in pairs if warm[p].get("same_turn_one") is False]
    if redone:
        print(f"  turn one came out different when written again in pairs {redone}; left out")
    pairs = [p for p in pairs if p not in redone]
    hits = [p for p in pairs if warm[p]["cached_tokens"] > 0]
    misses = [p for p in pairs if p not in hits]
    print(f"  pairs {len(pairs)} completed, {skipped(path.replace('.coldwarm.jsonl', '.log'))} skipped "
          f"(turn one left no room), warm hit {len(hits)}, missed {misses}")
    if ended:
        runtime_ended = sorted(p for p in pairs if ended.get(p))
        print(f"  turn one ended by the runtime in pairs {runtime_ended}; misses {misses}")
        for p in runtime_ended + [x for x in misses if x not in runtime_ended]:
            if p in tails:
                print(f"    pair {p} last fragments: {tails[p]}")
        some_hit = next((p for p in hits if p in tails), None)
        if some_hit is not None:
            print(f"    pair {some_hit} (a hit) last fragments: {tails[some_hit]}")
    if not hits:
        for arm, side in (("warm", warm), ("cold", cold)):
            t = [side[p]["prefill_ms"] for p in pairs]
            n = [totals[(p, arm)] for p in pairs if (p, arm) in totals]
            print(f"  no hits; {arm}-labelled prefill median {med(t)} ms [{span(t)}], runtime tokens {span(n)}")
        return
    wp = [warm[p]["prefill_ms"] for p in hits]
    cp = [cold[p]["prefill_ms"] for p in hits]
    ratios = [cold[p]["prefill_ms"] / warm[p]["prefill_ms"] for p in hits]
    every = [cold[p]["prefill_ms"] / warm[p]["prefill_ms"] for p in pairs]
    cold_tokens = [totals[(p, "cold")] for p in hits if (p, "cold") in totals]
    warm_tokens = [totals[(p, "warm")] for p in hits if (p, "warm") in totals]
    equal = sum(1 for p in hits if totals.get((p, "cold")) is not None
                and totals.get((p, "cold")) == totals.get((p, "warm")))
    print(f"  warm prefill median {med(wp):.0f} ms [{span(wp)}], engine count fed "
          f"{span([warm[p]['prompt_tokens'] for p in hits])}")
    print(f"  cold prefill median {med(cp):.0f} ms [{span(cp)}], runtime tokens "
          f"{span(cold_tokens)} (median {med(cold_tokens):.0f}), equal totals {equal}/{len(hits)}"
          if cold_tokens else
          f"  cold prefill median {med(cp):.0f} ms [{span(cp)}], engine estimate "
          f"{span([cold[p]['prompt_tokens'] for p in hits])} tokens (no runtime position in the log)")
    print(f"  cold/warm per pair, hits only: median {med(ratios):.1f}x [{span(ratios, '{:.1f}')}]; "
          f"all pairs: median {med(every):.1f}x")
    for order in sorted({warm[p].get("order", "warm-cold") for p in hits}):
        group = [cold[p]["prefill_ms"] / warm[p]["prefill_ms"] for p in hits
                 if warm[p].get("order", "warm-cold") == order]
        print(f"    {order}: {len(group)} pairs, median {med(group):.1f}x")
    same = sum(1 for p in hits if warm[p]["reply"] == cold[p]["reply"])
    print(f"  replies identical in {same}/{len(hits)} hit pairs")
    timed = [warm[p] for p in pairs] + [cold[p] for p in pairs]
    cpu = [r["cpu_c_before"] for r in timed]
    decode = lambda r: (r["generated_tokens"] - 1) * 1000 / r["decode_ms"] if r["decode_ms"] > 0 else float("nan")
    print(f"  CPU at start {span(cpu, '{:.1f}')} C, over 45 C in {sum(c > 45 for c in cpu)}/{len(cpu)}, "
          f"unreadable in {sum(c < 0 for c in cpu)}; screen on {sum(r['screen_on'] for r in timed)}/{len(timed)}; "
          f"cooled up to {max(r['cooled_s'] for r in timed)} s")
    print(f"  decode median tok/s: warm {med([decode(warm[p]) for p in hits]):.1f}, "
          f"cold {med([decode(cold[p]) for p in hits]):.1f}")


for argument in sys.argv[1:]:
    summarise(argument)
