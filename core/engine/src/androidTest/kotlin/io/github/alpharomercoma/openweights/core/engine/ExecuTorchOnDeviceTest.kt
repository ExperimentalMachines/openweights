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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The ExecuTorch engine against a real `.pte`, on a real phone.
 *
 * Push a model and the tokenizer it was exported with, named the way the app names them:
 * ```
 * adb shell mkdir -p /data/local/tmp/openweights
 * adb push Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.pte \
 *   /data/local/tmp/openweights/Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.pte
 * adb push Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.tokenizer.json \
 *   /data/local/tmp/openweights/Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.tokenizer.json
 * ```
 * Both files, because a `.pte` says nothing about which tokenizer produced it and the wrong
 * one gives fluent nonsense rather than an error. Without them these skip rather than fail,
 * so a machine with no device attached stays green.
 */
@RunWith(AndroidJUnit4::class)
class ExecuTorchOnDeviceTest {

    private lateinit var engine: ExecuTorchEngine
    private lateinit var bridge: RecordingBridge

    @Before
    fun setUp() {
        assumeTrue("no .pte at ${MODEL.path}", MODEL.isFile)
        assumeTrue("no tokenizer beside ${MODEL.name}", TOKENIZER.isFile)
        // This engine samples at the temperature it was built with, for every turn; a
        // SamplerParams temperature does not reach it. `-e temperature 0` makes it greedy.
        // What the application does at start-up: a Qualcomm NPU file needs its driver path and
        // decoder family set before it opens, and both read what these record.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        QnnSupport.init(context)
        VulkanSupport.init(context)
        bridge = RecordingBridge(NativeExecuTorchBridge())
        engine = TEMPERATURE?.let { ExecuTorchEngine(bridge, temperature = it) }
            ?: ExecuTorchEngine(bridge)
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) runBlocking { engine.unload() }
    }

    @Test
    fun opensACompiledModel(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)

        val loaded = engine.loadedModel
        assertThat(loaded).isNotNull()
        assertThat(loaded?.modelPath).isEqualTo(MODEL.absolutePath)
        Log.i(TAG, "loaded ${loaded?.description} (${loaded?.sizeBytes} bytes)")
    }

    @Test
    fun answersInSomethingLegible(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)

        val events = engine.chat(
            listOf(ChatMessage.text(ChatRole.USER, "What is the capital of Japan?")),
            SamplerParams(maxTokens = 64, thinking = false),
        ).toList()

        val done = events.filterIsInstance<GenerationEvent.Completed>().single()
        Log.i(TAG, "reply: ${done.content}")
        Log.i(TAG, "stats: ${done.stats}")

        // Deliberately weak on the content and strict on the shape. Whether a 1.7B model
        // knows this is not what is being tested; whether the prompt reached it in the
        // format it was trained on, and came back parsed, is.
        assertThat(done.content).isNotEmpty()
        assertThat(done.stats.decodeMs).isGreaterThan(0L)
    }

    /**
     * Whether a second turn costs the whole conversation or only what is new.
     *
     * The engine keeps the runtime's cache when the next prompt genuinely begins with what
     * has already been fed, which is what this measures. Two turns, the second extending
     * the first: if the cache is kept, its prefill is a fraction of turn one's despite the
     * conversation being longer.
     */
    @Test
    fun reportsWhatASecondTurnCosts(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)

        val first = listOf(ChatMessage.text(ChatRole.USER, LONG_PROMPT))
        val one = turn(first)

        // Stored the way the app stores it: the raw streamed reply, not the parsed
        // content. Storing the parsed form drops the reasoning, and the runtime's cache
        // holds the reasoning, so the next render would describe a different conversation
        // and throw the cache away. Measured: that is exactly what happened before.
        val second = first +
            ChatMessage.text(ChatRole.ASSISTANT, one.raw.ifEmpty { "Understood." }) +
            ChatMessage.text(ChatRole.USER, "In one word, what did I just describe?")
        val two = turn(second)

        Log.i(
            TAG,
            "turn 1: prompt=${one.stats.promptTokens} cached=${one.stats.cachedTokens} " +
                "prefill=${one.stats.prefillMs}ms",
        )
        Log.i(
            TAG,
            "turn 2: prompt=${two.stats.promptTokens} cached=${two.stats.cachedTokens} " +
                "prefill=${two.stats.prefillMs}ms",
        )
        val ratio = two.stats.prefillMs.toDouble() / one.stats.prefillMs.coerceAtLeast(1)
        Log.i(TAG, "second-turn prefill ratio: $ratio (well under 1 means the cache held)")
        // Speed is worthless if the answer stopped making sense. Feeding a suffix means the
        // runtime tokenises text that was previously tokenised as part of a larger string,
        // and a boundary falling mid-word would corrupt the sequence quietly — a fast reply
        // that has lost the thread. The split lands on `<|im_start|>`, a special token, so
        // it should be safe; this is what would catch it if it were not.
        Log.i(TAG, "turn 2 reply: ${two.content}")

        assertThat(one.stats.prefillMs).isGreaterThan(0L)
        // The point of the whole exercise: turn two must not re-read turn one. Generous,
        // because this asserts the cache was kept rather than a particular speed.
        assertThat(two.stats.prefillMs).isLessThan(one.stats.prefillMs)
        assertThat(two.stats.cachedTokens).isGreaterThan(0)
        // Still an answer, not debris from a mis-split sequence.
        assertThat(two.content).isNotEmpty()
        assertThat(two.content).doesNotContain("<|im_")
    }

    /**
     * The same second-turn prompt, read warm and read cold, on one load.
     *
     * [reportsWhatASecondTurnCosts] compares turn two with turn one, which feed different
     * text, so its ratio is not a speedup. Here both arms send the identical turn-two
     * conversation. Each pair generates its own turn one; warm is the real second turn,
     * extending the cache turn one left, and cold resets and reads the same prompt whole.
     *
     * Sampled, as the app samples, the warm arm has to go first: a forward-only cache has
     * no way back to the end of turn one once the cold arm has read, and a sampled turn one
     * comes out different every time (616, 347 and 297 tokens on the POCO). With
     * `-e temperature 0` the engine is greedy, turn one can be written again, and odd pairs
     * run cold first and then regenerate turn one for the warm arm, recording whether it
     * came out the same; greedy is also the only setting under which the two arms' replies
     * say anything about whether reuse changed the output.
     *
     * A warm arm that found nothing to extend is recorded with turn one's reply and the
     * engine's record against the next render, because that is a reuse failure in its own
     * right. Every timed turn waits for the CPU to cool, and each row records the CPU and
     * battery temperatures and whether the screen was on, because this phone throttles a
     * screen-off run two to five times. `-e pairs <n>` sets the count; rows go to
     * `eval-results/<stem>.coldwarm.jsonl`.
     */
    @Test
    fun theSecondTurnReadColdAndWarm(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)
        val first = listOf(ChatMessage.text(ChatRole.USER, LONG_PROMPT))
        val ask = ChatMessage.text(ChatRole.USER, "In one word, what did I just describe?")
        // Reasoning on, as in reportsWhatASecondTurnCosts: Qwen3 with it off closes an empty
        // think block that the next render drops, and the turn could never be extended.
        val opening = SamplerParams()
        val timed = SamplerParams(maxTokens = TIMED_TOKENS)
        val balanced = TEMPERATURE == 0f
        val pairs = arguments.getString("pairs")?.toIntOrNull() ?: DEFAULT_PAIRS
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!.apply { mkdirs() }
        val sink = File(out, MODEL.nameWithoutExtension + ".coldwarm.jsonl")
            .apply { writeText("") }

        var warmHits = 0
        var rewrittenDifferently = 0
        repeat(pairs) { pair ->
            engine.resetContext()
            val one = turn(first, opening)
            // The raw fragments turn one ended on, as the runtime's callback received them.
            val tail = bridge.lastFragments().joinToString(", ", "[", "]") { jsonString(it) }
            Log.i(
                TAG,
                "coldwarm pair=$pair turn one: generated=${one.stats.generatedTokens} " +
                    "reason=${one.done.reason} held=${one.stats.contextUsed} " +
                    "fragments=${bridge.fragments} tail=$tail",
            )
            // A turn one that filled the window leaves no room for turn two, which the
            // runtime then refuses outright. Not a reuse result either way.
            if (one.stats.contextUsed + TIMED_TOKENS + ROOM_FOR_ASK >= one.stats.contextSize) {
                Log.i(TAG, "coldwarm pair=$pair skipped: turn one left no room")
                return@repeat
            }
            val sent = first +
                ChatMessage.text(ChatRole.ASSISTANT, one.raw.ifEmpty { "Understood." }) +
                ask
            val order = if (balanced && pair % 2 == 1) listOf(COLD, WARM) else listOf(WARM, COLD)
            val replies = mutableMapOf<String, String>()
            var match = ""
            for (arm in order) {
                var sameTurnOne = true
                if (arm == COLD) {
                    engine.resetContext()
                } else {
                    if (order.first() == COLD) {
                        engine.resetContext()
                        sameTurnOne = turn(first, opening).raw == one.raw
                        if (!sameTurnOne) rewrittenDifferently++
                    }
                    match = recordAgainst(sent, timed)
                    Log.i(TAG, "coldwarm pair=$pair record: $match sameTurnOne=$sameTurnOne")
                }
                val before = DeviceState.read(coolFirst = true)
                val two = turn(sent, timed)
                val after = DeviceState.read(coolFirst = false)
                val s = two.stats
                replies[arm] = two.raw
                if (arm == WARM && s.cachedTokens > 0) warmHits++
                val opener = if (arm == WARM) {
                    """"turn_one_tokens": ${one.stats.generatedTokens}, """ +
                        """"turn_one_reason": "${one.done.reason}", """ +
                        """"turn_one": ${jsonString(one.raw)}, """ +
                        """"turn_one_tail": $tail, """ +
                        """"record": ${jsonString(match)}, """
                } else {
                    ""
                }
                val row =
                    """{"pair": $pair, "arm": "$arm", "order": "${order.joinToString("-")}", """ +
                        """"temperature": ${TEMPERATURE ?: "null"}, """ +
                        """"same_turn_one": $sameTurnOne, """ +
                        opener +
                        """"prompt_tokens": ${s.promptTokens}, """ +
                        """"cached_tokens": ${s.cachedTokens}, "prefill_ms": ${s.prefillMs}, """ +
                        """"ttft_ms": ${s.timeToFirstTokenMs}, """ +
                        """"generated_tokens": ${s.generatedTokens}, """ +
                        """"decode_ms": ${s.decodeMs}, "cpu_c_before": ${before.cpuC}, """ +
                        """"cpu_c_after": ${after.cpuC}, "battery_c": ${before.batteryC}, """ +
                        """"screen_on": ${before.screenOn}, "cooled_s": ${before.waitedS}, """ +
                        """"reply": ${jsonString(two.raw)}}"""
                sink.appendText(row + "\n")
                Log.i(
                    TAG,
                    "coldwarm pair=$pair arm=$arm fed=${s.promptTokens} cached=${s.cachedTokens} " +
                        "prefill=${s.prefillMs}ms ttft=${s.timeToFirstTokenMs}ms " +
                        "cpu=${before.cpuC}C screen=${before.screenOn}",
                )
            }
            Log.i(TAG, "coldwarm pair=$pair replies identical=${replies[WARM] == replies[COLD]}")
        }
        Log.i(TAG, "coldwarm done: ${sink.absolutePath}")
        // At least one warm arm must actually have been warm, or nothing was compared.
        assertThat(warmHits).isAtLeast(1)
        // A cold-first pair whose turn one came out different did not compare one prompt.
        // Judged after every row is written, so the rows that say which pair survive.
        assertThat(rewrittenDifferently).isEqualTo(0)
    }

    /**
     * What a cold read costs as the prompt grows: one user turn of 10, 21, 42 and 84 of
     * [LONG_PROMPT]'s sentences (about 250, 500, 1,000 and 2,000 tokens on LFM2.5), each from
     * a reset cache after the cooling gate, the order reversed every other round. The
     * runtime's own position line in the log gives the exact token count; the row's is the
     * engine's estimate. `-e sentences 10,21` and `-e repeats <n>` narrow it; rows go to
     * `eval-results/<stem>.prefill.jsonl`.
     */
    @Test
    fun coldPrefillByLength(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)
        val counts = (arguments.getString("sentences") ?: "10,21,42,84")
            .split(',').map { it.trim().toInt() }
        val repeats = arguments.getString("repeats")?.toIntOrNull() ?: REPEATS
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!.apply { mkdirs() }
        val sink = File(out, MODEL.nameWithoutExtension + ".prefill.jsonl")
            .apply { writeText("") }
        repeat(repeats) { round ->
            for (count in if (round % 2 == 0) counts else counts.reversed()) {
                engine.resetContext()
                val before = DeviceState.read(coolFirst = true)
                val stats = turn(
                    listOf(ChatMessage.text(ChatRole.USER, describing(count))),
                    SamplerParams(maxTokens = BUDGET, thinking = false),
                ).stats
                sink.appendText(
                    """{"round": $round, "sentences": $count, """ +
                        """"prompt_tokens": ${stats.promptTokens}, """ +
                        """"prefill_ms": ${stats.prefillMs}, """ +
                        """"ttft_ms": ${stats.timeToFirstTokenMs}, """ +
                        """"cpu_c_before": ${before.cpuC}, "screen_on": ${before.screenOn}, """ +
                        """"cooled_s": ${before.waitedS}}""" + "\n",
                )
                Log.i(
                    TAG,
                    "prefill round=$round sentences=$count fed=${stats.promptTokens} " +
                        "prefill=${stats.prefillMs}ms cpu=${before.cpuC}C",
                )
            }
        }
    }

    /**
     * Whether the engine's record of what it fed is a prefix of the next prompt's render,
     * and if not, the first character where they part. Read through reflection because
     * both are private, and a test is the only place that needs them: a warm turn that
     * re-reads everything says only that the comparison failed, never where.
     */
    private fun recordAgainst(messages: List<ChatMessage>, params: SamplerParams): String {
        val record = ExecuTorchEngine::class.java.getDeclaredField("fedText")
            .apply { isAccessible = true }.get(engine) as String
        val next = ExecuTorchEngine::class.java.getDeclaredMethod(
            "render",
            List::class.java,
            List::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply {
            isAccessible = true
        }.invoke(engine, messages, emptyList<Any>(), params.thinking) as String
        if (record.isNotEmpty() && next.startsWith(record)) {
            return "extends, record ${record.length} of ${next.length} chars"
        }
        val at = record.indices.firstOrNull { it >= next.length || record[it] != next[it] }
            ?: record.length
        fun around(text: String) = text.substring(
            (at - DIFF_CONTEXT).coerceAtLeast(0),
            (at + DIFF_CONTEXT).coerceAtMost(text.length),
        )
        return "breaks at $at of record ${record.length}, render ${next.length}: " +
            "record ${jsonString(around(record))} render ${jsonString(around(next))}"
    }

    /** Reasoning off is the case that cannot reuse the cache; it must still be correct. */
    @Test
    fun stillAnswersWithReasoningOff(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)

        val done = turn(listOf(ChatMessage.text(ChatRole.USER, "Name one colour.")))

        Log.i(TAG, "reasoning-off reply: ${done.content}")
        assertThat(done.content).isNotEmpty()
        assertThat(done.content).doesNotContain("<|im_end|>")
    }

    /**
     * The head prefill: a fresh chat's system block read before anybody asks.
     *
     * Fed through the runner's prefill-only entry, in pieces, and judged by the
     * runtime's own accounting rather than trust: the first question after a warm must
     * report paying for its own words, not for the head. The control is the same
     * question cold. This is also the test that would catch `prefillPrompt` failing
     * silently — the Java wrapper discards its error code — because a head that never
     * reached the cache leaves the turn's count at the cold figure.
     */
    /**
     * The numbers a model-by-window matrix is built from: resident memory after load, prefill
     * rate on a ~900-token prompt, decode rate over a reply long enough to average, resident
     * memory after the turn. Logged, not asserted; the phone decides what is acceptable.
     */
    @Test
    fun throughputReport(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)
        val loaded = engine.loadedModel
        Log.i(TAG, "matrix load: window=${loaded?.contextSize} rssMb=${residentMb()}")

        val head = ChatMessage.text(ChatRole.SYSTEM, LONG_PROMPT)
        val ask = ChatMessage.text(
            ChatRole.USER,
            "Write about 150 words on why a language model might run on a phone instead of a server.",
        )
        // Three turns from a cold cache each: one run on this phone swings by 2x with
        // thermals and core scheduling, so the matrix takes the median of three.
        repeat(REPEATS) { run ->
            engine.resetContext()
            val events =
                engine.chat(
                    listOf(head, ask),
                    SamplerParams(maxTokens = 160, thinking = false),
                ).toList()
            val done = events.filterIsInstance<GenerationEvent.Completed>().single()
            val stats = done.stats
            val prefillRate = stats.promptTokens * 1000.0 / maxOf(stats.prefillMs, 1)
            val decodeRate = stats.generatedTokens * 1000.0 / maxOf(stats.decodeMs, 1)
            val rates = "prefillTokS=%.1f decodeTokS=%.1f".format(prefillRate, decodeRate)
            Log.i(
                TAG,
                "matrix turn $run: prompt=${stats.promptTokens} prefillMs=${stats.prefillMs} " +
                    "generated=${stats.generatedTokens} decodeMs=${stats.decodeMs} $rates " +
                    "rssMb=${residentMb()}",
            )
            if (run == 0) Log.i(TAG, "matrix reply: ${done.content.take(200)}")
            assertThat(stats.generatedTokens).isGreaterThan(16)
        }
    }

    /** This process's resident set, in megabytes, as the kernel reports it. */
    /**
     * What the size of one prefill call costs. The engine feeds a long prompt in pieces of
     * about two hundred tokens so a Stop is honoured quickly; on a Snapdragon 8 Elite its
     * prefill came out behind llama.cpp's where a Dimensity 9400 had it far ahead
     * (`docs/research/executorch-state-and-recipes.md`), and the piece size was the one
     * untested suspect. Same prompt, cold each time, three repeats a size, written to
     * `eval-results/<stem>.pieces.json` so a Test Lab run can pull it. `-e pieces 400,800`
     * narrows the sizes.
     */
    @Test
    fun prefillByPieceSize(): Unit = runBlocking {
        val sizes = (arguments.getString("pieces") ?: "400,800,1600,3200,6400")
            .split(',').map { it.trim().toInt() }
        val messages = listOf(
            ChatMessage.text(ChatRole.SYSTEM, LONG_PROMPT),
            ChatMessage.text(ChatRole.USER, "Acknowledge in one short sentence."),
        )
        val rows = StringBuilder()
        for (size in sizes) {
            val sized = ExecuTorchEngine(
                NativeExecuTorchBridge(),
                warmPieceChars = size,
                generateTailChars = maxOf(size, PIECE_TAIL_FLOOR),
            )
            sized.load(MODEL, PARAMS)
            repeat(REPEATS) { run ->
                sized.resetContext()
                val done = sized.chat(messages, SamplerParams(maxTokens = 24, thinking = false))
                    .toList().filterIsInstance<GenerationEvent.Completed>().single()
                val stats = done.stats
                val rate = stats.promptTokens * 1000.0 / maxOf(stats.prefillMs, 1)
                val line = "pieces size=$size run=$run prompt=${stats.promptTokens} " +
                    "prefillMs=${stats.prefillMs} tokS=%.1f".format(rate)
                Log.i(TAG, line)
                if (rows.isNotEmpty()) rows.append(",\n")
                rows.append(
                    """ {"piece_chars": $size, "run": $run, """ +
                        """"prompt_tokens": ${stats.promptTokens}, """ +
                        """"prefill_ms": ${stats.prefillMs}, """ +
                        """"generated_tokens": ${stats.generatedTokens}, """ +
                        """"decode_ms": ${stats.decodeMs}}""",
                )
            }
            sized.unload()
        }
        val out = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(
            "eval-results",
        )!!
        out.mkdirs()
        File(out, MODEL.nameWithoutExtension + ".pieces.json")
            .writeText("{\"model\": \"${MODEL.name}\", \"rows\": [\n$rows\n]}\n")
    }

    private fun residentMb(): Long = File("/proc/self/status").readLines()
        .firstOrNull { it.startsWith("VmRSS:") }
        ?.filter { it.isDigit() }?.toLongOrNull()?.div(1024) ?: -1

    @Test
    fun warmHeadIsReusedByTheFirstTurn(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)
        val head = ChatMessage.text(ChatRole.SYSTEM, LONG_PROMPT)
        val ask = ChatMessage.text(ChatRole.USER, "Acknowledge in one short sentence.")

        val cold = turn(listOf(head, ask))
        Log.i(TAG, "cold: prompt=${cold.stats.promptTokens} prefill=${cold.stats.prefillMs}ms")

        engine.resetContext()

        val warm = engine.warm(listOf(head), params = SamplerParams(maxTokens = 48))
        assertThat(warm).isNotNull()
        Log.i(TAG, "warm: fed=${warm!!.warmedTokens} pieces in ${warm.prefillMs}ms")
        assertThat(warm.warmedTokens).isGreaterThan(0)

        val after = turn(listOf(head, ask))
        Log.i(
            TAG,
            "after warm: prompt=${after.stats.promptTokens} " +
                "cached=${after.stats.cachedTokens} prefill=${after.stats.prefillMs}ms " +
                "reply=${after.content}",
        )
        assertThat(after.stats.promptTokens).isLessThan(cold.stats.promptTokens / 4)
        assertThat(after.content).isNotEmpty()
        assertThat(after.content).doesNotContain("<|im_")
    }

    /**
     * Stop, pressed while the runtime is mid-reply.
     *
     * The runtime clears its own stop flag when its token loop starts and reports every
     * end as "end of turn", so a stop used to be either lost or reported as a finished
     * reply. Cancelled from the collector after a few tokens: the runtime must halt within
     * a handful more, and the engine must say it was cancelled.
     */
    @Test
    fun aStopMidReplyHaltsTheRuntimeAndSaysSo(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)
        var seen = 0
        var cancelledAtMs = 0L
        val events = mutableListOf<GenerationEvent>()

        engine.chat(
            listOf(ChatMessage.text(ChatRole.USER, "Tell me a long story about a lighthouse.")),
            SamplerParams(maxTokens = 512, thinking = false),
        ).collect { event ->
            events += event
            if (event is GenerationEvent.Token && ++seen == TOKENS_BEFORE_STOP) {
                cancelledAtMs = System.currentTimeMillis()
                engine.cancel()
            }
        }

        val done = events.filterIsInstance<GenerationEvent.Completed>().single()
        val tokens = events.count { it is GenerationEvent.Token }
        Log.i(TAG, "stopped after $tokens tokens, reason=${done.reason}")
        assertThat(cancelledAtMs).isNotEqualTo(0L)
        assertThat(done.reason).isEqualTo(StopReason.CANCELLED)
        assertThat(tokens).isLessThan(TOKENS_BEFORE_STOP + STOP_SLACK)
        assertThat(System.currentTimeMillis() - cancelledAtMs).isLessThan(STOP_WITHIN_MS)
    }

    /**
     * A reply cut for budget says so, because a tool call inside it must not run.
     *
     * Reasoning is left on so the budget is certain to be hit: a `<think>` block is never
     * eight tokens long. Measured with reasoning off, the model once answered this prompt
     * in two tokens and ended the turn itself, which is a different case.
     */
    @Test
    fun aReplyCutForBudgetIsReportedAsSuch(): Unit = runBlocking {
        engine.load(MODEL, PARAMS)

        val events = engine.chat(
            listOf(ChatMessage.text(ChatRole.USER, "Count slowly from one to one hundred.")),
            SamplerParams(maxTokens = BUDGET),
        ).toList()

        val done = events.filterIsInstance<GenerationEvent.Completed>().single()
        val streamed = events.filterIsInstance<GenerationEvent.Token>()
        Log.i(
            TAG,
            "budget $BUDGET: ${streamed.size} tokens, reason=${done.reason}, " +
                "text=${streamed.joinToString("") { it.text }.replace('\n', ' ')}",
        )
        assertThat(done.reason).isEqualTo(StopReason.MAX_TOKENS)
        assertThat(streamed.size).isAtMost(BUDGET + STOP_SLACK)
    }

    /**
     * One turn, with reasoning left on.
     *
     * Reasoning is deliberately on: Qwen3 disables it by closing an empty `<think>` block
     * in the assistant opener, that text lands in the cache, and the template never
     * reproduces it when the turn becomes history — so a turn generated with reasoning off
     * can never be extended. Switching it off is what costs the cache, which is the
     * opposite of how it sounds.
     *
     * The budget is the default, meaning the rest of the window, and that matters as much.
     * A reply cut short by a token budget never emits its end-of-turn marker, and without
     * that marker the engine cannot say which tokens the runtime actually committed, so it
     * gives up the cache rather than guess. Asking for 48 tokens here measured a ratio of
     * 1.96 — worse than no cache at all — purely because nothing ever finished; and the
     * 48 crept back in later, which on a Snapdragon 8 Gen 3 on 2026-09-02 measured the
     * same thing again: turn two re-read all 971 tokens. So the budget is the default.
     */
    private suspend fun turn(
        messages: List<ChatMessage>,
        params: SamplerParams = SamplerParams(),
    ): Turn {
        val events = engine.chat(messages, params).toList()
        return Turn(
            done = events.filterIsInstance<GenerationEvent.Completed>().single(),
            raw = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text },
        )
    }

    /** A finished turn, with the streamed text the app would store as history. */
    private data class Turn(val done: GenerationEvent.Completed, val raw: String) {
        val stats get() = done.stats
        val content get() = done.content
    }

    private companion object {
        const val TAG = "ExecuTorchOnDevice"
        const val REPEATS = 3

        /** Another export can be pointed at with `-e pte <path> -e tokenizer <path>`. */
        private val arguments = InstrumentationRegistry.getArguments()
        val MODEL = File(
            arguments.getString("pte")
                ?: "/data/local/tmp/openweights/Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.pte",
        )
        val TOKENIZER = File(
            arguments.getString("tokenizer")
                ?: (
                    "/data/local/tmp/openweights/" +
                        "Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK.tokenizer.json"
                    ),
        )

        /** `-e temperature <t>` builds the engine at that temperature; unset, the app's. */
        val TEMPERATURE = arguments.getString("temperature")?.toFloatOrNull()

        /** `-e context <n>` raises the preference for an export with a larger window. */
        val PARAMS = ModelLoadParams(
            contextLength = arguments.getString("context")?.toIntOrNull() ?: 2048,
        )
        const val TOKENS_BEFORE_STOP = 5

        const val WARM = "warm"
        const val COLD = "cold"
        const val DEFAULT_PAIRS = 8

        /** Enough of the reply to compare the two arms token for token; the timing is the prefill's. */
        const val TIMED_TOKENS = 48

        /** The question and the openers around it, with slack; about 25 tokens measured. */
        const val ROOM_FOR_ASK = 64

        /** Characters shown either side of where a record and a render part. */
        const val DIFF_CONTEXT = 60

        const val BUDGET = 8

        /** The runtime is asked to stop from inside its own token callback: one or two more. */
        const val STOP_SLACK = 4
        const val STOP_WITHIN_MS = 5_000L

        /** The tail handed to generate is never smaller than the shipped one. */
        const val PIECE_TAIL_FLOOR = 1600

        /** Long enough that re-reading it is visibly different from not re-reading it. */
        val LONG_PROMPT = describing(sentences = 40)

        /** [LONG_PROMPT] at another length: the same sentence, [sentences] times. */
        fun describing(sentences: Int) = buildString {
            append("Here is a description of a system I am building. ")
            repeat(sentences) {
                append(
                    "It runs language models on a phone, entirely on device, with no " +
                        "network involved at any point in generation. ",
                )
            }
            append("Acknowledge briefly.")
        }
    }
}
