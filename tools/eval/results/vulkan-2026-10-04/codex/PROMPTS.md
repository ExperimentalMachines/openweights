# The prompts each Codex review was given

Every review ran as `codex exec -m gpt-6.1-sol -c model_reasoning_effort=medium -s read-only` (a read-only sandbox:
the app reviews report no builds or device tests; the execupack review reports passing Ruff and seven version tests), from the repository named, and wrote its final message to the file named. Prompts are
verbatim from the session; scratch paths are shortened.

## 1.5.1-execuserve-r1.md

2026-10-04 01:38 UTC, from `~/execuserve`

```text
Review the uncommitted changes in this repository (git diff HEAD) as a QA reviewer. The change upgrades the ExecuTorch Android runtime (org.pytorch:executorch-android) from 1.4.0 to 1.5.1. Facts already established: ExecuTorch
```

## 1.5.1-openweights.md

2026-10-04 01:38 UTC, from `~/mobile-inference`

```text
Review the uncommitted changes in this repository (git diff HEAD) as a QA reviewer. The change upgrades the ExecuTorch Android runtime (org.pytorch:executorch-android) from 1.4.0 to 1.5.1. Facts already established: ExecuTorch
```

## 1.5.1-execuserve-r2.md

2026-10-04 01:41 UTC, from `~/execuserve`

```text
Re-review the uncommitted diff (git diff HEAD), focusing on runtimeMismatch in shared/catalog/.../HfCatalog.kt and its test: the intent is to warn when a model's ExecuTorch export version is newer than the app's runtime (including patch level), of a different major version, or more than one minor release older; otherwise no warning. Report concrete bugs only, or say there are none.
```

## 1.5.1-execupack.md

2026-10-04 02:10 UTC, from `~/execupack-vulkan`

```text
Review the uncommitted diff (git diff HEAD) as QA. It moves this exporter's ExecuTorch pin from 1.4.0 to 1.5.1 (torch 2.13.0 -> 2.14.0, pytorch-tokenizers 1.4.1 -> 1.5.0, torchvision 0.28.0 -> 0.29.0, EXECUTORCH_COMMIT to the v1.5.1 tag commit 3b60683923245cf472b7323426920e15623ba361). Established: torchao stays 0.18.0, QAIRT stays 2.37.0.250724, examples/mediatek is byte-identical between the tags, the vendored third_party params files are byte-identical, export_llm's ModelType is unchanged, Qualcomm's registry only gained entries, the custom LLM op schemas are unchanged. Read CLAUDE.md's constraints (version lock with the app AAR, tests/test_versions.py). Look for pins or references that must move together but did not, anything in workflows (.github/) or scripts that hard-codes 1.4.0/2.13.0/0.28.0, and anything that would break CI. Give file, line, why, fix. Report only real problems; say plainly if none.
```

## vulkan-openweights-r1.md

2026-10-04 03:11 UTC, from `~/mobile-inference`

```text
QA-review the uncommitted diff (git diff HEAD, plus new untracked files under core/engine or android/executorch named VulkanSupport.kt). Goal: Vulkan GPU exports of ExecuTorch models must work on every Android device that can run them and never strand a user on one that cannot. The change ships org.pytorch:executorch-android-vulkan:1.5.1 (registers both XnnpackBackend and VulkanBackend) instead of executorch-android, offers Vulkan files only when PackageManager reports FEATURE_VULKAN_HARDWARE_VERSION >= 1.1 and no earlier refusal is recorded, and records a refusal (per runtime release, in SharedPreferences) when a Vulkan file fails to load or fails on its first run, because ExecuTorch shaders check device features at dispatch and throw "not compatible with device". Look for: logic bugs (e.g. a non-GPU failure wrongly disabling Vulkan forever, cancellation or context-overflow treated as GPU failure, a Vulkan file misidentified as CPU or vice versa, install-id collisions), thread-safety, places that still assume XNNPACK-only, R8/ProGuard keep rules for the new AAR, and anything that breaks release builds. Give file, line, why, fix; report only real problems or say there are none.
```

## vulkan-execuserve-r1.md

2026-10-04 03:11 UTC, from `~/execuserve`

