# Decisions read from the model's distribution instead of its prose (2026-09-17)

**Question.** The agent loop recovers several decisions from what the model writes, with
English patterns that have each been patched for phrasings a phone found: whether a reply
announced or claimed a search (`TurnRunner`, about forty phrases), which tool a denial
meant (`CapabilityDenial.fitting`), whether a watch's finding is news (`WatchVerdict`),
and whether a short pronoun question continues the previous one (`searchQuery`). Each of
these is a closed question the model could answer directly. Can the engine ask it, read
the answer as probabilities, and do that cheaply enough on a hybrid model to be worth
measuring on a phone?

The idea comes from typed judgement services (TypeSafe's System One: Choice, Noul and
Score questions answered as distributions). Their hosted API cannot ship in the app, which
adds no network call, so the phone's own model is the judge. The hosted service is used
only on the workstation, as a second reading of the eval rows (below).

## The engine call

`Session::judge(messages, tools, reasoning, instruction, options)` renders the
conversation with the instruction added to the last user message (or as a new user
message after a reply), reads the prompt, and returns each option's probability as the
reply's first token, renormalised over the options, together with the share of the whole
distribution the options held (`option_mass`). Nothing is generated.

Two rules came from Codex's review of the design (gpt-5.6-luna, xhigh), both of them
blockers as first written:

- **Every option must be one token where it follows the prompt**, or the call is refused
  before anything is read. LFM2.5 spells SEARCH as SE + ARCH, and the probability of SE is
  not the probability of SEARCH. Yes, No and capital letters are single tokens here.
- **The options' mass is reported and floored.** Renormalising two tiny probabilities
  produces a confident-looking split of a question the model did not take up. It is not
  hypothetical: the first wording, "Reply with one word, answer or search", held 0.0008
  and 0.00000002 of the distribution on two test questions, while "Yes or No" held 0.9996
  and 0.99999. The loop ignores an answer under 0.9 (`JudgeQuestions.MIN_OPTION_MASS`).

On a model that refuses partial rollback, the call keeps a rollback point where the
question parts from the conversation, so the turn that follows restores it and reads only
its own header instead of the conversation.

## Measured: the cache claim, on the Mac

`engine_session.cpp` built against the pinned llama.cpp (CPU, static) with a driver that
runs two sessions side by side: one generates turns plainly, the other judges before every
turn. Greedy, 24 tokens a turn.

**LFM2.5 1.2B Instruct QAD Q4_0 (hybrid), seven rounds plus two edge cases:**

| Check | Result |
|---|---|
| judged turn against clean turn, 7 rounds (past the 4 kept points) | identical text, every token's log-probability equal (max difference 0.00) |
| tokens the judged turn read | 5 each round (clean turn: 14 to 28) |
| the judgement itself | 46 to 60 tokens read, 109 to 147 ms on the Mac CPU |
| two judgements in a row, then the turn | identical |
| a judgement after the reply (the watch shape), then a new turn | identical |
| options SEARCH/ANSWER, duplicate options, one option | refused, nothing read; the next turn identical |

**Qwen3.5 2B Q4_K_M (also hybrid) did not match**, from the first turn. A control with no
judgement and no restore found why: the model's greedy output depends on where a prompt
read is split.

| Qwen3.5 2B, same prompt | Output against one unsplit read |
|---|---|
| read twice the same way | identical |
| split 29, 1, 1 (the split a regenerate uses) | identical |
| split 22, 8, 1 | different |
| split 15, 15, 1 | different |
| judged: point restored at 22, then 9 read | different (log-probability off by 0.065 before the text parted) |

Every follow-up turn on that model already reads its prompt split at the previous turn's
end, so the judgement moves a split rather than adding a new kind of divergence. The
equality is asserted on the phone only for LFM2.5 (`JudgeOnDeviceTest`, which passed on two
phones the same afternoon; see below).

## Measured: the search gate, offline

`tools/eval/bench/gate.py` against llama-server with the phone's system prompt and date
exchange, on the decision suite's 160 public rows (RetrievalQA, PopQA, FreshQA, seed 7),
confirmed on 160 held-out rows (seed 8, `decisions-seed8.json`), and on the 48 asks that
need no search from `confidence.py`. A wrong bare answer is the label, graded as
`grade_decisions.py` grades (word-bounded alias matching, after the in-word defect found in
[typesafe-experiments.md](typesafe-experiments.md); the numbers here are regraded with it); the doubt gate (least likely of the first 20 reply tokens,
shipped since 2026-09-10) is read from the same bare answers.

