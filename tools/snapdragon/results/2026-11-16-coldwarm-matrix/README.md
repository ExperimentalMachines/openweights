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
holds anything back; it cannot see token ids or anything dropped before the Java callback.
ExecuTorch 1.6.0's token loop hands each token to the callback before its end-of-sequence check,
so the `"!"` fragment is the token that ended these turns: for this export the runtime's end token
is token 0, `"!"` (traced on 2026-11-16 from the runner and tokenizer sources, not from a token-id
trace on a phone; see "Why Qwen3's replies ended on "!"" below). A reply ending in "!" does not by itself cause
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
| POCO X8 Pro Max (MediaTek), 16 Nov | Qwen3-1.7B Q8_0, rollback | 13 (1,052 kept), 906 ms [156-15,703] | 1,064, 14,532 ms | hot, see below | **0 of 6** |
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
  tie flipped by the earlier batch layout or a defect in rollback is not established.
- **The POCO splits a third way** (run alone on 16 Nov, `poco/restore-Qwen3-1.7B-Q8_0.*`). It
  uses the same `android_armv9.0_1` variant as the Tab S10+, and its reset-cache reply is the
  tablet's, character for character; but its rolled-back reply differs from it in all 6 pairs,
  both reasoning, parting at character 195 (`more commonly referred to as` against `commonly
  mistaken for`). So the reply changed after a rollback on three of four chips, and the CPU
  variant alone does not decide it. Its first attempt, which overlapped another app's work, gave
  the same two replies. Its timings are shown for completeness and left out of every performance
conclusion: the warm-up conversation heated the phone, one
  rolled-back turn started at 69 C and took 15.7 s, and the per-pair ratios run from 1.0x to 97.5x.
- The hybrids' snapshot restore answered identically to the reset cache in every pair (LFM3,
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

## The POCO and MediaTek's NPU (2026-11-16, daytime)

The POCO X8 Pro Max (MediaTek Dimensity 9500s, MT6991, Android 16, HyperOS) dropped off USB
overnight and its rows were lost. These runs were made the next day on the owner's phone, on the
charger, screen on in every row, with the same test and the same cooling gate (45 C, capped at
60 s). USB dropped three times during the day: a few seconds into the first test, in the last minute
of the first full run, and 45 s into its repeat. From the second drop on, `poco_et.sh`, `poco_restore.sh` and
`poco_llama_prefill.sh` start each test in its own session on the phone and write its log there
(`poco_lib.sh`), so a dropped transport no longer ends a test, and they carry on over wireless
debugging (`WIFI=<ip:port>`) while USB is down. The run that died in its last minute was
repeated in full; its first three pairs, read off the phone before the repeat, gave the same
replies.

From 11:22:47 another project's benchmarks (ExecuPhone, SmolVLM2 and Gemma 3) ran on the same
phone at the same time. Every row here is from before that, except where a row says otherwise;
the runs that overlapped it (llama.cpp's Qwen3 rollback, a sampled Qwen3 run) are left out and
listed under "Not measured".

### Qwen3-1.7B INT8-INT4, greedy, the fifth chip

| Phone | Retained prefix: 20 tokens, median | Reset cache: 1,307 tokens, median | Ratio, median [range] | By order (reset first, retained first) | Replies identical |
|---|---|---|---|---|---|
| POCO X8 Pro Max (MediaTek MT6991) | **214 ms** [195-243] | **9,544 ms** [8,485-14,669] | **44.8x** [41.5-60.4] | 46.8x, 42.7x | 0 of 8 |

All 8 pairs reused the prefix and reached position 1,307 in both arms. The retained-prefix reply
and the reset-cache reply are each byte-identical to the other four chips' (one distinct reply
per arm across all five phones), parting at character 75 as above. This run followed the aborted
one with no rest, and the phone started its turns at 44.7 to 57.1 C; the reset-cache read took
8.5 s at 44.7 C and 14.7 s at 55.0 C. In the four pairs whose turns all started at or under 47.4 C
the ratio was 41.5x to 46.2x.

