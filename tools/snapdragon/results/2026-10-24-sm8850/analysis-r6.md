## Loads

| run | model | processor | load ms | error |
|---|---|---|---|---|
| r6 | Llama-3.2-3B-Instruct-Q4_0 | cpu | 748 |  |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | gpu | 11750 |  |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | npu | 1670 |  |
| r6 | Qwen3-1.7B-Q8_0 | cpu | 1614 |  |
| r6 | Qwen3-1.7B-Q8_0 | gpu | 4401 |  |
| r6 | Qwen3-1.7B-Q8_0 | npu | 1856 |  |
| r6 | Qwen3-4B-Q4_0 | cpu | 2118 |  |
| r6 | Qwen3-4B-Q4_0 | gpu | 6080 |  |
| r6 | Qwen3-4B-Q4_0 | npu | 2158 |  |

## Errors and skipped cells

None.

## Llama-3.2-3B-Instruct-Q4_0 (r6)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 85 / 20.3; first 2419 ms; handoff 0.0 ms; turn 5426 ms; 237.7 s | 106 / 16.9; first 2310 ms; handoff 6.3 ms; turn 5769 ms; 232.6 s | 86 / 19.0; first 2428 ms; handoff 3.9 ms; turn 5551 ms; 243.8 s |
| **gpu** | 167 / 20.5; first 1205 ms; handoff 5.2 ms; turn 3979 ms; 171.0 s | 163 / 12.8; first 1281 ms; handoff 0.0 ms; turn 5477 ms; 232.8 s | 165 / 19.0; first 1279 ms; handoff 5.1 ms; turn 4476 ms; 180.2 s |
| **npu** | 1152 / 20.8; first 188 ms; handoff 3.9 ms; turn 3140 ms; 111.0 s | 1307 / 12.8; first 175 ms; handoff 10.8 ms; turn 4444 ms; 173.2 s | 1202 / 19.0; first 187 ms; handoff 0.0 ms; turn 3379 ms; 119.2 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 4 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 29 / 0 / 4 | 100 / 84 / 84 | 112 / 24.8 |
| cpu:gpu | 7 / 0 / 26 | 78 / 84 / 72 | 135 / 19.8 |
| cpu:npu | 29 / 0 / 4 | 85 / 79 / 89 | 120 / 19.3 |
| gpu:cpu | 26 / 0 / 7 | 93 / 79 / 78 | 300 / 25.5 |
| gpu:gpu | 29 / 0 / 4 | 74 / 75 / 70 | 307 / 20.0 |
| gpu:npu | 26 / 0 / 7 | 80 / 74 / 85 | 309 / 19.3 |
| npu:cpu | 25 / 0 / 8 | 100 / 84 / 83 | 1444 / 25.9 |
| npu:gpu | 28 / 0 / 5 | 76 / 76 / 72 | 1469 / 19.9 |
| npu:npu | 21 / 0 / 12 | 81 / 75 / 85 | 1502 / 19.2 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 114 (5) | 110 (11) | 87 (16) | 99 (1) |
| gpu | 125 (5) | 184 (11) | 163 (16) | 171 (1) |
| npu | 577 (5) | 1192 (11) | 1248 (16) | 1297 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 23.1 (16) | 19.9 (13) | 15.2 (4) | - |
| gpu | 17.6 (16) | 13.4 (13) | 8.1 (4) | - |
| npu | 20.6 (16) | 18.2 (13) | 16.2 (4) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 28 | 5 | 9 |
| cpu:npu | 21 | 12 | 36.0 |
| gpu:cpu | 17 | 16 | 31.0 |
| gpu:gpu | 20 | 13 | 30 |
| gpu:npu | 20 | 13 | 32 |
| npu:cpu | 25 | 8 | 22.0 |
| npu:gpu | 26 | 7 | 12 |
| npu:npu | 22 | 11 | 12 |

