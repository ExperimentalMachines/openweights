## Loads

| run | model | processor | load ms | error |
|---|---|---|---|---|
| r1 | Llama-3.2-1B-Instruct-Q4_0 | cpu | 462 |  |
| r1 | Llama-3.2-1B-Instruct-Q4_0 | gpu | 2891 |  |
| r1 | Llama-3.2-1B-Instruct-Q4_0 | npu | 466 |  |
| r1 | google_gemma-3-1b-it-Q4_0 | cpu | 649 |  |
| r1 | google_gemma-3-1b-it-Q4_0 | gpu | 1505 |  |
| r1 | google_gemma-3-1b-it-Q4_0 | npu | 740 |  |

## Errors and skipped cells

None.

## Llama-3.2-1B-Instruct-Q4_0 (r1)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 210 / 41.5; first 892 ms; handoff 0.0 ms; turn 2437 ms; 104.9 s | 300 / 43.7; first 575 ms; handoff 1.6 ms; turn 2059 ms; 86.0 s | 340 / 38.3; first 512 ms; handoff 1.2 ms; turn 2184 ms; 88.2 s |
| **gpu** | 397 / 41.1; first 474 ms; handoff 2.2 ms; turn 2094 ms; 79.7 s | 400 / 28.4; first 518 ms; handoff 0.0 ms; turn 2632 ms; 101.9 s | 618 / 38.3; first 407 ms; handoff 3.5 ms; turn 2007 ms; 73.1 s |
| **npu** | 3230 / 40.9; first 73 ms; handoff 1.3 ms; turn 1540 ms; 54.5 s | 2975 / 43.8; first 81 ms; handoff 2.1 ms; turn 1421 ms; 51.5 s | 3126 / 38.4; first 75 ms; handoff 0.0 ms; turn 1613 ms; 57.8 s |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 349 (5) | 348 (11) | 256 (16) | 314 (1) |
| gpu | 311 (5) | 473 (11) | 443 (16) | 552 (1) |
| npu | 1206 (5) | 2694 (11) | 3263 (16) | 3755 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 47.7 (16) | 38.3 (13) | 31.4 (4) | - |
| gpu | 41.9 (16) | 35.4 (13) | 28.2 (4) | - |
| npu | 41.7 (16) | 37.1 (13) | 31.4 (4) | - |

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

## google_gemma-3-1b-it-Q4_0 (r1)

**Incomplete: 3 of nine cells ran.** 10 turns in 3 conversations matched across the 3 cells that ran. Cells with no successful turn: cpu:cpu, cpu:gpu, cpu:npu, gpu:cpu, npu:cpu, npu:gpu.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | did not run | did not run | did not run |
| **gpu** | did not run | 439 / 26.4; first 449 ms; handoff 0.0 ms; turn 2837 ms; 30.4 s | 414 / 47.8; first 505 ms; handoff 4.5 ms; turn 1817 ms; 20.2 s |
| **npu** | did not run | did not run | 3656 / 47.1; first 66 ms; handoff 0.0 ms; turn 1411 ms; 14.2 s |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | - | - | - | - |
| gpu | 164 (1) | 375 (4) | 443 (5) | - |
| npu | 768 (1) | 2701 (4) | 4040 (5) | - |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | - | - | - | - |
| gpu | 29.1 (5) | 23.8 (3) | 24.5 (2) | - |
| npu | 48.0 (5) | 47.3 (3) | 46.5 (2) | - |

No cpu:cpu cell among the cells that ran, so no agreement table.

## Across models

Prefill and decode rates are each processor's mean over its three cells (all matched turns, throttled or not); the pairing columns are the summed turn time over the matched turns.

| run | model | turns | prefill tok/s CPU / GPU / NPU | decode tok/s CPU / GPU / NPU | fastest pairing | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | slowest pairing |
|---|---|---|---|---|---|---|---|---|---|---|
| r1 | Llama-3.2-1B-Instruct-Q4_0 | 33 | 284 / 472 / 3110 | 41.2 / 38.6 / 38.3 | npu:gpu (51.5 s) | 54.5 s | 57.8 s | 101.9 s | 104.9 s | cpu:cpu (104.9 s) |
| r1 | google_gemma-3-1b-it-Q4_0 (incomplete) | 10 | nan / 426 / 3656 | nan / 26.4 / 47.5 | npu:npu (14.2 s) | did not run | 14.2 s | 30.4 s | did not run | gpu:gpu (30.4 s) |

## Conditions

