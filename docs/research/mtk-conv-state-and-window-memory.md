# The MediaTek LFM2 export: its conv state, the runner's pads, and what a window costs

Dated 2026-09-24. Poco X8 Pro Max (MediaTek MT6991, Dimensity 9400, 11.5 GB), screen on, charging.
LFM2.5-1.2B-Instruct at revision 0f604ada, A16W8, four chunks. Exports from
`executorch-model-exporter` branch `mtk-conv-state`; the MediaTek runner and the app's
disaggregated library built from ExecuTorch release/1.4 with `tools/npu/patches/executorch-release-1.4-pd.patch`.

## What was wrong

Two defects, both in how the export carried LFM2's short-convolution state.

**Memory.** MediaTek's LLM scripts give every layer a K and a V cache the size of the window.
LFM2's ten convolution layers kept their two positions of state in the first rows of that K,
passed the V through untouched, and wrote both back on every call. Ten of sixteen layers, 62% of
the cache, carried nothing. On this phone both 16k builds (A16W4 on 2026-09-23, A16W8 on
2026-09-24) took the whole phone down: `Kernel panic - not syncing: System is deadlocked on
memory` the first time, `reboot,hang_detect` the second, with 40 MB free and the memory held as
NPU buffers the out-of-memory killer cannot reclaim.

**Accuracy.** MediaTek's runner left-pads the first batch of a fresh cache with token 0 and
right-pads any later feed that is not a multiple of the batch, then rolls the caches back.
Attention masks the pads. A causal convolution has no mask, so at every conv layer the first real
tokens mixed in the pads' values, and after a right-padded feed the state handed on was made of
pads. The fp32 masks are 0 and -100, and a fully masked pad row is a constant that softmax
cancels, so the pads attend normally and carry realistic values. Measured without any
quantization, against Hugging Face on a 1,670-token prompt: KL 0.20 per step through the shell
runner's feed and 0.20 through the app's two-piece feed, against 1.4e-8 with no padding. With
A16W8 weights, 0.165. For scale, A16W8 quantization on its own measured 0.0068. The pads, not
the weights, are why every NPU build left fp32's greedy path within a few tokens.

## The fix

In the export (`mediatek-lfm2.patch`, exporter finding 36):

- Each conv layer takes its own `(1, 2048, 2)` state, the shape the CPU export keeps, and states
  go in layer order: one per conv layer, a K and a V per attention layer.
- Two small inputs fix the padding: `conv_valid` zeroes the pads before the convolution, and
  `conv_select` takes the next state at the last real positions.
- Attention regroups its queries by K/V head instead of repeating K and V four times.
- The prompt graph's batch is a per-export choice (`prompt_tokens`), because its attention scores
  grow with the window.

In the runner (`LlamaModelChunk`): states are told apart by shape (only a cache has the window
on its length axis), each chunk's state count comes from the model rather than from
`2 * num_layer / num_chunk`, only caches are rolled back, and the two padding inputs are filled
before every call. Exports with MediaTek's uniform layout (Qwen, and the first LFM2 files) load
and answer exactly as before: identical tokens on the old A16W8 512.

In the app (`tools/npu/mtk_pd_disaggregated_jni.cpp`): the NPU-to-CPU handoff maps layers from
what each chunk reports, the old layout's conv handoff no longer copies the wrong rows at any
window but 512, and a session now releases its NPU half. MediaTek's destructors are empty, so
every reopened session leaked it; the engine reopens per conversation, and the fourth question
of a device test had the process killed with 1.4 GB swapped.

## Quality on the phone

The twelve short questions and three needles of the phone rig, scored against the fp32 original's
greedy reply; fidelity is how many of the original's 64 greedy tokens a build reproduces before
it first diverges, on ten long prompts.

| Build | Correct | Word for word as fp32 | Needles | Fidelity median / mean |
|---|---:|---:|---:|---:|
| A16W4, old layout, 2k | 9/12 (2026-09-23) | | 3/3 | 1.5 / 3.3 |
| A16W8, old layout, 2k | 11/12 | 7/12 | 3/3 | 10 / 11.4 |
| A16W8, per-layer states only, 2k | 12/12 | 12/12 | 3/3 | 30.5 / 37.0 |
| A16W8, new recipe, 2k | 12/12 | 12/12 | 3/3 | 51.5 / 44.3 |
| A16W8, old layout, 4k | 11/12 | 7/12 | 3/3 | 6 / 6.4 |
| A16W8, new recipe, 4k | 12/12 | 12/12 | 3/3 | 33.5 / 34.9 |
| A16W8, new recipe, 8k | 12/12 | 12/12 | 3/3 | |

