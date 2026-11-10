#!/usr/bin/env python3
"""r50: the stall guard's early try against its scheduled tries alone, on an SM8550.

  python3 analyze_guard50.py chain50.log logcat-r50.txt [results-dir]

Each run is the process whose START line falls between the chain's start and end lines for it. The
engine logs every change the guard makes: "decode steps stalled" when it falls back, "trying 4 again
(scheduled|steps sped up ...)" when it tries the planned count, "the try at 4 stalled" when a try
fails, and "fast again; back to 4" when one succeeds. Each is placed against the chain's own pause
on and off lines (logged in whole seconds, just after the switch was written). For a W run the
return is the time from the pause going off to "back to 4"; for an M run the pause stays on, so
every try is a failed one and costs one stalled step.

The chain's own lines carry whole seconds, taken just after the switch was written, so a time
measured from them is uncertain by up to a second; when the chain also wrote "CHAIN pause on|off"
into logcat (chain50b on), those millisecond times are used instead, and the output says which.

A run counts only if its place-<label>.txt samples cover the pause: the first within 5 s of its
start, the last within 5 s of its end, no gap over 5 s, at least one with an engine thread busy, and
every one reading the switch on with each busy engine thread last on CPU 0, 1 or 2. Samples from the
last 2 s before the pause ends are left out: one can read the switch before it is cleared and the
threads after.
"""
import re
import statistics
import sys
from pathlib import Path


def logcat(path):
    out = []
    for line in open(path, errors="replace"):
        m = re.match(r"\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+\w\s+(\S+?)\s*:\s(.*)", line)
        if m:
            out.append((float(m.group(1)), m.group(2), m.group(3), m.group(4)))
    return out


def fields(msg):
    return dict(re.findall(r"(\w+)=([^\s,]+)", msg))


def held(place, on, off):
    """Whether the samples cover the pause and every one shows it held, with the counts behind it."""
    out = dict(n=0, bad=0, busy=0, gap=None, first=None, last=None, ok=False)
    if not place.exists():
        return out
    prev, times = None, []
    for line in open(place):
        p = line.split()
        if len(p) < 4 or not p[0].isdigit():
            continue
        t = int(p[0])
        cur = {tid: (int(cpu), int(ticks)) for tid, cpu, ticks in (f.split(":") for f in p[3:])}
        # A sample is stamped when it starts, and on three busy little cores reading every thread can
        # take most of a second, so one started in the last 2 s can read the switch before it is
        # cleared and the threads after (r50b-3): only samples well inside the pause are judged.
        if prev and on + 1 <= t <= off - 2:
            out["n"] += 1
            times.append(t)
            busy = [cpu for tid, (cpu, ticks) in cur.items() if tid in prev and ticks > prev[tid][1]]
            out["busy"] += bool(busy)
            if p[2] != "1" or any(c > 2 for c in busy):
                out["bad"] += 1
        prev = cur
    if times:
        out["gap"] = max(b - a for a, b in zip(times, times[1:])) if len(times) > 1 else 0
        out["first"], out["last"] = round(times[0] - on, 1), round(off - times[-1], 1)
        out["ok"] = out["bad"] == 0 and out["busy"] > 0 and out["gap"] <= 5 and out["first"] <= 5 and out["last"] <= 5
    return out


