## LFM2.5-1.2B-Instruct-QAD-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
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
| npu:cpu | 261.0 | 3162 | 50.4 | 98% | 86% | 33/33 | 33/33 |
| gpu:cpu | 283.2 | 482 | 50.2 | 91% | 93% | 33/33 | 33/33 |
| cpu:cpu | 303.5 | 275 | 49.9 | 85% | 100% | 33/33 | 33/33 |
| npu:npu | 321.7 | 2963 | 40.8 | 99% | 106% | 33/33 | 33/33 |
| gpu:npu | 341.5 | 484 | 41.0 | 93% | 113% | 33/33 | 33/33 |
| npu:gpu | 354.0 | 3294 | 37.0 | 99% | 117% | 33/33 | 33/33 |
| cpu:npu | 362.1 | 274 | 40.8 | 88% | 119% | 33/33 | 33/33 |
| gpu:gpu | 381.1 | 482 | 36.4 | 93% | 126% | 33/33 | 33/33 |
| cpu:gpu | 400.2 | 275 | 36.4 | 89% | 132% | 33/33 | 33/33 |

Decode share of single turns: 36.4% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 49.9, gpu:cpu 50.2, npu:cpu 50.4 tok/s, a spread of 1%.

Same decoder, different cells: GPU decode cpu:gpu 36.4, gpu:gpu 36.4, npu:gpu 37.0 tok/s, a spread of 2%.

Same decoder, different cells: NPU decode cpu:npu 40.8, gpu:npu 41.0, npu:npu 40.8 tok/s, a spread of 0%.

Fastest npu:cpu, gpu:cpu +8.5%, cpu:cpu +16.3%, npu:npu +23.3%, gpu:npu +30.8%, npu:gpu +35.6%, cpu:npu +38.7%, gpu:gpu +46.0%, cpu:gpu +53.3%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +16.3% | +16.0% | 0 of 33 | 0, 0.0 / 1, 0.7 | 103.8 / 105.4 °C |
| npu:cpu | cpu:gpu | 33 | +53.3% | +53.3% | 0 of 33 | 0, 0.0 / 0, 0.0 | 103.8 / 82.2 °C |
| npu:cpu | cpu:npu | 33 | +38.7% | +38.7% | 0 of 33 | 0, 0.0 / 0, 0.0 | 103.8 / 89.6 °C |
| npu:cpu | gpu:cpu | 33 | +8.5% | +8.5% | 0 of 33 | 0, 0.0 / 0, 0.0 | 103.8 / 106.9 °C |
| npu:cpu | gpu:gpu | 33 | +46.0% | +46.0% | 0 of 33 | 0, 0.0 / 0, 0.0 | 103.8 / 79.1 °C |
| npu:cpu | gpu:npu | 33 | +30.8% | +30.8% | 0 of 33 | 0, 0.0 / 0, 0.0 | 103.8 / 91.5 °C |
| npu:cpu | npu:gpu | 33 | +35.6% | +35.6% | 0 of 33 | 0, 0.0 / 0, 0.0 | 103.8 / 81.1 °C |
| npu:cpu | npu:npu | 33 | +23.3% | +23.3% | 0 of 33 | 0, 0.0 / 0, 0.0 | 103.8 / 92.3 °C |
| cpu:cpu | cpu:gpu | 33 | +31.9% | +32.2% | 0 of 33 | 1, 0.7 / 0, 0.0 | 105.4 / 82.2 °C |
| cpu:cpu | cpu:npu | 33 | +19.3% | +19.6% | 0 of 33 | 1, 0.7 / 0, 0.0 | 105.4 / 89.6 °C |
| cpu:cpu | gpu:cpu | 33 | -6.7% | -6.5% | 29 of 33 | 1, 0.7 / 0, 0.0 | 105.4 / 106.9 °C |
| cpu:cpu | gpu:gpu | 33 | +25.6% | +25.8% | 3 of 33 | 1, 0.7 / 0, 0.0 | 105.4 / 79.1 °C |
| cpu:cpu | gpu:npu | 33 | +12.5% | +12.8% | 5 of 33 | 1, 0.7 / 0, 0.0 | 105.4 / 91.5 °C |
| cpu:cpu | npu:gpu | 33 | +16.6% | +16.9% | 7 of 33 | 1, 0.7 / 0, 0.0 | 105.4 / 81.1 °C |
| cpu:cpu | npu:npu | 33 | +6.0% | +6.2% | 14 of 33 | 1, 0.7 / 0, 0.0 | 105.4 / 92.3 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 13 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 22.3 | 18.6 | 49.9 | 48.3 | 48.7 | 49.1 | 49.0 |
| cpu:gpu | 24.8 | 26.0 | 36.4 | 36.6 | 36.3 | 35.4 | 35.7 |
| cpu:npu | 21.2 | 24.3 | 40.8 | 40.7 | 40.6 | 40.3 | 40.2 |
| gpu:cpu | 28.6 | 18.6 | 50.2 | 50.2 | 50.1 | 49.1 | 49.0 |
| gpu:gpu | 24.7 | 26.0 | 36.4 | 37.0 | 36.1 | 35.6 | 35.7 |
| gpu:npu | 26.9 | 24.1 | 41.0 | 40.9 | 41.0 | 40.4 | 40.5 |
| npu:cpu | 19.4 | 18.6 | 50.4 | 51.0 | 50.3 | 49.0 | 48.9 |
| npu:gpu | 24.4 | 26.1 | 37.0 | 37.0 | 36.9 | 36.1 | 36.0 |
| npu:npu | 23.9 | 24.2 | 40.8 | 40.4 | 40.8 | 40.1 | 40.5 |

Shortest decode step 14.17 ms, longest 665.70 ms.

