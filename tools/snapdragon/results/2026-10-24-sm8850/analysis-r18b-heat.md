## Qwen3-4B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 65 ms | 73 ms | 77 ms | 84 ms | 98 ms | 105 ms | 118 ms | 143 ms |

Median decode step by context length, all of that decoder's cells pooled.

Core-pause stretches in the logger's samples: 5, lasting 0.8, 0.8, 0.8, 0.8, 0.8 min.

| cell | decode steps over 500 ms | in turns with a paused sample | turns with a paused sample |
|---|---|---|---|
| gpu:cpu | 26 | 26 | 6 |

On a GPU-decode cell a step over 500 ms is mostly the decoder's own pace at long context, not a stall.

