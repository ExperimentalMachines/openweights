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

import io.github.alpharomercoma.openweights.core.common.model.CompiledBackend
import org.pytorch.executorch.ExecuTorchRuntime
import org.pytorch.executorch.Module
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmGenerationConfig
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File

/**
 * The real ExecuTorch runtime, through `org.pytorch:executorch-android`.
 *
 * A thin adapter and deliberately nothing more: everything worth testing lives in
 * [ExecuTorchEngine] above it, against [ExecuTorchBridge] rather than against this.
 */
class NativeExecuTorchBridge : ExecuTorchBridge {

    private var module: LlmModule? = null
    private var moduleTemperature: Float = 0f

    /**
     * The window a Qualcomm export was compiled for, handed to every generation as its
     * sequence length: Qualcomm's static-graph runner has no other way to know it. Null for
     * every other runtime, where the export owns the window.
     */
    private var qnnWindow: Int? = null

    // Written from the runtime's callback rather than returned by it, so they are held
    // here for the duration of one blocking generate rather than passed through.
    private var lastStats: String? = null
    private var lastError: String? = null

    override fun load(
        modelPath: String,
        tokenizerPath: String,
        temperature: Float,
        multimodal: Boolean,
    ): Boolean {
        close()
        // A Qualcomm export runs on Qualcomm's own static-graph runner (the JNI layer's model
        // type 4), which needs the DSP's library path and the model family in its environment
        // before it opens (QnnSupport.prepare), and the window at every generation.
        val qnn = !multimodal && CompiledBackend.of(File(modelPath).name) == CompiledBackend.QNN
        if (qnn) QnnSupport.prepare(File(modelPath).name)
        qnnWindow = if (qnn) probe(modelPath).contextLength else null
        // The runner is chosen here and cannot be changed after: the multimodal one is a
        // different class in the runtime, the only one with a way in for a picture.
        val modelType = when {
            multimodal -> LlmModule.MODEL_TYPE_MULTIMODAL
            qnn -> MODEL_TYPE_QNN
            else -> LlmModule.MODEL_TYPE_TEXT
        }
        // Anything can come back from here: a missing native library throws
        // UnsatisfiedLinkError rather than an exception, and a .pte built for another
        // runtime version fails inside the loader. Both become the same failure the rest
        // of the app knows how to show.
        return runCatching {
            LlmModule(modelType, modelPath, tokenizerPath, temperature).also {
                it.load()
                module = it
                moduleTemperature = temperature
            }
        }.fold(
            onSuccess = { true },
            onFailure = { cause ->
                // A phone whose HTP will not start stops being offered NPU files.
                if (qnn) QnnSupport.recordIfIncompatible(cause)
                throw LlamaException(
                    "ExecuTorch could not open this model: ${cause.message ?: cause::class.java.simpleName}",
                )
            },
        )
    }

    /**
     * Reads the window and the rest of [ExportFacts] off the file through the plain module API.
     *
     * A second, short-lived handle on the same file: mapped rather than read, and only the
     * metadata methods run, so it costs a few milliseconds and no copy of the weights. The LLM
     * wrapper reads the same constants itself and logs them, but offers no way to ask, so this
     * is the only route to the window before the first turn overflows. The window matters
     * because the runtime clamps to it whatever a caller asks for: reported as the user's
     * preference instead, a 2048 export showed as 4096 and the runtime refused the turn
     * (2026-09-06, LFM2.5 1.2B xnnpack).
     */
    override fun probe(modelPath: String): ExportFacts {
        // Before the runtime sees the file at all: a download that stopped early would
        // otherwise be reported by the loader, through the path that aborts.
        PteHeader.damage(File(modelPath))?.let { damage ->
            return ExportFacts(contextLength = null, hasVision = false, unopenable = damage)
        }
        val program = Module.load(modelPath, Module.LOAD_MODE_MMAP)
        try {
            val methods = program.getMethods().toSet()
            fun read(name: String): Int? = name.takeIf { it in methods }?.let {
                program.execute(it).firstOrNull()?.takeIf { v -> v.isInt }?.toInt()?.toInt()
            }?.takeIf { it > 0 }
            val window = WINDOW_METHODS.firstNotNullOfOrNull(::read)
            val prefill = read(PREFILL_METHOD)
            return ExportFacts(
                contextLength = window,
                hasVision = VISION_ENCODER_METHOD in methods,
                prefillLength = prefill,
                stateResetAtZero = read("get_state_reset_at_zero")?.let { it != 0 },
                unopenable = unopenable(
                    statesPrefill = PREFILL_METHOD in methods,
                    backends = runnerMethods(methods).associateWith { method ->
                        program.getMethodMetadata(method).backends.toList()
                    },
                    registered = registeredBackends,
                ),
            )
        } finally {
            program.destroy()
        }
    }

