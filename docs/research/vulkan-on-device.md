# Vulkan GPU exports on the phone

**Measured 2026-10-03 and 2026-10-04 on three phones: a Poco X8 Pro Max (MediaTek MT6991,
Mali-G925-Immortalis MC11) and two Qualcomm Device Cloud handsets, a Snapdragon 8 Gen 3
(SM8650, Adreno 750) and a Snapdragon 8 Elite Gen 5 (SM8850, Adreno). One model throughout,
Qwen3-0.6B at a 2,048-token window, as published in
`experimentalmachines/Qwen3-0.6B-ExecuTorch`: `vulkan/Qwen3-0.6B-vulkan-8da4w-2k.pte` for
the GPU and an 8da4w `xnnpack/` file for the CPU. The apps run the ExecuTorch 1.5.1
runtime (`org.pytorch:executorch-android-vulkan:1.5.1`). Raw output, and every Codex review
of the code, is in [`tools/eval/results/vulkan-2026-10-04/`](../../tools/eval/results/vulkan-2026-10-04/).**

## The answer first

- **Both apps now open Vulkan exports on any phone whose GPU runs them**, from one runtime
  library that also carries the CPU (XNNPACK) path. Every check passed on all three GPUs:
  the openweights engine's nine on-device tests on each, ExecuServe serving the GPU build
  correctly on each, and, on the Poco, ExecuServe's OpenAI (16/16), edge-case (28/28) and
  Anthropic (8/8) suites.
- **Whether the GPU is the faster choice depends on the GPU.** On the Mali phone the CPU
  build decodes 2.9 to 3.3 times faster than the GPU build, and nothing measured favours
  the GPU there. On the newest Adreno (SM8850) the GPU reads long prompts 1.4 to 1.9 times
  as fast and decodes 1.2 to 1.4 times as fast with a long prompt in the cache, but decodes
  short replies at 0.7 times the CPU's speed. This is the same lesson as
  [gpu-backends.md](gpu-backends.md) for llama.cpp: "GPU is faster" is a property of a
  driver and a memory system, measured per family.
- **The apps do not prefer either build yet.** They offer CPU and GPU files side by side.
  On Mali that offers a slower file next to a faster one; recommending the CPU build where
  it is faster is open (see the end).

## 1. What shipped

### One library for CPU and GPU

The `executorch-android` AAR both apps shipped (1.4.0) registers `XnnpackBackend` only: its
`libexecutorch.so` has no `VulkanBackend` symbol, so a Vulkan `.pte` cannot load there: a
file naming a delegate the runtime has not linked fails with "backend is not registered"
([executorch.md](executorch.md)). `executorch-android-vulkan`
registers both, so CPU and GPU exports open from the same library and nothing else in the
engine changes. It is pinned at 1.5.1 because the exporter moved to 1.5.1 the same day, and
ExecuTorch promises an older file loads on the next minor runtime but promises nothing for
a file newer than the runtime (`runtime/COMPATIBILITY.md`). Every 1.4.0 file on the Hub
therefore keeps working, which section 3.4 checked on a phone.

| Repository | Commit | What |
|---|---|---|
| openweights | `3561143` | ExecuTorch 1.5.1 runtime |
| openweights | `55e4f68` | Vulkan exports open where the GPU runs them; a GPU that cannot is learned once |
| openweights | `a23195d0` | A file's backend is the one its name mentions last (Codex round 3) |
| ExecuServe | `1f75829` | ExecuTorch 1.5.1 runtime; the export-version warning follows the compatibility policy |
| ExecuServe | `c67ec10` | Vulkan: list and serve GPU exports on phones whose GPU runs them |
| ExecuServe | `13085b1` | `/v1/models` reports the delegate a model was exported for |
| ExecuServe | `f46833d` | GPU builds leave the list and the download queue on a refusal; the backend is recorded at install |

### Which phones are offered GPU files, and why it is decided twice

