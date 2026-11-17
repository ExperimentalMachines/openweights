"""runner.txt for pd_bench_mtk's NPU from an exporter config.json (mtk/<chip>/config.json): the
runner block of the variant with the given window, as key=value lines, lists comma-separated.

  python3 runner_txt.py config.json 4096 > runner.txt
"""
import json
import sys

cfg = json.load(open(sys.argv[1]))
window = int(sys.argv[2])
variants = [v for v in cfg["variants"] if v.get("context") == window]
if len(variants) != 1:
    sys.exit(f"{len(variants)} variants with window {window}")
for key, value in variants[0]["runner"].items():
    if isinstance(value, list):
        value = ",".join(str(x) for x in value)
    print(f"{key}={value}")
