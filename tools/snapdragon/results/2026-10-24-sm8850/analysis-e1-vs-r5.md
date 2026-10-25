# ExecuTorch QNN runner: 54 invocations, 54 with timings, 0 failed
rep 1: 27 of 27 manifest prompts timed; missing []
rep 2: 27 of 27 manifest prompts timed; missing []
cooling gate: cool passed after 45s hottest 54800; cool passed after 5s hottest 52900

## Per rep
| rep | turns | load ms (median) | prefill tok/s (pooled) | decode tok/s (pooled) | decode steps (min..max) | hottest before (max) | GPU cap > 0 before |
|---|---|---|---|---|---|---|---|
| 1 | 27 | 692 | 1057 | 30.3 | 63..63 | 60 °C | 0 |
| 2 | 27 | 692 | 1051 | 30.4 | 63..63 | 56 °C | 0 |

## Same turns, llama.cpp npu:npu (Qwen3-0.6B) beside ExecuTorch
Prefill: ExecuTorch reads the whole prompt (`n`), llama.cpp only the new tokens. TTFT is each one's time to its first token for that turn.

| conv | turn | n (whole) | new | ET rep | ET prefill ms | ET prefill tok/s | llama.cpp prefill ms | llama.cpp prefill tok/s | ET decode tok/s | llama.cpp decode tok/s | ET steps | llama.cpp steps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Daring-Anteater-0 | 0 | 30 | 30 | 1 | 114 | 263 | 42 | 711 | 30.5 | 56.3 | 63 | 63 |
| Daring-Anteater-0 | 1 | 547 | 517 | 1 | 523 | 1046 | 146 | 3547 | 30.2 | 49.8 | 63 | 63 |
| Daring-Anteater-0 | 2 | 1532 | 985 | 1 | 1321 | 1160 | 262 | 3757 | 30.1 | 42.1 | 63 | 63 |
| Daring-Anteater-6 | 0 | 268 | 268 | 1 | 321 | 835 | 86 | 3117 | 31.0 | 53.5 | 63 | 63 |
| Daring-Anteater-6 | 1 | 521 | 253 | 1 | 526 | 990 | 80 | 3153 | 32.8 | 50.6 | 63 | 63 |
| Daring-Anteater-6 | 2 | 1186 | 665 | 1 | 1041 | 1139 | 197 | 3380 | 30.9 | 44.1 | 63 | 63 |
| Daring-Anteater-8 | 0 | 59 | 59 | 1 | 117 | 504 | 43 | 1379 | 30.2 | 55.7 | 63 | 63 |
| Daring-Anteater-8 | 1 | 226 | 167 | 1 | 215 | 1051 | 60 | 2766 | 30.3 | 56.7 | 63 | 63 |
| UltraChat-200k-0 | 0 | 133 | 133 | 1 | 216 | 616 | 53 | 2515 | 30.3 | 56.2 | 63 | 63 |
| UltraChat-200k-0 | 1 | 188 | 55 | 1 | 214 | 879 | 42 | 1319 | 29.9 | 54.2 | 63 | 63 |
| UltraChat-200k-0 | 2 | 397 | 209 | 1 | 422 | 941 | 68 | 3055 | 29.7 | 53.5 | 63 | 63 |
| UltraChat-200k-0 | 3 | 501 | 104 | 1 | 417 | 1201 | 51 | 2031 | 30.1 | 50.9 | 63 | 63 |
| UltraChat-200k-2 | 0 | 139 | 139 | 1 | 214 | 650 | 59 | 2361 | 30.4 | 56.7 | 63 | 63 |
| UltraChat-200k-2 | 1 | 1243 | 1104 | 1 | 1071 | 1161 | 288 | 3829 | 29.0 | 43.6 | 63 | 63 |
| UltraChat-200k-2 | 2 | 1781 | 538 | 1 | 1519 | 1172 | 187 | 2870 | 29.0 | 39.9 | 63 | 63 |
| UltraChat-200k-3 | 0 | 162 | 162 | 1 | 216 | 750 | 60 | 2716 | 29.6 | 57.6 | 63 | 63 |
| UltraChat-200k-3 | 1 | 221 | 59 | 1 | 215 | 1028 | 43 | 1370 | 30.1 | 53.9 | 63 | 63 |
| WildChat-1M-18 | 0 | 63 | 63 | 1 | 117 | 538 | 46 | 1371 | 30.3 | 56.9 | 63 | 63 |
| WildChat-1M-18 | 1 | 520 | 457 | 1 | 547 | 951 | 122 | 3739 | 30.5 | 49.7 | 63 | 63 |
| WildChat-1M-18 | 2 | 845 | 325 | 1 | 729 | 1159 | 110 | 2968 | 32.2 | 46.1 | 63 | 63 |
| WildChat-1M-18 | 3 | 1668 | 823 | 1 | 1493 | 1117 | 245 | 3357 | 29.5 | 41.2 | 63 | 63 |
| WildChat-1M-23 | 0 | 18 | 18 | 1 | 114 | 158 | 36 | 505 | 31.2 | 56.6 | 63 | 63 |
| WildChat-1M-23 | 1 | 106 | 88 | 1 | 116 | 914 | 46 | 1914 | 30.2 | 55.3 | 63 | 63 |
| WildChat-1M-23 | 2 | 303 | 197 | 1 | 317 | 956 | 70 | 2822 | 31.3 | 55.9 | 63 | 63 |
| WildChat-1M-40 | 0 | 263 | 263 | 1 | 333 | 790 | 82 | 3194 | 29.6 | 52.8 | 63 | 63 |
| WildChat-1M-40 | 1 | 847 | 584 | 1 | 734 | 1154 | 176 | 3312 | 29.3 | 43.7 | 63 | 63 |
| WildChat-1M-40 | 2 | 1472 | 625 | 1 | 1229 | 1198 | 189 | 3299 | 29.3 | 43.2 | 63 | 63 |
| Daring-Anteater-0 | 0 | 30 | 30 | 2 | 112 | 268 | 42 | 711 | 31.1 | 56.3 | 63 | 63 |
| Daring-Anteater-0 | 1 | 547 | 517 | 2 | 518 | 1056 | 146 | 3547 | 33.3 | 49.8 | 63 | 63 |
| Daring-Anteater-0 | 2 | 1532 | 985 | 2 | 1304 | 1175 | 262 | 3757 | 29.9 | 42.1 | 63 | 63 |
| Daring-Anteater-6 | 0 | 268 | 268 | 2 | 330 | 812 | 86 | 3117 | 29.9 | 53.5 | 63 | 63 |
| Daring-Anteater-6 | 1 | 521 | 253 | 2 | 532 | 979 | 80 | 3153 | 28.4 | 50.6 | 63 | 63 |
| Daring-Anteater-6 | 2 | 1186 | 665 | 2 | 1058 | 1121 | 197 | 3380 | 30.1 | 44.1 | 63 | 63 |
| Daring-Anteater-8 | 0 | 59 | 59 | 2 | 113 | 522 | 43 | 1379 | 30.8 | 55.7 | 63 | 63 |
| Daring-Anteater-8 | 1 | 226 | 167 | 2 | 219 | 1032 | 60 | 2766 | 31.0 | 56.7 | 63 | 63 |
| UltraChat-200k-0 | 0 | 133 | 133 | 2 | 212 | 627 | 53 | 2515 | 29.8 | 56.2 | 63 | 63 |
| UltraChat-200k-0 | 1 | 188 | 55 | 2 | 214 | 879 | 42 | 1319 | 29.6 | 54.2 | 63 | 63 |
| UltraChat-200k-0 | 2 | 397 | 209 | 2 | 419 | 947 | 68 | 3055 | 30.0 | 53.5 | 63 | 63 |
| UltraChat-200k-0 | 3 | 501 | 104 | 2 | 419 | 1196 | 51 | 2031 | 30.6 | 50.9 | 63 | 63 |
| UltraChat-200k-2 | 0 | 139 | 139 | 2 | 213 | 653 | 59 | 2361 | 30.9 | 56.7 | 63 | 63 |
| UltraChat-200k-2 | 1 | 1243 | 1104 | 2 | 1070 | 1162 | 288 | 3829 | 30.3 | 43.6 | 63 | 63 |
| UltraChat-200k-2 | 2 | 1781 | 538 | 2 | 1559 | 1142 | 187 | 2870 | 29.3 | 39.9 | 63 | 63 |
| UltraChat-200k-3 | 0 | 162 | 162 | 2 | 212 | 764 | 60 | 2716 | 31.0 | 57.6 | 63 | 63 |
| UltraChat-200k-3 | 1 | 221 | 59 | 2 | 216 | 1023 | 43 | 1370 | 30.1 | 53.9 | 63 | 63 |
| WildChat-1M-18 | 0 | 63 | 63 | 2 | 111 | 568 | 46 | 1371 | 30.3 | 56.9 | 63 | 63 |
| WildChat-1M-18 | 1 | 520 | 457 | 2 | 515 | 1010 | 122 | 3739 | 29.2 | 49.7 | 63 | 63 |
| WildChat-1M-18 | 2 | 845 | 325 | 2 | 725 | 1166 | 110 | 2968 | 31.6 | 46.1 | 63 | 63 |
| WildChat-1M-18 | 3 | 1668 | 823 | 2 | 1560 | 1069 | 245 | 3357 | 29.4 | 41.2 | 63 | 63 |
| WildChat-1M-23 | 0 | 18 | 18 | 2 | 114 | 158 | 36 | 505 | 30.6 | 56.6 | 63 | 63 |
| WildChat-1M-23 | 1 | 106 | 88 | 2 | 113 | 938 | 46 | 1914 | 30.6 | 55.3 | 63 | 63 |
| WildChat-1M-23 | 2 | 303 | 197 | 2 | 320 | 947 | 70 | 2822 | 31.0 | 55.9 | 63 | 63 |
| WildChat-1M-40 | 0 | 263 | 263 | 2 | 313 | 840 | 82 | 3194 | 32.5 | 52.8 | 63 | 63 |
| WildChat-1M-40 | 1 | 847 | 584 | 2 | 730 | 1160 | 176 | 3312 | 30.2 | 43.7 | 63 | 63 |
| WildChat-1M-40 | 2 | 1472 | 625 | 2 | 1277 | 1153 | 189 | 3299 | 30.0 | 43.2 | 63 | 63 |

rep 1: 27 matched turns. Decode, pooled: ExecuTorch 30.3 tok/s, llama.cpp 50.3 tok/s.
  First turns only (both read the same tokens, 9 turns): prefill ExecuTorch 644 tok/s, llama.cpp 2242 tok/s.
  Median time to first token over matched turns: ExecuTorch 333 ms (whole history), llama.cpp 70 ms (new tokens only).
rep 2: 27 matched turns. Decode, pooled: ExecuTorch 30.4 tok/s, llama.cpp 50.3 tok/s.
  First turns only (both read the same tokens, 9 turns): prefill ExecuTorch 656 tok/s, llama.cpp 2242 tok/s.
  Median time to first token over matched turns: ExecuTorch 330 ms (whole history), llama.cpp 70 ms (new tokens only).
