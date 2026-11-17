"""The MediaTek prefill/decode matrix (pd_bench_mtk --gen-ref rows), read the way
tools/snapdragon/analyze_ref.py reads the Snapdragon ones.

  python3 analyze_mtk.py results-s4.jsonl [more.jsonl ...] [--unit s4=4bff7833]

Per model:
- each processor's load (time, memory available before and after) and each cell's gate (passed or
  timed out, the wait, AP and SKIN, the CPU ceilings at the start);
- each cell's summed turn time over the turns all nine cells completed (the common turns), its
  share of cpu:cpu, and the parts: prompt, first token, handoff (get + set), decode;
- each phase's rate on those turns: prefill tokens / prompt time, decode steps / decode time;
- decode steps over 500 ms (stalls), and how many turns started with a CPU ceiling lowered or a
  thermal status above 0;
- for a cell that hands over (prefill P, decode D), the median (conventional, the mean of the
  middle two for an even count) number of leading tokens its reply
  shares with D:D's reply on the same turn, and how many turns share fewer than 4 past turn 0.
  Both decode on D from the same history; only the processor that read the prompt differs, and the
  two exports differ in quantisation, so replies drift apart eventually. A broken handoff shows as
  agreement near zero on every turn after the break (the cosine check covers one short prompt and
  never the llama.cpp -> NPU direction; Codex review, round two).

Cells of one model ran one after another on one unit, in one shuffled order, after a throwaway
warm-up; a cell's turns share a thermal trajectory and its place in the order.
"""
import argparse
import json
import statistics
from collections import defaultdict

ORDER = ["cpu", "gpu", "npu"]
CELLS = [f"{p}:{d}" for p in ORDER for d in ORDER]
STALL_MS = 500


def model_of(r):
    return r["model"].split("/")[-1].replace(".gguf", "")


def load(paths):
    turns = defaultdict(dict)        # model -> (cell, conv, turn) -> row
    failed = defaultdict(int)        # (model, cell) -> count
    gates = defaultdict(dict)        # model -> cell -> cooled row
    loads = defaultdict(dict)        # model -> proc -> load row
    skipped = defaultdict(list)
    labels = defaultdict(set)
    for path in paths:
        for n, line in enumerate(open(path), 1):
            line = line.strip()
            if not line.startswith("{"):
                continue
            r = json.loads(line)
            if "model" not in r or r.get("label", "").endswith("warm"):
                continue
            m, k = model_of(r), r.get("kind")
            labels[m].add(r.get("label"))
            if k == "load" and "load_ms" in r:
                loads[m][r["proc"]] = r
            elif k == "cooled":
                gates[m][f"{r['prefill']}:{r['decode']}"] = r
            elif k == "cell_skipped":
                skipped[m].append(f"{r['prefill']}:{r['decode']} ({r.get('error')})")
            elif k in ("turn", "turn_error"):
                c = f"{r['prefill']}:{r['decode']}"
                key = (c, r["conv"], r["turn"])
                if key in turns[m]:
                    raise SystemExit(f"{path}:{n}: duplicate {m} {key}")
                if k == "turn_error" or r.get("error"):
                    failed[m, c] += 1
                    continue
                turns[m][key] = r
    return turns, failed, gates, loads, skipped, labels


