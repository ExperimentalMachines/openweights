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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import io.github.alpharomercoma.openweights.core.common.model.ToolDefinition
import io.github.alpharomercoma.openweights.core.common.model.assistantHistoryText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * The weight prefetch, against no prefetch, on conversations shaped like the app's.
 *
 * One cell is one model, one conversation shape, tools offered or not, and one level of outside
 * memory pressure (a balloon under the shell, started by `tools/memory/run_prefetch_matrix.sh`).
 * Each gap before a measured turn is `gap` seconds; in the last `lead` of them the prefetch arm
 * runs the app's own policy (TurnRunner.prefetchWeights: look again 2 s after a look that found
 * nothing to do, 10 s after one that did) and the control arm idles. Both arms sample the same
 * window the same way.
 *
 * Pairs are matched on the work, not just the order:
 * - `single`: a pair is one question asked twice from a reset context, once per arm. Every turn
 *   is a document of about a thousand tokens and the question; the context is reset after each
 *   turn, as a new chat does.
 * - `multi`: a block is two conversations with the same opening and the same follow-ups, one
 *   with the prefetch in every gap and one without, run one after the other from a reset; pair
 *   k is the two conversations' k-th follow-up. The arm a conversation gets is the policy being
 *   on or off for the whole of it, which is what the app does.
 * Which arm goes first is balanced: exactly half the pairs (blocks) start with the prefetch, in
 * a seeded shuffle. Questions are drawn in a separately seeded order.
 *
 * With tools offered, a turn whose round calls tools gets a canned result for every call and
 * another round, at most [MAX_ROUNDS]; rounds, stop reasons and calls left unanswered are kept,
 * so the analysis can see a pair whose arms did different work.
 *
 * Recorded per turn, from contiguous readings: time from Send to the first streamed token of
 * the first round, to the end of the turn, and the parts of the Send that are the benchmark's
 * own (the cancel, closing the sampler, the residency read); the process's major faults and CPU
 * through the window and through the turn; the least memory free in each; the weights out of
 * memory at Send; battery temperature, charging and thermal status; and the wall-clock times of
 * the window's opening, Send and the end, so low memory kills can be put in one or the other.
 * Nothing is dropped: a turn that fails is written with its error and its times, and a multi
 * conversation that fails marks its remaining turns as not run.
 */
@RunWith(AndroidJUnit4::class)
class PrefetchMatrixBenchmark {
    private val arguments = InstrumentationRegistry.getArguments()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File("/data/local/tmp/openweights")
    private val models = (arguments.getString("models") ?: "model.gguf")
        .split(',').map { File(directory, it.trim()) }
    private val run = arguments.getString("run") ?: "norun"
    private val label = arguments.getString("label") ?: "none"
    private val shape = arguments.getString("shape") ?: "single"
    private val withTools = arguments.getString("tools") == "true"
    private val pairs = arguments.getString("pairs")?.toIntOrNull() ?: DEFAULT_PAIRS
    private val blocks = arguments.getString("blocks")?.toIntOrNull() ?: DEFAULT_BLOCKS
    private val depth = arguments.getString("depth")?.toIntOrNull() ?: DEFAULT_DEPTH
    private val gapMs = (arguments.getString("gap")?.toLongOrNull() ?: DEFAULT_GAP_S) * MILLIS
    private val leadMs = (arguments.getString("lead")?.toLongOrNull() ?: DEFAULT_LEAD_S) * MILLIS
    private val seed = arguments.getString("seed")?.toIntOrNull() ?: DEFAULT_SEED
    private val window = arguments.getString("context")?.toIntOrNull() ?: DEFAULT_CONTEXT
    private val answerTokens = arguments.getString("answer")?.toIntOrNull() ?: ANSWER_TOKENS

    init {
        require(shape == "single" || shape == "multi") { "shape is single or multi, got $shape" }
        require(leadMs in 0..gapMs) { "lead must be between 0 and the gap" }
        require(pairs % 2 == 0 && blocks % 2 == 0) {
            "pairs and blocks must be even to balance the order"
        }
    }

    private val cellName get() = "$label-$shape-${if (withTools) "tools" else "plain"}"

    @Test
    fun prefetchAgainstNone() = runBlocking {
        val missing = models.filterNot { it.isFile }
        // A missing model is a failed cell, not a skipped one: the matrix must say it is incomplete.
        assertTrue("models not pushed: $missing", missing.isEmpty())
        for (model in models) {
            val report = Report(model)
            val error = runCatching { measure(model, report) }.exceptionOrNull()
            error?.let { Log.e(TAG, "${model.name} $cellName failed: ${it.message}", it) }
            report.error = error?.let { "${it.javaClass.simpleName}: ${it.message}" }
            report.write()
        }
    }

