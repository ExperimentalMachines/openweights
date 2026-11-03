"""Decode step time by context length, and, with a thermal log, the turns that ran under mitigation.

  python3 analyze_heat.py results-r14.jsonl [--therm thermlog-r14.txt]

Per model:
- the median decode step per decoder in 512-token context buckets (a step's context is the turn's
  position plus its new tokens plus the step's index), pooled over that decoder's cells. Context
  grows with replay order, so heat accumulates over the same steps: this shows where a decoder's
  step time grows, not why;
- with --therm (the phone-side logger, thermlog-r13.sh and later, one line every 10 s with each
  cooling device's state, the hottest NSP zone and the last finished turn's label): the stretches
  where CPU cores were paused (`thermal-pause-C0` or `-3C` above 0), each cell's decode steps over
  500 ms against those in turns with at least one paused sample, and NPU decode in turns with at
  least half their samples under the cDSP device above 0 against the same turn in another
  NPU-decode cell with none.

A sample is credited to the turn whose own interval (wall_ms at its start, plus turn_ms) holds the
sample's timestamp; samples between turns (gates, loads, warm-ups) are credited to none. The logger
stamps the time before it reads the devices, which takes a second or two, so a sample at a turn's
edge can fall either way. The logger's own turn label (the last finished turn) is not used.
"""
import argparse, bisect, json, statistics
from collections import defaultdict

BUCKET = 512
STALL_MS = 500


def model_of(r):
    return r["model"].split("/")[-1].replace(".gguf", "")


def cell_of(d):
    return f"{d['prefill']}:{d['decode']}"


def steps_by_context(turns):
    by = defaultdict(lambda: defaultdict(list))
    for d in turns:
        base = d["pos"] + d["new_tokens"]
        for i, s in enumerate(d.get("step_ms", [])):
            by[d["decode"]][(base + i) // BUCKET].append(s)
    buckets = sorted({k for row in by.values() for k in row})
    print("| decoder | " + " | ".join(f"{k * BUCKET} to {(k + 1) * BUCKET - 1}" for k in buckets) + " |")
    print("|---" * (len(buckets) + 1) + "|")
    for dec in ("cpu", "gpu", "npu"):
        row = by.get(dec)
        if row:
            print(f"| {dec} | " + " | ".join(f"{statistics.median(row[k]):.0f} ms" if k in row else "" for k in buckets) + " |")
    print()
    print("Median decode step by context length, all of that decoder's cells pooled.")
    print()


def mitigation(turns, lines, hdr):
    pause = [i for i, h in enumerate(hdr) if h in ("thermal-pause-C0", "thermal-pause-3C")]
    cdsp = hdr.index("cdsp") if "cdsp" in hdr else None
    # Each turn's own interval: wall_ms is taken at its start, turn_ms is its length.
    spans = sorted((d["wall_ms"] / 1000, (d["wall_ms"] + d["turn_ms"]) / 1000, j) for j, d in enumerate(turns))
    starts = [a for a, _, _ in spans]
    samples, paused, cd = defaultdict(int), defaultdict(int), defaultdict(int)
    stretches, start, last = [], None, None
    lo, hi = spans[0][0], spans[-1][1]
    for p in lines:
        ts = int(p[0])
        if not lo <= ts <= hi:
            continue
        states = p[1:1 + len(hdr)]
        on = any(states[i] != "0" for i in pause)
        if on and start is None:
            start = ts
        if not on and start is not None:
            stretches.append((start, last)); start = None
        last = ts
        k = bisect.bisect_right(starts, ts) - 1
        if k < 0 or ts > spans[k][1]:
            continue  # between turns: a gate, a load or a warm-up
        j = spans[k][2]
        samples[j] += 1
        paused[j] += on
        if cdsp is not None:
            cd[j] += states[cdsp] != "0"
    if start is not None:
        stretches.append((start, last))
    if last is None:
        print("No thermal-log samples fall in this model's turns.")
        print()
        return
    print(f"Core-pause stretches in the logger's samples: {len(stretches)}"
          + (", lasting " + ", ".join(f"{(b - a + 10) / 60:.1f}" for a, b in stretches) + " min" if stretches else "") + ".")
    print()
    print("| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |")
    print("|---|---|---|---|")
    cells = defaultdict(lambda: [0, 0, 0])
    for j, d in enumerate(turns):
        st = sum(1 for s in d.get("step_ms", []) if s > STALL_MS)
        c = cells[cell_of(d)]
        c[0] += st
        if paused[j]:
            c[1] += st; c[2] += 1
    for k, (st, inp, n) in cells.items():
        print(f"| {k} | {st} | {inp} | {n} |")
    print()
    print("On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.")
    print()
    rate = {}
    for j, d in enumerate(turns):
        if d["decode"] == "npu" and samples[j]:
            rate[(d["prefill"], d["conv"], d["turn"])] = (d["decode_steps"] / d["decode_ms"] * 1000, cd[j] / samples[j])
    ratios = [r / r2 for (pf, cv, tn), (r, f) in rate.items() for (pf2, cv2, tn2), (r2, f2) in rate.items()
              if (cv, tn) == (cv2, tn2) and pf != pf2 and f >= 0.5 and f2 == 0]
    if ratios:
        print(f"NPU decode in a turn with at least half its samples under cdsp above 0, against the same turn in another "
              f"NPU-decode cell with none: {len(ratios)} pairs, median {statistics.median(ratios):.2f} times the rate "
              f"(range {min(ratios):.2f} to {max(ratios):.2f}).")
        print()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--therm")
    a = ap.parse_args()
    rows = [json.loads(l) for l in open(a.run)]
    hdr, lines = None, []
    if a.therm:
        text = open(a.therm).read().splitlines()
        hdr = [x.split("=")[1] for x in text[0].split()]
        lines = [l.split() for l in text[1:] if len(l.split()) > len(hdr)]
    for m in dict.fromkeys(model_of(r) for r in rows if "turn_ms" in r):
        turns = [r for r in rows if "turn_ms" in r and model_of(r) == m]
        print(f"## {m}")
        print()
        steps_by_context(turns)
        if hdr:
            mitigation(turns, lines, hdr)


if __name__ == "__main__":
    main()
