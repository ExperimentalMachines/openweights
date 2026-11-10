#!/usr/bin/env python3
"""r36: energy per token on a phone with a working battery gauge, and a paced session's drain.

  python3 analyze_energy.py chain36.log bat36.txt logcat-r36.txt

The QDC phones bypass their battery, so the study's energy runs on the owner's POCO (MT6991), unplugged.
bat36.txt holds the gauge's current about once a second ("B <epoch> <uA>", discharge positive) and, every
tenth sample, the voltage, charge counter and temperatures ("T <epoch> key=value ..."). Each arm of the
chain idles 60 s before it starts; the median current of that idle (its last 50 s) is the arm's
baseline, so what is reported is the energy the model added over the phone sitting there with its
screen on. A window's energy is the trapezoid over the gauge's samples of each sample's current times
the voltage logged nearest it; prompt reading runs from the START line to START + prefillMs; decode
runs from the first token (START + the BENCH line's ttft) for decodeMs, the time to the last token. A
progress line's elapsedMs is when the line was logged, after its token, so it is not used as an end. Per token: prompt joules over the prompt tokens read, decode joules over the tokens after the
first, since decodeMs times those. The gauge averages over about a second, so a window's edges blur by
that much; prompt reading here takes tens of seconds, decode about a minute.
"""
import bisect
import re
import statistics
import sys
from collections import defaultdict


def fields(m):
    return dict(re.findall(r"(\w+)=([^\s,]+)", m))


def gauge(path):
    cur, temps = [], []
    for line in open(path):
        p = line.split()
        if len(p) >= 3 and p[0] == "B" and re.fullmatch(r"-?\d+", p[2]):
            cur.append((float(p[1]), int(p[2]) / 1e6))  # amperes
        elif p and p[0] == "T":
            temps.append((float(p[1]), fields(line)))
    return cur, temps


def volts(temps, t):
    i = bisect.bisect_left([x[0] for x in temps], t)
    near = min((temps[j] for j in (i - 1, i) if 0 <= j < len(temps)), key=lambda x: abs(x[0] - t))
    return int(near[1]["volt"]) / 1000


def energy(cur, temps, a, b, base=0.0):
    """Joules drawn between a and b above a baseline current, by the trapezoid over the samples in the
    window, each sample's current (less the baseline) times the voltage logged nearest it, scaled from
    the samples' span to the window's."""
    pts = [(t, (i - base) * volts(temps, t)) for t, i in cur if a <= t <= b]
    if len(pts) < 2:
        return None
    return sum((t1 - t0) * (p0 + p1) / 2 for (t0, p0), (t1, p1) in zip(pts, pts[1:])) * (b - a) / (pts[-1][0] - pts[0][0])


def logcat(path):
    out = []
    for line in open(path):
        m = re.match(r"\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+\w\s+(\S+?)\s*:\s(.*)", line)
        if m:
            out.append((float(m.group(1)), m.group(2), m.group(3), m.group(4)))
    return out


