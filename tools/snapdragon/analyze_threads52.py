#!/usr/bin/env python3
"""r52: the app's engine at 1, 2, 3, 4, 6 and 8 threads at a realistic length, with and without the pause.

  python3 analyze_threads52.py chain52.log logcat-r52.txt [results-dir]

Work: Qwen3 4B Q4_0, the 1,751-token prompt-r30, up to 400 tokens of reply (R stops after 2). A run is the
process whose START line falls between the chain's run line and its end line, and only that process's lines
after its START are read. Every rate comes from the engine's own clocks in its PROGRESS lines, emitted on change
and polled every 250 ms: promptRead with prefillMs (tokens and time to the last completed 512-token chunk), and
generated with decodeMs (first to latest token).

Prompt reading, arms N (no pause) and R (pause on before the run was launched):
  first chunk   512 over prefillMs at the line that reads promptRead=512; a run whose lines skip 512 has none.
                It includes the engine's first touch of the weights.
  later chunks  paused against unpaused over the same completed chunk endpoints, from the first chunk on, the
                unpaused run paired as for decode below.
An R run that completed no chunk before its 600 s cap is censored. Its cap line is written while the process
still runs, and a completed chunk is logged within 250 ms, so no chunk had completed 1 s before the cap; prompt
reading starts within 1 s of START (the uncapped runs' offset is printed as a check). Its first-chunk rate is
therefore below 512 / (cap - START - 2 s). Censored runs are listed apart, never put in a median.

The whole prompt: prefillMs on a run's first line in decode (reading's start to the end of its last batch; the first
token follows within a few ms), unpaused from
the N and D runs (D's pause comes after its prompt), paused from the R runs.

Decode, arms N and D (pause written after the chain saw the first decode progress line, "CHAIN pause on <label>" from OpenWeightsBench):
  D  starts at its first progress line logged at least 1 s after the marker (a line can carry a snapshot up to
     250 ms old), so the switch's moment is left out.
  The pair is the N run of the same round and thread count, or of the other round when that one was not run
  (the deadline cut round 2), marked as such; every run starts from the same cooling gate. Both rates are taken over the first and last
  generated counts that both runs logged inside D's window, so both end points are at the same context
  length (the logs do not hold the tokens, so identical replies are not shown). A D run with no such pair is listed apart and kept out of the table.
  Whole reply N is the engine's own figure from its BENCH line, for N runs whose test passed.

A paused run counts only if its place-<label>.txt samples cover the pause: judged samples run from 1 s after the
on marker to 2 s before the off marker (the run's end when there is none), each one compared with a previous
sample also inside that window; the first within 5 s of the window's start, the last within 5 s of its end, no
gap over 5 s, at least one with a thread busy, and every one reading all three switches on with every busy
thread of the engine's process last on CPU 0 or 1. Every thread counts: with CPUs 2 to 7 paused, none may run
there.

A D run whose pause is not shown held is kept out of the decode figures; its prompt, read before the pause, stays
in the whole-prompt table. If its bad samples all come first (a switch write
that took effect late), its line also gives, post hoc, the decode from 2 s after the last bad sample against its
unpaused pair between the same generated-token counts, and the tokens written before.

Each run's line also gives how often both CPU frequency ceilings (CPUs 0 to 5, and 6 and 7) sat at their maximum
in its samples, and the lowest each reached: Android's soft throttle lowers them as the phone heats, so an
unpaused rate here is the rate as the phone ran it, soft throttling included.
"""
import re
import statistics
import sys
from pathlib import Path

TAG = "OpenWeightsBench"


def logcat(path):
    out = []
    for line in open(path, errors="replace"):
        m = re.match(r"\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+\w\s+(\S+?)\s*:\s(.*)", line)
        if m:
            out.append((float(m.group(1)), m.group(2), m.group(3), m.group(4).strip()))
    return out


def fields(msg):
    return dict(re.findall(r"(\w+)=([^\s,]+)", msg))


def num(f, k):
    v = f.get(k)
    return int(v) if v is not None and v.lstrip("-").isdigit() else None