### LFM2.5-1.2B on MediaTek's NPU, and on the CPU with the same export

`-e bridge mtk-npu` runs the same test through `DisaggregatedExecuTorchBridge`: MediaTek's NPU
(NeuroPilot) reads every prompt and the CPU export writes the reply, with the state handed across
after each read. The NPU half is the a16w8 2k export staged under
`/data/local/tmp/mtk_models/0pd-g8-2048/mtk/mt6991/` (4 chunks, batch 128); the CPU half is
`LFM2.5-1.2B-Instruct-8da4w-2k.pte`. Every row records `on_npu`, and the test fails if the bridge
fell back to the CPU. The bridge takes the first staged directory with chunks, not one bound to
the CPU export; the load lines in `*.pd.log` and `greedy-lfm25-2k-npu-fix.log` name the a16w8 2k
chunks and this `.pte`. The CPU run uses the same `.pte` without the NPU. Times are the
instrumented engine's (`ExecuTorchEngine.chat` to its first token), not taps in the app.

| POCO, LFM2.5-1.2B 2k | Pairs, reuse | Retained prefix: prefill, median | Retained prefix: first token, median | Reset cache: prefill, median | Ratio (prefill) | Replies identical |
|---|---|---|---|---|---|---|
| CPU (XNNPACK), run alone | 8, 8 | **85 ms** [82-89] | 85 ms | **3,312 ms** [3,301-3,635], 971 tokens | **39.0x** [37.7-44.3] | 8 of 8 |
| NPU, the engine as shipped | 8, **0** | none: 5,709 ms [5,522-7,424] labelled warm, a full read | **11,671 ms** [10,887-14,046] | **5,651 ms** [5,532-6,735], 998 tokens | none | 8 of 8 |
| NPU, runtime-end prototype (not shipped), pairs 0 to 6 | 7, 7 | **430 ms** [420-454] | 433 ms | **5,657 ms** [5,595-5,738], 998 tokens | **13.1x** [12.3-13.5] | **0 of 7** |
| NPU, the committed fix (3f1aab51), run alone | 8, **8** | **438 ms** [418-491] | 441 ms | **5,945 ms** [5,543-6,793], 998 tokens | **13.6x** [12.3-15.5] | **0 of 8** |

- **As shipped, the NPU path never reused the prefix.** MediaTek's runner stops at its own stop
  token (`Reached stop token 7` in `*.pd.log`) without handing that token's text to the callback;
  turn one's last fragment was `"?"` in all 8 pairs, the engine saw no end marker, dropped its
  record, and the second turn reset the runtime. With this export a reset reloads the NPU runtime
  (2.0 to 3.0 s) and the CPU module (1.9 to 2.7 s; its first load took 2.8 s) before the full
  read, so the engine's second turn took a median 11,671 ms to its first token, 2.1 times the
  5,662 ms of a read from a reset cache (the test resets that arm before its clock starts).
