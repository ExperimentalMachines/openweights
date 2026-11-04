#!/usr/bin/env python3
"""One line per variant: how each last-pass reply would read."""
import json, sys, re, statistics as st
sys.path.insert(0, 'tools/eval/bench'); sys.path.insert(0, sys.argv[1])
sys.argv, files = ['x'], sys.argv[2:]
import lastround as L, grade_decisions as g
for f in files:
    rows = [json.loads(l) for l in open(f)]
    c = dict(n=len(rows), clean=0, spill=0, cut=0, runon=0, call=0, correct=0, couldnt=0, ctxfull=0, err=0); gen = []
    for r in rows:
        raw = r['raw']; shown = L.strip(L.parse(raw)); p = r['passes'][-1]
        gen.append(sum(x['gen'] for x in r['passes']))
        spill = bool(shown) and bool(L.SPILL.search(shown))
        closed = L.CLOSE in raw
        stop = p['stop']
        if p['err']: c['err'] += 1
        ans = shown
        ok = g.correct(g.plain(ans), r['answers']) if r['answers'] else None
        if stop != 0 and not closed: c['cut'] += 1
        elif not shown: c['call'] += 1
        else:
            if stop != 0: c['runon'] += 1
            if spill: c['spill'] += 1
            if stop == 0 and not spill: c['clean'] += 1
            if ok: c['correct'] += 1
            if g.CANNOT.search(g.plain(ans)): c['couldnt'] += 1
        if stop == 2: c['ctxfull'] += 1
    print(f.split('/')[-1].ljust(22), ' '.join(f'{k}={v}' for k, v in c.items()), 'median tokens', int(st.median(gen)))
