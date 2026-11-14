# llama.cpp v0.8.0 to v1.0.0, and ExecuTorch 1.5.1 to 1.6.0

2026-11-14. ExecuTorch moved from 1.5.1-openweights.4 to 1.6.0-openweights.2 and ships. llama.cpp
moved from v0.8.0 (8642541b, 2026-10-28) to v1.0.0 (b1b770d9, 2026-11-13) and was put back the same
morning (079fa8c4), because on a Galaxy S24 v1.0.0 decoded 20% to 23% slower at the 1,500 to 6,000
tokens of context a tool-using chat sits in (the section on the S24 below). Overnight no phone was
reachable (the POCO was off wireless debugging, the usual Test Lab project refused every signed-in
account, QDC needs a reservation), so most of what follows was measured on a 16 KB-page arm64
emulator (6 GB, Vulkan 1.3 through the host GPU) and on the Mac (Apple M5, 4P+6E), both shared with
other work; in the morning Test Lab ran under another project on Galaxy S24s, and those figures are
the phone's.

## llama.cpp v1.0.0

v1.0.0 removes 35 deprecated functions and `llama_set_warmup`, merges `clip.h` into mtmd and changes
CPU numerics: matrix multiplications take F32 activations (#31854) and flash attention accumulates V
in F32 (#29810). `engine_session.cpp` uses none of the removed calls and compiles unchanged.

### The cache contract

The engine's host driver ran the same script on both versions for seven models (LFM2.5 1.2B Q4_K_M
and QAD-Q4_0, LFM2.5 2.6B, LFM3 1.7B, Qwen3 1.7B Q8_0, Qwen3.5 2B, Gemma 3 1B): a tool turn, a
follow-up, a replaced pass, another chat and a fresh chat. The rendered prompt bytes, their token
counts, every cached and prompt split, the rollback points and the warm snapshot sizes are identical
on all seven. Replies differ in wording on five models, from the first differing character onwards;
the cache does not depend on them.

On the emulator, the app's engine tests ran on both versions: 18 passed and 2 skipped on each (no
audio model; the tool-loop test's model did not call a tool on either version), with the same warm
and reuse numbers. `WarmPrefixEval`
warmed 1,018 tokens in 8,171 and 8,237 ms, resident memory after load was 1,045 and 1,044 MB and
after the warm 1,122 MB on both.

### Quality

`LlamaCppParityEval`, greedy, seven cases a model on four models, 2 skipped on Gemma: 20 of 26 on
v0.8.0, 21 of 26 on v1.0.0. No case went from pass to fail; QAD-Q4_0's arithmetic trap went from fail
to pass. LFM2.5 Q4_K_M answered all seven word for word as before, QAD-Q4_0 six, Gemma six, and Qwen3
none (its reasoning diverges early and reaches the same grades).

GSM8K, IFEval and BFCL, 30 each, on LFM2.5 1.2B QAD-Q4_0 through `LlamaCppBenchmarkEval`:

| Set | v0.8.0 | v1.0.0 | Gained, lost | Same reply |
|---|---|---|---|---|
| GSM8K | 18 | 19 | 2, 1 | 14 |
| IFEval | 20 | 21 | 1, 0 | 9 |
| BFCL | 25 | 26 | 1, 0 | 29 |

No set lost; the net of three in 90 is within what changed. Resident memory was 1,058 MB on both.
The per-question speeds of this run are not used: the v1.0.0 half ran while the APKs and the
Android `llama-bench` builds were compiling on the same host.

### Speed, on the Mac

`llama-bench` from each version's tree with the app's CPU settings (4 threads), 512 tokens in and 128
out at depths 0 and 2,048, five models, six pairs each, the two versions back to back in alternating
order so that load from other work falls on both sides of a pair. Ratios are v1.0.0 over v0.8.0,
the median of six pairs. The machine was shared and single pairs range from 0.52 to 1.85, so only
medians that agree across pairs are read.

| Model | Prefill, depth 0 | Decode, depth 0 | Prefill, depth 2,048 | Decode, depth 2,048 |
|---|---|---|---|---|
| LFM2.5 1.2B Q4_K_M | 1.067 | 1.033 | 0.969 | 0.883 (slower in 6 of 6) |
| LFM2.5 1.2B QAD-Q4_0 | 1.037 | 1.029 | 1.009 | 0.901 (5 of 6) |
| LFM3 1.7B Q4_K_M | 0.990 | 0.992 | 1.000 | 0.837 (4 of 6) |
| Qwen3 1.7B Q8_0 | 1.156 (faster in 6 of 6) | 1.109 | 1.008 | 0.900 (5 of 6) |
| Gemma 3 1B Q4_K_M | 1.009 | 0.996 | 1.033 | 0.972 (4 of 6) |

Reading an empty context is the same or faster; Qwen3's Q8_0 prefill gains about 16% in every pair.
Writing with 2,048 tokens behind it is slower on every model. A second run isolated decode at depth,
64 tokens out at depths 0, 2,048 and 4,096, with flash attention on (as the app resolves it on the
CPU) and off, three models, six pairs a cell:

| Model | Depth 0, on | 2,048, on | 2,048, off | 4,096, on | 4,096, off |
|---|---|---|---|---|---|
| LFM2.5 1.2B Q4_K_M | 1.031 | 0.938 | 0.887 | 0.889 | 0.937 |
| LFM2.5 1.2B QAD-Q4_0 | 0.988 | 0.885 | 0.983 | 0.865 | 0.958 |
| Qwen3 1.7B Q8_0 | 1.004 | 0.834 (6 of 6) | 0.916 | 0.808 (6 of 6) | 0.905 (6 of 6) |

### Why decode at depth is slower, and why it is not patched here

Two upstream changes widen the F16 KV cache to F32 inside attention, where v0.8.0 multiplied and
added in F16 with the CPU's native half-precision instructions:

- #31854 makes F32 the CPU's input type for every F16 matrix product (`vec_dot_type` of F16 is now
  F32), so each key and value is converted before its multiply. Its author measured F16 matrix
  products 1.1 to 1.5 times slower on an M5 Max and kept the slower path on ARM; a faster F16
  product is left as a TODO. This is the cost with flash attention off.
