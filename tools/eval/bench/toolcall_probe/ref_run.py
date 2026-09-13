import json, os, sys, torch, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from transformers import AutoModelForCausalLM, AutoTokenizer
HF=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct')
inp=json.load(open(sys.argv[1])); out=sys.argv[2]
tok=AutoTokenizer.from_pretrained(HF)
model=AutoModelForCausalLM.from_pretrained(HF, torch_dtype=torch.float32); model.eval()
TC=tok.convert_tokens_to_ids("<|tool_call_start|>")
res={}
for q,p in inp['prompts'].items():
    ids=tok(p, add_special_tokens=False, return_tensors='pt').input_ids
    t=time.time()
    with torch.no_grad():
        logits=model(ids).logits[0,-1].float()
        probs=torch.softmax(logits,-1)
        top=torch.topk(probs,5)
        gen=model.generate(ids, max_new_tokens=28, do_sample=False)
    text=tok.decode(gen[0, ids.shape[1]:], skip_special_tokens=False)
    res[q]={"n_tokens":ids.shape[1],"p_toolcall":probs[TC].item(),"top":[(tok.convert_ids_to_tokens(i.item()),round(v.item(),4)) for v,i in zip(top.values,top.indices)],"greedy":text,"s":round(time.time()-t,1)}
    print(q, "| p(tool)=%.3f"%res[q]['p_toolcall'], "| top:", res[q]['top'][:3], "|", repr(text[:80]), flush=True)
json.dump(res, open(out,'w'), indent=1)
