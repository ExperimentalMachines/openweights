#!/usr/bin/env python3
"""Regrades decision-suite runs from `raw`, because `answer` changed meaning between test builds.

Splits a reply the way the app does, and says how each turn ended: answered, cut off while still
thinking, ran to the token cap in the visible reply, or ended on a call that could not run.
`SPILL` is a rough pattern for reasoning in the visible reply, the same for every run.

    python3 tools/eval/bench/compare_limit_turns.py <run.jsonl> [<other.jsonl> ...]

Run from the repository root. See docs/research/llama-cpp-v080.md.
"""
import json, re, statistics as st, sys, math
sys.path.insert(0, 'tools/eval/bench')
import grade_decisions as g

OPEN, CLOSE = '<think>', '</think>'
MARKUP = [re.compile(r'<\|tool_call_start\|>.*?<\|tool_call_end\|>', re.S), re.compile(r'<tool_call>.*?</tool_call>', re.S)]
# Reasoning-shaped prose in the part of the reply the user reads. A rough count, the same for every run.
SPILL = re.compile(r"\bWait\b|\? No\b|\bno, that|\bLet me (check|think|re|correct|search|see)|\bI need to\b|\bThe user\b|\bHmm\b|\bI should\b|\bMaybe\b|the (tool|search) (returned|call|tool|results? (is|are) not)|tool calls?\b", re.I)
UNCLOSED = [re.compile(r'<\|tool_call_start\|>.*\Z', re.S), re.compile(r'<tool_call>.*\Z', re.S)]

def repeated(t):
    r = t; seen = False
    while True:
        n = r.lstrip('\n')
        if n.startswith(CLOSE): r = n[len(CLOSE):]; seen = True
        elif n.startswith(OPEN + '\n') and CLOSE in n: r = n.split(CLOSE, 1)[1]; seen = True
        else: return n if seen else r

def parse(raw):
    o, c = raw.find(OPEN), raw.find(CLOSE)
    if o < 0 and c < 0: return raw
    if c >= 0 and (o < 0 or c < o):
        return repeated(raw[c + len(CLOSE):]).removeprefix('\n')
    e = raw.find(CLOSE, o + len(OPEN))
    if e < 0: return raw[:o]
    return (raw[:o] + repeated(raw[e + len(CLOSE):])).removeprefix('\n')

def strip(t):
    for p in MARKUP: t = p.sub('', t)
    for p in UNCLOSED: t = p.sub('', t)
    return t.strip()

def load(path):
    rows = [json.loads(l) for l in open(path)]
    head = rows[0]; rows = rows[1:]
    for r in rows:
        raw = r.get('raw') or ''
        r['shown'] = strip(parse(raw))
        last = r['passes'][-1]['stop'] if r['passes'] else 'NONE'
        closed = CLOSE in raw
        r['end'] = ('cut while thinking' if last == 'MAX_TOKENS' and not closed else
                    'run-on answer' if last == 'MAX_TOKENS' else
                    'ended on a call' if not r['shown'] else
                    'answered' if last == 'END_OF_TURN' else last)
        # Five passes or more. Nearly always a turn out of rounds, but not proof of one: a retry
        # after a false denial gets there on three searches (toolqa_easy-agenda rows).
        r['limit'] = len(r['passes']) >= 5
        r['spill'] = bool(r['shown']) and r['end'] != 'cut while thinking' and bool(SPILL.search(r['shown']))
        r['g'] = g.grade_row({**r, 'answer': r['shown']})
    return head, rows

def sign(a, b):
    n = a + b
    if not n: return 1.0
    return min(1.0, 2 * sum(math.comb(n, i) for i in range(min(a, b) + 1)) / 2 ** n)