- #29810 accumulates flash attention's V in F32. The F16 accumulator overflowed to inf and then
  NaN on long contexts (#29774), which turns a reply into garbage from that point. Upstream
  measured about 4% slower decode at 4k on ARM and accepted it. This adds to the first with flash
  attention on.

Both cost in proportion to the cache, which is why depth 0 does not show them on the Mac, and the
app always decodes at depth: its tool prompt is about 2,050 tokens.

The Mac's build is native to the M5 and is not the app's. `tools/snapdragon/llama_bench_ab.sh`
builds both versions for Android with the app's CPU flags (every CPU variant, KleidiAI on, OpenCL
off) and times decode in alternating pairs on one device. On the emulator, whose CPU is the same M5
running the app's Android variant, LFM2.5 1.2B QAD-Q4_0, six pairs, flash attention on:

| Depth | v0.8.0 | v1.0.0 | Ratio | v1.0.0 slower in |
|---|---|---|---|---|
| 0 | 30.4 tok/s | 29.7 | 0.964 | 5 of 6 |
| 2,048 | 23.5 | 22.5 | 0.956 | 5 of 6 |
| 4,096 | 18.8 | 18.0 | 0.961 | 5 of 6 |

So the app's own build loses about 4% at every depth there, not the Mac native build's 11% to 13% at
depth for the same file. A phone is neither: on the Galaxy S24 it lost 20% to 23% at 1,500 to 6,000
tokens (below), and that took v1.0.0 out of the release. Undoing either change locally would bring
back an overflow upstream fixed on purpose or diverge from upstream's numerics, so nothing is
patched; v1.0.0 can come back when its ARM path is fast again.

### Older Gemma 4 templates