`VulkanSupport` (`core/engine`, and a copy in ExecuServe's `android/executorch`) decides.

1. **Up front, from what Android reports.** ExecuTorch's Vulkan delegate creates a Vulkan
   1.1 instance, so a phone without `FEATURE_VULKAN_HARDWARE_VERSION` 1.1 is never offered a
   GPU file. All three phones report more (1.3, 1.3 and 1.4).
2. **Learned, from the runtime's own words.** Each shader declares the device features it
   needs (8- and 16-bit storage, int8 arithmetic, integer dot products), and a driver that
   lacks one makes the runtime throw "not compatible with device. Missing support for
   extension or physical device feature" (`backends/vulkan/runtime/vk_api/Exception.cpp`)
   when the model loads or first runs, not when the download starts. Android has no Java
   API for Vulkan extensions, so this cannot be known in advance. The first such failure is
   recorded, and from then on GPU files are not offered and the CPU build is named instead.

Only the runtime's incompatibility messages count (that string, "physical device feature",
`VK_ERROR_INITIALIZATION_FAILED`, `VK_ERROR_INCOMPATIBLE_DRIVER`,
`VK_ERROR_FEATURE_NOT_PRESENT`, `VK_ERROR_EXTENSION_NOT_PRESENT`, "VulkanBackend is not
available/registered"), matched in the failure's cause chain, which the Android wrapper fills
with the runtime's recent log. Out of device memory is deliberately not on the list: that is
this model being too large for this GPU, and a smaller one may run. A corrupt file, a bad
tokenizer or a full window never costs the phone its GPU builds. The first version recorded
a refusal on any failure; Codex's first review caught it (section 5).

The record is kept per runtime release (`refused:1.5.1`), because a newer runtime can carry
shaders this driver does run: an update gets one fresh attempt.

### Where a refusal is caught

A shader is first dispatched wherever the runtime first runs, so every call into it is
wrapped (`ExecuTorchEngine.gpuChecked`): load, reopen, text prefill, picture prefill, the
warm-up of the system prompt, and generate. The first version wrapped only generate.

The same review found that the bridge called any failure mentioning `max_context_len` a full
context window. The wrapper appends the runtime's recent log to its exceptions, and the
runner logs `max_context_len` on every run in an ordinary information line, so a GPU failure
became "context full" and was never seen as a refusal. Only the runner's two overflow
diagnostics count now: "Max seq length exceeded" and "Prompt exceeds KV cache capacity".
ExecuServe had the same matcher and the same fix.

### Telling a GPU file from a CPU file

A `.pte` carries no metadata the app can read, and the runtime has no call that says which
delegates a loaded file uses ([executorch.md](executorch.md)), so the name decides.

- **The file's path before its repository's name.** A repository can hold several
  backends' folders, and its name speaks for one at most: `…-ExecuTorch-XNNPACK` holding
  `vulkan/model.pte` is a GPU file.
- **An installed name keeps the backend its folder gave it.** Installed files are named
  repository plus file, so `xnnpack/Qwen3-8da4w.pte` and `vulkan/Qwen3-8da4w.pte` would both
  become `Qwen3-ExecuTorch-Qwen3-8da4w.pte` and the second download would overwrite the
  first. A GPU file whose folder alone says `vulkan/` gets `-vulkan` added.
- **When a name mentions two backends, the last one wins.** An installed name is the
  repository's name followed by the file's, so the file speaks last. A `…-Vulkan`
  repository's `xnnpack/` file installs with `-xnnpack` added and reads as CPU; before
  `a23195d0` it read as GPU and the chat screen labelled it so.
- **Nothing already installed is renamed.** Our own files' names mention one backend or
  none, so they are unchanged. A copy saved under an older name, finished or partly
  downloaded, is still recognised as installed and completed where it is.

### ExecuServe

- The catalog reads `vulkan/config.json` as well as `xnnpack/config.json`, only where
  `VulkanSupport` says the GPU can run it, and labels those rows "GPU (Vulkan)".
- A refusal takes GPU rows off the Models screen at once (a `StateFlow` the screen
  collects), and the downloader checks for one when a download is queued and again when it
  starts, for the screen and for `tools/execuserve pull` alike.
- Each install's `execuserve.json` records its delegate folder (`"backend": "vulkan"`), and
  `/v1/models` reports `executorch-vulkan` or `executorch-xnnpack` from it. A file copied in
  by hand has no record, so only its name can say.
- GPU install ids always contain `vulkan` and CPU ids never do (a CPU file named for Vulkan
  is not listed), so a CPU and a GPU install can never share a folder.
- `pull vulkan/x.pte` takes exactly that file, never the `xnnpack/` file of the same name.

## 2. How it was tested

| Phone | SoC, GPU | Android, Vulkan | Reached through |
|---|---|---|---|
| Poco X8 Pro Max (`2602BPC18G`) | MT6991, Mali-G925-Immortalis MC11, 11.0 GiB | 16, 1.3 | Wireless debugging; on 2026-10-04 over the phone's own hotspot while it stayed on another Wi-Fi |
| QDC "Pineapple" | SM8650 (Snapdragon 8 Gen 3), Adreno 750, 11.0 GiB | 14, 1.3 | Qualcomm Device Cloud, adb through an SSH tunnel |
| QDC "Canoe" | SM8850 (Snapdragon 8 Elite Gen 5), Adreno | 16, 1.4 | Qualcomm Device Cloud, adb through an SSH tunnel |

Three harnesses, each answering a different question:

- **`llama_main`**, ExecuTorch's own C++ runner, built from v1.4.0 for Android with the
  Vulkan and XNNPACK backends linked (`examples/models/llama`). It isolates the delegate from
  the app: same prompt, `--temperature 0`, the runner's own timing. It ran before the apps
  could load Vulkan files.
- **`ExecuTorchOnDeviceTest`** in `core/engine/src/androidTest`, run with `am instrument`
  and the file passed as `-e pte` / `-e tokenizer`. Nine tests: it opens the file, answers a
  question legibly, stops mid-reply when asked, reports a reply cut by the token budget,
  answers with reasoning off, reuses the warmed system prompt, measures what a second turn
  costs (the cache must hold), measures prefill by piece size, and a throughput matrix: a
  937-token prompt, three turns, prefill and decode tokens per second and resident memory.
- **ExecuServe**: `tools/execuserve pull` from the catalog on the phone, `tools/execuserve`
  to serve, the `tools/compat` suites, and the server's own run history
  (`/v1/execuserve/runs?format=csv`) for timing.

**Two different CPU files appear below, and the difference matters.** On 2026-10-03 the
published CPU file was `xnnpack/Qwen3-0.6B-8da4w-2k.pte`, rounded to nearest. It has since
been replaced by `xnnpack/Qwen3-0.6B-8da4w-gptq-2k.pte` (GPTQ, gated against fp32). The Poco
comparisons use the first; the SM8850 comparisons use the second. Both are 8da4w with the
same kernels, so speed is comparable; answers are not.

## 3. Results

### 3.1 Correct on every GPU

| Phone | Harness | Result |
|---|---|---|
| Poco (Mali) | `llama_main`, 2026-10-03 | "The capital of France is **Paris**" |
| Poco (Mali) | openweights engine tests | 9 of 9 |
| Poco (Mali) | ExecuServe debug (`13085b1`) | Tokyo, Jupiter, a correct stream; `executorch-vulkan`; OpenAI 16/16, edge cases 28/28, Anthropic 8/8 |
| SM8650 | openweights engine tests | 9 of 9 |
| SM8650 | ExecuServe debug (`c67ec10`) | Tokyo; `/v1/models` said `executorch-xnnpack` for the GPU file, which is what `13085b1` fixed |
| SM8850 | ExecuServe release APK (`f46833d`) | Catalog pulls of both builds; records `vulkan` / `xnnpack`; greedy Tokyo, 51, Jupiter; 36-chunk stream; 5 of 5 requests |
| SM8850 | openweights release APK | Installs, launches, keeps running |
| SM8850 | openweights engine tests | 9 of 9 on the GPU build (twice) and 9 of 9 on the GPTQ CPU build, test APK from the release commit |

**The "Osaka" reply is the model, not the GPU.** The engine test's legibility check samples
at the app's default temperature of 0.8 and asks for the capital of Japan. It got "Osaka"
from the GPU build on the Poco and, on the SM8850, from both the GPU build and the GPTQ CPU
build; it got "Tokyo" on the SM8650 twice. (The check passes either way: it tests that the
reply is legible, not that it is right.) Greedy through `llama_main` on the Poco, four
questions on both files:

| Question | CPU file (round to nearest) | GPU file |
|---|---|---|
| Capital of Japan | **Osaka** | Tokyo |
| Capital of France | Paris | Paris |
| Largest planet | Jupiter | Jupiter |
| 17 × 3 | 51 | 51 |

So the 0.6B model puts enough weight on "Osaka" for sampling to find it on either backend,
the GPU build's greedy answer is right, and the round-to-nearest CPU build's greedy answer
is wrong (that file is no longer published). Nothing here points at a numerical fault in the
Vulkan delegate. ExecuServe's greedy requests on the SM8850 answered Tokyo on the GPU.

### 3.2 Speed

**Poco X8 Pro Max (Mali-G925).** `llama_main`, a 19 to 21-token prompt:

| | XNNPACK (CPU, 8 threads) | Vulkan (GPU) | CPU ÷ GPU |
|---|---:|---:|---:|
| Decode, tok/s | **52.1** | 18.1 (2026-10-03), 15.7 (2026-10-04) | 2.9 to 3.3 |
| Prefill, tok/s | **125.8** | 44.5, 91.3 | 1.4 to 2.8 |
| With one CPU thread: decode / prefill | 32.3 / 70.4 | 18.7 / 29.4 | |

The Vulkan decode does not move with the CPU thread count (18.1 against 18.7), so the work
is on the GPU. The engine's throughput matrix on the same phone, with the app's own runtime
(1.5.1) and a 937-token prompt, three turns:

| Vulkan, Poco | Turn 0 | Turn 1 | Turn 2 |
|---|---:|---:|---:|
| Prefill, tok/s | 415.0 | 415.9 | 423.2 |
| Decode, tok/s | 12.5 | 13.7 | 13.4 |

No CPU file was run on a long prompt on this phone, so which backend reads long prompts
faster here is not measured.

**Snapdragon 8 Gen 3 (SM8650, Adreno 750).** Engine matrix, Vulkan: prefill 585.6, 638.7
and 639.2 tok/s, decode 40.6, 40.4 and 40.7 tok/s. No CPU file was run on this phone.

**Snapdragon 8 Elite Gen 5 (SM8850).** ExecuServe release build, the server's own timing,
greedy, 7 threads, thermal status none, three runs of each (the CSV is in the results
folder):

| | CPU (GPTQ) | GPU | GPU ÷ CPU |
|---|---:|---:|---:|
| 27-token prompt: prefill, tok/s | 540, 900, 931 | 794, 871, 900 | about 1 |
| 27-token prompt: decode, tok/s | **123.1, 124.8, 127.5** | 80.9, 90.8, 92.5 | 0.7 |
| 702-token prompt: prefill, tok/s | 746, 749, 771 | **901, 1,007, 1,180** | 1.4 |
| 702-token prompt: decode, tok/s | 49.6, 50.4, 51.4 | **61.3, 61.7, 63.9** | 1.2 |

The engine's matrix on the same phone (937-token prompt, three turns):

| SM8850, engine | Prefill, tok/s | Decode, tok/s | Resident |
|---|---|---|---|
| Vulkan | 1,046.9, 968.0, 1,116.8 | 54.3, 54.8, 51.4 | 264 to 268 MB |
| XNNPACK (GPTQ) | 556.1, 550.2, 548.3 | 38.9, 38.0, 38.1 | 1,164 to 1,166 MB |

Through the engine the GPU reads the 937-token prompt 1.9 times as fast and decodes 1.4
times as fast. The engine feeds a prompt in 800-character pieces where ExecuServe feeds it
whole, which is why the CPU's long-prompt figures differ between the two tables (550 against
750 tok/s); the piece-size test on the same phone read 525 to 604 tok/s on the CPU and 862 to
1,189 on the GPU across pieces of 400 to 6,400 characters. Decode slows as the context fills
on both backends, but less on the GPU, which is why the GPU leads with a long prompt and
trails with a short one.

