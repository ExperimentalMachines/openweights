#!/usr/bin/env python3
"""Do the decode thread count and the phone change what the model writes?

  python3 agree_threads.py results-r19.jsonl:r19-1-t6 results-r22.jsonl:r22-2-t2 ...

Each argument is a results file and a cell label. Every turn record keeps the greedy tokens it
decoded on the same teacher-forced context, so for each pair of cells the turns they share are
compared token for token: how many are identical, and where the others first diverge. Same count
against same count, on one phone or two, says whether the output is reproducible; different counts
say whether the thread count changes it (it can: the order of floating-point sums changes, and a
greedy choice that was close can flip).
"""
import itertools
import json
import sys


def cell(spec):
    path, label = spec.rsplit(":", 1)
    out = {}
    for line in open(path):
        r = json.loads(line)
        if r.get("kind") == "turn" and r.get("label") == label and not r.get("error"):
            out[(r["conv"], r["turn"])] = r["tokens"]
    return label, out


def first_divergence(a, b):
    for i, (x, y) in enumerate(zip(a, b)):
        if x != y:
            return i
    return None if len(a) == len(b) else min(len(a), len(b))


def main(specs):
    cells = [cell(s) for s in specs]
    for (la, a), (lb, b) in itertools.combinations(cells, 2):
        common = sorted(set(a) & set(b))
        divs = [d for d in (first_divergence(a[k], b[k]) for k in common) if d is not None]
        print(f"{la} vs {lb}: {len(common) - len(divs)} of {len(common)} turns identical"
              + (f"; the rest first diverge at tokens {sorted(divs)}" if divs else ""))


if __name__ == "__main__":
    main(sys.argv[1:])
