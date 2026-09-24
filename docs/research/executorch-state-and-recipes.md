# Compiled LFM2.5 state and quantization experiments

**Status, 2026-09-21: historical research, not model approval.** The v1/v2 lab
exports and phone tables below describe their recorded builds, not every file currently
published under a similar name. The later published GPTQ 32k artifact, revision
`de4b372c466aaec71f13dabeeb61fe9fb4981b12`, SHA-256
`677a0a59ffa469e1b8f8cb1cc765458fe3e816d48c471568441ab82b4e1560f3`,
remains **not approved** in the tested app configuration. The
[captured-failure review](repo-audit-2026-09-17.md#captured-failure-approval-review)
records fresh arithmetic and fact-extraction failures, false completion claims after failed
or declined writes, an invented public URL, and factual loss during compaction. Passing
historical tool-call probes, publication, and the latency fix do not resolve those failures.
They do not by themselves locate the defect in quantization or the runtime either.

**Evidence locations after repository separation.** App integration runners, the held-out
fixture and POCO decision captures remain under `tools/eval/`. Qualcomm-derived parser
regrades live under `~/ow-models/benchmarks/additional-app-tree-20260921/tools/eval/results/`;
the source-level parser regression cases remain in the app repository's Kotlin tests.
Model-side `tools/executorch/quantlab/` sources moved to
`~/ow-models/benchmarks/quantlab/src/`; `tools/eval/results/quantlab/` moved to
`~/ow-models/benchmarks/quantlab/results/`. Short `quantlab/` references below name the
corresponding source or result within that external archive. Qualcomm/Firebase benchmark
captures live in `~/ow-models/benchmarks/qualcomm-2026-09/` and the preserved legacy
trees. The Snapdragon 8 Elite files commit `e3db3de5` removed from `tools/eval/results/`
(the public-benchmark runs, the decision-suite rows and their logs) were restored there on
2026-09-24; the quantlab tables below still need the local archives, which a clone of this
repository does not contain.
Archived scripts retain their historical relative paths; they are provenance, not a
drop-in replacement for the current standalone exporter.

The separate September Qualcomm comparison also did not measure the current 1.2B
downloads: its base was a superseded published revision and its heretic build was never
published. Both 2.6B builds did match published bytes. See the external
`qualcomm-2026-09/archive/PUBLISHED-VS-BENCHMARKED.md` and
`~/ow-models/hf-mirror/AUDIT-2026-09-21.md`; matching names or recipes are not provenance.

2026-09-17. Follow-up to `executorch-tool-calling.md` and `executorch-tool-calling-qad.md`,
which found that the 8da4w export of LFM2.5 1.2B never calls `web_search`, located the
loss in the int4 feed-forward, and ended on a recipe 1.7 times the GGUF's size. This note
asks the question again with one constraint added by the maintainer: keep ExecuTorch's
XNNPACK path and KleidiAI's kernels (which decoded 1.45 and 1.58 times faster than the two
GGUFs on one S25 Ultra unit and 1.23 times faster than the QAD GGUF on another), and lose
as little as can be measured. Two causes were found: recurrent state that survives the
runner's reset, and the rounding of round-to-nearest int4. Neither is a defect of XNNPACK's
kernels. A new export clears the state in its graph, and solved int4 codes replace the
rounding in the same format, kernels and 795 MB; files already published still need the
app's reload guard. What the export then loses is reported from the held-out and phone
tables (sections 1 and 8), not inferred: on 141 held-out questions the solved export
matches Q4_K_M on correctness, has six points more unnecessary searches than it, and trails
the QAD GGUF by seven points of needed searches on that run. Two qualifications run through
the note. The first is that the size of the round-to-nearest loss depends on the prompt:
under the one-tool prompt the rounded export searches on 10 percent of the questions that
need it, and under the app's full sixteen-tool prompt on 60 percent, with the solved export
at 75 (section 8). The near-total refusal to call is the short prompt's; the deficit is not.
The second is that the solve leads on recall, correctness, known answers and fabricated
searches under both prompts and costs unnecessary searches under both (29 against 2 percent
under one tool on the Snapdragon, 55 against 48 under sixteen tools on the Dimensity, which
are different phones), so "restores tool calling" is a claim about those four columns and
not about the fifth. Every table here is one run a file unless it
says otherwise, and section 8 measures how wide a single run's error bars are.

Measured on a Galaxy S25 Ultra (SM-S938U1, Snapdragon 8 Elite, Android 16, 12 GB) reserved
through Firebase Device Streaming and driven over plain adb
(`tools/eval/streaming/ds_bridge.py`), and on the laptop with the scripts in
`tools/executorch/quantlab/`. Reviewed by Codex (gpt-5.6-luna, xhigh) six times and by agy twice; sections 8 and 9
says what it found and what it changed.

## 1. The answer, on the phone

All 160 rows of the decision suite (RetrievalQA, PopQA, FreshQA; `retrieve-or-answer.md`)
through the app's own loop, driven-search arm, greedy, one session; and the public
benchmarks, 30 prompts each (`public-benchmarks.md`), same session. Read this table as
exploratory: four of its 160 questions turned out to be in the GPTQ calibration set (section
8; removing them moves no column by more than three points), and the clean comparison is the
141-row held-out run of section 8. The GPTQ row here is v1, seed 7, the first unit. Every
"Correct" column in this note comes from the current word-bounded `grade_decisions.py`, not
from the older tables `retrieve-or-answer.md` leaves as they were.

| Artifact (S25 Ultra, first unit) | Searched when needed (75) | Searched when not (60) | Correct (117) | Correct, known (60) | Fabricated a search | GSM8K | IFEval | BFCL | Prefill tok/s, app prompt | Prefill, benchmark prompts of 256+ | Decode tok/s | Resident | Size |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 8da4w as shipped (round to nearest, state leak) | 4% (3) | 0% | 27% | 48% | 21% | 17 | 21 | 25 | 240 | 241 | 56.1 | 1181 MB | 795 MB |
| same recipe, state cleared in the graph | 12% (9) | 0% | 31% | 55% | 21% | 19 | 22 | 26 | 241 | 239 | 56.5 | 1182 MB | 795 MB |
| same format, codes solved by GPTQ (v1, seed 7, first unit; the reference artifact is v2, section 8), state cleared | **51% (38)** | 20% | 35% | 58% | **0%** | 18 | 20 | 26 | 240 | 213 | 55.4 | 1170 MB | 795 MB |
| QAD Q4_0 GGUF (llama.cpp) | 59% (44) | 23% | 37% | 62% | 0% | 18 | 20 | 26 | 271 | 278 | 38.2 | 1062 MB | 731 MB |
| Q4_K_M GGUF (llama.cpp) | 47% (35) | 23% | 36% | 62% | 0% | 18 | 23 | 26 | 193 | 187 | 35.0 | 1102 MB | 697 MB |

| Second unit, same pool, a later session | GSM8K | IFEval | BFCL | Prefill, app prompt | Prefill, 256+ | Prefill, all 90 | Decode tok/s | Resident |
|---|---|---|---|---|---|---|---|---|
| GPTQ v2 (section 6), state cleared | 21 | 20 | 25 | 347 | 306 | 235 | 57.5 | 1172 MB |
| QAD Q4_0 GGUF (llama.cpp) | 18 | 20 | 26 | not run | 345 | 310 | 46.8 | 1082 MB |

Speeds are medians (`quantlab/phone_table.py`): prefill is prompt tokens over prefill time,
on the decision suite's first pass (the app's own prompt, 550 to 600 tokens, cold) and on
the benchmark prompts of 256 tokens and more; decode is generated tokens less one over
decode time, replies under 16 tokens left out. The two units differ by a third on prefill
(`dumpsys battery` read 28.9 C and 18.7 C as each session began; the reports themselves do
not carry it), so speeds are compared within a unit only.

