## LFM2.5-2.6B-QAD-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:npu | passed | 5 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| cpu:cpu | passed | 55 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:npu, cpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 375, mean 375, max 1025, total 12378.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 569.4 | 132 | 25.8 | 84% | 100% | 30/31 | 30/31 |
| npu:npu | 599.8 | 1655 | 20.9 | 99% | 105% | 0/0 | 0/0 |

Decode share of single turns: 36.1% to 99.8%.

Fastest cpu:cpu, npu:npu +5.3%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| cpu:cpu | npu:npu | 33 | +5.3% | +5.3% | 14 of 33 | 0, 0.0 / 0, 0.0 | 106.2 / 74.4 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 38.7 | 37.1 | 25.8 | 25.5 | 25.2 | 24.9 | 24.9 |
| npu:npu | 47.6 | 47.9 | 20.9 | 20.8 | 20.8 | 20.7 | 20.6 |

Shortest decode step 26.28 ms, longest 77.40 ms.

Constant-rate projection from the 64-token run (r7) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| cpu:cpu | 553.4 | 569.4 | 1.03 | 100% | 100% |
| npu:npu | 601.4 | 599.8 | 1.00 | 109% | 105% |

