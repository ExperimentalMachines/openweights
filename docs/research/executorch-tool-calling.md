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
Empire). The recipe's `8da4w` then leaves the ten named rows at 0.00 to 0.09, seven of them
under 0.01 and none where greedy decoding calls. So the quantisation
is the dominant measured cause on these rows, in this configuration; the graph
contributes, and neither is proven for rows this note did not run.

Where in the network. The first guess was the LM head: the recipe applies torchao's
`Int8DynamicActivationIntxWeightConfig` to every `nn.Linear`, including `output`, the
copy of the tied embedding that `convert_weights.py` makes, while llama.cpp's Q4_K_M
keeps a tied head at Q6_K. Leaving the head in fp32 (the quantiser's filter patched to
skip it, `quantize_skip.patch`) changed nothing. Splitting the body says why: int4 on
the feed-forward projections alone leaves 0.06 to 0.42, int4 on the attention and
short-convolution projections alone leaves 0.15 to 0.43, and the two together collapse
to 0.09 and under. The loss compounds across the network; there is no layer to spare and
no single knob in this table.

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

## 3b. Is it the recipe, or this family?

The same probe on Qwen3 1.7B, exported with the same recipe (8da4w g32, embeddings
int8, 2k), Qwen3's own template with thinking off and the same note, probability of
`<tool_call>` (`qwen_probe.py`):

| Question | GGUF Q8_0 | fp32 reference | 8da4w .pte |
|---|---|---|---|
| What is Hanover the capital of? | 1.00 | 1.00 | 0.980 |
| What is Rome the capital of? | 1.00 | 1.00 | 0.980 |
| What is Columbia the capital of? | 1.00 | 1.00 | 0.973 |
| What is Jerusalem the capital of? | 1.00 | 1.00 | 0.963 |
| What is the religion of Hillsdale Free Will Baptist College? | 0.00 | 0.00 | 0.000 |
| What is Canada's most populous province? | 1.00 | 1.00 | 0.515 |
| Who is the author of Empire? | 1.00 | 1.00 | 0.986 |
| Who is the author of Memory? | 1.00 | 1.00 | 0.977 |
| Who is the author of Responsibility? | 1.00 | 1.00 | 0.982 |
| Who is the author of Skyscraper? | 1.00 | 1.00 | 0.984 |
| In what country is Zec Petawaga? | 0.00 | 0.00 | 0.000 |
| What is the capital of Quebec? | 0.00 | 0.00 | 0.000 |
| In what country is Ločenice? | 0.00 | 0.00 | 0.000 |
| In what country is Faculty of Engineering (LTH), Lund University? | 0.00 | 0.00 | 0.000 |
| What's the most popular film on Letterboxd this week? | 0.00 | 0.01 | 0.000 |
| What is the most recent country that President Donald Trump visited during his second presidency? | 0.20 | 0.23 | 0.000 |

The Qwen3 export keeps its calls: 0.96 to 0.99 on nine of ten noted rows against the
reference's 1.00, one row at 0.52. So the recipe does not erase tool calling in general;
it erases it where the margin was thin to begin with. LFM2.5's reference sits at 0.6 to
0.9 on the same rows and Qwen3's at 1.00, and the loss that leaves Qwen3 at 0.97 leaves
LFM2.5 at 0.01. Whether that thinner margin is LFM2.5's size, its training or its
hybrid short-convolution design is not separated here. The public BFCL table
(`benchmark-matrix.md`) says the same at coarser grain: every compiled family loses two
to five of thirty on explicit function-calling prompts, and only LFM2.5 collapses on the
app's own decision task.

## 3c. Qwen3.5 2B, exported both ways

Asked by the maintainer the same day: Qwen3.5 2B (`Qwen/Qwen3.5-2B`, a hybrid of Gated
DeltaNet and full-attention layers) as `unsloth/Qwen3.5-2B-GGUF` Q4_K_M against two of
our own ExecuTorch exports through the upstream `qwen3_5` example: fp32 (9.5 GB, static
shape, the only recipe its README documents) and 8da4w g32 with int8 embeddings (576 MB,
which the README calls deferred but which exports and runs). Same note, Qwen3.5's own
template with thinking off, probability of `<tool_call>` (`qwen35_probe.py`; the static
graph takes the prompt one token at a time, so the fp32 file was run on three rows):

