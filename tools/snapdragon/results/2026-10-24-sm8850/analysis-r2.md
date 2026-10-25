## Loads

| run | model | processor | load ms | error |
|---|---|---|---|---|
| r2 | Llama-3.2-1B-Instruct-Q4_0 | cpu | 699 |  |
| r2 | Llama-3.2-1B-Instruct-Q4_0 | gpu | 3198 |  |
| r2 | Llama-3.2-1B-Instruct-Q4_0 | npu | 813 |  |
| r2 | google_gemma-3-1b-it-Q4_0 | cpu | 655 |  |
| r2 | google_gemma-3-1b-it-Q4_0 | gpu | 1506 |  |
| r2 | google_gemma-3-1b-it-Q4_0 | npu | 733 |  |

## Errors and skipped cells

None.

## Llama-3.2-1B-Instruct-Q4_0 (r2)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 207 / 41.4; first 883 ms; handoff 0.0 ms; turn 2440 ms; 105.6 s | 209 / 28.1; first 902 ms; handoff 1.5 ms; turn 3148 ms; 129.3 s | 211 / 37.7; first 884 ms; handoff 1.3 ms; turn 2600 ms; 109.9 s |
| **gpu** | 392 / 41.1; first 525 ms; handoff 2.5 ms; turn 2003 ms; 80.3 s | 401 / 28.4; first 477 ms; handoff 0.0 ms; turn 2641 ms; 102.0 s | 396 / 36.7; first 486 ms; handoff 3.2 ms; turn 2099 ms; 86.1 s |
| **npu** | 2956 / 41.2; first 76 ms; handoff 1.5 ms; turn 1559 ms; 54.6 s | 2903 / 28.3; first 85 ms; handoff 4.0 ms; turn 2200 ms; 78.0 s | 2899 / 37.7; first 78 ms; handoff 0.0 ms; turn 1618 ms; 59.1 s |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 267 (5) | 255 (11) | 198 (16) | 242 (1) |
| gpu | 273 (5) | 437 (11) | 388 (16) | 455 (1) |
| npu | 1217 (5) | 2578 (11) | 3046 (16) | 3444 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 47.7 (16) | 38.5 (13) | 31.5 (4) | - |
| gpu | 32.4 (16) | 26.9 (13) | 21.0 (4) | - |
| npu | 41.0 (16) | 35.8 (13) | 30.9 (4) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 25 | 8 | 33.5 |
| cpu:npu | 18 | 15 | 19 |
| gpu:cpu | 16 | 17 | 18 |
| gpu:gpu | 16 | 17 | 19 |
| gpu:npu | 12 | 21 | 23 |
| npu:cpu | 19 | 14 | 21.5 |
| npu:gpu | 22 | 11 | 19 |
| npu:npu | 21 | 12 | 20.5 |

## google_gemma-3-1b-it-Q4_0 (r2)

**Incomplete: 3 of nine cells ran.** 20 turns in 5 conversations matched across the 3 cells that ran. Cells with no successful turn: cpu:cpu, cpu:gpu, cpu:npu, gpu:cpu, npu:cpu, npu:gpu.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | did not run | did not run | did not run |
| **gpu** | did not run | 419 / 25.6; first 478 ms; handoff 0.0 ms; turn 2876 ms; 65.0 s | 405 / 44.3; first 559 ms; handoff 3.9 ms; turn 1972 ms; 45.0 s |
| **npu** | did not run | did not run | 3239 / 43.7; first 72 ms; handoff 0.0 ms; turn 1510 ms; 30.9 s |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | - | - | - | - |
| gpu | 250 (2) | 411 (8) | 416 (10) | - |
| npu | 1245 (2) | 2634 (8) | 3470 (10) | - |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | - | - | - | - |
| gpu | 29.1 (9) | 22.4 (6) | 24.4 (5) | - |
| npu | 44.6 (9) | 43.9 (6) | 42.9 (5) | - |

No cpu:cpu cell among the cells that ran, so no agreement table.

## Across models

Prefill and decode rates are each processor's mean over its three cells (all matched turns, throttled or not); the pairing columns are the summed turn time over the matched turns.

