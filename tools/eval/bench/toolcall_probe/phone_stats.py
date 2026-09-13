import json, glob, os, statistics, sys
sys.path.insert(0, '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad')
from naming import subject
D = sys.argv[1] if len(sys.argv) > 1 else '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad/phone'
print("| Artifact | rows | prefill tok/s (median of first passes) | decode tok/s (median) | TTFT ms (median) | PSS MB after (median) | calls on noted rows | calls on unnoted need rows | calls on rows not needing |")
print("|---|---|---|---|---|---|---|---|---|")
for f in sorted(glob.glob(D + '/decisions-*.jsonl')):
    rows = [json.loads(l) for l in open(f)][1:]
    name = os.path.basename(f).replace('decisions-LFM2.5-1.2B-Instruct-', '').replace('-driven-search.jsonl', '')
    pre = []; dec = []; ttft = []; pss = []
    for r in rows:
        p0 = r['passes'][0]
        if p0.get('prefill_ms', 0) > 0 and p0.get('prompt_tokens', 0) > 0: pre.append(1000.0 * p0['prompt_tokens'] / p0['prefill_ms'])
        for p in r['passes']:
            if p.get('decode_ms', 0) > 0 and p.get('generated_tokens', 0) > 1: dec.append(1000.0 * (p['generated_tokens'] - 1) / p['decode_ms'])
        if p0.get('ttft_ms', 0) > 0: ttft.append(p0['ttft_ms'])
        if r.get('pss_kb_after'): pss.append(r['pss_kb_after'] / 1024)
    noted = [r for r in rows if subject(r['question'])]
    un_need = [r for r in rows if not subject(r['question']) and r.get('need')]
    no_need = [r for r in rows if not r.get('need')]
    c = lambda rs: sum(bool(r.get('calls')) for r in rs)
    med = lambda x: statistics.median(x) if x else float('nan')
    print("| %s | %d | %.0f | %.1f | %.0f | %.0f | %d of %d | %d of %d | %d of %d |" % (name, len(rows), med(pre), med(dec), med(ttft), med(pss), c(noted), len(noted), c(un_need), len(un_need), c(no_need), len(no_need)))