### 3.3 Memory

- **Resident memory is not comparable across GPU families.** The same Vulkan file showed
  222 to 249 MB resident on the SM8650 and 264 to 268 MB on the SM8850 (where the CPU file
  showed 1,164 to 1,166 MB), but 2,230 to 2,249 MB on the Mali phone. On the Poco, `llama_main` reported 1,047 MiB for the CPU file and 1,680
  MiB after loading the GPU file, rising to 2,201 MiB by the end of the reply. Where a driver
  places GPU buffers decides what the process is charged for; the number is not the model's
  size.
- **The Vulkan file is larger**: 616 MB against 497 MB for the CPU file at 2k (the 16k and
  32k GPU files are 704 MB and 805 MB).
- **32k does not fit a 12 GB phone.** The 32k Vulkan file loaded on the Poco and was killed
  while allocating its 7.5 GB fp32 KV cache, taking adb down with it. Its `config.json`
  already says `fits_phone_budget: false`.

### 3.4 The 1.5.1 runtime opens 1.4.0 files

Before the Vulkan work, on the Poco with the 1.5.1 runtime and the XNNPACK path: the 1.4.0
export `Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK` answered "Tokyo", the warmed head was
reused (1,114 tokens cached), a second turn cost 4% of the first's prefill, and the matrix's
first turn read 937 tokens at 132 tok/s and decoded at 14.0. A 1.5.1 export of
SmolLM2-135M answered too. One snag on the way was ours: the test's tokenizer file was
misnamed, and the engine refused the model with its "no tokenizer beside it" message, as it
should.

