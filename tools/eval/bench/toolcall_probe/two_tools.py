import json, os, sys, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prompts import SYSTEM, EX, DESC, PARAMS, SAMPLE
from transformers import AutoTokenizer
from naming import subject, trailer
HF=os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct'); tok=AutoTokenizer.from_pretrained(HF)
SP_DESC="Show pictures or short clips found on the web, as thumbnails to display rather than text to read. It answers no questions; for information of any kind use web_search."
SP_PARAMS={"type":"object","properties":{"query":{"type":"string","description":"What to look for"},"kind":{"type":"string","enum":["images","videos"],"description":"Pictures or clips. Defaults to images."}},"required":["query"]}
tools=[{"type":"function","function":{"name":"web_search","description":DESC,"parameters":json.loads(PARAMS)}},
       {"type":"function","function":{"name":"show_pictures","description":SP_DESC,"parameters":SP_PARAMS}}]
def msgs(q):
    m=[{"role":"system","content":SYSTEM}]
    for r,t in zip(["user","assistant","user","assistant"],EX): m.append({"role":r,"content":t})
    m.append({"role":"user","content":q}); return m
def user_text(q):
    s=subject(q); return q+("\n\n"+trailer(s) if s else "")
prompts={q:tok.apply_chat_template(msgs(user_text(q)), tools=tools, add_generation_prompt=True, tokenize=False) for q in SAMPLE}
json.dump({"sample":SAMPLE,"prompts":prompts}, open(os.path.dirname(__file__)+"/sample_prompts_trailer_hf_2tools.json","w"), indent=1)
print("ok", len(prompts), len(tok(prompts[SAMPLE[0]],add_special_tokens=False).input_ids))
