# How a reply ended decides whether the next turn reuses the cache

2026-11-16. The engine keeps a record of what the runtime's cache holds and extends it on the
next turn only when the reply ended cleanly, because a reply cut short leaves a cache it cannot
describe. Two ways a reply ends without the end marker reaching the engine were found this week.
One is a mistake in an export, which the engine was right to treat as a cut; the other is
MediaTek's NPU runner, where the engine was wrong to, and now is not. Measurements are in
`tools/snapdragon/results/2026-11-16-coldwarm-matrix/` (README sections "The POCO and MediaTek's
NPU" and "Why Qwen3's replies ended on "!"").

## Qwen3 INT8-INT4: the runtime's end token was "!"

In sampled chat about a third of Qwen3's second turns re-read the whole conversation (25 of 75 on
four chips overnight; 5 of 15 and 3 of 14 on a Galaxy Tab S10+ the next day). Every miss followed a
reply the runtime ended itself, and every one recorded ended on `"!"`.

- The export (`larryliu0820/Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK`, the one behind the talk's
  first figure) has no `get_eos_ids` method, only the older `get_eos_id`. ExecuTorch 1.6.0's
  runner then ends a reply on the tokenizer's own end token
  (`extension/llm/runner/llm_runner_helper.cpp`, `get_eos_ids`).
- A tokenizer opened from a `tokenizer.json` file reads nothing beside it, and takes as its end
  token the one special token named like an end; Qwen3 has six, so it keeps its default, 0
  (`extension/llm/tokenizers`, `hf_tokenizer.cpp`, `tokenizer.h`). Token 0 in Qwen3 is `"!"`.
- So the runtime stops at any standalone `"!"` and never feeds it back. In the 146 first replies
  of completed pairs recorded on this export `"!"` appears 27 times, always as the last character
  and never inside a reply. This is read from the runner and tokenizer sources and the replies'
  text; no token-id trace was taken on a phone. The app's own Qwen3 export lists `get_eos_ids` = [151645, 151643]; its replies wrote
  `"!"` and carried on to `<|im_end|>` (16 of 16) with no runtime end line.
- Keeping the record across such a stop (a prototype, never shipped) left the cache one token
  short: in the three pairs whose first reply ended on `"!"`, the retained-prefix arm reached one
  position fewer than the reset-cache arm (1,467 against 1,468 and twice more). The shipped
  engine's choice to drop the record is right for this export.
- The app cannot repair it. The tokenizer reads `tokenizer_config.json` only when it is handed a
  directory, and ExecuTorch's Android `LlmModule` refuses any tokenizer path that is not a file
  (`ExecuTorchRuntime.validateFilePath`); a build that handed it a directory failed to open the
  model ("path is not a file") on the tablet. The fix belongs in the export (`get_eos_ids`) or the
  runtime (reading `get_eos_id` when `get_eos_ids` is absent). Every export of ours we checked
  declares `get_eos_ids` (Qwen3 8da4w-gptq 4k and 2k, LFM2.5 1.2B 4k and 32k, LFM2.5 2.6B,
  LFM2.5-VL 450M).

## MediaTek's NPU: a real end that the engine did not believe

On the disaggregated path (the NPU reads the prompt, the CPU writes the reply) the runner stops on
its own stop tokens, `<|im_end|>` included, without handing the stop token's text to the callback,
so the marker never reached the engine and it dropped its record after every reply. The second
turn then reset the runtime, which on this path reloads the NPU and CPU halves before reading the
whole conversation.

The runner returns reason code 0 only when its loop ended on one of its stop tokens (budget,
cancel and a full window have codes of their own, and errors return nothing). Its stop set is
built from the tokenizer: the end token, id 0 where it is padding, and `<|im_end|>`,
`<|endoftext|>` and `</s>` where each is one token. The bridge now passes code 0 on as
`ExecuTorchOutcome.endedOnStopToken`. The engine keeps its record when the reply ended on the
marker, as before, or on a reported stop token while the engine had not asked for a cancel, and
in both cases only once the reply is committed to the NPU half (`commitReply`).

LFM2.5-1.2B, a16w8 2k NPU half, greedy, on the POCO X8 Pro Max (MT6991), time to first token at
turn two, median:

| Engine | Reuse | Second turn | Reset cache |
|---|---|---|---|
| As shipped | 0 of 8 | 11,671 ms | 5,662 ms |
| Prototype, keeping the record on any runtime-ended reply | 7 of 7 (pairs 0 to 6) | 433 ms | 5,667 ms |
| This change (code 0 only) | not measured | | |

This change has not run on a phone. Its run on the POCO crashed while the CPU half was loading,
after the NPU half had started (`greedy-lfm25-2k-npu-v10.*`); the cause was not recorded. What the
prototype shows is that keeping and committing the reply lets the NPU path reuse its prefix; that
the exact build passes code 0 through and does the same is still to be measured.

Both arms reached the same runner position (998) with the record kept, and on a Galaxy Tab S10+
(MT6989, Qwen3-1.7B a16w4) the one pair before the 2k window filled reached 1,653 in both. With
reuse the NPU's greedy reply changed (`Device-based offline AI.` against `Device-only AI`) in all
pairs; the cause was not established. The CPU still answered faster on this phone: 85 ms with
reuse and 3,312 ms for a full read (prefill, medians, run alone) against the NPU's 430 ms and
about 5,660 ms, with different exports and quantization.

Known and not changed here: the engine adds one to MediaTek's generated-token count, which the
runner already counts, and on this path the context occupancy is counted from what the CPU
generated rather than from what the NPU committed after re-reading the reply (Codex, 2026-11-16).