def held(place, ws, we):
    """Whether the samples cover [ws, we] and every one inside passes the sampled check, with the counts behind it.
    Each thread's CPU is the last one it ran on at the sample, so a run elsewhere between two samples can go unseen:
    this is a check of samples, not a continuous record."""
    out = dict(n=0, bad=0, busy=0, gap=None, first=None, last=None, ok=False)
    if not place.exists() or we <= ws:
        return out
    prev, times = None, []
    for line in open(place):
        p = line.split()
        if len(p) < 3 or not p[0].isdigit() or p[1] != "sw":
            continue
        t, sw = int(p[0]), p[2]
        # Thread records are tid:cpu:ticks; the fixed fields before them (xo, hot, cap) are skipped.
        cur = {a: (int(b), int(c)) for a, b, c in (f.split(":") for f in p[3:] if re.fullmatch(r"\d+:\d+:\d+", f))}
        inside = ws <= t <= we
        if inside and prev is not None and ws <= prev[0] and cur:
            out["n"] += 1
            times.append(t)
            busy = [cpu for tid, (cpu, ticks) in cur.items() if tid in prev[1] and ticks > prev[1][tid][1]]
            out["busy"] += bool(busy)
            if sw != "111" or any(c > 1 for c in busy):
                out["bad"] += 1
        prev = (t, cur)
    if times:
        out["gap"] = max((b - a for a, b in zip(times, times[1:])), default=0)
        out["first"], out["last"] = round(times[0] - ws, 1), round(we - times[-1], 1)
        out["ok"] = out["bad"] == 0 and out["busy"] > 0 and out["gap"] <= 5 and out["first"] <= 5 and out["last"] <= 5
    return out


def ceilings(place, ws=None, we=None):
    """Share of a run's samples (inside [ws, we] when given) with both CPU frequency ceilings at the phone's maximum,
    3,628.8 and 4,608 MHz, and the lowest of each in MHz."""
    caps = []
    if place.exists():
        for line in open(place):
            p = line.split()
            if len(p) > 8 and p[0].isdigit() and p[1] == "sw" and p[7] == "cap" and re.fullmatch(r"\d+/\d+", p[8]) \
                    and (ws is None or ws <= int(p[0]) <= we):
                caps.append(tuple(int(x) for x in p[8].split("/")))
    if not caps:
        return "no samples"
    full = sum(c == (3628800, 4608000) for c in caps)
    return (f"ceilings at max in {100 * full / len(caps):.0f}% of {len(caps)} samples, lowest "
            f"{min(c[0] for c in caps) // 1000}/{min(c[1] for c in caps) // 1000} MHz")


def late_hold(place, ws, we):
    """For a pause not shown held: the last sample in [ws, we] that read the switches on with a busy thread past
    CPU 1, and whether every later sample in the window held. A switch write whose effect came late shows as an
    early run of such samples and none after."""
    prev, bad, after_ok = None, [], True
    for line in open(place):
        p = line.split()
        if len(p) < 3 or not p[0].isdigit() or p[1] != "sw":
            continue
        t = int(p[0])
        cur = {a: (int(b), int(c)) for a, b, c in (f.split(":") for f in p[3:] if re.fullmatch(r"\d+:\d+:\d+", f))}
        if prev is not None and ws <= prev[0] and t <= we:
            busy = [cpu for tid, (cpu, ticks) in cur.items() if tid in prev[1] and ticks > prev[1][tid][1]]
            if p[2] != "111" or any(c > 1 for c in busy):
                bad.append(t)
        prev = (t, cur)
    return (bad[0], bad[-1]) if bad else None


def chunks(prog):
    """(promptRead, prefillMs) at each completed chunk the lines caught, in order."""
    out = []
    for _, f in prog:
        r, ms = num(f, "promptRead"), num(f, "prefillMs")
        if r and ms and (not out or r > out[-1][0]):
            out.append((r, ms))
    return out


def decode_points(prog, after=None):
    """{generated: decodeMs} from lines in decode, optionally only those logged at or after a time."""
    out = {}
    for t, f in prog:
        g, ms = num(f, "generated"), num(f, "decodeMs")
        if f.get("reading") == "false" and g and ms is not None and (after is None or t >= after) and g not in out:
            out[g] = ms
    return out