v0.8.0 rewrote tool results into the form an outdated Gemma 4 chat template expects, when the
template embedded in the GGUF lacked the official one's `{#- OpenAI Chat Completions:` block.
v1.0.0 removes that step (#31915): the template is applied as written. The app passes ordinary tool
messages, so a Gemma 4 GGUF with an old template formats tool results differently after this
update. The seven most downloaded Gemma 4 GGUF repositories checked on 2026-11-14 (ggml-org,
unsloth, bartowski, lmstudio-community; E2B, E4B and 26B-A4B) all carry the current template, five
of them in repositories last changed in July, so the exposure is a file downloaded before its
repository's update; downloading it again fixes it. Gemma 4 is not on the shortlist. The workaround is not carried in the app.

### OpenCL on Adreno

v1.0.0 changes the OpenCL backend by 18,430 lines (new kernels, an on-disk kernel cache, a new
Adreno generation table). The app compiles it in with Adreno kernels, and it is reachable without the
user choosing it: with both halves on automatic, a model whose recorded turns read ten times more
than they write is loaded with every layer on the GPU (`automaticLayers`). Kernels compile when the
first GPU buffer is allocated, and a kernel that fails to compile ends the process (`exit(1)`), as
in v0.8.0. Device discovery and the rule that only Adreno and Intel GPUs are accepted are
unchanged, so a Mali phone (the POCO) never reaches any of it. A load with no layers on an Adreno
GPU should compile nothing either, because a zero-sized buffer never reaches the backend's allocator
(`ggml_backend_buft_alloc_buffer`); that is read from the source, not seen on a device. The new kernel cache stays off on Android: its default directory is
the process's temporary directory, the app sets no `TMPDIR`, and the cache disables itself rather
than fail. Overnight no Adreno device was available; in the morning `GpuOffloadTest` ran it on a
Galaxy S24's Adreno 750 (the section on the S24 below).

## ExecuTorch 1.6.0

Built on the Linux host from v1.6.0 with 1.5.1's recipe: XNNPACK, Vulkan and QNN in one
`libexecutorch.so`, MediaTek's runner beside it, 16 KB pages. `patch_qnn.py` applies unchanged, the
MediaTek patch is regenerated against the new tree, and the text runner, prefiller, token generator,
stats and Android JNI layer are the same files as 1.5.1's. The Java API is identical (60 classes, 42
native methods). The new library needs the same libraries, names the same 18 JNI classes and exports
the same symbols. Maven Central has no 1.6.0 Android artifact yet, so the classes come from
ExecuTorch's release bucket, pinned by SHA-256 in `assemble_runtime.sh`.

What 1.6.0 changes that reaches the app: the Vulkan backend (324 files, quantized linear and conv1d
rewritten) and a new refusal of graphs with bool buffers on GPUs without 8-bit storage; SDPA's
exponential is no longer bit-exact; the tokenizer normalises to NFC (ExecuServe measured 23 prompt
tokens against 25 on Qwen3 for accented text written decomposed); `LlmGenerationConfig`'s default
temperature is -1 instead of 0.8, which the app never relies on because it always sets one.

### Three QNN runner defects, and the first dispatch

An adversarial Codex review of the first 1.6.0 build found three defects in the QNN runner that
1.5.1 shares, fixed in `patch_qnn.py` and in the `1.6.0-openweights.2` runtime:

- The logits buffer was a function-level `static` sized by the first model's vocabulary, so a second
  model with a larger one wrote past its end. It is now per thread and grows to the vocabulary.
- A failed prefill step was ignored, and the cache was updated and sampled from regardless. The
  step's error now returns.
- The stop flag was cleared when the token loop started, so a stop asked during prefill or on the
  first token was lost. It is cleared when inference starts.

The same review found that a GPU or Qualcomm NPU graph that opens can still fail natively on its
first dispatch, in the app's process, on the user's first turn. The probe process now writes a few
tokens with every off-CPU text export before passing it; multimodal exports are still only opened,
as before. Off-CPU is read from the file's own delegates (any other than `XnnpackBackend`), and from
its name as before. A sound trial passes once and is remembered, like the open it extends. Four more
Codex rounds shaped it:

- The runtime never reads `maxNewTokens`, so the trial counts its own callbacks and asks for the stop
  on each one past four.
- The JNI layer holds text back until its buffer is valid UTF-8, so a graph that writes garbage can
  fill the window without a callback. Past 30 s the trial asks for the stop every 250 ms, whatever
  it has delivered.
- A trial that counted four or more tokens and delivered none, or that the deadline ended before a
  decode step, refuses the open with its reason and is not remembered, so the next open tries again.
  The second case is an NPU that hung: the trial's prompt is one prefill call.
- A native crash during the trial is not remembered either. The probe leaves a marker file while it
  generates; a crash with the marker present was the dispatch, which a driver may not repeat, and
  the open is refused this time only. A crash while opening, without the marker, is remembered as
  before.

On the emulator's Vulkan, SmolLM2-135M's trial wrote three tokens after the prompt's and stopped
(the runtime had resolved an allowance of 2,043): 3.3 s, 2.4 s of it the first prefill dispatch, and
the whole check with the load 7.3 s in the debug build and 5.6 s in the minified one. The app then
opened the file on the GPU and answered with 92% of the prompt from cache.

A refusal string for the new 8-bit storage message was added and then removed: Codex showed it
never matched on the load path and would have turned off GPU builds for unrelated failures.

### Quality

