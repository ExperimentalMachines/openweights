# Vulkan on the phone, 2026-10-03 to 2026-10-04

The raw record behind [docs/research/vulkan-on-device.md](../../../../docs/research/vulkan-on-device.md).
Every file says at its top which phone, build and model it is, and whether it was **captured**
to a file when it ran or **transcribed** afterwards from the session's terminal output (the
command and everything it printed; nothing else was kept at the time). API keys are redacted
and workstation scratch paths shortened to `$SCRATCH`. Device clocks are local: the Poco is
UTC+8, the QDC handsets UTC-7.

| File | Phone | What | How kept |
|---|---|---|---|
| `poco-llama_main-1.4.0-2026-10-03.log` | Poco (Mali) | ExecuTorch v1.4.0 `llama_main`: XNNPACK and Vulkan 2k, both again on one CPU thread, then the 32k Vulkan file running out of memory | transcribed |
| `poco-engine-xnnpack-1.5.1-upgrade-2026-10-04.log` | Poco | The 1.5.1 runtime on the CPU path, before Vulkan: a 1.4.0 file and a 1.5.1 export | transcribed |
| `sm8650-engine-vulkan-2026-10-04.log` | SM8650 | Engine tests on the GPU build, 9 of 9, with the throughput matrix | transcribed |
| `sm8650-execuserve-debug-2026-10-04.log` | SM8650 | ExecuServe serving the GPU build; `/v1/models` still said `executorch-xnnpack` (fixed in ExecuServe `13085b1`) | transcribed |
| `poco-engine-vulkan-2026-10-04.log` | Poco | Engine tests on the GPU build, 9 of 9, with the matrix | transcribed |
| `poco-engine-vulkan-2026-10-04.instrument.txt` | Poco | The raw `am instrument` output of that run | captured |
| `poco-greedy-cpu-vs-gpu-2026-10-04.log` | Poco | Four questions, greedy, CPU file against GPU file | transcribed |
| `poco-execuserve-vulkan-2026-10-04.log` | Poco | ExecuServe on the GPU build; OpenAI 16/16, edge cases 28/28, Anthropic 8/8 | transcribed |
| `sm8850-execuserve-release-2026-10-04.log` | SM8850 | ExecuServe release APK: catalog pulls, recorded backends, answers, stream, status | transcribed |
| `sm8850-engine-vulkan-release-2026-10-04.instrument.txt` | SM8850 | First engine run on the GPU build, 9 of 9; its logcat was lost | captured |
| `sm8850-execuserve-runs-2026-10-04.csv`, `sm8850-execuserve-long-prompt.txt` | SM8850 | ExecuServe's run history after three short and three 702-token prompts on each build, and that prompt | captured |
| `sm8850-engine-Qwen3-0.6B-vulkan-8da4w-2k-2026-10-04.*` | SM8850 | Engine tests on the GPU build again, logcat captured | captured |
| `sm8850-engine-Qwen3-0.6B-8da4w-gptq-2k-2026-10-04.*` | SM8850 | Engine tests on the GPTQ CPU build | captured |
| `poco-engine-Qwen3-0.6B-vulkan-8da4w-2k-home-2026-10-04.*` | Poco | Engine tests on the GPU build again, at home, logcat captured | captured |
| `poco-engine-Qwen3-0.6B-8da4w-gptq-2k-home-2026-10-04.*` | Poco | Engine tests on the GPTQ CPU build, straight after | captured |
| `poco-execuserve-runs-2026-10-04.csv` | Poco | ExecuServe (debug build) run history, the SM8850's twelve requests on both builds | captured |
| `codex/` | | Every Codex review of the 1.5.1 bump and the Vulkan work, the two documentation reviews (`docs-*-r1.md`, `docs-*-r2.md`), and `PROMPTS.md` with each round's prompt | captured |
