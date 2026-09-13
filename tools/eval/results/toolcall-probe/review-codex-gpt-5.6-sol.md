## A. “The note, not runtime” explains the old gap

**Verdict: partially supported, overclaimed.**

- The Rome ablation proves the note can change the decision dramatically and that the compiled model fails to exploit it.
- The 16 rows are selection-biased: ten were chosen because GGUF called and `.pte` did not. They cannot explain the original 75-row aggregate.
- Matching 578 tokens does not uniquely prove exact prompt bytes; hashes help only if the hashed source text was independently recovered.

**Would disprove it:** On the original 75 rows, GGUF still substantially outperforms `.pte` when both receive verified-identical prompts without the note—or the old compiled prompts turn out not to contain the note.

**Measure next:** Re-run all 75, preferably the full 160, with a paired factorial:

- note on/off;
- GGUF, fp32 reference, fp32 `.pte`, shipped `.pte`;
- exact prompt bytes or token IDs logged;
- original phones as well as Mac;
- report call recall, false-call rate, and paired confidence intervals.

Add an untouched holdout set; do not select rows by prior disagreement.

## B. Quantisation versus the ExecuTorch graph

**Verdict: quantisation explains the large additional collapse, but it is not the only demonstrated cause.**

The fp32 `.pte` versus quantised `.pte` comparison is the strongest evidence: if graph, window, tokenizer, and inputs truly match, enabling 8da4w causes a large incremental loss. But the fp32 `.pte` deficits show that graph/export/runtime numerics also matter. Therefore this sentence is unjustified:

> “The ExecuTorch graph [and] runtime … are therefore not the cause.”

The defensible claim is: **the graph/runtime is insufficient to explain the near-zero shipped result; 8da4w adds the dominant observed degradation on these rows.**

Leaving the head unquantised shows that head quantisation alone is not sufficient to explain it. It does not fully prove “the loss is in the body” until you verify that the deployed graph genuinely retained the head in fp32 and account for interactions between head and body errors.

Also, a 2k bisection does not automatically diagnose the shipped 32k export.

**Would disprove it:** A byte-identical, 32k fp32 `.pte` exhibiting the same collapse; failure to reproduce the result after inspecting actual graph dtypes/delegation; or another export difference—not quantisation—tracking the failure.

**Measure next:**

- Repeat on 32k exports and all rows.
- Verify per-node dtypes and the patched head in the exported graph.
- Compare layerwise hidden-state/logit errors against transformers.
- Complete the feed-forward versus attention/conv split.
- Separate activation quantisation from weight quantisation.
- Compare full top-token margins, not only one token’s probability.

No specific quantisation knob has yet been selected by the evidence.

## C. `canonicalJson` regression risk

**Verdict: probably safe for valid JSON, but the justification and tests are too narrow.**

For valid JSON, removing insignificant whitespace outside strings is semantically safe. The walker appears to preserve:

- number spellings;
- nested arrays and objects;
- empty containers;
- Unicode and whitespace inside strings;
- escaped quotes and backslashes;
- key order.

Important reservations:

- It does **not** reproduce Python `json.dumps` generally. Python may escape Unicode and normalise parsed values; this function deliberately preserves their spelling.
- It performs no validation. Malformed JSON, unterminated strings, or invalid escapes can silently produce malformed output.
- `character.isWhitespace()` accepts more characters than JSON’s four legal whitespace characters. That is harmless for valid JSON but makes “canonical JSON” an overbroad name.
- The change affects every family using `asToolJson`; evidence from LFM2.5 does not prove that every family benefits.
- “What models saw in training” is unsupported merely because current templates use `tojson`.
- The “compact schema is unchanged” test actually tests already canonical-spaced JSON, not minified JSON.

**Would disprove safety:** Any family’s official template emits different bytes, or tool-call accuracy regresses under the canonical form.

**Measure next:** Golden-byte comparisons against each family’s actual Liquid/Jinja rendering using every production schema, followed by tool-call regression tests for Qwen3, Phi-4, SmolLM3, Qwen2.5, and Llama 3.2. Add cases for exponent numbers, `-0`, non-ASCII text, surrogate pairs, escaped backslash runs, empty arrays/objects, minified input, and invalid JSON.

Parsing and serialising with a deliberately configured serializer would provide validation, but only if preserving number spellings and key order is not a hard requirement.

## D. Piece-cut change

**Verdict: plausible for LFM2.5, not proven safe across tokenizers.**

The implementation still makes progress:

- No space: returns the complete window.
- Only a leading space: returns the complete window.
- Multiple spaces: cuts before the last one and leaves it for the next piece.

Potential regressions:

