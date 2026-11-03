## LFM2.5-2.6B-QAD-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:gpu, cpu:gpu, npu:cpu, gpu:cpu, gpu:npu, npu:npu, gpu:gpu, cpu:npu, cpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 375, mean 375, max 1025, total 12378.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:cpu | 595.3 | 1158 | 21.2 | 98% | 79% | 33/33 | 33/33 |
| npu:npu | 597.5 | 1048 | 21.1 | 98% | 79% | 33/33 | 33/33 |
| gpu:npu | 640.1 | 222 | 21.1 | 92% | 84% | 33/33 | 33/33 |
| npu:gpu | 667.4 | 1444 | 18.8 | 99% | 88% | 33/33 | 33/33 |
| cpu:npu | 678.2 | 126 | 21.2 | 86% | 89% | 33/33 | 33/33 |
| gpu:gpu | 696.0 | 223 | 19.2 | 92% | 92% | 33/33 | 33/33 |
| gpu:cpu | 700.6 | 222 | 19.1 | 92% | 92% | 33/33 | 33/33 |
| cpu:gpu | 749.7 | 129 | 18.8 | 88% | 99% | 33/33 | 33/33 |
| cpu:cpu | 758.3 | 119 | 18.7 | 87% | 100% | 33/33 | 33/33 |

Decode share of single turns: 36.2% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 18.7, gpu:cpu 19.1, npu:cpu 21.2 tok/s, a spread of 13%.

Same decoder, different cells: GPU decode cpu:gpu 18.8, gpu:gpu 19.2, npu:gpu 18.8 tok/s, a spread of 2%.

Same decoder, different cells: NPU decode cpu:npu 21.2, gpu:npu 21.1, npu:npu 21.1 tok/s, a spread of 0%.

Fastest npu:cpu, npu:npu +0.4%, gpu:npu +7.5%, npu:gpu +12.1%, cpu:npu +13.9%, gpu:gpu +16.9%, gpu:cpu +17.7%, cpu:gpu +25.9%, cpu:cpu +27.4%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +27.4% | +18.5% | 1 of 33 | 8, 57.6 / 19, 121.1 | 106.6 / 107.0 °C |
| npu:cpu | cpu:gpu | 33 | +25.9% | +39.4% | 1 of 33 | 8, 57.6 / 0, 0.0 | 106.6 / 87.6 °C |
| npu:cpu | cpu:npu | 33 | +13.9% | +26.1% | 1 of 33 | 8, 57.6 / 0, 0.0 | 106.6 / 96.5 °C |
| npu:cpu | gpu:cpu | 33 | +17.7% | +9.4% | 4 of 33 | 8, 57.6 / 16, 112.6 | 106.6 / 107.0 °C |
| npu:cpu | gpu:gpu | 33 | +16.9% | +29.4% | 1 of 33 | 8, 57.6 / 0, 0.0 | 106.6 / 89.6 °C |
| npu:cpu | gpu:npu | 33 | +7.5% | +19.1% | 1 of 33 | 8, 57.6 / 0, 0.0 | 106.6 / 97.3 °C |
| npu:cpu | npu:gpu | 33 | +12.1% | +24.1% | 1 of 33 | 8, 57.6 / 0, 0.0 | 106.6 / 87.2 °C |
| npu:cpu | npu:npu | 33 | +0.4% | +11.1% | 1 of 33 | 8, 57.6 / 0, 0.0 | 106.6 / 97.3 °C |
| cpu:cpu | cpu:gpu | 33 | -1.1% | +17.7% | 2 of 33 | 19, 121.1 / 0, 0.0 | 107.0 / 87.6 °C |
| cpu:cpu | cpu:npu | 33 | -10.6% | +6.4% | 4 of 33 | 19, 121.1 / 0, 0.0 | 107.0 / 96.5 °C |
| cpu:cpu | gpu:cpu | 33 | -7.6% | -7.7% | 29 of 33 | 19, 121.1 / 16, 112.6 | 107.0 / 107.0 °C |
| cpu:cpu | gpu:gpu | 33 | -8.2% | +9.2% | 9 of 33 | 19, 121.1 / 0, 0.0 | 107.0 / 89.6 °C |
| cpu:cpu | gpu:npu | 33 | -15.6% | +0.5% | 19 of 33 | 19, 121.1 / 0, 0.0 | 107.0 / 97.3 °C |
| cpu:cpu | npu:gpu | 33 | -12.0% | +4.8% | 16 of 33 | 19, 121.1 / 0, 0.0 | 107.0 / 87.2 °C |
| cpu:cpu | npu:npu | 33 | -21.2% | -6.2% | 26 of 33 | 19, 121.1 / 0, 0.0 | 107.0 / 97.3 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 37.1 | 43.8 | 18.7 | 24.2 | 22.3 | 21.6 | 8.9 |
| cpu:gpu | 48.4 | 50.6 | 18.8 | 18.9 | 18.7 | 18.4 | 18.5 |
| cpu:npu | 44.8 | 47.2 | 21.2 | 21.3 | 21.1 | 20.9 | 20.9 |
| gpu:cpu | 47.7 | 43.4 | 19.1 | 8.6 | 22.9 | 22.1 | 21.9 |
| gpu:gpu | 50.7 | 50.7 | 19.2 | 19.3 | 19.2 | 19.0 | 19.0 |
| gpu:npu | 47.8 | 47.4 | 21.1 | 21.1 | 21.0 | 20.8 | 20.9 |
| npu:cpu | 37.8 | 42.7 | 21.2 | 24.2 | 23.2 | 22.9 | 12.8 |
| npu:gpu | 49.3 | 50.6 | 18.8 | 18.8 | 18.7 | 18.4 | 18.4 |
| npu:npu | 46.2 | 47.3 | 21.1 | 21.2 | 21.0 | 20.9 | 20.9 |