- **With the prototype the NPU path reused the prefix in every pair** and the second turn took
  a median 433 ms to its first token, against 11,671 ms as shipped (a ratio of the two runs'
  medians, 27x). Pair 7's timed arms (11:24 to 11:25; its turn one started at 11:22:48) overlapped
  the other project's install and push and are left out: its reset-cache read took 6,618 ms,
  against 5,595 to 5,738 ms in pairs 0 to 6. The prototype (`runtime-end-fix-2026-11-16.patch`,
  held outside the repository, built into the test APK only) treats a reply as whole when nothing
  in the engine stopped the runtime, no budget was spent and the window was not full by the
  engine's estimate, and assumes the runtime's end token is then what ended the loop. That
  assumption is not proved for every bridge (a runtime can also return for its own budget, a
  cancel or a full window, and the engine's position is an estimate), and it is wrong for the
  INT8-INT4 Qwen3 export, whose runtime stops on `"!"` (see below), so it is not a fix to ship.
- **The committed fix reused the prefix in all 8 pairs** (`poco/greedy-lfm25-2k-npu-v13.*`, 18:15
  on 16 Nov). Not recorded in these files, from the operator's adb checks: the test APK was built
  from the tree at 3f1aab51, no other test was running, and another app's idle process was
  stopped first (available memory 2.3 GB before, 7.5 GB after). An earlier attempt at this build
  crashed while the CPU half loaded (`greedy-lfm25-2k-npu-v10.*`, which records only the crash);
  about 1.5 GB was available just after it, and its cause was not established. The engine now keeps its record when
  the runner reports that it stopped on a stop token (code 0) and the reply was committed to the
  NPU half: turn one ended `END_OF_TURN` in every pair, its 73-token reply was fed to the NPU
  (`NPU fed 73 ... 978 total`), and the second turn read 20 tokens on top of it to 998, the
  position the reset-cache arm reached by reading all 998. Median first token 441 ms against
  11,671 ms as shipped; the reply changed with reuse exactly as in the prototype run. Turns started
  at 44.0 to 46.4 C (7 of 16 above the 45 C gate, which waits at most 60 s).
- **The CPU run recorded lower times than the NPU for both reads**: a reset-cache read of about
  1,000 tokens took 3.3 s on the CPU and 5.7 s on the NPU, a retained-prefix turn 85 ms on the CPU
  and 430 ms on the NPU with the prototype. This is not a matched processor comparison: the two
  backends wrote different turn ones (971 and 998 tokens at turn two), the NPU half is a16w8
  against the CPU's 8da4w, and the NPU path hands its state to the CPU after each read.
- **NPU call times are consistent with whole 128-token batches plus overhead** (the export's
  runner logs `batch=128`; the logs give tokens and time per call, not batches run). In the
  as-shipped run, calls of 160 to 256 tokens took a median 828 ms (156 calls, 808 to 1,568 ms) and
  the 4-token opener 650 ms (28 calls); in the prototype run before the overlap, calls of 5, 20 and
  73 tokens took a median 419, 429 and 425 ms (24 calls, 411 to 640 ms). The cold read was fed as
  4, 163, 160, 162, 160, 160 and 189 tokens: 13 batches if each call rounds up. Packed whole, the
  998 tokens need 8, or 9 with the opener kept separate (an estimate, not measured).
- **With reuse the NPU's greedy reply changed**: `Device-based offline AI.` with the retained
  prefix and `Device-only AI` from a reset cache, in pairs 0 to 7 alike. Both arms reached the same
  position before the reply (998, the runner's `NPU fed ... total` at the handoff), and turn one's 73-token reply was fed to the NPU
  (`NPU fed 73 tokens`) before the second turn. The two arms fed the same text in different
  pieces to a quantized NPU; why the reply changed was not established.

The CPU row is the run made alone on 16 Nov at 17:00 (`poco/greedy-lfm25-2k-cpu.*`, timed turns
starting at 41 to 45 C). Its first attempt (11:25 to 11:40) overlapped the other project's app install and a
benchmark of theirs, and agreed with it: 89 ms and 3,300 ms, 37.0x, the same replies (kept in
`poco/overlapped-execuphone/`).

`summarise.py` prints the CPU and Qwen3 rows. For the NPU rows it reads the timings but not the
runner's positions (`NPU fed ... total` in the bridge's lines) and has no pair filter: the
prototype's pairs 0 to 6 are its rows with `pair` under 7, and over all 8 pairs it gives 436 ms,
5,664 ms and 13.2x.

Not done on the NPU: sampled runs, other models (only LFM2.5-1.2B has an MT6991 export here), a
batch-aligned feed.

## Four families on both runtimes (2026-11-16, afternoon)

Llama 3.2 and Gemma 3 beside the LFM2.5 and Qwen3 above, measured the same way on both runtimes:
the same second turn read with the first turn kept in the cache and from a reset cache, greedy,
order alternated, turn one written again for every reset-first pair and checked equal, a 48-token
timed reply after the cooling gate. ExecuTorch runs `ExecuTorchOnDeviceTest#theSecondTurnReadColdAndWarm`
as before (12 pairs); llama.cpp runs the new twin, `WarmPrefixEval#theSecondTurnReadColdAndWarm`
(8 pairs, the same user turn and question, no system message, so Gemma runs too). Test Lab,
Android 16, test APK v11 and v12 (v12 adds `-e sentences`). Models: Llama 3.2 1B (our
`experimentalmachines/Llama-3.2-1B-Instruct-ExecuTorch` 8da4w-gptq 4k; Q4_0 GGUF), Llama 3.2 3B
(Q4_0 GGUF), Gemma 3 1B (`larryliu0820/Gemma3-1B-IT-INT8-INT4-ExecuTorch-XNNPACK`, the only CPU
export we found; Q4_K_M GGUF), Gemma 3n E2B (unsloth Q4_K_M GGUF), LFM2.5 1.2B (QAD-Q4_0 GGUF) and
Qwen3 1.7B (Q8_0 GGUF). The Galaxy Tab S10+ ran ExecuTorch only (its llama.cpp timing is anomalous,
above).

| Model | Runtime | Phone | Pairs, reuse | Retained, median | Reset, median | Ratio [range] | Same reply | Warmest start |
|---|---|---|---|---|---|---|---|---|
| Llama 3.2 1B | ExecuTorch 8da4w-gptq 4k | Galaxy S24 | 12, 12 | 126 ms | 3090 ms, 994 tokens | 25.1x [20.4-32.3] | 12 of 12 | 45 C |
| Llama 3.2 1B | ExecuTorch 8da4w-gptq 4k | Galaxy S25 Ultra | 12, 12 | 85 ms | 3149 ms, 994 tokens | 36.9x [33.3-39.8] | 12 of 12 | 44 C |
| Llama 3.2 1B | ExecuTorch 8da4w-gptq 4k | Pixel 9 | 12, 12 | 332 ms | 8294 ms, 994 tokens | 25.0x [23.5-26.2] | 12 of 12 | 67 C |
| Llama 3.2 1B | ExecuTorch 8da4w-gptq 4k | Galaxy Tab S10+ | 12, 12 | 103 ms | 3480 ms, 994 tokens | 34.6x [30.4-36.0] | 12 of 12 | 45 C |
| Llama 3.2 1B | llama.cpp Q4_0 | Galaxy S24 | 8, 8 | 163 ms | 5064 ms, 1004 tokens | 30.9x [25.6-35.1] | 8 of 8 | 43 C |
| Llama 3.2 1B | llama.cpp Q4_0 | Galaxy S25 Ultra | 8, 8 | 122 ms | 3788 ms, 1004 tokens | 29.9x [23.2-35.4] | 8 of 8 | 44 C |
| Llama 3.2 1B | llama.cpp Q4_0 | Pixel 9 | 8, 8 | 374 ms | 18459 ms, 994 tokens | 49.4x [43.1-56.6] | 8 of 8 | 66 C |
| Llama 3.2 3B | llama.cpp Q4_0 | Galaxy S24 | 8, 8 | 472 ms | 18574 ms, 1033 tokens | 38.5x [34.5-41.1] | 8 of 8 | 45 C |
| Llama 3.2 3B | llama.cpp Q4_0 | Galaxy S25 Ultra | 8, 8 | 280 ms | 12586 ms, 1033 tokens | 45.8x [33.1-52.0] | 8 of 8 | 44 C |
| Llama 3.2 3B | llama.cpp Q4_0 | Pixel 9 | 8, 8 | 1035 ms | 51598 ms, 1060 tokens | 49.5x [44.0-62.7] | 8 of 8 | 44 C |
| Gemma 3 1B | ExecuTorch INT8-INT4 (1k window, 690-token prompt) | Galaxy S24 | 12, 12 | 110 ms | 2382 ms, 690 tokens | 21.9x [15.3-29.6] | 0 of 12 | 44 C |
| Gemma 3 1B | ExecuTorch INT8-INT4 (1k window, 690-token prompt) | Galaxy S25 Ultra | 12, 12 | 123 ms | 3345 ms, 690 tokens | 27.7x [22.5-29.1] | 0 of 12 | 44 C |
| Gemma 3 1B | ExecuTorch INT8-INT4 (1k window, 690-token prompt) | Pixel 9 | 12, 12 | 442 ms | 6434 ms, 690 tokens | 14.5x [13.4-17.4] | 0 of 12 | 65 C |
| Gemma 3 1B | ExecuTorch INT8-INT4 (1k window, 690-token prompt) | Galaxy Tab S10+ | 12, 12 | 102 ms | 2676 ms, 690 tokens | 26.2x [22.8-28.3] | 0 of 12 | 45 C |
| Gemma 3 1B | llama.cpp Q4_K_M | Galaxy S24 | 8, 8 | 270 ms | 12204 ms, 930 tokens | 44.2x [39.9-47.6] | 8 of 8 | 44 C |
| Gemma 3 1B | llama.cpp Q4_K_M | Galaxy S25 Ultra | 8, 8 | 194 ms | 8404 ms, 930 tokens | 42.6x [36.1-61.8] | 8 of 8 | 44 C |
| Gemma 3 1B | llama.cpp Q4_K_M | Pixel 9 | 8, 8 | 516 ms | 23744 ms, 930 tokens | 46.1x [40.4-49.0] | 8 of 8 | 66 C |
| Gemma 3n E2B | llama.cpp Q4_K_M | Galaxy S24 | 8, 8 | 544 ms | 20560 ms, 971 tokens | 37.7x [4.7-48.7] | 0 of 8 | 45 C |
| Gemma 3n E2B | llama.cpp Q4_K_M | Galaxy S25 Ultra | 8, 8 | 329 ms | 13289 ms, 971 tokens | 42.4x [36.5-49.0] | 0 of 8 | 44 C |
| Gemma 3n E2B | llama.cpp Q4_K_M | Pixel 9 | 8, 8 | 746 ms | 39964 ms, 928 tokens | 56.3x [51.3-62.4] | 8 of 8 | 69 C |
| LFM2.5 1.2B | llama.cpp QAD-Q4_0 | Galaxy S24 | 8, 8 | 182 ms | 6258 ms, 1010 tokens | 32.9x [25.3-36.0] | 8 of 8 | 45 C |
| LFM2.5 1.2B | llama.cpp QAD-Q4_0 | Galaxy S25 Ultra | 8, 8 | 160 ms | 5314 ms, 1010 tokens | 33.7x [18.4-36.3] | 8 of 8 | 45 C |
| Qwen3 1.7B | llama.cpp Q8_0 | Galaxy S24 | 8, 8 | 2284 ms | 7313 ms, 1251 tokens | 3.1x [2.8-3.8] | 8 of 8 | 45 C |
| Qwen3 1.7B | llama.cpp Q8_0 | Galaxy S25 Ultra | 8, 8 | 1983 ms | 5288 ms, 1251 tokens | 2.8x [2.4-3.4] | 8 of 8 | 45 C |

Times are each engine's `prefillMs`, which is not cut at the same place on both runtimes: on
llama.cpp it spans tokenizing, aligning the cache and reading the prompt; on ExecuTorch it is the
runtime's own prefill plus the pieces the engine fed before it, timed on the host. Compare within a
row, not across runtimes. The reset-cache token count is the runtime's position for ExecuTorch (994
for Llama, 690 for Gemma; the rows' own field is the engine's estimate) and llama.cpp's own count
for llama.cpp. `families.py` prints the table. Replies are compared as text, not token ids.

