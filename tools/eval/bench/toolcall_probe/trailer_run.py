import json, os, sys, math, re, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prompts import SYSTEM, EX, DESC, PARAMS, SAMPLE, TOOL
from transformers import AutoTokenizer
HF=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct')
tok=AutoTokenizer.from_pretrained(HF)
WORD=r"[^\W_][\w'.\-]*"
JOINER="of|the|and|in|at|de|von|van|da|del|la|le"
NAME=rf"(?:{WORD})(?: (?:{WORD}|{JOINER}))*"
LONG=rf"(?:[A-Z][\w'.\-]*)(?: (?:{WORD}|{JOINER}))+"
SHAPES=[re.compile(rf"^(?i:who) (?i:is|was|are|were) (?i:the )?({NAME})\??$"),
        re.compile(rf"^(?i:what) (?i:is|was|are|were) (?i:the |a |an )?({LONG})\??$"),
        re.compile(rf"^(?i:tell me about) (?i:the )?({NAME})\.?$"),
        re.compile(rf"^(?i:what happens) (?i:in|at the end of|to|after) (?i:the )?({NAME})\??$")]
STOP=set("i me you he she it we they this that these those him her them us who what someone anyone everyone nobody there here the a an my your our their his its yourself myself himself herself itself ourselves themselves something anything everything nothing mine yours ours theirs".split())
POSS=set("my your our their his her its".split())
def subject(q):
    t=q.strip()
    if len(t)>120: return None
    for s in SHAPES:
        m=s.match(t)
        if m:
            sub=m.group(1).strip().rstrip('.?!'); words=[w.lower() for w in sub.split(' ')]
            if sub.lower() in STOP or words[0] in STOP or any(w in POSS for w in words): return None
            return sub
    return None
def trailer(sub): return f"(This question names {sub}. Look it up with web_search before answering rather than recalling it, and answer from what the search returns.)"
tools=[{"type":"function","function":{"name":"web_search","description":DESC,"parameters":json.loads(PARAMS)}}]
def msgs(q):
    m=[{"role":"system","content":SYSTEM}]
    for r,t in zip(["user","assistant","user","assistant"],EX): m.append({"role":r,"content":t})
    m.append({"role":"user","content":q}); return m
def user_text(q):
    s=subject(q); return q+("\n\n"+trailer(s) if s else "")
def app_render(q):
    s = SYSTEM + "\nList of tools: [" + TOOL + "]"
    p = "<|startoftext|><|im_start|>system\n"+s+"<|im_end|>\n"
    for r,t in zip(["user","assistant","user","assistant"],EX): p += "<|im_start|>"+r+"\n"+t+"<|im_end|>\n"
    return p + "<|im_start|>user\n"+user_text(q)+"<|im_end|>\n<|im_start|>assistant\n"
q0="What is Rome the capital of?"
hfp=tok.apply_chat_template(msgs(user_text(q0)), tools=tools, add_generation_prompt=True, tokenize=False)
print("Rome subject:", subject(q0)); print("HF-style+trailer tokens:", len(tok(hfp,add_special_tokens=False).input_ids), "(+1 double BOS on the phone's llama path = ?)")
print("app-style+trailer tokens:", len(tok(app_render(q0),add_special_tokens=False).input_ids))
U="http://127.0.0.1:8098"
def post(path, body):
    req=urllib.request.Request(U+path, data=json.dumps(body).encode(), headers={"Content-Type":"application/json"})
    return json.loads(urllib.request.urlopen(req, timeout=600).read())
TC=tok.convert_tokens_to_ids("<|tool_call_start|>")
out={}; hfprompts={}; appprompts={}
for q in SAMPLE:
    p=tok.apply_chat_template(msgs(user_text(q)), tools=tools, add_generation_prompt=True, tokenize=False)
    hfprompts[q]=p; appprompts[q]=app_render(q)
    row={"subject":subject(q)}
    for label,prompt in (("hf-bytes",p.replace("<|startoftext|>","",1)),("hf-bytes-doubleBOS",p),("app-bytes",app_render(q).replace("<|startoftext|>","",1))):
        r=post("/completion",{"prompt":prompt,"temperature":0,"n_predict":28,"n_probs":5,"cache_prompt":False,"samplers":["temperature"]})
        first=r["completion_probabilities"][0]["top_logprobs"]
        pt=next((math.exp(x["logprob"]) for x in first if x["id"]==TC),0.0)
        row[label]={"p_toolcall":pt,"greedy":r["content"],"n":r.get("tokens_evaluated")}
    out[q]=row
    print(q[:34].ljust(34), "subj=%s"%(str(row['subject'])[:22]).ljust(27), " ".join("%s p=%.2f"%(k[:6],row[k]['p_toolcall']) for k in ("hf-bytes","hf-bytes-doubleBOS","app-bytes")), "|", repr(row["hf-bytes"]["greedy"][:50]), flush=True)
json.dump(out, open(os.path.dirname(__file__)+"/gguf_trailer_out.json","w"), indent=1)
json.dump({"sample":SAMPLE,"prompts":hfprompts}, open(os.path.dirname(__file__)+"/sample_prompts_trailer_hf.json","w"), indent=1)
json.dump({"sample":SAMPLE,"prompts":appprompts}, open(os.path.dirname(__file__)+"/sample_prompts_trailer_app.json","w"), indent=1)
