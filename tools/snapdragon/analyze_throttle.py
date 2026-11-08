#!/usr/bin/env python3
"""The throttle ladder (r20) and the thread test (r19), read against the 2 s thermal loggers.

  python3 analyze_throttle.py ladder  results-r20.jsonl trig-r20.txt trig-r20b.txt
  python3 analyze_throttle.py threads results-r19.jsonl trig-r20.txt trig-r20b.txt

The loggers: trig-r20.txt (from the ladder's start: XO, GPU clock and thermal power level, the
pause switches, kgsl, cDSP) and trig-r20b.txt (from probe 5: also the hottest GPU sensor, the GPU's
allowed ceiling and each CPU policy's ceiling, each device on its own column). Both are merged by
time; a field one logger lacks is None.

Time. A turn's record gives its start on the system clock (`wall_ms`), its length (`turn_ms`), its
decode time (`decode_ms`) and each decode step (`step_ms`). Decode is the end of the turn, so steps
are placed back from the turn's end (end-anchored). They are placed a second way, forward from the
prompt's end (`prompt_ms` and the two transfer timers, which miss the handoff's other work), and
the share of steps whose state differs between the two placements is reported: the size of the
timing error that matters here.

State. A step's interval is read against every logger sample from 2 s before it starts to 2 s after
it ends, plus the last sample before it, because the logger stamps the time before it reads the
devices. The pause state is "on" if every one of those samples had a CPU pause switch on, "off" if
none did, "mixed" otherwise, and "gap" if two consecutive samples there are more than 6 s apart.
The GPU's thermal power level is read the same way and is "mixed" if it changed. Mixed and gap
steps are counted, never assigned.

ladder: the same fixed work (one conversation's first three turns, 128 teacher-forced tokens a
turn) on GPU-only and CPU-only. A turn is "pure" when at least 90% of its decode time ran in one
pause state and one GPU power level, neither mixed nor gap. Pure turns are grouped by processor,
turn, pause state and power level, so the context and the work are the same and only the thermal
state differs. Prefill is classed by its own interval, the turn's first `prompt_ms`.

threads: per cell, in the order run, the warm-up apart. A cell is complete when its run ended with a
`done` record reporting no failures, it has exactly the conversation file's 33 distinct
(conversation, turn) pairs once each, and no turn error. The primary measure is excess stall
seconds, the sum over steps of max(step - 500 ms, 0). Then decode and turn seconds, and decode time
and excess stall seconds by pause state.
"""
import bisect, json, statistics, sys
from collections import Counter, defaultdict

STALL_MS = 500
NEAR = 2
GAP = 6
PURE = 0.9


def load_rows(path):
    rows, bad, lines = [], 0, open(path).read().splitlines()
    for i, line in enumerate(lines):
        if not line.strip():
            continue
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError:
            if i != len(lines) - 1:
                bad += 1  # anything but a last line cut off by a pull is a real fault
    return rows, bad


