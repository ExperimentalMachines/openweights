import json, os, sys, math, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prompts import SYSTEM, EX, DESC, PARAMS, render, SAMPLE
from transformers import AutoTokenizer
HF=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct')
tok=AutoTokenizer.from_pretrained(HF)
tools=[{"type":"function","function":{"name":"web_search","description":DESC,"parameters":json.loads(PARAMS)}}]
def msgs(q):
    m=[{"role":"system","content":SYSTEM}]
    for r,t in zip(["user","assistant","user","assistant"],EX): m.append({"role":r,"content":t})
    m.append({"role":"user","content":q}); return m
p0=tok.apply_chat_template(msgs("What is Rome the capital of?"), tools=tools, add_generation_prompt=True, tokenize=False)
print("HF-style tokens:", len(tok(p0,add_special_tokens=False).input_ids))
i=p0.find("List of tools"); print(repr(p0[i:i+700]))
TC=tok.convert_tokens_to_ids("<|tool_call_start|>")
U="http://127.0.0.1:8099"
def post(path, body):
    req=urllib.request.Request(U+path, data=json.dumps(body).encode(), headers={"Content-Type":"application/json"})
    return json.loads(urllib.request.urlopen(req, timeout=600).read())
out={}; prompts={}
for q in SAMPLE:
    p=tok.apply_chat_template(msgs(q), tools=tools, add_generation_prompt=True, tokenize=False)
    prompts[q]=p
    r=post("/completion",{"prompt":p.replace("<|startoftext|>","",1),"temperature":0,"n_predict":28,"n_probs":5,"cache_prompt":False,"samplers":["temperature"]})
    first=r["completion_probabilities"][0]["top_logprobs"]
    pt=next((math.exp(x["logprob"]) for x in first if x["id"]==TC),0.0)
    out[q]={"p_toolcall":pt,"top":[(x["id"],x["token"],round(math.exp(x["logprob"]),4)) for x in first[:3]],"greedy":r["content"]}
    print(q[:40], "| p(tool)=%.3f"%pt, "|", repr(r["content"][:70]), flush=True)
json.dump({"sample":SAMPLE,"prompts":prompts}, open(os.path.dirname(__file__)+"/sample_prompts_hf.json","w"), indent=1)
json.dump(out, open(os.path.dirname(__file__)+"/gguf_hf_out.json","w"), indent=1)