Shortest decode step 31.36 ms, longest 13658.75 ms.

## Qwen3-1.7B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| cpu:gpu | passed | 5 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| cpu:cpu | passed | 75 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: cpu:gpu, cpu:cpu, npu:cpu, gpu:gpu, npu:npu, gpu:cpu, gpu:npu, npu:gpu, cpu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 444.5 | 1757 | 28.5 | 98% | 81% | 33/33 | 33/33 |
| npu:cpu | 483.5 | 1763 | 26.2 | 98% | 88% | 33/33 | 33/33 |
| gpu:npu | 487.0 | 247 | 28.4 | 90% | 89% | 33/33 | 33/33 |
| cpu:npu | 529.7 | 127 | 28.5 | 82% | 96% | 33/33 | 33/33 |
| cpu:cpu | 550.1 | 131 | 27.1 | 84% | 100% | 30/31 | 30/31 |
| gpu:cpu | 593.8 | 246 | 22.9 | 92% | 108% | 33/33 | 33/33 |
| cpu:gpu | 783.3 | 165 | 17.5 | 91% | 142% | 17/18 | 16/17 |
| npu:gpu | 876.0 | 1994 | 14.3 | 99% | 159% | 33/33 | 33/33 |
| gpu:gpu | 917.3 | 242 | 14.3 | 95% | 167% | 33/33 | 33/33 |

Decode share of single turns: 30.6% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 27.1, gpu:cpu 22.9, npu:cpu 26.2 tok/s, a spread of 18%.

Same decoder, different cells: GPU decode cpu:gpu 17.5, gpu:gpu 14.3, npu:gpu 14.3 tok/s, a spread of 22%.

Same decoder, different cells: NPU decode cpu:npu 28.5, gpu:npu 28.4, npu:npu 28.5 tok/s, a spread of 0%.

