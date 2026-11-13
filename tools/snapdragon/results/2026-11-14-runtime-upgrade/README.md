# 2026-11-14: llama.cpp v1.0.0 and ExecuTorch 1.6.0, emulator and Mac

Written up in `docs/research/llama-cpp-v100-executorch-160.md`. No phone ran any of this.

- `parity/`: `ExecuTorchParityEval` and `LlamaCppParityEval` on the 16 KB arm64 emulator.
  `et151` and `et151b` are two runs of 1.5.1-openweights.4 (the determinism control), `et160` is
  1.6.0-openweights.2. `llama080` and `llama100` are the two llama.cpp pins, both with ExecuTorch
  1.6.0-openweights.1 beside them.
- `bench-executorch/`: GSM8K, IFEval and BFCL (30 each, `tools/eval/bench/benchmarks.json`) on the
  Software Mansion LFM2.5 1.2B XNNPACK export, graded with `tools/eval/bench/grade.py`.
  `bench-executorch/em-4k-*` is the app's default download,
  `experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch` `xnnpack/LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte`,
  run in ABBA halves (`@15` is the second 15 of each set).
- `gsm8k100/`: 100 more GSM8K questions (`questions-seed11.json`, none in `benchmarks.json`) on the
  default download, 1.5.1 and 1.6.0 in ABBA halves; `grade_gsm100.py` grades them with the repo's
  GSM8K rule and pairs the arms. `et160-exp` is 1.6.0 with only the SDPA softmax's `exp_u20()` put
  back to `exp()`, built in the same tree: identical to `et151` on all 100.
- `bench-llama/`: the same sets on LFM2.5 1.2B QAD-Q4_0 GGUF. The v1.0.0 half ran while builds
  compiled on the host, so its timings are not used.
- `host-pairs/`: `llama-bench` on the Mac, each version built natively from its tree, 4 threads,
  alternating pairs. `fa-report.txt` and `pairs-report.txt` are the summaries.
- `host-cache-contract/`: the engine's host driver on both pins, seven models; `compare.txt`.
- `emulator-ab/`: `tools/snapdragon/llama_bench_ab.sh run` on the emulator, the app's Android build.
- `engine-tests/`: the engine's instrumented tests on both llama.cpp pins (instrumentation output only).
