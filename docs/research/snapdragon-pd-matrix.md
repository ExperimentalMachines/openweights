# Prefill and decode on CPU, GPU and NPU: every pairing on a Snapdragon 8 Elite Gen 5

**Measured 2026-10-24 and 25 on a Qualcomm Device Cloud handset, `canoe`: SM8850 (Snapdragon 8
Elite Gen 5), Android 16, 23 GB, `adb` as root, on AC power in Qualcomm's rack, over four
reservations.** The first ran r1 to r4 and ended at about 16:55 phone time; the second (about
20:30 to 04:20) ran the three models the first could not reach (r6), LFM2.5 2.6B (r7), the
ExecuTorch NPU comparison (e1) and a second pass of five models in another cell order (r5). The
third and fourth, on 2026-10-25 on two units (727ba80 and a1716cc4), decoded each turn's reply at
the dataset's own length instead of 64 tokens (r8, r9, r9r;
[below](#at-the-datasets-own-reply-lengths)).

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
  (2.6B) ([below](#at-the-datasets-own-reply-lengths)).
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
decode budget adds one for the end of turn). Summed over a cell's turns, decode was 39% to 49% of
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
2026-10-25 ran it on two units of the same phone (`canoe`, SM8850): r8 on unit 727ba80, r9 and r9r on unit a1716cc4 (`analyze_ref.py`, output
in `analysis-r8.md`, `analysis-r9.md` and `analysis-r9r.md`). Qwen3 4B used two Hexagon sessions
(HTP0 and HTP1), as did Llama 3.2 3B, as in r6; the other models one.

**The cooling gate stopped passing.** Before each cell the gate waits for every CPU, GPU and NPU
zone to read 55 °C or less, the GPU to be uncapped and no CPU ceiling lowered, and gives up after a
set time (120 s; 300 s for r9's Qwen3 0.6B), after which the cell runs anyway. It waits; it does
not cool. After one cell of reply-length turns it timed out almost every time, with the GPU capped
at power level 8 and the CPU clusters' ceilings at 1.90 and 2.23 GHz (from 3.63 and 4.61): after
121 s, and after 302 s at 64.5 °C (CPU) on the first 300 s attempt. It passed only for the first
cell after an idle stretch (the first cell of r8 and of r9, both runs' Qwen3 first models) and once
after 50 s (r8's second cell). The table gives each cell's gate result; "timed out" covers a range
of states (hottest zone 56 to 71 °C when the gate gave up; r9's Qwen3 0.6B CPU-only cell started
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

Seconds are summed over the turns every cell of that row completed: 33 turns from nine
conversations, except Qwen3 4B, 20 turns from five conversations (7,027 decode steps). The
percentage is against that row's CPU-only cell. LFM2.5 2.6B is summed over the 29 turns both its
cells completed (eight conversations) and has no CPU-only cell. Over all 33 turns the 4B's
NPU-only cell took 1,064.7 s and NPU prefill with CPU decode 1,339.9 s.

- **Decode dominates, so NPU prefill leaves little to gain on the prompt.** NPU prefill read the
  prompt 10.3 to 15.4 times faster than the CPU and was 1% to 2% of an NPU-prefill cell's time;
  CPU prefill was still 15% to 16% of a CPU-only cell's. Single short turns are the exception:
  decode was 27.5% to 99.8% of individual turns.
- **CPU decode stalled on the two largest models.** In every cell that decoded on the CPU for
  Qwen3 4B and Llama 3.2 3B, the hottest CPU zone reached 106.6 to 107.3 °C at turn ends, and single
  decode steps took 0.5 to 15.9 s (22 to 37 such steps a cell, in two to six turns, 134 to 291 s
  in all). No NPU-only cell had one; their hottest CPU zone peaked at 98.5 °C. The temperatures
  are read at the turn's start and end, so they coincide with the stalls but do not show their
  cause. A stalled step is not the decoder's speed, but it is part of what a user of this phone
  would wait for, so the totals keep it and the comparison below is given both ways; "without"
  deletes the stalled steps' time, which measures their share, not what the cell would have taken
  without whatever caused them.
- **Wherever both NPU-prefill cells' gates timed out, NPU-only had the lower total on every
  transformer, by a margin that depends on the model and on the stalls; on the hybrids it was
  higher (LFM2.5 1.2B) or level (2.6B).** Over 33 turns (29 for LFM2.5 2.6B), NPU prefill with CPU
  decode took longer than NPU-only by:

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

  So on Qwen3 1.7B (in both orders) and Llama 3.2 3B NPU-only was faster with or without the
  stalls and on most turns; on Qwen3 4B its lead is the stalls; on Qwen3 0.6B it came from a few
  long turns and NPU prefill with CPU decode was faster on 23 of 33, and on Llama 3.2 1B the gap
  was 3.4% with each faster on about half the turns: ties in practice on the two smallest
  transformers.
  On the 4B's 20-turn cohort the stall-free gap is 18% (506.5 against 599.7 s), so that result
  also depends on which turns are counted. On the hybrid LFM2.5 1.2B the throttled CPU slowed
  (48.5 and 48.6 tok/s in its two cells, against 59.4 and 67.1 in the same cells at 64 tokens) but
  stayed ahead of the NPU's 40.9, with its hottest CPU zone at 106.5 °C and no stall, and NPU
  prefill with CPU decode was faster on every turn. On LFM2.5 2.6B the two were level (CPU decode 21.3 tok/s against the NPU's 20.8, each faster on
  about half the turns).
- **The 64-token winner won when its gate passed.** In r8 NPU prefill with CPU decode ran first,
  after a passed gate, and took 75% of CPU-only time, with NPU-only 13.5% longer. The same cell
  run last after a timed-out gate on the other unit took 87%. NPU-only took 292.7 and 292.8 s on
  the two units, after a timed-out gate both times.
- **On Qwen3 0.6B what moved is the CPU's decode rate.** In r9, where every gate timed out, CPU
  decode was 41.5 to 42.7 tok/s in the three cells that used it (a 3% spread) against the NPU's
  43.2; in r8's first cell it was 49.2. Where the CPU-decode cells ran at different points of the
  thermal history the same decoder differed by 13% to 15% (Qwen3 1.7B in r9, 24.8 to 28.4 tok/s;
  Qwen3 4B in r8, 9.1 to 10.2). These schedules do not isolate a backend effect inside that spread.
- **The share against CPU-only moves with the schedule as much as with the processor.** Qwen3
  1.7B's CPU-only cell took 518.5 s after a passed gate (r9) and 648.9 s after a timed-out one,
  run last (r9r), 25% apart on one unit (24.8 s of the 130.4 s difference is prefill), while its
  NPU-only cell took 452.9 and 441.1 s. Heat, order and inherited state were not varied apart, so
  the cause is not isolated; the comparison least exposed to it is between the two NPU-prefill
  cells.
- **NPU decode held its 64-token rate; CPU decode did not.** Re-weighting the 64-token turns (r4
  for Qwen3 0.6B and 1.7B, Llama 3.2 1B and LFM2.5 1.2B; r6 for Qwen3 4B and Llama 3.2 3B; r7 for
  LFM2.5 2.6B) to these lengths at their own measured rates
  predicted NPU-only within 1% to 5% on every model but Llama 3.2 3B (measured 1.01 to 1.05
  times the projection; 1.10 on the 3B). Cells decoding on the CPU measured 1.04 to 1.61 times
  their projection (1.16 or more in every cell but one; the 3B's two, with their stalls, 1.51 and
  1.61). The projection put NPU prefill with CPU decode at 72% of CPU-only on Qwen3 0.6B; r8
  measured 75%, r9 87%.
- **Decode slows along a long reply, more on the CPU.** Over the same 12 long turns (512 or more
  steps), CPU decode on Qwen3 0.6B fell 12% to 17% from the first 63 steps to steps 256 to 511 and
  NPU decode 5% to 6%, on both units, then recovered slightly past step 511. Context grows and heat
  accumulates over the same steps, so this does not say which causes it.

**What this does not show.** Each row is one order on one unit; only Qwen3 1.7B has two orders on
the same unit, and Qwen3 0.6B two orders on two units. The phone sat in a rack on AC power and
throttled within a cell; a hand-held phone in a short chat may decode with its CPU unthrottled for
much of the time, which is the condition r8's first cell describes (most 64-token turns were
throttled too), but that was not measured. The decode budget is the dataset's reply, not the
model's own: Qwen3's greedy outputs here open with `<think>`, and some reached an end of turn well
inside the budget (one at token 315 of 557), so a live reply could be longer or shorter. Answers
were not graded.

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
NPU prefill with CPU decode took 13% to 20% longer than NPU-only on Qwen3 1.7B and Llama 3.2 3B
without counting the CPU's stalls, and NPU-only led on Qwen3 4B over its 33 turns only through
the stalls (on its 20-turn cohort it still led by 18% without them); on the
two smallest, Qwen3 0.6B and Llama 3.2 1B, they tied. On the hybrid LFM2.5 1.2B the throttled
CPU stayed ahead of the NPU and NPU-only took 19% longer; on LFM2.5 2.6B the two were level. When the CPU started unthrottled (r8's first cell), NPU prefill
with CPU decode was fastest on Qwen3 0.6B, as at 64 tokens. So a fixed choice is wrong somewhere: what these runs suggest, and
do not test, is a decoder chosen per model family and moved to the NPU when the CPU's frequency
ceiling drops. This is a measurement of what each arrangement would buy on this chip, not a
decision. Before an app could ship any of it, these are still unmeasured:

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
- **Memory, energy and battery.** Three instances were resident at once in the 64-token runs (two
  in most reply-length runs); nothing measured power.

## What runs

One llama.cpp build (`b2e5e9b`, the commit the app vendors) with all three of its Snapdragon
backends: CPU (`ggml-cpu`), Adreno GPU (`ggml-opencl`) and Hexagon NPU (`ggml-hexagon`,
`libggml-htp-v81.so`), built with Qualcomm's toolchain image
`ghcr.io/snapdragon-toolchain/arm64-android:v0.7` as llama.cpp's Snapdragon guide does.

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

## What was not measured

- **ExecuTorch's CPU and GPU cells**, and any ExecuTorch model but Qwen3 0.6B.
- **Prompts past 4,096 tokens of context**, cold starts, and live (not teacher-forced)
  conversations.
- **Memory.** Two or three model instances were resident at once, so no deployment's footprint was
  measured.
- **Energy.** Nothing measured power or battery drain.
- **Answer quality.** Outputs were compared token for token with the CPU, not graded.
- **A second pass of Gemma 3 1B, Qwen3 1.7B Q8_0, LFM2.5 2.6B and Llama 3.2 3B at 64 tokens.**
  Each of their 64-token cells is one run.
- **At reply length:** Gemma 3 1B and Qwen3 1.7B Q8_0 at all; LFM2.5 2.6B's CPU-only cell; GPU
  prefill on any model but Qwen3 0.6B and 1.7B; GPU decode and CPU or GPU prefill into NPU decode
  on any model; a second order on one unit for any model but Qwen3 1.7B; and, apart from three
  cells (two the first of a run, one after a 50 s wait), any cell started with the CPU
  unthrottled, since the gate could not wait the phone back to 55 °C.

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
(10.3 to 15.4 times), the same-decoder spread (13% to 15%), the 4B's two cohorts, and the
statements about heat and model size, which it showed the schedules cannot isolate. A fourth, over
the whole change, found the longest stall was 15.9 s, not the 8.7 s first written; that Llama 3.2
3B also used two Hexagon sessions; that "kept its rate" was wrong for the hybrid's CPU, which
slowed 18% to 28% but stayed ahead; that the stalls coincide with, but are not shown to come from,
the readings near 107 °C; and that the 4B's "only through the stalls" holds on its 33 turns, not
its 20-turn cohort. It also found that `analyze_ref.py` checked equal work only on the common
cohort, dropped cells whose every turn failed, counted stalls only on the common cohort, and would
accept a reply-length run as the 64-token baseline; all four are fixed, with the stall counts now
per pair.
