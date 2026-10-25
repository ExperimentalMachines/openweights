"""Tables for ExecuTorch's QNN runner (run_et.sh) beside llama.cpp's npu:npu cell on the same turns.

The two do different prefill work: the runner reads the whole conversation every turn, pd_bench
only the turn's new tokens. So prefill is compared as a rate over what each read, and as
time-to-first-token per turn; decode as a rate at the context each started from.

  python3 analyze_et.py et-e1.txt results-r5.jsonl [--model Qwen3-0.6B] [--manifest manifest.json]
"""
import argparse, json, re, statistics as st
from collections import defaultdict

ap = argparse.ArgumentParser()
ap.add_argument("et_log")
ap.add_argument("pd_results", nargs="*")
ap.add_argument("--model", default="Qwen3-0.6B")
ap.add_argument("--manifest")
a = ap.parse_args()

head = re.compile(r"=== rep (\d+) file prompts/(\S+)_t(\d+)\.bin prompt_tokens (\d+) time (\d+) temps (.*)")
runs, cur, cool = [], None, []
for line in open(a.et_log, errors="replace"):
    if line.startswith("=== cool"):
        cool.append(line.strip()[4:])
    m = head.match(line)
    if m:
        cur = dict(rep=int(m[1]), conv=m[2], turn=int(m[3]), n=int(m[4]), temps=m[6].strip(), obs=None, rc=None)
        runs.append(cur)
    elif cur and line.startswith("PyTorchObserver "):
        cur["obs"] = json.loads(line.split(" ", 1)[1])
    elif cur and line.startswith("=== exit"):
        cur["rc"] = int(line.split()[2])
        t = re.search(r"temps_after (.*?) perf", line)
        cur["temps_after"] = t[1] if t else ""
        cur = None

def zone(s, key):
    m = re.search(key + r"\S*=(\d+)", s)
    return int(m[1]) / 1000 if m else None

def cap(s):
    m = re.search(r"gpu_cap=(\d+)", s)
    return int(m[1]) if m else None

ok = [r for r in runs if r["rc"] == 0 and r["obs"]]
print(f"# ExecuTorch QNN runner: {len(runs)} invocations, {len(ok)} with timings, {len(runs) - len(ok)} failed")
if a.manifest:
    want = {(m["conv"], m["turn"]) for m in json.load(open(a.manifest))}
    for rep in sorted({r["rep"] for r in runs}):
        got = {(r["conv"], r["turn"]) for r in ok if r["rep"] == rep}
        print(f"rep {rep}: {len(got)} of {len(want)} manifest prompts timed; missing {sorted(want - got)}")
print("cooling gate:", "; ".join(cool) or "none recorded")
print()

for r in ok:
    o = r["obs"]
    r["prefill_ms"] = o["prompt_eval_end_ms"] - o["inference_start_ms"]
    r["decode_ms"] = o["inference_end_ms"] - o["prompt_eval_end_ms"]
    r["gen"] = o["generated_tokens"]
    r["load_ms"] = o["model_load_end_ms"] - o["model_load_start_ms"]
    r["prefill_tps"] = o["prompt_tokens"] / r["prefill_ms"] * 1000 if r["prefill_ms"] > 0 else None
    r["decode_tps"] = r["gen"] / r["decode_ms"] * 1000 if r["decode_ms"] > 0 and r["gen"] else None

print("## Per rep")
print("| rep | turns | load ms (median) | prefill tok/s (pooled) | decode tok/s (pooled) | decode steps (min..max) | hottest before (max) | GPU cap > 0 before |")
print("|---|---|---|---|---|---|---|---|")
for rep in sorted({r["rep"] for r in ok}):
    rs = [r for r in ok if r["rep"] == rep]
    pre = sum(r["obs"]["prompt_tokens"] for r in rs) / sum(r["prefill_ms"] for r in rs) * 1000
    dec = sum(r["gen"] for r in rs) / sum(r["decode_ms"] for r in rs) * 1000
    hot = max(max(filter(None, [zone(r["temps"], "npu"), zone(r["temps"], "cpu"), zone(r["temps"], "gpu")]), default=0) for r in rs)
    capped = sum(1 for r in rs if (cap(r["temps"]) or 0) > 0)
    print(f"| {rep} | {len(rs)} | {st.median(r['load_ms'] for r in rs):.0f} | {pre:.0f} | {dec:.1f} | "
          f"{min(r['gen'] for r in rs)}..{max(r['gen'] for r in rs)} | {hot:.0f} °C | {capped} |")
