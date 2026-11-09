#!/usr/bin/env python3
"""r27: the app's own engine (ThreadCountBenchmark, LlamaCppEngine) with the CPU pause set by hand.

  python3 analyze_app.py chain27.log logcat-r27.txt trig-r22.txt

Each run in chain27.log (label, arm N<t> or P<t>, start and end) is joined with the test's log
lines (logcat -v epoch, tags OpenWeightsBench and OpenWeights) through its own process: the START
line inside the run's window whose thread count is the run's gives the PID, and only that PID's
lines count. BENCH, when the reply finished, must carry the same thread count. The last PROGRESS
line carries the tokens written, the time since START and the decode time to the latest token; the
rate is tokens after the first over that decode time, kept unrounded until the medians.

The thermal logger's samples inside the run, a second in from each end (the switches are set a
second before a run and cleared after it), must show the two switches chain27.sh writes (found in
chain27.log) on for a P arm and every pause switch off for an N arm, with no two samples more than
3 s apart; a run that fails is listed and left out. The engine's own line "threads: N workers kept
alive across graphs" shows its persistent pool was attached.
"""
import re
import statistics
import sys
from collections import defaultdict

LINE = re.compile(r"^\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+[VDIWEF]\s+(\S+?)\s*:\s(.*)$")
GAP = 3


def runs(path):
    st, out, written = {}, [], set()
    text = open(path).read().splitlines()
    for line in text:
        m = re.search(r"switches (/sys/class/thermal/\S+) (/sys/class/thermal/\S+)", line)
        if m:
            written |= {m.group(1).rsplit("/", 1)[-1], m.group(2).rsplit("/", 1)[-1]}
        m = re.match(r"(\d+) run (\S+) arm (\S+) threads (\d+)", line)
        if m:
            st[m.group(2)] = (int(m.group(1)), m.group(3), int(m.group(4)))
        m = re.match(r"(\d+) run (\S+) end rc (\d+)", line)
        if m and m.group(2) in st:
            a, arm, t = st[m.group(2)]
            out.append({"label": m.group(2), "arm": arm, "threads": t, "start": a, "end": int(m.group(1)),
                        "rc": int(m.group(3))})
    stopped = {re.match(r"\d+ watchdog stopped (\S+)", l).group(1) for l in text if "watchdog stopped" in l}
    for r in out:
        r["watchdog"] = r["label"] in stopped
    return out, written


def logcat(path):
    out = []
    for line in open(path, errors="replace"):
        m = LINE.match(line)
        if m:
            out.append((float(m.group(1)), m.group(2), m.group(3), m.group(4)))
    return sorted(set(out))


def pauses(path):
    head, out = None, []
    for line in open(path):
        p = line.split()
        if not p or p[0].startswith("#"):
            continue
        if p[0] == "time":
            head = p[1:]
            continue
        if head and p[0].isdigit() and len(p) == len(head) + 1:
            sw = {h.split("@")[1]: x != "0" for h, x in zip(head, p[1:]) if h.startswith("thermal-pause")}
            out.append((int(p[0]), sw))
    return out


def fields(msg):
    return dict(kv.split("=", 1) for kv in msg.split() if "=" in kv)


def main(chain, cat, trig):
    rs, written = runs(chain)
    lines, ps = logcat(cat), pauses(trig)
    rows, left = [], []
    for r in rs:
        window = [x for x in lines if r["start"] - 1 <= x[0] <= r["end"] + 2]
        starts = [(t, pid) for t, pid, tag, m in window
                  if tag == "OpenWeightsBench" and m.startswith("START") and fields(m).get("threads") == str(r["threads"])]
        if len(starts) != 1:
            left.append(f"{r['label']}: {len(starts)} START lines for {r['threads']} threads")
            continue
        pid = starts[0][1]
        mine = [(t, tag, m) for t, p, tag, m in window if p == pid]
        bench = [fields(m) for _, tag, m in mine if tag == "OpenWeightsBench" and m.startswith("BENCH")]
        bench = [b for b in bench if b.get("threads") == str(r["threads"])]
        prog = [fields(m) for _, tag, m in mine if tag == "OpenWeightsBench" and m.startswith("PROGRESS")]
        pool = [m for _, tag, m in mine if tag == "OpenWeights" and "kept alive across graphs" in m]
        sam = [(t, sw) for t, sw in ps if r["start"] + 1 <= t <= r["end"] - 1]
        gaps = [b[0] - a[0] for a, b in zip(sam, sam[1:])]
        if r["arm"][0] == "P":
            ok = all(sw.get(d) for _, sw in sam for d in written)
        else:
            ok = all(not any(sw.values()) for _, sw in sam)
        if not sam or not ok or max(gaps, default=0) > GAP:
            left.append(f"{r['label']}: pause state not the arm's in {len(sam)} samples, largest gap {max(gaps, default=0)} s")
            continue
        last = prog[-1] if prog else {}
        rate = None
        if last.get("decodeMs") not in (None, "null") and int(last.get("generated", 0)) > 1:
            rate = (int(last["generated"]) - 1) / (int(last["decodeMs"]) / 1000)
        rows.append({**r, "bench": bench[-1] if bench else None, "last": last, "rate": rate,
                     "pool": pool[-1] if pool else ""})
    print(f"Runs: {len(rs)}; written switches {sorted(written)}; left out: {left or 'none'}.")
    print()
    print("| run | arm | finished | decode tok/s, engine | decode tok/s, last progress | prefill tok/s | first token ms | "
          "tokens at last progress | s since START at it | pool |")
    print("|---|---|---|---|---|---|---|---|---|---|")
    for r in rows:
        b, p = r["bench"], r["last"]
        el = f"{int(p['elapsedMs']) / 1000:.0f}" if p.get("elapsedMs") else ""
        done = "yes" if b else ("watchdog" if r["watchdog"] else "no")
        rate = "" if r["rate"] is None else f"{r['rate']:.3f}"
        print(f"| {r['label']} | {r['arm']} | {done} | {b['decode'] if b else ''} | {rate} | "
              f"{b['prefill'] if b else ''} | {b['ttft'] if b else ''} | {p.get('generated', '')} | {el} | "
              f"{r['pool'].replace('threads: ', '')} |")
    print()
    by = defaultdict(list)
    for r in rows:
        if r["rate"] is not None:
            by[r["arm"]].append(r)
    for arm in sorted(by, key=lambda a: (int(a[1:]), a[0])):
        v = by[arm]
        rates = [x["rate"] for x in v]
        ttft = [int(x["bench"]["ttft"]) for x in v if x["bench"]]
        each = ", ".join(f"{x['rate']:.3f}" + ("" if x["bench"] else " stopped") for x in v)
        first = f"; first token median {statistics.median(ttft):.0f} ms" if ttft else ""
        print(f"- {arm}: decode {statistics.median(rates):.4f} tok/s median by the last progress, {len(v)} runs ({each}){first}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
