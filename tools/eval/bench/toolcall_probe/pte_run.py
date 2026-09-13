import json, os, sys, torch, time, types
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from executorch.examples.models.llama.runner.native import NativeLlamaRunner
HF=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct')
inp=json.load(open(sys.argv[1])); out=sys.argv[2]; pte=sys.argv[3]
params=os.path.join(os.path.dirname(__file__),'params.json'); json.dump({"vocab_size":65536}, open(params,'w'))
args=types.SimpleNamespace(pte=pte, params=params, tokenizer=HF+'/tokenizer.json', tokenizer_config=HF+'/tokenizer_config.json', max_len=2048, kv_cache=True)
r=NativeLlamaRunner(args)
from tokenizers import Tokenizer
hf=Tokenizer.from_file(HF+'/tokenizer.json')
TC=hf.token_to_id("<|tool_call_start|>")
res={}
for q,p in inp['prompts'].items():
    ids=hf.encode(p, add_special_tokens=False).ids   # literal BOS already in p
    t=time.time()
    logits=r.forward(torch.tensor([ids],dtype=torch.long), torch.tensor([0],dtype=torch.long))
    last=logits[0,-1].float() if logits.dim()==3 else logits[-1].float()
    probs=torch.softmax(last,-1); top=torch.topk(probs,5)
    # greedy continue
    cur=int(torch.argmax(last)); toks=[cur]; pos=len(ids)
    for _ in range(27):
        lg=r.forward(torch.tensor([[cur]],dtype=torch.long), torch.tensor([pos],dtype=torch.long))
        l=lg[0,-1] if lg.dim()==3 else lg[-1]
        cur=int(torch.argmax(l)); toks.append(cur); pos+=1
        if cur==hf.token_to_id("<|im_end|>"): break
    text=hf.decode(toks, skip_special_tokens=False)
    res[q]={"n_tokens":len(ids),"p_toolcall":probs[TC].item(),"top":[(hf.id_to_token(int(i)),round(float(v),4)) for v,i in zip(top.values,top.indices)],"greedy":text,"s":round(time.time()-t,1)}
    print(q, "| p(tool)=%.3f"%res[q]['p_toolcall'], "| top:", res[q]['top'][:3], "|", repr(text[:80]), flush=True)
json.dump(res, open(out,'w'), indent=1)
