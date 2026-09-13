import json, os, sys, time, urllib.request, difflib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prompts import SYSTEM, EX, DESC, PARAMS, render, SAMPLE
from tokenizers import Tokenizer
hf=Tokenizer.from_file(os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct/tokenizer.json'))
U="http://127.0.0.1:8099"
def post(path, body):
    req=urllib.request.Request(U+path, data=json.dumps(body).encode(), headers={"Content-Type":"application/json"})
    return json.loads(urllib.request.urlopen(req, timeout=600).read())
for _ in range(60):
    try:
        if json.loads(urllib.request.urlopen(U+"/health").read()).get("status")=="ok": break
    except Exception: pass
    time.sleep(2)
tools=[{"type":"function","function":{"name":"web_search","description":DESC,"parameters":json.loads(PARAMS)}}]
def msgs(q):
    m=[{"role":"system","content":SYSTEM}]
    for r,t in zip(["user","assistant","user","assistant"],EX): m.append({"role":r,"content":t})
    m.append({"role":"user","content":q}); return m
q0="What is Rome the capital of?"
lp=post("/apply-template", {"messages":msgs(q0),"tools":tools})["prompt"]
mine=render(q0, bos=False)
print("llama-rendered tokens (+BOS):", len(hf.encode(lp,add_special_tokens=False).ids)+1, " mine:", len(hf.encode(mine,add_special_tokens=False).ids)+1)
open(os.path.dirname(__file__)+"/llama_rendered_rome.txt","w").write(lp)
for tag,i1,i2,j1,j2 in difflib.SequenceMatcher(a=mine,b=lp,autojunk=False).get_opcodes():
    if tag!='equal': print("  DIFF",tag, repr(mine[i1:i2][:160]), "->", repr(lp[j1:j2][:160]))
TC="<|tool_call_start|>"
res={}
llama_prompts={}
for q in SAMPLE:
    for label,prompt in (("app-bytes",render(q,bos=False)),("llama-bytes",post("/apply-template",{"messages":msgs(q),"tools":tools})["prompt"])):
        r=post("/completion",{"prompt":prompt,"temperature":0,"n_predict":28,"n_probs":5,"cache_prompt":False,"samplers":["temperature"]})
        import math
        first=r["completion_probabilities"][0]["top_logprobs"] if r.get("completion_probabilities") else []
        p=next((math.exp(x["logprob"]) for x in first if x["token"]==TC),0.0)
        res.setdefault(q,{})[label]={"p_toolcall":p,"top":[(x["token"],round(math.exp(x["logprob"]),4)) for x in first[:3]],"greedy":r["content"],"n_tokens":r.get("tokens_evaluated")}
        if label=="llama-bytes": llama_prompts[q]="<|startoftext|>"+prompt
        print(q[:40], label, "| p(tool)=%.3f"%p, "| top:",res[q][label]["top"], "|", repr(r["content"][:70]), flush=True)
json.dump(res, open(os.path.dirname(__file__)+"/gguf_out.json","w"), indent=1)

json.dump({"sample":SAMPLE,"prompts":llama_prompts}, open(os.path.dirname(__file__)+"/sample_prompts_llama.json","w"), indent=1)