- env: {"label": "r1", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "before", "time": 1792869386, "order": "cpu:npu,cpu:gpu,npu:gpu,gpu:npu,npu:npu,npu:cpu,gpu:gpu,gpu:cpu,cpu:cpu", "temps": "cpullc-0-0=37200 cpullc-0-1=37200 cpu-0-2-1=36000 cpu-0-3-0=36800 cpu-0-3-1=36400 cpu-0-4-0=36800 cpu-0-4-1=36800 cpu-0-5-0=36800 cpu-0-5-1=38000 cpullc-1-0=35200 cpullc-1-1=36000 cpu-1-0-0=35600 cpu-1-0-1=36000 cpu-1-1-0=36000 cpu-1-1-1=36000 nsphvx-0=36300 nsphvx-1=35900 nsphvx-2=35900 nsphvx-3=36300 nsphmx-0=35500 nsphmx-1=36300 nsphmx-2=35900 nsphmx-3=36300 gpuss-0=36000 gpuss-1=36000 gpuss-2=36000 gpuss-3=35600 gpuss-4=35600 gpuss-5=35600 gpuss-6=35600 gpuss-7=35200 gpuss-8=35600 gpuss-9=36000 gpuss-10=36000 ddr=36000 cpu-0-0-0=37200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=34919 cpu-0-0-1=37600 sys-therm-1=35533 sys-therm-2=31717 sys-therm-3=32626 sys-therm-4=34565 cpu-0-1-0=36000 sys-therm-5=35942 sys-therm-6=34262 sys-therm-7=34965 sys-therm-9=43266 cpu-0-1-1=36400 sys-therm-11=29507 sys-therm-12=34914 sys-therm-8=29354 battery=39000 cpu-0-2-0=36400 ", "memavail_kb": 19357080}
- env: {"label": "r1", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "after", "time": 1792870095, "exit": 0, "temps": "cpullc-0-0=81400 cpullc-0-1=81400 cpu-0-2-1=81400 cpu-0-3-0=79100 cpu-0-3-1=79100 cpu-0-4-0=80600 cpu-0-4-1=79900 cpu-0-5-0=78300 cpu-0-5-1=78700 cpullc-1-0=77900 cpullc-1-1=77900 cpu-1-0-0=76000 cpu-1-0-1=76400 cpu-1-1-0=76400 cpu-1-1-1=76800 nsphvx-0=72200 nsphvx-1=71000 nsphvx-2=71400 nsphvx-3=71800 nsphmx-0=71800 nsphmx-1=72600 nsphmx-2=72600 nsphmx-3=73700 gpuss-0=72100 gpuss-1=71700 gpuss-2=72100 gpuss-3=70900 gpuss-4=70500 gpuss-5=71300 gpuss-6=72500 gpuss-7=72500 gpuss-8=71700 gpuss-9=72500 gpuss-10=72100 ddr=73300 cpu-0-0-0=76800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=63081 cpu-0-0-1=75200 sys-therm-1=68332 sys-therm-2=54944 sys-therm-3=58588 sys-therm-4=64969 cpu-0-1-0=74000 sys-therm-5=72818 sys-therm-6=61626 sys-therm-7=63440 sys-therm-9=57041 cpu-0-1-1=73300 sys-therm-11=46839 sys-therm-12=36198 sys-therm-8=47676 battery=39000 cpu-0-2-0=72900 ", "memavail_kb": 19549580}
- env: {"label": "r1", "model": "google_gemma-3-1b-it-Q4_0.gguf", "when": "before", "time": 1792870096, "order": "npu:npu,gpu:npu,gpu:gpu,cpu:cpu,npu:gpu,cpu:npu,cpu:gpu,gpu:cpu,npu:cpu", "temps": "cpullc-0-0=73600 cpullc-0-1=73300 cpu-0-2-1=73300 cpu-0-3-0=71700 cpu-0-3-1=72100 cpu-0-4-0=72900 cpu-0-4-1=72900 cpu-0-5-0=72100 cpu-0-5-1=72100 cpullc-1-0=70900 cpullc-1-1=71300 cpu-1-0-0=70200 cpu-1-0-1=70500 cpu-1-1-0=70500 cpu-1-1-1=71300 nsphvx-0=68300 nsphvx-1=67900 nsphvx-2=67900 nsphvx-3=68700 nsphmx-0=67900 nsphmx-1=68300 nsphmx-2=68300 nsphmx-3=68700 gpuss-0=68200 gpuss-1=68200 gpuss-2=68200 gpuss-3=67800 gpuss-4=67100 gpuss-5=67800 gpuss-6=69000 gpuss-7=68600 gpuss-8=68600 gpuss-9=69000 gpuss-10=69000 ddr=69400 cpu-0-0-0=71300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=63061 cpu-0-0-1=71700 sys-therm-1=67056 sys-therm-2=54982 sys-therm-3=58673 sys-therm-4=64793 cpu-0-1-0=70900 sys-therm-5=70063 sys-therm-6=61670 sys-therm-7=63428 sys-therm-9=57071 cpu-0-1-1=70900 sys-therm-11=46886 sys-therm-12=36212 sys-therm-8=47715 battery=39000 cpu-0-2-0=69800 ", "memavail_kb": 19540952}
- run: {"label": "r1", "status": "stopped during the second model: superseded by the fixed tool (r2)"}
