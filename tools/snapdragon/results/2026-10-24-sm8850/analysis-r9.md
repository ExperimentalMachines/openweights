## LFM2.5-1.2B-Instruct-QAD-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: cpu:cpu, npu:npu, npu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 411, mean 392, max 1083, total 12946.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:cpu | 271.1 | 2758 | 48.6 | 98% | 86% | 33/33 | 33/33 |
| cpu:cpu | 315.3 | 252 | 48.5 | 85% | 100% | 33/33 | 33/33 |
| npu:npu | 321.4 | 2610 | 40.9 | 99% | 102% | 33/33 | 33/33 |

Decode share of single turns: 38.2% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 48.5, npu:cpu 48.6 tok/s, a spread of 0%.

Fastest npu:cpu, cpu:cpu +16.3%, npu:npu +18.6%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +16.3% | +16.3% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 106.5 °C |
| npu:cpu | npu:npu | 33 | +18.6% | +18.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 93.8 °C |
| cpu:cpu | npu:npu | 33 | +1.9% | +1.9% | 17 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 93.8 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 13 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 18.6 | 20.0 | 48.5 | 49.2 | 48.7 | 46.9 | 46.3 |
| npu:cpu | 18.6 | 19.8 | 48.6 | 49.2 | 48.2 | 47.4 | 46.4 |
| npu:npu | 24.3 | 24.3 | 40.9 | 41.1 | 40.8 | 40.4 | 40.3 |

Shortest decode step 14.27 ms, longest 58.01 ms.

Constant-rate projection from the 64-token run (r4) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:cpu | 202.5 | 271.1 | 1.34 | 76% | 86% |
| cpu:cpu | 267.2 | 315.3 | 1.18 | 100% | 100% |
| npu:npu | 310.0 | 321.4 | 1.04 | 116% | 102% |

## LFM2.5-2.6B-QAD-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 29 | 0 | 0 |

Cells in the order they ran: npu:cpu, npu:npu.

Common cohort: 29 turns in 8 conversations completed in every cell. Decode steps a turn: median 375, mean 379, max 1025, total 10986.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:cpu | 526.6 | 1060 | 21.3 | 98% | n/a | 29/29 | 29/29 |
| npu:npu | 537.8 | 1018 | 20.8 | 98% | n/a | 29/29 | 29/29 |

Decode share of single turns: 84.8% to 99.7%.

Fastest npu:cpu, npu:npu +2.1%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | npu:npu | 29 | +2.1% | +2.1% | 14 of 29 | 0, 0.0 / 0, 0.0 | 106.9 / 96.5 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 10 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| npu:cpu | 37.4 | 47.1 | 21.3 | 21.7 | 20.6 | 20.2 | 19.7 |
| npu:npu | 46.7 | 47.9 | 20.8 | 20.8 | 20.7 | 20.6 | 20.6 |

Shortest decode step 31.26 ms, longest 73.37 ms.

Constant-rate projection from the 64-token run (r7) on the 29 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:cpu | 399.2 | 526.6 | 1.32 | n/a | n/a |
| npu:npu | 534.5 | 537.8 | 1.01 | n/a | n/a |

## Llama-3.2-1B-Instruct-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:npu, cpu:cpu, npu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 393, mean 367, max 1023, total 12107.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 330.7 | 2305 | 37.2 | 98% | 85% | 33/33 | 33/33 |
| npu:cpu | 342.0 | 2413 | 35.9 | 99% | 88% | 33/33 | 33/33 |
| cpu:cpu | 390.8 | 188 | 36.7 | 84% | 100% | 33/33 | 33/33 |

Decode share of single turns: 34.6% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 36.7, npu:cpu 35.9 tok/s, a spread of 2%.

Fastest npu:npu, npu:cpu +3.4%, cpu:cpu +18.2%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +14.2% | +14.2% | 2 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 106.2 °C |
| npu:cpu | npu:npu | 33 | -3.3% | -3.3% | 18 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 97.3 °C |
| cpu:cpu | npu:npu | 33 | -15.4% | -15.4% | 29 of 33 | 0, 0.0 / 0, 0.0 | 106.2 / 97.3 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 11 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 22.4 | 26.6 | 36.7 | 37.2 | 36.1 | 34.7 | 34.8 |
| npu:cpu | 25.6 | 27.3 | 35.9 | 36.5 | 35.2 | 34.0 | 33.6 |
| npu:npu | 25.7 | 26.1 | 37.2 | 37.3 | 36.6 | 35.9 | 36.8 |