| LFM2.5 1.2B QAD Q4_0 | AUROC, wrong bare answer, seed 7 | seed 8 | the set's need label, seed 7 | seed 8 |
|---|---|---|---|---|
| gate: "are you sure you know ... Yes or No", P(No) | 0.62 | 0.61 | 0.62 | 0.58 |
| gate: "do you need to look it up ... Yes or No", P(Yes) | 0.40 | not run | 0.41 | not run |
| doubt gate, least likely opening token | 0.76 | 0.69 | 0.61 | 0.52 |

The need label is the set's own (RetrievalQA's parametric-knowledge flag, PopQA popularity,
FreshQA change rate) and is null on 25 boundary rows of each seed, which are left out of
that column.

The "need" wording said yes to all 48 asks that need no search and was dropped. The "sure"
wording at P(No) > 0.5:

| | seed 7 | seed 8 |
|---|---|---|
| rows sent | 9 | 7 |
| of which bare answer wrong | 7 | 7 |
| asks that need no search sent | 0 of 48 | 0 of 48 |
| sent by the gate and not by the doubt gate | 7, 6 wrong | 6, 6 wrong |
| wrong answers caught, doubt gate alone | 55 of 60 sent | 52 of 59 sent |
| wrong answers caught, gate or doubt | 61 of 67 sent | 58 of 65 sent |
| lowest option mass on any row | 0.97 | 0.96 |

A 1.2B model's words about its own knowledge carry less than its token probabilities, as
the earlier self-verification verdict expected. But what the gate does send is almost
entirely wrong answers the doubt gate lets stand, on both seeds. That is small (six rows
in 160), and it costs a judgement of about 50 tokens on every turn with search on, so it is
measured as an addition on the phone (`DecisionSuiteOnDeviceTest` arms `gate-search`,
`gate-full`) before it is switched on.

**The same wording on Qwen3.5 2B Q4_K_M** (seed 7, same rows and prompt) is a different
picture, in both directions:

| Qwen3.5 2B Q4_K_M, seed 7 | AUROC, wrong bare answer | AUROC, the set's need label |
|---|---|---|
| gate, P(No) | 0.68 | 0.72 |
| doubt gate | 0.70 | 0.58 |

| Qwen3.5 2B, gate cutoff | rows sent | wrong among them | asks that need no search sent |
|---|---|---|---|
| P(No) > 0.5 | 124 | 93 | 24 of 48 |
| P(No) > 0.7 | 23 | 21 | 3 of 48 |

A 2B model reads its own knowledge about as well as its token probabilities do, and better
against the set's own label of what needs a search. But the cutoff does not carry over: 0.5
sends three rows in four on Qwen and half the asks that need no search, where it sent 9 rows
and none on LFM2.5. That is the trap the doubt gate avoided by using a single-token
probability instead of a mean log-probability, and it reappears here. One constant cannot
serve both models, so `JudgeQuestions.GATE_PROBABILITY` is marked as the LFM2.5 reading and
the switch stays off; a per-model cutoff needs held-out rows for every model it would
serve.

## What shipped, and what is off

Everything below is off in the app. Each is a switch the decision suite or a test turns on,
because no default here has a phone number behind it yet, and Codex's review said the same.

| Decision | Replaces | Where the judgement runs | Fallback |
|---|---|---|---|
| search gate (`honoursGate`) | the NamedSubject note, when it searches; adds to intent and doubt | once before the first pass, search on, not plan mode, not the user's own things, no pasted material | the turn as before, note included |
| denial's tool (`judgesDenials`) | `CapabilityDenial.fitting` keywords | only after the denial patterns fired; candidates are the offered repairable tools with their descriptions, and "none" | the keywords |
| follow-up query (`judgesFollowUps`) | the pronoun join in `searchQuery` | only when a join would happen, at the app's own search | the join |
| watch verdict (`WatchRunner.judgesVerdict`) | the byte comparison | only when the reply has no CHANGED or UNCHANGED line and there is a previous finding; both findings in the question | the byte comparison |

**Left as patterns on review:** plan detection (`readPlan`). The plan push needs numbered or
bulleted steps to put on the board, which the pattern checks exactly, and a semantic pass
would answer a different question at a model pass's cost. The intent patterns stay as the
compiled runtime's only path, since ExecuTorch returns no logits and cannot judge.

## The workstation grader