## 4. What this decides, and why

- **Ship the GPU path, and keep the CPU path beside it.** It is correct on every GPU tried,
  costs no second library, and a phone whose driver cannot run the shaders loses only its
  GPU offers, once, with the reason recorded.
- **On Mali, the CPU build is the one to use.** Decode is what a reader waits on, and the
  Mali GPU decodes at a third of the CPU's speed with this delegate. That matches
  llama.cpp's Vulkan on the same GPU in [gpu-backends.md](gpu-backends.md) only in its
  verdict, not its shape: there prefill was the loss and decode a small win.
- **On a recent Adreno, it depends on the conversation.** On the SM8850 the GPU reads long
  prompts 1.4 to 1.9 times as fast and decodes 1.2 to 1.4 times as fast with a long prompt
  behind it, and decodes a short exchange at 0.7 times the CPU's speed. The SM8650 had no
  CPU run, so no ratio is claimed for it.
- **Only Qwen3-0.6B was measured.** Larger models move the balance (the GPU's advantage
  grows with arithmetic per token), and the hybrid families are untested on 1.5.1's Vulkan
  delegate: 1.4.0's segfaulted on LFM2.5 at the first prefill
  ([executorch-state-and-recipes.md](executorch-state-and-recipes.md), "Another backend").

## 5. QA: six Codex rounds on the app code