    private suspend fun measure(model: File, report: Report) = coroutineScope {
        LlamaCppEngine().use { engine ->
            val loadStarted = System.nanoTime()
            engine.load(model, ModelLoadParams(contextLength = window))
            report.loadMs = (System.nanoTime() - loadStarted) / NANOS
            report.write()
            val tools = if (withTools) listOf(SEARCH) else emptyList()
            // Unmeasured: the first call's setup is not part of anybody's turn.
            engine.turn(listOf(system(), user(document(QUESTIONS_PLAIN[0]))), tools)
            engine.resetContext()

            val cellSeed = seed * 31 + model.name.hashCode() * 7 + cellName.hashCode()
            val pool = if (withTools) QUESTIONS_TOOLS else QUESTIONS_PLAIN
            val questions = pool.shuffled(Random(cellSeed + 1))
            if (shape == "single") {
                val order = balancedOrder(pairs, Random(cellSeed))
                for (pair in 0 until pairs) {
                    val question = questions[pair % questions.size]
                    for (position in 0..1) {
                        val prefetching = (position == 0) == order[pair]
                        val messages = listOf(system(), user(document(question)))
                        val row =
                            measuredTurn(
                                engine,
                                messages,
                                tools,
                                pair,
                                position,
                                prefetching,
                                question,
                            )
                        engine.resetContext()
                        report.add(row.row)
                    }
                }
            } else {
                val order = balancedOrder(blocks, Random(cellSeed))
                for (block in 0 until blocks) {
                    val followUps = (0 until depth).map {
                        questions[
                            (block * depth + it) %
                                questions.size,
                        ]
                    }
                    for (position in 0..1) {
                        val prefetching = (position == 0) == order[block]
                        conversation(engine, report, tools, block, position, prefetching, followUps)
                    }
                }
            }
        }
    }

    /** One multi-turn conversation from a reset, the arm on or off for all of it. */
    private suspend fun CoroutineScope.conversation(
        engine: LlamaCppEngine,
        report: Report,
        tools: List<ToolDefinition>,
        block: Int,
        position: Int,
        prefetching: Boolean,
        followUps: List<String>,
    ) {
        engine.resetContext()
        val history = mutableListOf(system(), user(document(OPENING)))
        history += engine.turn(history.toList(), tools).historyMessages
        for ((k, question) in followUps.withIndex()) {
            val pair = block * depth + k
            val asked = user(question)
            val measured =
                measuredTurn(engine, history + asked, tools, pair, position, prefetching, question)
            report.add(measured.row.put("block", block).put("depth", k + 1))
            if (measured.row.has("error")) {
                // The conversation's state after a failure is not one the other arm reached.
                for (rest in k + 1 until followUps.size) {
                    val skipped = notRun(block * depth + rest, position, prefetching)
                    report.add(skipped.put("block", block).put("depth", rest + 1))
                }
                return
            }
            history += asked
            history += measured.history
        }
    }

    /** A measured turn's record, and what it leaves in the conversation. */
    private class Measured(val row: JSONObject, val history: List<ChatMessage>)

