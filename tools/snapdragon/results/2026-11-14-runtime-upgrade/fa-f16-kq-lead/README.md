# A lead for llama.cpp v1.0.0's decode at depth, not shipped

`fa-f16-kq.patch` (against b1b770d9) makes flash attention's per-query CPU kernel read F16 keys with
an F16 dot on CPUs with native F16 arithmetic, as v0.8.0 did, and leaves V accumulating in F32
(upstream #29810, the overflow fix). Upstream's `test-backend-ops -o FLASH_ATTN_EXT` against Metal
passes 5,353 of 5,353 cases with it, as stock v1.0.0 does.

`mac-3way-decode.jsonl`: llama-bench on the Mac (M5, native build, 4 threads, flash attention on, 64
tokens), v0.8.0, stock v1.0.0 and patched v1.0.0 in rotating order, six rounds. Decode relative to
v0.8.0, medians:

| Model, context | stock v1.0.0 | patched |
|---|---|---|
| LFM2.5 1.2B QAD-Q4_0, 2,048 | 0.914 | 0.956 |
| LFM2.5 1.2B QAD-Q4_0, 4,096 | 0.904 | 0.942 |
| Qwen3 1.7B Q8_0, 2,048 | 0.824 | 0.873 |
| Qwen3 1.7B Q8_0, 4,096 | 0.780 | 0.842 |

It recovers a quarter to a half of the loss; the rest is V's F32 accumulation, read and written for
every key, which a register-tiled decode kernel would be needed to win back. Not run on a phone.
