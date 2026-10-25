# Prefill and decode on CPU, GPU and NPU: every pairing on a Snapdragon 8 Elite Gen 5

**Measured 2026-10-24 on a Qualcomm Device Cloud handset, `canoe`: SM8850 (Snapdragon 8 Elite
Gen 5), Adreno 840 GPU, Hexagon v81 NPU, 8 cores (six at 3.63 GHz, two at 4.61 GHz), 23 GB,
Android 16, `adb` as root, on AC power in Qualcomm's rack.** The reservation ended at about 16:55
phone time, before the last two models, the ExecuTorch comparison and the second pass ran; what
did not run is listed under [what was not measured](#what-was-not-measured).

## Results

From r4 (`tools/snapdragon/results/2026-10-24-sm8850/analysis-r4.md`, rows in `results-r4.jsonl`):
33 matched turns per cell from the nine conversations, every cell reading the same tokens and
writing 64 per turn (the first from the prefill context, 63 timed decode steps). Qwen3 4B has 22
matched turns from five conversations (three WildChat, two UltraChat) across the eight cells that
ran. Rates are pooled over all matched turns, throttled or not: they describe this phone in its
rack, on AC power, replaying fixed-length teacher-forced turns with each cell starting cooled, not
a hand-held phone in a live chat. Each processor's rate is the mean of the cells that used it
(three, or two for Qwen3 4B's CPU prefill and NPU decode). The cells differ more than a single
mean suggests: Qwen3 0.6B's GPU prefill ran 532 to 672 tok/s depending on the partner cell, Llama
3.2 1B's CPU decode 46.4 to 52.5; the per-cell figures are in the analysis file.

| Model (Q4_0) | Prefill tok/s, CPU / GPU / NPU | Decode tok/s, CPU / GPU / NPU | npu:cpu | npu:npu | gpu:gpu | cpu:cpu | Slowest |
|---|---|---|---|---|---|---|---|
| Qwen3 0.6B | 239 / 591 / 3,014 | 63.7 / 30.5 / 47.8 | **34.6 s** | 47.6 s | 90.5 s | 84.3 s | cpu:gpu, 122.7 s |
| Gemma 3 1B | 339 / 590 / 3,547 | 65.6 / 31.1 / 47.8 | **33.6 s** | 47.5 s | 91.5 s | 70.2 s | cpu:gpu, 106.8 s |
| Llama 3.2 1B | 242 / 532 / 3,041 | 49.2 / 34.3 / 38.8 | **43.7 s** | 57.4 s | 83.5 s | 93.9 s | cpu:gpu, 111.5 s |
| LFM2.5 1.2B (hybrid) | 322 / 624 / 3,368 | 63.2 / 46.7 / 42.7 | **34.9 s** | 52.1 s | 67.6 s | 75.1 s | cpu:npu, 85.0 s |
| Qwen3 1.7B | 139 / 284 / 2,097 | 33.1 / 18.7 / 29.9 | **67.3 s** | 74.9 s | 154.2 s | 148.5 s | cpu:gpu, 199.5 s |
| Qwen3 4B (8 of 9 cells) | 53 / 109 / 911 | 15.2 / 5.7 / 15.2 | **99.1 s** | 100.5 s | 313.1 s | 237.5 s | cpu:gpu, 397.5 s |

The pairing columns are the summed turn time (prefill, first token, handoff and decode) over the
model's matched turns.

- **Prefill: the NPU, by a wide margin, on every model.** 10.5 to 17 times the CPU and 5.1 to 8.4
  times the GPU. Its advantage grows with the width of the prompt (Llama 3.2 1B: about 1,150 tok/s
  for turns of under 64 new tokens, 3,200 to 3,700 for 256 or more), but it led at every width.
  Median time to first token with NPU prefill was 72 to 117 ms for the 0.6B to 1.7B models and
  270 to 288 ms for the 4B.
- **Decode: the CPU led on the five complete models** in the pooled figures (33 to 66 tok/s). The
  NPU was second on four of them and the GPU on LFM2.5. On Qwen3 4B the CPU and NPU were level
  (15.22 against 15.15 tok/s, from unequal partner counts; with GPU prefill held fixed the NPU was
  ahead, 15.15 against 15.14), and the GPU decoded at 5.7.
- **The fastest pairing measured was NPU prefill with CPU decode on all five complete models**,
  taking 41% to 48% of the CPU-only time and 10% to 33% less than doing both halves on the NPU. On
  Qwen3 4B it was the fastest of the eight cells that ran, but only 1.4% ahead of NPU only (99.1
  against 100.5 s, ahead in 14 of 22 turns), which one run cannot separate from noise.
- **The handoff.** On the NPU-to-CPU path the two state-copy calls took a median 1.1 to 6.3 ms a
  turn. The whole transition between the end of prefill and the start of decode, which also clears
  the destination context and sizes the buffer, took a median 2.9 to 32 ms and at most 103 ms. The
  state was a median 6.7 to 101 MB on the matched turns, up to 471 MB for the 4B at 3,191 tokens
  of context. The pairing totals include all of it.
- **The worst was CPU prefill with GPU decode** on five of the six: the slowest prefill with the
  slowest decode.
- **Greedy output differs between processors.** Against the CPU-only cell, 4 to 25 of 33 turns
  (14 to 15 of 22 on the 4B) matched it token for token, compared up to the first end of turn or
  the 64-token limit; most matches reach the limit without either side ending its turn. The data
  show that outputs differ, not why: numerical differences between backends and a defect in the
  state copy would both look like this, and nothing here validates the copied state beyond its byte
  count and position. Answers were not graded.

**Throttling and the GPU's rank.** 50 of 53 cells started with every zone at or under 55 °C and
the GPU uncapped; three on the 4B reached the wait limit (recorded as 302 s) still warm, one with
the GPU cap at 2. Even so, most turns ended with the GPU capped: for Llama 3.2 1B, 8 to 23 of 33
per cell, the NPU-only cell least. On the 10 Llama turns that ended GPU-uncapped in every cell (all
WildChat, early in replay order, a restricted cohort), the decode order was CPU 58 tok/s, GPU 44,
NPU 39, where over all turns it was CPU, NPU, GPU. That is consistent with the cap costing the GPU
its place but does not isolate it: an end-of-turn reading does not say which phase was capped,
and the CPU's and NPU's own limits were not recorded. NPU prefill was 2,905 to 3,033 tok/s on
those turns and 3,017 to 3,058 overall. In r2, where the cell starts read 73 to 98 °C on the CPU,
70 to 86 °C on the GPU and 72 to 87 °C on the NPU, Llama 3.2 1B's GPU prefill was 396 tok/s and
its CPU decode 41, against r4's 532 and 49.

**Where the work ran.** From the load logs, per model: the CPU instance is one graph; the GPU
instance puts every layer, the output layer and the KV cache in OpenCL buffers, with the token
embedding lookup on the CPU (two graph splits); the NPU instance puts every repeating layer and the
KV cache on Hexagon (`HTP0-REPACK`, and `HTP1` for the 4B), while the output projection, whose
weights are the embedding table on these tied models, runs on the CPU (three splits, four with two
sessions). On Llama 3.2 1B that is 524 MB of weights on the NPU and 205 MB on the CPU; on Qwen3
0.6B 237 and 122 MB. So "NPU" prefill and decode include a CPU matrix multiply for the logits of
each token decoded, and of the last prompt token.

**What this means for the app on a Snapdragon 8 Elite Gen 5.** The fastest arrangement measured
reads the prompt on the Hexagon NPU and writes the reply on the CPU, and llama.cpp can do both in
one process with a state copy between them. This is a measurement of what that would buy on this
chip, not a decision. Before an app could ship it, these are still unmeasured:

- **An unprivileged app.** Every run here was a root shell. Loading the Hexagon backend and
  reaching the DSP from an app's own UID, packaging the HTP libraries, recovering when the DSP
  session dies, and falling back on phones without it are all untested.
- **Cold start.** First-token times above are warm. Loading the NPU instance took 563 to 2,221 ms,
  and the warm-up batch after it is not counted.
- **Long prompts.** First turns were 18 to 268 tokens, the widest new-token span 1,215 and the
  longest context at decode 3,303; nothing reached 4,096 although the window was 8,192.
- **Live conversations.** The replay teacher-forces the dataset's replies, so the prefill side
  never needed the decode side's own tokens. A real split must feed the generated reply back to the
  NPU context, or copy the state back, before the next turn.
- **Memory, energy and battery.** Three instances were resident at once; nothing measured power.

## What was asked, and what this answers

Which processor should read the prompt (prefill) and which should write the reply (decode),
for each of the nine pairings of CPU, GPU and NPU, across models and conversation lengths taken
from public datasets.

## What runs

One llama.cpp build (`b2e5e9b`, the commit the app vendors) with all three of its Snapdragon
backends: CPU (`ggml-cpu`), Adreno GPU (`ggml-opencl`) and Hexagon NPU (`ggml-hexagon`,
`libggml-htp-v81.so`), built with Qualcomm's toolchain image
`ghcr.io/snapdragon-toolchain/arm64-android:v0.7` as llama.cpp's Snapdragon guide does.

`tools/snapdragon/pd_bench.cpp` loads a model three times, once per processor (`n_gpu_layers`
-1 on the GPU and NPU instances, 0 and no op offload on the CPU one), each with its own context.
A turn prefills its new tokens on the prefill processor, takes the first token from that
context, copies the sequence state (`llama_state_seq_get_data` / `set_data`, which carries the
KV cache and, on hybrids, the recurrent state) into the decode processor's context, and decodes
there. On a diagonal cell (both halves on one processor) nothing is copied; the decoded tokens
are dropped afterwards with `llama_memory_seq_rm` so the next turn continues from the dataset's
history. A hybrid (LFM2.5) cannot drop tokens, so on its diagonal cells the state is saved before
the prefill and restored after the decode, with the turn's prompt read again, all untimed.

| | decode on CPU | decode on GPU | decode on NPU |
|---|---|---|---|
| **prefill on CPU** | runs | runs (state copy) | runs (state copy) |
| **prefill on GPU** | runs (state copy) | runs | runs (state copy) |
| **prefill on NPU** | runs (state copy) | runs (state copy) | runs |

All nine pairings are configured in this runtime; which of them completed is reported per model
below. The off-diagonal ones are a real disaggregation: the conversation state moves from the
prefill context to the decode context every turn, one way (the prefill side keeps the history; the
decode side gets a fresh copy each turn). During the run all three model instances are resident at
once, so this measures speed, not the memory of a deployment that holds one or two copies.

**Models**, chosen for quantisations both accelerator backends implement (Q4_0 everywhere, one
Q8_0 to see the quantisation's effect): Llama 3.2 1B and 3B Instruct, Gemma 3 1B IT, LFM2.5
1.2B Instruct (the app's QAD Q4_0, a hybrid), Qwen3 1.7B (Q4_0 and Q8_0, the latter the app's),
Qwen3 4B. The 3B and 4B use two Hexagon sessions (`GGML_HEXAGON_NDEV=2`), because one session
holds 2 GB.

**Conversations** (`tools/snapdragon/pull_conversations.py`): the first conversation in each
length bucket (short, medium, long, by characters) from each of WildChat-1M, UltraChat 200k and
Daring-Anteater, nine in all, with at least two user turns. WildChat is real users talking to a
deployed assistant; UltraChat and most of Daring-Anteater are generated dialogues. ShareGPT52K
was the first choice for the third set; the Hugging Face datasets server cannot read it. MT-Bench
ships user turns only, and the replay needs the dataset's assistant replies, so it is not used.

**Replay.** History is teacher forced: every earlier assistant turn in a prompt is the dataset's
own reply, so all nine cells read exactly the same tokens. Each turn decodes 64 tokens, past an
end of turn if the model writes one, so every cell decodes the same amount; up to six turns per
conversation within an 8,192-token window. Greedy. Every context runs one 512-token batch and
eight decode steps before anything is timed, because the OpenCL backend compiles kernels on first
use (in the smoke run that put about 900 ms into the GPU's first turn). The order of the nine
cells is shuffled per model from a fixed seed. One shuffled order per model does not by itself
control for the phone heating through a model's run; each cell's start records the CPU, GPU, NPU
and memory temperatures, and a second pass with another seed is planned to counterbalance it.

## What does not run, and why

- **ExecuTorch pairings.** The app's other runtime has an XNNPACK (CPU), Vulkan (GPU) and QNN
  (NPU) delegate, but no way to move a conversation's state from one delegate's program to
  another's: each `.pte` keeps its KV cache inside its own graph in its own layout. The MediaTek
  handoff in [npu-pd-disaggregation.md](npu-pd-disaggregation.md) was written for one specific
  pair of exports. ExecuTorch can only fill the diagonal here, and only for Qwen3 0.6B, the one
  model with an SM8850 QNN export (an execupack artifact saved before it expired).
- **Vulkan in llama.cpp.** Not built: the Snapdragon guide builds OpenCL for Adreno, and the app
  ships OpenCL. Vulkan on Adreno is a fourth processor option, not one of the three asked for.
- **Mixture-of-experts and K-quants.** Not in the model set: the Hexagon backend's matrix
  multiply covers Q4_0, Q8_0 and MXFP4, so a Q4_K_M model would put most of its work back on the
  CPU and the "NPU" label would be wrong.

## Runs

- **r1, pilot, stopped.** The first version of the tool saved each diagonal cell's state between
  the prefill and the decode (untimed) to restore it afterwards. Codex's second review flagged that
  this disturbs the measured work, and the pilot showed it: the GPU decoded at 28 tok/s in its
  diagonal cell against 41 to 44 tok/s after another processor's prefill. r1 finished Llama 3.2 1B
  and was stopped in Gemma; its rows are kept (`results-r1.jsonl`) and not used for the tables.
- **r2.** The fixed tool (`pd_bench3`): transformers drop decoded tokens with `seq_rm`, hybrids
  snapshot before the prefill; warm-up, state sizes and restores are checked; each cell records its
  temperatures and each turn its elapsed time; the run records its settings and fails loudly.

- **r2, paused.** Llama 3.2 1B completed, each processor giving similar rates in its three cells
  (CPU prefill 207 to 211 tok/s, NPU 2,899 to 2,956; GPU decode 28.1 to 28.4), with the hottest CPU,
  GPU and NPU zones at 73 to 98, 70 to 86 and 72 to 87 °C at the cell starts. Read by hand from the
  phone after it (not in the result files): Android's thermal status was 3 (severe) and the GPU was
  capped at power level 8 of 17, 578 MHz against its 1.2 GHz top. Stopped during Gemma. Left to
  cool, the phone fell from 64 to 53 °C in about four minutes, when the cap lifted and the status
  read 0 (also read by hand). r2's rows are kept and reported separately as the throttled condition.
- **r3, stopped.** The same tool with a cooling gate (`pd_bench4 --cool 50`): before each cell
  it waits until the hottest CPU, GPU and NPU zones are at or under 50 °C and the GPU is uncapped.
  The first cell waited 503 s, which put the run near twelve hours, and was stopped there.
- **r4.** The gate at 55 °C, where the cap had already lifted, with the wait limited to about
  300 s (it polls every 5 s); each `cooled` row records the readings the cell started with, from
  which the analyser infers whether the gate passed (r4 has no explicit pass field).
  Every turn records the zones and the GPU cap when it ends. A capped turn is one whose end-of-turn
  reading found the GPU capped; that does not say whether the cap hit its prefill or its decode,
  and the GPU cap says nothing about the CPU's own frequency limits, which r4 does not record.
- **r5 (planned).** `pd_bench5`, a second seed for a different cell order: each turn is read at its
  start and end, with a timestamp and the CPU clusters' frequency ceilings (`scaling_max_freq`
  against `cpuinfo_max_freq`); the gate requires valid readings, the GPU uncapped and no CPU
  ceiling lowered, and records whether it passed, timed out or could not read the sensors. It does
  not yet treat an unreadable CPU frequency file as unknown.

## What was not measured

- **Llama 3.2 3B and Qwen3 1.7B Q8_0.** Queued last in r4, after Qwen3 4B; the reservation ended
  first.
- **Qwen3 4B's `cpu:npu` cell**, and 11 turns of its `npu:npu` cell. Its table is over the 22 turns
  the eight cells that ran share.
- **The second pass (r5).** Planned with `pd_bench5`, a second seed for a different cell order, on
  four models; it would have checked that the cell order and the heating did not decide the
  rankings, and recorded the CPU's own frequency ceilings. Without it each cell is one run.
- **ExecuTorch's NPU path.** The runner (`qnn_llama_runner`, ExecuTorch 1.5.1, built for this),
  Qualcomm's QNN 2.39 libraries with the V81 skeleton, the SM8850 Qwen3 0.6B export and 27
  tokenized prompts (`tools/snapdragon/make_et_prompts.py`, `run_et.sh`) were ready to push after
  r4. ExecuTorch has no way to move state between its delegates, so it could only have filled the
  diagonal.
- **Memory.** All three model instances were resident at once, so no deployment's footprint was
  measured.
- **Answer quality.** Outputs were compared token for token with the CPU, not graded.

## How far the labels can be trusted

A backend runs what it supports and llama.cpp's scheduler sends the rest to the CPU. "NPU" and
"GPU" therefore mean "the layers and their weights were placed there", not "every operation ran
there": the logs record the graph splits per context, summarised under [where the work ran](#results).
The input embedding lookup stays on the CPU in every cell.