- **Across the runs in this table every warm turn reused the first turn** (224 of 224 pairs: 96 on
  ExecuTorch, 128 on llama.cpp), every reset arm reused nothing, and the ExecuTorch arms reached the
  same runtime position in every pair. Every ExecuTorch turn one here (Llama 3.2 and Gemma 3) ended
  on its family's own marker delivered as text (`<|eot_id|>`, `<end_of_turn>`), so no ExecuTorch
  runtime ended a turn by itself in these runs; the llama.cpp test does not record how turn one
  ended (llama.cpp ends on the end-of-generation ids the GGUF lists).
- **Qwen3 on llama.cpp kept only part of the cache: 2.8x and 3.1x.** Its chat template drops the
  reasoning of earlier assistant turns, so turn two's prompt differs from the cache at turn one's
  reply: each warm arm kept the 902-token user turn and read 349 tokens again (the 849-token first
  reply as the template renders it once its reasoning is removed, and the question), 1.6 to 2.8 s
  (medians 1.98 s on the S25 Ultra and 2.28 s on the S24). The ExecuTorch engine renders
  history as it was fed, reasoning included, which is why Qwen3 kept the whole turn there (28.3x to
  54.5x above). Neither choice is simply right: Qwen3's model card asks that history carry only the
  final answer, which its template does, and the llama.cpp engine asks every template to keep past
  thinking and logs the ones that refuse (`kv: this template drops past thinking`); Qwen3's
  refuses. What keeping the reasoning does to answer quality was not measured.
