"""The throttle half of the MediaTek study: what the tablet did to its clocks and cores during each
session, and every CPU decode step that stalled, lined up with what the shell could see at that
moment. The Snapdragon equivalent is tools/snapdragon/analyze_heat.py; there the hard throttle
paused cores, which only root could see, while here a shell sees the online list, each CPU
policy's clock and ceiling, Android's thermal status and the AP and SKIN temperatures
(phone/mtklog.sh, every 2 s, status every 10 s), and since session 6 where each bench thread
last ran (phone/place.sh, every 2 s).

  python3 analyze_heat_mtk.py <session dir> [...]     e.g. results/2026-11-16-mt6989/s5

Per session: samples, how often every CPU ceiling sat at its top, the lowest ceilings seen, the
thermal statuses, the hottest AP reading, and whether any core ever left the online list. Then
each stall burst (CPU decode steps over 500 ms, consecutive ones within 5 s grouped): when, its
steps, the ceilings and status at the time, and the distinct CPUs the bench's threads were last
on in the placement samples inside the burst (threads crowded onto two or fewer would be a
pause the online list does not show; on SM8850 paused cores still read online).
"""
import glob
import json
import os
import sys
from collections import Counter

STALL_MS = 500
TOP = {"p0": 2000000, "p4": 2850000, "p7": 3400000}


def mtklog(path):
    rows = []
    for line in open(path):
        if not line[:1].isdigit():
            continue
        head, _, hal = line.partition("|")
        f = head.split()
        if len(f) < 12:
            continue
        r = {"t": int(f[0]), "online": f[1], "isolated": f[2], "p0": int(f[4]), "p4": int(f[6]), "p7": int(f[8]), "gpu_max": int(f[10])}
        h = hal.split()
        if len(h) >= 2:
            r["status"] = int(h[0]) if h[0].isdigit() else None
            try:
                r["ap"] = float(h[1])
            except ValueError:
                pass
        rows.append(r)
    return rows


def placements(d):
    out = []
    for p in glob.glob(os.path.join(d, "place-*.txt")):
        for line in open(p):
            f = line.split()
            if len(f) > 1 and f[0].isdigit():
                out.append((int(f[0]), [int(x.split(":")[1]) for x in f[1:] if ":" in x and x.split(":")[1].isdigit()]))
    return sorted(out)


def steps(d):
    """Every CPU decode step with its end time in epoch seconds, from the turn rows."""
    out = []
    for p in sorted(glob.glob(os.path.join(d, "results-*.jsonl"))):
        for line in open(p):
            if '"kind":"turn"' not in line:
                continue
            r = json.loads(line)
            if r.get("decode") != "cpu" or r.get("error"):
                continue
            t = r["wall_ms"] / 1000 + (r["prefill_ms"] + r.get("xfer_get_ms", 0) + r.get("xfer_set_ms", 0)) / 1000
            for i, s in enumerate(r["step_ms"]):
                t += s / 1000
                out.append((t, s, r["label"], f"{r['prefill']}:{r['decode']}", r["model"].split("/")[-1].replace(".gguf", ""), r["conv"], r["turn"], i))
    return out


def ghz(x):
    return f"{x / 1e6:.2f}".rstrip("0").rstrip(".")


def main():
    for d in sys.argv[1:]:
        name = os.path.basename(d.rstrip("/"))
        logs = sorted(glob.glob(os.path.join(d, "mtklog-*.txt")))
        L = [r for p in logs for r in mtklog(p)]
        print(f"\n## {name}\n")
        if L:
            top = sum(1 for r in L if all(r[k] >= TOP[k] for k in TOP))
            st = Counter(r["status"] for r in L if r.get("status") is not None)
            aps = [r["ap"] for r in L if "ap" in r]
            offline = sorted({r["online"] for r in L if r["online"] != "0-7"})
            isolated = sorted({r["isolated"] for r in L if r["isolated"] not in ("-", "")})
            low = min(L, key=lambda r: (r["p4"], r["p7"], r["p0"]))
            print(f"- {len(L):,} samples ({', '.join(os.path.basename(p) for p in logs)}); every CPU ceiling at its top in {100 * top / len(L):.1f}%")
            print(f"- lowest ceilings: {ghz(low['p0'])} / {ghz(low['p4'])} / {ghz(low['p7'])} GHz; GPU ceiling as low as {min(r['gpu_max'] for r in L) / 1000:.0f} MHz")
            print(f"- thermal status samples: {dict(sorted(st.items()))}; AP max {max(aps) if aps else '?'} C")
            print(f"- cores: {'all 8 online in every sample' if not offline else 'online lists seen: ' + ', '.join(offline)}; isolated: {', '.join(isolated) or 'none'}")
        S = steps(d)
        cpu_steps = len(S)
        stalls = [s for s in S if s[1] > STALL_MS]
        print(f"- CPU decode steps: {cpu_steps:,}; over {STALL_MS} ms: {len(stalls)}")
        bursts = []
        for s in sorted(stalls):
            if bursts and s[0] - bursts[-1][-1][0] < 5:
                bursts[-1].append(s)
            else:
                bursts.append([s])
        P = placements(d)
        for b in bursts:
            t0, t1 = b[0][0] - b[0][1] / 1000, b[-1][0]
            near = [r for r in L if t0 - 3 <= r["t"] <= t1 + 3]
            ceil = sorted({f"{ghz(r['p0'])}/{ghz(r['p4'])}/{ghz(r['p7'])}" for r in near})
            sts = sorted({r["status"] for r in near if r.get("status") is not None})
            onl = sorted({r["online"] for r in near})
            inside = [cpus for t, cpus in P if t0 - 1 <= t <= t1 + 1]
            where = sorted({c for cpus in inside for c in cpus})
            _, _, lab, cell, model, conv, turn, _ = b[0]
            print(
                f"  - {lab} {model} {cell} {conv} t{turn}: {len(b)} steps {min(x[1] for x in b) / 1000:.2f}-{max(x[1] for x in b) / 1000:.2f} s over {t1 - t0:.1f} s;"
                f" ceilings {', '.join(ceil) or '?'} GHz; status {sts or '?'}; online {', '.join(onl) or '?'};"
                f" threads last on CPUs {where if inside else 'no placement sample'}"
            )


if __name__ == "__main__":
    main()
