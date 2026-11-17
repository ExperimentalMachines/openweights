# Snapdragon 8 Gen 3 (Galaxy S24 Ultra) prefill/decode matrix, unrooted, 2026-11-16

Why: the MediaTek tablet (Dimensity 9300+, late 2023) read prompts on its NPU at 0.5 to 0.9x its
CPU, where the Snapdragon 8 Elite Gen 5 reference phone (late 2025) read them 9 to 15x on matched
turns. The 8 Gen 3 is the Dimensity 9300+'s generation, with the same core types (Cortex-X4 and
A720; 1 X4 + 5 A720 + 2 A520 here, 4 X4 + 4 A720 there), so it separates "when" from "who". Same
three models as the tablet's cleanest rows, same turns (2,048-token window rule), same seeded orders.

## Build and package (VM mtk-build, ~/sdw)

llama.cpp b10549 (b2e5e9b), ghcr.io/snapdragon-toolchain/arm64-android:v0.7 (NDK r29, Hexagon SDK
6.6.0.0), preset arm64-android-snapdragon-release (CPU -march=armv8.7a+fp16+dotprod+i8mm, as the
tablet's build; OpenCL; Hexagon skels v73/v75/v79/v81). The first configure failed with every
try_compile failing ("CMAKE_CXX_COMPILER not set"); rerun with --debug-trycompile it configured and
built. pd_bench_sd from tools/snapdragon/pd_bench_sd.cpp (pd_bench.cpp ported: unrooted heat via
dumpsys thermalservice + cpufreq + kgsl, the tablet's gate, --window/--pad-batch, MemAvailable
check, the tablet's field names). Binary sha256 prefix fb8524aa5e738bbb, libs 0e6b212c7f149d5d.

## Device (session hir29d7405xqa, unit ccc0f521)

SM-S928B (international S24 Ultra), ro.soc.model SM8650, board pineapple, Android 16, MemTotal
11,351,852 kB. CPU policies 0 (2.27 GHz), 2 (3.15), 5 (2.96), 7 (3.40). Adreno 750, max_gpuclk
1,000 MHz readable, gpuclk not (gpu_cur_mhz serialises as 0 = unreadable; Codex: should be -1).

## Pilot (probe-s24.jsonl): llama.cpp's Hexagon backend runs unrooted on a retail phone

ggml-hex: Arch v75, HTP0 session on cdsp (unsigned), hwinfo threads 4 hvx 4 hmx 1 vtcm 8 MB. All
three copies of Qwen3 0.6B loaded in one process: MemAvailable 6,539 -> 3,379 MB. First two turns
of WildChat-1M-18, cool: NPU prefill 732 (63 tokens) and 1,762 tok/s (457) against CPU 506 and 533,
GPU 556; decode NPU 27.6/24.3, CPU 35.5/29.0, GPU 28.8 tok/s. The NPU's first token is <think>
(151667), as on CPU and GPU (MediaTek's Qwen3 exports gave 151664).

## Codex review of the port (round five)
- Fixed before the run resumed: single-quoted $D in session_sd.sh (final pull and dl.log readout
  would have run in the phone's home directory); the driver was stopped in its hold loop and the
  session resumed with RESUME=.
- Fixed after (new file + rename while the driver ran): dl.log marks a model ready only when the
  phone's copy matches the Mac's size and checksum prefix.
- Left in this run's binary, noted: unreadable GPU clocks serialise as 0; cell rows say ctx 8192
  (load rows have the allocated 2,304); a full HAL read after every turn (the tablet read clocks
  only), about a second of untimed idle per turn.

## Run s24a (06:58 to 08:39 UTC): Qwen3 0.6B complete; Llama 3.2 1B cut off; Gemma 3 1B not started

At 08:38:57 adb's link to the bridge closed (bridge-resume.log: "adb side ended: ConnectionError('adb closed')")
while the session had 72 min left; the driver read one failed get-state as a lost device and
cancelled. Llama 3.2 1B had finished 3 of 9 pairings (cpu:gpu, npu:gpu, npu:npu: kept, not used).
Both drivers now reconnect for about two minutes before giving up (alive()).

Qwen3 0.6B, 23 turns (the generator checked they are the tablet's 23), all nine pairings in one process:

| cell | gate | wait s | AP C | turns ok / failed | total s | vs cpu:cpu | prompt s | 1st tok s | handoff s | clear s | decode s | prefill tok/s | decode tok/s | stalls | capped turns | status > 0 | agree w/ D:D | turns < 4 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| npu:cpu | passed | 106 | 39.5 | 23 / 0 | 279.6 | 91% | 4.1 | 0.02 | 0.12 | 0.00 | 275.2 | 1452 | 26.7 | 0 | 20/23 | 21/23 | 51 | 0 |
| npu:npu | passed | 76 | 39.5 | 23 / 0 | 305.5 | 100% | 4.1 | 0.01 | 0.00 | 0.00 | 301.4 | 1433 | 24.4 | 0 | 5/23 | 20/23 | - | - |
| cpu:cpu | passed | 41 | 39.5 | 23 / 0 | 306.0 | 100% | 19.5 | 0.02 | 0.00 | 0.00 | 286.5 | 303 | 25.7 | 0 | 22/23 | 21/23 | - | - |
| gpu:cpu | passed | 71 | 38.7 | 23 / 0 | 306.7 | 100% | 15.7 | 0.06 | 0.18 | 0.00 | 290.3 | 376 | 25.4 | 0 | 22/23 | 21/23 | 45 | 0 |
| gpu:npu | passed | 116 | 38.9 | 23 / 0 | 315.6 | 103% | 11.2 | 0.07 | 0.21 | 0.00 | 303.7 | 527 | 24.2 | 0 | 12/23 | 20/23 | 35 | 0 |
| cpu:npu | passed | 46 | 39.0 | 23 / 0 | 319.6 | 104% | 17.6 | 0.02 | 0.11 | 0.00 | 301.7 | 336 | 24.4 | 0 | 22/23 | 20/23 | 36 | 0 |
| gpu:gpu | timed_out | 121 | 40.6 | 23 / 0 | 449.2 | 147% | 12.6 | 0.05 | 0.00 | 0.00 | 436.6 | 470 | 16.9 | 0 | 7/23 | 23/23 | - | - |
| cpu:gpu | passed | 86 | 38.6 | 23 / 0 | 465.0 | 152% | 14.6 | 0.01 | 0.20 | 0.00 | 449.8 | 405 | 16.4 | 0 | 21/23 | 16/23 | 19 | 0 |
| npu:gpu | timed_out | 121 | 39.7 | 23 / 0 | 467.9 | 153% | 4.2 | 0.02 | 0.24 | 0.00 | 463.1 | 1395 | 15.9 | 0 | 22/23 | 23/23 | 23 | 0 |

Against the tablet's Qwen3 0.6B (same turns): NPU prefill 4.8x the CPU (tablet 0.5x), NPU decode
0.95x the CPU (tablet 0.17x), GPU prefill 1.2-1.7x (tablet 0.14x); fastest NPU -> CPU on both (91% /
94%); NPU-only ties CPU-only here (100%), 558% on the tablet. The tablet's CPU decodes faster (40.5
against 25.7 tok/s: four Cortex-X4 cores against one).

## CPU layout read from the registers (unit a64940db, 2026-11-16 ~10:00 UTC)

cpu0-1 part 0xd80 (Cortex-A520), policy0 2.27 GHz; cpu2-4 0xd81 (Cortex-A720), policy2 3.15 GHz; cpu5-6
0xd81 (Cortex-A720), policy5 2.96 GHz; cpu7 0xd82 (Cortex-X4), policy7 3.40 GHz. The tablet (MT6989):
4x A720 2.0 GHz + 3x X4 2.85 + 1x X4 3.4. Same core generation; the tablet has four X4s to the S24's
one, which fits its faster CPU decode (Qwen3 0.6B 40.5 against 25.7 tok/s on the same turns).

## Run s24b (unit a64940db): Llama 3.2 1B, then Gemma 3 1B on conversations-6.jsonl

The push hit an adb gap (two GGUF pushes failed; the new size and checksum check kept them out of
dl.log, so the chain could not start a model that was not there); both were re-pushed, verified, and
the chain started by hand at 08:52 UTC. The gcloud login for alpha@pytorch.ph had lapsed; after it was
renewed the bridge was restarted (adb said "device" while every shell said "closed"). Gemma switched
to the six-conversation subset mid-run (queue rewritten in place on the phone, the line the chain had
not read yet): measured Llama pairings took 8 to 9 min plus a 2-min gate that timed out every time
(this S24 runs hot: status 3, CPU ceilings ~1.3-1.7 GHz), so Gemma over all 23 turns (~85 min) would
have run past the 11:47 expiry. The site compares it against the tablet's Gemma rows on the same turns.
The Mac's disk filled at ~09:45 UTC (owner cleared it); the pull loop retried and resumed.

### s24b Llama 3.2 1B: complete (09:01:50 to 10:23:31 UTC, 9 pairings x 24 turns, failures 0)

Same 24 turns as the tablet's s4, and the same work per turn (pos/new tokens/decode steps, checked by
the site's generator across devices). Fastest NPU -> NPU 373.2 s; NPU -> CPU +9%; CPU only +19%;
GPU -> GPU slowest, +34%. Pooled rates: NPU prefill 1,540 tok/s against CPU 182 (8.45x); NPU decode
21.0 against CPU 19.4 tok/s (1.08x: the only shared model where an NPU out-decoded its CPU); GPU
prefill 177 (0.97x), GPU decode 17.2. Every one of the nine gates ran out (121 s): heat status 3 or 4
in 300 of 318 readings during timed turns, every CPU limit at its top in 3.4% of samples (lowest
1.69/1.29/1.40/1.36 GHz), AP up to 58.6 C; no CPU decode step over 500 ms (longest 147 ms). The
tablet's s4 on the same turns: CPU -> CPU 280.2 s, NPU prefill 0.88x, NPU decode 0.27x, all nine gates
passed, limits at top in 58.8% of samples. The run cannot separate why the tablet finished first (core
mix, runtimes, heat and memory all differ); that note above about four X4 cores is context, not a cause.

The first unit's (ccc0f521, s24a) three finished Llama pairings, for comparison with this unit's:
CPU -> GPU, NPU -> GPU and NPU -> NPU (published in the page's "Every number" fold).

## Codex fairness review, round one (codex-fairness-review-1.txt)

Fixed in the site's generator and page: heat counted only while a turn was timed (the S24's Qwen
share at top fell from 33.0% to 23.6%, the tablet's rose from 92.4% to 92.8%); a failed turn now
withholds a model and every pairing must read the same turns; the same work per turn is checked across
devices; the tablet's Gemma twin carries the six-conversation count; "run the same way", the core
count read as a cause, "neither got stuck" and "a second of untimed idle" are gone; gate outcomes are
on the page (S24 11 of 18 compared pairings ran out, tablet 0 of 18).

## Codex fairness review, round two (codex-fairness-review-2.txt)

Confirmed: round-one fixes hold (timed-turn heat, gate counts, no causal core-count claim, the stall
counts), shared work identical per turn on both devices, the cover's 6.3x against 0.7x is the same
statistic for both, and the partial Gemma does not leak. Fixed after it: turn_error rows now withhold a
model too; every NPU-read Qwen3 turn on the tablet (0.6B, 1.7B Q4_0 and Q8_0) had eog_at 0, its first
token ended the reply (the S24: 0 of 69), so the tablet's Qwen3 0.6B NPU -> CPU win is forced fixed-work
throughput, now said beside the finding and counted per pairing; "same files" dropped (the tablet's NPU
reads its own A16W8 export); "could not cool" and the form-factor causes dropped; the slow-step table
shows each pairing's own median; headline conditions made complete (non-empty, "close" = within a fifth,
either device first); heat probe durations 20.5 min then 6.2 min more; memory in MiB; source links name
both devices' folders.

### s24b Gemma 3 1B: complete (10:25:57 to ~11:28 UTC, 9 pairings x 17 turns on conversations-6, failures 0)

Session cancelled by the driver at 11:28:42 after its final pull (162 min; done rows for both models).
Fastest NPU -> NPU 262.7 s; NPU prefill 5.68x CPU, NPU decode 1.06x. The tablet's Gemma rebuilt on the
same 17 turns (same work per turn, checked): CPU -> CPU 179.0 s, NPU prefill 0.64x, decode 0.26x. Every
gate ran out. Starting heat did not depend on running after Llama: Llama's pairings (first model on the
unit) started at AP 41.7-45.2 C, status 2-3; Gemma's, straight after, 42.9-44.8 C, status 2-3; the s24a
unit (Qwen3 0.6B) started cooler, 38.6-40.6 C, status 0, and passed 7 of 9 gates. The unit matters more
than the order, on this phone as on the tablet. Owner chose next (2026-11-16 ~11:30 UTC): the other six
models on the S24, one fresh unit each, six sessions at once, on a new account (queues q-s24c..h.txt,
conversations-3.jsonl for Qwen3 4B).

## Codex fairness review, round three (codex-fairness-review-3.txt)

Confirmed: the three-model numbers recompute from raw rows (Gemma's twin matches the S24's 17 turns in
pos, new tokens, decode steps, output and reference tokens; cover 5.7x against 0.6x), the per-model
switch views and their defaults are fair, heat is read only inside matched turns. Fixed after it: "All
models", the NPU/GPU duel and the first-token table now use the matched rows (the tablet's whole Gemma run
is its own labelled row); the matrix view no longer shows the S24 under the tablet's name when a tablet
twin is withheld; "finished first" uses the faster device as denominator and says how many models each
won when they split; GPU and heat headlines need explicit thresholds (GPU at least 0.9 of its CPU against
0.2 or less; CPU limits lower on every model and half or more pairings starting warm); empty comparisons
render no findings; "the rest went on alike" removed; heat shares rounded once.

S24 unit-to-unit check from the cut-short first Llama run (same 24 turns, two units): CPU -> GPU +2.2%,
NPU -> NPU -2.0%, NPU -> GPU -11.6%. Unit variance, not run order, bounds these single runs.

## Six more models, one fresh unit each (second account, project voltaic-psyche-514908-s2), 2026-11-16

alpha@viableview.com; the new project needed devicestreaming, testing and toolresults APIs (the first
reserve failed 403 on testing.googleapis.com before any session existed). Sessions s24c..s24h reserved
11:39-11:41 UTC on ports 5611-5616, Gemma 3 1B heat probes queued after s24c and s24d. Units include
SM-S928B and SM-S928U1, all SM8650. The warm-up is a full CPU-only pass (16-20 min on the 3B/4B), so
Llama 3.2 3B moved to conversations-3.jsonl and Qwen3 4B's admission estimate came down (queues rewritten
on the phones before admission).

Memory: one process loads a copy per processor. For Qwen3 1.7B Q8_0, LFM2.5 2.6B and Llama 3.2 3B the
memory guard refused the NPU's copy beside the CPU's and GPU's; for Qwen3 4B (s24h) even the GPU's beside
the CPU's (3,669 MB free, 3,742 needed), so s24h ran CPU only and ended at 42 min. Fixed as the tablet
did: the NPU pairings appended to the same unit's queue as two-copy processes (NPU with CPU, NPU with
GPU), which load fine (Llama 3.2 3B: CPU copy at 7,214 MB free, NPU at 4,686). Qwen3 4B reran as s24i
(unit f36d3562) in three two-copy processes with --mem-margin-mb 768 (memguard.sh still kills under 600).

