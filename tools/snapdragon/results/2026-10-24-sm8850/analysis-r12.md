## LFM2.5-1.2B-Instruct-QAD-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:cpu | passed | 5 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| gpu:gpu | passed | 101 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:cpu, gpu:gpu, cpu:cpu, npu:npu, cpu:gpu, cpu:npu, gpu:cpu, gpu:npu, npu:gpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 411, mean 392, max 1083, total 12946.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:cpu | 227.2 | 3280 | 58.0 | 98% | 77% | 12/14 | 11/12 |
| cpu:cpu | 296.0 | 280 | 51.3 | 85% | 100% | 30/31 | 30/31 |
| gpu:cpu | 309.0 | 480 | 45.7 | 92% | 104% | 33/33 | 33/33 |
| npu:npu | 321.7 | 2748 | 40.8 | 99% | 109% | 33/33 | 33/33 |
| gpu:npu | 346.5 | 477 | 40.4 | 93% | 117% | 33/33 | 33/33 |
| npu:gpu | 355.9 | 3005 | 36.8 | 99% | 120% | 33/33 | 33/33 |
| cpu:npu | 362.7 | 269 | 40.8 | 88% | 123% | 33/33 | 33/33 |
| gpu:gpu | 363.6 | 507 | 38.1 | 93% | 123% | 27/28 | 27/28 |
| cpu:gpu | 401.1 | 275 | 36.3 | 89% | 136% | 33/33 | 33/33 |

Decode share of single turns: 36.9% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 51.3, gpu:cpu 45.7, npu:cpu 58.0 tok/s, a spread of 27%.

Same decoder, different cells: GPU decode cpu:gpu 36.3, gpu:gpu 38.1, npu:gpu 36.8 tok/s, a spread of 5%.

Same decoder, different cells: NPU decode cpu:npu 40.8, gpu:npu 40.4, npu:npu 40.8 tok/s, a spread of 1%.

Fastest npu:cpu, cpu:cpu +30.3%, gpu:cpu +36.0%, npu:npu +41.6%, gpu:npu +52.5%, npu:gpu +56.7%, cpu:npu +59.7%, gpu:gpu +60.1%, cpu:gpu +76.6%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +30.3% | +30.3% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 105.4 °C |
| npu:cpu | cpu:gpu | 33 | +76.6% | +76.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 86.5 °C |
| npu:cpu | cpu:npu | 33 | +59.7% | +59.7% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 95.7 °C |
| npu:cpu | gpu:cpu | 33 | +36.0% | +36.0% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 106.9 °C |
| npu:cpu | gpu:gpu | 33 | +60.1% | +60.1% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 81.1 °C |
| npu:cpu | gpu:npu | 33 | +52.5% | +52.5% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 97.7 °C |
| npu:cpu | npu:gpu | 33 | +56.7% | +56.7% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 89.9 °C |
| npu:cpu | npu:npu | 33 | +41.6% | +41.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.8 / 94.2 °C |
| cpu:cpu | cpu:gpu | 33 | +35.5% | +35.5% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 86.5 °C |
| cpu:cpu | cpu:npu | 33 | +22.5% | +22.5% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 95.7 °C |
| cpu:cpu | gpu:cpu | 33 | +4.4% | +4.4% | 14 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 106.9 °C |
| cpu:cpu | gpu:gpu | 33 | +22.8% | +22.8% | 4 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 81.1 °C |
| cpu:cpu | gpu:npu | 33 | +17.1% | +17.1% | 2 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 97.7 °C |
| cpu:cpu | npu:gpu | 33 | +20.2% | +20.2% | 7 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 89.9 °C |
| cpu:cpu | npu:npu | 33 | +8.7% | +8.7% | 13 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 94.2 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 13 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 18.4 | 18.4 | 51.3 | 51.4 | 50.7 | 49.7 | 48.7 |
| cpu:gpu | 24.3 | 26.1 | 36.3 | 36.3 | 36.2 | 35.4 | 35.6 |
| cpu:npu | 21.5 | 24.4 | 40.8 | 40.9 | 40.7 | 40.2 | 40.2 |
| gpu:cpu | 28.2 | 21.5 | 45.7 | 47.9 | 45.4 | 44.2 | 42.3 |
| gpu:gpu | 25.0 | 25.5 | 38.1 | 38.1 | 37.0 | 36.4 | 35.3 |
| gpu:npu | 25.9 | 24.6 | 40.4 | 41.0 | 40.2 | 39.8 | 39.5 |
| npu:cpu | 17.4 | 16.3 | 58.0 | 58.6 | 57.6 | 55.9 | 52.8 |
| npu:gpu | 24.6 | 26.1 | 36.8 | 37.2 | 36.7 | 36.0 | 36.3 |
| npu:npu | 23.7 | 24.3 | 40.8 | 40.8 | 40.7 | 40.3 | 40.3 |

