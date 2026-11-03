## Llama-3.2-3B-Instruct-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| gpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: gpu:cpu, gpu:npu, npu:npu, gpu:gpu, cpu:cpu, npu:gpu, npu:cpu, cpu:npu, cpu:gpu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 393, mean 367, max 1023, total 12107.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| npu:npu | 792.8 | 859 | 15.5 | 98% | 80% | 33/33 | 33/33 |
| gpu:npu | 817.0 | 151 | 16.4 | 91% | 82% | 33/33 | 33/33 |
| npu:cpu | 864.2 | 864 | 14.2 | 98% | 87% | 33/33 | 33/33 |
| gpu:cpu | 930.8 | 151 | 14.2 | 92% | 94% | 33/33 | 33/33 |
| cpu:npu | 960.0 | 50 | 16.6 | 76% | 96% | 33/33 | 33/33 |
| cpu:cpu | 995.3 | 74 | 14.4 | 84% | 100% | 33/33 | 33/33 |
| npu:gpu | 1156.5 | 1133 | 10.6 | 99% | 116% | 33/33 | 33/33 |
| gpu:gpu | 1246.9 | 148 | 10.4 | 94% | 125% | 33/33 | 33/33 |
| cpu:gpu | 1313.1 | 81 | 10.3 | 89% | 132% | 33/33 | 33/33 |

Decode share of single turns: 28.9% to 99.8%.

Same decoder, different cells: CPU decode cpu:cpu 14.4, gpu:cpu 14.2, npu:cpu 14.2 tok/s, a spread of 2%.

Same decoder, different cells: GPU decode cpu:gpu 10.3, gpu:gpu 10.4, npu:gpu 10.6 tok/s, a spread of 2%.

Same decoder, different cells: NPU decode cpu:npu 16.6, gpu:npu 16.4, npu:npu 15.5 tok/s, a spread of 7%.

Fastest npu:npu, gpu:npu +3.1%, npu:cpu +9.0%, gpu:cpu +17.4%, cpu:npu +21.1%, cpu:cpu +25.5%, npu:gpu +45.9%, gpu:gpu +57.3%, cpu:gpu +65.6%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | +15.2% | +16.1% | 0 of 33 | 9, 61.5 / 8, 63.4 | 107.3 / 106.5 °C |
| npu:cpu | cpu:gpu | 33 | +51.9% | +63.6% | 1 of 33 | 9, 61.5 / 0, 0.0 | 107.3 / 80.3 °C |
| npu:cpu | cpu:npu | 33 | +11.1% | +19.6% | 9 of 33 | 9, 61.5 / 0, 0.0 | 107.3 / 100.8 °C |
| npu:cpu | gpu:cpu | 33 | +7.7% | +4.3% | 8 of 33 | 9, 61.5 / 14, 93.9 | 107.3 / 107.3 °C |
| npu:cpu | gpu:gpu | 33 | +44.3% | +55.3% | 1 of 33 | 9, 61.5 / 0, 0.0 | 107.3 / 86.1 °C |
| npu:cpu | gpu:npu | 33 | -5.5% | +1.8% | 15 of 33 | 9, 61.5 / 0, 0.0 | 107.3 / 100.8 °C |
| npu:cpu | npu:gpu | 33 | +33.8% | +44.1% | 1 of 33 | 9, 61.5 / 0, 0.0 | 107.3 / 87.2 °C |
| npu:cpu | npu:npu | 33 | -8.3% | -1.2% | 21 of 33 | 9, 61.5 / 0, 0.0 | 107.3 / 100.8 °C |
| cpu:cpu | cpu:gpu | 33 | +31.9% | +40.9% | 3 of 33 | 8, 63.4 / 0, 0.0 | 106.5 / 80.3 °C |
| cpu:cpu | cpu:npu | 33 | -3.5% | +3.0% | 20 of 33 | 8, 63.4 / 0, 0.0 | 106.5 / 100.8 °C |
| cpu:cpu | gpu:cpu | 33 | -6.5% | -10.2% | 26 of 33 | 8, 63.4 / 14, 93.9 | 106.5 / 107.3 °C |
| cpu:cpu | gpu:gpu | 33 | +25.3% | +33.8% | 8 of 33 | 8, 63.4 / 0, 0.0 | 106.5 / 86.1 °C |
| cpu:cpu | gpu:npu | 33 | -17.9% | -12.3% | 27 of 33 | 8, 63.4 / 0, 0.0 | 106.5 / 100.8 °C |
| cpu:cpu | npu:gpu | 33 | +16.2% | +24.1% | 13 of 33 | 8, 63.4 / 0, 0.0 | 106.5 / 87.2 °C |
| cpu:cpu | npu:npu | 33 | -20.3% | -14.9% | 25 of 33 | 8, 63.4 / 0, 0.0 | 106.5 / 100.8 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 11 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 50.5 | 62.5 | 14.4 | 16.2 | 15.4 | 14.3 | 9.0 |
| cpu:gpu | 70.7 | 82.1 | 10.3 | 10.3 | 10.0 | 9.1 | 9.8 |
| cpu:npu | 48.4 | 57.4 | 16.6 | 17.3 | 16.8 | 15.6 | 13.5 |
| gpu:cpu | 54.0 | 60.9 | 14.2 | 8.2 | 15.4 | 14.4 | 14.8 |
| gpu:gpu | 74.3 | 82.0 | 10.4 | 10.3 | 10.0 | 9.1 | 9.8 |
| gpu:npu | 54.0 | 56.6 | 16.4 | 15.9 | 15.8 | 15.4 | 16.2 |
| npu:cpu | 56.6 | 63.1 | 14.2 | 15.5 | 14.7 | 10.9 | 14.5 |
| npu:gpu | 72.8 | 81.7 | 10.6 | 10.6 | 10.2 | 9.4 | 10.0 |
| npu:npu | 55.9 | 59.4 | 15.5 | 15.5 | 15.4 | 15.1 | 13.7 |

