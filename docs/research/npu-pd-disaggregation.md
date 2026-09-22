# NPU prefill, CPU decode: what works and what it costs

**Measured 2026-09-22 on the Poco X8 Pro Max, MediaTek MT6991 (Dimensity 9400),
Android 16 / HyperOS.** Phone awake, unlocked and idle for every timing.

## Decision

**It works, its decode now matches the CPU path, and it stays off on what the split itself
costs.** The NPU half loads inside the app, prefills, hands its state to the CPU half in
about a millisecond and a half, and the reply that comes back is correct. On 2026-09-23 the
decode half reached **26.5 to 29.1 tok/s against the CPU path's 30**, from 3.7 at the start
of that day, after three fixes described in [why decode lost](#why-decode-lost-and-what-fixed-it).
What still stops it being worth turning on:

- **An NPU call costs about half a second whether it carries 128 tokens or ten.** A turn
  after cache reuse prefills a median of 50 new tokens, and 56 tokens took 499 ms on the
  NPU, where the CPU path prefills at 126 to 188 tok/s. The NPU only wins on long fresh
  prompts, which the warm prefix already removed from the common case.
- **It holds two copies of the model**, 2.71 GB resident against 1.87 GB for the CPU path
  alone, and the phone starts swapping. See [what it costs on the phone](#what-it-costs-on-the-phone).
  Measured below, that residency alone costs a CPU decode about 25%.

The window is no longer one of the reasons. The first export held 512 tokens; the
4,096-token export built since covers a real prompt, about 2,050 tokens with the tool
prefix, and it is what every 2026-09-23 measurement used.

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
| The app kept one core for itself after a reply | Decode pinned the calling thread to cpu7 and never restored the mask, on a thread shared with the rest of the app | Mask saved and restored. The pin itself was later removed: it confined the delegate's pool with it, see [cause 2](#2-the-pool-was-sized-and-placed-without-regard-to-the-phone) |
| Prefill predicted padding and end of turn | The runner options were MediaTek's defaults: 16 heads and int16 activations against the 32 heads and fp32 the graphs were compiled for | Read from the `runner` block the exporter writes beside the chunks |
| Six of sixteen layers never handed off | The state scanner matched an attention cache only at sequence length 2048; this export is 32768 | Scanner takes the state block as the contiguous run of state tensors and reads the geometry off it |

Nothing in a `.pte` declares how many heads it has or what type its inputs are, so the
fourth one is structural: **the export manifest is the only source for the runner
options, and guessing them is not a small error.** With 16 heads and int16 the prefill
predicted token 0 and token 2, which are padding and end of turn. With the manifest's 32
heads and fp32 it predicts ordinary words.

This section once said the pinning was worth keeping, on 13.02 tok/s pinned against 10.58
unpinned. That was measured in the shell runner, whose pool never left one core, and it did
not transfer to the app, where the pin halved the cores the decode could use. It is gone.

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
| CPU decode, this runner | 13 to 18 tok/s on 2026-09-22; 26.5 to 29.1 in the app after the fixes below |
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
context. The 4,096-token export built since covers a real prompt and removes the refusal
for it; the refusal stays for anything longer than whatever window the chunks were built at.

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

## Why decode lost, and what fixed it

Dated 2026-09-23, Poco X8 Pro Max, phone awake and unlocked. The decode half went from
**3.7 tok/s to 29 in the app** (283 ms a step to 34), for three causes found in this order.
None was the loop, the handoff or the accelerator: `forward()` was 99.8% of every decoded
token throughout, so every fix had to reach inside one call into the model.

| In the app, same `.pte`, same prompt | ms per `forward()` | tok/s |
|---|---:|---:|
| Start of the day | 283 | 3.7 |
| Libraries actually built Release | 75 | 13.9 |
| MediaTek's performance lock held only while the NPU runs | 34 to 39.5 | 26.5 to 29.1 |
| The app's own CPU path, for reference | about 33 | 30 to 31 |

### 1. The binaries were linked against unoptimised archives

`CMAKE_BUILD_TYPE` was empty in the Android tree, so XNNPACK compiled with no `-O` flag.
Setting it to Release was not enough, and that cost a wrong result before it was caught:
`examples/mediatek`, the CMake project that builds the library, the runner and the bench,
links the parent's **installed** archives in `cmake-android-out/lib`, and a rebuild without
`--target install` left those at the unoptimised copies. The timestamps gave it away, 22
Sep 16:13 installed against 23 Sep 00:31 built, and so did the sizes, 11.6 MB against 6.5
MB for `libxnnpack_backend.a`. Once installed:

| `--start_pos` | stale archives | Release archives |
|---:|---:|---:|
| 0 | 45.7 ms | 23.8 ms |
| 1,024 | 116.0 ms | 27.9 ms |
| 4,096 | 274.6 ms | 39.5 ms |

The attention slope fell from 56 µs to 3.8 µs per cached position, about 6.5 GB/s for the
24.6 KB of K and V each position holds. **The "attention kernel this tree builds" that an
earlier version of this section named as the open cause was this, and nothing else.** The
same version credited the Release build with taking the shell runner from 10.54 to 14.88
tok/s; that build never reached the runner, so the change was noise. `tools/npu/build_pd_libs.sh`
now builds both trees Release, installs before linking, and refuses to strip anything into
the app unless the installed archives are byte-identical to the ones just built.

### 2. The pool was sized and placed without regard to the phone

Two changes, both matching what the prebuilt AAR's `jni_layer_llama.cpp` does:

- **Size.** The pool is `get_num_performant_cores() - 1` threads, which reads each core's
  microarchitecture from cpuinfo and drops efficiency cores (A520, A510, A55, A53), where
  ExecuTorch's default is one thread per core. On a chip with little cores an equal share
  of every parallel region would otherwise wait on one. This phone has none (X925, X4 and
  A720 all count as performant), so here it changes 8 threads to 7 and nothing measurable.
- **Placement.** A coroutine thread spawned while the UI thread is narrowed to the big
  cluster keeps `cpus=4-7` for life, and pthreadpool workers inherit their creator's mask.
  The pool is now created from a thread widened to every CPU the process's cpuset allows,
  and the decode caller is widened for the decode. No core is named: the kernel intersects
  the request with the cpuset. `engine_session.cpp` met the same trap on the llama.cpp path.
  An earlier pin of the caller to the single widest core is gone; it confined the pool with
  it, and the reading that justified it came from the shell runner, which behaves nothing
  like the app.

### 3. MediaTek's performance lock boosted the app into a worse schedule

With the libraries fixed, the app still decoded at half the shell runner's speed, and
`/proc/<tid>/schedstat` showed why: the decode threads spent more time queued than running.

| | caller run / queued | each worker run / queued |
|---|---|---|
| Same library, shell process | 0.97 / 0.01 | about 0.82 / 0.04 |
| Same library, inside the app | 0.60 / **0.39** | about 0.22 / **0.51** |

The phone had 4.7 cores idle at the time, so nothing was crowding them; the scheduler was
keeping seven runnable threads on cpu5 and cpu6. The difference between those threads and
the AAR path's, in the same app, was one field: **effective `uclamp.min` 1024 on every thread
of the NPU path's process, against 0** on the AAR path's. Watching it across launches: with
the NPU path off, the launch boost lapses after four seconds; with it on, it never did.

The source is in ExecuTorch's MediaTek backend. `NeuronExecuTorchDelegate::LoadCompiledNetwork`
created a `ScopePerformancer` per compiled chunk, a thread that renews MediaTek's
`FAST_SINGLE_ANSWER_MODE` performance lock every second for as long as the chunk stays
loaded. Four chunks, four threads, a boost held for the whole session, CPU decode included.
It only reached the foreground app, which is why the shell runner never saw it (all 95 of
its threads at 0, 800 tokens at 39.95 tok/s). Causation, tested one variable at a time:

| Performance lock | boost after launch | prefill, 81 tokens | `forward()` |
|---|---|---:|---:|
| For the model's lifetime (MediaTek's) | never lapses | about 500 ms | 50 to 57 ms, 2.3 cores |
| Never | lapses in 4 s | 724 ms | 36 to 40 ms, 4.1 to 4.4 cores |
| Only while the NPU executes (this fix) | only during prefill | 549 ms | 34 to 39.5 ms, 5.2 to 5.9 cores |

The lock is worth having where the APU works, so the fix keeps it for exactly that:
`NpuActivityPerformanceLock` in `APUWareUtilsLib.h`, one per process, taken synchronously
when an NPU execution begins, renewed while executions keep arriving, and released once the
APU has been idle for 100 ms. It also stops a loaded but idle model holding a
maximum-performance scenario, which was a battery cost on top of the decode one. The patch
is `tools/npu/patches/executorch-release-1.4-pd.patch`.

### Explanations measured and thrown away

Listed because each looked right at the time, so anyone reaching for one again knows it has
been paid for.

| Guess | How it died |
|---|---|
| Thermal drift | App 31 tok/s at 33.1 °C and 30 at 40.4 °C; the gap did not close. |
| A different export, or KleidiAI missing from it | Both halves load the same file. |
| `Module::execute` resolving the method by name each token | Driving the `Method` directly changed nothing. Reverted. |
| Swap thrash from holding both halves | 490 to 750 minor and 2 to 6 major faults/s, `VmSwap` flat. |
| The Neuron runtime's residency | The shell bench with the app holding both halves idle: 35.9 ms against 28.6 alone. Real, about 25%, not the 2x. |
| The `foreground` cgroup | Seen once on the decode threads; a later launch had them all in `top-app` and decoded just as slowly. |
| A boost triggered by each NPU call | Still slow after 20 idle seconds, and the boost was present before the NPU libraries loaded. |

### Checking it holds beyond this phone

`PdDecodeProbe` (core/engine androidTest) runs the decode half's own code on any arm64
phone, NPU or not, since `libexecutorch_pd_jni.so` needs only system libraries and
`libneuron_backend.so`, which loads MediaTek's adapter lazily. With an activity holding the
foreground, it times 30 steps at position 1,024 for the pool policy, for one thread per core,
for the prebuilt AAR at the same thread count, and on MediaTek phones for the policy with
MediaTek's lifetime lock held. On this phone:

| run | ms per step | cores | effective `uclamp.min` |
|---|---:|---:|---:|
| policy, this library | 26.3 to 27.9 | 5.1 to 6.4 | 0 |
| prebuilt AAR, same threads | 26.3 | | |
| one thread per core | 26.3 to 26.4 | 7.2 to 7.4 | 0 |
| policy with the lifetime lock | 32.8 to 33.3 | 2.9 | 1024 |
| policy after the lock is released | 26.2 | 6.4 | 0 |

The library now matches the AAR on this chip, the policy is neutral where there are no
efficiency cores, and the lock reproduces its mechanism outside the app: boost to 1024, pool
squeezed to under three cores, 25% slower even with no NPU half resident. The probe's first
row originally read 19 ms against 26 for the same settings, because it ran inside the probe
activity's own launch boost; it now waits that out. Run it on Test Lab with
`tools/eval/run_matrix_ftl.sh <model> <os> <prefix> pd-probe`. **As of 2026-09-23 it has
run on this phone only**; the Tensor, Exynos, Snapdragon and Dimensity 9300+ runs are pending.

`tools/npu/cpu_forward_bench.cpp` is the shell-process twin, and it reports how much of the
window the pool spent running and queued, which is the reading that found cause 3:

```sh
adb -s $SER push cpu_forward_bench /data/local/tmp/npu/
adb -s $SER shell am force-stop io.github.alpharomercoma.openweights.debug
adb -s $SER shell "cd /data/local/tmp/npu && LD_LIBRARY_PATH=. ./cpu_forward_bench \
  --cpu_model_path=<the app's .pte> --steps=30 --start_pos=1024 --mmap=true"
```

Sweep `--start_pos`: a number taken at position 0 says nothing about a real turn.

## What would turn it on

- An NPU export with a narrower prompt graph, or a runtime that can run a partial batch
  without paying for 128 tokens, so the typical 50-token turn stops costing half a second.
- A way to hold one copy of the weights, or a phone with the memory to spare.
- Then the prefill gain, measured on real multi turn traffic rather than on single
  questions, against the 1.07x to 1.47x ceiling already on file.