| run | model | turns | prefill tok/s CPU / GPU / NPU | decode tok/s CPU / GPU / NPU | fastest pairing | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | slowest pairing |
|---|---|---|---|---|---|---|---|---|---|---|
| r2 | Llama-3.2-1B-Instruct-Q4_0 | 33 | 209 / 396 / 2919 | 41.3 / 28.3 / 37.4 | npu:cpu (54.6 s) | 54.6 s | 59.1 s | 102.0 s | 105.6 s | cpu:gpu (129.3 s) |
| r2 | google_gemma-3-1b-it-Q4_0 (incomplete) | 20 | nan / 412 / 3239 | nan / 25.6 / 44.0 | npu:npu (30.9 s) | did not run | 30.9 s | 65.0 s | did not run | gpu:gpu (65.0 s) |

## Conditions

- env: {"label": "r2", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "before", "time": 1792870264, "order": "cpu:npu,cpu:gpu,npu:gpu,gpu:npu,npu:npu,npu:cpu,gpu:gpu,gpu:cpu,cpu:cpu", "temps": "cpullc-0-0=65900 cpullc-0-1=65900 cpu-0-2-1=65900 cpu-0-3-0=65100 cpu-0-3-1=65100 cpu-0-4-0=66300 cpu-0-4-1=66300 cpu-0-5-0=65900 cpu-0-5-1=66300 cpullc-1-0=64700 cpullc-1-1=65100 cpu-1-0-0=64300 cpu-1-0-1=65100 cpu-1-1-0=64700 cpu-1-1-1=65100 nsphvx-0=64100 nsphvx-1=62900 nsphvx-2=63700 nsphvx-3=63700 nsphmx-0=62900 nsphmx-1=63700 nsphmx-2=63300 nsphmx-3=63700 gpuss-0=64000 gpuss-1=64000 gpuss-2=64000 gpuss-3=63200 gpuss-4=63200 gpuss-5=63200 gpuss-6=63600 gpuss-7=63600 gpuss-8=63600 gpuss-9=64000 gpuss-10=64000 ddr=64300 cpu-0-0-0=66300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=61596 cpu-0-0-1=66300 sys-therm-1=63553 sys-therm-2=55025 sys-therm-3=56770 sys-therm-4=60514 cpu-0-1-0=65100 sys-therm-5=64637 sys-therm-6=60300 sys-therm-7=61777 sys-therm-9=58364 cpu-0-1-1=65100 sys-therm-11=48172 sys-therm-12=36143 sys-therm-8=49052 battery=39000 cpu-0-2-0=64700 ", "memavail_kb": 19537776}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=72900 cpu-0-0-1=74400 cpu-0-1-0=71300 cpu-0-1-1=71700 cpu-0-2-0=73600 cpu-0-2-1=73600 cpu-0-3-0=71300 cpu-0-3-1=72100 cpu-0-4-0=74000 cpu-0-4-1=74000 cpu-0-5-0=72500 cpu-0-5-1=72500 cpu-1-0-0=73600 cpu-1-0-1=73300 cpu-1-1-0=70500 cpu-1-1-1=71700 nsphvx-0=76400 nsphvx-1=76400 nsphvx-2=76400 nsphvx-3=77200 gpuss-0=69400 gpuss-1=67800 gpuss-2=69000 gpuss-3=67100 gpuss-4=66700 gpuss-5=67400 gpuss-6=68200 gpuss-7=69400 gpuss-8=68200 gpuss-9=69800 gpuss-10=68600 ddr=71300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=77500 cpu-0-0-1=77500 cpu-0-1-0=76800 cpu-0-1-1=77100 cpu-0-2-0=79500 cpu-0-2-1=79900 cpu-0-3-0=77500 cpu-0-3-1=77500 cpu-0-4-0=78700 cpu-0-4-1=78300 cpu-0-5-0=77500 cpu-0-5-1=77900 cpu-1-0-0=79100 cpu-1-0-1=78700 cpu-1-1-0=75600 cpu-1-1-1=76800 nsphvx-0=83800 nsphvx-1=83000 nsphvx-2=83800 nsphvx-3=84100 gpuss-0=76000 gpuss-1=74800 gpuss-2=75600 gpuss-3=73300 gpuss-4=73300 gpuss-5=73600 gpuss-6=74400 gpuss-7=76000 gpuss-8=74400 gpuss-9=76400 gpuss-10=74800 ddr=77500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=72500 cpu-0-0-1=72500 cpu-0-1-0=72100 cpu-0-1-1=72500 cpu-0-2-0=74400 cpu-0-2-1=74000 cpu-0-3-0=73300 cpu-0-3-1=72900 cpu-0-4-0=73300 cpu-0-4-1=72900 cpu-0-5-0=72500 cpu-0-5-1=72500 cpu-1-0-0=74000 cpu-1-0-1=73300 cpu-1-1-0=70900 cpu-1-1-1=71700 nsphvx-0=72900 nsphvx-1=72600 nsphvx-2=72600 nsphvx-3=72900 gpuss-0=75600 gpuss-1=76000 gpuss-2=76000 gpuss-3=74800 gpuss-4=75200 gpuss-5=75200 gpuss-6=74400 gpuss-7=76000 gpuss-8=75200 gpuss-9=76000 gpuss-10=75600 ddr=77100 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=70900 cpu-0-0-1=70900 cpu-0-1-0=70200 cpu-0-1-1=70200 cpu-0-2-0=72900 cpu-0-2-1=72100 cpu-0-3-0=71300 cpu-0-3-1=70900 cpu-0-4-0=71700 cpu-0-4-1=70900 cpu-0-5-0=70500 cpu-0-5-1=71300 cpu-1-0-0=72100 cpu-1-0-1=71700 cpu-1-1-0=69000 cpu-1-1-1=69800 nsphvx-0=71800 nsphvx-1=71400 nsphvx-2=71400 nsphvx-3=71400 gpuss-0=74000 gpuss-1=74800 gpuss-2=74400 gpuss-3=73600 gpuss-4=74000 gpuss-5=74000 gpuss-6=72900 gpuss-7=74000 gpuss-8=73600 gpuss-9=74400 gpuss-10=74400 ddr=75600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=75200 cpu-0-0-1=75600 cpu-0-1-0=74400 cpu-0-1-1=74400 cpu-0-2-0=76800 cpu-0-2-1=77100 cpu-0-3-0=75200 cpu-0-3-1=75600 cpu-0-4-0=76800 cpu-0-4-1=76000 cpu-0-5-0=75200 cpu-0-5-1=75200 cpu-1-0-0=76400 cpu-1-0-1=76000 cpu-1-1-0=72500 cpu-1-1-1=73300 nsphvx-0=81400 nsphvx-1=81100 nsphvx-2=82200 nsphvx-3=82200 gpuss-0=74000 gpuss-1=72900 gpuss-2=74000 gpuss-3=71300 gpuss-4=71300 gpuss-5=71700 gpuss-6=72500 gpuss-7=73600 gpuss-8=72500 gpuss-9=74400 gpuss-10=73300 ddr=75600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=81800 cpu-0-0-1=82600 cpu-0-1-0=80600 cpu-0-1-1=81000 cpu-0-2-0=83700 cpu-0-2-1=84100 cpu-0-3-0=81400 cpu-0-3-1=82200 cpu-0-4-0=84100 cpu-0-4-1=84500 cpu-0-5-0=82200 cpu-0-5-1=83300 cpu-1-0-0=83300 cpu-1-0-1=83000 cpu-1-1-0=79100 cpu-1-1-1=80600 nsphvx-0=86900 nsphvx-1=86500 nsphvx-2=87200 nsphvx-3=87200 gpuss-0=78700 gpuss-1=77500 gpuss-2=77900 gpuss-3=75600 gpuss-4=75600 gpuss-5=76000 gpuss-6=76800 gpuss-7=78300 gpuss-8=76800 gpuss-9=79100 gpuss-10=77100 ddr=80200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=95400 cpu-0-0-1=96500 cpu-0-1-0=91500 cpu-0-1-1=91900 cpu-0-2-0=96100 cpu-0-2-1=97300 cpu-0-3-0=92300 cpu-0-3-1=93000 cpu-0-4-0=98100 cpu-0-4-1=97700 cpu-0-5-0=93800 cpu-0-5-1=94200 cpu-1-0-0=94600 cpu-1-0-1=96100 cpu-1-1-0=95000 cpu-1-1-1=98100 nsphvx-0=84500 nsphvx-1=83400 nsphvx-2=84100 nsphvx-3=84100 gpuss-0=83000 gpuss-1=82600 gpuss-2=83300 gpuss-3=81000 gpuss-4=81400 gpuss-5=81400 gpuss-6=84500 gpuss-7=86400 gpuss-8=84100 gpuss-9=85300 gpuss-10=83700 ddr=88000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=77100 cpu-0-0-1=77500 cpu-0-1-0=76000 cpu-0-1-1=76400 cpu-0-2-0=78700 cpu-0-2-1=78300 cpu-0-3-0=77100 cpu-0-3-1=77100 cpu-0-4-0=78300 cpu-0-4-1=78300 cpu-0-5-0=76800 cpu-0-5-1=77100 cpu-1-0-0=78300 cpu-1-0-1=78700 cpu-1-1-0=75600 cpu-1-1-1=76800 nsphvx-0=76400 nsphvx-1=75300 nsphvx-2=76000 nsphvx-3=76000 gpuss-0=78700 gpuss-1=79100 gpuss-2=79500 gpuss-3=78300 gpuss-4=78700 gpuss-5=78700 gpuss-6=78300 gpuss-7=79100 gpuss-8=78700 gpuss-9=79900 gpuss-10=79100 ddr=80600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=94200 cpu-0-0-1=95800 cpu-0-1-0=91900 cpu-0-1-1=92300 cpu-0-2-0=95000 cpu-0-2-1=95800 cpu-0-3-0=91100 cpu-0-3-1=92700 cpu-0-4-0=96500 cpu-0-4-1=96900 cpu-0-5-0=93400 cpu-0-5-1=93000 cpu-1-0-0=94600 cpu-1-0-1=97700 cpu-1-1-0=92700 cpu-1-1-1=95000 nsphvx-0=82600 nsphvx-1=81800 nsphvx-2=82200 nsphvx-3=82200 gpuss-0=81800 gpuss-1=81800 gpuss-2=83000 gpuss-3=80200 gpuss-4=80200 gpuss-5=81000 gpuss-6=83300 gpuss-7=84900 gpuss-8=83300 gpuss-9=84500 gpuss-10=83000 ddr=86800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r2", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=98900 cpu-0-0-1=99600 cpu-0-1-0=96100 cpu-0-1-1=96900 cpu-0-2-0=99600 cpu-0-2-1=100400 cpu-0-3-0=95400 cpu-0-3-1=96500 cpu-0-4-0=100400 cpu-0-4-1=100400 cpu-0-5-0=98100 cpu-0-5-1=98500 cpu-1-0-0=96900 cpu-1-0-1=98500 cpu-1-1-0=98500 cpu-1-1-1=101600 nsphvx-0=86500 nsphvx-1=85700 nsphvx-2=86100 nsphvx-3=86100 gpuss-0=84900 gpuss-1=84100 gpuss-2=85700 gpuss-3=83300 gpuss-4=83300 gpuss-5=83700 gpuss-6=86800 gpuss-7=88400 gpuss-8=86400 gpuss-9=87200 gpuss-10=85700 ddr=89900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r2", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "after", "time": 1792871085, "exit": 0, "temps": "cpullc-0-0=92300 cpullc-0-1=93400 cpu-0-2-1=92700 cpu-0-3-0=89200 cpu-0-3-1=89600 cpu-0-4-0=91500 cpu-0-4-1=91100 cpu-0-5-0=89200 cpu-0-5-1=89200 cpullc-1-0=88400 cpullc-1-1=88800 cpu-1-0-0=86800 cpu-1-0-1=87200 cpu-1-1-0=87600 cpu-1-1-1=88400 nsphvx-0=82200 nsphvx-1=81400 nsphvx-2=81800 nsphvx-3=81800 nsphmx-0=82200 nsphmx-1=83000 nsphmx-2=82200 nsphmx-3=83400 gpuss-0=82600 gpuss-1=82200 gpuss-2=82600 gpuss-3=81400 gpuss-4=81400 gpuss-5=81800 gpuss-6=83300 gpuss-7=83700 gpuss-8=82600 gpuss-9=83300 gpuss-10=82600 ddr=84100 cpu-0-0-0=87200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=71306 cpu-0-0-1=86400 sys-therm-1=77330 sys-therm-2=62240 sys-therm-3=66318 sys-therm-4=73400 cpu-0-1-0=84100 sys-therm-5=82486 sys-therm-6=69807 sys-therm-7=71865 sys-therm-9=62980 cpu-0-1-1=83000 sys-therm-11=53349 sys-therm-12=36633 sys-therm-8=54564 battery=39000 cpu-0-2-0=83300 ", "memavail_kb": 19539256}
- env: {"label": "r2", "model": "google_gemma-3-1b-it-Q4_0.gguf", "when": "before", "time": 1792871087, "order": "npu:npu,gpu:npu,gpu:gpu,cpu:cpu,npu:gpu,cpu:npu,cpu:gpu,gpu:cpu,npu:cpu", "temps": "cpullc-0-0=83700 cpullc-0-1=83700 cpu-0-2-1=83700 cpu-0-3-0=82200 cpu-0-3-1=82200 cpu-0-4-0=83700 cpu-0-4-1=84500 cpu-0-5-0=83000 cpu-0-5-1=83000 cpullc-1-0=81000 cpullc-1-1=81400 cpu-1-0-0=80200 cpu-1-0-1=80600 cpu-1-1-0=81000 cpu-1-1-1=81800 nsphvx-0=77200 nsphvx-1=76800 nsphvx-2=77200 nsphvx-3=77200 nsphmx-0=77200 nsphmx-1=78000 nsphmx-2=77600 nsphmx-3=78400 gpuss-0=77500 gpuss-1=77500 gpuss-2=77900 gpuss-3=77100 gpuss-4=76800 gpuss-5=76800 gpuss-6=77900 gpuss-7=77900 gpuss-8=77100 gpuss-9=77900 gpuss-10=77500 ddr=78700 cpu-0-0-0=81000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=71295 cpu-0-0-1=81000 sys-therm-1=75848 sys-therm-2=62257 sys-therm-3=66354 sys-therm-4=73282 cpu-0-1-0=80200 sys-therm-5=79491 sys-therm-6=69780 sys-therm-7=71722 sys-therm-9=62996 cpu-0-1-1=80200 sys-therm-11=53379 sys-therm-12=36618 sys-therm-8=54604 battery=39000 cpu-0-2-0=79100 ", "memavail_kb": 19525836}
- cell: {"when": "start", "label": "r2", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=80200 cpu-0-0-1=80600 cpu-0-1-0=79100 cpu-0-1-1=79500 cpu-0-2-0=81800 cpu-0-2-1=81800 cpu-0-3-0=79900 cpu-0-3-1=79900 cpu-0-4-0=81800 cpu-0-4-1=81800 cpu-0-5-0=79900 cpu-0-5-1=79900 cpu-1-0-0=82200 cpu-1-0-1=82200 cpu-1-1-0=79100 cpu-1-1-1=79900 nsphvx-0=84900 nsphvx-1=84100 nsphvx-2=84500 nsphvx-3=85300 gpuss-0=78300 gpuss-1=77500 gpuss-2=77900 gpuss-3=76400 gpuss-4=76400 gpuss-5=76400 gpuss-6=77100 gpuss-7=78300 gpuss-8=77500 gpuss-9=78700 gpuss-10=77500 ddr=80200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=80600 cpu-0-0-1=81400 cpu-0-1-0=79900 cpu-0-1-1=80200 cpu-0-2-0=82600 cpu-0-2-1=82600 cpu-0-3-0=80600 cpu-0-3-1=80600 cpu-0-4-0=82200 cpu-0-4-1=81800 cpu-0-5-0=80600 cpu-0-5-1=81000 cpu-1-0-0=82200 cpu-1-0-1=81800 cpu-1-1-0=78300 cpu-1-1-1=79100 nsphvx-0=86500 nsphvx-1=86500 nsphvx-2=87200 nsphvx-3=87600 gpuss-0=79900 gpuss-1=78700 gpuss-2=79900 gpuss-3=77500 gpuss-4=77100 gpuss-5=77500 gpuss-6=78300 gpuss-7=79500 gpuss-8=78300 gpuss-9=79900 gpuss-10=78300 ddr=81000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r2", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=77500 cpu-0-0-1=78300 cpu-0-1-0=77100 cpu-0-1-1=77500 cpu-0-2-0=79900 cpu-0-2-1=79500 cpu-0-3-0=77500 cpu-0-3-1=78300 cpu-0-4-0=79100 cpu-0-4-1=78700 cpu-0-5-0=78700 cpu-0-5-1=79100 cpu-1-0-0=79500 cpu-1-0-1=79100 cpu-1-1-0=75600 cpu-1-1-1=76400 nsphvx-0=83800 nsphvx-1=83800 nsphvx-2=84100 nsphvx-3=84100 gpuss-0=77100 gpuss-1=76000 gpuss-2=77100 gpuss-3=74800 gpuss-4=74800 gpuss-5=75200 gpuss-6=75600 gpuss-7=77100 gpuss-8=75600 gpuss-9=77500 gpuss-10=76400 ddr=79100 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- run: {"label": "r2", "status": "paused during Gemma: thermal status 3, GPU capped at power level 8 (578 MHz); Llama 3.2 1B complete under that throttle"}
