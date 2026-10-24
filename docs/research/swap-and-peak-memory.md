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

> **Correction, 2026-10-23.** Every turn below began with `resetContext()` straight before its
> prompt, and that reset zeroes the whole KV cache (`llama_memory_clear` with data). Under
> pressure the zeroing drags the unused cache back in and can push weights out, so these turns
> paged what the reset did as well as what the idle gap did. The timings below are what was
> observed; what they cannot say is how much of the slowdown came from the idle gap rather
> than the reset. The runs at the end of this note, with the reset moved after each turn, were
> on a different day with less memory free and faster prefill, and do not settle that either.
> The flag's 20 µs a fault comes from these turns too; on the clean 4 GB runs at the end it
> flags every control turn (faults × 20 µs at 22% to 31% of the turn) while the arm with the
> weights brought back was faster by about a second. It needs recalibrating on matched runs.

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
a load with mmap). Any flagged pass flags the reply, and its footer then says "slowed by low phone memory". Every
reply's detail sheet has a Memory row, "1.63 GB peak", flagged or not. (The first build said
"memory paging" with a sentence explaining it; the owner judged both unreadable to anyone who
does not know what paging is, and the words were changed to say what happened instead.)

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

**In the app itself** (debug build, the same session, screen on and plugged in):

- LFM2.5 1.2B (GGUF): "Reading the prompt 0%", then `52→29 tok/s` at the right of the
  activity line; the finished footer read the same `52→29 tok/s`, 9.5 s, and the sheet's
  Memory row 1.63 GB peak. The prompt after the warmed prefix was 77 tokens, one batch, so the
  share went from 0 straight to written.
- Qwen3 1.7B (`.pte`): "Reading the prompt" with no share, then `-→13 tok/s` while writing,
  the prompt rate unknown rather than guessed; the finished footer filled it in, `77→11 tok/s`,
  78.7 s, and the Memory row read 3.01 GB.
- Qwen3 1.7B Q8_0 (GGUF, 21k window) under the 4 GB balloon, after 30 s idle: footer
  `16→16 tok/s 27.9s memory paging` in red (now "slowed by low phone memory"), prefill 78 tokens in 4.95 s, and the sheet's
  paging sentence under a 4.53 GB peak. A flagged footer is wider than the room beside the
  three actions, so it takes a line of its own, which `MessageActions` does on purpose.
- One thing the balloon also showed: with the app sent to the background under that pressure,
  the model was released and reloaded on return, though the process survived.

## Bringing the weights back while someone types (2026-10-23)

Same phone, unplugged (battery 70% to 44%), screen on, the owner's apps open: 4.2 GB free with
no balloon, against 7 GB on 10-12, so the balloon levels are not comparable across the two days.
Qwen3 1.7B Q8_0, 8k window, a 1k-token prompt, 96 tokens out, 20 s between turns. Built
(`InferenceEngine.prefetchWeights`, `TurnRunner.prefetchWeights`): on the first edit in the
composer after a reply, the engine counts its weight pages out of memory with `mincore` and,
when 64 MB or more is out, brings them back with `MADV_POPULATE_READ` in 16 MB steps off the
inference thread; it looks again 2 s after a look that found nothing to do and 10 s after one
that did, and a turn cancels it and waits out the step in flight.

**What it walks.** Each CPU buffer the model's tensors were placed in, less the token-embedding
table, which a prompt reads a row per token: 1,743 MB for this model, against a 1,749 MiB file.
With the table in, the ranges came to 2,059 MB, and 408 MB of them sat out of memory with
nothing pressing while the turn after took 850 faults. Mixture-of-experts models are not
prefetched at all. ExecuTorch is not either: its weights live inside XNNPACK, where the app
cannot see them, and its paging under pressure has not been measured.

**The benchmark.** `MemoryPressureBenchmark` with `-e lead 10`: odd turns run the app's own
policy for the last 10 s of the gap, even turns do not, so both arms meet the same phone in the
same minutes with the same gap. Send to first token is timed from the moment the prefetch is
stopped. The first runs reset the KV cache just before each turn, the artifact in the correction
above: with it, every look through the gap found the weights in and 640 to 1,400 MB were out at
Send in both arms. The reset now follows each turn. The runs with the reset before Send, and the
first ones with a single look at the start of the window, are in `2026-10-23-prefetch`, `-prefetch2`
and `-prefetch3`; only `-prefetch4` is the design described here.

Under a 4 GB balloon (`tools/memory/results/2026-10-23-prefetch4`), medians of four turns each:

