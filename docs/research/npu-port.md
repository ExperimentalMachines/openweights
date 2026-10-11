# The NPU port from ExecuServe: what came across, what the phone found, what ships

**Measured 2026-10-11 on the Poco X8 Pro Max (MediaTek MT6991, Android 16 / HyperOS),
minified `.rc` builds signed with the debug key, phone awake and unlocked.**

## Decision

The MediaTek path is ported, installs as one download per context window, loads on the NPU
and answers correctly. It does not make chat faster on this phone, which is what
[`npu-pd-disaggregation.md`](npu-pd-disaggregation.md) found in September and nothing in the
port changes: the NPU reads a prompt at 120 to 195 tok/s, one call of at most 128 tokens
costing about half a second, where the app's own CPU path reads the same prompt at 187 to
230 tok/s. Measured again on the rebuilt runtime with the phone's apps closed: the
same answers as the CPU, 1.0 s a turn against 0.5 to 0.7 s. The Qualcomm path (QNN) runs on a Snapdragon 8 Elite through Discover's published files
and reads a 305-token prompt faster than the GPU and the CPU there (774 tok/s against 684
and 465 on Qwen3-0.6B); it also killed
the app whenever a conversation reached the last prefill block of its window, which the
runtime now refuses as a full window instead (see "Qualcomm" below).

## What came across

| Piece | From | Here |
|---|---|---|
| ExecuTorch 1.5.1 with XNNPACK, Vulkan and QNN in `libexecutorch.so`, and Qualcomm's five runner fixes | ExecuServe `tools/executorch/build_libs.py` (Modal) | `tools/npu/build_executorch_android_linux.sh`, `tools/npu/patch_qnn.py` |
| MediaTek's NPU-prefill, CPU-decode runner | this repository's runner, already renamed there | `tools/npu/build_mediatek_linux.sh`, JNI names kept as this app's |
| QNN setup: `ADSP_LIBRARY_PATH`, decoder family, model type 4, `seqLen` | ExecuServe `QnnSupport` | `QnnSupport.kt`, `NativeExecuTorchBridge` |
| NPU memory estimate | ExecuServe `NpuMemory` (measured 3.54 GB for LFM2.5-1.2B 2k, estimated 3.9) | `NpuMemory` in `NpuInstall.kt` |
| Catalog bundles: chunks, embedding, the CPU build of the same window | ExecuServe's catalog | `NpuBundle`, Discover cards, `NpuInstall` |

## What only the phone found

- **Every NPU install was deleted the first time the model list opened.** A start-up sweep
  for the retired image-model bundles removed every directory in the models folder, and an
  NPU half is a directory. `ModelStore.deleteLegacyGenerationBundles` now spares `.npu`.
- **The app then ran chunks nobody installed.** With the install gone, the bridge fell back
  to a benchmark staging folder under `/data/local/tmp/mtk_models` left from September, and
  the NPU timings looked normal. The staging root is now a constructor argument that only
  the instrumented test passes.
- **The card refused a bundle that fits.** It ran the NPU estimate, already the whole
  footprint, through the compiled-model allowance as well (times 2.4 plus 450 MB): 10 GB for
  the 2k bundle on a 12 GB phone. `FitEstimator.estimateKnownNeed` takes it as it is: 4.0 GB
  for 2k, 5.4 GB for 4k, 8k refused.
- **The download list showed every chunk.** An install is now one row: every part carries
  the install's group tag, the list sums their progress, and one cancel or retry acts on all.
- **The context gauge doubled.** The runner reports prompt tokens and prefill time as totals
  since its last reset and the engine reads the generate call's own tail; the same turn
  showed 84% on the NPU against 46% on the CPU. The bridge now subtracts what it had fed.
- **The top bar said CPU.** It read the processor from the file name, and the decoding half
  is a CPU build. It now asks the engine.

## What review found (Codex, four rounds)

