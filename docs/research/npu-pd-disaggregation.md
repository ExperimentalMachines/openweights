# NPU prefill, CPU decode: what works and what it costs

**Measured 2026-09-22 on the Poco X8 Pro Max, MediaTek MT6991 (Dimensity 9400),
Android 16 / HyperOS.** Phone awake, unlocked and idle for every timing.

## Decision

**It works, and it stays off for now, on cost rather than correctness.** The NPU half
loads inside the app, prefills at 60 to 327 tok/s, hands its state to the CPU half in
under a millisecond, and the reply that comes back is correct. Two things stop it being
worth turning on today:

- **The NPU export holds 512 tokens.** A prompt longer than that prefills and then hands
  over a window the CPU half never saw the start of, so the runtime refuses it. The app's
  own prompts run to about 2,050 tokens once the tool prefix is in, which is four times
  the window.
- **The decode half of this runner is about half the speed of the app's own CPU path**,
  13 to 18 tok/s against the 27 tok/s the app gets from the same `.pte`. Decode is where
  the time goes, so as it stands the whole path is slower end to end than doing nothing.

Neither is a property of the accelerator. The first is an export flag, the second is a
hand written decode loop that the app's `LlmModule` beats. Both are fixable, and until
they are there is nothing to switch on.

Against that stands [`npu-prefill-multiturn.md`](npu-prefill-multiturn.md): on 138 real
turns decode is 60 to 77% of wall time, a turn prefills a median of 50 tokens after cache
reuse, and the whole offload projects to 1.07x to 1.47x. [`first-turn-latency.md`](first-turn-latency.md)
then took the one case with a large prefill, the cold first turn, from 18.5 s to under a
second by warming the prefix.

What did come out of it regardless is a crash fix that matters on every MediaTek phone.

## A correction

**An earlier version of this note said the handoff was blocked by a quantisation
mismatch. That was wrong, and the error was in the test, not the runtime.**

The prompt used was a bare sentence with no chat template. An instruct model given raw
text simply continues it, and this one degenerates: MediaTek's own runner, NPU prefill
and NPU decode, no handoff anywhere, answers *" there there there there ..."* to the same
raw prompt. The CPU half happened to handle it gracefully, so the degeneration was
charged to the handoff between them.

Under the model's own chat template every configuration answers:

| Path | Reply |
|---|---|
| NPU prefill, NPU decode | "The capital of Japan is Tokyo, and it is there because it serves as the political and economic hub of the country." |
| NPU prefill, handoff, CPU decode | "The capital of Japan is Tokyo, and it serves as the political and economic center of the country." |
| CPU prefill, CPU decode | "The capital of Japan is Tokyo, which is there because it is the largest and most populous city in the country." |

Four prompts through the disaggregated path, all correct, including "What is 17 times
24?" answered as 408. **The control that proves the handoff is doing the work**: run the
same thing with the state handoff skipped and nothing else changed, and the CPU decode
emits `TheCK::::::::::::`. The transferred state is what makes the reply.

The per layer error is real and was measured correctly: mean absolute error 0.33 to 1.14
across the six attention layers, 0.011 to 0.19 across the ten conv layers. It is simply
tolerable. Cosine against the keys the CPU computes for itself is 0.9892 at prompt token
7 and 0.9746 at token 14, at exactly the right aligned positions the handoff assumes, and
a decoder continues happily from that.

The lesson is cheap to state and was expensive to learn: **never judge a model's output
on a prompt that is not in the template it was trained for**, and when two halves are
being compared, test each half alone before blaming the seam.

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

The pinning was worth keeping, which is not what was expected. Measured both ways on the
same prompt: pinned 13.02 tok/s, unpinned 10.58 tok/s. The core is still chosen from the
kernel's capacity table rather than by name.

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

| Stage | Measured |
|---|---|
| NPU chunk load | 1.2 to 1.6 s |
| CPU module load | 16.5 to 17.2 s |
| NPU prefill | 60 to 327 tok/s, rising with prompt length |
| State handoff | 0.09 to 0.75 ms |
| CPU decode, this runner | 13 to 18 tok/s |
| NPU decode, MediaTek's runner | 10.5 to 11.2 tok/s |
| CPU decode, the app's own path, same `.pte` | 27 tok/s |
| CPU prefill, the app's own path, same `.pte` | 188 tok/s |

Two readings matter. **Splitting the work the right way round is confirmed**: CPU decode
beats NPU decode on the same model, so prefill on the accelerator and decode on the CPU
is the correct split, not the reverse. And **the prefill win is 1.7x, not the 5x the raw
NPU number suggests**, because the comparison that counts is against the app's own
KleidiAI prefill at 188 tok/s, not against nothing.

## The 512 token window

The chunks are compiled at `cache_size=512`; the export report records it as "forced with
`--context` (static NPU graphs)". A 1,098 token prompt prefills on the NPU at 326.9 tok/s
and then cannot be handed over: the older tokens have rolled out of the window, so the
CPU half would answer from the tail of the prompt alone.

**The runtime now refuses that rather than answering.** A fluent reply to a question the
model only half read is the worst failure available here, worse than an error, so
`Prefill` returns zero and the bridge raises instead of decoding from a truncated
context. Reopening this needs an export at a window that covers a real prompt.

## How it is wired, and how to turn it on

The libraries live in `core/engine/src/debug/jniLibs/arm64-v8a`, so a release build never
carries them and `DisaggregatedBridge.isAvailable` is false there by construction. They
are git ignored: 102 MB of vendor and generated binaries do not belong in this repository.

| File | Size | Where it comes from |
|---|---|---|
| `libexecutorch_pd_jni.so` | 23.6 MB stripped | `ninja executorch_pd_jni` in the ExecuTorch tree, from `tools/npu/mtk_pd_disaggregated_jni.cpp` |
| `libneuron_backend.so` | 77.8 MB | `ninja neuron_backend`, ExecuTorch `backends/mediatek` |
| `libneuron_buffer_allocator.so` | 771 KB | NeuroPilot Express SDK 8.0.8-build20250925, verbatim |
| `libneuronusdk_adapter.mtk.so` | not shipped | the phone's own, from `/system_ext/lib64` |

Even in a debug build the path stays on the CPU until an `enable-pd` file sits beside the
chunks. Touching the marker turns it on for a measurement run without a rebuild.

Chunks are found beside the model first. A staging directory under
`/data/local/tmp/mtk_models` is also searched, because on this ROM a file pushed into
`Android/data` by `adb` is invisible to the app: the app's view of its own external
directory is a bind mount that never picks up shell's writes, and a pushed chunk set can
only be read from there. That cost an hour before it was believed, so it is written down.

## What would turn it on

- An export whose window covers a real prompt, so the 512 token refusal stops firing.
- A decode loop that matches the app's own 27 tok/s rather than 13 to 18, since decode is
  most of the wall clock and the path cannot win while it loses there.
- Then the 1.7x prefill against the app's CPU path, measured on real multi turn traffic
  rather than on single questions, against the 1.07x to 1.47x ceiling already on file.