On the emulator's CPU, `ExecuTorchParityEval` (seven cases) ran twice on 1.5.1 and once on 1.6.0.
1.5.1 was identical to itself on all four models, so the emulator is deterministic and what moves
is the runtime. The two Vulkan exports (SmolLM2-135M, Qwen2.5-0.5B, both on the emulator's Vulkan)
answered word for word as on 1.5.1. The two XNNPACK exports did not: LFM2.5 1.2B changed 2 of 7
replies and Qwen3 1.7B all 7, with the same grades (5 of 7 and 7 of 7).

GSM8K, IFEval and BFCL, 30 each, 1.5.1 against 1.6.0, on two LFM2.5 1.2B XNNPACK exports: the app's
default download (`experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch`,
`xnnpack/LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte`, run in ABBA halves) and Software Mansion's:

| Set | Default 1.5.1 | Default 1.6.0 | Gained, lost | Software Mansion 1.5.1 | 1.6.0 | Gained, lost |
|---|---|---|---|---|---|---|
| GSM8K | 20 | 16 | 0, 4 | 18 | 17 | 2, 3 |
| IFEval | 21 | 21 | 0, 0 | 20 | 20 | 1, 1 |
| BFCL | 26 | 26 | 0, 0 | 27 | 27 | 0, 0 |

The default export's GSM8K losses all ran one way, so 100 more GSM8K questions were drawn (seed 11,
none of the first 30) and run on it: 67 of 100 on 1.5.1 and 65 on 1.6.0, 8 gained and 10 lost
(two-sided sign test p = 0.82), with 4 replies of 100 identical. Over all three GSM8K samples 1.6.0
gained 10 and lost 17 (p = 0.25). 1.6.0 rewrites nearly every reasoning reply on the CPU path and
no difference in accuracy is established; a small one would need far more questions to show.
One line makes the whole difference: 1.6.0 rebuilt in the same tree with only the SDPA softmax's
`exp_u20()` put back to `exp()` (`extension/llm/custom_ops/op_sdpa_impl.h:596`) answered the 100
questions byte for byte as 1.5.1 did, 100 of 100. The app ships 1.6.0 as upstream has it; that
one-line revert is the way back to 1.5.1's exact output if it is ever wanted. The
four lost answers on the first sample were wrong arithmetic setups and one reply that reached the
640-token cap, not broken text. 1.6.0 wrote 12% more tokens on that GSM8K sample (343 against 306).

Speed and memory, per question: on the default export (ABBA halves) decode ran at 1.011 times
1.5.1's rate and prefill at 1.024, resident memory 1,090 and 1,091 MB after load. On Software
Mansion's (one run after the other) decode was 1.029 and prefill 0.964, slower on 64 of 90, which
the ordered run cannot separate from drift.

## On a real Snapdragon: Galaxy S24 in Test Lab

Later the same morning Firebase Test Lab ran under another project: Galaxy S24 (SC-51E, Snapdragon
8 Gen 3, Adreno 750, Android 16), whose CPU loads the app's `android_armv8.6_1` variant. Each Test
Lab run lands on whichever unit is free, so a comparison is between units, and one unit of the
first pair was throttled; three units per version were run for that reason.

- **llama.cpp's OpenCL path on v1.0.0 works on the Adreno 750.** `GpuOffloadTest` loaded every
  layer of LFM2.5 QAD-Q4_0 on the GPU and answered at 31.3 tok/s (v0.8.0: 32.8). The GPU load
  took 29.4 s on v1.0.0 and 35.7 s on v0.8.0 and the seven-token GPU reply ran 4.6% slower on
  v1.0.0, one unit each: no slower load, and too few tokens to say more about GPU decode.
- **ExecuTorch 1.6.0's rewritten Vulkan backend works on a real Adreno driver.** SmolLM2-135M's
  Vulkan export answered every case at about 112 tok/s decode; its replies differ from the
  emulator's GPU, as a 135M model's do between GPUs. The default LFM2.5 download passed 4 of 7 on
  the CPU, both tool cases among them.
