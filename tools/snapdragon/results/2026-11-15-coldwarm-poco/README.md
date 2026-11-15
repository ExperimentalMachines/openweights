# The same prompt read cold and warm, 2026-11-15

The talk pack's owed item 4 (`~/ow-models/pytorch-jp/prefix-reuse-talk-pack.md`, section 15): a
controlled same-prompt comparison instead of turn two against turn one, with screen and thermal
state recorded. Every ratio below is the cold arm's time over the reuse arm's, per pair, and
reports the median and range over pairs.

## ExecuTorch: the second turn

`ExecuTorchOnDeviceTest#theSecondTurnReadColdAndWarm`, ExecuTorch 1.6.0-openweights.2, XNNPACK,
on the app's engine. Each pair generates turn one (the 40-sentence `LONG_PROMPT`); warm is the
real second turn ("In one word, what did I just describe?"), extending the cache turn one left,
and cold resets and reads the identical prompt whole. Every timed turn waits for the CPU to fall
under 45 C (at most 240 s on the POCO unless capped, 60 s on Test Lab) and records the CPU and
battery temperatures and whether the screen was on. Timed turns stop at 48 tokens.

This engine samples at the temperature it is built with, for every turn (`ExecuTorchEngine`'s
`temperature`, 0.8 in the app); a `SamplerParams` temperature does not reach it. The sampled runs
below therefore sampled turn one and the timed turns alike. Sampled, the warm arm has to go first:
the cache cannot go back to the end of turn one once the cold arm has read, and a sampled turn one
is never the same twice. The greedy run (`-e temperature 0`) writes turn one again identically,
so it alternates the order, and it is the only run whose replies say whether reuse changes the
output.

Token counts are the runtime's own (`given pos_ ... num_prompt_tokens` in the log lines). The
engine's count for the cold arm is an estimate (the pieces fed ahead are counted at the generate
tail's characters-per-token rate, or four characters per token without one) and was off by up to
about 190 tokens either way.

### POCO X8 Pro Max (Dimensity 9500s), over USB, screen on

| Run | Model | Warm hit | Warm: tokens read, prefill median | Cold: tokens read, prefill median | Cold / warm |
|---|---|---|---|---|---|
| **Greedy, order alternated** | LFM2.5-1.2B 8da4w-gptq-4k | 8 of 8 | 20, **111 ms** [105-126] | 1,003, **3,822 ms** [3,632-3,962] | **34.5x** [31.3-36.7] |
| Sampled, warm first | LFM2.5-1.2B 8da4w-gptq-4k | 8 of 8 | 20, **106 ms** [104-111] | 979-1,105, **4,452 ms** [4,182-5,451] | **41.7x** [39.8-49.1] |
| Sampled, warm first | Qwen3-1.7B INT8-INT4 (1.4.0 export) | 7 of 10 | 20, **253 ms** [239-279] | 1,177-1,719, **12,487 ms** [10,064-15,350] | **49.4x** [41.2-55.1], hits only |

- **The greedy run is the controlled one.** Turn one came out identical every time it was written
  again (4 of 4 cold-first pairs), both arms read 1,003 tokens in every pair, warm-first pairs
  gave 34.3x and cold-first pairs 34.6x, and the warm and cold replies were identical in 8 of 8
  pairs: on this model and prompt, reuse did not change the greedy output. CPU at the start of
  timed turns 43.7 to 45.1 C (gate capped at 60 s), screen on in all 16 rows.
- Sampled runs: CPU at the start of every timed turn 43.9 to 45.0 C; battery 37.9 to 40.4 C;
  screen on in every row. Decode medians were similar in both arms (LFM2.5 32.3 and 32.9 tok/s,
  Qwen3 10.8 and 10.2). Both arms read the same token count in 14 of 15 hit pairs. Their replies
  differ, as sampled replies do; they say nothing about reuse.
- **Qwen3 missed reuse in 3 of 10 pairs**: the second turn re-read everything. The Qwen3 ratio
  is over the seven hits; over all ten pairs, misses included, it is 44.0x. Why it misses is
  below; the same turn-one text replayed through the engine on the JVM extends correctly, so the
  cause is in the real token stream, not the text.
- Warm first is confounded with order and with what ran just before in the sampled runs. The
  cooling gate makes the starting temperature similar, not the whole thermal or scheduler state.
  The greedy run alternates order and finds it moving the ratio little (34.3x against 34.6x);
  the llama.cpp runs below alternate too, and their order groups differ by more.

Files: `greedy-LFM2.5-1.2B-Instruct-8da4w-gptq-4k.*`, `sampled-LFM2.5-1.2B-Instruct-8da4w-gptq-4k.*`
and `sampled-Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.*` (rows, the test's and the runtime's log
lines, instrumentation output); `run.sh` ran them (`S=<serial> ONLY=lfm EXTRA="-e temperature 0 -e
coolLimitS 60"` for the greedy run), `report.py` summarises a `.coldwarm.jsonl`. `attempt1-qwen3.log`
and `attempt2-qwen3-*` are two earlier designs, both sampled although the test asked for greedy:
alternating order with turn one regenerated in cold-first pairs, which a sampled turn one cannot
reproduce (616, 347 and 297 tokens), and a turn one that ran to the 2,048-token window and left
no room for turn two.

