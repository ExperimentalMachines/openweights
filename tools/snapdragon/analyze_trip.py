#!/usr/bin/env python3
"""r47 and r48: the phone heated until the hard throttle switched on by itself, then the same short decode inside it.

  python3 analyze_trip.py chain47.log trig47.txt logcat-r47.txt
  python3 analyze_trip.py chain48.log trig48.txt logcat-r48.txt

The chain logs when each heater started, the moment any pause device first read non-zero ("heat off: pause
on"), and each ThreadCountBenchmark run's start and end. The logger writes, about once a second, the time,
the XO thermistor and the hottest CPU zone in milli-C, then the eleven pause devices (r48's adds the CPUs
online and each cluster's current and maximum frequency after them). The benchmark logs a PROGRESS line
about every quarter second with tokens generated and decode milliseconds, the engine's guard a "decode
steps stalled" line when it falls back, and BENCH at the end. The logger's first sample with a device on
and its first sample after with all off are taken as the pause's edges; both are a second or two from the
real switch, so rates inside the pause are estimates bounded by samples.
"""
import re
import sys

DEVICES = 11


def trig(path):
    names, rows = [], []
    for line in open(path):
        p = line.split()
        if line.startswith("time xo cpu"):
            names = p[3:3 + DEVICES]
        elif p and p[0].isdigit():
            rows.append((int(p[0]), int(p[1]), int(p[2]), p[3:3 + DEVICES], p[3 + DEVICES:]))
    return names, rows


def logcat(path):
    out = []
    for line in open(path):
        m = re.match(r"\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+\w\s+(\S+?):\s(.*)", line)
        if m:
            out.append((float(m.group(1)), int(m.group(2)), m.group(3), m.group(4)))
    return out


def main(chain, trigp, catp):
    names, rows = trig(trigp)
    lines = open(chain).read().splitlines()
    heat = [ln for ln in lines if " heat" in ln and " on" in ln.split(":")[0]]
    for h in heat:
        print("heater:", h)
    trip = next(int(ln.split()[0]) for ln in lines if "heat off: pause on" in ln)
    start = int(next(ln for ln in lines if "heat on:" in ln).split()[0])
    on = [r for r in rows if r[0] >= start and any(x != "0" for x in r[3])]
    first = on[0]
    off = next(r for r in rows if r[0] > first[0] and all(x == "0" for x in r[3]))
    print(f"heating {trip - start} s ({(trip - start) / 60:.1f} min) from XO {next(r for r in rows if r[0] >= start)[1] / 1000:.1f} C")
    print(f"pause first seen {first[0]} at XO {first[1] / 1000:.3f} C: {[n for n, x in zip(names, first[3]) if x != '0']}")
    print(f"pause last seen on {max(r[0] for r in on if r[0] < off[0])}, first seen off {off[0]} at XO {off[1] / 1000:.3f} C: "
          f"about {off[0] - first[0]} s; {len([r for r in on if r[0] < off[0]])} samples on")
    pre = [r for r in rows if start <= r[0] < first[0]]
    print(f"highest XO while heating, before the first sample with a device on {max(r[1] for r in pre) / 1000:.3f} C")
    if first[4]:
        during = sorted({" ".join(r[4]) for r in rows if first[0] <= r[0] < off[0]})
        print(f"CPUs online and clusters cur/max MHz during the pause: {during}")
    cat = logcat(catp)
    runs = [(int(ln.split()[0]), ln.split()[2], ln.split()[4]) for ln in lines if re.match(r"\d+ run r4\d-\S+ guard", ln)]
    for t0, label, guard in runs:
        # The run's process is the one that logged its guard setting within seconds of the chain's start line,
        # with the value the chain asked for; its records are bounded by the chain's end line for the run.
        t1 = next((int(ln.split()[0]) for ln in lines if re.match(rf"\d+ run {re.escape(label)} end ", ln)), t0 + 400)
        pids = [p for t, p, tag, m in cat if t0 - 2 <= t <= t0 + 15 and m == f"stallGuard={guard}"]
        if not pids:
            continue
        pid = pids[0]
        mine = [(t, tag, m) for t, p, tag, m in cat if p == pid and t0 - 2 <= t <= t1 + 2]
        prog = []
        for t, tag, m in mine:
            if m.startswith("PROGRESS"):
                f = dict(kv.split("=") for kv in m.split()[1:])
                prog.append((t, f))
        bench = [m for t, tag, m in mine if m.startswith("BENCH")]
        guard_lines = [(t - trip, m) for t, tag, m in mine if "decode steps" in m]
        prefill = next((f["prefillMs"] for t, f in prog if f["prefillMs"] != "null"), None)
        print(f"\n{label} guard {guard} started {t0 - trip:+d} s from the trip; prompt read in {prefill} ms; {bench[-1] if bench else 'no BENCH'}")
        for t, m in guard_lines:
            print(f"  guard {t:+.1f} s: {m}")
        inside = [(t, int(f["generated"])) for t, f in prog if first[0] <= t < off[0] and f["decodeMs"] != "null"]
        if inside:
            (ta, ga), (tb, gb) = inside[0], inside[-1]
            print(f"  decoding inside the pause: tokens {ga} at {ta - trip:+.1f} s to {gb} at {tb - trip:+.1f} s, "
                  f"{(gb - ga) / (tb - ta):.2f} tok/s")
            fb = [t for t, m in guard_lines]
            if fb:
                after = [(t, g) for t, g in inside if t - trip >= fb[0]]
                if len(after) > 1:
                    print(f"  after the fallback, inside the pause: {(after[-1][1] - after[0][1]) / (after[-1][0] - after[0][0]):.2f} tok/s "
                          f"({after[-1][1] - after[0][1]} tokens in {after[-1][0] - after[0][0]:.1f} s)")
        gaps = [(prog[i][0] - trip, int(prog[i][1]["decodeMs"]) - int(prog[i - 1][1]["decodeMs"]), int(prog[i][1]["generated"]))
                for i in range(1, len(prog)) if prog[i][1]["decodeMs"] != "null" and prog[i - 1][1]["decodeMs"] != "null"]
        slow = [g for g in gaps if g[1] > 1500]
        for t, ms, g in slow:
            print(f"  {ms} ms of decode between progress lines ending at token {g}, {t:+.1f} s from the trip")


if __name__ == "__main__":
    main(*sys.argv[1:4])
