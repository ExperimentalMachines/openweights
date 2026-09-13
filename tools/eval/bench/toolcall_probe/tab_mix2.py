import json, os
S = '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad/'
P = '/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad/'
g = json.load(open(P + 'gguf_trailer_out.json'))
named = [q for q in g if g[q]['subject']]
cols = [("GGUF Q4_K_M", P + 'gguf_trailer_out.json', 'gguf'), ("fp32 .pte", P + 'pte_fp32_trailer_hf_out.json', None), ("all int8", S + 'pte_all8_out.json', None),
        ("int4 attention only", S + 'pte_attn4_out.json', None), ("int4 attention + w1,w3", S + 'pte_w13_4_out.json', None), ("int4 attention + w2", S + 'pte_w2_4_out.json', None),
        ("int4 attention + conv in_proj", S + 'pte_convin4_out.json', None), ("int4 attention + conv out_proj", S + 'pte_convout4_out.json', None),
        ("int4 feed-forward only", S + 'pte_ffn4_out.json', None), ("int4 conv only", S + 'pte_conv4_out.json', None), ("8da4w shipped", P + 'pte_trailer_hf_out.json', None)]
print("| Export | " + " | ".join(q.replace("What is ", "").replace("Who is the author of ", "author: ").replace(" the capital of?", "")[:14] for q in named) + " | calls | size MB |")
print("|" + "---|" * (len(named) + 3))
sizes = {"int4 attention + w1,w3": 'w13_4', "int4 attention + w2": 'w2_4', "int4 attention + conv in_proj": 'convin4', "int4 attention + conv out_proj": 'convout4', "all int8": 'all8', "int4 attention only": 'attn4', "int4 feed-forward only": 'ffn4', "int4 conv only": 'conv4', "fp32 .pte": 'fp32'}
for name, path, kind in cols:
    if not os.path.exists(path):
        print("| %s | missing |" % name); continue
    d = json.load(open(path))
    vals = [(d[q]['hf-bytes']['p_toolcall'] if kind == 'gguf' else d[q]['p_toolcall']) for q in named]
    sz = ''
    if name in sizes:
        f = os.path.expanduser('~/ow-models/etexport/lfm2_5_1_2b_%s_2k.pte' % sizes[name])
        sz = "%d" % (os.path.getsize(f) / 1048576) if os.path.exists(f) else ''
    elif name == "8da4w shipped":
        sz = "789"
    elif name.startswith("GGUF"):
        sz = "697"
    print("| %s | %s | %d of %d | %s |" % (name, " | ".join("%.2f" % v for v in vals), sum(v > 0.5 for v in vals), len(vals), sz))
