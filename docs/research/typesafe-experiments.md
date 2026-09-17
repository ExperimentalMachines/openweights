# The loop's text heuristics, read by a typed judgement model (2026-09-17)

**Question.** The loop and the eval grader decide several things from English patterns:
whether a reply announced, claimed or lamented a search (`TurnRunner.searchIntent`),
whether a reply was right (`grade_decisions.py`, alias containment), and whether a search
answered anything at all (nothing decides that today). TypeSafe's hosted System One model,
Jev (`jev-1.13.0`), answers typed questions as calibrated probabilities. Used on the
workstation only (the app adds no network call), can it show where those heuristics fail,
and which changes does the evidence support?

Jev is a second reader here, not ground truth: it finds rows worth reading, and every
claim below that matters rests on the rows or on the public gold answers, not on Jev alone.
Every request and answer is cached under `tools/eval/results/typesafe/`, so the tables
reproduce without a key; the per-row reading of section 1, reply in full, is
`matcher-rows.jsonl`.

## 1. The intent patterns, against a second reading

**Data.** Every reply a phone produced without calling a tool in the decision suite's
`bare`, `driven-search` and `driven-full` arms, deduplicated by question and reply
(`typesafe_matchers.py extract`): 2,759 replies from five model builds on four phones
(compiled LFM2.5 1.2B 1,429, Qwen3 1.7B Q8_0 666, LFM2.5 1.2B Q4_K_M 602, QAD Q4_0 62).
Deduplication drops how often a reply recurred, and the corpus covers only the no-call
path from a fresh single question: **what the app does** with each reply comes from
replaying it through the real loop (`MatcherReplayTest`: auto mode, web search the only
tool, no earlier conversation, doubt gate off because the rows carry no probabilities, no
streaming cut). Plan mode, other tools, earlier searches and pasted material are not
exercised. **Jev** answers six independent Noul questions per reply in one request:
announces a search, claims one was made, lacks the knowledge, denies a capability, only
offers to search, answers the question. A flag is a yes at 0.5, with no precedence between
flags; "only offers" is reported but not used, because it said yes on replies that claimed
a search and then offered more. Right and wrong are the fixed grader's (section 2).

| The app | Jev flags a claim, announcement, lament or denial | replies | wrong | right |
|---|---|---|---|---|
| searched | yes | 290 | 255 | 35 |
| denial repair | yes | 49 | 48 | 1 |
| denial repair | no | 1 | 1 | 0 |
| push | yes | 1 | 1 | 0 |
| push | no | 2 | 2 | 0 |
| nothing | yes | 471 | 419 | 52 |
| nothing | no | 1,945 | 1,396 | 549 |

Jev flags a claim, announcement or lament (denials aside) on 811 replies. Every one of the
290 the app searched for is among them, so the patterns act on 36% of those replies and on
nothing Jev does not flag. What they let through is wrong 89% of the time (419 of 471),
against 72% for replies neither reader flags.

| What the app lets through, by shape | replies | wrong | right |
|---|---|---|---|
| everything else (hedged existence denials, "It seems there might be some confusion") | 371 | 322 | 49 |
| an offer ending in a question: "Would you like me to look it up?" | 38 | 36 | 2 |
| "As of my knowledge cutoff in September 2024, ..." | 34 | 34 | 0 |
| announces another tool ("I'll run the script") | 14 | 13 | 1 |
| "... is not widely documented", "... currently unknown" | 11 | 11 | 0 |
| the instructions echoed ("You have working search tools available") | 3 | 3 | 0 |

The two lament shapes over **all** 2,759 replies, which is the count this project takes
before any matcher grows. They were written after reading these replies, so the counts are
the corpus they came from, not a held-out test:

| Shape | runtime | the app today | matched | wrong | right |
|---|---|---|---|---|---|
| knowledge cutoff | compiled | nothing | 40 | 39 | 1 |
| knowledge cutoff | compiled | searched | 2 | 2 | 0 |
| knowledge cutoff | GGUF | nothing | 15 | 15 | 0 |
| knowledge cutoff | GGUF | searched | 1 | 1 | 0 |
| not documented | compiled | nothing | 11 | 11 | 0 |
| not documented | compiled | searched | 3 | 2 | 1 |

**Could the phone's own model do this reading instead of patterns?** The same 2,759
replies through LFM2.5 1.2B QAD Q4_0 on llama-server, the reply put back as its own turn and
one Choice asked after it (A will search, B already searched, C does not know, D none),
scored as `Session::judge` scores (`local_reading.py`). Against Jev's 811:

