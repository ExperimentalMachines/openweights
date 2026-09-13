### Evaluation of Numbered Claims

---

#### **Claim A1: "An exact Q4_0 grid is unreachable on XNNPACK because its `qb4w` format requires unsigned nibbles with zero point 8 and a strictly positive scale (a $[-8, 7]$ grid; flipping negative $d$ shifts the range to $[-7, 8]$)."**

* **Verdict:** **SUPPORTED**
* **Strongest Objection:** The grid representation is technically symmetric in magnitude if re-centered, but XNNPACK’s packed micro-kernel layout expects asymmetric affine unsigned nibbles: $w = (q - \text{zp}) \cdot s$ with $\text{zp}=8, q \in [0, 15] \implies (q - 8) \in [-8, 7]$. When $d < 0$ in GGUF ($w = d \cdot (q_{\text{gguf}} - 8)$ where $(q_{\text{gguf}} - 8) \in [-8, 7]$), multiplying by $-1$ forces $q' = 16 - q_{\text{gguf}}$, which maps $-8$ to $+8$. Because $8 \notin [-8, 7]$, clamping to $+7$ truncates the extreme negative value by $12.5\%$ ($1/8$). However, claiming it is permanently "unreachable" ignores that one could emulate it with an explicit per-channel bias offset or by re-exporting with a non-zero-point-8 affine packing if torchao/XNNPACK exposed asymmetric zero-points (which `qb4w` fast micro-kernels currently do not support).
* **Cheapest Experiment:** Compute the weight Frobenius error $\|W_{\text{clamped}} - W_{\text{orig}}\|_F$ vs $\|W_{\text{HQQ}} - W_{\text{orig}}\|_F$ across all QAD layers. If clamped QAD has equal or higher reconstruction error than standard HQQ, the representation incompatibility is mathematically confirmed without running any forward passes.

---

#### **Claim A2: "The QAD source helps but cannot reach parity."**

* **Verdict:** **UNCERTAIN**
* **Strongest Objection:** You tested QAD dequantized to bf16 and re-quantized via **HQQ scale-only** (which broke at 4/9 calls) and via **clamped direct Q4_0 codes** (which broke at 0/9 calls due to truncation). You did *not* test QAD with a true round-to-nearest (RTN) projection onto XNNPACK’s native $[-8, 7]$ grid with strictly positive scale: $s = \max(|w|) / 8$. HQQ searches for scale/zero-point via an optimization loop that assumes continuous weights; running HQQ optimization on top of already-stepped QAD-quantized weights creates severe grid-beating artifacts and double-quantization noise.
* **Cheapest Experiment:** Take the QAD weights, compute standard per-block RTN onto XNNPACK’s exact positive-scale unsigned grid ($s = \max(|w|) / 7.5$ or $\max(|w|) / 8$, $q = \text{round}(w/s) + 8$), and evaluate tool token probabilities.

---

#### **Claim B(1): "Int8 activations plus int8 weights ('all int8') sit at the fp32 export's own level, so activation quantisation costs little and int4 weights cost the rest."**

* **Verdict:** **NOT SUPPORTED**
* **Strongest Objection:** Confusing marginal degradation with independent additive degradation. On Rome (fp32 `.pte`: 0.49 $\to$ all-int8: 0.28) and Author: Memory (fp32 `.pte`: 0.23 $\to$ all-int8: 0.14), int8 dynamic activation + weight quantization cuts the remaining probability roughly in half (Rome loses 43% of its probability mass). Furthermore, evaluating first-token greedy argmax masks catastrophic activation distribution skew: dynamic activation quantization (`qd8`) calculates token-wise scales that are vulnerable to single-token channel outliers in attention heads.
* **Cheapest Experiment:** Run **fp32 weights with int8 dynamic activations** (`8da_fp32w` / dynamic quant on activations only). If probabilities match the fp32 `.pte`, activations are exonerated; if Rome drops from 0.49 toward 0.28, activations are active contributors to the failure.

---

#### **Claim B(2): "Int4 on the attention projections alone is harmless (8 of 9, level with the GGUF); int4 on the feed-forward alone or on the short-conv projections alone is each fatal on its own."**

