# A `.pte` the runner refuses closes the app

2026-10-11. Found in an adversarial QA of vc650 (ExecuTorch 1.5.1, the Vulkan AAR) on
2026-10-09, fixed over three Codex review rounds and verified on the Poco X8 Pro (MT6991,
Android 16) on 2026-10-11.

## What happened

Opening any `.pte` that `LlmModule` cannot load killed the process instead of showing a
message:

```
F libc    : Fatal signal 6 (SIGABRT) ... (DefaultDispatch)
F DEBUG   : Abort message: 'ptr'
  #04 libfbjni.so (facebook::jni::log_::logassert)
  #05 libfbjni.so (facebook::jni::getJavaExceptionForCppException(std::exception_ptr)+204)
  #06 libfbjni.so (facebook::jni::translatePendingCppExceptionToJavaException()+48)
  #07 libexecutorch_jni.so
  #10 org.pytorch.executorch.extension.llm.LlmModule (init)
E ExecuTorch: Required metadata method get_max_seq_len not found in model
```

The file that showed it was one of the sixteen MediaTek NPU chunks in
`experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch` (`mtk/mt6991/...-chunk1of4.pte`),
which has no window methods because it is half of a disaggregated model.

It reproduced on every build tried, on the arm64 emulator:

| Build | Runtime | Result |
|---|---|---|
| vc650, the exact production bundle (sha256 `ddeb9e0b...`) | 1.5.1 Vulkan | SIGABRT `ptr` |
| vc615, rebuilt from `eac97a1a` | 1.4.0 XNNPACK | SIGABRT `ptr` |
| debug build of `405168f1`, not minified | 1.5.1 Vulkan | SIGABRT `ptr` |

So it is not R8. Every error the 1.5.1 JNI layer raises through `throwExecutorchException`
(`extension/android/jni/jni_helper.cpp`) goes this way: the `LlmModule` constructor's catch
blocks and `load()`, which is where an unreadable tokenizer or missing metadata lands, and the
plain `Module` constructor, `getMethods`, `getUsedBackends` and `execute`. `generate` and
`prefill` return status codes instead, and the Kotlin wrapper raises those, so errors after a
model is open (a full window among them) still arrive as exceptions.

The likely cause, from the symbols: `libexecutorch.so` carries its own C++ exception runtime
(it imports no `__cxa_*` exception function and does not depend on `libc++_shared.so`) but
imports fbjni's `translatePendingCppExceptionToJavaException`, which lives in `libfbjni.so` and
asks `libc++_shared.so` for the exception in flight. It finds none, and fbjni's assertion that
there is one is the `ptr` abort. ExecuTorch's own `extension/android/CMakeLists.txt` warns that
the two must share a C++ runtime. This is an inference from the binaries, not a native
experiment. No upstream issue or pull request describes it (searched 2026-10-11); #10444 asks
to drop fbjni, and #18128 and #19099 routed more errors through this helper. The fbjni keep rule
added for vc541 (the comment in `core/engine/consumer-rules.pro`, same `ptr` message) was proven
by loading a valid model, which never reaches this path.

A file whose header is sound but whose program is not is worse: the loader reads out of bounds
(SIGSEGV), and it does so from the model picker's probe, which reads every `.pte` on each
refresh. On the Poco a debug build with such a file in its models folder closed at every launch.

## Why vc650 made it reachable

047b8b92 added `NEUROPILOT` to `ExecuTorchSupport.BACKENDS`, the set Discover offers. The
release bundle carries neither `libneuron_backend.so` nor `libexecutorch_pd_jni.so`, and the
stock runtime registers only `XnnpackBackend` and `VulkanBackend`. On the Poco, production
vc650's page for the first recommended model listed the NPU chunks as "Compiled for NPU",
270 to 400 MB each, beside the five CPU files.

## The fix

