"""Tables from pd_bench rows: every prefill/decode pairing, per run label and model.

  python3 analyze_pd.py rows.jsonl [more.jsonl ...] [--conversations conversations.jsonl]

Rows are grouped by (label, model), never across runs. A turn counts for a model only if every
one of the nine cells succeeded on it, so no cell is scored on an easier subset; a cell with no
successful turn empties the match and is reported, not dropped. Duplicate observations of one
(label, model, cell, conversation, turn) are an error.

Rates are pooled over the matched turns, so turns with more tokens weigh more: prefill rate is
new tokens over the synchronised prompt evaluation (prompt_ms), decode rate is decode steps over
decode time, the handoff is the state copy out of one context and into the other. The cell
tables are primary; the per-processor rows pool every cell that used that processor for that
half and so mix partner effects. "Turn" times come from turn_ms where the tool recorded it
(prefill, first token, handoff with its clear, and decode); rows from the first tool version lack
it and show the sum of the stages instead, which leaves out the handoff's clear.
"""
import json, statistics, sys
from collections import defaultdict

ORDER = ["cpu", "gpu", "npu"]
CELLS = [(p, d) for p in ORDER for d in ORDER]


def load(paths):
    rows = []
    for p in paths:
        for line in open(p):
            line = line.strip()
            if line:
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError:
                    print(f"unreadable line in {p}: {line[:80]}", file=sys.stderr)
    return rows


def short(model):
    return model.split("/")[-1].replace(".gguf", "")


def bucket(n, edges):
    for hi, name in edges:
        if n < hi:
            return name
    return f"{edges[-1][0]}+"


WIDTH = [(64, "1-63"), (256, "64-255"), (1024, "256-1023")]
CONTEXT = [(512, "0-511"), (2048, "512-2047"), (4096, "2048-4095")]


def rate(num, ms):
    return num / (ms / 1000) if ms > 0 else float("nan")


def mean_or_nan(xs):
    xs = list(xs)
    return statistics.mean(xs) if xs else float("nan")


def turn_ms(t):
    return t.get("turn_ms", t["prefill_ms"] + t["xfer_get_ms"] + t["xfer_set_ms"] + t["decode_ms"])


