# Prefetch matrix: levels declared before their data

Written before any data at the level it names. The analysis for each is the one
`tools/memory/analyze_matrix.py` already runs; nothing below changes it.

## 2026-10-24, during the main matrix: 5 GB balloon (run suffix `-b5`)

The 4 GB level rarely had the weights out at Send: the phone had about 3 GB free before the
balloon and the three models are 0.7 to 1.8 GB. A 5 GB balloon is added to push them out.
Same three models, same four cells, 8 pairs, 2 blocks of depth 4.

## 2026-10-24 06:07: a model near the phone's RAM (run suffix `-big`)

Asked for by the owner, who pointed out the phone (11.5 GB RAM, 12 GB swap) was idle all
night, so small models stayed resident. Qwen3.5-9B-Q8_0.gguf (9.5 GB of weights) is run with
no balloon, because the model itself is the pressure. Same four cells, 8 pairs, 2 blocks of
depth 4, same seed. Run before the 5 GB level. If the model cannot load or is killed, that
is recorded as the result for the cell, not retried with other settings.