| Question | GGUF Q4_K_M | fp32 reference | fp32 .pte | 8da4w .pte |
|---|---|---|---|---|
| What is Hanover the capital of? | 0.94 | 0.95 | 0.95 | 0.108 |
| What is Rome the capital of? | 0.88 | 0.89 | 0.89 | 0.160 |
| What is Columbia the capital of? | 0.91 | 0.91 | 0.91 | 0.103 |
| What is Jerusalem the capital of? | 0.87 | 0.93 |  | 0.032 |
| What is Canada's most populous province? | 0.92 | 0.93 |  | 0.327 |
| Who is the author of Empire? | 0.88 | 0.87 |  | 0.026 |
| Who is the author of Memory? | 0.91 | 0.91 |  | 0.233 |
| Who is the author of Responsibility? | 0.91 | 0.90 |  | 0.418 |
| Who is the author of Skyscraper? | 0.91 | 0.87 |  | 0.068 |
| What is the most recent country that President Donald Trump visited during his second presidency? | 0.03 | 0.03 |  | 0.000 |

The GGUF is level with the reference and the fp32 export is level with both, so the
DeltaNet graph is exported faithfully. The 8da4w export loses the call on every row:
0.03 to 0.42 where the reference is 0.87 to 0.95, never above 0.5, so greedy decoding
calls on none of them. That is the LFM2.5 picture again, a little less severe, on a
different architecture from a different lab, while the plain-transformer Qwen3 1.7B kept
0.97 under the same recipe (section 3b). The two families that collapse are the two
hybrids with a recurrent or convolutional state carried across positions; the one that
survives is pure attention. Two families is a pattern, not a proof, and nothing here
separates the state-carrying layers from the rest of the recipe's loss.

On the phone this means a Qwen3.5 GGUF would call tools and a Qwen3.5 8da4w export would
not, and the export that does call is the fp32 file, which is too large for a phone.

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
a text with no whitespace at all, where any cut splits a token. The same fuzz against
six tokenizers (`fuzz_all.py`): 0 differing tokens for LFM2.5, Qwen3, Gemma 3, Llama 3.2
and gpt-oss; SmolLM2's GPT-2 pattern differs at line-break runs under this rule and the
old one alike (two tokens on each real prompt), because it reads a trailing run
differently from one before a word, and a cut that suits it makes LFM2.5 worse. The rule
is a heuristic measured per tokenizer, not a general guarantee; the runtime exposes no
tokenizer to cut by. Tests: `ExecuTorchEngineTest`, the two piece-cut cases.

Neither fix is sufficient to explain the collapse: with both applied, the shipped export
still gives the tool token 0.03 or under on the same named rows. Both are measurable losses the
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

## 4b. Reproducibility and a sample nobody chose

**The same measurement twice.** The shipped export run a second time on the same sixteen
prompts through the same runner: every probability identical to four decimals and every
greedy continuation identical (`pte_trailer_hf_out_run2.json`). The Mac runner is
deterministic; the phone's runtime was found not to be (`window-matrix.md`), which is a
reason to measure this on the laptop and confirm on the phone, not the other way round.

**A fresh draw.** Twenty-four rows drawn with seed 11 from the 144 rows the note had not
used (`fresh_sample.py`), the note attached where its rule fires (seven of them), same
bytes into each artifact, the fp32 reference included this time:

| Question | note | GGUF | fp32 reference | fp32 .pte | 8da4w .pte |
|---|---|---|---|---|---|
| When did the latest NFL season begin? |  | 0.00 | 0.00 | 0.00 | 0.000 |
| What is the best-selling video game franchise of all time? |  | 0.38 | 0.10 | 0.05 | 0.016 |
| In January 2023, the NHC revised the fatality data of Hurricane Katrina, increasing the reported death toll from 1,800 to what number? |  | 0.26 | 0.11 | 0.03 | 0.020 |
| On what date was Lunar New Year 2023? |  | 0.00 | 0.00 | 0.00 | 0.000 |
| What is the minimum annual income required for a family of four to be considered middle class in Delaware in 2023, according to the study? |  | 0.02 | 0.01 | 0.00 | 0.006 |
| White Gem is a variety of which vegetable? |  | 0.00 | 0.01 | 0.00 | 0.000 |
| Which team won the 2021 FIFA Club World Cup? |  | 0.19 | 0.01 | 0.00 | 0.005 |
| The longest unbeaten streak of all time in the Premier League is how many matches? |  | 0.30 | 0.12 | 0.03 | 0.002 |
| Which university did Taylor Swift receive her honorary Doctor of Fine Arts degree from? |  | 0.14 | 0.11 | 0.02 | 0.007 |
| When was the first road speed limit set in the UK for powered vehicles? |  | 0.03 | 0.01 | 0.00 | 0.000 |
| Who won the most recent Time Magazine's Athlete of the Year? |  | 0.15 | 0.09 | 0.03 | 0.027 |
| Who was the producer of Black and White? | yes | 0.70 | 0.66 | 0.45 | 0.021 |
| Who was the director of The Last Word? | yes | 0.75 | 0.69 | 0.56 | 0.023 |
| How long do NFL football teams have to get a play off (the play clock)? |  | 0.00 | 0.00 | 0.00 | 0.000 |
| In what country is Northland Communications? |  | 0.01 | 0.00 | 0.00 | 0.002 |
| Who was the producer of Parker? | yes | 0.68 | 0.58 | 0.40 | 0.042 |
| Which vaccine completely eradicated COVID-19 worldwide? |  | 0.00 | 0.00 | 0.00 | 0.001 |
| Who was the screenwriter for Open Fire? | yes | 0.70 | 0.60 | 0.46 | 0.046 |
| Who was the screenwriter for Last Night? | yes | 0.74 | 0.67 | 0.51 | 0.045 |
| In what city was Valdemar III of Denmark born? |  | 0.06 | 0.05 | 0.03 | 0.010 |
| When is the next leap year? |  | 0.01 | 0.00 | 0.00 | 0.000 |
| Skeletal, Smooth, and Cardiac are all types of what? |  | 0.00 | 0.01 | 0.00 | 0.001 |
| Who was the screenwriter for Bleak House? | yes | 0.81 | 0.76 | 0.57 | 0.045 |
| In what country is Călmuș River? |  | 0.02 | 0.01 | 0.00 | 0.003 |

