## LFM2.5-1.2B-Instruct-QAD-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 16 ms | 17 ms | 18 ms | 19 ms | 20 ms | 21 ms | 20 ms | 22 ms |
| gpu | 23 ms | 24 ms | 26 ms | 27 ms | 29 ms | 29 ms | 31 ms | 31 ms |
| npu | 23 ms | 24 ms | 24 ms | 25 ms | 25 ms | 26 ms | 26 ms | 27 ms |

Median decode step by context length, all of that decoder's cells pooled.

## google_gemma-3-1b-it-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 17 ms | 18 ms | 18 ms | 19 ms | 19 ms | 19 ms | 19 ms | 20 ms |
| gpu | 35 ms | 39 ms | 46 ms | 42 ms | 39 ms | 42 ms | 39 ms | 42 ms |
| npu | 21 ms | 21 ms | 21 ms | 21 ms | 21 ms | 22 ms | 22 ms | 22 ms |

Median decode step by context length, all of that decoder's cells pooled.

## Llama-3.2-1B-Instruct-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 24 ms | 28 ms | 29 ms | 32 ms | 34 ms | 36 ms | 37 ms | 38 ms |
| gpu | 30 ms | 33 ms | 36 ms | 40 ms | 45 ms | 46 ms | 50 ms | 51 ms |
| npu | 24 ms | 24 ms | 26 ms | 30 ms | 32 ms | 32 ms | 33 ms | 35 ms |

Median decode step by context length, all of that decoder's cells pooled.

## Qwen3-0.6B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 14 ms | 17 ms | 21 ms | 25 ms | 30 ms | 37 ms | 47 ms | 54 ms |
| gpu | 26 ms | 35 ms | 44 ms | 54 ms | 76 ms | 80 ms | 84 ms | 85 ms |
| npu | 18 ms | 21 ms | 23 ms | 24 ms | 26 ms | 28 ms | 30 ms | 32 ms |

Median decode step by context length, all of that decoder's cells pooled.

## Qwen3-1.7B-Q4_0

| decoder | 0 to 511 | 512 to 1023 | 1024 to 1535 | 1536 to 2047 | 2048 to 2559 | 2560 to 3071 | 3072 to 3583 | 3584 to 4095 |
|---|---|---|---|---|---|---|---|---|
| cpu | 33 ms | 37 ms | 42 ms | 47 ms | 54 ms | 64 ms | 71 ms | 77 ms |
| gpu | 45 ms | 54 ms | 63 ms | 73 ms | 94 ms | 99 ms | 103 ms | 104 ms |
| npu | 32 ms | 34 ms | 38 ms | 40 ms | 41 ms | 43 ms | 43 ms | 47 ms |

Median decode step by context length, all of that decoder's cells pooled.

