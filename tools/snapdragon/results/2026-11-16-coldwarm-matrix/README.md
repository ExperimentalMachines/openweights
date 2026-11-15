# The same prompt read with a retained prefix and with a reset cache, across chips, 2026-11-16

Follows `../2026-11-15-coldwarm-poco/`, which measured the POCO plus sampled runs on two Samsung
phones. This folder makes the comparison greedy and order-balanced on four more chips, adds the
CPU, GPU and NPU builds of one model, and records what text a reply ended on.

Every ratio is the reset-cache arm's time over the retained-prefix arm's, per pair, reported as
the median and range over pairs; where some pairs missed reuse, the ratio over the hits and the
ratio over all pairs are given side by side. Token counts are the runtime's own positions.

## ExecuTorch: the second turn, greedy, order alternated

`ExecuTorchOnDeviceTest#theSecondTurnReadColdAndWarm` with `-e temperature 0` (a greedy engine;
the per-turn `SamplerParams` temperature does not reach this engine), 12 pairs, odd pairs with the
reset-cache arm first and turn one written again. ExecuTorch 1.6.0-openweights.2, XNNPACK. Firebase
Test Lab, Android 16, screen on in every row, cooling gate at 45 C capped at 60 s.

Phones: Galaxy S24 (`SC-51E`, Snapdragon 8 Gen 3), Galaxy S25 Ultra (`pa3q`, Snapdragon 8 Elite),
Pixel 9 (`tokay`, Tensor G4), Galaxy Tab S10+ (`gts10pwifi`, MediaTek Dimensity 9300+).

### LFM2.5-1.2B 8da4w-gptq-4k (the app's default download), 1,003 tokens

| Phone | Retained prefix: 20 tokens, median | Reset cache: 1,003 tokens, median | Ratio, median [range] | By order (reset first, retained first) | Replies identical |
|---|---|---|---|---|---|
| Galaxy S24 | **112 ms** [92-127] | **3,884 ms** [2,869-4,033] | **33.0x** [23.5-38.1] | 33.7x, 32.8x | 12 of 12 |
| Galaxy S25 Ultra | **70 ms** [67-177] | **2,842 ms** [2,751-5,247] | **40.2x** [29.6-48.9] | 40.0x, 40.3x | 12 of 12 |
| Pixel 9 | **276 ms** [221-316] | **8,691 ms** [7,826-9,144] | **30.0x** [27.1-39.4] | 31.6x, 29.4x | 12 of 12 |
| Galaxy Tab S10+ (MediaTek) | **80 ms** [75-92] | **3,299 ms** [3,189-3,739] | **40.9x** [37.7-44.8] | 40.9x, 41.4x | 12 of 12 |

All 48 pairs reused the prefix; turn one came out identical every time it was written again.

### Qwen3-1.7B INT8-INT4 (the 1.4.0 export behind the talk's first figure), 1,307 tokens

| Phone | Retained prefix: 20 tokens, median | Reset cache: 1,307 tokens, median | Ratio, median [range] | By order (reset first, retained first) | Replies identical |
|---|---|---|---|---|---|
| Galaxy S24 | **247 ms** [223-271] | **9,403 ms** [7,714-10,684] | **37.1x** [31.6-43.1] | 37.1x, 37.0x | 0 of 12 |
| Galaxy S25 Ultra | **179 ms** [163-222] | **10,089 ms** [8,647-11,165] | **54.5x** [40.0-65.3] | 58.0x, 50.3x | 0 of 12 |
| Pixel 9 | **556 ms** [415-704] | **15,634 ms** [14,353-19,367] | **28.3x** [20.7-34.6] | 29.1x, 27.9x | 0 of 12 |
| Galaxy Tab S10+ (MediaTek) | **176 ms** [172-195] | **8,221 ms** [8,016-9,866] | **46.6x** [42.1-53.9] | 45.0x, 47.2x | 0 of 12 |

