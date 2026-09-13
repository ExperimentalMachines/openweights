import json, os, sys
from tokenizers import Tokenizer
tok = Tokenizer.from_file(os.path.expanduser('~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct/tokenizer.json'))
d = json.load(open('eval/prompt_dump.json'))
tools = d['tools']
print("tools type", type(tools), len(tools))
def tool_json(t):
    return json.dumps(t, ensure_ascii=False, separators=(', ', ': ')) if not isinstance(t, str) else t
def render(tools_sel, user):
    system = d['system'] + ("\nList of tools: [" + ", ".join(tool_json(t) for t in tools_sel) + "]" if tools_sel else "")
    return "<|startoftext|><|im_start|>system\n" + system + "<|im_end|>\n<|im_start|>user\n" + user + "<|im_end|>\n<|im_start|>assistant\n"
def warm_piece(text, limit=800):
    most = min(800, limit)
    if len(text) <= most: return text
    w = text[:most]
    nl = w.rfind('\n')
    if nl > 0: return w[:nl+1]
    sp = w.rfind(' ')
    if sp > 0: return w[:sp+1]
    return w
def pieces(prompt, call_chars=1600):
    rest = prompt; out=[]
    while len(rest) > call_chars:
        p = warm_piece(rest, call_chars); out.append(p); rest = rest[len(p):]
    out.append(rest); return out
def enc(s): return tok.encode(s, add_special_tokens=False).ids
def report(name, prompt):
    whole = enc(prompt)
    ps = pieces(prompt)
    pieced = [i for p in ps for i in enc(p)]
    # count boundary differences via difflib
    import difflib
    sm = difflib.SequenceMatcher(a=whole, b=pieced, autojunk=False)
    diff = sum(max(i2-i1, j2-j1) for tag,i1,i2,j1,j2 in sm.get_opcodes() if tag!='equal')
    print(f"{name}: chars={len(prompt)} whole={len(whole)} pieced={len(pieced)} pieces={len(ps)} differing_tokens={diff}")
    for tag,i1,i2,j1,j2 in sm.get_opcodes():
        if tag!='equal':
            print("   ", tag, repr(tok.decode(whole[i1:i2])), [tok.decode([x]) for x in whole[i1:i2]], "->", [tok.decode([x]) for x in pieced[j1:j2]])
names = [ (t.get('name') if isinstance(t,dict) else str(t)[:30]) for t in tools]
print(names)
ws = [t for t in tools if isinstance(t,dict) and t.get('name')=='web_search'] or tools[:1]
sp = [t for t in tools if isinstance(t,dict) and t.get('name') in ('web_search','show_pictures')]
user = d['firstUser'] if isinstance(d['firstUser'], str) else json.dumps(d['firstUser'])
report("no tools", render([], user))
report("web_search only", render(ws, user))
report("web_search+show_pictures", render(sp, user))
report("all tools", render(tools, user))
