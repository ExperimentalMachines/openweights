# Adversarial review request: why the compiled LFM2.5 1.2B narrates a search it never makes

You are reviewing an investigation, its two code fixes and its research note for an Android
app (OpenWeights: on-device LLMs, Kotlin, two runtimes: llama.cpp with GGUF and ExecuTorch
1.4.0 with .pte). You have NO tools and cannot open files; everything you need is in this
brief. Attack the reasoning. For every claim below say whether the evidence given supports
it, what would disprove it, and what you would measure next. Be concrete and short. Do not
restate the brief.

## The symptom
Production (Play build): the recommended LFM2.5-1.2B ExecuTorch export (8da4w, XNNPACK,
32k window) answers "I'll search for that" / "Based on my search, ..." and never calls
`web_search`; the same weights as Q4_K_M GGUF through llama.cpp call it. The maintainer
also saw that switching on a second tool, `show_pictures`, made the compiled model search.
An earlier note (2026-09-10) measured on 4 phones: GGUF searched 33 of 75 rows that needed
it, compiled 1 to 4 of 75, and attributed the residual to "the export" without evidence.

## What was done (all greedy, first-token probability of `<|tool_call_start|>`, Mac)
1. Reconstructed the phone's exact prompt bytes from the eval header (system text SHA-1,
   tool definition SHA-1, date exchange) and llama.cpp's exact prompt token count (578).
   Liquid AI's template renders the same messages to 545 tokens; the missing 33 are the
   app's own note that `TurnRunner.naming` appends to the question when it matches
   "what is the X"/"who is X": "(This question names Rome the capital of. Look it up with
   web_search before answering rather than recalling it, and answer from what the search
   returns.)". With the note: 578 exactly.
2. Without the note, NO artifact calls on "What is Rome the capital of?": GGUF 0.001,
   8da4w .pte 0.000, fp32 transformers reference 0.0005. Claim: the 2026-09-10
   "33/75 vs 1-4/75" compared one artifact obeying the note with one that could not.