Shortest decode step 11.45 ms, longest 117.03 ms.

## Llama-3.2-1B-Instruct-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: npu:gpu, gpu:cpu, npu:npu, npu:cpu, gpu:npu, gpu:gpu, cpu:gpu, cpu:npu, cpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 393, mean 367, max 1023, total 12107.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 340.9 | 1942 | 36.1 | 98% | 80% | 33/33 | 33/33 |
| gpu:cpu | 372.2 | 401 | 35.3 | 92% | 87% | 33/33 | 33/33 |
| gpu:npu | 383.7 | 398 | 34.2 | 92% | 90% | 33/33 | 33/33 |
| cpu:npu | 388.7 | 192 | 36.8 | 85% | 91% | 33/33 | 33/33 |
| cpu:cpu | 428.3 | 175 | 33.4 | 85% | 100% | 33/33 | 33/33 |
| npu:cpu | 448.6 | 2158 | 27.3 | 99% | 105% | 33/33 | 33/33 |
| npu:gpu | 473.6 | 2745 | 25.8 | 99% | 111% | 33/33 | 33/33 |
| gpu:gpu | 497.9 | 397 | 25.8 | 94% | 116% | 33/33 | 33/33 |
| cpu:gpu | 524.7 | 209 | 25.8 | 89% | 123% | 33/33 | 33/33 |

Decode share of single turns: 35.8% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 33.4, gpu:cpu 35.3, npu:cpu 27.3 tok/s, a spread of 29%.

Same decoder, different cells: GPU decode cpu:gpu 25.8, gpu:gpu 25.8, npu:gpu 25.8 tok/s, a spread of 0%.

Same decoder, different cells: NPU decode cpu:npu 36.8, gpu:npu 34.2, npu:npu 36.1 tok/s, a spread of 8%.

Fastest npu:npu, gpu:cpu +9.2%, gpu:npu +12.6%, cpu:npu +14.0%, cpu:cpu +25.6%, npu:cpu +31.6%, npu:gpu +38.9%, gpu:gpu +46.1%, cpu:gpu +53.9%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | -4.5% | +13.5% | 4 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 106.9 °C |
| npu:cpu | cpu:gpu | 33 | +17.0% | +39.1% | 1 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 85.3 °C |
| npu:cpu | cpu:npu | 33 | -13.4% | +3.0% | 9 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 100.0 °C |
| npu:cpu | gpu:cpu | 33 | -17.0% | -1.4% | 19 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 106.9 °C |
| npu:cpu | gpu:gpu | 33 | +11.0% | +31.9% | 1 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 86.9 °C |
| npu:cpu | gpu:npu | 33 | -14.5% | +1.7% | 16 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 101.1 °C |
| npu:cpu | npu:gpu | 33 | +5.6% | +25.5% | 1 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 86.9 °C |
| npu:cpu | npu:npu | 33 | -24.0% | -9.7% | 24 of 33 | 20, 71.3 / 0, 0.0 | 106.9 / 103.5 °C |
| cpu:cpu | cpu:gpu | 33 | +22.5% | +22.5% | 1 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 85.3 °C |
| cpu:cpu | cpu:npu | 33 | -9.2% | -9.2% | 27 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 100.0 °C |
| cpu:cpu | gpu:cpu | 33 | -13.1% | -13.1% | 32 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 106.9 °C |
| cpu:cpu | gpu:gpu | 33 | +16.3% | +16.3% | 6 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 86.9 °C |
| cpu:cpu | gpu:npu | 33 | -10.4% | -10.4% | 27 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 101.1 °C |
| cpu:cpu | npu:gpu | 33 | +10.6% | +10.6% | 10 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 86.9 °C |
| cpu:cpu | npu:npu | 33 | -20.4% | -20.4% | 30 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 103.5 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 11 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 23.0 | 29.9 | 33.4 | 35.3 | 32.7 | 31.2 | 30.1 |
| cpu:gpu | 32.0 | 37.0 | 25.8 | 25.4 | 25.3 | 24.4 | 24.8 |
| cpu:npu | 24.3 | 26.4 | 36.8 | 36.9 | 36.3 | 35.5 | 36.0 |
| gpu:cpu | 30.0 | 28.1 | 35.3 | 36.8 | 34.8 | 33.3 | 32.3 |
| gpu:gpu | 33.0 | 36.5 | 25.8 | 25.7 | 25.5 | 24.3 | 24.8 |
| gpu:npu | 27.7 | 27.6 | 34.2 | 36.6 | 35.7 | 34.7 | 35.1 |
| npu:cpu | 25.0 | 30.7 | 27.3 | 32.7 | 30.9 | 29.9 | 29.1 |
| npu:gpu | 32.8 | 36.6 | 25.8 | 25.5 | 25.4 | 24.3 | 24.8 |
| npu:npu | 25.9 | 27.1 | 36.1 | 36.3 | 35.7 | 34.7 | 35.0 |

