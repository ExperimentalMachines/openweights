#!/usr/bin/env python3
"""r32: the engine's stall guard at a realistic length, with pauses that come and go.

  python3 analyze_guard.py chain32.log logcat-r32.txt trig-r31.txt

Runs, their log lines and their PIDs are found as analyze_app.py finds them. For each run: the tokens
written and the decode time to the last of them (the last PROGRESS line), the decode rate, the first
token's time, the seconds the logger saw the pause switches on during the run, and the guard's own
lines from the engine ("threads: decode steps stalled" when it falls back to 2 threads, "fast again;
back to N" when it returns), each placed against the pause's logged start and end.
"""
import re
import statistics
import sys
from collections import defaultdict

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import analyze_app as A  # noqa: E402


def episodes(ps, a, b):
    """Pause stretches between a and b, from the first sample with any switch on to the first with none."""
    out, start = [], None
    for t, sw in ps:
        if t < a or t > b:
            continue
        on = any(sw.values())
        if on and start is None:
            start = t
        elif not on and start is not None:
            out.append((start, t))
            start = None
    if start is not None:
        out.append((start, b))
    return out


def main(chain, cat, trig):
    rs, _ = A.runs(chain)
    lines, ps = A.logcat(cat), A.pauses(trig)
    rows = []
    for r in rs:
        window = [x for x in lines if r["start"] - 1 <= x[0] <= r["end"] + 2]
        starts = [(t, pid) for t, pid, tag, m in window
                  if tag == "OpenWeightsBench" and m.startswith("START") and A.fields(m).get("threads") == str(r["threads"])]
        if len(starts) != 1:
            print(f"{r['label']}: {len(starts)} START lines, left out")
            continue
        t0, pid = starts[0]
        mine = [(t, tag, m) for t, p, tag, m in window if p == pid]
        prog = [(t, A.fields(m)) for t, tag, m in mine if tag == "OpenWeightsBench" and m.startswith("PROGRESS")]
        bench = [A.fields(m) for _, tag, m in mine if tag == "OpenWeightsBench" and m.startswith("BENCH")]
        guard = [(t - t0, m) for t, tag, m in mine if tag == "OpenWeights" and "decode steps" in m]
        eps = [(a - t0, b - t0) for a, b in episodes(ps, r["start"], r["end"])]
        last = prog[-1][1] if prog else {}
        gen = int(last.get("generated", 0))
        dec = int(last["decodeMs"]) / 1000 if last.get("decodeMs") not in (None, "null") else None
        first_tok = next((t - t0 for t, f in prog if f.get("reading") == "false"), None)
        rows.append({**r, "gen": gen, "dec": dec, "rate": (gen - 1) / dec if dec and gen > 1 else None,
                     "done": bool(bench), "first": first_tok, "eps": eps, "guard": guard})
    print("| run | arm | finished | tokens | decode s | decode tok/s | first token s | pause, s from START | guard, s from START |")
    print("|---|---|---|---|---|---|---|---|---|")
    for r in rows:
        eps = ", ".join(f"{a:.0f} to {b:.0f}" for a, b in r["eps"]) or "none"
        g = "; ".join(f"{t:.0f}: {'fell back' if 'stalled' in m else 'back'}" for t, m in r["guard"]) or "none"
        rate = f"{r['rate']:.2f}" if r["rate"] else ""
        dec = f"{r['dec']:.1f}" if r["dec"] else ""
        first = f"{r['first']:.0f}" if r["first"] is not None else ""
        done = "yes" if r["done"] else ("watchdog" if r["watchdog"] else "no")
        print(f"| {r['label']} | {r['arm']} | {done} | {r['gen']} | {dec} | {rate} | {first} | {eps} | {g} |")
    print()
    by = defaultdict(list)
    for r in rows:
        by[r["arm"]].append(r)
    for arm in sorted(by):
        v = [x for x in by[arm] if x["dec"]]
        if v:
            print(f"- {arm}: {len(v)} runs, tokens {[x['gen'] for x in v]}, decode s {[round(x['dec'], 1) for x in v]}, "
                  f"tok/s median {statistics.median([x['rate'] for x in v if x['rate']]):.2f}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