All 48 pairs reused the prefix (greedy turn one always ended on `<|im_end|>`, so the sampled
runs' end-marker miss did not occur). Both arms reached position 1,307 in every pair: the retained
prefix read 20 new tokens to get there, the reset cache all 1,307.

**Reuse changed Qwen3's greedy reply, the same way on all four chips.** The timed turns stop at 48
tokens, so "reply" here means those first 48 tokens, still inside the reasoning. Within each run
the retained-prefix reply was identical in all 12 pairs and so was the reset-cache reply, and the
two replies are the same on all four phones; they part at character 75 (counting from 0): the retained prefix wrote
`... describe?" and they provided ...`, the reset cache `... describe?" They provided ...`. LFM2.5's
captured replies (`Privacy-focused`, 5 tokens, under the 48-token cap) were identical in all 48
pairs. Equal token counts do not prove identical token ids; the
retained prefix holds turn one as generated, one token per step, and the reset cache reads the same
text in batched prefill.

Order made little difference: the two order groups' medians differ by 0.1x to 7.7x (at most 15%,
Qwen3 on the S25 Ultra), in both directions.

Limits: the Pixel 9's LFM2.5 run never cooled under 45 C (53 to 74 C at every timed turn, gate
capped at 60 s); the other runs started at 39.3 to 44.9 C. Each row of a run is the same phone; a
Test Lab run lands on whichever unit of that model is free. Decode medians were within 5% between
the arms except Qwen3 on the S25 Ultra (retained prefix 20.2 tok/s, reset cache 17.0) and LFM2.5
on the Pixel 9 (23.7 and 20.8).

### One model on the CPU, the GPU and the NPU of the same phone

Qwen3-1.7B from `experimentalmachines/Qwen3-1.7B-ExecuTorch`, 4k window: `xnnpack/` 8da4w-gptq (CPU),
`vulkan/` 8da4w-gptq (GPU), `qnn/sm8750/` hybrid (Hexagon NPU), greedy, order alternated. Each
backend writes its own greedy turn one (579, 662 and 423 tokens on the S25 Ultra), so the prompt
lengths differ between backends; compare within a row.

| Phone | Backend | Pairs, reuse | Retained prefix, median | Reset cache, median | Ratio |
|---|---|---|---|---|---|
| Galaxy S25 Ultra | CPU (XNNPACK) | 8, 8 | **171 ms** [163-188] | **9,304 ms** [8,775-10,272], 1,501 tokens | **54.9x** [47.1-60.9] |
| Galaxy S25 Ultra | GPU (Vulkan, Adreno) | 8, 8 | **116 ms** [113-271] | **3,372 ms** [3,360-3,443], 1,584 tokens | **29.2x** [12.4-30.1] |
| Galaxy S25 Ultra | NPU (QNN, Hexagon) | 12, **0** | none (both arms read everything: 870.5 ms [812-916] labelled warm) | **872.5 ms** [805-901], 1,346 tokens (the runtime's count) | none |
| Galaxy S24 | CPU (XNNPACK) | 8, 8 | **270 ms** [231-351] | **10,220 ms** [8,870-10,480], 1,501 tokens | **36.4x** [26.6-44.9] |
| Galaxy S24 | GPU (Vulkan, Adreno) | 8, 8 | **198 ms** [178-376] | **4,958 ms** [4,928-5,219], 1,584 tokens | **25.1x** [13.1-28.0] |

- **The NPU export opened and ran in the app's engine on a retail phone** (the S25 Ultra's
  Snapdragon 8 Elite is the `sm8750` the export targets). Its full read took 872.5 ms for 1,346
  tokens against the CPU export's 9,304 ms for 1,501: 9.6 times the CPU's tokens per second in
  these runs, with different exports and quantization, so not a matched processor comparison.
  **There was no reuse in these 12 greedy pairs**: every turn one carried the runtime's
  end-of-sequence log line, the callback's last fragment was `"!"` with no end marker after it,
  and the engine dropped its record, so the second turn re-read everything. The CPU's retained
  prefix (171 ms for 20 new tokens) was 5.1 times faster than the NPU's full read (1,346 tokens),
  unequal work by design. The run fails its own "at least one warm hit" assertion; its rows are
  the negative result. QNN needs the libraries extracted at install (`-Pow.testJniOnDisk=true`, see
  `core/engine/build.gradle.kts`) and `libcdsprpc.so` named in the test manifest.
- Greedy replies differed between the arms on the CPU and the GPU in every pair (Qwen3, as
  above); the NPU's two arms both read cold and answered identically.

