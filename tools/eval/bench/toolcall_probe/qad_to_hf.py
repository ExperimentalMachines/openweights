import os, sys, shutil, numpy as np, torch
sys.path.insert(0,'/Users/alpha/mobile-inference/core/engine/src/main/cpp/llama.cpp/gguf-py')
from gguf import GGUFReader
from gguf.quants import dequantize
from safetensors.torch import load_file, save_file
SRC=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct'); DST=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-QAD')
orig=load_file(SRC+'/model.safetensors')
r=GGUFReader(os.path.expanduser('~/ow-models/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf'))
def hfname(n):
    if n=='token_embd.weight': return 'model.embed_tokens.weight'
    if n=='token_embd_norm.weight': return 'model.embedding_norm.weight'
    b,i,rest=n.split('.',2); i=int(i)
    m={'attn_norm.weight':'operator_norm.weight','ffn_norm.weight':'ffn_norm.weight','ffn_gate.weight':'feed_forward.w1.weight','ffn_up.weight':'feed_forward.w3.weight','ffn_down.weight':'feed_forward.w2.weight','shortconv.conv.weight':'conv.conv.weight','shortconv.in_proj.weight':'conv.in_proj.weight','shortconv.out_proj.weight':'conv.out_proj.weight','attn_q.weight':'self_attn.q_proj.weight','attn_k.weight':'self_attn.k_proj.weight','attn_v.weight':'self_attn.v_proj.weight','attn_output.weight':'self_attn.out_proj.weight','attn_q_norm.weight':'self_attn.q_layernorm.weight','attn_k_norm.weight':'self_attn.k_layernorm.weight'}
    return f'model.layers.{i}.'+m[rest]
out={}; worst=1.0; report=[]
for t in r.tensors:
    k=hfname(t.name); ref=orig[k]
    a=dequantize(t.data, t.tensor_type) if t.tensor_type.name!='F32' else np.array(t.data)
    a=torch.from_numpy(np.ascontiguousarray(a)).float().reshape(ref.shape)
    cos=torch.nn.functional.cosine_similarity(a.flatten(), ref.float().flatten(), dim=0).item()
    worst=min(worst,cos); report.append((k,t.tensor_type.name,round(cos,4)))
    out[k]=a.to(torch.bfloat16).contiguous()
print("tensors", len(out), "of", len(orig), "missing:", [k for k in orig if k not in out][:5]); print("worst cosine vs original:", worst)
for k,ty,c in sorted(report,key=lambda x:x[2])[:6]: print("  low:",k,ty,c)
os.makedirs(DST, exist_ok=True)
for f in os.listdir(SRC):
    if f!='model.safetensors' and not f.startswith('.'): shutil.copy(os.path.join(SRC,f), DST)
save_file(out, DST+'/model.safetensors', metadata={"format":"pt"}); print("written", DST)
