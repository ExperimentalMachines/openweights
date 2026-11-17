# LFM3 1.7B: the app's search rules on the GGUF, and the compiled file

2026-11-15. One Snapdragon 8 Elite Gen 5 (SM8850) unit per run, reserved through QDC, driven
over adb with `tools/eval/bench/run_decisions.sh`. All 160 public questions of the decision
suite, thinking on, one tool (`web_search`), real web search. Graded with
`python3 -I tools/eval/bench/grade_decisions.py`. Rows:
`tools/eval/results/decisions/qdc-sm8850-{gate,pte}-decisions-*` and, for the baseline,
`qdc-full-decisions-LFM3-1.7B-Q4_K_M-driven-search-think.jsonl` from
[llama-cpp-v080.md](llama-cpp-v080.md).

Two questions. That note left the app's own search rules unmeasured on LFM3: does the GGUF
behave with them on? And LFM2.5's compiled 8da4w file lost every tool call
([executorch-state-and-recipes.md](executorch-state-and-recipes.md)): does LFM3's?

## Files

| File | Source |
|---|---|
| `LFM3-1.7B-Q4_K_M.gguf` | `LiquidAI/LFM3-1.7B-GGUF` |
| `lfm_3_1_7b_xnnpack_8da4w.pte` | `software-mansion/LFM3-ExecuTorch`, `1_7b/xnnpack/`, round to nearest |
| `LFM3-1.7B-8da4w-gptq-4k.pte` | `experimentalmachines/LFM3-1.7B-ExecuTorch`, `xnnpack/`, GPTQ codes, 4k window |

The app could not open a compiled LFM3 before this branch: its compiled path renders the
prompt itself and had no LFM3 template. `Lfm3Prompt` transcribes `LiquidAI/LFM3-1.7B`'s
`chat_template.jinja` and is held to it byte for byte by fixtures that
`tools/executorch/render_reference.py` renders from the real template. The GGUF carries the
same template file, so both runtimes read the same prompt. `underOpener` now accepts a think
opener without the trailing newline, because LFM3's opener has none and Qwen3.5's has one.

## Results

| 160 questions, SM8850 | GGUF, model alone | GGUF, intent rule | GGUF, intent and doubt gate | .pte, round to nearest | .pte, GPTQ |
|---|---:|---:|---:|---:|---:|
| Arm | driven-search | intent-search | doubt-search | driven-search | driven-search |
| Searched when needed, of 75 | 59 | 74 | 74 | 74 | 74 |
| Of those, the model's own call | 59 | 65 | 63 | 74 | 74 |
| Searched when not needed, of 60 | 23 | 34 | 34 | 55 | 52 |
| Correct, of 117 graded | 60 | 65 | 67 | 71 | 75 |
| Correct where a search was needed, of 42 | 12 | 15 | 16 | 15 | 18 |
| Correct where it was known, of 60 | 45 | 48 | 49 | 53 | 53 |
| Fabricated search claims, of 160 | 0 | 0 | 0 | 0 | 0 |
| Quoted the app's instructions | 11 | 0 | 0 | 15 | 10 |
| Median passes | 2 | 2 | 2 | 3 | 4 |
| Median seconds | 18.5 | 20.1 | 20.8 | 36.4 | 45.3 |

**The compiled file keeps tool calling.** Unlike LFM2.5, plain round-to-nearest 8da4w calls
on its own on 74 of 75 questions that need a search, more than the GGUF's 59, with no
fabricated claims. GPTQ codes are not needed to keep calls on LFM3: they change correct by
4 of 117, within what one run can show.

**The failure flips to over-searching.** Both compiled files search on 52 to 55 of the 60
questions the model can answer from memory, against 23 for the GGUF alone, and take more
passes. Correct on those known questions did not drop (53 against 45), so the cost is time
and egress, not accuracy, on this suite.

**The app's rules close the GGUF's gap.** The intent rule takes searched-when-needed from
59 to 74, and the doubt gate on top adds nothing measurable here (74 and 74, correct 67
against 65). LFM3 already says when it is unsure, so the gate has little left to catch.
Searching more did not make answers right: correct where a search was needed is 12 to 18 of
42 in every column, because the snippets rarely hold the answer.

## Limits

- One run of each arm, one phone unit per run, and units in the QDC pool differ. Gaps of a
  few questions are not established differences.
- The compiled runs' times were taken with the phone throttled and are not comparable with
  the GGUF's; no speed claim is made.
- The GGUF baseline ran on the v0.8.0 build before the last-round change in
  [llama-cpp-v080.md](llama-cpp-v080.md); the gate arms ran after it.
- One tool. Whether a compiled LFM3 decides as well with the full tool list is not measured.
- Verified through the decision suite's instrumented test, not in the app's chat screen.