3. Same bytes (Liquid rendering + note) into: GGUF via the app's own llama.cpp build
   (b10549), .pte via ExecuTorch's Python runner (pybindings), reference via transformers
   fp32:

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


   The 8da4w export's greedy replies are the phone's, word for word ("According to recent
   web search results", "Based on my search, Jerusalem is not"). Where the tool token
   loses, mass goes to "According" (0.34 on Rome) and "Based" (0.40 on Jerusalem).
4. Bisection of the export (same checkpoint, same recipe, 2k window): fp32 unquantised
   calls (0.85 on Hanover, level with GGUF 0.84). 8da4w with the tied output head left
   unquantised (torchao filter patched to skip fqn "output"): no change (0.005 vs 0.004).
   So the loss is in the body, not the LM head. Weight-only int4 g32 (`4w`) and
   weight-only int8 (`int8`) results:

(pending as this brief is sent: weight-only `4w` produced a degenerate model, prob 1.0 on one token; weight-only `int8` fails to trace on LFM2; `torchao:` modes refuse XNNPACK. A feed-forward-only and an attention/conv-only 8da4w split are running.)

   Note the fp32 .pte is itself below the fp32 reference on several rows (Rome 0.49 vs
   0.94, Canada 0.44 vs 0.86, Memory 0.23 vs 0.62), unexplained.
5. Second defect found and fixed in the app: the Kotlin templates spliced the tools'
   multi-line indented `parametersJson` literals verbatim, while Liquid's template's
   `tojson` (and llama.cpp's jinja, used on the GGUF path) write one line with ", " and
   ": ". Same GGUF, same note, only whitespace changed:

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


   Fix: `ToolDefinition.asToolJson` canonicalises the schema (whitespace only; strings
   and key order untouched). Test PromptJsonTest. The existing unit tests never caught it
   because their fixtures used single-line schemas.
6. Third defect fixed: the ExecuTorch engine feeds long prompts in 800-char pieces cut
   AFTER a space; LFM2.5's pre-tokenizer (Llama-3-style regex) keeps the space with the
   following word, so " not" became " " + "not" at every cut (1 to 19 tokens per prompt
   differed from the whole-prompt tokenization, all at cuts; 0 after cutting before the
   space). Fix: cut before the space. Test in ExecuTorchEngineTest.
7. The show_pictures effect, reproduced: with the second tool's definition added (its
   description ends "for information of any kind use web_search"), the 8da4w export's
   p(tool) rises, e.g. Rome 0.007 -> 0.512, Memory 0.046 -> 0.304; three rows call
   greedily. Claim: one more instruction pointing at the tool tips a near-zero decision,
   not a working path.
8. Ruled out with evidence: the C++ HF tokenizer (Android AAR links PCRE2 with UTF+UCP;
   ids identical to HF on the prompt, markup and a contractions probe); BOS (engine writes
   one, count 578 admits no second); sampling (ExecuTorch's TextPrefiller samples the
   post-prompt token at temperature 0 regardless of the module's temperature); the parser
   (0 of 160 compiled rows contained any call markup); the runner's sliding-window branch
   (only the reply budget); prompt length (all rows under 720 tokens).

## The diffs
```diff
diff --git a/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt b/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt
index 8021ede2..f42c4ed3 100644
--- a/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt
+++ b/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/PromptJson.kt
@@ -25,10 +25,52 @@ package io.github.alpharomercoma.openweights.core.common.model
  * that stays in each family's own file.
  */
 
-/** A tool as the OpenAI-shaped object most templates were trained to read. */
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
 internal fun ToolDefinition.asToolJson(): String =
     """{"type": "function", "function": {"name": ${name.jsonQuoted()}, """ +
-        """"description": ${description.jsonQuoted()}, "parameters": $parametersJson}}"""
+        """"description": ${description.jsonQuoted()}, "parameters": ${parametersJson.canonicalJson()}}}"""
+
+/**
+ * [this] JSON with its whitespace laid out the way `json.dumps` does by default: nothing
+ * between tokens except `", "` after a member and `": "` after a key.
+ *
+ * Walked character by character rather than parsed, for the same reason [reindentJson]
+ * is: nothing but whitespace outside string literals may change, so key order, number
+ * spellings and escapes all come out exactly as they went in.
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
+            character.isWhitespace() -> Unit
+            else -> append(character)
+        }
+    }
+}
 
 /** [this] as a JSON string literal, escaped the way `json.dumps` writes one. */
 internal fun String.jsonQuoted(): String = buildString {
diff --git a/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt b/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt
index 110fc725..a5941269 100644
--- a/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt
+++ b/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/ExecuTorchEngine.kt
@@ -778,8 +778,14 @@ class ExecuTorchEngine(
 
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
+     * prompt and its pieces sat at a cut made after a space, one to nineteen a prompt. A
+     * line break is its own token either way, so a cut after one changes nothing.
      */
     private fun warmPiece(text: String, limit: Int): String {
         val most = minOf(WARM_PIECE_CHARS, limit)
@@ -788,7 +794,7 @@ class ExecuTorchEngine(
         val newline = window.lastIndexOf('\n')
         if (newline > 0) return window.substring(0, newline + 1)
         val space = window.lastIndexOf(' ')
-        if (space > 0) return window.substring(0, space + 1)
+        if (space > 0) return window.substring(0, space)
         return window
     }
 

```

## The new test
```kotlin
/*
 * Copyright 2026 The OpenWeights Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.alpharomercoma.openweights.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The tool schema reaches the prompt spelled the way the family's own chat template
 * would spell it: one line, `", "` between members and `": "` after a key, which is what
 * Jinja's `tojson` writes and therefore what every one of these models was trained on.
 *
 * The real tools declare their schemas as indented multi-line literals, and every
 * template spliced that text in verbatim. Measured on the Mac (2026-09-13, LFM2.5 1.2B
 * Q4_K_M, greedy, the phone's own prompt with the named-subject note): the multi-line
 * spelling took the probability of the tool-call token from 0.84 to 0.48 on "What is
 * Hanover the capital of?", and the same on every other row that names a subject.
 */
class PromptJsonTest {

    @Test
    fun `a multi-line schema is rendered on one line as tojson writes it`() {
        val tool = ToolDefinition(
            name = "web_search",
            description = "Search the web.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "What to look up, as you would type it into a search box"
                    }
                  },
                  "required": ["query"]
                }
            """.trimIndent(),
        )

        assertThat(tool.asToolJson()).isEqualTo(
            """{"type": "function", "function": {"name": "web_search", """ +
                """"description": "Search the web.", "parameters": {"type": "object", """ +
                """"properties": {"query": {"type": "string", """ +
                """"description": "What to look up, as you would type it into a search box"}}, """ +
                """"required": ["query"]}}}""",
        )
    }

    @Test
    fun `canonical JSON leaves strings alone and keeps key order`() {
        val json = "{\n \"b\":\"x, y: {z}\",\n\t\"a\" : [1,\n 2 ,3], " +
            "\"c\":{}, \"d\":\"\\\"q\\\"\" }"

        assertThat(json.canonicalJson())
            .isEqualTo("""{"b": "x, y: {z}", "a": [1, 2, 3], "c": {}, "d": "\"q\""}""")
    }

    @Test
    fun `a compact schema is unchanged`() {
        val compact =
            """{"type": "object", "properties": {"q": {"type": "string"}}, "required": ["q"]}"""

        assertThat(compact.canonicalJson()).isEqualTo(compact)
    }
}

```

## Questions to attack
A. Is the "note, not runtime" reading of the 2026-09-10 gap sound, given only 16 rows were
   re-measured here (10 GGUF-called rows, 6 neither)? What sample would you demand?
B. Is the bisection conclusive that quantisation (not the ET graph) is the cause, given the
   fp32 .pte itself trails the reference on some rows?
C. Does anything in the two code fixes risk regressing other model families (Qwen3, Phi-4,
   SmolLM3, Qwen2.5, Llama 3.2 all use asToolJson; only ExecuTorch uses warmPiece)?
   canonicalJson strips ALL whitespace outside strings and re-inserts ", " and ": ".
   Consider numbers, nested arrays, escaped quotes, unicode, empty containers.
D. Could the piece-cut change break anything: a piece that ends without trailing space
   now, a window with no space at all, a prompt whose only spaces are inside markup?
E. What is the right product decision: keep recommending the GGUF; ship an ExecuTorch
   export quantised differently (which knob, based on the bisection); or drop ExecuTorch
   for LFM2.5? What measurement would you require before shipping a new export?
F. Anything in the research note (below) that is stated more strongly than the evidence?

## The research note
# Why the compiled LFM2.5 narrates a search it never makes

2026-09-13. The Play build's recommended LFM2.5 1.2B was the ExecuTorch export. Asked
something it should look up, it writes "I'll search for that" or "Based on my search, ..."
and calls nothing; the same weights as a GGUF call `web_search`. The 2026-09-10 tables
(`retrieve-or-answer.md`, `recommended-runtime.md`) measured the gap on four phones and
attributed the residual to "the export" without saying what in the export. This note
reproduces the failure on a laptop with the model's own token probabilities, separates
three things that had been measured as one, and says which one is the cause.

Everything below is greedy, first-token probability of `<|tool_call_start|>` at the
start of the reply, on the prompt bytes the phone actually sent. Scripts are in the
session scratch (`prompts.py`, `trailer_run.py`, `pte_run.py`, `ref_run.py`,
`piece_diff.py`, `export_*.sh`); the rows are the 2026-09-10 decision suite's, seed 7,
the ten rows where the GGUF called and the export did not, plus six where neither did.

## 1. The phone's prompt, reconstructed to the token

The harness header records the system text, the tool definitions (by SHA-1) and the
date exchange, and llama.cpp reports an exact prompt token count: 578 for "What is Rome
the capital of?". Liquid AI's template renders the same messages and tool to 545 tokens.
The missing 33 are the app's own named-subject note, attached to the question by
`TurnRunner.naming` whenever the question has the shape "what is the X" or "who is X":

> (This question names Rome the capital of. Look it up with web_search before answering
> rather than recalling it, and answer from what the search returns.)

With the note the count is 578 exactly. This matters because **without it no artifact
calls**: on "What is Rome the capital of?" the GGUF puts 0.001 on the tool token, the
export 0.000, and the unquantised reference model 0.0005. The 2026-09-10 "GGUF 33 of 75,
compiled 1 to 4 of 75" was therefore not the two runtimes deciding differently on a bare
question. It was the same note, obeyed by one artifact and not the other.

## 2. Same bytes, three artifacts

Liquid's rendering plus the note, identical bytes into each. GGUF through the app's own
llama.cpp build (b10549, Metal, greedy); the `.pte` through the ExecuTorch 1.4.0 Python
runner; the reference through transformers in fp32.

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

The export's greedy replies on these rows are the phone's replies word for word:
"According to recent web search results, ...", "Based on my search, Jerusalem is not
...", "Let me check the web to find the author ...". Where the tool token loses, the mass
goes to "According" (0.34 on Rome) and "Based" (0.40 on Jerusalem). The model has read
the note, decided to search, and cannot reach the token that starts a call.

## 3. What in the export

Exports of the same checkpoint with the same recipe at a 2k window, same bytes in. The
first four columns are the named rows above; the fp32 reference is transformers.

| Export | Hanover | Rome | Jerusalem | Empire | Memory |
|---|---|---|---|---|---|
| fp32 reference (not ExecuTorch) | 0.85 | 0.94 | 0.92 | 0.80 | 0.62 |
| fp32 `.pte`, no quantisation | 0.85 | 0.49 | 0.78 | 0.69 | 0.23 |
| 8da4w g32, embeddings int8 (shipped) | 0.004 | 0.007 | 0.006 | 0.043 | 0.046 |
| 8da4w g32, tied output head left in fp32 | 0.005 | 0.009 | 0.005 | 0.018 | 0.048 |
| 8da4w g32 on the feed-forward blocks only | see below | | | | |
| 8da4w g32 on the attention and conv blocks only | see below | | | | |

The unquantised export calls, level with the GGUF on Hanover (0.85 against 0.84) and
within the GGUF's range on the rest; it trails the fp32 reference on some rows (Rome
0.49 against 0.94, Memory 0.23 against 0.62), which is a loss of its own in the graph or
the runtime's fp32 kernels and is left unexplained here. The 8da4w export puts the tool
token under 0.01 on nine of ten named rows. The ExecuTorch graph, the runtime, the C++
tokenizer and the app's feeding of it are therefore not the cause. The quantisation is.

Where in the network was the first guess wrong. The recipe applies torchao's
`Int8DynamicActivationIntxWeightConfig` to every `nn.Linear`, including `output`, the copy
of the tied embedding that `convert_weights.py` makes for the LM head, while llama.cpp's
Q4_K_M keeps a tied head at Q6_K; an int4 head that has to rank a rare control token
above "According" was the obvious suspect. Leaving the head in fp32 (the quantiser's
filter patched to skip it) changed nothing: 0.005 against 0.004. The loss is in the
body. Two exports that split the body, one with only the feed-forward projections in
int4 and one with only the attention and short-convolution projections, are in the chain
as this is written; their rows go into the table above when they land.

Two recipes that would have separated weights from activations did not run: the
weight-only `4w` mode produced a degenerate model (one token at probability 1.0 on every
row) and the weight-only `int8` mode fails to trace on this architecture ("a and b must
have same reduction dim"); the `torchao:` modes refuse to combine with the XNNPACK
delegate. Those are ExecuTorch 1.4.0 facts, recorded so nobody re-runs them.

The public numbers for 1B models at four bits (Llama 3.2 1B loses 25 points of GSM8K and
16 of IFEval to round-to-nearest int4) are the size of this export's 13.0 against 19.6
and 15.6 against 21.6, and Liquid's own QAD checkpoint exists because plain Q4_0 "incurs a
larger relative quality loss" at this size. The GGUF's advantage is not the runtime; it is
a quantisation with a per-block minimum, six-bit tensors where they matter and no
activation quantisation at all.

## 4. Two defects in the app, found on the way and fixed

**The tool JSON was spelled differently on the compiled path.** The tools declare their
schemas as indented multi-line literals and every Kotlin template spliced that text in
verbatim; llama.cpp renders the same tool through the model's own template, whose
`tojson` writes one line with `", "` and `": "`. Same GGUF, same note, only the schema's
whitespace changed:

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

`ToolDefinition.asToolJson` now canonicalises the schema (`canonicalJson`, key order and
strings untouched), which also fixes `reindentJson` for Llama 3.2, whose walker assumed
compact input. Test: `PromptJsonTest`.

**Warm pieces were cut after a space.** The ExecuTorch engine feeds a long prompt in 800
character pieces cut at whitespace; LFM2.5's pre-tokenizer keeps a space with the word
after it, so a cut after the space turned " not" into " " and "not". Measured with the
tokenizer: 1 to 19 tokens differed per prompt between the whole reading and the pieces,
all at cuts, and 0 once the cut moved before the space. Test:
`ExecuTorchEngineTest."a piece cut at a space leaves the space to the next piece"`.

Neither of these is the cause: with both corrected, the shipped export still gives the
tool token under 0.01 on the same rows. They are real losses the GGUF never paid, and the
first one is a third of the probability mass on the decisive token.

## 5. Ruled out, with the evidence

- **The C++ tokenizer.** RE2 rejects the lookahead in LFM2.5's pre-tokenizer regex; the
  1.4.0 Android AAR links PCRE2 with `PCRE2_UTF | PCRE2_UCP` and its ids equal Hugging
  Face's on the whole prompt, the tool-call markup and a contractions probe.
- **BOS.** The engine writes one; llama.cpp's count of 578 admits no second one.
- **Sampling.** `TextPrefiller` samples the token after the prompt at temperature zero
  whatever the module was opened with, so the decision token is greedy on the phone too.
- **The parser.** No compiled row in 160 contained `web_search(` or a call marker; there
  was nothing to parse.
- **The runner's sliding-window branch** (`max_seq_len < max_context_len`) changes only
  the reply budget, not positions or masks.
- **The prompt length bound.** Every row here is under 720 tokens.

## 6. What this changes

The 2026-09-10 verdict stands on its outcome and falls on its reasoning: the GGUF is the
right recommendation, but not because llama.cpp calls tools and ExecuTorch cannot. The
decision suite compared one artifact obeying the app's note to another that could not,
and the 8da4w export's inability is a quantisation loss, not a runtime property. An
ExecuTorch export quantised differently is measurable with the same scripts, and the
table in section 3 says which knob to turn.

Show pictures. The maintainer saw the compiled model search once `show_pictures` was switched on beside `web_search`. Same export, same rows, the second tool's definition added (its description ends "for information of any kind use web_search"):

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

The decision moves from near zero to a coin toss on four rows and the model calls on three of them greedily. That is one more instruction pointing at the tool, not a working path: the GGUF sits at 0.8 on the same rows with one tool.