| | With the prefetch | Without |
|---|---|---|
| Weights out when the gap ended | 1.0 to 1.5 GB, brought back | 1.0 to 1.4 GB |
| Prefetch time and process CPU over the window | 1.5 to 2.0 s; 2.0 to 2.4 s of CPU | not sampled |
| Out at Send | 1 to 87 MB | 975 to 1,413 MB |
| Major faults in the turn | 56,682 | 325,049 |
| Prefill | 22.6 s | 23.2 s |
| Send to first token | 22.7 s | 23.3 s |
| Waiting at Send for a step in flight | 0 to 3 ms | |

Pair by pair (prefetch turn against the control turn after it), send to first token was lower by
0.90, 1.65 and 1.35 s and higher by 0.12 s; the turn's faults were 82% to 84% lower in every pair.

- **It does what it is for.** Every gap pushed a gigabyte or more of the weights out, and every
  prefetch had it back before Send, so the paging moved out of the turn and into the typing.
  That is a shift, not less paging: the prefetch windows and their turns took 1.55 million faults
  between them, against 1.41 million in the control turns, whose gaps were not counted.
- **Whether it saves time is not settled.** Lower in three pairs of four, by about a second; but
  four pairs, always in the same order, cannot separate that from drift over the run.
- **Under a 3 GB balloon the gaps rarely evicted anything** (one gap in eight, 562 MB, brought
  back by the prefetch that followed it); the control turns took 8 to 6,462 faults. There the
  prefetch turns were slower to first token in three pairs of four (by 0.5, 0.8 and 3.0 s, faster
  by 1.3 s in the other), with the prefetch doing nothing in three of those gaps, so nothing
  points at it, but it is not evidence of no cost either.
- **What was not established**: the effect on other apps (141 low memory kills of other apps over
  the 4 GB run, one in a prefetch window and none in a control window, counted from the log's
  timestamps), on battery, and on the responsiveness of the phone while it runs. With nothing
  pressing, a look costs 2 to 5 ms; the first after a load reads back 93 MB of buffer pages that
  were never written, 140 to 580 ms, which `mincore` counts as out.
- **Not every fault in a turn is a weight page.** A 3 GB turn with 17 MB of weights out at Send
  still took 86k faults. The KV cache and compute buffers paging is the likely explanation, not
  a measured one: the fault counter does not say which mapping a fault was in.

**Found on the way, not fixed here.** A reset in the app (a new chat) zeroes the whole KV cache
the same way, so under pressure it pages the unused cache back in, up to the window's whole
size. Clearing only the cells (`llama_memory_clear(…, false)`) would avoid it; whether hybrid
models' recurrent state needs the zeroing has to be checked first.

## The prefetch across models, shapes and loads (2026-10-24)

Same phone, overnight, nobody using it: 11.5 GB of RAM and 12 GB of swap by `/proc/meminfo`, 6.2 to
7.1 GB available at the start of each unloaded cell before its model loaded (2.7 to 5.3 GB once it
had). `PrefetchMatrixBenchmark` driven by `tools/memory/run_prefetch_matrix.sh`, read by
`tools/memory/analyze_matrix.py`. Results in `tools/memory/results/matrix-20261023-2356`,
`-20261024-0736-big` and `-20261024-0746-b5`; `matrix-predeclared.md` holds the two levels added
after the first data, each written before any of its own.

**Design.** Three models (LFM2.5 1.2B QAD Q4_0, Qwen3 1.7B Q8_0, LFM2.5 2.6B Q4_K_M), 8k window, at
most 96 tokens a round, 20 s between turns with the prefetch on for the last 10. The main matrix ran on the charger (every turn reports
charging); the 5 GB turns all report not charging. The balloon sizes are what was asked for: no
turn records the balloon's resident size or the swap state. Four cells per
model: single turn or a four-turn conversation, with the search tool offered or not; calls are
answered for at most three rounds, and some turns end with calls unanswered. Single turn: 8
pairs, the same question in both arms, the order balanced and shuffled, the context reset after
each turn. Multi turn: 2 blocks, each two whole conversations with the same questions, one per
arm, the order reversed between blocks. Send to first token is timed as tokens stream. Cells and
models run in a seeded shuffled order within a level; the levels were not shuffled.

**Main matrix, 4 GB balloon and none: 192 pairs, no turn with an error or without a first token.**

| | 4 GB, single | 4 GB, multi | none, single | none, multi |
|---|---|---|---|---|
| Prefetch sooner / later (pairs) | 25 / 23 | 21 / 27 | 27 / 21 | 23 / 25 |
| Smallest single-turn sign test p | 0.289 | | 0.289 | |