Fastest npu:npu, npu:cpu +8.8%, gpu:npu +9.6%, cpu:npu +19.2%, cpu:cpu +23.7%, gpu:cpu +33.6%, cpu:gpu +76.2%, npu:gpu +97.1%, gpu:gpu +106.4%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +13.8% | +13.8% | 5 of 33 | 0, 0.0 / 0, 0.0 | 106.6 / 104.7 °C |
| npu:cpu | cpu:gpu | 33 | +62.0% | +62.0% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.6 / 77.1 °C |
| npu:cpu | cpu:npu | 33 | +9.5% | +9.5% | 3 of 33 | 0, 0.0 / 0, 0.0 | 106.6 / 96.9 °C |
| npu:cpu | gpu:cpu | 33 | +22.8% | +9.3% | 1 of 33 | 0, 0.0 / 10, 65.4 | 106.6 / 106.6 °C |
| npu:cpu | gpu:gpu | 33 | +89.7% | +89.7% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.6 / 82.2 °C |
| npu:cpu | gpu:npu | 33 | +0.7% | +0.7% | 9 of 33 | 0, 0.0 / 0, 0.0 | 106.6 / 98.5 °C |
| npu:cpu | npu:gpu | 33 | +81.2% | +81.2% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.6 / 83.0 °C |
| npu:cpu | npu:npu | 33 | -8.1% | -8.1% | 17 of 33 | 0, 0.0 / 0, 0.0 | 106.6 / 96.9 °C |
| cpu:cpu | cpu:gpu | 33 | +42.4% | +42.4% | 4 of 33 | 0, 0.0 / 0, 0.0 | 104.7 / 77.1 °C |
| cpu:cpu | cpu:npu | 33 | -3.7% | -3.7% | 12 of 33 | 0, 0.0 / 0, 0.0 | 104.7 / 96.9 °C |
| cpu:cpu | gpu:cpu | 33 | +8.0% | -3.9% | 20 of 33 | 0, 0.0 / 10, 65.4 | 104.7 / 106.6 °C |
| cpu:cpu | gpu:gpu | 33 | +66.8% | +66.8% | 0 of 33 | 0, 0.0 / 0, 0.0 | 104.7 / 82.2 °C |
| cpu:cpu | gpu:npu | 33 | -11.5% | -11.5% | 20 of 33 | 0, 0.0 / 0, 0.0 | 104.7 / 98.5 °C |
| cpu:cpu | npu:gpu | 33 | +59.3% | +59.3% | 4 of 33 | 0, 0.0 / 0, 0.0 | 104.7 / 83.0 °C |
| cpu:cpu | npu:npu | 33 | -19.2% | -19.2% | 26 of 33 | 0, 0.0 / 0, 0.0 | 104.7 / 96.9 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 31.8 | 35.1 | 27.1 | 27.2 | 26.4 | 24.8 | 25.7 |
| cpu:gpu | 40.7 | 54.1 | 17.5 | 17.9 | 17.4 | 16.3 | 14.3 |
| cpu:npu | 30.7 | 34.4 | 28.5 | 28.6 | 28.2 | 27.7 | 27.9 |
| gpu:cpu | 34.9 | 36.9 | 22.9 | 26.5 | 22.8 | 23.8 | 24.1 |
| gpu:gpu | 56.1 | 63.6 | 14.3 | 14.5 | 14.3 | 13.3 | 13.3 |
| gpu:npu | 35.0 | 34.6 | 28.4 | 28.4 | 28.2 | 27.6 | 27.8 |
| npu:cpu | 37.4 | 36.7 | 26.2 | 26.7 | 25.7 | 24.2 | 25.0 |
| npu:gpu | 54.6 | 63.5 | 14.3 | 14.5 | 14.3 | 13.3 | 13.3 |
| npu:npu | 34.7 | 34.6 | 28.5 | 28.4 | 28.2 | 27.5 | 27.9 |

Shortest decode step 18.80 ms, longest 9956.78 ms.