`grade_decisions.py --typesafe` asks TypeSafe's hosted model two Noul questions per row
(does the reply match an accepted answer, given the aliases; does it say or imply a search
was made) and prints agreement counts beside the pattern grades. Alias containment stays
the metric: correctness needs the gold answers, and the second reading only shows where to
read rows by hand. Requests are cached by body; without `TYPESAFE_API_KEY` nothing is sent.
It is tested against a fake transport (`test_typesafe_judge.py`), and the same client read
1,997 searched rows against the live API (`jev-1.13.0`) for
[typesafe-experiments.md](typesafe-experiments.md), where the grader defect it found is
written up.

## Review of the implementation

Codex (gpt-5.6-luna, xhigh) read the diff adversarially and reported fifteen findings. Each
was checked against the code; ten were fixed, five were not, and the host proof was rerun
after the native changes (all checks still identical on LFM2.5).

| # | Finding | Outcome |
|---|---|---|
| 1 | An option that merged with the prompt's last token fell back to its standalone token, which is not what the model would read there | Fixed: only the prompt's tokens plus exactly one more count, in the engine and in `gate.py` |
| 2 | No logits after the prompt was read left the question in the cache | Fixed: the session resets |
| 3 | Stop during a judgement waited out the prefill | Fixed: coroutine cancellation reaches the native cancel, as it does for a chat |
| 4 | Kotlin refused an empty conversation that native accepted | Fixed: both refuse |
| 5 | The watch judgement rebuilt its prompt and tools instead of using the turn's | Fixed: a finished turn records the prompt and tools it left in the engine, and the judgement extends that record |
| 6 | In-turn judgements left out the pinned plan block the next pass sends | Fixed: judged on the pinned prompt with the pass's own tools |
| 7 | A watch judgement keeps a rollback point the next tick cannot use | Not changed: the next tick's system message carries the new finding, so it parts at the head whatever is kept; the cost is one state copy, and the same point serves the denial repair, whose retry does extend the judged prompt |
| 8 | The gate spends the turn's one app search before it succeeds | Not changed: once a turn is the rule the intent and doubt searches already follow; a search the user declined should not be asked for again in the same turn, and one that failed offline fails again |
| 9 | A background judgement did not yield to a waiting turn | Fixed: it returns null while a turn waits |
| 10 | `gate.py`'s option mass "can exceed one" | Partly: llama-server's top log-probabilities are normalised over the whole vocabulary, so the mass is a true share; an option outside the top 400 is now counted at the 400th's probability and the rows are reported (none so far) |
| 11 | Rows with no need label counted as not needing a search | Fixed, and worse than reported: 25 rows a seed, and the seed-8 file had turned the label "None" into false; the need-label AUROCs above are the corrected ones |
| 12 | The phone test compared text only and allowed eight tokens | Fixed: token pieces and log-probabilities compared, exactly five tokens, and a refused question leaves the next turn reading one |
| 13 | The gate arms are not in the suite's default arms | Not changed: the doubt arms never were either, and the default stays the two by two it was agreed as; stated in the suite's KDoc |
| 14 | The fake engine does not model the native cache | Not changed: cache behaviour is proven on the host and asserted on the phone, and a fake that imitated it would be testing itself |
| 15 | The mass floor's KDoc overstated the measured minimum | Fixed: 0.96 to 1.00 on LFM2.5, from 0.95 on Qwen3.5 2B |

## Measured on phones (2026-09-17 afternoon)

**The cache claim, `JudgeOnDeviceTest`, both tests passing on two chips.** POCO X8 Pro Max
(Dimensity 9400 class, over wireless debugging, screen on, thermal status 0) and a Pixel 10
Pro XL (Tensor G5, Firebase Test Lab). On both, six judged rounds of LFM2.5 1.2B QAD Q4_0
replied token for token and log-probability for log-probability as the clean rounds did,
each judged turn reading exactly 5 tokens, and a refused two-token option left the next turn
reading 1. The options' probability mass agreed between the two phones to seven digits.

| Phone | judgement: tokens read | judgement time, 6 rounds |
|---|---|---|
| Poco (Dimensity 9400 class) | 47 to 60 | 221, 224, 239, 244, 270 ms, and 2,061 ms once (the test holds two copies of the model) |
| Pixel 10 Pro XL (Tensor G5) | 47 to 60 | 336, 377, 439, 459, 500, 726 ms |

**The gate on the Poco, the decision suite through the real loop**, all 160 rows, LFM2.5
1.2B QAD Q4_0, the arms run in alternating order across 40-row chunks so the phone's
warming could not favour one, thermal status 0 on every row:

| Arm | correct | correct, needed | searched when needed | searched when not | median s | mean s | decode tok/s |
|---|---|---|---|---|---|---|---|
| doubt-search (the app today) | 60 | 10 of 75 | 53 | 23 of 60 | 9.0 | 7.7 | 30.8 |
| gate-search (the gate added) | 58 | 11 of 75 | 56 | 25 of 60 | 9.0 | 8.1 | 31.2 |