def summary(rows):
    ms = [r['ms'] / 1000 for r in rows]; lim = [r for r in rows if r['limit']]
    ct = lambda f: sum(1 for r in rows if f(r))
    q = sorted(ms)
    return {
        'rows': len(rows), 'errors': ct(lambda r: r['g']['error']),
        'correct': ct(lambda r: r['g']['correct']),
        'correct, not cut off': ct(lambda r: r['g']['correct'] and r['end'] != 'cut while thinking'),
        'correct need': ct(lambda r: r['need'] is True and r['g']['correct']),
        'correct no need': ct(lambda r: r['need'] is False and r['g']['correct']),
        'correct boundary': ct(lambda r: r['need'] is None and r['g']['correct']),
        'searched need': ct(lambda r: r['need'] is True and r['g']['searched']),
        'searched no need': ct(lambda r: r['need'] is False and r['g']['searched']),
        'fabricated': ct(lambda r: r['g']['fabricated']), 'said it could not': ct(lambda r: r['g']['abstained']),
        'quoted prompt': ct(lambda r: r['g']['meta']),
        'turns at limit': len(lim), 'correct at limit': sum(1 for r in lim if r['g']['correct']),
        'cut while thinking': ct(lambda r: r['end'] == 'cut while thinking'),
        'run-on answer': ct(lambda r: r['end'] == 'run-on answer'),
        'ended on a call': ct(lambda r: r['end'] == 'ended on a call'),
        'visible reasoning': ct(lambda r: r['spill']),
        'visible reasoning, at limit': sum(1 for r in lim if r['spill']),
        'finished, no pattern, at limit': sum(1 for r in lim if r['end'] == 'answered' and not r['spill']),
        'cut, at limit': sum(1 for r in lim if r['end'] == 'cut while thinking'),
        'run-on, at limit': sum(1 for r in lim if r['end'] == 'run-on answer'),
        'on a call, at limit': sum(1 for r in lim if r['end'] == 'ended on a call'),
        'answered, at limit': sum(1 for r in lim if r['end'] == 'answered'),
        'median s': round(st.median(ms), 1), 'p90 s': round(q[int(.9 * len(q))], 1), 'total min': round(sum(ms) / 60, 1),
        'median s at limit': round(st.median([r['ms'] / 1000 for r in lim]), 1) if lim else None,
        'min at limit': round(sum(r['ms'] for r in lim) / 60000, 1),
        'searches': sum(len(r['calls']) for r in rows),
    }

def main():
    runs = [(p, *load(p)) for p in sys.argv[1:]]
    sums = [summary(r) for _, _, r in runs]
    for k in sums[0]:
        print(k.ljust(28), *[str(s[k]).rjust(8) for s in sums])
    print('soc'.ljust(28), *[str(h['soc']).rjust(8) for _, h, _ in runs])
    if len(runs) == 2:
        a = {r['id']: r for r in runs[0][2]}; b = {r['id']: r for r in runs[1][2]}
        ids = [i for i in a if i in b]
        oa = [i for i in ids if a[i]['g']['correct'] and not b[i]['g']['correct']]
        ob = [i for i in ids if b[i]['g']['correct'] and not a[i]['g']['correct']]
        print(f'paired on {len(ids)}: only first {len(oa)}, only second {len(ob)}, sign test p={sign(len(oa), len(ob)):.3f}')
        both = [i for i in ids if a[i]['limit'] and b[i]['limit']]
        print(f'at limit in both {len(both)}, first only {sum(a[i]["limit"] and not b[i]["limit"] for i in ids)}, second only {sum(b[i]["limit"] and not a[i]["limit"] for i in ids)}')
        same = [i for i in ids if a[i]['raw'] == b[i]['raw']]
        print(f'byte-identical raw {len(same)} of {len(ids)}')
        print('\nrows that changed a correct verdict, with the passes each took:')
        for i in oa + ob:
            print(' ', 'lost' if i in oa else 'won ', i[:34].ljust(34), len(a[i]['passes']), len(b[i]['passes']), a[i]['end'], '|', b[i]['end'], '|', b[i]['question'][:44])

if __name__ == '__main__':
    main()