def common(R, cells):
    keys = None
    for c in cells:
        ks = {(k[1], k[2]) for k in R if k[0] == c}
        keys = ks if keys is None else keys & ks
    return sorted(keys or [])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("runs", nargs="+")
    ap.add_argument("--unit", action="append", default=[], help="label=unit hash, for the report")
    a = ap.parse_args()
    units = dict(u.split("=", 1) for u in a.unit)
    turns, failed, gates, loads, skipped, labels = load(a.runs)
    for m in sorted(turns):
        R = turns[m]
        present = [c for c in CELLS if any(k[0] == c for k in R)]
        keys = common(R, present)
        lab = ",".join(sorted(x for x in labels[m] if x))
        # One model's cells are compared only within one unit (Codex review, round three: rows from
        # two units would otherwise merge silently); --unit maps each label to the unit it ran on.
        us = {units.get(x, "?") for x in labels[m] if x}
        if len(us - {"?"}) > 1:
            raise SystemExit(f"{m}: labels {lab} ran on different units {sorted(us)}")
        part = "" if len(present) == 9 else f"  PARTIAL: {len(present)} of 9 cells, not a matrix result"
        print(f"\n## {m}  (labels {lab}; unit {', '.join(sorted(us))}){part}\n")
        for p in ORDER:
            L = loads[m].get(p)
            if L:
                print(f"- load {p}: {L['load_ms'] / 1000:.1f} s, available {L.get('avail_before_mb', '?')} -> {L.get('avail_after_mb', '?')} MB {L.get('error') or ''}")
        for s in skipped[m]:
            print(f"- skipped: {s}")
        print(f"- cells with turns: {len(present)} of 9; common turns: {len(keys)}")
        if not keys:
            print("- no turn completed in every cell: nothing to compare")
            continue
        # Work must be identical across cells on the common turns.
        for cv, t in keys:
            w = {(R[(c, cv, t)]["pos"], R[(c, cv, t)]["new_tokens"], R[(c, cv, t)]["decode_steps"]) for c in present}
            if len(w) != 1:
                raise SystemExit(f"{m} {cv} turn {t}: cells did different work {sorted(w)}")
        print("\n| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | status > 0 | agree w/ D:D | turns < 4 |")
        print("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        base = None
        rows = []
        for c in present:
            rs = [R[(c, cv, t)] for cv, t in keys]
            tot = sum(r["turn_ms"] for r in rs) / 1000
            if c == "cpu:cpu":
                base = tot
            rows.append((c, rs, tot))
        for c, rs, tot in sorted(rows, key=lambda x: x[2]):
            g = gates[m].get(c, {})
            prompt = sum(r["prompt_ms"] for r in rs) / 1000
            first = sum(r["prefill_ms"] - r["prompt_ms"] for r in rs) / 1000
            xfer = sum(r["xfer_get_ms"] + r["xfer_set_ms"] for r in rs) / 1000
            dec = sum(r["decode_ms"] for r in rs) / 1000
            ntok = sum(r["new_tokens"] for r in rs)
            steps = sum(r["decode_steps"] for r in rs)
            stalls = sum(1 for r in rs for x in r.get("step_ms", []) if x > STALL_MS)
            # A lowered CPU ceiling and a thermal status above 0 are different things (Codex review, round
            # four: one counter for both made a cell look throttled when only Android's status was up).
            capped = sum(1 for r in rs if r.get("before_cpu_capped"))
            warm = sum(1 for r in rs if (r.get("before_status") or 0) > 0)
            ok = sum(1 for k in R if k[0] == c)
            share = f"{tot / base * 100:.0f}%" if base else "?"
            clear = sum(r.get("clear_ms", 0) for r in rs) / 1000
            pp, dd = c.split(":")
            agree, low = "-", "-"
            if pp != dd and f"{dd}:{dd}" in present:
                lens = []
                for cv, t in keys:
                    a_t, b_t = R[(c, cv, t)]["tokens"], R[(f"{dd}:{dd}", cv, t)]["tokens"]
                    n = 0
                    while n < min(len(a_t), len(b_t)) and a_t[n] == b_t[n]:
                        n += 1
                    lens.append((t, n))
                agree = f"{statistics.median(n for _, n in lens):g}"
                low = str(sum(1 for t, n in lens if t > 0 and n < 4))
            print(f"| {c} | {g.get('gate', '?')} | {g.get('waited_s', '?')} | {g.get('ap_c', '?')} | {ok} / {failed[m, c]} | {tot:.1f} | {share} | {prompt:.1f} | {first:.2f} | {xfer:.2f} | {clear:.2f} | {dec:.1f} | {ntok / prompt:.0f} | {steps / dec:.1f} | {stalls} | {capped}/{len(rs)} | {warm}/{len(rs)} | {agree} | {low} |")


if __name__ == "__main__":
    main()