- **No single-turn sign test came near rejecting equal odds; multi turn and pooled are
  description** (96 sooner and 96 later pooled; 52 to 44 single turn, 44 to 52 multi turn). With
  8 pairs a cell this is not evidence of no effect: the widest single-turn interval runs from -4.9
  to +5.8 s (Qwen3, 4 GB, single, tools), the widest of all from -10.4 to +1.8 s (the 2.6B outlier
  below). Five of twelve multi cells have intervals that exclude
  zero (Qwen3 unloaded, multi, no tools: block means -0.22 and -0.17 s), but two blocks give a
  bootstrap three possible medians, so these are not inference either. Dropping each cell's first
  pair turns 96 to 96 into 87 to 81.
- **The weights were seldom out at Send in the control arm**: 4 turns of 192 with 256 MB or more
  (3 of them Qwen3, 4 GB, single, no tools). That is a snapshot taken after the gap, not a record
  of the gap: 22 of the 40 turns with 10,000 or more faults had nothing out at Send, and the
  prefetch arm reported bringing weights back in 18 of its 192 gaps, 4,558 MB in all (pages can
  repeat).
- **Faults moved into the gap in one cell, not in general.** In that Qwen3 cell the prefetch arm brought back 2,977 MB; its turns took 250,127 faults against
  1,055,598, and gap plus turn 1,026,905 against 1,058,638. In its pair with 1,209 MB out in the
  control arm, the prefetch arm answered 3.9 s sooner. Faults are counted for the whole process,
  so this is consistent with moving weight paging into the gap without proving it. Across all 18
  gaps that brought weights back, the turn's faults were lower than the control turn's in 5, the
  same in 3 and higher in 10 (407,594 against 666,584 in all, with 1,065,754 against 2,049 in the
  gaps).
- **The whole matrix's faults do not look like that.** Gap plus turn, the prefetch arm took
  5,268,369 major faults against 2,572,130, most of the difference in the outlier below; the gaps
  alone, 1,242,721 against 4,431.
- **CPU over the gap**, as each cell's median paired difference in process CPU: 20 to 75 ms in the
  cells where little came back, 630 ms (mean 699, max 1,630) in the Qwen3 cell where a lot did.
  Process CPU includes the sampler and the runtime; a control gap takes 200 to 400 ms on its own.
  Every Qwen3 cell's first prefetch gap reported 93 MB brought back. The bookkeeping between Send
  and the first prompt token (joining the prefetch, closing the sampler, reading residency) took a
  median 10 ms in the prefetch arm and 8 in the control, at most 32 and 36.
- **Other apps killed, exploratory.** Under 4 GB the low memory killer took other apps 18 times in
  prefetch gaps against 5 in control gaps, and 25 against 20 during replies, almost all in Qwen3
  cells. The log was a ring buffer read at the end of each cell and wrapped under the balloon: 73 of
  the 192 turns at 4 GB start before it does, so these are floors. Unloaded, none in the logs, with
  3 turns wholly or partly outside them. The runner now streams the log to a file on the phone for
  the whole cell, which the 5 GB cell below used.
- **Conditions drifted.** Battery temperature ran from 35.8 to 45.8 °C with thermal status 0
  throughout; all 4 GB cells ran before all unloaded ones, over 7 h 21 min; 10 of 12 multi cells
  put the prefetch conversation first in block 0, and in the outlier's first block matched Sends are
  12 minutes apart on average. Reversing the order between two blocks does not separate the arm from that drift.

**An outlier this does not explain.** LFM2.5 2.6B, 4 GB, multi turn, tools: block means of -20.6 and
+2.9 s. In its worst pair the prefetch arm took 97 s to first token against 34 s, with 175,856
faults in the gap (against 2), 2.0 million in the turn (against 134,000) and 1,850 ms of CPU in the
gap (against 260), while reporting nothing brought back; the memory floor in that gap fell 227 MB.
The prefetch reports only the steps it finishes, so a step cancelled at Send is one possible
reading, not an established one. Both arms missed the prompt cache on every round of that cell
(`cached_tokens` 0, 46 rounds), so the miss lengthens both and does not explain the difference.

**The 2.6B cache miss, and its fix measured.** In the main matrix the 2.6B reused nothing on any
round of its multi-turn cells with tools offered (90 rounds) and little without (20 of 66 rounds).
Its template opens `<think>` in the generation prompt; with thinking off the session prefills it,
so the streamed text lacks it, and history rebuilt from that text no longer matches the cached
prefix, which a hybrid cannot partly roll back. The benchmark rebuilt history with the one-argument
`assistantHistoryText`, as the app's tool loop in `TurnRunner` did, while the app's user-turn path
passed `thinkingPrefilled`. Both now pass it (`ToolRoundHistoryTest` covers `TurnRunner`). The main
matrix installed its test APK at 23:56, before that change (01:23); the 5 GB run installed one
built at 01:25, after it, and
there the 2.6B reused 1,417 to 1,973 tokens on all 15 rounds it recorded, reading 44 to 58 new ones,
where the old build's rounds that missed in the same cell read 1,456 to 2,209 from scratch. That is the multi-turn cell without
tools only; the cell with tools did not run. The 2.6B also ran more than one round in 48 of its 64
turns with no tool offered, writing calls the benchmark answered like any other; both arms did the
same work in every pair (prompt, cached and generated tokens, rounds and stop reasons match), so
the comparison holds, but those cells are not tool-free.

