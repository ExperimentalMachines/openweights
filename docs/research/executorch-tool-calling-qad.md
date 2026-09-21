# An ExecuTorch export of LFM2.5 from the quantisation-aware weights

2026-09-13, evening. Follow-up to `executorch-tool-calling.md`, which found that the
8da4w recipe erases the compiled LFM2.5 1.2B's tool calls and named no knob. The next
step that note owed was an export from a quantisation-aware checkpoint or a calibrated
recipe. This is that experiment, same probe, same sixteen rows, same bytes.

## What the recipe does to the weights

The checkpoint is bfloat16 (`config.json`, every tensor in `model.safetensors`). The
export first casts it to fp32 (`dtype_override: fp32`), then `8da4w` turns every linear
weight into int4 in groups of 32 along the input dimension with one scale per group
(symmetric, scale chosen by HQQ's scale-only search), and quantises the activations
entering each linear to int8 per token at run time. The embedding table goes to int8
per row. Round-to-nearest, no calibration data, no training. llama.cpp's Q4_K_M is also
4-bit weights in blocks of 32, but each block carries a scale and a minimum in 6 bits,
a third of the attention and feed-forward tensors and the tied head stay at 6 bits, and
activations are never quantised. Liquid's QAD Q4_0 is a checkpoint trained so that its
weights sit well on the Q4_0 grid: blocks of 32, one scale, no minimum, which is the
same granularity torchao's recipe uses.

## Calibrated recipes in ExecuTorch 1.4.0 and 1.4.1

`8da4w-gptq` is accepted by the export config and refused by the quantiser
("Unrecognized quantize mode: 8da4w-gptq"); the calibration arguments have nothing to
drive. 1.4.1 (2026-08-14) changes a PReLU fix and wheel paths and nothing in this path.
Liquid publishes no QAD safetensors, only the Q4_0 GGUF and an MLX 4-bit; the MLX path
refuses the XNNPACK delegate.

## Recovering the QAD weights

Q4_0 dequantises exactly (`scale x q`), so `qad_to_hf.py` reads every tensor of
`LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf`, maps the GGUF names back to the Hugging Face
names, and writes a bf16 `model.safetensors` beside a copy of the original config and
tokenizer. All 148 tensors map; cosine against the original bf16 weights is 0.9945 to
1.0, which rules out a permutation. `export_qad.sh` converts and exports it with the
shipped recipe; `export_qad_affine.sh` does the same with torchao's plain affine scale
instead of HQQ, since Q4_0's own scale is the block's signed maximum over -8 and neither
matches it exactly.

## Result

| Question | GGUF Q4_K_M | GGUF QAD Q4_0 | fp32 reference | 8da4w from bf16 (shipped) | 8da4w from QAD, HQQ scales | 8da4w from QAD, affine scales |
|---|---|---|---|---|---|---|
| What is Hanover the capital of? | 0.84 | 0.89 | 0.85 | 0.004 | 0.67 | 0.80 |
| What is Rome the capital of? | 0.87 | 0.94 | 0.94 | 0.007 | 0.27 | 0.17 |
| What is Columbia the capital of? | 0.79 | 0.95 | 0.93 | 0.000 | 0.71 | 0.39 |
| What is Jerusalem the capital of? | 0.66 | 0.95 | 0.92 | 0.006 | 0.62 | 0.33 |
| What is Canada's most populous province? | 0.82 | 0.96 | 0.86 | 0.010 | 0.63 | 0.72 |
| Who is the author of Empire? | 0.85 | 0.86 | 0.80 | 0.043 | 0.32 | 0.44 |
| Who is the author of Memory? | 0.74 | 0.79 | 0.62 | 0.046 | 0.22 | 0.58 |
| Who is the author of Responsibility? | 0.83 | 0.87 | 0.79 | 0.089 | 0.44 | 0.54 |
| Who is the author of Skyscraper? | 0.77 | 0.84 | 0.75 | 0.050 | 0.33 | 0.52 |
| What is the most recent country that President Donald Trump visited during his second presidency? | 0.92 | 0.68 | 0.66 | 0.110 | 0.37 | 0.21 |

Greedy calls on the nine named rows: GGUF Q4_K_M 9, shipped export 0, QAD-derived
export 4 with HQQ scales and 5 with affine scales. The QAD source moves the export
from 0.00 to 0.09 up to 0.2 to 0.8: half the calls come back, none of the rows reach the
GGUF, and the two scale choices trade rows with each other at the noise level of a
sixteen-row probe. The remaining gap is what QAD was not trained for: the int8 dynamic
activation quantisation, and a scale that is not Q4_0's. A recipe that quantised the
weights exactly onto the Q4_0 grid and left activations in fp32 does not exist in
ExecuTorch 1.4.x's XNNPACK path (weight-only `4w` exports a degenerate model, the
`torchao:` modes refuse the delegate).


