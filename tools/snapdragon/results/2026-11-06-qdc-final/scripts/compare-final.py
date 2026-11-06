import sys,json,collections,statistics as st
sys.path.insert(0,'tools/eval/bench'); sys.argv=['x']
import compare_limit_turns as L
D='tools/eval/results/decisions/qdc-sm8650-shipped-'
def s(R):
    c=lambda f:sum(1 for r in R if f(r))
    q=sorted(r['ms']/1000 for r in R)
    return dict(rows=len(R),errors=c(lambda r:r['g']['error']),correct=c(lambda r:r['g']['correct'] and r['end']!='cut while thinking'),median=round(st.median(q),1),p90=round(q[int(.9*len(q))],1))
good=lambda r:r['g']['correct'] and r['end']!='cut while thinking'
for m in ('LFM2.5-1.2B-Instruct-8da4w-gptq-4k','LFM2.5-1.2B-Instruct-Q4_K_M'):
    ho,O=L.load(D+'new-'+m+'.jsonl'); hn,N=L.load(D+'final-'+m+'.jsonl')
    a,b=s(O),s(N)
    print('==',m,'| system changed:',ho['system_sha1']!=hn['system_sha1'],'| tools changed:',ho['tools_sha1']!=hn['tools_sha1'])
    for k in a: print('  ',k.ljust(10),str(a[k]).rjust(7),str(b[k]).rjust(7))
    print('   ends   ',collections.Counter(r['end'] for r in O).most_common(5)); print('   ends   ',collections.Counter(r['end'] for r in N).most_common(5))
    x={r['id']:r for r in O}; y={r['id']:r for r in N}; ids=[i for i in x if i in y]
    oa=[i for i in ids if good(x[i]) and not good(y[i])]; ob=[i for i in ids if good(y[i]) and not good(x[i])]
    print('   paired',len(ids),'earlier only',len(oa),'final only',len(ob),'p=%.3f'%L.sign(len(oa),len(ob)))
