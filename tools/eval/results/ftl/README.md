# Test Lab runs kept from the eval bucket

The Firebase Test Lab runs wrote everything to `gs://openweights-eval-models`, which was
deleted on 2026-09-30. Before it went, every file was compared by content with this
repository and the local archives; what is here is what existed nowhere else and is text:
result rows, the test outcome (`test_result_*.xml`, `instrumentation.results`) and the
device logcat, xz-compressed (368.8 MB of logcat became 18 MB, and each file was checked to
decompress to the original bytes). The layout is the bucket's, less the
`artifacts/sdcard/Android/data/<package>/files/eval-results` part of the pulled paths.

| Folder | Runs | What they back |
|---|---|---|
| `checkpoint-ab/` | 4 | The before and after runs in `docs/research/dropped-pass-reread.md` (866 tokens read become 323 on a Pixel 10 Pro XL); the logcats hold the engine's `rollback refused` lines |
| `play-probe/` | 8 | `PlayProductionProbe` installing the production app through the Play Store (`docs/research/release-token-callback-abort.md`, "What Play serves") |
| `judge/` | 1 | The 2026-09-17 `Session::judge` run on a Pixel 10 Pro XL (`docs/research/typesafe-experiments.md`) |
| `decisions/` | 15 | Decision suite runs whose rows or logcats were not already under `decisions/` |

Four of the `decisions/` runs are one half of a `bare` arm (80 of the suite's 160 rows)
that passed on Test Lab but whose rows never reached `decisions/`, whose graded files for
those four still hold only the other 80:

| Phone | Model | Rows kept here |
|---|---|---|
| Galaxy S25 Ultra (SM-S938U1) | LFM2.5 1.2B Q4_K_M | 80 to 159 |
| Pixel 10 Pro XL | LFM2.5 1.2B Q4_K_M | 0 to 79 |
| Pixel 10 Pro XL | LFM2.5 1.2B 8da4w-32k (compiled) | 80 to 159 |
| Galaxy S24+ (SM-S926B) | LFM2.5 1.2B 8da4w-32k (compiled) | 80 to 159 |

They are kept here rather than appended there, so that every number already published
from `decisions/` still regenerates as it was. Two more `bare` runs here repeat rows that
are in `decisions/` with the same answers, and one Galaxy S24+ `intent-search` run answers
50 of its 80 questions differently from the run that was kept.

Not kept in the repository, and where they are instead:

- Screenshots, screen recordings and the Play-delivered split APKs (`base.apk`,
  `split_config.*.apk` for the production installs the probe saw): in the local archive
  `~/ow-models/benchmarks/eval-bucket-20260930/`, with everything else the bucket held
  except models and the debug APKs.
- The debug app and test APKs uploaded with each run: rebuildable from their commits.
- The models. Every model file in the bucket but one is a byte-size match for a public
  Hugging Face file (LiquidAI, Qwen, unsloth, ggml-org, pytorch, larryliu0820, and
  software-mansion at revision `a365c9da9b`). The exception is
  `LFM2.5-1.2B-Instruct-8da4w-32k.pte`, the round-to-nearest export most decision runs
  used, which differs from the published GPTQ 32k export; it and its tokenizer are in
  `~/ow-models/`.