* **Verdict:** **NOT SUPPORTED**
* **Strongest Objection:** In LFM2.5 (a hybrid Conv-Attention architecture), the attention layers account for a minority of the parameter count and computation budget compared to the feed-forward network (FFN) and 1D depthwise/short-convolutions. Attention projections exhibiting high tolerance might simply reflect lower parameter sensitivity or redundancy, while the "8 of 9" result suffers from severe variance: on Rome (0.91) and Jerusalem (0.80), "int4 attention only" yields *higher* confidence than the unquantized fp32 `.pte` (0.49 and 0.78). When a lower-precision model beats an unquantized baseline on the target token, the network is experiencing confidence miscalibration/drift, not "harmlessness."
* **Cheapest Experiment:** Check logits/KL divergence: measure the top-5 token distribution and entropy across all 16 prompt rows between fp32 `.pte` and "int4 attention only". If entropy drops drastically and probability shifts arbitrarily across non-target tokens, the apparent boost on tool tokens is an artifact of calibration collapse.

---

#### **Claim B(3): "Therefore a mixed recipe, attention at int4 and feed-forward plus conv at int8 per channel, is the candidate, at roughly 1.15 GB against the shipped 810 MB and the GGUF's 730 MB."**

* **Verdict:** **NOT SUPPORTED**
* **Strongest Objection:** The sizing and memory math undermines the entire deployment goal on mobile. Attention projections in LFM2.5 constitute only $\approx 20\text{--}25\%$ of total weight volume. Keeping feed-forward and conv at int8 produces a model ($1.15\text{ GB}$) that is $57\%$ larger than GGUF ($730\text{ MB}$) and $42\%$ larger than the target envelope ($810\text{ MB}$). Furthermore, mixed-precision dispatch in XNNPACK creates memory fragmentation and kernel switching overhead across GEMM blocks. If the target is beating GGUF on Android, shipping a 1.15 GB artifact that still trails GGUF in precision-density is dead on arrival.
* **Cheapest Experiment:** Measure peak RSS and memory bandwidth on desktop/Mac: run one forward step of the mixed-recipe `.pte` versus the GGUF Q4_K_M in llama.cpp to confirm working set size and latency.

---

#### **Claim B(4): "The fp32 ExecuTorch export's own gap to the reference (Rome 0.49 vs 0.94, Memory 0.23 vs 0.62) is now the binding limit for parity and is unexplained; a portable-SDPA fp32 export is being tried to test the custom SDPA op."**