## Qwen3-1.7B-Q8_0 (r6)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 143 / 26.4; first 1151 ms; handoff 0.0 ms; turn 3557 ms; 160.3 s | 143 / 16.1; first 1166 ms; handoff 5.4 ms; turn 4494 ms; 212.1 s | 147 / 25.0; first 1176 ms; handoff 3.9 ms; turn 3725 ms; 163.8 s |
| **gpu** | 248 / 26.3; first 739 ms; handoff 4.2 ms; turn 3073 ms; 127.1 s | 240 / 16.5; first 853 ms; handoff 0.0 ms; turn 4183 ms; 174.8 s | 274 / 24.8; first 732 ms; handoff 6.8 ms; turn 3267 ms; 127.8 s |
| **npu** | 1937 / 27.0; first 122 ms; handoff 4.0 ms; turn 2314 ms; 83.8 s | 2010 / 17.1; first 118 ms; handoff 8.3 ms; turn 3469 ms; 128.7 s | 1870 / 21.1; first 128 ms; handoff 0.0 ms; turn 2997 ms; 104.7 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 2 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 27 / 0 / 6 | 98 / 84 / 83 | 229 / 31.1 |
| cpu:gpu | 30 / 0 / 3 | 76 / 78 / 73 | 245 / 25.7 |
| cpu:npu | 25 / 0 / 8 | 83 / 77 / 85 | 251 / 25.8 |
| gpu:cpu | 26 / 0 / 7 | 94 / 81 / 80 | 447 / 31.1 |
| gpu:gpu | 29 / 0 / 4 | 75 / 76 / 71 | 452 / 26.0 |
| gpu:npu | 22 / 0 / 11 | 78 / 74 / 81 | 494 / 25.7 |
| npu:cpu | 23 / 0 / 10 | 98 / 84 / 83 | 1782 / 31.3 |
| npu:gpu | 24 / 0 / 9 | 76 / 77 / 72 | 1819 / 25.4 |
| npu:npu | 25 / 0 / 8 | 75 / 71 / 79 | 1683 / 21.8 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 224 (6) | 198 (10) | 135 (16) | 162 (1) |
| gpu | 202 (6) | 275 (10) | 251 (16) | 258 (1) |
| npu | 740 (6) | 1650 (10) | 2062 (16) | 2354 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 30.2 (16) | 26.0 (12) | 20.0 (5) | - |
| gpu | 20.8 (16) | 16.3 (12) | 10.2 (5) | - |
| npu | 25.3 (16) | 22.7 (12) | 20.5 (5) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 20 | 13 | 38 |
| cpu:npu | 14 | 19 | 28 |
| gpu:cpu | 21 | 12 | 32.5 |
| gpu:gpu | 16 | 17 | 34 |
| gpu:npu | 13 | 20 | 26.0 |
| npu:cpu | 19 | 14 | 34.5 |
| npu:gpu | 17 | 16 | 30.5 |
| npu:npu | 13 | 20 | 27.5 |

## Qwen3-4B-Q4_0 (r6)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 54 / 15.3; first 3368 ms; handoff 0.0 ms; turn 7648 ms; 353.5 s | 53 / 6.2; first 3406 ms; handoff 5.2 ms; turn 9636 ms; 557.9 s | 55 / 15.4; first 3418 ms; handoff 4.9 ms; turn 7763 ms; 349.8 s |
| **gpu** | 102 / 15.0; first 1961 ms; handoff 5.3 ms; turn 5938 ms; 253.7 s | 106 / 6.3; first 1703 ms; handoff 0.0 ms; turn 7119 ms; 443.0 s | 105 / 15.3; first 1655 ms; handoff 5.5 ms; turn 5800 ms; 249.0 s |
| **npu** | 813 / 15.5; first 268 ms; handoff 5.0 ms; turn 4017 ms; 149.5 s | 951 / 6.4; first 244 ms; handoff 9.1 ms; turn 5641 ms; 338.9 s | 801 / 15.4; first 288 ms; handoff 0.0 ms; turn 4192 ms; 149.9 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 0 turns uncapped in every cell that ran (none); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 30 / 0 / 3 | 103 / 87 / 88 | - |
| cpu:gpu | 33 / 0 / 0 | 80 / 80 / 77 | - |
| cpu:npu | 30 / 0 / 3 | 90 / 84 / 95 | - |
| gpu:cpu | 33 / 0 / 0 | 98 / 84 / 84 | - |
| gpu:gpu | 32 / 0 / 1 | 76 / 77 / 72 | - |
| gpu:npu | 33 / 0 / 0 | 86 / 80 / 91 | - |
| npu:cpu | 27 / 0 / 6 | 102 / 85 / 84 | - |
| npu:gpu | 29 / 0 / 4 | 74 / 75 / 70 | - |
| npu:npu | 28 / 0 / 5 | 87 / 81 / 92 | - |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 77 (6) | 67 (10) | 51 (16) | 62 (1) |
| gpu | 95 (6) | 120 (10) | 101 (16) | 119 (1) |
| npu | 462 (6) | 872 (10) | 862 (16) | 910 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 18.2 (16) | 14.8 (12) | 10.6 (5) | - |
| gpu | 12.6 (16) | 7.0 (12) | 2.2 (5) | - |
| npu | 17.1 (16) | 14.7 (12) | 12.6 (5) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 23 | 10 | 31.5 |
| cpu:npu | 23 | 10 | 32.5 |
| gpu:cpu | 22 | 11 | 25 |
| gpu:gpu | 21 | 12 | 30.0 |
| gpu:npu | 21 | 12 | 28.0 |
| npu:cpu | 22 | 11 | 45 |
| npu:gpu | 20 | 13 | 39 |
| npu:npu | 21 | 12 | 41.5 |

## Across models

Prefill and decode rates are each processor's mean over its three cells (all matched turns, throttled or not); the pairing columns are the summed turn time over the matched turns.

