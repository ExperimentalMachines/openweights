## Qwen3-1.7B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 28 ms | 32 ms | 34 ms | 38 ms | 43 ms | 49 ms | 61 ms | 68 ms |
| gpu | 45 ms | 50 ms | 59 ms | 69 ms | 91 ms | 94 ms | 100 ms | 103 ms |
| npu | 31 ms | 33 ms | 33 ms | 37 ms | 39 ms | 41 ms | 41 ms | 40 ms |

Median decode step by context length, all of that decoder's cells pooled.

No thermal-log samples fall in this model's turns.

## LFM2.5-2.6B-QAD-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 41 ms | 42 ms | 43 ms | 43 ms | 44 ms | 47 ms | 48 ms | 49 ms |
| gpu | 47 ms | 48 ms | 50 ms | 52 ms | 54 ms | 55 ms | 57 ms | 57 ms |
| npu | 45 ms | 46 ms | 47 ms | 48 ms | 49 ms | 50 ms | 50 ms | 51 ms |

Median decode step by context length, all of that decoder's cells pooled.

No thermal-log samples fall in this model's turns.

## Qwen3-1.7B-Q8_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 35 ms | 39 ms | 42 ms | 47 ms | 51 ms | 58 ms | 67 ms | 74 ms |
| gpu | 51 ms | 60 ms | 69 ms | 78 ms | 101 ms | 104 ms | 109 ms | 109 ms |
| npu | 38 ms | 41 ms | 42 ms | 44 ms | 46 ms | 48 ms | 49 ms | 51 ms |

Median decode step by context length, all of that decoder's cells pooled.

No thermal-log samples fall in this model's turns.

## Qwen3-4B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 64 ms | 66 ms | 77 ms | 86 ms | 93 ms | 102 ms | 117 ms | 139 ms |
| gpu | 80 ms | 94 ms | 107 ms | 167 ms | 397 ms | 461 ms | 540 ms | 562 ms |
| npu | 60 ms | 66 ms | 70 ms | 76 ms | 81 ms | 86 ms | 90 ms | 95 ms |

Median decode step by context length, all of that decoder's cells pooled.

Core-pause stretches in the logger's samples: 7, lasting 0.8, 0.8, 1.0, 1.0, 0.8, 2.8, 8.5 min.

| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |
|---|---|---|---|
| gpu:cpu | 31 | 31 | 5 |
| gpu:gpu | 866 | 0 | 0 |
| npu:gpu | 858 | 0 | 0 |
| cpu:npu | 0 | 0 | 4 |
| npu:npu | 0 | 0 | 8 |

On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.

NPU decode in a turn with at least half its samples under cdsp above 0, against the same turn in another NPU-decode cell with none: 7 pairs, median 0.68 times the rate (range 0.65 to 0.72).