print()

pd = defaultdict(list)
for path in a.pd_results:
    for line in open(path):
        r = json.loads(line)
        if r.get("kind") == "turn" and a.model in r["model"] and not r.get("error") and r["prefill"] == "npu" and r["decode"] == "npu":
            pd[(r["conv"], r["turn"])].append(r)
if not pd:
    print("No llama.cpp npu:npu turns for", a.model, "given; ExecuTorch shown alone.")
    raise SystemExit

print(f"## Same turns, llama.cpp npu:npu ({a.model}) beside ExecuTorch")
print("Prefill: ExecuTorch reads the whole prompt (`n`), llama.cpp only the new tokens. TTFT is each one's time to its first token for that turn.")
print()
print("| conv | turn | n (whole) | new | ET rep | ET prefill ms | ET prefill tok/s | llama.cpp prefill ms | llama.cpp prefill tok/s | ET decode tok/s | llama.cpp decode tok/s | ET steps | llama.cpp steps |")
print("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
pairs = []
for r in sorted(ok, key=lambda r: (r["rep"], r["conv"], r["turn"])):
    for p in pd.get((r["conv"], r["turn"]), []):
        if p["pos"] + p["new_tokens"] != r["n"]:
            print(f"| {r['conv']} | {r['turn']} | token count differs: ET {r['n']}, llama.cpp {p['pos'] + p['new_tokens']} |")
            continue
        lp = p["new_tokens"] / p["prefill_ms"] * 1000
        ld = p["decode_steps"] / p["decode_ms"] * 1000
        pairs.append((r, p, lp, ld))
        print(f"| {r['conv']} | {r['turn']} | {r['n']} | {p['new_tokens']} | {r['rep']} | {r['prefill_ms']:.0f} | {r['prefill_tps']:.0f} | "
              f"{p['prefill_ms']:.0f} | {lp:.0f} | {r['decode_tps']:.1f} | {ld:.1f} | {r['gen']} | {p['decode_steps']} |")
print()
for rep in sorted({r["rep"] for r, *_ in pairs}):
    ps = [x for x in pairs if x[0]["rep"] == rep]
    first = [x for x in ps if x[0]["turn"] == 0]
    et_dec = sum(r["gen"] for r, *_ in ps) / sum(r["decode_ms"] for r, *_ in ps) * 1000
    lc_dec = sum(p["decode_steps"] for _, p, *_ in ps) / sum(p["decode_ms"] for _, p, *_ in ps) * 1000
    print(f"rep {rep}: {len(ps)} matched turns. Decode, pooled: ExecuTorch {et_dec:.1f} tok/s, llama.cpp {lc_dec:.1f} tok/s.")
    if first:
        et_pre = sum(r["n"] for r, *_ in first) / sum(r["prefill_ms"] for r, *_ in first) * 1000
        lc_pre = sum(p["new_tokens"] for _, p, *_ in first) / sum(p["prefill_ms"] for _, p, *_ in first) * 1000
        print(f"  First turns only (both read the same tokens, {len(first)} turns): prefill ExecuTorch {et_pre:.0f} tok/s, llama.cpp {lc_pre:.0f} tok/s.")
    et_ttft = st.median(r["prefill_ms"] for r, *_ in ps)
    lc_ttft = st.median(p["prefill_ms"] for _, p, *_ in ps)
    print(f"  Median time to first token over matched turns: ExecuTorch {et_ttft:.0f} ms (whole history), llama.cpp {lc_ttft:.0f} ms (new tokens only).")
