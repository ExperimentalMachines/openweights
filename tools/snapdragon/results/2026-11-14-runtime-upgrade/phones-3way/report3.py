"""Three-way decode report from run3.sh: per model and depth, medians and per-round ratios against v0.8.0."""
import json, sys, statistics
from collections import defaultdict
for path in sys.argv[1:]:
    rows = [json.loads(l) for l in open(path) if l.startswith("{")]
    by = defaultdict(dict)
    for r in rows:
        by[(r["model_name"], r["n_depth"], r["round"])][r["version"]] = (r["avg_ts"], r.get("start_temp"))
    print(f"== {path.split('/')[-1]}: {len(rows)} rows")
    for m in sorted({k[0] for k in by}):
        for dep in sorted({k[1] for k in by if k[0] == m}):
            rr = [v for k, v in by.items() if k[0] == m and k[1] == dep and len(v) == 3]
            if not rr:
                continue
            med = {v: statistics.median(x[v][0] for x in rr) for v in ("v080", "v100", "v100p")}
            r1 = sorted(x["v100"][0] / x["v080"][0] for x in rr)
            r2 = sorted(x["v100p"][0] / x["v080"][0] for x in rr)
            below = sum(x < 1 for x in r1)
            print(f"  {m:15} d{dep:<5} n={len(rr)}  v0.8.0 {med['v080']:6.1f}  v1.0.0 {med['v100']:6.1f} "
                  f"({statistics.median(r1):.3f}, {r1[0]:.2f}-{r1[-1]:.2f}, lower in {below}/{len(r1)})  "
                  f"patched {med['v100p']:6.1f} ({statistics.median(r2):.3f}, {r2[0]:.2f}-{r2[-1]:.2f})")
