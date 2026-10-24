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

import android.util.Log
import io.github.alpharomercoma.openweights.core.common.device.CpuTopology
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.OutputModality
import io.github.alpharomercoma.openweights.core.common.model.ParsedToolCalls
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import io.github.alpharomercoma.openweights.core.common.model.ToolCall
import io.github.alpharomercoma.openweights.core.common.model.ToolCallParser
import io.github.alpharomercoma.openweights.core.common.model.ToolDefinition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.random.Random

/**
 * llama.cpp-backed [InferenceEngine].
 *
 * All native calls run on one dedicated thread. That is not just tidiness: llama.cpp
 * contexts are not thread-safe, and keeping generation on a single non-UI thread means the
 * JNIEnv handed to `nativeGenerate` stays valid for its token callbacks.
 */
class LlamaCppEngine internal constructor(
    private val bridge: LlamaBridge,
    private val defaultThreadCount: Int,
    private val defaultBatchThreadCount: Int,
    /**
     * Whether a pass sends live progress. Only a device test turns it off, to measure what
     * the readout costs against the same model without it.
     */
    private val liveProgress: Boolean = true,
) : InferenceEngine {
    constructor() : this(LlamaBridge(), recommendedThreadCount(), recommendedBatchThreadCount())

    /** The device test's: the same engine with or without its live progress. */
    internal constructor(liveProgress: Boolean) :
        this(LlamaBridge(), recommendedThreadCount(), recommendedBatchThreadCount(), liveProgress)

    private val engineExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "openweights-inference").apply { isDaemon = true }
        }

    private val engineThread: CoroutineDispatcher = engineExecutor.asCoroutineDispatcher()

    /** Guards load/unload against a concurrent generation. */
    private val lifecycleLock = Mutex()

    /**
     * Held while the native handle is used or freed.
     *
     * [cancel] runs on whatever thread the caller is on, so without this it could read a
     * handle that [freeCurrentHandle] deletes a moment later and hand a dangling pointer
     * to the native layer. Cancelling only flips an atomic flag natively, so holding a
     * lock across it costs nothing.
     */
    private val handleLock = Any()

    /**
     * Held shared by each weight prefetch step and exclusively by [freeCurrentHandle], so the
     * session a step reads cannot be freed under it. Separate from [handleLock] so a step that
     * waits on zram does not hold up a Stop or the live readout, which need only a moment.
     */
    private val lifetime = ReentrantReadWriteLock()

    /**
     * Bytes every prefetch step has walked, counted as each step returns. A look cut short at
     * Send returns no [WeightPrefetch], so this is the only record of the steps it took.
     */
    private val prefetchWalked = AtomicLong(0)

    /** 0 when no model is loaded. Guarded by [handleLock] for cross-thread reads. */
    private val handle = AtomicLong(0)

    @Volatile
    private var currentModel: LoadedModelInfo? = null

    /**
     * Whether the next pass is the first since a load that memory-mapped the weights. That
     * pass reads them in from the file for the first time, which the fault count cannot tell
     * from paging, so it is measured and never flagged. Weights read into memory at load,
     * the default, were already read by then.
     */
    private val coldPass = AtomicBoolean(false)

    override val loadedModel: LoadedModelInfo? get() = currentModel

    override suspend fun load(modelFile: File, params: ModelLoadParams, projectorFile: File?) {
        require(modelFile.isFile) { "model file does not exist: ${modelFile.path}" }
        require(projectorFile == null || projectorFile.isFile) {
            "projector file does not exist: ${projectorFile?.path}"
        }
        lifecycleLock.withLock {
            withContext(engineThread) {
                freeCurrentHandle()
                val newHandle = bridge.nativeLoadModel(
                    modelPath = modelFile.absolutePath,
                    mmprojPath = projectorFile?.absolutePath,
                    contextLength = params.contextLength,
                    threadCount = params.threadCount ?: defaultThreadCount,
                    batchThreadCount = params.batchThreadCount ?: defaultBatchThreadCount,
                    gpuLayers = params.gpuLayers,
                    opOffload = params.opOffload,
                    useMmap = params.useMmap,
                    kvCacheQuantized = params.kvCacheQuantized,
                    speculation = params.speculation,
                    imageTokens = params.imageTokens,
                    batchTokens = params.batchTokens,
                    microBatchTokens = params.microBatchTokens,
                )
                val info = try {
                    readModelInfo(newHandle, modelFile.absolutePath)
                } catch (failure: Throwable) {
                    // The weights are already mapped at this point. Publishing the handle
                    // before interrogating it left a failed load resident and made a retry
                    // compound the memory pressure that commonly caused the failure.
                    bridge.nativeFreeModel(newHandle)
                    throw failure
                }
                synchronized(handleLock) {
                    handle.set(newHandle)
                    currentModel = info
                }
                coldPass.set(params.useMmap)
            }
        }
    }

    override suspend fun unload() {
        lifecycleLock.withLock {
            withContext(engineThread) { freeCurrentHandle() }
        }
    }

    override fun chat(
        messages: List<ChatMessage>,
        params: SamplerParams,
        tools: List<ToolDefinition>,
    ): Flow<GenerationEvent> = callbackFlow {
        val activeHandle = handle.get()
        check(activeHandle != 0L) { "no model is loaded" }
        require(messages.isNotEmpty()) { "cannot generate a reply to an empty conversation" }

        var parsed = ParsedReply()
        val prompt = renderPrompt(activeHandle, messages)

        // Registered before the blocking JNI call. During prefill there are no token
        // callbacks, so waiting for trySend to fail cannot observe a collector that left.
        // Closing the producer channel can happen from the cancelling thread and flips the
        // native atomic immediately.
        channel.invokeOnClose { cause ->
            if (cause != null) this@LlamaCppEngine.cancel()
        }

        val pass = PassMeter(
            this,
            cold = coldPass.getAndSet(false),
            read = { progressOf(activeHandle) },
            ticks = liveProgress,
        )
        val stats = try {
            generate(activeHandle, messages, prompt, params, tools) { parsed = it }
        } finally {
            // Before anything that could suspend: see PassMeter.
            pass.close()
        }

        if (stats != null) {
            currentModel = currentModel?.copy(contextUsed = stats[CONTEXT_USED].toInt())
            send(completionEvent(stats, parsed, pass.close()))
        }
        close()
    }
        // Tokens arrive faster than a Compose collector redraws. Without an unbounded
        // buffer, trySend fails on backpressure and the sink reads that as "the
        // collector left", silently truncating the reply. With it, a failed trySend
        // means the channel is closed, which is the only case that should stop us.
        .buffer(Channel.UNLIMITED)
        .flowOn(engineThread)

    /**
     * The progress of the pass running on [expected], or null once that handle is no longer
     * the loaded one. Under [handleLock], which [freeCurrentHandle] also takes, so the read
     * can never reach a session that has been freed.
     */
    private fun progressOf(expected: Long): GenerationEvent.Progress? =
        onHandle(expected) { progressFrom(bridge.nativeProgress(it)) }

    /** The blocking native call, with the token and reply sinks wired to this producer's channel. */
    private fun ProducerScope<GenerationEvent>.generate(
        activeHandle: Long,
        messages: List<ChatMessage>,
        prompt: RenderedPrompt,
        params: SamplerParams,
        tools: List<ToolDefinition>,
        onParsed: (ParsedReply) -> Unit,
    ): LongArray? = bridge.nativeGenerate(
        handle = activeHandle,
        roles = messages.map { it.role.wireName }.toTypedArray(),
        contents = prompt.contents,
        toolCallIds = messages.map { it.toolCallId }.toTypedArray(),
        mediaPaths = prompt.mediaPaths,
        mediaCounts = prompt.mediaCounts,
        temperature = params.temperature,
        topK = params.topK,
        topP = params.topP,
        minP = params.minP,
        repeatPenalty = params.repeatPenalty,
        repeatLastN = params.repeatLastN,
        seed = params.seed ?: Random.nextInt(),
        maxTokens = params.maxTokens,
        reasoningBudget = params.reasoningBudget,
        toolNames = tools.map { it.name }.toTypedArray(),
        toolDescriptions = tools.map { it.description }.toTypedArray(),
        toolSchemas = tools.map { it.parametersJson }.toTypedArray(),
        enableThinking = params.thinking,
        reasoningEffort = params.reasoningEffort.wireName,
        sink = { text, logprob ->
            // trySend fails once the collector is gone, which stops generation.
            trySend(GenerationEvent.Token(text, logprob.takeIf { !it.isNaN() })).isSuccess
        },
        replySink = { content, reasoning, calls ->
            // llama.cpp knows several tool formats; where it recognises none, the
            // Kotlin parser covers the ones it does not, such as LFM2's.
            val nativeCalls = calls.toToolCalls()
            val fallback = if (nativeCalls.isEmpty()) {
                ToolCallParser.parse(content)
            } else {
                ParsedToolCalls(content, nativeCalls)
            }
            onParsed(ParsedReply(fallback.text, reasoning, fallback.calls))
        },
    )

    override fun cancel() = synchronized(handleLock) {
        val activeHandle = handle.get()
        if (activeHandle != 0L) {
            bridge.nativeCancel(activeHandle)
        }
    }

    override suspend fun warm(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        params: SamplerParams,
        snapshot: Boolean,
        store: String?,
    ): WarmResult? = withContext(engineThread) {
        val activeHandle = handle.get()
        if (activeHandle == 0L) return@withContext null
        // A warm that fails is a warm that did not happen, which the contract above says
        // is reported as null. The native side throws for everything but a cancellation -
        // a prefix longer than the context window, a template that will not render a
        // system message on its own, a decode refused under memory pressure - and every
        // one of those is a condition the next turn handles for itself. Warming fires
        // after every load from a launch nothing catches, so letting the throw through
        // turned "the head does not fit a 1,024-token window" into a crash on each load.
        val stats = try {
            bridge.nativeWarm(
                handle = activeHandle,
                // Marshalled exactly as chat marshals them, because the engine reuses this
                // work by comparing bytes: a role or a flag spelled differently here is a
                // prefix that never matches and a warm that warmed nothing.
                roles = messages.map { it.role.wireName }.toTypedArray(),
                contents = messages.map { it.text }.toTypedArray(),
                toolNames = tools.map { it.name }.toTypedArray(),
                toolDescriptions = tools.map { it.description }.toTypedArray(),
                toolSchemas = tools.map { it.parametersJson }.toTypedArray(),
                enableThinking = params.thinking,
                reasoningEffort = params.reasoningEffort.wireName,
                snapshot = snapshot,
                storePath = store,
            )
        } catch (failure: LlamaException) {
            Log.w("OpenWeights", "warm skipped: ${failure.message}")
            null
        } ?: return@withContext null
        currentModel = currentModel?.copy(
            contextUsed = (stats[0] + stats[1]).toInt(),
        )
        WarmResult(
            warmedTokens = stats[0].toInt(),
            reusedTokens = stats[1].toInt(),
            prefillMs = stats[2],
            snapshotBytes = stats[3],
        )
    }

    override suspend fun prefetchWeights(): WeightPrefetch? = withContext(Dispatchers.IO) {
        val expected = handle.get()
        if (expected == 0L) return@withContext null
        val startedAt = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
        val before = whileLoaded(expected, bridge::nativeWeightsNotResident)
        if (before == null || before < 0) return@withContext null
        if (before < PREFETCH_FLOOR_BYTES) {
            return@withContext WeightPrefetch(before, before, elapsedMs(), skipped = true)
        }
        var offset = 0L
        var failed = false
        while (true) {
            // Between steps, so a turn that starts mid-prefetch takes the CPU back within one.
            ensureActive()
            val from = offset
            val step = whileLoaded(expected) {
                bridge.nativePrefetchWeights(it, from, PREFETCH_STEP_BYTES)
            }
            if (step == null || step < 0L) failed = true
            if (step == null || step <= 0L) break
            offset += step
            prefetchWalked.addAndGet(step)
        }
        val after = whileLoaded(expected, bridge::nativeWeightsNotResident)?.takeIf { it >= 0 }
        WeightPrefetch(before, after, elapsedMs(), skipped = false, failed = failed).also {
            Log.i(
                "OpenWeights",
                "prefetch: ${it.outBefore / BYTES_PER_MB} MB of weights out, " +
                    "${it.outAfter?.div(BYTES_PER_MB)} MB after ${it.elapsedMs} ms" +
                    if (failed) ", stopped early" else "",
            )
        }
    }

    /** [prefetchWalked] so far; the device benchmark reads the change over a window. */
    internal fun prefetchWalkedBytes(): Long = prefetchWalked.get()

    /** Bytes of the weights out of memory now, or null; the device benchmark's reading. */
    internal fun weightsOut(): Long? {
        val expected = handle.get()
        if (expected == 0L) return null
        return whileLoaded(expected, bridge::nativeWeightsNotResident)?.takeIf { it >= 0 }
    }

    /**
     * [block] on the session [expected] names, or null once it is no longer the loaded one.
     * Under [handleLock], which [freeCurrentHandle] also takes, so the read can never reach a
     * session that has been freed. For calls that return at once.
     */
    private inline fun <T> onHandle(expected: Long, block: (Long) -> T): T? =
        synchronized(handleLock) {
            if (handle.get() != expected) null else block(expected)
        }

    /** [onHandle] for the slow weight reads, under [lifetime] rather than [handleLock]. */
    private inline fun <T> whileLoaded(expected: Long, block: (Long) -> T): T? = lifetime.read {
        if (handle.get() != expected) null else block(expected)
    }

    override suspend fun judge(
        messages: List<ChatMessage>,
        instruction: String,
        options: List<String>,
        tools: List<ToolDefinition>,
        params: SamplerParams,
    ): Judgement? = coroutineScope {
        // The native call blocks the engine thread, and a coroutine cancelled while it runs
        // would otherwise wait out the whole prefill before noticing: Stop pressed during a
        // judgement has to reach the decode loop the way it does during a chat.
        val finished = AtomicBoolean(false)
        val stopper = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) this@LlamaCppEngine.cancel()
            }
        }
        try {
            withContext(engineThread) {
                judgeOnEngine(messages, instruction, options, tools, params)
            }
        } finally {
            finished.set(true)
            stopper.cancel()
        }
    }

    private fun judgeOnEngine(
        messages: List<ChatMessage>,
        instruction: String,
        options: List<String>,
        tools: List<ToolDefinition>,
        params: SamplerParams,
    ): Judgement? {
        val activeHandle = handle.get()
        // Attachments are embeddings the question cannot re-read as text; see Session::judge.
        val judgeable = activeHandle != 0L && messages.isNotEmpty() && options.size >= 2
        if (!judgeable || messages.any { it.files.isNotEmpty() }) return null
        // A judgement that fails is one that was not made, and every caller has the path it
        // took before there were judgements. The throw is logged rather than raised for the
        // same reason warm's is: nothing about a declined question should end a turn.
        val values = try {
            bridge.nativeJudge(
                handle = activeHandle,
                roles = messages.map { it.role.wireName }.toTypedArray(),
                contents = messages.map { it.text }.toTypedArray(),
                toolCallIds = messages.map { it.toolCallId }.toTypedArray(),
                toolNames = tools.map { it.name }.toTypedArray(),
                toolDescriptions = tools.map { it.description }.toTypedArray(),
                toolSchemas = tools.map { it.parametersJson }.toTypedArray(),
                enableThinking = params.thinking,
                reasoningEffort = params.reasoningEffort.wireName,
                instruction = instruction,
                options = options.toTypedArray(),
            )
        } catch (failure: LlamaException) {
            Log.w("OpenWeights", "judge skipped: ${failure.message}")
            null
        } ?: return null
        val n = options.size
        if (values.size != n + JUDGE_TRAILER) return null
        currentModel = currentModel?.copy(
            contextUsed = (values[n + JUDGE_REUSED] + values[n + JUDGE_PROMPT]).toInt(),
        )
        return Judgement(
            options = options,
            probabilities = values.take(n),
            optionMass = values[n],
            promptTokens = values[n + JUDGE_PROMPT].toInt(),
            reusedTokens = values[n + JUDGE_REUSED].toInt(),
            prefillMs = values[n + JUDGE_PREFILL].toLong(),
        )
    }

    override suspend fun resetContext() {
        withContext(engineThread) {
            val activeHandle = handle.get()
            if (activeHandle != 0L) {
                bridge.nativeResetContext(activeHandle)
                currentModel = currentModel?.copy(contextUsed = 0)
            }
        }
    }

    /**
     * Frees the model and shuts down the engine thread.
     *
     * The application holds one engine for its whole lifetime, so this exists for tests
     * and for anything that creates a short-lived engine: without it each instance leaks
     * a thread.
     */
    override fun close() {
        // Generation runs on engineExecutor and does not hold handleLock while it is in
        // the native call, so freeing here directly could delete a session mid-decode.
        // Cancelling first and then freeing *on that thread* guarantees the native call
        // has returned before the memory goes away.
        cancel()
        runCatching {
            engineExecutor.submit { freeCurrentHandle() }.get(SHUTDOWN_TIMEOUT_SECONDS, SECONDS)
        }
        engineExecutor.shutdown()
    }

    override suspend fun setThreads(generateThreads: Int, batchThreads: Int) {
        require(generateThreads > 0 && batchThreads > 0) {
            "thread counts must be positive, got $generateThreads and $batchThreads"
        }
        withContext(engineThread) {
            val activeHandle = handle.get()
            if (activeHandle != 0L) {
                bridge.nativeSetThreads(activeHandle, generateThreads, batchThreads)
            }
        }
    }

    override fun systemInfo(): String = bridge.nativeSystemInfo()

    override fun computeDevices(): List<ComputeDevice> =
        ComputeDevice.parse(bridge.nativeComputeDevices())

    private fun freeCurrentHandle() = lifetime.write {
        synchronized(handleLock) {
            val previous = handle.getAndSet(0)
            if (previous != 0L) {
                bridge.nativeFreeModel(previous)
            }
            currentModel = null
        }
    }

    /** The terminal event, read out of the flat array the native layer returns. */
    private fun completionEvent(stats: LongArray, parsed: ParsedReply, memory: TurnMemory) =
        GenerationEvent.Completed(
            reason = STOP_REASONS.getOrElse(stats[0].toInt()) { StopReason.ERROR },
            content = parsed.content,
            reasoning = parsed.reasoning,
            toolCalls = parsed.toolCalls,
            stats = GenerationStats(
                promptTokens = stats[1].toInt(),
                generatedTokens = stats[2].toInt(),
                prefillMs = stats[3],
                decodeMs = stats[4],
                timeToFirstTokenMs = stats[5],
                contextUsed = stats[CONTEXT_USED].toInt(),
                contextSize = stats[7].toInt(),
                thinkingPrefilled = stats.getOrElse(THINKING_PREFILLED) { 0L } == TRUE_FLAG,
                cachedTokens = stats.getOrElse(CACHED_TOKENS) { 0L }.toInt(),
                memory = memory,
            ),
        )

    /** The conversation as arrays the native layer can take, media markers included. */
    private class RenderedPrompt(
        val contents: Array<String>,
        val mediaPaths: Array<String>,
        val mediaCounts: IntArray,
    )

    /**
     * Prepares the conversation for the native layer.
     *
     * The projector needs its own marker in the text wherever an attachment belongs, and
     * only it knows what that marker is. It varies per model. Without a projector loaded,
     * attachments are dropped rather than described: a text-only model handed a marker
     * would read it as literal text and answer about a picture it cannot see.
     */
    private fun renderPrompt(activeHandle: Long, messages: List<ChatMessage>): RenderedPrompt {
        val marker = if (currentModel?.mediaSupport?.any == true) {
            bridge.nativeMediaMarker(activeHandle)
        } else {
            null
        }
        val files = messages.map { if (marker == null) emptyList() else it.files }

        return RenderedPrompt(
            contents = messages.mapIndexed { index, message ->
                if (marker == null || files[index].isEmpty()) {
                    message.text
                } else {
                    message.withMediaMarkers(marker)
                }
            }.toTypedArray(),
            mediaPaths = files.flatten().map { it.path }.toTypedArray(),
            mediaCounts = files.map { it.size }.toIntArray(),
        )
    }

    private fun readModelInfo(activeHandle: Long, modelPath: String): LoadedModelInfo {
        val info = bridge.nativeModelInfo(activeHandle)
        // Read once: llama.cpp accounted for these at load and nothing moves afterwards.
        val offload = runCatching { bridge.nativeOffloadSummary(activeHandle) }.getOrDefault("")
        // `[vision, audio, speech]`. Read once, for the same reason: the projector is fixed
        // at load, and two of these three used to cost a JNI crossing each.
        val projector = bridge.nativeMediaSupport(activeHandle)
        return LoadedModelInfo(
            description = bridge.nativeModelDescription(activeHandle),
            parameterCount = info[0],
            sizeBytes = info[1],
            contextSize = info[2].toInt(),
            trainingContextSize = info[3].toInt(),
            layerCount = info[4].toInt(),
            contextUsed = info[5].toInt(),
            offloadedTo = offload.substringBefore('|'),
            offloadBuffers = offload.substringAfter('|', "")
                .split('|')
                .filter { it.isNotBlank() }
                .mapNotNull { entry ->
                    val name = entry.substringBefore(':')
                    val mib = entry.substringAfter(':', "").toIntOrNull() ?: return@mapNotNull null
                    name to mib
                },
            mediaSupport = MediaSupport(vision = projector[0], audio = projector[1]),
            outputModality = if (projector[2]) OutputModality.SPEECH else OutputModality.TEXT,
            supportsThinking = bridge.nativeSupportsThinking(activeHandle),
            supportsTools = bridge.nativeSupportsTools(activeHandle),
            supportsToolResults = bridge.nativeSupportsToolResults(activeHandle),
            supportsReasoningEffort = bridge.nativeSupportsReasoningEffort(activeHandle),
            modelPath = modelPath,
        ).also {
            // What the chat template can actually do, asked of the template at load. Logged
            // because every "why did it not search" question starts here and the answer is
            // otherwise invisible from outside the process.
            Log.i(
                "OpenWeights",
                "loaded: tools=${it.supportsTools} results=${it.supportsToolResults} " +
                    "thinking=${it.supportsThinking} " +
                    "effort=${it.supportsReasoningEffort} " +
                    "vision=${it.mediaSupport.vision} audio=${it.mediaSupport.audio} " +
                    "emits=${it.outputModality.name.lowercase()} " +
                    "ctx=${it.contextSize}",
            )
        }
    }

    /** The reply as the native parser produced it, filled in before the flow completes. */
    private data class ParsedReply(
        val content: String = "",
        val reasoning: String = "",
        val toolCalls: List<ToolCall> = emptyList(),
    )

    private companion object {
        /** Tool calls cross JNI flattened as [id, name, argumentsJson]. */
        const val TOOL_CALL_FIELDS = 3

        /**
         * Less of the weights out than this and the prefetch does nothing: 64 MB is about
         * 16,000 pages, a third of a second at the 20 µs each the paging flag uses
         * (MemoryMeter), which nobody waits on. It trips once per load on any model whose
         * buffers hold pages never written (93 MB of Qwen3 1.7B's, read back with one major
         * fault in 140 to 580 ms); that is the gate counting them, not paging, and not a
         * reason to raise it.
         */
        const val PREFETCH_FLOOR_BYTES = 64L * 1024 * 1024

        /**
         * One step of the prefetch. A turn that starts mid-prefetch waits for the step in
         * flight, and an unload waits for it before freeing the weights, so it is kept to
         * what zram decompresses in tens of milliseconds; the device benchmark logs each
         * prefetch's time.
         */
        const val PREFETCH_STEP_BYTES = 16L * 1024 * 1024
        const val BYTES_PER_MB = 1024L * 1024
        const val NANOS_PER_MILLI = 1_000_000L

        /** Index of the context-used field in the stats array the native layer returns. */
        const val CONTEXT_USED = 6

        /** Index and native truth value for whether the thinking prefix was reused. */
        const val THINKING_PREFILLED = 8
        const val TRUE_FLAG = 1L

        /** Index of the cached-tokens field: how much of this turn's prompt was reused. */
        const val CACHED_TOKENS = 9

        /**
         * A judgement crosses JNI as one probability per option followed by these fields,
         * at these offsets past the last option: [optionMass, promptTokens, reusedTokens,
         * prefillMs].
         */
        const val JUDGE_TRAILER = 4
        const val JUDGE_PROMPT = 1
        const val JUDGE_REUSED = 2
        const val JUDGE_PREFILL = 3

        /** Long enough for a cancelled decode step to return; short enough not to hang. */
        const val SHUTDOWN_TIMEOUT_SECONDS = 10L

        val STOP_REASONS = StopReason.entries

        fun Array<String>.toToolCalls(): List<ToolCall> = toList().chunked(TOOL_CALL_FIELDS)
            .filter { it.size == TOOL_CALL_FIELDS }
            .map { ToolCall(id = it[0], name = it[1], argumentsJson = it[2]) }

        const val MIN_THREADS = 2
        const val MAX_GEN_THREADS = 6
        const val MAX_BATCH_THREADS = 8

        /**
         * Token generation is bandwidth-bound and gets *slower* once the little cores join
         * in, because every step waits for the slowest thread. Half the cores approximates
         * the big-core count on the phones we have measured.
         *
         * On a Dimensity MT6991 (8 cores) this picks 4, which measured fastest: 16.8 tok/s
         * versus 12.8 at 8 threads.
         */
        fun recommendedThreadCount(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceIn(MIN_THREADS, MAX_GEN_THREADS)

        /**
         * Prompt processing is compute-bound and scales with cores, but only with cores
         * that are big by design. On the MT6991, whose eight all are (four A720s under
         * three X4s and an X925), 8 threads gave 69.5 tok/s against 55.3 at 4, and 96
         * against 72 when remeasured on 2026-09-03. On an SM8650, where two of the eight are A520s, 8 threads gave
         * 105.2 t/s against 139.0 at 6: the barrier at the end of each operation waits for
         * the slowest thread, and a slice on a little core takes about twice as long.
         *
         * [CpuTopology] is what tells the two chips apart, and it answers "every core" for
         * the first one, so this is the same number it always was wherever it was right.
         */
        fun recommendedBatchThreadCount(): Int =
            CpuTopology.performanceCores().coerceIn(MIN_THREADS, MAX_BATCH_THREADS)
    }
}
