import json, math
S = '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad/'
P = '/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad/'
g = json.load(open(P + 'gguf_trailer_out.json'))
sets = {"fp32 .pte": P + 'pte_fp32_trailer_hf_out.json', "all int8": S + 'pte_all8_out.json', "int4 attention only": S + 'pte_attn4_out.json', "8da4w shipped": P + 'pte_trailer_hf_out.json'}
for name, path in sets.items():
    d = json.load(open(path))
    noted = [q for q in d if g[q]['subject']]; un = [q for q in d if not g[q]['subject']]
    # top-1 probability as a crude calibration measure, and the second-best token on noted rows
    top1_noted = sum(d[q]['top'][0][1] for q in noted) / len(noted)
    top1_un = sum(d[q]['top'][0][1] for q in un) / len(un)
    fp_un = sum(d[q]['p_toolcall'] > 0.5 for q in un)
    second = [d[q]['top'][1][0] for q in noted]
    print("%-20s top-1 mass noted %.2f unnoted %.2f | false tool calls on %d unnoted rows: %d | max p(tool) unnoted %.2f | runner-up tokens on noted rows: %s" % (
        name, top1_noted, top1_un, len(un), fp_un, max(d[q]['p_toolcall'] for q in un), sorted(set(second))))
