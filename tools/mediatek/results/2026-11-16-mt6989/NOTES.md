# MediaTek prefill/decode and throttle study: running notes

Galaxy Tab S10+ (`gts10pwifi`, SM-X820, MT6989 = Dimensity 9300+: 1x Cortex-X4 3.4 GHz, 3x X4
2.85 GHz, 4x A720 2.0 GHz; Mali-G720 Immortalis MC12; MDLA NPU; 11.8 GB), Android 16, reached
through Firebase Android Device Streaming in project thematic-garage-514815-a4, unrooted.
Unit of session 3: serial hash `d650f7bb` (sha256 of ro.serialno, first 8).

## Facts found (2026-11-16)

- Shell reads: cpufreq scaling_max/cur, `/sys/kernel/gpu/*` (clock, max clock, busy, table), cpu
  online/isolated, `dumpsys thermalservice` (AP, SKIN, status), perfetto as shell (sched_switch,
  cpu_frequency, cpu_frequency_limits recorded; thermal events not). Denied: thermal zones,
  cooling devices, core_ctl, ged/gpufreq.
- The NPU runs from an unrooted shell (`mtk_pd_bench`, ExecuTorch v1.6.0 + app patch).
- NPU memory: Qwen3 0.6B A16W8 at a 4k window took 6.8 GB of kernel vmalloc and left 0.58 GB
  available (0.53 GB with the CPU's copy). Loading the GPU's (Vulkan) copy beside it panicked the
  kernel twice (dropbox SYSTEM_LAST_KMSG_*_KP, OOM with nothing killable: `s3/kp1.txt`, `kp2.txt`).
  The GPU's copy alone stays under 1 GB. So the NPU exports use a 2k window; every cell of a model
  reads only the turns that fit 2k (23 or 24 of the 33 Snapdragon turns).
- Exports calibrated on 2 of alpaca's 9 prompts (time); NPU compute per call is fixed by shape.
- Handoff check (Qwen3 0.6B, 4k, first turn): per-layer cosine NPU vs CPU K 0.90 to 0.9997, V 0.72
  to 0.99 (`s3/check-handoff.jsonl`): layout right, quantisation differs (Q4_0 vs A16W8).

## Exynos and Tensor: not run (the goal's condition, NPU accessible, not met)

- Exynos 2400 (Galaxy S24/S24+ on Device Streaming): no route to its NPU. ExecuTorch's Samsung
  backend and Samsung's LiteCore support the Exynos 2500 and 2600 only, and a retail unit's shell
  cannot open the NPU driver.
- Tensor G5 (Pixel 10 Pro XL): the NPU takes only Google's precompiled model files (Gemma through
  Google's own runtime), so no model can be exported to it and no handoff to llama.cpp exists; a
  nine-pairing matrix cannot be built.
- Best MediaTek in the catalog for this account: Galaxy Tab S10+ (MT6989); no Dimensity 9400 or 9500.

## Pilot, session 3 (llama.cpp only, Qwen3 0.6B, 33 turns)

- cpu:cpu: 383 s of turns; prefill 175 tok/s, decode 39.4 tok/s over the cell; capped from turn 4.
- gpu:gpu: prefill 40 tok/s, decode 29 tok/s.
- Soft throttle within minutes: status 3 at AP ~56 C, ceilings 1.6/1.9/1.9 GHz (from 2.0/2.85/3.4).
- The tablet cools fast: the gpu:gpu gate passed after 106 s (AP 35 C, full ceilings).

## Heat test, session 3 (8 busy + 8 dd loops, 720 s)

- Status 4 (CRITICAL) at AP 61.6 C, SKIN 46.7 C. CPU ceilings 1.6/1.8/1.8 GHz, GPU max clock
  260 MHz (bottom of its table). All 8 cores online; a 5 s perfetto trace at status 4 had every
  core 100% busy, so no core was paused (unlike SM8850's XO-triggered pause of cores 2-7).

## Other

- Device Streaming sessions NOT created by this study appeared on the same project (3x m2q, 1x
  cubs, 20:06 to 20:59 UTC); they draw on the same credit (ledger.tsv).
- The streamed tablet's static curl needs `--resolve` from ping and a PEM bundle from the system
  store; phone download ran ~1.7 MB/s, adb push ~13-18 MB/s, so models go Mac -> adb.

## Thread sweep, session 3 (unit d650f7bb; Qwen3 4B Q4_0, llama-bench CPU only, pp512 / tg128)

`s3/threads-cool1.txt` (3 shuffled rounds, gate: all ceilings at top and AP <= 45 C before each run;
the gate waited 20 to 45 s every time) and `s3/threads-hot1.txt` (2 rounds back to back; ceilings
before runs 1.6-2.0 / 2.0-2.85 / 2.0-2.9 GHz). tok/s per run:

| threads | prefill cool | decode cool | prefill hot | decode hot |
|---|---|---|---|---|
| 1 | 25.9 21.9 24.2 | 8.76 8.22 8.71 | 22.3 26.4 | 8.77 8.63 |
| 2 | 38.1 32.8 35.9 | 10.71 10.72 11.03 | 30.8 36.3 | 10.81 11.66 |
| 3 | 47.5 47.1 48.0 | 12.18 11.99 11.92 | 43.0 43.8 | 12.24 12.20 |
| 4 | 54.1 55.3 56.3 | 14.02 13.58 13.43 | 53.4 51.6 | 13.57 13.19 |
| 6 | 72.4 72.7 72.3 | 15.79 15.10 15.20 | 64.6 60.3 | 15.03 14.80 |
| 8 | 88.8 89.4 89.4 | 15.24 14.49 14.73 | 74.5 77.1 | 14.27 13.83 |

Prefill scales to 8 threads; decode peaks at 6. Lowered ceilings cost 6-8 thread prefill 10-16%
and decode 2.9% at 6 threads and 5.2% at 8 (means of the recorded rates; corrected from "about 2%"
in Codex round four). No stall: every run completed at normal rates.

## Session 4 (unit 4bff7833): matrix, Llama 3.2 1B then Gemma 3 1B at 2k, all 9 cells per model

Sessions land on different physical units (d650f7bb, then 4bff7833), so each model's nine cells
run inside one session and each model records its unit.

## Validation before the matrix

- Handoff check per format, NPU state converted to the CPU's layout vs the CPU's own, cosine:
  Qwen3 0.6B (plain KV) K 0.90-0.9997, V 0.72-0.99; Llama 3.2 1B (rope permutation) K 0.99-0.9997,
  V 0.94-0.99; Gemma 3 1B (sliding window: 4 global + 22 sliding layers) K 0.97-0.998, V 0.91-0.996;
  LFM2.5 1.2B (hybrid: 6 attention + 10 conv) K 0.994-0.997, V 0.97-0.99, conv 0.95-0.9995.
- Nine-cell smoke at 2k (Llama 3.2 1B, 2 turns): all cells ran, replies coherent in every handoff
  direction; NPU 3.8 GB, CPU 1.0, GPU 1.1, leaving 1.6 GB (`s3/smoke9l.*`).
- Gemma 3 needed a second rotary table (local base 10000) in the runtime: patch_bench.py adds it.

## Handoff quality in the matrix (session 4, Llama 3.2 1B, 2-prompt calibration)

- llama.cpp -> NPU (cpu:npu, gpu:npu) replies are coherent and on topic up to pos 821.
- NPU -> llama.cpp GPU (npu:gpu) replies are fine at pos 399 and at pos 521 in one conversation but
  word salad at pos 513 and 821 in two others: content dependent, not a fixed position, so drift
  between an A16W8 NPU cache and a Q4_0 llama.cpp reader is the leading explanation, not an index
  bug. Timings are unaffected (every cell does the same fixed work); quality is reported per cell.
- Leading-token agreement with the same-decoder cell (analyze_mtk.py) is confounded by the first
  token, which comes from the prefill processor; read replies, and the long-history cosine check
  (`--check-handoff --check-conv C --check-turn T`, cosine by 256-position range), instead.
- A nine-prompt calibration export of Llama 1B (`...-cal9`) is building to test whether
  calibration is the cause (qwen.py/llama.py/gemma.py calibrate on short prompts only).

## Session 4 heat (mtklog-s4.txt, 2,199 samples)

All 8 cores online throughout. CPU ceilings at top 71.6% of samples; thermal status 3 or 4 in 98
of 440 HAL samples; AP max 61.9 C, median 46.9 C. GPU max clock 702 MHz in 58.9% of samples,
1,300 MHz in 19.1% (rises under GPU load), 390 MHz in 2.9%.

## Llama 3.2 1B matrix (session 4, unit 4bff7833, 24 turns x 9 cells at 2k, all gates passed)

| cell | total s | vs cpu:cpu | prefill tok/s | decode tok/s |
|---|---|---|---|---|
| cpu:cpu | 280.2 | 100% | 180 | 31.8 |
| npu:cpu | 283.7 | 101% | 168 | 31.8 |
| cpu:gpu | 332.3 | 119% | 193 | 26.0 |
| npu:gpu | 332.6 | 119% | 171 | 26.4 |
| gpu:cpu | 545.0 | 194% | 20 | 35.8 |
| gpu:gpu | 585.9 | 209% | 21 | 28.6 |
| cpu:npu | 904.3 | 323% | 236 | 8.9 |
| npu:npu | 907.6 | 324% | 190 | 8.9 |
| gpu:npu | 1197.3 | 427% | 22 | 8.6 |

No stalls (no decode step over 500 ms). Handoff 0.04-0.63 s per cell in total.

## The NPU replies past a few hundred tokens: Llama 3 RoPE scaling (found and fixed, session 4)

- NPU -> llama.cpp cells (npu:cpu, npu:gpu) wrote word salad at pos 513 and 821, fine at turn 0.
- Long-history check (`s4/check-long-{single,incremental}.jsonl`): turn-by-turn and single
  prefill identical (no misalignment); V cosine flat; K cosine falls with position, 0.999 at 0-255
  to 0.91 past 1,280, in every layer.
- Cause: MediaTek's rotary table (llm_helper/rotary_embedding.cpp) uses the plain frequencies;
  Llama 3.1/3.2 train with llama3 scaling (factor 32, low 1, high 4, original 8192), which llama.cpp
  applies. The NPU model was self-consistent but rotated its keys with the wrong frequencies.
- Fix: patch_bench.py gives the table llama3 scaling when runner.txt names it (rope_llama3_*).
  After it: K cosine 0.993-1.000 at every position range (`s4/check-long-l3.jsonl`), and npu:cpu at
  pos 821 writes the same reply as cpu:cpu (`s4/l3-quality.jsonl`).
- Timings are unaffected (the table's values do not change the compute); the session-4 Llama
  timings stand, its NPU replies and handoffs were computed with the unscaled table.
- The owner's published MediaTek Llama exports and any runtime built from MediaTek's stock runner
  have the same drift; worth checking in ExecuServe and the app.

## Qwen3 0.6B matrix (session 5, unit d6761b43, 23 turns x 9 cells at 2k)

NPU + CPU + GPU did not fit in one process (the guard left the GPU out: 1,329 MB available, 1,460
needed), so the NPU and CPU cells ran first (s5), the rest in two processes after (s5b). Since
session 6 every model runs in three processes from the start (split_queue.py).

| cell | total s | vs cpu:cpu | prefill tok/s | decode tok/s |
|---|---|---|---|---|
| npu:cpu | 192.8 | 94% | 150 | 48.2 |
| cpu:cpu | 204.2 | 100% | 262 | 40.5 |
| cpu:gpu | 214.9 | 105% | 298 | 37.8 |
| npu:gpu | 261.8 | 128% | 154 | 33.1 |
| gpu:cpu | 273.7 | 134% | 44 | 53.3 |
| gpu:gpu | 353.4 | 173% | 45 | 33.4 |
| cpu:npu | 1116.9 | 547% | 392 | 6.7 |
| npu:npu | 1140.5 | 558% | 157 | 6.7 |
| gpu:npu | 1185.4 | 580% | 44 | 7.0 |

- Heat by process: the first process (right after the warm-up) started 20 of 23 turns with a CPU
  ceiling lowered, the later two none: CPU decode 40.5-48 tok/s vs 53.3 in gpu:cpu.
- One stall burst: cpu:cpu, UltraChat-200k-0 turn 1, steps 128-140 at 0.7-1.7 s (normal ~27 ms),
  ceilings 1.6/2.2/2.2 GHz, thermal status 0, all cores online, nothing else in the logger. A
  pause the shell cannot see is possible (on SM8850 paused cores also read online); since session 6
  place.sh logs every bench thread's CPU every 2 s.
- NPU first token: the NPU picks 151664 where the CPU picks 151667 (<think>) on every turn; the
  following tokens match the CPU's. Untested guess: clipped logits from the 2-prompt calibration (saturated
  maxima, argmax takes the lowest index). Timing unaffected (fixed decode lengths).

## Codex review, round three (gpt-6-astra, 2026-11-16 03:30 UTC)

Scope: patch_bench.py (llama3 RoPE, Dual LUT), split_queue.py + chain.sh admission, place.sh,
fetch_export.sh, analyze_mtk.py. No defect in the llama3 formula (matches transformers' three
branches and llama.cpp's rope_freqs; both float and int16 templates covered; float32 emulation
over 0-2047 differs by ~2.3e-4), the Dual LUT (local table unscaled, as Gemma 3), the `wait $bp`
exit code, or place.sh's stat parsing. Fixed:
- chain.sh admitted per line: a refused model's later processes still ran, and an admitted model
  could be cut short by a later check. Now a model is admitted or refused once, at its first line,
  after its GGUF arrived; a line without a numeric estimate is refused under DEADLINE; a trailing
  field and a final line without a newline are handled. Tested locally with stubs.
- fetch_export.sh matched `*Llama-3.*` for the RoPE lines (Llama 3.1's factor is 8, not 32). Now
  read from Hugging Face at the export's source_revision; any non-llama3 rope_scaling refused.
- analyze_mtk.py: one unit per model enforced, PARTIAL label below nine cells, empty intersection
  handled, conventional median for agreement (Llama npu:cpu 6.5, was reported as the upper value).
- patch_bench.py is not re-runnable on a patched tree (overlapping replacements); the docstring now
  says so; the build script restores pristine files first, so no build was affected.
Not changed: place.sh samples last CPU every 2 s, so it can show crowding, not prove a pause during
a shorter stall; agreement is a warning signal, not a handoff verdict (cosine checks are).
Sessions 4-6 ran the old chain.sh; their queues had one model per session or deadline skips of
whole models, so none ran partially by that bug (s5's Gemma/LFM lines were all skipped).

## Memory: from Qwen3 1.7B up, the NPU and a llama.cpp copy do not fit together (session 8, unit c8340578)

Probe on the idle unit before its chain (s8/probe-s8.jsonl, log-s8probe.txt, log-s8mem.txt):
- Qwen3 1.7B A16W4 2k NPU export (4 chunks 1.1 GB + fp32 embedding 1.24 GB): MemAvailable 8.1 GB
  -> 1.46 to 1.69 GB once loaded. VmallocUsed 0.37 -> 4.16 GB (+3.8 GB kernel), the process's own
  RSS 0.71 GB (anon 0.66, file 0.05: the embedding is not resident), the rest (~1.9 GB) neither
  vmalloc nor anon: most likely NeuroPilot's DMA buffers. Qwen3 0.6B A16W8 took 4.3 GB.
- The GPU (and the CPU) copy of the 1.7B Q4_0 needs 2,232 MB by the guard (1.2 x GGUF + 1,024 MB,
  the margin set after session 3's two out-of-memory kernel panics): refused. Without the margin
  ~100 MB would remain, the condition that panicked the tablet.
- So for 1.7B and up the four handoff pairings (cpu:npu, npu:cpu, gpu:npu, npu:gpu) cannot run on
  this 12 GB tablet; each model runs the five that fit: npu:npu alone, and cpu:cpu, gpu:gpu,
  cpu:gpu, gpu:cpu in a llama.cpp process. Qwen3 1.7B Q8_0 uses the same NPU export, so its npu:npu
  repeats Q4_0's on the same unit (a repeatability check), queued last.
- NPU decode, Qwen3 1.7B A16W4, first two turns of WildChat-1M-18, cool unit: 5.98 and 6.01 tok/s;
  prefill 73.5 tok/s (63 tokens) and 134 tok/s (457 tokens).

## Budget check (2026-11-16 03:50 UTC)

alpha@pytorch.ph cannot read the billing account, so spend is tallied from the Device Streaming
session list (create_time to expire_time; expire_time moves to the cancel time, matching ledger.tsv).
- This study: 476 min so far (incl. the three running sessions to now). Other sessions on the
  project (m2q x4, cubs x1, not this study): 170 min so far, ~194 when aj928f8oi3c4a expires.
- Projection: s6 to 05:57, s7 ~181 min, s8 ~165 min, then three sessions of ~140 min for LFM2.5
  2.6B, Llama 3.2 3B and Qwen3 4B: ~1,540 tablet min in all = ~$226 at $0.15/min after 30 free,
  plus VMs/disks ~$25-30: ~$255, if no more outside sessions appear.

## Session 9 (unit e880d908): LFM2.5 2.6B fits, nine pairings on six conversations

- Probe on the idle unit (s9/probe-s9.jsonl): the A16W4 NPU export loads beside a CPU copy and
  beside a GPU copy (the hybrid keeps far less per-token state than Qwen3's attention-only model).
  First two turns of WildChat-1M-18: NPU decode 3.0 tok/s in the NPU+CPU process and 4.0-4.1 in
  the NPU+GPU process (to watch in the matrix: Llama 1B and Qwen3 0.6B showed <=4% between
  processes); CPU decode 21-23, GPU decode 17.7; GPU prefill 10-15 tok/s, CPU 117-135, NPU 60-128.
- Nine pairings over all turns estimated at ~175 min, more than a session allows (Device Streaming
  caps a session at 3 h from activation: `extend --ttl 14400` left the expiry unchanged). So the
  queue reads conversations-6.jsonl (the first two of each dataset in file order, fixed before
  any timing; ~79% of the decode work on LFM2.5 1.2B), the NPU+GPU process last, no warm-up (the
  probe warmed the unit; chain.sh skips it when a file named nowarm exists).

## Session 10 (unit 4bff7833, as session 4): Qwen3 4B's NPU export does not load

- Probe (s10/probe-s10.jsonl): the guard refused the NPU, 7,098 MB available against 7,206 MB
  estimated (files + window + 1,024 MB). The estimate is low: Qwen3 1.7B's export took 6.4 GB
  against ~3.6 GB estimated, so the 4B (4.1 GB of files) would need roughly 10 GB, more than the
  tablet has free; forcing it would risk session 3's out-of-memory kernel panic. Not tried.
- CPU copy alone: 2.9 GB; CPU decode 13.8 / 12.5 tok/s on the first two turns, prefill 75 / 59.
- So Qwen3 4B runs only the four llama.cpp pairings, over every conversation (queue q-s10.txt).

## Session 11 (unit 4e2d9065): Llama 3.2 3B's NPU runs alone, at the edge of memory

- Probe (s11/probe-s11.jsonl, memcurve-s11probe.txt): the A16W4 export (2.15 GB of chunks, 1.58 GB
  fp32 embedding) loaded in 4.4 s; MemAvailable 6.9 GB -> 650-950 MB while running, dipping to
  ~650 at each 128-token prefill call, not falling across turns; lowest 636 MB. Decode 4.25 tok/s,
  prefill 45 / 79 tok/s (64 / 457 tokens).
- chain.sh now starts phone/memguard.sh beside every bench: it kills the bench if MemAvailable
  falls under 600 MB (session 3 panicked below ~530), and logs the lowest it saw (memguard-*.txt).
- Queue: the four llama.cpp pairings, then npu:npu alone; no handoff pairing can fit.
- A peer Claude session of the owner's (cold/warm study) reserved session-3i3eqfdajpxrt on the
  same project at ~04:45 UTC; logged in ledger.tsv as the owner's, not foreign.

## Gemma 3 1B matrix (session 6, unit 317808dd, 23 turns x 9 cells at 2k)

Four gates timed out (the unit ran warm: thermal status up to 4). Turns starting with a CPU ceiling
lowered: 18 to 22 of 23 in the seven cells that use the CPU or GPU heavily, 0 of 23 in npu:npu and
gpu:npu; so the cells did NOT all run under the same soft throttle (corrected in Codex round four:
the analyzer had counted a thermal status above 0 as a lowered ceiling). No stall.
| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cpu:cpu | passed | 96 | 36.5 | 23 / 0 | 227.8 | 100% | 28.2 | 0.00 | 0.00 | 0.00 | 199.6 | 217 | 39.3 | 0 | 22/23 | - | - |
| npu:cpu | passed | 71 | 36.6 | 23 / 0 | 244.9 | 108% | 43.2 | 0.01 | 0.12 | 0.04 | 201.5 | 142 | 38.9 | 0 | 22/23 | 4 | 9 |
| cpu:gpu | passed | 121 | 35.6 | 23 / 0 | 334.0 | 147% | 24.9 | 0.00 | 0.03 | 0.04 | 309.0 | 246 | 25.4 | 0 | 20/23 | 53 | 0 |
| npu:gpu | passed | 111 | 35.7 | 23 / 0 | 346.4 | 152% | 46.5 | 0.01 | 0.10 | 0.04 | 299.8 | 132 | 26.2 | 0 | 21/23 | 5 | 9 |
| gpu:cpu | timed_out | 121 | 36.6 | 23 / 0 | 442.1 | 194% | 256.3 | 0.20 | 0.62 | 0.04 | 185.0 | 24 | 42.4 | 0 | 23/23 | 28 | 1 |
| gpu:gpu | timed_out | 121 | 36.5 | 23 / 0 | 535.1 | 235% | 225.8 | 0.20 | 0.00 | 0.00 | 309.0 | 27 | 25.4 | 0 | 23/23 | - | - |
| npu:npu | passed | 106 | 36.0 | 23 / 0 | 758.4 | 333% | 36.0 | 0.01 | 0.00 | 0.00 | 722.4 | 170 | 10.9 | 0 | 22/23 | - | - |
| cpu:npu | timed_out | 121 | 37.1 | 23 / 0 | 782.9 | 344% | 24.5 | 0.00 | 0.07 | 0.00 | 758.3 | 250 | 10.3 | 0 | 23/23 | 2 | 8 |
| gpu:npu | timed_out | 121 | 36.5 | 23 / 0 | 1026.5 | 451% | 211.1 | 0.20 | 0.69 | 0.00 | 814.5 | 29 | 9.6 | 0 | 23/23 | 3 | 8 |

## Budget check (2026-11-16 04:55 UTC), with the owner's other sessions identified

The "foreign" sessions are the owner's: kmd424lq0snra (cubs), 3tuykgaggfrk8, zej7xs519o3va,
333jokfunags4, aj928f8oi3c4a (m2q) are the ExecuPhone Claude session's (its ledger
~/execuphone/bench/ddp/ledger.tsv); 3i3eqfdajpxrt (gts10pwifi, from 04:45, expiry 07:43) is the
OpenWeights cold/warm study's (peer session mobile-inference-b4). All draw on the same $300.
- Minutes so far: this study 775, ExecuPhone 176 (ended 04:10), cold/warm 7.
- Projection: this study ~1,340 when s6 to s11 end (~05:57 to ~07:00), cold/warm ~100, ExecuPhone
  176 if it starts no more: ~1,610 tablet min = ~$237, plus VMs/disks ~$28 = ~$265. Margin ~$35, so
  no further sessions of this study beyond s6 to s11; delete the VMs, disk and bucket at the end.

## LFM2.5 1.2B matrix (session 7, unit d6761b43 as session 5, 23 turns x 9 cells at 2k)

| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cpu:cpu | passed | 0 | 36.1 | 23 / 0 | 212.3 | 100% | 24.0 | 0.00 | 0.00 | 0.00 | 188.2 | 254 | 40.7 | 0 | 20/23 | - | - |
| npu:cpu | passed | 0 | 42.5 | 23 / 0 | 216.5 | 102% | 25.5 | 0.00 | 0.06 | 0.02 | 191.0 | 239 | 40.1 | 1 | 19/23 | 9 | 5 |
| npu:gpu | passed | 0 | 30.4 | 23 / 0 | 230.6 | 109% | 29.0 | 0.00 | 0.08 | 0.02 | 201.5 | 210 | 38.0 | 0 | 0/23 | 13 | 5 |
| cpu:gpu | passed | 0 | 41.4 | 23 / 0 | 230.8 | 109% | 24.1 | 0.00 | 0.02 | 0.02 | 206.7 | 253 | 37.1 | 0 | 0/23 | 66 | 0 |
| gpu:cpu | passed | 10 | 37.3 | 23 / 0 | 437.2 | 206% | 276.5 | 0.05 | 0.28 | 0.02 | 160.3 | 22 | 47.8 | 0 | 0/23 | 59 | 0 |
| gpu:gpu | passed | 0 | 49.2 | 23 / 0 | 469.6 | 221% | 271.7 | 0.05 | 0.00 | 0.00 | 197.9 | 22 | 38.7 | 0 | 0/23 | - | - |
| cpu:npu | passed | 30 | 34.7 | 23 / 0 | 1011.0 | 476% | 19.4 | 0.00 | 0.03 | 0.00 | 991.5 | 314 | 7.7 | 0 | 0/23 | 16 | 4 |
| npu:npu | passed | 25 | 33.1 | 23 / 0 | 1017.3 | 479% | 25.1 | 0.00 | 0.00 | 0.00 | 992.2 | 243 | 7.7 | 0 | 0/23 | - | - |
| gpu:npu | passed | 0 | 43.0 | 23 / 0 | 1575.4 | 742% | 271.4 | 0.05 | 0.29 | 0.00 | 1303.7 | 22 | 5.9 | 0 | 0/23 | 14 | 4 |

## Heat probe, session 6 (unit 317808dd; phone/hot.sh, Gemma 3 1B, WildChat-1M-18 turns 0-2, CPU only)

Cool probes behind the gate, then 8 busy + 8 dd loops for 20 min (6 threads) and 6 min more (2 threads),
each hot probe started the moment the loops stopped (the Snapdragon r47 protocol).


| probe | threads | gate | status | AP C | CPU ceilings MHz | prefill tok/s | decode tok/s | median step ms | longest ms | > 500 ms | threads on CPUs |
|---|---|---|---|---|---|---|---|---|---|---|---|
| cool6 | 6 | timed_out | 1 | 36.3 | 0:2000/2000,4:2850/2850,7:3400/3400 | 290.5 | 39.90 | 23.4 | 53 | 0 | [0, 1, 2, 3, 4, 5, 6, 7] |
| cool2 | 2 | passed | 0 | 36.1 | 0:2000/2000,4:2850/2850,7:3400/3400 | 156.2 | 31.85 | 31.5 | 46 | 0 | [0, 1, 2, 3, 4, 5, 6, 7] |
| hot6 | 6 | off | 4 | 64.0 | 0:1600/2000,4:1600/2850,7:1600/3400 | 214.9 | 38.75 | 24.5 | 48 | 0 | [0, 1, 2, 3, 4, 5, 6, 7] |
| hot2 | 2 | off | 4 | 64.1 | 0:1600/2000,4:1600/2850,7:1600/3400 | 112.0 | 26.71 | 36.6 | 53 | 0 | [0, 4, 5, 6, 7] |
- 6 threads, hot against cool (same turns): prefill -26%, decode -3%
- 2 threads, hot against cool (same turns): prefill -28%, decode -16%

- At Android status 4 (critical), AP 64 C, every CPU ceiling at 1.6 GHz and all 8 cores online, no
  decode step passed 53 ms: no stall. On SM8850 the hard throttle paused cores 2-7 and 6-thread
  decode fell to 0.24 tok/s. Here the throttle is the clock ceiling alone.
- The 30 s perfetto traces were NOT recorded: hot.sh passed perfetto a config path under
  /data/local/tmp, which it cannot open (SELinux, errno 13). Fixed (stdin); session 7's traces are
  started from the Mac (scratchpad trace7.sh) because its hot.sh was already running.

## Qwen3's first token after NPU prefill is always 151664 (<|file_sep|>), never <think> (151667)

- Every turn whose prompt the NPU read: Qwen3 0.6B (A16W8) 0/69 match the CPU's first token, Qwen3
  1.7B (A16W4) 0/25; always 151664 where CPU and GPU give 151667, and the reply goes on as after
  <think> ("\n", "Okay", ...). A peer session (cold/warm study) saw the same through the app's own
  disaggregated bridge on a Tab S10+ with the 1.7B export: not this bench's code.
- Probably not a wrong logits row: the other models' first tokens after NPU prefill match the CPU's
  in 71% to 84% of turns (Llama 3.2 1B 51/72 = 70.8%, Gemma 3 1B 54/69 = 78.3%, LFM2.5 1.2B 57/69 =
  82.6%, LFM2.5 2.6B 32/38 = 84.2% before its run finished), missing with ordinary different words;
  GPU prefill matches 95.7% to 100% (LFM2.5 1.2B 66/69). A Qwen-specific export or row problem is not
  ruled out by the other models working (corrected in Codex round four).
- Leading explanation, untested: the 16-bit output, calibrated on two short alpaca prompts, clips
  <think>'s large logit at the top of its range together with <|file_sep|>'s, and argmax returns the
  lower index of the tie. Test: dump the prefill logits' top five on the NPU (needs a bench flag and
  a short session). Timing unaffected (every pairing decodes the same number of steps). Applies to
  execupack's Qwen3 MediaTek exports: tell the owner.

## Heat probe, session 7 (unit d6761b43, as session 5; the same hot.sh after LFM2.5 1.2B's matrix)



| probe | threads | gate | status | AP C | CPU ceilings MHz | prefill tok/s | decode tok/s | median step ms | longest ms | > 500 ms | threads on CPUs |
|---|---|---|---|---|---|---|---|---|---|---|---|
| cool6 | 6 | passed | 0 | 23.2 | 0:2000/2000,4:2850/2850,7:3400/3400 | 316.5 | 44.10 | 20.9 | 45 | 0 | [0, 1, 3, 4, 5, 6, 7] |
| cool2 | 2 | passed | 0 | 50.3 | 0:2000/2000,4:2850/2850,7:3400/3400 | 179.2 | 34.50 | 28.3 | 40 | 0 | [4, 5, 6, 7] |
| hot6 | 6 | off | 2 | 52.0 | 0:1600/2000,4:2100/2850,7:2100/3400 | 281.9 | 42.31 | 22.0 | 45 | 0 | [0, 1, 2, 3, 4, 5, 6, 7] |
| hot2 | 2 | off | 2 | 52.8 | 0:1600/2000,4:2100/2850,7:2100/3400 | 174.6 | 34.78 | 27.7 | 38 | 0 | [3, 4, 5, 6, 7] |
- 6 threads, hot against cool (same turns): prefill -11%, decode -4%
- 2 threads, hot against cool (same turns): prefill -3%, decode +1%

- The same 20 min of 8 busy + 8 dd loops took this unit only to status 2, AP 54.6 C, SKIN 40.2 C,
  ceilings 1.6/2.0/2.0 GHz; session 6's unit reached status 4, AP 64.3 C, SKIN 49.8 C, 1.6 GHz on all
  three clusters. How far a unit throttles under the same load differs by unit.
- Scheduler traces (s7hot-hot6/hot2.pftrace, 30 s from the start of each hot probe, started from the
  Mac): with 6 threads the bench used 167.8 CPU-s in 30 s (5.6 cores), on all eight CPUs (52-93% of
  each); with 2 threads 57.0 CPU-s (1.9 cores), mostly CPU 7 (93%) and 4-6. Average clocks under the
  hot 6-thread probe: 1.38 GHz on cpu0-3, 2.21 on cpu4-6, 1.97 on cpu7. No core idle: no pause.

## Qwen3 1.7B Q4_0 and Q8_0 (session 8, unit c8340578, 23 turns x 5 pairings each)

The two npu:npu cells ran the same A16W4 export on the same unit, about 70 min apart: 1,192.9 s
and 1,163.9 s (2.4% apart), decode 6.4 and 6.6 tok/s: the NPU cell repeats within a few percent.
## Qwen3-1.7B-Q4_0  (labels s8; unit c8340578)  PARTIAL: 5 of 9 cells, not a matrix result
| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cpu:cpu | passed | 30 | 35.0 | 23 / 0 | 310.7 | 100% | 40.2 | 0.00 | 0.00 | 0.00 | 270.5 | 147 | 27.2 | 0 | 20/23 | - | - |
| cpu:gpu | passed | 35 | 35.3 | 23 / 0 | 398.2 | 128% | 42.1 | 0.00 | 0.12 | 0.16 | 355.7 | 141 | 20.7 | 0 | 20/23 | 130 | 0 |
| gpu:cpu | passed | 25 | 35.8 | 23 / 0 | 655.1 | 211% | 385.2 | 0.12 | 1.37 | 0.15 | 268.2 | 15 | 27.4 | 0 | 18/23 | 47 | 0 |
| gpu:gpu | passed | 55 | 34.3 | 23 / 0 | 719.4 | 232% | 377.4 | 0.11 | 0.00 | 0.00 | 341.9 | 16 | 21.5 | 0 | 20/23 | - | - |
| npu:npu | passed | 35 | 34.0 | 23 / 0 | 1192.9 | 384% | 48.0 | 0.01 | 0.00 | 0.00 | 1144.9 | 123 | 6.4 | 0 | 0/23 | - | - |
## Qwen3-1.7B-Q8_0  (labels s8; unit c8340578)  PARTIAL: 5 of 9 cells, not a matrix result
| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cpu:cpu | passed | 0 | 43.5 | 23 / 0 | 419.8 | 100% | 40.9 | 0.00 | 0.00 | 0.00 | 378.9 | 145 | 19.4 | 0 | 20/23 | - | - |
| cpu:gpu | passed | 30 | 35.2 | 23 / 0 | 481.8 | 115% | 40.4 | 0.00 | 0.13 | 0.15 | 441.0 | 146 | 16.7 | 0 | 20/23 | 83 | 0 |
| gpu:cpu | passed | 40 | 34.6 | 23 / 0 | 763.5 | 182% | 385.1 | 0.12 | 1.31 | 0.30 | 376.5 | 15 | 19.5 | 0 | 20/23 | 51 | 0 |
| gpu:gpu | passed | 55 | 34.9 | 23 / 0 | 814.1 | 194% | 377.4 | 0.11 | 0.00 | 0.00 | 436.6 | 16 | 16.9 | 0 | 21/23 | - | - |
| npu:npu | passed | 35 | 34.5 | 23 / 0 | 1163.9 | 277% | 47.6 | 0.01 | 0.00 | 0.00 | 1116.3 | 124 | 6.6 | 0 | 0/23 | - | - |

## Codex review, round four (gpt-6-astra, 2026-11-16 ~06:10 UTC): results, analysis and page

Held publication on three points, all fixed:
- The site generator published a model once each cell had one turn (Qwen3 4B appeared with 5 turns
  while running). It now needs a done row after every cell's start, rejects duplicate turns, unequal
  work and an empty intersection, keeps a heat-probe pair only with three matching turns and a
  logged heat-off, and cuts each session's logger to its matrix turns.
- The page said the RoPE fix came before the Llama runs: Llama 3.2 1B ran before it (times stand,
  NPU replies and handoffs used the unscaled table); Llama 3.2 3B ran after.
- The throttle finding merged two units: only 317808dd reached critical (6-thread decode -2.9%);
  d6761b43 reached status 2 (-4.1%); "no core paused" claimed more than a shell sees. Reworded.
Also: the Snapdragon NPU/CPU prefill ratio is now recomputed on the tablet's own turns from the
Snapdragon runs' raw rows (9.2 to 14.6x on matched turns; was 11 to 16x on all 33); process grouping
is a stated limit (NPU decode in GPU -> NPU ran -24% against CPU -> NPU on LFM2.5 1.2B; Qwen3 0.6B's
NPU -> CPU won on decode, 48.2 against 40.5 tok/s, not prefill); memory claims are refusals under the
guard's margin, not proofs; the Qwen3 first-token defect is disclosed on the page as untested; the
analyzer separates lowered ceilings from thermal status; dates, unit reuse and Q8_0 corrected.

## Qwen3 4B (session 10, unit 4bff7833 as session 4, 23 turns x the 4 llama.cpp pairings; NPU refused)

| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | status > 0 | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cpu:cpu | passed | 101 | 35.4 | 23 / 0 | 761.2 | 100% | 125.2 | 0.00 | 0.00 | 0.00 | 636.0 | 47 | 11.6 | 0 | 22/23 | 22/23 | - | - |
| cpu:gpu | passed | 91 | 35.6 | 23 / 0 | 1030.0 | 135% | 114.5 | 0.00 | 0.15 | 0.19 | 915.0 | 52 | 8.0 | 0 | 22/23 | 22/23 | 78 | 0 |
| gpu:cpu | timed_out | 121 | 36.0 | 23 / 0 | 1766.6 | 232% | 1153.0 | 0.11 | 1.64 | 0.20 | 611.5 | 5 | 12.0 | 0 | 17/23 | 23/23 | 69 | 0 |
| gpu:gpu | passed | 101 | 35.5 | 23 / 0 | 1921.1 | 252% | 1067.8 | 0.12 | 0.00 | 0.00 | 853.2 | 6 | 8.6 | 0 | 18/23 | 22/23 | - | - |

## LFM2.5 2.6B matrix (session 9, unit e880d908, 19 turns from conversations-6.jsonl x 9 cells)

First model whose NPU prefill beat its CPU's (pooled 125 against 100 tok/s); NPU -> CPU fastest at 94%.
| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | status > 0 | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| npu:cpu | passed | 0 | 33.4 | 19 / 0 | 337.7 | 94% | 46.0 | 0.00 | 0.11 | 0.02 | 291.5 | 125 | 20.9 | 0 | 7/19 | 16/19 | 6 | 4 |
| cpu:cpu | passed | 0 | 43.4 | 19 / 0 | 358.1 | 100% | 57.4 | 0.00 | 0.00 | 0.00 | 300.8 | 100 | 20.3 | 0 | 18/19 | 17/19 | - | - |
| npu:gpu | passed | 30 | 34.5 | 19 / 0 | 396.0 | 111% | 49.3 | 0.00 | 0.14 | 0.02 | 346.6 | 117 | 17.6 | 0 | 5/19 | 17/19 | 7 | 4 |
| cpu:gpu | passed | 25 | 34.6 | 19 / 0 | 413.9 | 116% | 54.4 | 0.00 | 0.03 | 0.02 | 359.4 | 106 | 17.0 | 0 | 0/19 | 16/19 | 126 | 2 |
| gpu:cpu | passed | 25 | 33.6 | 19 / 0 | 898.9 | 251% | 616.5 | 0.08 | 0.46 | 0.02 | 281.8 | 9 | 21.6 | 0 | 0/19 | 16/19 | 66 | 2 |
| gpu:gpu | passed | 0 | 35.9 | 19 / 0 | 979.6 | 274% | 629.9 | 0.08 | 0.00 | 0.00 | 349.7 | 9 | 17.4 | 0 | 0/19 | 16/19 | - | - |
| npu:npu | passed | 45 | 35.1 | 19 / 0 | 1379.8 | 385% | 45.0 | 0.00 | 0.00 | 0.00 | 1334.8 | 128 | 4.6 | 0 | 0/19 | 0/19 | - | - |
| cpu:npu | passed | 0 | 43.3 | 19 / 0 | 1386.9 | 387% | 44.5 | 0.00 | 0.05 | 0.00 | 1342.3 | 129 | 4.5 | 0 | 1/19 | 0/19 | 5 | 5 |
| gpu:npu | passed | 55 | 33.7 | 19 / 0 | 2270.4 | 634% | 635.4 | 0.08 | 0.48 | 0.00 | 1634.4 | 9 | 3.7 | 0 | 0/19 | 0/19 | 5 | 6 |

## Llama 3.2 3B (session 11, unit 4e2d9065, 24 turns x 5 pairings; RoPE scaling patched in)

npu:npu ran alone to the end; memguard's lowest MemAvailable 625 MB against its 600 MB floor (not triggered).
| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | status > 0 | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| cpu:cpu | timed_out | 121 | 37.5 | 24 / 0 | 638.9 | 100% | 100.9 | 0.00 | 0.00 | 0.00 | 537.9 | 64 | 14.5 | 0 | 23/24 | 24/24 | - | - |
| cpu:gpu | timed_out | 121 | 38.9 | 24 / 0 | 925.3 | 145% | 94.5 | 0.00 | 0.14 | 0.16 | 830.4 | 68 | 9.4 | 0 | 23/24 | 24/24 | 41.5 | 1 |
| gpu:cpu | timed_out | 121 | 37.1 | 24 / 0 | 1571.1 | 246% | 1059.9 | 0.10 | 1.42 | 0.16 | 509.5 | 6 | 15.3 | 0 | 19/24 | 24/24 | 37 | 1 |
| gpu:gpu | timed_out | 121 | 36.6 | 24 / 0 | 1620.1 | 254% | 912.9 | 0.10 | 0.00 | 0.00 | 707.1 | 7 | 11.0 | 0 | 22/24 | 24/24 | - | - |
| npu:npu | timed_out | 121 | 37.3 | 24 / 0 | 1775.5 | 278% | 85.4 | 0.00 | 0.00 | 0.00 | 1690.1 | 76 | 4.6 | 0 | 0/24 | 24/24 | - | - |

## Hard-throttle attempts, 2026-11-16 15:02-16:10 UTC (tablet session 12, unit 4bff7833; S24 s24m, unit 51282bf2)

Probe-only sessions on the second account, mirrored: a Gemma 3 1B warm-up, then hot.sh / hot_sd.sh with
MAXHEAT=2400 (the first heating's cap, 1,200 s in the matrix sessions, now a setting in both scripts and
drivers). The tablet's Gemma NPU export was not reachable (the first account's project lost billing, so
its disks and bucket refuse API calls), so neither attempt followed a matrix.
- Tablet: 41 min of heating, status 4 at most, AP 63.1 C; limits 1.6 GHz on every group and no lower;
  all eight cores online in all 1,328 logger samples. CPU decode 6 threads 37.5 -> 39.1 tok/s (no loss),
  2 threads 35.1 -> 31.4 (-11%); no step over 500 ms.
- S24: 41.2 min, status 4 at most, AP 70.3 C; big-core limits ~0.96-1.08 GHz and no lower; all eight
  cores online in all 1,287 samples. CPU decode 6 threads 25.2 -> 21.3 (-15%), 2 threads 28.2 -> 17.0
  (-40%); no step over 500 ms.
Verdict: doubling the heating did not reach a hard throttle on either device; both hold at critical
status with a fixed clock cap and every core reported online (a shell cannot read the pause switches).

## Exynos and Tensor NPU access, checked on devices (2026-11-17 ~01:20-01:37 PH; tools/eval/streaming/npu_access/)

check_npu.sh reserved a unit, read its SoC, NPU device nodes (owner, mode, SELinux label), tried to open
each from the shell (uid 2000), listed NPU and neural-network services and vendor libraries, and cancelled.
- Exynos 2400 (Galaxy S24+ SM-S926B, ro.soc.model s5e9945, Android 16; the S24 e1s request waited 11 min
  for a unit and was cancelled): the NPU node /dev/vertex10 (vendor_npu_device) and every npu*/unpu*
  node: open denied. The NPU is served only by android.hardware.neuralnetworks-service-enn (Samsung ENN
  behind Android's Neural Networks API); libenn_engine.so is not readable by the shell.
- Tensor G5 (Pixel 10 Pro XL, Android 16): /dev/edgetpu-soc, /dev/edgetpu-limited and /dev/gxp: open
  denied. The NPU is served only by android.hardware.neuralnetworks@service-darwinn-aidl and Google's
  edgetpu/tachyon services; com.google.edgetpu.tachyon-ndk.so is not readable by the shell.
- Tensor G6 (Pixel 11, cubs, Android 17; added 2026-11-17 ~03:40 PH after the live catalog showed the
  Pixel 11 units, session 2xudpgjs0eifo, about 1 min): /dev/edgetpu-soc and /dev/edgetpu-limited
  (edgetpu_device, 0660 system) and /dev/gxp (gxp_device, mode 0666 but SELinux-denied): open denied.
  Served only by android.hardware.neuralnetworks.IDevice/google-edgetpu, android.hardware.npu.IScheduling
  and com.google.edgetpu.{IEdgeTpuAppService,tachyon.IComputeService}. Same verdict as G5.
- Catalog re-read 2026-11-17 (213 models, unchanged count): no Dimensity 9400 or 9500 and no Exynos
  newer than 2400 (the S26 units are q, Snapdragon); the Tab S10+ is still the only MediaTek flagship.
Verdict, now from the devices: neither NPU is accessible to a shell; the only route is Android's Neural
Networks API (deprecated since Android 15), through which neither llama.cpp nor ExecuTorch runs these
LLMs with a prefill/decode cache handoff. The goal's condition ("only if their NPUs are accessible") is
not met, so no Exynos or Tensor matrix.

## 2.5-hour heat reruns, 2026-11-16 18:33-21:27 UTC (tablet s14, unit d6761b43; S24 s24o, unit ccc0f521)

Probe-only, mirrored, second account: a Gemma 3 1B warm-up, then hot.sh / hot_sd.sh with the heating cap
raised to the session's remaining time (9,000 s and 8,895 s), no matrix before. Both logged HOT_DONE, every
probe rc 0; each driver pulled its files and ended its session (s14 21:23, s24o 21:27 UTC). On the page they
replace the 41-minute attempts (s12, s24m) as each device's longest probe.
- Tablet: 153.6 min of heating, status 2 (moderate) at most, AP 55.1 C; limits 1.6 / 2.2 / 2.2 GHz
  (A720, X4 4-6, X4 7), 1.6 / 2.1 / 2.1 for the 2-thread probe; all eight cores online and none isolated in
  all 3,576 logger samples from heat-on to the end. CPU 6 threads: decode 40.6 -> 39.5 tok/s (-3%),
  prefill 306 -> 284 (-7%); 2 threads: decode 34.6 -> 34.0 (-2%), prefill 180 -> 176 (-2%); no step over
  500 ms (longest 57 ms).
- S24: 152.0 min, status 4 (critical) at most, AP 62.2 C; limits 1.69 / 1.29 / 1.40 / 1.36 GHz (A520,
  A720 2-4, A720 5-6, X4); all eight online, none isolated, in all 3,251 samples. CPU 6 threads: decode
  24.7 -> 22.3 (-10%), prefill 336 -> 192 (-43%); 2 threads: decode 29.2 -> 21.8 (-25%), prefill 162 -> 89
  (-45%); no step over 500 ms (longest 83 ms).
Verdict: after 2.5 hours of busy loops neither rerun read a core offline or isolated, and no decode step
passed 500 ms; a shell cannot read the core-pause switches, so a pause is not excluded. Heat responses
differed across the units tested: tablet unit d6761b43 stayed at moderate here as in its 20-minute probe
(session 7), while units 317808dd and 4bff7833 reached critical within 21 and 41 minutes; the S24's unit
ccc0f521 held higher limits (1.29-1.40 GHz) than unit 51282bf2 at 41 minutes (0.96-1.08 GHz). These
sessions do not isolate the cause (unit, placement in the lab, or room). The page states the tablet's split
under the heat figure (computed, unitSplit), names the left-out 41-minute run on unit 4bff7833 (leftOut),
and says the runs do not show which chip throttles more (Codex round 3, codex-simplify-review-3.txt).
Rates are now stored at six decimals: two-decimal storage rounded twice and moved about 25 displayed
numbers by one in the last digit (29.15 showed as 29.1 for 29.154); no finding changed.