def main(chain, cat, results="."):
    lines = open(chain).read().split("\n")
    lc = logcat(cat)
    rows = []
    for l in lines:
        m = re.match(r"(\d+) run (r50b?-\d+-([WMN])([ET])) arm \w guard (\S+)", l)
        if not m:
            continue
        label, arm, mode = m.group(2), m.group(3), m.group(4)
        t0 = int(m.group(1))
        end = next((int(x.split()[0]) for x in lines if f" run {label} end " in x), None)
        # A run with no end line was cut short (r50b-4, when the reservation ended): it is reported up
        # to the last line its log holds, marked incomplete, and never counted as held.
        incomplete = end is None
        if incomplete:
            end = int(lc[-1][0]) + 1 if lc else t0
        on = next((int(x.split()[0]) for x in lines if x.endswith(f"{label} pause on")), None)
        off = next((int(x.split()[0]) for x in lines if x.endswith(f"{label} pause off")), None)
        win = [x for x in lc if t0 - 1 <= x[0] <= end + 2]
        mark = lambda what: next((t for t, _, tag, msg in win if msg == f"CHAIN pause {what} {label}"), None)
        exact = mark("on") is not None
        hold_on, hold_off = on, off
        if exact:
            on, off = mark("on"), mark("off")
        st = [x for x in win if x[2] == "OpenWeightsBench" and x[3].startswith("START")]
        if len(st) != 1:
            print(f"{label}: {len(st)} START lines, left out")
            continue
        pid = st[0][1]
        mine = [x for x in win if x[1] == pid]
        prog = [(t, fields(msg)) for t, _, tag, msg in mine if tag == "OpenWeightsBench" and msg.startswith("PROGRESS")]
        g = [(t, msg) for t, _, tag, msg in mine if tag == "OpenWeights" and msg.startswith("threads:") and "workers" not in msg]
        fell = [t for t, msg in g if "decode steps stalled" in msg]
        tries = [(t, "early" if "sped up" in msg else "scheduled") for t, msg in g if "trying" in msg]
        failed = [(t, int(re.search(r"\((\d+) ms\)", msg).group(1))) for t, msg in g if "the try at" in msg and "stalled" in msg]
        dropped = [t for t, msg in g if "was dropped" in msg]
        back = [t for t, msg in g if "back to" in msg]
        last = prog[-1][1] if prog else {}
        gen = int(last.get("generated", 0))
        dec = int(last["decodeMs"]) / 1000 if last.get("decodeMs") not in (None, "null") else None
        # The placement samples carry whole seconds; with the ms marks the pause's own times bound them.
        h_on, h_off = (on, off) if exact else (hold_on, hold_off)
        hold = held(Path(results) / f"place-{label}.txt", h_on, h_off if h_off else end) if h_on else held(Path("/nonexistent"), 0, 0)
        if incomplete:
            hold["ok"] = False
        rows.append(dict(label=label, arm=arm, mode=mode, on=on, off=off, exact=exact, fell=fell, tries=tries, failed=failed, back=back,
                         dropped=dropped, incomplete=incomplete,
                         gen=gen, rate=(gen - 1) / dec if dec and gen > 1 else None, hold=hold,
                         watchdog=any(f"watchdog stopped {label}" in x for x in lines)))
    for r in rows:
        on, off = r["on"], r["off"]
        rel = lambda t, base: f"{t - base:+.1f}" if base else "n/a"
        h = r["hold"]
        print(f"{r['label']:9} {r['arm']}{r['mode']} tokens {r['gen']} rate {r['rate'] and round(r['rate'], 2)} "
              f"{'pause times to the ms' if r['exact'] else 'pause times to the second'}; "
              f"held {'yes' if h['ok'] else 'NO'} ({h['n']} samples, {h['busy']} busy, gap {h['gap']} s, first {h['first']} s, last {h['last']} s before off, {h['bad']} bad); fell {[rel(t, on) for t in r['fell']]} after on; "
              f"tries {[(rel(t, off or on), k) for t, k in r['tries']]} after {'off' if off else 'on'}; "
              f"failed {[ms for _, ms in r['failed']]} ms; dropped {len(r['dropped'])}; back {[rel(t, off or on) for t in r['back']]} after {'off' if off else 'on'}"
              f"{' WATCHDOG' if r['watchdog'] else ''}{' INCOMPLETE: cut short, its log as last pulled' if r['incomplete'] else ''}")
    print()
    for arm, mode in (("W", "E"), ("W", "T"), ("M", "E"), ("M", "T"), ("N", "E")):
        rs = [r for r in rows if r["arm"] == arm and r["mode"] == mode and (arm == "N" or r["hold"]["ok"])]
        if not rs:
            continue
        if arm == "W":
            lag = [r["back"][0] - r["off"] for r in rs if r["back"] and r["back"][0] > r["off"]]
            print(f"W{mode}: {len(rs)} runs; return after the pause went off {[round(x, 1) for x in lag]} s; "
                  f"failed tries {[len(r['failed']) for r in rs]}; rate {[round(r['rate'], 2) for r in rs if r['rate']]}")
        elif arm == "M":
            print(f"M{mode}: {len(rs)} runs; tries {[len(r['tries']) for r in rs]} (early {[sum(k == 'early' for _, k in r['tries']) for r in rs]}); "
                  f"failed-try steps {[r['failed'] and statistics.median(ms for _, ms in r['failed']) for r in rs]} ms median; "
                  f"rate {[round(r['rate'], 2) for r in rs if r['rate']]}")
        else:
            print(f"N{mode}: {len(rs)} runs; falls {[len(r['fell']) for r in rs]}; tries {[len(r['tries']) for r in rs]}; "
                  f"rate {[round(r['rate'], 2) for r in rs if r['rate']]}")


if __name__ == "__main__":
    main(*sys.argv[1:])