## Qwen3-1.7B-Q8_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: cpu:cpu, gpu:gpu, gpu:cpu, cpu:gpu, npu:gpu, cpu:npu, npu:npu, gpu:npu, npu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 619.2 | 1560 | 20.4 | 99% | 73% | 33/33 | 33/33 |
| gpu:npu | 641.8 | 221 | 21.2 | 92% | 76% | 33/33 | 33/33 |
| cpu:npu | 681.8 | 85 | 22.9 | 80% | 81% | 33/33 | 33/33 |
| gpu:cpu | 725.6 | 223 | 18.6 | 93% | 86% | 33/33 | 33/33 |
| npu:cpu | 742.1 | 1630 | 17.0 | 99% | 88% | 33/33 | 33/33 |
| cpu:cpu | 845.1 | 121 | 16.7 | 89% | 100% | 33/33 | 33/33 |
| npu:gpu | 957.3 | 1946 | 13.1 | 99% | 113% | 33/33 | 33/33 |
| gpu:gpu | 990.8 | 219 | 13.3 | 95% | 117% | 33/33 | 33/33 |
| cpu:gpu | 1022.5 | 136 | 13.3 | 91% | 121% | 33/33 | 33/33 |

Decode share of single turns: 34.8% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 16.7, gpu:cpu 18.6, npu:cpu 17.0 tok/s, a spread of 11%.

Same decoder, different cells: GPU decode cpu:gpu 13.3, gpu:gpu 13.3, npu:gpu 13.1 tok/s, a spread of 2%.

Same decoder, different cells: NPU decode cpu:npu 22.9, gpu:npu 21.2, npu:npu 20.4 tok/s, a spread of 13%.

Fastest npu:npu, gpu:npu +3.6%, cpu:npu +10.1%, gpu:cpu +17.2%, npu:cpu +19.8%, cpu:cpu +36.5%, npu:gpu +54.6%, gpu:gpu +60.0%, cpu:gpu +65.1%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +13.9% | +16.8% | 5 of 33 | 26, 168.1 / 24, 175.0 | 107.4 / 107.0 °C |
| npu:cpu | cpu:gpu | 33 | +37.8% | +78.1% | 3 of 33 | 26, 168.1 / 0, 0.0 | 107.4 / 88.8 °C |
| npu:cpu | cpu:npu | 33 | -8.1% | +18.8% | 7 of 33 | 26, 168.1 / 0, 0.0 | 107.4 / 100.4 °C |
| npu:cpu | gpu:cpu | 33 | -2.2% | +7.2% | 4 of 33 | 26, 168.1 / 18, 110.2 | 107.4 / 107.0 °C |
| npu:cpu | gpu:gpu | 33 | +33.5% | +72.6% | 3 of 33 | 26, 168.1 / 0, 0.0 | 107.4 / 87.6 °C |
| npu:cpu | gpu:npu | 33 | -13.5% | +11.8% | 9 of 33 | 26, 168.1 / 0, 0.0 | 107.4 / 99.3 °C |
| npu:cpu | npu:gpu | 33 | +29.0% | +66.8% | 3 of 33 | 26, 168.1 / 0, 0.0 | 107.4 / 85.7 °C |
| npu:cpu | npu:npu | 33 | -16.6% | +7.9% | 15 of 33 | 26, 168.1 / 0, 0.0 | 107.4 / 99.3 °C |
| cpu:cpu | cpu:gpu | 33 | +21.0% | +52.6% | 3 of 33 | 24, 175.0 / 0, 0.0 | 107.0 / 88.8 °C |
| cpu:cpu | cpu:npu | 33 | -19.3% | +1.7% | 19 of 33 | 24, 175.0 / 0, 0.0 | 107.0 / 100.4 °C |
| cpu:cpu | gpu:cpu | 33 | -14.1% | -8.2% | 28 of 33 | 24, 175.0 / 18, 110.2 | 107.0 / 107.0 °C |
| cpu:cpu | gpu:gpu | 33 | +17.2% | +47.9% | 3 of 33 | 24, 175.0 / 0, 0.0 | 107.0 / 87.6 °C |
| cpu:cpu | gpu:npu | 33 | -24.1% | -4.2% | 21 of 33 | 24, 175.0 / 0, 0.0 | 107.0 / 99.3 °C |
| cpu:cpu | npu:gpu | 33 | +13.3% | +42.9% | 8 of 33 | 24, 175.0 / 0, 0.0 | 107.0 / 85.7 °C |
| cpu:cpu | npu:npu | 33 | -26.7% | -7.6% | 21 of 33 | 24, 175.0 / 0, 0.0 | 107.0 / 99.3 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 39.9 | 44.3 | 16.7 | 22.5 | 21.2 | 11.4 | 12.2 |
| cpu:gpu | 55.4 | 69.0 | 13.3 | 13.4 | 13.2 | 12.4 | 12.5 |
| cpu:npu | 36.1 | 42.5 | 22.9 | 23.1 | 22.7 | 22.2 | 21.1 |
| gpu:cpu | 41.8 | 43.3 | 18.6 | 8.8 | 21.9 | 20.8 | 20.4 |
| gpu:gpu | 61.0 | 69.1 | 13.3 | 13.5 | 13.2 | 12.4 | 12.5 |
| gpu:npu | 41.3 | 44.5 | 21.2 | 21.5 | 21.2 | 20.7 | 19.2 |
| npu:cpu | 41.3 | 42.8 | 17.0 | 22.3 | 21.3 | 14.9 | 12.3 |
| npu:gpu | 56.7 | 69.9 | 13.1 | 13.3 | 13.1 | 12.2 | 12.2 |
| npu:npu | 43.2 | 45.7 | 20.4 | 20.7 | 20.4 | 20.0 | 18.8 |