### The end marker that never arrives

The test now wraps the runtime bridge in `RecordingBridge`, which keeps the last raw fragments
the runtime's callback delivered for turn one. Sampled Qwen3-1.7B INT8-INT4 (the app's sampling),
15 pairs on each of three phones:

| Phone | Completed pairs | Turn ones with the runtime's end-of-sequence line | Missed reuse | Ratio, hits only | Ratio, all completed pairs |
|---|---|---|---|---|---|
| Galaxy S24 | 15 of 16 | 4 | the same 4 | 43.6x [32.8-52.5] | 40.3x |
| Galaxy S25 Ultra | 15 of 16 | 6 | the same 6 | 71.8x [58.5-87.6] | 61.7x |
| Galaxy Tab S10+ (MediaTek) | 15 of 16 | 6 | the same 6 | 50.6x [39.6-59.0] | 41.2x |

One attempt per phone was skipped because turn one left no room for turn two (on the S24 and S25
Ultra turn one ran to its 1,145-token allowance, ending on `" resource"` and `".g"`; on the tablet
it ended on `<|im_end|>` too late). All sampled pairs ran warm first.

All 16 turn ones that carried the runtime's "Reached to the end of generation" line ended with
`"!"` as the last fragment the callback received (for example `[" guidance", " on", " specific",
" technical", " challenges", "!"]`), with nothing after it; all 29 other completed turn ones ended
with `"<|im_end|>"` delivered as a fragment of its own (`[..., "?", "<|im_end|>"]`).
`RecordingBridge` records each fragment as the Java callback receives it, before the engine
holds anything back; it cannot see token ids or anything dropped before the Java callback. ExecuTorch 1.6.0's token loop hands each token to the callback before its
end-of-sequence check, so the token that ended these turns reached the engine as no text, or not
at all; which token it was is still not recorded. A reply ending in "!" does not by itself cause
it: the 8da4w-gptq CPU export's greedy turn one also ended "...further details!" and was stopped by
the engine on `<|im_end|>` in all 8 pairs.

## llama.cpp: a fresh chat over a restored head

`WarmPrefixEval#aFreshChatReadColdAndRestored`, llama.cpp v0.8.0, 6 pairs, order alternated,
greedy, time to first token. Restored: warm the head, hold a two-turn conversation, open a new
chat (hybrids restore the head's snapshot; Qwen3 rolls back to the shared prefix). Reset cache: an
engine that never warmed.

| Phone | Model, path | Restored: read, median | Reset cache: read, median | Ratio | Replies identical |
|---|---|---|---|---|---|
| Galaxy S25 Ultra | LFM3-1.7B Q4_K_M, snapshot 15,415 KiB | 19 (1,018 restored), **196 ms** [183-1,889] | 1,037, **6,238 ms** | **33.1x** [3.2-46.6] | 6 of 6 |
| Galaxy S25 Ultra | LFM2.5-2.6B Q4_K_M, snapshot 16,652 KiB | 19 (1,018 restored), **424 ms** [333-484] | 1,037, **12,058 ms** | **30.8x** [25.4-33.5] | 6 of 6 |
| Galaxy S25 Ultra | Qwen3-1.7B Q8_0, rollback | 13 (1,052 kept), **198 ms** [190-213] | 1,064, **4,146 ms** | **21.1x** [19.0-22.0] | **0 of 6** |
| Galaxy S24 | Qwen3-1.7B Q8_0, rollback | 13 (1,052 kept), **202 ms** [188-417] | 1,064, **6,408 ms** | **31.7x** [15.0-33.8] | **0 of 6** |
| Galaxy Tab S10+ (MediaTek) | LFM2.5-1.2B QAD-Q4_0, snapshot 12,388 KiB | 18 (1,018 restored), 5,940 ms | 1,036, 21,144 ms | 3.5x, see below | 6 of 6 |
| Galaxy Tab S10+ (MediaTek) | Qwen3-1.7B Q8_0, rollback | 13 (1,052 kept), 11,409 ms | 1,064, 43,660 ms | 3.8x, see below | 6 of 6 |
| Galaxy S25 Ultra | gemma-3-1b-it | no warmable head (a one-token head): skipped by the test | | | |

- **After a rollback, Qwen3's greedy reply changed**, the same way on both phones and in every
  pair: the rolled-back cache reasoned first (`<think>\nOkay, the user is asking for the capital
  of France ... </think>\n\nParis`), the reset cache answered with an empty think block
  (`<think>\n</think>\n\nParis`). Both answers are Paris; the texts part at character 8, after
  `<think>\n`. No token-id trace was kept. On the Galaxy Tab S10+, where llama.cpp runs the
  `android_armv9.0_1` CPU variant instead of the Snapdragons' `android_armv8.6_1`, both arms
  reasoned and the replies matched in all 6 pairs. The reset-cache reply differed between the
  tablet and the phones. This is consistent with numerical sensitivity, but these runs neither
  establish a near tie nor rule out a rollback defect. The rollback kept 1,052 tokens: the 1,047-token head
  and 5 more from the start of the earlier conversation's user turn (role opener and the `What is`
  both questions share; the logs do not say which). Whether this is a near
  tie flipped by the earlier batch layout or a defect in rollback is not established. The
  hybrids' snapshot restore answered identically to the reset cache in every pair (LFM3,
  LFM2.5-2.6B here, LFM2.5-1.2B on three phones yesterday).
