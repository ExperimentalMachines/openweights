import os, random, difflib, glob
from tokenizers import Tokenizer
WS=' \t\r\n'
def cut(text, most=800):
    if len(text)<=most: return text
    w=text[:most]; nl=w.rfind('\n')
    while nl>0 and nl+1<len(text) and text[nl+1] in WS: nl=w.rfind('\n',0,nl)
    if nl>0: return w[:nl+1]
    sp=w.rfind(' ')
    if sp>0: return w[:sp]
    return w
def diff(tok, text, call=1600):
    out=[]; rest=text
    while len(rest)>call:
        p=cut(rest); out.append(p); rest=rest[len(p):]
    out.append(rest)
    whole=tok.encode(text,add_special_tokens=False).ids; pieced=[i for p in out for i in tok.encode(p,add_special_tokens=False).ids]
    sm=difflib.SequenceMatcher(a=whole,b=pieced,autojunk=False)
    return sum(max(i2-i1,j2-j1) for t,i1,i2,j1,j2 in sm.get_opcodes() if t!='equal')
random.seed(7)
words=["the","import","def","https://example.com/a/b?c=d&e=f","|","---","x_y","0x1F","  ","\n\n","\n    ","`code`","**bold**","don't","U+2019’s","1234567","(",")","{\"a\": 1}","...","—","日本語","émigré"]
texts=["".join(random.choice(words)+random.choice([" ","","  ","\n","\n\n","\t"]) for _ in range(random.randint(200,900))) for n in range(150)]
texts.append("\n".join("| col%d | val %d |  x  "%(i,i) for i in range(400))); texts.append("def f():\n    return 1\n\n\nclass A:\n    pass\n"*60)
import json
real=json.load(open('sample_prompts_trailer_hf.json'))['prompts']; texts += list(real.values())[:4]
paths={"LFM2.5-1.2B":"~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct/tokenizer.json","Qwen3-1.7B":"~/ow-models/etexport/hf/Qwen3-1.7B/tokenizer.json","Gemma3":"~/ow-models/etvl/gemma3/tokenizer.json","SmolLM2/SmolVLM2":"~/ow-models/etvl/smolvlm2/tokenizer.json","Llama-3.2-1B":"~/ow-models/etexport/src/examples/mediatek/models/llm_models/weights/Llama-3.2-1B-Instruct/tokenizer.json","gpt-oss":"~/ow-models/mlx/gpt-oss-20b-MXFP4-Q8/tokenizer.json"}
for name,p in paths.items():
    try: tok=Tokenizer.from_file(os.path.expanduser(p))
    except Exception as e: print(name,"load failed",str(e)[:80]); continue
    bad=[t for t in texts if diff(tok,t)>0]
    print(name.ljust(18), "texts with differing tokens: %d of %d"%(len(bad),len(texts)), " tokens:", sum(diff(tok,t) for t in bad), flush=True)