Shortest decode step 16.30 ms, longest 5222.40 ms.

## Qwen3-0.6B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: gpu:npu, gpu:gpu, npu:npu, cpu:gpu, gpu:cpu, cpu:cpu, npu:cpu, npu:gpu, cpu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 291.8 | 2816 | 43.3 | 99% | 80% | 33/33 | 33/33 |
| npu:cpu | 316.3 | 2756 | 40.0 | 98% | 87% | 33/33 | 33/33 |
| gpu:npu | 317.8 | 399 | 43.4 | 90% | 87% | 33/33 | 33/33 |
| gpu:cpu | 329.6 | 391 | 41.7 | 91% | 91% | 33/33 | 33/33 |
| cpu:npu | 347.1 | 196 | 43.5 | 83% | 95% | 33/33 | 33/33 |
| cpu:cpu | 364.2 | 187 | 41.3 | 83% | 100% | 33/33 | 33/33 |
| npu:gpu | 628.6 | 2891 | 20.0 | 99% | 173% | 33/33 | 33/33 |
| gpu:gpu | 652.8 | 391 | 20.0 | 95% | 179% | 33/33 | 33/33 |
| cpu:gpu | 683.1 | 200 | 20.0 | 91% | 188% | 33/33 | 33/33 |

Decode share of single turns: 28.6% to 99.7%.

Same decoder, different cells: CPU decode cpu:cpu 41.3, gpu:cpu 41.7, npu:cpu 40.0 tok/s, a spread of 4%.

Same decoder, different cells: GPU decode cpu:gpu 20.0, gpu:gpu 20.0, npu:gpu 20.0 tok/s, a spread of 0%.

Same decoder, different cells: NPU decode cpu:npu 43.5, gpu:npu 43.4, npu:npu 43.3 tok/s, a spread of 0%.

