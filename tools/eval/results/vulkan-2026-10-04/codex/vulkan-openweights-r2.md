Three remaining problems:

1. **P1: Vulkan failures can still be erased before the check.** [NativeExecuTorchBridge.kt:130](~/mobile-inference/core/engine/src/main/kotlin/io/github/alpharomercoma/openweights/core/engine/NativeExecuTorchBridge.kt:130) treats any message containing `max_context_len` as overflow. A Vulkan incompatibility with that metadata in the appended runtime log becomes a `ContextWindowExceededException` without its original cause. The new `gpuChecked` wrapper therefore misses it during generation, prefill, warm-up, and image prefill. **Fix:** use the precise overflow diagnostics, as ExecuServe now does, and preserve the original cause.

2. **P2: Repository names still override installed-file classification.** [ExecuTorchFileName.kt:79](~/mobile-inference/core/common/src/commonMain/kotlin/io/github/alpharomercoma/openweights/core/common/model/ExecuTorchFileName.kt:79) appends `vulkan`, but retains `XNNPACK` from the repository name. For `Qwen3-ExecuTorch-XNNPACK` containing `vulkan/model.pte`, the installed name contains both; `CompiledBackend.of` prioritizes XNNPACK and labels it CPU. **Fix:** preserve the authoritative path backend in installed metadata or remove conflicting repository backend markers when constructing the name.

3. **P2: ExecuServe install IDs still collide across backend folders.** [HfCatalog.kt:53](~/execuserve/shared/catalog/src/commonMain/kotlin/org/experimentalmachines/execuserve/catalog/HfCatalog.kt:53) derives the ID solely from the basename. `xnnpack/Qwen3-8da4w-2k.pte` and `vulkan/Qwen3-8da4w-2k.pte` share an install directory, download state, and installed indicator. The new test avoids this by giving the GPU basename a `vulkan` marker. **Fix:** include the backend in GPU install IDs while preserving existing CPU IDs.

The ordinary-failure refusal bug and runtime-call coverage are fixed at the wrapper level. ExecuServe’s precise overflow matching and catalog re-filtering address its two reported issues.

Read-only review; no Gradle or device tests run.

