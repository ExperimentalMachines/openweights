import json, hashlib, os
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),"..","..","results","decisions")+"/"
def load(f):
    rows=[json.loads(l) for l in open(R+f)]; return rows[0], rows[1:]
h,et=load("decisions-LFM2.5-1.2B-Instruct-8da4w-32k-driven-search.jsonl")
h2,gg=load("decisions-LFM2.5-1.2B-Instruct-Q4_K_M-driven-search.jsonl")
SYSTEM=h['system']; assert SYSTEM==h2['system']
DESC=("Search the web for what you cannot already know: what changed, what is recent, or the present state of a named person, product or organisation. Returns text; for pictures or clips use show_pictures. Not for settled knowledge (definitions, translations, history, arithmetic) and never to double check what you know: answer those yourself.")
PARAMS='''{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "What to look up, as you would type it into a search box"
    }
  },
  "required": ["query"]
}'''
def jq(s):
    return '"'+s.replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r').replace('\t','\\t')+'"'
TOOL='{"type": "function", "function": {"name": "web_search", "description": '+jq(DESC)+', "parameters": '+PARAMS+'}}'
sha=hashlib.sha1((DESC+PARAMS).encode()).hexdigest()
assert sha==h['tools_sha1'], (sha, h['tools_sha1'])
assert hashlib.sha1(SYSTEM.encode()).hexdigest()==h['system_sha1']
EX=h['date_exchange'] if isinstance(h['date_exchange'],list) else json.loads(h['date_exchange'].replace("'",'"'))
def render(question, bos=True):
    s = SYSTEM + "\nList of tools: [" + TOOL + "]"
    p = ("<|startoftext|>" if bos else "") + "<|im_start|>system\n"+s+"<|im_end|>\n"
    roles=["user","assistant","user","assistant"]
    for r,t in zip(roles,EX): p += "<|im_start|>"+r+"\n"+t+"<|im_end|>\n"
    p += "<|im_start|>user\n"+question+"<|im_end|>\n<|im_start|>assistant\n"
    return p
etq={r['question']:r for r in et}; ggq={r['question']:r for r in gg}
def called(r): return bool(r.get('calls'))
both=[q for q in ggq if q in etq]
gg_only=[q for q in both if called(ggq[q]) and not called(etq[q])]
neither=[q for q in both if not called(ggq[q]) and not called(etq[q])]
et_only=[q for q in both if called(etq[q]) and not called(ggq[q])]
bothc=[q for q in both if called(etq[q]) and called(ggq[q])]
SAMPLE = gg_only[:10] + neither[:4] + bothc[:2]
if __name__=="__main__":
    print("gg_only",len(gg_only),"neither",len(neither),"et_only",len(et_only),"both",len(bothc))
    from tokenizers import Tokenizer
    tok=Tokenizer.from_file(os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct/tokenizer.json'))
    q="What is Rome the capital of?"
    n=len(tok.encode(render(q),add_special_tokens=False).ids)
    print("Rome prompt tokens (with BOS):",n,"gguf reported",ggq[q]['passes'][0]['prompt_tokens'])
    json.dump({"sample":SAMPLE,"prompts":{q:render(q) for q in SAMPLE},"need":{q:etq[q].get('need') for q in SAMPLE}}, open(os.path.dirname(__file__)+"/sample_prompts.json","w"), indent=1)
