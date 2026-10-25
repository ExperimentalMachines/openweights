## Loads

| run | model | processor | load ms | error |
|---|---|---|---|---|
| r7 | LFM2.5-2.6B-QAD-Q4_0 | cpu | 1122 |  |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | gpu | 4232 |  |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | npu | 1422 |  |

## Errors and skipped cells

None.

## LFM2.5-2.6B-QAD-Q4_0 (r7)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 135 / 27.2; first 1828 ms; handoff 0.0 ms; turn 4043 ms; 163.1 s | 138 / 21.4; first 1587 ms; handoff 0.8 ms; turn 4466 ms; 181.8 s | 137 / 20.2; first 1814 ms; handoff 0.8 ms; turn 4786 ms; 188.2 s |
| **gpu** | 238 / 27.7; first 990 ms; handoff 2.4 ms; turn 3049 ms; 124.3 s | 239 / 21.1; first 906 ms; handoff 0.0 ms; turn 3932 ms; 147.4 s | 253 / 21.0; first 839 ms; handoff 2.5 ms; turn 3841 ms; 145.7 s |
| **npu** | 1454 / 28.4; first 167 ms; handoff 1.1 ms; turn 2432 ms; 81.3 s | 1574 / 22.2; first 157 ms; handoff 2.1 ms; turn 3162 ms; 101.7 s | 1493 / 21.0; first 176 ms; handoff 0.0 ms; turn 3165 ms; 106.8 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 4 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 29 / 0 / 4 | 98 / 83 / 82 | 175 / 33.0 |
| cpu:gpu | 26 / 0 / 7 | 78 / 79 / 74 | 190 / 29.6 |
| cpu:npu | 29 / 0 / 4 | 81 / 76 / 84 | 196 / 18.6 |
| gpu:cpu | 27 / 0 / 6 | 97 / 81 / 81 | 420 / 33.1 |
| gpu:gpu | 28 / 0 / 5 | 75 / 77 / 72 | 425 / 29.1 |
| gpu:npu | 24 / 0 / 9 | 77 / 72 / 81 | 439 / 21.1 |
| npu:cpu | 23 / 0 / 10 | 99 / 83 / 83 | 1722 / 33.1 |
| npu:gpu | 23 / 0 / 10 | 76 / 78 / 72 | 1754 / 29.1 |
| npu:npu | 20 / 0 / 13 | 78 / 72 / 82 | 1743 / 21.1 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 141 (6) | 143 (9) | 136 (16) | 135 (2) |
| gpu | 172 (6) | 235 (9) | 251 (16) | 232 (2) |
| npu | 728 (6) | 1330 (9) | 1584 (16) | 1566 (2) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 28.9 (16) | 27.6 (13) | 24.5 (4) | - |
| gpu | 22.5 (16) | 21.9 (13) | 17.7 (4) | - |
| npu | 21.2 (16) | 20.4 (13) | 20.0 (4) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 23 | 10 | 41.5 |
| cpu:npu | 18 | 15 | 31 |
| gpu:cpu | 19 | 14 | 25.5 |
| gpu:gpu | 20 | 13 | 37 |
| gpu:npu | 17 | 16 | 35.0 |
| npu:cpu | 22 | 11 | 33 |
| npu:gpu | 20 | 13 | 30 |
| npu:npu | 10 | 23 | 33 |

## Across models

Prefill and decode rates are each processor's mean over its three cells (all matched turns, throttled or not); the pairing columns are the summed turn time over the matched turns.

| run | model | turns | prefill tok/s CPU / GPU / NPU | decode tok/s CPU / GPU / NPU | fastest pairing | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | slowest pairing |
|---|---|---|---|---|---|---|---|---|---|---|
| r7 | LFM2.5-2.6B-QAD-Q4_0 | 33 | 137 / 243 / 1507 | 27.8 / 21.6 / 20.7 | npu:cpu (81.3 s) | 81.3 s | 106.8 s | 147.4 s | 163.1 s | cpu:npu (188.2 s) |

## Cooling before each cell

