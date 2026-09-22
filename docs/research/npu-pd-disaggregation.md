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
- **The decode half runs the same `.pte` about three and a half times slower than the
  app's own CPU path**, and the reason is the libraries it is built against, not the
  design. See [why decode loses](#why-decode-loses), which traces it from 6 tok/s in the
  app down to a single `forward()` call and four wrong guesses that were measured away.
- **It holds two copies of the model**, 2.71 GB resident against 1.87 GB for the CPU path
  alone, and the phone starts swapping. Loading both halves took 97.56 s against 16.5 s
  for one. See [what it costs on the phone](#what-it-costs-on-the-phone).

None of this is a property of the accelerator. The first is an export flag; the second is
a build, and the hand written loop it was blamed on turned out to be innocent. Both are
fixable, and until they are there is nothing to switch on.

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
| CPU decode, this runner | 13 to 18 tok/s, and see below for why |
| NPU decode, MediaTek's runner | 10.5 to 11.2 tok/s |
| CPU decode, the app's own path, same `.pte` | 30 to 31 tok/s |
| CPU prefill, the app's own path, same `.pte` | 188 tok/s |

Two readings matter. **Splitting the work the right way round is confirmed**: CPU decode
beats NPU decode on the same model, so prefill on the accelerator and decode on the CPU
is the correct split, not the reverse. And **the prefill win is 1.7x, not the 5x the raw
NPU number suggests**, because the comparison that counts is against the app's own
KleidiAI prefill at 188 tok/s, not against nothing.

## What it costs on the phone

Measured with `dumpsys meminfo`, same model, same phone, both halves settled after the
warm prefill, marker on and marker off:

| | CPU only | NPU prefill + CPU decode |
|---|---:|---:|
| PSS total | **1.87 GB** | **2.71 GB** |
| RSS total | 1.98 GB | 2.68 GB |
| Native heap | 1.75 GB | 1.86 GB |
| EGL mtrack (DMA) | 0 | **585 MB**, peaking at 856 MB during load |
| Swap in use | 0 | 138 MB |

**The NPU half costs 881 MB, and almost all of it is DMA memory rather than heap.** The
Neuron buffer allocator takes AHardwareBuffer, so the shared weights land in gralloc and
show up as `EGL mtrack`, not as native heap. Four chunks at 133.8 MB of shared weights is
535 MB of that; the state caches are 32 slots of `[1, 8, 512, 64]` fp32, 32 MB; the rest
is the prompt graph's working buffers, which are released after load.

The 512 MB fp32 embedding table is not in that figure. It is mapped `r--s` from the file,
so only the touched rows are resident and the kernel can evict them.

**The real cost is that this path holds two copies of the model.** 827 MB of XNNPACK
`.pte` for the decode half and 1.18 GB of NPU chunks plus embedding for the prefill half.
That is inherent to splitting prefill from decode across two runtimes, and it is what
`FitEstimator` does not model: it sizes one model file times `COMPILED_RUNTIME_FACTOR`,
and would under-count this path by the whole NPU half.

**And the phone notices.** With both halves resident the app swaps for the first time,
and loading the CPU half took **97.56 s against 16.5 s** on the same build minutes
earlier, purely because the NPU half was already holding 856 MB when it started. Against
the warm prefix work that took first turns from 18.5 s to 184 ms, a 97 s load is not a
detail.

### How that scales with the window

The caches are the only part that grows: 64 KB of DMA per token of window, across all 32
slots.

| Window | NPU caches | NPU half | App total |
|---:|---:|---:|---:|
| 512 | 32 MB | 0.86 GB | **2.73 GB** (measured) |
| 2048 | 128 MB | 0.95 GB | 2.82 GB |
| 4096 | 256 MB | 1.08 GB | 2.95 GB |
| 8192 | 512 MB | 1.33 GB | 3.20 GB |
| 16384 | 1.0 GB | 1.83 GB | 3.70 GB |
| 32768 | 2.0 GB | 2.83 GB | 4.70 GB |

Lower bounds: only the caches are scaled here. The attention mask and the prompt graph's
activations grow with the window as well and are not counted.

So the window is cheap on the device and expensive on the export host, which is the
opposite of where the 512 limit came from. Going to 4k costs about 220 MB more on the
phone and roughly doubles the export's peak host memory.

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

## Why decode loses

Dated 2026-09-23, Poco X8 Pro Max, phone awake and unlocked, temperature quoted with every
figure because this ROM throttles hard. The short answer: the decode loop is innocent, and
so is the accelerator. The libraries this path is built against run the same `.pte` about
three and a half times slower than the prebuilt `org.pytorch:executorch-android:1.4.0` AAR
that the app's own path uses.

Four explanations were measured and thrown away before that one. They are listed because
each looked obviously right at the time, and because anyone reaching for them again should
know they have already been paid for.

| Guess | How it died |
|---|---|
| Thermal drift | App 31 tok/s at 33.1 °C and 30 tok/s at 40.4 °C. The app barely moves with temperature; the gap does not close. |
| A different export, or KleidiAI missing from it | Both halves load the *same file*, `LFM2.5-1.2B-Instruct-8da4w-gptq-32k.pte`. The runner's `--cpu_model_path` points at the app's own download. |
| `Module::execute` resolving the method by name each token | Bound the inputs to the `Method` once and drove it directly. 11.85 to 12.93 tok/s before, 11.82 to 13.05 after. No change; reverted. |
| Swap thrash from holding both halves | 490 to 750 minor faults/s and 2 to 6 major faults/s during decode, `VmSwap` flat. At 4 KB a fault that is about 2 MB/s against the hundreds of MB/s a token streams. |

What the measurements did show, in order.

**The decode caller was pinned to one core, and it took the delegate's threadpool with
it.** Decode looks like one thread chasing one dependency chain, so an earlier version
pinned it to the widest core in the kernel's capacity table. It is not one thread: the
XNNPACK delegate fans every matmul across a pthreadpool. Reading `Cpus_allowed_list` for
every thread during a decode showed the caller on `cpus=7` and its seven workers on
`cpus=4-7`, eight runnable threads over four cores, against the app's own path where every
thread carries `cpus=0-7`. Busy cores, sampled from `/proc/<pid>/stat` over three seconds:

| | cores burned | affinity |
|---|---:|---|
| App's own ExecuTorch path | 6.12 | all threads `0-7` |
| This path, caller pinned | 3.2 | caller `7`, workers `4-7` |

The pin is gone. The reading that had justified it, 13.02 tok/s pinned against 10.58
unpinned, came from the standalone shell runner, where the pool is confined to one core
anyway and the only question is which core the single thread lands on. Re-tested there
after the fix: 10.11 unpinned, 10.54 pinned, which is noise. **A number from the bench
binary was carried over to the app without checking that the two behave alike, and they
do not.**

**The Android build had no optimisation flags at all.** `CMAKE_BUILD_TYPE` was empty in
both `cmake-android-out` and the `examples/mediatek` subproject that actually produces
`libexecutorch_pd_jni.so`, so XNNPACK compiled with no `-O` flag. The app's engine is the
prebuilt Release AAR. Reconfigured both with `-DCMAKE_BUILD_TYPE=Release`, confirmed
`-O3 -DNDEBUG` reaches the XNNPACK translation units, and rebuilt. The shell runner went
from 10.54 to 14.88 tok/s.

**Neither fix closed the gap, so the next step was to stop guessing.** Two instrumented
lines in the decode loop settled where the time goes: `forward()` is **99.8%** of decode,
312 ms of a 313 ms token, with a delegate threadpool of 8 threads. Whatever is slow is
inside one call into the model, not in the loop, not in the argmax, not in the tokenizer,
and not in the JNI callback.

### The bench that isolated it

`tools/npu/cpu_forward_bench.cpp` loads a CPU `.pte` and times one decode step, with no
Neuron runtime, no handoff, no tokenizer and no app. It links exactly the libraries the
disaggregated runner links, so the only difference between the two binaries is that this
one never initialises an NPU. That removes the last confound in one measurement:

| | ms per `forward()` | cores | note |
|---|---:|---:|---|
| App's own path, AAR 1.4.0 | ~33 | 6.12 | about 980 tokens of context, 38 to 40 °C |
| This tree's libraries, alone in a shell process | 116 | 7.04 | 1,024 tokens, 38 °C |
| This tree's libraries, inside the app | 283 | 3.2 to 3.3 | about 880 tokens, 38 °C |

Seven cores against six, nothing else loaded, and still three and a half times slower than
the AAR. So the Neuron runtime's residency is **not** the primary cause: most of the gap
is there without it.

The third row says the process matters as well, and by another factor of about 2.4. That
part is **not** isolated. Two candidates were not separated: the Neuron runtime's 1.2 GB
and 95 threads, and the fact that the app process holds a second, independent copy of
ExecuTorch and XNNPACK, the AAR's, with its own threadpool and its own static state.
Swap was ruled out for it (see the table above), scheduling was not the whole story once
the pin was gone, and beyond that it is an open question rather than an answer.

### Where the time actually goes

Sweeping the starting cache position shows the cost is linear in how much cache attention
has to read, and the line is clean:

| `--start_pos` | median `forward()` |
|---:|---:|
| 0 | 45.7 ms |
| 512 | 85.3 ms |
| 1,024 | 116.0 ms |
| 2,048 | 169.2 ms |
| 4,096 | 274.6 ms |

That fits `45.7 ms + 0.056 ms per cached position`. Per position this model reads K and V
for 6 attention layers times 8 KV heads times 64 dims in fp32, 24.6 KB, so 56 µs per
position works out at about **440 MB/s across seven cores**, which is roughly thirty times
below what this memory can do. The constant term is the linear layers and is the part
XNNPACK delegates; the slope is attention over the KV cache, which runs as the custom
`llama::sdpa_with_kv_cache` / `custom_sdpa` ops. Both are registered in the binary, so the
op is present, and it is the kernel behind it that is slow.

At the app's real context of about 980 tokens the fit predicts 100 ms a token, 10 tok/s,
against the AAR's measured 33 ms. **The remaining gap is the attention kernel this tree
builds, and closing it is what would make the whole path worth switching on.** That is
still open: the cause is isolated and reproducible with one command, but not yet named
down to the build flag.

### How to reproduce

```sh
adb -s $SER push cpu_forward_bench /data/local/tmp/npu/
adb -s $SER shell "cd /data/local/tmp/npu && LD_LIBRARY_PATH=. ./cpu_forward_bench \
  --cpu_model_path=<the app's .pte> --steps=30 --start_pos=1024 --mmap=true"
```

Force stop the app first. A held model and 856 MB of DMA in another process was what made
the very first disaggregated reading 6.5 tok/s prefill and 0.60 decode, and that reading
was nonsense.

## What would turn it on

- An export whose window covers a real prompt, so the 512 token refusal stops firing.
- A build of ExecuTorch whose attention kernels match the prebuilt AAR's, since decode is
  most of the wall clock and the path cannot win while it loses there. The loop is not the
  problem and does not need rewriting; see [why decode loses](#why-decode-loses).
- Then the 1.7x prefill against the app's CPU path, measured on real multi turn traffic
  rather than on single questions, against the 1.07x to 1.47x ceiling already on file.
