"""Grades GSM8K reports against the fresh 100-question set with the repo's own GSM8K rule, and pairs two arms.

    python3 grade_gsm100.py questions-seed11.json et151/gsm100.bench.json et160/gsm100.bench.json
"""
import json, sys, math
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[5] / "tools" / "eval" / "bench"))
import grade
refs = {p["id"]: p["reference"] for p in json.load(open(sys.argv[1]))["prompts"]}
arms = []
for path in sys.argv[2:]:
    cases = {c["id"]: c for c in json.load(open(path))["cases"]}
    arms.append((path, {i: grade.grade_gsm8k(c, refs[i]) for i, c in cases.items()}, cases))
for path, g, _ in arms:
    print(f"{sum(g.values())}/{len(g)}  {path.split('/')[-2]}")
if len(arms) >= 2:
    (_, a, ca), (_, b, cb) = arms[0], arms[1]
    ids = [i for i in a if i in b]
    gained = [i for i in ids if not a[i] and b[i]]; lost = [i for i in ids if a[i] and not b[i]]
    same = sum(ca[i].get("raw") == cb[i].get("raw") for i in ids)
    n = len(gained) + len(lost); k = min(len(gained), len(lost))
    p = min(1.0, 2 * sum(math.comb(n, j) for j in range(k + 1)) / 2 ** n) if n else 1.0
    print(f"second vs first: gained {len(gained)} {gained}; lost {len(lost)} {lost}; identical replies {same}/{len(ids)}; two-sided sign test p={p:.3f}")
