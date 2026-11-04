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

Not run: the public benchmarks on v0.8.0, the decision suite beyond the nine rows below, and vision through the app's
interface (`MultimodalTest` covers it in the engine).

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
- **Reasoning can continue past the budget.** On two of the nine, the model kept reasoning in the visible reply after the
  forced close, until the suite's 600-token cap. A reply in the app on the SM8850 ended much the same way: after four
  rounds of search and fetch, some refused with HTTP 403, the last reply was visible reasoning that stopped
  mid-sentence. Why it stopped there is not established. The budget of 128 was measured on Qwen3 1.7B, not on LFM3, so it
  should not be changed without a run on LFM3.

## What this leaves open

- **A tool call written inside the reasoning.** LFM3 and Qwen3.5 2B list their call tag as an end tag, so they are covered.
  LFM2.5 and Qwen3 1.7B do not, and a host replay on LFM2.5 2.6B shows the same throw if a call came before the block
  closed. No real reply has been seen doing that, so nothing was built for it. None of the 15 models checked has a token
  that merges a tag with other text, so matching on the end of the reply does not miss one
  (`tools/snapdragon/results/2026-11-04-qdc-v080/vocab-tags.txt`, from `vocab.cpp` beside it).
- **Fit on phones where KleidiAI repacks heavily.** The POCO's memory cost is small, but the 8 Elite Gen 5's is not, and
  `FitEstimator`'s fixed 512 MiB overhead does not always absorb it. At 32k context on that phone LFM3 Q4_K_M holds
  2597 MiB of native heap against an estimate of 2264, about 15% over, while LFM2.5 QAD-Q4_0 holds 1398 against 1559
  (`tools/snapdragon/results/2026-11-04-qdc-v080/memory-sm8850.txt`). So a model near the limit on such a phone can be
  called a fit and then run out of memory. Not fixed here: a fix must read a runtime capability, not a SoC name, and needs
  more files measured than these two.
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
