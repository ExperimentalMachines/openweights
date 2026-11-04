"""Tabulates the phone A/B logs: perplexity and llama-bench speed, b10549 against v0.8.0.

ab.log's bench lines were parsed from CSV with two quoted commas in the row, so the values it
printed are really avg_ns and stddev_ns ("pp0/tg0"), first row pp512 and second tg64; they are
converted back here. ab2.log and ab3.log come from JSON lines and are tok/s already.

Each speed pair ran once in each order across the logs; the table shows both runs and their mean
change, so the order a pair happened to run in cancels out.
"""
import re
import sys
from collections import defaultdict

PKG = {"q105": ("snap", "base"), "q080": ("snap", "new"), "a105": ("app", "base"), "a080": ("app", "new")}
DEV = {"none": "CPU", "GPUOpenCL": "GPU", "HTP0": "NPU"}


def parse(paths):
    ppl = defaultdict(dict)
    speed = defaultdict(lambda: defaultdict(list))  # key -> ver -> [(pp, tg, order_index)]
    for path in paths:
        first = {}
        for line in open(path):
            m = re.match(r"(\S+)\.gguf (ppl|bench) (\S+) (\S+) secs=(\d+) end_hot=(\d+) :: (.*)", line)
            if not m:
                continue
            model, kind, dev, pkg, secs, hot, rest = m.groups()
            build, ver = PKG[pkg]
            key = (build, model, DEV[dev])
            if kind == "ppl":
                p = re.search(r"PPL = ([\d.]+)", rest)
                ppl[key][ver] = float(p.group(1)) if p else None
                continue
            vals = [(int(a), int(b), float(c)) for a, b, c, _ in re.findall(r"pp(\d+)/tg(\d+)=([\d.e+-]+)\+-([\d.e+-]+)", rest)]
            if vals and vals[0][0] == 0 and vals[0][1] == 0:
                pp, tg = 512 / (vals[0][2] / 1e9), 64 / (vals[1][2] / 1e9)
            else:
                pp = next(v[2] for v in vals if v[0])
                tg = next(v[2] for v in vals if v[1])
            pos = (path, key)
            ran_first = pos not in first
            first.setdefault(pos, ver)
            speed[key][ver].append((pp, tg, "1st" if ran_first else "2nd"))
    return ppl, speed


def main():
    ppl, speed = parse(sys.argv[1:])
    keys = sorted(set(ppl) | set(speed))
    print("perplexity (WikiText-2 test, 16 x 512 tokens)")
    for k in keys:
        if k in ppl:
            b, n = ppl[k].get("base"), ppl[k].get("new")
            d = f"{100 * (n / b - 1):+.2f}%" if b and n else ""
            print(f"  {k[0]:4} {k[1][:30]:30} {k[2]}  b10549 {b}  v0.8.0 {n}  {d}")
    print("\nspeed, tok/s: each run's (b10549 -> v0.8.0, which ran first), then the mean change")
    for k in keys:
        if k not in speed:
            continue
        b, n = speed[k]["base"], speed[k]["new"]
        runs = []
        for i in range(min(len(b), len(n))):
            who = "b10549" if b[i][2] == "1st" else "v0.8.0"
            runs.append((b[i], n[i], who))
        pp_chg = [n_[0] / b_[0] - 1 for b_, n_, _ in runs]
        tg_chg = [n_[1] / b_[1] - 1 for b_, n_, _ in runs]
        desc = "; ".join(f"pp {b_[0]:.0f}->{n_[0]:.0f} tg {b_[1]:.1f}->{n_[1]:.1f} ({w} first)" for b_, n_, w in runs)
        mean = lambda xs: 100 * sum(xs) / len(xs)
        print(f"  {k[0]:4} {k[1][:30]:30} {k[2]}  {desc}  => prefill {mean(pp_chg):+.1f}%, decode {mean(tg_chg):+.1f}%  [{len(runs)} run(s)]")


main()
