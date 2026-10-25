## Loads

| run | model | processor | load ms | error |
|---|---|---|---|---|
| r5 | Llama-3.2-1B-Instruct-Q4_0 | cpu | 811 |  |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | gpu | 1880 |  |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | npu | 547 |  |
| r5 | Qwen3-0.6B-Q4_0 | cpu | 610 |  |
| r5 | Qwen3-0.6B-Q4_0 | gpu | 1379 |  |
| r5 | Qwen3-0.6B-Q4_0 | npu | 710 |  |
| r5 | Qwen3-1.7B-Q4_0 | cpu | 1399 |  |
| r5 | Qwen3-1.7B-Q4_0 | gpu | 3265 |  |
| r5 | Qwen3-1.7B-Q4_0 | npu | 1199 |  |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu | 695 |  |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu | 2137 |  |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu | 602 |  |
| r5 | Qwen3-4B-Q4_0 | cpu | 2306 |  |
| r5 | Qwen3-4B-Q4_0 | gpu | 7100 |  |
| r5 | Qwen3-4B-Q4_0 | npu | 2240 |  |

## Errors and skipped cells

None.

## LFM2.5-1.2B-Instruct-QAD-Q4_0 (r5)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 303 / 59.4; first 790 ms; handoff 0.0 ms; turn 1809 ms; 75.2 s | 323 / 44.9; first 770 ms; handoff 0.6 ms; turn 2091 ms; 84.2 s | 334 / 42.0; first 764 ms; handoff 0.6 ms; turn 2250 ms; 86.2 s |
| **gpu** | 666 / 63.4; first 325 ms; handoff 2.1 ms; turn 1317 ms; 51.3 s | 599 / 44.8; first 401 ms; handoff 0.0 ms; turn 1816 ms; 66.7 s | 716 / 41.6; first 371 ms; handoff 2.3 ms; turn 1896 ms; 67.3 s |
| **npu** | 3305 / 70.1; first 78 ms; handoff 0.8 ms; turn 964 ms; 33.5 s | 3187 / 47.9; first 82 ms; handoff 1.3 ms; turn 1406 ms; 47.5 s | 3377 / 41.6; first 76 ms; handoff 0.0 ms; turn 1569 ms; 53.6 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 11 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 22 / 0 / 11 | 93 / 79 / 78 | 377 / 73.2 |
| cpu:gpu | 21 / 0 / 12 | 76 / 77 / 72 | 424 / 57.7 |
| cpu:npu | 19 / 0 / 14 | 78 / 72 / 81 | 436 / 42.0 |
| gpu:cpu | 18 / 0 / 15 | 104 / 80 / 79 | 961 / 72.5 |
| gpu:gpu | 21 / 0 / 12 | 74 / 74 / 70 | 955 / 57.7 |
| gpu:npu | 14 / 0 / 19 | 74 / 70 / 78 | 950 / 41.9 |
| npu:cpu | 9 / 0 / 24 | 105 / 83 / 81 | 3425 / 73.1 |
| npu:gpu | 18 / 0 / 15 | 74 / 78 / 70 | 3145 / 57.3 |
| npu:npu | 4 / 0 / 29 | 72 / 67 / 76 | 3416 / 41.9 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 294 (5) | 362 (10) | 321 (16) | 294 (2) |
| gpu | 348 (5) | 692 (10) | 692 (16) | 580 (2) |
| npu | 1316 (5) | 2614 (10) | 3480 (16) | 3674 (2) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 67.4 (15) | 62.5 (12) | 59.3 (6) | - |
| gpu | 47.6 (15) | 45.3 (12) | 43.0 (6) | - |
| npu | 43.2 (15) | 41.3 (12) | 39.1 (6) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 21 | 12 | 40.0 |
| cpu:npu | 15 | 18 | 28.0 |
| gpu:cpu | 19 | 14 | 32.5 |
| gpu:gpu | 18 | 15 | 38 |
| gpu:npu | 16 | 17 | 25 |
| npu:cpu | 19 | 14 | 31.5 |
| npu:gpu | 17 | 16 | 35.0 |
| npu:npu | 14 | 19 | 32 |

## Llama-3.2-1B-Instruct-Q4_0 (r5)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 235 / 46.3; first 833 ms; handoff 0.0 ms; turn 2163 ms; 93.8 s | 242 / 32.9; first 846 ms; handoff 2.3 ms; turn 2498 ms; 111.3 s | 260 / 38.8; first 861 ms; handoff 1.2 ms; turn 2416 ms; 98.0 s |
| **gpu** | 502 / 46.3; first 428 ms; handoff 2.2 ms; turn 1727 ms; 68.2 s | 537 / 34.4; first 425 ms; handoff 0.0 ms; turn 2254 ms; 81.8 s | 565 / 38.3; first 416 ms; handoff 3.7 ms; turn 2018 ms; 75.2 s |
| **npu** | 2935 / 52.1; first 82 ms; handoff 1.4 ms; turn 1311 ms; 44.1 s | 2888 / 34.5; first 88 ms; handoff 2.3 ms; turn 1919 ms; 64.7 s | 3025 / 38.1; first 82 ms; handoff 0.0 ms; turn 1617 ms; 58.4 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 11 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 21 / 0 / 12 | 95 / 79 / 78 | 298 / 55.9 |
| cpu:gpu | 21 / 0 / 12 | 74 / 75 / 71 | 326 / 44.3 |
| cpu:npu | 15 / 0 / 18 | 78 / 73 / 80 | 333 / 39.7 |
| gpu:cpu | 22 / 0 / 11 | 93 / 77 / 76 | 799 / 55.6 |
| gpu:gpu | 20 / 0 / 13 | 73 / 74 / 68 | 836 / 44.3 |
| gpu:npu | 16 / 0 / 17 | 75 / 70 / 77 | 846 / 38.6 |
| npu:cpu | 13 / 0 / 20 | 104 / 82 / 81 | 2984 / 57.4 |
| npu:gpu | 19 / 0 / 14 | 70 / 73 / 67 | 2840 / 44.0 |
| npu:npu | 3 / 0 / 30 | 72 / 67 / 76 | 2981 / 38.4 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 270 (5) | 295 (11) | 235 (16) | 268 (1) |
| gpu | 326 (5) | 561 (11) | 539 (16) | 522 (1) |
| npu | 1121 (5) | 2438 (11) | 3130 (16) | 3618 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 53.6 (16) | 45.6 (13) | 38.8 (4) | - |
| gpu | 37.0 (16) | 33.4 (13) | 26.5 (4) | - |
| npu | 41.6 (16) | 37.4 (13) | 31.3 (4) | - |

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

