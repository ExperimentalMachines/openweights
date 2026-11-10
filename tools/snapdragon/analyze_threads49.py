#!/usr/bin/env python3
"""r49: the app's engine at 1 to 8 threads, normally and with six cores out of use, and where six threads ran.

  python3 analyze_threads49.py table chain49.log logcat-r49.txt [results-dir]
  python3 analyze_threads49.py trace trace-r49-T-P6.txt tasks-r49-T-P6.txt [from_ms to_ms]

table: each run is the process whose START line falls inside the chain's start and end lines for it. Its
PROGRESS lines give promptRead and prefillMs (the prompt's reading time) and, during decode, generated and
decodeMs; the engine's BENCH line, when the run finished, gives its own rates, and the rates here are checked
against it. Prefill tok/s is promptRead over prefillMs. Decode tok/s is the tokens after the first over the
decode milliseconds, which is how BENCH counts it; a run its watchdog stopped gives the rate up to its last
progress line. A paused run counts only if every place-<label>.txt sample with its threads busy had them on
CPUs 0 and 1 (the pause held); the medians are over the runs that count.

trace: the kernel's sched_switch events for 2 s of decode. The engine's threads are the ones named
openweights-inf in the run's thread list. For each, its time on each CPU; for each CPU, its time running an
engine thread, another task, or idle; and, between from_ms and to_ms of the trace, every stretch a CPU ran an
engine thread, for the figure.
"""
import json
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


def runs(chain):
    starts, ends = {}, {}
    for line in open(chain):
        m = re.match(r"(\d+) run (r49-\S+) threads (\d+) state ([NP])", line)
        if m:
            starts[m.group(2)] = (int(m.group(1)), int(m.group(3)), m.group(4))
        m = re.match(r"(\d+) run (r49-\S+) end rc (\S+) .* switches at end (\d+) result (\d)", line)
        if m:
            ends[m.group(2)] = (int(m.group(1)), m.group(3), m.group(4), m.group(5))
    watchdog = {m.group(1) for m in (re.search(r"watchdog stopped (r49-\S+)", ln) for ln in open(chain)) if m}
    return [(lab, *starts[lab], *ends[lab], lab in watchdog) for lab in starts if lab in ends]


def held(place, paused):
    """Whether the pause held: in every sample after the first, threads that used CPU time were on CPUs 0 and 1."""
    if not Path(place).exists():
        return None
    prev, bad, seen = {}, 0, 0
    for line in open(place):
        p = line.split()
        if len(p) < 3 or not p[0].isdigit():
            continue
        cur = {}
        for f in p[3:]:
            tid, cpu, ticks = f.split(":")
            cur[tid] = (int(cpu), int(ticks))
        busy = [cpu for tid, (cpu, ticks) in cur.items() if tid in prev and ticks > prev[tid][1]]
        if busy:
            seen += 1
            if paused and any(c > 1 for c in busy):
                bad += 1
        prev = cur
    return seen, bad


def table(chain, catp, d="."):
    cat = logcat(catp)
    rows = []
    for lab, t0, threads, state, t1, rc, sw_end, ok, dog in runs(chain):
        win = [x for x in cat if t0 - 1 <= x[0] <= t1 + 2]
        st = [x for x in win if x[2] == "OpenWeightsBench" and x[3].startswith("START")]
        if len(st) != 1:
            print(f"{lab}: {len(st)} START lines, skipped")
            continue
        pid = st[0][1]
        prog = [fields(m) for t, p, tag, m in win if p == pid and m.startswith("PROGRESS")]
        bench = [fields(m.replace(" tok/s", "")) for t, p, tag, m in win if p == pid and m.startswith("BENCH")]
        read = next((f for f in prog if f.get("prefillMs", "null") != "null"), None)
        dec = [f for f in prog if f.get("decodeMs", "null") != "null"]
        prefill = int(read["promptRead"]) / int(read["prefillMs"]) * 1000 if read else None
        decode = (int(dec[-1]["generated"]) - 1) / int(dec[-1]["decodeMs"]) * 1000 if dec and int(dec[-1]["decodeMs"]) > 0 else None
        h = held(Path(d) / f"place-{lab}.txt", state == "P")
        rows.append(dict(label=lab, threads=threads, state=state, prefill=prefill, decode=decode,
                         generated=int(dec[-1]["generated"]) if dec else (1 if read else 0),
                         bench=bench[0] if bench else None, watchdog=dog, switchesAtEnd=sw_end, held=h))
    for r in rows:
        b = r["bench"]
        chk = ""
        if b:
            chk = f" BENCH prefill {b['prefill']} decode {b['decode']}"
        print(f"{r['label']:12} t{r['threads']} {r['state']} prefill {r['prefill'] and round(r['prefill'], 2)} decode {r['decode'] and round(r['decode'], 3)} "
              f"tokens {r['generated']}{' WATCHDOG' if r['watchdog'] else ''} switches-at-end {r['switchesAtEnd']} place {r['held']}{chk}")
    cells = {}
    for r in rows:
        if not r["label"].startswith("r49-T"):
            ok = r["state"] == "N" or (r["held"] and r["held"][1] == 0 and r["held"][0] > 0)
            if ok:
                cells.setdefault((r["threads"], r["state"]), []).append(r)
    print("\n| threads | decode N | decode P | kept | prefill N | prefill P | kept | runs N/P |")
    print("|---|---|---|---|---|---|---|---|")
    out = []
    for t in range(1, 9):
        n, p = cells.get((t, "N"), []), cells.get((t, "P"), [])
        med = lambda xs, k: statistics.median([x[k] for x in xs if x[k] is not None]) if any(x[k] is not None for x in xs) else None
        dn, dp, pn, pp = med(n, "decode"), med(p, "decode"), med(n, "prefill"), med(p, "prefill")
        fmt = lambda x, d=2: "n/a" if x is None else f"{x:.{d}f}"
        kept = lambda a, b: "n/a" if a is None or b is None else f"{100 * b / a:.1f}%"
        print(f"| {t} | {fmt(dn)} | {fmt(dp, 3)} | {kept(dn, dp)} | {fmt(pn)} | {fmt(pp, 3)} | {kept(pn, pp)} | {len(n)}/{len(p)} |")
        out.append(dict(threads=t, decodeNormal=dn, decodePaused=dp, prefillNormal=pn, prefillPaused=pp, runsNormal=len(n), runsPaused=len(p),
                        decodeRanges=[[round(x["decode"], 3) for x in n if x["decode"]], [round(x["decode"], 3) for x in p if x["decode"]]],
                        prefillRanges=[[round(x["prefill"], 2) for x in n if x["prefill"]], [round(x["prefill"], 3) for x in p if x["prefill"]]]))
    print(json.dumps(out))


