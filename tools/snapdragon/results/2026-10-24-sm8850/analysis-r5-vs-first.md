# r5 against the first full run of each model

Qwen3 4B against r6 (its complete first run); the other four against r4. From compare_runs.py.


## LFM2.5-1.2B-Instruct-QAD-Q4_0

33 turns in 9 conversations, 9 cells, succeeded in both runs. Spearman rank correlation of cell times 0.98; largest change +6.4%, median 1.4% either way.

| cell | total s, r4 | total s, r5 | change | rank | prefill tok/s | decode tok/s |
|---|---|---|---|---|---|---|
| npu:cpu | 34.9 | 33.5 | -4.0% | 1 / 1 | 3227 / 3305 | 67.1 / 70.1 |
| npu:gpu | 44.7 | 47.5 | +6.4% | 2 / 2 | 3401 / 3187 | 51.0 / 47.9 |
| npu:npu | 52.1 | 53.6 | +2.7% | 3 / 4 | 3477 / 3377 | 42.8 / 41.6 |
| gpu:cpu | 52.6 | 51.3 | -2.4% | 4 / 3 | 626 / 666 | 63.2 / 63.4 |
| gpu:gpu | 67.6 | 66.7 | -1.3% | 5 / 5 | 585 / 599 | 44.4 / 44.8 |
| gpu:npu | 67.7 | 67.3 | -0.6% | 6 / 6 | 660 / 716 | 42.5 / 41.6 |
| cpu:cpu | 75.1 | 75.2 | +0.1% | 7 / 7 | 304 / 303 | 59.4 / 59.4 |
| cpu:gpu | 83.9 | 84.2 | +0.4% | 8 / 8 | 327 / 323 | 44.8 / 44.9 |
| cpu:npu | 85.0 | 86.2 | +1.4% | 9 / 9 | 335 / 334 | 42.9 / 42.0 |

## Llama-3.2-1B-Instruct-Q4_0

33 turns in 9 conversations, 9 cells, succeeded in both runs. Spearman rank correlation of cell times 1.00; largest change +5.9%, median 1.2% either way.

| cell | total s, r4 | total s, r5 | change | rank | prefill tok/s | decode tok/s |
|---|---|---|---|---|---|---|
| npu:cpu | 43.7 | 44.1 | +1.0% | 1 / 1 | 3017 / 2935 | 52.5 / 52.1 |
| npu:npu | 57.4 | 58.4 | +1.8% | 2 / 2 | 3058 / 3025 | 38.8 / 38.1 |
| npu:gpu | 61.1 | 64.7 | +5.9% | 3 / 3 | 3047 / 2888 | 36.5 / 34.5 |
| gpu:cpu | 64.9 | 68.2 | +5.2% | 4 / 4 | 525 / 502 | 48.8 / 46.3 |
| gpu:npu | 75.8 | 75.2 | -0.8% | 5 / 5 | 537 / 565 | 38.6 / 38.3 |
| gpu:gpu | 83.5 | 81.8 | -2.1% | 6 / 6 | 533 / 537 | 33.6 / 34.4 |
| cpu:cpu | 93.9 | 93.8 | -0.1% | 7 / 7 | 234 / 235 | 46.4 / 46.3 |
| cpu:npu | 99.2 | 98.0 | -1.2% | 8 / 8 | 251 / 260 | 39.0 / 38.8 |
| cpu:gpu | 111.5 | 111.3 | -0.2% | 9 / 9 | 241 / 242 | 32.7 / 32.9 |

## Qwen3-0.6B-Q4_0

33 turns in 9 conversations, 9 cells, succeeded in both runs. Spearman rank correlation of cell times 1.00; largest change +5.5%, median 1.4% either way.