    private suspend fun CoroutineScope.measuredTurn(
        engine: LlamaCppEngine,
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        pair: Int,
        position: Int,
        prefetching: Boolean,
        question: String,
    ): Measured {
        val row = JSONObject()
            .put("pair", pair).put("position", position)
            .put("arm", if (prefetching) "prefetch" else "none")
            .put("question", question)
        delay(gapMs - leadMs)
        val windowOpenWall = System.currentTimeMillis()
        val sampler = Sampler.open()
        val tally = Tally()
        val walkedBefore = engine.prefetchWalkedBytes()
        val loop: Job? = if (prefetching) {
            launch(Dispatchers.Default) {
                appPolicy(engine, tally)
            }
        } else {
            null
        }
        val env = environment()
        try {
            delay(leadMs)
        } catch (cancelled: CancellationException) {
            loop?.cancel()
            sampler.close()
            throw cancelled
        }

        // Send.
        val sentAt = System.nanoTime()
        val sendWall = System.currentTimeMillis()
        loop?.cancelAndJoin()
        val joinedAt = System.nanoTime()
        val walked = engine.prefetchWalkedBytes() - walkedBefore
        val windowCost = sampler.close()
        val closedAt = System.nanoTime()
        val outAtSend = engine.weightsOut()
        val readAt = System.nanoTime()
        row.put("window_open_wall_ms", windowOpenWall).put("send_wall_ms", sendWall)
            .put("join_ms", (joinedAt - sentAt) / NANOS)
            .put("sampler_close_ms", (closedAt - joinedAt) / NANOS)
            .put("residency_read_ms", (readAt - closedAt) / NANOS)
            .put("out_at_send_mb", outAtSend?.div(KIB * KIB) ?: JSONObject.NULL)
            .put("window_major_faults", windowCost.majorFaults)
            .put("window_cpu_ms", windowCost.cpuMs)
            .put("window_min_available_mb", windowCost.minAvailableKb / KIB)
            .put("window_start_available_mb", windowCost.startAvailableKb / KIB)
            .put("looks", tally.looks).put("brought_back_mb", tally.broughtBackBytes / (KIB * KIB))
            .put("walked_mb", walked / (KIB * KIB))
            .put("unknown_looks", tally.unknownLooks).put("prefetch_unsupported", tally.unsupported)
            .put("battery_temp_c", env.batteryTempC).put("charging", env.charging)
            .put("thermal_status", env.thermalStatus)

        // The turn's counters start where the window's ended.
        val turnSampler = Sampler.openFrom(windowCost.end)
        val result = runCatching { engine.turn(messages, tools) }
        val turnCost = turnSampler.close()
        row.put("end_wall_ms", System.currentTimeMillis())
            .put("turn_ms", (System.nanoTime() - sentAt) / NANOS)
            .put("turn_major_faults", turnCost.majorFaults)
            .put("turn_cpu_ms", turnCost.cpuMs)
            .put("turn_min_available_mb", turnCost.minAvailableKb / KIB)
        val failure = result.exceptionOrNull()
        if (failure != null) {
            row.put("error", "${failure.javaClass.simpleName}: ${failure.message}")
            Log.i(TAG, "$cellName $row")
            return Measured(row, emptyList())
        }
        val turn = result.getOrThrow()
        row.put(
            "send_to_first_token_ms",
            turn.firstTokenAt?.let { (it - sentAt) / NANOS } ?: JSONObject.NULL,
        )
            .put("rounds", turn.rounds.size)
            .put("unanswered_calls", turn.unanswered)
            .put("stop_reasons", JSONArray(turn.rounds.map { it.reason.name }))
            .put("prompt_tokens", JSONArray(turn.rounds.map { it.stats.promptTokens }))
            .put("cached_tokens", JSONArray(turn.rounds.map { it.stats.cachedTokens }))
            .put("context_used", turn.rounds.last().stats.contextUsed)
            .put("prefill_ms", JSONArray(turn.rounds.map { it.stats.prefillMs }))
            .put("generated_tokens", JSONArray(turn.rounds.map { it.stats.generatedTokens }))
            .put("decode_ms", JSONArray(turn.rounds.map { it.stats.decodeMs }))
            .put("paging_flag", turn.rounds.any { it.stats.memory?.pagingDetected == true })
        if (turn.rounds.any {
                it.reason == StopReason.ERROR
            }
        ) {
            row.put("error", "a round stopped with ERROR")
        }
        Log.i(TAG, "$cellName $row")
        return Measured(row, turn.historyMessages)
    }

    private fun notRun(pair: Int, position: Int, prefetching: Boolean) = JSONObject()
        .put("pair", pair).put("position", position)
        .put("arm", if (prefetching) "prefetch" else "none")
        .put("error", "not run: an earlier turn of this conversation failed")

    /** TurnRunner.prefetchWeights's loop, until the coroutine is cancelled at Send. */
    private suspend fun appPolicy(engine: InferenceEngine, tally: Tally) {
        while (true) {
            val result = engine.prefetchWeights()
            if (result == null) {
                tally.unsupported = true
                return
            }
            tally.add(result)
            delay(if (result.broughtBack) APP_PREFETCH_INTERVAL_MS else APP_PREFETCH_RECHECK_MS)
        }
    }

    private class TurnResult(
        val rounds: List<GenerationEvent.Completed>,
        val historyMessages: List<ChatMessage>,
        val firstTokenAt: Long?,
        val unanswered: Int,
    )

