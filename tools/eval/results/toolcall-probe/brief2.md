# Verification request: is this investigation biased, hard-coded, or irreproducible?

You are one of three independent reviewers (the others are a Codex model and Gemini; you
do not see their answers). You have NO tools and cannot open files; everything is in this
brief. The maintainer's exact questions: "Make sure we are not biased or hard coded a
specific fix. Make sure this is reliable and reproducible." Answer those three questions
with a verdict each (SUPPORTED / NOT SUPPORTED / UNCERTAIN), the strongest objection you
can find, and the cheapest experiment that would settle it. Then list anything in the
code diff that is a hard-coded, model-specific or symptom-level fix rather than a general
correction. Be concrete. Do not restate the brief.

## Context
An Android app runs LFM2.5-1.2B-Instruct through llama.cpp (GGUF Q4_K_M) and ExecuTorch
1.4.0 (a .pte exported with the upstream 8da4w XNNPACK recipe). In production the
ExecuTorch model writes "I'll search for that" / "Based on my search, ..." and never calls
`web_search`; the GGUF calls. A first review round (Codex gpt-5.6-sol and Gemini 3.8
Flash) already objected that sixteen rows selected by prior disagreement cannot stand for
the suite, that the fp32 ExecuTorch export's own loss had to be stated, and that no
replacement export knob is justified yet. The note below was rewritten to that. This
round adds two things they asked for.

## New evidence for this round

### Reproducibility: the same measurement twice
The shipped 8da4w .pte was run twice through the ExecuTorch Python runner on the same
16 prompts (greedy, first-token probability of `<|tool_call_start|>`): every probability
identical to four decimals, every greedy continuation identical. max |dp| = 0.
(The runtime on the phone was previously found nondeterministic run to run; the Mac
runner is not.)

### Selection bias: a fresh random sample of 24 rows
24 rows drawn with seed 11 from the 144 decision-suite rows NOT used in the note, the
app's named-subject note attached where its rule fires (7 of 24), Liquid AI's own
template rendering, identical bytes into each artifact. "phone" columns are what the
2026-09-10 on-device run recorded for that row.

| Question | note | need | GGUF (Mac) | phone GGUF called | 8da4w .pte (Mac) | phone .pte called | fp32 .pte (Mac) |
|---|---|---|---|---|---|---|---|
| When did the latest NFL season begin? |  | yes | 0.00 | False | 0.000 | False | 0.00 |
| What is the best-selling video game franchise of all time? |  |  | 0.38 | False | 0.016 | False | 0.05 |
| In January 2023, the NHC revised the fatality data of Hurricane Katrina, increasing the reported death toll from 1,800 to what number? |  |  | 0.26 | False | 0.020 | False | 0.03 |
| On what date was Lunar New Year 2023? |  |  | 0.00 | False | 0.000 | False | 0.00 |
| What is the minimum annual income required for a family of four to be considered middle class in Delaware in 2023, according to the study? |  | yes | 0.02 | False | 0.006 | False | 0.00 |
| White Gem is a variety of which vegetable? |  | yes | 0.00 | False | 0.000 | False | 0.00 |
| Which team won the 2021 FIFA Club World Cup? |  |  | 0.19 | False | 0.005 | False | 0.00 |
| The longest unbeaten streak of all time in the Premier League is how many matches? |  |  | 0.30 | False | 0.002 | False | 0.03 |
| Which university did Taylor Swift receive her honorary Doctor of Fine Arts degree from? |  |  | 0.14 | True | 0.007 | False | 0.02 |
| When was the first road speed limit set in the UK for powered vehicles? |  |  | 0.03 | False | 0.000 | False | 0.00 |
| Who won the most recent Time Magazine's Athlete of the Year? |  | yes | 0.15 | False | 0.027 | False | 0.03 |
| Who was the producer of Black and White? | yes | yes | 0.70 | True | 0.021 | False | 0.45 |
| Who was the director of The Last Word? | yes | yes | 0.75 | True | 0.023 | False | 0.56 |
| How long do NFL football teams have to get a play off (the play clock)? |  |  | 0.00 | False | 0.000 | False | 0.00 |
| In what country is Northland Communications? |  |  | 0.01 | False | 0.002 | False | 0.00 |
| Who was the producer of Parker? | yes |  | 0.68 | True | 0.042 | False | 0.40 |
| Which vaccine completely eradicated COVID-19 worldwide? |  |  | 0.00 | False | 0.001 | False | 0.00 |
| Who was the screenwriter for Open Fire? | yes | yes | 0.70 | True | 0.046 | False | 0.46 |
| Who was the screenwriter for Last Night? | yes |  | 0.74 | True | 0.045 | False | 0.51 |
| In what city was Valdemar III of Denmark born? |  | yes | 0.06 | False | 0.010 | False | 0.03 |
| When is the next leap year? |  |  | 0.01 | False | 0.000 | False | 0.00 |
| Skeletal, Smooth, and Cardiac are all types of what? |  |  | 0.00 | False | 0.001 | False | 0.00 |
| Who was the screenwriter for Bleak House? | yes | yes | 0.81 | True | 0.045 | False | 0.57 |
| In what country is Călmuș River? |  |  | 0.02 | False | 0.003 | False | 0.00 |


