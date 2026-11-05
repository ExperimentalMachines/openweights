#!/usr/bin/env python3
"""Old build against new build, as shipped, on one phone.

    python3 tools/eval/bench/compare_shipped.py <model-base> [...]

Run from the repository root. See docs/research/llama-cpp-v080.md.
"""
import sys,json,collections,statistics as st
sys.path.insert(0,'tools/eval/bench'); models=sys.argv[1:]; sys.argv=['x']
import compare_limit_turns as L
D='tools/eval/results/decisions/qdc-sm8650-shipped-'
def s(R):
    c=lambda f:sum(1 for r in R if f(r))
    q=sorted(r['ms']/1000 for r in R)
    return dict(rows=len(R),errors=c(lambda r:r['g']['error']),correct=c(lambda r:r['g']['correct'] and r['end']!='cut while thinking'),need=c(lambda r:r['need'] is True and r['g']['correct']),noneed=c(lambda r:r['need'] is False and r['g']['correct']),boundary=c(lambda r:r['need'] is None and r['g']['correct']),searched_need=c(lambda r:r['need'] is True and r['g']['searched']),searched_noneed=c(lambda r:r['need'] is False and r['g']['searched']),fabricated=c(lambda r:r['g']['fabricated']),could_not=c(lambda r:r['g']['abstained']),quoted=c(lambda r:r['g']['meta']),cut=c(lambda r:r['end']=='cut while thinking'),runon=c(lambda r:r['end']=='run-on answer'),oncall=c(lambda r:r['end']=='ended on a call'),spill=c(lambda r:r['spill']),five=c(lambda r:r['limit']),median=round(st.median(q),1),p90=round(q[int(.9*len(q))],1),minutes=round(sum(q)/60,1),searches=sum(len(r['calls']) for r in R),app_search=c(lambda r:any(x.get('host') for x in r['calls'])))
good=lambda r:r['g']['correct'] and r['end']!='cut while thinking'
for m in models:
    ho,O=L.load(D+'old-'+m+'.jsonl'); hn,N=L.load(D+'new-'+m+'.jsonl')
    a,b=s(O),s(N)
    print('==',m,'| system changed:',ho['system_sha1']!=hn['system_sha1'],'| tools changed:',ho['tools_sha1']!=hn['tools_sha1'],'| thermal',collections.Counter(r['thermal'] for r in O).most_common(2),collections.Counter(r['thermal'] for r in N).most_common(2))
    for k in a: print('  ',k.ljust(16),str(a[k]).rjust(7),str(b[k]).rjust(7))
    x={r['id']:r for r in O}; y={r['id']:r for r in N}; ids=[i for i in x if i in y]
    oa=[i for i in ids if good(x[i]) and not good(y[i])]; ob=[i for i in ids if good(y[i]) and not good(x[i])]
    print('   paired on',len(ids),': old only',len(oa),'new only',len(ob),'p=%.3f'%L.sign(len(oa),len(ob)),'| identical raw',sum(x[i]['raw']==y[i]['raw'] for i in ids))
    for i in oa[:12]: print('     lost',y[i]['question'][:44].ljust(44),'|',repr(x[i]['shown'][:60]),'->',repr(y[i]['shown'][:60]))
    for i in ob[:12]: print('     won ',y[i]['question'][:44].ljust(44),'|',repr(y[i]['shown'][:70]))