def load_trig(paths):
    out, short = [], 0
    for path in paths:
        head = None
        for line in open(path):
            p = line.split()
            if not p or p[0].startswith("#"):
                continue
            if p[0] == "time":
                head = [h.split("@")[0] for h in p[1:]]
                continue
            if head is None or not p[0].isdigit():
                continue
            if len(p) != len(head) + 1:
                short += 1
                continue
            v = dict()
            pause = []
            for h, x in zip(head, p[1:]):
                if h.startswith("thermal-pause"):
                    pause.append(x != "0")
                else:
                    v[h] = x
            out.append({
                "t": int(p[0]),
                "pause": any(pause),
                "xo": int(v.get("xo", v.get("sys-therm-0", 0))) / 1000,
                "gpu_c": int(v["gpu_max_c"]) / 1000 if "gpu_max_c" in v else None,
                "clk": int(v["gpuclk"]) // 1_000_000 if "gpuclk" in v else None,
                "ceil": int(v["max_gpuclk"]) // 1_000_000 if "max_gpuclk" in v else None,
                "pwr": int(v["thermal_pwrlevel"]) if "thermal_pwrlevel" in v else None,
                "kgsl": int(v["kgsl"]),
                "cpu_ceil": tuple(int(v[h]) // 1000 for h in head if h.startswith("max_policy")) or None,
            })
    out.sort(key=lambda s: (s["t"], s["ceil"] is None))
    merged = []
    for s in out:  # two loggers at one second: keep the one with more fields
        if merged and merged[-1]["t"] == s["t"]:
            continue
        merged.append(s)
    return merged, short


class States:
    def __init__(self, samples):
        self.s = samples
        self.t = [x["t"] for x in samples]

    def window(self, a, b):
        # From the last sample at or before a - NEAR to the first at or after b + NEAR, so the samples
        # bracketing the interval are in it however the logger's 2 s ticks fall (review found the
        # end bracket missing, which called a fully paused 10.6 s step a gap).
        i = max(bisect.bisect_right(self.t, a - NEAR) - 1, 0)
        j = min(bisect.bisect_left(self.t, b + NEAR) + 1, len(self.s))
        return self.s[i:j]

    def pause_episodes(self, a, b):
        """Each pause between a and b as (start, end): from the first sample with a pause switch on to
        the first sample with none, so a sample's interval is counted as it really was, not as 2 s."""
        out, start = [], None
        for x in self.s:
            if x["t"] < a - GAP or x["t"] > b + GAP:
                continue
            if x["pause"] and start is None:
                start = x["t"]
            elif not x["pause"] and start is not None:
                out.append((start, x["t"]))
                start = None
        if start is not None:
            out.append((start, min(b, self.s[-1]["t"])))
        return [(max(s, a), min(e, b)) for s, e in out if e > a and s < b]

    def state(self, a, b):
        w = self.window(a, b)
        if not w or w[0]["t"] > a or w[-1]["t"] < b:
            return "gap", None
        if any(y["t"] - x["t"] > GAP for x, y in zip(w, w[1:])):
            return "gap", None
        on = {x["pause"] for x in w}
        p = "mixed" if len(on) > 1 else ("on" if True in on else "off")
        pw = {x["pwr"] for x in w}
        return p, (next(iter(pw)) if len(pw) == 1 else "mixed")

    def at(self, ts):
        k = bisect.bisect_right(self.t, ts) - 1
        return self.s[k] if k >= 0 and ts - self.t[k] <= GAP else None


def placed(turn, end_anchored=True):
    if end_anchored:
        t = turn["wall_ms"] + turn["turn_ms"] - turn["decode_ms"]
    else:
        t = turn["wall_ms"] + turn["prompt_ms"] + turn.get("xfer_get_ms", 0) + turn.get("xfer_set_ms", 0)
    out = []
    for s in turn.get("step_ms") or []:
        out.append((t / 1000, (t + s) / 1000, s))
        t += s
    return out


def faults(rows, bad, short):
    k = Counter(d.get("kind") for d in rows)
    load_err = sum(1 for d in rows if d.get("kind") == "load" and d.get("error"))
    turn_err = sum(1 for d in rows if d.get("kind") == "turn" and d.get("error"))
    done_fail = sum(int(d.get("failures", 0)) for d in rows if d.get("kind") == "done")
    print(f"Records: {dict(k)}. Unreadable lines (not the last): {bad}. Logger rows of the wrong length: {short}. "
          f"Load errors {load_err}, turns with an error {turn_err}, turn_error records {k.get('turn_error', 0)}, "
          f"skipped cells {k.get('cell_skipped', 0)}, failures reported by runs {done_fail}.")
    print()


def ladder(rows, st):
    groups, impure, agree, total = defaultdict(list), Counter(), 0, 0
    for d in rows:
        if d.get("kind") != "turn" or d.get("error"):
            continue
        proc = d["prefill"] + ":" + d["decode"]
        a = placed(d)
        b = placed(d, end_anchored=False)
        exposure = defaultdict(float)
        for (s0, s1, ms), (u0, u1, _) in zip(a, b):
            k1, k2 = st.state(s0, s1), st.state(u0, u1)
            total += 1
            agree += k1 == k2
            exposure[k1] += ms
        dec = sum(exposure.values())
        key, ms = max(exposure.items(), key=lambda kv: kv[1])
        pre = st.state(d["wall_ms"] / 1000, (d["wall_ms"] + d["prompt_ms"]) / 1000)
        first = st.at(d["wall_ms"] / 1000)
        if key[0] in ("mixed", "gap") or key[1] == "mixed" or ms < PURE * dec:
            impure[proc] += 1
            continue
        groups[(proc, d["turn"], key[0], key[1])].append({
            "rate": d["decode_steps"] / d["decode_ms"] * 1000,
            "pre": d["new_tokens"] / d["prefill_ms"] * 1000,
            "pre_same": pre == key,
            "label": d["label"],
            "clk": statistics.median([x["clk"] for x in st.window(a[0][0], a[-1][1]) if x["clk"]] or [0]),
            "ceil": first["ceil"] if first else None,
            "gpu_c": first["gpu_c"] if first else None,
            "xo": first["xo"] if first else None,
            "stall": sum(max(m - STALL_MS, 0) for _, _, m in a) / 1000,
        })
    print(f"Step placement: {agree} of {total} steps ({100 * agree / max(total, 1):.1f}%) get the same state "
          f"placed from the turn's end or from the prompt's end. Turns not pure and left out: {dict(impure)}.")
    print()
    print("| processor | turn | pause | GPU power level | turns | decode tok/s, median (range) | GPU clock MHz, median | "
          "GPU ceiling MHz at start | prefill tok/s, median (state same as decode) | excess stall s | probes |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for key in sorted(groups, key=lambda k: (k[0], k[1], k[2], k[3] if isinstance(k[3], int) else -1)):
        v = groups[key]
        r = [x["rate"] for x in v]
        p = [x["pre"] for x in v if x["pre_same"]]
        ceil = sorted({x["ceil"] for x in v if x["ceil"]})
        pre = f"{statistics.median(p):.0f} ({len(p)})" if p else "none in the same state"
        print(f"| {key[0]} | {key[1]} | {key[2]} | {key[3]} | {len(v)} | {statistics.median(r):.1f} "
              f"({min(r):.1f} to {max(r):.1f}) | {statistics.median([x['clk'] for x in v]):.0f} | "
              f"{', '.join(map(str, ceil)) or 'not logged'} | {pre} | {sum(x['stall'] for x in v):.1f} | "
              f"{', '.join(x['label'].split('-')[1] for x in v)} |")
    print()
    print("Same processor and turn, against its own uncapped and unpaused turns (ratio of medians):")
    for proc, turn in sorted({(k[0], k[1]) for k in groups}):
        base = groups.get((proc, turn, "off", 0))
        if not base:
            print(f"- {proc} turn {turn}: no pure uncapped, unpaused turn to compare with")
            continue
        b = statistics.median([x["rate"] for x in base])
        for key in sorted(k for k in groups if k[:2] == (proc, turn) and k[2:] != ("off", 0)):
            v = [x["rate"] for x in groups[key]]
            print(f"- {proc} turn {turn}, pause {key[2]}, power level {key[3]}: {statistics.median(v) / b:.2f} "
                  f"({len(v)} turns against {len(base)})")


def threads(rows, st):
    expected = 33
    cells, order, done, errs = defaultdict(list), [], {}, Counter()
    for d in rows:
        lab = d.get("label")
        if d.get("kind") == "turn":
            if lab not in cells:
                order.append(lab)
            cells[lab].append(d)
        elif d.get("kind") == "done":
            done[lab] = int(d.get("failures", 0))
        elif d.get("kind") in ("turn_error", "cell_skipped") or (d.get("kind") == "load" and d.get("error")):
            errs[lab] += 1
    print("| cell | threads | turns | complete | start XO °C | peak XO °C | pause episodes, s | start GPU / CPU ceilings | "
          "excess stall s | steps over 500 ms | longest step s | decode s | turn s | decode s by pause on / off / mixed or gap | "
          "excess stall s by pause on / off / mixed or gap |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    per = defaultdict(list)
    for lab in order:
        ts = cells[lab]
        t = lab.rsplit("-t", 1)[-1] if "-t" in lab else "6"
        pairs = [(d["conv"], d["turn"]) for d in ts]
        complete = (done.get(lab) == 0 and not errs[lab] and len(pairs) == expected and len(set(pairs)) == expected
                    and not any(d.get("error") for d in ts))
        dec_by, ex_by = defaultdict(float), defaultdict(float)
        allst = []
        for d in ts:
            for s0, s1, ms in placed(d):
                p = st.state(s0, s1)[0]
                p = p if p in ("on", "off") else "other"
                dec_by[p] += ms / 1000
                ex_by[p] += max(ms - STALL_MS, 0) / 1000
                allst.append(ms)
        ex = sum(max(ms - STALL_MS, 0) for ms in allst) / 1000
        first = st.at(ts[0]["wall_ms"] / 1000)
        start = (f"{first['xo']:.1f}", f"{first['ceil']} / {first['cpu_ceil']}") if first else ("?", "?")
        dec = sum(d["decode_ms"] for d in ts) / 1000
        tur = sum(d["turn_ms"] for d in ts) / 1000
        a, b = ts[0]["wall_ms"] / 1000, (ts[-1]["wall_ms"] + ts[-1]["turn_ms"]) / 1000
        eps = st.pause_episodes(a, b)
        peak = max((x["xo"] for x in st.window(a, b)), default=0)
        print(f"| {lab} | {t} | {len(ts)} | {'yes' if complete else 'no'} | {start[0]} | {peak:.1f} | "
              f"{len(eps)}, {sum(e - s for s, e in eps):.0f} | {start[1]} | {ex:.1f} | "
              f"{sum(1 for m in allst if m > STALL_MS)} | {max(allst, default=0) / 1000:.1f} | {dec:.0f} | {tur:.0f} | "
              f"{dec_by['on']:.0f} / {dec_by['off']:.0f} / {dec_by['other']:.0f} | "
              f"{ex_by['on']:.1f} / {ex_by['off']:.1f} / {ex_by['other']:.1f} |")
        if lab != "r19warm" and complete:
            per[t].append((ex, dec, tur, dec_by["on"], ex_by["on"]))
    print()
    for t in sorted(per, key=int):
        v = per[t]
        m = lambda i: statistics.median([x[i] for x in v])
        print(f"- {t} threads, {len(v)} complete cells: excess stall s median {m(0):.1f} (each: "
              f"{', '.join(f'{x[0]:.1f}' for x in v)}), decode s median {m(1):.0f}, turn s median {m(2):.0f}, "
              f"decode s with a pause on, median {m(3):.0f}, excess stall s with a pause on, median {m(4):.1f}")
    print("Complete cells only; the warm-up is never counted.")


if __name__ == "__main__":
    mode, res, *trigs = sys.argv[1:]
    rows, bad = load_rows(res)
    samples, short = load_trig(trigs)
    faults(rows, bad, short)
    (ladder if mode == "ladder" else threads)(rows, States(samples))