Every review ran `gpt-6.1-sol` at medium reasoning in a read-only sandbox, so none ran builds
or tests; each fix was tested before the next round. Outputs and the exact prompts are in
[`codex/`](../../tools/eval/results/vulkan-2026-10-04/codex/).

| Round | Reviewed | Found | Outcome |
|---|---|---|---|
| 1.5.1 bump | openweights runtime bump | Nothing: binding classes identical, R8 rules, ABIs and 16 KB alignment unchanged | Pushed |
| 1.5.1 bump | ExecuServe runtime bump | A 1.5.2 file would not warn (patch ignored); the README still said 1.4 | Fixed; re-review clean |
| 1.5.1 bump | execupack pins | Nothing | Merged |
| Vulkan 1 | Both apps, uncommitted | Any failure disabled Vulkan for good; prefill, warm-up and picture prefill bypassed the check; `max_context_len` in normal logs hid GPU errors; the CPU and GPU builds of one file installed over each other; a repository name overrode the file's path; ExecuServe's list stayed stale after a refusal | All fixed |
| Vulkan 2 | Both apps, after the fixes | The bridge's overflow matcher (openweights); install ids still colliding across folders (ExecuServe); a Vulkan-named repository suppressing the folder suffix; `pull` matching basenames before paths | All fixed, then committed (`55e4f68`, `c67ec10`, `13085b1`) |
| Vulkan 3 | The pushed commits | Every earlier fix confirmed; five narrow cases: a CPU file in a Vulkan-named repository labelled GPU; GPU rows stale until reload; ids colliding for files named `…-vulkan`; a pull in flight enqueuing after a refusal; the backend reported from names | All fixed (`a23195d0`, `f46833d`) |
| Vulkan 4 to 6 | Those fixes | ExecuServe clean at once. openweights: the new names would orphan a copy saved under the old name, then a half-downloaded one | Fixed; round 6 found none |