- Independently tokenizing a piece beginning with whitespace may behave differently for SentencePiece or tokenizer implementations that treat beginning-of-input specially.
- Ending a piece on a word boundary is safe only if no token can span that boundary for the relevant tokenizer.
- “A newline is its own token either way” is tokenizer-specific, not a general fact.
- A no-whitespace 800-character segment can still split a Unicode surrogate pair or other multibyte sequence.
- Spaces inside markup are not special; safety depends on token-ID equivalence, not whether the text is markup.
- If native ingestion trims leading whitespace, the new boundary would lose data.

**Would disprove safety:** Whole-prompt token IDs differ from concatenated piece IDs for any supported ExecuTorch tokenizer, or generation changes on a boundary-shifted but otherwise identical prompt.

**Measure next:** Property-test every ET tokenizer with production prompts plus randomized Unicode, CRLF, long URLs/code/JSON, leading and repeated spaces, and forced cuts at every nearby character. Compare exact token IDs and actual native-runner logits, not only the Python tokenizer. Assert reconstruction and forward progress.

## E. Product decision

**Decision now: keep GGUF as the recommended LFM2.5 artifact and de-recommend the current `.pte`.** Do not drop ExecuTorch for LFM2.5 permanently; the evidence condemns this export recipe, not the runtime category.

Do not yet advertise a replacement knob. The current experiment identifies “something outside the head in 8da4w,” but does not distinguish:

- dynamic activation int8;
- int4 body weights;
- embeddings;
- feed-forward blocks;
- attention/conv blocks;
- quantisation interactions.

The pending split should determine the next export. If feasible, the most informative immediate control is **the same body weights without dynamic activation quantisation**, followed by selective fp16/int8 retention of whichever blocks the split implicates.

Before shipping, require:

- the exact 32k Android export;
- full original suite plus untouched holdout;
- search recall and false-positive rate;
- narrated-search-without-call rate;
- one-tool and multi-tool conditions;
- short and long prompts, including chunk boundaries;
- comparison with GGUF and fp32 `.pte`;
- several representative phones;
- task-quality benchmarks, latency, RAM, package size, and thermal behavior;
- predefined acceptance thresholds rather than “looks improved.”

## F. Overstatements in the note

These should be weakened:

- **“Exact prompt bytes”** — token-count agreement is strong corroboration, not unique proof. Log bytes or token IDs.
- **“Without it no artifact calls”** — shown for Rome, not all artifacts and rows.
- **“The model … decided to search and cannot reach the token”** — narration suggests a search-related state, but does not establish an internal decision. The token is reachable; it loses the greedy competition.
- **“The unquantised export calls … within GGUF’s range on the rest”** — several probabilities are far below GGUF, especially Memory. A probability alone also does not establish the argmax.
- **“Graph/runtime … are not the cause”** — contradicted by the unexplained fp32 `.pte` loss. Say they are not sufficient to explain the shipped collapse.
- **“The quantisation is [the cause]”** — say 8da4w is the dominant measured incremental cause in the tested configuration.
- **“The loss is in the body”** — say unquantising the head alone did not recover it.
- **“The GGUF advantage … is [its quantisation scheme]”** — plausible, but not isolated experimentally.
- **Public 1B benchmarks explain this result** — they provide context, not causal evidence for tool-token suppression.
- **Failed export modes are ExecuTorch 1.4.0 facts** — they are facts about the tested command, architecture, and configuration; another recipe may work.
- **“Every model was trained on” this JSON spelling** — unsupported. At most it matches the inspected inference templates.
- **“Neither defect is the cause”** — both are measurable contributors. Say neither is sufficient to explain the remaining near-zero `.pte` result.
- **“A third of the probability mass”** — 0.84 to 0.48 is a 0.36-point drop, about 43% relative; the wording is mathematically unclear and based on one example.
- **Tokenizer ruled out** — ruled out for the tested probes, not universally.
- **BOS ruled out by count** — count agreement is evidence, but direct token-ID logging is conclusive.
- **Prompt length ruled out** — being under 720 rules out exceeding the limit, not length-dependent numerical effects.
- **`show_pictures` proves one more instruction tipped it** — confounded by added schema text, tool ordering, prompt length, and an additional candidate tool. Test instruction-only text, a dummy second tool, reordered tools, and description variants.
- **“The table says which knob to turn”** — it currently does not; the decisive split is still pending.
- **“The verdict stands”** — reasonable as an interim product recommendation, but it should remain provisional until the fixed app and candidate replacement are rerun on devices.

The strongest defensible conclusion is narrower: **the app had two prompt-fidelity defects, the shipped 8da4w export suffers a large additional tool-decision degradation relative to fp32 `.pte`, and the fp32 `.pte` itself retains an unexplained gap from transformers.**