| run | model | turns | prefill tok/s CPU / GPU / NPU | decode tok/s CPU / GPU / NPU | fastest pairing | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | slowest pairing |
|---|---|---|---|---|---|---|---|---|---|---|
| r6 | Llama-3.2-3B-Instruct-Q4_0 | 33 | 92 / 165 / 1220 | 20.5 / 14.2 / 19.0 | npu:cpu (111.0 s) | 111.0 s | 119.2 s | 232.8 s | 237.7 s | cpu:npu (243.8 s) |
| r6 | Qwen3-1.7B-Q8_0 | 33 | 144 / 254 / 1939 | 26.6 / 16.6 / 23.6 | npu:cpu (83.8 s) | 83.8 s | 104.7 s | 174.8 s | 160.3 s | cpu:gpu (212.1 s) |
| r6 | Qwen3-4B-Q4_0 | 33 | 54 / 104 / 855 | 15.3 / 6.3 / 15.3 | npu:cpu (149.5 s) | 149.5 s | 149.9 s | 443.0 s | 353.5 s | cpu:gpu (557.9 s) |

## Cooling before each cell

| run | model | cell | gate | waited s | CPU / GPU / NPU °C | GPU cap |
|---|---|---|---|---|---|---|
| r6 | Llama-3.2-3B-Instruct-Q4_0 | cpu:gpu | passed | 5 | 45 / 43 / 44 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | gpu:cpu | passed | 55 | 55 / 54 / 54 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | cpu:cpu | passed | 171 | 55 / 54 / 54 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | npu:gpu | timed_out | 302 | 57 / 55 / 56 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | npu:npu | passed | 262 | 55 / 54 / 54 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | npu:cpu | passed | 241 | 55 / 54 / 55 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | gpu:npu | passed | 297 | 54 / 53 / 54 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | gpu:gpu | passed | 252 | 55 / 54 / 54 | 0 |
| r6 | Llama-3.2-3B-Instruct-Q4_0 | cpu:npu | passed | 241 | 55 / 53 / 54 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | gpu:gpu | timed_out | 302 | 56 / 55 / 55 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | cpu:cpu | passed | 226 | 55 / 54 / 54 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | cpu:gpu | passed | 191 | 55 / 54 / 55 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | npu:npu | passed | 166 | 55 / 54 / 55 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | npu:cpu | passed | 241 | 55 / 54 / 54 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | npu:gpu | passed | 246 | 55 / 54 / 54 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | gpu:npu | passed | 241 | 55 / 54 / 54 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | gpu:cpu | passed | 226 | 55 / 54 / 55 | 0 |
| r6 | Qwen3-1.7B-Q8_0 | cpu:npu | passed | 267 | 55 / 54 / 54 | 0 |
| r6 | Qwen3-4B-Q4_0 | npu:gpu | timed_out | 302 | 55 / 54 / 55 | 0 |
| r6 | Qwen3-4B-Q4_0 | npu:cpu | passed | 297 | 55 / 53 / 54 | 0 |
| r6 | Qwen3-4B-Q4_0 | cpu:cpu | timed_out | 302 | 56 / 55 / 55 | 0 |
| r6 | Qwen3-4B-Q4_0 | cpu:gpu | timed_out | 302 | 60 / 58 / 59 | 8 |
| r6 | Qwen3-4B-Q4_0 | gpu:cpu | timed_out | 302 | 60 / 59 / 60 | 8 |
| r6 | Qwen3-4B-Q4_0 | gpu:npu | timed_out | 302 | 59 / 58 / 58 | 0 |
| r6 | Qwen3-4B-Q4_0 | gpu:gpu | timed_out | 302 | 59 / 57 / 58 | 0 |
| r6 | Qwen3-4B-Q4_0 | npu:npu | timed_out | 302 | 56 / 55 / 56 | 0 |
| r6 | Qwen3-4B-Q4_0 | cpu:npu | timed_out | 302 | 57 / 55 / 56 | 0 |

## Conditions

