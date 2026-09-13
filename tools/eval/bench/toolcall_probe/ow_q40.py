"""Put Liquid's QAD Q4_0 codes and scales straight into torchao's int4 tensors.

Called from the patched quantize.py after quantize_() when OW_Q40_GGUF names a GGUF.
Q4_0 block: fp16 d, then 16 bytes; byte j holds q[j] in its low nibble and q[j+16] in
its high nibble; value = d * (q - 8). torchao's tensor holds int8 codes in [-8, 7] and a
float scale per group of 32, so code = q - 8 and scale = d, sign included.
"""
import os, sys
import numpy as np
import torch
sys.path.insert(0, '/Users/alpha/mobile-inference/core/engine/src/main/cpp/llama.cpp/gguf-py')
from gguf import GGUFReader

DIM = 2048


def gguf_name(fqn):
    parts = fqn.split('.')
    if parts[0] != 'layers':
        return None, None
    n = parts[1]
    rest = '.'.join(parts[2:])
    m = {'attention.wq': 'attn_q', 'attention.wk': 'attn_k', 'attention.wv': 'attn_v', 'attention.wo': 'attn_output',
         'feed_forward.w1': 'ffn_gate', 'feed_forward.w3': 'ffn_up', 'feed_forward.w2': 'ffn_down',
         'conv.out_proj': 'shortconv.out_proj'}
    if rest in m:
        return f'blk.{n}.{m[rest]}.weight', None
    chunk = {'conv.B_proj': 0, 'conv.C_proj': 1, 'conv.x_proj': 2}
    if rest in chunk:
        return f'blk.{n}.shortconv.in_proj.weight', chunk[rest]
    return None, None


def q40_blocks(t):
    raw = np.array(t.data).reshape(-1)
    rows = int(t.shape[1])  # gguf shape is [ne0=in, ne1=out]; numpy rows = out
    cols = int(t.shape[0])
    nb = cols // 32
    raw = raw.reshape(rows, nb, 18)
    d = raw[:, :, :2].copy().view(np.float16).reshape(rows, nb).astype(np.float32)
    nib = raw[:, :, 2:]
    lo = (nib & 0x0F).astype(np.int8)
    hi = (nib >> 4).astype(np.int8)
    q = np.concatenate([lo, hi], axis=2).reshape(rows, cols)  # positions 0..15 then 16..31 per block
    return torch.from_numpy(q.astype(np.int8) - 8), torch.from_numpy(d)


def apply(model, gguf_path):
    r = GGUFReader(os.path.expanduser(gguf_path))
    tensors = {t.name: t for t in r.tensors}
    done = 0
    worst = 0.0
    for fqn, mod in model.named_modules():
        w = getattr(mod, 'weight', None)
        if w is None or not hasattr(w, 'qdata') or not hasattr(w, 'scale'):
            continue
        name, chunk = gguf_name(fqn)
        if name is None:
            print('ow_q40: left as quantised:', fqn)
            continue
        t = tensors[name]
        assert t.tensor_type.name == 'Q4_0', (name, t.tensor_type.name)
        codes, d = q40_blocks(t)
        if chunk is not None:
            codes = codes[chunk * DIM:(chunk + 1) * DIM]
            d = d[chunk * DIM:(chunk + 1) * DIM]
        assert tuple(codes.shape) == tuple(w.qdata.shape), (fqn, codes.shape, w.qdata.shape)
        assert tuple(d.shape) == tuple(w.scale.shape), (fqn, d.shape, w.scale.shape)
        ref = w.dequantize().float()
        w.qdata.copy_(codes.to(w.qdata.dtype))
        w.scale.copy_(d.to(w.scale.dtype))
        w.zero_point.zero_()
        new = w.dequantize().float()
        worst = max(worst, (new - ref).abs().max().item())
        done += 1
    print(f'ow_q40: replaced {done} linears with Q4_0 codes and scales; max |dequant change| vs torchao requant {worst:.4f}')
    if os.environ.get('OW_Q40_ABS_SCALE') == '1':
        flipped = 0
        for fqn, mod in model.named_modules():
            w = getattr(mod, 'weight', None)
            if w is None or not hasattr(w, 'qdata'):
                continue
            neg = w.scale < 0
            if neg.any():
                # negate codes where the scale is negative; a code of -8 becomes 8 and is clamped to 7
                cols = neg.repeat_interleave(32, dim=1)
                w.qdata.copy_(torch.where(cols, (-w.qdata.int()).clamp(-8, 7).to(torch.int8), w.qdata))
                w.scale.copy_(w.scale.abs())
                flipped += int(neg.sum())
        print(f'ow_q40: made {flipped} group scales positive (codes negated, -8 clamped to 7)')