What the speed columns say, and it is not what this project had assumed. Decode, in
matched sessions: GPTQ v1 55.4 against the QAD GGUF's 38.2 tokens per second (1.45 times)
and Q4_K_M's 35.0 (1.58 times) on the first unit; GPTQ v2 57.5 against QAD's 46.8 (1.23
times) on the second. Prefill, metric by metric: on the first unit the app-prompt medians
are 240 against QAD's 271 and Q4_K_M's 193, and the long-prompt medians 213 against 278 and
187; on the second unit the long-prompt medians are 306 against QAD's 345. So on a
Snapdragon 8 Elite ExecuTorch's advantage is decode; its prefill is about a tenth to a
quarter behind llama.cpp's Q4_0, which repacks for the same i8mm kernels, and ahead of
Q4_K_M. The 2.3 times prefill lead measured on the Dimensity 9400 (362 against Q4_K_M's 157 tokens
per second, the table in `executorch-tool-calling-qad.md`) does not carry to this chip. Whether the engine's
800-character prefill pieces cost ExecuTorch throughput here is examined in section 8.

Read: the GPTQ export searches on 38 of the 75 rows that need it where the shipped file
searches on 3, stops inventing searches (34 replies in 160 to none), and on the public sets
matches the QAD GGUF on all three (18, 20, 26) and Q4_K_M on GSM8K and BFCL while trailing
it by three on IFEval (20 against 23), while decoding 1.45 times faster than the QAD GGUF and
1.6 times faster than Q4_K_M on the same phone. On recall it sits between the two GGUFs
(Q4_K_M 47, this file 51, QAD 59 percent), and it is one phone, one session, thirty
prompts a set. The acceptance bar in
`executorch-tool-calling.md` section 6 (seed 8, several tools, a second phone, thresholds
first) still stands before any of this is a recommendation in the app.

