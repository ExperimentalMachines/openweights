## LFM2.5-2.6B-QAD-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: cpu:cpu, npu:cpu, npu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 375, mean 375, max 1025, total 12378.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 601.8 | 1067 | 21.0 | 98% | 85% | 33/33 | 33/33 |
| npu:cpu | 678.4 | 1134 | 18.5 | 98% | 96% | 33/33 | 33/33 |
| cpu:cpu | 704.5 | 118 | 20.5 | 86% | 100% | 33/33 | 33/33 |

Decode share of single turns: 36.7% to 99.9%.

Same decoder, different cells: CPU decode cpu:cpu 20.5, npu:cpu 18.5 tok/s, a spread of 10%.

Fastest npu:npu, npu:cpu +12.7%, cpu:cpu +17.1%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +3.8% | +14.8% | 7 of 33 | 22, 118.3 / 11, 61.5 | 106.6 / 107.0 °C |
| npu:cpu | npu:npu | 33 | -11.3% | +7.4% | 7 of 33 | 22, 118.3 / 0, 0.0 | 106.6 / 96.9 °C |
| cpu:cpu | npu:npu | 33 | -14.6% | -6.4% | 25 of 33 | 11, 61.5 / 0, 0.0 | 107.0 / 96.9 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 37.0 | 44.2 | 20.5 | 24.3 | 22.5 | 21.7 | 21.5 |
| npu:cpu | 37.0 | 44.6 | 18.5 | 23.1 | 21.9 | 21.3 | 8.9 |
| npu:npu | 46.9 | 47.6 | 21.0 | 20.9 | 20.8 | 20.7 | 20.8 |

Shortest decode step 30.94 ms, longest 7921.14 ms.

Constant-rate projection from the 64-token run (r7) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 601.4 | 601.8 | 1.00 | 109% | 85% |
| npu:cpu | 453.6 | 678.4 | 1.50 | 82% | 96% |
| cpu:cpu | 553.4 | 704.5 | 1.27 | 100% | 100% |

## Qwen3-1.7B-Q8_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:npu, npu:cpu, cpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 528.1 | 1824 | 23.9 | 99% | 62% | 33/33 | 33/33 |
| npu:cpu | 634.4 | 1664 | 19.9 | 99% | 74% | 33/33 | 33/33 |
| cpu:cpu | 851.8 | 119 | 16.5 | 88% | 100% | 33/33 | 33/33 |

Decode share of single turns: 35.7% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 16.5, npu:cpu 19.9 tok/s, a spread of 20%.

Fastest npu:npu, npu:cpu +20.1%, cpu:cpu +61.3%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +34.3% | +17.1% | 2 of 33 | 8, 53.7 / 29, 172.0 | 107.0 / 107.0 °C |
| npu:cpu | npu:npu | 33 | -16.8% | -9.1% | 23 of 33 | 8, 53.7 / 0, 0.0 | 107.0 / 96.9 °C |
| cpu:cpu | npu:npu | 33 | -38.0% | -22.3% | 30 of 33 | 29, 172.0 / 0, 0.0 | 107.0 / 96.9 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 38.8 | 44.5 | 16.5 | 22.4 | 21.0 | 19.6 | 20.6 |
| npu:cpu | 39.4 | 44.1 | 19.9 | 22.1 | 21.3 | 15.1 | 21.1 |
| npu:npu | 38.8 | 41.7 | 23.9 | 23.9 | 23.8 | 23.3 | 23.4 |

Shortest decode step 30.23 ms, longest 12145.90 ms.

Constant-rate projection from the 64-token run (r6) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 610.2 | 528.1 | 0.87 | 105% | 62% |
| npu:cpu | 490.8 | 634.4 | 1.29 | 85% | 74% |
| cpu:cpu | 579.0 | 851.8 | 1.47 | 100% | 100% |

## google_gemma-3-1b-it-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:cpu | passed | 0 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| cpu:cpu | passed | 45 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:cpu, cpu:cpu, npu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 436, mean 402, max 1141, total 13256.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:cpu | 221.6 | 3515 | 60.8 | 98% | 76% | 7/9 | 7/8 |
| npu:npu | 288.3 | 3448 | 46.6 | 99% | 99% | 29/30 | 29/30 |
| cpu:cpu | 290.0 | 301 | 53.3 | 86% | 100% | 27/28 | 27/28 |

Decode share of single turns: 36.3% to 99.7%.

Same decoder, different cells: CPU decode cpu:cpu 53.3, npu:cpu 60.8 tok/s, a spread of 14%.

Fastest npu:cpu, npu:npu +30.1%, cpu:cpu +30.8%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +30.8% | +30.8% | 1 of 33 | 0, 0.0 / 0, 0.0 | 105.5 / 104.3 °C |
| npu:cpu | npu:npu | 33 | +30.1% | +30.1% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.5 / 78.7 °C |
| cpu:cpu | npu:npu | 33 | -0.6% | -0.6% | 18 of 33 | 0, 0.0 / 0, 0.0 | 104.3 / 78.7 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 13 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 18.7 | 18.2 | 53.3 | 52.3 | 52.2 | 51.0 | 50.3 |
| npu:cpu | 15.9 | 15.5 | 60.8 | 62.1 | 60.8 | 59.7 | 56.0 |
| npu:npu | 21.9 | 21.5 | 46.6 | 46.3 | 46.5 | 46.2 | 46.2 |

Shortest decode step 12.85 ms, longest 126.72 ms.

Constant-rate projection from the 64-token run (r4) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:cpu | 197.0 | 221.6 | 1.13 | 77% | 76% |
| npu:npu | 286.4 | 288.3 | 1.01 | 112% | 99% |
| cpu:cpu | 255.3 | 290.0 | 1.14 | 100% | 100% |

