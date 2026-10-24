# Qwen3.5-9B-Q8_0, no balloon: the benchmark's own kill line per cell

No cell produced a report; the "done" in cells.tsv means the process was gone. Other victims are
counted, not named: the raw log names the owner's apps and stays out of git.

```
b0-multi-false: 1792853032.458 Kill 'io.github.alpharomercoma.openweights.core.engine.test' (17093), uid 10368, oom_score_adj 0 to free 73104kB rss, 8273232kB swap; reason: memavailable critical lower
  other kills in the log: 101
b0-multi-true: 1792852831.718 Kill 'io.github.alpharomercoma.openweights.core.engine.test' (4688), uid 10368, oom_score_adj 0 to free 78268kB rss, 8216704kB swap; reason: memavailable critical lower
  other kills in the log: 68
b0-single-false: 1792852923.873 Kill 'io.github.alpharomercoma.openweights.core.engine.test' (11193), uid 10368, oom_score_adj 0 to free 88932kB rss, 8172648kB swap; reason: memavailable critical lower
  other kills in the log: 85
b0-single-true: 1792852731.812 Kill 'io.github.alpharomercoma.openweights.core.engine.test' (22020), uid 10368, oom_score_adj 0 to free 75336kB rss, 8028616kB swap; reason: memavailable critical lower
  other kills in the log: 193
```