| run | model | cell | gate | waited s | CPU / GPU / NPU °C | GPU cap |
|---|---|---|---|---|---|---|
| r7 | LFM2.5-2.6B-QAD-Q4_0 | gpu:cpu | timed_out | 302 | 56 / 55 / 56 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | cpu:cpu | passed | 297 | 55 / 54 / 55 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | cpu:npu | passed | 242 | 55 / 54 / 55 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | npu:cpu | timed_out | 302 | 56 / 54 / 55 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | npu:gpu | passed | 252 | 55 / 54 / 55 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | gpu:npu | passed | 196 | 55 / 54 / 55 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | cpu:gpu | passed | 216 | 55 / 54 / 54 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | npu:npu | passed | 257 | 55 / 54 / 54 | 0 |
| r7 | LFM2.5-2.6B-QAD-Q4_0 | gpu:gpu | passed | 201 | 55 / 54 / 54 | 0 |

## Conditions

- env: {"label": "r7", "model": "LFM2.5-2.6B-QAD-Q4_0.gguf", "when": "before", "time": 1792905894, "order": "gpu:cpu,cpu:cpu,cpu:npu,npu:cpu,npu:gpu,gpu:npu,cpu:gpu,npu:npu,gpu:gpu", "temps": "cpullc-0-0=61000 cpullc-0-1=61000 cpu-0-2-1=60200 cpu-0-3-0=59000 cpu-0-3-1=59000 cpu-0-4-0=60200 cpu-0-4-1=60600 cpu-0-5-0=59800 cpu-0-5-1=59800 cpullc-1-0=59000 cpullc-1-1=59000 cpu-1-0-0=58700 cpu-1-0-1=58700 cpu-1-1-0=59000 cpu-1-1-1=59400 nsphvx-0=56900 nsphvx-1=57300 nsphvx-2=56500 nsphvx-3=56500 nsphmx-0=56900 nsphmx-1=56900 nsphmx-2=57300 nsphmx-3=57300 gpuss-0=57000 gpuss-1=56600 gpuss-2=57400 gpuss-3=57000 gpuss-4=57700 gpuss-5=56600 gpuss-6=57700 gpuss-7=57700 gpuss-8=57400 gpuss-9=57000 gpuss-10=57400 ddr=58100 cpu-0-0-0=59000 cpu-hw-trip-0=105000 sys-therm-0=55802 cpu-hw-trip-1=105000 cpu-0-0-1=59000 sys-therm-1=56779 sys-therm-2=52915 sys-therm-3=55142 cpu-0-1-0=59000 sys-therm-4=57239 sys-therm-5=57740 sys-therm-6=55493 sys-therm-7=56110 sys-therm-9=59496 cpu-0-1-1=58700 sys-therm-11=50096 sys-therm-12=38226 sys-therm-8=50377 cpu-0-2-0=59000 battery=39000 ", "memavail_kb": 20545008}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=55200 cpu-0-0-1=54800 cpu-0-1-0=54400 cpu-0-1-1=54400 cpu-0-2-0=56000 cpu-0-2-1=55600 cpu-0-3-0=54800 cpu-0-3-1=54400 cpu-0-4-0=55200 cpu-0-4-1=55200 cpu-0-5-0=54800 cpu-0-5-1=55200 cpu-1-0-0=56000 cpu-1-0-1=56000 cpu-1-1-0=54400 cpu-1-1-1=55200 nsphvx-0=54600 nsphvx-1=54900 nsphvx-2=54600 nsphvx-3=54900 gpuss-0=54300 gpuss-1=53500 gpuss-2=53900 gpuss-3=53500 gpuss-4=53900 gpuss-5=53500 gpuss-6=54300 gpuss-7=54300 gpuss-8=54600 gpuss-9=54300 gpuss-10=54300 ddr=55000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53600 cpu-0-1-1=54000 cpu-0-2-0=55200 cpu-0-2-1=55200 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54400 cpu-0-4-1=54400 cpu-0-5-0=54000 cpu-0-5-1=54400 cpu-1-0-0=55600 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54400 nsphvx-0=53800 nsphvx-1=54600 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52700 gpuss-2=53500 gpuss-3=52700 gpuss-4=53100 gpuss-5=52300 gpuss-6=53500 gpuss-7=53500 gpuss-8=53500 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=52900 cpu-0-2-0=54400 cpu-0-2-1=54000 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=54200 nsphvx-1=54600 nsphvx-2=54200 nsphvx-3=54600 gpuss-0=53900 gpuss-1=53100 gpuss-2=53900 gpuss-3=53100 gpuss-4=53500 gpuss-5=53100 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53900 gpuss-10=53900 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54400 cpu-0-0-1=54400 cpu-0-1-0=54000 cpu-0-1-1=54800 cpu-0-2-0=55600 cpu-0-2-1=55200 cpu-0-3-0=54000 cpu-0-3-1=54000 cpu-0-4-0=54800 cpu-0-4-1=54800 cpu-0-5-0=54400 cpu-0-5-1=54000 cpu-1-0-0=55600 cpu-1-0-1=55600 cpu-1-1-0=54400 cpu-1-1-1=54800 nsphvx-0=54600 nsphvx-1=54900 nsphvx-2=54600 nsphvx-3=54900 gpuss-0=53900 gpuss-1=53100 gpuss-2=54300 gpuss-3=53500 gpuss-4=53500 gpuss-5=53100 gpuss-6=53900 gpuss-7=54300 gpuss-8=54300 gpuss-9=53900 gpuss-10=53900 ddr=55000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54000 cpu-0-4-1=54400 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=55200 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54400 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54400 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54400 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=55200 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52700 gpuss-2=53500 gpuss-3=52700 gpuss-4=53100 gpuss-5=52300 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53100 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53600 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=54000 cpu-0-5-1=53600 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=54000 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=53800 gpuss-0=53500 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r7", "model": "gguf/LFM2.5-2.6B-QAD-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=71800 cpu-0-0-1=72200 cpu-0-1-0=71400 cpu-0-1-1=71800 cpu-0-2-0=74900 cpu-0-2-1=74500 cpu-0-3-0=72900 cpu-0-3-1=72900 cpu-0-4-0=73300 cpu-0-4-1=73300 cpu-0-5-0=72600 cpu-0-5-1=72600 cpu-1-0-0=75700 cpu-1-0-1=75300 cpu-1-1-0=71400 cpu-1-1-1=72600 nsphvx-0=71100 nsphvx-1=71500 nsphvx-2=70700 nsphvx-3=70700 gpuss-0=76000 gpuss-1=77100 gpuss-2=77100 gpuss-3=76800 gpuss-4=77900 gpuss-5=77100 gpuss-6=76400 gpuss-7=77100 gpuss-8=77100 gpuss-9=77500 gpuss-10=77500 ddr=77900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r7", "model": "LFM2.5-2.6B-QAD-Q4_0.gguf", "when": "after", "time": 1792909579, "exit": 0, "temps": "cpullc-0-0=72200 cpullc-0-1=72600 cpu-0-2-1=72600 cpu-0-3-0=71000 cpu-0-3-1=70600 cpu-0-4-0=72200 cpu-0-4-1=71400 cpu-0-5-0=71000 cpu-0-5-1=71000 cpullc-1-0=71000 cpullc-1-1=70600 cpu-1-0-0=70600 cpu-1-0-1=70600 cpu-1-1-0=69900 cpu-1-1-1=70200 nsphvx-0=66900 nsphvx-1=67200 nsphvx-2=66500 nsphvx-3=67200 nsphmx-0=67200 nsphmx-1=66500 nsphmx-2=67200 nsphmx-3=67600 gpuss-0=68200 gpuss-1=67400 gpuss-2=68200 gpuss-3=67100 gpuss-4=67400 gpuss-5=67100 gpuss-6=67800 gpuss-7=68200 gpuss-8=67800 gpuss-9=67400 gpuss-10=67400 ddr=68200 cpu-0-0-0=69100 cpu-hw-trip-0=105000 sys-therm-0=61905 cpu-hw-trip-1=105000 cpu-0-0-1=67900 sys-therm-1=65752 sys-therm-2=54172 sys-therm-3=55677 cpu-0-1-0=67500 sys-therm-4=60075 sys-therm-5=67931 sys-therm-6=60065 sys-therm-7=61993 sys-therm-9=56803 cpu-0-1-1=68300 sys-therm-11=46914 sys-therm-12=38372 sys-therm-8=47058 cpu-0-2-0=68300 battery=39000 ", "memavail_kb": 20612768}
- run: {"label": "r7", "seed": "1", "bin": "pd_bench5", "gen": 64, "ctx": 8192, "threads": 6, "turns": 6, "cool": 55, "hexagon_env": "", "status": 0}