Done so far: s24g Llama 3.2 3B (81 turns, 88 min), s24f LFM2.5 2.6B (171 turns, 122 min), s24c LFM2.5
1.2B (207 turns + heat probe, 125 min), all 0 errors. S24 heat probe (s24c, Gemma 3 1B, unit a64940db):
cool probes started at status 3 (the gate never cooled it), heated to status 4 with big-core limits
~0.96-1.08 GHz; CPU decode 24.6 -> 20.4 tok/s at 6 threads (-17%), 28.4 -> 17.1 at 2 threads (-40%);
all eight cores online throughout, longest step 298 ms: a soft throttle only, no Elite-style core pause.
Backups: the drivers pull to this folder every 3 min; ~/ow-backup/s24-2026-11-16/ gets a copy every 60 s
(backup_loop.sh, never stops on a failed copy).

## All nine rows on both devices (s24d closed 14:30 UTC); Codex fairness review, final round (codex-fairness-review-4.txt)

s24e Qwen3 1.7B Q8_0 (207 turns, 3 processes, 148 min), s24i Qwen3 4B (81 turns, 3 processes, margin
768 MiB, lowest MemAvailable 1,122 MB, 87 min), s24d Qwen3 1.7B Q4_0 (207 turns, one process, 171 min,
second S24 Gemma heat probe with a 15-min first heating as the session ran short). All 81 S24 pairings,
1,530 turns, no failed turn; work per turn matches the tablet on every model. Second S24 probe: 6
threads 24.9 -> 17.9 tok/s (-28%), 2 threads 26.9 -> 18.5 (-31%), status 4, all cores online.
Codex's final round confirmed the data, sdShared and the probe arithmetic, and found presentation issues,
all fixed: the S24's restricted Qwen3 4B row had been labelled "NPU refused" and supplied its fastest
(GPU -> CPU 323.5 s) where its real best was NPU -> NPU 282.1 s (finish-time comparisons now use each
device's whole run over the same turns; pooled ratios keep the shared pairings); the page said the S24 ran
one process (now stated per model from done-row counts: the field "processes"); pooled rate tables and the
cover now use the shared pairings (cover 8.7x against 0.9x over the eight models with NPU figures on
both); the heat headline no longer says "only" and needs lower limits plus real losses; unequal probe
heating (15 vs ~21 min) and starting status (S24 2-3, tablet 0-1) are stated, and the probes are called
not a controlled test of which throttles more; ties and an empty NPU group are handled; the Q8_0 file is
named where files are described. Session minutes on the second account: 790 (s24c..s24i), about $119 at
$0.15/min, about $87 if each session's first 30 minutes are free.

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

## Turn parity, 2026-11-16 15:02-16:55 UTC (second account; one fresh unit each)

- s24j Gemma 3 1B, all 23 turns, nine pairings (unit 4eb65273, 207 turns, 0 errors, 91 min): NPU prefill
  5.22x CPU, decode 1.04x; fastest NPU -> NPU 359.7 s against the tablet's CPU -> CPU 227.8 s (session 6).
- s24k Llama 3.2 3B, all 24 turns, the tablet's five pairings (unit dede0011, 120 turns, 0 errors, 92 min,
  two processes): NPU prefill 8.75x, decode 1.07x; fastest NPU -> NPU 630.6 s against the tablet's CPU ->
  CPU 638.9 s: on full turns the S24 finished this model first (1.3%), where the 9-turn subset had the
  tablet ahead. Full turns changed a finding.
