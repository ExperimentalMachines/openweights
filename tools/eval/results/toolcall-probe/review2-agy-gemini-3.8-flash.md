### Verdicts on the Maintainer's Questions

#### 1. Freedom from Bias: UNCERTAIN
* **Strongest objection:** The 24-row fresh sample confirms the symptom (the shipped 8da4w `.pte` never calls, 0/24), but it also demonstrates that the `fp32 .pte` export itself suffers a massive, uncharacterized degradation on non-noted prompts. In the 24-row table, for all 17 prompts without the named-subject note, the `fp32 .pte` probability for `<|tool_call_start|>` never exceeds 0.05 (matching 8da4w's suppression), whereas GGUF scores 0.19, 0.26, 0.30, 0.38. The investigation attributes the primary failure to quantization (`8da4w`), yet the benchmark rows that show 8da4w's drop from fp32 are almost exclusively the 7 prompts artificially boosted by `TurnRunner.naming` (where fp32 ranges from 0.40 to 0.57). By evaluating artifact quality primarily through the lens of an app-injected prompt heuristic rather than natural tool-calling distributions across the suite, the conclusion that "quantization is the dominant cause" over general ExecuTorch export/runtime degradation remains biased by prompt construction.
* **Cheapest experiment to settle it:** Run the unquantized transformers reference model on all 24 sample rows (or all 17 unnoted rows) to determine whether the `fp32 .pte` export already dropped from fp32 reference baseline before quantization was ever applied.

---

#### 2. Free of Hard-Coded / Specific Fixes: NOT SUPPORTED
* **Strongest objection:** The code diff contains tokenizer heuristics and prefill chunking logic tailored explicitly to LFM2.5's byte-pair/pre-tokenizer rules (`ExecuTorchEngine.kt`), along with comments acknowledging it is unverified for other families. More critically, the investigation revealed that the entire tool-calling evaluation in production was dependent on a hard-coded heuristic string injection in Kotlin (`TurnRunner.naming`), which was written specifically to force tool-calling on "what is the X" / "who is X" questions. 
* **Cheapest experiment to settle it:** Run `ExecuTorchEngineTest` across text samples tokenized with Llama 3.2's tokenizer and a BPE tokenizer that handles spaces/newlines differently (e.g., SentencePiece/Unigram or TikToken rules) to verify if `warmPiece` causes token boundary corruption or split tokens on other supported engines.

---

#### 3. Reliable and Reproducible: SUPPORTED
* **Strongest objection:** Python runner determinism on macOS does not guarantee execution reproducibility on physical target devices. The brief notes: *"The runtime on the phone was previously found nondeterministic run to run; the Mac runner is not."* If the production Android runtime (XNNPACK delegate multi-threading, FP16/FP32 math accumulation, or thermal throttling/scheduling) introduces runtime nondeterminism, a macOS Python environment measuring static greedy logit probabilities will reproduce itself perfectly without guaranteeing identical token output on device.
* **Cheapest experiment to settle it:** Run 5 consecutive greedy passes of the shipped `.pte` on a physical Android test device across the 24 sample prompts and compute exact token agreement and edit distance across passes.

---

### Concrete Hard-Coded, Model-Specific, or Symptom-Level Elements in the Code Diff

1. **LFM2.5 Tokenizer-Specific Cut Rule (`ExecuTorchEngine.kt`):**
   * **Location:** `ExecuTorchEngine.kt#warmPiece` (`if (space > 0) return window.substring(0, space)`).
   * **Issue:** Truncating *before* the space assumes every tokenizer prepends whitespace to the following subword token (the GPT/LFM `Ġ` convention). In SentencePiece models (e.g., standard Llama/SentencePiece variations using ` `) or tokenizers where spaces can be attached to preceding tokens or merged, cutting strictly before the space can artificially orphan whitespace tokens or alter tokenization. The docstring itself explicitly admits this limitation: *"True for LFM2.5's tokenizer, which is the one measured; a family whose tokenizer reads a piece's first character specially would need its own measurement."*

2. **Heuristic Whitespace Trailing Scan for Newlines (`ExecuTorchEngine.kt`):**
   * **Location:**
     ```kotlin
     while (newline > 0 && newline + 1 < text.length && text[newline + 1].isWhitespace()) {
         newline = window.lastIndexOf('\n', newline - 1)
     }
     ```
   * **Issue:** This is a symptom-level regex/string workaround for chunked prefill rather than token-boundary-aware chunking. It hard-codes an assumption that any newline followed by whitespace must be kept together to preserve multi-character whitespace tokens (like `\n\n` or `\n   `). If a prompt contains a long indented block (e.g., YAML, Markdown lists, code) exceeding `WARM_PIECE_CHARS`, this loop skips all indented linebreaks and falls back to a space cut or arbitrary character truncation at `most`, completely defeating the logic.

3. **Inflexible JSON Canonicalization Formatting (`PromptJson.kt`):**
   * **Location:** `PromptJson.kt#canonicalJson` hard-coding `", "` and `": "`.
   * **Issue:** While matching standard Python `json.dumps` defaults, this is a symptom-level patch for LFM2.5's prompt sensitivity. It performs naive character-by-character replacements without AST parsing. If a JSON schema uses non-standard whitespace within formatted definitions or if another model family expects compact JSON (`","`, `":"`)—which many fine-tuned chat templates use to save context tokens—this formatting is hard-coded across all templates via `ToolDefinition.asToolJson()`.