`diag-Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.*` is a diagnostic pass (8 pairs, sampled, the
test's `record:` line per pair). The phone's memory was nearly full during it (read with `top`
just before, not kept), so its timings are not used. Both turns one the runtime ended left an
empty record; the six others left an exact prefix of the next render.

### Galaxy S24 (Snapdragon 8 Gen 3) and Galaxy S25 Ultra (Snapdragon 8 Elite), Firebase Test Lab

One test APK for all six runs (uploaded once, not kept here), models from the project's Test Lab
bucket, sampled, warm first, cooling gate at 45 C capped at 60 s, screen on in every row.

| Phone | Model | Warm hit | Warm prefill, median | Cold: tokens read, prefill median | Cold / warm |
|---|---|---|---|---|---|
| S24 | LFM2.5-1.2B | 8 of 8 | **98 ms** [76-110] | 999-1,059, **2,988 ms** | **29.7x** [26.5-40.1] |
| S24 | Qwen3-1.7B | 6 of 10 | **264 ms** [243-302] | 1,442-1,898, **12,652 ms** | **45.5x** [41.1-58.7], hits only (41.1x over all ten) |
| S25 Ultra | LFM2.5-1.2B | 8 of 8 | **70 ms** [65-77] | 988-1,059, **2,940 ms** | **42.1x** [39.1-46.7] |
| S25 Ultra | Qwen3-1.7B | 8 of 10 | **152 ms** [133-165] | 1,189-1,839, **11,170 ms** | **73.9x** [50.4-89.5], hits only (58.4x over all ten) |

CPU at the start of timed turns 36.0 to 44.9 C. The warm arm read 20 tokens in every hit; the
two arms read the same token total in every hit on both phones.

### Why Qwen3 misses (all three phones)

What the logs and the source show:

- Every Qwen3 turn one that the runtime ended at its own end-of-sequence check (its log says
  "Reached to the end of generation") was followed by a miss, and every turn one the engine
  stopped was followed by a hit: POCO 3 of 3 and 7 of 7, S24 4 of 4 and 6 of 6, S25 Ultra 2 of 2
  and 8 of 8, and the diagnostic pass 2 of 2 and 6 of 6.
- In ExecuTorch 1.6.0 the token loop hands each token to the callback, then breaks if the caller
  asked it to stop, then checks for end of sequence. The engine asks it to stop when the stream
  carries `<|im_end|>`, so a turn the engine stopped never reaches the end-of-sequence check. A
  turn that does reach it ended on a token whose text, as the engine received it, held no
  `<|im_end|>`; turn one's recorded text ends with the reply itself.
- With no end marker the engine cannot tell the reply from one cut at the window, so it drops its
  record of the cache (`fedText`, `ExecuTorchEngine.kt` at the `fedText =` assignment). The
  diagnostic pass read that record empty after both runtime-ended turns. The turn still reports
  `END_OF_TURN`, the runtime's default.

Not established: which token ended those turns and why its text carried no marker (an empty
decode of a special token, or text held back on the way through JNI, are both possible). LFM2.5's
turn one never ended at the runtime's check here.

## llama.cpp: a fresh chat over a restored snapshot

`WarmPrefixEval#aFreshChatReadColdAndRestored`, llama.cpp v0.8.0, LFM2.5-1.2B QAD-Q4_0 (a hybrid:
it cannot roll back, so a fresh chat after a conversation restores the head's snapshot). Each
pair asks the identical fresh-chat question twice on two engines: restored (warm the 1,018-token
head with its 12,388 KiB snapshot, hold a two-turn conversation, open a new chat) and cold (an
engine that never warmed and has no snapshot, one unrelated turn first so mapping the weights in
is not counted). Alternating order, cooling gate, greedy (this engine takes the turn's sampler),
96 tokens. Times are time to first token, which includes the restore.

| Phone | Restored: read, TTFT median | Cold: read, TTFT median | Cold / restored | Replies identical |
|---|---|---|---|---|
| POCO (over USB, screen on) | 18 (1,018 restored), **238 ms** [168-281] | 1,036, **7,068 ms** [6,658-7,266] | **29.8x** [25.3-42.1] | 6 of 6 |
| S24 | 18 (1,018 restored), **149 ms** [140-162] | 1,036, **4,752 ms** [4,645-4,934] | **31.9x** [28.7-35.2] | 6 of 6 |
| S25 Ultra | 18 (1,018 restored), **132 ms** [109-175] | 1,036, **4,269 ms** [3,847-5,242] | **31.7x** [27.9-41.0] | 6 of 6 |

- By order (restored first against cold first): POCO 25.4x and 34.3x, S24 30.7x and 33.2x, S25
  Ultra 34.0x and 29.7x. The order groups differ by up to a third, in no consistent direction;
  with three pairs a group, that is an observed difference, not a measured order effect.
- Every reply was the one word "Paris", so "identical" is scoped to that question.
- On the POCO the cooling gate was capped at 60 s (`run_restore.sh`, `COOL_LIMIT_S=60`) after
  three hours of runs, so five timed turns of the first three pairs started at 45.7 to 47.3 C.
  The restored arm's 238 ms (QAD-Q4_0) sits beside the 235 ms measured on 3 Sep with Q4_K_M
  (`docs/research/first-turn-latency.md`), which had no matched cold reading.
- Both arms exclude setup (load, warm, the conversation); neither is a cold app launch.

Files: `LFM2.5-1.2B-Instruct-QAD-Q4_0.restore.*` (POCO, from `run_restore.sh`); `ftl-<device>/`
holds each Test Lab run's rows and the test's and runtimes' log lines (gcloud's own output, which
names the project and bucket, is kept outside the repository); `ftl.sh` ran them,
`report_restore.py` summarises a `.restore.jsonl`.