In the app itself, same phone, debug build, one tool: asked "Who is the current CEO of
Nintendo?", the shipped export replied "I checked the latest available information using
web search. As of September 2026, the current CEO of Nintendo is Shunki Masaru", with no
search made. The GPTQ export searched ("Searched the web for current CEO of Nintendo") and
answered Shuntaro Furukawa (the app's own counter read 495 then 61 tokens per second, on a turn that reused 73 percent of its prompt from the cache; the screen is `quantlab/app/app2.png`). Asked for the capital of
Quebec it did not search, which is right, and said Montreal, which is wrong: a plausible
answer is not evidence of a correct one. Across the suites, the "correct, known" column is
58 percent against the QAD file's 62 on this seed-7 run (v1) and 70 against 70 on the
held-out run (v2, section 8).

## 2. Cause one: a new prompt inherited the last one's convolution state

ExecuTorch 1.4.0's LFM2 definition keeps each short convolution's last two input columns
in a registered buffer, `conv_state`, and prepends it to whatever arrives next
(`examples/models/lfm2/short_conv.py`). Nothing ever clears it. The runner's `reset()` is
`pos_ = 0` and nothing else (`extension/llm/runner/text_llm_runner.cpp:353`), and that is
what `LlmModule.resetContext()` reaches. So every prompt that starts at position zero on a
module that has already run starts on the tail of the previous conversation, in all ten
conv blocks, at the positions every later token attends to.

This was also the "loss of the graph" that `executorch-tool-calling.md` section 3 left
unexplained: the probe reused one runner for sixteen prompts, so the first row was clean
and the rest were not.

| fp32, no quantisation, first-token probability of the tool call | Hanover (run first) | Rome | Columbia | Jerusalem | Memory |
|---|---|---|---|---|---|
| Hugging Face reference | 0.852 | 0.939 | 0.931 | 0.921 | 0.618 |
| `.pte`, one runner reused (the 2026-09-13 table) | 0.852 | 0.491 | 0.703 | 0.779 | 0.227 |
| eager module, state left from the previous prompt | 0.852 | 0.680 | 0.628 | 0.382 | |
| eager module, state cleared | 0.852 | 0.939 | 0.931 | 0.921 | 0.618 |
| `.pte`, opened fresh per prompt (Codex) | 0.852 | 0.939 | | 0.921 | 0.618 |

Codex isolated it independently: clearing only the conv buffers restores Rome to 0.9387,
clearing only the KV cache leaves it at 0.6801. A KV cache is addressed by position and
masked past it, so Qwen3, Llama, Gemma and SmolLM exports do not have this defect.
Upstream's own Qwen3.5 definition, which also carries recurrent state, already multiplies
its state by `input_pos[0] != 0` (`llama/attention.py`, `_maybe_reset_state`); the LFM2
definition lacks the same line.

**The fix, in the graph** (`quantlab/lfm2_state_fix.py`): the state is multiplied by
`min(input_pos, 1)` before it is prepended (first written as `input_pos > 0`; section 8 says
why that changed), so a prefill at position zero reads zeros
whatever the buffer holds and a continuation reads the state as before. Verified on a real
file: the fixed 8da4w export, reused across prompts, gives exactly the probabilities the
old export gives only when it is reopened per prompt (0.077, 0.034, 0.023, 0.022, 0.036 on rows two to six;
`quantlab/pte/lfm_8da4w_*.json`, where the reused old file reads 0.008, 0.007, 0.003, 0.002,
0.006 on the same rows). Exports made this way carry `get_state_reset_at_zero` in their metadata.

**The guard, in the app** (`ExecuTorchEngine.resetRuntime`, written by Codex, trimmed for
detekt): a used LFM2 file without that marker is reopened instead of reset, about a second;
everything else keeps the cheap reset. The guard's tests cover this distinction. The
exports listed when this experiment began predated the fix and carried no marker.

What the leak cost on its own, same recipe, same phone, same unit: 4 to 12 percent
searched when needed, 27 to 31 percent correct, and 17, 21, 25 to 19, 22, 26 on the public
sets. In the separate held-out comparison of section 8, solved codes take the state-cleared
recipe from 10 to 49 percent searched when needed and from 33 to 45 percent correct. That
makes rounding the larger observed cause; it does not make the two shares additive, and it
does not show that rounding is the only remaining one.

**What it means for the record.** Every compiled-LFM2.5 quality number in the earlier notes
that came from a harness reusing one unmarked file was taken with the leak (the graph-fixed
exports and the app with its reload guard are separate, state-cleared evidence): the LFM cells of
`public-benchmarks.md` and `benchmark-matrix.md`, the compiled rows of
`retrieve-or-answer.md`, `recommended-runtime.md` and both tool-calling notes, the LFM
rows of `window-matrix.md` (its "same file twice" reading included, since the state made
output depend on row order). Codex's table, by document and line, is in
`tools/eval/results/quantlab/codex-engine-review.md`. The direction of those conclusions
survives (section 1's second row is still far from the GGUF); their sizes do not, and
"the phone's runtime is not bit-reproducible" is no longer established for this family.
Throughput numbers are unaffected.

## 3. Cause two: round-to-nearest int4, not the int8 activations

With the state cleared, the eager module export_llm traces was quantised in place and
probed on 40 rows (the sixteen of 2026-09-13 plus the 24 fresh ones): 16 where the fp32
module calls (p at least 0.5), 24 where it does not. `quantlab/sweep.py`; KL is the mean
divergence of the next-token distribution from the fp32 module's. Two paths produced the
rows and they are not interchangeable: "torchao" rows ran torchao's quantised tensors on
the CPU (`sweep1_families_torchao.json`, and the GPTQ row with its codes injected), "fake"
rows ran diagnostic fake quantisation (`sweep2_where_partial.json`), the only way to
quantise activations or weights alone. Where both exist they agree on the all-int4 recipe
(0.093 and 0.086, no call kept in either) and not on the all-int8 one (0.612 with 9 kept
against 0.690 with 14), so comparisons across paths are directional; the real all-int8 file
through the XNNPACK runner calls on all nine named rows (0.56 to 0.90,
`quantlab/pte/lfm_all8_reloaded.json`). What is claimed of a recipe is
claimed from its real file.

| Setting | Path | Linear weights | Mean p on the 16 calling rows | Calls kept | Calls on the 24 quiet rows | KL |
|---|---|---|---|---|---|---|
| fp32 | both | 4681 MB | 0.756 | 16 | 0 | 0 |
| int8 activations only, weights untouched | fake | | 0.738 | 16 | 0 | 0.020 |
| int8 activations only, on `w2` alone | fake | | 0.720 | 14 | 0 | 0.011 |
| 8da8w per channel everywhere | torchao | 1172 MB | 0.612 | 9 | 0 | 0.046 |
| attention int4, the rest int8 | torchao | 1144 MB | 0.720 | 16 | 1 | 0.106 |
| feed-forward int4, the rest int8 | torchao | 819 MB | 0.233 | 0 | 0 | 0.403 |
| 8da4w g32, round to nearest with HQQ scales (shipped) | torchao | 658 MB | 0.093 | 0 | 0 | 0.807 |
| same, torchao's plain affine scales | torchao | 658 MB | 0.095 | 1 | 0 | 1.243 |
| **8da4w g32, codes and scales from GPTQ** | torchao | 658 MB | 0.871 | 16 | 2 | 0.109 |

ExecuTorch stores blockwise scales as bf16, eight bits of mantissa. Modelled three ways on
the same 40 rows as fake quantisation on the GPU (`quantlab/pod/lab_sweep3_bf16_rn.json`,
the run to cite; an earlier run modelled the storage as truncation and is withdrawn):
scales in fp32, scales rounded to bf16 as the exporter stores them, and scales rounded
first with the codes re-rounded against them. Round to nearest reads KL 0.84, 0.70 and
0.95 with no call kept in any. These are modelled values, not measurements of a lowered file, and the three procedures
change the scales and, in one, the codes together; they show the combined procedure is
sensitive to a third of a percent on the scales in either direction, not an isolated storage
effect. The GPTQ solve
picks scales that are exact in bf16, so nothing moves at lowering.

In this 40-row LFM2.5 diagnostic the per-token int8 activations, which are the
precondition of KleidiAI's kernels, move the KL far less than the int4 weights do (16 of 16,
KL 0.02, on the fake path; the CPU torchao path and the fake path do not agree exactly, as
the all-int8 rows show, and Qwen3.5 in section 7 is activation-sensitive). The loss is the
int4 weights, as the 2026-09-13 bisection said, and it is a loss of rounding, not of the
format: the same format with solved codes keeps 15 of the 16 calls (14 on the torchao
path). That matches the one public table of
this exact scheme. Meta's Llama 3.2 1B card: BFCL v2 25.7 in bf16, 14.3 after "vanilla
PTQ" to 4-bit groups of 32 with 8-bit dynamic activations, 15.9 with SpinQuant, 23.7 with
QAT and LoRA; MMLU over the same steps 49.3, 43.3, 47.3, 49.0. For LFM2.5's decision token, function calling is what round to nearest breaks first; the other families in section 7 have other sensitive parts.

On the real file through the XNNPACK runner (`quantlab/pte_probe.py`), the GPTQ export
puts 0.66 to 0.96 on the ten named rows and completes a well-formed
`[web_search(query="...")]` on all ten, calls on the Letterboxd and Trump rows (which need
a search), and stays under 0.07 on the four rows that do not.

### How calibrated codes reach XNNPACK

ExecuTorch has no calibrated int4 path to this delegate: `8da4w-gptq` is accepted and
refused (pytorch/executorch #3632, open since 2024), the pt2e XNNPACK quantiser is int8
(#9846), torchao's GPTQ writes weight-only formats the delegate cannot lower, and XNNPACK
has no blockwise int4 kernel behind float activations (`XNN_MIN_BLOCKSIZE 32`, blockwise
only after `qd8`). But the delegate does not care who chose the codes. `quantlab/gptq.py`
solves them on the eager module, layer by layer with everything upstream already
quantised, each Hessian taken from the linear's input after the per-token int8 rounding
the delegate will apply, the shared 533-token head counted once; `recipe.inject` writes
codes and scales into torchao's tensors before lowering. The grid is the delegate's own:
symmetric, codes in -8 to 7, groups of 32, positive scales, and scales already exactly
representable in bf16, because that is how ExecuTorch 1.4 stores them
(`node_visitor.py`, `scale.to(torch.bfloat16)`). Layer output error against round to
nearest: 0.10 to 0.40, lowest on `w2` (0.10 to 0.16). Twenty-four minutes on a laptop CPU,
157 calibration rows: the app's own prompt with seed-8 questions the probe never uses, each
with the fp32 model's greedy reply, so the decision token is in the data without a label.

### What "GPTQ" means in every table of this note

`gptq.py` as first written solved the linears inside the layers and stopped: the output head
(the tied embedding's copy, 65,536 by 2,048, a ninth of LFM2.5's weights) kept round-to-nearest
int4 in every export measured here, on every family. The export log says so in two lines
("int4 group 32: 113 linears", "injected calibrated codes into 112 linears") and nobody
read them until agy's review (2026-09-18, `quantlab/agy-review-1.md`). So "GPTQ" below and
above means GPTQ layers with a round-to-nearest head. The solver now does the head too, and
`recipe.inject` names every blockwise linear it leaves to round to nearest; what the head
solve is worth is measured in section 8, not assumed.

### The tilt

The GPTQ export's mean on calling rows is above fp32's (0.871 against 0.756) and it calls
on two quiet rows (video-game franchise 0.55, where fp32 says 0.10 and Q4_K_M 0.38). On the
phone that is "searched when not" 20 percent, the GGUFs' level (23), against the shipped
file's 0. A third of the calibration replies were tool calls and every row was the app's
prompt, so a calibration tilt is the likely reading. A second solve on a wider set (the
same rows plus a held-out seed-8 draw of GSM8K, IFEval and BFCL, 120 rows, graded ids
removed; `quantlab/calib_public.py`) is recorded in section 6.

## 4. What stays true of XNNPACK, and what is general

- Weight-only int4 with float activations does not exist on this delegate and nothing on
  `main` or in 1.5.0 (2026-09-16) changes that. For LFM2.5 the activation-only diagnostic did
  not explain the collapse; Qwen3.5 does pay for its activations (section 7).
- Groups smaller than 32 do not exist either (#14221, open). Not needed.
- A recipe is a list of regex rules (`quantlab/recipe.py`), the same function in the lab
  and in the export, so a mixed recipe (int8 on `w2`, int4 elsewhere, about 835 MB) is one
  JSON file if a family needs it. LFM2.5 did not.
- What is XNNPACK's and what is not: the solver is generic; the grid is the backend's.
  Positive scales, symmetric codes, groups in multiples of 32, bf16 scales and per-token
  int8 inputs in the Hessian are XNNPACK's contract. Vulkan lowers the same symmetric group-32 checkpoint (8da4w or weight-only 4w), and on a
  phone it ran the Qwen3 weight-only file and segfaulted on the LFM2.5 hybrid (section 8):
  lowering is not running. torchao's low-bit kernels take groups of 16 and zero points; QNN
  and MediaTek are static, calibrated pt2e flows with 16-bit activations and need their own
  pass. The next backend is a grid description beside the recipe and a device run, not a
  new solver.

## 5. Tools this added

- `tools/executorch/quantlab/`: `etmodel.py` (the eager module export_llm traces, state fix
  applied), `recipe.py` (rules, apply, inject), `fake.py` (diagnostic settings no delegate
  runs: weights only, activations only, Hadamard, bf16 scales), `sweep.py`, `probe.py`,
  `pte_probe.py`, `calib.py`, `calib_public.py`, `gptq.py`, `export.py` (one command from a
  converted checkpoint to a `.pte` with a named recipe; nothing in the venv is edited),
  `phone_table.py`. Environment: `~/ow-models/etexport/.venv` (executorch 1.4.0, torch
  2.14, torchao 0.18).
- `tools/eval/streaming/ds_bridge.py`: a Device Streaming phone as an adb device without
  Android Studio. The API hands out one gRPC stream that multiplexes adb services; the
  bridge plays the device end of adb's transport protocol on localhost. Three things the
  documentation does not say: the client library's wrapper blocks on a first reply that
  never comes (use the raw stub), the stream needs an `x-goog-user-project` header, and an
  idle stream is dropped after about a minute. HTTP/2 pings were not a durable fix (after a
  while the server answered `too_many_pings` and dropped it anyway), so the bridge opens a
  no-op adb service every 25 seconds. Observed limits, not documented ones: a session is
  created with at most an hour, `extend` took one to three hours and no further, and the
  project's quota is 200 streaming minutes a month.

## 6. The second solve, and the gate it was read against

Codex's gate, set before the run: mean KL to fp32 at most 0.15 and the 95th percentile at
most 0.50 on prompts calibration never saw, no call on a row where fp32 sits under 0.2, and
the calling rows' mean within 0.05 of fp32's. Held-out prompts are the 90 graded benchmark
rows (`quantlab/render_bench.py`; `calib_public.py` removes their ids from the calibration
draw) and the 40 app rows of section 3.

| 8da4w g32 | App rows: mean p on calling rows (fp32 0.756) | Calls kept | Calls where fp32 is under 0.2 | App rows KL | Benchmark rows KL, mean | 95th percentile | Max |
|---|---|---|---|---|---|---|---|
| round to nearest (shipped) | 0.093 | 0 of 16 | 0 | 0.807 | 0.307 | 1.40 | 2.20 |
| GPTQ, calibrated on the app's prompt only (v1) | 0.871 | 16 | 2 | 0.109 | 0.211 | 0.75 | 2.70 |
| GPTQ, app prompt plus a held-out GSM8K, IFEval and BFCL draw (v2) | 0.685 | 14 | 0 | 0.124 | 0.074 | 0.26 | 1.53 |

The first solve was tilted, as suspected: calibrated on one prompt shape it held that shape
and drifted on everything else (benchmark KL 0.21, barely better than round to nearest's
0.31), and it leaned toward calling. The second, on 277 rows and 35,804 positions, is three
to four times closer to fp32 on general prompts and makes no call fp32 would not. The table
above is the pre-QA gate (CPU torchao path, fp32 embedding). The gate to cite is the
re-run with the embedding at int8 as exported (`quantlab/pod/emb/lab_gate_lfm_app.json`,
`_bench.json`, GPU fake path): v2 gives 0.743 on the calling rows against fp32's 0.756,
keeps 15 of 16, makes no false call, app-row KL 0.147 with a 95th percentile of 0.497,
benchmark-row KL 0.073 with a 95th percentile of 0.362. That passes every gate as set
(the app-row 95th percentile passes 0.50 narrowly). On the real file it completes a well-formed call on
all ten named rows (0.71 to 0.92), calls on the Trump row and, like fp32, not on the
Letterboxd or Hillsdale rows that v1 had started calling on.

On the phone, a second S25 Ultra from the same pool (a cooler unit: time to first token on
the app's prompt 1.7 s against 2.4 s, so speeds are not compared across the two sessions):

| Artifact | Searched when needed (75) | Searched when not (60) | Correct (117) | Correct, known (60) | Fabricated a search |
|---|---|---|---|---|---|
| GPTQ v1 | 51% (38) | 20% | 35% | 58% | 0% |
| GPTQ v2 | 48% (36) | 22% | 34% | 60% | 0% |
| Q4_K_M GGUF | 47% (35) | 23% | 36% | 62% | 0% |
| QAD Q4_0 GGUF | 59% (44) | 23% | 37% | 62% | 0% |

The two solves are within the suite's noise of each other and of Q4_K_M on every column
(75 rows put about eleven points either side of a percentage). v2 is the one to carry forward, because the held-out KL makes it the candidate closest to
fp32 on prompts the app has not thought of yet (closest, not identical: 0.06 of KL and 93
percent top-token agreement over a reply, section 8). The QAD file searches more often in
this suite; these runs do not say whether that comes from its training, its recipe, or
both.

**Where the solve runs.** On the 24 GB laptop one 1.2B solve took 24 minutes alone (1,451 s,
`quantlab/logs/lab_gptq_all4.log`) and 71 with the wider calibration set and other jobs
running (4,250 s, `lab_gptq_all4_v2.log`). On a rented RTX 4090 (`gptq.py --device cuda`) the same solve takes 8 minutes (483 s, `quantlab/pod/gptq_lfm_v2_cuda.log`), identical codes to the CPU's on a synthetic check, and
`quantlab/pod_family.sh` runs a family end to end: calibration text, solve, both gates,
both exports. One trap on the way: from MPS, `tensor.to("cpu", torch.float64)` in one call
returned garbage on torch 2.14 and the Cholesky then failed as not positive-definite; two
steps are correct.

**What this gate is worth, read two days later (2026-09-19).** The 0.05 band above is not a
usable acceptance test, and the reason is that the 16 app rows it is read on are the set
every recipe in this note was tuned against. On twelve tool-shaped questions drawn fresh,
under the same app prompt, the published v2 file is 0.120 *under* fp32's mean on its calling
rows where on the tuned set it sits 0.021 over -- a swing wider than the differences the
band is meant to resolve. The first file the exporter's own pipeline built, whose
calibration carried no tool schema at all, reads 0.092 under on that fresh draw and keeps
all twelve calls, so it is not worse than what is published, though it was read as a
regression on the tuned set first. What does separate cleanly on the fresh draw is the
failure this note is about: round to nearest keeps none of fp32's twelve choices and sits at
0.049. The exporter now gates publishing on that measurement instead, at 90 percent of
choices kept and no more than 0.25 under fp32's mean, with the band one-sided because the
abliterated export calls more readily than the model it came from (0.945). Numbers and raw
files in `quantlab/gate/` and the JOURNAL entry of the same date. Read the section above as
what distinguished two candidate solves on one prompt shape, which is what it was for, and
not as a threshold any export should be held to.

## 7. Other families, same pipeline

`quantlab/pod_family.sh <family> <tag>` on the rented 4090: calibration text in the
family's own template (157 app rows, 120 held-out public rows, the fp32 model's replies),
the solve, both gates as fake quantisation on the GPU with scales as the delegate stores
them, and both exports. "App rows" are the 40 probe questions in that family's template;
"benchmark rows" the 90 graded prompts, which calibration never sees.

| Family | Rows | fp32 calls | Round to nearest: calls kept | lowest p on a calling row | KL mean | KL 95th | GPTQ: calls kept | lowest p | KL mean | KL 95th |
|---|---|---|---|---|---|---|---|---|---|---|
| LFM2.5 1.2B (short conv hybrid) | app | 16 | 0 | 0.02 | 0.807 | | 14 | 0.39 | 0.124 | 0.47 |
| | benchmark | 30 | 30 | | 0.307 | 1.40 | 30 | | 0.074 | 0.26 |
| Qwen3 1.7B (attention only) | app | 17 | 15 | 0.00 | 1.531 | 11.0 | 16 | 0.02 | 0.967 | 4.10 |
| | benchmark | 28 | 26 | 0.002 | 0.712 | 4.37 | 28 | 0.979 | 0.357 | 1.65 |
| Llama 3.2 1B (attention only) | app | 40 | 35 | 0.009 | 0.285 | 0.69 | 34 | 0.269 | 0.207 | 0.58 |
| | benchmark | none at the first token | | | 0.848 | 2.20 | | | 0.141 | 0.59 |
| Qwen3.5 2B (Gated DeltaNet hybrid) | app | 19 | 2 | 0.00 | 1.348 | 3.31 | 8 | 0.00 | 1.200 | 3.06 |
| | benchmark | 27 | 19 | | 0.522 | 1.50 | 24 | | 0.078 | 0.30 |

Read: the collapse on the app's decision belongs to the two hybrids and not to the two
attention-only families. Round to nearest leaves LFM2.5 with 0 of 16 calls and Qwen3.5 with
2 of 19, while Qwen3 keeps 15 of 17 and Llama 35 of 40. The 2026-09-13 note called the
collapse LFM2.5's alone, and that was written before Qwen3.5 was measured; on this table it
is a property the recurrent families share. LFM2.5 is the family where GPTQ alone restores
most of the first-token behaviour in the lab gate
(15 of 16 calls with the embedding quantised) and lifts the phone suite, without restoring
every call or matching the QAD file on every held-out column (section 8). On the two attention-only families round to
nearest keeps most calls, and what it loses is fidelity everywhere else: Llama 3.2 1B sits
at a mean KL of 0.85 from its fp32 self on general prompts and GPTQ takes that to 0.14, a
sixfold reduction, the same direction as Meta's own table for this model and this scheme
(BFCL v2 25.7 in bf16, 14.3 after round to nearest). Qwen3's distributions are nearly
one-hot, so a single flipped row costs a KL above 10 and its means are dominated by two
rows; the calls are the clearer reading there, 26 of 28 to 28 of 28 with the weakest
calling row going from 0.002 to 0.979. On Qwen3's real files through the XNNPACK runner
the named rows read 0.96 to 0.99 under round to nearest with one at 0.52, and 0.98 to 0.99
with that row at 0.99 under GPTQ (`quantlab/pte/qwen3_rtn.json`, `qwen3_gptq.json`). Llama 3.2 opens a call with plain JSON, so its "call
token" is the first token of `{"` and a benchmark row never starts with it; only its KL is
reported there.

**Qwen3.5 2B is the family GPTQ alone does not rescue.** On short general prompts it
does what it did everywhere else (KL 0.52 to 0.08, 19 of 27 calls to 24). On the app's own
640-token prompt it barely moves (KL 1.35 to 1.20, 2 of 19 calls to 8). A sweep of the
round-to-nearest recipe on those rows says where (`quantlab/recipes/sweep_qwen35_where.json`,
fake path, fp32 keeps 19):

| Qwen3.5 2B, app rows, round to nearest | Linear weights | Mean p on calling rows (fp32 0.850) | Calls kept | KL |
|---|---|---|---|---|
| int8 activations only | | 0.840 | 17 | 0.110 |
| int8 everywhere | 1885 MB | 0.845 | 17 | 0.095 |
| int4 weights only, activations untouched | 1058 MB | 0.307 | 4 | 0.960 |
| all int4 (the recipe of the 2026-09-13 note) | 1058 MB | 0.203 | 2 | 1.348 |
| int4, the DeltaNet gates `in_proj_a`, `in_proj_b` at int8 | 1059 MB | 0.323 | 5 | 1.091 |
| int4, the DeltaNet projections (qkv, z, a, b, out) at int8 | 1225 MB | 0.671 | 15 | 0.476 |
| int4, the feed-forward at int8 | 1456 MB | 0.364 | 7 | 1.038 |
| int4, the full-attention projections at int8 | 1097 MB | 0.282 | 5 | 1.278 |
| int4, the head at int8 | 1282 MB | 0.220 | 2 | 1.312 |
| int8, only the feed-forward at int4 | 1487 MB | 0.741 | 16 | 0.439 |
| int8, only the DeltaNet projections at int4 | 1718 MB | 0.658 | 13 | 0.770 |

The reverse of LFM2.5: there the feed-forward was the sensitive family and attention could
go to int4 freely; here the feed-forward takes int4 with the least damage and the layers
that write into the recurrent state do not, which is what the literature on state-space
models says of the state's inputs (Quamba2, arXiv 2503.22879) and what public 4-bit builds
of larger Gated DeltaNet models do by protecting those projections. Protecting only the
two small gates is not enough. The int8 activations also cost something here (KL 0.11, two
calls) where on LFM2.5 they cost nothing. The affordable recipe is the DeltaNet projections
at int8 and everything else at int4 (`quantlab/recipes/deltanet8-rest4.json`), 1.2 GB of
linear weights against 1.06, with the int4 part solved by GPTQ:

| Qwen3.5 2B | Linear weights | App rows: calls kept (19) | mean p (fp32 0.850) | calls fp32 would not make | KL | Benchmark rows: calls kept (27) | KL mean | 95th |
|---|---|---|---|---|---|---|---|---|
| all int4, round to nearest | 1058 MB | 2 | 0.203 | 0 | 1.348 | 19 | 0.522 | 1.50 |
| all int4, GPTQ | 1058 MB | 8 | 0.373 | 0 | 1.200 | 24 | 0.078 | 0.30 |
| DeltaNet int8, rest int4, round to nearest | 1225 MB | 15 | 0.671 | 1 | 0.477 | 23 | 0.316 | 1.10 |
| **DeltaNet int8, rest int4 by GPTQ** | 1225 MB | 17 | 0.796 | 0 | 0.230 | 26 | 0.073 | 0.26 |
| int8 everywhere | 1885 MB | 17 | 0.845 | 2 | 0.095 | | | |

The mixed solve keeps as many of the app's calls as int8 everywhere at two thirds of the
weight, with the calling rows' mean 0.054 under fp32's and one benchmark row called that
fp32 leaves at under 0.2. Those are laptop-and-GPU results on the eager module (the fake path), measured before any
compiled Qwen3.5 file existed; the recipe is a JSON file and `export.py` takes it. Since
then the app has gained a compiled Qwen3.5 template (`Qwen35Prompt.kt`, eleven fixtures
rendered from the upstream template, fourteen unit tests), though `tools/eval/compare.py`
still drops the family and the XML parser reads one call a reply and makes every value a
string. Both exports have since run on the S25 Ultra through Test Lab, BFCL only, one run
each
(`ftl-s25u-Qwen3.5-2B-8da4w-rtn-2k.bench-bfcl.json`, `ftl-s25u-Qwen3.5-2B-mix-gptq-2k.bench-bfcl.json`):

| Qwen3.5 2B, BFCL, S25 Ultra, one run each | Rows run | Made a call | Pass | Prefill tok/s | Decode tok/s | Resident MB |
|---|---|---|---|---|---|---|
| all int4 g32, round to nearest | 30 | 24 | 15 | 6.8 | 6.9 | 1467 |
| DeltaNet int8, rest int4, GPTQ | 28 (time budget) | 25 | 16 | 5.7 | 5.7 | 1625 |

Nine called rows in each file failed the typed grade. The app's XML parser hands every
argument over as a string, numbers included, so most of those failures are the parser's: a
list or a boolean is in seven of the nine for round to nearest and six of the nine for the
mixed solve. A few are the model's, not the parser's (one wrong enum value, one omitted
argument), so the pass count is depressed rather than purely a parser artefact. The two runs
completed 30 and 28 rows and their call rates do not separate the two files, and these prompts (325 to 717 tokens, few tools) are not the
640-token app prompt the lab's prediction was about. What the runs do show is the speed of this export path:
six to seven tokens a second on both prefill and decode on these units, because upstream
exports the family with a static shape and the portable attention and the runner then
prefills a token at a time. In these two runs both exports attempted a call on most rows, which is more
than the lab's app-prompt result predicted for the rounded file; whether either holds up
under the app's own prompt on a phone was not tested, and at six to seven tokens a second
the export path (dynamic shape and an attention kernel for Gated DeltaNet) is the barrier
before that question becomes worth asking.

Llama's checkpoint is Hugging Face's layout converted back to Meta's
(`quantlab/convert_llama_hf.py`; next-token KL to the Hugging Face model 7e-9), from an
ungated mirror because the shell held no token for the gated repository. Gemma 4 was asked
for and not done: ExecuTorch ships it as a multi-method program with its own runner, which
this app's engine cannot open, the weights are gated, and the app renders no tools for
Gemma, so there would be no call to measure. Qwen3.5 2B got its compiled template on
2026-09-18 (see above); its lab numbers are joined by one BFCL run a file on a phone.

**On the phone.** Device Streaming's monthly quota (200 minutes a project) was spent by
the two LFM2.5 sessions, so these four runs went through Firebase Test Lab's S25 Ultra pool
(`tools/eval/bench/run_ftl_local.sh`, which sends the files from the laptop and so needs no
billed bucket). Each run lands on whichever unit is free, and the same kernels read 26.8 and
30.8 tokens per second on two of them, so only the grades are compared.

| S25 Ultra, 30 prompts a set | GSM8K | IFEval | BFCL | BFCL, parser fixed | Decode tok/s | Resident |
|---|---|---|---|---|---|---|
| Qwen3 1.7B, round to nearest | 20 | 16 | 24 | | 30.8 | 1893 MB |
| Qwen3 1.7B, GPTQ | 22 | 14 | 27 | | 26.8 | 1893 MB |
| Llama 3.2 1B, round to nearest | 14 | 4 | 13 | 13 | 47.1 | 1320 MB |
| Llama 3.2 1B, GPTQ | 17 | 6 | 7 | 13 | 38.7 | 1333 MB |

In this thirty-prompt-a-set run Qwen3 moves the way the lab said: three more BFCL rows, two
more GSM8K, two fewer IFEval. No repeat was run, so the run cannot tell those from
prompt-sample variation.

Llama's BFCL fell from 13 to 7, and the first explanation written down for it was wrong.
It looked like a calibration artefact, because the solved model writes its calls as
`{"type": "function", "function": "get_weather", "parameters": {...}}`, the shape the tools
are declared in, on 17 of 30 rows, where round to nearest does so on 1, and the app's parser
reads only `{"name": ..., "parameters": ...}`. But the fp32 model under the app's own
template (`quantlab/app_templates.py`, a transcription of `Llama32Prompt.kt`) writes that
shape on 22 of 30 rows, and under Hugging Face's template on the same 22
(`quantlab/pod/llama_fp32_call_shapes.json`). So the shape is the model's own, the parser did not know it, and
that omission is the whole of the six-point gap: `ToolCallParser.parseBareJson` now
reads both shapes (and no longer takes an argument called "name" for the function's);
re-reading the same two phone reports with that rule gives 13 and 13. The counts (fp32 22,
GPTQ 17, round to nearest 1) are aggregates: they say the solved export writes the model's
shape far more often than round to nearest does, not that it matches fp32 row for row, and
they do not show why round to nearest stopped writing it. The rest of Llama's BFCL losses
are its own: numbers written as strings. The held-out KL (0.85 to 0.14) is the number that
says what GPTQ did to this family; BFCL through this harness, before the parser fix, was
measuring the parser.

The four `regrade-parserfix-Llama-3.2-1B-Instruct-8da4w-*.bench*.json` files under
`~/ow-models/benchmarks/additional-app-tree-20260921/tools/eval/results/` are offline parser
regrades, not new device runs. Their original reports are
`~/ow-models/benchmarks/qualcomm-legacy-app-tree/tools/eval/results/ftl-s25u-Llama-3.2-1B-Instruct-8da4w-{gptq,rtn}-2k.bench.json`.
Across the 90 cases, the GPTQ regrade changes 15 `calls` fields and no generated text or
timings; the RTN cases are unchanged. The source report SHA-256 values are
`155b83e16b08579763e380a2bedcadd02ee88d3412b728006919be6f921a78eb` (GPTQ) and
`1c8e9d90fd546646f06e93e19938eb65ebed6ec63d9bb630bade13e6759fb0ea` (RTN).
The `-parserfix` model suffix labels that reinterpretation, not another model artifact.

## 8. The completion pass (2026-09-18): held out, second phone, other backend

Everything in this section is copied from `tools/eval/results/quantlab/JOURNAL.md`, which
names the file behind each number, and `SUMMARY.md`, which `quantlab/collect.py` regenerates
from the raw reports.

### Held out

The GPTQ calibration took its app-prompt questions from `decisions-seed8.json` and excluded
only the 40 probe questions; seeds 7 and 8 share seven questions, so **four of the phone's
160 test questions had been calibrated on** (the question and the fp32 model's own reply,
never a label). Removing them moves no column of section 1 by more than three points (Q4_K_M's recall, 47
to 44; `quantlab/decisions_s25u_seed7_minus_calibration_overlap.md`). The clean test is a new
draw: seed 9 with every question of seed 7, seed 8 and the probe removed, 141 rows
(`tools/eval/bench/decisions-seed9-heldout.json`; `calib.py` now refuses to run without
these exclusions).

| Held-out, one tool, Galaxy S25 Ultra (Test Lab; GPTQ v2 and QAD on one unit, RTN and Q4_K_M on another) | Searched when needed (63) | Searched when not (56) | Correct (112) | Correct, known (56) | Fabricated a search |
|---|---|---|---|---|---|
| 8da4w round to nearest, state cleared (32k window; the rest 2k) | 10% (6) | 2% (1) | 33% (37) | 57% (32) | 25% (35/141) |
| **8da4w GPTQ v2, state cleared** | 49% (31) | 29% (16) | 45% (50) | 70% (39) | 1% (1/141) |
| Q4_K_M GGUF | 51% (32) | 23% (13) | 45% (50) | 70% (39) | 1% (1/141) |
| QAD Q4_0 GGUF | 56% (35) | 34% (19) | 46% (52) | 70% (39) | 0% |

| Held-out, one tool, Poco X8 Pro Max (Dimensity 9400) | Searched when needed (63) | Searched when not (56) | Correct (112) | Correct, known (56) | Fabricated |
|---|---|---|---|---|---|
| 8da4w round to nearest, state cleared by the app's guard | 10% (6) | 4% (2) | 30% (34) | 52% (29) | 25% (35/141) |
| 8da4w GPTQ v2, state cleared | 44% (28) | 25% (14) | 41% (46) | 62% (35) | 1% (2/141) |
| Q4_K_M GGUF | 51% (32) | 23% (13) | 44% (49) | 68% (38) | 1% (2/141) |
| QAD Q4_0 GGUF | 56% (35) | 32% (18) | 45% (50) | 70% (39) | 0% |

On questions nothing was tuned on, on the Snapdragon the solved export is close to Q4_K_M
on needed searches (49 against 51), matches it on correctness, known-answer correctness
and fabricated searches, has six points more unnecessary searches (29 against 23), and is
seven points of recall under the QAD file. Round to nearest with the state leak already
fixed searches on 10 percent, invents a search in a quarter of its replies and is twelve
points less correct; those two rows come from different units. The same pair on one unit
(`quantlab/decisions_s25u_s9c_same_unit_rtn_vs_gptq.md`, a third Test Lab run) reads round
to nearest 11, 2, 30, 54 and 26 percent against GPTQ v2 48, 29, 41, 64 and 1: 37 points of
recall, 11 of correctness, and fabricated searches from a quarter to one percent, on the
same phone. In the one battery-powered run on the Dimensity the gap to the QAD file is wider (twelve
points of recall, seven questions of 63; four of correctness, four questions of 112), and
round to nearest reads 10, 30 and 25
percent there against the Snapdragon's 10, 33 and 25: the same pattern in the one run on
each chip, which is not yet a chip-wide property. The sixteen-tool arm ran only on the
Dimensity, so nothing here says whether that prompt would change the Snapdragon the same
way.
The loss this note set out to explain is a loss of correctness as well as of tool calls,
and the solve recovers most of both.

### The output head, and what "every token" says

Every GPTQ file above has a round-to-nearest head (section 3). Solving it too
(`gptq.py --resume`, 23 seconds) moves the calling rows' mean from 0.682 to 0.715 (fp32
0.756) and leaves KL where it was; on the phone it grades 20, 22, 25 against 21, 20, 25 on
the public sets, and on the held-out suite, both files on one unit, 48 against 49 percent
searched when needed and 43 against 45 percent correct (known answers 35 against 39 of 56).
Nothing measurable, and the known-answer rows lean the wrong way inside the noise. The
solver can do it; the evidence does not ask for it, and the v2 file with its
round-to-nearest head stays the reference artifact. (The v2 file's recall, correctness and known-answer correctness, 49, 45 and 70 percent,
matched to the count on two different units, with unneeded searches at 29 and 25, although
the rows underneath did not replicate (14 questions differ on correctness and 6 on whether a
search ran); a third unit read 48, 41 and 64, so this file varied by about four points of correctness and
six of known answers across those three units, which is the only direct measurement here of
how much a single run moves and the width worth keeping in mind for the single-run rows
elsewhere.)

`quantlab/seqkl.py` is the stronger gate agy's review asked for: the fp32 model writes a
reply to each of the 90 held-out benchmark prompts and every recipe is teacher-forced over
it, thousands of positions instead of ninety.

| Every token of the reply (fp32 embedding; `pod/lab_seqkl_*.json`) | Positions | Round to nearest: KL | top token is fp32's | GPTQ: KL | top token is fp32's |
|---|---|---|---|---|---|
| LFM2.5 1.2B | 4,484 | 0.0997 | 90.8% | 0.0601 | 92.8% |
| Qwen3 1.7B | 4,924 | 0.2636 | 88.8% | 0.1291 | 92.2% |
| Llama 3.2 1B | 4,930 | 0.1483 | 87.7% | 0.0533 | 92.7% |
| Qwen3.5 2B, DeltaNet int8 and the rest int4 | 5,475 | 0.0622 | 91.5% | 0.0357 | 93.7% |

With the embedding at int8, as the 8da4w exports have it and as the lab had not until
Codex's second QA, the four rows read 0.1027 and 0.0621 (LFM2.5), 0.2689 and 0.1281 (Qwen3),
0.1507 and 0.0550 (Llama), 0.0631 and 0.0355 (Qwen3.5 mixed) (`pod/emb/lab_seqkl_*.json`):
slightly different numbers, the same reading. In these four teacher-forced GPU diagnostics GPTQ
lowers the mean reply-token KL by 40 to 64 percent for the recipes tested (Qwen3.5's is the
mixed recipe). That is a distribution result on fp32 replies, not a phone accuracy result,
and it does not by itself establish "no loss". The much larger effect of sections 3 and 6
is at the first token after the prompt, where the decision to call is made and where round
to nearest is fragile. Both belong in the report, each with its width.

### Context length, and the machine that exports

The same codes exported at 2k, 8k and 32k on one machine give the same probability on all
16 probe rows to four decimals (`quantlab/pte/lfm_gptq2_2k_podexport.json`, `_8k`, `_32k`):
the exported window does not touch the decision, as the 2026-09-07 window matrix found for
the older recipe. The same recipe exported on the Mac and on an x86-64 box differs by up to
0.129 on a row with the same decision on every row. The two files differ in 16,930 bytes of
795,686,528, all in the int8 token embedding: torchao's `hqq_scale_only` search gives the
embedding different low bits on Apple silicon and on x86-64 (it gives the int4 head the same
bits on both, and its `affine` algorithm is identical on both for both). Two consequences:
a single row's probability carries about 0.1 of noise from last-bit differences, so nothing
here rests on a single row; and an export is byte-reproducible only on the machine family
that made it unless the embedding is quantised deterministically
(`export.py --affine-embedding`).

On the Poco, held out, one tool, the same pair at both windows
(`quantlab/decisions_poco_s9_one_tool.md`): GPTQ v2 reads 44 percent recall, 41 and 43
percent correct, 62 and 70 percent known at 2k and 32k; round to nearest reads 10 percent
recall, 30 and 28 correct, 25 and 21 fabricated. The recall counts are the same at both
windows for both files (28 of 63 and 6 of 63), though the rows underneath flip in both
directions and cancel: three questions each way for the solved file and one each way for
round to nearest, with two more of the solved file's unneeded searches appearing at 32k. The
other displayed counts move by one to five rows, inside the width one file shows across runs. The 2k file is
the Mac's export and the 32k file the pod's, so in this one comparison the window and the
export machine together produce no net shift, with the two causes confounded.

A sixteen-tool prompt does not fit a 2,048-token window: every row of that arm on the 2k
export failed at prefill, which is a property of the window and is filed as void. The
several-tools comparison ran on the 32k exports, on the Poco, held out, one run each
(`decisions/poco-s9-decisions-*-driven-full.jsonl`):

| Held-out, the app's sixteen tools, Poco X8 Pro Max | Searched when needed (63) | Any call when needed (63) | Searched when not (56) | Correct (112) | Correct, known (56) | Fabricated | Abstained |
|---|---|---|---|---|---|---|---|
| 8da4w round to nearest, 32k window, state cleared by the guard, charger | 60% (38) | 62% (39) | 48% (27) | 40% (45) | 61% (34) | 2% (3/141) | 10% (14/141) |
| 8da4w GPTQ v2, 32k window, charger | 75% (47) | 86% (54) | 55% (31) | 46% (52) | 66% (37) | 1% (2/141) | 4% (5/141) |
| Q4_K_M GGUF, charger | 60% (38) | 68% (43) | 30% (17) | 35% (39) | 52% (29) | 1% (2/141) | 24% (34/141) |
| QAD Q4_0 GGUF, battery | 59% (37) | 65% (41) | 57% (32) | 37% (41) | 55% (31) | 0% | 17% (24/141) |

Two readings. First, with the full tool list the two GGUFs and the rounded export sit
together at 59 to 60 percent recall and 35 to 40 percent correct, and the solved export
stands apart at 75 and 46; Q4_K_M has the fewest unneeded searches and abstains on a
quarter of the questions, the QAD file on a sixth. Second, and the one that qualifies section 1: in this one
sixteen-tool run the round-to-nearest file does not refuse to call. It reaches 60 percent
recall and 2 percent fabricated searches where the one-tool prompt gave it 10 and 25, and
the solved file's lead shrinks to 15 points of recall, 6 of correctness and 5 of known
answers. That is not the same as being well behaved: its unneeded searches go from 4 percent
under one tool to 48 here, it trails the solved file by 24 points on making any call when
one is needed (62 against 86), it abstains on 10 percent of the questions against the solved
file's 4, and it leaks raw tool-call syntax into four answers against the solved file's one.
The solved file makes 4 more unneeded searches than it does, 31 against 27, which is 7
percentage points. Round to nearest trails the solve on recall,
correctness and known answers under both prompts, and the gap narrows under sixteen tools,
where a schema-heavy prompt raises tool calling for every file in the table. Under the
2,503-token full prompt round to nearest made some call on 39 of the 63 needed rows; under
the 577-token one-tool prompt it searched on 6 of 63. What these runs license is that the
near-total refusal to call is specific to the shorter prompt, not that the prompt explains
the loss, and a report should say both rather than either alone. Against the
one-tool arm, sixteen tools raise both needed and unneeded searches for every file, the
effect `routing-literature.md` describes for GGUF. The QAD arm ran on battery and the
other three on the charger, so timings are not compared across the table.

### The size of a prefill call

`ExecuTorchOnDeviceTest.prefillByPieceSize`, S25 Ultra, a prompt of 911 to 917 tokens (the
cut moves a token or two), cold, three runs a size: 286, 324, 337, 345 and 314 tokens per second at 200, 400, 800, 1,600 and 2,047
characters a call. The shipped 800 is 2.3 percent under the best (337 against 345). Within ExecuTorch the
piece size is not a large source of variation, so it stays as it is; this test does not by
itself say why ExecuTorch trails llama.cpp's Q4_0.

### Another backend

`export.py --backend vulkan` lowers the same solved codes in one partition, as
`et_vk.linear_dq8ca_q4gsw` for 8da4w and as `et_vk.linear_q4gsw` for a weight-only recipe
(`recipes/4w-g32.json`: the recipe's `"act": false`, which XNNPACK cannot run and Vulkan
can). `org.pytorch:executorch-android-vulkan` 1.4.0 is on Maven Central and
`-PexecutorchBackend=vulkan` builds the engine's test APK against it.

| Qwen3 1.7B, Galaxy S25 Ultra | BFCL of 30 | Prefill tok/s | Decode tok/s | Resident |
|---|---|---|---|---|
| XNNPACK, 8da4w round to nearest | 24 | 157 | 30.8 | 1893 MB |
| XNNPACK, 8da4w GPTQ | 27 | 124 | 26.8 | 1893 MB |
| Vulkan on the Adreno 830, weight-only int4, the same GPTQ codes | 27 | 336 | 35.7 | 247 MB |

Each row ran on a different Test Lab unit and the Vulkan row is weight-only where the
XNNPACK rows carry int8 activations, so the speeds are observations, not a controlled
comparison of delegates; the comparable result is the grade. One solve, two delegates with
different activation schemes, the same 27 of 30 on one run each, which says the codes
survive the second delegate, not that the two delegates are equivalent. Two things
went wrong first and both belong in the report. The state fix of section 2 was written as
`(input_pos > 0)`; Vulkan's ahead-of-time partitioner accepted the comparison and the 1.4.0
runtime aborted at load with "Missing operator: aten.gt.Scalar". It is now
`clamp(input_pos, max=1)`, the same 0 or 1 from an operator every delegate has: a graph
patch that is meant to travel has to be written in the smallest vocabulary. And LFM2.5 on
Vulkan, with that fixed, loads in 4.2 seconds and then segfaults inside the runtime at the
first prefill (`quantlab/logs/ftl-vulkan-4w-crash2.logcat`): ExecuTorch 1.4.0's Vulkan
delegate does not yet run the hybrid graph. That is upstream's, and it is why the hybrid
families stay on XNNPACK for now.

What carries to a backend and what does not, as measured rather than argued: the solver and
the codes carry (symmetric int4, groups of 32); the activation scheme is the recipe's
(`"act"`); what each delegate's runtime implements has to be tested on a device, because
lowering is not running. QNN and MediaTek quantise statically through pt2e with 16-bit
activations and were not attempted.

### Reviewed again

agy (`quantlab/agy-review-1.md`) found the unsolved head, that `recipe.inject` could leave a
linear at round to nearest without saying so, that a test question could reach calibration,
that the summary merged two phones under one prefix, and ten sentences of this note whose
numbers had no file behind them (each now has one, or says it was read by hand). Codex
(`quantlab/codex-qa2-review.md`) verified the head solve's placement on all four families
and found that the lab's gates ran with an fp32 embedding, that a decision run carried no
identity for its question file, and that the calibration exclusions failed open. All fixed;
the journal says what each changed.

## 9. Reviewed (Codex, gpt-5.6-luna, xhigh, four briefs)

1. *The fp32 gap.* Found the reused runner in `pte_run.py` independently, showed Hugging
   Face, the eager module with and without a KV cache and a freshly opened `.pte` agree to
   six decimals, and isolated the conv buffers as the state that matters. No model
   definition, conversion or kernel defect.
2. *The app.* Listed every flow that starts at position zero on a used module (a new chat,
   regenerate, edit and resend, a warm after a non-extension, a failed turn's retry, the
   compactor, every eval harness), confirmed the engine never rewinds to a positive
   position, classified the published measurements, reviewed the graph fix as export-safe,
   and wrote the reload guard and the metadata marker.
3. *The GPTQ pipeline, adversarially.* The solver's Cholesky use and block update are
   right. Two real defects: a group that crosses a block boundary took its scale from
   uncompensated weights (not reachable at group 32 and block 128, fixed anyway), and this
   note's first draft modelled ExecuTorch's bf16 scale storage as truncation when the 1.4
   exporter rounds to nearest (the truncating conversion in `XNNCompiler.cpp` only meets
   fp32 scales, which this exporter no longer writes). The solved scales were bf16-exact
   either way, so the shipped numbers stand; the helper and one table row were corrected.
   Its reading of the tilt is the one in section 3, and its acceptance gate (held-out KL,
   zero calls where fp32 is under 0.2, call-row mean within 0.05 of fp32, thresholds set
   before the run) is the bar the second solve is read against.
4. *This note, before it was committed.* Eight corrections, all taken: the speed paragraph
   had assembled one range from two phone units and called an eleven percent prefill
   deficit "level"; "level with both GGUFs" hid three IFEval points against Q4_K_M; the
   introduction said export alone fixes the state leak when published files need the app's
   guard; section 3's table mixed two quantisation paths without saying so, and they
   disagree on the all-int8 row; the bf16 paragraph read a modelled sweep as a measurement;
   the Llama paragraph claimed row-level faithfulness that aggregate counts cannot show;
   the bridge's limits were stated as facts rather than observations; and two claims (the
   units' temperatures, every export on the Hub) had no artifact behind them.

### The last read (Codex, brief six, `quantlab/codex-final2-review.md`)

Twenty-two places where the note said more than its files: "lose nothing" in the
introduction; a seed-7 table with no independence caveat; v1 foregrounded where v2 is the
reference; "a fifth of the distance" as if the two causes added; "every compiled number" as
if the fixed exports were included; "keeps every call" for a path that keeps 15; "function
calling is what round to nearest breaks first" as a law; the bf16 sweep read as a storage
effect; section 6 citing the fp32-embedding gate after the int8 one existed; "the same
model"; "rescues outright"; a stale sentence saying Qwen3.5 had no template; "noise" with no
repeat behind it; "level on every column" over a six-point column; a held-out table that hid
its two units; "nothing changes" over a change of 0.003; the every-token result worded as
quality; "within 2 percent" for 2.3; the Vulkan speeds as if matched; and a Quebec anecdote
promoted to a claim about knowledge. All reworded above. What it said a report still could
not get from this note: the several-tools result for the compiled model, a same-unit
round-to-nearest against GPTQ held-out pair, and a device run of Qwen3.5. The second and
third are in sections 8 and 7 now, and the first is the sixteen-tool table in section 8.

As of the 2026-09-18 staging snapshot, replacement exports existed but were not yet
published. Six repositories then carried exports made with the old recipe: the 1.2B base
at five windows and at 32k, the 2.6B in both forms, and the
two abliterated checkpoints (their download counts, 1,127 between them on 2026-09-18, come
from the Hub's API, not from this repository's files). Those six were exported before either
fix, so the state leak is in all of them by construction; only the 1.2B base has solved
codes, and the other three checkpoints are different weights that each need their own solve,
about fifteen GPU-minutes at the rate measured on the 4090, before their second cause is
fixed. A re-export alone would fix the state leak and leave round to nearest, which section
1 shows is the larger of the two losses under a one-tool prompt, so it is not worth doing by
itself.

For the 1.2B base the five windows are built and probed on the real files
(`quantlab/pte/windows/probe_*.json`, with `SHA256SUMS` beside them): identical to four
decimals on all sixteen prompts and the same greedy token at every window, the 2k file's
probe outputs identical on every row to those of the file that earned the phone numbers (the
two `.pte` files are not byte-identical, being separate exports), and
`get_state_reset_at_zero` read back from each. The staged tree also holds a card, a
`config.json` in the variants form and the checksums. On 2026-09-19 the maintainer supplied
a token and the five went to `alpharomercoma/LFM2.5-1.2B-Instruct-ExecuTorch-XNNPACK`, the
32k one to `experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch-XNNPACK-32k`, each with the
card and a `config.json` naming only the solved files. The Hub's recorded sha256 matches the
staged checksum on all six uploads. The old files were not deleted, so a client reading
`config.json` gets the fixed export while a direct link to an old filename still serves the
broken one; removing them is a separate decision. The 2.6B and the two abliterated
checkpoints, 565 of those downloads, still carried the old recipe at that snapshot.

Publishing did not settle the recommendation. In these historical runs, under one tool on
the Snapdragon the solved export matched Q4_K_M's correctness but made more unnecessary
searches and was seven points of recall behind QAD-Q4_0. Under one tool on the Dimensity it
was seven points behind Q4_K_M and twelve behind QAD; under sixteen tools on the Dimensity it
led Q4_K_M by fifteen points of recall and QAD by sixteen, and led both on correctness.
These comparisons apply to the recorded artifacts and prompts. A second-phone sixteen-tool
arm was still missing, and the later captured published GPTQ32k failures described at the
top of this note independently block approval. Historical recall gains cannot override
incorrect arithmetic, factual loss or false claims about tool outcomes.

Still not established by this research: a fix for the LFM2.5 Vulkan runtime crash, a
Qwen3.5 export that prefills at a usable speed, or general compiled-model quality approval.
The snapshot's pending 2.6B and abliterated solves and publication work must not be read as
current Hub inventory; the later external mirror audit records what was actually published.

## Sources

- Meta, Llama 3.2 1B SpinQuant model card, quantisation scheme and BFCL v2 table:
  https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8
- Frantar et al., GPTQ: https://arxiv.org/abs/2210.17323
- Liu et al., SpinQuant (W4A8 Llama 3 8B: RTN 65.3, GPTQ 64.6, SpinQuant 68.6 of 69.6): https://arxiv.org/abs/2405.16406
- KL as the sensitivity measure for hybrid models: https://arxiv.org/abs/2604.13440
- Quamba2, SSM input sensitivity: https://arxiv.org/abs/2503.22879
- ExecuTorch XNNPACK quantisation: https://docs.pytorch.org/executorch/stable/backends/xnnpack/xnnpack-quantization.html
- pytorch/executorch #3632 (8da4w-gptq), #9846 (pt2e 8da4w), #14221 (group 16), #22044 (int4 accuracy), #21858 (LFM2.5 runner)
- torchao QAT for this exact config: https://pytorch.org/blog/quantization-aware-training/
- Liquid's bundling tool defaulting to GGUF: https://docs.liquid.ai/deployment/tools/model-bundling/changelog
- Device Streaming API: https://docs.cloud.google.com/device-streaming/docs/overview
- ExecuTorch 1.5.0 release notes: https://github.com/pytorch/executorch/releases/tag/v1.5.0