- env: {"label": "r6", "model": "Llama-3.2-3B-Instruct-Q4_0.gguf", "when": "before", "time": 1792893112, "order": "cpu:gpu,gpu:cpu,cpu:cpu,npu:gpu,npu:npu,npu:cpu,gpu:npu,gpu:gpu,cpu:npu", "temps": "cpullc-0-0=33200 cpullc-0-1=33200 cpu-0-2-1=33600 cpu-0-3-0=32400 cpu-0-3-1=32400 cpu-0-4-0=32800 cpu-0-4-1=33200 cpu-0-5-0=32800 cpu-0-5-1=33200 cpullc-1-0=32000 cpullc-1-1=32400 cpu-1-0-0=32000 cpu-1-0-1=41700 cpu-1-1-0=33200 cpu-1-1-1=33600 nsphvx-0=31500 nsphvx-1=31500 nsphvx-2=31500 nsphvx-3=31500 nsphmx-0=31900 nsphmx-1=31900 nsphmx-2=31900 nsphmx-3=31900 gpuss-0=31800 gpuss-1=31400 gpuss-2=31800 gpuss-3=31800 gpuss-4=32100 gpuss-5=31800 gpuss-6=32500 gpuss-7=32500 gpuss-8=32500 gpuss-9=32100 gpuss-10=32100 ddr=32500 cpu-0-0-0=34300 cpu-hw-trip-0=105000 sys-therm-0=30750 cpu-hw-trip-1=105000 cpu-0-0-1=33900 sys-therm-1=31165 sys-therm-2=29375 sys-therm-3=29625 cpu-0-1-0=33200 sys-therm-4=30263 sys-therm-5=31570 sys-therm-6=30625 sys-therm-7=30901 sys-therm-9=43012 cpu-0-1-1=33900 sys-therm-11=29748 sys-therm-12=37352 sys-therm-8=29175 cpu-0-2-0=33600 battery=39000 ", "memavail_kb": 19477160}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=44000 cpu-0-0-1=43600 cpu-0-1-0=43200 cpu-0-1-1=43200 cpu-0-2-0=44800 cpu-0-2-1=44400 cpu-0-3-0=43600 cpu-0-3-1=43200 cpu-0-4-0=43600 cpu-0-4-1=43600 cpu-0-5-0=43600 cpu-0-5-1=43600 cpu-1-0-0=44800 cpu-1-0-1=44800 cpu-1-1-0=42800 cpu-1-1-1=43600 nsphvx-0=43000 nsphvx-1=43400 nsphvx-2=43000 nsphvx-3=43400 gpuss-0=42600 gpuss-1=42200 gpuss-2=42600 gpuss-3=42200 gpuss-4=42600 gpuss-5=41800 gpuss-6=43400 gpuss-7=43000 gpuss-8=43400 gpuss-9=43000 gpuss-10=42600 ddr=43800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=52900 cpu-0-2-0=54800 cpu-0-2-1=54000 cpu-0-3-0=52900 cpu-0-3-1=52900 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=52900 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=52700 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=52700 gpuss-3=51900 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=56000 cpu-0-0-1=56000 cpu-0-1-0=55600 cpu-0-1-1=55600 cpu-0-2-0=57100 cpu-0-2-1=56700 cpu-0-3-0=56000 cpu-0-3-1=55600 cpu-0-4-0=56300 cpu-0-4-1=56700 cpu-0-5-0=56000 cpu-0-5-1=55600 cpu-1-0-0=56700 cpu-1-0-1=56700 cpu-1-1-0=55600 cpu-1-1-1=56300 nsphvx-0=54900 nsphvx-1=55700 nsphvx-2=55300 nsphvx-3=55700 gpuss-0=55400 gpuss-1=54600 gpuss-2=55400 gpuss-3=54600 gpuss-4=55000 gpuss-5=54300 gpuss-6=55000 gpuss-7=55800 gpuss-8=55000 gpuss-9=55400 gpuss-10=55000 ddr=56600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=52900 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=52900 cpu-0-3-1=52900 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54400 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=53600 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=52700 gpuss-1=52300 gpuss-2=52700 gpuss-3=51900 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=55200 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=51900 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53200 cpu-0-0-1=53200 cpu-0-1-0=52900 cpu-0-1-1=52900 cpu-0-2-0=54000 cpu-0-2-1=54000 cpu-0-3-0=52900 cpu-0-3-1=52900 cpu-0-4-0=53600 cpu-0-4-1=53200 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=53200 cpu-1-1-1=53600 nsphvx-0=53000 nsphvx-1=53400 nsphvx-2=53000 nsphvx-3=53400 gpuss-0=52700 gpuss-1=52300 gpuss-2=52300 gpuss-3=51900 gpuss-4=52300 gpuss-5=51500 gpuss-6=53100 gpuss-7=53100 gpuss-8=53100 gpuss-9=52700 gpuss-10=52700 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53400 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=51900 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=52900 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=52900 cpu-0-4-0=54000 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=51900 gpuss-4=52700 gpuss-5=51900 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53100 gpuss-10=53100 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r6", "model": "gguf/Llama-3.2-3B-Instruct-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=83000 cpu-0-0-1=83000 cpu-0-1-0=82200 cpu-0-1-1=83800 cpu-0-2-0=85300 cpu-0-2-1=84900 cpu-0-3-0=81800 cpu-0-3-1=81800 cpu-0-4-0=84100 cpu-0-4-1=83800 cpu-0-5-0=82200 cpu-0-5-1=82600 cpu-1-0-0=87200 cpu-1-0-1=87600 cpu-1-1-0=83000 cpu-1-1-1=84100 nsphvx-0=86900 nsphvx-1=87600 nsphvx-2=86900 nsphvx-3=87200 gpuss-0=81400 gpuss-1=79900 gpuss-2=81400 gpuss-3=77500 gpuss-4=78700 gpuss-5=78300 gpuss-6=79900 gpuss-7=81400 gpuss-8=80200 gpuss-9=81800 gpuss-10=79900 ddr=83300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r6", "model": "Llama-3.2-3B-Instruct-Q4_0.gguf", "when": "after", "time": 1792896683, "exit": 0, "temps": "cpullc-0-0=83800 cpullc-0-1=85300 cpu-0-2-1=84900 cpu-0-3-0=81100 cpu-0-3-1=80700 cpu-0-4-0=83000 cpu-0-4-1=82200 cpu-0-5-0=80700 cpu-0-5-1=80300 cpullc-1-0=82200 cpullc-1-1=81800 cpu-1-0-0=81400 cpu-1-0-1=81400 cpu-1-1-0=79900 cpu-1-1-1=80700 nsphvx-0=78800 nsphvx-1=79200 nsphvx-2=76900 nsphvx-3=76900 nsphmx-0=77200 nsphmx-1=76900 nsphmx-2=77600 nsphmx-3=77600 gpuss-0=76000 gpuss-1=75200 gpuss-2=76400 gpuss-3=74000 gpuss-4=74800 gpuss-5=74000 gpuss-6=75600 gpuss-7=76000 gpuss-8=75600 gpuss-9=75600 gpuss-10=74800 ddr=76400 cpu-0-0-0=77600 cpu-hw-trip-0=105000 sys-therm-0=66731 cpu-hw-trip-1=105000 cpu-0-0-1=78400 sys-therm-1=71782 sys-therm-2=57621 sys-therm-3=60638 cpu-0-1-0=76000 sys-therm-4=66773 sys-therm-5=75852 sys-therm-6=66044 sys-therm-7=67881 sys-therm-9=58613 cpu-0-1-1=77600 sys-therm-11=48454 sys-therm-12=38614 sys-therm-8=48722 cpu-0-2-0=77200 battery=39000 ", "memavail_kb": 20174548}
- env: {"label": "r6", "model": "Qwen3-1.7B-Q8_0.gguf", "when": "before", "time": 1792896685, "order": "gpu:gpu,cpu:cpu,cpu:gpu,npu:npu,npu:cpu,npu:gpu,gpu:npu,gpu:cpu,cpu:npu", "temps": "cpullc-0-0=77200 cpullc-0-1=77600 cpu-0-2-1=78000 cpu-0-3-0=75300 cpu-0-3-1=75700 cpu-0-4-0=76800 cpu-0-4-1=76000 cpu-0-5-0=75700 cpu-0-5-1=75700 cpullc-1-0=75700 cpullc-1-1=75700 cpu-1-0-0=75700 cpu-1-0-1=75700 cpu-1-1-0=74900 cpu-1-1-1=75700 nsphvx-0=72600 nsphvx-1=72200 nsphvx-2=71900 nsphvx-3=72200 nsphmx-0=72200 nsphmx-1=71900 nsphmx-2=72600 nsphmx-3=72600 gpuss-0=72500 gpuss-1=71700 gpuss-2=72100 gpuss-3=71300 gpuss-4=71700 gpuss-5=70900 gpuss-6=72500 gpuss-7=72500 gpuss-8=72100 gpuss-9=72100 gpuss-10=72100 ddr=72900 cpu-0-0-0=74900 cpu-hw-trip-0=105000 sys-therm-0=66714 cpu-hw-trip-1=105000 cpu-0-0-1=74100 sys-therm-1=70592 sys-therm-2=57645 sys-therm-3=60638 cpu-0-1-0=73300 sys-therm-4=66603 sys-therm-5=73656 sys-therm-6=66022 sys-therm-7=67809 sys-therm-9=58601 cpu-0-1-1=72900 sys-therm-11=48504 sys-therm-12=38590 sys-therm-8=48764 cpu-0-2-0=73700 battery=39000 ", "memavail_kb": 20172468}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54800 cpu-0-0-1=54800 cpu-0-1-0=54400 cpu-0-1-1=54400 cpu-0-2-0=55600 cpu-0-2-1=55600 cpu-0-3-0=54400 cpu-0-3-1=54400 cpu-0-4-0=55200 cpu-0-4-1=55600 cpu-0-5-0=54800 cpu-0-5-1=54800 cpu-1-0-0=56000 cpu-1-0-1=56000 cpu-1-1-0=54400 cpu-1-1-1=55200 nsphvx-0=54900 nsphvx-1=55300 nsphvx-2=54600 nsphvx-3=55300 gpuss-0=54300 gpuss-1=53500 gpuss-2=54300 gpuss-3=53500 gpuss-4=53900 gpuss-5=53100 gpuss-6=54300 gpuss-7=54600 gpuss-8=54300 gpuss-9=54300 gpuss-10=54300 ddr=55400 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=52900 cpu-0-1-1=52900 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=51900 gpuss-4=52700 gpuss-5=51900 gpuss-6=53500 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=53200 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=52900 cpu-1-1-1=53200 nsphvx-0=54200 nsphvx-1=54600 nsphvx-2=54200 nsphvx-3=54600 gpuss-0=53900 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=53500 gpuss-5=52700 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53100 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=53600 nsphvx-0=54600 nsphvx-1=54600 nsphvx-2=54600 nsphvx-3=54600 gpuss-0=53900 gpuss-1=53100 gpuss-2=53900 gpuss-3=52700 gpuss-4=53900 gpuss-5=53100 gpuss-6=53500 gpuss-7=54300 gpuss-8=53900 gpuss-9=53900 gpuss-10=53500 ddr=54600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=54000 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54600 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53500 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53500 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=53400 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=52700 gpuss-3=51900 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=54200 nsphvx-2=53000 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=54000 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=55200 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=53800 gpuss-0=53500 gpuss-1=52300 gpuss-2=53500 gpuss-3=52300 gpuss-4=53100 gpuss-5=51900 gpuss-6=53500 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=52700 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=52700 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r6", "model": "gguf/Qwen3-1.7B-Q8_0.gguf", "failures": 0, "temps": "cpu-0-0-0=80700 cpu-0-0-1=81100 cpu-0-1-0=79500 cpu-0-1-1=79500 cpu-0-2-0=83400 cpu-0-2-1=83400 cpu-0-3-0=81400 cpu-0-3-1=82600 cpu-0-4-0=82200 cpu-0-4-1=81800 cpu-0-5-0=81400 cpu-0-5-1=80700 cpu-1-0-0=85300 cpu-1-0-1=84900 cpu-1-1-0=80700 cpu-1-1-1=81800 nsphvx-0=83400 nsphvx-1=83400 nsphvx-2=83000 nsphvx-3=83000 gpuss-0=79500 gpuss-1=77900 gpuss-2=79500 gpuss-3=76000 gpuss-4=77100 gpuss-5=76800 gpuss-6=78300 gpuss-7=79900 gpuss-8=79100 gpuss-9=80600 gpuss-10=79100 ddr=81800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r6", "model": "Qwen3-1.7B-Q8_0.gguf", "when": "after", "time": 1792900122, "exit": 0, "temps": "cpullc-0-0=81800 cpullc-0-1=82600 cpu-0-2-1=82200 cpu-0-3-0=78700 cpu-0-3-1=79100 cpu-0-4-0=81100 cpu-0-4-1=81400 cpu-0-5-0=79500 cpu-0-5-1=78700 cpullc-1-0=80300 cpullc-1-1=79900 cpu-1-0-0=79500 cpu-1-0-1=79500 cpu-1-1-0=78400 cpu-1-1-1=79100 nsphvx-0=76500 nsphvx-1=76500 nsphvx-2=74600 nsphvx-3=74600 nsphmx-0=74900 nsphmx-1=74900 nsphmx-2=75700 nsphmx-3=75700 gpuss-0=74000 gpuss-1=73600 gpuss-2=74400 gpuss-3=72900 gpuss-4=73300 gpuss-5=72500 gpuss-6=74000 gpuss-7=74000 gpuss-8=73600 gpuss-9=73600 gpuss-10=73300 ddr=74800 cpu-0-0-0=76000 cpu-hw-trip-0=105000 sys-therm-0=64817 cpu-hw-trip-1=105000 cpu-0-0-1=76400 sys-therm-1=69980 sys-therm-2=55809 sys-therm-3=58794 cpu-0-1-0=74900 sys-therm-4=64888 sys-therm-5=74125 sys-therm-6=63921 sys-therm-7=65806 sys-therm-9=57028 cpu-0-1-1=74500 sys-therm-11=46888 sys-therm-12=38535 sys-therm-8=47103 cpu-0-2-0=75300 battery=39000 ", "memavail_kb": 20247772}
- env: {"label": "r6", "model": "Qwen3-4B-Q4_0.gguf", "when": "before", "time": 1792900124, "order": "npu:gpu,npu:cpu,cpu:cpu,cpu:gpu,gpu:cpu,gpu:npu,gpu:gpu,npu:npu,cpu:npu", "temps": "cpullc-0-0=75700 cpullc-0-1=75700 cpu-0-2-1=75300 cpu-0-3-0=73700 cpu-0-3-1=73700 cpu-0-4-0=74900 cpu-0-4-1=74500 cpu-0-5-0=74500 cpu-0-5-1=74100 cpullc-1-0=74100 cpullc-1-1=73700 cpu-1-0-0=73300 cpu-1-0-1=73700 cpu-1-1-0=73700 cpu-1-1-1=74100 nsphvx-0=70700 nsphvx-1=70700 nsphvx-2=69600 nsphvx-3=69900 nsphmx-0=70300 nsphmx-1=70300 nsphmx-2=70700 nsphmx-3=70700 gpuss-0=70200 gpuss-1=70200 gpuss-2=70500 gpuss-3=69400 gpuss-4=70200 gpuss-5=69400 gpuss-6=70900 gpuss-7=70500 gpuss-8=70500 gpuss-9=70200 gpuss-10=70200 ddr=71300 cpu-0-0-0=72900 cpu-hw-trip-0=105000 sys-therm-0=64802 cpu-hw-trip-1=105000 cpu-0-0-1=72600 sys-therm-1=68715 sys-therm-2=55814 sys-therm-3=58794 cpu-0-1-0=71400 sys-therm-4=64774 sys-therm-5=71832 sys-therm-6=63952 sys-therm-7=65781 sys-therm-9=57160 cpu-0-1-1=71800 sys-therm-11=46918 sys-therm-12=38529 sys-therm-8=47131 cpu-0-2-0=71800 battery=39000 ", "memavail_kb": 20262816}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54400 cpu-0-1-0=53600 cpu-0-1-1=53600 cpu-0-2-0=55200 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54400 cpu-0-4-1=54800 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=55200 cpu-1-0-1=55200 cpu-1-1-0=54000 cpu-1-1-1=54400 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=54200 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=52700 gpuss-4=53100 gpuss-5=52300 gpuss-6=53900 gpuss-7=53900 gpuss-8=53500 gpuss-9=53100 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53600 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=53600 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=55600 cpu-0-0-1=54800 cpu-0-1-0=54800 cpu-0-1-1=55200 cpu-0-2-0=56000 cpu-0-2-1=56000 cpu-0-3-0=54400 cpu-0-3-1=54800 cpu-0-4-0=55200 cpu-0-4-1=55200 cpu-0-5-0=54800 cpu-0-5-1=54800 cpu-1-0-0=56000 cpu-1-0-1=56000 cpu-1-1-0=54800 cpu-1-1-1=55600 nsphvx-0=54900 nsphvx-1=55300 nsphvx-2=54900 nsphvx-3=54900 gpuss-0=54300 gpuss-1=53900 gpuss-2=54600 gpuss-3=53500 gpuss-4=54300 gpuss-5=53900 gpuss-6=54600 gpuss-7=54600 gpuss-8=54600 gpuss-9=54300 gpuss-10=54300 ddr=55800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=58700 cpu-0-0-1=58700 cpu-0-1-0=57900 cpu-0-1-1=57900 cpu-0-2-0=59400 cpu-0-2-1=59400 cpu-0-3-0=58300 cpu-0-3-1=58300 cpu-0-4-0=59000 cpu-0-4-1=59000 cpu-0-5-0=58300 cpu-0-5-1=58300 cpu-1-0-0=59800 cpu-1-0-1=59800 cpu-1-1-0=58300 cpu-1-1-1=59000 nsphvx-0=58400 nsphvx-1=58800 nsphvx-2=58400 nsphvx-3=58400 gpuss-0=57400 gpuss-1=57000 gpuss-2=57700 gpuss-3=56600 gpuss-4=57400 gpuss-5=56600 gpuss-6=57700 gpuss-7=58500 gpuss-8=57700 gpuss-9=57700 gpuss-10=57400 ddr=58500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=59400 cpu-0-0-1=59000 cpu-0-1-0=58300 cpu-0-1-1=58700 cpu-0-2-0=60200 cpu-0-2-1=60600 cpu-0-3-0=58700 cpu-0-3-1=58700 cpu-0-4-0=59400 cpu-0-4-1=59400 cpu-0-5-0=58700 cpu-0-5-1=58700 cpu-1-0-0=60200 cpu-1-0-1=59800 cpu-1-1-0=59000 cpu-1-1-1=59000 nsphvx-0=58000 nsphvx-1=58800 nsphvx-2=58000 nsphvx-3=58800 gpuss-0=58100 gpuss-1=57400 gpuss-2=58100 gpuss-3=57400 gpuss-4=58100 gpuss-5=57400 gpuss-6=58100 gpuss-7=58900 gpuss-8=58500 gpuss-9=58100 gpuss-10=58100 ddr=59300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=57900 cpu-0-0-1=57900 cpu-0-1-0=57500 cpu-0-1-1=57900 cpu-0-2-0=59000 cpu-0-2-1=58700 cpu-0-3-0=57500 cpu-0-3-1=57100 cpu-0-4-0=58300 cpu-0-4-1=58300 cpu-0-5-0=57900 cpu-0-5-1=57500 cpu-1-0-0=59000 cpu-1-0-1=59000 cpu-1-1-0=57500 cpu-1-1-1=57900 nsphvx-0=57600 nsphvx-1=58000 nsphvx-2=57300 nsphvx-3=58000 gpuss-0=56600 gpuss-1=56200 gpuss-2=57000 gpuss-3=55800 gpuss-4=56600 gpuss-5=55800 gpuss-6=57400 gpuss-7=57700 gpuss-8=57400 gpuss-9=57400 gpuss-10=57000 ddr=58100 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=57500 cpu-0-0-1=57500 cpu-0-1-0=57100 cpu-0-1-1=57100 cpu-0-2-0=58700 cpu-0-2-1=58700 cpu-0-3-0=57100 cpu-0-3-1=57100 cpu-0-4-0=57900 cpu-0-4-1=58300 cpu-0-5-0=57500 cpu-0-5-1=57500 cpu-1-0-0=58700 cpu-1-0-1=58700 cpu-1-1-0=57100 cpu-1-1-1=57900 nsphvx-0=57300 nsphvx-1=57300 nsphvx-2=56900 nsphvx-3=57300 gpuss-0=56600 gpuss-1=56200 gpuss-2=56600 gpuss-3=56200 gpuss-4=56600 gpuss-5=55800 gpuss-6=57000 gpuss-7=57000 gpuss-8=57000 gpuss-9=57000 gpuss-10=56200 ddr=57700 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=55200 cpu-0-0-1=55200 cpu-0-1-0=54400 cpu-0-1-1=54800 cpu-0-2-0=56000 cpu-0-2-1=56000 cpu-0-3-0=54800 cpu-0-3-1=54400 cpu-0-4-0=55600 cpu-0-4-1=55600 cpu-0-5-0=54800 cpu-0-5-1=54800 cpu-1-0-0=56300 cpu-1-0-1=56300 cpu-1-1-0=54800 cpu-1-1-1=55600 nsphvx-0=54900 nsphvx-1=54900 nsphvx-2=54900 nsphvx-3=55300 gpuss-0=54600 gpuss-1=53900 gpuss-2=54600 gpuss-3=53900 gpuss-4=53900 gpuss-5=53100 gpuss-6=54300 gpuss-7=54600 gpuss-8=54600 gpuss-9=54300 gpuss-10=54300 ddr=55000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=56000 cpu-0-0-1=55600 cpu-0-1-0=54800 cpu-0-1-1=54800 cpu-0-2-0=56300 cpu-0-2-1=56300 cpu-0-3-0=55200 cpu-0-3-1=54800 cpu-0-4-0=56000 cpu-0-4-1=56300 cpu-0-5-0=55200 cpu-0-5-1=55200 cpu-1-0-0=56700 cpu-1-0-1=56700 cpu-1-1-0=55200 cpu-1-1-1=55600 nsphvx-0=55300 nsphvx-1=55700 nsphvx-2=54900 nsphvx-3=55300 gpuss-0=54600 gpuss-1=53900 gpuss-2=54600 gpuss-3=53900 gpuss-4=54300 gpuss-5=53500 gpuss-6=54600 gpuss-7=55400 gpuss-8=54600 gpuss-9=54600 gpuss-10=54600 ddr=55800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r6", "model": "gguf/Qwen3-4B-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=88000 cpu-0-0-1=88000 cpu-0-1-0=86500 cpu-0-1-1=86900 cpu-0-2-0=90700 cpu-0-2-1=90300 cpu-0-3-0=88400 cpu-0-3-1=89900 cpu-0-4-0=89600 cpu-0-4-1=89200 cpu-0-5-0=88800 cpu-0-5-1=88000 cpu-1-0-0=92300 cpu-1-0-1=92600 cpu-1-1-0=87600 cpu-1-1-1=89200 nsphvx-0=90700 nsphvx-1=91500 nsphvx-2=90700 nsphvx-3=91100 gpuss-0=86100 gpuss-1=84500 gpuss-2=86100 gpuss-3=82200 gpuss-4=83300 gpuss-5=83000 gpuss-6=84500 gpuss-7=86800 gpuss-8=85300 gpuss-9=86800 gpuss-10=84900 ddr=88400 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r6", "model": "Qwen3-4B-Q4_0.gguf", "when": "after", "time": 1792905746, "exit": 0, "temps": "cpullc-0-0=89600 cpullc-0-1=89900 cpu-0-2-1=89600 cpu-0-3-0=85700 cpu-0-3-1=85700 cpu-0-4-0=88000 cpu-0-4-1=87600 cpu-0-5-0=85300 cpu-0-5-1=84900 cpullc-1-0=86900 cpullc-1-1=86900 cpu-1-0-0=86100 cpu-1-0-1=86100 cpu-1-1-0=85300 cpu-1-1-1=86100 nsphvx-0=82600 nsphvx-1=82600 nsphvx-2=82200 nsphvx-3=80700 nsphmx-0=81100 nsphmx-1=81100 nsphmx-2=81500 nsphmx-3=81500 gpuss-0=80200 gpuss-1=79500 gpuss-2=80600 gpuss-3=78300 gpuss-4=79500 gpuss-5=78700 gpuss-6=80200 gpuss-7=80200 gpuss-8=79500 gpuss-9=79900 gpuss-10=79500 ddr=81000 cpu-0-0-0=83400 cpu-hw-trip-0=105000 sys-therm-0=70691 cpu-hw-trip-1=105000 cpu-0-0-1=82200 sys-therm-1=76079 sys-therm-2=61126 sys-therm-3=64456 cpu-0-1-0=81100 sys-therm-4=70830 sys-therm-5=80396 sys-therm-6=69884 sys-therm-7=71817 sys-therm-9=61326 cpu-0-1-1=80300 sys-therm-11=51744 sys-therm-12=38814 sys-therm-8=52116 cpu-0-2-0=81100 battery=39000 ", "memavail_kb": 20549236}
- run: {"label": "r6", "seed": "1", "bin": "pd_bench5", "gen": 64, "ctx": 8192, "threads": 6, "turns": 6, "cool": 55, "hexagon_env": "", "status": 0}
