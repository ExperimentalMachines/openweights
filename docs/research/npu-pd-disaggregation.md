# NPU prefill, CPU decode: what works and what the handoff costs

**Measured 2026-09-22 on the Poco X8 Pro Max, MediaTek MT6991 (Dimensity 9400),
Android 16 / HyperOS.** Phone awake, unlocked and idle for every timing.

## Decision

**The path runs and is off.** The NPU half now loads inside the app, prefills a real
prompt at 148 to 302 tok/s, and hands its state to the CPU half in under a millisecond.
The reply that comes back is wrong, and the reason is not the plumbing: the compiled
A16W4 chunks and the 8da4w GPTQ CPU export do not agree closely enough on what a key is
for one to continue the other's prompt. Turning the path on needs both halves exported
from the same quantisation, which is exporter work, not app work.

Against that stands [`npu-prefill-multiturn.md`](npu-prefill-multiturn.md): on 138 real
turns decode is 60 to 77% of wall time, a turn prefills a median of 50 tokens after cache
reuse, and the whole offload projects to 1.07x to 1.47x. [`first-turn-latency.md`](first-turn-latency.md)
then took the one case with a large prefill, the cold first turn, from 18.5 s to under a
second by warming the prefix. The workload the NPU would help with has already been
answered twice, so the bar for spending 102 MB of native libraries on this is high.

What did come out of it is a crash fix that matters on every MediaTek phone, and five
defects in the disaggregated runner that would have been charged to the NPU.

## The app could not load the NeuroPilot adapter at all

Every build before this one died at launch on this phone. Two different crashes, one
cause.

Bundling MediaTek's SDK copy of `libneuronusdk_adapter.mtk.so` in the APK gives
`SIGSEGV` at `pc=0x0` inside `.init_array`, before a line of our code runs: the library
resolves its own dependencies by name against `/vendor/lib64` and calls what it finds,
and in an app's linker namespace it finds nothing and calls null.

Removing it from the APK moves the crash later, to `SIGABRT` inside
`NeuronSharedWeights`, with this above it in the log:

```
E AdapterShimApi: Unable to open library libneuronusdk_adapter.mtk.so
E AdapterShimApi: Unable to open library libneuron_adapter_mgvi.so
E AdapterShimApi: Unable to open library libneuron_adapter.so
E AdapterShimApi: Unable to open function NeuronMemory_createFromAHardwareBuffer
E NeuronBackend: Check fail: memory == nullptr ... NeuronBufferAllocator.h
E NeuronBackend: allocate MemoryUnit failed
```

`NeuronBackend::init` calls `ET_CHECK` there, and `ET_CHECK` aborts the process. A phone
that cannot reach the adapter does not get an error, it gets a dead app.

The adapter is on the phone, at `/system_ext/lib64/libneuronusdk_adapter.mtk.so`, and it
exports exactly the API the backend wants, `NeuronMemory_createFromAHardwareBuffer`
included. MediaTek publishes it to apps in `/system/etc/public.libraries-mtk.txt`,
alongside the `libapuware*.mtk.so` driver libraries. The linker still refused it:

```
dlopen failed: library "/system_ext/lib64/libneuronusdk_adapter.mtk.so" needed or
dlopened by "/apex/com.android.art/lib64/libnativeloader.so" is not accessible for the
namespace "clns-10"
```

It resolved the name to the right file and then rejected it, because from API 31 an app
must name a vendor public library in its manifest before the linker will link it in.
Declaring them fixed it:

```xml
<uses-native-library android:name="libneuronusdk_adapter.mtk.so" android:required="false" />
```

with the same for the eight `libapuware*.mtk.so` and `libneuron_sys_util.mtk.so`, all
`required="false"` so the app still installs on a phone that has none of them. After
that the NPU loads in the app in 1.2 to 1.6 s and `apuware_server` starts taking perf
locks, which is the platform saying the accelerator is genuinely in use.

**Bundling the SDK copy is not an alternative to this, it is the thing that crashes.**
The device's own copy is the one built for an app namespace.

## Five defects that were not the NPU's fault

Found and fixed while getting a reply out of the path at all. Four of them would have
been charged to the accelerator by anyone reading the numbers.

| What was seen | Cause | Fix |
|---|---|---|
| Debug build died mid reply, `JNI DETECTED ERROR: input is not valid Modified UTF-8` | `NewStringUTF` on a token holding half a multi byte character, which a BPE tokenizer emits for any non ASCII text | Whole characters only, the trailing partial sequence waits for the next token |
| Every reply ran to the token limit | Stop ids hard coded to 124900 and 124894, from a 128k vocabulary; this model's is 64402 so nothing ever matched | Resolved from the tokenizer at load, `[2, 7]` here |
| The app kept one core for itself after a reply | Decode pinned the calling thread to cpu7 and never restored the mask, on a thread shared with the rest of the app | Mask saved and restored, and the core is chosen by reading `cpu_capacity`, not by knowing what a Dimensity 9400 is |
| Prefill predicted padding and end of turn | The runner options were MediaTek's defaults: 16 heads and int16 activations against the 32 heads and fp32 the graphs were compiled for | Read from the `runner` block the exporter writes beside the chunks |
| Six of sixteen layers never handed off | The state scanner matched an attention cache only at sequence length 2048; this export is 32768 | Scanner takes the state block as the contiguous run of state tensors and reads the geometry off it |

