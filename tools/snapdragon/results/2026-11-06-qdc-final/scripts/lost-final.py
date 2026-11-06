import sys,json,collections,re
sys.path.insert(0,'tools/eval/bench'); sys.argv=['x']
import compare_limit_turns as L
D='tools/eval/results/decisions/qdc-sm8650-shipped-'
good=lambda r:r['g']['correct'] and r['end']!='cut while thinking'
for m in ('LFM2.5-1.2B-Instruct-8da4w-gptq-4k','LFM2.5-1.2B-Instruct-Q4_K_M'):
    _,O=L.load(D+'new-'+m+'.jsonl'); _,N=L.load(D+'final-'+m+'.jsonl')
    x={r['id']:r for r in O}; y={r['id']:r for r in N}
    print('==',m)
    sentence=sum('No search engine could be reached' in (r.get('shown') or '') for r in N)
    keys=[k for k in N[0].keys()]
    print('  keys',keys[:40])
    print('  sentence replies',sentence,'| identical raw',sum(x[i]['raw']==y[i]['raw'] for i in x if i in y))
    def calls(r):
        c=r.get('tool_calls') or r.get('calls') or r.get('steps') or []
        return len(c) if isinstance(c,list) else c
    fail=lambda r:'No search provider' in json.dumps(r)
    print('  rows touching a failed search: earlier',sum(fail(r) for r in O),'final',sum(fail(r) for r in N))
    prov=lambda R:collections.Counter(p for r in R for p in re.findall(r'from (DuckDuckGo|Brave|Yahoo)',json.dumps(r)))
    print('  providers earlier',dict(prov(O)),'final',dict(prov(N)))
    lament=re.compile(r"real.?time|up.?to.?date|\blive\b",re.I)
    print('  need-search correct earlier',sum(r['need'] is True and good(r) for r in O),'final',sum(r['need'] is True and good(r) for r in N),'| no-need correct earlier',sum(r['need'] is not True and good(r) for r in O),'final',sum(r['need'] is not True and good(r) for r in N))
    ids=[i for i in x if i in y]
    for lab,sel in (('lost',[i for i in ids if good(x[i]) and not good(y[i])]),('won',[i for i in ids if good(y[i]) and not good(x[i])])):
        for i in sel:
            print('  ',lab,str(y[i]['need'])[:5].ljust(5),y[i]['question'][:46].ljust(46),'|',repr((x[i]['shown'] or '')[:62]),'->',repr((y[i]['shown'] or '')[:72]))
