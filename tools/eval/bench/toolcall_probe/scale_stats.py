import os, sys, numpy as np
sys.path.insert(0, '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad')
from ow_q40 import q40_blocks
from gguf import GGUFReader
r = GGUFReader(os.path.expanduser('~/ow-models/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf'))
neg = zero = tot = 0; nonfinite = 0; mn = 1e9; mx = 0
for t in r.tensors:
    if t.tensor_type.name != 'Q4_0':
        continue
    codes, d = q40_blocks(t)
    d = d.numpy()
    tot += d.size; neg += int((d < 0).sum()); zero += int((d == 0).sum()); nonfinite += int((~np.isfinite(d)).sum())
    a = np.abs(d[d != 0]); mn = min(mn, a.min()); mx = max(mx, a.max())
print(f"groups {tot}: negative {neg} ({neg/tot:.1%}), zero {zero}, nonfinite {nonfinite}, |scale| range {mn:.2e} .. {mx:.2e}")