| Reader | acts on | agrees with Jev | precision | recall |
|---|---|---|---|---|
| the app's patterns | 290 | 290 | 1.00 | 0.36 |
| local model, most likely letter | 940 | 547 | 0.58 | 0.67 |
| local model, P(not D) > 0.73 (the patterns' recall) | 417 | 292 | 0.70 | 0.36 |
| local model, P(not D) > 0.8 | 129 | 108 | 0.84 | 0.13 |

The local reading ranks reasonably (AUROC 0.82, well above the 0.61 to 0.62 the same model
managed judging its own knowledge in `judged-decisions.md`), but at every cutoff it is less
precise than the patterns. The prompt here is not the app's, though: no system message, no
tool definitions, no earlier turns, and four letters where the loop has more routes. So this
keeps the patterns for now; it does not settle an on-device judged intent.

## 2. The grader, against a second reading

`typesafe_results.py` reads the 1,997 unique rows where a web search ran, with the gold
aliases in view: does the reply match an accepted answer, and do the results support,
not address, or contradict it. Reading the disagreements with the grader found a defect:

**Alias containment was not word-bounded.** `correct()` tested `alias in reply` on
normalised text, so a short alias matched inside any word: "CA" in "lo**ca**ted" (Canada),
"US" in "foc**us**" or "**US**S", "S. C." (normalised to "s c") across "it**s c**apital",
"2" inside "2024". It now matches whole words (`contains`, tested in
`test_grade_decisions.py` on the phone rows, multi-word, punctuated, numeric, decimal, date
and CJK aliases), and `findable` uses the same test. Over the 8,500 unique graded decision
rows, 251 lose credit and none gain it; each of 61 arms drops 0 to 8 rows of 160 (Poco
LFM2.5 Q4_K_M doubt-search 63 to 58, intent-search 54 to 50; the compiled model's
intent-search 46 to 41). Of 76 paired comparisons between arms on the same phone and model,
71 keep their direction; the 5 that change are within two wins either way (bare against
driven arms on the compiled model and on Exynos, first-search against intent-full on the
compiled model). The doubt gate's two comparisons against the intent rule hold (Poco 15/6
becomes 14/6, held out 5/1 becomes 6/1). The absolute "correct" columns written before
2026-09-17 in [retrieve-or-answer.md](retrieve-or-answer.md) and
[recommended-runtime.md](recommended-runtime.md) are high by that much; the gate numbers in
[judged-decisions.md](judged-decisions.md) are regraded.

With the fixed grader, the grader and Jev agree on 1,923 of the 1,997 searched rows (both
right 485, both wrong 1,438). Of the 74 they part on, 34 are Jev crediting an answer the
aliases miss ("Osgood Perkins" for the set's "Oz Perkins"), and 40 are the grader crediting a
reply Jev does not, which is where to read next.

## 3. Whether a search answered the question

Sufficiency, asked with no gold answer in view: do the results state the answer to the
question? 1,422 unique requests (rows that share a question and its results share one)
cover the 1,997 rows:

| Jev: the results state the answer | replies right |
|---|---|
| yes (1,087 rows) | 465 (43%) |
| no (910 rows) | 60 (7%) |

This is an association, not an effect: a hard question makes both poor results and a wrong
reply likely. Jev and the grader's `findable` (a gold alias in the results, now
word-bounded) agree on 527 of 612 findable rows and part on 560 of 1,385 others, mostly
paraphrases and alias gaps. In 203 rows the results contradict a reply graded wrong: the
model had a different answer in front of it.

## What the evidence supports

Reviewed by Codex (gpt-5.6-luna, xhigh), which read the scripts, caches and this note; its
verdict per proposal is given with what was changed in response.

| # | Proposal | Evidence | Verdict |
|---|---|---|---|
| 1 | Word-bounded alias containment in the grader | 251 of 8,500 rows credited by in-word matches, none newly credited | **Done**, with tests; 5 near-tie comparisons change direction; the two earlier notes' absolute columns are high by 0 to 8 rows an arm |
| 2 | Teach the loop the knowledge-cutoff and not-documented laments | 72 matches, 70 wrong and 2 right, on the corpus they were read from | **Supported with changes.** Not as `CapabilityDenial.lamentsUnknown` itself: that predicate also routes `push` to the denial repair, whose guards differ from the intent rule's. A search-only predicate, required to carry an explicit inability or unresolved answer (a knowledge-cutoff caveat can open a complete answer), checked on held-out replies, then an `intent-search` arm on a phone |
| 3 | Treat "Would you like me to look it up?" as the decision to search | 38 replies, 36 wrong | **Not supported.** An offer is not consent; the ends-in-a-question guard stays |
| 4 | An on-device "do these results state the answer" judgement | right 43% against 7% | **Supported as a measurement only.** Confounded by question difficulty. Measured since on the Poco as a page read: 66 correct against 60, paired 6 to 0, at 15 more seconds on the rows that read; the cutoff was tuned on those rows, so a held-out seed and a shorter page are next |
| 5 | Reject on-device judged intent | local precision 0.58 at the most likely letter, below the patterns at every cutoff | **Not supported as a rejection.** The prompt lacked the app's context; patterns stay as the interim default |

**Proposal 2 on phones (2026-09-17 afternoon).** Built as `honoursUnresolved`, a search-only
kind in `TurnRunner.searchIntent` read on the first two sentences, off by default. Checked on
1,933 held-out no-call replies first: a knowledge-cutoff caveat with a negation matched 20
(19 wrong), the undocumented shape 12 (11 wrong). On the phone, which model produces the
shape decides everything. Over every recorded row, 73 of 3,328 compiled LFM2.5 replies carry
it against 3 of 1,040 QAD Q4_0 GGUF replies, and the doubt gate already covers the GGUF.