Fastest npu:npu, npu:cpu +8.4%, gpu:npu +8.9%, gpu:cpu +13.0%, cpu:npu +19.0%, cpu:cpu +24.8%, npu:gpu +115.4%, gpu:gpu +123.8%, cpu:gpu +134.1%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +15.1% | +15.1% | 6 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 106.5 °C |
| npu:cpu | cpu:gpu | 33 | +116.0% | +116.0% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 83.8 °C |
| npu:cpu | cpu:npu | 33 | +9.7% | +9.7% | 5 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 95.7 °C |
| npu:cpu | gpu:cpu | 33 | +4.2% | +4.2% | 9 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 104.2 °C |
| npu:cpu | gpu:gpu | 33 | +106.4% | +106.4% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 86.9 °C |
| npu:cpu | gpu:npu | 33 | +0.5% | +0.5% | 8 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 98.4 °C |
| npu:cpu | npu:gpu | 33 | +98.7% | +98.7% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 87.6 °C |
| npu:cpu | npu:npu | 33 | -7.8% | -7.8% | 12 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 94.2 °C |
| cpu:cpu | cpu:gpu | 33 | +87.6% | +87.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 83.8 °C |
| cpu:cpu | cpu:npu | 33 | -4.7% | -4.7% | 12 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 95.7 °C |
| cpu:cpu | gpu:cpu | 33 | -9.5% | -9.5% | 31 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 104.2 °C |
| cpu:cpu | gpu:gpu | 33 | +79.3% | +79.3% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 86.9 °C |
| cpu:cpu | gpu:npu | 33 | -12.7% | -12.7% | 15 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 98.4 °C |
| cpu:cpu | npu:gpu | 33 | +72.6% | +72.6% | 4 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 87.6 °C |
| cpu:cpu | npu:npu | 33 | -19.9% | -19.9% | 20 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 94.2 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 19.1 | 22.0 | 41.3 | 42.4 | 39.8 | 36.6 | 38.5 |
| cpu:gpu | 33.2 | 44.1 | 20.0 | 20.2 | 19.8 | 18.0 | 18.0 |
| cpu:npu | 16.9 | 23.3 | 43.5 | 44.1 | 42.9 | 41.2 | 41.5 |
| gpu:cpu | 23.4 | 21.6 | 41.7 | 42.4 | 40.0 | 36.6 | 39.4 |
| gpu:gpu | 32.9 | 44.1 | 20.0 | 20.6 | 19.8 | 18.0 | 18.1 |
| gpu:npu | 21.9 | 23.1 | 43.4 | 43.8 | 42.9 | 41.1 | 41.4 |
| npu:cpu | 23.8 | 22.9 | 40.0 | 40.2 | 38.8 | 35.6 | 36.7 |
| npu:gpu | 31.2 | 44.1 | 20.0 | 20.4 | 19.9 | 18.0 | 18.1 |
| npu:npu | 19.8 | 23.3 | 43.3 | 43.4 | 42.9 | 41.1 | 41.4 |

Shortest decode step 10.01 ms, longest 126.10 ms.

## Qwen3-1.7B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 19 | 0 | 0 |

Cells in the order they ran: cpu:gpu, cpu:cpu, npu:cpu, gpu:gpu, npu:npu, gpu:cpu, gpu:npu, npu:gpu.

Common cohort: 19 turns in 4 conversations completed in every cell. Decode steps a turn: median 312, mean 315, max 561, total 5981.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 219.2 | 1514 | 27.8 | 98% | 70% | 19/19 | 19/19 |
| gpu:npu | 247.0 | 236 | 27.1 | 89% | 79% | 19/19 | 19/19 |
| cpu:cpu | 313.3 | 105 | 23.4 | 81% | 100% | 19/19 | 19/19 |
| npu:cpu | 352.5 | 1470 | 17.2 | 99% | 113% | 19/19 | 19/19 |
| gpu:cpu | 367.2 | 233 | 17.6 | 93% | 117% | 19/19 | 19/19 |
| npu:gpu | 442.0 | 1848 | 13.7 | 99% | 141% | 19/19 | 19/19 |
| gpu:gpu | 463.6 | 228 | 13.7 | 94% | 148% | 19/19 | 19/19 |
| cpu:gpu | 490.7 | 118 | 13.7 | 89% | 157% | 19/19 | 19/19 |

Decode share of single turns: 30.7% to 99.6%.

Same decoder, different cells: CPU decode cpu:cpu 23.4, gpu:cpu 17.6, npu:cpu 17.2 tok/s, a spread of 36%.

Same decoder, different cells: GPU decode cpu:gpu 13.7, gpu:gpu 13.7, npu:gpu 13.7 tok/s, a spread of 0%.

Same decoder, different cells: NPU decode gpu:npu 27.1, npu:npu 27.8 tok/s, a spread of 2%.