def trace(tracep, tasksp, a=None, b=None):
    eng = {line.split()[0] for line in open(tasksp) if "openweights-inf" in line}
    ev = []
    for line in open(tracep):
        m = re.search(r"\[(\d+)\].*?\s(\d+\.\d+): sched_switch: prev_comm=.* prev_pid=(\d+) .*==> next_comm=.* next_pid=(\d+)", line)
        if m:
            ev.append((float(m.group(2)), int(m.group(1)), m.group(3), m.group(4)))
    ev.sort()
    t_start, t_end = ev[0][0], ev[-1][0]
    span = t_end - t_start
    cur, since = {}, {}
    busy = {}  # (cpu, tid) -> seconds
    stretches = []  # (cpu, tid, from, to) for engine threads
    every = []  # (cpu, tid, from, to) for every task but idle, for the window drawn in the figure

    def account(cpu, tid, fr, to):
        busy[(cpu, tid)] = busy.get((cpu, tid), 0) + to - fr
        if tid in eng:
            stretches.append((cpu, tid, fr - t_start, to - t_start))
        if tid != "0":
            every.append((cpu, tid, fr - t_start, to - t_start))

    for t, cpu, prev, nxt in ev:
        # Before a CPU's first switch in the window it was running that switch's previous task.
        account(cpu, cur.get(cpu, prev), since.get(cpu, t_start), t)
        cur[cpu], since[cpu] = nxt, t
    for cpu in cur:
        account(cpu, cur[cpu], since[cpu], t_end)
    names = {tid: f"T{i + 1}" for i, tid in enumerate(sorted(eng, key=int))}
    print(f"{len(ev)} switches over {span * 1000:.0f} ms; engine threads {[names[t] + '=' + t for t in sorted(eng, key=int)]}")
    for tid in sorted(eng, key=int):
        per = {cpu: s for (cpu, x), s in busy.items() if x == tid}
        tot = sum(per.values())
        n = sum(1 for s in stretches if s[1] == tid)
        print(f"  {names[tid]} ran {tot / span * 100:.1f}% of the window, in {n} stretches, on CPUs {sorted(per)}: "
              + ", ".join(f"{c}:{s / span * 100:.1f}%" for c, s in sorted(per.items())))
    for cpu in range(8):
        e = sum(s for (c, x), s in busy.items() if c == cpu and x in eng)
        idle = sum(s for (c, x), s in busy.items() if c == cpu and x == "0")
        other = sum(s for (c, x), s in busy.items() if c == cpu) - e - idle
        # A core taken out of use logs no switch at all, which would otherwise print as 0% everything.
        if not any(c == cpu for (c, _x) in busy):
            print(f"  CPU {cpu}: no events in the window (out of use)")
            continue
        print(f"  CPU {cpu}: engine {e / span * 100:.1f}%, other {other / span * 100:.1f}%, idle {idle / span * 100:.1f}%")
    lens = sorted(to - fr for c, tid, fr, to in stretches)
    if lens:
        print(f"  engine stretches: {len(lens)}, median {statistics.median(lens) * 1000:.3f} ms, longest {lens[-1] * 1000:.1f} ms")
    # How an engine thread left its core: still runnable (R, R+: the scheduler took the core) or blocked (S, D: the
    # thread gave it up), and how often one was woken. A thread that waited by sleeping would show S and wake-ups.
    left, woken = {}, 0
    for line in open(tracep):
        m = re.search(r"sched_switch: prev_comm=.* prev_pid=(\d+) .*prev_state=(\S+) ==>", line)
        if m and m.group(1) in eng:
            left[m.group(2)] = left.get(m.group(2), 0) + 1
        m = re.search(r"sched_wakeup: comm=.* pid=(\d+) ", line)
        if m and m.group(1) in eng:
            woken += 1
    print(f"  engine threads left their core {sum(left.values())} times, by state {left}; woken {woken} times")
    if a is not None:
        a, b = float(a) / 1000, float(b) / 1000
        sel = [(c, names.get(tid, "other"), round((max(fr, a) - a) * 1000, 3), round((min(to, b) - a) * 1000, 3)) for c, tid, fr, to in every if to > a and fr < b]
        print(json.dumps(sel))


if __name__ == "__main__":
    {"table": table, "trace": trace}[sys.argv[1]](*sys.argv[2:])
