#!/usr/bin/env python3
"""Rebuilds the turns that reached the round limit, up to the pass that was told to stop."""
import json, sys
OLD = 'tools/eval/results/decisions/qdc-full-decisions-LFM3-1.7B-Q4_K_M-driven-search-think.jsonl'
FIX = 'tools/eval/results/decisions/qdc-sm8650-budget-decisions-LFM3-1.7B-Q4_K_M-driven-search-think.jsonl'
def load(p):
    rows = [json.loads(l) for l in open(p)]
    return rows[0], {r['id']: r for r in rows[1:]}
hold, old = load(OLD); hfix, fix = load(FIX)
closing = sys.argv[1]; out = open(sys.argv[2], 'w')
ids = [i for i in old if len(old[i]['passes']) >= 5 or len(fix.get(i, {'passes': []})['passes']) >= 5]
n = 0
for i in ids:
    r = fix[i] if len(fix[i]['passes']) >= 5 else old[i]
    ex = hfix['date_exchange']
    m = [{'role': 'system', 'content': hfix['system']}]
    for k, t in enumerate(ex):
        m.append({'role': 'user' if k % 2 == 0 else 'assistant', 'content': t})
    m.append({'role': 'user', 'content': r['question']})
    calls = r['calls']
    for k, c in enumerate(calls):
        q = json.loads(c['arguments']).get('query', '')
        m.append({'role': 'assistant', 'content': f'<think>I will search for this.</think><tool_call>\n<function=web_search>\n<parameter=query>\n{q}\n</parameter>\n</function>\n</tool_call>'})
        res = c.get('result') or ''
        if k == len(calls) - 1:
            res += '\n\n' + closing
        m.append({'role': 'tool', 'content': res, 'tool_call_id': 'c%d' % k})
    out.write(json.dumps({'id': i, 'question': r['question'], 'answers': r['answers'], 'need': r['need'], 'messages': m}) + '\n'); n += 1
print(n, 'rows', 'max calls', max(len((fix[i] if len(fix[i]['passes']) >= 5 else old[i])['calls']) for i in ids))