## Qwen3-0.6B-Q4_0 (r5)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 236 / 57.9; first 700 ms; handoff 0.0 ms; turn 1489 ms; 85.4 s | 234 / 29.5; first 598 ms; handoff 5.5 ms; turn 2385 ms; 121.8 s | 248 / 47.6; first 646 ms; handoff 3.8 ms; turn 1914 ms; 91.6 s |
| **gpu** | 605 / 61.5; first 321 ms; handoff 4.3 ms; turn 1225 ms; 54.2 s | 560 / 31.4; first 311 ms; handoff 0.0 ms; turn 1902 ms; 87.2 s | 753 / 47.0; first 253 ms; handoff 7.6 ms; turn 1616 ms; 61.1 s |
| **npu** | 2944 / 65.7; first 84 ms; handoff 3.9 ms; turn 882 ms; 36.5 s | 2933 / 33.7; first 84 ms; handoff 10.0 ms; turn 1621 ms; 66.9 s | 2948 / 47.3; first 82 ms; handoff 0.0 ms; turn 1326 ms; 47.9 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 12 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 20 / 0 / 13 | 95 / 78 / 77 | 320 / 73.1 |
| cpu:gpu | 21 / 0 / 12 | 72 / 72 / 69 | 342 / 38.6 |
| cpu:npu | 19 / 0 / 14 | 76 / 72 / 77 | 347 / 48.3 |
| gpu:cpu | 16 / 0 / 17 | 103 / 79 / 77 | 891 / 72.6 |
| gpu:gpu | 19 / 0 / 14 | 69 / 71 / 66 | 883 / 38.7 |
| gpu:npu | 7 / 0 / 26 | 71 / 67 / 72 | 884 / 47.0 |
| npu:cpu | 11 / 0 / 22 | 104 / 82 / 81 | 2948 / 71.4 |
| npu:gpu | 11 / 0 / 22 | 70 / 73 / 66 | 2928 / 38.3 |
| npu:npu | 0 / 0 / 33 | 68 / 64 / 70 | 2957 / 47.2 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 474 (6) | 391 (10) | 218 (16) | 268 (1) |
| gpu | 490 (6) | 825 (10) | 600 (16) | 729 (1) |
| npu | 1171 (6) | 2479 (10) | 3106 (16) | 3815 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 89.7 (16) | 56.8 (12) | 34.0 (5) | - |
| gpu | 45.6 (16) | 29.5 (12) | 17.1 (5) | - |
| npu | 55.0 (16) | 44.2 (12) | 37.0 (5) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 11 | 22 | 36.5 |
| cpu:npu | 12 | 21 | 26 |
| gpu:cpu | 9 | 24 | 33.5 |
| gpu:gpu | 8 | 25 | 31 |
| gpu:npu | 4 | 29 | 34 |
| npu:cpu | 13 | 20 | 32.0 |
| npu:gpu | 11 | 22 | 40.5 |
| npu:npu | 11 | 22 | 36.5 |

## Qwen3-1.7B-Q4_0 (r5)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 136 / 31.6; first 1274 ms; handoff 0.0 ms; turn 3135 ms; 151.6 s | 139 / 18.7; first 1262 ms; handoff 6.5 ms; turn 4095 ms; 197.0 s | 142 / 28.8; first 1280 ms; handoff 3.8 ms; turn 3435 ms; 155.4 s |
| **gpu** | 290 / 32.7; first 652 ms; handoff 4.9 ms; turn 2517 ms; 104.9 s | 286 / 19.1; first 642 ms; handoff 0.0 ms; turn 3649 ms; 149.9 s | 310 / 29.1; first 651 ms; handoff 6.9 ms; turn 2786 ms; 110.4 s |
| **npu** | 2016 / 33.6; first 120 ms; handoff 3.8 ms; turn 1933 ms; 68.5 s | 1993 / 18.6; first 126 ms; handoff 10.2 ms; turn 3159 ms; 118.9 s | 2064 / 29.3; first 117 ms; handoff 0.0 ms; turn 2212 ms; 76.6 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 5 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 28 / 0 / 5 | 95 / 81 / 80 | 178 / 37.7 |
| cpu:gpu | 26 / 0 / 7 | 73 / 74 / 70 | 193 / 25.4 |
| cpu:npu | 24 / 0 / 9 | 79 / 75 / 82 | 195 / 30.1 |
| gpu:cpu | 23 / 0 / 10 | 92 / 78 / 78 | 507 / 37.6 |
| gpu:gpu | 24 / 0 / 9 | 71 / 72 / 68 | 530 / 25.3 |
| gpu:npu | 21 / 0 / 12 | 76 / 72 / 80 | 534 / 28.6 |
| npu:cpu | 21 / 0 / 12 | 101 / 82 / 82 | 2251 / 38.4 |
| npu:gpu | 26 / 0 / 7 | 70 / 71 / 68 | 2137 / 25.2 |
| npu:npu | 15 / 0 / 18 | 76 / 70 / 79 | 2291 / 28.8 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 199 (6) | 183 (10) | 131 (16) | 153 (1) |
| gpu | 221 (6) | 326 (10) | 294 (16) | 289 (1) |
| npu | 817 (6) | 1706 (10) | 2148 (16) | 2469 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 37.8 (16) | 32.1 (12) | 23.3 (5) | - |
| gpu | 24.9 (16) | 18.0 (12) | 11.1 (5) | - |
| npu | 31.4 (16) | 28.4 (12) | 24.8 (5) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 13 | 20 | 31.0 |
| cpu:npu | 12 | 21 | 28 |
| gpu:cpu | 16 | 17 | 28 |
| gpu:gpu | 11 | 22 | 28.0 |
| gpu:npu | 13 | 20 | 25.0 |
| npu:cpu | 16 | 17 | 25 |
| npu:gpu | 10 | 23 | 28 |
| npu:npu | 12 | 21 | 34 |

## Qwen3-4B-Q4_0 (r5)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 55 / 15.0; first 3369 ms; handoff 0.0 ms; turn 7688 ms; 350.1 s | 54 / 6.4; first 3378 ms; handoff 5.7 ms; turn 8706 ms; 540.2 s | 55 / 13.4; first 3372 ms; handoff 4.9 ms; turn 8162 ms; 366.8 s |
| **gpu** | 112 / 15.4; first 1753 ms; handoff 5.6 ms; turn 5585 ms; 240.7 s | 109 / 6.5; first 1755 ms; handoff 0.0 ms; turn 7061 ms; 426.0 s | 110 / 15.3; first 1772 ms; handoff 6.5 ms; turn 5724 ms; 244.2 s |
| **npu** | 802 / 15.5; first 270 ms; handoff 4.9 ms; turn 4032 ms; 150.1 s | 953 / 6.6; first 240 ms; handoff 14.2 ms; turn 5545 ms; 329.0 s | 791 / 15.4; first 285 ms; handoff 0.0 ms; turn 4158 ms; 149.6 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 1 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 30 / 0 / 3 | 98 / 84 / 84 | 131 / 23.1 |
| cpu:gpu | 32 / 0 / 1 | 76 / 78 / 73 | 133 / 19.7 |
| cpu:npu | 30 / 0 / 3 | 83 / 79 / 89 | 134 / 17.5 |
| gpu:cpu | 29 / 0 / 4 | 96 / 81 / 80 | 207 / 23.3 |
| gpu:gpu | 30 / 0 / 3 | 75 / 76 / 72 | 208 / 19.8 |
| gpu:npu | 30 / 0 / 3 | 84 / 78 / 90 | 230 / 17.4 |
| npu:cpu | 29 / 0 / 4 | 102 / 85 / 85 | 606 / 23.7 |
| npu:gpu | 29 / 0 / 4 | 74 / 75 / 70 | 609 / 19.7 |
| npu:npu | 29 / 0 / 4 | 88 / 81 / 93 | 600 / 17.5 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 81 (6) | 68 (10) | 52 (16) | 63 (1) |
| gpu | 102 (6) | 122 (10) | 108 (16) | 121 (1) |
| npu | 462 (6) | 865 (10) | 855 (16) | 899 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 17.8 (16) | 15.0 (12) | 10.9 (5) | - |
| gpu | 12.9 (16) | 7.3 (12) | 2.3 (5) | - |
| npu | 16.4 (16) | 14.0 (12) | 12.0 (5) | - |

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
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | 33 | 320 / 660 / 3290 | 64.3 / 45.9 / 41.7 | npu:cpu (33.5 s) | 33.5 s | 53.6 s | 66.7 s | 75.2 s | cpu:npu (86.2 s) |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | 33 | 246 / 535 / 2949 | 48.2 / 33.9 / 38.4 | npu:cpu (44.1 s) | 44.1 s | 58.4 s | 81.8 s | 93.8 s | cpu:gpu (111.3 s) |
| r5 | Qwen3-0.6B-Q4_0 | 33 | 239 / 639 / 2942 | 61.7 / 31.5 / 47.3 | npu:cpu (36.5 s) | 36.5 s | 47.9 s | 87.2 s | 85.4 s | cpu:gpu (121.8 s) |
| r5 | Qwen3-1.7B-Q4_0 | 33 | 139 / 295 / 2024 | 32.6 / 18.8 / 29.1 | npu:cpu (68.5 s) | 68.5 s | 76.6 s | 149.9 s | 151.6 s | cpu:gpu (197.0 s) |
| r5 | Qwen3-4B-Q4_0 | 33 | 55 / 110 / 849 | 15.3 / 6.5 / 14.7 | npu:npu (149.6 s) | 150.1 s | 149.6 s | 426.0 s | 350.1 s | cpu:gpu (540.2 s) |