The ordering is the same as in section 2 and it holds on rows nobody chose: on the seven
noted rows the reference sits at 0.58 to 0.76, the GGUF at 0.68 to 0.81, the unquantised
export at 0.40 to 0.57, the shipped export at 0.02 to 0.05. On the seventeen unnoted rows
the reference itself never passes 0.12 and the unquantised export tracks it (0.05 and
under), so the export's low numbers there are the model's, not a loss; the GGUF is the
odd one out, above the reference on four of them (0.38 against 0.10 on the video-game
row), which is Q4_K_M nudging the tool token up rather than the fp32 export pushing it
down. The Mac's greedy decision agrees with what the phone recorded on 23 of 24 rows for
the GGUF (the Taylor Swift row called on the phone at what is 0.14 here) and on 24 of 24
for the export.

**On the phone, with the fixes.** Both reviewers asked for the phone's own run-to-run
behaviour. The Poco (paired that evening, on the charger, screen on) ran the shipped
export through the fixed build on the first 40 decision rows twice, driven-search arm,
greedy (`rerun2-` and `rerun3-` in `tools/eval/results/decisions/`; a first attempt
finished its rows and was killed by the ROM before the pull). Calls: 0 of 40 in both
runs, as on 2026-09-10. Replies narrating a search with no call: 3 and 2 against
4 then. The reply text is identical between the two runs on 10 of 40 rows and
opens with the same word on 38 of 40: the phone's runtime is not bit-reproducible, as
the window matrix found, and the decision it reaches is. So the two app fixes ship the
bytes the template would have and leave the export exactly where the Mac said it was:
the collapse is the artifact's, and it reproduces on the device, run after run.

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

Round one, Codex (gpt-5.6-sol, medium) and Gemini 3.8 Flash (medium), the same brief, no
tools. Both: keep the GGUF, name no knob yet, say the fp32 `.pte`'s own loss, call the two
fixes contributors and not causes, and do not let sixteen selected rows stand for the
suite. Taken: the 160-row replay in section 1, the wording of sections 2, 3, 5 and 6,
the whitespace set in `canonicalJson` narrowed to JSON's four characters, the piece-cut
fuzz and the line-break rule, an escaped-backslash and minified case in the tests.
Gemini's claim that Jinja's `tojson` writes compact JSON is wrong for the templates in
question (transformers' filter is `json.dumps` with default separators, and the rendered
prompt was checked) and was not taken. Codex's blank-schema guard was not needed:
`ToolDefinition` refuses one at construction.

Round two, on the maintainer's three questions (bias, a hard-coded fix, reproducibility),
Mistral Vibe and Gemini 3.8 Flash (Codex was rate-limited that evening and the maintainer
dropped it from this round). Both:
reproducible on the Mac, and the phone's own run-to-run behaviour is still owed (the
phone would not take a connection this session). Vibe: the sixteen rows are a biased
draw; answered by section 4b, where a random draw says the same. Gemini: the unquantised
export "degrades massively on unnoted prompts" against the GGUF; answered by the
reference row in 4b, which shows the reference is as low there and the GGUF is the
artifact above it. Both: the piece cut is a rule measured on one tokenizer; answered by
the six-tokenizer fuzz in section 4, which passes five families and fails SmolLM2 under
the old rule and the new one alike, and by the KDoc now saying so. Both: `canonicalJson`
hard-codes one spelling; the spelling is the one every tool-rendering template on file
writes (Qwen3, Qwen3.5 and GLM `tool | tojson`; Llama 3.2 `tojson(indent=4)`, which the
Llama template already re-indents from the canonical form; Gemma renders no tools), so it
is the templates' spelling and not this model's. Nothing in either review changed the
verdict.