Agreement between the Mac GGUF's greedy decision (call iff p > 0.5) and the phone's
recorded GGUF call: 23 of 24. Between the Mac .pte's greedy decision and the
phone's recorded .pte call: 24 of 24.

## The code diff (final)
```diff
diff --git a/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt b/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt
index 8021ede2..035538cd 100644
--- a/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt
+++ b/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt
@@ -19,16 +19,63 @@ package io.github.alpharomercoma.openweights.core.common.model
 /**
  * The JSON spellings the prompt templates share.
  *
- * Each template splices [ToolDefinition.parametersJson] in verbatim rather than
- * re-encoding it — re-encoding would reorder keys, and the schema is the model's only
- * description of what the arguments mean. What varies per family is the wrapping, and
- * that stays in each family's own file.
+ * Each template splices [ToolDefinition.parametersJson] in with only its whitespace
+ * changed, never re-encoded: re-encoding would reorder keys, and the schema is the
+ * model's only description of what the arguments mean. What varies per family is the
+ * wrapping, and that stays in each family's own file.
  */
 
-/** A tool as the OpenAI-shaped object most templates were trained to read. */
-internal fun ToolDefinition.asToolJson(): String =
-    """{"type": "function", "function": {"name": ${name.jsonQuoted()}, """ +
-        """"description": ${description.jsonQuoted()}, "parameters": $parametersJson}}"""
+/**
+ * A tool as the OpenAI-shaped object most templates were trained to read.
+ *
+ * The schema goes in on one line, spelled as Jinja's `tojson` spells it, because that is
+ * the text every chat template on file writes and so the text these models saw in
+ * training. The tools themselves declare their schemas as indented literals, and splicing
+ * those in verbatim was measured to matter: on the phone's own prompt for "What is
+ * Hanover the capital of?", LFM2.5 1.2B Q4_K_M (greedy, Mac, 2026-09-13) put 0.84 on the
+ * tool-call token with the one-line spelling and 0.48 with the indented one.
+ */
+internal fun ToolDefinition.asToolJson(): String {
+    val schema = parametersJson.canonicalJson()
+    return """{"type": "function", "function": {"name": ${name.jsonQuoted()}, """ +
+        """"description": ${description.jsonQuoted()}, "parameters": $schema}}"""
+}
+
+/**
+ * [this] JSON with its whitespace laid out the way `json.dumps` does by default: nothing
+ * between tokens except `", "` after a member and `": "` after a key.
+ *
+ * Walked character by character rather than parsed, for the same reason [reindentJson]
+ * is: nothing but whitespace outside string literals may change, so key order, number
+ * spellings and escapes all come out exactly as they went in. Nothing is validated: a
+ * schema that is not JSON comes out not JSON, as it went in before; [ToolDefinition]
+ * refuses a blank one at construction.
+ */
+internal fun String.canonicalJson(): String = buildString {
+    var inString = false
+    var escaped = false
+    this@canonicalJson.forEach { character ->
+        when {
+            inString -> {
+                append(character)
+                when {
+                    escaped -> escaped = false
+                    character == '\\' -> escaped = true
+                    character == '"' -> inString = false
+                }
+            }
+            character == '"' -> {
+                append(character)
+                inString = true
+            }
+            character == ',' -> append(", ")
+            character == ':' -> append(": ")
+            // JSON's own four; anything else outside a string is the schema's problem, kept.
+            character == ' ' || character == '\n' || character == '\r' || character == '\t' -> Unit
+            else -> append(character)
+        }
+    }
+}
 
 /** [this] as a JSON string literal, escaped the way `json.dumps` writes one. */
 internal fun String.jsonQuoted(): String = buildString {
diff --git a/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt b/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt
index 110fc725..8d34e720 100644
--- a/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt
+++ b/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt
@@ -778,17 +778,31 @@ class ExecuTorchEngine(
 
     /**
      * The next piece to feed: at most [WARM_PIECE_CHARS] and never more than [limit],
-     * preferring to end at a line break, then at a space, so the cut re-tokenizes no more
-     * oddly than it must.
+     * preferring to end at a line break, then just before a space.
+     *
+     * Before a space and not after it, because the tokenizer keeps a space with the word
+     * that follows: " not" is one token in the whole prompt, and a piece that ended with
+     * the space handed the runtime " " and "not" instead. Measured with LFM2.5's tokenizer
+     * on the search-only prompt (2026-09-13): every token that differed between the whole
+     * prompt and its pieces sat at a cut made after a space, one to nineteen a prompt.
+     * True for LFM2.5's tokenizer, which is the one measured; a family whose tokenizer
+     * reads a piece's first character specially would need its own measurement.
      */
     private fun warmPiece(text: String, limit: Int): String {
         val most = minOf(WARM_PIECE_CHARS, limit)
         if (text.length <= most) return text
         val window = text.substring(0, most)
-        val newline = window.lastIndexOf('\n')
+        // A line break followed by more whitespace is one token with it ("\n\n", "\n    "),
+        // so the cut goes after a break that ends its run. Fuzzed against the tokenizer
+        // on code, tables, URLs and whitespace runs (2026-09-13): the plain rule left 36 of
+        // 303 texts with differing tokens, this one none.
+        var newline = window.lastIndexOf('\n')
+        while (newline > 0 && newline + 1 < text.length && text[newline + 1].isWhitespace()) {
+            newline = window.lastIndexOf('\n', newline - 1)
+        }
         if (newline > 0) return window.substring(0, newline + 1)
         val space = window.lastIndexOf(' ')
-        if (space > 0) return window.substring(0, space + 1)
+        if (space > 0) return window.substring(0, space)
         return window
     }
 
diff --git a/core/engine/src/test/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngineTest.kt b/core/engine/src/test/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngineTest.kt
index 74e723cb..6f072518 100644
--- a/core/engine/src/test/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngineTest.kt
+++ b/core/engine/src/test/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngineTest.kt
@@ -758,6 +758,48 @@ class ExecuTorchEngineTest {
         assertThat(bridge.prompts.last()).doesNotContain("Rule 0:")
     }
 
+    /**
+     * A piece cut after a space changes the tokens: LFM2.5's pre-tokenizer keeps a space
+     * with the word after it, so " not" is one token in the whole prompt and two (" ",
+     * "not") when the cut lands between them. Measured with the tokenizer on the search-only
+     * prompt (2026-09-13): one to nineteen tokens differed from the whole-prompt reading,
+     * every one of them at a piece cut. The cut goes before the space instead, so the next
+     * piece opens with it and the runtime reads the same tokens the model was trained on.
+     */
+    @Test
+    fun `a piece cut at a space leaves the space to the next piece`() = runTest {
+        engine.load(installed(MODEL), PARAMS)
+        val oneLine = ChatMessage.text(ChatRole.SYSTEM, LONG_RULES.replace('\n', ' '))
+
+        engine.warm(listOf(oneLine), params = NO_THINKING)
+
+        assertThat(bridge.prefills.size).isGreaterThan(1)
+        // The template's own line breaks still cut after the break; every other cut lands
+        // before a space, so the space opens the next piece.
+        bridge.prefills.zipWithNext().forEach { (piece, next) ->
+            if (!piece.endsWith("\n")) {
+                assertThat(piece).doesNotMatch("(?s).* $")
+                assertThat(next).startsWith(" ")
+            }
+        }
+        assertThat(bridge.prefills.joinToString("")).contains(oneLine.text)
+    }
+
+    /** A blank line at a cut stays whole: "\n\n" is one token, and a piece may not end inside it. */
+    @Test
+    fun `a piece never ends inside a run of line breaks`() = runTest {
+        engine.load(installed(MODEL), PARAMS)
+        val paragraphs = ChatMessage.text(ChatRole.SYSTEM, LONG_RULES.replace("\n", "\n\n"))
+
+        engine.warm(listOf(paragraphs), params = NO_THINKING)
+
+        assertThat(bridge.prefills.size).isGreaterThan(1)
+        bridge.prefills.zipWithNext().forEach { (piece, next) ->
+            if (piece.endsWith("\n")) assertThat(next).doesNotMatch("(?s)^\\s.*")
+        }
+        assertThat(bridge.prefills.joinToString("")).contains(paragraphs.text)
+    }
+
     /**
      * The exporter bounds one prefill at `max_seq_len - 1` tokens and the runtime chunks
      * at `max_seq_len`, so a generate call carrying a whole long prompt fails on the

```

