### Verdicts on Conclusions

---

#### **C1: The loss is concentrated: int4 on feed-forward kills the call; int4 on attention is harmless; int4 on conv in_proj costs one row.**
* **Verdict:** **SUPPORTED**
* **Strongest Objection:** In the first table, "int4 attention only" yields 8/9 calls, outperforming both "all int8" (5/9) and "fp32 .pte" (6/9). Quantization noise on attention projections may accidentally act as an uncalibrated logit booster/regularizer on tool tokens rather than being genuinely "harmless" (i.e. we have not measured perplexity or non-tool generation coherence).
* **Cheapest Settling Experiment:** Compare pre-softmax logits (not probabilities) for the tool token vs. top-5 alternatives on `Rome` and `Hanover` across bf16, all-int8, and int4-attention to verify whether attention int4 is preserving representational geometry or just drifting margin.

---

#### **C2: No int4-dominant recipe calls tools; every calling recipe keeps feed-forward at int8 (2/3 of weights), forcing files to 1.1–1.2 GB.**
* **Verdict:** **SUPPORTED** (within PTQ parameter space tested)
* **Strongest Objection:** The parameter sweep only evaluated group-size 32 standard uniform affine/symmetric PTQ (`qd8 x qc4w`). It did not test non-uniform / outlier-preserving quantization (e.g. keeping top 1% outlier channels/activations in int8/fp16 while quantizing the rest of FFN to int4, or smoothquant-style channel rescaling).
* **Cheapest Settling Experiment:** Run a quick scale check on FFN `w1/w3` activation and weight distributions in Python; if dynamic range is driven by sparse channel outliers, test whether channel-wise scaling allows FFN int4 without QAT.

---

#### **C3: Best recipe matches GGUF on 33 of 33 rows; 1.75x size trade-off is a product decision pending phone benchmarks.**
* **Verdict:** **UNCERTAIN**
* **Strongest Objection:** "Matches GGUF on 33 of 33 rows" claims parity on unmeasured ground truth. On the 9-row set, int4-attention got 8/9 calls while GGUF got 9/9 (falling down on `author: Skyscr` at $p=0.49$). Furthermore, greedy $p(\text{tool})$ on 40 curated/templated prompt prefixes does not guarantee multi-token decode stability, JSON schema validity, or absence of degradation on standard text prompts.
* **Cheapest Settling Experiment:** Run greedy generation (max 64 tokens) on the 16 original rows to verify that the tool call actually completes valid JSON syntax and halts properly, rather than degenerating post-first-token.

---

#### **C4: The only route to a smaller file that still calls is QAT on this mixed layout; needs a GPU.**
* **Verdict:** **NOT SUPPORTED**
* **Strongest Objection:** It assumes QAT is the *only* intermediate step between standard uniform PTQ and a working sub-800MB model. Standard 2024–2025 PTQ toolkits routinely rescue FFN int4 collapse without full backprop retraining:
  1. **AWQ / SmoothQuant:** scales down activation outlier channels into weight channels.
  2. **GPTQ / HQQ:** second-order Hessian error compensation during round-off.
  Both run on CPU/Mac in a few minutes without full QAT infrastructure or labeled training data.
* **Cheapest Settling Experiment:** Export FFN layers with AWQ/GPTQ (or static activation observers with act-order heuristics) into ExecuTorch before standing up a GPU QAT pipeline.

---

#### **C5: The fp32 ExecuTorch export's gap to reference is real, not SDPA, unexplained, and caps any recipe.**
* **Verdict:** **SUPPORTED**
* **Strongest Objection:** Calling it an absolute "cap" is empirically contradicted by the data: `int4 attention only` achieves higher tool probability on `Rome` (0.91 vs 0.49) and `Hanover` (0.87 vs 0.85) than `fp32 .pte`. However, the underlying premise—that the fp32 export diverges from the PyTorch bf16 reference upstream of quantization—is real and concerning.
* **Cheapest Settling Experiment:** Run layer-by-layer cosine similarity / max absolute difference between PyTorch native eager fp32 and `fp32 .pte` outputs on a single prompt to isolate exactly which module (RoPE frequency precision, RMSNorm epsilon casting, or causal mask addition) causes the numerical drift.

---

### Method Validity for Product Decision

**Is the evening's method fit to steer a product decision?**

**NO.** It is a solid, disciplined triage for isolating the sensitivity of individual layer families under uniform PTQ, but it is **not yet fit to steer a product packaging decision** for three reasons:
1. **Single-token surrogate metric:** $p(\text{tool})$ at step 0 measures prompt-representation alignment, not task completion. It does not measure whether the model emits valid arguments, obeys schema, or hallucinations mid-generation.
2. **Unaddressed baseline defect (C5):** Shipping a 1.2 GB model to work around an unexplained divergence between ExecuTorch fp32 and PyTorch reference risks baking an export-pipeline bug (e.g. RoPE, norm casting, or KV cache layout) into the production binary.
3. **Missing intermediate PTQ steps:** Jumping from naive RTN (round-to-nearest) to "QAT on GPU" or "ship 1.2 GB" skips zero-training data-aware PTQ (AWQ/GPTQ) that GGUF typically leverages via optimal quantization matrices.

---

### What to Run First Tomorrow

1. **Step 1 (Fix the Foundation - 30 mins):** Layer-by-layer diff between native PyTorch fp32 and `fp32 .pte` on the `Rome` prompt.
   * Check specifically: RoPE frequency calculation dtype (`float32` vs cast to half/double) and RMSNorm denominator epsilon placement.
2. **Step 2 (Sanity Check Generation - 15 mins):** Take `int4 attention only` and let it decode 32 tokens on the 9 calling prompts to confirm it produces valid tool invocations rather than garbage tokens following the first token.
3. **Step 3 (Rescue FFN without GPU - 1 hour):** Profile weight/activation magnitude kurtosis across FFN layers (`w1`, `w2`, `w3`) to see if an AWQ/SmoothQuant scale pass on the FFN projections recovers the 789 MB 8da4w target on CPU.
