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
import java.util.Locale

/**
 * Executes models using Prefill/Decode (PD) Disaggregation: the MediaTek NPU reads the prompt,
 * then the CPU writes the reply from the same `.pte` the CPU path loads.
 *
 * Falls back to [NativeExecuTorchBridge] when NPU chunks or native libraries are unavailable.
 *
 * [stagingRoot] is for the instrumented test alone: chunks pushed by adb under it, beside an
 * `enable-pd` marker, are used when the model has no installed NPU half. The app passes none.
 * On this ROM a file adb pushes into Android/data is invisible to the app, so a bench can only
 * stage there; searched in the shipped app it once stood in, unnoticed, for an install the
 * start-up sweep had deleted (2026-10-11).
 *
 * [trial] is the probe process's bridge, whose open is what [NpuInstall.markTried] records:
 * it opens a half the probe has not tried yet, and leaves the opening mark alone, since a
 * crash there is the probe's to judge. The app's bridge opens only a half that was tried.
 */
class DisaggregatedExecuTorchBridge(
    private val fallback: ExecuTorchBridge = NativeExecuTorchBridge(),
    private val stagingRoot: File? = null,
    private val trial: Boolean = false,
) : ExecuTorchBridge {

    private val nativeBridge = DisaggregatedBridge()
    private var handle: Long = 0L
    private var usingFallback = false

    /** The temperature the session was opened at, which the runner samples with per reply. */
    private var temperature = 0f

    /**
     * Tokens and milliseconds the runner had already prefilled when the next generate call
     * starts. The runner reports totals since its last reset, and the engine reads the
     * generate call's own tail: counted as totals, every turn charged the conversation to the
     * context twice (84% shown against the CPU path's 46% on the same turn, 2026-10-11).
     */
    private var fedTokens = 0
    private var fedMs = 0L

    /** The model whose NPU half is marked as opening, until its first prompt is read. */
    private var openingModel: File? = null

    private fun openedNpu() {
        openingModel?.let(NpuInstall::opened)
        openingModel = null
    }

    /** Whether the loaded session is prefilling on the NPU rather than on the CPU runtime. */
    override val prefillingOnNpu: Boolean
        get() = handle != 0L && !usingFallback

    override var npuSkipped: String? = null
        private set

    /** The file the last session opened and whether it read prompts on the NPU, for [reload]. */
    private var lastPath: String? = null
    private var lastOnNpu = false

    override fun tokenizerAddsBos(tokenizerPath: String): Boolean = false

    override fun probe(modelPath: String): ExportFacts = fallback.probe(modelPath)

    override fun load(
        modelPath: String,
        tokenizerPath: String,
        temperature: Float,
        multimodal: Boolean,
    ): Boolean {
        close()
        npuSkipped = null
        return open(modelPath, tokenizerPath, temperature, multimodal, Reopen.NO).also {
            remember(modelPath)
        }
    }

    override fun reload(
        modelPath: String,
        tokenizerPath: String,
        temperature: Float,
        multimodal: Boolean,
    ): Boolean {
        val same = modelPath == lastPath
        close()
        val reopen = when {
            !same -> Reopen.NO
            lastOnNpu -> Reopen.NPU
            else -> Reopen.CPU
        }
        if (!same) npuSkipped = null
        return open(modelPath, tokenizerPath, temperature, multimodal, reopen).also {
            remember(modelPath)
        }
    }

    private fun remember(modelPath: String) {
        lastPath = modelPath
        lastOnNpu = prefillingOnNpu
    }

    /** How [open] chooses the processor: afresh, or as the session it reopens had it. */
    private enum class Reopen { NO, NPU, CPU }

    private fun open(
        modelPath: String,
        tokenizerPath: String,
        temperature: Float,
        multimodal: Boolean,
        reopen: Reopen,
    ): Boolean {
        if (!DisaggregatedBridge.isAvailable || multimodal) {
            Log.i(TAG, "DisaggregatedBridge unavailable or multimodal requested; using fallback")
            usingFallback = true
            return fallback.load(modelPath, tokenizerPath, temperature, multimodal)
        }

        val modelFile = File(modelPath)
        val npuPair = if (reopen ==
            Reopen.CPU
        ) {
            null
        } else {
            resolveNpuComponents(modelFile, reopen == Reopen.NPU)
        }

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

        // Cleared once a prompt has been read; found still set by the next launch, it refuses
        // this half (NpuInstall.opening).
        if (stagingRoot == null && !trial) {
            NpuInstall.opening(modelFile)
            openingModel = modelFile
        }
        val loadedHandle = nativeBridge.nativeLoad(
            runnerOptionsJson = runnerOptions,
            promptModelPaths = promptModelPaths,
            tokenEmbeddingPath = embeddingPath,
            cpuModelPath = modelFile.absolutePath,
            tokenizerPath = tokenizerPath,
            temperature = temperature,
        )

        if (loadedHandle == 0L) {
            openedNpu()
            Log.w(TAG, "nativeLoad failed; falling back to CPU runtime")
            npuSkipped =
                "The NPU half of this model could not be opened, so prompts are read on the CPU."
            usingFallback = true
            return fallback.load(modelPath, tokenizerPath, temperature, multimodal)
        }

        handle = loadedHandle
        fedTokens = 0
        fedMs = 0L
        usingFallback = false
        this.temperature = temperature
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

        val res = nativeBridge.nativeGenerate(h, prompt, maxNewTokens, temperature) { token ->
            onToken(token)
            true
        } ?: throw failure(h, "failed while generating")

        val code = res.getOrElse(RESULT_REASON) { 0L }.toInt()
        // The reply reached the end of the NPU window: the turn loop answers that as a full
        // context, the same as the CPU runtime's own overflow.
        if (code == REASON_WINDOW_FULL) throw overflow()
        val reason = when (code) {
            REASON_CANCELLED -> StopReason.CANCELLED
            REASON_MAX_TOKENS -> StopReason.MAX_TOKENS
            else -> StopReason.END_OF_TURN
        }
        openedNpu()
        val totalTokens = res.getOrElse(RESULT_PROMPT_TOKENS) { 0L }.toInt()
        val totalMs = res.getOrElse(RESULT_PREFILL_MS) { 0L }
        val promptTokens = (totalTokens - fedTokens).coerceAtLeast(0)
        val prefillMs = (totalMs - fedMs).coerceAtLeast(0L)
        fedTokens = totalTokens
        fedMs = totalMs
        val genTokens = res.getOrElse(RESULT_GENERATED_TOKENS) { 0L }.toInt()
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
        // A refused prefill used to pass unnoticed and leave the next generate reading a cache
        // that did not hold the prompt.
        val before = System.currentTimeMillis()
        val total = nativeBridge.nativePrefill(h, prompt)
        if (total <= 0) throw failure(h, "could not prefill")
        fedTokens = total
        fedMs += System.currentTimeMillis() - before
        openedNpu()
    }

    /**
     * Feeds the reply the CPU half wrote to the NPU half, whose cache holds only the prompt:
     * the next turn is prefilled on the NPU and handed to the CPU, so without it the CPU
     * would decode from a conversation that lost its last answer. One NPU call per 128 tokens.
     */
    override fun commitReply(answer: String): Boolean {
        if (!prefillingOnNpu || answer.isEmpty()) return fallbackCommit(answer)
        return runCatching { prefill(answer) }.isSuccess
    }

    private fun fallbackCommit(answer: String): Boolean =
        if (usingFallback) fallback.commitReply(answer) else true

    /** The runner's own reason for refusing: a prompt past its NPU window, or a runtime failure. */
    private fun failure(h: Long, what: String): LlamaException =
        if (nativeBridge.nativeLastError(h) == DisaggregatedBridge.ERROR_OVERFLOW) {
            overflow()
        } else {
            LlamaException("The MediaTek runtime $what.")
        }

    private fun overflow() = ContextWindowExceededException(
        "The conversation no longer fits this model's NPU window. Start a new chat, or use a " +
            "build with a larger window.",
    )

    override fun resetContext() {
        if (usingFallback) {
            fallback.resetContext()
            return
        }
        val h = handle
        if (h != 0L) nativeBridge.nativeResetContext(h)
        fedTokens = 0
        fedMs = 0L
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
        // Closed by the app rather than by a crash: whatever it was doing, it survived.
        openedNpu()
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
     * null when this model has no usable NPU half here. An NPU half Discover installed beside
     * the model is used whenever it is complete, fits and is not refused, because downloading
     * it was the user asking for it.
     */
    private fun resolveNpuComponents(modelFile: File, memoryJustFreed: Boolean): NpuComponents? {
        val installed = NpuInstall.read(modelFile)
        val root = stagingRoot
        return when {
            installed != null -> installedComponents(modelFile, installed, memoryJustFreed)
            root != null -> stagedComponents(root)
            else -> null
        }
    }

    /**
     * [installed], unless it is refused, or the last launch died opening it (which refuses
     * it), or the probe process has not read a prompt with it, or the phone has too little
     * memory free to hold it beside the model. [memoryJustFreed] skips the memory check for a
     * reopen of a session that held this half a moment ago.
     */
    private fun installedComponents(
        modelFile: File,
        installed: NpuInstall.Complete,
        memoryJustFreed: Boolean,
    ): NpuComponents? {
        if (NpuInstall.diedOpening(modelFile)) {
            NpuInstall.refuse(modelFile, "the app closed while its NPU half was opening")
        }
        val refusal = NpuInstall.refusal(modelFile)
        val short = if (memoryJustFreed) null else NpuMemory.shortfall(modelFile, installed)
        npuSkipped = when {
            refusal != null ->
                "The NPU half of this model is turned off because $refusal. " +
                    "Prompts are read on the CPU until it is downloaded again."
            short != null -> memoryNote(short.first + NpuMemory.RESERVE_BYTES, short.second)
            !trial && !NpuInstall.tried(modelFile) ->
                "The NPU half of this model has not been " +
                    "tried yet, so prompts are read on the CPU. Load the model again to try it."
            else -> null
        }
        npuSkipped?.let {
            Log.w(TAG, "NPU half of ${modelFile.name} not used: $it")
            return null
        }
        return NpuComponents(installed.chunks, installed.embedding, installed.runnerJson)
    }

    private fun memoryNote(need: Long, free: Long): String {
        val needed = gigabytes(need)
        val has = gigabytes(free)
        return "The NPU half of this model needs about $needed of free memory and the phone has " +
            "$has, so prompts are read on the CPU. Close apps you are not using and load the " +
            "model again to use the NPU."
    }

    /** Chunks staged under [root] for the instrumented test, used only beside [ENABLE_MARKER]. */
    private fun stagedComponents(root: File): NpuComponents? {
        val soc = Build.SOC_MODEL.lowercase()
        val candidateDirs = root.listFiles().orEmpty()
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .map { File(it, "mtk/$soc") }

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

        // The layout of the array nativeGenerate returns. Positional because it crosses
        // JNI, so the two sides are kept in step by name here rather than by counting.
        const val RESULT_REASON = 0
        const val RESULT_PROMPT_TOKENS = 1
        const val RESULT_GENERATED_TOKENS = 2
        const val RESULT_PREFILL_MS = 3
        const val RESULT_DECODE_MS = 4

        const val REASON_MAX_TOKENS = 1
        const val REASON_CANCELLED = 2
        const val REASON_WINDOW_FULL = 3
        private const val BYTES_PER_GB = (1L shl 30).toDouble()

        /** In the binary gigabytes the model cards use, so the two figures can be compared. */
        fun gigabytes(bytes: Long): String = "%.1f GB".format(Locale.ROOT, bytes / BYTES_PER_GB)
    }
}
