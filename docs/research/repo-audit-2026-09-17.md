# The repository read cold, for someone about to work on compiled models

2026-09-17. A read-only audit made at the start of the session recorded in
`executorch-state-and-recipes.md`: what the documents say against what the code and tools
do, with the ExecuTorch path in front. Items fixed the same day are marked.

## Consistent

- The runtime is pinned once (`gradle/libs.versions.toml`, `executorch = "1.4.0"`) and
  `executorch.md`, `executorch-own-exports.md` and `executorch-vision.md` agree with it.
- One backend, XNNPACK (`ExecuTorchSupport.kt`); README and ARCHITECTURE agree.
- No document still recommends the 8da4w LFM2.5 export; the shortlist in
  `HuggingFaceClient.kt` is four GGUF rows with the two compiled files under Experimental.

## Stale or contradictory

- `executorch-tool-calling-qad.md` closed on "measured on the laptop only" under a table of
  160 phone rows. *Fixed: the closing section is rewritten and points at the new note.*
- README's runtime table listed ExecuTorch tool use as "all families except SmolLM2 and
  Gemma 3", and its table of published exports carried no warning. *Fixed.*
- ROADMAP (lines 12 and 33) still leads with the compiled LFM2.5's prefill speed and "the
  app now ships its own 32k exports", with no pointer to the 2026-09-10 reversal. Open.
- The `phone-deploy` skill named the app's test package for "an instrumented test"; the
  engine evals live in a second APK with its own runner. *Fixed.*
- CONTRIBUTING sends a reader to the 4,200-line CONTEXT.md for "the exact commands". Open.
- `tools/executorch/export_qwen3.sh`, the only export script the notes reference, pushes to
  the release package's directory and runs from a source checkout no other export used. Open;
  `tools/executorch/quantlab/export.py` replaces it for the families it covers.
- `executorch-own-exports.md` omits the scripts that built the shipped files
  (`export1_2b_32k.sh`, `export_windows.sh`) and the `convert_weights` step. Open.
- The sibling exporter repository (`~/executorch-model-exporter`: XNNPACK, Vulkan, QNN,
  MediaTek workflows) is mentioned nowhere here. Open.
- `tools/eval/bench/report.py` keys a cell on (family, engine, device); an unknown prefix
  such as `prod-` or `s25u-` falls into the Dimensity column and the last file wins, so
  re-running it today would silently change `benchmark-matrix.md`. There is no recipe
  dimension. Open, and the first thing to fix before a second recipe is published.
- Decode speed is computed two ways: `(generated - 1) / decode_ms` in `GenerationStats` and
  `phone_stats.py`, `generated / decode_ms` in `window_report.py`. Open.

## Reproducibility of the export pipeline, as found

- Recipes lived as environment-gated patches (`OW_INT8_REGEX`, `OW_Q40_GGUF`) in a venv's
  `quantize.py`; `quantize_skip.patch` was no longer applied, so `export_head8.sh`,
  `export_ffnq.sh` and `export_mixq.sh` would now write plain 8da4w without an error.
- Eighteen probe scripts hard-coded session scratch directories, one of which is gone.
  *`prompts.py` is now repository-relative; the rest are superseded by quantlab.*
- No single command went from a checkpoint to a `.pte` with a named recipe. *Fixed:
  `quantlab/export.py`, which edits nothing in the venv.*
- Inputs that exist only under `~/ow-models/etexport`: the Hugging Face folders, the
  converted checkpoints, the 2.6B params file, the model cards and `hf_publish_org.py`.

## What a newcomer still has to discover alone

1. No lockfile for the export environment (executorch 1.4.0, torch 2.14, torchao 0.18,
   transformers 5.16.1 is what works).
2. The end-to-end path (export, the file-name rules that pick a template, the two test
   APKs, `prompt_dump.json`, live web search in the decision suite, grading) is written
   down in no one place; `quantlab/README.md` covers the export half.
3. Results have no manifest: file hash, recipe, window, versions.
4. The acceptance bar for recommending a compiled export is prose in two notes, not a gate
   a script runs. `quantlab/sweep.py` now prints the laptop half of it.
5. What adding a delegate costs (an AAR flavour, `ExecuTorchSupport.BACKENDS`; QNN's Maven
   artifact was 1.2.0 when `executorch.md` was written) is not written down.

## Security and mobile harness follow-up, 2026-09-21

