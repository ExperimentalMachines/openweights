### A. "Note, not runtime" reading of the 2026-09-10 gap

* **Evidence assessment: Not supported (overclaimed).** 
  Only 16 of 75 rows were measured, and only 10 were GGUF-calling rows. Crucially, the brief’s own data refutes the claim: Row 16 (*Trump presidency*) has **no note**, yet GGUF puts 0.92 on `<|tool_call_start|>` while 8da4w puts 0.11. GGUF was clearly capable of triggering search on temporal queries without prompt injection. Assuming all 33 GGUF calls in the 75-row suite were artifacts of `TurnRunner.naming` is unfounded.
* **What would disprove it:** 
  Finding rows among the remaining 23 GGUF-calling cases from 2026-09-10 that did not trigger `TurnRunner.naming` regex ("what is the X" / "who is X"), but where GGUF still called `web_search`.
* **What to measure next:** 
  Run all 75 original rows through both runtimes under two strict conditions: bare prompt vs. note-injected prompt (4 × 75 matrix). Tally exact trigger rates to separate the prompt-injection effect from baseline tool-calling sensitivity.

---

### B. Bisection concluding quantization (not ET graph/runtime) is the cause

* **Evidence assessment: Not supported.** 
  The unquantized `fp32 .pte` exhibits massive degradation on its own compared to the PyTorch reference: Rome drops from 0.94 to 0.49 (−48%), Canada from 0.86 to 0.44 (−49%), and Memory from 0.62 to 0.23 (−63%). The ET graph/kernel execution is losing up to half the decision margin before quantization touches a single weight. Furthermore, the bisection is incomplete: the FFN vs. attention splits are pending, weight-only int8 fails to trace, and 4w is degenerate.
* **What would disprove it:** 
  Demonstrating that fixing an FP32 numerical divergence in ExecuTorch (e.g., RoPE implementation, causal mask handling, or accumulation precision in XNNPACK) raises `fp32 .pte` to parity with PyTorch reference, and consequently lifts the 8da4w export above the decision threshold.
* **What to measure next:** 
  Compute layer-by-layer cosine similarity and maximum absolute error between PyTorch reference and `fp32 .pte` across the forward pass on the 578-token prompt. Check whether XNNPACK uses FP16 accumulation or altered softmax precision in attention.

---

### C. Risk of regressions from `canonicalJson` across model families

* **Evidence assessment: Partially supported, but introduces regression risks.**
  The whitespace fix helps LFM2.5, but hardcoding `", "` and `": "` risks regressing other architectures:
  1. **Out-of-distribution formatting:** Tokenizers for Qwen2.5, Llama 3.2, and SmolLM are sensitive to whitespace tokens. Jinja’s standard `tojson` filter in Python defaults to compact `separators=(',', ':')` without spaces in many pipelines. Forcing spaces creates tokenization mismatches for models trained on compact JSON.
  2. **Empty / non-standard inputs:** If `parametersJson` is empty string `""` or invalid, `canonicalJson` returns `""`, creating syntax errors (`"parameters": }}`).
  3. **Escaped backslashes:** In `canonicalJson`, an escaped backslash sequence followed by a quote (e.g. `\\\\\"`) toggles `escaped` unevenly because `escaped` is reset without checking if the current character is itself an escape escape.
* **What would disprove it:** 
  Tool-calling regressions on Qwen2.5, Llama 3.2, or Phi-4 unit tests when switching from their expected schema formatting to `canonicalJson`.
* **What to measure next:** 
  1. Tokenize schemas using each family's official Hugging Face tokenizer with both compact (`separators=(',', ':')`) and canonical (`separators=(', ', ': ')`) strings to check for token splitting.
  2. Run the tool-calling eval suite across all supported model families (Qwen, Phi, SmolLM, Llama) before and after this commit.

---

### D. Risks in the piece-cut change (`warmPiece`)