- **Decode at depth, the number that decided.** `ContextLengthBenchmark.measuresWhatAFullWindowCosts`
  on the app's engine, one load, the context filled to each depth and 96 tokens decoded, two units
  per version, the two test APKs differing only in llama.cpp:

  | Context tokens | v0.8.0 tok/s | v1.0.0 tok/s | Decode ratio | Prefill ratio |
  |---|---|---|---|---|
  | 35 | 52.2, 53.0 | 48.6, 49.7 | 0.93 | 0.78 |
  | 1,480 | 40.4, 50.1 | 39.2, 33.3 | 0.80 | 0.90 |
  | 5,832 | 34.4, 35.1 | 24.8, 28.9 | 0.77 | 0.93 |
  | 11,629 | 17.3, 18.1 | 14.8, 16.5 | 0.88 | 1.05 |
  | 23,240 | 9.0, 8.6 | 7.9, 8.4 | 0.93 | 1.11 |

  At all five depths every v1.0.0 unit decoded slower than every v0.8.0 unit. The app's
  tool prompt alone is about 2,050 tokens, so this is the range every chat with tools is in, and a
  fifth slower there is felt on every token; v1.0.0 was taken back out of the release for it. The
  v1.0.0 units ran in another project about 40 minutes later; Test Lab draws both from the same pool.
- **Speed, `SpeedProbe`, three units per version, medians:** reading a 404-token prompt, v1.0.0
  ran at 1.05, 1.06, 1.07 and 1.18 times v0.8.0's rate at 2, 4, 6 and 8 threads; the 16 tokens
  written after it at the app's 4 threads came out at 0.93, 0.90, 1.13 and 1.07, no change either
  way. The 2-token rows swing from 0.5 to 2.8 between units and are not read.

## The shipping build on three real phones

The engine test APK from main at 25708b1a (llama.cpp v0.8.0, ExecuTorch 1.6.0-openweights.2, the
final probe code) and the minified release of the same commit, in Test Lab under the second project:

- **Galaxy S24** (Snapdragon 8 Gen 3, Adreno 750): `GpuOffloadTest` put every layer on the Adreno
  and answered at 35.1 tok/s; `LlamaCppEngineTest` passed what it could (the two cache tests need a
  Qwen file that was not pushed); `ExecuTorchParityEval` ran the default download on the CPU (4 of 7)
  and SmolLM2's Vulkan export on the Adreno (2 of 5), no errors.
- **Pixel 9 Pro** (Tensor G4, Mali-G715, the same `android_armv9.0_1` CPU variant the POCO loads):
  the same, with OpenCL correctly dropping the Mali GPU and the GPU test skipping; ExecuTorch 1.6.0's
  rewritten Vulkan backend ran on a real Mali driver, with the same grades as on the S24.
- **OPPO A79 5G** (MediaTek Dimensity, Mali-G57), the minified release under Robo: it opened LFM2.5
  QAD-Q4_0 from its models folder, warmed and saved the 789-token prefix, restored it from disk in
  60 ms on later loads, re-read the prompt correctly after Robo changed a setting, and did not crash.

## The release build

What ships is ExecuTorch 1.6.0 with llama.cpp at v0.8.0, the pin the current production build
already runs. The smoke test below was of the v1.0.0 build; the GGUF path it exercised is
unchanged by going back. The minified, signed release, installed beside the debug app under a temporary `.rc` suffix, on the
emulator: LFM2.5 QAD-Q4_0 on llama.cpp v1.0.0 answered with 91% of its prompt from the warm prefix;
the default ExecuTorch download answered on the CPU with 92%; SmolLM2's Vulkan export passed its
trial and answered on the GPU; a garbage `.pte` crashed only the probe process when the model
picker read it and was refused in words when opened. The app stayed up throughout. `bundleRelease`
signs, every 64-bit library in the bundle is 16 KB aligned, and its ExecuTorch libraries are the
1.6.0-openweights.2 ones byte for byte.

## Not measured

The S24 ran both runtimes; these are still open, in the order they matter:

- **The POCO itself** (Dimensity 9500s, Mali-G925). The Pixel 9 Pro covered its CPU variant and a
  Mali Vulkan driver, and the OPPO a MediaTek phone, but not this one. `llama_bench_ab.sh run
  <serial> <model>` times llama.cpp there in about 15 minutes, should v1.0.0 come back.
- **QNN and the MediaTek NPU.** Built and checked statically, with three QNN runner defects fixed
  from source; never run on hardware. Both now pass through the probe process before the app opens
  them (the trial for QNN, the NPU-half check for MediaTek), so a first dispatch that crashes ends
  the probe process, not the app: a QNN file is refused with a message, a MediaTek NPU half is
  refused and the file's CPU build used.
- **Phone CPU speed for ExecuTorch.** Only the emulator's figures above.
- **The probe trial's normal length on a phone.** Its 30 s deadline ends any trial still running;
  a sound trial took 3.3 s on the emulator's Vulkan and was not timed on phone hardware.
