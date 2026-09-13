import json, os, sys, math, time, urllib.request, types, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prompts import SYSTEM, EX, DESC, PARAMS, SAMPLE
from naming import subject, trailer
from transformers import AutoTokenizer
HF=os.path.expanduser('~/ow-models/etexport/hf/Qwen3.5-2B'); tok=AutoTokenizer.from_pretrained(HF)
ptes=[a for a in sys.argv[1:] if a.endswith('.pte')]
ROWS=int(os.environ.get('ROWS','16'))
tools=[{"type":"function","function":{"name":"web_search","description":DESC,"parameters":json.loads(PARAMS)}}]
def msgs(q):
    m=[{"role":"system","content":SYSTEM}]
    for r,t in zip(["user","assistant","user","assistant"],EX): m.append({"role":r,"content":t})
    s=subject(q); m.append({"role":"user","content":q+("\n\n"+trailer(s) if s else "")}); return m
prompts={q:tok.apply_chat_template(msgs(q), tools=tools, add_generation_prompt=True, tokenize=False, enable_thinking=False) for q in SAMPLE}
TC=tok.convert_tokens_to_ids("<tool_call>"); print("tool token", TC, "prompt tokens", len(tok(prompts[SAMPLE[0]]).input_ids), flush=True)
SAMPLE=[q for q in SAMPLE if subject(q)][:ROWS]+[SAMPLE[-1]]
res={q:{} for q in SAMPLE}
U="http://127.0.0.1:8096"
def post(path, body):
    req=urllib.request.Request(U+path, data=json.dumps(body).encode(), headers={"Content-Type":"application/json"}); return json.loads(urllib.request.urlopen(req, timeout=600).read())
for _ in range(120):
    try:
        if json.loads(urllib.request.urlopen(U+"/health").read()).get("status")=="ok": break
    except Exception: pass
    time.sleep(2)
for q in SAMPLE:
    r=post("/completion",{"prompt":prompts[q],"temperature":0,"n_predict":16,"n_probs":5,"cache_prompt":False,"samplers":["temperature"]})
    first=r["completion_probabilities"][0]["top_logprobs"]; res[q]["gguf_q4km"]=next((math.exp(x["logprob"]) for x in first if x["id"]==TC),0.0); res[q]["gguf_greedy"]=r["content"][:40]
print("gguf done", flush=True)
from transformers import AutoModelForImageTextToText
model=AutoModelForImageTextToText.from_pretrained(HF, dtype=torch.float32); model.eval()
for q in SAMPLE:
    ids=tok(prompts[q], return_tensors='pt').input_ids
    with torch.no_grad(): probs=torch.softmax(model(input_ids=ids).logits[0,-1].float(),-1)
    res[q]["ref"]=probs[TC].item()
print("ref done", flush=True); del model
from executorch.examples.models.llama.runner.native import NativeLlamaRunner
params=os.path.join(os.path.dirname(os.path.abspath(__file__)),'params_qwen35.json'); json.dump({"vocab_size":248320}, open(params,'w'))
for pte in ptes:
    if not os.path.exists(pte): print("missing", pte); continue
    name=os.path.basename(pte)
    args=types.SimpleNamespace(pte=pte, params=params, tokenizer=HF+'/tokenizer.json', tokenizer_config=HF+'/tokenizer_config.json', max_len=2048, kv_cache=True)
    r=NativeLlamaRunner(args)
    for q in SAMPLE:
        ids=tok(prompts[q]).input_ids
        try:
            logits=r.forward(torch.tensor([ids],dtype=torch.long), torch.tensor([0],dtype=torch.long))
        except Exception:
            # static shape: one token at a time
            for i,t in enumerate(ids): logits=r.forward(torch.tensor([[t]],dtype=torch.long), torch.tensor([i],dtype=torch.long))
        last=logits[0,-1].float() if logits.dim()==3 else logits[-1].float()
        res[q][name]=torch.softmax(last,-1)[TC].item(); print(name, q[:30], "%.3f"%res[q][name], flush=True)
        try: r.model.reset()  # not all runners
        except Exception: pass
        r=NativeLlamaRunner(args)  # fresh state per prompt
json.dump(res, open(os.path.dirname(os.path.abspath(__file__))+"/qwen35_probe_out_%s.json"%os.environ.get("TAG","x"),"w"), indent=1)
for q in SAMPLE: print(q[:40].ljust(40), "note" if subject(q) else "    ", " ".join("%s %.3f"%(k[:12],v) for k,v in res[q].items() if isinstance(v,float)))
