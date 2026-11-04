# llama.cpp b10549 against v0.8.0 on an SM8850

QDC reservation sa938847, 2026-10-31, unit a1716cc4 (`canoe`, Snapdragon 8 Elite Gen 5, Android 16,
root). Not yet written up under `docs/research/`; `summary.txt` is the tabulated A/B.

## What ran

| File | What it is |
|---|---|
| `raw/inst-*.txt`, `raw/openweights-logcat-*.txt`, `raw/ow-*.txt` | Engine on-device tests, run 1 (b10549 first): 7 classes, 14 tests per APK |
| `raw/run2-inst-*.txt`, `raw/openweights-run2-logcat-*.txt` | Run 2 (v0.8.0 first), plus MultimodalTest: 20 tests per APK |
| `raw/q35-*` | ToolCallingTest with Qwen3.5 2B Q4_K_M as `model.gguf`, both APKs |
| `raw/ab.log` | Upstream Snapdragon preset tools (`q105`/`q080`, no KleidiAI): perplexity and llama-bench on CPU, Adreno, Hexagon |
| `raw/ab2.log` | The same tools built with the app's CMake flags (`a105`/`a080`, KleidiAI, CPU variants loaded at runtime), CPU |
| `raw/ab3.log` | Every speed pair of ab and ab2 again in the opposite order, plus GPU start-up time |
| `raw/ab4.log` to `raw/ab7.log` | Why the app path prefills slowly: SME off, KleidiAI off (`k105`), ARMv8.6 fallback (`v86`), ARMv9.0 fallback (`v90`) |
| `raw/ab8.log` | Peak RSS (VmHWM) and speed with KleidiAI on and off on both versions (`a105`, `a080`, `k080`, `k105`) |
| `raw/template-probe/` | `scripts/probe.cpp` output: the same conversations rendered by each version on the Mac |
| `engine-v0.8.0.patch` | The 22-line change `engine_session.cpp` needs to build against v0.8.0 |

The `ab.log` speed lines were parsed from CSV whose rows hold quoted commas, so the values printed
there are `avg_ns` and `stddev_ns`; `scripts/summarize.py` converts them. `ab2.log` onwards use
`-o jsonl`. Perplexity is WikiText-2 test, 16 chunks of 512 tokens, 6 threads; speed is llama-bench
pp512 and tg64, three repetitions, after a wait for the hottest CPU, GPU or NSP zone to fall below
55 C (at most 120 s).

The `raw/openweights-*logcat*.txt` files are the app's own lines from each phone log. The full logs stayed out of the
repository because they also hold the cloud phone's network identifiers and a vendor upload token.