**A model near the RAM: Qwen3.5 9B Q8_0 (9.5 GB), no balloon.** It never reached a turn, so it says
nothing about the prefetch. In each of the four cells the low memory killer took the benchmark 66 to
157 s after the cell started, during the load, with 73 to 89 MB resident and 8.0 to 8.3 GB in swap,
after 67 to 192 kills of other processes (39 to 63 distinct names, the launcher among them). The
engine loads without mapping (`useMmap = false`, whose KDoc says KleidiAI repacks into its own
buffer either way), so the weights are anonymous memory that can only go to swap, which fits what
the killer reported. Recorded as the result, as declared; not retried with other settings.

**5 GB balloon, stopped early.** The owner needed the phone after the first cell (multi turn, no
tools): LFM2.5 1.2B and Qwen3 1.7B complete, LFM2.5 2.6B cut after 5 turns (1 prefetch, 4 control).
1.2B: 3 sooner, 5 later, median -6.5 ms; this does not show equivalence. Qwen3: prefetch sooner in 6
pairs of 8; in the 2 pairs whose control turn had 256 MB or more out at Send (358 and 1,542 MB) it
answered 1.2 and 10.8 s sooner, prefill lower by about the same. Qwen3's other apps killed: 11 in
prefetch gaps and turns against 46 in control ones (9 and 13 in the gaps), but all 46 fell in the
block where control ran first, and its replies ran 50 s in all against 35. Two pairs and one cell;
the three other cells and the cell with tools for the cache fix are still to run.

**What this leaves.** These runs did not establish a latency benefit, a latency cost, or the effect
on other apps. Where weights were out, faults moved into the gap in one cell and not across all 18
gaps that brought weights back; of the six pairs with 256 MB or more out in the control turn, the
prefetch arm was sooner by 3.9, 0.4, 1.2 and 10.8 s and later by 0.3 and 1.2 s, and those pairs
were chosen by a reading taken after the gap; the 4 GB kills point one way and the 5 GB ones the
other; and the 2.6B outlier, with the prefetch arm 63 s slower, is unexplained. The default stays
on provisionally. Settling it needs the 5 GB level completed, the prefetch's cancelled work counted,
and the arms alternated within a block.

**The paging flag, checked on these turns.** Each matrix turn has a partner doing the same work in
the other arm, so a flagged turn can be held against it. Of the 418 turns in pairs (main matrix and
the 5 GB cell), the app's own flag, which judges each pass, fired on 14: 11 were 10% or more slower
than their partner, 2 slower by less, 1 not slower. None of the 192 unloaded turns was flagged.
A partner is not a paging-free baseline (one flagged 2.6B turn took 95.0 s against its partner's
93.0 s, both with about 200,000 faults), and pairs are sequential, a median 105 s apart.

To see whether another per-fault cost would do better, the rule was rerun on whole turns (faults
over the turn's wall time, which the app does per pass, so 13 fire instead of 14). "Slowed" is
10% or more slower than the partner; "missed" a slowed turn with 50,000 or more faults more than
its partner and no flag (39 turns were slowed, 16 of them with that excess):

| µs a fault | Flagged | Slowed | Slower by less than 10% | Not slower | Missed |
|---|---|---|---|---|---|
| 10 | 3 | 3 | 0 | 0 | 13 |
| 15 | 6 | 5 | 0 | 1 | 11 |
| 20 (the app's) | 13 | 11 | 1 | 1 | 5 |
| 30 | 16 | 12 | 2 | 2 | 4 |
| 40 | 21 | 14 | 2 | 5 | 2 |

Going from 20 to 40 would catch 3 more slowed turns and flag 5 more that were not slowed; going to
15 would miss 6 more. Pairs whose faults differ by 50,000 or more put a fault at a median 36 µs of
prefill (exclusive quartiles 11 and 60, 24 pairs, two of them negative), so the cost moves with
conditions; in the 10-23 run above the flag fired on every 4 GB control turn when the difference
from the prefetch arm was about a second. This is an exploratory, turn-level sensitivity check on
relative labels, not a calibration: nothing in it argues for moving off 20, and 20 stays as a
choice between missed warnings and unneeded ones.