* **Evidence assessment: Supported, but creates new edge cases.**
  Cutting before the space prevents splitting `" not"` into `" "` and `"not"`, which is sound for Byte-level BPE. However:
  1. **Leading whitespace in subsequent pieces:** The space excluded from the end of piece $N$ becomes the leading character of piece $N+1$. While prefix spaces match BPE words, consecutive spaces (e.g. `"  "`) or indentation will cause piece $N+1$ to begin with spaces that may tokenize differently.
  2. **No-space windows:** In prompts with blocks lacking whitespace (URLs, base64 strings, long JSON keys, minified schemas), `space <= 0` and `newline <= 0`, causing it to cut arbitrarily at `most` (800 chars) and still split tokens.
  3. **Inconsistency with newlines:** Line breaks are still cut *after* (`newline + 1`). In Llama-3/LFM2.5 tokenizers, repeated newlines (`\n\n`) or newline-plus-indentation (`\n   `) are merged tokens. Cutting after `\n` splits these tokens just as cutting after space did.
* **What would disprove it:** 
  Showing token ID discrepancies between chunked prefill and whole-prompt prefill when the prompt contains repeated whitespace, long URLs, or multi-space indentation.
* **What to measure next:** 
  Run an automated fuzz test comparing `tokenizer.encode(whole_text)` vs. concatenation of `tokenizer.encode(piece)` across arbitrary texts containing code, Markdown tables, repeated spaces, and URLs.

---

### E. Product decision and required validation

* **The Decision:** 
  **Keep recommending GGUF in production. Do not ship a new ExecuTorch export for LFM2.5 yet.**
  * *Rationale:* The bisection is unfinished. Weight-only `4w` is degenerate, weight-only `int8` fails to trace, `torchao:` refuses XNNPACK, and 8da4w with FP32 head failed. ExecuTorch 1.4.0 currently has *no* working, performant alternative quantization recipe for this architecture.
* **Measurements required before shipping any future ET export:**
  1. **Full 75-row eval on physical Android hardware:** First-token probability $\ge 0.70$ and 0% narration hallucination ("Based on my search...") on greedy generation.
  2. **End-to-end execution:** Successful tool call JSON generation and recovery after tool result injection.
  3. **General degradation check:** Validate perplexity or standard task accuracy (e.g. MMLU / IFEval subset) to ensure quantization changes targeting tool tokens did not destroy general generation capabilities.
  4. **Performance parity:** Measure TTFT (time-to-first-token) and memory footprint on ARM64 against GGUF Q4_K_M.

---

### F. Claims in the research note stated more strongly than the evidence

1. **"The 2026-09-10 'GGUF 33 of 75, compiled 1 to 4 of 75' was therefore not the two runtimes deciding differently on a bare question. It was the same note, obeyed by one artifact and not the other."**
   * *Overstated:* Only 16 rows were evaluated. The brief's own data shows GGUF calls tool tokens with 0.92 probability on bare questions (Row 16) where 8da4w fails (0.11).
2. **"The ExecuTorch graph, the runtime, the C++ tokenizer and the app's feeding of it are therefore not the cause. The quantisation is."**
   * *Overstated:* Ignores that `fp32 .pte` suffers massive logit degradation (up to 50% loss on Rome and Memory) compared to reference FP32 without any quantization. The ET graph/runtime is contributing significantly to the margin loss.
3. **"The loss is in the body."**
   * *Overstated:* Only the tied LM head was tested in FP32. This does not rule out input embeddings (which were kept at int8), positional embedding calculations, or cumulative graph drift.
4. **"An ExecuTorch export quantised differently is measurable with the same scripts, and the table in section 3 says which knob to turn."**
   * *False:* Section 3 does not show which knob to turn; it shows placeholder rows ("see below"), failed tracing (`int8`), degenerate generation (`4w`), and delegate incompatibilities (`torchao:` vs XNNPACK).
