# llama.cpp v0.8.0, v1.0.0 and v1.0.0 with the F16 key dot, on two phones, 2026-11-14

llama-bench built for Android with the app's CPU flags (`tools/snapdragon/llama_bench_ab.sh build`, the
patched tree from `../fa-f16-kq-lead/fa-f16-kq.patch`), run over adb by `run3.sh`: decode of 64 tokens
at depths 0, 2,048 and 4,096, flash attention on, 4 threads, the three builds in rotating order, four
rounds per model, a cooling gate before each run (SM8550: hottest CPU zone under 42 C; POCO: the
thermal HAL's CPU reading under 45 C, its shell cannot read thermal zones) and the start temperature
in every row.

- `qdc-sm8550.*`: QDC reservation sa974452, Snapdragon 8 Gen 2 (kalama), android_armv8.6_1 variant.
- `poco-mt6991.*`: the POCO X8 Pro Max over USB, Dimensity 9500s (MT6991), android_armv9.0_1 variant,
  screen on and unlocked, charging from 15%.

Models: LFM2.5-1.2B-Instruct QAD-Q4_0 (the app's default GGUF) and Qwen3-1.7B Q8_0. `report.txt` is
`report3.py` over both files. `dev.sh` (not kept) was a two-line adb wrapper per device.
