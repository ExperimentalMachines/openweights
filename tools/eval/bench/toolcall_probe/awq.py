"""Activation-aware scaling (AWQ-style) of LFM2.5-1.2B's feed-forward and short-conv input
projections, folded into the preceding norms so the fp32 model is unchanged, then saved as a
bf16 Hugging Face checkpoint for the standard 8da4w export.

For a linear W with input channels j and calibration activations x: s_j = max|x_j|^a / max|W_:j|^(1-a),
normalised, then W_:j *= s_j and the producer of x_j is divided by s_j. Producers here:
  feed_forward.w1, w3  <- ffn_norm.weight        (same input, one scale)
  feed_forward.w2      <- row j of w3 (the product channel)   [optional, AWQ_W2=1]
  conv.in_proj         <- operator_norm.weight (conv layers only)
Attention is left alone: int4 there did not cost the call.
"""
import os, sys, json, shutil, math, torch
from transformers import AutoTokenizer, AutoModelForCausalLM
sys.path.insert(0, '/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad')
from prompts import SYSTEM, EX, DESC, PARAMS
from naming import subject, trailer

ALPHA = float(os.environ.get('AWQ_ALPHA', '0.5'))
DO_W2 = os.environ.get('AWQ_W2', '1') == '1'
DO_CONV = os.environ.get('AWQ_CONV', '1') == '1'
DO_W13 = os.environ.get('AWQ_W13', '1') == '1'
N_CAL = int(os.environ.get('AWQ_N', '64'))
SRC = os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct')
DST = os.path.expanduser(os.environ.get('AWQ_DST', '~/ow-models/etexport/hf/LFM2.5-1.2B-AWQ'))
S8 = '/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad/decisions_seed8.json'

tok = AutoTokenizer.from_pretrained(SRC)
model = AutoModelForCausalLM.from_pretrained(SRC, dtype=torch.float32); model.eval()
tools = [{"type": "function", "function": {"name": "web_search", "description": DESC, "parameters": json.loads(PARAMS)}}]
def msgs(q):
    m = [{"role": "system", "content": SYSTEM}]
    for r, t in zip(["user", "assistant", "user", "assistant"], EX): m.append({"role": r, "content": t})
    s = subject(q); m.append({"role": "user", "content": q + ("\n\n" + trailer(s) if s else "")}); return m
rows = json.load(open(S8))['rows']
cal = [tok.apply_chat_template(msgs(r['question']), tools=tools, add_generation_prompt=True, tokenize=False) for r in rows[:N_CAL]]
# a reply prefix so the decision position and a few reply tokens are in the statistics too
cal = [p + '<|tool_call_start|>[web_search(query="' if i % 2 == 0 else p + 'The answer is' for i, p in enumerate(cal)]

layers = model.model.layers
stats = {}   # (layer, key) -> max |x| per input channel
def hook(key):
    def fn(mod, inp, out):
        x = inp[0].detach().reshape(-1, inp[0].shape[-1]).abs().amax(dim=0)
        stats[key] = torch.maximum(stats[key], x) if key in stats else x
    return fn
handles = []
for i, L in enumerate(layers):
    handles.append(L.feed_forward.w1.register_forward_hook(hook((i, 'ffn_in'))))
    if DO_W2: handles.append(L.feed_forward.w2.register_forward_hook(hook((i, 'w2_in'))))
    if DO_CONV and hasattr(L, 'conv') and hasattr(L.conv, 'in_proj'):
        handles.append(L.conv.in_proj.register_forward_hook(hook((i, 'conv_in'))))
with torch.no_grad():
    for k, p in enumerate(cal):
        ids = tok(p, add_special_tokens=False, return_tensors='pt').input_ids[:, :640]
        model(ids)
        if k % 16 == 0: print('calibrated', k + 1, flush=True)
for h in handles: h.remove()

# reference logits before folding, on one probe-like prompt
probe = tok.apply_chat_template(msgs("What is Hanover the capital of?"), tools=tools, add_generation_prompt=True, tokenize=False)
pid = tok(probe, add_special_tokens=False, return_tensors='pt').input_ids
TC = tok.convert_tokens_to_ids("<|tool_call_start|>")
with torch.no_grad(): before = torch.softmax(model(pid).logits[0, -1], -1)[TC].item()

def scales_for(x_max, w_max):
    s = (x_max.clamp(min=1e-5) ** ALPHA) / (w_max.clamp(min=1e-5) ** (1 - ALPHA))
    s = s / math.sqrt(s.max().item() * s.min().item())
    return s.clamp(min=1e-2, max=1e2)

with torch.no_grad():
    for i, L in enumerate(layers):
        # feed-forward gate and up: fold into ffn_norm
        w1, w3 = L.feed_forward.w1.weight, L.feed_forward.w3.weight
        wmax = torch.maximum(w1.abs().amax(0), w3.abs().amax(0))
        if DO_W13:
            s = scales_for(stats[(i, 'ffn_in')], wmax)
            w1.mul_(s); w3.mul_(s); L.ffn_norm.weight.div_(s)
        if DO_W2:
            w2 = L.feed_forward.w2.weight          # [dim, hidden]; input channel = hidden j
            s2 = scales_for(stats[(i, 'w2_in')], w2.abs().amax(0))
            w2.mul_(s2); L.feed_forward.w3.weight.div_(s2[:, None])   # product channel j = silu(w1 x)_j * (w3 x)_j
        if DO_CONV and hasattr(L, 'conv') and hasattr(L.conv, 'in_proj'):
            wi = L.conv.in_proj.weight
            sc = scales_for(stats[(i, 'conv_in')], wi.abs().amax(0))
            wi.mul_(sc); L.operator_norm.weight.div_(sc)
    after = torch.softmax(model(pid).logits[0, -1], -1)[TC].item()
print('fp32 tool-token probability on Hanover before %.4f after folding %.4f (should match)' % (before, after))

os.makedirs(DST, exist_ok=True)
for f in os.listdir(SRC):
    if f != 'model.safetensors' and not f.startswith('.'): shutil.copy(os.path.join(SRC, f), DST)
model.to(torch.bfloat16).save_pretrained(DST, safe_serialization=True)
if os.path.exists(os.path.join(DST, 'model.safetensors.index.json')):
    print('sharded output; merge needed')
print('saved', DST)