```text
QA-review the uncommitted diff (git diff HEAD, plus new untracked files under core/engine or android/executorch named VulkanSupport.kt). Goal: Vulkan GPU exports of ExecuTorch models must work on every Android device that can run them and never strand a user on one that cannot. The change ships org.pytorch:executorch-android-vulkan:1.5.1 (registers both XnnpackBackend and VulkanBackend) instead of executorch-android, offers Vulkan files only when PackageManager reports FEATURE_VULKAN_HARDWARE_VERSION >= 1.1 and no earlier refusal is recorded, and records a refusal (per runtime release, in SharedPreferences) when a Vulkan file fails to load or fails on its first run, because ExecuTorch shaders check device features at dispatch and throw "not compatible with device". Look for: logic bugs (e.g. a non-GPU failure wrongly disabling Vulkan forever, cancellation or context-overflow treated as GPU failure, a Vulkan file misidentified as CPU or vice versa, install-id collisions), thread-safety, places that still assume XNNPACK-only, R8/ProGuard keep rules for the new AAR, and anything that breaks release builds. Give file, line, why, fix; report only real problems or say there are none.
```

## vulkan-openweights-r2.md

2026-10-04 03:22 UTC, from `~/mobile-inference`

```text
Re-review the uncommitted diff (git diff HEAD, including untracked VulkanSupport.kt and VulkanSupportTest.kt). A previous review found: (1) unrelated failures permanently disabled Vulkan, (2) some dispatch paths (prefill, warm-up, image prefill) bypassed the check, (3) install names could collide between xnnpack/ and vulkan/ folders, (4) repository names overrode file paths when classifying backends; and in ExecuServe a false context-overflow match on max_context_len and stale GPU listings after a refusal. The redesign records a refusal only when the failure message chain contains ExecuTorch Vulkan incompatibility text (not compatible with device, physical device feature, VulkanBackend not available/registered, VK_ERROR_INITIALIZATION_FAILED/INCOMPATIBLE_DRIVER/FEATURE_NOT_PRESENT/EXTENSION_NOT_PRESENT; deliberately not out-of-device-memory), applied at every runtime entry point. Check whether those issues are actually fixed and whether the redesign introduced new real bugs. File, line, why, fix; real problems only, or say none.
```

## vulkan-both-r2.md

2026-10-04 03:22 UTC, from `~/execuserve`

```text
Re-review the uncommitted diff (git diff HEAD, including untracked VulkanSupport.kt and VulkanSupportTest.kt). A previous review found: (1) unrelated failures permanently disabled Vulkan, (2) some dispatch paths (prefill, warm-up, image prefill) bypassed the check, (3) install names could collide between xnnpack/ and vulkan/ folders, (4) repository names overrode file paths when classifying backends; and in ExecuServe a false context-overflow match on max_context_len and stale GPU listings after a refusal. The redesign records a refusal only when the failure message chain contains ExecuTorch Vulkan incompatibility text (not compatible with device, physical device feature, VulkanBackend not available/registered, VK_ERROR_INITIALIZATION_FAILED/INCOMPATIBLE_DRIVER/FEATURE_NOT_PRESENT/EXTENSION_NOT_PRESENT; deliberately not out-of-device-memory), applied at every runtime entry point. Check whether those issues are actually fixed and whether the redesign introduced new real bugs. File, line, why, fix; real problems only, or say none.
```

## vulkan-openweights-r3.md

2026-10-04 08:02 UTC, from `~/mobile-inference`

```text
Review commit 55e4f681 (git show 55e4f681) in this repo: Vulkan support for the ExecuTorch engine. Earlier reviews found and the commit claims to fix: (1) unrelated failures permanently disabling Vulkan; (2) prefill/warm-up/image prefill bypassing refusal handling; (3) NativeExecuTorchBridge treating any max_context_len mention as overflow, hiding Vulkan errors; (4) xnnpack/ and vulkan/ files of the same basename installing over each other; (5) a repository name with XNNPACK or Vulkan overriding the file path backend. Verify each is actually fixed and look for any new concrete defect (correctness, thread safety, R8, release-build risk). Report only real problems with file:line and a fix, ranked P1-P3, or say none.
```

## vulkan-execuserve-r3.md

2026-10-04 08:02 UTC, from `~/execuserve`

```text
Review commits c67ec10 and 13085b1 (git show c67ec10 13085b1) in this repo: Vulkan support in ExecuServe. Earlier reviews found and these commits claim to fix: (1) unrelated failures persistently disabling Vulkan; (2) max_context_len in normal logs turning GPU failures into overflow; (3) stale catalog still offering GPU downloads after a refusal; (4) backend identified from arbitrary file names; (5) xnnpack/ and vulkan/ files with the same basename sharing an install id; (6) pull with an explicit vulkan/ path selecting the xnnpack file. Verify each is actually fixed and look for any new concrete defect (correctness, thread safety, R8, release-build risk). Report only real problems with file:line and a fix, ranked P1-P3, or say none.
```

