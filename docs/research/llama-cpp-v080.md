# llama.cpp b10549 to v0.8.0

2026-11-04. The pin moved from b10549 (2026-08-21) to v0.8.0 (b11960, 2026-10-28), the only 0.8.x tag at
the time. llama.cpp switched to v0.x.y tags in August 2026, so "0.8" is this tag and not an old version.
`engine_session.cpp` needed 22 changed lines for the new chat and parse APIs. Nothing else in the app did.

## What was checked

On the POCO X8 Pro Max (Dimensity 9500s, MT6991, 12 GB, the armv9.0_1 CPU variant), awake and on battery:

- `ktlintCheck detekt test jvmTest`, `:app:assembleDebug`, the test APKs and `:app:bundleRelease` all pass.
  `verifyJniSymbols` finds all 17 names and 2 native signatures after R8.
- Engine on-device tests: 18 passed, 0 failed, 4 skipped. The skips are environmental: two sliding-window
  tests need a Gemma file at `bench/gemma.gguf`, GPU offload needs a GPU backend, and two-picture vision
  needs a second test image. `everyStopIsMeasuredOnEveryPicture` has no assertions, runs for over 16 minutes
  and was stopped; it is a measurement, not a regression check.
- The same three-turn chat in the debug app on both versions, LFM2.5 1.2B QAD-Q4_0, with a real web search:

  | Turn | v0.8.0, prefill and decode | b10549, prefill and decode | Tokens in, both | Cache hit, both |
  |---|---|---|---|---|
  | "hi" | 74 and 28 tok/s | 65 and 26 tok/s | 846 | 92% |
  | Web search | 92 and 29 tok/s | 99 and 27 tok/s | 2.1k | 79% |
  | Follow-up | 82 and 27 tok/s | 89 and 26 tok/s | 3.6k | 80% |

  Token counts and prefix reuse are identical, and the speeds are within noise on a warm phone.
- The minified release build, installed under a temporary `.rc` suffix, answered the same chat with an empty
  crash buffer. This is the check that would have caught the abort in
  [release-token-callback-abort.md](release-token-callback-abort.md).

Then on a Snapdragon 8 Elite Gen 5 (QDC SM8850, Android 16, 23 GB), with the budget fix below in place:

- Engine on-device tests: 23 passed, 0 failed, 1 skipped (no audio model). That covers what the POCO skipped: the
  sliding window on Gemma, GPU offload and two-picture vision.
- `HarnessSmokeTest` and `LongConversationOnDeviceTest` pass. `ToolTurnOnDeviceTest` had been skipping on every model,
  because its `check_order` tool starts switched off and so was never offered (the log read `tools=[]`). With it
  switched on, LFM2.5 1.2B QAD-Q4_0 and LFM3 1.7B pass. Qwen3 1.7B Q8_0 fails the test's rule of exactly two passes: it
  asks twice, and the second ask is answered from the first run, before a correct answer. b10549 does the same,
  so this upgrade did not cause it.
- The minified, signed release build, with `verifyJniSymbols` finding all 17 names and 2 signatures after R8, answered
  a three-turn LFM2.5 chat with a live search (846 tokens and 92% reuse on the first turn, the same as the POCO) and a
  four-round LFM3 chat. The crash buffer stayed empty.

Raw data: `tools/snapdragon/results/2026-11-04-qdc-v080/`; `inst-engine-after-review.txt` is the 24-test run. An
adversarial Codex review of the first push found the closing-tag confidence leak, tests that would have passed with no
budget at all, and overstatements in this note, all corrected here.

Not run: the public benchmarks on v0.8.0, the whole decision suite (only the must-search runs of 20 to 40 questions below), and vision
through the app's interface (`MultimodalTest` covers it in the engine).

The final build then passed on the POCO as well: `ReasoningBudgetOnDeviceTest` 5 of 5, `ToolTurnOnDeviceTest` and `HarnessSmokeTest` 2 of 2,
with an empty crash buffer (`tools/snapdragon/results/2026-11-04-poco-v080/inst-*-final.txt`).