Shortest decode step 37.96 ms, longest 12341.67 ms.

## Qwen3-4B-Q4_0

| cell | gate | waited s | GPU cap at start | CPU ceiling at start | turns done | failed | replies cut by --gen |
|---|---|---|---|---|---|---|---|
| gpu:cpu | passed | 5 | 0 | 0:3628/3628,6:4608/4608 | 33 | 0 | 0 |
| gpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:gpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| cpu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| npu:cpu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |
| gpu:npu | timed_out | 121 | 8 | 0:1900/3628,6:2227/4608 | 33 | 0 | 0 |

Cells in the order they ran: gpu:cpu, gpu:gpu, npu:gpu, cpu:npu, npu:npu, cpu:gpu, cpu:cpu, npu:cpu, gpu:npu.

Common cohort: 33 turns in 9 conversations completed in every cell. Decode steps a turn: median 401, mean 378, max 1046, total 12462.

| cell | total s | prefill tok/s | decode tok/s | decode share | vs cpu:cpu | CPU capped before/after | GPU capped before/after |
|---|---|---|---|---|---|---|---|
| gpu:cpu | 1004.2 | 114 | 13.8 | 90% | 73% | 23/24 | 23/24 |
| npu:npu | 1121.9 | 660 | 11.3 | 98% | 82% | 33/33 | 33/33 |
| cpu:npu | 1162.8 | 41 | 14.2 | 75% | 85% | 33/33 | 33/33 |
| gpu:npu | 1232.9 | 104 | 11.1 | 91% | 90% | 33/33 | 33/33 |
| cpu:cpu | 1371.6 | 49 | 11.0 | 83% | 100% | 33/33 | 33/33 |
| npu:cpu | 1398.2 | 615 | 9.0 | 99% | 102% | 33/33 | 33/33 |
| npu:gpu | 2859.8 | 971 | 4.4 | 100% | 209% | 33/33 | 33/33 |
| gpu:gpu | 2939.4 | 102 | 4.4 | 96% | 214% | 33/33 | 33/33 |
| cpu:gpu | 3076.5 | 53 | 4.4 | 93% | 224% | 33/33 | 33/33 |

Decode share of single turns: 26.2% to 99.9%.

Same decoder, different cells: CPU decode cpu:cpu 11.0, gpu:cpu 13.8, npu:cpu 9.0 tok/s, a spread of 53%.

Same decoder, different cells: GPU decode cpu:gpu 4.4, gpu:gpu 4.4, npu:gpu 4.4 tok/s, a spread of 1%.

Same decoder, different cells: NPU decode cpu:npu 14.2, gpu:npu 11.1, npu:npu 11.3 tok/s, a spread of 28%.

Fastest gpu:cpu, npu:npu +11.7%, cpu:npu +15.8%, gpu:npu +22.8%, cpu:cpu +36.6%, npu:cpu +39.2%, npu:gpu +184.8%, gpu:gpu +192.7%, cpu:gpu +206.4%.

Pairs on their own matched turns (y against x):

