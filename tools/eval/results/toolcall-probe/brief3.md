# Adversarial QA: the ExecuTorch LFM2.5 quantisation iteration

You have NO tools and cannot open files; everything is in this brief. Do not restate it.
Attack the reasoning and the experimental design. For each numbered claim give a verdict
(SUPPORTED / NOT SUPPORTED / UNCERTAIN), the strongest objection, and the cheapest
experiment that would settle it. Then answer the three questions at the end.

## Background
An Android app runs LFM2.5-1.2B-Instruct (bf16 checkpoint) through llama.cpp (GGUF) and
ExecuTorch 1.4.0 XNNPACK (.pte exported with upstream `export_llm`, recipe `8da4w`: int4
weights in groups of 32 with HQQ scale-only, int8 per-token dynamic activations, int8
embeddings). Earlier today it was established, with greedy first-token probabilities of
the tool-call token on the phone's exact prompt bytes (16 rows, of which 9 carry the app's
"look it up with web_search" note), that the shipped export puts 0.00 to 0.09 on the
tool token where the GGUF Q4_K_M puts 0.66 to 0.87 and the fp32 transformers reference
0.62 to 0.94; that the fp32 ExecuTorch export itself trails the reference on some rows;
and that the loss "compounds across the body" (int4 on feed-forward only left 0.06 to
0.42, int4 on attention+conv only left 0.15 to 0.43, both together under 0.01). All
numbers are deterministic on the Mac (bit-identical on rerun). Upstream facts checked
today: XNNPACK has no fp-activation blockwise int4 kernel (int8 activations are the
precondition of the fast KleidiAI path); GPTQ has no code path; torchao QAT with the
exact config lowers through the normal 8da4w path; Liquid's own ExecuTorch bundles used
8da4w with an int8 output head and are now deprecated in favour of GGUF.

## This session's experiments (same 16 rows, same bytes, 2k exports, greedy)

### A. QAD weights
Liquid publishes a quantisation-aware Q4_0 GGUF (weights trained to sit on the Q4_0
grid: blocks of 32, one fp16 scale d = signed max / -8, value = d*(q-8)). It was
dequantised exactly back to bf16 (148 tensors, cosine 0.9945 to 1.0 vs the original bf16
weights, so no permutation) and exported with the same 8da4w recipe: tool token 0.22 to
0.71, 4 of 9 named rows call greedily (shipped: 0 of 9). Then the Q4_0 codes and scales
were written straight into torchao's int4 tensors (qdata int8 in [-8,7], scale fp32 per
group) so the grid is exact: XNNPACK refused to run it (forward error 0x1); 50.0% of the
32.4M groups have a negative scale and no group has a zero scale. Making scales positive
by negating codes clamps the -8 code to 7 in those groups (the block's largest weight
loses 1/8 of its magnitude): 0.15 to 0.48, 0 of 9. Conclusion drawn: an exact Q4_0 grid
is unreachable on XNNPACK (its qb4w format is unsigned nibbles with zero point 8 and a
positive scale, a -8..+7 grid; a negative d flips it to -7..+8), and the QAD source
helps but cannot reach parity.

### B. Activations vs weights, by module family (original bf16 checkpoint)
int8 per-channel weights (XNNPACK qd8 x qc8w) on every linear except one family at int4 g32:

| Export | Hanover | Rome | Columbia | Jerusalem | Canada's most popu | author: Empire? | author: Memory? | author: Responsibi | author: Skyscraper | calls |
|---|---|---|---|---|---|---|---|---|---|---|
| GGUF Q4_K_M | 0.84 | 0.87 | 0.79 | 0.66 | 0.82 | 0.85 | 0.74 | 0.83 | 0.77 | 9 of 9 |
| fp32 reference | 0.85 | 0.94 | 0.93 | 0.92 | 0.86 | 0.80 | 0.62 | 0.79 | 0.75 | 9 of 9 |
| fp32 .pte | 0.85 | 0.49 | 0.70 | 0.78 | 0.44 | 0.69 | 0.23 | 0.59 | 0.62 | 6 of 9 |
| 8da4w shipped | 0.00 | 0.01 | 0.00 | 0.01 | 0.01 | 0.04 | 0.05 | 0.09 | 0.05 | 0 of 9 |
| all int8 (8da8w) | 0.64 | 0.28 | 0.88 | 0.80 | 0.49 | 0.72 | 0.14 | 0.44 | 0.61 | 5 of 9 |
| int4 feed-forward only | 0.10 | 0.27 | 0.04 | 0.06 | 0.05 | 0.36 | 0.17 | 0.12 | 0.38 | 0 of 9 |
| int4 attention only | 0.87 | 0.91 | 0.77 | 0.80 | 0.82 | 0.89 | 0.58 | 0.72 | 0.49 | 8 of 9 |
| int4 conv only | 0.13 | 0.48 | 0.20 | 0.44 | 0.20 | 0.16 | 0.07 | 0.11 | 0.08 | 0 of 9 |
| 8da4w from QAD (HQQ) | 0.67 | 0.27 | 0.71 | 0.62 | 0.63 | 0.32 | 0.22 | 0.44 | 0.33 | 4 of 9 |
| QAD codes, scales made positive | 0.41 | 0.18 | 0.48 | 0.33 | 0.32 | 0.17 | 0.15 | 0.30 | 0.18 | 0 of 9 |


Conclusions drawn: (1) int8 activations plus int8 weights ("all int8") sit at the fp32
export's own level, so the activation quantisation costs little and the int4 weights
cost the rest; (2) int4 on the attention projections alone is harmless (8 of 9, level
with the GGUF); int4 on the feed-forward alone or on the short-conv projections alone
is each fatal on its own; (3) therefore a mixed recipe, attention at int4 and
feed-forward plus conv at int8 per channel, is the candidate, at roughly 1.15 GB against
the shipped 810 MB and the GGUF's 730 MB; (4) the fp32 ExecuTorch export's own gap to
the reference (Rome 0.49 vs 0.94, Memory 0.23 vs 0.62) is now the binding limit for
parity and is unexplained; a portable-SDPA fp32 export is being tried to test the
custom SDPA op.

### C. In flight
A second split within feed-forward (w1+w3 vs w2) and conv (in_proj vs out_proj).

## Questions
1. Is conclusion B(2) credible given "int4 attention only" scores ABOVE the fp32 export
   and the all-int8 export on several rows? What would you check before believing it?
2. Is the "compounds across the body" story from the earlier note now wrong, or
   consistent with B? State precisely what the earlier two-way split and this four-way
   split together do and do not show.
3. What is the cheapest next experiment that decides whether the mixed recipe is worth
   a phone run, and what acceptance threshold would you set (on these probabilities, on
   the 160-row suite, on speed and size) before recommending it over the GGUF?
Also flag anything that looks like a hard-coded or model-specific shortcut in the
bisection method itself (a regex-driven per-module quantiser patch in the venv's
quantize.py, env-gated), and whether the 9-row named subset is enough to steer this.