## The research note (final)
# Why the compiled LFM2.5 narrates a search it never makes

2026-09-13. The Play build's recommended LFM2.5 1.2B was the ExecuTorch export. Asked
something it should look up, it writes "I'll search for that" or "Based on my search, ..."
and calls nothing; the same weights as a GGUF call `web_search`. The 2026-09-10 tables
(`retrieve-or-answer.md`, `recommended-runtime.md`) measured the gap on four phones and
attributed the residual to "the export" without saying what in the export. This note
reproduces the failure on a laptop with the model's own token probabilities, separates
three things that had been measured as one, and says which one carries the loss. It was
reviewed by Codex (gpt-5.6-sol) and Gemini 3.8 Flash the same day; section 7 says what
they changed.

Everything below is greedy, first-token probability of `<|tool_call_start|>` at the start
of the reply, on the prompt bytes the phone sent. Scripts:
`tools/eval/bench/toolcall_probe/`; rows, probabilities and both reviews:
`tools/eval/results/toolcall-probe/`. The rows are sixteen of the 2026-09-10 decision
suite's (seed 7): the ten where the GGUF called and the export did not, and six where
neither did. That selection is deliberate and it bounds what the note can claim: it
explains those rows, and the 160-row replay in section 1 is what carries to the suite.

## 1. The phone's prompt, reconstructed