    /**
     * False for every tokenizer, because this runtime never asks for a BOS: the 1.4.0 JNI
     * layer's `num_bos_` is 0 unless the module is built with a count the Java constructor
     * this bridge uses does not take, and the C++ tokenizer adds BOS only when asked, even
     * for a tokenizer.json whose post-processor declares one (measured on the Mac with the
     * same tokenizers library: `encode(text, 0, 0)` never yields BOS, `encode(text, 1, 0)`
     * does, and a literal `<|startoftext|>` in the text is encoded to the BOS id). Every
     * ExecuTorch prompt this app ever fed was therefore BOS-less; LFM2.5-1.2B tolerated it,
     * LFM2.5-2.6B answered garbage. The engine writes the family's BOS into the text.
     */
    // Qualcomm's runner adds BOS itself (ExecuServe), and its tokenizer is saved under the
    // model's own name, so the name says which runner it belongs to.
    override fun tokenizerAddsBos(tokenizerPath: String): Boolean =
        CompiledBackend.of(File(tokenizerPath).name) == CompiledBackend.QNN

    override fun prefillImage(pixels: FloatArray, width: Int, height: Int, channels: Int) {
        val running = module ?: throw LlamaException("No model loaded")
        runCatching { running.prefillImages(pixels, width, height, channels) }.onFailure { cause ->
            throw cause.asOverflow() ?: LlamaException(
                "ExecuTorch could not read the picture: ${cause.message ?: cause::class.java.simpleName}",
            )
        }
    }

    /**
     * The one failure a full window produces, as the exception the turn loop answers.
     *
     * Raised the same way from a text prefill, a picture prefill and a generation, because
     * a window fills at whichever of the three lands last, and only one of them used to be
     * translated: an overflow during a picture read as "could not read the picture".
     */
    private fun Throwable.asOverflow(): ContextWindowExceededException? {
        val text = message.orEmpty()
        // The runner's own two diagnostics only (text_llm_runner.cpp: the conversation so far,
        // and a single prompt, past the window). The wrapper appends recent runtime log lines
        // to its messages, and those mention max_context_len in passing, so the name alone
        // turned unrelated failures, a GPU that cannot run a Vulkan export among them, into a
        // full window (codex QA).
        if ("Max seq length exceeded" !in text &&
            "Prompt exceeds KV cache capacity" !in text
        ) {
            return null
        }
        return ContextWindowExceededException(
            "The conversation no longer fits this model's exported context window. " +
                "Start a new chat, or use a model exported with a larger window.",
        )
    }

    override fun generate(
        prompt: String,
        maxNewTokens: Int,
        onToken: (String) -> Unit,
    ): ExecuTorchOutcome {
        val running = module ?: throw LlamaException("No model loaded")

        lastStats = null
        lastError = null

        // The config form rather than generate(prompt, seqLen, ...), because only this
        // one separates "how long may the whole thing be" from "how much may be added".
        //
        // Stated for the record rather than relied on: in 1.4.0 the wrapper's generate
        // reads seqLen, echo, temperature and the BOS/EOS counts out of this config and
        // never maxNewTokens (the AAR's bytecode), so the runtime resolves its allowance
        // from the window and this value changes nothing. The budget that holds is the
        // engine's StopDiscipline, which counts callbacks and stops the loop itself.
        val config = generationConfig(maxNewTokens, moduleTemperature, qnnWindow)

        val attempt = runCatching {
            running.generate(
                prompt,
                config,
                object : LlmCallback {
                    override fun onResult(result: String) = onToken(result)

                    override fun onStats(stats: String) {
                        this@NativeExecuTorchBridge.lastStats = stats
                    }

                    override fun onError(errorCode: Int, message: String) {
                        this@NativeExecuTorchBridge.lastError =
                            message.ifBlank { "ExecuTorch error $errorCode" }
                    }
                },
            )
        }
        attempt.exceptionOrNull()?.let { cause ->
            // The one overflow a conversation can reach in ordinary use, translated: a
            // .pte's window was fixed at export, and a history that has outgrown it is a
            // fact about the file, not a crash to show as one.
            throw cause.asOverflow() ?: cause
        }

        lastError?.let { throw LlamaException(it) }
        return outcomeFrom(lastStats)
    }