- s24l Qwen3 4B, all 23 turns, the tablet's four pairings (unit ccc0f521, 92 turns, 0 errors, 112 min):
  fastest CPU -> CPU 1,107.3 s against the tablet's 761.2 s. Its CPU and GPU copies shared one
  process with --mem-margin-mb 768, as s24i (queue phone_sd/q-s24l.txt, line 4).
The replaced subset runs (s24b Gemma, s24g Llama 3.2 3B, s24i Qwen3 4B) stay in the generated data as
sdExtra but are not shown on the page since 2026-11-17 (owner: exclude superseded reruns), never compared. Bug found and fixed on the way: sdShared rebuilt a restricted
row from the first config entry with the same model (the old subset run), mixing 9 turns into a 24-turn
comparison; it now takes the entry with the row's own session, and does not restrict a parity run that
already ran the tablet's pairings. Every compared pair now reads the same turns and the same pairings.
Cover after parity: 8.6x against 0.9x. Second-account minutes: 790 (s24c-s24i) + 424 (s24j 91, s24k 92,
s24l 112, s24m 67, s12 62) = 1,214, about $182 at $0.15/min (about $128 with 30 free minutes per session).

## Codex check after parity (codex-fairness-review-5.txt)

Confirmed: full parity (64 shared pairings, 1,450 turn rows per device, every turn's work equal), the
sdShared session fix, the findings and cover (8.60x against 0.86x; Llama 3.2 3B's 1.29% S24 lead is
real; finish margins 1.29-36.67%), and both long probes (no hard throttle observed; 1,328 and 1,287
logger samples all 0-7). Fixed after it: S24 cells left out to match the tablet now read "not run, to
match the tablet" (they had borrowed the tablet's "NPU refused" and "did not fit" labels); the memory note
names only runs whose guard refused a copy (refusedLoads, new field) and says Llama 3.2 3B split by plan;
extra rows keep their own pooled rates; the heat limitation counts distinct units (tablet three, S24 two:
51282bf2 was heated twice); the heat finding and baseline note name the 20-minute cohort; the Heating
switch shows the measured minutes; mtkSessions keeps only each model's published session; the model
picker lists each model once; the finish comparison is "each device's fastest among the pairings it ran
on those turns".

## Ledger, second account (2026-11-17 19:35 UTC)

session-minutes-2026-11-17.txt lists every Device Streaming session on project voltaic-psyche-514908-s2,
creation to end (the API leaves activation times empty, so each span includes the wait for a device:
the safe side). 19 sessions, 1,484 minutes so far ($223 at $0.15/min); 1,730 minutes ($259) if the two
running heat reruns (s24o e3q, s14 gts10pwifi) run to their 21:33 expiry. Under $300 at list price before
any free minutes. Matches the earlier tally: 1,214 + NPU checks (mustang 1, e1s 13, e2s 1) + the two
cut-short sessions (s24n 68, s13 67) + the reruns so far. The first account's spend (alpha@pytorch.ph,
the $300 credit) cannot be re-read since its project lost billing; its last budget check, in the
tablet's NOTES.md, projected about $265 with VMs and disks.
Plus the Tensor G6 access check (Pixel 11, session 2xudpgjs0eifo, about 1 min): about 1,731 minutes, $260.

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