## 6. What was recorded, and what was not

The question this note was written to answer included "did we write to any logs, did we
record the numbers?" Mostly no, at the time, and this is the account.

- **Captured to files when they ran:** the raw `am instrument` output of the Poco's engine
  run and of the first SM8850 engine run (pass or fail and timing only), and the Codex
  outputs. All of them sat in a temporary directory on the workstation until this note.
- **Read to the terminal and not saved:** every logcat line with the engine's numbers on the
  Poco and the SM8650, every `llama_main` run, ExecuServe's suites and replies. They survive
  only as the session's terminal output, and are committed here transcribed from it, each
  file saying so, with keys redacted.
- **Lost and measured again:** the first SM8850 engine run's logcat (cleared before the run,
  then rolled out of the buffer), and ExecuServe's run history from that session (empty
  after the server restarted). Both were re-measured on the same phone while it was still
  connected, captured to files this time: the engine test on both builds (which also gave
  the SM8850 its CPU engine numbers), and ExecuServe's CSV of twelve runs.
- **Never measured:** ExecuServe's decode speed on the Poco (its replies were checked, not
  timed), a CPU long-prompt run on the Poco, and any CPU run on the SM8650.
- **QDC's own session logs** (`/data/local/tmp/QDC_logs` on the device) were not pulled, and
  the device is wiped when a session ends.

## Open

- **Recommend the CPU build where it is faster.** Both apps offer the two builds without a
  preference. The data says CPU on Mali and depends-on-length on Adreno; a rule needs a CPU
  long-prompt run on the Poco and a CPU run on an SM8650 first.
- **Larger models and the hybrid families on 1.5.1's Vulkan delegate.**
- **Sustained runs.** Every number here is from a cool phone over a few minutes.

## Reproducing

```sh
# The engine tests against any .pte on the phone (the test APK from :core:engine:assembleDebugAndroidTest)
adb install -r -t core/engine/build/outputs/apk/androidTest/debug/engine-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class io.github.alpharomercoma.openweights.core.engine.ExecuTorchOnDeviceTest \
  -e pte /data/local/tmp/owvk/Qwen3-0.6B-vulkan-8da4w-2k.pte \
  -e tokenizer /data/local/tmp/owvk/Qwen3-0.6B-vulkan-8da4w-2k.tokenizer.json \
  io.github.alpharomercoma.openweights.core.engine.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s ExecuTorchOnDevice:I    # the numbers are logged here, not in the instrument output
```

On a Qualcomm Device Cloud handset, adb runs on Qualcomm's side of the tunnel, so
`adb forward` binds there and not on the workstation: call a server on the phone from
`adb shell`. Download models on the device with `curl` rather than pushing them; the tunnel
moved about 100 KB/s on the SM8650 session.
