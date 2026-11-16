# Why Qwen3's greedy reply differs between a retained prefix and a reset cache, 2026-11-16

Follows `../README.md`: on four phones, Qwen3-1.7B INT8-INT4 (XNNPACK) wrote the same 48-token
second turn differently warm and cold, `... describe?" and they provided ...` against
`... describe?" They provided ...`, parting at reply token 20 (character 75). Two causes were
possible: the arms fed different token ids (H1), or the same ids with different arithmetic
(H2). Everything here ran on the Mac, with no phone.

**Answer: on the Mac, H2 is a demonstrated mechanism; that it caused the phones' divergence is
likely but not proved, and H1 is very unlikely but not excluded by observation.** In the Mac
reconstruction both arms are fed the same 1,307 ids; the phones' generated ids were not logged,
and the Mac does not reproduce the phones' warm and cold divergence (its C2 arm does write the
phones' warm reply).
What differs is how those ids were grouped into forward calls: the retained prefix computed
turn one's reply one token per step during decode, the reset cache computed the same ids in
batched prefill pieces, and on this export the logits depend on that forward-call schedule,
directly and through the KV entries computed earlier. On the Mac, with the ids identical by
construction, the app's warm and cold arms write different greedy replies every time.

## What was run

- Model: `larryliu0820/Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK` revision `50d9ad69`,
  `model.pte` sha256 `074d87c3...`, its `tokenizer.json` (`~/ow-models/et/`). The export's
  `forward` takes tokens `[1, n]` and cache positions `[n]` and returns logits for all `n`
  positions (window 2048), so every arm gives a logit row per position.
- Runtime on the Mac: ExecuTorch 1.4.0 pybindings (pip wheel, XNNPACK), torch 2.14.0,
  tokenizers 0.23.2, pytorch_tokenizers 1.4.1 (the C++ HFTokenizer the AAR links), Apple M5,
  `~/ow-models/etexport/.venv`. The phones ran ExecuTorch 1.6.0-openweights.2.
- The second turn exactly as the app sends it: `prompts.py` transcribes the test's prompt,
  `Qwen3Prompt.render(verbatimHistory = true)` and `ExecuTorchEngine.feedText`'s piece cutting
  (800-character prefill pieces, the last 1,600 characters to generate). Its rebuilt record
  matches the phone's (`extends, record 6424 of 6523 chars`).
- `tokcheck.py`: tokenizer only. `arms.py`: the arms below. `followup.py`: the phone's parting
  token and the whole distribution. `h1exact.py`: every other encoding of turn one's reply
  with the phone's token count, scored on the model. Raw outputs: the matching `.json` files;
  `arms-t4.json` is `arms.py` at 4 threads (`arms-t10.json` at 10).

## H1, token ids: very unlikely, not excluded by observation

| Check | Result |
|---|---|
| Runtime C++ tokenizer against Hugging Face, on prompt one, reply, tail, prompt two | identical ids on all four |
| Prompt two in the app's 9 cold pieces, against one encode of the whole, against encode(head) + encode(tail) | identical, 1,307 ids |
| Positions: prompt one + reply + tail | 903 + 384 + 20 = **1,307**, the phones' runtime position in both arms |
| Phone turn one: generated tokens | 385 = canonical 384 + `<\|im_end\|>`; its last six fragments equal the canonical encoding's |
| Encodings of the reply text with exactly 384 ids, other than the canonical one | 3 places only (`/G`+`PU` vs `/`+`GPU` twice, ` (`+`e` vs ` `+`(e`); no shorter encoding exists |
| Those alternatives on the model, teacher-forced along the phone's reply one token per step | first id **8.7, 16.8 and 22.7 logits** below the canonical id (11.2, 18.5, 48.9 nats over the span) |
| Teacher-forced along the canonical encoding of the phone's reply text: how far each token sits below the Mac's top choice | at most **3.24 logits** (30 of 385 steps differ at all); this assumes the phone's ids were canonical |
| The Mac's own greedy turn one (631 tokens) | every id equals the canonical encoding of its text |

The phone's generated ids were never logged, so H1 cannot be excluded by observation. For it
to hold, the phone would have had to choose, at one of the three sites, an id the Mac ranks 8.7
logits or more below the canonical one, while the canonical reading of its text never sits more
than 3.24 below the Mac's top. The 3.24 figure assumes the canonical ids, so it is a
consistency check that makes H1 unlikely, not a bound. The three sites combine into 8
encodings with 384 ids: the canonical one and seven alternatives.

