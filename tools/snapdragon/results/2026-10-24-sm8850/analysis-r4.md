## Loads

| run | model | processor | load ms | error |
|---|---|---|---|---|
| r4 | Llama-3.2-1B-Instruct-Q4_0 | cpu | 544 |  |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | gpu | 3085 |  |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | npu | 806 |  |
| r4 | Qwen3-1.7B-Q4_0 | cpu | 782 |  |
| r4 | Qwen3-1.7B-Q4_0 | gpu | 4279 |  |
| r4 | Qwen3-1.7B-Q4_0 | npu | 1161 |  |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu | 645 |  |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu | 2953 |  |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu | 563 |  |
| r4 | google_gemma-3-1b-it-Q4_0 | cpu | 646 |  |
| r4 | google_gemma-3-1b-it-Q4_0 | gpu | 1458 |  |
| r4 | google_gemma-3-1b-it-Q4_0 | npu | 726 |  |
| r4 | Qwen3-0.6B-Q4_0 | cpu | 499 |  |
| r4 | Qwen3-0.6B-Q4_0 | gpu | 1467 |  |
| r4 | Qwen3-0.6B-Q4_0 | npu | 583 |  |
| r4 | Qwen3-4B-Q4_0 | cpu | 1383 |  |
| r4 | Qwen3-4B-Q4_0 | gpu | 7160 |  |
| r4 | Qwen3-4B-Q4_0 | npu | 2221 |  |

## Errors and skipped cells

None.

## LFM2.5-1.2B-Instruct-QAD-Q4_0 (r4)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 304 / 59.4; first 798 ms; handoff 0.0 ms; turn 1819 ms; 75.1 s | 327 / 44.8; first 724 ms; handoff 0.7 ms; turn 2093 ms; 83.9 s | 335 / 42.9; first 723 ms; handoff 0.6 ms; turn 2180 ms; 85.0 s |
| **gpu** | 626 / 63.2; first 345 ms; handoff 1.9 ms; turn 1342 ms; 52.6 s | 585 / 44.4; first 430 ms; handoff 0.0 ms; turn 1842 ms; 67.6 s | 660 / 42.5; first 356 ms; handoff 2.2 ms; turn 1851 ms; 67.7 s |
| **npu** | 3227 / 67.1; first 84 ms; handoff 1.1 ms; turn 1014 ms; 34.9 s | 3401 / 51.0; first 78 ms; handoff 1.9 ms; turn 1309 ms; 44.7 s | 3477 / 42.8; first 75 ms; handoff 0.0 ms; turn 1531 ms; 52.1 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 10 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 23 / 0 / 10 | 92 / 78 / 78 | 393 / 73.3 |
| cpu:gpu | 21 / 0 / 12 | 74 / 75 / 71 | 438 / 58.1 |
| cpu:npu | 19 / 0 / 14 | 76 / 72 / 80 | 448 / 43.2 |
| gpu:cpu | 20 / 0 / 13 | 102 / 80 / 78 | 919 / 73.1 |
| gpu:gpu | 22 / 0 / 11 | 71 / 72 / 69 | 954 / 57.4 |
| gpu:npu | 17 / 0 / 16 | 72 / 69 / 77 | 940 / 42.8 |
| npu:cpu | 17 / 0 / 16 | 104 / 81 / 81 | 3285 / 73.3 |
| npu:gpu | 13 / 0 / 20 | 72 / 79 / 70 | 3290 / 57.8 |
| npu:npu | 9 / 0 / 24 | 70 / 67 / 76 | 3487 / 42.9 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 299 (5) | 361 (10) | 326 (16) | 290 (2) |
| gpu | 358 (5) | 617 (10) | 677 (16) | 509 (2) |
| npu | 1358 (5) | 2705 (10) | 3552 (16) | 3758 (2) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 66.4 (15) | 61.9 (12) | 58.0 (6) | - |
| gpu | 48.8 (15) | 45.5 (12) | 43.5 (6) | - |
| npu | 44.1 (15) | 42.3 (12) | 40.1 (6) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 21 | 12 | 40.0 |
| cpu:npu | 14 | 19 | 29 |
| gpu:cpu | 19 | 14 | 32.5 |
| gpu:gpu | 18 | 15 | 38 |
| gpu:npu | 16 | 17 | 26 |
| npu:cpu | 19 | 14 | 31.5 |
| npu:gpu | 17 | 16 | 35.0 |
| npu:npu | 16 | 17 | 26 |

## Llama-3.2-1B-Instruct-Q4_0 (r4)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 234 / 46.4; first 821 ms; handoff 0.0 ms; turn 2075 ms; 93.9 s | 241 / 32.7; first 849 ms; handoff 1.3 ms; turn 2543 ms; 111.5 s | 251 / 39.0; first 836 ms; handoff 1.2 ms; turn 2375 ms; 99.2 s |
| **gpu** | 525 / 48.8; first 429 ms; handoff 2.1 ms; turn 1634 ms; 64.9 s | 533 / 33.6; first 464 ms; handoff 0.0 ms; turn 2236 ms; 83.5 s | 537 / 38.6; first 382 ms; handoff 3.5 ms; turn 1973 ms; 75.8 s |
| **npu** | 3017 / 52.5; first 78 ms; handoff 1.5 ms; turn 1324 ms; 43.7 s | 3047 / 36.5; first 80 ms; handoff 2.9 ms; turn 1877 ms; 61.1 s | 3058 / 38.8; first 80 ms; handoff 0.0 ms; turn 1613 ms; 57.4 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 10 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 23 / 0 / 10 | 91 / 78 / 78 | 301 / 58.0 |
| cpu:gpu | 22 / 0 / 11 | 72 / 73 / 70 | 328 / 44.2 |
| cpu:npu | 20 / 0 / 13 | 77 / 72 / 80 | 330 / 39.8 |
| gpu:cpu | 20 / 0 / 13 | 101 / 79 / 77 | 816 / 58.3 |
| gpu:gpu | 21 / 0 / 12 | 69 / 71 / 67 | 795 / 44.3 |
| gpu:npu | 18 / 0 / 15 | 72 / 69 / 77 | 826 / 39.0 |
| npu:cpu | 15 / 0 / 18 | 103 / 81 / 81 | 3033 / 58.5 |
| npu:gpu | 16 / 0 / 17 | 70 / 76 / 68 | 2905 / 44.2 |
| npu:npu | 8 / 0 / 25 | 71 / 67 / 76 | 2932 / 38.9 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 273 (5) | 289 (11) | 234 (16) | 247 (1) |
| gpu | 319 (5) | 528 (11) | 554 (16) | 453 (1) |
| npu | 1152 (5) | 2565 (11) | 3216 (16) | 3702 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 55.3 (16) | 46.3 (13) | 39.4 (4) | - |
| gpu | 37.9 (16) | 33.4 (13) | 26.1 (4) | - |
| npu | 41.8 (16) | 38.1 (13) | 31.6 (4) | - |

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

