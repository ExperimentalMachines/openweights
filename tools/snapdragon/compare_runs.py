"""Two runs of the matrix side by side, per model: did the second pass reproduce the first?

  python3 compare_runs.py first.jsonl second.jsonl

Each file must hold one run label. A model is compared on the cells both runs attempted and on
the (conversation, turn) pairs that succeeded in every one of those cells in both runs, so neither
run is scored on an easier subset; failed turns are counted and left out. A matched turn must
have done the same work in both runs (position, new tokens, decode steps), or the script stops.
For each cell the summed turn time and the pooled prefill and decode rates are given for both
runs; the cells are ranked by turn time in each run (ties share an average rank) and the ranks
compared with Spearman's correlation.
"""
import json, math, statistics, sys
from collections import Counter, defaultdict

ORDER = ["cpu", "gpu", "npu"]
CELLS = [(p, d) for p in ORDER for d in ORDER]
WORK = ("pos", "new_tokens", "decode_steps")
TIMES = ("turn_ms", "prompt_ms", "decode_ms")


def turns(path):
    ok = defaultdict(dict)  # model -> {(cell, conv, turn): row}
    attempted = defaultdict(set)  # model -> cells with any turn row
    failed = defaultdict(Counter)  # model -> cell -> failed turns
    labels = set()
    for n, line in enumerate(open(path), 1):
        line = line.strip()
        if not line:
            continue
        r = json.loads(line)
        if r.get("kind") not in ("turn", "turn_error"):
            continue
        labels.add(r.get("label"))
        model = r["model"].split("/")[-1].replace(".gguf", "")
        cell = (r["prefill"], r["decode"])
        attempted[model].add(cell)
        if r["kind"] == "turn_error" or r.get("error"):
            failed[model][cell] += 1
            continue
        for k in TIMES:
            v = r.get(k)
            if not isinstance(v, (int, float)) or not math.isfinite(v) or v <= 0:
                sys.exit(f"{path}:{n}: {model} {cell} turn {r['conv']}/{r['turn']} has no usable {k}")
        key = (cell, r["conv"], r["turn"])
        if key in ok[model]:
            sys.exit(f"{path}:{n}: duplicate observation of {model} {cell} {r['conv']} turn {r['turn']}")
        ok[model][key] = r
    if len(labels) != 1:
        sys.exit(f"{path}: expected one run label, found {sorted(map(str, labels))}")
    return ok, attempted, failed, labels.pop()


def avg_ranks(values):
    order = sorted(range(len(values)), key=lambda i: values[i])
    ranks = [0.0] * len(values)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and values[order[j + 1]] == values[order[i]]:
            j += 1
        for k in range(i, j + 1):
            ranks[order[k]] = (i + j) / 2 + 1
        i = j + 1
    return ranks


def spearman(a, b):
    ra, rb = avg_ranks(a), avg_ranks(b)
    ma, mb = statistics.fmean(ra), statistics.fmean(rb)
    va = sum((x - ma) ** 2 for x in ra)
    vb = sum((y - mb) ** 2 for y in rb)
    if va == 0 or vb == 0:
        return float("nan")
    return sum((x - ma) * (y - mb) for x, y in zip(ra, rb)) / math.sqrt(va * vb)


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    (a, att_a, fail_a, la), (b, att_b, fail_b, lb) = turns(sys.argv[1]), turns(sys.argv[2])
    for model in sorted(set(att_a) ^ set(att_b)):
        print(f"{model}: only in {la if model in att_a else lb}, not compared.\n")
    for model in sorted(set(att_a) & set(att_b)):
        print(f"## {model}\n")
        cells = [c for c in CELLS if c in att_a[model] and c in att_b[model]]
        left_out = [f"{p}:{d}" for p, d in CELLS if (p, d) not in cells]
        if left_out:
            print(f"Partial: cells not attempted in both runs, left out: {', '.join(left_out)}.\n")
        for tag, fails in ((la, fail_a[model]), (lb, fail_b[model])):
            if fails:
                print(f"Failed turns in {tag}: " + ", ".join(f"{p}:{d} {n}" for (p, d), n in sorted(fails.items())) + ".\n")
        if not cells:
            print("No cell attempted in both runs.\n")
            continue
        keys = None
        for run in (a[model], b[model]):
            for c in cells:
                ks = {(k[1], k[2]) for k in run if k[0] == c}
                keys = ks if keys is None else keys & ks
        keys = sorted(keys)
        if not keys:
            print("No turn succeeded in every shared cell of both runs.\n")
            continue
        for cv, t in keys:
            works = {tuple(run[(c, cv, t)][w] for w in WORK) for run in (a[model], b[model]) for c in cells}
            if len(works) != 1:
                sys.exit(f"{model} {cv} turn {t}: the runs did different work ({WORK}: {sorted(works)})")
        tot, pre, dec = {}, {}, {}
        for tag, run in ((la, a[model]), (lb, b[model])):
            for c in cells:
                rows = [run[(c, cv, t)] for cv, t in keys]
                tot[tag, c] = sum(r["turn_ms"] for r in rows) / 1000
                pre[tag, c] = sum(r["new_tokens"] for r in rows) / sum(r["prompt_ms"] for r in rows) * 1000
                dec[tag, c] = sum(r["decode_steps"] for r in rows) / sum(r["decode_ms"] for r in rows) * 1000
        ta, tb = [tot[la, c] for c in cells], [tot[lb, c] for c in cells]
        ra, rb = dict(zip(cells, avg_ranks(ta))), dict(zip(cells, avg_ranks(tb)))
        changes = [y / x - 1 for x, y in zip(ta, tb)]
        print(f"{len(keys)} turns in {len({cv for cv, _ in keys})} conversations, {len(cells)} cells, "
              f"succeeded in both runs. Spearman rank correlation of cell times {spearman(ta, tb):.2f}; "
              f"largest change {max(changes, key=abs) * 100:+.1f}%, median "
              f"{statistics.median(abs(x) for x in changes) * 100:.1f}% either way.\n")
        print(f"| cell | total s, {la} | total s, {lb} | change | rank | prefill tok/s | decode tok/s |")
        print("|---|---|---|---|---|---|---|")
        for c in sorted(cells, key=lambda c: tot[la, c]):
            print(f"| {c[0]}:{c[1]} | {tot[la, c]:.1f} | {tot[lb, c]:.1f} | "
                  f"{(tot[lb, c] / tot[la, c] - 1) * 100:+.1f}% | {ra[c]:g} / {rb[c]:g} | "
                  f"{pre[la, c]:.0f} / {pre[lb, c]:.0f} | {dec[la, c]:.1f} / {dec[lb, c]:.1f} |")
        print()


if __name__ == "__main__":
    main()