- **Reuse changed the greedy reply on some families and not others.** Identical in every pair:
  Llama 3.2 on both runtimes, Gemma 3 1B and LFM2.5 on llama.cpp. Changed in every pair, the same
  way on every chip: Gemma 3 1B on ExecuTorch (`Optimization.` kept, `Translation.` reset, four
  chips from three vendors), Qwen3 on ExecuTorch (above), and Gemma 3n on llama.cpp on the two
  Snapdragons, where only the whitespace after `On-device.` differed (on the Pixel 9 both arms
  answered `On-device.`). For Qwen3 the analysis in `qwen3-divergence/` points to the same token ids
  computed with different arithmetic (a reconstruction on a Mac); the other families were not
  analysed.
- **A model that reads its prompt more slowly gained more in these pairs, though not as a rule.** On
  llama.cpp Llama 3.2 3B read its 1,033 tokens 3.3 to 3.7 times as slowly as 1B read 1,004 (S25
  Ultra and S24) and gained 45.8x and 38.5x against 29.9x and 30.9x; Gemma 3 1B read 930 tokens 2.2
  to 2.4 times as slowly as Llama 3.2 1B and gained 42.6x and 44.2x. But Gemma 3n read more slowly
  than Llama 3.2 3B on the S25 Ultra (13,289 against 12,586 ms) and gained less (42.4x against
  45.8x): the ratio depends on what the retained turn costs as well as on the full read. Why Gemma
  reads more slowly was not analysed.
