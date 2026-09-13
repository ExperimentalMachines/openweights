import re
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
