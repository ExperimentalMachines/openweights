# What a second turn re-reads after each way the first one ended, 2026-11-16

The talk's claim is that an app has to know how a turn ended, and what the runtime committed,
before it reuses a cache. `ReuseBoundary` (core/engine androidTest) puts each boundary in front of
an engine through the same `InferenceEngine` calls the app makes, so both runtimes answer the same
questions. Four cases, 4 pairs each, greedy:

- `end`: turn one ends on its own marker; turn two extends it.
- `budget`: turn one is cut by an 8-token budget.
- `cancel`: cancel is asked on turn one's fourth streamed fragment (ExecuTorch reported 5 tokens
  generated, llama.cpp 4).
- `edit`: turn one ends normally, and turn two's first user turn has one phrase changed in its
  middle ("entirely on device" to "mostly on device", the 20th of 40 sentences).

Each case's turn two is sent twice: once on what the case left in the cache, once after a reset,
the order alternated. A case arm run second writes turn one again first; the row records that
turn one's reason, length and what the engine held, and the run fails if a turn one ended some
other way than its case names. Every turn one ended as its case names (48 of 48 case arms), and
every one written again matched the first in text, reason and held count (24 of 24, the
cancelled ones included). The question is "In two sentences, what did I describe, and what is
one risk of building it?", answered under a 64-token cap (Llama 3.2 used all 64; the LFM2.5
replies ended on their own at 38 to 44), so two replies can part somewhere other than the first
word. `boundary.py` leaves out any pair whose turn one ended some other way or was written again
differently, and says how many; none were.

Galaxy S25 Ultra (Snapdragon 8 Elite), one Test Lab device model, Android 16, 16 Nov 2026, timed
turns starting at 37.4 to 44.8 C, screen on. ExecuTorch: LFM2.5-1.2B-Instruct 8da4w-gptq 4k,
our ExecuTorch 1.6.0 build (1.6.0-openweights.2), `ExecuTorchOnDeviceTest#reuseBoundaries
-e temperature 0`. llama.cpp v0.8.0: Llama-3.2-1B-Instruct Q4_0 (full attention) and
LFM2.5-1.2B-Instruct QAD-Q4_0 (hybrid), `WarmPrefixEval#reuseBoundaries`. The rows are from the
test APK built from this commit. A first run with the harness before its stop-reason checks gave
the same token counts and byte-identical replies (96 of 96 rows); it is not kept here. Token
counts are the engines' (`promptTokens` read, `cachedTokens` kept); llama.cpp's are its own,
ExecuTorch's are the engine's estimates (its kept and read counts are separate estimates and need
not add up to the reset arm's).

## Tokens the second turn read, medians of 4

| Turn one | ExecuTorch, LFM2.5 | llama.cpp, Llama 3.2 1B | llama.cpp, LFM2.5 (hybrid) |
|---|---|---|---|
| ended on its marker | extend: 28 read, 981 kept, 104 ms (reset 965, 3,174 ms) | extend: 28 read, 942 kept, 190 ms (reset 970, 3,982 ms) | extend: 28 read, 990 kept, 155 ms (reset 1,018, 3,310 ms) |
| cut by an 8-token budget | reset: 938 read, 3,076 ms | extend: 28 read, 938 kept, 170 ms | extend: 28 read, 913 kept, 151 ms |
| cancelled | reset: 934 read, 2,884 ms | extend: 29 read, 933 kept, 210 ms | reset: 937 read, 2,947 ms |
| first user turn edited mid-way | reset: 969 read, 3,752 ms | truncate: 503 read, 467 kept, 2,200 ms | reset: 1,018 read, 3,103 ms |

The reset arm read every token in every case and kept none (`boundary.py` prints both arms).

| Replies the same as the reset arm's | ExecuTorch, LFM2.5 | llama.cpp, Llama 3.2 1B | llama.cpp, LFM2.5 |
|---|---|---|---|
| ended on its marker | **0 of 4** | 4 of 4 | 4 of 4 |
| cut by a budget | 4 of 4 (both arms read cold) | **0 of 4** | **0 of 4** |
| cancelled | 4 of 4 (both cold) | 4 of 4 | 4 of 4 (both cold) |
| edited | 4 of 4 (both cold) | 4 of 4 | 4 of 4 (both cold) |

## What it shows

- **ExecuTorch extends only after a clean end.** After a budget cut or a cancel the engine keeps
  no record of what it fed, because it cannot say where the last emitted but uncommitted token
  ends; after the edit its record exists but stops matching the next render at character 2,188
  (`record` on the rows). Each of the three costs a full read. The engine's own occupancy
  estimates were 910 after the cut and 907 after the cancel.
- **llama.cpp kept a cut reply and a cancelled one on the full-attention model.** After the budget
  cut the engine spliced the reply's own tokens back into the prompt (`kv: 1 remembered reply
  spliced into the prompt`, logged for the end, budget and edit cases, never for the cancel), and
  turn two read 28 tokens. After the cancel it kept a matching prefix of 933 without a logged
  splice and read 29.
- **On the hybrid, a budget cut kept everything and a cancel kept nothing.** After the cut the
  engine held 913 positions and extended them; after the cancel it held 0 and read all 937. The
  logs show the cancel ending the decode call; in this engine, a hybrid that cannot drop the
  unfinished tail resets instead. The edit was refused as the model requires (`kv: rollback to N
  refused (recurrent state), re-reading all N`): there is no checkpoint before the edit, so it
  re-read 1,018.
- **An edit is the case truncation pays for.** Llama 3.2 cut back to the edit and re-read 503 of
  970 tokens, 1.9 times faster than a reset; the hybrid and ExecuTorch read everything.
- **Reuse changed longer replies.** LFM2.5 on ExecuTorch after a clean end, under a 64-token cap,
  answered in 44 tokens where the reset arm answered in 42, differently in 4 of 4 pairs (`...memory constraints, which could
  affect performa...` against `...memory usage due to the device's constraints.`, first
  difference at zero-based character 208); in the earlier 5-token test its replies matched in 56
  of 56. On llama.cpp, after a budget cut, both models' replies differed from the reset arm's (first
  difference at character 327 on Llama 3.2, whose replies all ran to the 64-token cap, so its
  comparisons are of truncated text; and at 215 on LFM2.5, which ended on its own at 38 and 40
  tokens), though both arms logged the same splice; after a clean end they matched. Each case and arm gave one reply across
  its 4 pairs, and the same replies in both runs. Their cause was not established; the token ids
  were not logged.

Limits: one device model, one export per runtime and family, 4 pairs a case. The `edit` case
changes the middle of a 1,000-token user turn; an edit nearer the end would let Llama 3.2 keep
more, and would change nothing for ExecuTorch or the hybrid without a checkpoint past it; tail
edits were not measured. The engines' token counts are not the runtime positions for ExecuTorch.
Timings are prefill, not time to first token, and are given for scale only.

Files: `pa3q/<run>.boundary.jsonl` (rows), `pa3q/<run>.log` (the test's and the runtimes' log
lines only), `boundary.py` (the summary), `ftl.sh` (the Test Lab runner, as in
`2026-11-16-coldwarm-matrix/` with the boundary rows and the `ReuseBoundary` tag added, and an
exit status that follows the test's).