| x | y | turns | y / x | y / x without stalls | y faster on | stalls x / y (steps, s) | hottest CPU zone x / y |
|---|---|---|---|---|---|---|---|
| npu:cpu | cpu:cpu | 33 | -1.9% | +14.4% | 10 of 33 | 24, 255.8 / 6, 64.5 | 106.9 / 106.9 °C |
| npu:cpu | cpu:gpu | 33 | +120.0% | +105.9% | 2 of 33 | 24, 255.8 / 1237, 724.3 | 106.9 / 81.4 °C |
| npu:cpu | cpu:npu | 33 | -16.8% | +1.8% | 18 of 33 | 24, 255.8 / 0, 0.0 | 106.9 / 99.6 °C |
| npu:cpu | gpu:cpu | 33 | -28.2% | -12.1% | 26 of 33 | 24, 255.8 / 0, 0.0 | 106.9 / 106.5 °C |
| npu:cpu | gpu:gpu | 33 | +110.2% | +98.0% | 2 of 33 | 24, 255.8 / 1165, 677.1 | 106.9 / 79.5 °C |
| npu:cpu | gpu:npu | 33 | -11.8% | +7.9% | 14 of 33 | 24, 255.8 / 0, 0.0 | 106.9 / 99.6 °C |
| npu:cpu | npu:gpu | 33 | +104.5% | +88.5% | 2 of 33 | 24, 255.8 / 1206, 706.7 | 106.9 / 77.9 °C |
| npu:cpu | npu:npu | 33 | -19.8% | -1.8% | 20 of 33 | 24, 255.8 / 0, 0.0 | 106.9 / 99.6 °C |
| cpu:cpu | cpu:gpu | 33 | +124.3% | +80.0% | 4 of 33 | 6, 64.5 / 1237, 724.3 | 106.9 / 81.4 °C |
| cpu:cpu | cpu:npu | 33 | -15.2% | -11.0% | 30 of 33 | 6, 64.5 / 0, 0.0 | 106.9 / 99.6 °C |
| cpu:cpu | gpu:cpu | 33 | -26.8% | -23.2% | 33 of 33 | 6, 64.5 / 0, 0.0 | 106.9 / 106.5 °C |
| cpu:cpu | gpu:gpu | 33 | +114.3% | +73.1% | 8 of 33 | 6, 64.5 / 1165, 677.1 | 106.9 / 79.5 °C |
| cpu:cpu | gpu:npu | 33 | -10.1% | -5.7% | 24 of 33 | 6, 64.5 / 0, 0.0 | 106.9 / 99.6 °C |
| cpu:cpu | npu:gpu | 33 | +108.5% | +64.7% | 11 of 33 | 6, 64.5 / 1206, 706.7 | 106.9 / 77.9 °C |
| cpu:cpu | npu:npu | 33 | -18.2% | -14.2% | 26 of 33 | 6, 64.5 / 0, 0.0 | 106.9 / 99.6 °C |

Per decode step, over the common cohort (ms; tok/s by step range):

The range columns use only the 12 turns with 512 or more decode steps, so every range covers the same turns.

| cell | first decode step, median | later steps, median | tok/s whole reply | tok/s steps 1-63 | tok/s 64-255 | tok/s 256-511 | tok/s 512 on |
|---|---|---|---|---|---|---|---|
| cpu:cpu | 65.7 | 82.9 | 11.0 | 12.4 | 11.6 | 10.8 | 7.6 |
| cpu:gpu | 93.6 | 108.7 | 4.4 | 4.5 | 4.3 | 3.5 | 3.9 |
| cpu:npu | 56.9 | 69.3 | 14.2 | 14.5 | 14.1 | 13.5 | 13.3 |
| gpu:cpu | 64.8 | 69.4 | 13.8 | 13.8 | 13.4 | 12.7 | 13.0 |
| gpu:gpu | 93.2 | 107.3 | 4.4 | 4.6 | 4.4 | 3.5 | 4.0 |
| gpu:npu | 87.1 | 89.8 | 11.1 | 10.9 | 10.7 | 10.8 | 9.8 |
| npu:cpu | 70.6 | 87.4 | 9.0 | 5.8 | 8.4 | 10.2 | 7.5 |
| npu:gpu | 88.5 | 107.6 | 4.4 | 4.6 | 4.3 | 3.5 | 3.9 |
| npu:npu | 83.7 | 89.7 | 11.3 | 11.6 | 11.3 | 11.0 | 9.8 |

Shortest decode step 39.55 ms, longest 18279.67 ms.

