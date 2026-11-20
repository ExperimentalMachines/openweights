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
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.CompiledBackend
import io.github.alpharomercoma.openweights.core.common.model.ExecuTorchFileName
import io.github.alpharomercoma.openweights.core.common.model.MediaKind
import io.github.alpharomercoma.openweights.core.common.model.MessagePart
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.PromptTemplate
import io.github.alpharomercoma.openweights.core.common.model.PromptTemplates
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import io.github.alpharomercoma.openweights.core.common.model.ToolCallParser
import io.github.alpharomercoma.openweights.core.common.model.ToolDefinition
import io.github.alpharomercoma.openweights.core.common.model.VisionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs a model that was compiled ahead of time, through ExecuTorch.
 *
 * The trade against [LlamaCppEngine] is worth stating plainly, because it is not a matter
 * of one being better written:
 *
 * - **Append-only prefix reuse.** An unchanged text prefix is kept across turns and
 *   only its suffix is fed. Edits, pictures and replies without a streamed end marker
 *   invalidate that reuse record. Runtime token counts cover generation; separate text
 *   prefills contribute estimates because the Java API exposes no tokenizer.
 * - **A curated catalogue.** A `.pte` is compiled on a desktop for one backend, and for an
 *   NPU one SoC, so models arrive because a build was run for them. That is the constraint
 *   this whole project exists to escape, which is why this engine is the second one and not
 *   the first.
 * - **Export-specific vision.** Pictures need a compiled encoder and a known processor
 *   layout. llama.cpp instead loads a separate projector through libmtmd.
 *
 * What it buys is the only path to an accelerator that has no ggml backend. See
 * `docs/research/mediatek-npu.md` for what that is measured to be worth.
 */
