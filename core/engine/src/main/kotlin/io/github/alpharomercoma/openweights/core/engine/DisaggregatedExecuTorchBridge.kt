/*
 * Copyright 2026 The OpenWeights Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.alpharomercoma.openweights.core.engine

import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Executes models using Prefill/Decode (PD) Disaggregation:
 * MediaTek NeuroPilot NPU for Prefill + ARM Cortex-X925 CPU for Decode.
 *
 * Falls back to [NativeExecuTorchBridge] when NPU chunks or native libraries are unavailable.
 */
class DisaggregatedExecuTorchBridge(
    private val fallback: ExecuTorchBridge = NativeExecuTorchBridge(),
) : ExecuTorchBridge {

    private val nativeBridge = DisaggregatedBridge()
    private var handle: Long = 0L
    private var usingFallback = false

    /** Whether the loaded session is prefilling on the NPU rather than on the CPU runtime. */
    val prefillingOnNpu: Boolean
        get() = handle != 0L && !usingFallback

    override fun tokenizerAddsBos(tokenizerPath: String): Boolean = false

    override fun probe(modelPath: String): ExportFacts = fallback.probe(modelPath)

    override fun exportedContextLength(modelPath: String): Int? =
        fallback.exportedContextLength(modelPath)

    override fun load(
        modelPath: String,
        tokenizerPath: String,
        temperature: Float,
        multimodal: Boolean,
    ): Boolean {
        close()

        if (!DisaggregatedBridge.isAvailable || multimodal) {
            Log.i(TAG, "DisaggregatedBridge unavailable or multimodal requested; using fallback")
            usingFallback = true
            return fallback.load(modelPath, tokenizerPath, temperature, multimodal)
        }

        val modelFile = File(modelPath)
        val npuPair = resolveNpuComponents(modelFile)

        if (npuPair == null) {
            // The reason was already logged by the resolver: no chunks here, or chunks
            // without the marker that turns the path on.
            Log.i(TAG, "Running ${modelFile.name} on the CPU runtime")
            usingFallback = true
            return fallback.load(modelPath, tokenizerPath, temperature, multimodal)
        }

        val (chunks, embedding, runnerOptions) = npuPair
        val promptModelPaths = chunks.joinToString(",") { it.absolutePath }
        val embeddingPath = embedding.absolutePath

        Log.i(
            TAG,
            "Loading Disaggregated runtime: ${chunks.size} NPU chunks " +
                "(${chunks.first().name}..), embedding=${embedding.name}, " +
                "CPU model=${modelFile.name}",
        )

        val loadedHandle = nativeBridge.nativeLoad(
            runnerOptionsJson = runnerOptions,
            promptModelPaths = promptModelPaths,
            tokenEmbeddingPath = embeddingPath,
            cpuModelPath = modelFile.absolutePath,
            tokenizerPath = tokenizerPath,
            temperature = temperature,
        )

        if (loadedHandle == 0L) {
            Log.w(TAG, "nativeLoad failed; falling back to CPU runtime")
            usingFallback = true
            return fallback.load(modelPath, tokenizerPath, temperature, multimodal)
        }

        handle = loadedHandle
        usingFallback = false
        Log.i(TAG, "Disaggregated NPU+CPU runtime successfully initialized (handle=$handle)")
        return true
    }

    override fun generate(
        prompt: String,
        maxNewTokens: Int,
        onToken: (String) -> Unit,
    ): ExecuTorchOutcome {
        if (usingFallback) {
            return fallback.generate(prompt, maxNewTokens, onToken)
        }

        val h = handle
        if (h == 0L) throw LlamaException("No disaggregated model loaded")

        val res = nativeBridge.nativeGenerate(h, prompt, maxNewTokens) { token ->
            onToken(token)
            true
        } ?: throw LlamaException("Disaggregated generation failed")

        val reason = when (res.getOrElse(RESULT_REASON) { 0L }.toInt()) {
            REASON_CANCELLED -> StopReason.CANCELLED
            REASON_MAX_TOKENS -> StopReason.MAX_TOKENS
            else -> StopReason.END_OF_TURN
        }
        val promptTokens = res.getOrElse(RESULT_PROMPT_TOKENS) { 0L }.toInt()
        val genTokens = res.getOrElse(RESULT_GENERATED_TOKENS) { 0L }.toInt()
        val prefillMs = res.getOrElse(RESULT_PREFILL_MS) { 0L }
        val decodeMs = res.getOrElse(RESULT_DECODE_MS) { 0L }

        return ExecuTorchOutcome(
            reason = reason,
            promptTokens = promptTokens,
            generatedTokens = genTokens,
            prefillMs = prefillMs,
            decodeMs = decodeMs,
        )
    }

    override fun prefill(prompt: String) {
        if (usingFallback) {
            fallback.prefill(prompt)
            return
        }
        val h = handle
        if (h == 0L) throw LlamaException("No disaggregated model loaded")
        nativeBridge.nativePrefill(h, prompt)
    }

    override fun resetContext() {
        if (usingFallback) {
            fallback.resetContext()
            return
        }
        val h = handle
        if (h != 0L) nativeBridge.nativeResetContext(h)
    }

    override fun stop() {
        if (usingFallback) {
            fallback.stop()
            return
        }
        val h = handle
        if (h != 0L) nativeBridge.nativeStop(h)
    }

    override fun close() {
        val h = handle
        handle = 0L
        if (h != 0L) {
            nativeBridge.nativeClose(h)
        }
        if (usingFallback) {
            fallback.close()
            usingFallback = false
        }
    }

    private data class NpuComponents(
        val chunks: List<File>,
        val embedding: File,
        /** The exporter's `runner` block, verbatim, or empty when the manifest is absent. */
        val runnerOptions: String,
    )

    /**
     * The compiled chunks, the embedding table and the options they were compiled for, or
     * null when this model has no NPU half here.
     *
     * The directory must also hold [ENABLE_MARKER]. The path answers correctly and is off
     * on cost, not on correctness: the chunks hold 512 tokens against the roughly 2,050 a
     * real prompt reaches once the tool prefix is in, and this runner's decode is 13 to 18
     * tok/s against the 27 the app's own CPU path gets from the same file, which is where
     * the wall clock goes. See docs/research/npu-pd-disaggregation.md. Touching the marker
     * turns the path on for a measurement run without a rebuild.
     */
    private fun resolveNpuComponents(modelFile: File): NpuComponents? {
        val parent = modelFile.parentFile ?: return null
        val soc = Build.SOC_MODEL.lowercase()
        val candidateDirs = buildList {
            add(File(parent, "mtk/$soc"))
            add(File(parent, "mtk"))
            add(parent)
            // A staging directory for measurement runs. On this ROM a file pushed into
            // Android/data by adb is invisible to the app: the app's view of its own
            // external directory is a bind mount that does not pick up shell's writes, so
            // a pushed chunk set can only be read from there. Nothing downloaded ever
            // lands in it, and the marker still has to be present, so it costs a stat on
            // a path that exists on no phone but this bench.
            File(STAGING_ROOT).listFiles().orEmpty()
                .filter { it.isDirectory }
                .sortedBy { it.name }
                .forEach { add(File(it, "mtk/$soc")) }
        }

        val dir = candidateDirs.firstOrNull { holdsNpuHalf(it) } ?: return null
        if (!File(dir, ENABLE_MARKER).exists()) {
            Log.i(
                TAG,
                "NPU chunks in ${dir.absolutePath} but $ENABLE_MARKER is not; staying on CPU",
            )
            return null
        }
        val (chunks, embedding) = npuFilesIn(dir)
        return NpuComponents(chunks, embedding!!, readRunnerOptions(dir))
    }

    /** Whether [dir] holds a full NPU half: at least one compiled chunk and the embedding table. */
    private fun holdsNpuHalf(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val (chunks, embedding) = npuFilesIn(dir)
        if (chunks.isNotEmpty() && embedding != null) return true
        Log.i(TAG, "${dir.absolutePath} has ${chunks.size} chunks, embedding=${embedding != null}")
        return false
    }

    private fun npuFilesIn(dir: File): Pair<List<File>, File?> {
        val files = dir.listFiles().orEmpty()
        val chunks = files
            .filter { it.name.contains("chunk", ignoreCase = true) && it.name.endsWith(".pte") }
            .sortedBy { it.name }
        val embedding = files.firstOrNull {
            it.name.contains("embedding", ignoreCase = true) && it.name.endsWith(".bin")
        }
        return chunks to embedding
    }

    /**
     * The `runner` block out of the manifest the exporter leaves beside the chunks.
     *
     * Empty when there is no manifest, which makes the native side fall back to MediaTek's
     * defaults. Those defaults are wrong for this family (16 heads and int16 against the
     * 32 heads and fp32 the graphs were built for), so a missing manifest is logged.
     */
    private fun readRunnerOptions(dir: File): String {
        val manifest = File(dir, "config.json").takeIf { it.isFile }
            ?: dir.listFiles().orEmpty().firstOrNull {
                it.name.startsWith("export-report") &&
                    it.name.endsWith(".json")
            }
        if (manifest == null) {
            Log.w(
                TAG,
                "No export manifest in ${dir.absolutePath}; the NPU half will use MediaTek defaults",
            )
            return ""
        }
        return runCatching {
            val root = JSONObject(manifest.readText())
            val runner = root.optJSONObject("runner")
                ?: root.optJSONArray("variants")?.optJSONObject(0)?.optJSONObject("runner")
            if (runner == null) {
                Log.w(
                    TAG,
                    "No runner block in ${manifest.name}; the NPU half will use MediaTek defaults",
                )
                ""
            } else {
                runner.toString()
            }
        }.getOrElse { cause ->
            Log.w(TAG, "Could not read ${manifest.name}: ${cause.message}")
            ""
        }
    }

    private companion object {
        const val TAG = "OpenWeightsPD"
        const val ENABLE_MARKER = "enable-pd"
        const val STAGING_ROOT = "/data/local/tmp/mtk_models"

        // The layout of the array nativeGenerate returns. Positional because it crosses
        // JNI, so the two sides are kept in step by name here rather than by counting.
        const val RESULT_REASON = 0
        const val RESULT_PROMPT_TOKENS = 1
        const val RESULT_GENERATED_TOKENS = 2
        const val RESULT_PREFILL_MS = 3
        const val RESULT_DECODE_MS = 4

        const val REASON_MAX_TOKENS = 1
        const val REASON_CANCELLED = 2
    }
}