This was an implementation audit, not a claim of zero false positives or false negatives.
Independent reviews checked the network, local-data, runtime and UI boundaries, then reviewed
the fixes. Regression tests exercise reachable behavior rather than treating a scanner
finding as proof.

### Confirmed and repaired

- Conversation branches preserve carried tool provenance and stored step IDs. Folding and
  reopening no longer erase private/untrusted flags while derived content survives.
- Watches persist summary provenance in schema 20, treat legacy unknown flags conservatively,
  and put sanitized previous summaries in USER content rather than the system head.
- Session file ownership uses exact paths, provider document identity/metadata, grant
  lifetime and session revision. Write, delete and fetch-save retain the authorized scope
  through approval, staging and HTTP waits. User-file approval does not confer ownership.
- Conditional `fetch_url` saves count as durable writes, including aliases; read-only and
  find forms remain read-only. Untrusted durable writes still require approval in YOLO.
- Canvas headers are bounded by aggregate bytes and elapsed time before authentication.
  SVG receives CSP like HTML. Actual Chromium experiments confirmed that CSP blocks the
  SVG fetch but not top-level navigation, so external-browser launch now asks every time.
- Optional publisher configs have a 1 MiB decompressed-body limit and four concurrent reads.
  Oversized optional metadata does not hide an otherwise usable model.
- Single-quoted tool arguments preserve quoted braces and escaped apostrophes. An unfinished
  quoted value cannot dispatch a partial call.
- Read-aloud selects an installed voice reported as offline, checks selection and utterance
  failures, and surfaces errors instead of silently selecting an online voice.
- Compiled context occupancy survives truncation until reset. Image-adjacent text is bounded,
  accounting is cumulative where the native API is cumulative, surrogate pairs and image
  wrappers remain whole, and the UI distinguishes verified windows from legacy estimates.
- Compiled-only inert controls are hidden. Shared settings, network recipients and mode
  exceptions are stated consistently in the UI, README, store copy and privacy policy.

### Comparison and deliberate non-changes