The harness header records the system text and the tool definition by SHA-1 and the date
exchange verbatim, and llama.cpp reports an exact prompt token count: 578 for "What is
Rome the capital of?". Liquid AI's template renders those messages and that tool to 545
tokens. The missing 33 are the app's own named-subject note, attached to the question by
`TurnRunner.naming` whenever it has the shape "what is the X" or "who is X":

> (This question names Rome the capital of. Look it up with web_search before answering
> rather than recalling it, and answer from what the search returns.)

With the note the count is 578 exactly. Hash and count corroborate the bytes; the token
ids were not logged on the phone, so "exact" is the strongest reading the evidence allows.

Without the note, on "What is Rome the capital of?", no artifact calls: GGUF 0.001, the
export 0.000, the unquantised reference model 0.0005. With it, the GGUF calls and the
export does not (section 2). So the note is a large part of the 2026-09-10 gap, and the
suite's own rows say how large. Replaying the naming rule over all 160 rows of the
driven-search arm (`tools/eval/results/decisions/`):

| | rows the note applies to | rows it does not |
|---|---|---|
| GGUF Q4_K_M called | 42 of 42 | 18 of 118 |
| 8da4w export called | 0 of 42 | 3 of 118 |
| of the 75 rows that need a search | 21 noted, GGUF called all 21 | 54 unnoted, GGUF called 12 |

Two thirds of the GGUF's 33 needed calls were the note obeyed; the compiled model
obeyed it on none. The other third, 12 against 3 on rows with no note, is the same
artifact difference on the model's own decisions, and the Trump row in section 2 (0.92
against 0.11 with no note) is one of them. "Not the runtime deciding differently" is
therefore true of the noted rows and of the artifact; it is not a claim that the GGUF
never decides to search on its own.

## 2. Same bytes, three artifacts

Liquid's rendering plus the note, identical bytes into each. GGUF through the app's own
llama.cpp build (b10549, Metal, greedy); the `.pte` through the ExecuTorch 1.4.0 Python
runner on the shipped 32k file; the reference through transformers in fp32.