def a_rate(points, a, b):
    r = rate(points, a, b)
    return f"{r:.2f} tok/s" if r else "n/a"


def rate(points, a, b):
    return (b - a) / (points[b] - points[a]) * 1000 if b > a and points[b] > points[a] else None


def main(chain, cat, results="."):
    lines = open(chain).read().split("\n")
    lc = logcat(cat)
    runs = {}
    for l in lines:
        m = re.match(r"(\d+) run (r52-(\d)-([NDR])(\d)) threads (\d) arm", l)
        if not m:
            continue
        label, rnd, arm, t = m.group(2), int(m.group(3)), m.group(4), int(m.group(6))
        t0 = int(m.group(1))
        endl = next((x for x in lines if f" run {label} end " in x), None)
        if endl is None:
            print(f"{label}: no end line yet, left out")
            continue
        end = int(endl.split()[0])
        # Chain times are whole seconds taken by `date +%s`, so the run line is at or before the launch and
        # the end line at or after the process ended.
        st = [x for x in lc if x[2] == TAG and x[3].startswith("START") and t0 <= x[0] < end + 1]
        if len(st) != 1:
            print(f"{label}: {len(st)} START lines, left out")
            continue
        pid, t_start = st[0][1], st[0][0]
        mine = [x for x in lc if x[1] == pid and x[2] == TAG and t_start <= x[0] < end + 1]
        prog = [(t_, fields(msg)) for t_, _, _, msg in mine if msg.startswith("PROGRESS")]
        bench = next((fields(msg) for _, _, _, msg in mine if msg.startswith("BENCH")), None)

        def marker(what):
            # R's on marker is written just before its launch, D's after it; the off marker after the run.
            ms = [t_ for t_, _, tag, msg in lc if tag == TAG and msg == f"CHAIN pause {what} {label}" and t0 - 5 <= t_ < end + 1]
            return ms[0] if len(ms) == 1 else None

        cap = next((int(x.split()[0]) for x in lines if x.split()[1:2] == [label] and "stopped at its cap" in x), None)
        r = dict(label=label, round=rnd, arm=arm, threads=t, t_start=t_start, end=end, prog=prog, bench=bench, cap=cap,
                 passed=endl.rstrip().endswith("result 1"),
                 watchdog=any(f"watchdog stopped {label}" in x for x in lines))
        if arm in "DR":
            on, off = marker("on"), marker("off")
            r["on"] = on
            ws = max(on, t_start) + 1 if on is not None else None
            r["hold"] = held(Path(results) / f"place-{label}.txt", ws, (off if off else end) - 2) if ws else held(Path("/none"), 0, 0)
            r["win"] = (ws, (off if off else end) - 2) if ws else None
        runs[label] = r

    def pair(rnd, t, arm):
        """The N run of the same round and thread count; failing that, of the other round (marked)."""
        same = [x for x in runs.values() if x["round"] == rnd and x["threads"] == t and x["arm"] == arm and x["passed"]]
        other = [x for x in runs.values() if x["round"] != rnd and x["threads"] == t and x["arm"] == arm and x["passed"]]
        return (same or other or [None])[0]

    # The whole prompt: prefillMs on the run's first line in decode, from the start of reading to the end of its last batch.
    # N and D read it unpaused (D's pause comes after), R paused; an R run counts only if its pause held.
    whole = {t: dict(N=[], P=[]) for t in (1, 2, 3, 4, 6, 8)}
    for r in runs.values():
        first = next((num(f, "prefillMs") for _, f in r["prog"] if f.get("reading") == "false" and num(f, "prefillMs")), None)
        if first is None:
            continue
        r["whole_ms"] = first
        if r["arm"] in "ND":
            whole[r["threads"]]["N"].append((first, r["label"]))
        elif r["hold"]["ok"]:
            whole[r["threads"]]["P"].append((first, r["label"]))

    T = (1, 2, 3, 4, 6, 8)
    tab = {t: dict(fcN=[], fcR=[], cens=[], dec=[], whole=[], later=[], unpaired=[]) for t in T}
    offsets = []
    print("run        thr arm  first chunk  decode                                        notes")
    for r in sorted(runs.values(), key=lambda r: (r["round"], r["threads"], r["arm"])):
        t, note, dec = r["threads"], [], ""
        ck = chunks(r["prog"])
        fc = ck[0][0] / ck[0][1] * 1000 if ck and ck[0][0] == 512 else None
        if ck and ck[0][0] != 512:
            note.append(f"first line read {ck[0][0]}, no first-chunk figure")
        first512 = next(((t_, f) for t_, f in r["prog"] if num(f, "promptRead") == 512), None)
        if first512:
            offsets.append(num(first512[1], "elapsedMs") - num(first512[1], "prefillMs"))
        h = r.get("hold")
        if r["arm"] in "DR" and not h["ok"]:
            note.append(f"pause NOT shown held ({h['n']} samples, {h['busy']} busy, gap {h['gap']}, first {h['first']}, "
                        f"last {h['last']}, {h['bad']} bad), left out")
        if r["watchdog"]:
            note.append("WATCHDOG")
        if r["arm"] == "N":
            if fc:
                tab[t]["fcN"].append(fc)
            if r["passed"] and r["bench"]:
                w = float(r["bench"]["decode"])
                tab[t]["whole"].append(w)
                dec = f"whole reply {w:.1f} tok/s (BENCH)"
            else:
                note.append("test did not pass, no whole-reply figure")
        elif r["arm"] == "R" and h["ok"]:
            if fc:
                tab[t]["fcR"].append(fc)
                n = pair(r["round"], t, "N")
                nck = dict(chunks(n["prog"])) if n else {}
                a, b = 512, ck[-1][0]
                if b > a and a in nck and b in nck:
                    rp = (b - a) / (ck[-1][1] - ck[0][1]) * 1000
                    rn = (b - a) / (nck[b] - nck[a]) * 1000
                    tab[t]["later"].append((r["label"] + ("" if n["round"] == r["round"] else f" (N from round {n['round']})"), a, b, rp, rn))
            elif not ck and r["cap"]:
                s = r["cap"] - r["t_start"] - 2
                tab[t]["cens"].append(512 / s)
                note.append(f"no chunk by its cap, {s:.0f} s after START: below {512 / s:.3f} tok/s (censored)")
            elif not ck:
                note.append("no chunk and no cap line: left out")
        elif r["arm"] == "D" and h["ok"]:
            pd = decode_points(r["prog"], after=r["on"] + 1.0)
            n = pair(r["round"], t, "N")
            pn = decode_points(n["prog"]) if n else {}
            cross = "" if not n or n["round"] == r["round"] else f" (N from round {n['round']})"
            common = sorted(set(pd) & set(pn))
            if len(pd) >= 2 and len(common) >= 2:
                a, b = common[0], common[-1]
                d, dn = rate(pd, a, b), rate(pn, a, b)
                if d and dn:
                    tab[t]["dec"].append((d, dn, r["label"] + cross))
                    dec = f"tokens {a} to {b}: paused {d:.3f}, unpaused {dn:.2f} tok/s{cross}"
            elif len(pd) >= 2:
                g = sorted(pd)
                d = rate(pd, g[0], g[-1])
                tab[t]["unpaired"].append((r["label"], g[0], g[-1], d))
                dec = f"tokens {g[0]} to {g[-1]}: paused {d:.3f} tok/s, NO PAIR yet"
            else:
                note.append("too few decode lines after the pause")
        elif r["arm"] == "D" and r.get("win"):
            # Post hoc, never in the table: from 2 s after the last sample that showed a thread past CPU 1.
            lb = late_hold(Path(results) / f"place-{r['label']}.txt", *r["win"])
            if lb:
                pre = decode_points(r["prog"], after=r["on"] + 1.0)
                pd = decode_points(r["prog"], after=lb[1] + 2.0)
                n = pair(r["round"], t, "N")
                pn = decode_points(n["prog"]) if n else {}
                common = sorted(set(pd) & set(pn))
                early = sorted(g for g in pre if g not in pd)
                if early:
                    g = sorted(pre)
                    e0, e1 = g[0], early[-1]
                    note.append(f"threads past CPU 1 in the pause from {lb[0] - r['on']:+.0f} to {lb[1] - r['on']:+.0f} s; "
                                f"tokens {e0} to {e1} then at {a_rate(pre, e0, e1)}")
                if len(common) >= 2:
                    a, b = common[0], common[-1]
                    d, dn = rate(pd, a, b), rate(pn, a, b)
                    dec = (f"POST HOC from {lb[1] + 2 - r['on']:+.0f} s: tokens {a} to {b}: paused {d:.3f}, unpaused {dn:.2f}"
                           + ("" if n["round"] == r["round"] else f" (N from round {n['round']})"))
        if r["cap"]:
            note.append("stopped at its cap")
        pl = Path(results) / f"place-{r['label']}.txt"
        note.append(ceilings(pl) if r["arm"] != "D" or not r.get("win") else
                    "in the pause: " + ceilings(pl, *r["win"]) + "; before it: " + ceilings(pl, 0, r["win"][0] - 2))
        print(f"{r['label']:10} t{t}  {r['arm']}    {(f'{fc:.2f}' if fc else 'n/a'):>9}    {dec:45} {'; '.join(note)}")

    med = lambda xs: statistics.median(xs) if xs else None
    f = lambda x, d=2: "n/a" if x is None else f"{x:.{d}f}"
    k = lambda a, b: "n/a" if a is None or b is None else f"{100 * a / b:.1f}%"
    print(f"\nStart of prompt reading after the run's clock started, every run with a first chunk: max {max(offsets, default=0)} ms")
    print("\n| threads | first chunk N | first chunk paused | kept | censored paused | decode N (same counts) | decode paused | kept | pairs | whole reply N |")
    print("|---|---|---|---|---|---|---|---|---|---|")
    for t, v in tab.items():
        fcn, fcp = med(v["fcN"]), med(v["fcR"])
        dp, dn = med([d for d, _, _ in v["dec"]]), med([n for _, n, _ in v["dec"]])
        cens = ", ".join(f"<{x:.3f}" for x in v["cens"]) or "none"
        print(f"| {t} | {f(fcn)} | {f(fcp)} | {k(fcp, fcn)} | {cens} | {f(dn)} | {f(dp, 3)} | {k(dp, dn)} | {len(v['dec'])} | {f(med(v['whole']), 1)} |")
    print("\nThe whole 1,751-token prompt, start of reading to the end of its last batch (engine clock), median and range in s:")
    print("| threads | unpaused (N and D runs) | n | paused (R runs) | n | kept |")
    print("|---|---|---|---|---|---|")
    for t in T:
        a, b = sorted(x for x, _ in whole[t]["N"]), sorted(x for x, _ in whole[t]["P"])
        rng = lambda xs: f"{statistics.median(xs) / 1000:.1f} ({xs[0] / 1000:.1f} to {xs[-1] / 1000:.1f})" if xs else "n/a"
        kept = f"{100 * statistics.median(a) / statistics.median(b):.0f}%" if a and b else "n/a"
        print(f"| {t} | {rng(a)} | {len(a)} | {rng(b)} | {len(b)} | {kept} |")

    print("\nPaired decode, each D against its N between the same generated-token counts:")
    for t, v in tab.items():
        for d, n, lab in v["dec"]:
            print(f"  t{t}: {lab} paused {d:.3f}, unpaused {n:.2f}, kept {100 * d / n:.1f}%")
        for lab, a, b, d in v["unpaired"]:
            print(f"  t{t}: {lab} tokens {a} to {b} paused {d:.3f} tok/s, no pair (kept out of the table)")
    print("\nLater chunks over matched endpoints (paused R against the N run of the same round):")
    for t, v in tab.items():
        for lab, a, b, rp, rn in v["later"]:
            print(f"  t{t}: {lab} tokens {a} to {b}: paused {rp:.2f}, unpaused {rn:.2f} tok/s, kept {100 * rp / rn:.1f}%")


if __name__ == "__main__":
    main(*sys.argv[1:])
