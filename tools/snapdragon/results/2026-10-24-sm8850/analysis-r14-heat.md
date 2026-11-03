## Qwen3-4B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 63 ms | 69 ms | 77 ms | 87 ms | 95 ms | 106 ms | 125 ms | 143 ms |
| gpu | 80 ms | 93 ms | 107 ms | 166 ms | 430 ms | 491 ms | 586 ms | 614 ms |
| npu | 60 ms | 67 ms | 71 ms | 77 ms | 82 ms | 90 ms | 93 ms | 97 ms |

Median decode step by context length, all of that decoder's cells pooled.

Core-pause stretches in the logger's samples: 8, lasting 1.2, 11.5, 1.2, 1.0, 1.0, 1.0, 1.1, 13.9 min.

| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |
|---|---|---|---|
| gpu:cpu | 0 | 0 | 0 |
| gpu:gpu | 1165 | 0 | 0 |
| npu:gpu | 1206 | 0 | 0 |
| cpu:npu | 0 | 0 | 2 |
| npu:npu | 0 | 0 | 15 |
| cpu:gpu | 1237 | 0 | 0 |
| cpu:cpu | 6 | 6 | 1 |
| npu:cpu | 24 | 24 | 4 |
| gpu:npu | 0 | 0 | 15 |

On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.

NPU decode in a turn with at least half its samples under cdsp above 0, against the same turn in another NPU-decode cell with none: 32 pairs, median 0.68 times the rate (range 0.64 to 1.01).

## Llama-3.2-3B-Instruct-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 54 ms | 58 ms | 62 ms | 65 ms | 71 ms | 83 ms | 93 ms | 104 ms |
| gpu | 64 ms | 73 ms | 81 ms | 110 ms | 133 ms | 144 ms | 156 ms | 161 ms |
| npu | 49 ms | 54 ms | 56 ms | 59 ms | 61 ms | 65 ms | 67 ms | 70 ms |

Median decode step by context length, all of that decoder's cells pooled.

Core-pause stretches in the logger's samples: 8, lasting 0.8, 0.6, 2.8, 5.1, 1.0, 1.0, 1.9, 2.0 min.

| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |
|---|---|---|---|
| gpu:cpu | 14 | 14 | 2 |
| gpu:npu | 0 | 0 | 3 |
| npu:npu | 0 | 0 | 7 |
| gpu:gpu | 0 | 0 | 0 |
| cpu:cpu | 8 | 8 | 1 |
| npu:gpu | 0 | 0 | 0 |
| npu:cpu | 9 | 9 | 1 |
| cpu:npu | 0 | 0 | 6 |
| cpu:gpu | 0 | 0 | 0 |

On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.

NPU decode in a turn with at least half its samples under cdsp above 0, against the same turn in another NPU-decode cell with none: 20 pairs, median 0.66 times the rate (range 0.62 to 1.04).