- **The new runtime could not have shipped.** ExecuServe's `libexecutorch.so`, its QNN
  backend and both MediaTek libraries were linked for 4 KB pages (NDK r26c's default), and
  Google Play refuses an arm64 library that is not 16 KB aligned at this target SDK. Every
  check here and the phone passed them. All are rebuilt with
  `-Wl,-z,max-page-size=16384`, and `tools/npu/assemble_runtime.sh` refuses any 64-bit
  library aligned below 16 KB.
- **MediaTek's buffer allocator is a 4 KB binary.** It is replaced by
  `tools/npu/neuron_buffer_allocator.cpp`: the five C entry points ExecuTorch's backend
  reaches by `dlopen`, compiled against the backend's own header so `MemoryUnit` has its
  layout exactly, one allocator per process (the backend and the runner each call `create`)
  with its own lock, and `find` by interior pointer, which the backend passes.
- **Two BOS tokens.** The engine writes the family's BOS into the prompt text and the runner
  asked the tokenizer for another. The runner now asks for none.
- **A bad chunk could close the app at every launch.** The Neuron backend opens the chunks
  in the app's process and the app reopens its last model at start-up. Downloads are
  hash-checked before a part takes its name, and the bridge now marks a half as opening
  until its first prompt is read: a launch that finds the mark refuses that half until it is
  downloaded again and reads with the CPU.
- **Deleting a model mid-install** left its part downloads running, recreating the folder.
  Delete now cancels the install's downloads and waits for the workers to stop.
- **Pairing by window alone** could hand the NPU half a different model's CPU build. The
  export config's `source_model` must now name the CPU file.
- **The NPU never saw the replies.** The CPU half writes the reply into its own cache; the
  NPU's holds only the prompt, and the next turn is read on the NPU and handed to the CPU.
  The engine recorded the reply as held, so a follow-up was answered from a conversation
  missing the last answer. The engine now offers every clean reply to the bridge
  (`commitReply`), which feeds it to the NPU; a bridge that cannot gives up reuse and the
  next turn starts over.
- **The probe's check went stale.** The probe process already opens the NPU half in its
  trial load (it uses the same bridge), but its verdict was keyed on the CPU file and the
  tokenizer, so a half that landed later was never tried. The key now carries every part,
  and a trial that crashes with a half present refuses the half and judges the CPU build
  on its own.
- **An installed NPU card read as not installed** after any model-list refresh, lost its
  memory verdict on another refresh, and read as installed with the CPU build or tokenizer
  missing; the accelerator list was empty through the probe wrapper. All fixed.
- **The assembly gate skipped what it could not read** and checked no architecture; it now
  refuses a non-ELF file, a wrong class or machine for the directory, or a non-shared
  object. A build directory kept from an earlier run is refused unless it was made by the
  same tag and patch.
- **The NPU half was skipped without a word.** Ported from ExecuServe, the bridge refuses
  the half when the estimate plus a 1 GB reserve exceeds `MemAvailable`, and then read with
  the CPU while the card said installed and the top bar said CPU. The chat now says how much
  memory the half needs, how much is free, and that closing apps and loading again uses it.
- **A trial that fell back to the CPU passed for the NPU half** (round 3). The probe applies
  the memory gate too, so a half could be verified while it was skipped, then opened untried
  in the app's process once memory was free; and a graph can open and fail on its first call.
  The trial now reads a prompt with the half, records that it did (`NpuInstall.markTried`),
  and the app's process opens only a tried half; a trial that skipped it is remembered as
  CPU-only and tried again when the half would fit. A half that cannot read its trial prompt
  is refused.
- **A reset could move a session between processors** (round 3). LFM2 exports are reopened
  to reset, and the reopen decided afresh, so the top bar could name the other processor. A
  reopen now keeps the processor the session had.
- **The skip notice was erased** by the chat restored at start-up, and a native open that
  failed fell back without one (round 3). Both fixed.
- **Deleting and reinstalling at once** could let the late sweep for a deleted install's
  stragglers remove the new install's parts. The sweep now runs only when no install started
  after the delete.
- **A one-token trial never ran the CPU half** (round 4). The first token comes from the
  NPU's logits at the handoff, so a trial asking for one token passed without the CPU half
  ever decoding. The trial now asks for four and passes only when at least two came back.
- **The tried mark outlived what it tried** (round 4). It named the model alone, so a
  runtime update, a new runner config or a replaced chunk kept a mark for something never
  tried. It now names the runtime build, the runner config's hash and every part, and the
  probe's cache and the Vulkan and QNN refusals are keyed on the full artifact version
  (`1.5.1-openweights.3`), so a rebuild of one release is seen as new.
- **The stats line's prompt time** came from wall time around the JNI call minus the
  runner's earlier total, which could understate a short tail. The runner now reports its
  own prefill clock (`nativePrefillMs`), shipped in `1.5.1-openweights.3` with the
  conv-state bounds check.

## Measured

Same model (LFM2.5-1.2B-Instruct, 2k window), same phone, same day.

| | CPU path | NPU prefill, CPU decode |
|---|---:|---:|
| Warm prefix at load, 860 tokens | 3.7 to 4.6 s | 4.7 to 6.4 s |
| Prompt reading | 187 to 230 tok/s | 120 to 195 tok/s |
| One NPU call | | about 0.53 s, at most 128 tokens |
| Decode | 31 tok/s | 29 to 31 tok/s |
| NPU runtime load | | 2.3 to 2.7 s |
| Memory estimate, 2k | | 4.0 GB (3.54 GB measured by ExecuServe) |

## The 16 KB runtime on the phone

Rebuilt runtime (`1.5.1-openweights.2`), same phone, 2026-10-11 10:34 to 10:54, a game and
a social app left open in the background as the phone's owner had them.

| | Result |
|---|---|
| CPU, LFM2.5-1.2B 2k | Loads; warm prefix 860 tokens in 3.8 to 4.0 s; "Paris", then "the Seine" on the follow-up; send to answer 0.73 s and 0.48 s; 148 and 109 tok/s read, 28 to 30 tok/s decode |
| GPU, Qwen3-0.6B Vulkan 2k | Passes the probe process and loads on Vulkan; top bar says GPU; warm prefix 984 tokens in 2.2 to 2.6 s; "Paris"; the follow-up resolves "it" to Paris; 254 tok/s read, 12 tok/s decode |
| NPU bundle | Discover offers one card per window; the download is one row (2.39 GB) that reuses the CPU build already present; the half survives a force stop and relaunch |
| NPU load, apps open | Refused by the memory gate on every try: estimate 4,091 MB plus the 1 GB reserve against 2.7 to 4.5 GB free with those apps open; the chat says so |
| NPU load, apps closed (12:59, 6.3 GB free) | The probe process's trial opens the half, prefills, hands off and writes a token in 6.8 s; the app's process then opens it (NPU runtime 2.6 s, CPU half 2.5 s) with the 16 KB libraries and this repository's buffer allocator; top bar says NPU; warm prefix 860 tokens in 4.9 to 6.1 s |
| NPU turns | "Paris", then "the Seine", word for word the CPU's answers; send to answer 0.98 s and 1.00 s against the CPU's 0.73 s and 0.48 s; the follow-up fed 14 tokens to the NPU in 0.60 s; context gauge 46% and 48%, the CPU's figures |

The memory gate is the finding for ordinary use: on a 12 GB phone, the 2k NPU half does not
fit beside what the owner keeps open, so the NPU path runs only after apps are closed. The
reserve is ExecuServe's and has no measurement behind it; it stays until one says otherwise.
With room, the NPU answers exactly as the CPU does and is slower per turn, because one NPU
call costs about half a second however few tokens it reads, and a follow-up is a few tokens.

The phone run found one more bug: a verdict the probe had cached before the tried mark
existed kept the half off for good ("not been tried yet", and no trial). A cached pass
without the mark is now tried again whenever the half would fit.

The Qwen3-0.6B follow-up came back with no answer on the GPU twice: the reasoning ends in a
literal `<|endoftext|>` without closing, and names the wrong river. Its CPU build does the
same on the same two turns, so it is the model or the app's Qwen3 follow-up prompt, not the
GPU, and it is left open as its own item.

## Runtime `1.5.1-openweights.3` on the phone

Commit `bdd97f91`, minified `.rc` build, 2026-10-11 15:28 to 15:32, phone idle on the home
screen with 5.9 GB free.

| | Result |
|---|---|
| Probe trial | Opens the half, the NPU reads the 5-token trial prompt, hands off, and the CPU half decodes 4 tokens (23 tok/s); verified in 8.0 s with the tried mark written |
| App load | NPU runtime 2.4 s, CPU half 2.7 s; top bar says NPU; warm prefix 860 tokens in 4.7 to 4.9 s |
| Turn 1 | "The capital of France is Paris."; 71 tokens read in 586 ms on the runner's clock, which the stats line now shows (121 tok/s); 0.9 s send to answer; context 46% |
| Turn 2 | "The river that runs through Paris is the Seine River."; 14 tokens read in 581 ms (24 tok/s, the half-second floor of one NPU call); 1.0 s; context 48% |

The same answers and the same per-turn cost as the `.2` runtime, now with the bounds check
in the binary, a trial that proves both halves, and a prompt rate the stats line takes
from the runner rather than from the clock around it.

## Qualcomm: Snapdragon 8 Elite (SM8750) through QDC

Qualcomm Device Cloud, SM8750 (`sun`), Android 16, 2026-10-11 around 03:00 phone time, the
same `.rc` build of `bdd97f91`. Everything went through the app as a user would: Discover
offered the `qnn/sm8750` files of `experimentalmachines/Qwen3-0.6B-ExecuTorch` and
`Qwen3-1.7B-ExecuTorch` as "Compiled for NPU", downloaded and hash-checked them, the probe
process passed them (0.66 s for the 1.7B), and the top bar said NPU. The runtime picked the
V79 skeleton the app carries and reported QNN API 2.29 (qnn-runtime 2.39) running context
binaries compiled with QAIRT 2.37; the load took about 0.3 s.

| Qwen3, 2k window | Prompt of ~305 tokens | Decode | Warm prefix, 984 tokens |
|---|---:|---:|---:|
| 0.6B, NPU (QNN) | 0.39 s (774 tok/s) | 34 tok/s | 1.39 to 1.54 s |
| 0.6B, GPU (Vulkan) | 0.45 s (684 tok/s) | 40 tok/s | 0.95 to 0.96 s |
| 0.6B, CPU | 0.67 s (465 tok/s) | 26 to 34 tok/s | 1.43 to 1.46 s |
| 1.7B, NPU (QNN) | 75-token question in 57 ms (1,316 tok/s) | 32 to 36 tok/s | 0.57 to 0.58 s |

Answers: "Paris" on all three. Follow-ups were uneven on Qwen3 whatever the processor: the
CPU answered "the Seine"; the 0.6B NPU export twice ended its reasoning without closing it
(the app then shows no answer), and the GPU did once on a first turn. That is the model's,
not the delegate's, and stays the open Qwen3 item. A stray character in the earlier
follow-up tests was the test script tapping the keyboard, not the app.

The warm prefix is where the NPU loses on the small model: the app feeds it in seven pieces,
and the QNN runner pads each to a 128-token block (877 tokens took 1,408 slots). The 1.7B
export reads the same pieces in 0.58 s, so the cost is per block, not per piece.

**The crash.** Three times in ten minutes the app was killed by the QNN runner's assert in
`PromptProcessor::prefill`: after the 1.7B's 546-token follow-up, when that conversation was
reopened, and after a long-prompt reply. The runner asserts that a prompt ends at least one
prefill block (128 tokens) short of the window; `patch_qnn.py` guarded at the window itself,
so a conversation ending in that last block (984 held plus 982 fed, past 1,920) reached the
assert. With the ~1,000-token tool prompt and Qwen3's reasoning, a 2k QNN export reaches it
within two or three turns. The guards in both `generate` and `prefill` now use the prompt
processor's bound and return the overflow error, which the app already answers: the warm
fails quietly and the next turn says the conversation no longer fits, as the CPU runner did
on the same phone when the 1.7B's 859-token reasoning filled its window. Built into
`1.5.1-openweights.4` and replayed on a second SM8750 reservation the same morning: the
1.7B's long-prompt reply (673 tokens) filled the window, the runner refused the next piece
("1865 held + 64 new, past 1920") and the app stayed up; the follow-up, the reopened
conversation and a turn in it did the same, and the phone's crash log stayed empty. The app
then condensed the conversation and answered the next turn, reading its 1,302-token prompt
at 2,114 tok/s on the NPU. One more fix came of it: the wrapper's message for that refusal
is only the code ("[ExecuTorch Error 0x12] Invalid argument: Failed to generate"), which the
chat showed raw; on Qualcomm's runner that code is now read as a full window
(`isWindowFull`), as the CPU runner's diagnostics already were. A reply cut off by the full
window still shows nothing in its place, on every runtime; that is the app's, and open.

No QNN export exists for the Snapdragon 8 Elite Gen 5 (SM8850) on the Hub: every published
folder is `qnn/sm8750`, so Discover rightly offers none there. Qwen3-0.6B and SmolLM2-135M
were exported for it at 2k (execupack's Export QNN workflow, publishing off, artifacts only)
for that session.