Fastest npu:npu, gpu:npu +12.7%, cpu:cpu +42.9%, npu:cpu +60.8%, gpu:cpu +67.5%, npu:gpu +101.7%, gpu:gpu +111.5%, cpu:gpu +123.9%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | -8.4% | +13.2% | 10 of 33 | 30, 211.1 / 11, 68.4 | 106.9 / 107.3 °C |
| npu:cpu | cpu:gpu | 33 | +21.9% | +66.2% | 3 of 33 | 30, 211.1 / 0, 0.0 | 106.9 / 88.8 °C |
| npu:cpu | gpu:cpu | 33 | -6.4% | +4.2% | 11 of 33 | 30, 211.1 / 18, 135.8 | 106.9 / 106.9 °C |
| npu:cpu | gpu:gpu | 33 | +14.4% | +56.1% | 3 of 33 | 30, 211.1 / 0, 0.0 | 106.9 / 89.2 °C |
| npu:cpu | gpu:npu | 33 | -15.9% | +14.6% | 12 of 33 | 30, 211.1 / 0, 0.0 | 106.9 / 100.8 °C |
| npu:cpu | npu:gpu | 19 | +25.4% | +55.6% | 1 of 19 | 9, 68.4 / 0, 0.0 | 106.5 / 87.6 °C |
| npu:cpu | npu:npu | 33 | -39.4% | -17.3% | 23 of 33 | 30, 211.1 / 0, 0.0 | 106.9 / 101.1 °C |
| cpu:cpu | cpu:gpu | 33 | +33.0% | +46.9% | 2 of 33 | 11, 68.4 / 0, 0.0 | 107.3 / 88.8 °C |
| cpu:cpu | gpu:cpu | 33 | +2.1% | -7.9% | 23 of 33 | 11, 68.4 / 18, 135.8 | 107.3 / 106.9 °C |
| cpu:cpu | gpu:gpu | 33 | +24.9% | +37.9% | 6 of 33 | 11, 68.4 / 0, 0.0 | 107.3 / 89.2 °C |
| cpu:cpu | gpu:npu | 33 | -8.3% | +1.3% | 19 of 33 | 11, 68.4 / 0, 0.0 | 107.3 / 100.8 °C |
| cpu:cpu | npu:gpu | 19 | +41.1% | +41.1% | 4 of 19 | 0, 0.0 / 0, 0.0 | 106.5 / 87.6 °C |
| cpu:cpu | npu:npu | 33 | -33.8% | -27.0% | 27 of 33 | 11, 68.4 / 0, 0.0 | 107.3 / 101.1 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 6 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 30.8 | 40.1 | 23.4 | 24.4 | 22.6 | 20.9 | 19.3 |
| cpu:gpu | 54.4 | 68.0 | 13.7 | 13.9 | 13.5 | 12.8 | 12.1 |
| gpu:cpu | 39.0 | 41.6 | 17.6 | 23.4 | 20.1 | 18.6 | 17.3 |
| gpu:gpu | 52.3 | 68.0 | 13.7 | 13.8 | 13.5 | 12.8 | 12.4 |
| gpu:npu | 34.7 | 36.6 | 27.2 | 27.7 | 26.8 | 26.2 | 24.9 |
| npu:cpu | 40.3 | 43.9 | 17.2 | 20.4 | 9.2 | 19.2 | 17.9 |
| npu:gpu | 54.9 | 68.0 | 13.7 | 13.8 | 13.5 | 12.8 | 12.1 |
| npu:npu | 36.2 | 35.6 | 27.8 | 28.1 | 27.5 | 26.9 | 26.0 |

Shortest decode step 22.40 ms, longest 13691.48 ms.

## google_gemma-3-1b-it-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: cpu:gpu, npu:cpu, gpu:npu, npu:gpu, gpu:gpu, gpu:cpu, npu:npu, cpu:npu, cpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 436, mean 402, max 1141, total 13256.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:cpu | 254.1 | 3107 | 53.1 | 98% | 80% | 33/33 | 33/33 |
| gpu:cpu | 276.6 | 413 | 53.8 | 89% | 87% | 33/33 | 33/33 |
| npu:npu | 287.5 | 3125 | 46.8 | 99% | 91% | 33/33 | 33/33 |
| gpu:npu | 310.1 | 412 | 47.4 | 90% | 98% | 33/33 | 33/33 |
| cpu:cpu | 316.7 | 250 | 49.6 | 84% | 100% | 33/33 | 33/33 |
| cpu:npu | 324.3 | 277 | 47.4 | 86% | 102% | 33/33 | 33/33 |
| npu:gpu | 563.1 | 3294 | 23.7 | 99% | 178% | 33/33 | 33/33 |
| gpu:gpu | 594.2 | 405 | 23.5 | 95% | 188% | 33/33 | 33/33 |
| cpu:gpu | 603.2 | 281 | 23.7 | 93% | 190% | 33/33 | 33/33 |

