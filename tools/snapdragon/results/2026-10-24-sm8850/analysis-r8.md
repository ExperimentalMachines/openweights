## Qwen3-0.6B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:cpu | passed | 0 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| cpu:cpu | passed | 50 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:cpu, cpu:cpu, npu:npu, gpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:cpu | 257.8 | 2974 | 49.2 | 98% | 75% | 11/12 | 11/12 |
| npu:npu | 292.7 | 2964 | 43.2 | 99% | 85% | 33/33 | 33/33 |
| gpu:cpu | 327.6 | 395 | 42.0 | 91% | 95% | 33/33 | 33/33 |
| cpu:cpu | 344.4 | 211 | 43.1 | 84% | 100% | 27/28 | 27/28 |

Decode share of single turns: 31.4% to 99.7%.

Same decoder, different cells: CPU decode cpu:cpu 43.1, gpu:cpu 42.0, npu:cpu 49.2 tok/s, a spread of 17%.

Fastest npu:cpu, npu:npu +13.5%, gpu:cpu +27.1%, cpu:cpu +33.6%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +33.6% | +33.6% | 1 of 33 | 0, 0.0 / 0, 0.0 | 105.5 / 105.1 °C |
| npu:cpu | gpu:cpu | 33 | +27.1% | +27.1% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.5 / 97.8 °C |
| npu:cpu | npu:npu | 33 | +13.5% | +13.5% | 4 of 33 | 0, 0.0 / 0, 0.0 | 105.5 / 81.7 °C |
| cpu:cpu | gpu:cpu | 33 | -4.9% | -4.9% | 24 of 33 | 0, 0.0 / 0, 0.0 | 105.1 / 97.8 °C |
| cpu:cpu | npu:npu | 33 | -15.0% | -15.0% | 17 of 33 | 0, 0.0 / 0, 0.0 | 105.1 / 81.7 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 18.7 | 20.6 | 43.1 | 42.6 | 40.8 | 36.9 | 39.2 |
| gpu:cpu | 26.9 | 21.5 | 42.0 | 42.4 | 40.4 | 36.7 | 39.2 |
| npu:cpu | 22.2 | 18.0 | 49.2 | 51.3 | 47.5 | 42.6 | 43.4 |
| npu:npu | 19.1 | 23.4 | 43.2 | 43.1 | 42.8 | 41.0 | 41.3 |

Shortest decode step 7.58 ms, longest 96.78 ms.

Constant-rate projection from the 64-token run (r4) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:cpu | 204.3 | 257.8 | 1.26 | 72% | 75% |
| npu:npu | 278.3 | 292.7 | 1.05 | 98% | 85% |
| gpu:cpu | 246.0 | 327.6 | 1.33 | 86% | 95% |
| cpu:cpu | 284.9 | 344.4 | 1.21 | 100% | 100% |

## Qwen3-4B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 20 | 0 | 0 |

Cells in the order they ran: npu:npu, npu:cpu, cpu:cpu.

Common cohort: 20 turns in 5 conversations completed in every cell. Decode steps a turn: median 356.5, mean 351, max 1046, total 7027.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 506.5 | 628 | 14.2 | 98% | 56% | 20/20 | 20/20 |
| npu:cpu | 696.6 | 609 | 10.2 | 98% | 76% | 20/20 | 20/20 |
| cpu:cpu | 911.9 | 46 | 9.1 | 85% | 100% | 20/20 | 20/20 |

Decode share of single turns: 27.5% to 99.7%.

Same decoder, different cells: CPU decode cpu:cpu 9.1, npu:cpu 10.2 tok/s, a spread of 13%.

Fastest npu:npu, npu:cpu +37.5%, cpu:cpu +80.1%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 20 | +30.9% | +19.6% | 2 of 20 | 12, 96.9 / 23, 194.6 | 107.0 / 106.6 °C |
| npu:cpu | npu:npu | 33 | -20.5% | +1.5% | 23 of 33 | 37, 290.6 / 0, 0.0 | 107.0 / 98.5 °C |
| cpu:cpu | npu:npu | 20 | -44.5% | -29.4% | 19 of 20 | 23, 194.6 / 0, 0.0 | 106.6 / 98.5 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 7 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 64.8 | 77.5 | 9.1 | 12.3 | 8.2 | 6.9 | 7.1 |
| npu:cpu | 70.1 | 79.0 | 10.2 | 12.2 | 8.2 | 11.1 | 11.8 |
| npu:npu | 61.6 | 68.9 | 14.2 | 14.3 | 14.0 | 13.6 | 14.0 |

Shortest decode step 46.72 ms, longest 15937.73 ms.

Constant-rate projection from the 64-token run (r6) on the 20 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 482.8 | 506.5 | 1.05 | 79% | 56% |
| npu:cpu | 493.2 | 696.6 | 1.41 | 81% | 76% |
| cpu:cpu | 610.3 | 911.9 | 1.49 | 100% | 100% |

