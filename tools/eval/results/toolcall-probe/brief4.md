# Final QA of one evening's iteration (no tools; everything is here; do not restate)

Same probe as before: greedy first-token probability of the tool-call token for
LFM2.5-1.2B on the phone's own prompt bytes; 16 rows (9 with the app's "look it up"
note) plus 24 fresh rows (7 noted) drawn by seed. ExecuTorch 1.4.0 XNNPACK, 2k exports
from the original bf16 checkpoint; int8 means int8 per-channel weights with int8
dynamic activations (qd8 x qc8w), int4 means groups of 32 with int8 dynamic activations.
Your previous round's objections were taken as follows: the attention-only result was
replicated on the 24 fresh rows (below); no export makes a false call on the 17 unnoted
rows (max tool probability 0.04); top-1 mass on unnoted rows is 0.71 for int4-attention
vs 0.79 for the fp32 export (not collapsed); the fp32 export with the portable SDPA is
identical to three decimals to the custom-SDPA one, so the fp32 export's gap to the
reference is not the SDPA op; an fp32-weight int8-activation export does not exist on
XNNPACK (dynamic int8 activations only exist in front of quantised weights).

## Second split (attention kept at int4; one more group at int4; rest int8)
| Export | Hanover | Rome | Columbia | Jerusalem | Canada's most  | author: Empire | author: Memory | author: Respon | author: Skyscr | calls | size MB |
|---|---|---|---|---|---|---|---|---|---|---|---|
| GGUF Q4_K_M | 0.84 | 0.87 | 0.79 | 0.66 | 0.82 | 0.85 | 0.74 | 0.83 | 0.77 | 9 of 9 | 697 |
| fp32 .pte | 0.85 | 0.49 | 0.70 | 0.78 | 0.44 | 0.69 | 0.23 | 0.59 | 0.62 | 6 of 9 | 4978 |
| all int8 | 0.64 | 0.28 | 0.88 | 0.80 | 0.49 | 0.72 | 0.14 | 0.44 | 0.61 | 5 of 9 | 1248 |
| int4 attention only | 0.87 | 0.91 | 0.77 | 0.80 | 0.82 | 0.89 | 0.58 | 0.72 | 0.49 | 8 of 9 | 1222 |
| int4 attention + w1,w3 | 0.58 | 0.50 | 0.56 | 0.04 | 0.44 | 0.51 | 0.14 | 0.28 | 0.33 | 3 of 9 | 997 |
| int4 attention + w2 | 0.32 | 0.63 | 0.53 | 0.51 | 0.22 | 0.25 | 0.51 | 0.53 | 0.47 | 5 of 9 | 1110 |
| int4 attention + conv in_proj | 0.86 | 0.87 | 0.69 | 0.43 | 0.82 | 0.74 | 0.27 | 0.77 | 0.73 | 7 of 9 | 1169 |
| int4 attention + conv out_proj | 0.63 | 0.93 | 0.44 | 0.53 | 0.83 | 0.70 | 0.38 | 0.13 | 0.45 | 5 of 9 | 1204 |
| int4 feed-forward only | 0.10 | 0.27 | 0.04 | 0.06 | 0.05 | 0.36 | 0.17 | 0.12 | 0.38 | 0 of 9 | 911 |
| int4 conv only | 0.13 | 0.48 | 0.20 | 0.44 | 0.20 | 0.16 | 0.07 | 0.11 | 0.08 | 0 of 9 | 1178 |
| 8da4w shipped | 0.00 | 0.01 | 0.00 | 0.01 | 0.01 | 0.04 | 0.05 | 0.09 | 0.05 | 0 of 9 | 789 |

## Fresh rows (24, seven noted)
| Export | producer of Black and  | director of The Last W | producer of Parker? | screenwriter for Open  | screenwriter for Last  | screenwriter for Bleak | calls (7 noted) | max p on 17 unnoted | false calls |
|---|---|---|---|---|---|---|---|---|---|
| GGUF Q4_K_M | 0.70 | 0.75 | 0.68 | 0.70 | 0.74 | 0.81 | 6 | 0.38 | 0 |
| fp32 reference | 0.66 | 0.69 | 0.58 | 0.60 | 0.67 | 0.76 | 6 | 0.12 | 0 |
| fp32 .pte | 0.45 | 0.56 | 0.40 | 0.46 | 0.51 | 0.57 | 3 | 0.05 | 0 |
| 8da4w shipped | 0.02 | 0.02 | 0.04 | 0.05 | 0.05 | 0.05 | 0 | 0.03 | 0 |
| all int8 | 0.26 | 0.35 | 0.54 | 0.68 | 0.45 | 0.51 | 3 | 0.03 | 0 |
| int4 attention only | 0.67 | 0.76 | 0.52 | 0.63 | 0.64 | 0.70 | 6 | 0.04 | 0 |

## Conclusions drawn
C1. The loss is concentrated: int4 on the feed-forward (either its gate/up or its down
    projection) is what kills the call; int4 on attention is harmless; int4 on the conv
    input projections costs one row.
C2. No int4-dominant recipe calls tools; every calling recipe keeps the feed-forward at
    int8, which is two thirds of the weights, so those files are 1.1 to 1.2 GB vs the
    GGUF's 697 MB and the shipped export's 789 MB.
C3. The best recipe (attention int4, rest int8, 1222 MB) matches the GGUF on 33 of 33
    rows measured. Whether a file 1.75x the GGUF is worth its prefill speed is a product
    decision to be made after a phone run (speed, memory, 160-row suite, held-out draw)
    with thresholds set first.
C4. The only route to a smaller file that still calls is QAT on this mixed layout so
    the feed-forward survives int4; it needs a GPU.
C5. The fp32 ExecuTorch export's own gap to the reference (e.g. Rome 0.49 vs 0.94) is
    real, is not the SDPA op, and is unexplained; it caps any recipe.

Give a verdict (SUPPORTED / NOT SUPPORTED / UNCERTAIN) for C1 to C5 with the strongest
objection and the cheapest settling experiment, then say whether the evening's method
(regex-gated per-family quantisation hooks in the venv, 16 + 24 rows, one Mac) is fit
to steer a product decision, and what you would run first tomorrow.