* **Verdict:** **SUPPORTED**
* **Strongest Objection:** Blaming custom SDPA assumes the error is located in the attention core. However, LFM2.5 contains short/depthwise convolutions, RMSNorm/LayerNorm formulations, and custom rotary/position embeddings that could suffer from PyTorch $\to$ TorchScript/Edge dialect numerical drift or casting issues (e.g., intermediate downcasts to float16 or accumulation order differences in XNNPACK's fp32 micro-kernels vs reference PyTorch eager fp32).
* **Cheapest Experiment:** Layer-by-layer cosine/MSE inspection: hook hidden states at every transformer block output in PyTorch eager fp32 reference vs the ExecuTorch fp32 export using a single static prompt (e.g., Rome). Find the exact layer index where relative $L_2$ error diverges $> 10^{-4}$.

---

### Questions

#### 1. Is conclusion B(2) credible given "int4 attention only" scores ABOVE the fp32 export and the all-int8 export on several rows? What would you check before believing it?

* **Credibility:** **Not credible.** In deterministic evaluation, when quantizing a sub-module increases the target token probability beyond the unquantized float baseline (e.g., Rome jumping from 0.49 in fp32 `.pte` to 0.91 in int4-attention, and Author: Memory jumping from 0.23 to 0.58), it indicates **severe temperature/logit scaling distortion** or destructive suppression of competing tokens (e.g., normal text continuation tokens).
* **What to check:**
  1. **Logit Shift and Margin:** Inspect raw logits before softmax for both the target tool token and the top-3 competing tokens. Check whether the target logit increased or whether the denominator shrank because all alternative token logits were severely suppressed.
  2. **Top-1 / Greedy Path Degradation:** Test generation beyond the first token on the remaining 7 non-tool prompts. If the model suffers from over-confidence, it will likely hallucinate tool calls when plain text output was expected (false positive rate).
  3. **Rotary / Projection Saturation:** Check if the $W_q, W_k, W_v, W_o$ scales saturated, altering the effective dot-product temperature $\sqrt{d_k}$ inside attention.

---

#### 2. Is the "compounds across the body" story from the earlier note now wrong, or consistent with B? State precisely what the earlier two-way split and this four-way split together do and do not show.

* **Consistency:** **Consistent, but mischaracterized.** The earlier note claimed loss compounds monotonically across the body. Table B shows that catastrophic failure is not uniform: it is concentrated almost entirely within the non-attention blocks (FFN and short-conv).
* **What the two-way split showed:**
  * Attention + Conv at int4 produced $0.15\text{--}0.43$.
  * Feed-forward at int4 produced $0.06\text{--}0.42$.
  * Both together produced $<0.01$.
  * *What it showed:* Neither isolated half explained the complete drop to zero on its own; both contributed damage.
* **What the four-way split shows:**
  * When Attention and Conv are separated, Attention at int4 causes virtually no loss on tool selection (8/9), whereas Conv at int4 drops the suite to 0/9 ($0.07\text{--}0.48$).
  * FFN at int4 independently drops the suite to 0/9 ($0.04\text{--}0.38$).
* **What they do NOT show:**
  * They do **not** show that Attention is invariant to quantization in full-text generation (only evaluated at first-token tool call probability).
  * They do **not** isolate cross-layer interactions between FFN and Conv: because both FFN and Conv independently crash the model to 0/9, it is unknown whether quantizing *both* Conv and FFN at int8 while Attention is int4 avoids the non-linear collapse when all tokens interact over multi-turn generation.

---

#### 3. What is the cheapest next experiment that decides whether the mixed recipe is worth a phone run, and what acceptance threshold would you set?

* **Cheapest Next Experiment:**
  Export the exact candidate mixed `.pte` (**int4 g32 Attention, int8 qc8w Conv + FFN**) on Mac and run the full deterministic **16-row prompt suite** (measuring both the 9 tool-call prompts and the 7 negative/conversational prompts) plus **perplexity/loss on 128 tokens of WikiText or C4**.
* **Acceptance Thresholds:**
  1. **Probabilities (First-Token):** $\ge 8/9$ tool-calling rows greedily trigger the tool token; minimum tool probability across all 9 rows $\ge 0.50$; false positive rate on the 7 non-tool rows $= 0$ (must not emit tool token).
  2. **160-Row Suite:** $\ge 90\%$ concordance with fp32 reference on tool-call decisions.
  3. **Size:** $\le 950\text{ MB}$ uncompressed `.pte`. If the mixed recipe is $1.15\text{ GB}$, it must be rejected immediately against the $730\text{ MB}$ GGUF Q4_K_M unless it achieves a $\ge 2.0\times$ time-to-first-token speedup.
  4. **Speed (Pre-Phone):** Benchmark XNNPACK single-thread Mac CPU latency. If mixed int4/int8 execution latency is $\ge 85\%$ of pure `8da8w` latency, the int4 attention compression yields zero memory-bandwidth dividend due to dequantization overhead.

---

### Methodological & Engineering Flaws Flagged

1. **Regex-driven Per-Module Quantizer Patch in venv (`quantize.py`):**
   * *Fragility:* Regex matching on module names (e.g., matching `.*attn.*` vs `.*mlp.*` or `.*conv.*`) during `torchao` export frequently misidentifies projection layers (such as gating projections `w1/w3`, cross-layer norms, or input/output projection embeddings).
   * *State Contamination:* Modifying the venv's source code in-place with environment-variable flags bypasses Torch Dynamo graph tracing guarantees, invalidates clean build isolation, and creates unreproducible `.pte` artifacts that cannot be replicated in a clean CI/CD pipeline.
   * *Proper Pattern:* Use torchao's filter functions (e.g., pass a deterministic callable `filter_fn(module, fqn)` to `quantize_`) checked directly into the export repo.

2. **Steering via the 9-Row Named Subset:**
   * *Dangerously Underspecified:* The 9 rows test only a single semantic behavior: the decision boundary for emitting a tool invocation token on a primed prompt ("look it up with web_search").
   * *Degeneracy Blindspot:* A broken model whose weights are shifted to uniformly bias toward tool calling will score a perfect 9/9 on this subset while failing catastrophically on regular queries (hallucinating search on every turn). Testing without negative controls (prompts where tool calling is strictly forbidden) renders the entire optimization loop vulnerable to severe reward hacking.