- **The same family gave different conversations on different chips' llama.cpp builds**: the
  Pixel 9 wrote a shorter turn one than the Snapdragons for Llama 3.2 1B (44 against 54 tokens) and
  Gemma 3n (928 against 971 at turn two). Within a phone both arms reached the same token count.

Limits: the Pixel 9 started its timed turns at 46 to 69 C in every run but Llama 3.2 3B (at most
44 C), above the cooling target, so its ratios may be thermally confounded; its LFM2.5 and Qwen3 llama.cpp runs did not start (the gcloud
login expired) and are covered by the S24 and S25 Ultra; Gemma 3 1B's ExecuTorch export is exported at 1,024 tokens, so its prompt was 28
sentences (690 tokens) and its ratios are not comparable to the 1,000-token rows (its first runs at
40 sentences skipped every pair: turn one left no room); one export per family and runtime.

## Why Qwen3's replies ended on "!", and a second Tab S10+ (2026-11-16, daytime)

Runs on a Galaxy Tab S10+ (MT6989) reserved through Device Streaming for this study (12:45 to
13:56 local), over `poco_et.sh` with `S=localhost:5612`; files in `gts10pwifi-day/`. Same test,
Qwen3-1.7B INT8-INT4, sampled (the app's sampling), warm first, 16 pairs each.

| Run | Engine | Completed pairs | Reuse | Retained prefix, median | Reset cache, median | Ratio, hits |
|---|---|---|---|---|---|---|
| `sampled-qwen3-int8` | as shipped | 15 | 10 | **194 ms** [170-244] | **10,636 ms** [7,963-12,224] | **50.1x** [40.6-62.1] |
| `sampled-qwen3-int8-eoscfg` | as shipped | 14 | 11 | **189 ms** [161-218] | **9,452 ms** [7,293-10,144] | **47.0x** [43.9-56.7] |
| `sampled-qwen3-int8-fix` | runtime-end prototype | 16 | 16 | **189 ms** [173-238] | **10,407 ms** [7,795-13,431] | **50.3x** [44.3-64.0] |

Every missed pair (5 and 3) is one whose turn one the runtime ended itself, on `"!"`, as on the
three phones overnight. The `eoscfg` run put a `tokenizer_config.json` naming `<|im_end|>` beside
the tokenizer; it changed nothing, because the tokenizer reads that file only when it is handed a
directory and the engine hands it the tokenizer file, so it is a second run of the shipped engine.

**The cause: for this export the runtime's end token is token 0, which is `"!"`.**

- The export has no `get_eos_ids` method, only the older `get_eos_id` (151645, read on the Mac
  with ExecuTorch's Python bindings). ExecuTorch 1.6.0's runner then falls back to the
  tokenizer's own end token (`extension/llm/runner/llm_runner_helper.cpp`, `get_eos_ids`).
- Given a file path, the HF tokenizer does not read `tokenizer_config.json`. It then looks for
  exactly one special token whose name holds "end" or "eos"; Qwen3 has six (`<|endoftext|>`,
  `<|im_end|>`, `<|object_ref_end|>`, `<|box_end|>`, `<|quad_end|>`, `<|vision_end|>`), so it
  keeps its default end token, 0 (`hf_tokenizer.cpp`, `setup_special_token_ids`; `tokenizer.h`).
  Token 0 in Qwen3's vocabulary is `"!"`. This reading is from a local tokenizers checkout of
  2026-09-10 and the 1.6.0 runner source, not a trace on the phone.
- The app's own Qwen3 export (`Qwen3-1.7B-8da4w-gptq-4k`) has `get_eos_ids` = [151645, 151643].
  Its greedy turn ones ended `" details", "!", "<|im_end|>"` on the S24 and S25 Ultra (16 of 16),
  with no runtime end-of-sequence line: there `"!"` did not stop anything.
- In the 146 first replies of completed pairs recorded for this export in this folder (rewritten
  turn ones and `poco/overlapped-execuphone/` excluded), `"!"` appears 27 times, each time as the
  last character, and never inside a reply. A reply such as
  `Sure! Here is ...` would presumably stop at `Sure!` with this export; none occurred here.
- **The stopping `"!"` never enters the cache.** With the prototype keeping the record, the three
  turn ones that ended on `"!"` left the retained-prefix arm one position short of the reset-cache
  arm at turn two (1,467 against 1,468; 1,370 against 1,371; 1,377 against 1,378); in the 13 other
  pairs both arms reached the same position. So for this export the shipped engine is right to
  drop its record, and the prototype is wrong: it records a `"!"` the runtime never read.
- **The app cannot hand the runtime a better end token.** The tokenizer would read a
  `tokenizer_config.json` naming `<|im_end|>` if it were given a directory, but ExecuTorch's
  Android `LlmModule` refuses any tokenizer path that is not a file
  (`ExecuTorchRuntime.validateFilePath`); a build of the engine that passed a directory failed to
  open the model on a Galaxy Tab S10+ ("path is not a file"). The fix belongs in the export
  (`get_eos_ids`) or the runtime (reading `get_eos_id` when `get_eos_ids` is absent). Every export
  of ours checked lists `get_eos_ids` (Qwen3 8da4w-gptq 4k and 2k, LFM2.5 1.2B 4k and 32k,
  LFM2.5 2.6B, LFM2.5-VL 450M).

**On MediaTek's NPU the stop is the real end token.** MediaTek's runner builds its stop set from
the tokenizer, not from the export: the tokenizer's end token unless it is 0, id 0 only where it
decodes as padding, and the single-token encodings of `<|im_end|>`, `<|endoftext|>` and `</s>`
(`tools/npu/mtk_pd_disaggregated_jni.cpp`). That gave `[2, 7, 0]` for LFM2.5 and
`[151643, 151645]` for Qwen3, whose id 0 is `"!"` and so is left out. It stopped at token 7 and 151645,
the `<|im_end|>` of each, without handing its text to the callback. There the cache holds the reply
without the marker, as after a clean end, and the prototype's record matched it (998 and 998 on
the POCO; 1,653 and 1,653 below). A fix to ship would keep the record only when the runtime
reports that it stopped on the template's end token; the prototype cannot tell the two apart.

**Qwen3-1.7B on this tablet's NPU** (MT6989, the a16w4 2k export from the MediaTek matrix session,
`tools/mediatek/results/2026-11-16-mt6989/`; CPU half the INT8-INT4 `.pte`; greedy). Both runs
stopped in pair 1, when turn one (about 730 tokens) left no room in the 2k window
(`ContextWindowExceededException`), so each has one pair:

