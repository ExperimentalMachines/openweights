## Qwen3-1.7B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:npu, npu:cpu, cpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 441.1 | 1726 | 28.7 | 98% | 68% | 33/33 | 33/33 |
| npu:cpu | 518.1 | 1671 | 24.4 | 99% | 80% | 33/33 | 33/33 |
| cpu:cpu | 648.9 | 112 | 22.9 | 84% | 100% | 33/33 | 33/33 |

Decode share of single turns: 30.8% to 99.7%.

Same decoder, different cells: CPU decode cpu:cpu 22.9, npu:cpu 24.4 tok/s, a spread of 7%.

Fastest npu:npu, npu:cpu +17.5%, cpu:cpu +47.1%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +25.3% | +25.3% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 107.3 °C |
| npu:cpu | npu:npu | 33 | -14.9% | -14.9% | 28 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 95.3 °C |
| cpu:cpu | npu:npu | 33 | -32.0% | -32.0% | 32 of 33 | 0, 0.0 / 0, 0.0 | 107.3 / 95.3 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 33.7 | 42.3 | 22.9 | 24.2 | 22.5 | 21.1 | 20.8 |
| npu:cpu | 38.1 | 39.8 | 24.4 | 25.6 | 24.1 | 22.9 | 22.4 |
| npu:npu | 34.1 | 34.2 | 28.7 | 28.6 | 28.4 | 27.8 | 28.1 |

Shortest decode step 22.43 ms, longest 88.42 ms.

Constant-rate projection from the 64-token run (r4) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 432.5 | 441.1 | 1.02 | 87% | 68% |
| npu:cpu | 398.0 | 518.1 | 1.30 | 80% | 80% |
| cpu:cpu | 498.4 | 648.9 | 1.30 | 100% | 100% |

