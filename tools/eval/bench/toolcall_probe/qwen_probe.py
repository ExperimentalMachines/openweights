import json, os, sys, math, time, urllib.request, types, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prompts import SYSTEM, EX, DESC, PARAMS, SAMPLE
from naming import subject, trailer
from transformers import AutoTokenizer, AutoModelForCausalLM
HF=os.path.expanduser('~/ow-models/etexport/hf/Qwen3-1.7B'); tok=AutoTokenizer.from_pretrained(HF)
tools=[{"type":"function","function":{"name":"web_search","description":DESC,"parameters":json.loads(PARAMS)}}]
def msgs(q):
    m=[{"role":"system","content":SYSTEM}]
    for r,t in zip(["user","assistant","user","assistant"],EX): m.append({"role":r,"content":t})
    s=subject(q); m.append({"role":"user","content":q+("\n\n"+trailer(s) if s else "")}); return m
prompts={q:tok.apply_chat_template(msgs(q), tools=tools, add_generation_prompt=True, tokenize=False, enable_thinking=False) for q in SAMPLE}
TC=tok.convert_tokens_to_ids("<tool_call>"); print("tool token id", TC, "prompt tokens", len(tok(prompts[SAMPLE[0]]).input_ids), flush=True)
res={q:{} for q in SAMPLE}
# GGUF Q8_0
U="http://127.0.0.1:8097"
def post(path, body):
    req=urllib.request.Request(U+path, data=json.dumps(body).encode(), headers={"Content-Type":"application/json"}); return json.loads(urllib.request.urlopen(req, timeout=600).read())
for _ in range(90):
    try:
        if json.loads(urllib.request.urlopen(U+"/health").read()).get("status")=="ok": break
    except Exception: pass
    time.sleep(2)
for q in SAMPLE:
    r=post("/completion",{"prompt":prompts[q],"temperature":0,"n_predict":16,"n_probs":5,"cache_prompt":False,"samplers":["temperature"]})
    first=r["completion_probabilities"][0]["top_logprobs"]; res[q]["gguf_q8"]=next((math.exp(x["logprob"]) for x in first if x["id"]==TC),0.0); res[q]["gguf_greedy"]=r["content"][:40]
print("gguf done", flush=True)
# fp32 reference
model=AutoModelForCausalLM.from_pretrained(HF, dtype=torch.float32); model.eval()
for q in SAMPLE:
    ids=tok(prompts[q], return_tensors='pt').input_ids
    with torch.no_grad(): probs=torch.softmax(model(ids).logits[0,-1].float(),-1)
    res[q]["ref"]=probs[TC].item()
print("ref done", flush=True); del model
# 8da4w pte
from executorch.examples.models.llama.runner.native import NativeLlamaRunner
params=os.path.join(os.path.dirname(os.path.abspath(__file__)),'params_qwen.json'); json.dump({"vocab_size":151936}, open(params,'w'))
args=types.SimpleNamespace(pte=os.path.expanduser('~/ow-models/etexport/qwen3_1_7b_8da4w_2k.pte'), params=params, tokenizer=HF+'/tokenizer.json', tokenizer_config=HF+'/tokenizer_config.json', max_len=2048, kv_cache=True)
r=NativeLlamaRunner(args)
for q in SAMPLE:
    ids=tok(prompts[q]).input_ids
    logits=r.forward(torch.tensor([ids],dtype=torch.long), torch.tensor([0],dtype=torch.long)); last=logits[0,-1].float() if logits.dim()==3 else logits[-1].float()
    res[q]["pte_8da4w"]=torch.softmax(last,-1)[TC].item()
print("pte done", flush=True)
json.dump(res, open(os.path.dirname(os.path.abspath(__file__))+"/qwen_probe_out.json","w"), indent=1)
for q in SAMPLE: print(q[:40].ljust(40), "note" if subject(q) else "    ", "gguf_q8 %.2f  ref %.2f  pte %.3f"%(res[q]["gguf_q8"],res[q]["ref"],res[q]["pte_8da4w"]))