| Question (named-subject note attached unless marked) | GGUF Q4_K_M | fp32 reference (transformers) | fp32 .pte | 8da4w .pte (shipped) | 8da4w, head unquantised |
|---|---|---|---|---|---|
| What is Hanover the capital of? | 0.84 | 0.85 | 0.85 | 0.004 | 0.005 |
| What is Rome the capital of? | 0.87 | 0.94 | 0.49 | 0.007 | 0.009 |
| What is Columbia the capital of? | 0.79 | 0.93 | 0.70 | 0.000 | 0.024 |
| What is Jerusalem the capital of? | 0.66 | 0.92 | 0.78 | 0.006 | 0.005 |
| What is the religion of Hillsdale Free Will Baptist College? (no note) | 0.09 | 0.10 | 0.02 | 0.009 | 0.003 |
| What is Canada's most populous province? | 0.82 | 0.86 | 0.44 | 0.010 | 0.015 |
| Who is the author of Empire? | 0.85 | 0.80 | 0.69 | 0.043 | 0.018 |
| Who is the author of Memory? | 0.74 | 0.62 | 0.23 | 0.046 | 0.048 |
| Who is the author of Responsibility? | 0.83 | 0.79 | 0.59 | 0.089 | 0.102 |
| Who is the author of Skyscraper? | 0.77 | 0.75 | 0.62 | 0.050 | 0.072 |
| In what country is Zec Petawaga? (no note) | 0.05 | 0.03 | 0.01 | 0.020 | 0.005 |
| What is the capital of Quebec? (no note) | 0.00 | 0.00 | 0.00 | 0.000 | 0.000 |
| In what country is Ločenice? (no note) | 0.00 | 0.00 | 0.00 | 0.000 | 0.000 |
| In what country is Faculty of Engineering (LTH), Lund University? (no note) | 0.01 | 0.01 | 0.00 | 0.001 | 0.001 |
| What's the most popular film on Letterboxd this week? (no note) | 0.27 | 0.22 | 0.05 | 0.053 | 0.075 |
| What is the most recent country that President Donald Trump visited during his second presidency? (no note) | 0.92 | 0.66 | 0.59 | 0.110 | 0.092 |

The shipped export's greedy replies on these rows are the phone's replies word for word:
"According to recent web search results, ...", "Based on my search, Jerusalem is not
...", "Let me check the web to find the author ...". Where the tool token loses, the mass
goes to "According" (0.34 on Rome) and "Based" (0.40 on Jerusalem): the token is
reachable, and it loses the greedy competition to the words of a search already done.

## 3. What in the export

Exports of the same checkpoint with the same recipe, same bytes in. The shipped file is
the 32k export; the controls are 2k exports, which the window matrix
(`executorch-window-matrix.md`) found to differ from 32k in memory and nothing else.

| Export | Hanover | Rome | Jerusalem | Empire | Memory |
|---|---|---|---|---|---|
| fp32 reference (transformers, not ExecuTorch) | 0.852 | 0.939 | 0.921 | 0.804 | 0.618 |
| fp32 `.pte`, no quantisation (2k) | 0.852 | 0.491 | 0.779 | 0.694 | 0.227 |
| 8da4w g32, embeddings int8 (the shipped 32k file) | 0.004 | 0.007 | 0.006 | 0.043 | 0.046 |
| 8da4w g32, tied output head left in fp32 (2k) | 0.005 | 0.009 | 0.005 | 0.018 | 0.048 |
| 8da4w g32 on the feed-forward projections only (2k) | 0.250 | 0.419 | 0.062 | 0.080 | 0.193 |
| 8da4w g32 on the attention and short-conv projections only (2k) | 0.433 | 0.331 | 0.178 | 0.355 | 0.150 |

Two losses, read from the rows. The unquantised `.pte` already trails the reference on
some rows (Rome 0.49 against 0.94, Memory 0.23 against 0.62) while matching it on
others: a loss of the graph or the fp32 kernels, unexplained here, and on its own not
enough to stop a call (it still puts the tool token first on Hanover, Jerusalem and
Empire). The recipe's `8da4w` then takes every named row under 0.01. So the quantisation
is the dominant measured cause on these rows, in this configuration; the graph
contributes, and neither is proven for rows this note did not run.

