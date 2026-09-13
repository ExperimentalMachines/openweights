**Bias**
- Verdict: SUPPORTED
- Strongest objection: The 16-row core analysis is a deliberate, non-random selection (10 rows where GGUF called and export did not, 6 where neither did) that maximizes the observed effect; the detailed mechanism investigation (sections 2–4) rests on this subset even though suite-level claims are carried by the 160-row replay.
- Cheapest experiment: Replicate the per-row probability and greedy analysis of sections 2–4 on the 24-row random sample (seed 11) and show the same patterns hold.

---

**Hard-coded**
- Verdict: SUPPORTED
- Strongest objection: The `warmPiece` cut rules are explicitly derived from LFM2.5 tokenizer measurements; the code comment warns that families with different tokenizer behavior “would need its own measurement,” so the logic is not guaranteed to generalize.
- Cheapest experiment: Feed a long prompt that crosses piece boundaries to another model family (e.g., Llama 3.2), tokenize the pieces vs. the whole, and verify no token differs.

---
**Reproducible**
- Verdict: SUPPORTED
- Strongest objection: Phone runtime nondeterminism is acknowledged in general, yet all phone measurements are single runs from 2026-09-10; reproducibility is directly shown only for the Mac runner, not for the phone where the original issue occurs.
- Cheapest experiment: Re-run the 24-row sample on the same phone device multiple times and confirm identical call/no-call outcomes.

---
**Hard-coded, model-specific, or symptom-level fixes in the diff**
- `String.canonicalJson()`: Symptom-level, model-specific. Only normalizes whitespace to match Jinja’s `tojson` output (based on LFM2.5 measurements: 0.84 vs. 0.48 on “Hanover”); not a general JSON canonicalizer.
- `warmPiece()` changes: Symptom-level, model-specific. Cut-before-space and line-break-run handling are explicitly justified by LFM2.5 tokenizer measurements (1–19 differing tokens per prompt at cuts after spaces) and the code comment limits applicability to tokenizers with LFM2.5’s properties.
