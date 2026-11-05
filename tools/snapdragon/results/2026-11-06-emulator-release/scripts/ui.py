#!/usr/bin/env python3
"""Prints a label's centre from a uiautomator dump, or every labelled node with --list."""
import html, re, sys
x = open(sys.argv[1], encoding='utf8', errors='replace').read()
nodes = []
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0)
    g = lambda k: html.unescape((re.search(rf' {k}="([^"]*)"', n) or [None, ''])[1])
    b = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', g('bounds'))
    if b: nodes.append((g('text'), g('content-desc'), [int(v) for v in b.groups()]))
if sys.argv[2] == '--list':
    for t, d, b in nodes:
        if t or d: print(repr(t[:int(sys.argv[3]) if len(sys.argv) > 3 else 300]), repr(d[:70]), b)
else:
    for t, d, b in nodes:
        if sys.argv[2] in (t, d) or (len(sys.argv) > 3 and sys.argv[2] in t + d):
            print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); break
