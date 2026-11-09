#!/usr/bin/env python3
"""r33 and r37: a chat session paced like a person, against the phone's heat and throttles.

  python3 analyze_session.py qdc  chain33.log logcat-r33.txt trig-r31.txt [A|B]
  python3 analyze_session.py poco chain37.log logcat-r37.txt bat37.txt

SessionBenchmark logs a SESSION line when it starts and a TURN line per reply (`prompt` is the new
tokens read, `prefill` and `decode` the engine's rates, `ttft` the first token's wait). The session
is the process that logged the SESSION line inside the chain's window; the engine's guard lines are
that process's "decode steps" lines. The QDC logger gives the XO thermistor, the hottest CPU zone,
the clusters' frequency ceilings and the pause switches about once a second; the POCO's logger gives
the thermal service's CPU and skin temperatures and status about every 12 s, and the gauge.
"""
import re
import statistics
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import analyze_app as A  # noqa: E402


def window(chain, which):
    a = b = None
    for line in open(chain):
        m = re.match(r"(\d+) (?:session|run) (r3\d-\S+)", line)
        if m and (which is None or m.group(2).endswith(which)):
            if " end " in line:
                b = int(m.group(1))
            else:
                a = int(m.group(1))
    return a, b


def turns(cat, a, b):
    lines = A.logcat(cat)
    pids = [p for t, p, tag, m in lines if a - 2 <= t <= b + 2 and m.startswith("SESSION threads")]
    assert len(pids) == 1, pids
    pid = pids[0]
    mine = [(t, tag, m) for t, p, tag, m in lines if p == pid]
    ts = [(t, A.fields(m)) for t, tag, m in mine if m.startswith("TURN")]
    guard = [(t, m) for t, tag, m in mine if tag == "OpenWeights" and "decode steps" in m]
    return ts, guard


def qdc(a, b, trig):
    rows = []
    for line in open(trig):
        p = line.split()
        if p and p[0].isdigit() and a <= int(p[0]) <= b:
            rows.append(p)
    xo = max(int(r[1]) for r in rows) / 1000
    cpu = max(int(r[3]) for r in rows) / 1000
    paused = sum(1 for r in rows if int(r[9]) + int(r[10]) + int(r[11]) > 0)
    full = rows[0][14:16]
    capped = sum(1 for r in rows if r[14] != "3628800" or r[15] != "4608000")
    return (f"XO max {xo:.1f} C, hottest CPU zone max {cpu:.1f} C; {paused} of {len(rows)} samples with a pause "
            f"switch on; {capped} ({capped / len(rows):.0%}) with a CPU ceiling below its top (first sample {full})")


def poco(a, b, bat):
    rows = [(float(line.split()[1]), A.fields(line)) for line in open(bat) if line.startswith("T ")]
    rows = [f for t, f in rows if a <= t <= b]
    return (f"CPU max {max(float(f['CPU']) for f in rows):.1f} C, skin max {max(float(f['SKIN']) for f in rows):.1f} C; "
            f"thermal status {sorted({f['status'] for f in rows})}; level {rows[0]['level']} to {rows[-1]['level']}; "
            f"USB {sorted({f['usb'] for f in rows})}; screen {sorted({f['screen'] for f in rows})}; online {sorted({f['online'] for f in rows})}")


def main(kind, chain, cat, log, which=None):
    a, b = window(chain, which)
    ts, guard = turns(cat, a, b)
    print(f"{len(ts)} turns in {(b - a) / 60:.1f} min; " + (qdc(a, b, log) if kind == "qdc" else poco(a, b, log)))
    print(f"guard lines: {len(guard)}" + "".join(f"\n  {t - a:.0f} s: {m}" for t, m in guard))
    print("| turn | conversation | new prompt tokens | prompt tok/s | first token s | tokens | decode tok/s |")
    print("|---|---|---|---|---|---|---|")
    for t, f in ts:
        print(f"| {f['n']} | {f['conv']} | {f['prompt']} | {f['prefill']} | {int(f['ttft']) / 1000:.1f} | {f['generated']} | {f['decode']} |")
    dec = [float(f["decode"]) for t, f in ts]
    ttft = [int(f["ttft"]) / 1000 for t, f in ts]
    firsts, lasts, conv = [], [], None
    for t, f in ts:
        if f["conv"] != conv:
            firsts.append(float(f["decode"]))
            conv = f["conv"]
    for i, (t, f) in enumerate(ts):
        if i + 1 == len(ts) or ts[i + 1][1]["conv"] != f["conv"]:
            lasts.append(float(f["decode"]))
    print(f"\ndecode tok/s median {statistics.median(dec):.1f} ({min(dec)} to {max(dec)}); first turn of each conversation "
          f"{firsts}, last {lasts}; first token median {statistics.median(ttft):.1f} s, max {max(ttft):.1f} s, "
          f"{sum(1 for x in ttft if x > 10)} turns over 10 s")


if __name__ == "__main__":
    main(*sys.argv[1:6])