    /**
     * A user turn as the app's loop runs it: a round, and while the model calls tools, the
     * round as it was decoded, a canned result for every call, and another round.
     */
    private suspend fun InferenceEngine.turn(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
    ): TurnResult {
        val rounds = mutableListOf<GenerationEvent.Completed>()
        val added = mutableListOf<ChatMessage>()
        var firstTokenAt: Long? = null
        var unanswered = 0
        while (true) {
            // Timed as the tokens stream, not after the round: the first piece is when the
            // person sees the reply begin.
            val events = chat(
                messages = messages + added,
                params = SamplerParams(
                    temperature = 0f,
                    maxTokens = answerTokens,
                    seed = 1,
                    thinking = false,
                ),
                tools = tools,
            ).onEach {
                if (it is GenerationEvent.Token &&
                    firstTokenAt == null
                ) {
                    firstTokenAt = System.nanoTime()
                }
            }
                .toList()
            val done = events.filterIsInstance<GenerationEvent.Completed>().single()
            rounds += done
            val raw = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
            added +=
                ChatMessage.text(
                    ChatRole.ASSISTANT,
                    assistantHistoryText(raw, done.stats.thinkingPrefilled),
                )
            if (done.toolCalls.isEmpty()) break
            if (rounds.size >= MAX_ROUNDS) {
                unanswered = done.toolCalls.size
                break
            }
            done.toolCalls.forEach { added += ChatMessage.toolResult(it.id, SEARCH_RESULT) }
        }
        return TurnResult(rounds, added, firstTokenAt, unanswered)
    }

    private class Environment(
        val batteryTempC: Double,
        val charging: Boolean,
        val thermalStatus: Int,
    )

    private fun environment(): Environment {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / TENTHS
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val power = context.getSystemService(PowerManager::class.java)
        return Environment(temp, plugged, power?.currentThermalStatus ?: -1)
    }

    /** Exactly half true, in a seeded shuffle, so neither arm can draw every first place. */
    private fun balancedOrder(count: Int, random: Random): List<Boolean> =
        (List(count / 2) { true } + List(count / 2) { false }).shuffled(random)

    private fun system() = ChatMessage.text(ChatRole.SYSTEM, SYSTEM)

    private fun user(text: String) = ChatMessage.text(ChatRole.USER, text)

    /** About a thousand tokens of ordinary prose, then the question. */
    private fun document(question: String): String = buildString {
        append("Here are some notes. Answer the question at the end in two sentences.\n")
        repeat(DOCUMENT_BLOCKS) {
            append("Ada Lovelace worked with Charles Babbage on the Analytical Engine. ")
        }
        append("\nQuestion: ").append(question)
    }

    /** One model's cell, rewritten whole after every turn, through a rename so a kill cannot truncate it. */
    private inner class Report(val model: File) {
        val turns = JSONArray()
        var loadMs: Long? = null
        var error: String? = null

        fun add(row: JSONObject) {
            turns.put(row)
            write()
        }

        fun write() {
            val out = context.getExternalFilesDir("eval-results")!!.also { it.mkdirs() }
            val json = JSONObject()
                .put("run", run).put("model", model.name).put("label", label).put("shape", shape)
                .put("tools", withTools).put(
                    "pairs",
                    if (shape ==
                        "single"
                    ) {
                        pairs
                    } else {
                        blocks * depth
                    },
                )
                .put("blocks", if (shape == "multi") blocks else JSONObject.NULL)
                .put("depth", if (shape == "multi") depth else JSONObject.NULL)
                .put("gap_s", gapMs / MILLIS).put("lead_s", leadMs / MILLIS).put("seed", seed)
                .put("context", window).put("answer_tokens", answerTokens)
                .put("load_ms", loadMs ?: JSONObject.NULL).put("error", error ?: JSONObject.NULL)
                .put("turns", turns)
            val name = "${model.nameWithoutExtension}.$run.$cellName.prefetch.json"
            val tmp = File(out, "$name.tmp")
            tmp.writeText(json.toString(1))
            check(tmp.renameTo(File(out, name))) { "could not write $name" }
        }
    }

    /** Every look one window's prefetching took. Written on the prefetch's thread, read after the join. */
    private class Tally {
        @Volatile var looks = 0

        @Volatile var broughtBackBytes = 0L

        @Volatile var unknownLooks = 0

        @Volatile var unsupported = false

        @Synchronized
        fun add(result: WeightPrefetch) {
            looks++
            val after = result.outAfter
            if (result.broughtBack && after != null) broughtBackBytes += result.outBefore - after
            if (result.broughtBack && after == null) unknownLooks++
        }
    }

