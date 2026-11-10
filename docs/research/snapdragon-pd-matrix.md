# Prefill and decode on CPU, GPU and NPU: every pairing on a Snapdragon 8 Elite Gen 5

**Measured 2026-10-24 to 2026-11-10 on Qualcomm Device Cloud handsets, `canoe`: SM8850 (Snapdragon
8 Elite Gen 5), Android 16, 23 GB, `adb` as root, on AC power in Qualcomm's rack, over fourteen
reservations.** The first ran r1 to r4 and ended at about 16:55 phone time; the second (about
20:30 to 04:20) ran the three models the first could not reach (r6), LFM2.5 2.6B (r7), the
ExecuTorch NPU comparison (e1) and a second pass of five models in another cell order (r5). The
third and fourth, on 2026-10-25 on two units (727ba80 and a1716cc4), decoded each turn's reply at
the dataset's own length instead of 64 tokens (r8, r9, r9r); a fifth and a sixth that evening,
both on a third unit (c581fd31), ran what the fourth could not finish (r10, r11), so all nine
models have reply-length results ([below](#at-the-datasets-own-reply-lengths)). The seventh to
ninth, on 2026-11-02 and 03 on units 64a112da, c581fd31 and 783ee7e5, ran all nine pairings of
every model at reply length, each model on one unit (r12 to r16,
[below](#all-nine-pairings-at-reply-length-r12-to-r16)). An eleventh, on 2026-11-08 and 09 on
c581fd31, measured what the throttles cost on the same fixed work
([r20](#what-the-throttles-cost-on-fixed-work-r20)) and ran 2 CPU threads against 6 on the cell
that stalled most ([r19](#six-threads-against-two-r19)). A twelfth, on 2026-11-09 on the same unit,
set each throttle's state by hand on a cool phone, so that heat and state no longer arrived together
([r21, r23 to r27](#the-throttles-states-set-by-hand-on-a-cool-phone-r21-r23-to-r27)). A thirteenth,
the same day on a fifth unit (5d3e8f0a), ran the counterbalanced thread test from a shared gate
([r22](#from-a-shared-gate-on-a-fifth-unit-r22)) and the app's own engine in a pause set by hand
([r27](#the-apps-own-engine-in-a-pause-r27)), and in its extension profiled a paused run
([r28](#where-a-paused-threads-time-goes-r28)) and tried a barrier that gives up its core (r29). A
fourteenth, on 2026-11-09 and 10 on unit 4816891, tried that barrier with a later yield
([r31](#a-barrier-that-gives-up-its-core-r29-r31)), measured a fallback built into the app's engine
([r34, r38, r39](#a-fallback-in-the-apps-engine-r34-r38-r39)) and chatted at a person's pace for half an hour
and more ([r33, r39](#ordinary-use-r33-r37-r39-r46)); the owner's POCO X8 Pro Max (MediaTek MT6991), not a QDC
phone, ran two such sessions and 4 against 2 threads (r37, r41). A fifteenth, on 2026-11-10 on unit
64a112da (phone A), chatted with no pause between turns for 97 minutes, three sessions back to back
([r46](#ordinary-use-r33-r37-r39-r46)), then heated the phone until the hard throttle came on by itself and
decoded inside it, with the guard off and on ([r47, r48](#a-natural-hard-throttle-forced-r47-r48)),
and ran the app's engine at every thread count from 1 to 8 with the pause off and on, two runs traced by
the kernel's scheduler ([r49](#every-thread-count-reading-and-writing-and-where-six-threads-ran-r49)). A
sixteenth, the same day on a Snapdragon 8 Gen 2 (SM8550), measured the guard trying 4 threads again as
soon as its steps speed up ([r50](#trying-again-as-soon-as-the-steps-speed-up-r50-a-snapdragon-8-gen-2)).

| Processor | What it is, as read from the phone |
|---|---|
| CPU | Qualcomm Oryon (implementer 0x51, part 0x002), 8 cores in two clusters: cpu0 to cpu5 at up to 3.629 GHz (384 MHz floor), cpu6 and cpu7 at up to 4.608 GHz (768 MHz floor) |
| GPU | Adreno 840, OpenCL 3.0 with FP16, 18 frequency steps from 160 MHz to 1.2 GHz |
| NPU | Hexagon v81 (HTP); each session reports 8 threads, 8 HVX units, 1 HMX unit and 8 MB of VTCM |

## What was asked, and what this answers

Which processor should read the prompt (prefill) and which should write the reply (decode),
for each of the nine pairings of CPU, GPU and NPU, across models and conversation lengths taken
from public datasets. All nine pairings ran on all nine models with no failed turn; what did not
run is listed under [what does not run](#what-does-not-run-and-why) and
[what was not measured](#what-was-not-measured).

## Results

33 matched turns per cell from nine conversations (WildChat-1M, UltraChat 200k and
Daring-Anteater, three each), every cell reading the same tokens and writing 64 per turn (the
first from the prefill context, 63 timed decode steps). Rates are pooled over all matched turns,
throttled or not: they describe this phone in its rack, on AC power, replaying fixed-length
teacher-forced turns with a cooling gate before each cell (it timed out on 12 of the 81 cells
in this table, all in the second session), not a hand-held phone in a live chat. Each processor's
rate is the mean of the three cells' pooled rates for that half, rounded once. The first five
models are from r4 (`analysis-r4.md`), Qwen3 1.7B Q8_0, Llama 3.2 3B and Qwen3 4B from r6
(`analysis-r6.md`), LFM2.5 2.6B from r7 (`analysis-r7.md`), all under
`tools/snapdragon/results/2026-10-24-sm8850/`.

| Model (Q4_0 unless named) | Prefill tok/s, CPU / GPU / NPU | Decode tok/s, CPU / GPU / NPU | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | Slowest |
|---|---|---|---|---|---|---|---|
| Qwen3 0.6B | 239 / 591 / 3,014 | 63.7 / 30.5 / 47.8 | **34.6 s** | 47.6 s | 90.5 s | 84.3 s | cpu:gpu, 122.7 s |
| Gemma 3 1B | 339 / 590 / 3,547 | 65.6 / 31.1 / 47.8 | **33.6 s** | 47.5 s | 91.5 s | 70.2 s | cpu:gpu, 106.8 s |
| Llama 3.2 1B | 242 / 532 / 3,041 | 49.2 / 34.3 / 38.8 | **43.7 s** | 57.4 s | 83.5 s | 93.9 s | cpu:gpu, 111.5 s |
| LFM2.5 1.2B (hybrid, QAD) | 322 / 624 / 3,368 | 63.2 / 46.7 / 42.7 | **34.9 s** | 52.1 s | 67.6 s | 75.1 s | cpu:npu, 85.0 s |
| Qwen3 1.7B | 139 / 284 / 2,097 | 33.1 / 18.7 / 29.9 | **67.3 s** | 74.9 s | 154.2 s | 148.5 s | cpu:gpu, 199.5 s |
| Qwen3 1.7B Q8_0 | 144 / 254 / 1,939 | 26.6 / 16.6 / 23.6 | **83.8 s** | 104.7 s | 174.8 s | 160.3 s | cpu:gpu, 212.1 s |
| LFM2.5 2.6B (hybrid, QAD) | 137 / 243 / 1,507 | 27.8 / 21.6 / 20.7 | **81.3 s** | 106.8 s | 147.4 s | 163.1 s | cpu:npu, 188.2 s |
| Llama 3.2 3B | 92 / 165 / 1,220 | 20.5 / 14.2 / 19.0 | **111.0 s** | 119.2 s | 232.8 s | 237.7 s | cpu:npu, 243.8 s |
| Qwen3 4B | 54 / 104 / 855 | 15.27 / 6.28 / 15.35 | **149.5 s** | 149.9 s | 443.0 s | 353.5 s | cpu:gpu, 557.9 s |

The pairing columns are the summed turn time (prefill, first token, handoff and decode) over the
33 matched turns. The cells differ more than a single mean suggests (Qwen3 0.6B's GPU prefill ran
532 to 672 tok/s depending on the partner cell, Llama 3.2 1B's CPU decode 46.4 to 52.5); the
per-cell figures are in the analysis files.

- **Prefill: the NPU, by a wide margin, on every model.** 10.5 to 15.9 times the CPU and 5.1 to
  8.2 times the GPU. It generally ran faster on wider prompts (Llama 3.2 1B: about 1,150 tok/s for
  turns of under 64 new tokens, 3,200 to 3,700 for 256 or more), though not strictly (Qwen3 4B
  and LFM2.5 2.6B dip slightly in their widest buckets), and it led at every width. Median time to first token with NPU prefill was 72 to 128 ms for the 0.6B to 1.7B
  models, 157 to 188 ms for the 2.6B and 3B, and 244 to 288 ms for the 4B.
- **Decode: the CPU led on eight of nine** in the pooled figures (20.5 to 65.6 tok/s). The NPU
  was second on six of them and the GPU on the two LFM2.5 hybrids. On Qwen3 4B the NPU was ahead
  by 0.08 tok/s (15.35 against 15.27), a tie in practice. The CPU's lead narrows with size on the
  transformers (Qwen3 0.6B: CPU 1.33 times the NPU; Llama 3.2 3B 1.08; Qwen3 4B 0.99) but not on
  the hybrids (LFM2.5 1.2B 1.48, 2.6B 1.34). The aggregate hides a reversal at long context: in
  the 2,048 to 4,095 token bucket the NPU decoded faster than the CPU on five transformers (Qwen3
  0.6B 37.0 against 35.9, Qwen3 1.7B 25.1 against 23.5, its Q8_0 20.5 against 20.0, Llama 3.2 3B
  16.2 against 15.2, Qwen3 4B 12.6 against 10.6). Those buckets hold four or five turns each, all late in replay order and so
  also the hottest, so they show a tendency, not a rule for picking a decoder.
- **NPU prefill with CPU decode had the lowest summed time on all nine** in the first pass, taking 41% to 52% of
  the CPU-only time. Against doing both halves on the NPU it saved 7% to 33% (6.9% on Llama 3.2
  3B), except on Qwen3 4B, where it was 0.2% ahead (149.54 against 149.87 s): equal for any
  practical purpose, not proven faster, and in the second pass NPU-only was 0.3% ahead instead. It
  is a summed result, not a per-turn rule: on Qwen3 4B NPU-only was faster on 15 of the 33 turns
  in r6 and 17 in r5. **This is a result for 64-token replies.** At the dataset's own reply
  lengths decode is most of every cell's time, and with the phone throttled NPU-only had the lower total
  on the transformers run that way, while the hybrids kept this ranking (LFM2.5 1.2B) or tied
  (2.6B) ([below](#at-the-datasets-own-reply-lengths)); all nine pairings at those lengths are
  [further below](#all-nine-pairings-at-reply-length-r12-to-r16).
- **The handoff.** On the NPU-to-CPU path the state copy took a median 1.1 to 5.0 ms a turn, and
  the median handoff across every off-diagonal cell 0.6 to 11 ms. The state was a median 6.7 to
  101 MB on the first session's matched turns, up to 471 MB for the 4B at 3,191 tokens of context.
  The pairing totals include all of it.
- **The worst was CPU prefill with a slow decoder**: CPU with GPU on six models, CPU with NPU on
  the two hybrids and Llama 3.2 3B.
- **Greedy output differs between processors.** Against the CPU-only cell, the number of the 33
  turns that are token for token equal over the compared prefix (up to the first end of turn on
  either side, or the 64-token limit) ranges from 4 to 28 by cell and model. The data show that
  outputs differ, not why: numerical differences between backends and a defect in the state copy
  would both look like this, and nothing here validates the copied state beyond its byte count
  and position. Answers were not graded.

**How far these numbers reach.** Nine conversations, one reply length (64 tokens; the dataset's
own lengths are covered separately [below](#at-the-datasets-own-reply-lengths)) and one run of
each cell per pass. The 33 turns of a cell share histories and a thermal trajectory, so they are
not 33 independent repeats, and context grows with replay order, so long context and accumulated
heat arrive together. Summed times weight long turns more. No interval or equivalence test is
given; r5 below is the only check on the 64-token runs' repeatability, and margins of a few percent should be read
against its spread.

### Q8_0 against Q4_0 (Qwen3 1.7B)

The app ships Qwen3 1.7B as Q8_0. Here it decoded slower than Q4_0 on every processor (CPU 26.6
against 33.1 tok/s, NPU 23.6 against 29.9, GPU 16.6 against 18.7), read prompts at about the same
rate on the CPU (144 against 139) and a little slower on the NPU (1,939 against 2,097), so every
pairing took longer. The Q8_0 ran in the second session and the Q4_0 in the first. r5 repeated the
Q4_0 within 4% per cell (below), which shows the Q4_0 figures repeat, but the quantisation effect
stays confounded with the session: the two were never interleaved. The
placement was the same shape: three graph splits on the NPU instance, 1,428 MiB on `HTP0-REPACK`.

One Q8_0 result has no explanation here: its NPU decode was 21.1 tok/s in the NPU-only cell
against 25.0 and 24.8 after CPU or GPU prefill, and on the only 2 turns uncapped in every cell
the gap stayed (21.8 against 25.8 and 25.7). "Uncapped" means no cap was read at the turn's
start or end; it does not mean equal temperatures, equal clocks within the turn, or no NPU
throttling, which is not read at all. The Q4_0 shows no such gap (30.0, 29.6 and 30.0). The cause
is unknown.

### The second pass (r5)

r5 ran five models again with another seed, so each model's nine cells ran in a different order
from the first pass (`compare_runs.py`, output in `analysis-r5-vs-first.md`). Qwen3 0.6B was
added to r5 as a same-session control for the ExecuTorch comparison. Qwen3 4B is compared with
r6, where all nine of its cells ran; the others with r4.

| Model | Against | Rank correlation of the nine cell times | Median change | Largest change | Fastest, first / second pass |
|---|---|---|---|---|---|
| Llama 3.2 1B | r4 | 1.00 | 1.2% | +5.9% (npu:gpu) | npu:cpu / npu:cpu |
| Qwen3 0.6B | r4 | 1.00 | 1.4% | +5.5% (npu:cpu) | npu:cpu / npu:cpu |
| Qwen3 1.7B | r4 | 0.95 | 1.8% | +3.7% (npu:gpu) | npu:cpu / npu:cpu |
| LFM2.5 1.2B | r4 | 0.98 | 1.4% | +6.4% (npu:gpu) | npu:cpu / npu:cpu |
| Qwen3 4B | r6 | 0.95 | 2.9% | -5.1% (gpu:cpu) | npu:cpu / **npu:npu** |

- **The rankings repeat.** Every model kept its slowest cell, and the four smaller ones their
  fastest. The rank changes are between cells that were within 1% to 4% of each other in the
  first pass (Qwen3 1.7B's gpu:gpu, cpu:cpu and cpu:npu; LFM2.5 1.2B's npu:npu and gpu:cpu; three
  pairs on Qwen3 4B, each within 2%).
- **Qwen3 4B's top two swapped.** NPU-only took 149.6 s and NPU prefill with CPU decode 150.1 s
  (0.3% apart; NPU-only faster on 17 of 33 turns), where r6 had them 149.9 and 149.5 s. Over both
  passes the two are a tie on this model.
- **Most per-processor rates repeat to a few percent.** The exception is GPU prefill on Qwen3 0.6B,
  8% faster in r5 (639 against 591 tok/s; 12% in its gpu:npu cell). On Llama 3.2 1B, NPU prefill was 2,888 to 3,025
  tok/s by cell in r5 against 3,017 to 3,058 in r4, and CPU decode 46.3 to 52.1 against 46.4 to
  52.5.
- **Two cells moved for reasons the data suggest.** Qwen3 4B's gpu:cpu was 5.1% faster in r5
  (GPU prefill 112 against 102 tok/s); in r6 that cell started with the GPU capped at power level 8.
  Its cpu:npu was 4.9% slower, with NPU decode 13.4 against 15.4 tok/s on nearly every turn from
  the third on. It was the last cell in both orders; its gate passed in r5 and timed out in r6
  (56.7 °C), so the gate does not explain it either, and nothing recorded does.
- **The gate timed out on 6 of the 4B's 9 cells** in r5 (none on the other four models), all
  within 3 °C of the limit and with the GPU uncapped.

### At the dataset's own reply lengths

Every turn above decodes 64 tokens. The dataset's own replies to those turns are about six times
longer: 401 tokens median and 378 mean by the Qwen3 tokenizer, up to 1,046 (as plain text; the
decode budget adds one for the end of turn; other tokenizers count differently, up to 1,141 on
Gemma 3 1B). Summed over a cell's turns, decode was 39% to 49% of
a CPU-only cell's time at 64 tokens and is 84% to 99% of every cell's time at reply length
(individual short turns are lower, down to 27.5%). So the 64-token totals weight
prefill several times more than these conversations do, and the "NPU prefill with CPU decode"
saving above is a saving at 64-token replies.

`pd_bench --gen-ref` decodes, for each turn, as many tokens as the dataset's reply to it has
(tokenized as plain text, plus one for the end of turn; capped at 2,048, which cut no reply) and
records each decode step's time. Everything else is unchanged: same build, conversations, 33 turns,
teacher forcing, 6 threads, greedy decoding that continues past an end of turn. The cells were four
chosen as candidates once decode dominates, from the 64-token ranking: NPU prefill with CPU decode,
NPU-only, GPU prefill with CPU decode and CPU-only; GPU decode and CPU or GPU prefill into NPU
decode were not run at these lengths. pd_bench loads only the processors its cells use, so most
of these runs held a CPU and an NPU instance and no GPU one. Two further reservations on
2026-10-25 ran it on two units of the same phone (`canoe`, SM8850): r8 on unit 727ba80, r9 and
r9r on unit a1716cc4, and two more that evening, r10 and r11, on unit c581fd31 (`analyze_ref.py`,
output in `analysis-r8.md` to `analysis-r11.md`). Qwen3 4B used two Hexagon sessions (HTP0 and
HTP1), as did Llama 3.2 3B, as in r6; the other models one.

**The cooling gate stopped passing.** Before each cell the gate waits for every CPU, GPU and NPU
zone to read 55 °C or less, the GPU to be uncapped and no CPU ceiling lowered, and gives up after a
set time (120 s; 300 s for r9's Qwen3 0.6B), after which the cell runs anyway. It waits; it does
not cool. After one cell of reply-length turns it timed out almost every time, with the GPU capped
at power level 8 and the CPU clusters' ceilings at 1.90 and 2.23 GHz (from 3.63 and 4.61): after
121 s, and after 302 s at 64.5 °C (CPU) on the first 300 s attempt. It passed only for the first
cell after an idle stretch (the first cell of r8 and of r9, both runs' Qwen3 first models) and once
after 50 s (r8's second cell), on the first two units. On the third unit it passed for both of r10's cells (5 and 55 s;
the second followed an NPU-only cell that never capped the CPU) and for r11's first two, Gemma 3
1B's (0 and 45 s), then timed out on every later cell as on the other units. The table gives each cell's gate result; "timed out" covers a range
of states (hottest zone 56 to 72 °C when the gate gave up; r11's Gemma 3 1B NPU-only cell timed
out on temperature alone, with the GPU uncapped and full CPU ceilings; r9's Qwen3 0.6B CPU-only cell started
with the GPU uncapped and the ceilings partly back, at 3.40 and 4.34 GHz), and a passed gate says nothing about
the rest of the cell (r8's first cell was CPU-capped before 11 of its 33 turns).

| Model, run, unit | Order the cells ran in (gate) | npu:npu | npu:cpu | gpu:cpu | cpu:cpu |
|---|---|---|---|---|---|
| Qwen3 0.6B, r8, 727ba80 | npu:cpu (passed), cpu:cpu (passed), npu:npu, gpu:cpu (timed out) | 292.7 s, 85% | **257.8 s, 75%** | 327.6 s, 95% | 344.4 s |
| Qwen3 0.6B, r9, a1716cc4 | gpu:cpu, npu:npu, cpu:cpu, npu:cpu (all timed out) | **292.8 s, 84%** | 305.1 s, 87% | 328.8 s, 94% | 348.8 s |
| Qwen3 1.7B, r9, a1716cc4 | cpu:cpu (passed), gpu:cpu, npu:cpu, npu:npu (timed out) | **452.9 s, 87%** | 511.0 s, 99% | 519.3 s, 100% | 518.5 s |
| Qwen3 1.7B, r9r, a1716cc4 | npu:npu, npu:cpu, cpu:cpu (all timed out) | **441.1 s, 68%** | 518.1 s, 80% | not run | 648.9 s |
| Qwen3 4B, r8, 727ba80 | npu:npu, npu:cpu, cpu:cpu (all timed out; cpu:cpu reached 20 turns before the reservation ended) | **506.5 s, 56%** | 696.6 s, 76% | not run | 911.9 s |
| Llama 3.2 3B, r9, a1716cc4 | npu:cpu, cpu:cpu, npu:npu (all timed out) | **733.5 s, 64%** | 1,014.1 s, 88% | not run | 1,150.9 s |
| LFM2.5 1.2B, r9, a1716cc4 | cpu:cpu, npu:npu, npu:cpu (all timed out) | 321.4 s, 102% | **271.1 s, 86%** | not run | 315.3 s |
| Llama 3.2 1B, r9, a1716cc4 | npu:npu, cpu:cpu, npu:cpu (all timed out) | **330.7 s, 85%** | 342.0 s, 88% | not run | 390.8 s |
| LFM2.5 2.6B, r9, a1716cc4 | npu:cpu, npu:npu (both timed out; the reservation ended 29 turns into npu:npu) | 537.8 s | **526.6 s** | not run | not run |
| LFM2.5 2.6B, r10, c581fd31 | npu:npu, cpu:cpu (both passed; the reservation ended before npu:cpu finished) | 599.8 s, 105% | not finished | not run | **569.4 s** |
| LFM2.5 2.6B, r11, c581fd31 | cpu:cpu, npu:cpu, npu:npu (all timed out) | **601.8 s, 85%** | 678.4 s, 96% | not run | 704.5 s |
| Gemma 3 1B, r11, c581fd31 | npu:cpu (passed), cpu:cpu (passed), npu:npu (timed out, uncapped) | 288.3 s, 99% | **221.6 s, 76%** | not run | 290.0 s |
| Qwen3 1.7B Q8_0, r11, c581fd31 | npu:npu, npu:cpu, cpu:cpu (all timed out) | **528.1 s, 62%** | 634.4 s, 74% | not run | 851.8 s |

Seconds are summed over the turns every cell of that row completed: 33 turns from nine
conversations, except Qwen3 4B, 20 turns from five conversations (7,027 decode steps). The
percentage is against that row's CPU-only cell. LFM2.5 2.6B in r9 is summed over the 29 turns both
its cells completed (eight conversations) and has no CPU-only cell; in r10 over 33 turns. Over all 33 turns the 4B's
NPU-only cell took 1,064.7 s and NPU prefill with CPU decode 1,339.9 s.

- **Decode dominates, so NPU prefill leaves little to gain on the prompt.** NPU prefill read the
  prompt 9.1 to 15.4 times faster than the CPU and was 1% to 2% of an NPU-prefill cell's time;
  CPU prefill was still 12% to 16% of a CPU-only cell's. Single short turns are the exception:
  decode was 27.5% to 99.9% of individual turns.
- **CPU decode stalled on the four models with the largest weights.** In every cell that decoded
  on the CPU for Qwen3 4B, Llama 3.2 3B, Qwen3 1.7B Q8_0 and LFM2.5 2.6B (r11), single decode steps
  took 0.5 to 15.9 s (8 to 37 such steps a cell, in one to six turns, 54 to 291 s in all). Each of
  those cells reached about 107 °C at some turn's end; the 21 turns that held a stall ended at 94.6
  to 106.6 °C. No NPU-only cell had one, and none of the five smaller models' cells did; NPU-only
  cells' hottest CPU zone peaked at 98.8 °C. LFM2.5 2.6B's CPU cells had none in r9 or r10, so the
  stalls are not a fixed property of a model either, and the split by weight size describes these
  runs, not a cause. Temperatures are read only at each turn's start and end, so nothing here shows
  what made a step stall. A stalled step is not the decoder's speed, but it is part of what a user of this phone
  would wait for, so the totals keep it and the comparison below is given both ways; "without"
  deletes the stalled steps' time, which measures their share, not what the cell would have taken
  without whatever caused them.
- **Wherever both NPU-prefill cells' gates timed out, NPU-only had the lower total on every
  transformer measured that way, by a margin that depends on the model and on the stalls; on the
  hybrids it was higher (LFM2.5 1.2B) or level once the stalls are taken out (2.6B).** Over 33
  turns (29 for LFM2.5 2.6B in r9), NPU prefill with CPU decode took longer than NPU-only by:

  | Model, run | With the stalls | Without them | Turns NPU-only was faster |
  |---|---|---|---|
  | Qwen3 0.6B, r9 | 4.2% | 4.2% (none) | 10 of 33 |
  | Llama 3.2 1B, r9 | 3.4% | 3.4% (none) | 18 of 33 |
  | Qwen3 1.7B, r9 (NPU-only last) | 12.8% | 12.8% (none) | 23 of 33 |
  | Qwen3 1.7B, r9r (NPU-only first) | 17.5% | 17.5% (none) | 28 of 33 |
  | Llama 3.2 3B, r9 | 38.2% | 19.8% | 28 of 33 |
  | Qwen3 4B, r8 | 25.8% | -1.4% (NPU prefill with CPU decode 1.4% faster) | 23 of 33 |
  | LFM2.5 1.2B, r9 | -15.7% (NPU prefill with CPU decode faster) | -15.7% (none) | 0 of 33 |
  | LFM2.5 2.6B, r9 | -2.1% (NPU prefill with CPU decode faster) | -2.1% (none) | 14 of 29 |
  | Qwen3 1.7B Q8_0, r11 | 20.1% | 10.0% | 23 of 33 |
  | LFM2.5 2.6B, r11 | 12.7% | -6.9% (NPU prefill with CPU decode faster) | 7 of 33 |

  So on Qwen3 1.7B (in both orders), its Q8_0 and Llama 3.2 3B NPU-only was faster with or
  without the stalls and on most turns; on Qwen3 4B its lead is the stalls; on Qwen3 0.6B it came from a few
  long turns and NPU prefill with CPU decode was faster on 23 of 33, and on Llama 3.2 1B the gap
  was 3.4% with each faster on about half the turns: ties in practice on the two smallest
  transformers.
  On the 4B's 20-turn cohort the stall-free gap is 18% (506.5 against 599.7 s), so that result
  also depends on which turns are counted. On the hybrid LFM2.5 1.2B the throttled CPU slowed
  (48.5 and 48.6 tok/s in its two cells, against 59.4 and 67.1 in the same cells at 64 tokens) but
  stayed ahead of the NPU's 40.9, with its hottest CPU zone at 106.5 °C and no stall, and NPU
  prefill with CPU decode was faster on every turn. On LFM2.5 2.6B the two were level in r9 (CPU
  decode 21.3 tok/s against the NPU's 20.8, each faster on about half the turns), and in r11
  NPU-only led only through the CPU's stalls, with NPU prefill with CPU decode faster on 26 of 33
  turns.
- **Gemma 3 1B sides with the hybrids, on one schedule.** Its cells did not start in the same
  state: NPU prefill with CPU decode first and CPU-only second, both after passed gates, and
  NPU-only last, after a gate that timed out on temperature with nothing capped. So the 23% between
  its NPU-prefill cells is not comparable with the table, and no comparison between its cells is
  free of order and heat. The closest: on the 27 turns where both the CPU-only and the NPU-only
  cell were CPU-capped at both ends, CPU decode ran at 51.5 tok/s against the NPU's 46.5 (pooled
  over all 33, 53.3 against 46.6; the CPU-only cell was capped by the end of 28 turns), and
  CPU-only took 290.0 s against NPU-only's 288.3. Gemma keeps the largest share of its weights on the CPU
  in an NPU cell (45%, its tied output layer; see [where the work ran](#where-the-work-ran)).
- **On the third unit the gate passed, and NPU-only took nearly the same time.** r10's two LFM2.5
  2.6B cells started after passed gates (5 and 55 s), and its NPU-only cell never saw the CPU or
  GPU capped. On the 29 turns it shares with r9, where that cell ran after a timed-out gate on
  another unit, it took 533.0 against 537.8 s, 0.9% less: one comparison, with unit, order and
  thermal history all different. NPU-only took 5.3% longer than CPU-only there (599.8 against
  569.4 s; CPU-only faster on 19 of 33 turns, and capped on 30 of them by the turn's end). On
  LFM2.5 2.6B the CPU decoder was level with the NPU in r9 (21.3 against 20.8 tok/s), ahead in r10
  (25.8 against 20.9) and behind in r11 (18.5 and 20.5 against 21.0), where its stalls fell.
- **The 64-token winner won when its gate passed.** In r8 NPU prefill with CPU decode ran first,
  after a passed gate, and took 75% of CPU-only time, with NPU-only 13.5% longer. The same cell
  run last after a timed-out gate on the other unit took 87%. NPU-only took 292.7 and 292.8 s on
  the two units, after a timed-out gate both times.
- **On Qwen3 0.6B what moved is the CPU's decode rate.** In r9, where every gate timed out, CPU
  decode was 41.5 to 42.7 tok/s in the three cells that used it (a 3% spread) against the NPU's
  43.2; in r8's first cell it was 49.2. Where the CPU-decode cells ran at different points of the
  thermal history the same decoder differed by 10% to 20% (Qwen3 1.7B in r9, 24.8 to 28.4 tok/s;
  Qwen3 4B in r8, 9.1 to 10.2; Qwen3 1.7B Q8_0 in r11, 16.5 to 19.9). These schedules do not isolate a backend effect inside that spread.
- **The share against CPU-only moves with the schedule as much as with the processor.** Qwen3
  1.7B's CPU-only cell took 518.5 s after a passed gate (r9) and 648.9 s after a timed-out one,
  run last (r9r), 25% apart on one unit (24.8 s of the 130.4 s difference is prefill), while its
  NPU-only cell took 452.9 and 441.1 s. Heat, order and inherited state were not varied apart, so
  the cause is not isolated; the comparison least exposed to it is between the two NPU-prefill
  cells.
- **NPU decode held its 64-token rate; CPU decode mostly did not.** Re-weighting the 64-token turns (r4
  for Qwen3 0.6B and 1.7B, Llama 3.2 1B, LFM2.5 1.2B and Gemma 3 1B; r6 for Qwen3 4B, Llama 3.2
  3B and Qwen3 1.7B Q8_0; r7 for LFM2.5 2.6B) to these lengths at their own measured rates
  predicted NPU-only within about 5% on every model but two (measured 1.00 to 1.05 times the
  projection): Llama 3.2 3B took 1.10 times its projected time, and Qwen3 1.7B Q8_0 0.87 times,
  13% less, its 64-token NPU-only decode being the one noted above as slower for no recorded
  reason.
  Cells decoding on the CPU measured 1.03 to 1.61 times their projection: the six that started
  after a passed gate 1.03 to 1.26, the rest 1.16 to 1.61, the highest the cells with stalls. The
  projection put NPU prefill with CPU decode at 72% of CPU-only on Qwen3 0.6B; r8 measured 75%,
  r9 87%.
- **Decode slows along a long reply, more on the CPU.** Over the same 12 long turns (512 or more
  steps), CPU decode on Qwen3 0.6B fell 12% to 17% from the first 63 steps to steps 256 to 511 and
  NPU decode 5% to 6%, on both units, then recovered slightly past step 511. Context grows and heat
  accumulates over the same steps, so this does not say which causes it.

**What this does not show.** Each row is one order on one unit; only Qwen3 1.7B has two orders on
the same unit, Qwen3 0.6B two orders on two units, and LFM2.5 2.6B parts of three runs on two. The phone sat in a rack on AC power and
throttled within a cell; a hand-held phone in a short chat may decode with its CPU unthrottled for
much of the time, which is the condition r8's first cell describes (most 64-token turns were
throttled too), but that was not measured. The decode budget is the dataset's reply, not the
model's own: Qwen3's greedy outputs here open with `<think>`, and some reached an end of turn well
inside the budget (one at token 315 of 557), so a live reply could be longer or shorter. Answers
were not graded.

### All nine pairings at reply length (r12 to r16)

The runs above measured three or four cells per model at reply length. Three more reservations,
on 2026-11-02 and 03, ran all nine pairings of every model that way, each model's nine cells in
one run on one unit, so that a model's cells compare with each other: r12 on unit 64a112da, r13 on
c581fd31 (the r10 and r11 unit), r14, r15 and r16 on 783ee7e5. The llama.cpp build, `pd_bench.cpp` as
committed in 6e7ed1c5 (the binary's sha256 prefix, 204879540c459b5e, is in each chain log), the conversations,
the model files and the flags are those of r9 to r11: `--gen-ref`, 6 threads, the 55 °C gate with a
120 s limit, an 8,192-token window, up to six turns a conversation, two Hexagon sessions for Qwen3
4B and Llama 3.2 3B. Each model's cells ran in one order, the nine cells shuffled by Python's
`random.Random("r12" + model file name)` and kept for that model in every later run (`chain12.sh`
to `chain16.sh`). 3,710 turns ran, none failed and no reply was cut (`analysis-r12.md` to
`analysis-r16.md` from `analyze_ref.py`; decode step by context and the thermal log in
`analysis-r12-heat.md` to `analysis-r16-heat.md` from `analyze_heat.py`).

| Model, run, unit | cpu:cpu | cpu:gpu | cpu:npu | gpu:cpu | gpu:gpu | gpu:npu | npu:cpu | npu:gpu | npu:npu |
|---|---|---|---|---|---|---|---|---|---|
| Qwen3 0.6B, r12, 64a112da | 364.2 s | 188% | 95% | 91% | 179% | 87% | 87% | 173% | **80%** |
| Gemma 3 1B, r12, 64a112da | 316.7 s | 190% | 102% | 87% | 188% | 98% | **80%** | 178% | 91% |
| Llama 3.2 1B, r12, 64a112da | 428.3 s | 123% | 91% | 87% | 116% | 90% | 105% | 111% | **80%** |
| LFM2.5 1.2B, r15, 783ee7e5 | 303.5 s | 132% | 119% | 93% | 126% | 113% | **86%** | 117% | 106% |
| Qwen3 1.7B, r16, 783ee7e5 | 563.5 s | 171% | 95% | 111% | 163% | 92% | 93% | 156% | **79%** |
| Qwen3 1.7B Q8_0, r13, c581fd31 | 845.1 s | 121% | 81% | 86% | 117% | 76% | 88% | 113% | **73%** |
| LFM2.5 2.6B, r13, c581fd31 | 758.3 s | 99% | 89% | 92% | 92% | 84% | **79%** | 88% | **79%** |
| Llama 3.2 3B, r14, 783ee7e5 | 995.3 s | 132% | 96% | 94% | 125% | 82% | 87% | 116% | **80%** |
| Qwen3 4B, r14, 783ee7e5 | 1,371.6 s | 224% | 85% | 73% † | 214% | 90% | 102% | 209% | **82%** |

Each model's CPU-only cell is its summed time over the 33 turns; every other cell is a percentage
of it. † marks the one cell that ran after a passed gate (first in its run); every other cell ran
after a timed-out one, with the GPU capped at power level 8 (578 MHz) and the CPU
ceilings at 1.90 and 2.23 GHz. In bold, the fastest cell that ran after a timed-out gate; LFM2.5
2.6B's two are 0.4% apart (595.3 and 597.5 s).

- **NPU prefill was in the fastest cell on every model,** at 73% to 86% of CPU-only. The decoder in
  that cell was the NPU on the six transformers other than Gemma 3 1B, the CPU on Gemma 3 1B and
  LFM2.5 1.2B, and either on LFM2.5 2.6B. One cell ran faster than the bold one: Qwen3 4B's GPU
  prefill with CPU decode (73%), which ran first after a gate that passed in 5 s with the CPU
  uncapped on 9 of its 33 turns, so it is not ranked with the rest. r16 ran that one cell again,
  warm, on the same unit: 1,446.3 s, 105% of r14's CPU-only, with 24 decode steps over 500 ms in
  four hard throttles. That cell was then run warm once more on a fourth unit (r18b, 4816891), by the
  maintainer's choice to complete the matrix with a warm cell even from another phone: 1,406.3 s,
  103% of r14's CPU-only, with all 33 turns capped and 26 decode steps over 500 ms in five hard
  throttles. The published page shows r18b in that cell, marked as from phone D; this table keeps
  r14's cell (†). Qwen3 1.7B's row
  is r16's: r13's CPU-only and CPU prefill with GPU decode cells had run after passed gates, so r16
  ran all nine again after a warm-up; NPU-only went from 81% of CPU-only (r13, on another unit) to
  79%.
- **GPU decode was never the fastest.** Its three cells took 111% to 224% of CPU-only on eight
  models, and 88% to 99% on LFM2.5 2.6B, whose CPU-only cell held 19 decode steps over 500 ms
  (758.3 s; 704.5 s in r11 on the same unit). The GPU was the slowest decoder on eight models in
  every cell (on Qwen3 0.6B 20.0 tok/s against the NPU's 43.3 to 43.5 and the CPU's 40.0 to 41.7;
  on Qwen3 4B 4.4 against 11.1 to 14.2 and 9.0 to 11.0, its cool cell left out) and within 3.5% of
  the CPU on LFM2.5 2.6B (18.8 to 19.2 against 18.7 to 21.2), behind its NPU (21.1 to 21.2). Every
  GPU-decode cell in the table read the GPU capped at the end of all 33 turns. In r13, superseded
  by r16, Qwen3 1.7B's CPU prefill with GPU decode ran first, and on the 14 turns where its GPU read
  1,200 MHz and uncapped at both ends, the capped CPU decoded faster in all 39 matched turn pairs.
- **The GPU's decode step grows with context, on Qwen3 4B steeply.** Its median step was 107 ms
  at 1,024 to 1,535 tokens of context, 166 ms at 1,536 to 2,047, 430 ms at 2,048 to 2,559 and 614
  ms at 3,584 to 4,095, against the NPU's 71, 77, 82 and 97 ms and the CPU's 77, 87, 95 and 143 ms
  over the same buckets. The other Qwen3 models' GPU step also rose most between the 1,536 and
  2,048 buckets (Qwen3 0.6B 54 to 76 ms, 1.7B 72 to 95, its Q8_0 78 to 101); Llama 3.2 3B's rose
  more evenly (81, 110, 133). The cause is not identified, and since context and heat grow
  together over a cell this says where the step grows, not why. It is why Qwen3 4B's GPU-decode
  cells logged 1,165 to 1,237 steps over 500 ms: on a GPU-decode cell those are the decoder's pace,
  not stalls, and `analyze_ref.py`'s "without stalls" column means nothing there.
- **With the decoder fixed, the prefill processor moved little.** On the six models whose fastest
  cell decoded on the NPU, reading the prompt on the CPU instead of the NPU added 3 to 17 points of
  CPU-only time and on the GPU 2 to 13, with the cells at different points of their run's thermal
  history (on Qwen3 4B the CPU-prefill cell ran fourth and the GPU-prefill cell last).
- **The CPU decoder's rate varied more across a model's cells than the GPU's or, mostly, the
  NPU's.** Each of those cells differs in both its prefill processor and its place in the run, so
  this does not say which of the two moved it. Across a model's three cells with the same decoder, CPU decode spread 1% to 53% (53% on Qwen3
  4B, whose fastest CPU-decode cell is the passed-gate one), GPU decode 0% to 2%, and NPU decode 0%
  to 1% on four models and 7% to 28% on five (Llama 3.2 1B and 3B, Qwen3 1.7B Q4_0 and Q8_0, Qwen3
  4B). Qwen3 1.7B's GPU prefill with NPU decode ran through the 18:12 hard throttle logged below.
- **LFM2.5 1.2B was run again with a warm-up (r15).** In r12 its fastest cell, NPU prefill with
  CPU decode at 77%, ran first after a gate that passed in 5 s, with the CPU capped by the end of
  only 14 of its 33 turns, and its CPU-only cell started with the GPU uncapped and full CPU
  ceilings, so r12's LFM2.5 1.2B row was not comparable cell to cell. r15 ran one throwaway CPU-only
  pass first (r15warm, 285.3 s, in its own file and not in the table), then r12's order. The first
  counted cell's gate timed out after 121 s, with the hottest zone at 61.4 °C, the GPU capped and
  the CPU ceilings lowered, and every cell read the GPU and CPU capped at the end of every turn. NPU prefill with CPU decode
  took 86% and stayed the fastest, then GPU prefill with CPU decode at 93% (104% in r12); the
  other six cells came within 5 points of r12's on another unit. It is one of two models, with Gemma 3 1B
  (49.6 to 53.8 against 46.8 to 47.4 tok/s), whose CPU decoded faster than its NPU when both were
  throttled (49.9 to 50.4 against 40.8 to 41.0 tok/s), so its NPU-only cell took 106%.

**What the thermal log adds.** From r13's last model (Qwen3 4B, superseded by r14) on, a script on
the phone (`thermlog-r13.sh` to `thermlog-r15.sh`) read the kernel's cooling devices every 10 s,
with the hottest NSP zone and the last finished turn (`thermlog-r13.txt` to `thermlog-r15.txt`).

- **One mitigation engaged as a unit.** Two CPU pause devices (`thermal-pause-C0` and
  `thermal-pause-3C`; by their names, core masks that together cover cores 2 to 7, an inference
  from the names, not a reading), the GPU's `kgsl` device at state 5 and the cDSP's `cdsp` device at
  state 5 switched on and off together, in stretches of 0.6 to 13.9 minutes: 7 in r13's Qwen3 4B,
  8 in each of r14's two models, none in r15. The GPU's frequency limit is a separate device
  (`gpu`, state 8 being the 578 MHz power level); it engaged about 3.5 minutes into r14's first cell and one
  minute into r15's warm-up, and stayed on to the end of each run. It read 8 at the logger's last r14 sample, 1.4 minutes
  after r14 ended, and 0 at r15's first, 8.2 minutes after; r15warm's gate, about 9.5 minutes
  after, found the GPU uncapped at 1,200 MHz and the CPU ceilings full. When it lifted between
  those readings is not known.
- **Every CPU decode step over 500 ms in r13's Qwen3 4B and in r14 fell in a turn that ran during
  a pause stretch:** 31 of 31 in r13's Qwen3 4B, 30 of 30 in r14's and 31 of 31 in Llama 3.2 3B's.
  r15, with no pause stretch, had one such step, in its CPU-only cell. A sample is credited to the
  turn whose interval (its start time and length, both in the results) holds the sample's
  timestamp; the logger stamps the time before reading the devices, which takes a second or two,
  so a sample at a turn's edge can fall either way. This shows turns, not steps: a stalled step
  fell in a turn with at least one paused sample. The runs used six threads,
  which with cores 2 to 7 paused would share two cores; the data show the stalls coincide with the
  pauses, not that the pauses cause them.
- **NPU decode did not stall under the mitigation, but slowed.** In turns that ran at least half
  of their samples with `cdsp` above 0, NPU decode ran at a median 0.66 to 0.68 times the rate of
  the same turn in another NPU-decode cell that ran with it at 0 (20 pairs on Llama 3.2 3B, 32 and 7
  on Qwen3 4B in r14 and r13; crediting edge samples one way or the other moves the r14 count
  between 32 and 36, and its median by 0.01). On Qwen3 1.7B Q8_0, which ran before the logger started, the
  NPU-only cell took 528.1 s in r11 (first in its order) and 619.2 s in r13 (seventh) on the same
  unit: the two matched within 0.8 tok/s for the first 22 turns, then, after the NPU zone read
  100.8 °C at the end of the 20th and 21st, r13's fell to 15.0 to 18.2 tok/s from the 23rd on
  while the zone dropped to 83 to 89 °C. That is consistent with the cDSP mitigation the logger
  recorded later, but no cooling device was read during that cell. The Q8_0 row's 73% includes
  that slowdown.
- **Other cells repeated on that unit.** LFM2.5 2.6B's NPU-only cell took 599.8, 601.8 and 597.5 s
  in r10, r11 and r13, and Qwen3 1.7B Q8_0's CPU-only cell 851.8 and 845.1 s in r11 and r13.

**What this does not show.** One order per model, so a cell's place in the run and its thermal
history go with it, and whether a pause stretch lands in a cell moves a CPU-decode total (Qwen3 4B's
NPU prefill with CPU decode had four turns in pause stretches and 24 steps over 500 ms, and took
102%, against NPU-only's 82%).
Different models ran on different units, so rows do not compare in absolute seconds. Everything
else said under [what this does not show](#at-the-datasets-own-reply-lengths) for the earlier
reply-length runs holds here: a rack phone on AC power, teacher-forced turns, a decode budget set
by the dataset's reply, no graded answers.

### Why the CPU's reply gets stuck (r16)

A stuck token is one CPU decode step over 500 ms, where the CPU's median step is 18 to 85 ms by
model. Every one fell in a turn where the CPU wrote the reply; no NPU decode step in the compared
cells exceeded 198.7 ms. On 3 November, during r16, three loggers ran on unit 783ee7e5 beside the
benchmark: `trig-r16.sh` (every 2 s, the sensor described below and the four cooling devices),
`affinity-r16.sh` (every 5 s, the shell's CPU affinity mask and `nproc`) and the cooling-device
logger (`thermlog-r16.sh`); their logs are `trig-r16.txt`, `affinity-r16.txt` and `thermlog-r16.txt`.
The page published from this report calls the mild throttle the soft throttle and the cool-down the
hard throttle. The phone's thermal configuration was read by `adb` as root (`phone-evidence-2026-11-03.md`).

**Two throttles.**

| | The mild throttle | The cool-down |
|---|---|---|
| What it does | GPU capped at power level 8 (578 MHz), CPU ceilings 1.90 and 2.23 GHz | `thermal-pause-C0` and `thermal-pause-3C` (by their names, cores 2 to 7 paused), `kgsl` and `cdsp` at 5 |
| When | Every compared turn (r16: 329 of 330 at those ceilings, the other lower still) | About a minute at a time |
| What switches it | `cpu-cluster0`, `cpu-cluster1` and `gpu` cooling devices with no kernel thermal-zone binding, set by the user-space `thermal-engine-v2` (its config file is empty; its rules are built in) | All four devices share a passive `step_wise` trip on `sys-therm-0` at 78 °C with 8 °C hysteresis; each also has other bindings (`kgsl`: the eleven `gpuss` sensors at 105 °C and the PMIC current limits) |

**The sensor.** `sys-therm-0` is thermal zone 61. The device tree (`soc/thermal-zones/sys-therm-0`)
reads it from channel 0x44 of the PMK8850 power-management chip's ADC (`pmk8850@0/vadc@9000`), whose
node is labelled `pmk8850_xo_therm`: a thermistor named for the crystal oscillator (XO) that clocks
the chip; where it sits on the board is not documented. It is not a CPU, GPU or NPU sensor, and not Android's `skin` reading (which read 57.8 and
64.1 °C while `sys-therm-0` read 68.7 and 77.3 °C). The board is a Qualcomm reference design (model
string "Canoe QRD").

**What each throttle does to each processor.** The mild throttle's cost compares the 9 Qwen3 4B GPU
prefill with CPU decode turns that r14 ran with nothing capped (GPU at 1,200 MHz, full CPU
ceilings; that cell started after a passed gate) against the same turns in r16 (578 MHz, 1.90 and
2.23 GHz), on the same unit. The NPU's cool-down cost compares turns run at least half under `cdsp`
above 0 with the same turn in another NPU-decode cell run with it at 0.

| | Mild throttle | Cool-down |
|---|---|---|
| CPU | Ceilings 3.63 to 1.90 GHz (six cores) and 4.61 to 2.23 GHz (two); decode 17.1 to 13.6 tok/s, 21% slower, no step over 500 ms | `thermal-pause-C0` and `thermal-pause-3C` (by the names, masks 0xC0 and 0x3C: cores 6 and 7, and 2 to 5) leave cores 0 and 1; with 6 threads, stretches of 5 to 20 tokens in 44 to 71 s |
| GPU | Capped at power level 8 of 17, 578 MHz, from 1.2 GHz; prefill 201.6 to 104.7 tok/s, about half | `kgsl` at 5 of 17; effect not measured |
| NPU | No visible limit: `cdsp` reads 0 outside cool-downs and the clock is not readable; LFM2.5 2.6B's NPU-only cell repeated at 599.8, 601.8 and 597.5 s on one unit with the mild throttle off (r10) and on (r11, r13) | `cdsp` at 5 of 12; median 0.66 (Llama 3.2 3B, 20 pairs) and 0.68 (Qwen3 4B, 32 and 7) of the matched turn's rate; on Qwen3 1.7B the three GPU prefill with NPU decode turns inside the 18:12 cool-down ran at 14.8 to 15.9 tok/s against 28.6 to 32.9 for the same turns in its other NPU-decode cells, about half; no NPU decode step over 198.7 ms |

The mild throttle came on within minutes of heavy work (the GPU cap about 3 minutes into r14's
first cell, 1 minute into r15's warm-up). After r14 ended, the GPU cap was still on at the logger's
last r14 sample 1.4 minutes later and off at r15's first 8.2 minutes later; r15warm's gate found
full CPU ceilings 9.6 minutes after. When in that window each lifted is not known.

**Cool and warm starts.** The gate that defines them is the one described above for the reply-length
runs (`pd_bench.cpp`, `cool_down`): every 5 s it reads the hottest `cpu-0-*`/`cpu-1-*`, `gpuss-*` and
`nsphvx-*`/`nsphmx-*` zones, the GPU's `thermal_pwrlevel` and each CPU policy's `scaling_max_freq`
against `cpuinfo_max_freq`, and passes when all three zones are at or under 55 °C, the GPU uncapped
and no ceiling lowered; with `--cool-max 120` it gives up once 120 s have passed (recorded as 121 s).
It waits and does nothing else. "Started cool" is a passed gate, "started warm" a timed-out one. The
throwaway warm-up passes (r15warm, r16warm) run the model's 33 turns with CPU prefill and CPU decode
before the first counted cell, so that cell's gate times out like the rest. The gate's sensors are
not the cool-down's: the gate reads the processors' zones at 55 °C, the cool-down the XO thermistor
at 78 °C.

**The cool-down switches with hysteresis, and the log shows it.** Over r16's 2,557 readings (to
19:07:28) the sensor read up to 78.02 °C while the cool-down was off (1,448 of 2,372 off readings
between 70 and 78 °C) and down to 69.96 °C while it was on (162 of 185). Between the trips it keeps
its last state. The first reading after each of r16's six switches on was 78.08 to 78.14 °C and
after each switch off 69.73 to 69.91 °C, the four devices changing in the same 2 s reading:

| On | Off | Length | Writing the reply | CPU decode steps over 500 ms |
|---|---|---|---|---|
| 18:00:50 | 18:01:51 | 61 s | CPU, Qwen3 1.7B GPU prefill | 9, from 18:00:50 to 18:01:51 |
| 18:12:04 | 18:13:29 | 85 s | NPU, Qwen3 1.7B | none (NPU decode) |
| 18:51:27 | 18:52:26 | 59 s | CPU, Qwen3 4B GPU prefill | 6, from 18:51:27 to 18:52:24 |
| 18:55:43 | 18:56:48 | 65 s | CPU, Qwen3 4B | 6, from 18:55:48 to 18:56:47 |
| 19:00:31 | 19:01:33 | 62 s | CPU, Qwen3 4B | 5, from 19:00:32 to 19:01:33 |
| 19:05:20 | 19:06:27 | 67 s | CPU, Qwen3 4B | 7, from 19:05:23 to 19:06:25 |

Times are local (UTC+8). Every cool-down that came while the CPU wrote produced one stuck stretch in
the same window, to within a few seconds; the earlier 10 s logs could only place stalls in the same
turn. During Qwen3 4B's GPU prefill with CPU decode, consecutive cool-downs began 4 min 16 s to
4 min 49 s apart.

**Heat is shared across the chip.** Llama 3.2 3B in r14, median of each processor's hottest zone at
turn end: CPU-only 106 / 94 / 94 °C (CPU / GPU / NPU), NPU-only 95 / 89 / 97, GPU-only 84 / 84 / 81,
after earlier cells had heated the phone. All three instances stay loaded through a model's run; in
the CPU-only cell the NPU and GPU instances do no work, yet the NPU zones read 94 °C. The NPU and GPU
cells still run the output layer and the embedding lookup on the CPU. The 18:12 cool-down came during an NPU-decode
cell and paused the CPU cores anyway: the XO thermistor responds to whichever processor heats the board. So a cell's speed depends on how hot the cells before it left
the phone, which is why the comparison requires every cell to start after a timed-out gate.

**The usable-core mask does not show the pause.** Through the 18:00 cool-down the shell's affinity
mask stayed `ff` and `nproc` 8, so `sched_getaffinity` gives an app no sign of paused cores on this
phone.

**A likely mechanism.** The benchmark runs 6 CPU threads. llama.cpp's CPU backend
built without OpenMP (as the app and `pd_bench` are) synchronises its workers in `ggml_barrier`
(`ggml-cpu.c`) by spinning on an atomic counter with the Arm `yield` hint, which does not give the
core to the scheduler; the compute loop calls it after every executed graph node (Llama 3.2 3B's
reserved graph has 874 nodes, some skipped or fused). With two cores left, finished workers can hold
them while workers with work wait to be scheduled, at hundreds of synchronisation points per token.
Unlike CPython's GIL, whose waiters sleep, these waiters keep their core. The stuck stretches wrote 5
to 20 tokens in 44 to 71 s (single tokens 0.6 to 18.3 s), far below what fair sharing of two cores
would give. The test of 6 threads against 2 is [below](#six-threads-against-two-r19). With the
pause set by hand on a cool phone, 6 threads kept both remaining cores busy and made almost no
progress while 2 did not stall ([r23](#the-pause-and-the-cpus-threads-r23)). A later profile put
about 70% of a paused run's CPU time in the function that holds `ggml_barrier`'s wait
([r28](#where-a-paused-threads-time-goes-r28)), and the kernel's scheduler trace recorded all six
threads on the two cores left ([r49](#every-thread-count-reading-and-writing-and-where-six-threads-ran-r49)).

**The app.** `ThermalPolicy` sets the decode threads once per reply (`ModelRuntime.planThreads`) from
Android's thermal status: half the cores (4 here), 3 at light or moderate, 2 at severe, and no reply
at critical or above. This phone's skin thresholds are 48, 49, 50, 60, 61 and 90 °C (light to
shutdown). Android read severe (skin 57.8 °C, about 17:38) and, about 20 minutes later, emergency (64.1 °C, about 17:59). A reply
already running keeps its count, so these readings do not show that the app avoids the stall.

**Candidate changes.** Switching to 2 threads while the pause switches read on, as root, was
measured ([r23](#the-pause-and-the-cpus-threads-r23)): it recovered most of what a pause cost, but an
app cannot do it as written. Not measured: drop to 2 threads after one slow token (the app already
calls `llama_set_n_threads`); let a waiting worker sleep or yield after a short spin in
`ggml_barrier`; write on the NPU during a cool-down. Matching threads to `sched_getaffinity` is ruled out on this
phone by the mask above; pinning alone does not stop six threads sharing two cores.

### Six threads against two (r19)

On 2026-11-08 and 09, on unit c581fd31, the cell with the most stuck tokens, Qwen3 4B with GPU
prefill and CPU decode, ran with `--threads 2` against the `--threads 6` used everywhere else: the
same build (binary sha256 prefix 204879540c459b5e), model (92f4a83cdfe691b2), conversations, flags
and 120 s gate as r18b. A throwaway 6-thread cell came first; the fixed order after it, set before
any result, was 6 2 2 6 2 6 6 2, repeated twice. The reservation ended during the third cell, so one complete cell
of each ran: one ordered pair, not the planned replication. Every gate timed out (the phone was
never back at 55 °C). Two loggers ran beside it: `trig20b.sh` (every 2 s: the XO thermistor, the
hottest GPU sensor, the GPU's clock, allowed ceiling and power level, each pause switch, `kgsl`,
`cdsp` and each CPU policy's ceiling) and `place19.sh` (every 2 s: each benchmark thread's last
CPU and its CPU time). The analysis is `analyze_throttle.py threads`; "excess stall" is the time
each step took past 500 ms, summed, so a few long stalls weigh as much as many short ones.

| Cell | Threads | Peak XO | Pauses | Steps over 500 ms | Excess stall | Longest step | Decode, summed | Turns, summed |
|---|---|---|---|---|---|---|---|---|
| r19warm (not counted) | 6 | 78.4 °C | 6, 360 s | 30 | 338 s | 18.4 s | 1,373 s | 1,487 s |
| r19-1-t6 | 6 | 78.4 °C | 7, 392 s | 36 | 372 s | 18.6 s | 1,391 s | 1,505 s |
| r19-2-t2 | 2 | 76.3 °C | none | 0 | 0 | 0.25 s | 1,581 s | 1,695 s |

- **With 6 threads, every stall was in or at the edge of a pause.** Of the 6-thread cell's 372 s of
  excess, 241 s were in steps that ran wholly under a pause and 130 s in steps that straddled a
  switch; none in a step with no pause. The warm-up repeated it (222 s and 116 s, none outside).
- **During a pause the threads ran on cores 0 and 1.** Of 1,358 samples of a benchmark thread that
  had used CPU time since its last sample, taken while a pause was on, 1,354 (99.7%) last ran on CPU
  0 or 1; with no pause they spread over all eight. The process's allowed-CPU list stayed 0 to 7
  throughout. That is consistent with the mechanism above, six threads on the two cores the pause
  leaves, though a last-CPU field does not trace every core a thread used between samples.
- **With 2 threads the phone stayed under the trip.** The XO thermistor peaked at 76.3 °C against
  78.4 °C in both 6-thread cells, so there was no pause and no step over 0.25 s; its CPU zones ended
  turns at a median 102 °C against 106 °C. The cell never decoded inside a pause, so it shows
  nothing about what 2 threads do in one.
- **And 2 threads were slower.** Decode, summed over the 33 turns, took 1,581 s against 1,391 s
  (13.6% longer), turns 1,695 s against 1,505 s (12.6%), the 6-thread figures including their
  stalls. Take the excess out and the 6-thread decode is about 1,020 s.

In this pair, the 2-thread cell stayed below the trip and decoded about 12% fewer tokens per second,
including the 6-thread cell's stalls. One ordered pair does not separate the thread count from the heat each
cell inherited: both started with the XO thermistor at about 65 °C, but the 2-thread cell's
hottest CPU zone read 70.2 °C at its gate against 78.3 °C for the 6-thread cell's. It is not the
counterbalanced set that was planned, and it is one model on one unit. This cell never decoded
inside a pause; 2 threads inside one, and a switch to 2 at the pause, were measured later with the
pause set by hand ([r23](#the-pause-and-the-cpus-threads-r23)), and the counterbalanced rerun
from a shared gate ran on a fifth unit ([r22](#from-a-shared-gate-on-a-fifth-unit-r22)). Dropping to 2
threads after one slow token is still not measured.

#### From a shared gate, on a fifth unit (r22)

On 2026-11-09, in a thirteenth reservation, on a fifth unit (5d3e8f0a), the counterbalanced rerun
r19 lacked: the same cell, binary, model, conversations and flags, with a stricter gate before every
cell (the XO thermistor and the hottest CPU, GPU and NPU zone at or under 45 °C, the GPU's thermal
level 0 and every CPU ceiling at its top; a gate not passed in 1,800 s stops the chain) and the order
6 2 2 6 fixed before any result (`chain22.sh`). A second block, 2 6 6 2, started after r27's first
attempt stopped at its gate and was itself stopped during its third turn so that r27 could run; it is
not counted. The gates waited 0, 1,040, 1,110 and 1,130 s. The 1 s logger (`trig22.sh`) and
`place22.sh` ran beside it.

| Cell | Threads | XO at the first turn | Peak XO | Pauses | Steps over 500 ms | Excess stall | Longest step | Decode, summed | Turns, summed |
|---|---|---|---|---|---|---|---|---|---|
| r22-1-t6 | 6 | 38.1 °C | 77.5 °C | none | 0 | 0 | 0.2 s | 967 s | 1,071 s |
| r22-2-t2 | 2 | 48.6 °C | 76.7 °C | none | 0 | 0 | 0.2 s | 1,556 s | 1,667 s |
| r22-3-t2 | 2 | 48.4 °C | 76.8 °C | none | 0 | 0 | 0.2 s | 1,558 s | 1,669 s |
| r22-4-t6 | 6 | 48.0 °C | 78.3 °C | 3, 199 s | 20 | 189 s | 15.8 s | 1,254 s | 1,364 s |

- **With a similar first-turn XO, 6 threads tripped and 2 did not.** The gate is read before the
  model loads; cells 2 to 4 then began their first turns with the XO thermistor at 48.0 to 48.6 °C. The 6-thread cell crossed 78 °C and paused three times
  (199 s); the 2-thread cells peaked at 76.7 and 76.8 °C. The first cell, the day's first and so
  cooler (38.1 °C), peaked at 77.5 °C without a pause: from a cool enough start, 6 threads finished
  a whole cell under the trip.
- **The stalls were the pauses again.** Of the tripped cell's 189 s of excess, 129 s were in steps
  wholly under a pause and 61 s in steps that straddled a switch or a gap in the log, none outside; 326 of the 331 busy
  samples of a benchmark thread taken during a pause last ran on CPU 0 or 1, against 145 of 564
  outside them.
- **And 2 threads were slower anyway.** The two 2-thread cells decoded in 1,556 and 1,558 s: 24%
  longer than the tripped 6-thread cell with its stalls, and 61% longer than the untripped one. These
  are the totals observed; the stalls' own cost was not isolated, and switching only while paused was
  measured with a toggle set by hand ([r23](#the-pause-and-the-cpus-threads-r23)), not here.
- **What it does not show.** One block on one unit, so each count rests on two cells, one of which
  started cooler as a run's first cell does, and whether a 6-thread cell trips looks to depend on how
  near 78 °C a cell ends, which two cells cannot pin down. r19's cells on phone B do not compare with
  these: a different unit and a different gate.

### Throttling and the GPU's rank

r4 and r6/r7 differ in what they record. r4 records the GPU cap at the end of each turn; r5, r6
and r7 (`pd_bench5`) record the GPU cap and whether either CPU cluster's frequency ceiling was
lowered, at each turn's start and end. A "capped" turn below is one where any of that was seen; it
does not say which phase it hit.

- **Most turns were capped in the second session.** For Llama 3.2 3B, 7 to 29 of 33 turns per
  cell (21 to 29 in all but CPU prefill with GPU decode); for Qwen3 4B, 27 to 33; for LFM2.5 2.6B, 20 to 29. The GPU cap and a lowered CPU ceiling
  were seen on nearly the same turns (Llama 3.2 3B: 218 and 220 of 297), so the two cannot be told
  apart here. Cells that decoded on the CPU ended turns at a median 93 to 103 °C on the CPU zones.
- **On the turns uncapped in every cell, the CPU still decoded fastest, but the GPU's rank
  moved.** For LFM2.5 2.6B (4 common turns), decode read CPU 33.0 to 33.1, GPU 29.1 to 29.6, NPU
  18.6 to 21.1; for Llama 3.2 3B (4) CPU 24.8 to 25.9, GPU 19.8 to 20.0, NPU 19.2 to 19.3; for
  Qwen3 1.7B Q8_0 (2) CPU 31.1 to 31.3, GPU 25.4 to 26.0. Qwen3 4B has no turn uncapped in every
  cell. On Llama 3.2 3B the GPU edged ahead of the NPU there, where over all turns it was well
  behind (14.2 against 19.0), as Llama 3.2 1B's did in the first session (CPU 58, GPU 44, NPU 39
  on its 10 common turns). That is consistent with the cap costing the GPU its place but does not
  isolate it. These cohorts are small, all WildChat, and early in replay order.
- **Qwen3 0.6B's CPU decode** was 69.3 tok/s after NPU prefill against 59.6 in the CPU-only cell
  over all turns, but 75.8 against 75.6 on the 12 common uncapped turns: consistent with thermal
  conditions contributing to the difference. r4 read only the GPU cap at each turn's end, so this
  cohort says nothing about the CPU's own ceiling.
- **The cooling gate** (every zone at or under 55 °C, the GPU uncapped, and in r6/r7 no CPU
  ceiling lowered, waiting up to about 300 s) timed out on 10 of r6's 27 cells (8 of Qwen3 4B's
  9) and 2 of r7's 9. 7 of the 12 started within 2 °C of the limit; the warmest, two of the 4B's
  cells, started at 60 °C with the GPU capped at power level 8. The first session's figure: 50 of 53 cells passed.
- **In r2**, where the cell starts read 73 to 98 °C on the CPU, 70 to 86 °C on the GPU and 72 to
  87 °C on the NPU, Llama 3.2 1B's GPU prefill was 396 tok/s and its CPU decode 41, against r4's
  532 and 49.

### What the throttles cost on fixed work (r20)

The comparisons above could not separate the GPU's cap from the rest: few turns, early in replay
order, the GPU cap and the CPU ceilings seen together. So before the thread test, in the same
reservation, the same work ran again and again while the phone heated: Qwen3 4B, the first three
turns of one conversation (`probe.jsonl`, UltraChat-200k-2) with the dataset's histories and 128
output tokens a turn (127 timed steps), on a GPU-only cell and a CPU-only cell, alternating which
went first, with no gate. A CPU-only cell ran between probes to heat the phone. A turn of a given
index is then the same work at the same position every time, and only the phone's state changes.
Each turn is classed by the state over its decode steps, read from the 2 s loggers (`trig20.sh`,
then `trig20b.sh` from the fifth probe; a turn counts only if at least 90% of its decode time ran
in one pause state and one GPU power level). The analysis is `analyze_throttle.py ladder`; the two
ways of placing steps in time it compares agree on 22,096 of 22,098.

The plan was to heat until six probes had run through a pause, then idle back to uncapped and
repeat. In 25 rounds over 109 minutes only three probes did: pauses came in three episodes, 109,
120 and 114 s, 343 s in all. The ladder stopped at its heat limit, and the cool-down and its
uncapped repeats never ran, so the uncapped GPU figures are one probe, the first, and the cool CPU
figures two or three turns from the first three.

- **The mild throttle set in within minutes and stayed.** The GPU's thermal power level first went
  to 8 (an allowed ceiling of 578 MHz against 1,200) 227 s in, went back to 0 three times, and from
  528 s in stayed at 8 to the end.
- **Observed ratios on the same turn:** median rate at power level 8 with no pause divided by median
  rate uncapped and unpaused:

  | Turn (position, new tokens) | GPU decode | GPU prefill | CPU decode | CPU prefill |
  |---|---|---|---|---|
  | 0 (0, 139) | 13.2 of 19.3 tok/s, 0.68 | 138 of 271, 0.51 | 18.0 of 21.9, 0.82 | 78 of 123, 0.63 |
  | 1 (139, 1,104) | 9.2 of 14.4, 0.64 | 120 of 237, 0.51 | 13.9 of 17.6, 0.79 | 60 of 91, 0.66 |
  | 2 (1,243, 538) | 2.7 of 3.4, 0.80 | 90 of 178, 0.50 | 12.2 of 15.8, 0.77 | 44 of 65, 0.67 |

  GPU prefill retained about half its uncapped rate, close to the clock ratio (578 of 1,200 is
  0.48); decode retained 0.64 to 0.80. These timings do not establish why decode lost less. They are
  ratios of medians from one model on one unit, with a one-probe GPU baseline, not correction factors
  for other cells. The same ceiling set by hand on a cool phone, against six uncapped probes (5, 2
  and 6 of their turns counted), gave 0.678, 0.635 and 0.769 ([r21](#the-gpus-cap-r21)).
- **The cool-down did not visibly slow GPU decode.** On the three probes whose GPU turns 1 and 2 ran
  through a pause, decode was 0.997 and 0.994 of the same turns with no pause at the same power level.
  The pause switches move with `kgsl` and `cdsp`, so this does not isolate them, and no CPU-only
  probe ran through a pause. The pause alone, set by hand on a cool phone, cost GPU decode at most
  1.6% ([r24](#the-hard-throttles-pause-and-the-gpu-r24)).
- **The GPU's slowdown on a long conversation is there uncapped.** In the uncapped probe GPU decode
  fell from 19.3 tok/s at the start to 3.4 at position 1,243, the CPU's from 21.9 to 15.8. The large
  slowdown is present even without the logged GPU cap or CPU pauses, so those limits cannot fully
  explain it. This one probe does not isolate context length from other turn-dependent effects or
  identify a backend or hardware cause.
- **What it means for the matrix.** Every GPU-prefill and GPU-decode turn selected for the matrix
  recorded power level 8 at both ends (891 of 891 each, by the selection in the site's
  `build-prefill-decode-data.mjs`), as the CPU's turns recorded its lowered ceilings. The matrix
  compares the processors as this phone runs them once warm, not at their best: in these thermal
  states the GPU retained 0.64 to 0.80 of its uncapped decode rate and the CPU retained 0.77 to 0.82.
  This comparison does not isolate individual caps.

### The throttles' states set by hand on a cool phone (r21, r23 to r27)

r20 measured the throttles where they fall, on a hot phone, so heat and each cap arrived together and
its uncapped GPU figures rested on one probe. On 2026-11-09, in a twelfth reservation on the same
unit (c581fd31), each state was set by hand instead, as root, with the phone brought back under one
temperature threshold before every probe, so that the state set is the one deliberate difference
between arms (r27 did the same on a fifth unit, in the next reservation):

- the GPU's ceiling through kgsl's `max_pwrlevel` (0 is 1,200 MHz; 8 is 578 MHz, the level the
  thermal cap held in r20);
- the hard throttle's CPU pause through the two pause switches' `cur_state` (`thermal-pause-3C`,
  cores 2 to 5, and `thermal-pause-C0`, cores 6 and 7; the names are core masks);
- the CPU's ceilings through `scaling_max_freq` (1,900.8 and 2,227.2 MHz, the values r20 logged).

The work is r20's: Qwen3 4B, the first three turns of `probe.jsonl` with the dataset's histories,
up to 128 teacher-forced tokens a turn. A 1 s logger (`trig21.sh`) recorded the GPU's clock, its
hand-set and thermal levels, the pause switches, each CPU policy's ceiling and the XO thermistor.
Each order was fixed before any result, after one throwaway probe. The gate is read before the
model loads and each arm's state applies from the load on, so by each probe's first turn the hottest
CPU or GPU zone read 50 to 66 °C in r21, 54 to 69 in r24, 71 to 74 in r23 and 62 to 100 in r25: the
arms start from the same gate, not the same heat, and an arm whose state runs cooler stays cooler.
Which turns count differs by run (each rule is in `analyze_throttle.py`'s docstring). A turn of
r21 needs 90% of its decode time unpaused and at thermal level 0 by r20's bracketing rule, and its
arm's hand-set level in every logger sample stamped during its decode, less its last second; a turn
of r24 or r25 needs its arm's state in every such sample (r25's U arm only no pause and no ceiling
set by hand, and r25 does not check the GPU); a paused turn of r23 or r26 needs the pause in every
such sample, an unpaused one in none. Prefill rates are reported for the same turns, without a
check of their own. The stamps are whole seconds taken just before the reads, pause episodes run
from one switch transition to the next with no check for gaps, and steps are placed back from each
turn's end; placing them forward from the prompt's end instead changes the state of 27 of r23's
5,715 steps, 4 of r26's 635, 1 of r24's 4,572 and none of r21's or r25's.

Two things had to be checked before any of it meant anything. Writing `max_pwrlevel` held every
busy sample of a capped probe at or below 578 MHz (one, in a warm-up, at 500) while `max_gpuclk`
went on reading 1,200, since only the
thermal cap moves that file; the benchmark's own records therefore say 1,200 MHz for capped turns,
and the analysis reads the logger instead. And the pause set by hand does what the thermal one
does: with both switches at 1, all 48 reads of six busy loops' last CPU found core 0 or 1 (24 each),
against 60 spread over cores 0 to 5 with the switches at 0 (a check run by hand, its output kept in
`pausecheck23.txt`); the switches held for 30 s idle and under load (`feas23.txt`), and the
processes' allowed-CPU lists read 0 to 7 throughout, as they did in r19.

#### The GPU's cap (r21)

Twelve GPU-only probes, uncapped (U) or capped at 578 MHz (F), in the order U F F U F U U F U F F U.
Before each the phone came back under 42 °C (hottest CPU and GPU zone) with no thermal cap; the
waits were 25 to 255 s, none timed out, and the XO thermistor read 38.8 to 39.7 °C at each gate.
The analysis is `analyze_throttle.py clock`.

| Turn | Decode at 578 MHz | Uncapped | Ratio (each capped probe against its neighbours) | Prefill ratio | r20, thermal cap on a hot phone |
|---|---|---|---|---|---|
| 0 | 12.85 tok/s | 18.96 | 0.678 (0.669 to 0.680) | 0.54 | 13.2 of 19.3, 0.68 |
| 1 | 9.01 | 14.20 | 0.635 (0.631 to 0.641) | 0.49 | 9.2 of 14.4, 0.64 |
| 2 | 2.90 | 3.77 | 0.769 (0.762 to 0.777) | 0.50 | 2.7 of 3.4, 0.80 |

Ratios are of medians. The range in brackets is each capped probe against the mean of the counted
uncapped probes at most two places from it in the run order (up to two, from one side or both):
four comparisons at turn 1, six at the others.

- **The ceiling alone came close to the thermal cap's rates.** Capped by hand on a cool phone, GPU
  decode ran 2.5% and 2.4% slower than r20's thermally capped medians on a hot phone at turns 0 and
  1, and 5.6% faster at turn 2; prefill 0.9 to 2.8% slower (137, 117 and 88 tok/s against 138, 120
  and 90). The hand-set ceiling gives rates close to what the mild throttle gave; how much heat adds
  on its own is not identified by runs this different.
- **The baseline is six probes, not one.** Under the analysis' rule 5, 2 and 6 uncapped turns count
  at turns 0, 1 and 2 (the next point says why), and across them uncapped decode varied by less than
  2%. r20's single uncapped probe was slower at turn 2 (3.4 tok/s against 3.76 to 3.83), which is
  most of why r20 put that turn's ratio at 0.80.
- **Uncapped does not hold, even from cool.** The hottest GPU sensor reached 100 to 105 °C during
  uncapped prefill, and five times, each during an uncapped prefill, the thermal engine lowered the
  GPU one to three levels (as far as 1,000 MHz) for about a second, so the uncapped prefill rates
  include those seconds; capped, the sensor peaked at 61.0 °C. Five uncapped turns whose decode
  began or ended within 2 s of such a moment are left out by the analysis' rule; every sample taken
  during their decode read uncapped, and counting them moves no ratio by more than 0.002.

#### The hard throttle's pause and the GPU (r24)

Twelve GPU-only probes in four arms, the ceiling uncapped (U) or at 578 MHz (F) and the pause off (0)
or on (1) for the whole probe, in the order U0 U1 F1 F0, F0 F1 U1 U0, U1 U0 F0 F1. The gate was the
one used for r23 and r25: the hottest CPU and GPU zone and the XO thermistor at or under 45 °C, no cap,
no pause, every CPU ceiling at its top (waits 90 to 205 s, none timed out). Every turn of every
probe was in its arm's state in every sample (`analyze_throttle.py gpupause`), and no step took
longer than 500 ms.

| Turn | Pause against none, uncapped | Pause against none, at 578 MHz | Decode uncapped / paused, tok/s |
|---|---|---|---|
| 0 | 0.994 | 1.002 | 18.94 / 18.82 |
| 1 | 0.984 | 0.997 | 14.24 / 14.01 |
| 2 | 0.998 | 0.992 | 3.76 / 3.75 |

- **The pause cost GPU decode 1.6% at most among these medians.** The largest difference, uncapped
  at turn 1, is outside both arms' ranges (14.23 to 14.25 against 13.98 to 14.05 tok/s); the rest are under 1%. This
  agrees with r20's 0.997 and 0.994 through natural pauses, now with a common cooling gate and the
  pause as the deliberate difference. It is the pause alone: the natural hard throttle also sets the `kgsl`
  and `cdsp` cooling states to 5, and set by hand the pause left both at 0 (one of the 402 paused
  samples during r24's probes read `kgsl` 1). Through r20's natural pauses the GPU's power level stayed at 8.
- **GPU prefill was not slower under the pause.** At turn 0, uncapped, it was about 5% faster (266
  to 271 tok/s against 253 to 257); at 578 MHz the turn-0 ranges overlap (118 to 140 against 118 to
  141), and turns 1 and 2 are within 2.2%. These runs do not show why.
- **The cap repeats.** At 578 MHz without a pause, decode was 0.676, 0.632 and 0.770 of uncapped, as
  in r21 (0.678, 0.635, 0.769).

#### The pause and the CPU's threads (r23)

Eighteen probes with GPU prefill and CPU decode, as in the thread cells, in six arms: no pause with 6
or 2 threads (N6, N2); the pause on for the whole probe with 6 or 2 (P6, P2); the pause toggled 15 s
on and 15 s off from the probe's start with 6 threads (T6), or with 6 that drop to 2 while it is on
(TS). The switch is a flag added to the benchmark for this, `--paused-threads 2`: before every decode
step it reads the two pause switches and, when the count it wants changes, calls
`llama_set_n_threads`; the turn record keeps each step's count. It reads the switches as root: a
strategy tested here with root's help, not something an app can do as written (the switches carry
the `sysfs_thermal` label, and whether an app may read it was not tested). Every arm ran the same new binary (`pd_bench_sw`, sha256 prefix
3307ddc1e66ccddd). Three of each, in the order N6 P6 T6 N2 P2 TS, TS P2 N2 T6 P6 N6, P6 TS N6 P2 T6 N2;
gates of 195 to 455 s, none timed out; a probe still running at 1,200 s was killed. The analysis is
`analyze_throttle.py pause` and `place23.py`.

| Arm | Decode, three turns | Excess stall | Decode rate while paused | While not |
|---|---|---|---|---|
| N6 | 21.0 s (21.0 to 21.2) | 0 | none | 18.0 to 18.2 tok/s |
| N2 | 33.8 s (33.8 to 34.0) | 0 | none | 11.2 to 11.3 |
| P6 | none finished its first turn in 1,200 s | | no turn ended | none |
| P2 | 54.9 s (54.7 to 55.4) | 0 | 6.88 to 6.97 | none |
| T6 | 47.4 s (47.1 to 50.8) | 24.5 s | 0.28 to 0.91 | 17.8 to 18.1 |
| TS | 37.4 s (37.4 to 43.3) | 5.1 s | 4.29 to 6.32 | 17.2 to 17.7 |

Ranges are the three probes'. A step's token counts toward "while paused" by the share of the step
the logged pause episodes covered; that assumes a step progresses evenly, so it splits the tokens by
a model rather than observing a rate in each state.

- **The pause alone makes 6 threads stall, on a cool phone.** None of the three P6 probes finished
  turn 0, whose 127 steps take 5.6 s unpaused, before the 1,200 s limit. Every busy sample of a
  benchmark thread was on CPU 0 or 1, and between consecutive samples the threads present in both
  had used 1.43 to 1.73 cores' worth of CPU time (median per probe): the threads stayed busy on the
  two cores left while the turn did not end. That is consistent with the barrier mechanism above.
  Other causes are not ruled out: with no pool attached ggml makes a new one for every graph (each
  P6 probe showed 590 to 597 distinct thread ids), the scheduler's share between six threads was not
  traced, and no profile was taken.
- **Two threads do not stall in a pause.** P2 decoded at 6.9 tok/s with no step over 500 ms, against
  N2's 11.2 unpaused: the two cores the pause leaves (0 and 1, the 3.63 GHz cluster) are slower than
  the 4.6 GHz pair 2 threads otherwise ran on (CPU 6 and 7 held 54 to 58% of N2's busy samples),
  though the thermal engine lowered N2's CPU ceilings in 64 of its 77 decode samples and P2's in
  none.
- **Switching recovers most of a pause.** With the pause on and off every 15 s, TS decoded at 4.3 to
  6.3 tok/s while paused against T6's 0.3 to 0.9, and finished the three turns in 21% less time
  (37.4 against 47.4 s) with similar paused exposure (a median 25.7 s each by the share of logger
  samples; 27.8 s for T6 and 24.8 s for TS by the logged pause episodes). Outside the pauses the arms
  also differed in CPU ceilings, which the thermal engine lowered in 52 of T6's 100 decode samples
  and 31 of TS's 80. It did not reach P2's
  6.9: a step already running on 6 threads when a pause begins still stalls, and that is what most
  of TS's 5.1 s of excess is. The count matched the logged pause on all 605 steps that could be
  classed. No cost of reading the switches showed: the one TS turn with no pause near it decoded at
  17.74 tok/s, against N6's 17.54 to 17.72 at the same turn, and one turn cannot show a small cost.
  The arms' rates "while not" mix different turns and ceilings, so they do not measure it.
- **What it does not show.** Natural pauses lasted 109 to 120 s in r20, not 15, so a real one has
  fewer edges and longer stretches where switching pays; this toggle measures the cost of the edges,
  not a natural pattern. The app runs 4 threads by default, 3 or 2 as Android's thermal status rises
  (`ThermalPolicy`), and it attaches a persistent thread pool to each context
  (`engine_session.cpp`), which the benchmark does not, so the app's own behaviour in a pause was not
  measured in r23; r27 measured it. A detector an app could use, such as a slow step, was not built or measured.

#### The CPU's ceilings (r25)

Eight CPU-only probes (`cpu:cpu`, 6 threads, the reference binary), with no ceiling set (U) or with
the soft throttle's ceilings written to `scaling_max_freq` (C: 1,900.8 MHz on cores 0 to 5 and 2,227.2
MHz on 6 and 7), in the order U C C U, C U U C, cut from twelve before it started so that it would
finish inside the reservation. The gate as for r24 (waits 145 to 235 s, none timed out). The
analysis is `analyze_throttle.py cpuclock`.

| Turn | Decode at the soft ceilings | No ceiling set | Ratio | Prefill ratio | r20, on a hot phone |
|---|---|---|---|---|---|
| 0 | 18.33 tok/s (18.25 to 18.65) | 22.45 (22.32 to 22.50) | 0.817 | 0.62 | 18.0 of 21.9, 0.82 |
| 1 | 14.23 (14.19 to 14.29) | 17.72 (17.60 to 17.77) | 0.803 | 0.68 | 13.9 of 17.6, 0.79 |
| 2 | 12.93 (12.80 to 13.04) | 15.61 (15.48 to 15.62) | 0.828 | 0.74 | 12.2 of 15.8, 0.77 |

- **The ceilings alone came close to r20's rates too.** Set by hand on a cool phone, they gave
  decode 2.0%, 2.4% and 5.9% faster than r20's medians on a hot phone at turns 0 to 2, and prefill
  1.4 to 7.9% faster (79, 63 and 47 tok/s against 78, 60 and 44); how much heat adds on its own is
  not identified.
- **"No ceiling" is not the CPU at its top.** By the end of the first turn, seconds after a cool
  start, the hottest CPU zone read 103 °C, and it peaked at 106.6 °C; the thermal engine moved both
  ceilings about every second, so across the U probes' decode no logger sample had both at their tops
  (medians 3,187.2 and 3,648 MHz, lowest 2,745.6 and 2,668.8). The benchmark's own records, read at
  each turn's ends, show full ceilings for all of them. So the ratios compare the soft throttle's
  ceilings with the phone's own fast mitigation, as r20's did, not with a fixed top clock. With the
  ceilings set lower, the hottest CPU zone peaked at 82.6 °C and nothing else moved them.

#### The app's thread counts in a pause (r26)

The app decodes with 4 threads, or 3 or 2 as Android's thermal status rises (`ThermalPolicy`), and
r23 ran neither 4 nor 3 in a pause. In the reservation's last half hour, as r23 in everything but a
600 s watchdog and no warm-up probe: paused 4 threads (P4), paused 3 (P3), then 4 unpaused (N4), one
probe each, in that order; of a second block, N4 P3 P4, the N4 probe finished before the reservation
ended.

| Threads, pause | Decode |
|---|---|
| 6, paused (r23, three probes) | no turn ended in 1,200 s |
| 4, paused | no turn ended in 600 s |
| 3, paused | turns 0 and 1 at 0.48 and 0.46 tok/s, every step 1.1 to 3.4 s (median 2.1 and 2.2); stopped by the watchdog in turn 2 |
| 2, paused (r23, three probes) | 6.9 tok/s, no step over 500 ms |
| 4, not paused (two probes) | 21.12, 16.18 and 13.97 tok/s at turns 0 to 2 (6 threads: 22.44, 17.60 and 15.49) |

- **More threads than the two cores left stall, and the app's default is one of them.** 4 threads
  did not end the first turn in 600 s, 3 decoded at under 0.5 tok/s, 2 at 6.9. As in P6, every
  busy sample of P4 and P3 was on CPU 0 or 1.
- **What it does not show.** One probe each, and the benchmark's thread handling; the app's own
  engine, with its persistent pool, is r27 below. Unpaused, 4 threads decoded 6 to 10% slower than 6
  on this work.

#### The app's own engine in a pause (r27)

r23 and r26 used the benchmark, which leaves ggml to make a new thread pool for every graph. The app
attaches one persistent pool to each context and reads prompts with as many threads as it writes
replies (`ThermalPolicy`). So on the fifth unit, after r22, the engine's own device test ran with
the pause set by hand: `ThreadCountBenchmark` (`LlamaCppEngine`, the app's native session; its log
confirmed "N workers kept alive across graphs" on every run), one thread count per run, a 19-token
prompt and 64 tokens of reply, Qwen3 4B Q4_0 (the study's file). Arms: N<t> or P<t>, the pause off or
on (both switches, found by type) for the whole run, t in 2, 3, 4 and 6, in the order N4 P4 P2 N2 P3
N3 N6 P6, then the reverse (`chain27.sh`); in the reservation's last hour, P4 N4 N2 P2 P2 N2 N4 P4
and then P3 N3 N3 P3 (`chain27c.sh`, `chain27d.sh`), each order fixed before its runs, so 2, 3 and 4
threads ran four times in each state and 6 twice. r23's gate, a 420 s watchdog, and logcat kept for
the test's and the engine's own tags only. The test logs every live progress event, so a run the
watchdog stops keeps its last count. Every run held its arm's state in every logger sample inside
it, a second in from each end (setup and cleanup excluded), with both written switches checked.

| Threads | Not paused | Paused | Paused against not |
|---|---|---|---|
| 2 | 22.91 tok/s (22.84 to 22.97) | 10.57 (10.14 to 10.72) | 0.46 |
| 3 | 22.53 (22.34 to 22.64) | 0.51 (0.50 to 0.53) | 0.023 |
| 4, the app's default | 23.12 (23.09 to 23.17) | 0.24 (0.22 to 0.25) | 0.010 |
| 6 | 23.25 (23.23, 23.27) | 0.083 (0.082, 0.084), both stopped at 420 s | 0.004 |

Medians, with the range over the runs; decode rates from each run's last progress line, tokens after
the first over the decode time to the latest one (the engine's own summary prints one decimal, too
coarse in a pause). The analysis is `analyze_app.py`.

- **The app's engine stalls in a pause as the benchmark did.** At its default of 4 threads it decoded
  a token every 4.1 s against 43 ms, about a hundredth of its rate (0.0105); with 6 it wrote 34 tokens in
  about 400 s. The persistent pool did not prevent it: the paused rates fall with every thread past
  the two cores left, as in r26 (3 threads: 0.51 here, 0.46 to 0.48 there; the two differ in work and
  unit, so this is agreement in kind, not a controlled comparison of the two pools).
- **2 threads kept 46% of the unpaused decode rate.** And unpaused, on this short prompt, 2, 3, 4 and 6
  threads decoded within 3.2% of one another (medians 22.53 to 23.25 tok/s), so 2 threads gave up almost
  no decode rate outside a pause here; their first token came later (median 272 ms against 214 ms for
  4 threads). That holds for a 19-token prompt and 64 tokens; at the dataset's
  longer contexts the benchmark's 2 threads were slower than 6 unpaused (r23, r22).
- **What it does not show.** Four runs per arm (two with 6 threads) on one unit, a short reply at a
  short context, and the pause set by hand; the app's thermal policy, which picks the count from Android's status before
  each reply, was not in the loop, and no slow-step detector was tried.

#### Where a paused thread's time goes (r28)

Everything above fits threads that spin while they wait, and nothing had shown it. On the fifth
unit (5d3e8f0a), the benchmark decoded with 6 threads with the pause on (P6) and off (N6) while
`simpleperf record -e cpu-clock -f 1000` sampled its process (30 s paused, 20 s not; `chain28.sh`,
`perf-P6.txt`, `perf-N6.txt`).

| | Paused, 6 threads | Not paused, 6 threads |
|---|---|---|
| Samples, and cores busy on average | 55,029 in 30 s, 1.83 | 69,192 in 20 s, 3.46 |
| `ggml_graph_compute_thread` | 69.6% | 8.6% |
| The Q4_0 matrix kernel (repacked) | 25.5% | 60.7% |
| Flash attention | 3.4% | 6.9% (plus 10.3% in the Q6_K kernel) |

`ggml_barrier` is inlined into `ggml_graph_compute_thread`; its wait (`disasm-r28.txt`,
0x282ec to 0x282fc) loads the barrier's counter, compares it with the value at entry, leaves if it
moved, and otherwise executes a `yield` hint and loops. The hint does not give up the core. So with
six threads on the two cores the pause leaves, about 70% of the CPU time went to the function that
holds that wait, against 9% unpaused, while the matrix and attention kernels' share fell from about
four fifths to under a third.
The samples were kept by function and by thread, not by instruction, so the split inside
`ggml_graph_compute_thread` between the wait and its other code is not in the files; its other code
is a loop over the graph's nodes, 8.6% of an unpaused run that does about a hundred times the work.

#### A barrier that gives up its core (r29, r31)

If the waiting threads gave their cores back, the working ones could finish. A patch to llama.cpp's
CPU backend (`barrier-yield.patch`, built into `libggml-cpu.so` 5e5eebcd) adds a `sched_yield()`
after a set number of spins, read from `GGML_BARRIER_YIELD_SPINS`; unset, it is the stock loop.
The benchmark ran Qwen3 4B Q4_0 on one three-turn conversation (UltraChat-200k-2, 128 tokens a
turn, prompts on the GPU, replies on the CPU), each arm after r23's gate, the pause off (N) or on
(P) for the whole arm, 6 or 4 threads, and Y0 (the stock wait), Y10k or Y1M spins before a yield,
in orders fixed before any result: r23's work, so its N6 matches r23's 21.0 s over the three turns.
r29 ran on the fifth unit, r31 on phone D (4816891); a 300 s watchdog stopped an arm with turns still
to go.

| Arm | r29, tok/s per turn | r31, tok/s per turn |
|---|---|---|
| N6-Y0, stock, not paused | 22.58, 18.15, 16.31 | 21.98, 17.50, 15.78; 22.39, 17.68, 15.31 |
| N6-Y1M | | 21.95, 17.53, 15.83; 22.05, 17.32, 15.88 |
| N6-Y10k | 6.98, 6.12, 10.13 | 4.41, 17.56, 16.20 |
| P6-Y0, stock, paused | no turn finished | no turn in 300 s |
| P6-Y1M | | 0.63, then stopped; twice |
| P4-Y1M | | 1.20, 1.16, then stopped; twice |
| P6-Y10k | 3.42, 3.11, 2.98; 3.56, 3.28, 3.17 | 3.51, 3.21, 3.07 |
| P4-Y10k | 3.56, 3.24, 3.11; 3.57, 3.27, 3.23 | |

Decode steps over decode time, per turn; each arm's turns in order, separated by semicolons where an
arm ran twice. The rate falls from turn to turn as the context grows.

- **Yielding ends the stall, and how early it yields decides what it costs.** After 10,000 spins
  the paused phone decoded 3.0 to 3.6 tok/s with 6 or 4 threads, where the stock wait finished no
  turn. But the same setting slowed unpaused decode, in all three of r29's turns (to 31% to 62% of
  the stock rate) and in the first of r31's (20%), so a yield that early costs the case with no
  pause, and not predictably.
- **After 1,000,000 spins it cost nothing measurable unpaused and saved little paused.** Unpaused,
  Y1M's turns fell inside or beside the stock wait's two runs (21.95 to 22.05, 17.32 to 17.53 and
  15.83 to 15.88 tok/s, against 21.98 to 22.39, 17.50 to 17.68 and 15.31 to 15.78). Paused, it
  decoded 0.63 tok/s with 6 threads and 1.2 with 4, against about 3.2 for Y10k.
- **Neither beats fewer threads.** On this same work, in the same pause, 2 threads decoded 6.88 to
  6.97 tok/s with the stock wait (r23, P2), about twice Y10k's rate. A budget between the two, or a yield that
  starts late and then comes sooner, was not tried; what an upstream change would need is a wait
  that costs nothing when every thread has a core and gives the core up when one does not.

### A fallback in the app's engine (r34, r38, r39)

An app cannot count on reading the pause: on this QDC build the test process could read all eleven
pause devices, but on the owner's retail POCO it could not even list `/sys/class/thermal` (r36's
check). What every app has is how long each decode step took. So the engine now carries a guard
(`stall_guard.h`, `StallGuard`) that watches its own steps: two steps in a row that are slow, more
than 8 times the recent normal step or more than 2 s over it, and over 250 ms (or over 1.5 s with
no history yet), drop decode, and prompt reading with it, to 2 threads; every 30 s, once it has at
least four steps at 2 threads, it tries one step at the planned count, and keeps it if that step
took at most 3 times the median of its last eight steps at 2 threads and at most 2 s over it (since
r50 it also tries at once when its 2-thread steps get much faster,
[below](#trying-again-as-soon-as-the-steps-speed-up-r50-a-snapdragon-8-gen-2)). The
2 s is from the pause itself, which added 4 to 9 s to each step where it was measured rather than
multiplying it, so that a model whose normal step is already a second, where the pause is 4 to 6
times, is caught too. The same 2 s bounds a try: a pause that added under 2 s to a step already near 2 s
at 2 threads could pass one and become the normal; none measured added under 4 s. A thermal re-plan never ends a fallback, and a try whose decode fails, or
that is still pending when the next reply starts, is dropped, not judged. The normal step is the
median of the last eight steps that were not slow; against fewer than two of them only a step past
1.5 s is slow, until four are in a step past 1.5 s is slow as well, and after that a step joins them
only within 3 times their median, so neither one odd early
step nor a slowdown in smaller steps moves the bar a stall must clear. It does nothing to a plan of
2 or fewer threads, and `OPENWEIGHTS_STALL_GUARD=0` turns it off. Its host test (`stall_guard_test.cpp`,
24 cases on step times modelled on r27, 36 since r50) runs in CI. The device runs below used `engine-test5`, built
before a fourth and a fifth review changed how the normal step is kept and added the reply-start
drop; in every run measured the normal step was steady and the stalled ones 37 to 127 times longer
(the guard's own log lines), which every version judges alike. The final rules ran on the POCO
(r43, r45) without a pause; none has run in one.

r34 ran the engine's own `ThreadCountBenchmark` on phone D at a realistic length: a 1,751-token
prompt (one user message, `prompt-r30.txt`), up to 1,024 tokens of reply, an 8,192-token window, 4 threads
unless named. Arms: N, no pause; W, both switches on from 10 s after decode starts to 40 s after (a
pause that comes and goes); M, on from 10 s after decode starts to the run's end; each with the
guard off (0) or on (G). Decode's start was read from the run's own progress lines as it ran. Block 1
(`engine-test4`) ran W4-G M4-0 N2-0 N4-G M4-G W4-0 N4-0; block 2 (`engine-test5`, the guard after
the third review) the reverse order. r38 repeated the M arm four times on `engine-test5`
(M4-G M4-0 M4-G M4-0) and recorded, every 2 s, the CPU each of the test process's threads last ran on;
r39 ran W4-G twice more on `engine-test5` with the same record.

| Arm | Runs | Tokens | Decode tok/s | Guard |
|---|---|---|---|---|
| N4-0, no pause | r34-7, r34-8 | 1,022; 1,024 | 10.04, 10.30 | off |
| N4-G | r34-4, r34-11 | 1,024; 1,024 | 8.90, 10.18 | fell back 105 s into decode in r34-4 (below) |
| N2-0 | r34-3, r34-12 | 1,024; 1,024 | 8.94, 9.16 | off |
| W4-0, a 30 s pause | r34-6, r34-9 | 1,023; 1,024 | 8.20, 8.38 | off |
| W4-G | r34-1, r39-1, r39-2 | 1,024; 1,024; 1,023 | 8.84, 8.84, 8.61 | fell back 6, 12 and 14 s after the pause began, back 6, 12 and 14 s after it ended |
| M4-0, a pause that stays | r34-2, r34-13, r38-2 | 192, 181, 183 by the watchdog | 0.61, 0.57, 0.58 | off |
| M4-G | r34-5, r38-1, r38-3 | 1,013 by the watchdog; 1,024; 1,024 | 3.21, 3.37, 3.36 | fell back 7, 19 and 15 s after the pause began |

Tokens after the first over the decode time to the last progress line (`analyze_guard.py`); the
watchdog stopped a run 420 s after it started.

- **A pause that stays: 5.5 to 5.9 times the tokens a second.** With the guard the engine wrote
  3.21 to 3.37 tok/s through the rest of the reply; without it 0.57 to 0.61. Each try at 4 threads
  in the pause costs one stalled step, 2.6 to 5.7 s in r34-5 and 5.0 to 8.7 s in r38's two runs,
  one try every 33 to 38 s.
- **A pause that comes and goes.** The three W4-G runs that held their pause decoded in 115.7, 115.8
  and 118.7 s, against 124.6 and 122.0 s for the two W4-0 runs. The return to 4 threads comes at the
  guard's first try, 30 s after it fell back, so 6 to 14 s after the pause ended here.
- **Falling back costs a little where the pause never came.** N2-0 decoded 11% slower than N4-0
  here (medians 9.05 against 10.17 tok/s). The guard only falls back on a stall: the one guarded run
  with none, r34-11, decoded 10.18 tok/s, between the two unguarded runs (10.04 and 10.30); one run
  cannot measure what its bookkeeping costs. On the owner's MediaTek POCO (r41, on USB power, the
  same prompt and length, the guard on, order 4 2 2 4) 2 threads cost far more: decode 4.86 and 5.07
  tok/s against 6.77 and 7.22 with 4 (about 29% slower), prompt reading 6.29 and 7.03 against 12.54
  and 13.65 (about 49% slower), the first token at 249 and 278 s against 128 and 140 s. At this
  length, then, a fixed 2 threads costs the POCO far more than the SM8850, which is what falling back
  only on a stall avoids; other lengths were not run there.
- **One stall with no pause on the logged switches.** In r34-4 (N4-G) two steps took 3.9 and 4.8 s
  against a normal 129 ms, 105 s into decode, with no pause switch on, no major page fault and the
  CPU ceilings unchanged; the hottest CPU zone fell from 89 to 73 °C over those seconds, as it does
  in a pause, and the guard fell back for the run's last 10 s. The logger read three of the unit's
  eleven pause devices (both `thermal-pause-3C` and `thermal-pause-C0`); the eight one-core devices
  (`thermal-pause-1` to `-80`) were not logged, so a pause through them is not ruled out. No other
  unpaused run had a step over 1.5 s.
- **At least one of fourteen pauses set by hand did not hold, and likely three.** In r38-4 (M4-0) the test's busy threads ran
  on CPUs 0, 1, 6 and 7 with both written switches and the third reading 1, about 3.6 cores of CPU
  time a second, and decode went on at 6.72 tok/s; in the three r38 runs whose pause held, they ran
  on CPUs 0 and 1 only, 1.7 cores a second, as did r39's two (1.6). Two runs in r34's block 2 (r34-10 M4-G and r34-14
  W4-G) behaved the same way by every measure kept for them (no step over 1.5 s, decode at about 70%
  of unpaused, about four cores of CPU time, the hottest CPU zone near 80 °C rather than the low 60s
  of a held pause), but have no placement log to show it; they are left out of the table as likely
  pauses that did not hold, not counted as guard results either way. So `thermal-pause-C0` written to 1 does not always keep work off
  CPU 6 and 7 on this unit; why was not found.
- **What it does not show.** One unit, one model, one prompt; the pause set by hand; the build that
  ships (`engine-test5`) fell back and tried 4 threads again in r38, where the pause stayed, and
  fell back and returned in r39.
  A pause that starts while the prompt is being read is not watched: the guard reads decode steps,
  and r27's paused prompt reading at 4 threads ran at 2.8 tok/s against 27.7 with 2.

#### Trying again as soon as the steps speed up (r50, a Snapdragon 8 Gen 2)

A scheduled try comes up to 30 s after the cores return, and r48's reply ended at 2 threads before
its next one was due. So the guard now also tries the planned count at once when two steps in a row
at 2 threads are each at least 1.6 times faster than the median of its last eight there
(`RETURN_FACTOR`, `RETURN_CONFIRM`): when a pause ended, 2-thread steps had got 2.2 to 3.5 times
faster (r49 with the pause set by hand, r34 and r38 on phone D, r48 in a heat brought on). Such a try
is judged against the slower of the two fast steps, not against the fallback's median, which still
holds the pause's slower steps: judged against that, a step still stalled at the planned count could
pass and become the normal. An early try that fails turns early tries off until the fallback ends, so
a pause whose 2-thread steps keep swinging past the factor costs one stalled step, not one every few
steps; the scheduled try stays as the backstop throughout, and is still judged against the
fallback's median as it stands when the try is judged. The two fast steps must be two in a row at
the fallback count: a step at a lower plan between them breaks the run. Fast steps make an early try
even at the step the schedule falls due. `OPENWEIGHTS_STALL_GUARD=timer`
keeps only the scheduled tries, exactly the rule before, so one build runs both, and the engine now
logs every try ("trying 4 again", scheduled or "steps sped up"), every failed one and every one
dropped before it was judged. The host test has 36 cases; seven deliberate breaks of the new code
each fail it: the early try judged against the median, a scheduled one against a median saved when
it started, no limit on false early tries, the history cleared after a failed try, a fast run kept
across a lower plan, one fast step enough, and the timer mode ignored.

r50 ran a first version, which Codex then reviewed: it judged an early try against the fallback's
median, and it cleared the fallback's history after any failed try instead of limiting early tries.
In a host test of 2-thread steps swinging 2.7 times for nine minutes it made 50 false early tries
where the final rules make one, and a slow try after an early trigger could pass. A second review
added the two rules after it: a scheduled try judged on the median at judgement, and fast runs broken
by a lower plan. None of these changes an
outcome below: each W run's early try took about 85 and 104 ms, under the 116 and 123 ms 2-thread
steps that started it, so it passes against either; no run re-planned its thread count; each M run
made one early try and it failed, and with 2-thread steps under 2.1 s the
four new steps a cleared history needed came well inside the next 30 s. The rules after the first
review ran as r50b, below; the second review's two act only on a re-plan, which r50b made none of
either.

r50 ran on a sixteenth reservation, sa964839, on 2026-11-10, on unit 57fa7c29: an SM8550 (Snapdragon 8
Gen 2, Android 14; cores 0 to 2 at up to 2.0 GHz, 3 to 6 at 2.8, 7 at 3.19). Its kernel binds
`thermal-pause-F8` (cores 3 to 7) to `xo-therm` at 78 °C, the SM8850's XO trip, but it leaves the three
little cores; that device written 1 by hand was the pause. With it on, every sampled busy loop ran on
cores 0 to 2, and it read 1 in 45 of 45 reads over 90 s under load (`hold50.log`). With it on and the
guard off, 4 threads decoded at about 0.15 tok/s, 5 to 7 s a token, against 15.5 tok/s with it off
(`probe50.log`, the benchmark's 19-token prompt). Then `chain50.sh`, on the work of r34 and r39: Qwen3
4B Q4_0, the 1,751-token `prompt-r30.txt`, up to 512 tokens, 4 threads, an 8,192-token window. Arms W
(the pause on 10 s after decode starts, off 30 s later) and M (on from 10 s, held), each with the guard
as built (E) or `timer` (T), in the order W-E W-T W-T W-E M-E M-T M-T M-E, then N-E with no pause,
fixed before any run; a cooling gate before each run and a 600 s watchdog. A logger read the switch
and each engine thread's last core about every 2 s; in every paused run the samples covered the pause
(the first and last within 4 s of its ends, no gap over 3 s), every one read the switch on, and every
busy engine thread was last on core 0, 1 or 2. The analysis is `analyze_guard50.py`; the files are in
`tools/snapdragon/results/2026-11-10-sm8550/`. The chain logged the pause's on and off in whole
seconds, read just after the switch was written and read back, so a time measured from them is an
approximate one, from a whole-second timestamp logged after the write; r50b also logged a marker into
logcat just after each write, so its times are measured to the millisecond from that marker.

| Arm | Fell back after the pause began | Tries | Back to 4 after the pause ended | Decode |
|---|---|---|---|---|
| W, early try (2 runs) | 17.2 and 15.5 s | 1 each, early, passed | 1.0 and 0.7 s | 5.70 and 5.46 tok/s |
| W, scheduled only (2) | 15.3 and 21.6 s | 1 each, scheduled, passed | 15.5 and 21.8 s | 5.44 and 5.08 |
| M, early try (2) | 14.1 and 14.5 s | 13 each, all failed: 12 scheduled, 1 early | never, the pause held | 0.89 and 0.90 |
| M, scheduled only (2) | 14.8 and 20.0 s | 12 each, all scheduled, all failed | never | 0.90 and 0.91 |
| N, early try (1) | no fallback | none | | 7.93 |

Decode is the tokens after the first over the decode time to the last progress line; the M runs
were stopped by the watchdog after 484 to 495 tokens, about 530 s into their pause.

- **Back in about a second instead of 15 to 22.** In both W runs with the early try, the try came 0.6
  and 0.9 s after the logged second of the switch's end, and passed: about a second after the write
  at most, from whole-second marks. With the schedule alone it came 15.4 and 21.6 s after; that wait depends on where the 30 s
  clock stood when the pause ended, so it can be anything up to 30 s plus a step.
- **One false early try in each held pause.** Each M-E run made one early try that failed, 431 and
  424 s into the pause, beside its 12 scheduled ones. The 2-thread steps in the pause wandered from
  0.71 to 2.1 s (the last step before each try), and the two that fired were on steps of 743 and 832
  ms. A failed try is one stalled step at 4 threads: 7.8 to 11.9 s here, medians 9.4 to 10.4 s by run.
  The held-pause decode rates differ by at most 0.02 tok/s between the arms.
- **Here the schedule is the costly part.** With three little cores left, a stalled 4-thread step took
  about 10 s, against 2.6 to 8.7 s on phone D, so 12 failed scheduled tries took about a quarter of a
  530 s pause. Now that a return is caught within about a second, a longer interval would cost less in
  a held pause and little at a return; none was measured.
- **Falling back took 14 to 22 s.** Two stalled steps of about 10 s each, against 7 to 19 s on phone D.
- **What it does not show.** One unit of a chip the rest of this report did not use, the pause set by
  hand, two runs per arm and one with no pause, one model and one prompt. A natural pause on this
  phone, with its clocks lowered as well, was not tried, and the false-try rate rests on two runs.

r50b ran the rules after the first review on the same phone, work and pause, from a new APK (`apk 72ae0c21110e24dc`
in `chain50b.log`): W-E, W-T, W-E, then M-E, in that order. The chain also wrote the pause's on and
off into logcat right after each write, so the return is measured to the millisecond from that marker. The reservation ended about 490 s into
M-E's pause; its log to that point had been pulled, its placement samples and the four runs'
instrumentation results had not. In the three W runs every placement sample from 1 s after the
pause began to 2 s before it ended read the switch on with each busy engine thread on core 0, 1 or
2. One sample taken in the last second read the switch before it was cleared and the threads after,
which is why the analysis now leaves those out.

| Run | Fell back after the pause began | Try after the pause ended | Back to 4 | Decode |
|---|---|---|---|---|
| W-E | 19.3 s | 0.2 s, early, passed | 0.3 s | 5.81 tok/s |
| W-T | 13.7 s | 13.7 s, scheduled, passed | 13.8 s | 5.36 |
| W-E | 13.0 s | 0.3 s, early, passed | 0.4 s | 5.53 |
| M-E, to 490 s into the pause | 16.6 s | 12 tries, all failed (8.0 to 11.1 s each): 11 scheduled, 1 early | never | |

- **With the reviewed rules, back within half a second.** Measured from the marker logged just after
  the switch was written off, both early tries came 0.2 and 0.3 s after it, and passed.
- **The limit on false early tries held.** In the held pause one early try fired, 150 s in on a 735 ms
  step, and failed; none followed in the 340 s after it, while the 2-thread step before each
  scheduled try ranged from 773 to 1,609 ms and the scheduled tries kept coming about every 40 s. One
  run, and its placement samples were not pulled: that the pause held there rests on every try
  stalling.

### Ordinary use (r33, r37, r39, r46)

Does a person's chat reach the hard throttle? `SessionBenchmark` chats through the study's nine
conversations turn by turn, each reply the model's own (greedy, up to 512 tokens) and kept in the
history, with a reading pause before each next message, on one loaded engine for the whole session
and a new history for each conversation, as a new chat is in the app: 4 threads, an 8,192-token
window, the guard on. On phone D, screen on at a middle brightness, on the rack's supply, after r23's
gate: r33-A with 30 s between turns for 40 minutes, then r33-B with 5 s for 30 minutes, then r39-S
with no pause at all (an agent's loop, or a reader who sends at once), set for 45 minutes, which ran
out of the conversations' 34 user turns at 30.6. On the owner's POCO (r37), on USB power and
charging, screen on at a fixed brightness, 30 s for 30 minutes.

| | r33-A, 30 s pause | r33-B, 5 s pause | r39-S, no pause | r37, POCO, 30 s pause | r41-S, POCO, no pause |
|---|---|---|---|---|---|
| Turns | 29 in 40.7 min | 31 in 30.4 min | 34 in 30.6 min | 17 in 30.9 min | 34 in 42.8 min |
| Hottest reading | XO 64.2 °C | XO 73.7 °C | XO 76.4 °C | CPU 73.8 °C, skin 44.6 °C | CPU 85.4 °C, skin 44.5 °C |
| Hard throttle | none of 1,876 samples | none of 1,453 | none of 1,709, all eleven devices read | thermal status 0 throughout | thermal status 0 throughout |
| CPU ceilings below top | 98% of samples | 99% | 100% | not readable | not readable |
| Decode, median (range) | 12.8 (5.6 to 20.1) tok/s | 13.3 (5.6 to 20.8) | 12.7 (5.9 to 20.5) | 8.2 (5.4 to 11.2) | 8.1 (5.5 to 11.3) |
| First token, median (max) | 4.8 s (119.3) | 4.8 s (119.4) | 4.9 s (119.4) | 3.2 s (108.4) | 6.4 s (138.2) |
| Guard fallbacks | 0 | 0 | 0 | 0 | 0 |

`analyze_session.py`. The SM8850's hard throttle switches on at 78 °C on the XO thermistor. r41-S
ran on USB power with the screen set to stay on while plugged in, but the power service read
"Dozing" in the logger's samples from 8.6 to 32.4 minutes in and from 35.8 minutes to the end (why
was not logged). Whether that slowed the session was not measured; it is not a session with the
screen on throughout.

- **No pace reached the hard throttle on the rack phone** (and r46, below, kept it up for 97
  minutes on another unit). With no pause between turns the XO peaked
  at 76.4 °C, 1.6 °C under the trip, first passing 76.2 at 24.7 minutes; its per-minute mean ran 74.2
  to 75.9 °C over the last ten minutes and was 75.9 when the turns ran out, so a longer session might
  have reached the trip. With 5 s it held 70 to 73 °C over its last five minutes. The soft throttle held almost throughout all three: the CPU ceilings sat at about half their top (1.90 GHz, and 1.98 to 2.23 against
  3.63 and 4.61) from early in each session.
- **Decode slowed within each conversation as its context grew**, from 14 to 21 tok/s on a
  conversation's first turn to 5.6 on the seventh turn of the first, and recovered with each new
  conversation; the POCO showed the same shape, lower (11.2 to 5.4).
- **Some first tokens took about two minutes, on both phones, after the cache stopped matching.**
  Before the sixth turn of the first conversation the engine logged "5 remembered replies spliced
  into the prompt" and then "diverged at 2623 of 3136 cached, prompt 3173, re-reading 550": the
  prompt stopped matching the cache 550 tokens from its end, where the cache still held 513 tokens
  that it dropped. That took 119 s on the SM8850 and 108 s on the POCO, about 5 tok/s, where each
  conversation's first turn read 21 to 97 tok/s (15 to 265 tokens). The engine warns at load that
  this template drops past thinking; why the splice did not keep this prefix, and why reading at
  that depth was this slow, were not traced, and no cool run of the same turn separated heat.
- **The guard never fell back** in 145 turns on two phones, one with no Snapdragon pause at all.
- **The POCO never reported a thermal state above 0**, even with its CPU at 85.4 °C in r41-S, so the
  app's own thermal policy, which reads that status, would not have cut threads there.
- **What it does not show.** A rack phone in open air and a phone on a desk, not in a hand or a
  pocket; one model; the POCO charging, which adds its own heat; one session of each kind.

r39-S's XO was still near its peak when the turns ran out, so r46 ran the same session three times
back to back on phone A (unit 64a112da), from the build with the guard's final rules (62439b56):
r46-S1 after a cooling gate, r46-S2 and r46-S3 at once after it, each started the moment the one
before ended. Same settings as r39-S (no pause, 4 threads, an 8,192-token window, up to 512 tokens,
the guard on, screen on at a middle brightness, the rack's supply). `trig46.sh` logged the XO, the
hottest CPU zone and all eleven pause devices about once a second (it sleeps 1 s after each reading,
so 853 of the gaps are 2 s); it did not log the CPU frequency ceilings. The sample at the moment S2
ended and S3 began counts in both columns.

| | r46-S1, after a gate | r46-S2, straight after | r46-S3, straight after |
|---|---|---|---|
| Turns | 34 in 29.4 min | 34 in 33.5 min | 34 in 34.0 min |
| XO at the start, highest | 30.5 °C, 75.4 °C | 75.2 °C, 77.6 °C | 76.9 °C, 77.9 °C |
| Hard throttle | none of 1,512 samples | none of 1,710 | none of 1,737 |
| Decode, median (range) | 12.9 (6.1 to 20.5) tok/s | 11.4 (5.2 to 14.3) | 11.2 (5.4 to 14.1) |
| First token, median (max) | 4.9 s (99.9) | 5.3 s (119.8) | 5.3 s (119.8) |
| Guard fallbacks | 0 | 0 | 0 |

- **97 minutes of chat with no pause came within 0.1 °C of the trip, and no switch was seen on.**
  The highest sampled XO reading was 77.9 °C (77.924), against the trip at 78. 74 samples were at or
  above 77.5 °C, 9 in S2 and 65 in S3, and none of the eleven pause devices was on in the 4,958
  distinct samples from S1's start to S3's end. Samples about a second apart cannot rule out a
  crossing shorter than the gap. Whether a fourth session, a warmer room or a hand would cross it
  was not measured.
- **The later, hotter sessions decoded about an eighth slower.** The second and third sessions
  decoded 12% and 13% slower by median than the first (one fixed order, so this does not isolate
  the cause), and the conversations' first turns fell from 13.7 to 20.5 tok/s
  in the first session to 12.5 to 14.3 in the next two; the slow re-read turn took 119.8 s hot against 99.9 s cool.
- **The guard logged no fallback in 247 turns of chat on three phones** (phone D, phone A and the
  POCO). No natural Snapdragon pause was observed in the sampled sessions, so these runs did not
  show the guard acting through one; the POCO reports Android's thermal status, not these devices.
- **Phone A against phone D.** In these single gated runs, phone A's r46-S1 and phone D's r39-S had
  similar median decode rates, 12.9 and 12.7 tok/s, and sampled XO peaks of 75.4 and 76.4 °C. One run
  on each does not isolate differences between the units.

### A natural hard throttle, forced (r47, r48)

No chat session reached the trip, so r47 and r48 heated phone A until a pause device came on by itself
and ran the same short work inside the pause: `ThreadCountBenchmark`, 4 threads, its built-in 19-token
prompt, up to 256 tokens (temperature 0.2, seed 1), a 2,048-token window, the guard off (`stallGuard 0`) or
on. No switch was written by hand. The heat was eight shell busy loops and the screen at full brightness.
In r47 those alone took the XO, as the logger read it, from 54.4 to about 68 °C in 15 minutes and were
slowing (about 0.5 °C a minute), so, decided while watching, four loops running `dd if=/dev/zero
of=/dev/null` were added at 68.2 °C (`heat2.sh`) and four more at about 76 °C (`heat3.sh`); each helper
stopped its loops in the second it found a device on. r48 ran all sixteen from the start. The chain
stopped its heaters at the first poll with a device on (every 2 s in r47, 1 s in r48) and started the run
at once. Figures from `analyze_trip.py`, from the chain's timestamps and the progress records, with the
logger's first on and first off samples as approximate edges of the pause; the temperature and seed are
the benchmark's own (`ThreadCountBenchmark.kt`).

| | Cool, before heating (r47-B0, B1) | In the pause, guard off (r47-P0) | In the pause, guard on (r48-P1) |
|---|---|---|---|
| Heating to the trip | | 29.9 min, from an XO of 54.4 °C | 9.6 min, from 62.3 °C |
| Pause, by the logger's samples | | on at 77.988 °C, off at 69.963, about 52 s | on at 78.016 °C, off at 69.930, about 67 s |
| Run started | | 2 s after the first sample with a device on | the same second |
| 19-token prompt | 213 and 229 ms | 7,807 ms | 5,603 ms |
| Decode inside the pause | 21.6 and 21.3 tok/s, whole run | 0.24 tok/s (tokens 2 to 9 in 28.6 s) | 3.75 tok/s after the fallback (171 tokens in 45.6 s) |
| Guard | off, then on | off | fell back 21.1 s after the trip, on a 3,870 ms step |
| Whole run | | decode 4.9 tok/s, most of it after the pause | decode 4.3 tok/s |

- **The trip and the release are where the earlier runs put them.** Both times the same three devices,
  `thermal-pause-3C` and both `thermal-pause-C0` (CPUs 2 to 7), were first seen on at 77.988 and
  78.016 °C, and all were first seen off below 70 °C. In every one of r48's samples with them on, CPUs
  0 to 7 were online and the two clusters read 1,900 of 1,900 MHz and 883 of 2,227 MHz.
- **Without the guard, decode fell to about a hundredth.** 0.24 tok/s against 21.3 to 21.6 cool: tokens
  took 2.8 to 6.1 s each. About 2 s after the release it ran at about 16 tok/s again.
- **With the guard, decode ran at about a sixth of its cool speed.** After the fallback, r48 decoded
  3.75 tok/s in the pause, about 17% of the cool runs and about 15 times r47's guard-off rate; across all
  of its decode inside the pause, slow first steps included, 3.48. These are single runs in separate
  heating runs, so they show the difference observed, not a controlled measure of the guard's effect. The first decode steps took about 4 s each, and
  the guard fell back after two steps in a row past 1.5 s, the rule it uses before it has normal steps to
  compare ("against 0"). A 4.7 s gap at token 130, 35 s after the fallback, fits the guard's scheduled try at 4
  threads failing inside the pause; tries are not logged, so that is inferred. Against a pause set by
  hand (r34, r38, r39) the guard gave 3.2 to 3.4 tok/s on longer work.
- **Neither arm protects the prompt.** 19 tokens took 5.6 to 7.8 s inside the pause against about 0.22 s
  cool. The guard watches decode only; the app reads prompts with as many threads as performance cores.
- **A stall with no pause device on.** r47-P1 (guard on) started 67 s after the trip, when the pause had
  already released, at an XO of 71.2 °C. After token 178 progress was very slow: decode time rose 2,711 and 3,984 ms across the next two
  progress lines, which ended at tokens 179 and 181, while all eleven devices read 0 in every sample; the hottest CPU zone fell from
  104.6 to 92.3 °C over those seconds and came back. The guard fell back on a 3,919 ms step against a
  60 ms median, and the run ended at 11.2 tok/s. The cause was not identified: r47's logger read neither
  frequencies nor online CPUs, and r48's, which did, saw no such event.
- **What it does not show.** Heat from busy loops and memory copies, not from a chat; one 19-token prompt
  and 256 tokens; one run of each arm on one unit, in different heating runs; pauses that lasted about 52
  and 67 s once the heaters stopped, which says nothing of how long one the model's own load kept up
  would last. r48's
  guard-off arm (r48-P0) did not run: r48-P1 outlasted the pause, and the chain ran P0 only if a device
  was still on.

### Every thread count, reading and writing, and where six threads ran (r49)

r26 and r27 measured decode at 2, 3, 4 and 6 threads with the pause set by hand, on two other units,
and none of their runs kept how fast the prompt was read. r49, on phone A after r48 on 2026-11-10, ran
the app's engine at every count from 1 to 8, normally (N) and with the three devices the phone's own
trip had set in r47 and r48 (`thermal-pause-3C` and both `thermal-pause-C0`) written 1 for the whole run
(P). Before any run, a check started six busy loops and saw them run on CPUs 0 and 1 only with the
switches on, and on CPUs 0 to 5 with them off. Then three rounds of the sixteen cells, each round in
an order shuffled once before any run (`random.seed(49)`), with a gate before every run: the hottest CPU
and GPU zone and the XO at or under 45 °C, the GPU uncapped, every CPU policy at its ceiling and all
three switches 0 (passed in 5 to 135 s). `ThreadCountBenchmark` (build 62439b56), its built-in 19-token
prompt, up to 32 tokens, a 2,048-token window, the guard off, a watchdog at 120 s normally and 300 s
with the pause. The engine reads the prompt with as many threads as it writes the reply, as the app
does. A logger read the three switches and each thread's last CPU and CPU time every second
(`place-<label>.txt`); a paused run counts only if every sample with its threads busy had them on CPUs
0 and 1. All 24 did, and all 2,948 of their samples read the three switches on. These are samples a
second apart, not a continuous record; the traces below are one. Before the rounds, two 6-thread runs, one in
each state, recorded the kernel's `sched_switch` and `sched_wakeup` events for 2 s of decode
(`trace-r49-T-N6.txt`, `trace-r49-T-P6.txt`) with the process's thread list. The chain is `chain49.sh`,
the analysis `analyze_threads49.py`; the whole chain took 1 hour 53 minutes.

| Threads | Decode, normally | Decode, six cores out of use | Kept | Prefill, normally | Prefill, six cores out of use | Kept |
|---|---|---|---|---|---|---|
| 1 | 15.75 tok/s (15.70 to 15.82) | 7.28 (7.22 to 7.35) | 46% | 42.7 (42.6 to 42.7) | 17.3 (16.0 to 17.7) | 40% |
| 2 | 23.65 (23.58 to 23.65) | 10.70 (10.40 to 10.71) | 45% | 71.7 (71.7 to 72.5) | 31.2 (30.8 to 32.9) | 44% |
| 3 | 23.31 (23.29 to 23.33) | 0.80 (0.68 to 0.80) | 3.4% | 84.8 (77.9 to 86.0) | 8.7 (8.3 to 9.1) | 10% |
| 4, the app's default | 23.29 (23.26 to 23.31) | 0.351 (0.348 to 0.361) | 1.5% | 89.6 (89.6 to 91.4) | 4.09 (3.48 to 4.62) | 4.6% |
| 5 | 23.74 (23.61 to 23.77) | 0.206 (0.184 to 0.207) | 0.9% | 111.1 (108.6 to 111.8) | 3.33 (2.73 to 4.39) | 3.0% |
| 6 | 23.85 (23.05 to 24.07) | 0.124 (0.122 to 0.136) | 0.5% | 125.8 (125.0 to 129.3) | 2.11 (2.05 to 2.32) | 1.7% |
| 7 | 22.74 (21.48 to 23.49) | 0.087 (0.086 to 0.091), all three stopped at 300 s | 0.4% | 138.7 (125.0 to 139.7) | 1.66 (1.63 to 1.82) | 1.2% |
| 8 | 19.78 (19.44 to 19.83) | 0.069 (0.062 to 0.070), all three stopped at 300 s | 0.3% | 126.7 (104.4 to 138.7) | 1.04 (1.00 to 1.38) | 0.8% |

Medians of three runs, with the range over them. Prefill is the 19 prompt tokens over the reading time;
decode, the tokens after the first over the decode time to the last progress line, as in r27, so a run
the watchdog stopped counts up to its last one (the six stopped runs had written 18 to 26 tokens). For
the runs that finished, the engine's own summary line, which also times the run's end, read prefill
within 2% of these and decode 2% to 5% lower normally and from 27% lower to 9% higher with the pause,
where its one decimal is coarse.

- **Past two threads, six cores out of use cost nearly all of both jobs.** At 3 threads decode kept 3.4%
  and prefill 10%; at the app's default of 4, 1.5% and 4.6%; at 8, 0.3% and 0.8%, so the 19-token prompt
  took about 18 s against about 0.15 s. Every thread past two made both slower.
- **1 and 2 threads kept 40 to 46%.** With six cores out of use, 2 threads were the fastest count at both
  jobs: decode 10.70 tok/s and prefill 31.2, about 30 and 8 times what 4 threads managed. The paused
  decode rates at 2, 3, 4 and 6 threads (10.70, 0.80, 0.351, 0.124) have the shape of r27's on another
  unit with 64 tokens (10.57, 0.51, 0.24, 0.083), 1.0 to 1.6 times as fast.
- **Normally, writing barely changes from 2 to 7 threads; reading does.** Decode ran 22.74 to 23.85
  tok/s at 2 to 7 threads, 15.75 at 1 and 19.78 at 8. Prefill rose from 42.7 at 1 to 71.7 at 2, 89.6 at
  4 and 125.8 at 6; from 6 to 8 the ranges overlap. So outside a pause 2 threads gave up no decode
  against 4 and read this prompt 20% slower than 4 and 43% slower than 6 (265 ms against 151 ms at 6).
  This is a 19-token prompt; on the guard tests' 1,751-token prompt on phone D, 2 threads wrote 11%
  slower than 4 ([above](#a-fallback-in-the-apps-engine-r34-r38-r39)).
- **Recorded: six threads on the two cores left.** In the normal trace each of the six engine threads
  ran 99.6% to 99.8% of the 2 s; two stayed on one big core each (CPUs 6 and 7) and four ran 89% to 91%
  of the time on one of CPUs 1, 3, 4 and 5, in 616 stretches with a median of 2.1 ms. In the paused
  trace CPUs 2 to 7 logged no event at all, and all six threads ran on CPUs 0 and 1: two shared CPU 0
  at 47.8% and 47.9% of the window each, four shared CPU 1 at 22.4% to 22.5% each, and other tasks took
  4.3% and 10.1% of the two cores (most of it the lab's screen streaming, the graphics driver and USB).
  The trace holds 6,362 stretches of an engine thread on the two cores, a median of 0.103 ms each,
  against 616 of 2.1 ms normally. This records directly what r19 and r23 inferred from last-CPU
  samples.
- **No engine thread left its core to sleep.** Each `sched_switch` records the state of the thread it
  switches out. In the paused trace an engine thread was switched out 6,361 times, and every time it
  was still runnable (`R` 6,190 times, `R+` 171); none left blocked (`S` or `D`), and in the 2 s the
  kernel woke an engine thread 0 times, against 24 wake-ups of the process's other threads. Normally:
  610 switch-outs, all runnable, 0 wake-ups. So in both windows none of the six threads left its
  core to sleep or was woken. A thread that gave its core up with `sched_yield` would also leave runnable, so the trace
  alone cannot tell a spin from a yield; r28's disassembly of the benchmark's wait shows the Arm `yield`
  hint, which does not enter the kernel. The engine was writing about 23 tokens a second as the normal
  trace ran (its progress went from 77 to 118 tokens in 1.8 s), so that 2 s window spanned about 45
  tokens and the threads did not sleep between them either; the paused window lay between the engine's progress reports of 1 and 2
  generated tokens, a few seconds either side, so it says nothing about the gap between tokens. Which threads had finished their slice when they were switched out is not in
  the trace.
- **The lost speed is far more than the lost cores.** Together the six threads ran 1.86 cores' worth of
  the paused window against 5.98 normally, about a third, while decode fell to 0.5%. That fits r28's
  profile, where most of a paused run's CPU time went to the function holding `ggml_barrier`'s spin: a
  thread holding a core can be waiting for one that has none. The trace shows no thread leaving its core
  blocked; what each was doing while it held a core is not recorded, so this part is inference.
- **What it does not show.** One unit and the pause set by hand; a 19-token prompt, so normal prefill
  took 140 to 450 ms and moved by up to a quarter between runs at 8 threads; 32 tokens of reply; three
  runs per cell. The traces cover 2 s of decode at 6 threads in each state; a core's time before its
  first switch in the window is credited to that switch's previous task. The app's heat policy was not
  in the loop.

### What the model writes at each thread count

Every turn record keeps its greedy tokens, decoded on the same teacher-forced context, so cells can
be compared token for token (`agree_threads.py`). r19's two cells (phone B) and r22's four (the
fifth unit), 33 turns each:

- **The same thread count gave the same tokens on both phones**: 6 against 6 and 2 against 2, 33 of
  33 turns identical, within a unit and across the two.
- **6 threads against 2 changed the reply in 23 of 33 turns.** Every pairing of a 6 with a 2 agreed
  on the same 10 turns and first diverged at the same token in the others, from the 13th to the
  305th (median 109th). A different thread count adds the same numbers in a different order, and a
  greedy choice between two close tokens can flip; after that the reply is different text. Which is
  better was not graded.
- **So a thread change mid-reply can change what follows it.** The stall guard above switches
  between 4 and 2 threads when steps stall, so with it on, a reply can depend on when a pause came as
  well as on the prompt. 4 against 2, and switches mid-reply, were not compared token for token.

### Memory

The engine logs its resident set after loading and around each turn (`mem:` lines). Qwen3 4B Q4_0
in the app's engine on the SM8850: 3,534 MB resident (3,424 MB proportional) with a 2,048-token
window (r27); 4,399 MB after loading with an 8,192-token window and 4,462 to 4,467 MB after a reply
of up to 1,024 tokens (r34), none of it in swap. On the owner's POCO X8 Pro Max (MediaTek MT6991),
the same file: 3,020 MB resident after loading with 2,048 tokens and 3,760 MB with 8,192, with 110
and 241 MB of the process in swap (a check before r36, and r37). Why the SM8850's process is larger
at the same window was not traced; the two load different variants of the CPU backend.

### Energy, and what a rack phone cannot measure

The QDC phones run from the rack's supply with the battery bypassed: the gauge read 1 to 7 mA idle
or loaded and USB offline, and through `perfetto`'s `android.power` source the PowerStats rails the
platform lists read zero (CPU, GPU, NPU) or 2^64 - 1 (SoC). So no QDC run measured energy. The owner's POCO
has a working gauge; r36 is built to measure energy per token there, unplugged, at 4 and 2 threads,
and has not run (Runs).

### Where the work ran

From the load logs. The CPU instance is one graph. The GPU instance puts every layer, the output
layer and the KV cache in OpenCL buffers, with the token embedding lookup on the CPU (two graph
splits). The NPU instance puts every repeating layer and the KV cache (and on the hybrids the
recurrent state) on Hexagon, while the output projection, whose weights are the embedding table on
these tied models, runs on the CPU (three splits, four with two sessions). So "NPU" prefill and
decode include a CPU matrix multiply for the logits of each token decoded, and of the last prompt
token. Weight buffers of the NPU instance:

| Model | On Hexagon (`HTP*-REPACK`), MiB | On the CPU (`CPU_REPACK`), MiB | CPU share |
|---|---|---|---|
| Qwen3 0.6B | 237 | 122 | 34% |
| Gemma 3 1B | 376 | 306 | 45% |
| Llama 3.2 1B | 524 | 205 | 28% |
| LFM2.5 1.2B | 556 | 105 | 16% |
| Qwen3 1.7B | 758 | 243 | 24% |
| Qwen3 1.7B Q8_0 | 1,428 | 315 | 18% |
| LFM2.5 2.6B | 1,306 | 205 | 14% |
| Llama 3.2 3B | 1,517 (two sessions) | 308 | 17% |
| Qwen3 4B | 1,955 (two sessions) | 304 | 13% |

These are allocated buffers, not bytes moved or time spent per token, but decode reads each
weight once per token, so the CPU share is a rough guide to how much of an "NPU" decode step's
weight traffic is the CPU's. On the seven transformers the NPU's decode rate rises against the
CPU's as that share falls (rank correlation -0.96), from 0.73 to 0.75 of the CPU's rate at 34% to
45% to 1.0 on Qwen3 4B at 13%. The hybrids break it: LFM2.5 2.6B has the same 14% share as Qwen3
4B and decodes on the NPU at 0.75 of the CPU's rate (LFM2.5 1.2B at 16%: 0.68). Over all nine the
rank correlation is only -0.30. A correlation, not a measured cause.

### ExecuTorch on the same NPU (e1)

ExecuTorch's Qualcomm runner (`qnn_llama_runner`, ExecuTorch 1.5.1, QNN 2.39 with the V81
skeleton) ran the SM8850 Qwen3 0.6B export (execupack's QNN workflow, 2,048-token window, its own
quantisation rather than Q4_0) on the 27 of the 33 turns whose whole history fits that window with
room for the reply (`make_et_prompts.py`), twice (54 calls, none
failed; `run_et.sh`, `analyze_et.py`, `analysis-e1-vs-r4.md`). Its timings come from the runner's
own `PyTorchObserver` line: prefill from inference start to the end of prompt evaluation, which
includes picking the first token as `pd_bench`'s does, and decode as generated tokens over the
time after it (63 tokens, as in `pd_bench`).

| Same 27 turns | ExecuTorch QNN, rep 1 / rep 2 | llama.cpp Hexagon, npu:npu |
|---|---|---|
| Decode | 30.3 / 30.4 tok/s | 50.7 tok/s |
| First-turn prefill (9 turns, same tokens) | 644 / 656 tok/s | 2,400 tok/s |
| Median time to first token | 333 / 330 ms | 70 ms |
| Model load, every call | 692 ms median | once per run |

The runner has no way to keep a conversation's state between calls, so each call reads the whole
history: its time to first token is for the whole prompt, llama.cpp's for the new tokens only.
Decode uses the same timed token count and first-turn prefill the same prompt tokens, but the
quantisation, the exported graph and the runtime differ, so this compares the two configurations
as tested, not the runtimes in general. The llama.cpp column is r4's npu:npu cell, from the first
session; the r5 control is below.

**Same-session control.** r5 ran Qwen3 0.6B's matrix in the same reservation, starting 29 minutes
after e1 ended. On the same 27 turns its npu:npu cell decoded at 50.3 tok/s, read the nine first turns at 2,242
tok/s and had a median time to first token of 70 ms (`analysis-e1-vs-r5.md`), against r4's 50.7,
2,400 and 70. The gap to ExecuTorch persists within one session.

### What this means for the app on a Snapdragon 8 Elite Gen 5

At 64-token replies the fastest arrangement measured reads the prompt on the Hexagon NPU and
writes the reply on the CPU, and llama.cpp can do both in one process with a state copy between
them. On Qwen3 4B, doing both halves on the NPU was as fast (0.2% behind in one pass, 0.3% ahead
in the other) and needs no copy; on Llama 3.2 3B it was 7.4% slower.

At the dataset's own reply lengths the reply is most of the wait, so the choice is the decoder,
and the answer depended on the model and on the phone's throttled state. With this phone throttled
(both NPU-prefill cells' gates timed out),
NPU prefill with CPU decode took 10% to 20% longer than NPU-only on Qwen3 1.7B, its Q8_0 and
Llama 3.2 3B without counting the CPU's stalls, and NPU-only led on Qwen3 4B over its 33 turns only through
the stalls (on its 20-turn cohort it still led by 18% without them); on the two smallest, Qwen3
0.6B and Llama 3.2 1B, they tied. On the hybrid LFM2.5 1.2B the throttled CPU stayed ahead of the
NPU and NPU-only took 19% longer. On LFM2.5 2.6B NPU prefill with CPU decode was 2.1% faster in
r9; in r11 NPU-only was 12.7% faster with the CPU's stalls and 6.9% slower without them, and
lost 26 of 33 turns. On Gemma 3 1B the CPU decoded faster than the NPU even capped, on one
schedule. Where NPU prefill with CPU decode started after a passed gate (Qwen3 0.6B in r8, Gemma
3 1B in r11) it was fastest, at 75% and 76% of CPU-only time, as at 64 tokens. So a fixed choice is
wrong somewhere. One hypothesis these runs raise, and do not test: choose the decoder per model
family and move it to the NPU when the CPU's frequency ceiling drops; against it, the capped CPU
still led on Gemma 3 1B and LFM2.5 1.2B, and matched gate results are not matched thermal
histories.

All nine pairings of every model at reply length (r12 to r16), every table cell but one after a
timed-out gate, point the same way. Among the cells after a timed-out gate, NPU prefill was in the
fastest on every model (73% to 86% of CPU-only time), with NPU decode on six transformers, CPU decode on Gemma 3 1B and LFM2.5 1.2B,
and the two within 0.4% on LFM2.5 2.6B. GPU decode never won: it was the slowest decoder on eight
models and within 3.5% of the CPU on LFM2.5 2.6B, and on Qwen3 4B its step grew about fourfold past
2,048 tokens of context. Under the phone's hard throttle the CPU decoder stalled and the NPU decoder
slowed to about half or two thirds of its speed without stalling, so which decoder is faster at a given moment
depends on that state as well as on the model. This is a measurement of what each arrangement
would buy on this chip, not a decision. Before an app could ship any of it, these are still
unmeasured:

- **An unprivileged app.** Every run here was a root shell. Loading the Hexagon backend and
  reaching the DSP from an app's own UID, packaging the HTP libraries, recovering when the DSP
  session dies, and falling back on phones without it are all untested.
- **Cold start.** First-token times above are warm. Loading the NPU instance took 563 to 2,221 ms
  in the first session and 1,422 to 2,158 ms in the second, and the warm-up batch after it is not
  counted.
- **Long prompts.** First turns were 18 to 268 tokens, the widest new-token span 1,215 and the
  longest context at decode 3,303; nothing reached 4,096 although the window was 8,192.
- **Live conversations.** The replay teacher-forces the dataset's replies, so the prefill side
  never needed the decode side's own tokens. A real split must feed the generated reply back to the
  NPU context, or copy the state back, before the next turn.
- **Energy and battery.** No run measured power: the rack phones cannot ([energy](#energy-and-what-a-rack-phone-cannot-measure)).
  Memory is measured for one instance in the app's engine ([memory](#memory)), not for an NPU split,
  which keeps two instances resident.

## What runs

One llama.cpp build (`b2e5e9b`, release b10549, the commit the app vendored until 2026-11-04) with
all three of its Snapdragon backends: CPU (`ggml-cpu`), Adreno GPU (`ggml-opencl`) and Hexagon NPU (`ggml-hexagon`,
`libggml-htp-v81.so`), built with Qualcomm's toolchain image
`ghcr.io/snapdragon-toolchain/arm64-android:v0.7` as llama.cpp's Snapdragon guide does.

The benchmark runs used that build: r1 to r26, r28, and r29 and r31 with a patched `libggml-cpu`
built beside its stock one. The runs on the app's own engine (`ThreadCountBenchmark` and
`SessionBenchmark`: r27, r32 to r34 and r37 to r49), all built from 2026-11-09 on, used the
llama.cpp the app has vendored since 3611edfb: v0.8.0 (`8642541b`, 2026-10-28).

`tools/snapdragon/pd_bench.cpp` loads a model three times, once per processor (`n_gpu_layers`
-1 on the GPU and NPU instances, 0 and no op offload on the CPU one), each with its own context.
A turn prefills its new tokens on the prefill processor, takes the first token from that
context, copies the sequence state (`llama_state_seq_get_data` / `set_data`, which carries the
KV cache and, on hybrids, the recurrent state) into the decode processor's context, and decodes
there. On a diagonal cell (both halves on one processor) nothing is copied; the decoded tokens
are dropped afterwards with `llama_memory_seq_rm` so the next turn continues from the dataset's
history. A hybrid (LFM2.5) cannot drop tokens, so on its diagonal cells the state is saved before
the prefill and restored after the decode, with the turn's prompt read again, all untimed.

| | decode on CPU | decode on GPU | decode on NPU |
|---|---|---|---|
| **prefill on CPU** | ran, 9 of 9 models | ran (state copy), 9 of 9 | ran (state copy), 9 of 9 |
| **prefill on GPU** | ran (state copy), 9 of 9 | ran, 9 of 9 | ran (state copy), 9 of 9 |
| **prefill on NPU** | ran (state copy), 9 of 9 | ran (state copy), 9 of 9 | ran, 9 of 9; ExecuTorch QNN for Qwen3 0.6B |

The off-diagonal cells are a real disaggregation: the conversation state moves from the prefill
context to the decode context every turn, one way (the prefill side keeps the history; the decode
side gets a fresh copy each turn). During the 64-token runs all three model instances are resident at once,
so this measures speed, not the memory of a deployment that holds one or two copies.

**Models**, chosen for quantisations both accelerator backends implement (Q4_0 everywhere, one
Q8_0 to see the quantisation's effect): Qwen3 0.6B, 1.7B (Q4_0 and Q8_0, the latter the app's)
and 4B, Gemma 3 1B IT, Llama 3.2 1B and 3B Instruct, LFM2.5 1.2B Instruct and 2.6B (the app's QAD
Q4_0 files, hybrids). The 3B and 4B use two Hexagon sessions (`GGML_HEXAGON_NDEV=2`), because one
session holds 2 GB.

**Conversations** (`tools/snapdragon/pull_conversations.py`): the first conversation in each
length bucket (short, medium, long, by characters) from each of WildChat-1M, UltraChat 200k
(`train_sft`) and Daring-Anteater, nine in all, with at least two user turns, 33 turns in total.
WildChat is real users talking to a deployed assistant; UltraChat and most of Daring-Anteater are
generated dialogues. ShareGPT52K was the first choice for the third set; the Hugging Face datasets
server cannot read it. MT-Bench ships user turns only, and the replay needs the dataset's
assistant replies, so it is not used.

**Replay.** History is teacher forced: every earlier assistant turn in a prompt is the dataset's
own reply, so all nine cells read exactly the same tokens. Each turn decodes 64 tokens, past an
end of turn if the model writes one, so every cell decodes the same amount; up to six turns per
conversation within an 8,192-token window; 6 threads; greedy. Every context runs one 512-token
batch and eight decode steps before anything is timed, because the OpenCL backend compiles
kernels on first use (in the smoke run that put about 900 ms into the GPU's first turn). The order
of the nine cells is shuffled per model from a fixed seed, and r5 repeats five models with
another seed.

## What does not run, and why

- **ExecuTorch pairings.** The app's other runtime has an XNNPACK (CPU), Vulkan (GPU) and QNN
  (NPU) delegate, but no way to move a conversation's state from one delegate's program to
  another's: each `.pte` keeps its KV cache inside its own graph in its own layout. The MediaTek
  handoff in [npu-pd-disaggregation.md](npu-pd-disaggregation.md) was written for one specific
  pair of exports. ExecuTorch can only fill the diagonal, and only its NPU cell ran here, for
  Qwen3 0.6B, the one model with an SM8850 QNN export.
- **ExecuTorch's CPU and GPU diagonals.** No XNNPACK or Vulkan export was pushed to this phone;
  they were not run.
- **Vulkan in llama.cpp.** Not built: the Snapdragon guide builds OpenCL for Adreno, and the app
  ships OpenCL. Vulkan is another backend for the same Adreno GPU, not one of the three asked for.
- **Mixture-of-experts and K-quants.** Not in the model set: the Hexagon backend's matrix
  multiply covers Q4_0, Q8_0 and MXFP4, so a Q4_K_M model would put most of its work back on the
  CPU and the "NPU" label would be wrong.

## Runs

- **r1, pilot, stopped.** The first version of the tool saved each diagonal cell's state between
  the prefill and the decode (untimed) to restore it afterwards. Codex's second review flagged that
  this disturbs the measured work, and the pilot showed it: the GPU decoded at 28 tok/s in its
  diagonal cell against 41 to 44 tok/s after another processor's prefill. r1 finished Llama 3.2 1B
  and was stopped in Gemma; its rows are kept (`results-r1.jsonl`) and not used for the tables.
- **r2, paused.** The fixed tool (`pd_bench3`): transformers drop decoded tokens with `seq_rm`,
  hybrids snapshot before the prefill; warm-up, state sizes and restores are checked; each cell
  records its temperatures and each turn its elapsed time. Llama 3.2 1B completed, with the hottest
  CPU, GPU and NPU zones at 73 to 98, 70 to 86 and 72 to 87 °C at the cell starts. Read by hand
  from the phone after it: Android's thermal status was 3 (severe) and the GPU was capped at power
  level 8 of 17, 578 MHz against its 1.2 GHz top. Stopped during Gemma. r2's rows are kept and
  reported separately as the throttled condition.
- **r3, stopped.** The same tool with a cooling gate at 50 °C. The first cell waited 503 s, which
  put the run near twelve hours, and was stopped there.
- **r4.** The gate at 55 °C, where the cap had already lifted, with the wait limited to about
  300 s. Six models; Llama 3.2 3B and Qwen3 1.7B Q8_0 were queued after Qwen3 4B, and the
  reservation ended during the 4B (eight of its nine cells ran, 22 matched turns). r4's 4B is
  superseded by r6's complete run.
- **r6.** `pd_bench5`, in the second reservation: each turn is read at its start and end, with a
  timestamp and the CPU clusters' frequency ceilings; the gate requires valid readings, the GPU
  uncapped and no CPU ceiling lowered, and records whether it passed or timed out. Llama 3.2 3B,
  Qwen3 1.7B Q8_0 and Qwen3 4B, all 27 cells.
- **r7.** The same, for LFM2.5 2.6B, added on request.
- **e1.** ExecuTorch's QNN runner on Qwen3 0.6B, two repetitions, with the same cooling gate
  before each repetition.
- **r5.** The second pass: Llama 3.2 1B, Qwen3 0.6B, Qwen3 1.7B, LFM2.5 1.2B and Qwen3 4B with
  another seed, all 45 cells, 1,485 turns, none failed (`analysis-r5.md`).
- **r8.** `pd_bench --gen-ref` (each turn decodes its dataset reply's length and logs each step's
  time) in a third reservation of 93 minutes, unit 727ba80: Qwen3 0.6B in four cells, then Qwen3
  4B in three; the reservation ended 20 turns into the 4B's CPU-only cell. 218 turns, none failed.
- **r9 and r9r.** The same tool in a fourth reservation, unit a1716cc4, run alongside r8. The
  queue was changed twice while it ran, each time by starting a new script after the running cell
  and stopping the old script's shell (never the benchmark): after Codex's review the GPU-prefill
  cell and a cross-unit Qwen3 4B cell were dropped and the gate's limit raised to 300 s; when the
  300 s gate still timed out it went back to 120 s and Qwen3 1.7B was queued again in another
  order under its own label (r9r). In order: Qwen3 1.7B, Qwen3 0.6B, Qwen3 1.7B again (r9r),
  Llama 3.2 3B, LFM2.5 1.2B, Llama 3.2 1B and LFM2.5 2.6B, which the reservation's end (about
  11:12) cut 29 turns into its NPU-only cell, before its CPU-only cell. Gemma 3 1B and Qwen3 1.7B
  Q8_0 were queued after it and did not run. 623 turns in r9 and 99 in r9r, none failed.
- **r10.** The same tool in a fifth reservation that evening, unit c581fd31: LFM2.5 2.6B's three
  cells, a repeat of its first under r10x, then Gemma 3 1B and Qwen3 1.7B Q8_0 were queued. The
  phone stopped answering between 21:20 and 21:22, about 27 minutes after the tunnel opened, with
  NPU-only and CPU-only complete (66 turns, none failed) and no turn of NPU prefill with CPU decode
  copied; whether it had started is not known, and nothing after it ran.
- **r11.** The same tool in a sixth reservation, on the same unit, c581fd31, after it was wiped:
  Gemma 3 1B, Qwen3 1.7B Q8_0 and LFM2.5 2.6B, three cells each, results copied every minute.
  All 297 turns ran, none failed, and the copy matched the phone's file by checksum.
- **r12.** All nine cells per model at reply length, in a seventh reservation on 2026-11-02, unit
  64a112da, in the order LFM2.5 1.2B, Gemma 3 1B, Llama 3.2 1B, Qwen3 0.6B, Qwen3 1.7B. LFM2.5 1.2B, Gemma 3 1B, Llama 3.2 1B and Qwen3 0.6B completed;
  the reservation ended after about 7 h 10 min during Qwen3 1.7B, with seven of its cells complete,
  19 turns of NPU prefill with GPU decode, and CPU prefill with NPU decode not started. 1,438 turns,
  none failed, copied every minute. A short check of GPU decode at reply length before the run is
  kept as `smoke12.*` and not used. r12's LFM2.5 1.2B is superseded by r15 and its Qwen3 1.7B by
  r13; both are kept.
- **r13.** The same in an eighth reservation on 2026-11-02 and 03, unit c581fd31: Qwen3 1.7B, LFM2.5
  2.6B and Qwen3 1.7B Q8_0 completed; the reservation ended after about 9 h during Qwen3 4B, with
  four cells complete and NPU-only at 28 turns. The thermal logger started during Qwen3 4B. 1,051
  turns, none failed. r13's Qwen3 4B is superseded by r14 and kept.
- **r14.** The same in a ninth reservation on 2026-11-03, unit 783ee7e5, with the logger running
  from the start: Qwen3 4B and Llama 3.2 3B, all nine cells each. 594 turns, none failed, and every
  file matched the phone's by checksum.
- **r15 and r15warm.** In the same reservation and on the same unit after r14, LFM2.5 1.2B again,
  because r12's fastest cell for it had run first after a passed gate. One change of procedure,
  agreed with the maintainer beforehand: a throwaway CPU-only pass (r15warm, its own file) ran
  first, so that the first counted cell would start with its gate timing out and the caps on, as
  the rest did (it did: 121 s, 61.4 °C, GPU at power level 8, CPU ceilings lowered). The order is
  still one order. Then r12's. 297
  turns and the warm-up's 33, none failed, and every file matched the phone's by checksum.
- **r36, r37 and r41.** On the owner's POCO X8 Pro Max (MediaTek MT6991, HyperOS OS3.0.304.0, Android
  16), not a QDC phone, on 2026-11-09 and 10, over USB and then Wi-Fi debugging; the engine's test APK
  as on phone D (416d8c86b7f9aadf) and the study's Qwen3 4B (pushed from the Mac, 92f4a83cdfe691b2).
  The phone's shell cannot read its thermal zones, cooling devices or battery files; the thermal
  service (`dumpsys thermalservice`) and the battery service (`dumpsys battery get -f current_now`,
  about a second a read) can, so `logger.sh` samples those. r36 (`chain36.sh`, energy per token
  unplugged, then a session) waited three times for the phone to be unplugged and never ran: the
  first start took one stray read (the cable out for about 4 s and back) for the unplug and was
  stopped by hand in its first idle, before any run; the chain now wants ten unplugged reads in a
  row, and its second and third starts timed out after 30 minutes on USB power. Each start held the
  screen on at the brightness it found and put the timeout and automatic brightness back when it
  ended. r37 (`chain37.sh`) ran the session on USB power instead. The phone then sat idle, still
  plugged in, until r41 (`chain41.sh`) at 06:46 on 2026-11-10: the 4 against 2 thread runs and a
  session with no pause between turns, on USB power, a watcher stopping the chain if the phone was
  unplugged (it was not). The cooling gate (the thermal service's CPU at 42 °C) ran out at 300 s
  before the fourth thread run and the session, which started at 43 to 45 °C. r42 (`chain42.sh`) then
  checked the guard as fixed after its fourth review (`engine-test6`, 62a7ff88b697a087) the same way:
  one 4-thread run at r41's length (decode 6.7 tok/s by the engine's summary) and a 3-minute session
  with no pause (3 turns), with no fallback logged; r43 (`chain43.sh`) the same for the guard after
  its fifth review (`engine-test7`, 84e410f825442207): decode 7.1 tok/s, 3 turns, no fallback logged. r44
  (the sixth review's build) was stopped in its gate when the seventh review's build was ready, and
  r45 (`chain45.sh`, `engine-test9`, c5bda9fad2db4705, the rules as committed) ran the same check:
  decode 6.6 tok/s, 3 turns, no fallback logged. A first check of the benchmark
  before r36 was killed by the system's backup agent, which backed up the newly installed test
  package and killed its process; the second check ran (12.1 tok/s, 8 tokens).
- **r50.** A sixteenth reservation, sa964839, on 2026-11-10, on unit 57fa7c29 (SM8550), reached
  through the tunnel on 127.0.0.1:5097. The engine test APK was built from this commit's engine
  (`apk 4f9fea5ebc9d0a03` in `chain50.log`) and Qwen3 4B fetched on the phone (92f4a83cdfe691b2).
  `probe50.sh` checked the pause with busy loops and ran 4 threads with and without it; its 2-thread
  run never started, because the probe's watchdog only force-stopped the test and the waiting
  instrumentation did not return, so it was stopped by hand. `hold50.sh` checked that the switch holds.
  `chain50.sh` ran the nine runs with `guard50.sh` beside it; logcat holds only the app's own tags.
- **r50b.** After r50 on the same reservation: the APK with the first review's rules (`72ae0c21110e24dc`), `chain50b.sh`
  with `guard50b.sh`. The reservation ended during its fourth run, M-E ("Name does not resolve" in
  the tunnel's log); `chain50b.log` and `logcat-r50b.txt` are as last pulled, 490 s into that run's
  pause, and the place files of runs 1 to 3 are complete. No `inst-r50b-*` file was pulled.
- **r49.** On sa964425 after r48, the same unit and build. `chain49.sh` ran the pause check, the two
  traced runs and the three rounds; `guard49.sh` stopped the test if the chain died (it logged "chain
  gone" after R49_DONE). `logcat-r49.txt` holds only the app's own tags. `inst-r49-*` are the
  instrumentation results, `place-r49-*` the logger's samples, `tasks-r49-T-*` the thread lists and
  `trace-r49-T-*` the kernel's trace buffer for each traced run. All six paused 7 and 8 thread runs were
  stopped by the 300 s watchdog.
- **r47 and r48.** On sa964425 after r46, the same unit and build. `chain47.sh` ran the two cool runs,
  the heating and the two runs after the trip, `trig47.sh` its logger (`trig47.txt`); `heat2.sh` and
  `heat3.sh` were started by hand during r47's heating. `chain48.sh` heated with all sixteen loops from the
  start, and `trig48.sh` added the CPUs online and each cluster's frequency to each sample (`trig48.txt`;
  the isolated-CPU field it also reads was empty, so those rows have one column fewer than the header).
  `guard47.sh` and `guard48.sh` killed any loop left behind once the chain ended. r48's chain logged
  `R47_DONE`, a label carried over from r47. `logcat-r47.txt` and `logcat-r48.txt` hold only the app's
  own tags; logcat replays its buffer when it starts, so `logcat-r48.txt` also holds r47's lines, told
  apart by process id. `inst-r47-*` and `inst-r48-P1.txt` are the instrumentation results.
- **r46.** A fifteenth reservation, sa964425, on 2026-11-10, on unit 64a112da (phone A, r12's unit),
  reached through the tunnel on 127.0.0.1:5097. The engine test APK was built from 62439b56
  (`apk c5bda9fad2db4705` in `chain46.log`) and the model fetched on the phone (92f4a83cdfe691b2).
  `chain46.sh` ran the gate and the three sessions, `trig46.sh` the logger (`trig46.txt`), and
  `guard46.sh` stopped the test if the chain died (it logged "chain gone, test stopped" after
  R46_DONE, when the test had already finished). Results were pulled every five minutes;
  `logcat-r46.txt` holds only the app's own tags. `inst-r46-S*.txt` are the instrumentation results
  (OK, 1 test each), `place-r46-S*.txt` where each thread ran.
- **r28, r29, r32, r31, r33, r34, r38 and r39.** r28 and r29 in the thirteenth reservation's extension, on
  5d3e8f0a after r27; then a fourteenth reservation, sa963265, on 2026-11-09 and 10, on phone D
  (4816891), whose model was fetched on the phone (92f4a83cdfe691b2). r28 (`chain28.sh`) profiled
  one paused and one unpaused 6-thread run with `simpleperf`. r29 (`chain29.sh`) ran 7 of its 9
  probes before the reservation moved; its patched library was built on the build box from
  `barrier-yield.patch` (sha256 prefix 5e5eebcd) beside the stock one (c91baa82). r30 (`chain30.sh`,
  `follow30.sh`), the guard's first device test, was queued there and never started before the
  reservation moved; r32 is its design on phone D. On phone D, r32
  (`chain32.sh`) timed its pause from each run's start; the pause would have come during prompt
  reading (about 95 s of the 2,257-token prompt), so it was stopped by hand after its first run
  (`r32-1-N4-0`, kept as one more unpaused 4-thread run) and redesigned as r34, whose pause starts 10
  s after decode does, found from the run's own progress lines in logcat. r34's first start was
  stopped in its first run to fix the guard after review, and block 1 ran on that build
  (`engine-test4`, 9ccfad1b16dd6996). r33 (`chain33.sh`) installed the build after the second review
  (`engine-test5`, 416d8c86b7f9aadf), which r31 did not use and block 2 and r33-B did. Every chain
  that writes a pause began by checking that the two switches hold six busy loops to CPU 0 and 1.
  `follow35.sh` ran them in the order r34 block 1, r33-A, r31, r34 block 2, r33-B; the logger
  (`trig31.sh`, `trig-r31.txt`) ran throughout, and results were pulled every three minutes.
  After it, r38 (`chain38.sh`, `follow38.sh`) and r39 (`chain39.sh`, `follow39.sh`), each from the
  same pause check, with `trig39.sh` logging all eleven pause devices every second (`pause39.txt`)
  from r39's start; r39-S's 45-minute budget outlasted the conversations. r40 (`chain40.sh`,
  `follow40.sh`) was to run two such sessions back to back, the second without a gate, to reach a
  natural pause with the guard on; the reservation ended about 22 minutes into the first (23 turns,
  no pause device on, no fallback logged, the XO at most 75.0 °C), and it is not counted above. A second
  `trig39.sh` started with it, so `pause39.txt` holds two loggers' samples from then. Codex reviewed the guard
  three times before it shipped to the phone (Reviews).
- **r22 and r27.** A thirteenth reservation, sa962766, on 2026-11-09, on a fifth unit, 5d3e8f0a,
  five hours. The phone had been wiped: the benchmark package was pushed again (sha256 prefix
  204879540c459b5e) and Qwen3 4B fetched on the phone (92f4a83cdfe691b2). This unit numbers its
  cooling devices differently and has two `thermal-pause-3C` devices, so the scripts that write a
  pause find the switches by type, and each r27 chain began by checking that the two it writes hold
  six busy loops to CPU 0 and 1 (they did: 36 of 36 reads at the first start, 60 of 60 at the restart,
  60 and 48 before the two extra chains). r22 ran `chain22.sh` with `trig22.sh` (its own
  log, `trig-r22.txt`) and `place22.sh`. After r22's fourth cell `follow22.sh` stopped it between
  cells, installed the engine's test APK (`engine-debug-androidTest.apk`, sha256 prefix
  df83da502a55f485, built from this commit's `ThreadCountBenchmark`) and copied the model to the test's
  path. r27's first start stopped at its first gate, whose 900 s limit, carried over from r23's short
  probes, was shorter than a cool-down after a full cell (r22's gates took 1,040 to 1,130 s); the
  follower then started r22's second block, which was stopped by hand about 85 s in, during its third
  turn after two completed ones (`r22-5-t2`, not counted; its line in `chain22.log` says "72 s into its
  first turn", which was wrong), and r27 was restarted with an 1,800 s limit. Two short chains used the
  last hour (`chain27c.sh`, `chain27d.sh`). Of r27's 28 runs, 26 completed and the two with 6 threads
  paused were stopped by the watchdog, as intended. Codex checked `chain27.sh`,
  `guard27.sh` and the test change before they ran (the guard follows the chain's PID, the watchdog
  stops the test package without blocking, every progress event is logged, a named model must exist).
  Results were pulled every three minutes and checked by sha256.
- **r21, r24, r23, r25 and r26.** A twelfth reservation, sa962335, on 2026-11-09, on unit c581fd31,
  about six hours. The phone had been wiped, so the benchmark package (`pkg-ref.tgz`, binary sha256
  prefix 204879540c459b5e) was pushed again and Qwen3 4B fetched on the phone (92f4a83cdfe691b2).
  `pd_bench.cpp` gained `--paused-threads` in 9614430d; the second binary, `pd_bench_sw` (sha256
  prefix 3307ddc1e66ccddd), was built from it on the build box against the same `pkg-ref` libraries
  and used for every r23 and r26 arm, the reference binary for r21, r24 and r25. `chain21.sh` ran r21;
  `feas23.sh` and a hand check (`pausecheck23.txt`) tested the pause set by hand; `run21.sh` then ran
  `chain24.sh`, `chain23.sh` and `chain25.sh`, each with `guard.sh` beside it to put back anything a
  dying script left set. r25 was cut from twelve probes to eight before it started, and the
  natural-heat thread test planned after it (`chain22.sh`: 2 against 6 threads from a shared 45 °C gate,
  to close r19's limit) could not finish a cell in the time left, so the slot ran r26 instead
  (`chain22.wrap`, `chain26.sh`). Three reviews changed the scripts before they ran: a timed-out gate
  stops a chain instead of running anyway, writes are read back, a pause is cleared only while the
  XO is under 70 °C (the guard waits for it), a pause held throughout was joined by one toggled on
  and off, and the watchdog escalates. Loggers: `trig21.sh` every second throughout, `place23.sh` every 2 s
  from r23 on. Every gate passed and no run failed; the three P6 probes and r26's P4 and P3, which
  reached their watchdogs, are reported as such. Results were pulled every three minutes and checked by sha256.
- **r20 and r19.** An eleventh reservation, sa959107, on 2026-11-08 and 09, on unit c581fd31, with
  the benchmark package fetched again from the build box (binary sha256 prefix 204879540c459b5e) and
  the models from Hugging Face on the phone (Qwen3 4B 92f4a83cdfe691b2). r20, the ladder: 29 probes,
  174 turns, none failed, run by `chain20.sh` and, from the fifth probe, `chain20b.sh` after a review
  of the first (it counts a probe as hot only if the logger saw a pause during it, stops on a failure
  and requires the cool-down to clear every cap); it ended at `LADDER_HEAT_LIMIT`. `trig20b.sh`
  stopped after its first two samples and was restarted, so its fields are missing from probes 5 and
  6 (`trig20.sh` covers them for the pause, `kgsl` and power level). r19: started by `follow20.sh`
  at the ladder's limit; the warm-up and two cells complete, the third cut at its first turn when
  the reservation ended (`r19-3-t2`, not counted). `follow19.sh`, which would have idled the phone
  back to uncapped and repeated the probe four times, never started. Results were pulled every three
  minutes and checked by sha256.
- **r18, r18warm and r18b.** A tenth reservation on 2026-11-04 (local time; 3 November on the
  phone's clock), on a fourth unit, 4816891, rerun Qwen3 4B's GPU prefill with CPU decode warm,
  because r14's had run after a passed gate. The benchmark package was fetched again from the build
  box (binary sha256 prefix 204879540c459b5e, as before). A one-conversation CPU-only warm-up
  (r18warm, 148.2 s) was too short: r18's gate passed after 40 s at 54.4 °C with nothing capped, so
  r18 was stopped at 26 turns, with the phone hot, and started again at once as r18b, whose gate timed
  out after 121 s at 69.1 °C with the GPU capped and the CPU ceilings lowered. 33 turns, none failed,
  and every file matched the phone's by checksum. The 2 s logger (`trig-r18.txt`) caught five hard
  throttles at the same switch points as on unit 783ee7e5 (first readings after each switch on 78.00
  to 78.20 °C, after each switch off 69.69 to 69.93 °C), and every CPU decode step over 500 ms fell in
  a turn with a paused sample (26 of 26, `analysis-r18b-heat.md`). A thread-count test (6 threads
  against 2, Llama 3.2 3B) that ran after r16 lost its raw files when the session's scratch space was
  cleared and the reservation ended; it is not reported.
- **r16 and r16warm.** In the same reservation, on the same unit, after r15: a throwaway CPU-only
  pass of Qwen3 1.7B (r16warm), then all nine of its cells in r12's order, because r13's first two
  had run after passed gates; then Qwen3 4B's GPU prefill with CPU decode alone, warm, for the
  same reason (the queue was changed to that one cell while Qwen3 1.7B ran, by starting
  `chain16b.sh` to wait on the running benchmark and stopping `chain16.sh`'s shell). Every gate
  timed out. 330 turns and the warm-up's 33, none failed, and every file matched the phone's by
  checksum. Three loggers ran beside it (`thermlog-r16`, `trig-r16`, `affinity-r16`, each cut at
  r16's end; they ran on into the next test), and the phone's thermal configuration was read
  (`phone-evidence-2026-11-03.md`).

## What was not measured

- **ExecuTorch's CPU and GPU cells**, and any ExecuTorch model but Qwen3 0.6B.
- **Prompts past 4,096 tokens of context**, cold starts, and live (not teacher-forced)
  conversations.
- **Memory of a split.** The benchmark keeps two or three model instances resident, so no split's
  footprint was measured; one instance in the app's engine was ([memory](#memory)).
- **Energy.** Nothing measured power or battery drain: the rack phones' gauge is bypassed, and r36,
  built for the owner's POCO, did not run because the phone stayed on USB power.
- **Answer quality.** Outputs were compared token for token with the CPU and across thread counts,
  not graded.
- **The thread test in natural heat, repeated.** r19 is one ordered pair on phone B; r22 is one
  counterbalanced block (two cells of each count) on a fifth unit, its second block cut for r27.
  Inside a pause set by hand, r23 and r26 (the benchmark) and r27 (the app's engine) measured 6, 4,
  3 and 2 threads, and r49 (the app's engine) every count from 1 to 8 with prefill, on short fixed
  work, one model.
- **A fallback through a natural pause.** The engine's guard (r34, r38, r39) was measured against a
  pause set by hand; no natural pause was observed during its runs or the sampled chat sessions, the
  heaviest of which (r46, 97 minutes with no pause) reached a sampled 77.9 °C, about 0.1 °C under
  the trip. In a natural pause forced by busy loops and memory copies (r47, r48) it was measured once
  each way, on 19 prompt tokens and up to 256 more; never in a pause the model's own heat brought on. Android's thermal status was not tried as a signal.
- **Other chips.** The pause is a Qualcomm mechanism measured on the SM8850 only. On the MediaTek
  POCO, r41 measured what 2 threads cost against 4 at one length, and r37 and r41 that the guard stays
  quiet in chat; no pause or stall was produced there.
- **A CPU-only probe through a pause**, and the pause together with `kgsl` and `cdsp` at 5 as the
  natural hard throttle sets them.
- **A second pass of Gemma 3 1B, Qwen3 1.7B Q8_0, LFM2.5 2.6B and Llama 3.2 3B at 64 tokens.**
  Each of their 64-token cells is one run.
- **At reply length:** a second run of all nine cells of any model on one unit (some cells repeat
  across r10, r11 and r13 on c581fd31, and LFM2.5 1.2B ran all nine on two units, r12 and r15); and,
  apart from the cells after passed gates (the first of r8 and r9, r8's second, r10's two, r11's
  first two, r12's first two, r13's first two and r14's first) and two that timed out with full CPU
  ceilings (r11's Gemma 3 1B NPU-only cell and r12's LFM2.5 1.2B CPU-only cell), any cell started
  with the CPU unthrottled, since after a long cell the gate rarely got the phone back to 55 °C.
  The cooling devices were read only from r13's last model on.

## How far the labels can be trusted

A backend runs what it supports and llama.cpp's scheduler sends the rest to the CPU. "NPU" and
"GPU" therefore mean "the layers and their weights were placed there", not "every operation ran
there": the logs record the graph splits per context, summarised under
[where the work ran](#where-the-work-ran). The input embedding lookup stays on the CPU in every
cell.

## Reviews

Codex reviewed the plan, the tools and the findings in rounds. From the second session's rounds:
`run_et.sh` read a stale timing file, had no cooling gate, ran a glob instead of the manifest and
always exited 0 (all fixed before e1); its gate matched the `cpu-hw-trip` zones, which hold a fixed
105 °C (fixed); the prefill ranges, the 3B's decode wording ("approaches parity", not level), the
4B tie (0.27%, practical, not proven), the Q8_0 NPU-only decode gap (not explained by heat), the
0.6B CPU-decode gap (consistent with heat) and the gate timeouts were corrected in this note; the
agreement count is now "equal over the compared prefix".

For the reply-length runs, three more rounds. The first, on the `--gen-ref` change, found no
unequal work between cells and asked for turns dropped by the context limit, replies cut by the
cap and the per-step clock to be checked (none dropped and none cut; the clock adds two reads a
step, against steps of 7.6 ms or more, and was not measured separately). The second, adversarial, found two of r8's four Qwen3
0.6B cells had started after timed-out gates, that "the projection held" mixed cohorts and hid
21% to 26% higher absolute times, that one order per model cannot separate processor from order,
and that a Qwen3 4B cell on the other unit could not be ranked with the rest; the queue was changed
as described under r9, and `analyze_ref.py` now reports each gate, pairwise cohorts, CPU and GPU
caps before and after each turn, cut replies, and step ranges over the same turns. The third
recomputed every number in the reply-length section from the raw rows (all table totals and
percentages correct) and narrowed its wording: gate results instead of "cool" and "hot", the prefill ratio
(10.3 to 15.4 times at the time), the same-decoder spread (13% to 15% at the time), the 4B's two cohorts, and the
statements about heat and model size, which it showed the schedules cannot isolate. A fourth, over
the whole change, found the longest stall was 15.9 s, not the 8.7 s first written; that Llama 3.2
3B also used two Hexagon sessions; that "kept its rate" was wrong for the hybrid's CPU, which
slowed 18% to 28% but stayed ahead; that the stalls coincide with, but are not shown to come from,
the readings near 107 °C; and that the 4B's "only through the stalls" holds on its 33 turns, not
its 20-turn cohort. It also found that `analyze_ref.py` checked equal work only on the common
cohort, dropped cells whose every turn failed, counted stalls only on the common cohort, and would
accept a reply-length run as the 64-token baseline; all four are fixed, with the stall counts now
per pair. A fifth, on r10, and a sixth, on r11, found the numbers right and the wording
ahead of them: one cross-unit comparison is not a rule about heat; NPU-only took 5.3% longer than
CPU-only, not CPU-only "5.3% faster"; the r11 stalls' own turns ended at 94.6 to 106.6 °C, below
the 107 °C first attached to them; Gemma 3 1B's CPU-only cell also started after a passed gate, so
none of its comparisons is free of order; eight cells, not seven, started with full CPU ceilings;
and LFM2.5 2.6B's r11 result (NPU prefill with CPU decode 6.9% faster without the stalls, and on
26 of 33 turns) is not "level".

For r12 to r15, a seventh round recomputed all 81 table entries, the turn counts, the context
buckets, the decoder spreads, the 39-pair GPU comparison, the repeat totals and the pause counts
from the raw rows, and found them right. It corrected the wording: "fastest on every model" holds
among the cells after a timed-out gate (Qwen3 4B's passed-gate GPU prefill with CPU decode was
lower); r15 had one CPU step over 500 ms with no pause stretch, so the stall statement is scoped to
r13's Qwen3 4B and r14; Gemma 3 1B, not only LFM2.5 1.2B, decoded faster on its throttled CPU than
its NPU; the CPU-prefill cost is 3 to 17 points and the rerun's cells within 5 points when
computed from raw values rather than rounded percentages; there were three more reservations, not
four; r12 did not run the smallest model first; the warm-up matches the recorded gate and caps,
not thermal histories; and the GPU's recovery after r14 is bracketed by the saved readings (8.2
and 9.5 minutes), not the seven-minute reading first written, which was taken by hand and not
kept. It also found `analyze_heat.py` crediting samples taken between cells to the next turn
through the logger's label; it now joins samples to turns by time, which left the stall counts
unchanged and moved r14's NPU comparison from 36 pairs to 32 (median 0.67 to 0.68).

For r16 and the published page built from this report, Codex checked the throttle findings and the
page in four more rounds and Gemini (through agy) read the page once for order and clarity. They
reproduced every figure, and corrected: the soft throttle's recovery, which was bracketed once (the
GPU cap lifted between 1.4 and 8.2 minutes after a run), not timed at 8 to 10 minutes; the hysteresis
counts, now limited to r16's 2,557 readings; the state between 70 and 78 °C, which is the last state
rather than set by the temperature's direction; "within 1%" for the NPU, which holds for LFM2.5 2.6B's
NPU-only cell, not for NPU cells in general; the XO thermistor's placement, which its name does not
establish; the two Android readings, 20 minutes apart (17:38 and 17:59), not the hour first written;
and that a CPU-only cell's NPU and GPU instances are loaded but idle, not absent.

For r21 and r23 to r26, Codex reviewed each chain before it ran (three rounds; what changed is under
Runs) and this section and its analysis after. That round reproduced the decode tables, orders,
waits, stall sums and the 605 classified switch steps, and corrected: five level drops in r21, not
six; the capped GPU's peak, 61.0 °C, not under 58; the uncapped turns counted (5, 2 and 6 at turns 0
to 2, not six at each); 402 paused samples in r24, not 435; 2.2%, not 2%; and the paused exposure,
which depends on the method (27.8 against 24.8 s by episodes). It removed a step count estimated
from thread ids, an "upper bound" for the switching arm, and wording that made the ceiling the
whole cost, the pause's 1.6% a bound and unpaused switching free of cost. It also found that the
arms share a gate, not a starting heat; that r23's unpaused arms ran under ceilings the thermal
engine lowered; and that the app attaches a persistent thread pool, which the benchmark does not.
A second pass, over r25, r26 and the corrections, found the comparisons with r20 taken from rounded
figures (now from its unrounded medians: 2.0% to 5.9% faster on the CPU, not "within 2%"), asked for
each run's counting rule and the neighbour comparison to be stated as the code does them, and
removed "heat held equal" from r24 and a claim that no natural pause's length was measured.

For r22 and r27, Codex checked the r27 scripts and the test change before they ran, then this report
and `analyze_app.py`. It reproduced both tables, r22's excess and pause figures and the placement
counts, and corrected: the stopped `r22-5-t2` had finished two turns, not been stopped in its first;
which placement check gave which count; 26 of r27's runs completed and two were stopped, not "none
failed"; "from the same start" is a similar first-turn XO after a shared gate; r22 does not isolate
the stalls' cost or test switching, so the trade it was said to support is left to r23; 2 threads gave
up decode rate, not first-token time, which was later. It had the analysis tie each run's log lines to
its own process and thread count, check both written switches and the logger's coverage, and take
medians from unrounded rates.

For r28 to r38, Codex reviewed the guard three times before it reached a phone. The first round
found a fallback that could never end once the step times grew for another reason, prompt reading
left at the planned count, a thermal re-plan that handed back the stalled count, and a baseline
inflated by slow steps; the second, a try judged against a mean that one slow step after falling back
could inflate enough to pass a still-stalled try, and a failed decode leaving a try's count applied.
All were fixed with host-test cases, and the third found it ready behind its switch. A fourth round
read this report and its scripts, reproduced the r29 and r31, guard, session and agreement tables,
and corrected: the slow sixth turn's 550 tokens were read after the prompt stopped matching the
cache, not all new, and heat was not separated from it; only r38-4's failed pause is shown by
placement, so r34-10's and r34-14's are likely, not shown; one guarded run without a stall cannot show
what the guard costs; 6 against 2 threads shows a thread count can
change the reply, not that a switch mid-reply does; Y1M's unpaused cost is within run-to-run
variation, not nothing; and `analyze_energy.py`, not yet run, used one voltage per window, timed
decode from the end of prompt reading rather than the first token, and counted the first token. Two
shorter rounds checked those fixes (the slow turn's log does not say which of the 550 tokens were
the previous reply, and one run cannot bound the guard's cost) and r39 (the XO did not hold at 76.2
to 76.4 °C over the session's last ten minutes, as first written: it first passed 76.2 at 24.7
minutes, and its per-minute mean was 74.2 to 75.9).
A full review of the six commits that followed, through r41, found two bugs in the guard and no wrong
figure: a try scheduled by a reply's last step survived into the next reply, which then
opened at the planned count inside a pause even after a thermal re-plan down and back; and a slow
first step under 1.5 s, or a slowdown in steps under 8 times the normal one, was averaged into the
normal step until a stall no longer cleared the bar. Both are fixed with host-test cases, as is a
plan of 1 thread feeding the fallback's samples. The first fix for the second, the fastest of the
first four steps as the normal one, was found by a fifth round to miss a stall inside those four
steps and to fall back falsely after an unusually fast first step; the normal step is now the median
of the last eight, with a case for each; a sixth found that one fast first step still made the next
two ordinary steps a stall, so the relative rule now waits for two normal steps; a seventh that a model whose normal step is
already a second would show the pause as 4 to 6 times, under the factor, so a step more than 2 s
over the normal one is slow as well, and a try is held to the same excess; `analyze_energy.py` now starts decode at the first
token from the BENCH line's time to first token, not from a progress line logged after it.