Paired, the gate was right on 3 rows the doubt arm got wrong and wrong on 5 it got right.
It fired on 10 rows, 4 of them right against the doubt arm's 2 on the same rows; the rest of
the difference is the live web returning different results an hour apart. Decode speed
is untouched, and a row costs 0.4 s more on average. The offline gain (six wrong answers a
split) does not show at this size, so the gate stays off.

**The gate on a Pixel 10 Pro XL** (Tensor G5, Test Lab), the same 160 rows in alternating
40-row chunks: doubt-search 60 correct, gate-search 60, paired 3 rows each way; the gate
fired on 10 rows (3 right against the doubt arm's 2). The unit reported thermal status
LIGHT on 33 doubt rows and 61 gate rows, so its decode speeds (25.4 and 21.2 tok/s) are not
comparable; the answers are. Two chips, the same verdict: no gain at this size.

**Across models, offline** (`gate.py` on the MacBook, both seeds, graded word-bounded; the
cutoff for each model is the one that caught the most wrong answers on seed 7 with at least
80% of its sends wrong and at most 3 of the 48 asks that need no search, then applied
unchanged to seed 8):

| Model | split | gate AUROC, wrong | doubt AUROC, wrong | gate AUROC, need | doubt AUROC, need | cutoff | gate sends / wrong / no-search asks | wrong added beyond the doubt gate | right answers it sends to search |
|---|---|---|---|---|---|---|---|---|---|
| LFM2.5 1.2B QAD Q4_0 | seed 7 | 0.62 | 0.76 | 0.62 | 0.61 | 0.3 | 17 / 14 / 0 | 11 | 2 |
| | seed 8 | 0.61 | 0.69 | 0.58 | 0.52 | 0.3 | 22 / 16 / 0 | 13 | 6 |
| LFM2.5 1.2B Q4_K_M | seed 7 | 0.64 | 0.78 | 0.58 | 0.62 | 0.3 | 10 / 9 / 1 | 9 | 1 |
| | seed 8 | 0.58 | 0.74 | 0.59 | 0.61 | 0.3 | 9 / 9 / 1 | 7 | 0 |
| Qwen3 1.7B Q8_0 | seed 7 | 0.61 | 0.70 | 0.60 | 0.60 | none qualifies | 0 | 0 | 0 |
| | seed 8 | 0.64 | 0.70 | 0.55 | 0.56 | none | 0 | 0 | 0 |
| Qwen3.5 2B Q4_K_M | seed 7 | 0.68 | 0.70 | 0.72 | 0.58 | 0.7 | 22 / 20 / 3 | 7 | 0 |
| | seed 8 | 0.68 | 0.78 | 0.68 | 0.63 | 0.7 | 33 / 28 / 3 | 11 | 2 |

The doubt gate ranks wrong answers better on every model and seed. The gate's value is
additive and model-specific: 7 to 13 more wrong answers searched per 160 rows on the two
LFM2.5 quants and Qwen3.5 2B, at 0 to 6 right answers sent to a search they did not need,
and nothing on Qwen3 1.7B, which is never sure enough to say no. Each model needs its own
cutoff (0.3, 0.3, none, 0.7). The phone runs above used 0.5, where LFM2.5 sends about 9 rows
a seed; the offline gain at 0.3 is larger than anything a 160-row phone run can resolve
against live-web variance, so the gate stays off and the per-model cutoffs are recorded
rather than shipped. LFM2.5 2.6B Q4_K_M was dropped from the sweep: under the harness's
system prompt with no tools offered its bare replies were tool-call markup or empty (its
tokens went to calls and thinking), which the grader cannot score like the others, and on a
24 GB M5 MacBook with other applications open (about 180 MB free, 1.5 GB swapped) it decoded at 2.7 tok/s in swap.

## Decision

Nothing here is switched on. The engine call stays: it is correct on two chips and cheap.
The gate, the denial choice, the follow-up join and the watch verdict stay off, the gate
because two phones and four models showed no gain it could carry across models with one
cutoff. The page read in [typesafe-experiments.md](typesafe-experiments.md) is the one use
that moved answers on a phone, and it stays off until a held-out seed repeats it.

## Owed

- The page read (`readsTopResult`) on held-out seed 8 on the Poco, and a shorter page to cut
  its 15 seconds.
- The denial, follow-up and watch judgements have no public set; each needs recorded cases
  from real traffic before its switch is considered.
