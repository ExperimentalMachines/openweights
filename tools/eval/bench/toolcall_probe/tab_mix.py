import json, os
S = '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad/'
P = '/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad/'
cols = [("GGUF Q4_K_M", P + 'gguf_trailer_out.json', 'gguf'), ("fp32 reference", P + 'ref_trailer_hf_out.json', None),
        ("fp32 .pte", P + 'pte_fp32_trailer_hf_out.json', None), ("8da4w shipped", P + 'pte_trailer_hf_out.json', None),
        ("all int8 (8da8w)", S + 'pte_all8_out.json', None), ("int4 feed-forward only", S + 'pte_ffn4_out.json', None),
        ("int4 attention only", S + 'pte_attn4_out.json', None), ("int4 conv only", S + 'pte_conv4_out.json', None),
        ("8da4w from QAD (HQQ)", S + 'pte_qad_trailer_hf_out.json', None), ("QAD codes, scales made positive", S + 'pte_q40abs_out.json', None)]
if os.path.exists(S + 'pte_nosdpa_out.json'):
    cols.insert(3, ("fp32 .pte, portable SDPA", S + 'pte_nosdpa_out.json', None))
data = {}
for name, path, kind in cols:
    if not os.path.exists(path):
        print("missing", name); continue
    d = json.load(open(path))
    data[name] = {q: (v['hf-bytes']['p_toolcall'] if kind == 'gguf' else v['p_toolcall']) for q, v in d.items()}
g = json.load(open(P + 'gguf_trailer_out.json'))
named = [q for q in g if g[q]['subject']]
head = "| Export | " + " | ".join(q.replace("What is ", "").replace("Who is the author of ", "author: ").replace(" the capital of?", "")[:18] for q in named) + " | calls |"
print(head); print("|" + "---|" * (len(named) + 2))
for name in [c[0] for c in cols if c[0] in data]:
    vals = [data[name][q] for q in named]
    print("| %s | %s | %d of %d |" % (name, " | ".join("%.2f" % v for v in vals), sum(v > 0.5 for v in vals), len(vals)))
open(S + 'table_mix.md', 'w').write("")  # placeholder; the printed table is copied by the caller
