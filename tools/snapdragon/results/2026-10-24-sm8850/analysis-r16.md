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
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: cpu:gpu, cpu:cpu, npu:cpu, gpu:gpu, npu:npu, gpu:cpu, gpu:npu, npu:gpu, cpu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 446.9 | 1690 | 28.3 | 98% | 79% | 33/33 | 33/33 |
| gpu:npu | 517.6 | 245 | 26.6 | 91% | 92% | 33/33 | 33/33 |
| npu:cpu | 524.1 | 1693 | 24.1 | 99% | 93% | 33/33 | 33/33 |
| cpu:npu | 532.9 | 125 | 28.4 | 82% | 95% | 33/33 | 33/33 |
| cpu:cpu | 563.5 | 126 | 26.5 | 84% | 100% | 33/33 | 33/33 |
| gpu:cpu | 624.3 | 247 | 21.6 | 92% | 111% | 33/33 | 33/33 |
| npu:gpu | 877.1 | 1990 | 14.3 | 99% | 156% | 33/33 | 33/33 |
| gpu:gpu | 918.2 | 243 | 14.3 | 95% | 163% | 33/33 | 33/33 |
| cpu:gpu | 962.9 | 128 | 14.3 | 90% | 171% | 33/33 | 33/33 |

Decode share of single turns: 30.2% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 26.5, gpu:cpu 21.6, npu:cpu 24.1 tok/s, a spread of 22%.

Same decoder, different cells: GPU decode cpu:gpu 14.3, gpu:gpu 14.3, npu:gpu 14.3 tok/s, a spread of 0%.

Same decoder, different cells: NPU decode cpu:npu 28.4, gpu:npu 26.6, npu:npu 28.3 tok/s, a spread of 7%.

Fastest npu:npu, gpu:npu +15.8%, npu:cpu +17.3%, cpu:npu +19.3%, cpu:cpu +26.1%, gpu:cpu +39.7%, npu:gpu +96.3%, gpu:gpu +105.5%, cpu:gpu +115.5%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +7.5% | +7.5% | 8 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 106.5 °C |
| npu:cpu | cpu:gpu | 33 | +83.7% | +83.7% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 75.3 °C |
| npu:cpu | cpu:npu | 33 | +1.7% | +1.7% | 14 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 99.2 °C |
| npu:cpu | gpu:cpu | 33 | +19.1% | +7.6% | 1 of 33 | 0, 0.0 / 9, 60.6 | 106.9 / 106.9 °C |
| npu:cpu | gpu:gpu | 33 | +75.2% | +75.2% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 84.9 °C |
| npu:cpu | gpu:npu | 33 | -1.2% | -1.2% | 13 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 100.0 °C |
| npu:cpu | npu:gpu | 33 | +67.4% | +67.4% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 84.5 °C |
| npu:cpu | npu:npu | 33 | -14.7% | -14.7% | 25 of 33 | 0, 0.0 / 0, 0.0 | 106.9 / 98.4 °C |
| cpu:cpu | cpu:gpu | 33 | +70.9% | +70.9% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 75.3 °C |
| cpu:cpu | cpu:npu | 33 | -5.4% | -5.4% | 16 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 99.2 °C |
| cpu:cpu | gpu:cpu | 33 | +10.8% | +0.0% | 20 of 33 | 0, 0.0 / 9, 60.6 | 106.5 / 106.9 °C |
| cpu:cpu | gpu:gpu | 33 | +62.9% | +62.9% | 0 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 84.9 °C |
| cpu:cpu | gpu:npu | 33 | -8.1% | -8.1% | 22 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 100.0 °C |
| cpu:cpu | npu:gpu | 33 | +55.7% | +55.7% | 4 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 84.5 °C |
| cpu:cpu | npu:npu | 33 | -20.7% | -20.7% | 27 of 33 | 0, 0.0 / 0, 0.0 | 106.5 / 98.4 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 36.7 | 36.0 | 26.5 | 26.9 | 25.9 | 24.5 | 25.4 |
| cpu:gpu | 50.0 | 63.5 | 14.3 | 14.5 | 14.2 | 13.3 | 13.2 |
| cpu:npu | 30.0 | 34.6 | 28.4 | 28.4 | 28.1 | 27.5 | 27.8 |
| gpu:cpu | 34.8 | 39.6 | 21.6 | 25.3 | 23.7 | 22.0 | 21.6 |
| gpu:gpu | 51.9 | 63.5 | 14.3 | 14.6 | 14.2 | 13.3 | 13.2 |
| gpu:npu | 36.1 | 35.9 | 26.6 | 28.3 | 27.9 | 27.3 | 27.3 |
| npu:cpu | 36.1 | 39.9 | 24.1 | 24.7 | 23.8 | 22.5 | 22.1 |
| npu:gpu | 53.2 | 63.5 | 14.3 | 14.6 | 14.3 | 13.3 | 13.2 |
| npu:npu | 32.4 | 34.8 | 28.3 | 28.5 | 28.0 | 27.5 | 27.7 |

Shortest decode step 22.19 ms, longest 8431.80 ms.

## Qwen3-4B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: gpu:cpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| gpu:cpu | 1446.3 | 104 | 9.4 | 92% | n/a | 33/33 | 33/33 |

Decode share of single turns: 44.6% to 98.8%.

Fastest gpu:cpu.

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| gpu:cpu | 64.8 | 83.7 | 9.4 | 4.2 | 11.2 | 10.3 | 10.1 |

Shortest decode step 46.61 ms, longest 17074.26 ms.

