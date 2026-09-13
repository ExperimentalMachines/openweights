import json, os, sys, math, random, time, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prompts import SYSTEM, EX, DESC, PARAMS, SAMPLE, gg, etq, ggq
from naming import subject, trailer
from transformers import AutoTokenizer
HF=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct'); tok=AutoTokenizer.from_pretrained(HF)
tools=[{"type":"function","function":{"name":"web_search","description":DESC,"parameters":json.loads(PARAMS)}}]
def msgs(q):
    m=[{"role":"system","content":SYSTEM}]
    for r,t in zip(["user","assistant","user","assistant"],EX): m.append({"role":r,"content":t})
    m.append({"role":"user","content":q}); return m
def user_text(q):
    s=subject(q); return q+("\n\n"+trailer(s) if s else "")
pool=[q for q in ggq if q in etq and q not in SAMPLE]
random.seed(11); fresh=random.sample(pool, 24)
prompts={q:tok.apply_chat_template(msgs(user_text(q)), tools=tools, add_generation_prompt=True, tokenize=False) for q in fresh}
json.dump({"sample":fresh,"prompts":prompts}, open(os.path.dirname(os.path.abspath(__file__))+"/sample_prompts_fresh.json","w"), indent=1)
U="http://127.0.0.1:8098"
def post(path, body):
    req=urllib.request.Request(U+path, data=json.dumps(body).encode(), headers={"Content-Type":"application/json"})
    return json.loads(urllib.request.urlopen(req, timeout=600).read())
for _ in range(90):
    try:
        if json.loads(urllib.request.urlopen(U+"/health").read()).get("status")=="ok": break
    except Exception: pass
    time.sleep(2)
TC=tok.convert_tokens_to_ids("<|tool_call_start|>"); out={}
for q in fresh:
    r=post("/completion",{"prompt":prompts[q].replace("<|startoftext|>","",1),"temperature":0,"n_predict":24,"n_probs":5,"cache_prompt":False,"samplers":["temperature"]})
    first=r["completion_probabilities"][0]["top_logprobs"]; pt=next((math.exp(x["logprob"]) for x in first if x["id"]==TC),0.0)
    out[q]={"subject":subject(q),"need":etq[q].get('need'),"phone_gguf_called":bool(ggq[q].get('calls')),"phone_pte_called":bool(etq[q].get('calls')),"p_toolcall":pt,"greedy":r["content"]}
    print(q[:44].ljust(44), "note" if subject(q) else "    ", "gguf p=%.2f"%pt, "phone gguf/pte called:", out[q]["phone_gguf_called"], out[q]["phone_pte_called"], flush=True)
json.dump(out, open(os.path.dirname(os.path.abspath(__file__))+"/gguf_fresh_out.json","w"), indent=1)