## Qwen3-0.6B-Q4_0 (r4)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 237 / 59.6; first 680 ms; handoff 0.0 ms; turn 1477 ms; 84.3 s | 234 / 29.0; first 627 ms; handoff 4.7 ms; turn 2368 ms; 122.7 s | 247 / 48.2; first 625 ms; handoff 3.8 ms; turn 1916 ms; 91.1 s |
| **gpu** | 568 / 62.1; first 323 ms; handoff 4.6 ms; turn 1151 ms; 55.0 s | 532 / 30.3; first 329 ms; handoff 0.0 ms; turn 2019 ms; 90.5 s | 672 / 47.4; first 338 ms; handoff 8.6 ms; turn 1657 ms; 62.4 s |
| **npu** | 3079 / 69.3; first 80 ms; handoff 4.4 ms; turn 839 ms; 34.6 s | 2965 / 32.1; first 84 ms; handoff 11.1 ms; turn 1599 ms; 69.9 s | 2999 / 47.6; first 82 ms; handoff 0.0 ms; turn 1332 ms; 47.6 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 12 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 21 / 0 / 12 | 89 / 76 / 77 | 329 / 75.6 |
| cpu:gpu | 21 / 0 / 12 | 70 / 71 / 68 | 348 / 38.2 |
| cpu:npu | 20 / 0 / 13 | 74 / 71 / 77 | 356 / 49.0 |
| gpu:cpu | 19 / 0 / 14 | 103 / 78 / 77 | 839 / 73.9 |
| gpu:gpu | 20 / 0 / 13 | 67 / 69 / 65 | 870 / 37.9 |
| gpu:npu | 11 / 0 / 22 | 69 / 67 / 73 | 837 / 47.4 |
| npu:cpu | 13 / 0 / 20 | 104 / 81 / 81 | 3067 / 75.8 |
| npu:gpu | 13 / 0 / 20 | 68 / 72 / 65 | 2968 / 37.8 |
| npu:npu | 0 / 0 / 33 | 67 / 64 / 71 | 2984 / 48.0 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 490 (6) | 379 (10) | 219 (16) | 261 (1) |
| gpu | 461 (6) | 714 (10) | 565 (16) | 653 (1) |
| npu | 1260 (6) | 2625 (10) | 3150 (16) | 3861 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 90.0 (16) | 59.0 (12) | 35.9 (5) | - |
| gpu | 44.7 (16) | 29.3 (12) | 15.8 (5) | - |
| npu | 55.6 (16) | 44.7 (12) | 37.0 (5) | - |

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

## Qwen3-1.7B-Q4_0 (r4)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 139 / 32.4; first 1256 ms; handoff 0.0 ms; turn 3015 ms; 148.5 s | 138 / 18.3; first 1271 ms; handoff 7.1 ms; turn 4150 ms; 199.5 s | 141 / 29.6; first 1262 ms; handoff 4.0 ms; turn 3453 ms; 153.8 s |
| **gpu** | 281 / 32.8; first 657 ms; handoff 5.0 ms; turn 2376 ms; 105.9 s | 274 / 18.6; first 658 ms; handoff 0.0 ms; turn 3635 ms; 154.2 s | 295 / 30.0; first 755 ms; handoff 5.9 ms; turn 2836 ms; 110.1 s |
| **npu** | 2091 / 34.1; first 113 ms; handoff 4.1 ms; turn 1912 ms; 67.3 s | 2115 / 19.3; first 117 ms; handoff 9.5 ms; turn 3014 ms; 114.7 s | 2086 / 30.0; first 115 ms; handoff 0.0 ms; turn 2186 ms; 74.9 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 5 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 27 / 0 / 6 | 93 / 79 / 80 | 187 / 39.6 |
| cpu:gpu | 28 / 0 / 5 | 69 / 71 / 68 | 199 / 24.7 |
| cpu:npu | 26 / 0 / 7 | 78 / 74 / 82 | 201 / 29.8 |
| gpu:cpu | 24 / 0 / 9 | 89 / 76 / 76 | 515 / 39.2 |
| gpu:gpu | 26 / 0 / 7 | 69 / 70 / 67 | 518 / 24.8 |
| gpu:npu | 22 / 0 / 11 | 74 / 71 / 79 | 505 / 29.6 |
| npu:cpu | 21 / 0 / 12 | 100 / 80 / 81 | 2320 / 39.6 |
| npu:gpu | 23 / 0 / 10 | 70 / 71 / 68 | 2298 / 24.8 |
| npu:npu | 17 / 0 / 16 | 74 / 70 / 79 | 2290 / 29.3 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 203 (6) | 185 (10) | 131 (16) | 153 (1) |
| gpu | 226 (6) | 321 (10) | 279 (16) | 288 (1) |
| npu | 849 (6) | 1771 (10) | 2226 (16) | 2546 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 38.6 (16) | 32.4 (12) | 23.5 (5) | - |
| gpu | 24.6 (16) | 18.2 (12) | 11.0 (5) | - |
| npu | 32.6 (16) | 28.9 (12) | 25.1 (5) | - |

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

## Qwen3-4B-Q4_0 (r4)

**Incomplete: 8 of nine cells ran.** 22 turns in 5 conversations matched across the 8 cells that ran. Cells with no successful turn: cpu:npu.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 54 / 15.0; first 3692 ms; handoff 0.0 ms; turn 7321 ms; 237.5 s | 52 / 5.6; first 3746 ms; handoff 8.5 ms; turn 9477 ms; 397.5 s | did not run |
| **gpu** | 109 / 15.1; first 1709 ms; handoff 6.6 ms; turn 5375 ms; 165.2 s | 109 / 5.8; first 1731 ms; handoff 0.0 ms; turn 6997 ms; 313.1 s | 110 / 15.2; first 1686 ms; handoff 8.4 ms; turn 5688 ms; 164.4 s |
| **npu** | 864 / 15.6; first 288 ms; handoff 6.3 ms; turn 4013 ms; 99.1 s | 988 / 5.8; first 270 ms; handoff 18.9 ms; turn 5531 ms; 247.2 s | 880 / 15.1; first 280 ms; handoff 0.0 ms; turn 4315 ms; 100.5 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 1 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 19 / 0 / 3 | 97 / 82 / 82 | 138 / 23.7 |
| cpu:gpu | 21 / 0 / 1 | 73 / 75 / 71 | 106 / 19.2 |
| gpu:cpu | 19 / 0 / 3 | 94 / 80 / 80 | 228 / 23.3 |
| gpu:gpu | 19 / 0 / 3 | 70 / 72 / 68 | 227 / 19.3 |
| gpu:npu | 19 / 0 / 3 | 80 / 76 / 86 | 205 / 17.6 |
| npu:cpu | 17 / 0 / 5 | 98 / 83 / 83 | 656 / 22.7 |
| npu:gpu | 18 / 0 / 4 | 70 / 72 / 68 | 648 / 19.3 |
| npu:npu | 15 / 0 / 7 | 79 / 75 / 86 | 640 / 17.6 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 88 (3) | 65 (7) | 50 (11) | 63 (1) |
| gpu | 113 (3) | 111 (7) | 107 (11) | 118 (1) |
| npu | 472 (3) | 869 (7) | 922 (11) | 978 (1) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 18.2 (10) | 15.1 (8) | 10.9 (4) | - |
| gpu | 13.0 (10) | 6.3 (8) | 2.2 (4) | - |
| npu | 17.1 (10) | 14.5 (8) | 12.6 (4) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 22 | 0 | - |
| cpu:gpu | 15 | 7 | 43 |
| gpu:cpu | 15 | 7 | 35 |
| gpu:gpu | 15 | 7 | 35 |
| gpu:npu | 14 | 8 | 28.0 |
| npu:cpu | 15 | 7 | 46 |
| npu:gpu | 14 | 8 | 46.5 |
| npu:npu | 14 | 8 | 44.0 |

## google_gemma-3-1b-it-Q4_0 (r4)

33 turns in 9 conversations matched across all nine cells.
Decoded tokens per turn: [64].

Per cell: prefill tok/s / decode tok/s; median time to first token; median handoff; median turn; all matched turns.