    /** Counters across an interval, and the least memory free during it, sampled on its own thread. */
    private class Sampler private constructor(private val start: Snapshot) :
        Thread("window-sampler") {
        @Volatile private var running = true
        private val lock = Any()
        private var least = start.availableKb

        override fun run() {
            while (running) {
                val now = Snapshot.read().availableKb
                synchronized(lock) { least = minOf(least, now) }
                try {
                    sleep(SAMPLE_MS)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }

        /** Stops the thread and waits for it, so nothing it reads lands after this. */
        fun close(): Cost {
            running = false
            interrupt()
            join()
            val end = Snapshot.read()
            val min = synchronized(lock) { minOf(least, end.availableKb) }
            return Cost(
                end.majorFaults - start.majorFaults,
                end.cpuMs - start.cpuMs,
                min,
                start.availableKb,
                end,
            )
        }

        class Cost(
            val majorFaults: Long,
            val cpuMs: Long,
            val minAvailableKb: Long,
            val startAvailableKb: Long,
            val end: Snapshot,
        )

        companion object {
            fun open() = openFrom(Snapshot.read())

            fun openFrom(start: Snapshot) = Sampler(start).also { it.start() }
        }
    }

    private data class Snapshot(val majorFaults: Long, val cpuMs: Long, val availableKb: Long) {
        companion object {
            fun read(): Snapshot {
                // Fields after the command name's closing bracket: majflt is the 10th, utime and
                // stime the 12th and 13th, in clock ticks of 10 ms.
                val stat = File(
                    "/proc/self/stat",
                ).readText().substringAfterLast(')').trim().split(' ')
                val available = File("/proc/meminfo").readLines()
                    .first { it.startsWith("MemAvailable:") }
                    .substringAfter(':').trim().substringBefore(' ').toLong()
                return Snapshot(
                    majorFaults = stat[MAJFLT].toLong(),
                    cpuMs = (stat[UTIME].toLong() + stat[STIME].toLong()) * TICK_MS,
                    availableKb = available,
                )
            }
        }
    }

    private companion object {
        const val TAG = "OpenWeightsPrefetch"
        const val KIB = 1024L
        const val MILLIS = 1000L
        const val NANOS = 1_000_000L
        const val TENTHS = 10.0
        const val SAMPLE_MS = 100L
        const val MAJFLT = 9
        const val UTIME = 11
        const val STIME = 12
        const val TICK_MS = 10L
        const val DEFAULT_PAIRS = 8
        const val DEFAULT_BLOCKS = 2
        const val DEFAULT_DEPTH = 4
        const val DEFAULT_GAP_S = 20L
        const val DEFAULT_LEAD_S = 10L
        const val DEFAULT_SEED = 20261024
        const val DEFAULT_CONTEXT = 8_192
        const val ANSWER_TOKENS = 96
        const val MAX_ROUNDS = 3
        const val DOCUMENT_BLOCKS = 80

        /** TurnRunner's PREFETCH_INTERVAL_MS and PREFETCH_RECHECK_MS, which :app keeps private. */
        const val APP_PREFETCH_INTERVAL_MS = 10_000L
        const val APP_PREFETCH_RECHECK_MS = 2_000L

        const val SYSTEM =
            "You are a helpful assistant on a phone. Answer briefly. Use a tool only " +
                "for what you cannot know: something recent or live."

        const val OPENING = "Summarise these notes in two sentences."

        val QUESTIONS_PLAIN = listOf(
            "Who was Ada Lovelace, and what did she write about the engine?",
            "What did Charles Babbage design before the Analytical Engine?",
            "Why is Ada Lovelace called the first programmer?",
            "What could the Analytical Engine compute, in principle?",
            "How did the Jacquard loom influence the Analytical Engine?",
            "What were Lovelace's notes on Menabrea's paper about?",
            "Was the Analytical Engine ever built in Babbage's lifetime?",
            "What does the Difference Engine do that the Analytical Engine generalises?",
            "Which Bernoulli numbers did Lovelace's program compute?",
            "What did Lovelace say machines could not do?",
            "How does the engine's store relate to modern memory?",
            "What is the mill, in Babbage's design?",
        )

        val QUESTIONS_TOOLS = listOf(
            "What version of Kotlin came out this month?",
            "What is the weather in Manila right now?",
            "Who won the most recent Formula 1 race?",
            "What is the latest Android release, as of today?",
            "What did the Bank of Japan decide at its meeting this week?",
            "What is the current price of gold per ounce?",
            "Which film topped the box office last weekend?",
            "What is the newest Pixel phone announced this year?",
            "What is today's exchange rate from US dollars to yen?",
            "Who is currently leading the Premier League table?",
            "What new features did the latest Chrome release add?",
            "What is the latest stable Linux kernel version?",
        )

        const val SEARCH_RESULT =
            "[1] Result: the answer is in the linked page, updated today. https://example.org/latest"

        val SEARCH = ToolDefinition(
            name = "web_search",
            description =
            "Search the web, only for what you cannot already know: what changed, what is " +
                "recent, or the present state of a named person, product or organisation.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "What to look up, as you would type it into a search box"
                    }
                  },
                  "required": ["query"]
                }
            """.trimIndent(),
        )
    }
}