| Poco, 160 rows, thermal 0 | correct | correct, needed | searched when needed | searched when not | fabricated | mean s |
|---|---|---|---|---|---|---|
| QAD Q4_0 GGUF, doubt-search | 60 | 10 of 75 | 53 | 23 of 60 | 0 | 7.7 |
| QAD Q4_0 GGUF, lament-search | 59 | 11 of 75 | 53 | 23 of 60 | 0 | 8.8 |
| compiled 8da4w, intent-search | 48 | 5 of 75 | 24 | 10 of 60 | 0 | 7.7 |
| compiled 8da4w, lament-search | 46 | 6 of 75 | 26 | 12 of 60 | 3 | 7.3 |

On the GGUF the new kind never fired. On the compiled model the phone's log counts 3
`unresolved` searches across the lament runs, beside 60 claimed and 42 denied; the paired
difference (6 rows won, 8 lost) is the compiled runtime's known run-to-run divergence, not
the rule. Safe and rare: it stays off, since three firings in 160 rows cannot show a benefit.

The same comparison on a Pixel 10 Pro XL (Tensor G5, Test Lab), compiled model, 160 rows:
intent-search 43 correct, lament-search 42, paired 7 rows won and 8 lost; the unit's log
shows the new kind firing once. Two chips, the same reading: the rule is rare on the
compiled model and absent on the GGUF, and a 160-row phone run cannot see it.

**Proposal 4 on the phone (2026-09-17 evening): reading the top page when the snippets fall
short.** Built as `readsTopResult` (off by default): after a search, the model is asked
through `Session::judge` whether the results state the answer, and above a cutoff the app
fetches the first result through `fetch_url`, under that tool's own switch, before the model
answers. Offline first, on the MacBook: the 1.2B QAD model's P(No), with the results in a
user turn, ranked Jev's "results lack the answer" at AUROC 0.79 and a wrong reply at 0.77 over
2,045 recorded searched rows (`local_sufficiency.py`), and 0.05 was chosen there. On the Poco
the same model, reading the results in the tool turn the app uses, put P(No) at a median of
0.000 and a 90th percentile of 0.003, so a first 160-row run at 0.05 read no page at all.
Its readings still ranked (AUROC 0.74 for a wrong reply over 84 searched rows), and the
cutoff was moved to 0.0005 on those rows, which makes the rerun below a check of the
mechanism on the rows it was tuned on, not a held-out measure.

| Poco, LFM2.5 1.2B QAD Q4_0, 160 rows, thermal 0 | correct | correct, needed | rows reading a page | mean s | median s |
|---|---|---|---|---|---|
| read-base: search and fetch_url offered, fetch switched on | 60 | 12 of 75 | 0 | 10.4 | 11.4 |
| read-search: the same, and the page read | 66 | 14 of 75 | 36 | 15.1 | 13.6 |

Paired, the page read was right on 6 rows the base got wrong and wrong on none (exact sign
test p = 0.03). It fired on 36 of 113 searched rows; 28 fetches succeeded (median 1.1 s), and
on those 36 rows 9 replies were right against the base's 4. The model never called
`fetch_url` itself in the base arm. The cost is on the rows that read: 28.1 s against 12.9 s,
most of it reading up to 4,000 characters of page at phone prefill speed.

**Decision (2026-09-17).** Nothing from these experiments is switched on. The grader fix is
in, because it is a defect. The lament kind stays off, too rare to measure. The page read
stays off until the same arm on held-out seed 8 repeats the paired result without a cutoff
tuned on its rows, and until the page it reads is short enough to cost less than 15 seconds.

Codex's other findings, and what was done: the first write-up used two label functions (a
precedence in the local-reading score, a veto by "only offers" in the matcher table), now
one set of independent flags everywhere; the first table dropped four rows, now all 2,759;
right and wrong were graded with the defective grader, now regraded; the shape counts were
not reproducible from the scripts, now `typesafe_matchers.py report` writes every row with
its full reply, flags, grade and shape. Its claim that the sufficiency table could not be
reproduced from the cache was a misreading: the sufficiency request carries no reply, so
1,422 cached requests serve all 1,997 rows, and the report runs from the cache alone.

Cookbooks read for fit (TypeSafe docs, 2026-09-17): classification using confidence and
self-consistency (read a band rather than a 0.5 split on the judged decisions in
`JudgeQuestions`, which only floors the options' mass today); pre-parsed value extraction
and citation checking (the shape of experiments 1 and 2: patterns over-find, a reader
decides; on the phone the reader measured too weak so far, on the workstation it found the
grader defect); classifying RAG passages and line-by-line search (experiment 3's
sufficiency question). They do not fit the tool-call parsers (syntax, not meaning), control
token sanitising (security must not depend on a model the page can talk past), the
repetition detector and canvas grader (exact structural signals), date extraction (no date
text is parsed in code), re-ranking (reordering results already in context breaks the
prompt's byte stability), SDE cascades (no hosted model may be called from the app), or
skill suggestion (a question every turn is the search-first route rejected on 2026-09-10).