Decode share of single turns: 35.8% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 49.6, gpu:cpu 53.8, npu:cpu 53.1 tok/s, a spread of 9%.

Same decoder, different cells: GPU decode cpu:gpu 23.7, gpu:gpu 23.5, npu:gpu 23.7 tok/s, a spread of 1%.

Same decoder, different cells: NPU decode cpu:npu 47.4, gpu:npu 47.4, npu:npu 46.8 tok/s, a spread of 1%.

Fastest npu:cpu, gpu:cpu +8.8%, npu:npu +13.2%, gpu:npu +22.1%, cpu:cpu +24.7%, cpu:npu +27.6%, npu:gpu +121.6%, gpu:gpu +133.9%, cpu:gpu +137.4%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +24.7% | +24.7% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 106.5 °C |
| npu:cpu | cpu:gpu | 33 | +137.4% | +137.4% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 81.8 °C |
| npu:cpu | cpu:npu | 33 | +27.6% | +27.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 95.7 °C |
| npu:cpu | gpu:cpu | 33 | +8.8% | +8.8% | 2 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 103.1 °C |
| npu:cpu | gpu:gpu | 33 | +133.9% | +133.9% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 78.0 °C |
| npu:cpu | gpu:npu | 33 | +22.1% | +22.1% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 95.0 °C |
| npu:cpu | npu:gpu | 33 | +121.6% | +121.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 81.1 °C |
| npu:cpu | npu:npu | 33 | +13.2% | +13.2% | 0 of 33 | 0, 0.0 / 0, 0.0 | 105.4 / 93.8 °C |
| cpu:cpu | cpu:gpu | 33 | +90.5% | +90.5% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 81.8 °C |
| cpu:cpu | cpu:npu | 33 | +2.4% | +2.4% | 10 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 95.7 °C |
| cpu:cpu | gpu:cpu | 33 | -12.7% | -12.7% | 32 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 103.1 °C |
| cpu:cpu | gpu:gpu | 33 | +87.6% | +87.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 78.0 °C |
| cpu:cpu | gpu:npu | 33 | -2.1% | -2.1% | 20 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 95.0 °C |
| cpu:cpu | npu:gpu | 33 | +77.8% | +77.8% | 2 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 81.1 °C |
| cpu:cpu | npu:npu | 33 | -9.2% | -9.2% | 23 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 93.8 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 13 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 19.1 | 19.4 | 49.6 | 49.8 | 49.6 | 48.6 | 47.5 |
| cpu:gpu | 38.6 | 41.5 | 23.7 | 24.7 | 24.7 | 22.8 | 20.8 |
| cpu:npu | 17.9 | 21.1 | 47.4 | 48.0 | 47.3 | 47.2 | 47.0 |
| gpu:cpu | 29.2 | 17.5 | 53.8 | 54.4 | 54.5 | 53.1 | 51.6 |
| gpu:gpu | 40.2 | 38.6 | 23.5 | 23.7 | 23.1 | 22.7 | 22.2 |
| gpu:npu | 23.5 | 21.1 | 47.4 | 47.4 | 47.6 | 47.3 | 46.9 |
| npu:cpu | 19.6 | 17.9 | 53.1 | 53.0 | 53.3 | 52.4 | 50.9 |
| npu:gpu | 40.0 | 41.5 | 23.7 | 24.7 | 24.7 | 22.8 | 20.8 |
| npu:npu | 21.0 | 21.4 | 46.8 | 46.9 | 46.6 | 46.5 | 46.4 |

Shortest decode step 15.05 ms, longest 81.04 ms.