## Should KleidiAI stay on

The October trial on a Snapdragon 8 Elite Gen 5 suggested turning it off, because v0.8.0's new Q4_K and Q6_K
kernels repack weights and raised memory there, by up to 41% in the app engine after loading (Qwen3.5 2B Q4_K_M,
1948 to 2740 MB of RSS), with no decode gain on that chip. Raw data: `tools/snapdragon/results/2026-10-31-llama-v080/`.
The POCO had not been measured, so it was, with `llama-bench` built from the app's CMake flags, 512 tokens in
and 64 out, 3 repetitions, 6 threads, in both run orders. The two orders mostly agree to about 1%. The largest gaps
are Q8_0 without KleidiAI (decode 15.7 and 16.6 tok/s, peak 3636 and 3361 MB) and b10549's QAD-Q4_0 decode (about 3%).
`small.gguf` in the raw log is the Q4_K_M file.

| Model | Build | Peak memory | Prefill, tok/s | Decode, tok/s |
|---|---|---|---|---|
| LFM2.5 1.2B QAD-Q4_0 | b10549 | 1416 MB | 161.3 | 43.5 |
| | v0.8.0 | 1442 MB, +1.9% | 145.7, -9.7% | 37.9, -12.8% |
| | v0.8.0, no KleidiAI | 1419 MB, +0.3% | 163.9, +1.6% | 40.8, -6.2% |
| LFM2.5 1.2B Q4_K_M | b10549 | 1481 MB | 110.9 | 33.1 |
| | v0.8.0 | 1534 MB, +3.6% | 125.8, +13.4% | 34.4, +3.9% |
| | v0.8.0, no KleidiAI | 1485 MB, +0.3% | 111.3, +0.4% | 33.8, +1.9% |
| Qwen3 1.7B Q8_0 | b10549 | 3522 MB | 89.7 | 24.6 |
| | v0.8.0 | 3542 MB, +0.6% | 87.5, -2.5% | 25.6, +3.7% |
| | v0.8.0, no KleidiAI | 3498 MB, -0.7% | 68.5, -23.6% | 16.2, -34.3% |

KleidiAI stays on. Turning it off costs Q8_0 a third of its decode speed on this phone and saves about 1 to 4%
of memory. Raw data: `tools/snapdragon/results/2026-11-04-poco-v080/ab.log`.

## LFM3 1.7B, and the budget bug it found

`LLM_ARCH_LFM3` is in v0.8.0 and not in b10549, so this upgrade is what makes LFM3 load at all. Its first test in
the app, Q4_K_M with thinking on, failed on one prompt every time: "Will it rain in Tokyo tomorrow?" died after
"Thought for 7 s" with `Unexpected empty grammar stack after accepting piece: \n (207)`, three times out of three,
because the tool pass is greedy.

The template lists three tags that end the reasoning: `\n</think>`, `</think>` and `<tool_call>`. The model writes
the second. The reasoning budget watched only the first, so it never saw the block end, kept counting into the reply,
and at 128 tokens wrote a closing tag there:

- inside a tool call, where the grammar has no way to accept it, and the turn failed;
- inside an answer, which came back as `The capital of France\n</think>\nThe capital of France is Paris.`

Both were reproduced on the POCO by `ReasoningBudgetOnDeviceTest` before the fix
(`tools/snapdragon/results/2026-11-04-poco-v080/before-fix-budget.txt`). The budget now stops at any listed tag: a budget
landing in the call returns the same call and text as no budget, one landing in the answer leaves the answer byte for
byte the same, and a control confirms that a budget inside the reasoning still closes it. Qwen3.5 2B lists the same
three tags. llama-server passes all of them to its own budget (`reasoning_budget_end_tags`).

The closing tag no longer counts towards the answer's confidence either. It belongs to the reasoning, but its
probability reached the reader of the answer's opening tokens (a logprob of -0.68 in the test before this), so one
unlikely tag could have started a search for an answer the model was sure of. How often that happened was not measured.