def main():
    args = sys.argv[1:]
    conv_path = None
    if "--conversations" in args:
        i = args.index("--conversations")
        conv_path = args[i + 1]
        del args[i:i + 2]
    rows = load(args)
    lengths = {}
    if conv_path:
        for line in open(conv_path):
            c = json.loads(line)
            lengths[c["id"]] = c["bucket"]

    turns = [r for r in rows if r.get("kind") == "turn"]
    loads = [r for r in rows if r.get("kind") == "load"]
    problems = ([r for r in rows if r.get("kind") in ("turn_error", "cell_skipped")] + [t for t in turns if t["error"]]
                + [{"model": r["model"], "error": f"load on {r['proc']}: {r['error']}"} for r in loads if r.get("error")]
                + [{"model": r["model"], "error": f"{r['failures']} failures reported by the tool"} for r in rows if r.get("kind") == "done" and r.get("failures")]
                + [{"model": r["model"], "error": f"exit {r['exit']}"} for r in rows if r.get("kind") == "env" and r.get("exit") not in (None, 0)])

    groups = defaultdict(list)
    for t in turns:
        groups[(t["label"], short(t["model"]))].append(t)

    print("## Loads\n")
    print("| run | model | processor | load ms | error |\n|---|---|---|---|---|")
    for l in loads:
        print(f"| {l['label']} | {short(l['model'])} | {l['proc']} | {l.get('load_ms', 0):.0f} | {l['error'] or ''} |")

    print("\n## Errors and skipped cells\n")
    print("None." if not problems else "")
    for e in problems:
        print(f"- {e.get('label')} {short(e.get('model', ''))} {e.get('prefill')}:{e.get('decode')} "
              f"{e.get('conv', '')} turn {e.get('turn', '')}: {e.get('error')}")

    summary = []
    for (label, model), ts in sorted(groups.items()):
        seen = defaultdict(int)
        for t in ts:
            seen[(t["prefill"], t["decode"], t["conv"], t["turn"])] += 1
        dups = [k for k, n in seen.items() if n > 1]
        ok = {c: set() for c in CELLS}
        for t in ts:
            if not t["error"]:
                ok[(t["prefill"], t["decode"])].add((t["conv"], t["turn"]))
        empty = [c for c in CELLS if not ok[c]]
        present = [c for c in CELLS if ok[c]]
        matched = set.intersection(*ok.values())
        partial = False
        if not matched and len(present) >= 2:
            # An incomplete matrix (the run ended first): match across the cells that ran, and say so.
            matched = set.intersection(*(ok[c] for c in present))
            partial = True
        mt = [t for t in ts if (t["conv"], t["turn"]) in matched and not t["error"]]
        print(f"\n## {model} ({label})\n")
        if dups:
            print(f"**{len(dups)} duplicate observations; this group is not analysed.**")
            continue
        print((f"**Incomplete: {len(present)} of nine cells ran.** " if partial else "")
              + f"{len(matched)} turns in {len({c for c, _ in matched})} conversations matched across "
              + (f"the {len(present)} cells that ran." if partial else "all nine cells.")
              + (f" Cells with no successful turn: {', '.join(p + ':' + d for p, d in empty)}." if empty else ""))
        if not matched:
            continue
        gen = {t["output_tokens"] for t in mt}
        print(f"Decoded tokens per turn: {sorted(gen)}.\n")

        stats = {}
        for c in CELLS:
            cs = [t for t in mt if (t["prefill"], t["decode"]) == c]
            if not cs:
                stats[c] = None
                continue
            stats[c] = dict(
                pre=rate(sum(t["new_tokens"] for t in cs), sum(t["prompt_ms"] for t in cs)),
                dec=rate(sum(t["decode_steps"] for t in cs), sum(t["decode_ms"] for t in cs)),
                first=statistics.median(t["prefill_ms"] for t in cs),
                hand=statistics.median(t["xfer_get_ms"] + t["xfer_set_ms"] for t in cs),
                turn=statistics.median(turn_ms(t) for t in cs),
                total=sum(turn_ms(t) for t in cs) / 1000)
        order = sorted([c for c in CELLS if stats[c]], key=lambda c: stats[c]["total"])
        summary.append((label, model + (" (incomplete)" if partial else ""), len(matched),
                        {p: mean_or_nan(stats[(p, d)]["pre"] for d in ORDER if stats[(p, d)]) for p in ORDER},
                        {d: mean_or_nan(stats[(p, d)]["dec"] for p in ORDER if stats[(p, d)]) for d in ORDER}, order, stats))
        print("Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; "
              "median turn; all matched turns.\n")
        print("| prefill \\ decode | " + " | ".join(ORDER) + " |\n|---|---|---|---|")
        for p in ORDER:
            cols = []
            for d in ORDER:
                s = stats[(p, d)]
                if not s:
                    cols.append("did not run")
                    continue
                cols.append(f"{s['pre']:.0f} / {s['dec']:.1f}; first {s['first']:.0f} ms; handoff {s['hand']:.1f} ms; "
                            f"turn {s['turn']:.0f} ms; {s['total']:.1f} s")
            print(f"| **{p}** | " + " | ".join(cols) + " |")

        # Heat. A turn is "uncapped" only if every reading it has says so: the GPU cap after the turn,
        # and from pd_bench5 on also before it, plus the CPU clusters' frequency ceilings. A missing or
        # unreadable value makes the turn "unknown", never uncapped. Rates are then compared on the turns
        # that were uncapped in all nine cells, so every cell is scored on the same turns.
        def state(t):
            caps = [t.get("gpu_cap"), t.get("before_gpu_cap")] if "before_gpu_cap" in t else [t.get("gpu_cap")]
            cpu = [t.get("cpu_capped"), t.get("before_cpu_capped")] if "cpu_capped" in t else []
            if any(c is None or c < 0 for c in caps) or t.get("cpu_c", -1) < 0:
                return "unknown"
            return "capped" if any(c > 0 for c in caps) or any(cpu) else "uncapped"
        if any("gpu_cap" in t for t in mt):
            st = {(t["prefill"], t["decode"], t["conv"], t["turn"]): state(t) for t in mt}
            common = set.intersection(*[{(t["conv"], t["turn"]) for t in mt if (t["prefill"], t["decode"]) == c and st[(c[0], c[1], t["conv"], t["turn"])] == "uncapped"} for c in present])
            src = sorted({t["source"] for t in mt if (t["conv"], t["turn"]) in common})
            print("\nThrottling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU "
                  "clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. "
                  f"Rates on the right use only the {len(common)} turns uncapped in every cell that ran "
                  f"({', '.join(src) or 'none'}); these are early turns in replay order, so they are a restricted cohort, "
                  "not the phone without throttling.\n")
            print("| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |\n|---|---|---|---|")
            for c in present:
                cs = [t for t in mt if (t["prefill"], t["decode"]) == c]
                n = {k: sum(1 for t in cs if st[(c[0], c[1], t["conv"], t["turn"])] == k) for k in ("capped", "unknown", "uncapped")}
                temps = " / ".join(f"{statistics.median(t[k] for t in cs if t.get(k, -1) >= 0):.0f}" if any(t.get(k, -1) >= 0 for t in cs) else "-" for k in ("cpu_c", "gpu_c", "npu_c"))
                cm = [t for t in cs if (t["conv"], t["turn"]) in common]
                rates = (f"{rate(sum(t['new_tokens'] for t in cm), sum(t['prompt_ms'] for t in cm)):.0f} / "
                         f"{rate(sum(t['decode_steps'] for t in cm), sum(t['decode_ms'] for t in cm)):.1f}") if cm else "-"
                print(f"| {c[0]}:{c[1]} | {n['capped']} / {n['unknown']} / {n['uncapped']} | {temps} | {rates} |")

        print("\nPrefill tok/s by the turn's new tokens, from the three cells with that prefill processor "
              "(unique turns in brackets):\n")
        heads = [n for _, n in WIDTH] + [f"{WIDTH[-1][0]}+"]
        print("| prefill on | " + " | ".join(heads) + " |\n|---|" + "---|" * len(heads))
        for proc in ORDER:
            pre = [t for t in mt if t["prefill"] == proc]
            cols = []
            for h in heads:
                w = [t for t in pre if bucket(t["new_tokens"], WIDTH) == h]
                cols.append(f"{rate(sum(t['new_tokens'] for t in w), sum(t['prompt_ms'] for t in w)):.0f} "
                            f"({len({(t['conv'], t['turn']) for t in w})})" if w else "-")
            print(f"| {proc} | " + " | ".join(cols) + " |")

        print("\nDecode tok/s by the context already held when decoding starts (pos + new tokens), "
              "from the three cells with that decode processor:\n")
        heads = [n for _, n in CONTEXT] + [f"{CONTEXT[-1][0]}+"]
        print("| decode on | " + " | ".join(heads) + " |\n|---|" + "---|" * len(heads))
        for proc in ORDER:
            dec = [t for t in mt if t["decode"] == proc]
            cols = []
            for h in heads:
                w = [t for t in dec if bucket(t["pos"] + t["new_tokens"], CONTEXT) == h]
                cols.append(f"{rate(sum(t['decode_steps'] for t in w), sum(t['decode_ms'] for t in w)):.1f} "
                            f"({len({(t['conv'], t['turn']) for t in w})})" if w else "-")
            print(f"| {proc} | " + " | ".join(cols) + " |")

        # Greedy output against the CPU-only cell, up to each side's first end of turn. A match to
        # the end of the shorter span is censored, not a difference.
        ref = {(t["conv"], t["turn"]): t for t in mt if (t["prefill"], t["decode"]) == ("cpu", "cpu")}
        if not ref:
            print("\nNo cpu:cpu cell among the cells that ran, so no agreement table.")
            continue
        print("\nGreedy tokens against cpu:cpu, compared up to the first end of turn on either side:\n")
        print("| cell | identical to the end | differ | median tokens before the first difference |\n|---|---|---|---|")
        for c in present:
            same, diff, where = 0, 0, []
            for t in mt:
                if (t["prefill"], t["decode"]) != c:
                    continue
                r = ref[(t["conv"], t["turn"])]
                span = min(x if x >= 0 else len(y["tokens"]) for x, y in ((r["eog_at"], r), (t["eog_at"], t)))
                k = 0
                while k < span and r["tokens"][k] == t["tokens"][k]:
                    k += 1
                if k == span:
                    same += 1
                else:
                    diff += 1
                    where.append(k)
            print(f"| {c[0]}:{c[1]} | {same} | {diff} | {statistics.median(where) if where else '-'} |")

    if summary:
        print("\n## Across models\n")
        print("Prefill and decode rates are each processor's mean over its three cells (all matched turns, throttled or not); "
              "the pairing columns are the summed turn time over the matched turns.\n")
        print("| run | model | turns | prefill tok/s CPU / GPU / NPU | decode tok/s CPU / GPU / NPU | fastest pairing | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | slowest pairing |")
        print("|---|---|---|---|---|---|---|---|---|---|---|")
        for label, model, n, pre, dec, order, st in summary:
            f = lambda c: f"{st[c]['total']:.1f} s" if st[c] else "did not run"
            print(f"| {label} | {model} | {n} | {pre['cpu']:.0f} / {pre['gpu']:.0f} / {pre['npu']:.0f} | "
                  f"{dec['cpu']:.1f} / {dec['gpu']:.1f} / {dec['npu']:.1f} | {order[0][0]}:{order[0][1]} ({f(order[0])}) | "
                  f"{f(('npu','cpu'))} | {f(('npu','npu'))} | {f(('gpu','gpu'))} | {f(('cpu','cpu'))} | {order[-1][0]}:{order[-1][1]} ({f(order[-1])}) |")

    cooled = [r for r in rows if r.get("kind") == "cooled"]
    if cooled:
        print("\n## Cooling before each cell\n")
        print("| run | model | cell | gate | waited s | CPU / GPU / NPU °C | GPU cap |\n|---|---|---|---|---|---|---|")
        for r in cooled:
            gate = r.get("gate") or ("passed" if r["cpu_c"] <= r["limit_c"] and r["gpu_c"] <= r["limit_c"] and r["npu_c"] <= r["limit_c"] and r["gpu_cap"] == 0 else "not passed")
            print(f"| {r['label']} | {short(r['model'])} | {r['prefill']}:{r['decode']} | {gate} | {r['waited_s']:.0f} | "
                  f"{r['cpu_c']:.0f} / {r['gpu_c']:.0f} / {r['npu_c']:.0f} | {r['gpu_cap']} |")

    print("\n## Conditions\n")
    for r in rows:
        if r.get("kind") in ("env", "run", "done") or (r.get("kind") == "cell"):
            keep = {k: v for k, v in r.items() if k not in ("kind",)}
            print(f"- {r['kind']}: {json.dumps(keep)}")


main()