| cell | total s, r4 | total s, r5 | change | rank | prefill tok/s | decode tok/s |
|---|---|---|---|---|---|---|
| npu:cpu | 34.6 | 36.5 | +5.5% | 1 / 1 | 3079 / 2944 | 69.3 / 65.7 |
| npu:npu | 47.6 | 47.9 | +0.7% | 2 / 2 | 2999 / 2948 | 47.6 / 47.3 |
| gpu:cpu | 55.0 | 54.2 | -1.4% | 3 / 3 | 568 / 605 | 62.1 / 61.5 |
| gpu:npu | 62.4 | 61.1 | -2.1% | 4 / 4 | 672 / 753 | 47.4 / 47.0 |
| npu:gpu | 69.9 | 66.9 | -4.3% | 5 / 5 | 2965 / 2933 | 32.1 / 33.7 |
| cpu:cpu | 84.3 | 85.4 | +1.3% | 6 / 6 | 237 / 236 | 59.6 / 57.9 |
| gpu:gpu | 90.5 | 87.2 | -3.6% | 7 / 7 | 532 / 560 | 30.3 / 31.4 |
| cpu:npu | 91.1 | 91.6 | +0.5% | 8 / 8 | 247 / 248 | 48.2 / 47.6 |
| cpu:gpu | 122.7 | 121.8 | -0.7% | 9 / 9 | 234 / 234 | 29.0 / 29.5 |

## Qwen3-1.7B-Q4_0

33 turns in 9 conversations, 9 cells, succeeded in both runs. Spearman rank correlation of cell times 0.95; largest change +3.7%, median 1.8% either way.

| cell | total s, r4 | total s, r5 | change | rank | prefill tok/s | decode tok/s |
|---|---|---|---|---|---|---|
| npu:cpu | 67.3 | 68.5 | +1.8% | 1 / 1 | 2091 / 2016 | 34.1 / 33.6 |
| npu:npu | 74.9 | 76.6 | +2.2% | 2 / 2 | 2086 / 2064 | 30.0 / 29.3 |
| gpu:cpu | 105.9 | 104.9 | -0.9% | 3 / 3 | 281 / 290 | 32.8 / 32.7 |
| gpu:npu | 110.1 | 110.4 | +0.3% | 4 / 4 | 295 / 310 | 30.0 / 29.1 |
| npu:gpu | 114.7 | 118.9 | +3.7% | 5 / 5 | 2115 / 1993 | 19.3 / 18.6 |
| cpu:cpu | 148.5 | 151.6 | +2.1% | 6 / 7 | 139 / 136 | 32.4 / 31.6 |
| cpu:npu | 153.8 | 155.4 | +1.0% | 7 / 8 | 141 / 142 | 29.6 / 28.8 |
| gpu:gpu | 154.2 | 149.9 | -2.8% | 8 / 6 | 274 / 286 | 18.6 / 19.1 |
| cpu:gpu | 199.5 | 197.0 | -1.3% | 9 / 9 | 138 / 139 | 18.3 / 18.7 |

## Qwen3-4B-Q4_0

33 turns in 9 conversations, 9 cells, succeeded in both runs. Spearman rank correlation of cell times 0.95; largest change -5.1%, median 2.9% either way.

| cell | total s, r6 | total s, r5 | change | rank | prefill tok/s | decode tok/s |
|---|---|---|---|---|---|---|
| npu:cpu | 149.5 | 150.1 | +0.4% | 1 / 2 | 813 / 802 | 15.5 / 15.5 |
| npu:npu | 149.9 | 149.6 | -0.2% | 2 / 1 | 801 / 791 | 15.4 / 15.4 |
| gpu:npu | 249.0 | 244.2 | -1.9% | 3 / 4 | 105 / 110 | 15.3 / 15.3 |
| gpu:cpu | 253.7 | 240.7 | -5.1% | 4 / 3 | 102 / 112 | 15.0 / 15.4 |
| npu:gpu | 338.9 | 329.0 | -2.9% | 5 / 5 | 951 / 953 | 6.4 / 6.6 |
| cpu:npu | 349.8 | 366.8 | +4.9% | 6 / 7 | 55 / 55 | 15.4 / 13.4 |
| cpu:cpu | 353.5 | 350.1 | -0.9% | 7 / 6 | 54 / 55 | 15.3 / 15.0 |
| gpu:gpu | 443.0 | 426.0 | -3.9% | 8 / 8 | 106 / 109 | 6.3 / 6.5 |
| cpu:gpu | 557.9 | 540.2 | -3.2% | 9 / 9 | 53 / 54 | 6.2 / 6.4 |

