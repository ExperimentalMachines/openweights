"""A --gen-ref run: every turn decodes the dataset reply's own length, each step timed.

  python3 analyze_ref.py results-r8.jsonl [--first results-r4.jsonl[=model,model] ...]

Per model:
- each cell's cooling gate (passed or timed out, the wait, and whether the GPU cap or a lowered
  CPU ceiling was already on at the start), and its attempted, completed and failed turns;
- each cell's summed turn time over the turns every cell completed;
- each pair against npu:cpu and cpu:cpu on that pair's own matched turns, with the decode steps
  over 500 ms (stalls) counted, the pair's ratio with and without them, and how many turns each
  side was faster on;
- how often the CPU or GPU was seen capped, before and after the turn, and how many replies the
  --gen cap cut short;
- per decode step: the first decode step after the handoff (not the time to first token, which
  comes from the prefill) against the rest, and the rate by step range over the same turns, so a
  fall along the reply is not a change of which turns are counted;
- with --first (a 64-token run, optionally limited to some models with =name,name), those turns
  re-weighted to this run's decode lengths at their own measured rates. This is a constant-rate
  projection: it ignores context growth and heat.

Cells in one model ran one after another on one phone in one order, so the turns of a cell share
a thermal trajectory and its position in the order; the tables describe that schedule. A total
"without stalls" deletes the stalled steps' time; it is a measure of their share, not of what
the cell would have taken without whatever caused them.
"""
import argparse, json, statistics
from collections import defaultdict

ORDER = ["cpu", "gpu", "npu"]
CELLS = [f"{p}:{d}" for p in ORDER for d in ORDER]
WORK = ("pos", "new_tokens", "decode_steps")
STALL_MS = 500


def model_of(r):
    return r["model"].split("/")[-1].replace(".gguf", "")


def load(path, only=None):
    rows = defaultdict(dict)
    failed = defaultdict(int)
    gates = defaultdict(dict)
    skipped = defaultdict(list)
    seen = set()  # failed or not, so a failure repeated as a success is caught too
    for n, line in enumerate(open(path), 1):
        line = line.strip()
        if not line:
            continue
        r = json.loads(line)
        k = r.get("kind")
        if "model" not in r or (only and model_of(r) not in only):
            continue
        if k == "cooled":
            gates[model_of(r)][f"{r['prefill']}:{r['decode']}"] = r
        elif k == "cell_skipped":
            skipped[model_of(r)].append(f"{r['prefill']}:{r['decode']} ({r.get('error')})")
        elif k in ("turn", "turn_error"):
            m, c = model_of(r), f"{r['prefill']}:{r['decode']}"
            key = (c, r["conv"], r["turn"])
            if (m, key) in seen:
                raise SystemExit(f"{path}:{n}: duplicate {m} {key}")
            seen.add((m, key))
            if k == "turn_error" or r.get("error"):
                failed[m, c] += 1
                continue
            rows[m][key] = r
    return rows, failed, gates, skipped, seen


def common(R, cells):
    keys = None
    for c in cells:
        ks = {(k[1], k[2]) for k in R if k[0] == c}
        keys = ks if keys is None else keys & ks
    return sorted(keys or [])


def check_work(m, R, cells, keys):
    for cv, t in keys:
        w = {tuple(R[(c, cv, t)][x] for x in WORK) for c in cells}
        if len(w) != 1:
            raise SystemExit(f"{m} {cv} turn {t}: cells {cells} did different work {sorted(w)}")


def total(R, c, keys):
    return sum(R[(c, cv, t)]["turn_ms"] for cv, t in keys) / 1000


def stalls(R, c, keys):
    return [x for cv, t in keys for x in R[(c, cv, t)].get("step_ms", []) if x > STALL_MS]


def rate(xs):
    return f"{len(xs) / sum(xs) * 1000:.1f}" if xs else "none"