| Pair 0 | Retained prefix | Reset cache |
|---|---|---|
| As shipped | none: the record was dropped, 1,653 positions read, 17,242 ms | 17,106 ms |
| Runtime-end prototype | **878 ms** (1,633 kept, 20 read, both arms at 1,653) | 16,636 ms |

Positions are the runner's (`NPU fed ... total`); the rows' own fields are the engine's estimates
(1,829 read, 1,626 kept). Times are prefill.

NPU reads ran at about 94 tokens/s in 160-token pieces (about 1.7 s per piece). Every reply on the
NPU began with `<|file_sep|>` (151664) where the CPU writes `<think>` (151667); the MediaTek matrix
session records the same in every Qwen3 NPU prefill it ran (0.6B and 1.7B), so it is not
specific to this test; whether the exports, the tokenizer, the runner or the handoff cause it is
not established. After turn one the prototype fed the 730-token reply to the NPU, which
took 5.3 s after the reply was complete.

## Not measured, and what failed

- **The POCO overnight** dropped off USB during a greedy Qwen3 run, and those rows were lost; the
  POCO's runs above were made the next day.
- **On the POCO, overlapped and left out**: llama.cpp's Qwen3-1.7B Q8_0 rollback (11:40 to 12:03,
  the phone at 48 to 62 C; run again alone, above), sampled Qwen3 (stopped after 4 rows; not run
  again) and the CPU run's first attempt (run again alone, above). Their rows are kept in
  `poco/overlapped-execuphone/` and left out of every table; the CPU attempt's timings and the
  rollback attempt's replies are cited above only to compare with the runs made alone.