So does LFM3 reject tools and say it does not know? On what was tried, no. It used a tool on all five prompts tried
in the app, though four of those ran before the fix. After it, the Tokyo prompt searched, opened a forecast page and
answered yes at 92% precipitation, where open-meteo gave 100% and 10.9 mm for the same day. This is not a measurement of
retrieval: the decision suite was stopped at nine of its 160 rows by a low battery, and those rows are not committed.
On those nine, all labelled as
needing a search, LFM3 searched on eight and LFM2.5 1.2B on five, and the one miss was a confident wrong answer, not a
refusal. Two limits showed up in them:

- **It is slow here.** A median of 69 s a question against 8.5 s for LFM2.5 on the same rows, at about 3 passes each and
  up to four searches. The app decoded it at 14 to 17 tok/s.
- **Reasoning could continue past the budget.** On two of the nine, the model kept reasoning in the visible reply after the
  forced close, until the suite's 600-token cap.

### What the budget does to LFM3, and the note that came of it

Raising the budget does not help. On the Mac's GPU, with the app's prompt and recorded search results, 40 public questions
that need a search, greedy, the pass after a search result (`tools/snapdragon/results/2026-11-04-qdc-v080/budget-sweep/`):

| Budget | Ran to the 600-token cap | Finished with another call | Finished without one |
|---|---|---|---|
| None | 11 | 24 | 5 |
| 256 | 3 | 27 | 10 |
| 128 | 1 | 20 | 19 |
| 128, with a note | 0 | 12 | 28 |

So the budget stays at 128, and a forced close now says why: "I have thought enough, so I will act on what I have now."
llama-server has the same idea as `reasoning_budget_message`. Measured through the app's own loop with real web search on the
SM8850, LFM3 Q4_K_M, thinking on, on 30 public questions that need a search:

| | Without the note | With it |
|---|---|---|
| Correct | 10 | 10 (one gained, one lost) |
| Median time a question | 34.2 s | 20.7 s |
| Searches | 66 | 40 |
| Questions searched at all | 27 | 24 |
| Hit the cap | 3 | 2 |
| Said it could not find or does not know | 2 | 5 |

It is faster with the same accuracy, and it gives up a little sooner. It reduces the run-on and does not end it: replies can still
go on reasoning, mostly on the last pass, which has no budget. This is one run on one model: the speedup is 39.5% in median time here,
not a general figure. The note is English and stays in the reasoning, so later turns see it too; non-English conversations are
untested. On Qwen3 1.7B Q8_0, 20 questions, the numbers were 2 correct against 0 and searching on 7 against 10, but crashes took 2
questions with the note and 5 without, for the reason in the next section, so that run cannot show whether it helps or harms there.

LFM3's QAD-Q4_0 file is smaller and a little faster than Q4_K_M on the SM8850: 43 against 40 tok/s, and 272 MiB less at peak.

### Compared with LFM2.5, and whether the upgrade changed LFM2.5

LFM2.5 1.2B Q4_K_M made the same search decision on every one of 40 must-search questions on v0.8.0 and on b10549: 15 searched on
both. So an answer given without a search is the model, not the upgrade. Both scored 0 of 40 on those questions, and 2 of 40
in September on the POCO: they are a long tail the model cannot answer alone and the snippets rarely hold.

On the 30 questions both ran on the same phone, LFM3 got 10 right to LFM2.5's 0, searching on 24 against 13, at 20.7 s against 7.3 s.
That is only the half of the benchmark where a search is needed, so it says nothing yet about needless searches on the other half,
and it is not a verdict on the shortlist.

## A crash this found, older than the upgrade

Qwen3 1.7B with thinking on failed 5 of 20 questions on the SM8850 with `generation failed: string_view::substr`, on the pass after a
tool call. It is not llama.cpp. `Session::tokenize_prompt` (2026-09-05) took a span's token index, valid only for the reply that owns
the span, and used it on every remembered reply to find that owner again. On a shorter reply that read past the end of the
vector, and the garbage offset made `compare` throw. Remembered replies outlive a chat, so an older short reply could set it off. The
span now keeps a pointer to its reply's tokens, and nothing looks it up.