"New recipe" is the per-layer states, the padding inputs and the regrouped attention, at batch
128. On the 512 window the per-layer build is also 12/12 and 12/12, against the old layout's 11
and 7. Every run on the NPU is deterministic: the same build gives the same tokens every time. Through
the app's disaggregated path (NPU prefill, CPU decode on the 8da4w-2k export,
`DisaggregatedOnDeviceTest`) the new 2k answers 13 of 13 including the needle; the CPU export on
its own answers 11 (528 for 17 x 24, and "Venus" for the needle).

## What a window costs

NPU memory is the growth of the system `DMA-BUF` total in `dumpsys meminfo` while the runner holds
a build; it is the whole of the fall in available memory apart from the runner's own 0.35 GB.
More than half of it is allocated by MediaTek's driver, not held by the process.

| Build | MB per window position | At 512 | At 4k |
|---|---:|---:|---:|
| Old layout, batch 128 | 0.54 to 0.58 | 1.89 GB | 3.84 GB |
| Per-layer states, batch 128 | 0.40 | 1.79 GB | |
| New recipe, batch 128 | 0.38 | 1.82 GB | 3.19 GB |
| New recipe, batch 64 | 0.27 | 1.62 GB | 2.59 GB |
| New recipe, batch 32 | 0.23 | 1.52 GB | 2.33 GB |

The regrouped attention alone was worth about 5% (0.40 to 0.38); removing the conv caches, and
then the prompt graph's batch, did the rest. The new 8k at batch 128 took 4.50 GB, where the old
8k took 5.9 GB. A 16k at batch 128 would need about 8 GB and was stopped at 5.6 GB by a guard that
kills the runner below 1 GB free. The slopes project a 16k at batch 32 at about 5.5 GB, which
would fit; its first export lost a chunk to MediaTek's compiler service after 92 minutes of
calibration, and it has not yet been measured. 32k projects to about 9 GB even at batch 32, more
than a 12 GB phone has.

## What was published

The new recipe at batch 128 for 512, 2k, 4k and 8k, the same files measured here, to
`experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch` under `mtk/mt6991/` on 2026-09-24
(commits a5d3a39 to 8184ae8), replacing the A16W4 512 that was there. The card says these need
the runner patch above: MediaTek's stock runner refuses them at load. The app downloads no
`mtk/` folder; the disaggregated bridge reads one only from a staged directory with an
`enable-pd` marker, and it still takes every chunk file in the folder with the first variant's
settings, so a folder holding several windows has to be trimmed to one before it is staged.

## Speed

Shell runner, the same long prompt per window, builds alternated, two rounds each. Prefill is
prompt tokens over seconds (the runner's own figure rounds the prompt up to whole batches).

| Window, prompt | Build | Prefill | Decode |
|---|---|---:|---:|
| 2k, 1,772 tokens | old layout | 295 to 305 tok/s | 5.7 tok/s |
| | per-layer states only | 332 to 336 tok/s | 7.7 to 7.8 tok/s |
| | new recipe | 314 to 317 tok/s | 6.6 to 8.2 tok/s |
| 4k, 3,615 tokens | old layout | 143 to 181 tok/s | 2.5 tok/s |
| | new recipe, batch 128 | 194 to 197 tok/s | 4.9 to 5.3 tok/s |
| | new recipe, batch 64 | 143 to 168 tok/s | 5.1 to 6.1 tok/s |
| | new recipe, batch 32 | 84 to 105 tok/s | 5.1 to 6.0 tok/s |
| 8k, 7,301 tokens | old layout | 126 to 140 tok/s | 1.1 to 2.1 tok/s |
| | new recipe, batch 128 | 187 tok/s | 4.8 tok/s |

The window-sized conv caches cost more at long windows than at short ones: at 8k the new build
reads a third faster and decodes more than twice as fast. The app's CPU prefill on file is 188
tok/s ([npu-pd-disaggregation.md](npu-pd-disaggregation.md), the KleidiAI path in the app) and 290
to 360 tok/s ([executorch-own-exports.md](executorch-own-exports.md), the device test on its own
export), measured differently; against either, what the NPU gains on reading a prompt is modest
and shrinks as the window grows, and the decode stays on the CPU either way.

## Reproducing

The phone rig is `~/ow-models/mtk-dev/phone-rig/` on the workstation: `reference.py` writes the
fp32 references, `phone_run.py` and `fidelity_run.py` score a build, `memguard.sh` and
`dmatotal.sh` measure memory with the 1 GB guard. The fp32 replica of the runner is the exporter's
`docs/research/evidence/mtk-conv-state-and-padding.py`.