Shortest decode step 30.25 ms, longest 12992.90 ms.

## Qwen3-4B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 28 | 0 | 0 |

Cells in the order they ran: gpu:cpu, gpu:gpu, npu:gpu, cpu:npu, npu:npu.

Common cohort: 28 turns in 7 conversations completed in every cell. Decode steps a turn: median 440, mean 390, max 1046, total 10927.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 963.5 | 647 | 11.5 | 98% | n/a | 28/28 | 28/28 |
| cpu:npu | 998.1 | 49 | 13.9 | 79% | n/a | 28/28 | 28/28 |
| gpu:cpu | 1218.6 | 102 | 9.8 | 92% | n/a | 28/28 | 28/28 |
| npu:gpu | 2570.8 | 938 | 4.3 | 100% | n/a | 28/28 | 28/28 |
| gpu:gpu | 2662.7 | 100 | 4.3 | 96% | n/a | 28/28 | 28/28 |

Decode share of single turns: 26.1% to 99.9%.

Same decoder, different cells: GPU decode gpu:gpu 4.3, npu:gpu 4.3 tok/s, a spread of 0%.

Same decoder, different cells: NPU decode cpu:npu 13.9, npu:npu 11.5 tok/s, a spread of 20%.

Fastest npu:npu, cpu:npu +3.6%, gpu:cpu +26.5%, npu:gpu +166.8%, gpu:gpu +176.4%.

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 11 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:npu | 55.4 | 71.1 | 13.9 | 14.3 | 13.9 | 13.4 | 12.5 |
| gpu:cpu | 69.4 | 79.3 | 9.8 | 3.2 | 12.3 | 11.4 | 11.3 |
| gpu:gpu | 99.9 | 114.2 | 4.3 | 4.6 | 4.3 | 3.5 | 4.0 |
| npu:gpu | 104.5 | 114.2 | 4.3 | 4.6 | 4.3 | 3.5 | 4.0 |
| npu:npu | 71.2 | 86.0 | 11.5 | 11.9 | 11.5 | 11.0 | 9.8 |

Shortest decode step 47.27 ms, longest 15272.11 ms.

