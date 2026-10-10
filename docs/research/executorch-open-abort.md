# A `.pte` the runner refuses closes the app

2026-10-11. Found in an adversarial QA of vc650 (ExecuTorch 1.5.1, the Vulkan AAR) on
2026-10-09, fixed and verified on the Poco X8 Pro (MT6991, Android 16) on 2026-10-11.

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

So it is not R8. fbjni asserts that a C++ exception is in flight and finds none, which is
what happens when the library that threw and the one translating use different C++
runtimes; `libexecutorch.so` lists no `libc++_shared.so` among its dependencies. Kotlin
cannot catch it. The fbjni keep rule added for vc541 (the comment in `core/engine/consumer-rules.pro`,
same `ptr` message) was proven by loading a valid model, which never reaches this path.

A plain launch with such a file present does not crash: the model picker's probe uses
`Module`, not `LlmModule`, and a failed load does not become the last-used model.

## Why vc650 made it reachable

047b8b92 added `NEUROPILOT` to `ExecuTorchSupport.BACKENDS`, the set Discover offers. The
release bundle carries neither `libneuron_backend.so` nor `libexecutorch_pd_jni.so`, and the
stock runtime registers only `XnnpackBackend` and `VulkanBackend`. On the Poco, production
vc650's page for the first recommended model listed the NPU chunks as "Compiled for NPU",
270 to 400 MB each, beside the five CPU files.

## The fix

- Discover offers only what this build opens: `NEUROPILOT` is out of `BACKENDS`. The NPU
  path itself is unchanged; it never needed Discover, because it reads chunks staged beside
  a CPU `.pte` with an enable marker.
- The engine refuses before the runner can abort. `NativeExecuTorchBridge.probe` now reports
  `ExportFacts.unopenable`:
  - the extended header, read in Kotlin, says the file is not an ExecuTorch program or ends
    before its segments do (on every export checked the file ends exactly at segment base
    plus segment bytes);
  - a method names a delegate not in `ExecuTorchRuntime.getRegisteredBackends()`;
  - `get_max_seq_len` is missing, which both runners require first.
  A probe that throws is also a refusal now; before, the runner was asked anyway.

## Verified on the Poco

Minified release build with a temporary `.rc` package suffix, beside the Play install:

| File | Before (vc650) | After |
|---|---|---|
| NPU chunk 1 of 4 | listed in Discover; SIGABRT on open | not listed; "cannot be opened: it was compiled for NeuropilotBackend, which this build of the app does not include" |
| Software Mansion's LFM2.5 1.2B cut to 120 MB | not tried on the phone | "the file stops at 120 MB of 759 MB, so the download did not finish" |
| An HTML error page saved as `.pte` | not tried on the phone | "the file is not an ExecuTorch program" |
| The whole LFM2.5 1.2B xnnpack `.pte` | answers | answers: Paris, then Tokyo with 12 prompt tokens on the second turn, 31 tok/s decode |
| LFM2.5 1.2B Q4_K_M GGUF, online | answers | answers, tools offered, the denial repair wrote the haiku |

Not tested: a `.pte` exported with ExecuTorch 1.5.x, and a Vulkan export through the
release build.

## Also in this change

Watches made before schema 20 have no provenance on their saved summary. The runner read
that as private and untrusted, every search then needed an approval no one is there to give,
and the refused check recorded itself as private again, so such a watch never searched
again. The summary is now withheld from the model when its origin is unknown, and the check
runs as a watch's first one does. Unit-tested; not reproduced on a device.

The off-by-one between the exporter's token bound and `get_max_seq_len` is reported upstream
(pytorch/executorch#23679). The app already keeps each prefill call under
`get_max_seq_len - 1` tokens (`ExportFacts.prefillLength` and `ExecuTorchEngine.callChars`),
which is safe for both the old bound and the fixed one, and the Java binding exposes no input sizes to
read the bound from, so nothing changed there.