- **On the Tab S10+ the restore worked (1,018 tokens restored, 18 read) but llama.cpp's timing
  there is anomalous.** It chose the `android_armv9.0_1` CPU variant with 8 worker threads, and
  calls carried a floor of about 6 s: 6,123 ms for 165 tokens and 6,151 ms for 290, then 13,051,
  21,356 and 34,875 ms. Its ExecuTorch runs on the same tablet were normal. Cause not
  investigated; its 3.5x and 3.8x are not comparable with the other rows.

## Cold prefill against prompt length

`ExecuTorchOnDeviceTest#coldPrefillByLength` (LFM2.5-1.2B 8da4w-gptq-4k, one user turn of 10 to 84
sentences) and `WarmPrefixEval#coldPrefillByLength` (LFM2.5-1.2B QAD-Q4_0, a system text of 5 to
80 rules), three rounds, order reversed every other round, reset cache before each, median:

| Phone | Runtime | Tokens: prefill |
|---|---|---|
| Galaxy S24 | ExecuTorch | 245: 872 ms; 487: 1,705 ms; 949: 3,405 ms; 1,873: 7,228 ms (281 to 259 tok/s) |
| Galaxy S24 | llama.cpp | 165: 717 ms; 290: 1,267 ms; 540: 2,605 ms; 1,040: 5,412 ms; 2,040: 10,583 ms (230 to 193 tok/s) |
| Galaxy Tab S10+ | ExecuTorch | 245: 944 ms; 487: 1,886 ms; 949: 3,468 ms; 1,873: 7,454 ms (about 250 to 275 tok/s) |
| Galaxy Tab S10+ | llama.cpp | 165 and 290: about 6.1 s; 2,040: 34,875 ms (the anomaly above) |

On the S24 the cold read grows close to linearly with length: time per token rose about 8%
(ExecuTorch) and 19% (llama.cpp) from the shortest prompt to the longest. These sweeps measure
only cold reads.

## Not measured, and what failed

- **The POCO** dropped off USB during a greedy Qwen3 run; its rows stayed on the phone and were
  not pulled. Nothing from the POCO is in this folder. The MediaTek NPU path (NeuroPilot) was not
  run: the session's notes put its need at about 5.2 GB available, more than the phone showed
  before the run (not kept here).
- **QNN sampled** and the QNN 0.6B were not run.

Files: `<device>/<run>.*` per Test Lab phone (`SC-51E`, `pa3q`, `tokay`, `gts10pwifi`): rows
(`.coldwarm.jsonl`, `.restore.jsonl`, `.prefill.jsonl`) and the test's and runtimes' log lines
(never the whole logcat). `ftl.sh` ran them (ACCOUNT, PROJECT, BUCKET, RAW, APK from the
environment); `poco_et.sh`, `poco_restore.sh` and `poco_llama_prefill.sh` are the USB equivalents;
`summarise.py` and `report_restore.py` print the tables. The test APK was built with
`-Pow.testJniOnDisk=true` (then presence alone; now it must be `true`).