// 702 lines against detekt's 600. The reasoning budget is what crossed it, and cutting the
// engine in two is a change of its own: the pieces here share the module handle, the reuse
// record and the token callback, and a split that hid that would be harder to follow than a
// long file. Raised here and not in the shared config, so the next class that grows fails.
@Suppress("LargeClass")
class ExecuTorchEngine(
    private val bridge: ExecuTorchBridge,
    /** How a picture on disk becomes the encoder's square; swapped out in tests. */
    private val reader: PictureReader = AndroidPictureReader(),
    /**
     * The sampling temperature used for loading and each generation. This engine keeps it
     * fixed for the loaded session; zero means greedy for reproducible evaluation. The
     * native bridge must also set it per call, where the AAR otherwise defaults to 0.8.
     */
    private val temperature: Float = DEFAULT_TEMPERATURE,
    /**
     * The size of one prefill call, in characters: a warm piece and the tail handed to
     * generate. The defaults are the shipped ones ([WARM_PIECE_CHARS], about two hundred
     * tokens, the interrupt latency in text; [GENERATE_TAIL_CHARS], measured on the shipped
     * exports). Characters are a heuristic, not a tokenizer-independent token bound.
     * Parameters only so a device test can measure what the piece size costs in prefill
     * throughput (`ExecuTorchOnDeviceTest.prefillByPieceSize`); nothing in the app passes them.
     */
    private val warmPieceChars: Int = WARM_PIECE_CHARS,
    private val generateTailChars: Int = GENERATE_TAIL_CHARS,
) : InferenceEngine {

    private var info: LoadedModelInfo? = null
    private var template: PromptTemplate? = null
    private var contextSize: Int = 0

    private var opened: OpenedExport? = null
    private var moduleHasRun = false

    /** The pass running now, under [turns]. See [PassMeter]. */
    private val pass = PassSlot()

    /**
     * This pass's memory attached, its ticker ended first, so nothing from the pass follows
     * the completion that carries these stats.
     */
    private fun GenerationStats.metered() = copy(memory = pass.close())

    /**
     * The runner was closed for a reopen that did not happen: its check refused the files, was
     * cancelled, or the open failed. The model is still the one chosen, so the next turn opens
     * it again rather than reaching a prefill with nothing loaded (codex QA).
     */
    private var closedForReopen = false

    private val needsReloadOnReset: Boolean
        get() {
            val held = opened ?: return false
            if (closedForReopen) return true
            val normalized = held.model.name.lowercase().filter { it.isLetterOrDigit() }
            return moduleHasRun && "lfm2" in normalized && held.stateResetAtZero != true
        }

    /**
     * The most characters one call into the runtime may carry, prefill or generate.
     *
     * The exporter bounds the token input at `max_seq_len - 1` and the runtime chunks a
     * long prompt at `max_seq_len`, so a single call of 2048 tokens fails on a 2048/32k
     * export with "Attempted to resize a bounded tensor with a maximum capacity of 2047
     * elements to 2048 elements" (Poco X8 Pro, 2026-09-08: every research step's prompt
     * with the tool prefix was refused, read as a full window, and retried without tools).
     * Characters are the only measure this engine has before tokenizing. This cap avoids
     * the measured failures on the shipped prompts, but is not a universal token bound:
     * byte-fallback tokenizers can use more than one token per UTF-16 code unit.
     */
    private var callChars: Int = generateTailChars

    /**
     * What this turn fed through prefill before generate: pieces of the prompt too long
     * for one call (see [feedAhead]), by characters and by the wall clock.
     *
     * Kept so the turn's stats can say what the turn cost. The runtime's own figures cover
     * the generate call alone, and a turn whose head went in as pieces used to report only
     * its tail: the prefill rate of the last four hundred tokens, and the two thousand
     * before them counted as cached, which they were not. Zeroed at the start of every turn.
     */
    private var fedAheadChars: Int = 0
    private var fedAheadMs: Long = 0L

    /** The text the turn's generate call was given, whose token count the runtime reports. */
    private var tailChars: Int = 0

    /**
     * The text prefix known to match the runtime's committed state.
     *
     * Kept as text because the next prompt can extend it only when its rendering starts
     * with this string. Empty means reuse is unsafe, not that the context is empty:
     * a truncated reply or pictures discard this record without erasing [heldTokens].
     */
    private var fedText: String = ""

    /** Whether the open file reads pictures, which is the file's to say and the template's to feed. */
    private var multimodal: Boolean = false

    /** The family's BOS, when the tokenizer beside the model will not write it; else empty. */
    private var bosPrefix: String = ""

    /**
     * Occupied context, independently of whether [fedText] permits reuse.
     *
     * Generation reports token counts; separate text prefills are estimated. A reply
     * stopped for budget still occupies context even though its final sampled token's
     * character boundary is unknown and the next turn must reset and replay the prompt.
     * Only a valid prefix match makes this count [GenerationStats.cachedTokens].
     */
    private var heldTokens: Int = 0

    /** Set by [cancel]; read between warm pieces, which is where a warm can stop. */
    @Volatile
    private var warmStopped = false

    /**
     * One thing at a time on the runtime, and on the record of what it holds.
     *
     * The llama.cpp engine has one native thread and everything queues behind it. This one
     * had none: a turn, a warm and a reset could each run on whichever IO thread the
     * caller was on, and the app does overlap them, a fold's summary turn against the warm
     * queued behind the load. Two writers to [fedText] and the runtime's own position at
     * once is text fed twice into the cache. So every entry that touches either takes this
     * lock, held for the whole of a turn's stream; [cancel] does not, because its job is to
     * end what holds it.
     */
    private val turns = Mutex()

    // What the runtime holds right now, not what it held at load. The turn runner reads
    // this before every round to decide how much tool output fits; a constant zero told
    // it the whole window was free after every pass.
    override val loadedModel: LoadedModelInfo? get() = info?.copy(contextUsed = heldTokens)

    override suspend fun load(modelFile: File, params: ModelLoadParams, projectorFile: File?) {
        // Off the caller's thread. The chat loads from the main dispatcher, and this maps
        // the whole file and allocates the cache for the exported window: on a 1.8 GB
        // export at 32k that is seconds, and the phone showed "not responding" every five
        // seconds until it was done (2026-09-07, LFM2.5 2.6B). llama.cpp's engine has
        // always hopped to its own thread here; this one did not.
        turns.withLock { withContext(Dispatchers.IO) { loadLocked(modelFile, params) } }
    }

    private fun loadLocked(modelFile: File, params: ModelLoadParams) {
        val tokenizer = tokenizerFor(modelFile)
            ?: refuse(
                "${modelFile.name} has no tokenizer beside it. A .pte carries a compiled " +
                    "graph and nothing that says how to tokenize for it, so both files " +
                    "have to be installed together.",
            )
        // An empty tokenizer opens: the runtime falls back to an empty TikToken and the model
        // loads, then every prompt fails to encode ("Tokenizer error: 5", on the Poco). Said
        // here instead of as a failure on the first message.
        if (tokenizer.length() == 0L) {
            refuse("${modelFile.name} cannot be opened: its tokenizer file is empty.")
        }

        // Refuse rather than guess. A wrong template does not fail: the model answers, a
        // little worse, and its tool calls stop parsing, which reads as a bad model.
        val rendering = PromptTemplates.forModel(modelFile.name)
            ?: refuse(
                "No prompt template for ${modelFile.name}. This build can render: " +
                    PromptTemplates.known.joinToString(", ") + ".",
            )

        closeModel()
        // The window is the file's, not the preference's. The runtime cannot resize its
        // compiled cache, so neither a larger nor a smaller preference changes the limit.
        // A file the probe cannot read, or reads as one the runner would refuse, is refused
        // here. The runner used to be the authority, but every refusal it makes while
        // opening aborts the process instead of returning a message (ExportFacts.unopenable).
        val facts = runCatching { bridge.probe(modelFile.absolutePath) }
            .getOrElse { cause ->
                refuse(
                    "ExecuTorch could not read ${modelFile.name}: " +
                        (cause.message ?: cause::class.java.simpleName),
                )
            }
        facts.unopenable?.let { refuse("${modelFile.name} cannot be opened: $it.") }
        val exported = facts.contextLength
        callChars = facts.callChars()
        // Qualcomm's runner prefills in its own 128-token blocks and wants the whole prompt in
        // one call: fed in pieces, each piece is tokenized apart and its edges add tokens, and
        // ExecuServe measured the prefill falling from 1,700 to 1,180 tok/s that way.
        if (CompiledBackend.of(modelFile.name) == CompiledBackend.QNN) callChars = Int.MAX_VALUE
        // Older text exports without metadata retain the preference as an estimate only.
        contextSize = exported ?: params.contextLength
        // Pictures need both halves: an encoder in the file and a template that knows
        // the square it takes. A vision export of a family this app cannot feed opens as
        // text, which is honest and still useful.
        multimodal = facts.hasVision && rendering.vision != null
        // A model whose tokenizer will not write its BOS gets it from the template instead.
        bosPrefix = rendering.bosToken
            ?.takeUnless { bridge.tokenizerAddsBos(tokenizer.absolutePath) }
            .orEmpty()
        // The multimodal runner in the AAR this app ships aborts the whole process, not the
        // call, when an export lacks the window method it reads first (measured with an
        // older exporter's SmolVLM2: "Required metadata method get_max_seq_len not found").
        // get_max_context_len alone is insufficient: the runner requires get_max_seq_len.
        if (multimodal && (exported == null || facts.prefillLength == null)) {
            refuse(
                "${modelFile.name} was exported without the metadata this app's ExecuTorch " +
                    "runtime needs to read pictures. Ask its publisher for a current export.",
            )
        }
        // The runner opens the file once where a crash cannot reach the app, before it opens
        // it here: a damaged program or an unreadable tokenizer ends that process instead.
        bridge.verify(modelFile.absolutePath, tokenizer.absolutePath, multimodal)
            ?.let { refuse(refusalFor(modelFile, it)) }
        open(modelFile, tokenizer, facts.stateResetAtZero)

        // Publish the fixed export window, not a smaller preference the runner ignores.
        template = rendering
        info = LoadedModelInfo(
            description = modelFile.nameWithoutExtension,
            parameterCount = 0,
            sizeBytes = modelFile.length(),
            contextSize = contextSize,
            trainingContextSize = exported ?: contextSize,
            layerCount = 0,
            contextUsed = 0,
            offloadedTo = "ExecuTorch",
            mediaSupport = MediaSupport(vision = multimodal),
            // The template is the authority: a family whose format cannot express tools
            // must not be offered them, or the agent loop waits for calls that cannot come.
            supportsThinking = rendering.supportsThinking,
            supportsTools = rendering.supportsTools,
            supportsToolResults = rendering.supportsTools,
            modelPath = modelFile.absolutePath,
            contextSizeIsEstimated = exported == null,
        )
    }

    /** The character bound for one runtime call: see [callChars]. */
    private fun ExportFacts.callChars(): Int =
        minOf(generateTailChars, (prefillLength ?: Int.MAX_VALUE) - 1).coerceAtLeast(1)

    /** A load refused for a reason the caller can show. */
    private fun refuse(message: String): Nothing = throw LlamaException(message)

    /**
     * Clears what a failed run held and rethrows. A Vulkan file that opens and then fails on
     * its first run is the shape a missing device feature takes, since each shader is checked
     * when it is dispatched rather than when the file loads, so that is recorded too; a
     * cancelled turn is not a failure of the GPU and is not.
     */
    private fun abandonRun(failure: Throwable): Nothing {
        fedText = ""
        heldTokens = 0
        runCatching { resetRuntime() }
        throw failure
    }

    /**
     * Runs one call into the runtime, and when it fails because this phone's GPU cannot run a
     * Vulkan export at all (see [VulkanSupport.recordIfIncompatible]) records that, so GPU
     * builds stop being offered here, and refuses with the way out. Every other failure
     * passes through untouched: only the runtime's own incompatibility report costs the
     * phone its GPU builds, never a bad file, a full window or an allocation.
     */
    private inline fun <T> gpuChecked(call: () -> T): T = try {
        call()
    } catch (failure: Throwable) {
        val reason = VulkanSupport.recordIfIncompatible(failure) ?: throw failure
        throw LlamaException(gpuRefusal(reason))
    }

    override suspend fun unload() = turns.withLock { withContext(Dispatchers.IO) { closeModel() } }

    /** Under [turns]: the callers that already hold it cannot take it twice. */
    private fun closeModel() {
        bridge.close()
        fedText = ""
        heldTokens = 0

        info = null
        template = null
        opened = null
        moduleHasRun = false
        closedForReopen = false
    }

    /** Opens the file and remembers how, so [resetRuntime] can open it again. */
    private fun open(modelFile: File, tokenizer: File, stateResetAtZero: Boolean?) {
        // The publisher's own setting for the vision export: sampled at the text default it
        // turns generic and repetitive (Software Mansion's model notes, and their runner's
        // defaults for LFM2.5-VL).
        val heat = if (multimodal) VISION_TEMPERATURE else temperature
        val held = OpenedExport(modelFile, tokenizer, heat, stateResetAtZero)
        if (!gpuChecked {
                bridge.load(modelFile.absolutePath, tokenizer.absolutePath, heat, multimodal)
            }
        ) {
            refuse("ExecuTorch could not open ${modelFile.name}")
        }
        opened = held
        moduleHasRun = false
        pass.opened()
    }

    /**
     * The runner's reset rewinds its position and nothing else, and an LFM2 export made
     * before 2026-09-17 keeps its short-convolution state in a buffer the graph never
     * clears: the next prompt starts on the last two columns of the previous one.
     * Measured on the fp32 module, second prompt onward: the tool-call token falls from
     * 0.94 to 0.68 and from 0.92 to 0.38; on a Galaxy S25 Ultra the same 8da4w recipe
     * grades 17, 21 and 25 of 30 with the leak and 19, 22 and 26 without
     * (`docs/research/executorch-state-and-recipes.md`). An export that clears the state
     * in its own graph says so with `get_state_reset_at_zero`; any other used LFM2 file
     * is opened again, about a second, and everything else keeps the cheap reset.
     */
    private fun resetRuntime() {
        val held = opened
        if (held == null || !needsReloadOnReset) {
            bridge.resetContext()
            moduleHasRun = false
            return
        }
        bridge.close()
        moduleHasRun = false
        closedForReopen = true
        // Checked again before it is opened again: the files are paths, and a download that
        // replaced the tokenizer while the model was held would otherwise reach the runtime
        // here, in the app's process (codex QA). Unchanged files answer from the record.
        bridge.verify(held.model.absolutePath, held.tokenizer.absolutePath, multimodal)?.let {
            throw LlamaException("${held.model.name} cannot be opened again: $it.")
        }
        val reopened = gpuChecked {
            bridge.reload(
                held.model.absolutePath,
                held.tokenizer.absolutePath,
                held.temperature,
                multimodal,
            )
        }
        if (!reopened) throw LlamaException("ExecuTorch could not reopen ${held.model.name}")
        closedForReopen = false
        pass.opened()
    }

    override fun chat(
        messages: List<ChatMessage>,
        params: SamplerParams,
        tools: List<ToolDefinition>,
    ): Flow<GenerationEvent> = channelFlow {
        // The first run of a freshly opened module maps its file in, which the fault count
        // cannot tell from paging; measured, never flagged. See [MemoryMeter].
        turns.withLock {
            pass.run(this) { live -> stream(messages, params, tools, live) }
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    /** One turn, from render to the completed event, with [turns] held throughout. */
    private suspend fun ProducerScope<GenerationEvent>.stream(
        messages: List<ChatMessage>,
        params: SamplerParams,
        tools: List<ToolDefinition>,
        live: LiveCount,
    ) {
        val rendering = template ?: throw LlamaException("No model loaded")
        cancelRequested = false
        val pictures = if (multimodal) messages.pictures() else emptyList()
        val prompt = render(
            messages.markingPictures(pictures.isNotEmpty()),
            tools,
            params.thinking,
        )
        val started = System.currentTimeMillis()
        fedAheadChars = 0
        fedAheadMs = 0L
        val (fresh, reused) = openTurn(prompt, pictures, rendering, started) ?: return

        // What the runtime already holds, and whether this turn extends it.
        //
        // ExecuTorch continues rather than matching a prefix: `pos_` survives a generation
        // and `generate` appends wherever it left off. That makes re-sending a conversation
        // a bug — measured, turn one ended at pos_ 2047 and turn two was refused for
        // appending 2068 more into a 2048 window — but it also means the cache is worth
        // keeping when the new prompt genuinely begins with what is in it.
        //
        // Comparing the rendered text is the whole test, and it is exact. A template that
        // re-renders an earlier turn differently produces a prompt that is not an extension,
        // the comparison fails, and the turn starts from nothing. No knowledge of *why* it
        // changed is needed here, which matters because the reasons are not obvious:
        // Qwen3 drops `<think>` from assistant turns once a newer user question arrives, the
        // tool list lives at the very front and this app withdraws it when a turn's budget
        // is spent, and consecutive tool results collapse into one block whose terminator
        // moves when a second result lands. Each rewrites text that has already been fed.
        // An exact hit would leave nothing to send, and this runtime rejects an empty
        // prompt outright — it only accepts one after a separate native prefill call that
        // this bridge never makes. Requiring new text keeps that unreachable.
        //
        // A turn with pictures starts from nothing and leaves nothing to extend: the
        // pictures sit in the cache as embeddings, which the text record here cannot
        // describe, so the next turn re-feeds them rather than trusting a prefix match.

        val reply = StreamedReply(rendering)

        // Zero means no caller-imposed output budget, so the window is the bound. Not the
        // whole window: the room it has left. The native runner does not stop at the edge by
        // itself, and asked for more tokens than are left it raises "Error 0x12: start_pos: N
        // must be less than cache size at dim 1: M" from inside its own loop, which reaches
        // the user as a runtime failure on a turn that had a better answer available: a full
        // context is something the turn loop can act on, an error is not. The AAR ignores
        // maxNewTokens as well, so the budget is enforced by [discipline] counting callbacks,
        // and this clamp is what keeps that count inside the window.
        val room = passRoom(contextSize, heldTokens, fresh.length)
        if (room <= 0) throw nothingLeft(fresh.length)
        val budget = (params.maxTokens.takeIf { it > 0 } ?: contextSize).coerceAtMost(room)

        // Anything thrown below leaves the runtime's position advanced by however much it
        // managed to prefill and decode, which no longer matches anything recorded. The
        // cache is unusable rather than merely unknown, so it is dropped: a retry that
        // trusted the old record would send the same suffix at an already-advanced position
        // and duplicate it.
        val discipline = StopDiscipline(budget)
        // Not cleared here. A Stop that landed while the prompt was being rendered or the
        // context reset has already set it, and the runtime's own stop flag it also raised
        // is wiped when the token loop starts; the flag is the only record left. The
        // callback reads it on the first token and re-issues the stop. It is cleared at
        // the start of the turn instead, before anything a Stop could be aimed at.
        tailChars = fresh.length

        // The reasoning budget, which this runtime has no notion of: see [ThinkingGate] and
        // [closeThinkingBlock]. A template that ends its assistant opener inside an open block
        // (Qwen3.5 writes the tag and a newline, LFM3 1.7B writes the tag alone) has spent
        // nothing of the allowance, so the gate opens with the turn; one that closes an empty
        // block there (Qwen3 with reasoning switched off, LFM3 with thinking off) starts
        // outside it, and so does a template whose opener holds no tag at all.
        val gate = ThinkingGate(
            budget = params.reasoningBudget,
            openTag = THINK_OPEN,
            closeTag = THINK_CLOSE,
            openedByTemplate = prompt.trimEnd('\n').endsWith(THINK_OPEN),
        )
        val run = runPasses(params, fresh, budget, reply, gate, discipline, live)

        // Whatever was held back in case it grew into a marker still belongs to the reply
        // when nothing came of it. Without this flush the caller's streamed text ends short
        // of the completed text, and since the app stores what it streamed, its history
        // would no longer match what was fed.
        reply.flush()?.let { trySend(GenerationEvent.Token(it)) }

        // What the runtime is *committed* to, which is not what it produced. A sampled token
        // only enters the KV cache when it is fed back in to produce the next one, so the
        // token that ended generation never got there: stopping at `<|im_end|>` leaves the
        // cache holding everything before it and not the marker itself. Recording the marker
        // would make the next turn skip feeding it, and the two turns would run together
        // with no end-of-turn between them.
        //
        // The same is true of a reply that ran out of budget, except there the uncommitted
        // token is ordinary text this code cannot identify by character position. So that
        // case gives up reuse entirely rather than guess: an empty record forces the next
        // turn to start over, which is slow and correct.
        //
        // A turn whose thinking block this engine closed is the case worth keeping, and the
        // close is what makes it keepable: those few tokens are in the cache (they were the
        // next call's prompt) and in [StreamedReply.answer] (they were streamed with the rest),
        // so a turn that closed its block and then finished describes the cache as exactly as
        // an uncapped reply does. That matters because it is every tool pass: a pass stopped
        // for budget never emits its end marker, and losing the record costs a full re-prefill
        // of the whole conversation on the pass after it, eleven to nineteen seconds on a
        // hybrid model (see GenerationStats.thinkingPrefilled), which is most of a capped pass.
        // What is still given up is a reply the budget cut *after* the close: its last token is
        // uncommitted like any other cap hit.
        //
        // The MediaTek runtime writes the reply on the CPU and reads prompts on the NPU, whose
        // cache never saw it: recorded as held without that, the next turn appended to a
        // conversation missing the last answer (codex QA). commitReply feeds it to the NPU.
        val whole = reply.endedWhole(run.outcome, cancelRequested) && pictures.isEmpty()
        fedText =
            if (whole && bridge.commitReply(reply.answer)) {
                prompt + reply.answer
            } else {
                ""
            }
        // Losing prefix reuse does not empty the context. Keep its occupancy for folding
        // and headroom decisions until the next turn actually resets the runtime.
        // The multimodal runner reports its full prompt position, including text prefills
        // and pictures; the text runner reports only the current generate call's tail.
        heldTokens = run.outcome.promptTokens + run.outcome.generatedTokens +
            if (multimodal) 0 else reused + fedAheadTokens(run.outcome)

        val raw = reply.answer
        val parsed = ToolCallParser.parse(raw)
        send(
            GenerationEvent.Completed(
                reason = reasonFor(reply, discipline, run.outcome.reason, run.ended),
                stats = statsFor(
                    run.outcome,
                    started,
                    run.firstTokenAt,
                    reused,
                    prompt,
                    run.passes,
                ).metered(),
                content = parsed.text.underOpener(prompt).withoutReasoning(),
                reasoning = raw.underOpener(prompt).reasoning(),
                toolCalls = parsed.calls,
            ),
        )
    }

    override fun cancel() {
        warmStopped = true
        cancelRequested = true
        // The runtime's stop is safe against a running generation and nothing else: a
        // picture inside the encoder is left to finish, and the flag above ends the turn
        // before the next one (codex QA).
        if (!feedingPictures) bridge.stop()
    }

    /** True while a picture is inside the encoder, when the runtime must not be stopped. */
    @Volatile
    private var feedingPictures = false

    /**
     * Set by [cancel] and read by the token callback, which re-issues the stop. The
     * runtime's flag alone is not enough: see the callback in [chat].
     */
    @Volatile
    private var cancelRequested = false

    /**
     * Whether this token is the one to stop on: the user asked, the collector has gone,
     * or the discipline's own budget and rut rules say so. The first two are asked on
     * every token because the runtime clears its stop flag when the loop starts.
     */
    private fun stopNow(
        discipline: StopDiscipline,
        fragment: String,
        reply: StreamedReply,
        collectorGone: Boolean,
    ): Boolean = cancelRequested ||
        collectorGone ||
        discipline.shouldStop(fragment, reply.endedCleanly)

    /**
     * Why a generation ended, decided here because the runtime's own answer is always
     * "end of turn": its callback reports statistics, not why the loop stopped, and the
     * bridge can only pass that on. A pass cut for budget, caught in a rut, or stopped by
     * the user therefore read as a finished one, so a tool call sitting in a truncated
     * reply ran and the turn loop kept a stopped turn's history as if it were whole.
     *
     * [ended] is the turn's own verdict where it has one, which the last pass cannot give:
     * closing a thinking block ends a pass without ending the turn, and a turn that stopped
     * because the block had spent the whole output budget is a deliberate close rather than
     * the cap hit [StopReason.MAX_TOKENS] reports.
     */
    private fun reasonFor(
        reply: StreamedReply,
        discipline: StopDiscipline,
        runtime: StopReason,
        ended: StopReason?,
    ): StopReason = when {
        cancelRequested -> StopReason.CANCELLED
        ended != null -> ended
        reply.endedCleanly -> StopReason.END_OF_TURN
        discipline.cut -> StopReason.MAX_TOKENS
        else -> runtime
    }

    /**
     * The turn's error when the window has no room left before it writes anything.
     *
     * A full context is a state the turn loop acts on and an error it does not: the runner
     * raises "start_pos: N must be less than cache size at dim 1: M" from inside its own loop,
     * which reaches the user as a failure on a turn that had a better answer available.
     */
    private fun nothingLeft(tailChars: Int): Nothing = throw ContextWindowExceededException(
        "This turn needs more than this model's $contextSize position window: $heldTokens are " +
            "already held and the last $tailChars characters of the prompt are still to read. " +
            "Start a new chat, or use a model with a larger window.",
    )

    /** What a turn's passes came to, once a thinking block may have split it in two. */
    private class TurnRun(
        val outcome: ExecuTorchOutcome,
        val passes: Int,
        val firstTokenAt: Long,
        val ended: StopReason?,
    )

    /** One generation call of a turn: what the runtime reported, and whether it wrote at all. */
    private class Passed(
        val outcome: ExecuTorchOutcome,
        val wrote: Boolean,
        val firstTokenAt: Long,
    )

    /** What the passes of one turn add up to, counted as they land. */
    private class Tally {
        var passes = 0
        var closes = 0
        var firstTokenAt = 0L
        var merged: ExecuTorchOutcome? = null

        /** One close this engine wrote. Bounded by [MAX_REASONING_CLOSES] in the caller. */
        fun note(close: String?) {
            if (close != null) closes += 1
        }

        /**
         * Summed rather than replaced: a turn whose block was closed is two generations, and
         * only reporting the second would hide the thinking from the row that says what the
         * turn cost. See [ExecuTorchOutcome.through].
         */
        fun add(passed: Passed) {
            if (passed.wrote) passes += 1
            if (firstTokenAt == 0L) firstTokenAt = passed.firstTokenAt
            merged = merged?.through(passed.outcome) ?: passed.outcome
        }
    }

    /**
     * Why a turn stopped on a close it could not have.
     *
     * Two ways to run out: the closes it is allowed, or the room in the window. Which one it
     * was matters to the reader of the row and to the turn loop, which treats a full context
     * as something to act on and a spent budget as the engine having done its job.
     */
    private fun stoppedFor(closes: Int, produced: Int, budget: Int): StopReason =
        if (closes < MAX_REASONING_CLOSES && produced < budget) {
            StopReason.CONTEXT_FULL
        } else {
            StopReason.REASONING_CLOSED
        }

    /**
     * Runs the turn's passes, closing the thinking block whenever its allowance is spent.
     *
     * A turn is one generation until the block is closed and two after it: llama.cpp writes the
     * close into its own token loop and carries on to the answer, and this runtime has no hook
     * inside a generation, so here the close has to end one call and open the next. The loop
     * ends on the first pass that leaves no block open.
     */
    private suspend fun ProducerScope<GenerationEvent>.runPasses(
        params: SamplerParams,
        prompt: String,
        budget: Int,
        reply: StreamedReply,
        gate: ThinkingGate,
        discipline: StopDiscipline,
        live: LiveCount,
    ): TurnRun {
        var next = prompt
        var ended: StopReason? = null
        var done = false
        val tally = Tally()
        try {
            moduleHasRun = true
            while (!done) {
                val close = if (gate.tripped) {
                    closeThinkingBlock(params, reply, gate, discipline, tally.closes, budget)
                } else {
                    null
                }
                if (gate.tripped && close == null) {
                    ended = stoppedFor(tally.closes, discipline.produced, budget)
                    done = true
                } else {
                    tally.note(close)
                    next = close ?: next
                    tally.add(passOnce(next, budget, reply, gate, discipline, live))
                    done = !gate.tripped
                }
            }
        } catch (failure: Throwable) {
            abandonRun(failure)
        }
        return TurnRun(
            outcome = tally.merged ?: ExecuTorchOutcome(StopReason.END_OF_TURN),
            passes = tally.passes,
            firstTokenAt = tally.firstTokenAt,
            ended = ended,
        )
    }

    /** One generation call, with the token callback the engine hangs its accounting on. */
    private suspend fun ProducerScope<GenerationEvent>.passOnce(
        prompt: String,
        budget: Int,
        reply: StreamedReply,
        gate: ThinkingGate,
        discipline: StopDiscipline,
        live: LiveCount,
    ): Passed {
        pass.ran()
        var wrote = false
        var firstTokenAt = 0L
        val outcome = gpuChecked {
            bridge.generate(prompt, (budget - discipline.produced).coerceAtLeast(1)) { fragment ->
                if (firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                wrote = true
                live.token()
                reply.accept(fragment)?.let { trySend(GenerationEvent.Token(it)) }
                gate.accept(fragment)
                // Asked on every token rather than once from cancel(), because the runtime
                // clears its stop flag when the token loop starts: a Stop that lands during the
                // prefill ahead of it is erased, and a collector that has gone away has nobody
                // left to call cancel() at all. Left running, the loop holds the module's lock
                // until the window fills, and the next turn or a model switch waits behind it.
                // The block's allowance is the third reason: left running, the model spends the
                // rest of the output budget thinking and the answer never starts.
                //
                // Asked rather than short-circuited behind [ThinkingGate.tripped]: the discipline
                // is what remembers this fragment, and a close re-feeds the token the cache never
                // committed, so it has to have seen the one that tripped the gate.
                val stop = stopNow(discipline, fragment, reply, isClosedForSend)
                if (gate.tripped || stop) bridge.stop()
            }
        }
        return Passed(outcome, wrote, firstTokenAt)
    }

    /**
     * Writes the close into the cache and the reply, and returns what the next call reads.
     *
     * Null when the turn cannot have one: it has used every close it is allowed, the output
     * budget is spent, or the window has no room for the note and the tag.
     *
     * The words are the C++ session's, before the tag and not after it, because a model stopped
     * mid-thought with no explanation goes on reasoning in the part the reader sees: LFM3 1.7B
     * over 40 public questions after a search result ran to a 600-token cap 1 time without the
     * note and 0 with it (`docs/research/llama-cpp-v080.md`).
     *
     * The text is streamed, not merely recorded, because the app persists what it streamed: a
     * close in the cache and not in the history breaks the next turn's prefix match. And it goes
     * in as the next call's prompt rather than as a prefill of its own, because a prompt is
     * appended at the runtime's position exactly as a prefill is, and the runtime refuses a
     * prompt that tokenizes to nothing where a prefill can leave the record and the cache
     * disagreeing about where the turn is.
     *
     * The last fragment goes back in front of it. A sampled token only enters the cache when it
     * is fed back to produce the next one, so the token that ended the pass is in the reply and
     * not in the cache; llama.cpp has no such gap because it closes before that token is sampled.
     */
    private fun ProducerScope<GenerationEvent>.closeThinkingBlock(
        params: SamplerParams,
        reply: StreamedReply,
        gate: ThinkingGate,
        discipline: StopDiscipline,
        closes: Int,
        budget: Int,
    ): String? {
        if (closes >= MAX_REASONING_CLOSES || discipline.produced >= budget) return null
        val close = "\n\n" + (params.reasoningNote ?: DEFAULT_REASONING_NOTE) + THINK_CLOSE + "\n"
        val cost = tokensForChars(close.length) + 1
        if (heldTokens + discipline.produced + cost >= contextSize) return null
        reply.accept(close)?.let { trySend(GenerationEvent.Token(it)) }
        gate.closedByEngine()
        return discipline.lastFragment + close
    }

    /**
     * Reads [messages] into the runtime's cache, ahead of anybody asking anything.
     *
     * The llama runtime warms by prefilling and can abort mid-batch; this one warms on
     * the runner's prefill-only entry, which cannot be stopped once called — [stop]
     * gates the token loop, and a prefill has no token loop. So the text goes in pieces,
     * cut at whitespace, and cancellation is checked between them: the piece is the
     * interrupt latency. Everything fed stays useful when interrupted — the cache is
     * append-only and the record grows piece by piece — so the turn that interrupted, or
     * the next warm, extends whatever was already read. The pieces re-tokenize at their
     * cut points, which is the same thing every turn boundary in this engine already
     * does to the stream.
     *
     * The contract is "make the cache exactly this prompt": equal reads nothing, an
     * extension feeds the difference, and anything else — including a cache *longer*
     * than the target, which a forward-only runtime cannot serve — resets and refeeds.
     * That last arm means a reopened or branched conversation re-reads in the
     * background here where llama would roll back; slower, correct, and off the user's
     * clock.
     *
     * [snapshot] and [store] have no meaning on this runtime: it cannot roll back, so
     * there is nothing a snapshot could restore, and it cannot serialize state, so
     * nothing outlives the process. A cold start pays one background head read.
     */
    override suspend fun warm(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        params: SamplerParams,
        snapshot: Boolean,
        store: String?,
    ): WarmResult? = turns.withLock {
        warmLocked(messages, tools, params)
    }

    /**
     * [beginTurn], with its two ways of not getting there.
     *
     * A Stop that lands while the pictures are going in ends the turn as a cancellation,
     * with nothing generated and nothing left in the cache; any other failure clears the
     * cache too, because half a prompt in it is worse than none (the next turn would
     * extend it), and is rethrown for the caller to show. Null means the turn is over.
     */
    @Suppress("SwallowedException")
    private suspend fun ProducerScope<GenerationEvent>.openTurn(
        prompt: String,
        pictures: List<MessagePart.File>,
        rendering: PromptTemplate,
        started: Long,
    ): Pair<String, Int>? = try {
        beginTurn(prompt, pictures, rendering)
    } catch (stopped: StoppedWhileFeeding) {
        forgetHeld()
        send(
            GenerationEvent.Completed(
                reason = StopReason.CANCELLED,
                stats = statsFor(
                    ExecuTorchOutcome(StopReason.CANCELLED),
                    started,
                    0L,
                    0,
                    prompt,
                    passes = 0,
                )
                    .metered(),
                content = "",
                reasoning = "",
                toolCalls = emptyList(),
            ),
        )
        null
    } catch (failure: Throwable) {
        forgetHeld()
        throw failure
    }

    private fun forgetHeld() {
        fedText = ""
        heldTokens = 0
        // Not when the failure was the reopen itself: the runner is already closed, and trying
        // again here would run the whole check, and its wait, a second time inside the same
        // failure (codex QA). The next turn reopens it.
        if (!closedForReopen) runCatching { resetRuntime() }
    }

    /**
     * What this turn hands to generate, and how many tokens the runtime already held.
     *
     * The cache is extended when the new prompt begins with what was fed before and the
     * turn carries no pictures; otherwise it is cleared and the whole prompt is fresh,
     * with the pictures fed on the way so that only the closing text remains.
     */
    private fun beginTurn(
        prompt: String,
        pictures: List<MessagePart.File>,
        rendering: PromptTemplate,
    ): Pair<String, Int> {
        val extending = pictures.isEmpty() &&
            fedText.isNotEmpty() &&
            prompt.startsWith(fedText) &&
            prompt.length > fedText.length
        if (extending) return feedAhead(prompt.substring(fedText.length), heldTokens)
        resetRuntime()
        fedText = ""
        heldTokens = 0
        val fresh = if (pictures.isEmpty()) prompt else feedPictures(prompt, pictures, rendering)
        return feedAhead(fresh, 0)
    }

    /**
     * Feeds all but the last [callChars] of [fresh] through prefill, in warm pieces, and
     * returns the tail for generate with the tokens fed ahead added to [held].
     *
     * The runtime's own chunking cannot be trusted with a long prompt (see [callChars]),
     * and its prefill entry cannot be stopped once called, so the pieces are the warm's
     * and a Stop is read between them. Everything fed stays in the record: the cache is
     * append-only, so the tail generate extends it exactly as a warmed turn does.
     */
    private fun feedAhead(fresh: String, held: Int): Pair<String, Int> =
        feedText(fresh, keepTail = callChars) to held

    /** Bounded text prefills, also used before each picture; returns any reserved tail. */
    private fun feedText(fresh: String, keepTail: Int): String {
        var rest = fresh
        while (rest.length > keepTail) {
            if (cancelRequested) throw StoppedWhileFeeding()
            val piece = warmPiece(rest, callChars)
            val before = System.currentTimeMillis()
            moduleHasRun = true
            pass.ran()
            gpuChecked { bridge.prefill(piece) }
            fedAheadMs += System.currentTimeMillis() - before
            fedAheadChars += piece.length
            fedText += piece
            heldTokens += (piece.length / WARM_CHARS_PER_TOKEN).coerceAtLeast(1)
            rest = rest.substring(piece.length)
        }
        // A stop during the last prefill or picture must not start a generation.
        if (cancelRequested) throw StoppedWhileFeeding()
        return rest
    }

    /**
     * Feeds every text-and-picture pair ahead of the final text, which the generate
     * call takes.
     *
     * The prompt arrives with one [PICTURE_MARKER] where each picture belongs. Around each
     * the export expects its own brackets, the same ones its processor writes: the text
     * before, then `<|image_start|>`, then the picture as embeddings, then `<|image_end|>`
     * leading the text after. What is returned is that last text, brackets included.
     */
    private fun feedPictures(
        fresh: String,
        pictures: List<MessagePart.File>,
        rendering: PromptTemplate,
    ): String {
        val spec = rendering.vision ?: throw LlamaException("This model cannot read pictures")
        val segments = fresh.split(PICTURE_MARKER)
        refuseUnlessPicturesFit(segments, pictures, spec, fresh)
        var carried = ""
        pictures.forEachIndexed { index, picture ->
            // Each picture is a second or two of encoder; a Stop is honoured between them.
            feedText(carried + segments[index] + spec.before, keepTail = 0)
            if (cancelRequested) throw StoppedWhileFeeding()
            feedingPictures = true
            try {
                gpuChecked {
                    bridge.prefillImage(
                        readPicture(picture, spec),
                        spec.side,
                        spec.side,
                        Letterbox.CHANNELS,
                    )
                }
            } finally {
                feedingPictures = false
            }
            carried = spec.after
        }
        return carried + segments.last()
    }

    /** The picture on the encoder's square, in the range the encoder takes. */
    private fun readPicture(picture: MessagePart.File, spec: VisionSpec): FloatArray {
        val raw = reader.read(picture.path, spec.side, spec.fit)
            ?: throw LlamaException(
                "Could not read ${picture.name ?: picture.path.substringAfterLast('/')}",
            )
        return spec.pixels.applyTo(raw)
    }

    /**
     * Refused before the first encoder call rather than discovered at the last: eight
     * pictures at 256 positions each are a 2048 window with no room for a word.
     */
    private fun refuseUnlessPicturesFit(
        segments: List<String>,
        pictures: List<MessagePart.File>,
        spec: VisionSpec,
        fresh: String,
    ) {
        if (segments.size != pictures.size + 1) {
            throw LlamaException(
                "${segments.size - 1} picture markers for ${pictures.size} pictures",
            )
        }
        val visual = pictures.size * spec.tokens
        val brackets = pictures.size * (spec.before.length + spec.after.length)
        val text = (fresh.length + brackets) / WARM_CHARS_PER_TOKEN
        if (visual + text >= contextSize) {
            throw ContextWindowExceededException(
                "${pictures.size} pictures take $visual of this model's $contextSize positions, " +
                    "leaving no room for the conversation. Send fewer pictures at a time.",
            )
        }
    }

    /** A Stop that landed between pictures, before anything was generated. */
    private class StoppedWhileFeeding : LlamaException("Stopped while feeding the prompt")

    private fun List<ChatMessage>.pictures(): List<MessagePart.File> =
        flatMap { message -> message.files.filter { it.kind == MediaKind.IMAGE } }

    /**
     * The messages with each picture replaced by [PICTURE_MARKER] in its text, so the
     * template renders the conversation with the pictures in place; untouched when the
     * turn carries none, so a marker never reaches a model that would read it aloud.
     */
    private fun List<ChatMessage>.markingPictures(withPictures: Boolean): List<ChatMessage> =
        if (!withPictures) {
            this
        } else {
            // Every message is re-rendered, not only those with pictures: a marker typed
            // into an earlier text-only message would otherwise be counted as a picture.
            map { message ->
                ChatMessage(
                    message.role,
                    listOf(MessagePart.Text(message.textWithPictureMarkers())),
                )
            }
        }

    /**
     * The message's text with a marker where each picture sits, and nothing else added.
     *
     * The export's processor writes the image brackets straight into the text with no
     * whitespace around them, so none is added here; the shared media rendering puts a
     * newline on each side, which the model would read as text and which moves every
     * later position. Files that are not pictures are left out, since this runtime has
     * no way in for them, and a marker typed into the text is removed so the count of
     * markers stays the count of pictures.
     */
    private fun ChatMessage.textWithPictureMarkers(): String = buildString {
        parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> append(part.text.replace(PICTURE_MARKER, ""))
                is MessagePart.File -> if (part.kind == MediaKind.IMAGE) append(PICTURE_MARKER)
            }
        }
    }

    private suspend fun warmLocked(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        params: SamplerParams,
    ): WarmResult? = withContext(Dispatchers.IO) {
        val rendering = template ?: return@withContext null
        // The llama warm renders without the generation prompt so its text prefixes any
        // future turn; these templates cannot be asked that, so it is computed instead:
        // the common prefix of this conversation rendered as a prompt and the same
        // conversation with one more user turn is the history text alone — everything up
        // to where the assistant opener and the next turn diverge. A family that folds
        // the system text into the first user turn diverges early and warms almost
        // nothing, which is the correct no-op for it.
        // A conversation with pictures is fed at the turn, embeddings and all; there is
        // no text-only warm of it that the turn could extend.
        if (multimodal && messages.pictures().isNotEmpty()) return@withContext null
        val full = render(messages, tools, params.thinking)
        val probed = render(
            messages + ChatMessage.text(ChatRole.USER, WARM_PROBE),
            tools,
            params.thinking,
        )
        val prompt = full.commonPrefixWith(probed)
        if (prompt.isEmpty()) return@withContext null
        if (prompt == fedText) {
            return@withContext WarmResult(
                warmedTokens = 0,
                reusedTokens = heldTokens,
                prefillMs = 0,
                snapshotBytes = 0,
            )
        }

        val extending = fedText.isNotEmpty() && prompt.startsWith(fedText)
        val reused = if (extending) heldTokens else 0
        var fresh = if (extending) {
            prompt.substring(fedText.length)
        } else {
            resetRuntime()
            fedText = ""
            heldTokens = 0
            prompt
        }

        warmStopped = false
        val started = System.currentTimeMillis()
        var warmed = 0
        try {
            while (fresh.isNotEmpty() && !warmStopped) {
                val piece = warmPiece(fresh, callChars)
                moduleHasRun = true
                pass.ran()
                gpuChecked { bridge.prefill(piece) }
                fedText += piece
                val tokens = (piece.length / WARM_CHARS_PER_TOKEN).coerceAtLeast(1)
                heldTokens += tokens
                warmed += tokens
                fresh = fresh.substring(piece.length)
            }
        } catch (failure: LlamaException) {
            // The runtime's position no longer matches anything recorded; an extension
            // record that might be wrong is worse than none. Same discipline as a
            // failed generate. Logged rather than raised, because a warm is an
            // optimization and its failure already costs exactly what it saves.
            Log.w(TAG, "warm prefill failed; the next turn starts cold", failure)
            fedText = ""
            heldTokens = 0
            runCatching { resetRuntime() }
            return@withContext null
        }
        WarmResult(
            warmedTokens = warmed,
            reusedTokens = reused,
            prefillMs = System.currentTimeMillis() - started,
            snapshotBytes = 0,
        )
    }

    /**
     * The next piece to feed: at most [WARM_PIECE_CHARS] and never more than [limit],
     * preferring to end at a line break, then just before a space.
     *
     * Before a space and not after it, because the tokenizer keeps a space with the word
     * that follows: " not" is one token in the whole prompt, and a piece that ended with
     * the space handed the runtime " " and "not" instead. Measured with LFM2.5's tokenizer
     * on the search-only prompt (2026-09-13): every token that differed between the whole
     * prompt and its pieces sat at a cut made after a space, one to nineteen a prompt.
     * Fuzzed on 156 texts against six tokenizers (`tools/eval/bench/toolcall_probe/
     * fuzz_all.py`): no token differs for LFM2.5, Qwen3, Gemma 3, Llama 3.2 or gpt-oss.
     * SmolLM2's GPT-2 pattern reads a run of line breaks differently at the end of a
     * string than before a word, so a cut after one still differs there (two tokens on
     * the real prompts) under this rule and under the old one alike; no single cut
     * satisfies both patterns, and the runtime offers no tokenizer to cut by.
     */
    private fun warmPiece(text: String, limit: Int): String {
        var most = minOf(warmPieceChars, limit)
        if (text.length <= most) return text
        if (most > 0 && text[most - 1].isHighSurrogate() && text[most].isLowSurrogate()) {
            most--
        }
        if (most == 0) {
            throw LlamaException(
                "The prefill character bound cannot fit a complete Unicode character",
            )
        }
        val window = text.substring(0, most)
        // A line break followed by more whitespace is one token with it ("\n\n", "\n    "),
        // so the cut goes after a break that ends its run. Fuzzed against the tokenizer
        // on code, tables, URLs and whitespace runs (2026-09-13): the plain rule left 36 of
        // 303 texts with differing tokens, this one none.
        var newline = window.lastIndexOf('\n')
        while (newline > 0 && newline + 1 < text.length && text[newline + 1].isWhitespace()) {
            newline = window.lastIndexOf('\n', newline - 1)
        }
        var cut = if (newline > 0) {
            newline + 1
        } else {
            window.lastIndexOf(' ').takeIf { it > 0 } ?: most
        }
        // Each call is tokenized independently. Splitting an image control token turns
        // one special ID into ordinary text even when concatenating the pieces is exact.
        template?.vision?.let { spec ->
            cut = cutOutsideImageBracket(text, cut, most, spec.before)
            cut = cutOutsideImageBracket(text, cut, most, spec.after)
        }
        return text.substring(0, cut)
    }

    private fun cutOutsideImageBracket(text: String, cut: Int, limit: Int, bracket: String): Int {
        if (bracket.isEmpty()) return cut
        val start = text.lastIndexOf(bracket, cut - 1)
        if (start < 0 || start + bracket.length <= cut) return cut
        if (start > 0) return start
        if (bracket.length <= limit) return bracket.length
        throw LlamaException("The prefill character bound cannot fit a complete image bracket")
    }

    override suspend fun resetContext() = turns.withLock {
        resetRuntime()
        fedText = ""
        heldTokens = 0
    }

    /** Thread counts belong to the backend a `.pte` was compiled against, not to a call. */
    override suspend fun setThreads(generateThreads: Int, batchThreads: Int) = Unit

    override fun systemInfo(): String = "ExecuTorch"

    /**
     * The NPU, and only when this session's prompts are actually going to it.
     *
     * Not when the phone merely has one: the disaggregated bridge falls back to the CPU
     * runtime whenever a model has no compiled chunks here, and a badge saying NPU over a
     * reply the CPU produced is worse than no badge.
     */
    override fun computeDevices(): List<ComputeDevice> {
        if (!bridge.prefillingOnNpu) return emptyList()
        return listOf(
            ComputeDevice(
                id = "npu",
                description = "MediaTek NeuroPilot NPU",
                kind = ComputeDeviceKind.ACCELERATOR,
                totalMemoryBytes = 0L,
            ),
        )
    }

    override fun acceleratorNote(): String? = bridge.npuSkipped

    override fun close() {
        bridge.close()
        fedText = ""
        heldTokens = 0
        opened = null
        moduleHasRun = false

        info = null
        template = null
    }

    /**
     * The turn's cost, from the runtime's figures for the generate call plus what this
     * engine fed ahead of it.
     *
     * The runtime measures the generate call from inside: `prompt_eval_end_ms` less
     * `inference_start_ms` is its prefill, `inference_end_ms` less `prompt_eval_end_ms`
     * its decode, and both are what it actually spent. Two corrections are this side's.
     *
     * The first token is the prefill's. The runner samples one token at the end of the
     * prompt and hands it to the callback before its decode loop starts, and
     * `generated_tokens` counts only the loop's steps, so the reply the reader saw is one
     * token longer than the runtime's count. Reported as the reader saw it, and that also
     * makes the decode rate exact: [GenerationStats.decodeTokensPerSecond] divides the
     * tokens after the first by the decode time, and the loop's steps are exactly those.
     * Without the correction a thirty-token reply read as twenty-nine at three per cent
     * under its real rate, and the same reply on llama.cpp, which counts its first token,
     * did not, so the two runtimes disagreed about identical work.
     *
     * One correction per pass, which is why [passes] is a parameter and not "did a token
     * arrive": a turn whose thinking block was closed is two generations, and each of them
     * ends its prompt with a token the runtime does not count.
     *
     * The pieces fed ahead are prompt, not cache. A prompt longer than one call may carry
     * goes in as prefill pieces first (see [feedAhead]). Their time is measured around
     * each call. The text runner reports only the tail's token count, so the head uses
     * that tail's character-to-token rate, or four characters per token without a count.
     * The multimodal runner instead reports its full prompt position, including all
     * prefills and pictures; subtracting reuse is sufficient and avoids double-counting.
     *
     * Time to first token is wall clock from the start of the turn to the first fragment,
     * pieces included, because that is the wait the user felt.
     */
    private fun statsFor(
        outcome: ExecuTorchOutcome,
        started: Long,
        firstTokenAt: Long,
        reused: Int,
        prompt: String,
        passes: Int,
    ): GenerationStats {
        val finished = System.currentTimeMillis()
        val timeToFirst = if (firstTokenAt > 0) firstTokenAt - started else finished - started
        val promptTokens = if (multimodal) {
            (outcome.promptTokens - reused).coerceAtLeast(0)
        } else {
            outcome.promptTokens + fedAheadTokens(outcome)
        }
        return GenerationStats(
            promptTokens = promptTokens,
            generatedTokens = outcome.generatedTokens + passes,
            // The runtime's prefill covers the generate call; the pieces before it were
            // timed here. When the runtime reported nothing, the wait to the first token is
            // the whole prefill, pieces and all.
            prefillMs = outcome.prefillMs.takeIf { it > 0 }?.plus(fedAheadMs) ?: timeToFirst,
            decodeMs = outcome.decodeMs.takeIf { it > 0 } ?: (finished - started - timeToFirst),
            timeToFirstTokenMs = timeToFirst,
            contextUsed = heldTokens,
            contextSize = contextSize,
            // What the runtime kept rather than what it re-read: what was held before this
            // turn began. The pieces fed this turn are in promptTokens above, not here.
            // Zero on a turn that started from nothing, which is the honest answer there.
            cachedTokens = reused,
            // The opener can carry a thinking block the reply continues from — Qwen3
            // closes an empty one when reasoning is switched off — and it is in the prompt
            // and never in the reply, so stored history has to have it put back.
            //
            // Worth knowing that this does not rescue the cache. The template drops that
            // block from history however it is stored, so a turn generated with reasoning
            // off can never be extended: switching reasoning *off* is what costs the cache
            // here, which is the opposite of what it sounds like.
            thinkingPrefilled = prompt.endsWith(THINK_CLOSE + "\n\n"),
        )
    }

    /**
     * How many tokens this turn's fed-ahead pieces came to, at the rate the generate
     * call measured for its own tail. See [statsFor] for why this is the one estimate.
     */
    private fun fedAheadTokens(outcome: ExecuTorchOutcome): Int {
        if (fedAheadChars == 0) return 0
        val tail = tailChars
        val charsPerToken = if (tail > 0 && outcome.promptTokens > 0) {
            tail.toDouble() / outcome.promptTokens
        } else {
            WARM_CHARS_PER_TOKEN.toDouble()
        }
        return (fedAheadChars / charsPerToken).toInt().coerceAtLeast(1)
    }

    /**
     * The tokenizer exported alongside [model], by name.
     *
     * A sibling rather than a lookup, because the pairing has to survive a user moving
     * files around: `Qwen3-1.7B.pte` is answered by `Qwen3-1.7B.tokenizer.json`.
     */
    /**
     * The prompt as the runtime should see it: the template's text, led by the family's BOS
     * when the tokenizer beside this model will not add one. Every render goes through here
     * so the cache's record of what was fed and the next prompt agree on the prefix.
     */
    private fun render(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        thinking: Boolean,
    ): String {
        val rendering = template ?: throw LlamaException("No model loaded")
        return bosPrefix + rendering.render(messages, tools, thinking)
    }

    private fun tokenizerFor(model: File): File? =
        File(model.parentFile, ExecuTorchFileName.tokenizerNameFor(model.name))
            .takeIf { it.isFile }

    /**
     * The reply with the think tag its opener held put back. Qwen3.5 with reasoning on ends
     * its prompt inside an open block, so the reply starts mid-thought and closes a block it
     * never opened; read alone, that closer is text and the thinking becomes the answer.
     * Only when the block did close: a thought cut off by the budget stays as it arrived.
     * Qwen3.5 follows the tag with a newline and LFM3 does not, hence the trim.
     */
    private fun String.underOpener(prompt: String): String {
        val continuesThought = prompt.trimEnd('\n').endsWith(THINK_OPEN) && THINK_CLOSE in this
        return if (continuesThought && !startsWith(THINK_OPEN)) THINK_OPEN + this else this
    }

    private fun String.reasoning(): String {
        val open = indexOf(THINK_OPEN)
        val close = indexOf(THINK_CLOSE)
        if (open < 0 || close < open) return ""
        return substring(open + THINK_OPEN.length, close).trim()
    }

    private fun String.withoutReasoning(): String {
        val close = lastIndexOf(THINK_CLOSE)
        // A closer with no opener before it is text, not the end of a thinking block: a
        // model describing the tag would otherwise lose everything it said up to there.
        if (close < 0 || indexOf(THINK_OPEN) !in 0..<close) return this
        return substring(close + THINK_CLOSE.length).trim()
    }

    private companion object {
        const val THINK_OPEN = "<think>"
        const val THINK_CLOSE = "</think>"

        /**
         * What the model is told just before the block is closed, when the caller gave no
         * wording of its own. The C++ session's default, character for character: the
         * measurement that chose it (see [closeThinkingBlock]) was taken with these words, and
         * a turn that reads the same on both engines is worth more than a second opinion.
         */
        const val DEFAULT_REASONING_NOTE =
            "I have thought enough, so I will act on what I have now."

        /**
         * How many times one turn will close a thinking block the model reopened.
         *
         * A model that keeps reopening spends a close and a pass each time; three of them is
         * already more than the two the tool loop can ask for, and a turn that keeps doing it
         * is not going to answer.
         */
        const val MAX_REASONING_CLOSES = 3

        /**
         * The text session default, passed through loading and generation. Per-turn
         * [SamplerParams] do not currently change this engine session setting.
         */
        const val DEFAULT_TEMPERATURE = 0.8f

        /** What the publisher samples LFM2.5-VL at; the text default makes it ramble. */
        const val VISION_TEMPERATURE = 0.1f
    }
}

/** What the engine's resetRuntime needs to open the same file again. */
private class OpenedExport(
    val model: File,
    val tokenizer: File,
    val temperature: Float,
    val stateResetAtZero: Boolean?,
)

/**
 * When a generation has to be cut, decided a callback at a time.
 *
 * Three reasons, all enforced here because the runtime enforces none of them. The budget
 * passed down turned out to be advisory: on-device, a 768-token budget behind a 405-token
 * SmolLM3 prompt resolved to 1643 in the runner and the model wrote until the window was
 * full; one callback is one token, so counting callbacks is exact. And this runtime's
 * sampler has no repetition penalty, so a greedy model in a rut emits one token forever —
 * measured: SmolLM3 filled its budget with a control character after a tool result. The
 * same token thirty-two times in a row is a rut, never prose; whole-word loops stay the
 * model's own.
 */
private class StopDiscipline(private val budget: Int) {
    /**
     * Tokens produced this turn, counted across every pass of it.
     *
     * The budget is the turn's, not the pass's: a turn whose thinking block was closed is two
     * generations, and the second one may write only what the first left. Read by the engine
     * to hand the next pass what remains, and to know when nothing does.
     */
    var produced = 0
        private set

    /** The fragment the last call saw, which is the token this runtime never commits. */
    var lastFragment = ""
        private set
    private var repeats = 0

    /** True once this cut the generation short: budget spent or a rut, not a clean end. */
    var cut = false
        private set

    fun shouldStop(fragment: String, endedCleanly: Boolean): Boolean {
        produced += 1
        repeats = if (fragment == lastFragment) repeats + 1 else 0
        lastFragment = fragment
        if (endedCleanly) return true
        if (produced >= budget || repeats >= MAX_TOKEN_RUT) cut = true
        return cut
    }

    private companion object {
        /** Identical consecutive tokens before generation is cut as degenerate. */
        const val MAX_TOKEN_RUT = 32
    }
}

/**
 * This pass's outcome and [next]'s as one turn's.
 *
 * A turn whose thinking block was closed is two generations, and the row that reports what the
 * turn cost has to be both of them: keeping only the second would hide the thinking, and keeping
 * only the first would hide the answer. Counts and times add; what describes a moment rather
 * than a total comes from the pass that ended the turn, which is [next]. The same rule
 * [GenerationStats.through] follows for the turn loop's own passes.
 */
private fun ExecuTorchOutcome.through(next: ExecuTorchOutcome): ExecuTorchOutcome =
    ExecuTorchOutcome(
        reason = next.reason,
        promptTokens = promptTokens + next.promptTokens,
        generatedTokens = generatedTokens + next.generatedTokens,
        prefillMs = prefillMs + next.prefillMs,
        decodeMs = decodeMs + next.decodeMs,
        endedOnStopToken = next.endedOnStopToken,
    )

/**
 * The reasoning budget, counted one token at a time while a thinking block is open.
 *
 * The ExecuTorch path had none, so a model that opens a block and does not close it wrote its
 * whole monologue into the answer: a tool pass on the phone is capped at 128 thinking tokens
 * (`TurnRunner.deciding`), and with nothing to close the block the runtime ran on to the output
 * cap and every one of those tokens landed where the reader looks for the reply. This is the
 * same mechanism the llama.cpp session runs (`engine_session.cpp`), which is what makes a
 * budget mean the same thing on either engine.
 *
 * The rules it keeps, and why each one:
 *
 * - It starts open when the rendered prompt already opened the block, which is a template's
 *   doing and not the model's: Qwen3.5 ends its assistant opener with `<think>` and a newline,
 *   so the first token the model writes is already inside the block. A template that closes an
 *   empty block there instead (Qwen3 with reasoning switched off) opens nothing, and a reply
 *   that starts in the answer must not be counted as thought.
 * - It opens when the model writes the opener itself, matched across fragments: a tag does not
 *   arrive on a boundary, so `<thi` and `nk>` are one tag and looking for it in a single
 *   fragment would miss it.
 * - It closes when the close tag is seen, again across fragments, and the token that bears it
 *   counts: it is still inside the block when it arrives.
 * - The count never resets. A model that reopens a block it was shut out of has already spent
 *   the budget it is given, so it trips on the open tag and is closed again in a token, which
 *   is what llama.cpp's loop does with the same turn.
 */
internal class ThinkingGate(
    private val budget: Int,
    openTag: String,
    closeTag: String,
    openedByTemplate: Boolean,
) {
    private val openTag = openTag
    private val closeTag = closeTag

    /** Kept so a tag split across two fragments is still one tag. */
    private val carry = maxOf(openTag.length, closeTag.length) - 1
    private var tail = ""
    private var opened = openedByTemplate && budget >= 0

    /** Tokens counted while the block was open, never reset. */
    var counted = 0
        private set

    /** True while a thinking block is open, whether the template opened it or the model did. */
    val open: Boolean get() = opened

    /** True when the allowance is spent with the block still open: a close is owed now. */
    val tripped: Boolean get() = opened && counted >= budget

    /**
     * One fragment of the reply, which the runtime delivers a token at a time.
     *
     * A negative budget is no budget at all and leaves the block to the model, which is the
     * default for a turn with no tools.
     */
    fun accept(fragment: String) {
        if (budget < 0) return
        val wasOpen = opened
        val text = tail + fragment
        // Later in this fragment wins: a reply that closes and reopens in one token ends open,
        // and one that opens and closes ends closed.
        val closer = text.lastIndexOf(closeTag)
        val opener = text.lastIndexOf(openTag)
        if (closer >= 0 && closer > opener) {
            opened = false
        } else if (opener >= 0) {
            opened = true
        }
        if (wasOpen) counted += 1
        tail = if (carry > 0) text.takeLast(carry) else ""
    }

    /** This engine wrote the close: whatever the model writes next starts outside the block. */
    fun closedByEngine() {
        opened = false
    }
}

/**
 * A reply arriving in fragments, and the question of how much of it is safe to show.
 *
 * Separate from the engine because the arithmetic is fiddly and entirely about text.
 * Fragments do not arrive on marker boundaries, so `<|im_end|>` reaches the callback in
 * pieces; streaming each piece as it lands puts `<|i` on screen and then takes it away.
 * Nothing that could still grow into a marker is released until it either becomes one or
 * cannot.
 */
private class StreamedReply(private val template: PromptTemplate) {
    private val text = StringBuilder()
    private var shown = 0
    private var ends = -1

    /** True once the model produced an end-of-turn marker rather than merely stopping. */
    val endedCleanly: Boolean get() = ends >= 0

    /**
     * Whether the reply ended where a record of it can be kept: on the marker, or where the
     * runtime says its loop ended on its own stop token without handing over the marker's
     * text (MediaTek's runner), which it never feeds back either. The record is kept only
     * once the reply is also committed (`commitReply`). Anything else that ended without the
     * marker (a budget, a full window, a cancel, or a stop on "!" for an export whose end
     * token falls back to id 0) leaves a cache this side cannot describe.
     */
    fun endedWhole(outcome: ExecuTorchOutcome, cancelled: Boolean): Boolean =
        endedCleanly || (outcome.endedOnStopToken && !cancelled)

    /** The reply without the marker or anything after it. */
    val answer: String get() = text.take(if (ends >= 0) ends else text.length).toString()

    /** Adds [fragment], returning any text that has become safe to show. */
    fun accept(fragment: String): String? {
        text.append(fragment)
        if (ends < 0) {
            ends = template.stopMarkers
                .mapNotNull { marker -> text.indexOf(marker).takeIf { at -> at >= 0 } }
                .minOrNull() ?: -1
        }
        val safe = if (ends >= 0) ends else text.length - template.danglingMarkerLength(text)
        if (safe <= shown) return null
        return text.substring(shown, safe).also { shown = safe }
    }

    /** Text withheld in case it became a marker, once it is known that it did not. */
    fun flush(): String? {
        val finish = if (ends >= 0) ends else text.length
        if (finish <= shown) return null
        return text.substring(shown, finish).also { shown = finish }
    }
}

/** One warm piece: about two hundred tokens, which is the interrupt latency in text. */
private const val WARM_PIECE_CHARS = 800

/**
 * The measured character cap for one generate call on the shipped exports. Anything
 * beyond it is fed ahead in warm pieces. This is not a universal token bound; see `callChars`.
 */
private const val GENERATE_TAIL_CHARS = 1600

/** Stands in for a picture in the rendered prompt until the picture is fed in its place. */
private const val PICTURE_MARKER = "\u0000picture\u0000"

/**
 * How many tokens a pass may still write into the window it was handed.
 *
 * [held] is what the cache already holds. The tail is the part of the prompt the generate call
 * carries rather than one a prefill piece has already read, so it is not in [held] and is
 * charged here instead: at [WARM_CHARS_PER_TOKEN], the rate every prefill on this path is
 * counted at, rounded up, and one token on top. That rate is an estimate and the runtime's own
 * check is exact, so a pass handed the window's last token and one more raises "start_pos: N
 * must be less than cache size at dim 1" from inside its loop rather than stopping, which is
 * the failure this whole clamp exists to keep away from the user.
 *
 * Zero or less means the turn has no room at all; the caller answers that as a full context.
 */
internal fun passRoom(contextSize: Int, held: Int, tailChars: Int): Int =
    contextSize - held - tokensForChars(tailChars) - 1

/** [chars] as prompt tokens at this engine's rate, rounded up. */
private fun tokensForChars(chars: Int): Int =
    (chars + WARM_CHARS_PER_TOKEN - 1) / WARM_CHARS_PER_TOKEN

/** The app-wide rough estimate; the runtime reports no token count for a prefill. */
private const val WARM_CHARS_PER_TOKEN = 4

/** Any user text: only the render's shape matters, never the probe's content. */
private const val WARM_PROBE = "x"

private const val TAG = "OpenWeights"

/**
 * Why [model] cannot be opened, given the isolated trial's answer [why]. The probe runs a GPU
 * file's first tokens, so a device that cannot run its shaders says so there rather than on the
 * first turn, and that is learned for the whole phone the same way the first turn learned it.
 */
private fun refusalFor(model: File, why: String): String {
    val gpu = if (CompiledBackend.of(model.name) == CompiledBackend.VULKAN) {
        VulkanSupport.recordIfIncompatible(LlamaException(why))
    } else {
        null
    }
    return gpu?.let(::gpuRefusal) ?: "${model.name} cannot be opened: $why."
}

/** What the user is told when this phone's GPU cannot run Vulkan builds at all. */
private fun gpuRefusal(reason: String): String =
    "This phone's GPU cannot run this Vulkan build ($reason). GPU builds will not be " +
        "offered on this phone again; the same model's CPU build runs anywhere."
