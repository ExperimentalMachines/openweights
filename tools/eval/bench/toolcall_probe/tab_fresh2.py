import json
S = '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad/'
P = '/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad/'
g = json.load(open(P + 'gguf_fresh_out.json'))
cols = [("GGUF Q4_K_M", P + 'gguf_fresh_out.json'), ("fp32 reference", P + 'ref_fresh_out.json'), ("fp32 .pte", P + 'pte_fp32_fresh_out.json'),
        ("8da4w shipped", P + 'pte_fresh_out.json'), ("all int8", S + 'pte_all8_fresh_out.json'), ("int4 attention only", S + 'pte_attn4_fresh_out.json')]
data = {n: json.load(open(p)) for n, p in cols}
noted = [q for q in g if g[q]['subject']]; un = [q for q in g if not g[q]['subject']]
print("| Export | " + " | ".join(q.replace("Who was the ", "")[:22] for q in noted) + " | calls (7 noted) | max p on 17 unnoted | false calls |")
print("|" + "---|" * (len(noted) + 4))
for n, _ in cols:
    d = data[n]
    vals = [d[q]['p_toolcall'] for q in noted]
    unv = [d[q]['p_toolcall'] for q in un]
    print("| %s | %s | %d | %.2f | %d |" % (n, " | ".join("%.2f" % v for v in vals), sum(v > 0.5 for v in vals), max(unv), sum(v > 0.5 for v in unv)))
