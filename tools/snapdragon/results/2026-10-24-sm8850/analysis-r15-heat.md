## LFM2.5-1.2B-Instruct-QAD-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 16 ms | 17 ms | 18 ms | 19 ms | 20 ms | 20 ms | 22 ms | 22 ms |
| gpu | 23 ms | 24 ms | 25 ms | 27 ms | 29 ms | 29 ms | 31 ms | 31 ms |
| npu | 23 ms | 23 ms | 24 ms | 24 ms | 25 ms | 26 ms | 26 ms | 26 ms |

Median decode step by context length, all of that decoder's cells pooled.

Core-pause stretches in the logger's samples: 0.

| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |
|---|---|---|---|
| npu:cpu | 0 | 0 | 0 |
| gpu:gpu | 0 | 0 | 0 |
| cpu:cpu | 1 | 0 | 0 |
| npu:npu | 0 | 0 | 0 |
| cpu:gpu | 0 | 0 | 0 |
| cpu:npu | 0 | 0 | 0 |
| gpu:cpu | 0 | 0 | 0 |
| gpu:npu | 0 | 0 | 0 |
| npu:gpu | 0 | 0 | 0 |

On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.