    override fun prefill(prompt: String) {
        val running = module ?: throw LlamaException("No model loaded")
        // In 1.4.0 the wrapper throws on a nonzero native code ("Prefill failed"), which
        // is caught and translated below. Earlier releases discarded the code, so the
        // engine's warm is also verified behaviourally — a chat after a warm must extend
        // it, and the runtime's own prompt count says whether it did.
        runCatching { running.prefillPrompt(prompt) }.onFailure { cause ->
            throw cause.asOverflow() ?: LlamaException(
                "ExecuTorch could not prefill: ${cause.message ?: cause::class.java.simpleName}",
            )
        }
    }

    private companion object {
        /** Qualcomm's static-graph runner, by the JNI layer's number for it. */
        const val MODEL_TYPE_QNN = 4

        /**
         * The delegates linked into this build: XnnpackBackend and VulkanBackend from the
         * Vulkan AAR. Asked of the runtime rather than written down, so a different AAR
         * answers for itself.
         */
        val registeredBackends: Set<String> by lazy {
            ExecuTorchRuntime.getRegisteredBackends().toSet()
        }

        /** What exporters call the window, most specific first. */
        val WINDOW_METHODS = listOf("get_max_context_len", "get_max_seq_len")

        /** The most tokens one prefill call may carry; the window itself on older exports. */
        const val PREFILL_METHOD = "get_max_seq_len"

        /** The method a multimodal export carries its image encoder under. */
        const val VISION_ENCODER_METHOD = "vision_encoder"
    }

    override fun resetContext() {
        module?.resetContext()
    }

    override fun stop() {
        module?.stop()
    }

    override fun close() {
        module?.close()
        module = null
    }

    /**
     * What the runtime measured, read out of the JSON it reports when a generation ends.
     *
     * Read by name and defensively rather than parsed into a type, because these field
     * names belong to ExecuTorch and change between releases. A field that is missing
     * leaves a zero, and [ExecuTorchEngine] falls back to wall-clock timing for the two
     * that matter — which is worse than the runtime's own numbers and better than a
     * number that quietly means something else.
     */
    private fun outcomeFrom(stats: String?): ExecuTorchOutcome {
        // 1.4.0 stats contain counts and timings, but no EOS id, stop reason or final
        // position. TextTokenGenerator returns the same count when EOS lands on the last
        // permitted step as when the loop exhausts its allowance. Do not infer truncation
        // from a missing streamed marker: a tokenizer may decode a genuine EOS as empty.
        // Reliable context-full classification needs a native termination-reason API.
        if (stats == null) return ExecuTorchOutcome(StopReason.END_OF_TURN)

        val promptTokens = stats.longField("prompt_tokens")
        val generatedTokens = stats.longField("generated_tokens")
        val inferenceStart = stats.longField("inference_start_ms")
        val promptEval = stats.longField("prompt_eval_end_ms")
        val inferenceEnd = stats.longField("inference_end_ms")

        val prefill = (promptEval - inferenceStart).coerceAtLeast(0)
        val decode = (inferenceEnd - promptEval).coerceAtLeast(0)
        val complete = promptEval > 0 && inferenceStart > 0

        return ExecuTorchOutcome(
            reason = StopReason.END_OF_TURN,
            promptTokens = promptTokens.toInt(),
            generatedTokens = generatedTokens.toInt(),
            prefillMs = if (complete) prefill else 0,
            decodeMs = if (complete && inferenceEnd > 0) decode else 0,
        )
    }

