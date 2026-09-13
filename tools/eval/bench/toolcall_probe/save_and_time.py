import json, os, shutil
S = '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad/'
P = '/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad/'
W = os.getcwd()
for f in ['ow_q40.py', 'patch_quantize.py', 'patch_quantize_mix.py', 'export_q40.sh', 'chain_q40.sh', 'chain_mix.sh', 'chain_mix2.sh',
          'chain_nosdpa.sh', 'scale_stats.py', 'tab_mix.py', 'tab_fresh2.py', 'calib_check.py', 'fresh2.sh']:
    shutil.copy(S + f, 'tools/eval/bench/toolcall_probe/' + f)
for f in ['pte_q40abs_out.json', 'pte_all8_out.json', 'pte_ffn4_out.json', 'pte_attn4_out.json', 'pte_conv4_out.json',
          'pte_all8_fresh_out.json', 'pte_attn4_fresh_out.json']:
    shutil.copy(S + f, 'tools/eval/results/toolcall-probe/' + f)
print('copied')
# wall time per row (Mac, Python runner, full-prompt forward + 28 greedy tokens), a rough speed signal only
for name, path in [("8da4w shipped (32k)", P + 'pte_trailer_hf_out.json'), ("all int8", S + 'pte_all8_out.json'), ("int4 attention only", S + 'pte_attn4_out.json'),
                   ("int4 feed-forward only", S + 'pte_ffn4_out.json'), ("int4 conv only", S + 'pte_conv4_out.json'), ("fp32 .pte", P + 'pte_fp32_trailer_hf_out.json')]:
    d = json.load(open(path))
    ss = sorted(v['s'] for v in d.values())
    print("%-24s median s/row %.1f" % (name, ss[len(ss) // 2]))
