# Swap and peak memory on the phone

2026-10-12, POCO X8 Pro Max (MT6991, 11.5 GB, Android 16 / HyperOS, 12 GB of swap), screen on
and awake, unplugged (battery 32% to 22% over the runs), the owner's own apps open in the
background. Harness: `MemoryPressureBenchmark` (engine device test), `tools/memory/run_pressure.sh`
and `tools/memory/analyze.py`; reports in `tools/memory/results/2026-10-12-*`.

Two questions. Does a model's real peak memory match what the fit verdict promises? And when the
phone runs short and Android compresses part of the model into zram, can the app see it from
inside, and from what point does it cost the user time?

## What an app can read

Checked from an app's own SELinux context (`run-as` on a debuggable package) on this ROM:

| Source | Readable | What it gives |
|---|---|---|
| `/proc/self/status` | yes | `VmRSS`, `VmSwap` (this process's swapped-out size), `VmHWM` |
| `/proc/self/smaps_rollup` | yes | the same sizes, slower to read |
| `/proc/self/stat` | yes | major and minor faults, CPU time |
| `/proc/meminfo` | yes | `MemAvailable`, `SwapTotal`, `SwapFree` |
| `/proc/vmstat` | **no** | system-wide `pswpin`, `pswpout` |
| `/proc/pressure/memory` | **no** | the kernel's memory pressure stall figure |
| `/proc/swaps` | **no** | which swap devices exist (zram, or HyperOS's disk extension) |

So the signal has to come from this process's own counters. That is enough, as below.

## Peak memory against the fit verdict

Footprint is resident plus swapped, sampled every 100 ms through the load and through one turn.
"Promised" is `FitEstimator`: weights + KV cache at the window + 450 MB for a GGUF, weights × 2.4
+ 450 MB for a `.pte`. GGUFs at a 16k window with a 4k-token prompt; `.pte` at their exported
window with a 1.2k-token prompt.

| Model | File | Load peak | Settled resident | Turn peak | Promised | Peak against promise |
|---|---|---|---|---|---|---|
| LFM2.5 1.2B QAD Q4_0 (GGUF) | 664 | 1,164 | 1,130 | 1,231 | 1,306 | -6% |
| Qwen3 1.7B Q8_0 (GGUF) | 1,749 | 3,967 | 3,838 | 4,028 | 3,991 | **+1%** |
| LFM2.5-VL 3B Q4_0 (GGUF, no projector) | 1,520 | 2,145 | 2,137 | 2,275 | 2,226 | **+2%** |
| Qwen3 1.7B 8da4w (`.pte`) | 1,221 | 2,941 | 2,765 | 2,860 | 3,381 | -13% |
| Gemma 3 4B HQQ (`.pte`) | 3,035 | 4,393 | 4,156 | 4,452 | 7,734 | -42% |
| LFM2.5-VL 450M (`.pte`) | 619 | 898 | 1,096 | 1,164 | 1,935 | -40% |

All figures MB.

- **The GGUF estimate is right to within a few percent, and two models went just past it.** The
  450 MB allowance is spent almost exactly at a 16k window.
- **A transformer's KV cache is resident from the load, not as it fills.** Qwen3 1.7B at 16k
  settles at 3,838 MB with an empty conversation: the 1,749 MB file plus its whole 1,792 MiB
  cache. At 8k it is 3,061 MB (896 MiB of cache). `FitEstimator.defaultContextLength` says a
  wide window "costs almost nothing, because the cache is allocated lazily". That was measured
  on LFM2.5, a hybrid with 16 KB of cache a token, and does not hold for Qwen3 on this build.
  The verdict itself already counts the full cache, so it is not wrong; the comment is.
- **The compiled estimate is generous**: 13% to 42% over what the three exports used. Its
  factor comes from one Qwen3 export on a Dimensity 9400, and it matches that model here; the
  Gemma and 450M exports need far less.
- Peaks during a turn sit 60 to 300 MB above the settled figure: compute buffers and the part
  of the cache the prompt fills.

## Under pressure

A balloon under the shell (`tools/memory/balloon.c`) holds N MB of random bytes, which zram
cannot compress, and touches every page every two seconds. Qwen3 1.7B, GGUF Q8_0 at an 8k window
and the `.pte`, a 1k-token prompt and 96 tokens out. The turns are 20 s apart (30 s at 5 GB),
because that idle time is when a real conversation's weights go cold: while the person reads
the reply.

Qwen3 1.7B Q8_0 (GGUF):

| Balloon | Turn | Prefill t/s | Decode t/s | Wall s | Major faults | Swapped after | CPU / wall |
|---|---|---|---|---|---|---|---|
| 0 | 0 to 2 | 54.0 to 55.0 | 15.95 to 16.82 | 29.9 to 30.6 | 0 | 31 MB | 7.1 to 7.3 |
| 4 GB | 0 | 56.1 | 16.06 | 29.6 | 831 | 394 MB | 7.03 |
| 4 GB | 1 | **43.1** | 15.83 | **36.9** | **453,179** | 1,055 MB | 6.74 |
| 4 GB | 2 | 52.3 | 15.91 | 31.4 | 16,088 | 469 MB | 7.09 |
| 5 GB | 0 | **39.7** | 14.55 | **40.2** | **417,011** | 1,149 MB | 6.42 |
| 5 GB | 1 | **34.3** | 15.23 | **45.1** | **640,339** | 1,152 MB | 6.04 |
| 5 GB | 2 | **28.0** | 15.61 | **53.6** | **1,254,711** | 1,159 MB | 5.58 |
| 5 GB | 3 | **38.0** | 14.45 | **41.7** | **422,648** | 1,155 MB | 6.21 |
| 6 GB | | | | | | | killed |
| 8 GB | | | | | | | killed |

- **Being swapped out costs nothing; being paged back in during a turn is what costs.** At 4 GB
  the model had 971 MB compressed two seconds after loading, and its first turn ran at full
  speed: what went out was the cache past the prompt, which no turn touches. The second turn,
  after 20 s idle, paged 1.7 GB of weights back in and took 7 s longer.
- **The cost lands in prefill.** Prefill is the first pass over every weight, so it is the pass
  that brings them back; decode stays within 10% of normal once they are in.
- **Each major fault costs 15 to 27 µs of wall time** (the extra seconds over the 30.2 s
  baseline, divided by the faults), and the slowdown is in proportion to the faults: 0.4 million
  cost 10 s, 1.25 million cost 23 s.
- **The swapped-out size does not show thrashing.** At 5 GB it sat at about 1,150 MB through
  turns that paged 1.6 to 4.8 GB in, because pages came back and went out at the same rate. The
  fault count shows it; the size does not.
- **CPU over wall falls as the faults rise** (7.2 normally, 5.6 at the worst turn): the process
  spends that time waiting for memory rather than computing.
- **At 6 GB and above the low memory killer took the process during the load**, at
  `oom_score_adj` 0 with 65 MB resident and 2.4 GB swapped ("critical pressure and device is low
  on memory"). Before that it made 62 kills of 39 other processes, the owner's background apps
  among them. The only warning from inside was free memory: 350 MB at the 5 GB level.

The `.pte` behaved like the GGUF where it was measured: at 4 GB it had 539 MB swapped out with
no faults coming back and ran at its normal 125 to 137 t/s prefill and 10.1 to 11.8 decode. Its
working memory is anonymous (XNNPACK's packed weights), so it swaps like a GGUF rather than being
dropped and re-read like a mapped file. One caution: a `.pte`'s first turn after a load can take
tens of thousands of faults with no pressure at all (Gemma 3 4B: 61,525) because the runtime maps
the file, so a fault count alone would misfire there. Costed at 20 µs each that is 1.2 s of a
40.6 s turn, 3%, which a threshold on time rather than count leaves alone.

## The threshold this supports

Seconds lost to paging ≈ major faults × 20 µs. Flag a turn when that is over 10% of the turn's
wall time.

| Turn | Faults | × 20 µs | Share of the turn | Flagged |
|---|---|---|---|---|
| any baseline | 0 to 831 | under 0.02 s | under 0.1% | no |
| 4 GB, turn 2 | 16,088 | 0.3 s | 1% | no |
| Gemma `.pte`, first turn | 61,525 | 1.2 s | 3% | no |
| 4 GB, turn 1 | 453,179 | 9.1 s | 25% | yes |
| 5 GB, every turn | 417,011 to 1,254,711 | 8.3 to 25.1 s | 21% to 47% | yes |

The gap between the two groups is wide (3% against 21%), so the cutoff is not delicate. What it
does not cover is the kill: nothing in a turn predicts it, and the cue is before the load, when
free memory is below what the model needs. That is what the fit verdict's TIGHT case already
compares against.

## Limits

One phone, one model under pressure, on battery with the owner's apps present, so absolute
speeds are lower than a cool, plugged-in phone gives (Qwen3 1.7B Q8_0 measured 18.9 t/s
prefill with a 4k prompt at the start of the session, 55 t/s with a 1k prompt later). The balloon
is an active app; a background app that sits idle would be swapped out instead and press less.
The per-fault cost is zram on this kernel; a phone that swaps to flash would cost more per fault,
which only makes the same threshold fire sooner.

## What the app does with it

Built the same day, and measured on the same phone after it was built (screen on, awake; the
readout runs on battery at 9 to 13%, the pressure runs plugged in; reports in `tools/memory/results/2026-10-12-flag` and
`2026-10-12-readout`).

**The fit estimate.** `RUNTIME_OVERHEAD_BYTES` goes from 450 to 512 MB, past the 499 MB the
VL model's turn needed, and `FitEstimator`'s comment that a wide window costs nothing until it
is filled now says it was a hybrid's small cache that made it cheap. `COMPILED_RUNTIME_FACTOR`
stays at 1.4: it covers all three exports measured, and nothing measured says a 32k export
fits under less.

**The paging flag.** Each engine pass reads `/proc/self/stat` and `/proc/self/status` at its
start and end, and four times a second between, for the peak. A pass is flagged when its major
faults × 20 µs come to at least a second and to more than 10% of the pass's own wall time;
tools between passes are not counted, so a slow web fetch cannot dilute it. A pass that reads
freshly opened weights for the first time is not flagged at all, because those faults are a
file being read in, not paging (the Gemma `.pte` case above, and every GGUF's first turn after
a load with mmap). Any flagged pass flags the reply, and its footer then says "memory paging". Every reply's
detail sheet has a Memory row with the sampled peak, flagged or not.

Qwen3 1.7B Q8_0, 8k window, 1.3k-token prompt, 20 s between turns, what the engine's own meter
concluded:

| Balloon | Turn | Wall s | Prefill t/s | Major faults | Flagged |
|---|---|---|---|---|---|
| 0 | 0 to 2 | 25.4 to 26.2 | 64.6 to 67.3 | 0 to 526 | no |
| 4 GB | 0 | 28.5 | 57.8 | 16,542 | no |
| 4 GB | 1 | 26.3 | 66.4 | 102 | no |
| 4 GB | 2 | 28.2 | 58.7 | 391,108 | **yes** |
| 5 GB | 0 | 40.8 | 38.5 | 897,132 | **yes** |
| 5 GB | 1 | 58.4 | 25.8 | 913,077 | **yes** |
| 5 GB | 2 | 33.7 | 47.8 | 491,813 | **yes** |

- **No false alarm at baseline, and every turn that paged hard was flagged.** The engine's
  fault counts agree with the test's independent reading of the same counter to within 0.05%,
  and its sampled peaks with the test's 100 ms sampler exactly.
- **The closest call was right, narrowly.** The flagged 4 GB turn took 10% longer than the
  baseline average (its prefill 13% longer), against a 10% threshold. Its faults were
  cheaper than the morning's: 2.5 s for 391k, 6 µs each. Across this run's flagged turns the
  cost was 6 to 36 µs a fault, so 20 µs is a middle figure, not a constant; the flag is a
  yes or no on a wide gap, and it says "may have slowed it" for that reason.
- **The 4 GB first turn was slower without paging** (57.8 t/s, 16k faults), probably the
  balloon's touch loop competing for memory bandwidth, and the flag does not blame paging for
  it.

**The live readout.** While a reply is written, the line under it shows the turn's prefill and
decode rates as they stand (`173 → 34 tok/s`), and the share of the prompt read so far. Both
engines feed it from the same ticker as the memory peak. llama.cpp's session keeps a small
mutex-guarded snapshot that the ticker reads (whole batches of the prompt, every token after);
ExecuTorch counts tokens as they reach Kotlin and has no prompt progress to give, because it
reads a prompt in one call. An event goes out only when something moved.

What the ticker costs: LFM2.5 1.2B QAD Q4_0 (GGUF), two engines in one process, one with the
ticker and one without, a 1,489-token prompt and 72 tokens out, six pairs with the order
swapped every pair:

| | Prefill t/s, median | Decode t/s, median |
|---|---|---|
| With the readout | 173.3 | 34.52 |
| Without | 173.6 | 34.37 |

- **No cost that can be measured**: 0.2% and 0.4%, in opposite directions, well inside the
  turn-to-turn spread. The one large paired gap (+10% prefill) is a single slow turn without
  the readout in the first pair.
- **The live figure ends where the final one does.** Live prefill equals the final prefill
  rate on every turn; live decode ends 0.6 to 1.7% above the final figure, because the live
  interval ends at the latest token and the final one after the step that found the reply's
  end.
- **The prompt share moves in steps of a batch.** Eleven events a turn, a few while the
  prompt was read, which moves 512 tokens at a time (about every three seconds at this speed),
  and the rest while writing. Counting inside a batch would need a callback from inside llama.cpp's decode, which
  this does not add.

Not checked on the phone: the screen itself (covered by the Compose tests, not by a
screenshot), and the readout on an ExecuTorch model.