## vulkan-openweights-r4.md

2026-10-04 08:18 UTC, from `~/mobile-inference`

```text
Review the uncommitted diff (git diff HEAD). It fixes your last finding: a repository named for Vulkan (publisher/Qwen3-Vulkan) holding xnnpack/model.pte installed under a name CompiledBackend.of read as Vulkan, so ChatViewModel labelled a CPU model GPU. The fix: CompiledBackend.of now picks the backend whose marker appears LAST in the text (installed names are repo name then file name), and ExecuTorchFileName.modelNameFor appends the path backend when the name would otherwise read as a different known backend, leaving names that read as UNKNOWN unchanged so existing installs keep their file names. Verify the fix and look for regressions this ordering change could cause anywhere CompiledBackend.of is called (discovery, FitCard, ChatViewModel, ModelStore), including already-installed models changing name or classification. Report only real problems with file:line and a fix, ranked P1-P3, or say none.
```

## vulkan-execuserve-r4.md

2026-10-04 08:18 UTC, from `~/execuserve`

```text
Review the uncommitted diff (git diff HEAD). It addresses your last four findings: (1) Models screen catalog stale after a Vulkan refusal: VulkanSupport.usableState StateFlow, collected in ModelsScreen; (2) install ids colliding for basenames containing vulkan: GPU ids always contain vulkan, and HfCatalog.variants no longer lists a CPU-folder file whose name says vulkan; (3) a pull in flight enqueuing a GPU download after a refusal: Downloader takes a refuses() check applied at enqueue and again when the install starts, ServeService.pull reports a refusal; (4) backend reported from names: Manifest and ModelEntry carry backend, Routes reports it, falling back to the name only for hand-copied files with no manifest. Verify each fix and look for new concrete defects (correctness, races, serialization compatibility with existing execuserve.json files, Compose recomposition, R8). Report only real problems with file:line and a fix, ranked P1-P3, or say none.
```

## vulkan-openweights-r5.md

2026-10-04 08:23 UTC, from `~/mobile-inference`

```text
Re-review the uncommitted diff (git diff HEAD). Your last finding: for publisher/Qwen3-Vulkan holding xnnpack/Qwen3-8da4w.pte, this diff renames the install from Qwen3-Vulkan-Qwen3-8da4w.pte to ...-xnnpack.pte, so an existing install looks undownloaded and a redownload lands at a second destination. The fix keeps legacy installs where they are rather than renaming (so name-keyed settings survive): ExecuTorchFileName.installedNamesFor returns the current name and the pre-suffix legacy name; discovery treats either as downloaded; ModelStore.compiledDestination returns the legacy file when it exists and the new one does not. A legacy copy keeps its old name and so its old label, which released versions already showed. Verify the fix and check every other place an installed .pte name is derived from a repo and path (ModelsViewModel download, tokenizer placement, deletion, last-model and per-file prefs) for a mismatch. Report only real problems with file:line and a fix, ranked P1-P3, or say none.
```

## vulkan-openweights-r6.md

2026-10-04 08:26 UTC, from `~/mobile-inference`

```text
Re-review the uncommitted diff (git diff HEAD), focusing on ModelStore.compiledDestination. Your last finding: a legacy partial download (<legacy name>.pte.part) was ignored, so a redownload started at the new suffixed name. Now a destination counts as started when the file or its DOWNLOAD_PARTIAL_SUFFIX file exists; the current name wins if started, else a started legacy name, else the current name. Verify, and confirm nothing else in the diff regressed. Report only real problems with file:line and a fix, ranked P1-P3, or say none.
```

## docs-ow-r1.md, docs-es-r1.md, docs-ep-r1.md

2026-10-04, after the documentation was pushed: each repository's documentation of this work
was checked against its raw logs, CSVs and code. The prompts asked for every number, ratio and
claim to be recomputed and checked, contradictions, broken links, leaked secrets and misleading
statements, ranked P1-P3. All findings were fixed in the commit that adds these files.

## docs-ow-r2.md, docs-es-r2.md, docs-ep-r2.md

The same day, on the fixes: each repository's uncommitted documentation diff, with the first
review's output in it, re-checked for each finding being fixed and every stated ratio
recomputed from the raw files. ExecuServe came back clean; openweights and execupack each had
one P3 wording item, both fixed in the same commit.