- **Not run on the POCO**: Qwen3 4k on the CPU and the GPU, llama.cpp restores of the other
  GGUFs, cold prefill against prompt length.
- **QNN sampled** and the QNN 0.6B were not run.

Files: `<device>/<run>.*` per Test Lab phone (`SC-51E`, `pa3q`, `tokay`, `gts10pwifi`): rows
(`.coldwarm.jsonl`, `.restore.jsonl`, `.prefill.jsonl`) and the test's and runtimes' log lines
(never the whole logcat). `ftl.sh` ran them (ACCOUNT, PROJECT, BUCKET, RAW, APK from the
environment); `poco_et.sh`, `poco_restore.sh` and `poco_llama_prefill.sh` (with `poco_lib.sh`) are the
equivalents for a phone on USB or wireless debugging, writing to `poco/` (`<run>.log` is the
test's, the runtime's and the NPU bridge's lines; `greedy-lfm25-2k-npu.pd.log` holds the bridge's
lines for that run, which its `.log` filter missed);
`summarise.py` and `report_restore.py` print the tables, and `families.py` the family table
(`WarmPrefixEval#theSecondTurnReadColdAndWarm` is the llama.cpp test behind it). The day's
second tablet runs are in `gts10pwifi-day/`. The test APK was built with
`-Pow.testJniOnDisk=true` (then presence alone; now it must be `true`).