Where in the network. The first guess was the LM head: the recipe applies torchao's
`Int8DynamicActivationIntxWeightConfig` to every `nn.Linear`, including `output`, the
copy of the tied embedding that `convert_weights.py` makes, while llama.cpp's Q4_K_M
keeps a tied head at Q6_K. Leaving the head in fp32 (the quantiser's filter patched to
skip it, `quantize_skip.patch`) changed nothing. Splitting the body says why: int4 on
the feed-forward projections alone leaves 0.06 to 0.42, int4 on the attention and
short-convolution projections alone leaves 0.15 to 0.43, and the two together collapse
to under 0.01. The loss compounds across the network; there is no layer to spare and no
single knob in this table.

Three recipes that would have separated weights from activations did not run in
ExecuTorch 1.4.0 with the XNNPACK delegate, with this architecture and these commands:
weight-only `4w` produced a degenerate model (one token at probability 1.0 on every
row), weight-only `int8` fails to trace ("a and b must have same reduction dim"), and the
`torchao:` modes refuse to combine with XNNPACK. Another recipe may work; these are
recorded so nobody repeats these three.

For scale: published four-bit round-to-nearest losses on 1B models (Llama 3.2 1B, 25
points of GSM8K and 16 of IFEval) are the size of this export's 13.0 against 19.6 and
15.6 against 21.6 on the public benchmarks, and Liquid's own QAD checkpoint exists
because plain Q4_0 "incurs a larger relative quality loss" at this size. Context, not
proof: nothing here isolates which property of Q4_K_M (per-block minimum, six-bit
tensors, unquantised activations) the GGUF owes its calls to.

## 4. Two defects in the app, found on the way and fixed