    /** The number stored against [name], or zero when this build of ExecuTorch omits it. */
    private fun String.longField(name: String): Long {
        val key = "\"$name\""
        val at = indexOf(key)
        if (at < 0) return 0
        val colon = indexOf(':', at + key.length)
        if (colon < 0) return 0
        return generateSequence(colon + 1) { it + 1 }
            .takeWhile { it < length }
            .dropWhile { this[it] == ' ' }
            .takeWhile { this[it].isDigit() }
            .map { this[it] }
            .joinToString("")
            .toLongOrNull() ?: 0
    }
}

/**
 * Why the runner would refuse a file, decided from what the plain module API can read.
 *
 * Two refusals the runner is known to make while opening, both of which abort the process
 * rather than raise (see [ExportFacts.unopenable]):
 * - no `get_max_seq_len`: the text and multimodal runners both require it before anything
 *   else ("Required metadata method get_max_seq_len not found in model"). A MediaTek NPU
 *   chunk, which is half of a disaggregated model and never a model alone, is one.
 * - a delegate this build did not link: the runtime reports the backend as not registered.
 *   NeuroPilot is the one a published file names (`NeuropilotBackend`); this AAR carries
 *   XNNPACK and Vulkan.
 *
 * @param backends each method's delegates, as [org.pytorch.executorch.MethodMetadata] lists them.
 */
internal fun unopenable(
    statesPrefill: Boolean,
    backends: Map<String, List<String>>,
    registered: Set<String>,
): String? {
    val missing = backends.values.flatten().toSortedSet() - registered
    return when {
        missing.isNotEmpty() ->
            "it was compiled for ${missing.joinToString(" and ")}, which this build of the app " +
                "does not include"
        !statesPrefill ->
            "it does not state get_max_seq_len, which the runtime needs to open a model; it may " +
                "be part of a model rather than a whole one, or come from an older exporter"
        else -> null
    }
}

/**
 * The methods a runner loads, whose delegates therefore have to be linked. A file with
 * `forward` is opened by the text runner, which loads that and nothing else; one without it is
 * the multimodal layout, whose runner loads its decoder, embedding and encoders. A method no
 * runner loads, an auxiliary one compiled for some other delegate, does not decide whether the
 * model opens (codex QA). A file with none of these names is judged on all of its methods.
 */
internal fun runnerMethods(methods: Set<String>): Set<String> = when {
    TEXT_METHOD in methods -> setOf(TEXT_METHOD)
    else -> (methods intersect MULTIMODAL_METHODS).ifEmpty { methods }
}

private const val TEXT_METHOD = "forward"
private val MULTIMODAL_METHODS =
    setOf("text_decoder", "token_embedding", "vision_encoder", "audio_encoder")

/** The AAR defaults each call to 0.8, which otherwise overrides the loaded sampler. */
internal fun generationConfig(
    maxNewTokens: Int,
    temperature: Float,
    seqLen: Int? = null,
): LlmGenerationConfig = LlmGenerationConfig.create()
    .temperature(temperature)
    .maxNewTokens(maxNewTokens)
    // Only for Qualcomm's runner, which cannot read its window off the file.
    .let { builder -> seqLen?.let(builder::seqLen) ?: builder }
    // No seqLen override: the export owns the total window. This AAR ignores
    // maxNewTokens, so the engine's callback discipline enforces the output budget.
    // Echo replays the prompt through onResult, and the whole conversation would
    // arrive looking like something the model had written.
    .echo(false)
    // Explicit, because this one is applied per call and would otherwise append an
    // EOS token to the end of every suffix fed in, turning what should be a
    // continuation into a sequence of terminated fragments. The module's BOS is
    // the opposite case: it is added once after construction or resetContext and
    // then suppressed, so incremental feeding cannot duplicate it, and the
    // per-call numBos in this config is ignored by the JNI layer entirely.
    .numEos(0)
    .build()