## Ledger, second account, final (2026-11-16 21:35 UTC)

session-minutes-2026-11-17-final.txt: 20 sessions on voltaic-psyche-514908-s2, every one FINISHED, 1,713
minutes creation to end, $257 at $0.15/min before any free minutes. The two reruns came in at 173.9 and
169.9 minutes (they ended before the 21:33 expiry). Nothing is running on this project. First account: last
projection about $265 with VMs and disks (tablet NOTES.md); not re-readable since its billing was lost.

## Why two accounts (recorded 2026-11-16 21:50 UTC)

The study started on the goal's account, alpha@pytorch.ph (project thematic-garage-514815-a4, the $300
credit): the whole tablet matrix (sessions 3 to 11), its heat probes s6/s7, and the S24's first three
models (s24a, s24b). At 09:40 UTC on 2026-11-16 that project had about $280 spent, projected $297 to $300.
At 11:06 UTC I asked whether to run the S24's other six models, saying it would take the total to about
$400 against the $300 credit; the owner answered "I'm switching accounts. Tell me if it's finished", then
"in parallel" and "go" (11:34 UTC). Everything after that (s24c onward, tablet probes s12 to s14, the NPU
access checks; session.log names each project) ran on the owner's new project voltaic-psyche-514908-s2: $257 at list price, above. Read at
21:50 UTC: thematic-garage-514815-a4 is ACTIVE with billing disabled (billingEnabled False), as when its
disks and bucket began refusing calls.
The owner kept directing work on the new project after the switch: "rerun both. Then move on to what I
said" (18:32 UTC), after s24n and s13 were cancelled at 18:29 ("Don't cancel them"), started s24o and s14 there.
Read at 21:55 UTC with alpha@pytorch.ph: its only billing account, 0165ED-A61C4C-B8FA5D ("My Billing
Account"), is closed (open False), so the $300 credit is spent and no session can be reserved there. Reopening
it needs the owner to add payment in the Cloud console, and any further spend there would exceed the goal's
$300 cap; neither is mine to do.