## Cooling before each cell

| run | model | cell | gate | waited s | CPU / GPU / NPU °C | GPU cap |
|---|---|---|---|---|---|---|
| r5 | Llama-3.2-1B-Instruct-Q4_0 | cpu:npu | passed | 15 | 55 / 54 / 55 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | npu:cpu | passed | 116 | 55 / 54 / 54 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | npu:npu | passed | 166 | 54 / 54 / 54 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | cpu:gpu | passed | 106 | 55 / 54 / 54 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | gpu:npu | passed | 166 | 55 / 54 / 55 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | gpu:gpu | passed | 131 | 55 / 54 / 55 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | cpu:cpu | passed | 141 | 55 / 53 / 54 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | npu:gpu | passed | 106 | 55 / 54 / 55 | 0 |
| r5 | Llama-3.2-1B-Instruct-Q4_0 | gpu:cpu | passed | 70 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | cpu:gpu | passed | 196 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | npu:cpu | passed | 181 | 55 / 54 / 54 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | gpu:npu | passed | 146 | 55 / 54 / 54 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | cpu:npu | passed | 116 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | npu:gpu | passed | 146 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | npu:npu | passed | 111 | 55 / 54 / 54 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | gpu:cpu | passed | 91 | 54 / 53 / 54 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | gpu:gpu | passed | 121 | 55 / 54 / 54 | 0 |
| r5 | Qwen3-0.6B-Q4_0 | cpu:cpu | passed | 111 | 55 / 54 / 54 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | cpu:gpu | passed | 191 | 55 / 54 / 54 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | cpu:cpu | passed | 106 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | npu:gpu | passed | 171 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | gpu:cpu | passed | 186 | 55 / 53 / 54 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | npu:npu | passed | 221 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | cpu:npu | passed | 161 | 55 / 54 / 54 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | gpu:gpu | passed | 226 | 55 / 53 / 54 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | npu:cpu | passed | 151 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-1.7B-Q4_0 | gpu:npu | passed | 201 | 55 / 54 / 55 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu:gpu | passed | 161 | 55 / 54 / 55 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu:gpu | passed | 136 | 55 / 54 / 55 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu:npu | passed | 171 | 55 / 54 / 55 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu:npu | passed | 136 | 55 / 54 / 55 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu:cpu | passed | 151 | 55 / 54 / 55 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu:npu | passed | 176 | 55 / 54 / 54 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu:cpu | passed | 111 | 55 / 54 / 54 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu:gpu | passed | 116 | 55 / 54 / 55 | 0 |
| r5 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu:cpu | passed | 176 | 55 / 53 / 54 | 0 |
| r5 | Qwen3-4B-Q4_0 | gpu:cpu | passed | 171 | 54 / 53 / 54 | 0 |
| r5 | Qwen3-4B-Q4_0 | npu:gpu | timed_out | 302 | 56 / 54 / 55 | 0 |
| r5 | Qwen3-4B-Q4_0 | cpu:cpu | passed | 297 | 55 / 54 / 55 | 0 |
| r5 | Qwen3-4B-Q4_0 | cpu:gpu | timed_out | 302 | 58 / 57 / 58 | 0 |
| r5 | Qwen3-4B-Q4_0 | npu:npu | timed_out | 302 | 58 / 57 / 57 | 0 |
| r5 | Qwen3-4B-Q4_0 | gpu:npu | timed_out | 302 | 57 / 55 / 56 | 0 |
| r5 | Qwen3-4B-Q4_0 | npu:cpu | timed_out | 302 | 57 / 56 / 56 | 0 |
| r5 | Qwen3-4B-Q4_0 | gpu:gpu | timed_out | 302 | 57 / 55 / 56 | 0 |
| r5 | Qwen3-4B-Q4_0 | cpu:npu | passed | 302 | 55 / 54 / 55 | 0 |

## Conditions

