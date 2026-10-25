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
| Daring-Anteater-0 | 0 | 30 | 30 | 1 | 114 | 263 | 36 | 841 | 30.5 | 58.6 | 63 | 63 |
| Daring-Anteater-0 | 1 | 547 | 517 | 1 | 523 | 1046 | 145 | 3569 | 30.2 | 50.8 | 63 | 63 |
| Daring-Anteater-0 | 2 | 1532 | 985 | 1 | 1321 | 1160 | 264 | 3736 | 30.1 | 42.7 | 63 | 63 |
| Daring-Anteater-6 | 0 | 268 | 268 | 1 | 321 | 835 | 83 | 3212 | 31.0 | 53.8 | 63 | 63 |
| Daring-Anteater-6 | 1 | 521 | 253 | 1 | 526 | 990 | 82 | 3094 | 32.8 | 50.4 | 63 | 63 |
| Daring-Anteater-6 | 2 | 1186 | 665 | 1 | 1041 | 1139 | 194 | 3424 | 30.9 | 44.1 | 63 | 63 |
| Daring-Anteater-8 | 0 | 59 | 59 | 1 | 117 | 504 | 39 | 1528 | 30.2 | 56.3 | 63 | 63 |
| Daring-Anteater-8 | 1 | 226 | 167 | 1 | 215 | 1051 | 59 | 2809 | 30.3 | 54.4 | 63 | 63 |
| UltraChat-200k-0 | 0 | 133 | 133 | 1 | 216 | 616 | 51 | 2612 | 30.3 | 55.4 | 63 | 63 |
| UltraChat-200k-0 | 1 | 188 | 55 | 1 | 214 | 879 | 40 | 1372 | 29.9 | 56.1 | 63 | 63 |
| UltraChat-200k-0 | 2 | 397 | 209 | 1 | 422 | 941 | 68 | 3073 | 29.7 | 53.2 | 63 | 63 |
| UltraChat-200k-0 | 3 | 501 | 104 | 1 | 417 | 1201 | 50 | 2075 | 30.1 | 51.4 | 63 | 63 |
| UltraChat-200k-2 | 0 | 139 | 139 | 1 | 214 | 650 | 52 | 2676 | 30.4 | 54.9 | 63 | 63 |
| UltraChat-200k-2 | 1 | 1243 | 1104 | 1 | 1071 | 1161 | 286 | 3860 | 29.0 | 44.1 | 63 | 63 |
| UltraChat-200k-2 | 2 | 1781 | 538 | 1 | 1519 | 1172 | 183 | 2934 | 29.0 | 40.3 | 63 | 63 |
| UltraChat-200k-3 | 0 | 162 | 162 | 1 | 216 | 750 | 54 | 2973 | 29.6 | 57.3 | 63 | 63 |
| UltraChat-200k-3 | 1 | 221 | 59 | 1 | 215 | 1028 | 41 | 1439 | 30.1 | 55.7 | 63 | 63 |
| WildChat-1M-18 | 0 | 63 | 63 | 1 | 117 | 538 | 47 | 1349 | 30.3 | 57.1 | 63 | 63 |
| WildChat-1M-18 | 1 | 520 | 457 | 1 | 547 | 951 | 121 | 3770 | 30.5 | 50.0 | 63 | 63 |
| WildChat-1M-18 | 2 | 845 | 325 | 1 | 729 | 1159 | 106 | 3072 | 32.2 | 46.1 | 63 | 63 |
| WildChat-1M-18 | 3 | 1668 | 823 | 1 | 1493 | 1117 | 248 | 3324 | 29.5 | 42.1 | 63 | 63 |
| WildChat-1M-23 | 0 | 18 | 18 | 1 | 114 | 158 | 30 | 594 | 31.2 | 57.5 | 63 | 63 |
| WildChat-1M-23 | 1 | 106 | 88 | 1 | 116 | 914 | 45 | 1940 | 30.2 | 57.9 | 63 | 63 |
| WildChat-1M-23 | 2 | 303 | 197 | 1 | 317 | 956 | 70 | 2830 | 31.3 | 52.9 | 63 | 63 |
| WildChat-1M-40 | 0 | 263 | 263 | 1 | 333 | 790 | 81 | 3254 | 29.6 | 55.7 | 63 | 63 |
| WildChat-1M-40 | 1 | 847 | 584 | 1 | 734 | 1154 | 173 | 3369 | 29.3 | 46.8 | 63 | 63 |
| WildChat-1M-40 | 2 | 1472 | 625 | 1 | 1229 | 1198 | 195 | 3210 | 29.3 | 42.5 | 63 | 63 |
| Daring-Anteater-0 | 0 | 30 | 30 | 2 | 112 | 268 | 36 | 841 | 31.1 | 58.6 | 63 | 63 |
| Daring-Anteater-0 | 1 | 547 | 517 | 2 | 518 | 1056 | 145 | 3569 | 33.3 | 50.8 | 63 | 63 |
| Daring-Anteater-0 | 2 | 1532 | 985 | 2 | 1304 | 1175 | 264 | 3736 | 29.9 | 42.7 | 63 | 63 |
| Daring-Anteater-6 | 0 | 268 | 268 | 2 | 330 | 812 | 83 | 3212 | 29.9 | 53.8 | 63 | 63 |
| Daring-Anteater-6 | 1 | 521 | 253 | 2 | 532 | 979 | 82 | 3094 | 28.4 | 50.4 | 63 | 63 |
| Daring-Anteater-6 | 2 | 1186 | 665 | 2 | 1058 | 1121 | 194 | 3424 | 30.1 | 44.1 | 63 | 63 |
| Daring-Anteater-8 | 0 | 59 | 59 | 2 | 113 | 522 | 39 | 1528 | 30.8 | 56.3 | 63 | 63 |
| Daring-Anteater-8 | 1 | 226 | 167 | 2 | 219 | 1032 | 59 | 2809 | 31.0 | 54.4 | 63 | 63 |
| UltraChat-200k-0 | 0 | 133 | 133 | 2 | 212 | 627 | 51 | 2612 | 29.8 | 55.4 | 63 | 63 |
| UltraChat-200k-0 | 1 | 188 | 55 | 2 | 214 | 879 | 40 | 1372 | 29.6 | 56.1 | 63 | 63 |
| UltraChat-200k-0 | 2 | 397 | 209 | 2 | 419 | 947 | 68 | 3073 | 30.0 | 53.2 | 63 | 63 |
| UltraChat-200k-0 | 3 | 501 | 104 | 2 | 419 | 1196 | 50 | 2075 | 30.6 | 51.4 | 63 | 63 |
| UltraChat-200k-2 | 0 | 139 | 139 | 2 | 213 | 653 | 52 | 2676 | 30.9 | 54.9 | 63 | 63 |
| UltraChat-200k-2 | 1 | 1243 | 1104 | 2 | 1070 | 1162 | 286 | 3860 | 30.3 | 44.1 | 63 | 63 |
| UltraChat-200k-2 | 2 | 1781 | 538 | 2 | 1559 | 1142 | 183 | 2934 | 29.3 | 40.3 | 63 | 63 |
| UltraChat-200k-3 | 0 | 162 | 162 | 2 | 212 | 764 | 54 | 2973 | 31.0 | 57.3 | 63 | 63 |
| UltraChat-200k-3 | 1 | 221 | 59 | 2 | 216 | 1023 | 41 | 1439 | 30.1 | 55.7 | 63 | 63 |
| WildChat-1M-18 | 0 | 63 | 63 | 2 | 111 | 568 | 47 | 1349 | 30.3 | 57.1 | 63 | 63 |
| WildChat-1M-18 | 1 | 520 | 457 | 2 | 515 | 1010 | 121 | 3770 | 29.2 | 50.0 | 63 | 63 |
| WildChat-1M-18 | 2 | 845 | 325 | 2 | 725 | 1166 | 106 | 3072 | 31.6 | 46.1 | 63 | 63 |
| WildChat-1M-18 | 3 | 1668 | 823 | 2 | 1560 | 1069 | 248 | 3324 | 29.4 | 42.1 | 63 | 63 |
| WildChat-1M-23 | 0 | 18 | 18 | 2 | 114 | 158 | 30 | 594 | 30.6 | 57.5 | 63 | 63 |
| WildChat-1M-23 | 1 | 106 | 88 | 2 | 113 | 938 | 45 | 1940 | 30.6 | 57.9 | 63 | 63 |
| WildChat-1M-23 | 2 | 303 | 197 | 2 | 320 | 947 | 70 | 2830 | 31.0 | 52.9 | 63 | 63 |
| WildChat-1M-40 | 0 | 263 | 263 | 2 | 313 | 840 | 81 | 3254 | 32.5 | 55.7 | 63 | 63 |
| WildChat-1M-40 | 1 | 847 | 584 | 2 | 730 | 1160 | 173 | 3369 | 30.2 | 46.8 | 63 | 63 |
| WildChat-1M-40 | 2 | 1472 | 625 | 2 | 1277 | 1153 | 195 | 3210 | 30.0 | 42.5 | 63 | 63 |

rep 1: 27 matched turns. Decode, pooled: ExecuTorch 30.3 tok/s, llama.cpp 50.7 tok/s.
  First turns only (both read the same tokens, 9 turns): prefill ExecuTorch 644 tok/s, llama.cpp 2400 tok/s.
  Median time to first token over matched turns: ExecuTorch 333 ms (whole history), llama.cpp 70 ms (new tokens only).
rep 2: 27 matched turns. Decode, pooled: ExecuTorch 30.4 tok/s, llama.cpp 50.7 tok/s.
  First turns only (both read the same tokens, 9 turns): prefill ExecuTorch 656 tok/s, llama.cpp 2400 tok/s.
  Median time to first token over matched turns: ExecuTorch 330 ms (whole history), llama.cpp 70 ms (new tokens only).