def main(chain, bat, cat):
    cur, temps = gauge(bat)
    lines = logcat(cat)
    runs = []
    for line in open(chain):
        m = re.match(r"(\d+) run (r36-\S+) threads (\d+)", line)
        if m:
            runs.append({"label": m.group(2), "threads": int(m.group(3)), "start": int(m.group(1))})
        m = re.match(r"(\d+) run (r36-\S+) end rc (\d+).*result (\d)", line)
        if m:
            r = next(x for x in runs if x["label"] == m.group(2))
            r.update(end=int(m.group(1)), ok=m.group(4) == "1")
    rows = []
    for r in runs:
        if "end" not in r or r["label"] == "r36-S":
            continue
        mine = [x for x in lines if r["start"] - 1 <= x[0] <= r["end"] + 2]
        start = [(t, pid) for t, pid, tag, m in mine if tag == "OpenWeightsBench" and m.startswith("START")]
        if len(start) != 1:
            print(f"{r['label']}: {len(start)} START lines, left out")
            continue
        t0, pid = start[0]
        prog = [fields(m) for t, p, tag, m in mine if p == pid and m.startswith("PROGRESS")]
        last = prog[-1]
        pre_s = int(last["prefillMs"]) / 1000
        dec_s = int(last["decodeMs"]) / 1000
        bench = [m for t, p, tag, m in mine if p == pid and m.startswith("BENCH")]
        ttft = re.search(r"ttft=(\d+)", bench[0]) if bench else None
        if ttft is None:
            print(f"{r['label']}: no BENCH line, left out")
            continue
        dec_start = t0 + int(ttft.group(1)) / 1000
        dec_end = dec_start + dec_s
        n_prompt, n_gen = int(last["promptRead"]), int(last["generated"]) - 1
        base_win = [i for t, i in cur if r["start"] - 55 <= t <= r["start"] - 5]
        base = statistics.median(base_win)
        base_w = base * volts(temps, r["start"] - 30)
        e_pre = energy(cur, temps, t0, t0 + pre_s, base)
        e_dec = energy(cur, temps, dec_start, dec_end, base)
        cpu = next((float(f.get("CPU", "nan")) for t, f in temps if t >= r["start"]), float("nan"))
        rows.append({**r, "base_w": base_w, "pre_s": pre_s, "dec_s": dec_s, "n_prompt": n_prompt, "n_gen": n_gen,
                     "pre_w": e_pre / pre_s + base_w, "dec_w": e_dec / dec_s + base_w, "e_pre": e_pre, "e_dec": e_dec,
                     "pre_mj": e_pre / n_prompt * 1000, "dec_mj": e_dec / n_gen * 1000, "cpu": cpu})
    print("| run | threads | idle W | prompt tokens | prompt s | prompt W | prompt mJ/token | tokens | decode s "
          "| decode tok/s | decode W | decode mJ/token | CPU C at start |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for x in rows:
        print(f"| {x['label']} | {x['threads']} | {x['base_w']:.2f} | {x['n_prompt']} | {x['pre_s']:.1f} | {x['pre_w']:.2f} "
              f"| {x['pre_mj']:.0f} | {x['n_gen']} | {x['dec_s']:.1f} | {x['n_gen'] / x['dec_s']:.2f} | {x['dec_w']:.2f} "
              f"| {x['dec_mj']:.0f} | {x['cpu']:.1f} |")
    by = defaultdict(list)
    for x in rows:
        by[x["threads"]].append(x)
    print()
    for t in sorted(by):
        v = by[t]
        med = lambda k: statistics.median(x[k] for x in v)  # noqa: E731
        print(f"- {t} threads, {len(v)} runs: prompt {med('pre_mj'):.0f} mJ/token at "
              f"{statistics.median(x['n_prompt'] / x['pre_s'] for x in v):.1f} tok/s; decode {med('dec_mj'):.0f} mJ/token at "
              f"{statistics.median(x['n_gen'] / x['dec_s'] for x in v):.2f} tok/s; decode adds {med('dec_w') - med('base_w'):.2f} W "
              f"over idle {med('base_w'):.2f} W")
    session(runs, cur, temps, lines)


def session(runs, cur, temps, lines):
    s = next((r for r in runs if r["label"] == "r36-S" and "end" in r), None)
    if not s:
        return
    a, b = s["start"], s["end"]
    tt = [(t, f) for t, f in temps if a <= t <= b]
    turns = [fields(m) for t, p, tag, m in lines if a <= t <= b and m.startswith("TURN")]
    guard = [m for t, p, tag, m in lines if a <= t <= b and tag == "OpenWeights" and "decode steps" in m]
    e = energy(cur, temps, a, b)
    hours = (b - a) / 3600
    print(f"\nr36-S: {len(turns)} turns in {(b - a) / 60:.1f} min; mean draw {e / (b - a):.2f} W; "
          f"level {tt[0][1]['level']} to {tt[-1][1]['level']} ({(int(tt[0][1]['level']) - int(tt[-1][1]['level'])) / hours:.1f} points an hour); "
          f"counter {(int(tt[0][1]['ctr']) - int(tt[-1][1]['ctr'])) / 1000:.0f} mAh; "
          f"CPU max {max(float(f['CPU']) for t, f in tt):.1f} C, skin max {max(float(f['SKIN']) for t, f in tt):.1f} C; "
          f"thermal status max {max(int(f['status'] or 0) for t, f in tt)}; screen {sorted({f['screen'] for t, f in tt})}; "
          f"guard lines {len(guard)}")
    for x in turns:
        print(f"  TURN {x['n']} {x['conv']} prompt {x['prompt']} prefill {x['prefill']} decode {x['decode']} "
              f"ttft {x['ttft']} generated {x['generated']}")
    for g in guard:
        print("  GUARD", g)


if __name__ == "__main__":
    main(*sys.argv[1:4])