**The tool JSON was spelled differently on the compiled path.** The tools declare their
schemas as indented multi-line literals and every Kotlin template spliced that text in
verbatim; llama.cpp renders the same tool through the model's own template, whose
`tojson` writes one line with `", "` and `": "` (transformers' `tojson` is `json.dumps`
with its default separators; llama.cpp's jinja does the same). Same GGUF, same note, only
the schema's whitespace changed:

| Question | one-line schema | indented schema |
|---|---|---|
| What is Hanover the capital of? | 0.84 | 0.48 |
| What is Rome the capital of? | 0.87 | 0.57 |
| What is Columbia the capital of? | 0.79 | 0.41 |
| What is Jerusalem the capital of? | 0.66 | 0.24 |
| What is Canada's most populous province? | 0.82 | 0.53 |
| Who is the author of Empire? | 0.85 | 0.67 |
| Who is the author of Memory? | 0.74 | 0.54 |
| Who is the author of Responsibility? | 0.83 | 0.62 |
| Who is the author of Skyscraper? | 0.77 | 0.60 |

`ToolDefinition.asToolJson` now canonicalises the schema (`canonicalJson`: whitespace
outside strings only; key order, number spellings and escapes untouched; nothing
validated, and `ToolDefinition` already refuses a blank schema). Every Kotlin template
uses it; the spelling it produces is the one each family's own template writes, which is
the inspected inference template and not a claim about training data. It also fixes
`reindentJson` for Llama 3.2, whose walker assumed compact input. Test: `PromptJsonTest`
(minified, escaped backslashes, `-0`, an exponent, a non-ASCII escape, empty containers).

**Warm pieces were cut after a space.** The ExecuTorch engine feeds a long prompt in 800
character pieces cut at whitespace; LFM2.5's pre-tokenizer keeps a space with the word
after it, so a cut after the space turned " not" into " " and "not". Measured with the
tokenizer: 1 to 19 tokens differed per prompt between the whole reading and the pieces,
all at cuts. The cut now lands before the space, and after a line break only when the
break ends its run ("\n\n" and "\n    " are single tokens). Fuzzed on 303 texts of code,
tables, URLs and whitespace runs plus the four real prompts: 0 differing tokens, except
a text with no whitespace at all, where any cut splits a token. Measured for LFM2.5's
tokenizer only. Tests: `ExecuTorchEngineTest`, the two piece-cut cases.

Neither fix is sufficient to explain the collapse: with both applied, the shipped export
still gives the tool token under 0.01 on the same rows. Both are measurable losses the
GGUF path never paid, and the first is 0.36 of probability on the decisive token, on one
example.

**Show pictures.** The maintainer saw the compiled model search once `show_pictures` was
switched on beside `web_search`. Same export, same rows, the second tool's definition
added (its description ends "for information of any kind use web_search"):

| Question | web_search alone | web_search and show_pictures |
|---|---|---|
| What is Hanover the capital of? | 0.004 | 0.043 |
| What is Rome the capital of? | 0.007 | 0.512 |
| What is Columbia the capital of? | 0.000 | 0.078 |
| What is Jerusalem the capital of? | 0.006 | 0.026 |
| What is the religion of Hillsdale Free Will Baptist College? | 0.009 | 0.095 |
| What is Canada's most populous province? | 0.010 | 0.023 |
| Who is the author of Empire? | 0.043 | 0.267 |
| Who is the author of Memory? | 0.046 | 0.304 |
| Who is the author of Responsibility? | 0.089 | 0.243 |
| Who is the author of Skyscraper? | 0.050 | 0.307 |
| In what country is Zec Petawaga? | 0.020 | 0.022 |
| What is the capital of Quebec? | 0.000 | 0.000 |
| In what country is Ločenice? | 0.000 | 0.002 |
| In what country is Faculty of Engineering (LTH), Lund University? | 0.001 | 0.003 |
| What's the most popular film on Letterboxd this week? | 0.053 | 0.130 |
| What is the most recent country that President Donald Trump visited during his second presidency? | 0.110 | 0.532 |

The decision moves from near zero to a coin toss on four rows and the model calls on
three of them greedily. The change confounds several things (a second candidate tool,
168 more tokens, an extra sentence naming `web_search`), so what it shows is only that
the export's decision sits close enough to zero for a different prefix to tip it; the
GGUF sits at 0.8 on the same rows with one tool.

## 5. Ruled out, for the probes run

- **The C++ tokenizer.** RE2 rejects the lookahead in LFM2.5's pre-tokenizer regex; the
  1.4.0 Android AAR links PCRE2 with `PCRE2_UTF | PCRE2_UCP` and, on the Mac build of the
  same library, its ids equal Hugging Face's on the whole prompt, the tool-call markup and
  a contractions probe.
- **BOS.** The engine writes one; llama.cpp's count of 578 admits no second one. Token
  ids were not logged on the phone, so this is corroboration rather than proof.
- **Sampling.** `TextPrefiller` samples the token after the prompt at temperature zero
  whatever the module was opened with, so the decision token is greedy on the phone too.
- **The parser.** No compiled row in 160 contained `web_search(` or a call marker; there
  was nothing to parse.
- **The runner's sliding-window branch** (`max_seq_len < max_context_len`) changes only
  the reply budget, not positions or masks.
- **The prompt length bound.** Every row here is under 720 tokens, so the 2047-token
  input bound (`openweights-executorch-prefill-bound`) is not in play; length-dependent
  numerical effects were not tested.

## 6. What this changes

The 2026-09-10 recommendation stands and stays provisional: the GGUF is the right
artifact for LFM2.5, the shipped 8da4w export should not be recommended, and ExecuTorch
as a runtime is not condemned by this, only this recipe on this family. No replacement
export is named, because the table in section 3 offers no single knob: the loss
compounds across the body, and the recipes that would have separated activation from
weight quantisation did not run. Before any new export is recommended, the reviewers'
bar is the right one: the exact window shipped, the full 160 rows plus a held-out draw,
call recall and unnecessary calls, the narrated-search rate, one and several tools,
prompts that cross a piece boundary, against the GGUF and the fp32 `.pte`, on more than
one phone, with thresholds set before the run.

The two app fixes ship regardless: the compiled path now feeds the bytes the template
would have, and no piece cut changes a token.

## 7. Reviewed

Codex (gpt-5.6-sol, medium) and Gemini 3.8 Flash (medium), the same brief, no tools.
Both: keep the GGUF, name no knob yet, say the fp32 `.pte`'s own loss, call the two fixes
contributors and not causes, and do not let sixteen selected rows stand for the suite.
Taken: the 160-row replay in section 1, the wording of sections 2, 3, 5 and 6, the
whitespace set in `canonicalJson` narrowed to JSON's four characters, the piece-cut fuzz
and the line-break rule, an escaped-backslash and minified case in the tests. Gemini's
claim that Jinja's `tojson` writes compact JSON is wrong for the templates in question
(transformers' filter is `json.dumps` with default separators, and the rendered prompt
was checked) and was not taken. Codex's blank-schema guard was not needed:
`ToolDefinition` refuses one at construction.

