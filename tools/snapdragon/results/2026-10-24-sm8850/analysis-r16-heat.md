## Qwen3-1.7B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 31 ms | 35 ms | 38 ms | 42 ms | 46 ms | 53 ms | 61 ms | 68 ms |
| gpu | 45 ms | 54 ms | 63 ms | 72 ms | 95 ms | 99 ms | 103 ms | 103 ms |
| npu | 31 ms | 33 ms | 33 ms | 37 ms | 39 ms | 41 ms | 41 ms | 41 ms |

Median decode step by context length, all of that decoder's cells pooled.

Core-pause stretches in the logger's samples: 2, lasting 1.0, 1.5 min.

| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |
|---|---|---|---|
| cpu:gpu | 0 | 0 | 0 |
| cpu:cpu | 0 | 0 | 0 |
| npu:cpu | 0 | 0 | 0 |
| gpu:gpu | 0 | 0 | 0 |
| npu:npu | 0 | 0 | 0 |
| gpu:cpu | 9 | 9 | 1 |
| gpu:npu | 0 | 0 | 2 |
| npu:gpu | 0 | 0 | 0 |
| cpu:npu | 0 | 0 | 0 |

On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.

NPU decode in a turn with at least half its samples under cdsp above 0, against the same turn in another NPU-decode cell with none: 4 pairs, median 0.51 times the rate (range 0.50 to 0.52).

## Qwen3-4B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 69 ms | 76 ms | 78 ms | 89 ms | 103 ms | 124 ms | 122 ms | 145 ms |

Median decode step by context length, all of that decoder's cells pooled.

Core-pause stretches in the logger's samples: 4, lasting 0.8, 1.0, 1.0, 1.0 min.

| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |
|---|---|---|---|
| gpu:cpu | 24 | 24 | 4 |

On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.