| prefill \ decode | cpu | gpu | npu |
|---|---|---|---|
| **cpu** | 332 / 62.9; first 706 ms; handoff 0.0 ms; turn 1614 ms; 70.2 s | 336 / 29.8; first 659 ms; handoff 1.5 ms; turn 2754 ms; 106.8 s | 350 / 48.3; first 680 ms; handoff 1.1 ms; turn 1989 ms; 78.5 s |
| **gpu** | 561 / 64.8; first 434 ms; handoff 2.0 ms; turn 1448 ms; 54.4 s | 552 / 30.1; first 473 ms; handoff 0.0 ms; turn 2428 ms; 91.5 s | 658 / 47.9; first 409 ms; handoff 3.6 ms; turn 1732 ms; 62.6 s |
| **npu** | 3664 / 69.3; first 76 ms; handoff 1.5 ms; turn 1025 ms; 33.6 s | 3434 / 33.4; first 82 ms; handoff 2.4 ms; turn 1877 ms; 66.2 s | 3542 / 47.3; first 72 ms; handoff 0.0 ms; turn 1390 ms; 47.5 s |

Throttling, as read at the end of each turn (and from pd_bench5 also at its start, with the CPU clusters' frequency ceilings). Capped means a cap was seen; it does not say which phase it hit. Rates on the right use only the 11 turns uncapped in every cell that ran (WildChat-1M); these are early turns in replay order, so they are a restricted cohort, not the phone without throttling.

| cell | capped / unknown / uncapped turns | median CPU / GPU / NPU °C at turn end | common uncapped turns: prefill / decode tok/s |
|---|---|---|---|
| cpu:cpu | 20 / 0 / 13 | 99 / 78 / 78 | 412 / 70.8 |
| cpu:gpu | 22 / 0 / 11 | 70 / 71 / 69 | 453 / 37.3 |
| cpu:npu | 19 / 0 / 14 | 76 / 72 / 78 | 454 / 48.4 |
| gpu:cpu | 19 / 0 / 14 | 101 / 78 / 77 | 848 / 69.9 |
| gpu:gpu | 20 / 0 / 13 | 68 / 69 / 66 | 845 / 36.7 |
| gpu:npu | 11 / 0 / 22 | 70 / 68 / 73 | 816 / 47.9 |
| npu:cpu | 12 / 0 / 21 | 104 / 82 / 82 | 3621 / 71.7 |
| npu:gpu | 13 / 0 / 20 | 69 / 74 / 67 | 3347 / 37.3 |
| npu:npu | 0 / 0 / 33 | 69 / 66 / 72 | 3451 / 47.4 |

Prefill tok/s by the turn's new tokens, from the three cells with that prefill processor (unique turns in brackets):

| prefill on | 1-63 | 64-255 | 256-1023 | 1024+ |
|---|---|---|---|---|
| cpu | 345 (5) | 364 (11) | 349 (15) | 294 (2) |
| gpu | 333 (5) | 591 (11) | 643 (15) | 473 (2) |
| npu | 1382 (5) | 2798 (11) | 3759 (15) | 4106 (2) |

Decode tok/s by the context already held when decoding starts (pos + new tokens), from the three cells with that decode processor:

| decode on | 0-511 | 512-2047 | 2048-4095 | 4096+ |
|---|---|---|---|---|
| cpu | 67.8 (15) | 63.2 (12) | 65.0 (6) | - |
| gpu | 32.7 (15) | 28.8 (12) | 32.1 (6) | - |
| npu | 48.3 (15) | 47.7 (12) | 47.0 (6) | - |

Greedy tokens against cpu:cpu, compared up to the first end of turn on either side:

| cell | identical to the end | differ | median tokens before the first difference |
|---|---|---|---|
| cpu:cpu | 33 | 0 | - |
| cpu:gpu | 16 | 17 | 19 |
| cpu:npu | 9 | 24 | 24.0 |
| gpu:cpu | 10 | 23 | 17 |
| gpu:gpu | 13 | 20 | 21.5 |
| gpu:npu | 8 | 25 | 21 |
| npu:cpu | 15 | 18 | 21.0 |
| npu:gpu | 15 | 18 | 16.5 |
| npu:npu | 8 | 25 | 21 |

## Across models

Prefill and decode rates are each processor's mean over its three cells (all matched turns, throttled or not); the pairing columns are the summed turn time over the matched turns.

| run | model | turns | prefill tok/s CPU / GPU / NPU | decode tok/s CPU / GPU / NPU | fastest pairing | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | slowest pairing |
|---|---|---|---|---|---|---|---|---|---|---|
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | 33 | 322 / 624 / 3368 | 63.2 / 46.7 / 42.7 | npu:cpu (34.9 s) | 34.9 s | 52.1 s | 67.6 s | 75.1 s | cpu:npu (85.0 s) |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | 33 | 242 / 532 / 3041 | 49.2 / 34.3 / 38.8 | npu:cpu (43.7 s) | 43.7 s | 57.4 s | 83.5 s | 93.9 s | cpu:gpu (111.5 s) |
| r4 | Qwen3-0.6B-Q4_0 | 33 | 239 / 591 / 3014 | 63.7 / 30.5 / 47.8 | npu:cpu (34.6 s) | 34.6 s | 47.6 s | 90.5 s | 84.3 s | cpu:gpu (122.7 s) |
| r4 | Qwen3-1.7B-Q4_0 | 33 | 139 / 284 / 2097 | 33.1 / 18.7 / 29.9 | npu:cpu (67.3 s) | 67.3 s | 74.9 s | 154.2 s | 148.5 s | cpu:gpu (199.5 s) |
| r4 | Qwen3-4B-Q4_0 (incomplete) | 22 | 53 / 109 / 911 | 15.2 / 5.7 / 15.2 | npu:cpu (99.1 s) | 99.1 s | 100.5 s | 313.1 s | 237.5 s | cpu:gpu (397.5 s) |
| r4 | google_gemma-3-1b-it-Q4_0 | 33 | 339 / 590 / 3547 | 65.6 / 31.1 / 47.8 | npu:cpu (33.6 s) | 33.6 s | 47.5 s | 91.5 s | 70.2 s | cpu:gpu (106.8 s) |

## Cooling before each cell

| run | model | cell | gate | waited s | CPU / GPU / NPU °C | GPU cap |
|---|---|---|---|---|---|---|
| r4 | Llama-3.2-1B-Instruct-Q4_0 | cpu:npu | passed | 70 | 54 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | cpu:gpu | passed | 111 | 54 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | npu:gpu | passed | 131 | 55 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | gpu:npu | passed | 101 | 55 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | npu:npu | passed | 96 | 55 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | npu:cpu | passed | 91 | 55 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | gpu:gpu | passed | 131 | 55 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | gpu:cpu | passed | 106 | 55 / 54 / 55 | 0 |
| r4 | Llama-3.2-1B-Instruct-Q4_0 | cpu:cpu | passed | 136 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | npu:npu | passed | 176 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | gpu:gpu | passed | 121 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | cpu:gpu | passed | 116 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | npu:gpu | passed | 156 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | gpu:cpu | passed | 131 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | cpu:cpu | passed | 166 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | cpu:npu | passed | 226 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | gpu:npu | passed | 196 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-1.7B-Q4_0 | npu:cpu | passed | 141 | 55 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu:gpu | passed | 146 | 55 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu:gpu | passed | 111 | 54 / 53 / 54 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu:npu | passed | 121 | 55 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu:gpu | passed | 111 | 55 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu:npu | passed | 121 | 55 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu:cpu | passed | 96 | 55 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | npu:cpu | passed | 111 | 54 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | gpu:cpu | passed | 146 | 55 / 54 / 55 | 0 |
| r4 | LFM2.5-1.2B-Instruct-QAD-Q4_0 | cpu:npu | passed | 151 | 54 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | npu:npu | passed | 121 | 55 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | gpu:npu | passed | 80 | 55 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | gpu:gpu | passed | 80 | 55 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | cpu:cpu | passed | 91 | 55 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | npu:gpu | passed | 141 | 54 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | cpu:npu | passed | 80 | 55 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | cpu:gpu | passed | 141 | 55 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | gpu:cpu | passed | 111 | 55 / 54 / 55 | 0 |
| r4 | google_gemma-3-1b-it-Q4_0 | npu:cpu | passed | 146 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | cpu:cpu | passed | 136 | 54 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | npu:cpu | passed | 146 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | cpu:npu | passed | 131 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | npu:npu | passed | 126 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | cpu:gpu | passed | 75 | 54 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | gpu:gpu | passed | 116 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | gpu:npu | passed | 80 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | gpu:cpu | passed | 86 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-0.6B-Q4_0 | npu:gpu | passed | 111 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-4B-Q4_0 | npu:gpu | passed | 91 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-4B-Q4_0 | npu:cpu | passed | 186 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-4B-Q4_0 | cpu:cpu | passed | 287 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-4B-Q4_0 | cpu:gpu | not passed | 302 | 57 / 56 / 57 | 2 |
| r4 | Qwen3-4B-Q4_0 | gpu:cpu | not passed | 302 | 55 / 55 / 56 | 0 |
| r4 | Qwen3-4B-Q4_0 | gpu:npu | not passed | 302 | 55 / 54 / 56 | 0 |
| r4 | Qwen3-4B-Q4_0 | gpu:gpu | passed | 302 | 55 / 54 / 55 | 0 |
| r4 | Qwen3-4B-Q4_0 | npu:npu | passed | 196 | 55 / 54 / 55 | 0 |

## Conditions

- env: {"label": "r4", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "before", "time": 1792872189, "order": "cpu:npu,cpu:gpu,npu:gpu,gpu:npu,npu:npu,npu:cpu,gpu:gpu,gpu:cpu,cpu:cpu", "temps": "cpullc-0-0=72100 cpullc-0-1=71700 cpu-0-2-1=71700 cpu-0-3-0=70200 cpu-0-3-1=70500 cpu-0-4-0=71300 cpu-0-4-1=71300 cpu-0-5-0=70500 cpu-0-5-1=70500 cpullc-1-0=69800 cpullc-1-1=70200 cpu-1-0-0=69000 cpu-1-0-1=69400 cpu-1-1-0=69400 cpu-1-1-1=69400 nsphvx-0=67500 nsphvx-1=66800 nsphvx-2=67200 nsphvx-3=67200 nsphmx-0=67200 nsphmx-1=67500 nsphmx-2=66800 nsphmx-3=67900 gpuss-0=66700 gpuss-1=66700 gpuss-2=67100 gpuss-3=66300 gpuss-4=65500 gpuss-5=66300 gpuss-6=66700 gpuss-7=67100 gpuss-8=66700 gpuss-9=67100 gpuss-10=67100 ddr=67800 cpu-0-0-0=70200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=59705 cpu-0-0-1=69400 sys-therm-1=64576 sys-therm-2=50662 sys-therm-3=54019 sys-therm-4=60991 cpu-0-1-0=69000 sys-therm-5=68315 sys-therm-6=58475 sys-therm-7=60426 sys-therm-9=53008 cpu-0-1-1=69400 sys-therm-11=42746 sys-therm-12=36118 sys-therm-8=43269 battery=39000 cpu-0-2-0=69000 ", "memavail_kb": 19560140}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53100 cpu-0-1-1=53500 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=54600 cpu-0-4-1=53900 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53100 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=53600 nsphvx-2=54400 nsphvx-3=54400 gpuss-0=53500 gpuss-1=52700 gpuss-2=53100 gpuss-3=52700 gpuss-4=52300 gpuss-5=52300 gpuss-6=53100 gpuss-7=53100 gpuss-8=53100 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=53900 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=53900 cpu-1-1-0=53100 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=53600 nsphvx-2=54000 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=52300 gpuss-5=52300 gpuss-6=53100 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54300 cpu-0-2-1=54600 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=53900 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53900 cpu-1-1-1=53900 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53500 gpuss-2=53100 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=53100 gpuss-7=53100 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54600 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=52700 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=53900 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53100 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=53600 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54400 nsphvx-1=53600 nsphvx-2=54000 nsphvx-3=54400 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=53100 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52300 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=55000 cpu-0-2-1=55000 cpu-0-3-0=53900 cpu-0-3-1=54300 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54800 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53500 gpuss-2=53900 gpuss-3=52700 gpuss-4=53100 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53900 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r4", "model": "gguf/Llama-3.2-1B-Instruct-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=85300 cpu-0-0-1=85700 cpu-0-1-0=83000 cpu-0-1-1=82600 cpu-0-2-0=85700 cpu-0-2-1=86400 cpu-0-3-0=82600 cpu-0-3-1=83000 cpu-0-4-0=86400 cpu-0-4-1=86800 cpu-0-5-0=83700 cpu-0-5-1=83700 cpu-1-0-0=82600 cpu-1-0-1=83700 cpu-1-1-0=83000 cpu-1-1-1=84900 nsphvx-0=75700 nsphvx-1=74500 nsphvx-2=74900 nsphvx-3=74900 gpuss-0=74800 gpuss-1=74000 gpuss-2=75200 gpuss-3=72900 gpuss-4=72900 gpuss-5=73600 gpuss-6=76400 gpuss-7=77900 gpuss-8=76000 gpuss-9=76800 gpuss-10=76000 ddr=79100 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r4", "model": "Llama-3.2-1B-Instruct-Q4_0.gguf", "when": "after", "time": 1792873872, "exit": 0, "temps": "cpullc-0-0=79900 cpullc-0-1=80200 cpu-0-2-1=79900 cpu-0-3-0=77500 cpu-0-3-1=77500 cpu-0-4-0=78700 cpu-0-4-1=79100 cpu-0-5-0=77100 cpu-0-5-1=77500 cpullc-1-0=76000 cpullc-1-1=76000 cpu-1-0-0=74800 cpu-1-0-1=75200 cpu-1-1-0=74800 cpu-1-1-1=75200 nsphvx-0=72200 nsphvx-1=69900 nsphvx-2=70200 nsphvx-3=70600 nsphmx-0=70200 nsphmx-1=71000 nsphmx-2=71400 nsphmx-3=72200 gpuss-0=70900 gpuss-1=70500 gpuss-2=70900 gpuss-3=69800 gpuss-4=69400 gpuss-5=69800 gpuss-6=70900 gpuss-7=70900 gpuss-8=70200 gpuss-9=70900 gpuss-10=70500 ddr=71700 cpu-0-0-0=73600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=62162 cpu-0-0-1=73600 sys-therm-1=67181 sys-therm-2=54094 sys-therm-3=57604 sys-therm-4=63911 cpu-0-1-0=72500 sys-therm-5=71608 sys-therm-6=60801 sys-therm-7=62587 sys-therm-9=56381 cpu-0-1-1=72900 sys-therm-11=46333 sys-therm-12=36146 sys-therm-8=47078 battery=39000 cpu-0-2-0=72900 ", "memavail_kb": 19557360}
- env: {"label": "r4", "model": "Qwen3-1.7B-Q4_0.gguf", "when": "before", "time": 1792873874, "order": "npu:npu,gpu:gpu,cpu:gpu,npu:gpu,gpu:cpu,cpu:cpu,cpu:npu,gpu:npu,npu:cpu", "temps": "cpullc-0-0=73600 cpullc-0-1=74000 cpu-0-2-1=72900 cpu-0-3-0=72100 cpu-0-3-1=72500 cpu-0-4-0=72900 cpu-0-4-1=73300 cpu-0-5-0=72100 cpu-0-5-1=72900 cpullc-1-0=70500 cpullc-1-1=71300 cpu-1-0-0=69800 cpu-1-0-1=70200 cpu-1-1-0=70500 cpu-1-1-1=70900 nsphvx-0=67900 nsphvx-1=67200 nsphvx-2=67200 nsphvx-3=67500 nsphmx-0=67500 nsphmx-1=68300 nsphmx-2=67900 nsphmx-3=68700 gpuss-0=68200 gpuss-1=68200 gpuss-2=68200 gpuss-3=67400 gpuss-4=67100 gpuss-5=67400 gpuss-6=68600 gpuss-7=68600 gpuss-8=68200 gpuss-9=68200 gpuss-10=68200 ddr=69400 cpu-0-0-0=71700 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=62162 cpu-0-0-1=71300 sys-therm-1=66312 sys-therm-2=54159 sys-therm-3=57618 sys-therm-4=63807 cpu-0-1-0=70500 sys-therm-5=69581 sys-therm-6=60840 sys-therm-7=62550 sys-therm-9=56436 cpu-0-1-1=70200 sys-therm-11=46353 sys-therm-12=36149 sys-therm-8=47096 battery=39000 cpu-0-2-0=69400 ", "memavail_kb": 19582060}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=53100 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53500 cpu-1-1-1=54600 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54800 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53500 gpuss-2=53900 gpuss-3=52700 gpuss-4=52700 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53100 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=53900 cpu-1-0-1=53900 cpu-1-1-0=53100 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54000 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53100 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53100 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=53900 cpu-0-1-0=53500 cpu-0-1-1=53100 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=52700 gpuss-6=53100 gpuss-7=53100 gpuss-8=53100 gpuss-9=53500 gpuss-10=53100 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=55000 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=55000 cpu-0-5-0=54300 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54800 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53900 gpuss-2=53900 gpuss-3=53100 gpuss-4=52700 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53500 gpuss-9=53900 gpuss-10=53900 ddr=54600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=53600 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53900 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=53100 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=55000 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=53900 cpu-1-0-0=54600 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52300 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=53100 gpuss-9=53900 gpuss-10=53100 ddr=54600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=54300 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=54300 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=52700 gpuss-6=53100 gpuss-7=53500 gpuss-8=52700 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r4", "model": "gguf/Qwen3-1.7B-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=85700 cpu-0-0-1=86400 cpu-0-1-0=83700 cpu-0-1-1=83700 cpu-0-2-0=86800 cpu-0-2-1=87200 cpu-0-3-0=84900 cpu-0-3-1=86400 cpu-0-4-0=87200 cpu-0-4-1=87200 cpu-0-5-0=85300 cpu-0-5-1=85300 cpu-1-0-0=84100 cpu-1-0-1=84900 cpu-1-1-0=84500 cpu-1-1-1=85700 nsphvx-0=78000 nsphvx-1=76800 nsphvx-2=76800 nsphvx-3=77200 gpuss-0=76800 gpuss-1=76400 gpuss-2=77500 gpuss-3=74800 gpuss-4=75200 gpuss-5=75600 gpuss-6=78300 gpuss-7=79900 gpuss-8=77900 gpuss-9=79100 gpuss-10=77500 ddr=81400 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r4", "model": "Qwen3-1.7B-Q4_0.gguf", "when": "after", "time": 1792876466, "exit": 0, "temps": "cpullc-0-0=82600 cpullc-0-1=83000 cpu-0-2-1=82600 cpu-0-3-0=79900 cpu-0-3-1=79900 cpu-0-4-0=81800 cpu-0-4-1=81000 cpu-0-5-0=79500 cpu-0-5-1=79500 cpullc-1-0=78700 cpullc-1-1=79100 cpu-1-0-0=76800 cpu-1-0-1=77100 cpu-1-1-0=77100 cpu-1-1-1=77500 nsphvx-0=74100 nsphvx-1=72900 nsphvx-2=72600 nsphvx-3=72600 nsphmx-0=72200 nsphmx-1=72900 nsphmx-2=72900 nsphmx-3=74100 gpuss-0=72500 gpuss-1=72500 gpuss-2=72500 gpuss-3=71700 gpuss-4=71300 gpuss-5=71700 gpuss-6=72900 gpuss-7=73300 gpuss-8=72500 gpuss-9=73300 gpuss-10=72500 ddr=73600 cpu-0-0-0=76800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=63282 cpu-0-0-1=76400 sys-therm-1=68887 sys-therm-2=54213 sys-therm-3=57761 sys-therm-4=64679 cpu-0-1-0=74400 sys-therm-5=73617 sys-therm-6=61974 sys-therm-7=63923 sys-therm-9=56124 cpu-0-1-1=74400 sys-therm-11=45843 sys-therm-12=36227 sys-therm-8=46616 battery=39000 cpu-0-2-0=74000 ", "memavail_kb": 20295652}
- env: {"label": "r4", "model": "LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "when": "before", "time": 1792876468, "order": "npu:gpu,gpu:gpu,gpu:npu,cpu:gpu,npu:npu,cpu:cpu,npu:cpu,gpu:cpu,cpu:npu", "temps": "cpullc-0-0=74800 cpullc-0-1=74400 cpu-0-2-1=74800 cpu-0-3-0=73300 cpu-0-3-1=73600 cpu-0-4-0=74800 cpu-0-4-1=74000 cpu-0-5-0=73300 cpu-0-5-1=73300 cpullc-1-0=72100 cpullc-1-1=72100 cpu-1-0-0=71300 cpu-1-0-1=72100 cpu-1-1-0=71700 cpu-1-1-1=72500 nsphvx-0=69100 nsphvx-1=68300 nsphvx-2=68700 nsphvx-3=69100 nsphmx-0=68700 nsphmx-1=69500 nsphmx-2=69100 nsphmx-3=69900 gpuss-0=69800 gpuss-1=69800 gpuss-2=70200 gpuss-3=69400 gpuss-4=69400 gpuss-5=69400 gpuss-6=70200 gpuss-7=70200 gpuss-8=70200 gpuss-9=70200 gpuss-10=69800 ddr=70900 cpu-0-0-0=73300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=63316 cpu-0-0-1=72100 sys-therm-1=67756 sys-therm-2=54271 sys-therm-3=57790 sys-therm-4=64546 cpu-0-1-0=71300 sys-therm-5=70946 sys-therm-6=61927 sys-therm-7=63842 sys-therm-9=56240 cpu-0-1-1=70900 sys-therm-11=45900 sys-therm-12=36245 sys-therm-8=46682 battery=39000 cpu-0-2-0=71300 ", "memavail_kb": 20311544}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54400 nsphvx-1=53600 nsphvx-2=54000 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=52300 gpuss-4=52300 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=53100 gpuss-9=53900 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53100 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=54300 cpu-0-4-1=54600 cpu-0-5-0=53500 cpu-0-5-1=53500 cpu-1-0-0=53900 cpu-1-0-1=54300 cpu-1-1-0=53100 cpu-1-1-1=53900 nsphvx-0=54000 nsphvx-1=53600 nsphvx-2=54000 nsphvx-3=54400 gpuss-0=53100 gpuss-1=53100 gpuss-2=53100 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=52700 gpuss-7=52700 gpuss-8=52700 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53500 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54600 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53900 gpuss-3=53100 gpuss-4=53100 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=54300 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53100 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53900 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54600 cpu-1-0-1=54600 cpu-1-1-0=53100 cpu-1-1-1=54300 nsphvx-0=54400 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53100 gpuss-2=53900 gpuss-3=53100 gpuss-4=52700 gpuss-5=52700 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53500 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=54300 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54600 cpu-1-0-1=54300 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54800 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=53100 gpuss-6=53100 gpuss-7=53100 gpuss-8=52700 gpuss-9=53900 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53500 cpu-0-0-1=53500 cpu-0-1-0=53100 cpu-0-1-1=52700 cpu-0-2-0=53900 cpu-0-2-1=53900 cpu-0-3-0=53100 cpu-0-3-1=53100 cpu-0-4-0=53900 cpu-0-4-1=53500 cpu-0-5-0=53100 cpu-0-5-1=53500 cpu-1-0-0=53500 cpu-1-0-1=53100 cpu-1-1-0=52700 cpu-1-1-1=52700 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54400 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=52700 gpuss-6=53100 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53500 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52300 gpuss-5=53100 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53100 cpu-0-1-1=53100 cpu-0-2-0=54300 cpu-0-2-1=53900 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=53900 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=53900 cpu-1-0-1=54300 cpu-1-1-0=53100 cpu-1-1-1=53500 nsphvx-0=54000 nsphvx-1=53600 nsphvx-2=54000 nsphvx-3=54400 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=51900 gpuss-5=52300 gpuss-6=52300 gpuss-7=52700 gpuss-8=52700 gpuss-9=53100 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r4", "model": "gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=72500 cpu-0-0-1=72900 cpu-0-1-0=71700 cpu-0-1-1=71300 cpu-0-2-0=74000 cpu-0-2-1=74000 cpu-0-3-0=72100 cpu-0-3-1=72500 cpu-0-4-0=73600 cpu-0-4-1=73600 cpu-0-5-0=72100 cpu-0-5-1=72100 cpu-1-0-0=75200 cpu-1-0-1=74800 cpu-1-1-0=71700 cpu-1-1-1=72900 nsphvx-0=76800 nsphvx-1=76800 nsphvx-2=77200 nsphvx-3=77600 gpuss-0=72100 gpuss-1=70500 gpuss-2=71700 gpuss-3=69000 gpuss-4=69000 gpuss-5=69400 gpuss-6=69800 gpuss-7=71300 gpuss-8=70200 gpuss-9=72100 gpuss-10=70500 ddr=73300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r4", "model": "LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf", "when": "after", "time": 1792878234, "exit": 0, "temps": "cpullc-0-0=72100 cpullc-0-1=72500 cpu-0-2-1=72900 cpu-0-3-0=70900 cpu-0-3-1=70900 cpu-0-4-0=72100 cpu-0-4-1=72500 cpu-0-5-0=70900 cpu-0-5-1=70900 cpullc-1-0=70500 cpullc-1-1=70900 cpu-1-0-0=70200 cpu-1-0-1=70500 cpu-1-1-0=70200 cpu-1-1-1=70900 nsphvx-0=70200 nsphvx-1=69900 nsphvx-2=69900 nsphvx-3=70200 nsphmx-0=67900 nsphmx-1=68700 nsphmx-2=68300 nsphmx-3=69100 gpuss-0=67800 gpuss-1=67400 gpuss-2=68200 gpuss-3=66300 gpuss-4=66700 gpuss-5=66700 gpuss-6=67100 gpuss-7=67400 gpuss-8=66700 gpuss-9=67400 gpuss-10=67100 ddr=67800 cpu-0-0-0=69000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=60116 cpu-0-0-1=69400 sys-therm-1=64334 sys-therm-2=52553 sys-therm-3=55124 sys-therm-4=60317 cpu-0-1-0=67800 sys-therm-5=67304 sys-therm-6=59416 sys-therm-7=60936 sys-therm-9=55768 cpu-0-1-1=67800 sys-therm-11=45259 sys-therm-12=36103 sys-therm-8=46038 battery=39000 cpu-0-2-0=67800 ", "memavail_kb": 20301388}
- env: {"label": "r4", "model": "google_gemma-3-1b-it-Q4_0.gguf", "when": "before", "time": 1792878235, "order": "npu:npu,gpu:npu,gpu:gpu,cpu:cpu,npu:gpu,cpu:npu,cpu:gpu,gpu:cpu,npu:cpu", "temps": "cpullc-0-0=68200 cpullc-0-1=68200 cpu-0-2-1=68200 cpu-0-3-0=66700 cpu-0-3-1=67400 cpu-0-4-0=68200 cpu-0-4-1=68200 cpu-0-5-0=67400 cpu-0-5-1=67400 cpullc-1-0=66300 cpullc-1-1=66700 cpu-1-0-0=65900 cpu-1-0-1=66300 cpu-1-1-0=66300 cpu-1-1-1=66700 nsphvx-0=64800 nsphvx-1=64500 nsphvx-2=64800 nsphvx-3=65200 nsphmx-0=64100 nsphmx-1=64800 nsphmx-2=64800 nsphmx-3=65200 gpuss-0=64700 gpuss-1=64700 gpuss-2=64700 gpuss-3=64300 gpuss-4=64000 gpuss-5=64000 gpuss-6=64700 gpuss-7=65100 gpuss-8=64700 gpuss-9=65100 gpuss-10=65100 ddr=65900 cpu-0-0-0=67400 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=60083 cpu-0-0-1=67100 sys-therm-1=63375 sys-therm-2=52578 sys-therm-3=55137 sys-therm-4=60183 cpu-0-1-0=66300 sys-therm-5=65650 sys-therm-6=59416 sys-therm-7=60824 sys-therm-9=55877 cpu-0-1-1=65900 sys-therm-11=45292 sys-therm-12=36050 sys-therm-8=46056 battery=39000 cpu-0-2-0=65900 ", "memavail_kb": 20308956}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53100 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=53900 cpu-1-0-1=54300 cpu-1-1-0=53100 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54300 cpu-0-2-1=53900 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53100 gpuss-3=52700 gpuss-4=52700 gpuss-5=52700 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54600 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=54300 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=54300 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54600 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54800 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=53100 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=53900 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53100 gpuss-2=53500 gpuss-3=53100 gpuss-4=52300 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53100 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53100 cpu-0-1-1=53500 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=53900 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=53900 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54000 nsphvx-3=54400 gpuss-0=53500 gpuss-1=53500 gpuss-2=53100 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=53100 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=55000 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54800 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=53100 gpuss-5=53100 gpuss-6=53100 gpuss-7=53100 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54600 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=55000 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53500 gpuss-2=53900 gpuss-3=53100 gpuss-4=52700 gpuss-5=52700 gpuss-6=53100 gpuss-7=53100 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=55000 cpu-0-2-1=54600 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54400 nsphvx-1=54400 nsphvx-2=54800 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53500 gpuss-2=53900 gpuss-3=52700 gpuss-4=52700 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53900 gpuss-10=53900 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53500 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=53900 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r4", "model": "gguf/google_gemma-3-1b-it-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=89200 cpu-0-0-1=90300 cpu-0-1-0=85700 cpu-0-1-1=85700 cpu-0-2-0=90300 cpu-0-2-1=90700 cpu-0-3-0=85700 cpu-0-3-1=86100 cpu-0-4-0=91100 cpu-0-4-1=90700 cpu-0-5-0=86400 cpu-0-5-1=86400 cpu-1-0-0=88400 cpu-1-0-1=89600 cpu-1-1-0=88800 cpu-1-1-1=90700 nsphvx-0=79100 nsphvx-1=78400 nsphvx-2=78000 nsphvx-3=78000 gpuss-0=77500 gpuss-1=77100 gpuss-2=78700 gpuss-3=75200 gpuss-4=75600 gpuss-5=76400 gpuss-6=79500 gpuss-7=81000 gpuss-8=79100 gpuss-9=80200 gpuss-10=78700 ddr=82600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r4", "model": "google_gemma-3-1b-it-Q4_0.gguf", "when": "after", "time": 1792879862, "exit": 0, "temps": "cpullc-0-0=84500 cpullc-0-1=84500 cpu-0-2-1=84500 cpu-0-3-0=81000 cpu-0-3-1=81400 cpu-0-4-0=83000 cpu-0-4-1=83000 cpu-0-5-0=80600 cpu-0-5-1=80600 cpullc-1-0=80200 cpullc-1-1=80200 cpu-1-0-0=77900 cpu-1-0-1=78300 cpu-1-1-0=78300 cpu-1-1-1=79100 nsphvx-0=74500 nsphvx-1=73700 nsphvx-2=72600 nsphvx-3=72900 nsphmx-0=72900 nsphmx-1=73700 nsphmx-2=73700 nsphmx-3=74900 gpuss-0=72900 gpuss-1=72500 gpuss-2=73300 gpuss-3=71300 gpuss-4=71700 gpuss-5=71700 gpuss-6=72900 gpuss-7=73300 gpuss-8=72900 gpuss-9=73300 gpuss-10=72500 ddr=74000 cpu-0-0-0=76800 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=61195 cpu-0-0-1=76400 sys-therm-1=67967 sys-therm-2=52075 sys-therm-3=55914 sys-therm-4=63764 cpu-0-1-0=74400 sys-therm-5=73652 sys-therm-6=59545 sys-therm-7=61653 sys-therm-9=55375 cpu-0-1-1=74800 sys-therm-11=45478 sys-therm-12=36118 sys-therm-8=46008 battery=39000 cpu-0-2-0=74000 ", "memavail_kb": 20290456}
- env: {"label": "r4", "model": "Qwen3-0.6B-Q4_0.gguf", "when": "before", "time": 1792879863, "order": "cpu:cpu,npu:cpu,cpu:npu,npu:npu,cpu:gpu,gpu:gpu,gpu:npu,gpu:cpu,npu:gpu", "temps": "cpullc-0-0=75200 cpullc-0-1=74400 cpu-0-2-1=74400 cpu-0-3-0=72900 cpu-0-3-1=73300 cpu-0-4-0=74400 cpu-0-4-1=74400 cpu-0-5-0=72900 cpu-0-5-1=73300 cpullc-1-0=71700 cpullc-1-1=72100 cpu-1-0-0=71300 cpu-1-0-1=71700 cpu-1-1-0=72100 cpu-1-1-1=72500 nsphvx-0=69100 nsphvx-1=68300 nsphvx-2=68300 nsphvx-3=69100 nsphmx-0=68700 nsphmx-1=69500 nsphmx-2=69500 nsphmx-3=69500 gpuss-0=69400 gpuss-1=68600 gpuss-2=69000 gpuss-3=68600 gpuss-4=67800 gpuss-5=68200 gpuss-6=69400 gpuss-7=69400 gpuss-8=69000 gpuss-9=69400 gpuss-10=69000 ddr=70200 cpu-0-0-0=72500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=61355 cpu-0-0-1=72900 sys-therm-1=66737 sys-therm-2=52214 sys-therm-3=56053 sys-therm-4=63664 cpu-0-1-0=71700 sys-therm-5=70794 sys-therm-6=59752 sys-therm-7=61774 sys-therm-9=55504 cpu-0-1-1=70900 sys-therm-11=45508 sys-therm-12=36136 sys-therm-8=46066 battery=39000 cpu-0-2-0=70500 ", "memavail_kb": 20299204}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53100 cpu-0-1-1=53100 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53100 cpu-0-5-1=53900 cpu-1-0-0=53900 cpu-1-0-1=54300 cpu-1-1-0=53100 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53100 gpuss-3=52300 gpuss-4=52300 gpuss-5=52300 gpuss-6=52300 gpuss-7=53100 gpuss-8=52700 gpuss-9=53100 gpuss-10=53100 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=54300 cpu-0-3-1=54300 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=53900 cpu-1-0-0=54600 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "cpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54600 cpu-0-0-1=55000 cpu-0-1-0=53900 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=54300 cpu-0-5-1=55000 cpu-1-0-0=54600 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54600 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54800 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53900 gpuss-10=53500 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54600 cpu-1-0-1=54600 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53500 gpuss-2=53900 gpuss-3=52700 gpuss-4=52300 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53500 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=53900 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53500 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=53900 cpu-1-0-1=53900 cpu-1-1-0=53100 cpu-1-1-1=53500 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54000 nsphvx-3=54400 gpuss-0=53500 gpuss-1=52700 gpuss-2=53500 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52300 gpuss-9=53500 gpuss-10=52700 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54300 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=54600 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54000 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53100 gpuss-3=52700 gpuss-4=52300 gpuss-5=52300 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=53500 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54600 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52300 gpuss-5=53100 gpuss-6=52700 gpuss-7=53100 gpuss-8=53100 gpuss-9=53100 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=55000 cpu-0-2-1=55000 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54800 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53900 gpuss-2=53500 gpuss-3=53100 gpuss-4=53100 gpuss-5=53100 gpuss-6=53100 gpuss-7=53500 gpuss-8=53100 gpuss-9=53900 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=55000 cpu-0-2-1=55000 cpu-0-3-0=53500 cpu-0-3-1=54300 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=52700 gpuss-6=53100 gpuss-7=53100 gpuss-8=53100 gpuss-9=53900 gpuss-10=53500 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- done: {"label": "r4", "model": "gguf/Qwen3-0.6B-Q4_0.gguf", "failures": 0, "temps": "cpu-0-0-0=64700 cpu-0-0-1=64700 cpu-0-1-0=64000 cpu-0-1-1=64000 cpu-0-2-0=66300 cpu-0-2-1=65900 cpu-0-3-0=65100 cpu-0-3-1=65100 cpu-0-4-0=65900 cpu-0-4-1=66300 cpu-0-5-0=64700 cpu-0-5-1=64700 cpu-1-0-0=65500 cpu-1-0-1=65900 cpu-1-1-0=64000 cpu-1-1-1=64300 nsphvx-0=64500 nsphvx-1=63700 nsphvx-2=64500 nsphvx-3=64500 gpuss-0=65500 gpuss-1=65900 gpuss-2=65900 gpuss-3=64700 gpuss-4=64700 gpuss-5=65100 gpuss-6=64700 gpuss-7=65500 gpuss-8=65100 gpuss-9=66300 gpuss-10=65900 ddr=67100 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- env: {"label": "r4", "model": "Qwen3-0.6B-Q4_0.gguf", "when": "after", "time": 1792881556, "exit": 0, "temps": "cpullc-0-0=65500 cpullc-0-1=65900 cpu-0-2-1=66300 cpu-0-3-0=64700 cpu-0-3-1=65100 cpu-0-4-0=65500 cpu-0-4-1=66300 cpu-0-5-0=64300 cpu-0-5-1=64700 cpullc-1-0=64000 cpullc-1-1=64000 cpu-1-0-0=63200 cpu-1-0-1=63600 cpu-1-1-0=63200 cpu-1-1-1=63600 nsphvx-0=62500 nsphvx-1=61700 nsphvx-2=62100 nsphvx-3=62500 nsphmx-0=61400 nsphmx-1=61000 nsphmx-2=61400 nsphmx-3=61700 gpuss-0=62000 gpuss-1=61600 gpuss-2=61600 gpuss-3=60800 gpuss-4=60500 gpuss-5=61200 gpuss-6=61200 gpuss-7=61200 gpuss-8=60800 gpuss-9=61200 gpuss-10=61200 ddr=61600 cpu-0-0-0=63200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=56918 cpu-0-0-1=62800 sys-therm-1=59837 sys-therm-2=50664 sys-therm-3=52065 sys-therm-4=55771 cpu-0-1-0=62000 sys-therm-5=61353 sys-therm-6=55853 sys-therm-7=57177 sys-therm-9=55131 cpu-0-1-1=61600 sys-therm-11=44902 sys-therm-12=35934 sys-therm-8=45493 battery=39000 cpu-0-2-0=62400 ", "memavail_kb": 20347268}
- env: {"label": "r4", "model": "Qwen3-4B-Q4_0.gguf", "when": "before", "time": 1792881558, "order": "npu:gpu,npu:cpu,cpu:cpu,cpu:gpu,gpu:cpu,gpu:npu,gpu:gpu,npu:npu,cpu:npu", "temps": "cpullc-0-0=63200 cpullc-0-1=63200 cpu-0-2-1=62800 cpu-0-3-0=61600 cpu-0-3-1=62000 cpu-0-4-0=62400 cpu-0-4-1=62400 cpu-0-5-0=61200 cpu-0-5-1=62000 cpullc-1-0=60500 cpullc-1-1=60800 cpu-1-0-0=60800 cpu-1-0-1=60800 cpu-1-1-0=60800 cpu-1-1-1=61200 nsphvx-0=59800 nsphvx-1=59000 nsphvx-2=59400 nsphvx-3=59800 nsphmx-0=59000 nsphmx-1=59800 nsphmx-2=59000 nsphmx-3=59800 gpuss-0=59700 gpuss-1=59700 gpuss-2=59700 gpuss-3=59300 gpuss-4=58500 gpuss-5=58900 gpuss-6=59300 gpuss-7=59700 gpuss-8=58900 gpuss-9=59700 gpuss-10=59700 ddr=60100 cpu-0-0-0=61600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 sys-therm-0=56801 cpu-0-0-1=62400 sys-therm-1=58968 sys-therm-2=50650 sys-therm-3=52065 sys-therm-4=55656 cpu-0-1-0=62000 sys-therm-5=60224 sys-therm-6=55786 sys-therm-7=57043 sys-therm-9=55164 cpu-0-1-1=61600 sys-therm-11=44897 sys-therm-12=35877 sys-therm-8=45501 battery=39000 cpu-0-2-0=60500 ", "memavail_kb": 20346328}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54300 cpu-0-2-1=54600 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=53900 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=52700 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54600 cpu-0-0-1=54600 cpu-0-1-0=53500 cpu-0-1-1=53900 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53500 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54600 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=55200 gpuss-0=53900 gpuss-1=53500 gpuss-2=53500 gpuss-3=53100 gpuss-4=52700 gpuss-5=52700 gpuss-6=53100 gpuss-7=53500 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53900 cpu-0-3-1=53900 cpu-0-4-0=54600 cpu-0-4-1=54600 cpu-0-5-0=53900 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53900 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54400 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53100 gpuss-2=53900 gpuss-3=53100 gpuss-4=52700 gpuss-5=53100 gpuss-6=52700 gpuss-7=53500 gpuss-8=52700 gpuss-9=53900 gpuss-10=53100 ddr=54300 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "cpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=56600 cpu-0-0-1=57000 cpu-0-1-0=55800 cpu-0-1-1=55800 cpu-0-2-0=57000 cpu-0-2-1=57000 cpu-0-3-0=56200 cpu-0-3-1=56200 cpu-0-4-0=57000 cpu-0-4-1=56600 cpu-0-5-0=55800 cpu-0-5-1=56200 cpu-1-0-0=56600 cpu-1-0-1=57000 cpu-1-1-0=55800 cpu-1-1-1=56600 nsphvx-0=57100 nsphvx-1=56300 nsphvx-2=56700 nsphvx-3=57100 gpuss-0=55800 gpuss-1=55800 gpuss-2=55400 gpuss-3=55000 gpuss-4=54600 gpuss-5=54600 gpuss-6=55000 gpuss-7=55400 gpuss-8=55000 gpuss-9=55400 gpuss-10=55800 ddr=56200 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "cpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=55000 cpu-0-0-1=55400 cpu-0-1-0=54600 cpu-0-1-1=53900 cpu-0-2-0=55800 cpu-0-2-1=55400 cpu-0-3-0=54600 cpu-0-3-1=55000 cpu-0-4-0=55000 cpu-0-4-1=55800 cpu-0-5-0=54300 cpu-0-5-1=55000 cpu-1-0-0=55400 cpu-1-0-1=55000 cpu-1-1-0=54300 cpu-1-1-1=55000 nsphvx-0=55600 nsphvx-1=54800 nsphvx-2=55600 nsphvx-3=55600 gpuss-0=54600 gpuss-1=54300 gpuss-2=54600 gpuss-3=53900 gpuss-4=53500 gpuss-5=53900 gpuss-6=53500 gpuss-7=54300 gpuss-8=53900 gpuss-9=54300 gpuss-10=54300 ddr=55000 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=55000 cpu-0-0-1=55000 cpu-0-1-0=54300 cpu-0-1-1=54300 cpu-0-2-0=55400 cpu-0-2-1=55000 cpu-0-3-0=54600 cpu-0-3-1=54600 cpu-0-4-0=55000 cpu-0-4-1=55400 cpu-0-5-0=54300 cpu-0-5-1=54300 cpu-1-0-0=55000 cpu-1-0-1=55000 cpu-1-1-0=54300 cpu-1-1-1=54600 nsphvx-0=55200 nsphvx-1=54800 nsphvx-2=55200 nsphvx-3=55600 gpuss-0=54300 gpuss-1=53900 gpuss-2=54300 gpuss-3=53500 gpuss-4=53100 gpuss-5=53500 gpuss-6=53500 gpuss-7=53900 gpuss-8=53900 gpuss-9=53900 gpuss-10=53900 ddr=54600 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "gpu", "decode": "gpu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54600 cpu-0-1-0=53900 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54600 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53500 cpu-0-5-1=53900 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54800 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54400 gpuss-0=53500 gpuss-1=53100 gpuss-2=53100 gpuss-3=52700 gpuss-4=52300 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53500 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
- cell: {"when": "start", "label": "r4", "model": "gguf/Qwen3-4B-Q4_0.gguf", "prefill": "npu", "decode": "npu", "gen": 64, "ctx": 8192, "threads": 6, "max_turns": 6, "npu_devices": "HTP0,HTP1", "temps": "cpu-0-0-0=54300 cpu-0-0-1=54300 cpu-0-1-0=53500 cpu-0-1-1=53500 cpu-0-2-0=54600 cpu-0-2-1=54300 cpu-0-3-0=53500 cpu-0-3-1=53900 cpu-0-4-0=54300 cpu-0-4-1=54300 cpu-0-5-0=53900 cpu-0-5-1=54300 cpu-1-0-0=54300 cpu-1-0-1=54300 cpu-1-1-0=53500 cpu-1-1-1=54300 nsphvx-0=54400 nsphvx-1=54000 nsphvx-2=54400 nsphvx-3=54800 gpuss-0=53500 gpuss-1=53500 gpuss-2=53500 gpuss-3=52700 gpuss-4=52700 gpuss-5=52700 gpuss-6=52700 gpuss-7=53100 gpuss-8=52700 gpuss-9=53900 gpuss-10=53100 ddr=53900 cpu-hw-trip-0=105000 cpu-hw-trip-1=105000 "}