## What upstream says (2026-09-13, web sweep and a read of `main`)

**The wall, checked.** Three walls were named; two are real and one was a misreading.

- *Weight-only blockwise int4 on XNNPACK does not exist at the kernel level.* XNNPACK's
  fully-connected subgraph accepts blockwise int4 (`qbint4`) only from dynamically
  quantised int8 activations (`qd8`, `qdu8`, `qp8`); an fp32 input with blockwise int4 is
  `XNN_UNREACHABLE`. ExecuTorch's partitioner mirrors that, and its docs say "Weight-only
  quantization is not currently supported on XNNPACK". The KleidiAI kernels behind the
  2 to 3x prefill are all `qai8dxp x qsi4c32p`: the int8 activation is the precondition of
  the fast path, not a choice on top of it. Our `4w` export ran unpartitioned through
  portable int4 dequantisation, which nobody tests; its degenerate output says nothing
  about int4 quality. Nothing on `main` after 1.4.0 changes this.
- *Calibrated int4 on XNNPACK does not exist.* `8da4w-gptq` has had no code path since
  May 2024 (pytorch/executorch #3632, open); the pt2e `XNNPACKQuantizer` is int8 only
  (#9846, open since April 2025).
- *QAT does exist and lowers.* torchao's `QATConfig` accepts the exact config the export
  uses, `Int8DynamicActivationIntxWeightConfig(int4, PerGroup)`, since ao #3001
  (September 2025); a converted model has the plain 8da4w structure and takes the same
  XNNPACK path. Meta's own 1B and 3B exports ship this way because "the 1B/3B models are
  sensitive to accuracy loss when regular post-training quantization is applied". The
  `use_qat` flag in `export_llm` is not that path; it only loads Meta's pre-quantised
  checkpoint format, which is why it looked closed. torchao 0.18 in our venv has the API.

**What others do.** Liquid's own ExecuTorch bundles were `8da4w_output_8da8w`: the head at
8 bits. We already measured the head at fp32 and it changed nothing, so that knob is
spent here. Liquid's bundling tool now defaults to GGUF and calls ExecuTorch "deprecated
and may be removed"; it gives performance as the reason and publishes no accuracy for
the bundles. No public report of tool calling lost on an int4 ExecuTorch export exists;
pytorch/executorch #22044 (Qwen2.5 0.5B wrong facts, SmolLM2 135M mojibake at int4) and
#21858 (an LFM2.5 8da4w export degenerate through the runner) are the nearest.

**Why the QAD-derived export recovers only half.** Q4_0 stores the block's signed
maximum over -8 as the scale; torchao's symmetric int4 chooses its own, so the
dequantised weights land off the grid they were trained for and pick up fresh rounding
noise. The exact fix is to build the int4 codes and scales straight from the GGUF
blocks (torchao's `QATConfig(step="convert")` honours a `custom_scale`), which nobody
has done yet. The int8 activation quantisation stays either way, and QAD never saw it.

**What could get an LFM2.5 export level with the GGUF, in order of cost.**

1. Re-quantise the QAD weights onto their own grid (custom scales from the Q4_0 blocks),
   one export, one probe. Removes the weight noise entirely; leaves the activations.
2. Bisect by module type rather than depth: everything at int8 except one family at
   int4 (attention, conv `in_proj`, conv `out_proj`, feed-forward), six exports, one
   afternoon. Says where the int4 loss sits and whether a mixed recipe is enough.
3. QAT with torchao on the exact recipe, activations included: a short distillation
   from the bf16 model on tool-calling-heavy chat data, then convert and export. This
   is what Liquid did for Q4_0 and what Meta does for 1B; it is the only path that
   trains the model for the int8 activations too. Needs a GPU for a few hours and a
   loader into `export_llm`.
4. A `torchao:fpa4w` build (fp32 activations, int4 weights, torchao kernels, no
   XNNPACK) as a diagnostic: if calls return, the activations were the loss. Slower
   and a custom runtime; not to ship.

Skip: GPTQ, pt2e calibration, SpinQuant, and waiting for weight-only int4 on XNNPACK.

## The iteration (same evening, moving fast)

Everything below is the same probe: greedy, first-token probability of the tool-call
token, the sixteen rows from `executorch-tool-calling.md` (nine carry the note), the
original bf16 checkpoint unless said otherwise, 2k exports. Reviewed by Gemini 3.8 Flash
between rounds (`review3-agy-gemini-3.8-flash.md`); what it changed is at the end.

### The exact Q4_0 grid is unreachable on XNNPACK

The QAD file's own codes and scales were written straight into torchao's int4 tensors
(`ow_q40.py`, a hook in the venv's `quantize.py` gated by `OW_Q40_GGUF`), so that no
re-rounding happened at all; the earlier HQQ re-quantisation had moved weights by up to
0.05. XNNPACK refused to run the result (forward error 0x1): half of the 32.4 million
groups carry a negative scale, because Q4_0 stores the block's signed maximum over -8
and XNNPACK's `qb4w` format is unsigned nibbles around a zero point of 8 with a positive
scale. Negating those blocks' codes puts their largest weight at +8, one step outside
the grid; clamping it to 7 costs that weight an eighth of its magnitude in half the
blocks, and the export lands at 0.15 to 0.48, worse than the HQQ re-quantisation (0.22
to 0.71). So the QAD weights cannot be used as trained on this backend: the two grids
are mirror images, and half the blocks fall on the wrong side.

### Where the int4 loss sits, by module family

A second hook (`OW_INT8_REGEX`) puts int8 per-channel weights (XNNPACK `qd8 x qc8w`,
which it does support) on every linear whose name matches, leaving the rest at int4
groups of 32. First every family alone at int4 with the others at int8, then, with
attention kept at int4, one projection group of the feed-forward or the conv at a time:

| Export | Hanover | Rome | Columbia | Jerusalem | Canada's most  | author: Empire | author: Memory | author: Respon | author: Skyscr | calls | size MB |
|---|---|---|---|---|---|---|---|---|---|---|---|
| GGUF Q4_K_M | 0.84 | 0.87 | 0.79 | 0.66 | 0.82 | 0.85 | 0.74 | 0.83 | 0.77 | 9 of 9 | 697 |
| fp32 .pte | 0.85 | 0.49 | 0.70 | 0.78 | 0.44 | 0.69 | 0.23 | 0.59 | 0.62 | 6 of 9 | 4978 |
| all int8 | 0.64 | 0.28 | 0.88 | 0.80 | 0.49 | 0.72 | 0.14 | 0.44 | 0.61 | 5 of 9 | 1248 |
| int4 attention only | 0.87 | 0.91 | 0.77 | 0.80 | 0.82 | 0.89 | 0.58 | 0.72 | 0.49 | 8 of 9 | 1222 |
| int4 attention + w1,w3 | 0.58 | 0.50 | 0.56 | 0.04 | 0.44 | 0.51 | 0.14 | 0.28 | 0.33 | 3 of 9 | 997 |
| int4 attention + w2 | 0.32 | 0.63 | 0.53 | 0.51 | 0.22 | 0.25 | 0.51 | 0.53 | 0.47 | 5 of 9 | 1110 |
| int4 attention + conv in_proj | 0.86 | 0.87 | 0.69 | 0.43 | 0.82 | 0.74 | 0.27 | 0.77 | 0.73 | 7 of 9 | 1169 |
| int4 attention + conv out_proj | 0.63 | 0.93 | 0.44 | 0.53 | 0.83 | 0.70 | 0.38 | 0.13 | 0.45 | 5 of 9 | 1204 |
| int4 feed-forward only | 0.10 | 0.27 | 0.04 | 0.06 | 0.05 | 0.36 | 0.17 | 0.12 | 0.38 | 0 of 9 | 911 |
| int4 conv only | 0.13 | 0.48 | 0.20 | 0.44 | 0.20 | 0.16 | 0.07 | 0.11 | 0.08 | 0 of 9 | 1178 |
| 8da4w shipped | 0.00 | 0.01 | 0.00 | 0.01 | 0.01 | 0.04 | 0.05 | 0.09 | 0.05 | 0 of 9 | 789 |

Three readings. First, int8 everywhere ("all int8": int8 activations, int8 weights)
sits at the fp32 export's level, so the activation rounding costs little and the int4
weights cost the rest. Second, the loss is not spread evenly, as the earlier two-way
split had suggested: int4 on the attention projections alone is harmless, and the same
recipe on the feed-forward alone or the short-conv projections alone is fatal on its
own. The earlier split had lumped attention with conv, and the conv was doing the
damage. Third, within those families the feed-forward is the sensitive one: int4 on
its gate and up projections, or on its down projection, drops calls even with
everything else at int8, while int4 on the conv input projections costs one row.

The attention-only reading replicates on the 24 fresh rows (seed 11, seven noted, drawn
by nobody), where it is again level with the GGUF and above the fp32 export:

| Export | producer of Black and  | director of The Last W | producer of Parker? | screenwriter for Open  | screenwriter for Last  | screenwriter for Bleak | calls (7 noted) | max p on 17 unnoted | false calls |
|---|---|---|---|---|---|---|---|---|---|
| GGUF Q4_K_M | 0.70 | 0.75 | 0.68 | 0.70 | 0.74 | 0.81 | 6 | 0.38 | 0 |
| fp32 reference | 0.66 | 0.69 | 0.58 | 0.60 | 0.67 | 0.76 | 6 | 0.12 | 0 |
| fp32 .pte | 0.45 | 0.56 | 0.40 | 0.46 | 0.51 | 0.57 | 3 | 0.05 | 0 |
| 8da4w shipped | 0.02 | 0.02 | 0.04 | 0.05 | 0.05 | 0.05 | 0 | 0.03 | 0 |
| all int8 | 0.26 | 0.35 | 0.54 | 0.68 | 0.45 | 0.51 | 3 | 0.03 | 0 |
| int4 attention only | 0.67 | 0.76 | 0.52 | 0.63 | 0.64 | 0.70 | 6 | 0.04 | 0 |

No export makes a false call on the seventeen unnoted rows (the largest tool-token
probability there is 0.04 for the attention-only export). Its top-1 mass on those rows
is 0.71 against the fp32 export's 0.79, a little less certain and not collapsed, which
answers the reviewer's worry that scoring above the unquantised export meant
miscalibration; why int4 attention scores above int8 attention on 33 rows out of 33 is
not explained.

### The fp32 export's own gap is not the custom SDPA

The fp32 export with the portable attention (`use_sdpa_with_kv_cache=False`) gives the
same numbers to three decimals (Hanover 0.852, Rome 0.491, Columbia 0.703, Jerusalem
0.779). The gap to the reference is somewhere else in the graph: the short conv, the
norms, the rotary embedding, or the fp32 kernels' accumulation order. Not found tonight.

### What this leaves

There is no int4-dominant ExecuTorch recipe for LFM2.5 that calls tools. Every export
that calls keeps the feed-forward at int8, and the feed-forward is two thirds of the
weights, so those files are 1.1 to 1.2 GB against the GGUF's 697 MB and the shipped
export's 789 MB. The best of them, attention at int4 with everything else at int8,
matches the GGUF on 33 of 33 rows measured; whether a file 1.75 times the GGUF's size
is worth its prefill speed is a product decision, and the reviewer's bar for it is
right: measure the phone (speed, memory, the 160-row suite, a held-out draw) before
recommending it, and set the thresholds first. The one recipe that could bring the size
back down is QAT with torchao on this exact mixed layout, which trains the feed-forward
to survive int4 and needs a GPU.

Timings from the Mac runner are not reported: the runs overlapped and the shipped file
is a 32k export, so the per-row times mean nothing.

### Reviewed (round three, Gemini 3.8 Flash)

Taken: the fresh-row replication and the calibration check above, the size table, the
"not the custom SDPA" test, the acceptance bar quoted in the last section, and the
honest statement that the int4-attention gain over int8 is unexplained. Not taken: its
suggestion of an fp32-weight, int8-activation export (no such XNNPACK kernel: dynamic
int8 activations exist only in front of quantised weights) and its reading of the
all-int8 row as "activations halve the probability" (0.49 to 0.28 on Rome and 0.23 to
0.14 on Memory are two rows; the other seven move both ways within 0.1). Its point that
the regex-driven, env-gated hook in the venv's `quantize.py` is not a reproducible
build is right: the hook is a bisection instrument and stays out of the app; a shipped
recipe would be a `filter_fn` in a checked-in export script.

### Reviewed (round four, Gemini 3.8 Flash, on the conclusions)

Verdicts: the concentration of the loss in the feed-forward, supported; no int4-dominant
recipe within plain round-to-nearest, supported; "matches the GGUF" for the mixed
recipe, uncertain, because a first-token probability is not a completed call; QAT as
the only route to a smaller file, not supported, because outlier-aware post-training
methods (activation-aware scaling of the feed-forward's input channels, second-order
rounding) run on a laptop without training data and were not tried; the fp32 export's
gap, supported and the first thing to find. Its verdict on the method: fit for
isolating which layers are sensitive, not fit on its own to steer a packaging decision,
for three reasons it names: a single-token surrogate, an unexplained baseline defect,
and the skipped intermediate methods.

Taken now: the completed-call check on the 28 greedy tokens the probe already recorded:

- int4 attention only: 9 of 9 named rows start a call, 9 of those are a complete well-formed web_search call within the 28 greedy tokens
- int4 attention + conv in_proj: 8 of 9 named rows start a call, 8 of those are a complete well-formed web_search call within the 28 greedy tokens
- all int8: 7 of 9 named rows start a call, 7 of those are a complete well-formed web_search call within the 28 greedy tokens

Left for tomorrow, in the order the reviewer put them and this note agrees with: a
layer-by-layer comparison of the fp32 export against eager fp32 on one prompt to find
the graph's own gap; an activation-aware channel scaling of the feed-forward before
int4, which needs only a forward pass over a few prompts and a fold of the scales into
the norm weights, and which is the one untried path to a file under 800 MB; then, if
that fails, QAT on the mixed layout. Nothing about the per-family hook belongs in the
app; the shipped recipe, if there is one, is a checked-in export script.

## On the phone, and the AWQ attempt (2026-09-13, late)

**Activation-aware scaling did not rescue int4 on the feed-forward.** `awq.py` folds
per-channel scales into the gate and up projections (through `ffn_norm`), the down
projection (through the rows of `w3`) and the conv input projections (through
`operator_norm`), from activation maxima over 64 seed-8 prompts; the fold is exact in
fp32 (Hanover 0.8521 before and after). Then the shipped recipe:

| AWQ variant, then the shipped 8da4w recipe | tool-token probability on the 9 named rows | greedy calls |
|---|---|---|
| all folds, alpha 0.5 | 0.06 to 0.31 | 0 of 9 |
| all folds, alpha 0.3 | 0.01 to 0.63 | 2 of 9 |
| all folds, alpha 0.7 | 0.01 to 0.30 | 0 of 9 |
| all folds, alpha 0.2 | 0.00 to 0.30 | 0 of 9 |
| gate, up and conv folds, no down fold, alpha 0.5 | 0.00 to 0.02 | 0 of 9 |
| gate and up folds only, alpha 0.5 | 0.00 to 0.03 | 0 of 9 |
| down fold only, alpha 0.3 | 0.03 to 0.12 | 0 of 9 |
| down fold only, alpha 0.2 | 0.00 to 0.10 | 0 of 9 |
| down and conv folds, alpha 0.3 | 0.03 to 0.41 | 0 of 9 |

The best variant lifts the tool token from under 0.09 to 0.2 to 0.6 on a few rows and
calls on two; the down-projection fold is what helps and only together with the others;
stronger scaling hurts. Outlier redistribution is not the mechanism, or not enough of
it. QAT is what is left for a file under 800 MB.

**The phone, all 160 rows, four artifacts** (`prod-decisions-*` in
`tools/eval/results/decisions/`, `phone_stats.py` for the speed columns):

| Artifact (Poco X8 Pro Max, 160 rows, driven-search, greedy, one session, on battery) | Searched when needed (75) | of the 42 noted rows | of the 54 unnoted rows that need one | Unnecessary (60) | Correct (117 non-stale) | Narrated a search with no call | Prefill tok/s | Decode tok/s | Time to first token | Resident memory | Size |
|---|---|---|---|---|---|---|---|---|---|---|---|
| shipped 8da4w export | 3% (2) | 0 | 2 | 0% | 34% | 22% | 285 | 36.7 | 2.1 s | 1.85 GB | 789 MB |
| int4-attention export (rest int8) | 32% (24) | 35 | 6 | 15% | 38% | 3% | 362 | 30.1 | 1.7 s | 2.33 GB | 1253 MB |
| QAD Q4_0 GGUF | 60% (45) | 42 | 24 | 23% | 39% | 1% | 227 | 35.7 | 2.4 s | 1.03 GB | 731 MB |
| Q4_K_M GGUF | 44% (33) | 41 | 13 | 25% | 40% | 0% | 157 | 31.1 | 3.6 s | 1.06 GB | 697 MB |

Read: the mixed export calls where the app's note tells it to (35 of 42) and rarely
on the model's own judgement (6 of 54 against the QAD file's 24), so it recovers most
of the note-driven recall and little of the rest; its narrated searches fall from 22
to 3 percent; it prefills 1.6 times faster than the QAD file and 2.3 times faster than
Q4_K_M, decodes about 15 percent slower, and holds 2.3 GB resident against the GGUFs'
1.0 GB, which on a 12 GB phone is the number that matters. The QAD Q4_0 GGUF is the
best artifact on every quality column and is 731 MB.

**Public benchmarks, same phone, same night** (`prod-*.bench.graded.json`):

| Public benchmarks, same phone, same night, 30 prompts each | GSM8K | IFEval | BFCL |
|---|---|---|---|
| shipped 8da4w export | 17 | 18 | 25 |
| int4-attention export | 19 | 21 | 27 |
| Q4_K_M GGUF, five-phone mean from September 10 | 19.6 | 21.6 | 26.0 |

On this phone the mixed export is two to three points of thirty above the shipped
export on every set and level with the GGUF's five-phone means. The shipped export
scores higher here than its own five-phone means (13.0, 15.6, 22.4), on a stronger
phone and through the fixed schema spelling; whether the spelling moved BFCL was still
not isolated.

**Decision.** Not shipped. The mixed export is the first compiled LFM2.5 that calls
tools on a phone and its benchmarks are level with the GGUF, but it recalls half of
what the QAD GGUF recalls, is 1.7 times its size and 2.3 times its resident memory, and
decodes slower; the one thing it wins is prefill. The QAD Q4_0 GGUF is the artifact to
recommend for LFM2.5, ahead of Q4_K_M, on this run. This was one phone, one session,
on battery, without the seed-8 pass; a second phone and the held-out draw are owed
before any of it is a number in a README.

## What this changes

Nothing ships from this. The mixed export is the first compiled LFM2.5 that calls a tool on
the phone's own prompt, it was measured on the Poco above (160 rows, one session), and it
is not level with either GGUF. The ask to upstream recorded here, a weight-only int4 path on
XNNPACK, turned out not to be needed.

**Superseded 2026-09-17** by `executorch-state-and-recipes.md`: every `.pte` row in this
note after a run's first prompt was taken with LFM2's convolution state leaking from the
previous prompt (the probe reused one runner, the phone harness one engine), which is also
the whole of the "fp32 export's own gap"; and the int4 loss is one of rounding, not of the
format. GPTQ codes on the same grid recover most calls at 795 MB in those later experiments,
not every call or every quality metric. These historical builds do not approve the later
published GPTQ32k artifact; its captured-failure approval review remains on hold.