## H2, same ids, different arithmetic: shown on the Mac

Every arm feeds the identical 1,307 ids (`ids_equal_W` is true for all), greedy, 48 tokens.
Rows bitwise equal: how many of the 1,307 prompt positions give exactly W's logits.

| Arm | How the same ids were fed | Rows bitwise equal to W | Timed reply parts from W at token | Equals phone warm / cold reply for |
|---|---|---|---|---|
| **W** (app warm) | prompt one in the app's 6 forwards, reply 384 one per step, tail 20 in one forward | 1,307 | | 31 / 20 tokens |
| **C** (app cold) | the app's 9 pieces: 8 prefill forwards and the generate tail | 670 (the 648 fed in identical forwards, the next 16, 6 scattered) | **31** (` are` W, ` run` C) | 45 / 20 tokens |
| C2 | the whole 1,307 in one forward | 160 | 31 | all 48 / 20 tokens |
| Rb | W with only the reply in one forward instead of 384 steps | 904 | 8 | 8 / 8 |
| St | W with only the 20-token tail one per step | 1,287 | 46 | 31 / 20 |
| S | every id one per step | 9 | 2 (closes thinking) | 2 / 2 |
| W2, C again | W and C repeated | W2: 1,307; C again: 670, as C | W2: none; C again: 31, as C | as W, as C |

- Deterministic: W repeated is bitwise identical to W, C repeated gives C's reply and C's
  distances to W, and the whole table is identical at 4 and 10 threads (every field of
  `arms-t4.json` and `arms-t10.json` agrees except wall time). The difference comes
  from the forward shape, not from run-to-run noise or the thread count.
- Changing only how the reply's 384 positions were grouped (Rb), or only the 20-token tail
  (St), changes the greedy reply. Rows matched W bitwise mostly where both arms computed them
  in identical forwards (C: the 648 fed in identical forwards); C2 still matched its first 160
  rows inside one differently shaped forward, so the shape alone does not decide every row.
- Small in distribution, enough at a near tie. At the last prompt position, C against W:
  KL 0.00037 nats, `<think>` at 0.9979 (W) against 0.9976 (C), top logit moved by 0.31 (largest gap
  anywhere in the vocabulary 3.37). Argmax differs at 29 of the 1,307 prompt positions.
- At the phones' parting token (both arms forced along the phones' shared 20-token prefix),
  ` and` leads ` They` by **2.01 logits warm, 0.84 cold** (C2: 1.82): on the Mac the cold
  arm's arithmetic moves this decision toward ` They`, the side the phones' cold arm landed
  on, without crossing. On the Mac the arms part later, at token 31, where W's margin is 0.16.

## Limits

- The Mac's XNNPACK arithmetic is not the phones'. Its greedy turn one leaves the phone's at
  token 4 (` the` over ` let` by 0.022 logits) and no Mac arm writes ` They` at token 20. No
  Mac arm reproduces the phones' warm and cold pair (C2's reply happens to equal the phones'
  warm reply). The Mac shows the mechanism; it does not reproduce the phones' flip.
- One module is loaded once and the arms run in a fixed order. Each arm restarts positions at
  0, which relies on the export masking future positions and overwriting cache entries; that
  was not checked separately, and fresh-module and reordered-arm controls were not run.
- Arm S ran 48 steps with no stop at `<|im_end|>`, so its text past that marker is a harness
  artifact, not a sign of a broken stepping path.
- Which kernel makes a row depend on its forward's shape (GEMM tiling, the custom SDPA's
  blocking, dynamic int8 activation quantization turning last-bit differences into whole
  quantization steps) was not isolated, and no unquantized model was run.
- Runtime versions differ: 1.4.0 pybindings here, 1.6.0-openweights.2 on the phones.
- The H1 bound uses the Mac's logits to judge the phone's decisions; the 3.2-logit figure is
  the largest disagreement observed between the two over turn one.

## For the speaker notes

In the Mac reconstruction, both arms were fed the same 1,307 token ids; the phones' generated
ids were not logged. The retained cache had computed turn one's
reply one token at a time, the reset cache the whole conversation in batched pieces. On the
Mac, the same ids fed those two ways gave different greedy replies every run, and the batched
arithmetic narrowed the deciding margin at the phones' parting word from 2.0 to 0.8 logits
without crossing it: consistent with the phones' flip, which the Mac does not reproduce.
Reuse is 28.3 to 54.5 times faster (the four phones' medians) but not bit-identical to a fresh read.