The read is undefined behaviour, so a throw is not a reliable test: the same old build threw 6 times on 20 questions in one run and
none in another. AddressSanitizer is. Running the app's real `engine_session.cpp` on the Mac (`reply-splice-crash/`), it reports a
bad read in `tokenize_prompt` on the old code by the second question, and 0 across 35 turns on the new. The code is the same at
b10549, so the Play release likely has this bug too. The planned run of the old engine on the phone did not finish, because the
reservation ended. No automated test guards this: CI has no native tests, and a device test cannot fail reliably on it.

Qwen3 also often writes its own closing tag straight after the budget's, in 10 to 14 of those 20 replies, and it reached the answer as
a literal `</think>`. `parseAssistantReply` now drops a tag repeated at the very start of the answer.

## The fit estimate on phones with SME

v0.8.0's KleidiAI kernels pack weights to the SME vector length, so on a CPU with SME the file's size no longer says what the
weights cost. `FitEstimator` promised file + KV cache + 512 MiB, and that was too little. Measured in the real app on the
SM8850, one turn at 32k context, peak memory against that estimate, as a share of the file:

| Quant type | Files | Past the old estimate | Allowance now |
|---|---|---|---|
| Q5_K_M | 1 | 45% | 55% |
| Q4_0, including QAD | 3 | 30 to 51% | 60% |
| Q4_K_M | 5 | -1 to 88% | 95% |
| Q8_0 | 2 | 104 and 105% | 115% |
| Q6_K | 2 | 151 and 159% | 165% |

The estimate now adds that share when `/proc/cpuinfo` lists `sme`, which the kernel prints from the same bit llama.cpp checks.
The SM8850 lists it and the POCO does not, so nothing is named by chip and the POCO's estimates do not change. A type with no
measurement takes the largest share. A real search turn peaked at most 20 MiB above the short turn, so each allowance is 6 to
10 points above the worst file. F16 and BF16 add nothing, because no kernel packs them: an F16 file of 13,153 MiB held 13,141 in memory
on an Apple M5. Every measured file now peaks within its promise (`FitEstimatorTest`, raw record in
`tools/snapdragon/results/2026-11-04-qdc-v080/memory-sm8850.txt`).

The Mac could not stand in for the phone: an M5 has SME2 with a 64-byte vector and packs the same files to 1.6 to 2.2 times
their size (`repack-buffers.cpp` beside that record), so a phone with a wider vector than the SM8850's may need more than this.

## What this leaves open

- **A tool call written inside the reasoning.** LFM3 and Qwen3.5 2B list their call tag as an end tag, so they are covered.
  LFM2.5 and Qwen3 1.7B do not, and a host replay on LFM2.5 2.6B shows the same throw if a call came before the block
  closed. No real reply has been seen doing that, so nothing was built for it. None of the 15 models checked has a token
  that merges a tag with other text, so matching on the end of the reply does not miss one
  (`tools/snapdragon/results/2026-11-04-qdc-v080/vocab-tags.txt`, from `vocab.cpp` beside it).
- **A `<think>` written in a visible answer reopens the budget**, on the pass that has one, while the Kotlin parser treats
  it as answer text. It was like this before the upgrade and has not been seen happening.
- **Templates that print the date near the head** (Llama 3.2) change prompt bytes at midnight, so one turn re-reads the
  conversation. Also unchanged by the upgrade.
- **QAD-Q4_0 is slower in `llama-bench` on the POCO**, by 10% on prefill and 13% on decode, while Q4_K_M, the
  file that carries the shortlist's grades, gains. The cause is not isolated, and the in-app chat above does
  not show it.
- **Replies change on some files.** On the 8 Elite Gen 5, Gemma 3 1B Q4_K_M's greedy replies differ with the new
  Q4_K kernels and its perplexity rose 1.2%. LFM2.5's greedy replies were identical there.
- **The parse at the end of a turn is content-only** at both pins, because no PEG parser is loaded. Tool calls
  come from the Kotlin `ToolCallParser`. The comment at that site claimed otherwise and was corrected.
