# A search the loop made for the model re-read the conversation (2026-09-14)

**Symptom, reported from the phone.** LFM2.5 1.2B QAD-Q4_0 was slow again on replies that
searched, with the warm prefix loaded and nothing else running, while the compiled export
of the same model stayed fast. It had the shape of the late-August slowdown
([kv-cache-regression.md](kv-cache-regression.md)): a prompt rewritten where the cache had
already read it.

## Cause

Since 2026-09-10 the loop carries out a search the model did not make: when the model says
it will look something up and calls nothing (`honourIntent`), and when its opening token
fell under one in five (`honourDoubt`, GGUF only, because the compiled runtime returns no
logits). Both go through `TurnRunner.appSearch`, which drops the model's pass and puts
"Searching the web for: ..." and the results in its place. Every other repair keeps the
pass's own text and appends, so its prompt only grows; this one replaces bytes the cache
has read, starting where the dropped reply began.

A transformer cuts its cache back to that point for free. LFM2.5 is a hybrid: its
recurrent layers keep a running state, not a row per token, and `llama_memory_seq_rm`
refuses to cut it. The engine then fell back to the warm-prefix snapshot, the only other
state it kept, and read everything after the tool block again: the whole conversation so
far, the question, and the results. The cost grows with the conversation, at the phone's
prefill rate.

The compiled model never reaches `honourDoubt`, which is why it did not show this.

## Fix

The engine keeps llama.cpp's own context checkpoint for hybrid and recurrent models, as
llama-server does. Each text prompt is read up to its last token, the part of the memory
that cannot be rolled back is copied there (`llama_state_seq_get_data_ext` with
`LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY`: the recurrent state only, not the attention cache),
and then the last token is read. When a later prompt diverges past that point and the
rollback is refused, `align_cache` puts the recurrent state back, trims the attention
cache to the same position, and reads only what is new. Up to four points are kept; a reset
drops them, and a restore drops those past it. Nothing the model reads changes. The same
path serves a regenerated reply and a question stopped and sent again.

## Measured

**Host replay, the QAD-Q4_0 file on a Mac CPU** (`engine_session.cpp` built against the
pinned llama.cpp, the app's 16-tool system prompt, three earlier turns, then a question
whose reply is dropped for a search):

| | before | after |
|---|---|---|
| the engine's line | `rollback refused; restored the warm prefix of 2432 instead of re-reading all 2897` | `rollback refused; restored the point at 2832, reading 64 instead of 464` |
| tokens read for the search pass | 465 | 64 |
| its prefill | 1,968 ms | 389 ms |
| ordinary passes, time to first token | 219, 153, 154, 147 ms | 219, 162, 151, 139 ms |
| the same prompt sent again (regenerate) | would read 464 | read 1 in 28 ms, reply byte-identical |

A byte-identical regenerate is the check that the restored state is exact: both reads of
that prompt have the same batch boundaries, so any difference in state would show in a
greedy reply. The search pass's own reply differs a few words in between the two builds,
which is the batch-boundary difference already documented for every rollback path.

**On a phone, the decision suite through the real loop** (`DecisionSuiteOnDeviceTest`,
`doubt-search` arm, the first 20 RetrievalQA rows, Pixel 10 Pro XL, both builds from the
same commit with and without the change):

| Row (the loop dropped the pass and searched) | tokens read, before | after | prefill, before | after | whole row, before | after |
|---|---|---|---|---|---|---|
| `popqa_331903` | 866 | 323 | 5,489 ms | 2,544 ms | 11,364 ms | 8,152 ms |
| `popqa_4498593` | 949 | 446 | 6,151 ms | 3,543 ms | 11,760 ms | 9,039 ms |
| `popqa_1430021` (searched on the fixed build only) | | 410 | | 3,375 ms | | 8,333 ms |

The engine logged `rollback to 544 refused (recurrent state), re-reading all 866` before,
and `rollback refused; restored the point at 543, reading 323 instead of 866` after. The
twelve rows that answered in one pass on both builds took a median 3,564 ms before and
3,422 ms after, so the extra single-token batch and the state copy cost nothing measurable.
Both phones reported thermal status 0 throughout. The suite starts each row from an empty
context and a 544-token system prompt, so "before" here is the whole prompt; in the app,
with the warm prefix restored, it is everything after the tool block, which is the whole
conversation and grows with every turn.

A first pair of runs was discarded: the unit given the fixed build reported
`THERMAL_STATUS_LIGHT` from the first row and its prefill decayed from 167 to 55 tok/s
across the run, on rows the change does not touch, while the other unit stayed at 0.

## What to take from it

Every search the loop makes for the model is a rewritten prompt. The byte-stability rule
([kv-cache-regression.md](kv-cache-regression.md)) was written for the head of the prompt;
on a hybrid it holds for every position the cache has read, and the repair path that
replaced the pass was the one place in the loop that broke it. The engine now makes that
case cheap whatever the loop does, so a future repair that drops a pass does not bring it
back. Watch for `kv: rollback refused; restored the warm prefix ... instead of re-reading
all` on a turn past the first: on a hybrid, after this change, it means a prompt diverged
before the last point, which is a byte-stability bug in the loop.