[Pi](https://github.com/badlogic/pi-mono/tree/main/packages/coding-agent) keeps a small core
tool set. [Oh My Pi](https://github.com/can1357/oh-my-pi) has a richer desktop harness and
[small-model guidance](https://github.com/can1357/oh-my-pi/blob/main/.omp/skills/system-prompts/small-models.md).
The [Arena Harness Tax experiment](https://arena.ai/blog/coding-agents-harness-tax) compared
21 model/harness pairs on two 30-task subsets, with three runs per task and a 100-turn cap.
It supports questioning overhead, not transplanting desktop tools or predicting 1B phone
results. The four-tool comparison is not evidence that OpenWeights' other tools are useless.

The existing eighteen-tool registry, opt-in switches, stable prompt heads, prefix reuse,
hybrid checkpoints, canceled-warm preservation and KV snapshots stay. No cache, thermal,
thread, context or recommendation default was tuned without a phone measurement. The
charcoal/lime interface and typography stay; changes make its controls and consent truthful.
Corrected exports remain subject to the existing on-device quality and state-reset checks.

### Verification and remaining boundaries

Before fixes, six canvas/parser regressions failed, and the config regression consumed the
entire 2 MiB response. They pass after the fixes. Focused tools, engine, Room migration,
watch/branch, speech and Compose suites passed. Native Robolectric renders at 360 by 640 dp
verified GGUF controls, fixed compiled context, an editable labelled legacy estimate, and
browser consent. The throwaway rendering fixture was removed after inspection.

The POCO was not discoverable through adb or mDNS, and no emulator was installed. These are
host checks, not device performance measurements or validation of unpublished corrected
exports. ExecuTorch 1.4.0's Java callback/stats API lacks a reliable terminal cause for every
EOS/context-full case; native API work is needed before that distinction can be claimed.
See its [stats](https://github.com/pytorch/executorch/blob/v1.4.0/extension/llm/runner/stats.h)
and [generator](https://github.com/pytorch/executorch/blob/v1.4.0/extension/llm/runner/text_token_generator.h).

SAF has no atomic compare-and-mutate operation and cannot detect an external provider
replacement preserving document ID, size and modification metadata. Offline voice selection
trusts Android speech-service metadata. External browsers remain outside app network guards.
The pinned QuickJS-NG is v0.16.1, beyond the
[published 0.16.0 OOM fixes](https://github.com/quickjs-ng/quickjs/security/advisories)
inspected here. This is not a complete native dependency audit. llama.cpp itself
[treats untrusted model files as a sandboxing concern](https://github.com/ggml-org/llama.cpp/blob/master/SECURITY.md).

Final repository verification passed:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew ktlintCheck detekt test jvmTest :app:assembleDebug
```

The complete debug reports contain 709 app cases (708 passed; the replay case skipped
because its optional input is absent), 450 tools cases, 79 engine cases and 141 data cases,
with zero failures. Other modules and JVM tests also passed in the same invocation.
The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Store copy is 3,185 characters,
within Play's 4,000-character limit; all five locales include the new capability and consent
strings. No commit, push, release upload or device installation was performed.

## On-device acceptance follow-up, 2026-09-21

The POCO X8 Pro Max was subsequently found with macOS DNS-SD and connected over wireless
adb. The debug app was installed in place, preserving data, and exercised through its real
Compose UI on Android 16. Screenshots, UI hierarchy dumps and the audit conversations were
captured. The final installed APK's SHA-256 matches the local build:
`1cb36b7bcc20cc5a5576986e4a60e556770457d7355e77ca18c7f9e5fff69b38`.
Its package is `io.github.alpharomercoma.openweights.debug`, version `2.0.0-debug`, code 619.

### Live UI and authorization checks

- The compiled model shows its fixed 32768-token context and CPU processor, with unsupported
  sampling controls absent and the capability explanation visible. GGUF retains its controls.
- A branch persisted separately and reopened. Read-aloud entered and left its active state,
  and the network disclosure was visible. Audio output and network traffic were not measured.
- The compiled model created HTML with `write_file`; `show_website` rendered it. Browser
  cancellation stayed inside the app, the next attempt requested consent again, and approval
  opened the localhost page in Chrome.
- The actual canvas server returned CSP and `Referrer-Policy: no-referrer` for HTML and SVG.
  A 33000-byte extra header received HTTP 431. Incomplete headers closed after 10.02 seconds.
  A probe while the app was behind Chrome timed out; bringing the app foreground restored
  service. The cause of that background observation was not established.
- A cross-conversation overwrite waited for approval and left the file unchanged until Run.
  Removing the folder grant while another approval was pending made Run fail with
  `No folder shared`; the exact file bytes remained unchanged. Declining also preserved them.
- Stopping an active answer retained the partial text, and the next turn generated normally.

Two presentation defects were reproduced and corrected. Failed and skipped file writes no
longer use the successful past-tense `Saved` headline. A tool preamble is no longer rendered
both in the work block and the answer while approval waits. Installed-build screenshots
confirmed the failed/skipped states and one preamble rather than two. These fixes do not
make the model's own subsequent claims trustworthy: it still claimed success after refusal.

### GGUF startup-order performance regression

The same prompt, `Calculate 200 minus 171. Reply with the number only. Do not use tools.`,
produced `200 - 171 equals 29.` with 619 input tokens, 8 output tokens and 534 cached tokens.
The answer is correct arithmetic but does not obey the number-only formatting instruction.

| Startup and transition | Reply time | Decode rate |
| --- | --- | --- |
| ExecuTorch first, then GGUF, before correction | 19.6, 19.7, 19.8 s | about 1 token/s |
| GGUF first, before correction | 1.3 to 1.4 s | 26 to 27 tokens/s |
| GGUF first, unload/reload or round trip through ExecuTorch | 1.2 to 1.3 s | 30 to 31 tokens/s |
| ExecuTorch first, then GGUF, corrected repeated starts | 1.3 to 1.4 s | 27 to 29 tokens/s |
| Final APK, GGUF first | 1.3 s | 29 tokens/s |
| Final APK, ExecuTorch first, then GGUF | 1.4 s | 28 tokens/s |

The slow process had eight `openweights-inf` workers, all restricted to CPUs 4-6, mask `70`.
The fast control had the same eight workers and ARM backend, but mask `ff`, CPUs 0-7.
Thermal status was zero during the investigation. Ordinary unload/reload and actual
ExecuTorch generation in a GGUF-first process did not reproduce the slowdown. This is
startup-order dependent, not evidence that every backend switch poisons native libraries.

Copying the process leader's mask at model-load time was insufficient: that experiment
still took 19.5 seconds with mask `70`. The Android native backend now captures the leader's
mask once during backend initialization, then restores it on the inference thread before
loading the model and creating its persistent pool. The corrected process had all eight
workers on mask `ff`. Kernel cpuset restrictions still apply. No core IDs are hard-coded,
and thread counts, performance defaults and model artifacts are unchanged. The external
component responsible for the transient thread placement was not identified.

These are awake, unlocked device reproduction timings, not a thermally controlled model
benchmark. ExecuTorch also completed a post-correction generation at 29 tokens/s.

### Output quality is not signed off

The compiled model was the published GPTQ 32k artifact from
[LFM2.5-1.2B-Instruct-ExecuTorch](https://huggingface.co/experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch),
revision `de4b372c466aaec71f13dabeeb61fe9fb4981b12`, file
`xnnpack/LFM2.5-1.2B-Instruct-8da4w-gptq-32k.pte`. Its 827144832-byte file on the phone has
SHA-256 `677a0a59ffa469e1b8f8cb1cc765458fe3e816d48c471568441ab82b4e1560f3`.
No unpublished export was tested, modified or uploaded.

| Manual case | GGUF QAD Q4_0 | Published compiled GPTQ |
| --- | --- | --- |
| 3 notebooks at 45 pesos, 2 pens at 18, pay 200 | Total 171, change 29 | Total 171, change -29 |
| Follow-up JSON | Valid JSON, correct values | Valid JSON, retained wrong -29 |
| Two-person delivery extraction | Correct count and Friday delivery | Correct count, assigned both deliveries to Friday |
| Unknown router serial | Correctly abstained | Correctly abstained |
| Recall after explicit compaction | Kept LIME-482, invented $12.00 change | Invented codeword "refund" and lost amounts |
| Fresh simple subtraction | 29 | 229 in two fresh chats |

The original GGUF prompt accidentally retained the pre-existing `who` draft as a prefix;
that initial case is not a clean identical-prompt comparison. Fresh subtraction probes were
clean. Stored summaries explain the recall failures: the GGUF summary kept the codeword
but none of the amounts; the compiled summary kept neither. This is factual loss during
compaction, not a missing conversation in storage. The compiled model also invented a
public `https://yourdomain.com/audit.html` URL for a local file.

This small manual sample establishes concrete failures, not a statistical backend ranking
or proof that quantization caused them. Do not promote the compiled artifact on this basis.
Legacy export-window and vision execution paths were not exercised with physical models.

### Final verification and retained evidence

After the UI fixes, app lint, detekt and all 710 app test cases completed with zero failures:
709 passed and the optional replay case skipped. The final native build also passed:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:ktlintCheck :app:detekt :app:testDebugUnitTest :app:assembleDebug
```

The build retains a C++17 warning about an existing captured structured binding in CPU
backend selection. The full-repository checks above preceded these device follow-ups.
Device evidence, exact audit transcripts and selected screenshots are retained locally at
`/tmp/openweights-device-audit-20260921/`. No release, commit, push or model publication was
performed.

The phone was left on its original GGUF model with the unsent `who` draft restored, no
shared folder, and Save a file and Show website off. The published compiled model, 26 audit
conversations and the approved sample HTML remain for inspection. The uploaded installer,
temporary SVG probe and device-side UI capture files were removed, and the two DNS-SD
discovery processes were stopped.

### Captured-failure approval review

Decision: **hold approval of the captured compiled artifact**. This decision applies to
SHA-256 `677a0a59ffa469e1b8f8cb1cc765458fe3e816d48c471568441ab82b4e1560f3`
in the tested app configuration, not to every ExecuTorch export. Review used the retained
transcripts, manifest and failed/skipped-write screenshots; no new phone runs were needed.

Evidence references below are message IDs in `audit-transcripts.json`, not independent
benchmark trials. A repeated answer or copied branch is not an additional independent case.

| Finding | Captured evidence | Approval consequence |
| --- | --- | --- |
| Basic arithmetic failure | Message 32 gives change -29; correction 42 gives 79; fresh-chat messages 44 and 64 both give 229 for 200 minus 171 | Blocker even without compaction |
| Fact extraction failure | Message 36 adds Mira to Friday despite the prompt specifying Thursday | Blocker for factual grounding |
| False action completion | Messages 54 and 62 claim a write succeeded; screenshots show failed and user-declined tool states respectively | Blocker for trustworthy tool use, not evidence of an authorization bypass |
| Invented publication | Message 50 invents `https://yourdomain.com/audit.html` for a local preview | Blocker for truthful reporting of side effects |
| Destructive factual compaction | Conversation 7's summary omits LIME-482, 171, 200 and 29; message 40 invents "refund" as the codeword | Blocker for compacted conversation use; also affects the GGUF baseline |
| Exact-output noncompliance | Messages 60, 76 and 88 return `READY.` rather than `READY` | Runtime recovery passed, but those exact-output checks did not |

Message 34 is syntactically valid JSON, but its change value is wrong. Because the prompt
explicitly asked to reuse the previous answer, it demonstrates error propagation rather
than a separate arithmetic failure. Message 38's appropriate abstention and successful HTML
rendering are genuine passes, but neither cancels the correctness and truthfulness blockers.
The summary review found only LIME-482 in the GGUF summary and none of those four facts in
the compiled summary. Recall cannot be judged as if those omitted facts remained available.

The captures establish failures of the tested model-and-app combination. They do not locate
the arithmetic defect in weights, quantization, export, tokenization or runtime state. In
particular, screenshots prove the contradiction between a tool result and the final answer,
not the exact serialized tool-result tokens the model received. GGUF QAD and compiled GPTQ
are not a controlled precision-only comparison. The initial `who` prefix and unmatched
runtime decoding capabilities further limit cross-model attribution. The CPU-affinity fix
addresses latency; it does not establish a fix for these semantic failures.

Before reconsidering approval:

1. Pin the candidate artifact, tokenizer, export/runtime versions and app build. Preserve
   rendered prompt bytes or token IDs, actual decoding configuration, reset state and
   serialized tool results. Compare clean fresh sessions and reused sessions separately.
2. Require the captured correctness cases to pass: total 171, change 29, subtraction 29,
   Leo alone delivering on Friday, valid JSON with correct values, and exact requested
   output where explicitly required. Add unseen operands and delivery permutations to
   distinguish general correctness from fitting the captured prompts.
3. Require summaries to retain the codeword, amounts, currency and payment relationship,
   and verify correct recall across compaction. Score summary preservation separately from
   answer generation. Include both backends because the GGUF control also lost facts.
4. Require successful, failed, declined and revoked-grant tool outcomes to be reported
   accurately, with file bytes matching the authoritative result and no invented public URL.
   A correct status chip alone is insufficient when the answer contradicts it.
5. Establish a matched source-model, raw-export and app comparison before attributing a
   failure to quantization or changing export settings. Passing the small captured set is
   necessary, not sufficient: evaluate a predeclared representative holdout before promotion.

This review changes neither model files nor app behavior, and performs no publication or
approval action. Existing runtime and authorization passes remain valid within their tested
scope; general compiled-model quality approval remains withheld.

## Worktree reconciliation, 2026-09-21

The pending app, runtime, tool, data, evaluator and documentation changes were reviewed
together after the model-side workspace separation. The follow-up repaired root-only JSON
tool dispatch, conservative provenance for restored unknown tools, provider renames that
invalidate document IDs, cancellation after the final picture/prefill, and missing mandatory
vision sequence metadata. Regression cases cover those boundaries. The C++17 structured
binding capture warning in the earlier device build was removed without adding allocations.

The full host check passed after separating bare-JSON protocol tests and decomposing their
field scanner, without suppressing the complexity checks:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew ktlintCheck detekt test jvmTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :core:engine:compileDebugAndroidTestKotlin
```

The offline evaluator's 11 tests passed, Python compilation and four shell syntax checks
passed, and the real grader regenerated `summary.json` from the retained decision captures.
Explicitly VOID window-failure captures remain as evidence but are excluded from grading.
Offline smoke runs exercised upload URL/path quoting, credential-free process arguments,
HTTP failure propagation, bridge forwarding, argument redaction and disconnect cleanup.
Their disposable harnesses were removed. No billable cloud action or new model run occurred.
These checks do not establish live cloud API compatibility or physical vision-model behavior.

Eight additional raw device records, four Qualcomm-derived parser regrades and the loose
screen recording were preserved outside Git with SHA-256 verification. Four raw records
already had identical archived copies; distinct files and manifests are under
`~/ow-models/benchmarks/additional-app-tree-20260921/`. Two malformed raw logs remain
byte-for-byte intact, and the headerless record is not assigned invented device metadata.
App integration utilities, held-out inputs and POCO captures remain in the repository.

README and the current context now reflect the measured device findings and the approval
hold, rather than equating publication or historical recipe results with current quality.
No models, defaults, mirrors or exporter sources were changed by this reconciliation.
The work is intended for local commits only; hosted CI requires a later push and is not
claimed by these local checks.