Shortest decode step 16.47 ms, longest 67.42 ms.

Constant-rate projection from the 64-token run (r4) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 322.5 | 330.7 | 1.03 | 99% | 85% |
| npu:cpu | 247.3 | 342.0 | 1.38 | 76% | 88% |
| cpu:cpu | 326.4 | 390.8 | 1.20 | 100% | 100% |

## Llama-3.2-3B-Instruct-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:cpu, cpu:cpu, npu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 393, mean 367, max 1023, total 12107.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 733.5 | 797 | 16.8 | 98% | 64% | 33/33 | 33/33 |
| npu:cpu | 1014.1 | 830 | 12.1 | 99% | 88% | 33/33 | 33/33 |
| cpu:cpu | 1150.9 | 68 | 12.3 | 85% | 100% | 33/33 | 33/33 |

Decode share of single turns: 30.0% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 12.3, npu:cpu 12.1 tok/s, a spread of 2%.

Fastest npu:npu, npu:cpu +38.2%, cpu:cpu +56.9%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +13.5% | +15.7% | 1 of 33 | 23, 135.4 / 22, 133.9 | 107.3 / 107.3 °C |
| npu:cpu | npu:npu | 33 | -27.7% | -16.5% | 28 of 33 | 23, 135.4 / 0, 0.0 | 107.3 / 98.5 °C |
| cpu:cpu | npu:npu | 33 | -36.3% | -27.9% | 30 of 33 | 22, 133.9 / 0, 0.0 | 107.3 / 98.5 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 11 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 52.9 | 67.8 | 12.3 | 14.3 | 13.2 | 9.7 | 8.7 |
| npu:cpu | 56.9 | 69.1 | 12.1 | 5.9 | 13.6 | 12.8 | 12.7 |
| npu:npu | 53.9 | 57.3 | 16.8 | 17.7 | 17.5 | 16.4 | 16.5 |

Shortest decode step 38.19 ms, longest 9822.47 ms.

Constant-rate projection from the 64-token run (r6) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 664.3 | 733.5 | 1.10 | 87% | 64% |
| npu:cpu | 628.0 | 1014.1 | 1.61 | 82% | 88% |
| cpu:cpu | 762.8 | 1150.9 | 1.51 | 100% | 100% |

## Qwen3-0.6B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| gpu:cpu | timed_out | 302 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 302 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 302 | 0 | 0:3398/3628,6:4339/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 302 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: gpu:cpu, npu:npu, cpu:cpu, npu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 292.8 | 2897 | 43.2 | 99% | 84% | 33/33 | 33/33 |
| npu:cpu | 305.1 | 2895 | 41.5 | 98% | 87% | 33/33 | 33/33 |
| gpu:cpu | 328.8 | 398 | 41.8 | 91% | 94% | 33/33 | 33/33 |
| cpu:cpu | 348.8 | 205 | 42.7 | 84% | 100% | 31/31 | 30/31 |

Decode share of single turns: 31.2% to 99.7%.

Same decoder, different cells: CPU decode cpu:cpu 42.7, gpu:cpu 41.8, npu:cpu 41.5 tok/s, a spread of 3%.

