"""The heat probes (phone/hot.sh): the same three turns of one conversation, decoded on the CPU with
6 and with 2 threads, first behind the cooling gate and then straight after 8 shell busy loops and
8 dd loops heated the unit. The MediaTek counterpart of the Snapdragon r47 run, where the hard
throttle paused cores 2 to 7 and 6-thread decode fell to 0.24 tok/s.

  python3 analyze_hot_mtk.py <session dir> [...]     e.g. results/2026-11-16-mt6989/s6

Per probe: the gate, Android's thermal status, the AP temperature and the CPU ceilings at the start
of its first turn, prefill and decode rates pooled over its turns (prompt tokens over prompt time,
decode steps over decode time), the median and longest decode step, steps over 500 ms, and the
CPUs the bench's threads were last seen on (place.sh, every 2 s). Then each hot probe against its
cool one on the same turns.
"""
import glob
import json
import os
import statistics
import sys


def probes(d):
    rows = [json.loads(l) for p in glob.glob(os.path.join(d, "results-*hot.jsonl")) for l in open(p) if l.startswith("{")]
    out = {}
    for r in rows:
        tag = r.get("label", "").split("-")[-1]
        p = out.setdefault(tag, {"turns": [], "gate": None, "label": r.get("label")})
        if r.get("kind") == "turn" and not r.get("error"):
            p["turns"].append(r)
        elif r.get("kind") == "cooled":
            p["gate"] = r
    return out


def where(d, label):
    cpus = set()
    for p in glob.glob(os.path.join(d, f"place-{label}.txt")):
        for line in open(p):
            f = line.split()
            cpus |= {int(x.split(":")[1]) for x in f[1:] if ":" in x and x.split(":")[1].isdigit()}
    return sorted(cpus)


def main():
    for d in sys.argv[1:]:
        P = probes(d)
        if not P:
            continue
        print(f"\n## {os.path.basename(d.rstrip('/'))}\n")
        print("| probe | threads | gate | status | AP C | CPU ceilings MHz | prefill tok/s | decode tok/s | median step ms | longest ms | > 500 ms | threads on CPUs |")
        print("|---|---|---|---|---|---|---|---|---|---|---|---|")
        stats = {}
        for tag in ("cool6", "cool2", "hot6", "hot2"):
            p = P.get(tag)
            if not p or not p["turns"]:
                continue
            T = sorted(p["turns"], key=lambda r: r["turn"])
            steps = [s for r in T for s in r["step_ms"]]
            pre = sum(r["new_tokens"] for r in T) / sum(r["prompt_ms"] for r in T) * 1000
            dec = sum(r["decode_steps"] for r in T) / sum(r["decode_ms"] for r in T) * 1000
            stats[tag] = (pre, dec, {(r["conv"], r["turn"]) for r in T})
            g = p["gate"] or {}
            r0 = T[0]
            print(
                f"| {tag} | {r0['threads'] if 'threads' in r0 else tag[-1]} | {g.get('gate', '?')} | {r0.get('before_status')} | {r0.get('before_ap_c')} | {r0.get('before_cpu_max_mhz')} |"
                f" {pre:.1f} | {dec:.2f} | {statistics.median(steps):.1f} | {max(steps):.0f} | {sum(1 for s in steps if s > 500)} | {where(d, p['label'])} |"
            )
        for n in ("6", "2"):
            if f"cool{n}" in stats and f"hot{n}" in stats:
                c, h = stats[f"cool{n}"], stats[f"hot{n}"]
                same = "same turns" if c[2] == h[2] else "DIFFERENT turns"
                print(f"- {n} threads, hot against cool ({same}): prefill {100 * (h[0] / c[0] - 1):+.0f}%, decode {100 * (h[1] / c[1] - 1):+.0f}%")


if __name__ == "__main__":
    main()
