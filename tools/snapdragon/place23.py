"""Per probe of r23 (or r26: pass chain26 as the second argument): how many cores' worth of CPU time
the benchmark's threads used between samples, how many distinct thread ids it showed, and the last
CPU of samples in which a thread had used CPU time since its previous sample, from place23.txt
(every 2 s: time, pid, allowed list, then tid:last_cpu:utime+stime ticks at 100 a second). Probe
spans from the chain's log.

  python3 place23.py tools/snapdragon/results/2026-10-24-sm8850 [chain26]
"""
import re
import sys
from collections import Counter

d = sys.argv[1] if len(sys.argv) > 1 else "."
chain = sys.argv[2] if len(sys.argv) > 2 else "chain23"
st, probes = {}, []
for line in open(f"{d}/{chain}.log"):
    m = re.match(r"(\d+) probe (\S+) arm (\S+)", line)
    if m:
        st[m.group(2)] = (int(m.group(1)), m.group(3))
    m = re.match(r"(\d+) probe (\S+) end rc (\d+)", line)
    if m and m.group(2) in st:
        probes.append((m.group(2), st[m.group(2)][1], st[m.group(2)][0], int(m.group(1)), m.group(3)))
rows = []
for line in open(f"{d}/place23.txt"):
    p = line.split()
    if line.startswith("#") or len(p) < 4:
        continue
    rows.append((int(p[0]), [tuple(x.split(":")) for x in p[3:]]))
for lab, arm, a, b, rc in probes:
    r = [x for x in rows if a <= x[0] <= b]
    if len(r) < 2:
        continue
    # Without an attached pool ggml makes a disposable one for every graph it computes, so workers come
    # and go: CPU use is summed between consecutive samples over the threads present in both, and the
    # count of distinct thread ids is kept (a probe whose turn never ends leaves no other step count).
    rates, cpus, prev, pt = [], Counter(), {}, None
    for t, th in r:
        cur = {tid: int(tk) for tid, c, tk in th}
        if pt is not None and t > pt:
            rates.append(sum(cur[x] - prev[x] for x in cur if x in prev) / 100 / (t - pt))
        for tid, c, tk in th:
            if tid in prev and int(tk) > prev[tid]:
                cpus[int(c)] += 1
        prev, pt = cur, t
    rates.sort()
    ids = len({tid for _, th in r for tid, c, tk in th})
    print(f"{lab} ({arm}) rc {rc}: {r[-1][0] - r[0][0]} s logged, cores busy between samples median "
          f"{rates[len(rates) // 2]:.2f}, distinct thread ids {ids}, busy samples by last CPU {dict(sorted(cpus.items()))}")