Fastest npu:npu, npu:cpu +4.2%, gpu:cpu +12.3%, cpu:cpu +19.1%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +14.3% | +14.3% | 8 of 33 | 0, 0.0 / 0, 0.0 | 98.5 / 104.2 °C |
| npu:cpu | gpu:cpu | 33 | +7.8% | +7.8% | 6 of 33 | 0, 0.0 / 0, 0.0 | 98.5 / 103.1 °C |
| npu:cpu | npu:npu | 33 | -4.0% | -4.0% | 10 of 33 | 0, 0.0 / 0, 0.0 | 98.5 / 84.1 °C |
| cpu:cpu | gpu:cpu | 33 | -5.7% | -5.7% | 22 of 33 | 0, 0.0 / 0, 0.0 | 104.2 / 103.1 °C |
| cpu:cpu | npu:npu | 33 | -16.1% | -16.1% | 18 of 33 | 0, 0.0 / 0, 0.0 | 104.2 / 84.1 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 18.5 | 20.9 | 42.7 | 43.5 | 41.0 | 37.4 | 39.4 |
| gpu:cpu | 23.5 | 21.6 | 41.8 | 41.7 | 40.3 | 36.5 | 39.3 |
| npu:cpu | 23.5 | 21.8 | 41.5 | 41.5 | 39.9 | 36.4 | 38.9 |
| npu:npu | 18.7 | 23.3 | 43.2 | 43.6 | 42.7 | 41.0 | 41.4 |

Shortest decode step 7.82 ms, longest 105.34 ms.

Constant-rate projection from the 64-token run (r4) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 278.3 | 292.8 | 1.05 | 98% | 84% |
| npu:cpu | 204.3 | 305.1 | 1.49 | 72% | 87% |
| gpu:cpu | 246.0 | 328.8 | 1.34 | 86% | 94% |
| cpu:cpu | 284.9 | 348.8 | 1.22 | 100% | 100% |

## Qwen3-1.7B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| cpu:cpu | passed | 5 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: cpu:cpu, gpu:cpu, npu:cpu, npu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 452.9 | 1516 | 28.0 | 98% | 87% | 33/33 | 33/33 |
| npu:cpu | 511.0 | 1687 | 24.8 | 98% | 99% | 33/33 | 33/33 |
| cpu:cpu | 518.5 | 147 | 28.4 | 85% | 100% | 19/20 | 18/19 |
| gpu:cpu | 519.3 | 241 | 26.5 | 90% | 100% | 33/33 | 33/33 |

Decode share of single turns: 33.6% to 99.7%.

Same decoder, different cells: CPU decode cpu:cpu 28.4, gpu:cpu 26.5, npu:cpu 24.8 tok/s, a spread of 15%.

Fastest npu:npu, npu:cpu +12.8%, cpu:cpu +14.5%, gpu:cpu +14.7%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +1.5% | +1.5% | 15 of 33 | 0, 0.0 / 0, 0.0 | 107.3 / 105.4 °C |
| npu:cpu | gpu:cpu | 33 | +1.6% | +1.6% | 11 of 33 | 0, 0.0 / 0, 0.0 | 107.3 / 106.5 °C |
| npu:cpu | npu:npu | 33 | -11.4% | -11.4% | 23 of 33 | 0, 0.0 / 0, 0.0 | 107.3 / 98.8 °C |
| cpu:cpu | gpu:cpu | 33 | +0.1% | +0.1% | 17 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 106.5 °C |
| cpu:cpu | npu:npu | 33 | -12.7% | -12.7% | 21 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 98.8 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 31.2 | 33.1 | 28.4 | 28.7 | 27.7 | 26.0 | 25.7 |
| gpu:cpu | 35.4 | 35.9 | 26.5 | 26.6 | 25.8 | 24.4 | 25.9 |
| npu:cpu | 36.4 | 39.1 | 24.8 | 25.0 | 24.5 | 23.2 | 23.0 |
| npu:npu | 32.8 | 35.3 | 28.0 | 28.2 | 27.8 | 27.3 | 27.4 |

Shortest decode step 18.62 ms, longest 125.26 ms.

Constant-rate projection from the 64-token run (r4) on the 33 turns both runs completed in every cell here:

| cell | projected s | measured s | measured / projected | projected vs cpu:cpu | measured vs cpu:cpu |
|---|---|---|---|---|---|
| npu:npu | 432.5 | 452.9 | 1.05 | 87% | 87% |
| npu:cpu | 398.0 | 511.0 | 1.28 | 80% | 99% |
| cpu:cpu | 498.4 | 518.5 | 1.04 | 100% | 100% |
| gpu:cpu | 448.8 | 519.3 | 1.16 | 90% | 100% |

