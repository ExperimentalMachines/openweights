import sys,re
out=[]
for l in open(sys.argv[1],errors='replace'):
    m=re.match(r"^'(.*)' '.*' \[\d+",l)
    if m and len(m.group(1))>2 and not re.search(r'tok/s|type / for|Instruct|LFM3-1|What is the|Who wrote|Who won|Reading the prompt|CPU|ctx|↑|^\d+(\.\d+)?s$|Generating|Thought for|Battery',m.group(1)): out.append(m.group(1))
print(' || '.join(out) or open(sys.argv[1],errors='replace').read()[:100].replace('\n',' '))