Nothing in a `.pte` declares how many heads it has or what type its inputs are, so the
fourth one is structural: **the export manifest is the only source for the runner
options, and guessing them is not a small error.** With 16 heads and int16 the prefill
predicted token 0 and token 2, which are padding and end of turn. With the manifest's 32
heads and fp32 it predicts ordinary words.

## What the handoff actually does

The NPU keeps every state tensor in one shape, `[1, 8, 512, 64]`, 1 MB each, eight slots
per chunk, sequence on dim 2 and right aligned in the window. The CPU export keeps
attention as `[1, 32768, 8, 64]`, sequence major and left aligned, and short conv state as
`[1, 2048, 2]`. The handoff transposes the prompt's own positions and nothing else.

The slot mapping is confirmed rather than assumed: ten of the thirty two slots hold data
at the head of the buffer, which is where a conv state sits and where a right aligned
cache is still zero, and those ten are at exactly the chunk offsets of the model's ten
conv layers. Treating the slots as per layer K/V pairs instead reads an untouched slot,
so the layout is all K then all V.

Timing, 16 layers, 15 to 205 token prompts:

| Stage | Measured |
|---|---|
| NPU chunk load | 1.2 to 1.6 s |
| CPU module load | 16.5 to 17.2 s |
| NPU prefill | 334 to 794 ms, 148 to 302 tok/s |
| State handoff | 0.09 to 0.72 ms |
| CPU decode | 16.6 to 17.8 tok/s, 58 to 60 ms a token |

The handoff is free. That was the thing worth knowing and it is true.

## Why the reply is still wrong

Same runner, same prompt, same decode loop, one flag apart:

- CPU prefill, no handoff: *"The capital of Japan is Tokyo, which is there because it is
  the largest and most populous city in the country, serving as the political, economic,
  and cultural center."*
- NPU prefill, handoff, CPU decode: *" there there there there there ..."* to the token
  limit.

The caches are the same quantity and the alignment is right. Comparing the transferred
keys against the ones the CPU computes for itself, position by position, cosine is
**0.9892 at prompt token 7 and 0.9746 at token 14**, both at exactly the right aligned
position the handoff assumes. So the design holds.

What does not hold is the agreement. Mean absolute error on the same keys is **0.33 to
1.14 across the six attention layers** and 0.011 to 0.19 across the ten conv layers,
growing with depth, on values whose own scale is about 1. Prompt token 0 is worse again,
cosine 0.39, and restoring it from the CPU's own state changes nothing about the reply,
so the attention sink is not carrying this on its own.

That gap is what an A16W4 NeuroPilot compilation and an 8da4w GPTQ XNNPACK export
disagree by. Four bit weights on one side and a different four bit grid on the other do
not produce the same key for the same token, and a decoder asked to continue from keys
that are 30% off gives up and repeats itself.

**The fix is an export, not a patch.** Both halves have to come from one quantisation
before the handoff can be judged again. Until then the numbers above are what the path
costs, not what it is worth.

## How it is wired, and how to turn it on

The libraries live in `core/engine/src/debug/jniLibs/arm64-v8a`, so a release build never
carries them and `DisaggregatedBridge.isAvailable` is false there by construction. They
are git ignored: 102 MB of vendor and generated binaries do not belong in this repository.

| File | Size | Where it comes from |
|---|---|---|
| `libexecutorch_pd_jni.so` | 23.6 MB stripped | `ninja executorch_pd_jni` in the ExecuTorch tree, from `examples/mediatek/executor_runner/mtk_pd_disaggregated_jni.cpp` |
| `libneuron_backend.so` | 77.8 MB | `ninja neuron_backend`, ExecuTorch `backends/mediatek` |
| `libneuron_buffer_allocator.so` | 771 KB | NeuroPilot Express SDK 8.0.8-build20250925, verbatim |
| `libneuronusdk_adapter.mtk.so` | not shipped | the phone's own, from `/system_ext/lib64` |

Even in a debug build the path stays on the CPU until an `enable-pd` file sits beside the
chunks. That is deliberate: the reply is wrong, and a wrong reply that reads fluently is
worse than no feature. Touching the marker turns it on for one measurement run without a
rebuild.

Chunks are found beside the model first. A staging directory under
`/data/local/tmp/mtk_models` is also searched, because on this ROM a file pushed into
`Android/data` by `adb` is invisible to the app: the app's view of its own external
directory is a bind mount that never picks up shell's writes, and a pushed chunk set can
only be read from there. That cost an hour before it was believed, so it is written down.

## What would reopen this

- Both halves exported from one quantisation, and the per layer MAE above falling far
  enough that the CPU decode continues the NPU's prompt.
- A workload where prefill is not a median of 50 tokens. There is not one in the app
  today; the warm prefix removed the only one there was.