def load_first(specs):
    first, attempted = defaultdict(dict), defaultdict(set)
    for spec in specs:
        p, _, sel = spec.partition("=")
        only = set(sel.split(",")) if sel else None
        fr, _, _, _, seen = load(p, only)
        if only and only - set(fr):
            raise SystemExit(f"{p}: no completed turn for {sorted(only - set(fr))}")
        for m, key in seen:
            if key in attempted[m]:
                raise SystemExit(f"{p}: {m} {key} is already in an earlier --first file")
            attempted[m].add(key)
        for m, R in fr.items():
            for key, r in R.items():
                # The projection rests on the run having decoded 64 tokens a turn (63 timed steps).
                if r["decode_steps"] != 63 or "ref_tokens" in r:
                    raise SystemExit(f"{p}: {m} {key} is not a 64-token turn")
                first[m][key] = r
    return first


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--first", nargs="*", default=[])
    a = ap.parse_args()
    rows, failed, gates, skipped, _ = load(a.run)
    first = load_first(a.first)
    models = sorted(set(rows) | {m for m, _ in failed} | set(gates))
    for m in models:
        R = rows.get(m, {})
        cells = [c for c in CELLS if any(k[0] == c for k in R)]
        listed = [c for c in CELLS if c in cells or failed.get((m, c)) or c in gates[m]]
        print(f"## {m}\n")
        for s in skipped.get(m, []):
            print(f"- Cell skipped: {s}.")
        print("| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |")
        print("|---|---|---|---|---|---|---|---|")
        order = sorted(listed, key=lambda c: min([R[k]["wall_ms"] for k in R if k[0] == c] or [float("inf")]))
        for c in order:
            g = gates[m].get(c, {})
            rs = [r for k, r in R.items() if k[0] == c]
            cut = sum(1 for r in rs if r.get("ref_tokens", -1) > r.get("turn_gen", 0))
            print(f"| {c} | {g.get('gate', '?')} | {g.get('waited_s', '?')} | {g.get('gpu_cap', '?')} | "
                  f"{g.get('cpu_max_mhz', '?')} | {len(rs)} | {failed.get((m, c), 0)} | {cut} |")
        print(f"\nCells in the order they ran: {', '.join(order)}.\n")

        keys = common(R, cells)
        if keys:
            check_work(m, R, cells, keys)
            steps = [R[(cells[0], cv, t)]["decode_steps"] for cv, t in keys]
            print(f"Common cohort: {len(keys)} turns in {len({cv for cv, _ in keys})} conversations completed in "
                  f"every cell. Decode steps a turn: median {statistics.median(steps):g}, mean "
                  f"{statistics.fmean(steps):.0f}, max {max(steps)}, total {sum(steps)}.\n")
            tot = {c: total(R, c, keys) for c in cells}
            print("| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |")
            print("|---|---|---|---|---|---|---|---|")
            for c in sorted(cells, key=tot.get):
                rs = [R[(c, cv, t)] for cv, t in keys]
                pre = sum(r["new_tokens"] for r in rs) / sum(r["prompt_ms"] for r in rs) * 1000
                dec = sum(r["decode_steps"] for r in rs) / sum(r["decode_ms"] for r in rs) * 1000
                share = sum(r["decode_ms"] for r in rs) / sum(r["turn_ms"] for r in rs)
                vs = f"{tot[c] / tot['cpu:cpu'] * 100:.0f}%" if "cpu:cpu" in tot else "n/a"
                cb = sum(1 for r in rs if r.get("before_cpu_capped"))
                ca = sum(1 for r in rs if r.get("cpu_capped"))
                gb = sum(1 for r in rs if (r.get("before_gpu_cap") or 0) > 0)
                ga = sum(1 for r in rs if (r.get("gpu_cap") or 0) > 0)
                print(f"| {c} | {tot[c]:.1f} | {pre:.0f} | {dec:.1f} | {share * 100:.0f}% | {vs} | {cb}/{ca} | {gb}/{ga} |")
            shares = [R[(c, cv, t)]["decode_ms"] / R[(c, cv, t)]["turn_ms"] for c in cells for cv, t in keys]
            print(f"\nDecode share of single turns: {min(shares) * 100:.1f}% to {max(shares) * 100:.1f}%.")
            # The same decoder in different cells differs only in its place in the order and the heat it
            # inherited, so its spread is the floor under which no margin between cells means anything.
            for d in ORDER:
                same = [c for c in cells if c.endswith(":" + d)]
                if len(same) > 1:
                    r = {c: sum(R[(c, cv, t)]["decode_steps"] for cv, t in keys) / sum(R[(c, cv, t)]["decode_ms"] for cv, t in keys) * 1000 for c in same}
                    print(f"\nSame decoder, different cells: {d.upper()} decode " + ", ".join(f"{c} {v:.1f}" for c, v in r.items())
                          + f" tok/s, a spread of {(max(r.values()) / min(r.values()) - 1) * 100:.0f}%.")
            best = sorted(cells, key=tot.get)
            print(f"\nFastest {best[0]}" + "".join(f", {c} +{(tot[c] / tot[best[0]] - 1) * 100:.1f}%" for c in best[1:]) + ".\n")
        else:
            print("No turn completed in every cell.\n")

        # Pairs on their own matched turns, so a cell cut short does not shrink every comparison.
        # A step over 500 ms is not the decoder's speed; such steps appeared only in CPU-decode cells
        # with a CPU zone near 107 °C at the turn's end, so they are counted and also taken out.
        pairs = [(x, y) for x in ("npu:cpu", "cpu:cpu") if x in cells for y in cells
                 if y != x and not (x == "cpu:cpu" and y == "npu:cpu")]
        if pairs:
            print("Pairs on their own matched turns (y against x):\n")
            print("| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |")
            print("|---|---|---|---|---|---|---|---|")
            for x, y in pairs:
                pk = common(R, [x, y])
                if not pk:
                    continue
                check_work(m, R, [x, y], pk)
                tx, ty = total(R, x, pk), total(R, y, pk)
                sx, sy = stalls(R, x, pk), stalls(R, y, pk)
                faster = sum(1 for k in pk if R[(y, *k)]["turn_ms"] < R[(x, *k)]["turn_ms"])
                hot = lambda c: max(R[(c, *k)]["cpu_c"] for k in pk)
                print(f"| {x} | {y} | {len(pk)} | {(ty / tx - 1) * 100:+.1f}% | "
                      f"{((ty - sum(sy) / 1000) / (tx - sum(sx) / 1000) - 1) * 100:+.1f}% | {faster} of {len(pk)} | "
                      f"{len(sx)}, {sum(sx) / 1000:.1f} / {len(sy)}, {sum(sy) / 1000:.1f} | {hot(x):.1f} / {hot(y):.1f} °C |")
            print()
        if not keys:
            continue

        print("Per decode step, over the common cohort (ms; tok/s by step range):\n")
        long_keys = [k for k in keys if R[(cells[0], *k)]["decode_steps"] >= 512]
        print(f"The range columns use only the {len(long_keys)} turns with 512 or more decode steps, so every "
              f"range covers the same turns.\n")
        print("| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |")
        print("|---|---|---|---|---|---|---|---|")
        for c in cells:
            rs = [R[(c, cv, t)] for cv, t in keys]
            lr = [R[(c, cv, t)] for cv, t in long_keys]
            firsts = [r["step_ms"][0] for r in rs if r["step_ms"]]
            rest = [x for r in rs for x in r["step_ms"][1:]]
            allx = [x for r in rs for x in r["step_ms"]]
            bk = [rate([x for r in lr for x in r["step_ms"][lo - 1:hi]]) for lo, hi in ((1, 63), (64, 255), (256, 511), (512, 10**9))]
            print(f"| {c} | {statistics.median(firsts):.1f} | {statistics.median(rest):.1f} | {rate(allx)} | " + " | ".join(bk) + " |")
        print(f"\nShortest decode step {min(x for c in cells for cv, t in keys for x in R[(c, cv, t)]['step_ms']):.2f} ms, "
              f"longest {max(x for c in cells for cv, t in keys for x in R[(c, cv, t)]['step_ms']):.2f} ms.\n")

        F = first.get(m)
        if F:
            fk = [k for k in keys if all((c, *k) in F for c in cells)]
            if not fk:
                print("No turn of the 64-token run matches every cell here; no projection.\n")
                continue
            for cv, t in fk:
                for c in cells:
                    f, r = F[(c, cv, t)], R[(c, cv, t)]
                    if (f["pos"], f["new_tokens"]) != (r["pos"], r["new_tokens"]):
                        raise SystemExit(f"{m} {c} {cv} turn {t}: the 64-token run read different tokens")
            proj = {}
            for c in cells:
                s = 0
                for cv, t in fk:
                    f, r = F[(c, cv, t)], R[(c, cv, t)]
                    s += f["turn_ms"] - f["decode_ms"] + r["decode_steps"] * f["decode_ms"] / f["decode_steps"]
                proj[c] = s / 1000
            meas = {c: total(R, c, fk) for c in cells}
            labels = sorted({F[(c, *k)]["label"] for k in fk for c in cells})
            print(f"Constant-rate projection from the 64-token run ({', '.join(labels)}) on the {len(fk)} turns "
                  f"both runs completed in every cell here:\n")
            print("| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |")
            print("|---|---|---|---|---|---|")
            for c in sorted(cells, key=meas.get):
                pv = f"{proj[c] / proj['cpu:cpu'] * 100:.0f}%" if "cpu:cpu" in proj else "n/a"
                mv = f"{meas[c] / meas['cpu:cpu'] * 100:.0f}%" if "cpu:cpu" in meas else "n/a"
                print(f"| {c} | {proj[c]:.1f} | {meas[c]:.1f} | {meas[c] / proj[c]:.2f} | {pv} | {mv} |")
            print()


if __name__ == "__main__":
    main()