- env: {"label": "r5", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "before", "time": 1792910272, "order": "cpu:npu,npu:cpu,npu:npu,cpu:gpu,gpu:npu,gpu:gpu,cpu:cpu,npu:gpu,gpu:cpu", "temps": "cpullc-0-0=54400 cpullc-0-1=53600 cpu-0-2-1=54000 cpu-0-3-0=52500 cpu-0-3-1=52900 cpu-0-4-0=53200 cpu-0-4-1=53200 cpu-0-5-0=53200 cpu-0-5-1=52900 cpullc-1-0=52500 cpullc-1-1=52500 cpu-1-0-0=52500 cpu-1-0-1=52500 cpu-1-1-0=52500 cpu-1-1-1=52900 nsphvx-0=50700 nsphvx-1=50700 nsphvx-2=50700 nsphvx-3=51100 nsphmx-0=51100 nsphmx-1=51100 nsphmx-2=51100 nsphmx-3=51500 gpuss-0=51500 gpuss-1=51200 gpuss-2=51500 gpuss-3=51200 gpuss-4=51900 gpuss-5=50800 gpuss-6=51900 gpuss-7=51900 gpuss-8=51900 gpuss-9=51500 gpuss-10=51900 ddr=52300 cpu-0-0-0=54000 cpu-hw-trip-0=105000 sys-therm-0=48831 cpu-hw-trip-1=105000 cpu-0-0-1=53200 sys-therm-1=50328 sys-therm-2=45332 sys-therm-3=46399 cpu-0-1-0=52500 sys-therm-4=48595 sys-therm-5=51479 sys-therm-6=48686 sys-therm-7=49358 sys-therm-9=52863 cpu-0-1-1=52900 sys-therm-11=42210 sys-therm-12=37976 sys-therm-8=42260 cpu-0-2-0=52500 battery=39000 ", "memavail_kb": 20513720}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53600 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54400 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=53500 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53500 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54000 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=52700 gpuss-7=53100 gpuss-8=53500 gpuss-9=53100 gpuss-10=53100 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=52900 cpu-0-4-0=54000 cpu-0-4-1=53600 cpu-0-5-0=52900 cpu-0-5-1=53200 cpu-1-0-0=54400 cpu-1-0-1=54400 cpu-1-1-0=53200 cpu-1-1-1=53600 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=52700 gpuss-3=51900 gpuss-4=52300 gpuss-5=51900 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53500 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54400 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=54000 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=53600 nsphvx-0=53400 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52700 gpuss-2=53500 gpuss-3=52700 gpuss-4=53100 gpuss-5=52300 gpuss-6=53900 gpuss-7=53500 gpuss-8=53500 gpuss-9=53500 gpuss-10=53500 ddr=54600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=53600 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=52900 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=52700 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=52700 gpuss-7=53100 gpuss-8=53100 gpuss-9=52700 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=52900 cpu-0-2-0=54400 cpu-0-2-1=54000 cpu-0-3-0=52900 cpu-0-3-1=52900 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=52900 cpu-1-0-0=54400 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=53200 nsphvx-0=54200 nsphvx-1=54600 nsphvx-2=53800 nsphvx-3=54600 gpuss-0=53500 gpuss-1=52700 gpuss-2=53500 gpuss-3=52700 gpuss-4=53500 gpuss-5=52300 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53100 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53600 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54000 cpu-0-3-0=53200 cpu-0-3-1=52900 cpu-0-4-0=53600 cpu-0-4-1=53200 cpu-0-5-0=53200 cpu-0-5-1=52900 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=52900 cpu-1-1-1=53600 nsphvx-0=54200 nsphvx-1=54600 nsphvx-2=54600 nsphvx-3=54600 gpuss-0=53900 gpuss-1=53100 gpuss-2=53900 gpuss-3=53100 gpuss-4=53500 gpuss-5=52700 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53900 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r5", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=79500 cpu-0-0-1=79900 cpu-0-1-0=78000 cpu-0-1-1=78000 cpu-0-2-0=81100 cpu-0-2-1=81100 cpu-0-3-0=79100 cpu-0-3-1=79500 cpu-0-4-0=80700 cpu-0-4-1=80300 cpu-0-5-0=79100 cpu-0-5-1=78700 cpu-1-0-0=79900 cpu-1-0-1=80300 cpu-1-1-0=79500 cpu-1-1-1=80700 nsphvx-0=73000 nsphvx-1=72600 nsphvx-2=72200 nsphvx-3=72200 gpuss-0=72100 gpuss-1=71700 gpuss-2=73300 gpuss-3=70900 gpuss-4=71700 gpuss-5=71300 gpuss-6=74400 gpuss-7=75600 gpuss-8=74000 gpuss-9=74400 gpuss-10=73300 ddr=76800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r5", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "after", "time": 1792912011, "exit": 0, "temps": "cpullc-0-0=76400 cpullc-0-1=76000 cpu-0-2-1=75300 cpu-0-3-0=72900 cpu-0-3-1=72900 cpu-0-4-0=74100 cpu-0-4-1=73700 cpu-0-5-0=73700 cpu-0-5-1=74100 cpullc-1-0=78000 cpullc-1-1=72600 cpu-1-0-0=72600 cpu-1-0-1=72600 cpu-1-1-0=72600 cpu-1-1-1=72600 nsphvx-0=69200 nsphvx-1=69200 nsphvx-2=68400 nsphvx-3=68800 nsphmx-0=69200 nsphmx-1=68400 nsphmx-2=68800 nsphmx-3=69200 gpuss-0=68600 gpuss-1=67800 gpuss-2=69000 gpuss-3=67400 gpuss-4=68200 gpuss-5=67400 gpuss-6=69400 gpuss-7=69400 gpuss-8=69000 gpuss-9=69000 gpuss-10=68600 ddr=69800 cpu-0-0-0=72200 cpu-hw-trip-0=105000 sys-therm-0=61326 cpu-hw-trip-1=105000 cpu-0-0-1=70600 sys-therm-1=65663 sys-therm-2=53408 sys-therm-3=55865 cpu-0-1-0=70600 sys-therm-4=61250 sys-therm-5=69527 sys-therm-6=59857 sys-therm-7=61722 sys-therm-9=56052 cpu-0-1-1=69500 sys-therm-11=46289 sys-therm-12=38394 sys-therm-8=46309 cpu-0-2-0=70200 battery=39000 ", "memavail_kb": 20546428}
- env: {"label": "r5", "model": "Qwen3-0.6B-Q4_0.gguf", "when": "before", "time": 1792912013, "order": "cpu:gpu,npu:cpu,gpu:npu,cpu:npu,npu:gpu,npu:npu,gpu:cpu,gpu:gpu,cpu:cpu", "temps": "cpullc-0-0=71400 cpullc-0-1=71000 cpu-0-2-1=71000 cpu-0-3-0=69100 cpu-0-3-1=68700 cpu-0-4-0=69900 cpu-0-4-1=69500 cpu-0-5-0=69500 cpu-0-5-1=69900 cpullc-1-0=68700 cpullc-1-1=68700 cpu-1-0-0=68300 cpu-1-0-1=68300 cpu-1-1-0=67500 cpu-1-1-1=67900 nsphvx-0=65300 nsphvx-1=65700 nsphvx-2=65300 nsphvx-3=64900 nsphmx-0=65300 nsphmx-1=65300 nsphmx-2=66100 nsphmx-3=66100 gpuss-0=65900 gpuss-1=65500 gpuss-2=66300 gpuss-3=65500 gpuss-4=65900 gpuss-5=65500 gpuss-6=66700 gpuss-7=66700 gpuss-8=66300 gpuss-9=65900 gpuss-10=66300 ddr=66700 cpu-0-0-0=68300 cpu-hw-trip-0=105000 sys-therm-0=61271 cpu-hw-trip-1=105000 cpu-0-0-1=67200 sys-therm-1=64756 sys-therm-2=53431 sys-therm-3=55855 cpu-0-1-0=67900 sys-therm-4=61171 sys-therm-5=67594 sys-therm-6=59905 sys-therm-7=61694 sys-therm-9=56285 cpu-0-1-1=67500 sys-therm-11=46316 sys-therm-12=38342 sys-therm-8=46365 cpu-0-2-0=66800 battery=39000 ", "memavail_kb": 20547916}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54800 cpu-0-0-1=54400 cpu-0-1-0=54800 cpu-0-1-1=57900 cpu-0-2-0=55600 cpu-0-2-1=55600 cpu-0-3-0=54400 cpu-0-3-1=54400 cpu-0-4-0=55200 cpu-0-4-1=55200 cpu-0-5-0=56700 cpu-0-5-1=60200 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=54000 cpu-1-1-1=54400 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54400 cpu-0-5-0=53200 cpu-0-5-1=52900 cpu-1-0-0=55200 cpu-1-0-1=55200 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=51900 gpuss-4=52700 gpuss-5=52300 gpuss-6=53500 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=52900 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52300 gpuss-2=52700 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53500 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53500 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=52900 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=52900 cpu-0-4-0=54000 cpu-0-4-1=53600 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=53200 cpu-1-1-1=53600 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=52700 gpuss-3=51900 gpuss-4=52300 gpuss-5=51900 gpuss-6=52700 gpuss-7=53100 gpuss-8=53100 gpuss-9=52700 gpuss-10=52700 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=54000 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53900 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=52900 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=52700 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53500 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r5", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=81400 cpu-0-0-1=81800 cpu-0-1-0=79500 cpu-0-1-1=79900 cpu-0-2-0=82600 cpu-0-2-1=83400 cpu-0-3-0=79100 cpu-0-3-1=79500 cpu-0-4-0=82200 cpu-0-4-1=82600 cpu-0-5-0=80700 cpu-0-5-1=80300 cpu-1-0-0=81100 cpu-1-0-1=82200 cpu-1-1-0=81100 cpu-1-1-1=82200 nsphvx-0=73400 nsphvx-1=73400 nsphvx-2=72600 nsphvx-3=73000 gpuss-0=72500 gpuss-1=72100 gpuss-2=73600 gpuss-3=70900 gpuss-4=72100 gpuss-5=71700 gpuss-6=74800 gpuss-7=76000 gpuss-8=74800 gpuss-9=74800 gpuss-10=74000 ddr=77500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r5", "model": "Qwen3-0.6B-Q4_0.gguf", "when": "after", "time": 1792913920, "exit": 0, "temps": "cpullc-0-0=79100 cpullc-0-1=79500 cpu-0-2-1=78400 cpu-0-3-0=75700 cpu-0-3-1=75300 cpu-0-4-0=76800 cpu-0-4-1=77200 cpu-0-5-0=75300 cpu-0-5-1=75700 cpullc-1-0=75700 cpullc-1-1=75300 cpu-1-0-0=73700 cpu-1-0-1=74100 cpu-1-1-0=73700 cpu-1-1-1=74500 nsphvx-0=70300 nsphvx-1=69900 nsphvx-2=68000 nsphvx-3=68400 nsphmx-0=69200 nsphmx-1=69200 nsphmx-2=69900 nsphmx-3=70300 gpuss-0=69400 gpuss-1=68600 gpuss-2=69400 gpuss-3=67800 gpuss-4=68600 gpuss-5=68200 gpuss-6=69800 gpuss-7=69800 gpuss-8=69800 gpuss-9=69400 gpuss-10=69000 ddr=70200 cpu-0-0-0=72200 cpu-hw-trip-0=105000 sys-therm-0=61253 cpu-hw-trip-1=105000 cpu-0-0-1=71800 sys-therm-1=65851 sys-therm-2=53203 sys-therm-3=56517 cpu-0-1-0=70600 sys-therm-4=62591 sys-therm-5=70152 sys-therm-6=59988 sys-therm-7=61831 sys-therm-9=55598 cpu-0-1-1=70200 sys-therm-11=45389 sys-therm-12=38367 sys-therm-8=45513 cpu-0-2-0=70600 battery=39000 ", "memavail_kb": 20539588}
- env: {"label": "r5", "model": "Qwen3-1.7B-Q4_0.gguf", "when": "before", "time": 1792913921, "order": "cpu:gpu,cpu:cpu,npu:gpu,gpu:cpu,npu:npu,cpu:npu,gpu:gpu,npu:cpu,gpu:npu", "temps": "cpullc-0-0=71400 cpullc-0-1=71400 cpu-0-2-1=70600 cpu-0-3-0=68700 cpu-0-3-1=69100 cpu-0-4-0=71000 cpu-0-4-1=71800 cpu-0-5-0=70600 cpu-0-5-1=70200 cpullc-1-0=69500 cpullc-1-1=69500 cpu-1-0-0=69100 cpu-1-0-1=69100 cpu-1-1-0=69100 cpu-1-1-1=69500 nsphvx-0=65300 nsphvx-1=65700 nsphvx-2=65300 nsphvx-3=65300 nsphmx-0=65700 nsphmx-1=65700 nsphmx-2=65700 nsphmx-3=66500 gpuss-0=66300 gpuss-1=65900 gpuss-2=66300 gpuss-3=65900 gpuss-4=66700 gpuss-5=65900 gpuss-6=67400 gpuss-7=67400 gpuss-8=67400 gpuss-9=67100 gpuss-10=66700 ddr=67800 cpu-0-0-0=69900 cpu-hw-trip-0=105000 sys-therm-0=61228 cpu-hw-trip-1=105000 cpu-0-0-1=69500 sys-therm-1=64859 sys-therm-2=53219 sys-therm-3=56521 cpu-0-1-0=68700 sys-therm-4=62401 sys-therm-5=67977 sys-therm-6=60019 sys-therm-7=61733 sys-therm-9=55679 cpu-0-1-1=68300 sys-therm-11=45410 sys-therm-12=38375 sys-therm-8=45531 cpu-0-2-0=68300 battery=39000 ", "memavail_kb": 20524200}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54400 cpu-0-4-1=54400 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53500 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53100 ddr=54600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54000 cpu-0-3-0=52900 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53200 cpu-0-5-1=52900 cpu-1-0-0=54800 cpu-1-0-1=54000 cpu-1-1-0=52900 cpu-1-1-1=53600 nsphvx-0=54200 nsphvx-1=54600 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=53500 gpuss-5=52700 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53900 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=53600 nsphvx-0=54600 nsphvx-1=54900 nsphvx-2=54200 nsphvx-3=54600 gpuss-0=53900 gpuss-1=53100 gpuss-2=53900 gpuss-3=53100 gpuss-4=53500 gpuss-5=53100 gpuss-6=53900 gpuss-7=53900 gpuss-8=53900 gpuss-9=53500 gpuss-10=53500 ddr=54600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54000 cpu-0-3-0=52900 cpu-0-3-1=52900 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=51900 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53500 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53200 cpu-0-1-0=52900 cpu-0-1-1=53600 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=53600 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=52700 gpuss-3=51900 gpuss-4=52300 gpuss-5=51900 gpuss-6=52700 gpuss-7=53100 gpuss-8=53100 gpuss-9=52700 gpuss-10=52700 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54400 cpu-0-0-1=53600 cpu-0-1-0=53600 cpu-0-1-1=54000 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=53600 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=54200 nsphvx-1=54200 nsphvx-2=54200 nsphvx-3=54600 gpuss-0=53100 gpuss-1=52700 gpuss-2=53500 gpuss-3=52700 gpuss-4=53100 gpuss-5=52300 gpuss-6=53100 gpuss-7=53900 gpuss-8=53500 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=54000 cpu-0-2-0=55200 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54400 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53600 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=53800 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=52700 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r5", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=72600 cpu-0-0-1=72900 cpu-0-1-0=72900 cpu-0-1-1=74100 cpu-0-2-0=74900 cpu-0-2-1=74500 cpu-0-3-0=72200 cpu-0-3-1=72200 cpu-0-4-0=74100 cpu-0-4-1=73300 cpu-0-5-0=72900 cpu-0-5-1=72600 cpu-1-0-0=76400 cpu-1-0-1=76400 cpu-1-1-0=72600 cpu-1-1-1=73700 nsphvx-0=75700 nsphvx-1=76100 nsphvx-2=76100 nsphvx-3=76100 gpuss-0=72100 gpuss-1=70200 gpuss-2=71700 gpuss-3=69000 gpuss-4=69800 gpuss-5=69000 gpuss-6=70900 gpuss-7=72100 gpuss-8=70900 gpuss-9=72100 gpuss-10=70900 ddr=73300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r5", "model": "Qwen3-1.7B-Q4_0.gguf", "when": "after", "time": 1792916713, "exit": 0, "temps": "cpullc-0-0=73300 cpullc-0-1=73300 cpu-0-2-1=73700 cpu-0-3-0=71000 cpu-0-3-1=70600 cpu-0-4-0=72600 cpu-0-4-1=72900 cpu-0-5-0=71400 cpu-0-5-1=71000 cpullc-1-0=72600 cpullc-1-1=71800 cpu-1-0-0=71800 cpu-1-0-1=72200 cpu-1-1-0=71000 cpu-1-1-1=71800 nsphvx-0=69200 nsphvx-1=69600 nsphvx-2=69200 nsphvx-3=69600 nsphmx-0=69600 nsphmx-1=69200 nsphmx-2=68800 nsphmx-3=68800 gpuss-0=68200 gpuss-1=67400 gpuss-2=68600 gpuss-3=67100 gpuss-4=67800 gpuss-5=67100 gpuss-6=67800 gpuss-7=68600 gpuss-8=68200 gpuss-9=67800 gpuss-10=68200 ddr=69000 cpu-0-0-0=70200 cpu-hw-trip-0=105000 sys-therm-0=60484 cpu-hw-trip-1=105000 cpu-0-0-1=70600 sys-therm-1=64525 sys-therm-2=52780 sys-therm-3=54385 cpu-0-1-0=67900 sys-therm-4=59015 sys-therm-5=67330 sys-therm-6=59731 sys-therm-7=61321 sys-therm-9=55811 cpu-0-1-1=67200 sys-therm-11=45563 sys-therm-12=38390 sys-therm-8=45697 cpu-0-2-0=68300 battery=39000 ", "memavail_kb": 20517148}
- env: {"label": "r5", "model": "LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "when": "before", "time": 1792916715, "order": "gpu:gpu,cpu:gpu,gpu:npu,cpu:npu,gpu:cpu,npu:npu,cpu:cpu,npu:gpu,npu:cpu", "temps": "cpullc-0-0=69100 cpullc-0-1=69100 cpu-0-2-1=68300 cpu-0-3-0=66800 cpu-0-3-1=66800 cpu-0-4-0=67500 cpu-0-4-1=67500 cpu-0-5-0=67500 cpu-0-5-1=67500 cpullc-1-0=67200 cpullc-1-1=67200 cpu-1-0-0=66800 cpu-1-0-1=67200 cpu-1-1-0=66800 cpu-1-1-1=67200 nsphvx-0=64600 nsphvx-1=64900 nsphvx-2=64200 nsphvx-3=64600 nsphmx-0=64600 nsphmx-1=64200 nsphmx-2=64600 nsphmx-3=64600 gpuss-0=64700 gpuss-1=64000 gpuss-2=64700 gpuss-3=64000 gpuss-4=64300 gpuss-5=63600 gpuss-6=64300 gpuss-7=64700 gpuss-8=64300 gpuss-9=64700 gpuss-10=64000 ddr=65100 cpu-0-0-0=66400 cpu-hw-trip-0=105000 sys-therm-0=60430 cpu-hw-trip-1=105000 cpu-0-0-1=65600 sys-therm-1=63310 sys-therm-2=52796 sys-therm-3=54436 cpu-0-1-0=65600 sys-therm-4=59000 sys-therm-5=65393 sys-therm-6=59705 sys-therm-7=61220 sys-therm-9=55896 cpu-0-1-1=65200 sys-therm-11=45591 sys-therm-12=38395 sys-therm-8=45736 cpu-0-2-0=65600 battery=39000 ", "memavail_kb": 20519424}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54400 cpu-0-5-0=53200 cpu-0-5-1=53600 cpu-1-0-0=55200 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53600 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=54000 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53100 gpuss-3=52700 gpuss-4=53100 gpuss-5=52300 gpuss-6=53100 gpuss-7=53900 gpuss-8=53500 gpuss-9=53100 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=54200 nsphvx-1=53800 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53500 gpuss-1=52700 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53100 gpuss-7=53900 gpuss-8=53500 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54400 cpu-0-0-1=54000 cpu-0-1-0=54000 cpu-0-1-1=54400 cpu-0-2-0=55200 cpu-0-2-1=55600 cpu-0-3-0=54000 cpu-0-3-1=54000 cpu-0-4-0=54800 cpu-0-4-1=54800 cpu-0-5-0=54400 cpu-0-5-1=54400 cpu-1-0-0=54800 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54800 nsphvx-0=54200 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53500 gpuss-3=52300 gpuss-4=52700 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53600 cpu-0-4-0=54000 cpu-0-4-1=53600 cpu-0-5-0=53600 cpu-0-5-1=54000 cpu-1-0-0=55200 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54400 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53600 cpu-0-2-0=54800 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=53600 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=54200 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=53100 gpuss-1=52300 gpuss-2=53100 gpuss-3=52300 gpuss-4=52700 gpuss-5=52300 gpuss-6=53500 gpuss-7=53500 gpuss-8=53500 gpuss-9=53100 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=53200 cpu-0-4-1=53200 cpu-0-5-0=53200 cpu-0-5-1=53200 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53200 cpu-1-1-1=53200 nsphvx-0=54200 nsphvx-1=54900 nsphvx-2=54200 nsphvx-3=54900 gpuss-0=53500 gpuss-1=53100 gpuss-2=53900 gpuss-3=52700 gpuss-4=53500 gpuss-5=52700 gpuss-6=53500 gpuss-7=53900 gpuss-8=53500 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53600 cpu-0-0-1=53200 cpu-0-1-0=52900 cpu-0-1-1=53200 cpu-0-2-0=54400 cpu-0-2-1=54400 cpu-0-3-0=53200 cpu-0-3-1=53200 cpu-0-4-0=53600 cpu-0-4-1=53600 cpu-0-5-0=53200 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54400 cpu-1-1-0=53200 cpu-1-1-1=54000 nsphvx-0=53400 nsphvx-1=53800 nsphvx-2=53800 nsphvx-3=53800 gpuss-0=52700 gpuss-1=52300 gpuss-2=52700 gpuss-3=52300 gpuss-4=52300 gpuss-5=51900 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=52700 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r5", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=91500 cpu-0-0-1=92300 cpu-0-1-0=89200 cpu-0-1-1=89200 cpu-0-2-0=93000 cpu-0-2-1=93800 cpu-0-3-0=88400 cpu-0-3-1=88800 cpu-0-4-0=93400 cpu-0-4-1=93400 cpu-0-5-0=90300 cpu-0-5-1=90700 cpu-1-0-0=90700 cpu-1-0-1=91900 cpu-1-1-0=91500 cpu-1-1-1=93000 nsphvx-0=78800 nsphvx-1=79200 nsphvx-2=78000 nsphvx-3=78400 gpuss-0=77900 gpuss-1=77100 gpuss-2=79100 gpuss-3=75600 gpuss-4=76800 gpuss-5=76400 gpuss-6=81400 gpuss-7=83000 gpuss-8=80600 gpuss-9=81000 gpuss-10=79500 ddr=84100 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r5", "model": "LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "when": "after", "time": 1792918707, "exit": 0, "temps": "cpullc-0-0=88800 cpullc-0-1=89200 cpu-0-2-1=88000 cpu-0-3-0=83400 cpu-0-3-1=83800 cpu-0-4-0=86100 cpu-0-4-1=86100 cpu-0-5-0=84100 cpu-0-5-1=83000 cpullc-1-0=83000 cpullc-1-1=83000 cpu-1-0-0=81100 cpu-1-0-1=81100 cpu-1-1-0=81100 cpu-1-1-1=81400 nsphvx-0=74900 nsphvx-1=74600 nsphvx-2=73000 nsphvx-3=73000 nsphmx-0=74200 nsphmx-1=74200 nsphmx-2=74900 nsphmx-3=75700 gpuss-0=74000 gpuss-1=73600 gpuss-2=74800 gpuss-3=72500 gpuss-4=73600 gpuss-5=72900 gpuss-6=75200 gpuss-7=75200 gpuss-8=74800 gpuss-9=74400 gpuss-10=73600 ddr=75600 cpu-0-0-0=77600 cpu-hw-trip-0=105000 sys-therm-0=60923 cpu-hw-trip-1=105000 cpu-0-0-1=76800 sys-therm-1=67950 sys-therm-2=51592 sys-therm-3=55424 cpu-0-1-0=75700 sys-therm-4=63422 sys-therm-5=74313 sys-therm-6=59326 sys-therm-7=61472 sys-therm-9=54851 cpu-0-1-1=75300 sys-therm-11=44644 sys-therm-12=38352 sys-therm-8=44742 cpu-0-2-0=74900 battery=39000 ", "memavail_kb": 20488036}
- env: {"label": "r5", "model": "Qwen3-4B-Q4_0.gguf", "when": "before", "time": 1792918709, "order": "gpu:cpu,npu:gpu,cpu:cpu,cpu:gpu,npu:npu,gpu:npu,npu:cpu,gpu:gpu,cpu:npu", "temps": "cpullc-0-0=74900 cpullc-0-1=75300 cpu-0-2-1=74500 cpu-0-3-0=72900 cpu-0-3-1=73300 cpu-0-4-0=74100 cpu-0-4-1=74500 cpu-0-5-0=73300 cpu-0-5-1=73300 cpullc-1-0=72900 cpullc-1-1=72900 cpu-1-0-0=72200 cpu-1-0-1=72600 cpu-1-1-0=72200 cpu-1-1-1=72900 nsphvx-0=68400 nsphvx-1=68400 nsphvx-2=67600 nsphvx-3=68000 nsphmx-0=68400 nsphmx-1=68400 nsphmx-2=68800 nsphmx-3=68800 gpuss-0=68600 gpuss-1=68200 gpuss-2=68600 gpuss-3=67800 gpuss-4=68200 gpuss-5=67400 gpuss-6=69400 gpuss-7=69400 gpuss-8=68600 gpuss-9=68600 gpuss-10=68200 ddr=69400 cpu-0-0-0=71800 cpu-hw-trip-0=105000 sys-therm-0=61120 cpu-hw-trip-1=105000 cpu-0-0-1=71000 sys-therm-1=66294 sys-therm-2=51795 sys-therm-3=55663 cpu-0-1-0=70600 sys-therm-4=63339 sys-therm-5=70589 sys-therm-6=59552 sys-therm-7=61623 sys-therm-9=54976 cpu-0-1-1=70600 sys-therm-11=44656 sys-therm-12=38354 sys-therm-8=44776 cpu-0-2-0=70600 battery=39000 ", "memavail_kb": 20494584}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=53200 cpu-0-0-1=53200 cpu-0-1-0=52900 cpu-0-1-1=52900 cpu-0-2-0=54400 cpu-0-2-1=54000 cpu-0-3-0=52900 cpu-0-3-1=52900 cpu-0-4-0=53200 cpu-0-4-1=53200 cpu-0-5-0=53200 cpu-0-5-1=53600 cpu-1-0-0=54400 cpu-1-0-1=54400 cpu-1-1-0=52900 cpu-1-1-1=53600 nsphvx-0=53400 nsphvx-1=53400 nsphvx-2=53400 nsphvx-3=53800 gpuss-0=52700 gpuss-1=52300 gpuss-2=52700 gpuss-3=51900 gpuss-4=52300 gpuss-5=51500 gpuss-6=52700 gpuss-7=52700 gpuss-8=53100 gpuss-9=52300 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54800 cpu-0-0-1=54400 cpu-0-1-0=54000 cpu-0-1-1=54400 cpu-0-2-0=55600 cpu-0-2-1=55200 cpu-0-3-0=54400 cpu-0-3-1=54000 cpu-0-4-0=54800 cpu-0-4-1=54800 cpu-0-5-0=54400 cpu-0-5-1=54000 cpu-1-0-0=55600 cpu-1-0-1=56000 cpu-1-1-0=54400 cpu-1-1-1=55200 nsphvx-0=54600 nsphvx-1=54900 nsphvx-2=54200 nsphvx-3=54900 gpuss-0=54300 gpuss-1=53100 gpuss-2=54300 gpuss-3=53100 gpuss-4=53500 gpuss-5=52700 gpuss-6=54300 gpuss-7=54300 gpuss-8=54300 gpuss-9=53900 gpuss-10=53900 ddr=55000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54000 cpu-0-0-1=53600 cpu-0-1-0=53200 cpu-0-1-1=53200 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54400 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=54000 cpu-1-0-0=54800 cpu-1-0-1=55200 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54200 gpuss-0=53100 gpuss-1=52700 gpuss-2=53500 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=56700 cpu-0-0-1=56700 cpu-0-1-0=56300 cpu-0-1-1=56000 cpu-0-2-0=57900 cpu-0-2-1=57500 cpu-0-3-0=56300 cpu-0-3-1=56300 cpu-0-4-0=56700 cpu-0-4-1=57100 cpu-0-5-0=56300 cpu-0-5-1=56700 cpu-1-0-0=57900 cpu-1-0-1=57900 cpu-1-1-0=56300 cpu-1-1-1=57100 nsphvx-0=56500 nsphvx-1=56900 nsphvx-2=56500 nsphvx-3=57300 gpuss-0=56200 gpuss-1=55400 gpuss-2=56200 gpuss-3=55000 gpuss-4=55800 gpuss-5=55400 gpuss-6=56200 gpuss-7=56600 gpuss-8=56200 gpuss-9=56200 gpuss-10=55800 ddr=57000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=57100 cpu-0-0-1=56700 cpu-0-1-0=56300 cpu-0-1-1=56300 cpu-0-2-0=57900 cpu-0-2-1=57900 cpu-0-3-0=56300 cpu-0-3-1=56000 cpu-0-4-0=57100 cpu-0-4-1=57100 cpu-0-5-0=56700 cpu-0-5-1=57100 cpu-1-0-0=57900 cpu-1-0-1=57900 cpu-1-1-0=56700 cpu-1-1-1=56700 nsphvx-0=56100 nsphvx-1=56900 nsphvx-2=56500 nsphvx-3=56500 gpuss-0=56200 gpuss-1=55400 gpuss-2=56200 gpuss-3=55400 gpuss-4=55800 gpuss-5=55000 gpuss-6=55800 gpuss-7=56600 gpuss-8=56200 gpuss-9=56200 gpuss-10=56200 ddr=57400 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=55600 cpu-0-0-1=55600 cpu-0-1-0=55200 cpu-0-1-1=54800 cpu-0-2-0=56300 cpu-0-2-1=56000 cpu-0-3-0=55200 cpu-0-3-1=54800 cpu-0-4-0=55600 cpu-0-4-1=56300 cpu-0-5-0=55200 cpu-0-5-1=55200 cpu-1-0-0=56700 cpu-1-0-1=56700 cpu-1-1-0=55200 cpu-1-1-1=56000 nsphvx-0=55700 nsphvx-1=56100 nsphvx-2=55300 nsphvx-3=56100 gpuss-0=55000 gpuss-1=53900 gpuss-2=55000 gpuss-3=53900 gpuss-4=54600 gpuss-5=53900 gpuss-6=54600 gpuss-7=55400 gpuss-8=55000 gpuss-9=55000 gpuss-10=55000 ddr=55400 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=56000 cpu-0-0-1=56000 cpu-0-1-0=55600 cpu-0-1-1=56000 cpu-0-2-0=56700 cpu-0-2-1=56700 cpu-0-3-0=55600 cpu-0-3-1=55600 cpu-0-4-0=56300 cpu-0-4-1=56300 cpu-0-5-0=56000 cpu-0-5-1=56300 cpu-1-0-0=57100 cpu-1-0-1=57100 cpu-1-1-0=55600 cpu-1-1-1=56000 nsphvx-0=55700 nsphvx-1=56100 nsphvx-2=55300 nsphvx-3=55700 gpuss-0=55000 gpuss-1=54600 gpuss-2=55400 gpuss-3=54600 gpuss-4=55000 gpuss-5=54600 gpuss-6=55400 gpuss-7=55800 gpuss-8=55400 gpuss-9=55400 gpuss-10=55400 ddr=56200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=56000 cpu-0-0-1=56000 cpu-0-1-0=54800 cpu-0-1-1=55200 cpu-0-2-0=56300 cpu-0-2-1=56300 cpu-0-3-0=55200 cpu-0-3-1=55200 cpu-0-4-0=56000 cpu-0-4-1=56000 cpu-0-5-0=56000 cpu-0-5-1=55600 cpu-1-0-0=56700 cpu-1-0-1=56300 cpu-1-1-0=55200 cpu-1-1-1=56000 nsphvx-0=55300 nsphvx-1=55700 nsphvx-2=54900 nsphvx-3=56100 gpuss-0=55000 gpuss-1=54300 gpuss-2=55000 gpuss-3=54300 gpuss-4=54600 gpuss-5=53900 gpuss-6=54600 gpuss-7=55400 gpuss-8=55400 gpuss-9=55000 gpuss-10=55000 ddr=56200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54000 cpu-0-0-1=54000 cpu-0-1-0=53200 cpu-0-1-1=54000 cpu-0-2-0=54800 cpu-0-2-1=54800 cpu-0-3-0=53600 cpu-0-3-1=53200 cpu-0-4-0=54000 cpu-0-4-1=54000 cpu-0-5-0=54000 cpu-0-5-1=53600 cpu-1-0-0=54800 cpu-1-0-1=54800 cpu-1-1-0=53600 cpu-1-1-1=54000 nsphvx-0=53800 nsphvx-1=54200 nsphvx-2=53800 nsphvx-3=54600 gpuss-0=53100 gpuss-1=52700 gpuss-2=53500 gpuss-3=52300 gpuss-4=53100 gpuss-5=52300 gpuss-6=53500 gpuss-7=53900 gpuss-8=53100 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r5", "model": "gguf/Qwen3-4B-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=80700 cpu-0-0-1=80700 cpu-0-1-0=79100 cpu-0-1-1=79100 cpu-0-2-0=84500 cpu-0-2-1=85300 cpu-0-3-0=80300 cpu-0-3-1=79900 cpu-0-4-0=82600 cpu-0-4-1=81800 cpu-0-5-0=80700 cpu-0-5-1=80300 cpu-1-0-0=86900 cpu-1-0-1=86100 cpu-1-1-0=80700 cpu-1-1-1=81400 nsphvx-0=86500 nsphvx-1=86900 nsphvx-2=86500 nsphvx-3=86500 gpuss-0=81000 gpuss-1=79100 gpuss-2=81000 gpuss-3=77100 gpuss-4=78700 gpuss-5=77500 gpuss-6=78700 gpuss-7=80200 gpuss-8=79500 gpuss-9=81400 gpuss-10=79900 ddr=82600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r5", "model": "Qwen3-4B-Q4_0.gguf", "when": "after", "time": 1792924138, "exit": 0, "temps": "cpullc-0-0=83000 cpullc-0-1=83000 cpu-0-2-1=82200 cpu-0-3-0=78700 cpu-0-3-1=78700 cpu-0-4-0=80700 cpu-0-4-1=80700 cpu-0-5-0=78700 cpu-0-5-1=78400 cpullc-1-0=84100 cpullc-1-1=84500 cpu-1-0-0=79900 cpu-1-0-1=79900 cpu-1-1-0=78700 cpu-1-1-1=79100 nsphvx-0=78400 nsphvx-1=78800 nsphvx-2=77600 nsphvx-3=78400 nsphmx-0=78000 nsphmx-1=78000 nsphmx-2=78400 nsphmx-3=78400 gpuss-0=76400 gpuss-1=75200 gpuss-2=76000 gpuss-3=74400 gpuss-4=74800 gpuss-5=74000 gpuss-6=75600 gpuss-7=75600 gpuss-8=75600 gpuss-9=76000 gpuss-10=75200 ddr=76400 cpu-0-0-0=77600 cpu-hw-trip-0=105000 sys-therm-0=67796 cpu-hw-trip-1=105000 cpu-0-0-1=76800 sys-therm-1=72470 sys-therm-2=58965 sys-therm-3=61801 cpu-0-1-0=75700 sys-therm-4=67330 sys-therm-5=75896 sys-therm-6=67258 sys-therm-7=69001 sys-therm-9=60119 cpu-0-1-1=76000 sys-therm-11=50298 sys-therm-12=38668 sys-therm-8=50624 cpu-0-2-0=76400 battery=39000 ", "memavail_kb": 20518800}
- run: {"label": "r5", "seed": "2", "bin": "pd_bench5", "gen": 64, "ctx": 8192, "threads": 6, "turns": 6, "cool": 55, "hexagon_env": "", "status": 0}