- **A process of its own for every first read.** `PteProbeService` runs in `:pte_probe`. The
  model picker's probe happens there, and before the engine opens a file it opens it there too,
  through the same bridge, then closes it. If the runtime crashes, that process ends, `PteProbe`
  reads the exit reason (`getHistoricalProcessExitReasons`), and only a native crash (or a crash
  signal) is blamed on the file. The refusal is remembered per file, tokenizer and runtime
  release, so a crashing file is not handed to the runtime again on every launch. A death that is
  not a crash, or no answer in time, is asked once more and then refused for now without being
  remembered; nothing is ever read in the app's own process as a fallback. One request at a time,
  so a crash or timeout is attributed to the request that caused it. Starting the probe process
  starts nothing else of the app (`OpenWeightsApplication`).
- **Checks before the runtime.** `ExportFacts.unopenable`: the extended header, read in Kotlin
  (not an ExecuTorch program, or a download that ends before its segments; on every export
  checked the file ends exactly at segment base plus segment bytes); a delegate the runner would
  load that is not in `ExecuTorchRuntime.getRegisteredBackends()`; no `get_max_seq_len`. An empty
  tokenizer is refused by the engine.
- **Discover offers only what this build opens.** `NEUROPILOT` is out of `BACKENDS`. The NPU
  path itself never needed Discover: it reads chunks staged beside a CPU `.pte` with an enable
  marker.

## Verified on the Poco

Minified release build with a temporary `.rc` package suffix, beside the Play install. The app
stayed up in every row.

| File | Production vc650 | Fixed |
|---|---|---|
| NPU chunk 1 of 4 | listed in Discover; SIGABRT on open | not listed; "compiled for NeuropilotBackend, which this build of the app does not include" |
| Header-valid program with a garbage body, in the models folder | (debug build before the probe process: SIGSEGV at every launch) | three launches: the probe process crashed once and the refusal was remembered; open: "the ExecuTorch runtime crashed reading it" |
| Root offset past the program | not tried | same |
| LFM2.5 1.2B `.pte` cut to 120 MB | not tried | "the file stops at 120 MB of 759 MB, so the download did not finish" |
| An HTML error page saved as `.pte` | not tried | "the file is not an ExecuTorch program" |
| A sound `.pte` with an empty tokenizer | not tried | "its tokenizer file is empty" (before that check: the runtime opened it as an empty TikToken and failed every prompt) |
| A sound `.pte` with a garbage tokenizer | not tried | the trial open crashed in the probe process; refused; with the right tokenizer put back it was checked again and opened |
| The whole LFM2.5 1.2B xnnpack `.pte` | answers | first open about 9 s to ready including a 1.7 s trial open; later opens about 5 s, as before; Madrid, then Lisbon with 11 prompt tokens on the second turn |
| LFM2.5 1.2B Q4_K_M GGUF, online | answers | answers with tools offered |

Not tested on a device: a `.pte` exported with ExecuTorch 1.5.x, a Vulkan export through the
release build, a vision export through the new checks, a trial open of a multi-gigabyte model on
a low-memory phone, and the NPU path's trial (it runs only on debug builds with the NeuroPilot
libraries).

## Watches, removed instead of repaired

The same QA found that watches made before schema 20 never searched again after vc650: their
saved summary had no provenance, was read as private, and every check recorded itself as
private again. A repair was written and reviewed, and then the feature was removed outright on
2026-10-11. Schema 21 drops the tables, and the application cancels the periodic work
WorkManager still holds for watches and deletes their notification channel at start-up.

## The prefill bound

The off-by-one between the exporter's token bound and `get_max_seq_len` is reported upstream
(pytorch/executorch#23679, open). The engine keeps each prefill call under `get_max_seq_len - 1`
UTF-16 characters (`ExportFacts.prefillLength` and `ExecuTorchEngine.callChars`), which bounds the
tokens only for a tokenizer that never makes more than one token per character; a byte-fallback
tokenizer fed unusual text can exceed it. The Java binding exposes no input sizes and no
tokenizer, so neither the bound nor a token count can be read from the app, and nothing changed
here.
