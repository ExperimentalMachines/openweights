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

package io.github.alpharomercoma.openweights.core.engine.eval

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import io.github.alpharomercoma.openweights.core.engine.DeviceState
import io.github.alpharomercoma.openweights.core.engine.GenerationEvent
import io.github.alpharomercoma.openweights.core.engine.LlamaCppEngine
import io.github.alpharomercoma.openweights.core.engine.ReuseBoundary
import io.github.alpharomercoma.openweights.core.engine.StopReason
import io.github.alpharomercoma.openweights.core.engine.jsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves the warm-prefix path does what it claims, on real weights.
 *
 * Three claims, each measured rather than trusted:
 * 1. A warmed prefix is *reused*: the first turn after [LlamaCppEngine.warm] reports most
 *    of its prompt as cached.
 * 2. A new conversation after an old one still reuses the prefix — the rollback families
 *    by cutting the cache, the recurrent and hybrid families by restoring the snapshot.
 * 3. None of it changes a single byte of output: greedy decoding over a restored state
 *    answers exactly what greedy decoding over a cold prefill answers. This is the
 *    assertion that makes the other two safe to ship.
 */
@RunWith(AndroidJUnit4::class)
class WarmPrefixEval {

    @Test
    fun warmedPrefixIsReusedAndAnswersAreUnchanged(): Unit = runBlocking {
        val models = EVAL_DIR.listFiles { file -> file.extension == "gguf" }.orEmpty()
        assumeTrue("no .gguf files in $EVAL_DIR", models.isNotEmpty())
        models.sortedBy { it.name }.forEach { check(it) }
    }

    private suspend fun check(model: File) {
        Log.i(TAG, "warm eval on ${model.name}")

        // Baseline session: a computed prefix, then the question. Byte-for-byte equality
        // is only owed between runs that decode the same batches in the same order —
        // llama.cpp's numbers shift a little with batch boundaries, which is a property
        // multi-turn caching already lives with — so the baseline is built on the same
        // schedule the warm path uses: prefix in the cache, question decoded on top.
        val baseline = LlamaCppEngine()
        val computed: Reply
        try {
            baseline.load(model, ModelLoadParams(contextLength = CONTEXT))
            val warm = baseline.warm(listOf(system()), params = PARAMS)
            assertWithMessage("warm on ${model.name}").that(warm).isNotNull()
            Log.i(
                TAG,
                "${model.name}: warmed=${warm!!.warmedTokens} reused=${warm.reusedTokens} " +
                    "in ${warm.prefillMs}ms snapshot=${warm.snapshotBytes / 1024}KB",
            )
            if (warm.warmedTokens + warm.reusedTokens < 100) {
                // Some template families have no system region to warm — Gemma folds the
                // system text into the first user turn — so a warm renders almost nothing.
                // That is a no-op in production, not a failure: the model simply keeps
                // paying the cold first turn it always paid.
                Log.i(TAG, "${model.name}: template carries no warmable prefix, skipping")
                return
            }
            computed = baseline.ask(SECOND_QUESTION)
            assertWithMessage("${model.name}: warmed prefix was not reused")
                .that(computed.stats.cachedTokens).isGreaterThan(200)
            assertThat(computed.text).isNotEmpty()
        } finally {
            baseline.unload()
            baseline.close()
        }

        // Fresh session: warm, hold a conversation to move the cache past the prefix, then
        // open a new chat. A transformer gets back to the prefix by rolling the turns off;
        // a hybrid cannot roll back and must restore the snapshot. Either way the state
        // under the question is reconstructed rather than freshly computed — and the reply
        // must not change by a byte.
        val engine = LlamaCppEngine()
        try {
            engine.load(model, ModelLoadParams(contextLength = CONTEXT))
            val snapshotBytes = engine.warm(listOf(system()), params = PARAMS)?.snapshotBytes ?: 0L
            val first = engine.ask(QUESTION)
            assertThat(first.text).isNotEmpty()
            engine.ask(
                QUESTION,
                ChatMessage.text(ChatRole.ASSISTANT, first.text),
                ChatMessage.text(ChatRole.USER, "Now add ten to it."),
            )

            val restored = engine.ask(SECOND_QUESTION)
            assertWithMessage("${model.name}: fresh chat re-read the prefix")
                .that(restored.stats.cachedTokens).isGreaterThan(200)
            if (snapshotBytes > 0) {
                // The snapshot restore replaces the state wholesale, so its schedule is
                // identical to the baseline's and the reply is owed byte for byte. The
                // rollback families are llama.cpp's own shipping path and keep a few
                // header tokens from the old conversation's batch, whose accumulation
                // order can flip one near-tie token deep in a thinking chain — the same
                // epsilon every cached multi-turn reply already carries — so for them the
                // claim is the answer, not the bytes.
                assertWithMessage("${model.name}: restored state answered differently")
                    .that(restored.text).isEqualTo(computed.text)
            } else {
                assertWithMessage("${model.name}: fresh chat lost the answer")
                    .that(restored.text).contains("Paris")
                assertWithMessage("${model.name}: baseline lost the answer")
                    .that(computed.text).contains("Paris")
            }
            Log.i(
                TAG,
                "${model.name}: fresh-chat cached=${restored.stats.cachedTokens} " +
                    "prompt=${restored.stats.promptTokens} " +
                    "ttft=${restored.stats.timeToFirstTokenMs}ms",
            )
        } finally {
            engine.unload()
            engine.close()
        }
    }

    /**
     * The same fresh-chat question, read cold and read over a restored head.
     *
     * The fresh-chat figures behind the talk (235 ms on LFM2.5) are times to first token after
     * a restore, with no cold reading of the same prompt beside them. Here each pair asks the
     * identical question twice, on two engines. Restored: warm the head with its snapshot,
     * hold a two-turn conversation so the cache has moved past the head, then open a new
     * chat, which a hybrid can only serve by restoring the snapshot and a transformer by
     * rolling back. Cold: a fresh engine that never warmed, with one unrelated turn first so
     * mapping the weights in is not counted as prefill. Pairs alternate which arm goes first,
     * each timed turn waits for the CPU to cool, and each row records the phone's state.
     * `-e gguf <path>` picks one model (default: every .gguf in [EVAL_DIR]); `-e pairs <n>`;
     * rows go to `eval-results/<stem>.restore.jsonl`.
     */
    @Test
    fun aFreshChatReadColdAndRestored(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val models = arguments.getString("gguf")?.let { listOf(File(it)) }
            ?: EVAL_DIR.listFiles { file ->
                file.extension == "gguf"
            }.orEmpty().sortedBy { it.name }
        assumeTrue("no .gguf to run", models.any { it.isFile })
        val pairs = arguments.getString("pairs")?.toIntOrNull() ?: DEFAULT_PAIRS
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!.apply { mkdirs() }
        for (model in models.filter { it.isFile }) {
            val sink = File(out, model.nameWithoutExtension + ".restore.jsonl").apply {
                writeText("")
            }
            var unwarmable = false
            repeat(pairs) { pair ->
                if (unwarmable) return@repeat
                val order = if (pair % 2 == 0) listOf(RESTORED, COLD) else listOf(COLD, RESTORED)
                val replies = mutableMapOf<String, String>()
                for (arm in order) {
                    val row = timedFreshChat(model, arm)
                    if (row == null) {
                        // Gemma folds the system text into the first user turn, so there is
                        // no head to warm or restore: nothing to compare, not a failure.
                        Log.i(TAG, "restore ${model.name}: no warmable prefix, skipped")
                        unwarmable = true
                        return@repeat
                    }
                    replies[arm] = row.reply.text
                    val s = row.reply.stats
                    sink.appendText(rowJson(pair, arm, order, row) + "\n")
                    Log.i(
                        TAG,
                        "restore ${model.name} pair=$pair arm=$arm prompt=${s.promptTokens} " +
                            "cached=${s.cachedTokens} prefill=${s.prefillMs}ms " +
                            "ttft=${s.timeToFirstTokenMs}ms cpu=${row.before.cpuC}C",
                    )
                    // Written before judging, so a failed pair still leaves its row.
                    if (arm == RESTORED) {
                        assertWithMessage("${model.name}: the fresh chat did not restore the head")
                            .that(s.cachedTokens).isGreaterThan(MIN_RESTORED)
                    } else {
                        assertWithMessage("${model.name}: the cold arm reused a cache")
                            .that(s.cachedTokens).isLessThan(MAX_COLD_REUSE)
                    }
                }
                Log.i(
                    TAG,
                    "restore pair=$pair replies identical=${replies[RESTORED] == replies[COLD]}",
                )
            }
        }
    }

    /**
     * What a cold read costs as the prompt grows, on this runtime: a system text of 5, 10, 20
     * and 40 of [system]'s rules (about 130 to 1,000 tokens) and one question, each from a
     * reset cache after the cooling gate, the order reversed every other round. One engine,
     * with an unrelated turn first so mapping the weights in is not counted. `-e gguf <path>`
     * picks the model; rows go to `eval-results/<stem>.prefill.jsonl`.
     */
    @Test
    fun coldPrefillByLength(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val model = File(arguments.getString("gguf") ?: "$EVAL_DIR/none.gguf")
        assumeTrue("no .gguf at $model", model.isFile)
        val counts = (arguments.getString("rules") ?: "5,10,20,40,80")
            .split(',').map { it.trim().toInt() }
        val repeats = arguments.getString("repeats")?.toIntOrNull() ?: 3
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!.apply { mkdirs() }
        val sink = File(out, model.nameWithoutExtension + ".prefill.jsonl").apply { writeText("") }
        val engine = LlamaCppEngine()
        try {
            engine.load(model, ModelLoadParams(contextLength = CONTEXT))
            val short = PARAMS.copy(maxTokens = 4)
            engine.chat(listOf(ChatMessage.text(ChatRole.USER, "Say OK.")), short).toList()
            repeat(repeats) { round ->
                for (count in if (round % 2 == 0) counts else counts.reversed()) {
                    engine.resetContext()
                    val before = DeviceState.read(coolFirst = true)
                    val messages = listOf(system(count), ChatMessage.text(ChatRole.USER, QUESTION))
                    val stats = engine.chat(messages, short).toList()
                        .filterIsInstance<GenerationEvent.Completed>().single().stats
                    sink.appendText(
                        """{"round": $round, "rules": $count, """ +
                            """"prompt_tokens": ${stats.promptTokens}, """ +
                            """"cached_tokens": ${stats.cachedTokens}, """ +
                            """"prefill_ms": ${stats.prefillMs}, """ +
                            """"ttft_ms": ${stats.timeToFirstTokenMs}, """ +
                            """"cpu_c_before": ${before.cpuC}, """ +
                            """"screen_on": ${before.screenOn}, """ +
                            """"cooled_s": ${before.waitedS}}""" + "\n",
                    )
                    Log.i(
                        TAG,
                        "prefill ${model.name} round=$round rules=$count " +
                            "prompt=${stats.promptTokens} cached=${stats.cachedTokens} " +
                            "prefill=${stats.prefillMs}ms cpu=${before.cpuC}C",
                    )
                }
            }
        } finally {
            engine.unload()
            engine.close()
        }
    }

    /**
     * The same second turn read with the first turn kept in the cache and from a reset cache,
     * the llama.cpp twin of `ExecuTorchOnDeviceTest.theSecondTurnReadColdAndWarm`: the same
     * user turn ([longPrompt], about a thousand tokens), the same question, greedy, the turn
     * one reply carried as the app carries it, the order alternated with turn one written
     * again for every cold-first pair, a 48-token timed reply after the cooling gate. No
     * system message, so it runs on Gemma, whose template folds a system text into the first
     * turn and leaves [aFreshChatReadColdAndRestored] nothing to restore. `-e gguf <path>`,
     * `-e pairs <n>` (default 8); rows go to `eval-results/<stem>.coldwarm.jsonl` in the
     * ExecuTorch test's layout.
     */
    @Test
    fun theSecondTurnReadColdAndWarm(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val model = File(arguments.getString("gguf") ?: "$EVAL_DIR/none.gguf")
        assumeTrue("no .gguf at $model", model.isFile)
        val pairs = arguments.getString("pairs")?.toIntOrNull() ?: COLDWARM_PAIRS
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!.apply { mkdirs() }
        val sink = File(out, model.nameWithoutExtension + ".coldwarm.jsonl")
            .apply { writeText("") }
        val first = listOf(ChatMessage.text(ChatRole.USER, longPrompt()))
        val opening = PARAMS.copy(maxTokens = TURN_ONE_TOKENS)
        val timed = PARAMS.copy(maxTokens = TIMED_TOKENS)
        val engine = LlamaCppEngine()
        var warmHits = 0
        var rewritten = 0
        var coldReused = 0
        try {
            engine.load(model, ModelLoadParams(contextLength = CONTEXT))
            repeat(pairs) { pair ->
                engine.resetContext()
                val one = engine.turn(first, opening)
                // A turn one cut by its budget leaves a cache the engine does not extend; such a
                // pair compares nothing, so it is skipped and said so.
                if (one.reason != StopReason.END_OF_TURN) {
                    Log.i(TAG, "coldwarm pair=$pair skipped: turn one ended ${one.reason}")
                    return@repeat
                }
                val sent = first + ChatMessage.text(ChatRole.ASSISTANT, one.text) +
                    ChatMessage.text(ChatRole.USER, ASK)
                val order = if (pair % 2 == 1) listOf(COLD, WARM) else listOf(WARM, COLD)
                val replies = mutableMapOf<String, String>()
                for (arm in order) {
                    var sameTurnOne = true
                    if (arm == COLD) {
                        engine.resetContext()
                    } else if (order.first() == COLD) {
                        engine.resetContext()
                        val again = engine.turn(first, opening)
                        sameTurnOne = again.text == one.text && again.reason == one.reason
                        if (!sameTurnOne) rewritten++
                    }
                    val before = DeviceState.read(coolFirst = true)
                    val two = engine.turn(sent, timed)
                    val after = DeviceState.read(coolFirst = false)
                    val s = two.stats
                    replies[arm] = two.text
                    if (arm == WARM && s.cachedTokens > MIN_RESTORED) warmHits++
                    if (arm == COLD && s.cachedTokens > 0) coldReused++
                    val fields = listOf(
                        "pair" to "$pair",
                        "arm" to jsonString(arm),
                        "order" to jsonString(order.joinToString("-")),
                        "temperature" to "0.0",
                        "runtime" to jsonString("llama.cpp"),
                        "same_turn_one" to "$sameTurnOne",
                        "turn_one_tokens" to "${one.stats.generatedTokens}",
                        "turn_one_reason" to jsonString("${one.reason}"),
                        "turn_one" to jsonString(one.text),
                        "prompt_tokens" to "${s.promptTokens}",
                        "cached_tokens" to "${s.cachedTokens}",
                        "prefill_ms" to "${s.prefillMs}",
                        "ttft_ms" to "${s.timeToFirstTokenMs}",
                        "generated_tokens" to "${s.generatedTokens}",
                        "decode_ms" to "${s.decodeMs}",
                        "cpu_c_before" to "${before.cpuC}",
                        "cpu_c_after" to "${after.cpuC}",
                        "battery_c" to "${before.batteryC}",
                        "screen_on" to "${before.screenOn}",
                        "cooled_s" to "${before.waitedS}",
                        "reply" to jsonString(two.text),
                    )
                    val row = fields.joinToString(", ", "{", "}") { (k, v) -> "\"$k\": $v" }
                    sink.appendText(row + "\n")
                    Log.i(
                        TAG,
                        "coldwarm ${model.name} pair=$pair arm=$arm fed=${s.promptTokens} " +
                            "cached=${s.cachedTokens} prefill=${s.prefillMs}ms " +
                            "ttft=${s.timeToFirstTokenMs}ms " +
                            "turn_one=${one.stats.generatedTokens} " +
                            "cpu=${before.cpuC}C screen=${before.screenOn}",
                    )
                }
                val same = replies[WARM] == replies[COLD]
                Log.i(TAG, "coldwarm pair=$pair replies identical=$same")
            }
        } finally {
            engine.unload()
            engine.close()
        }
        // Judged after every row is written, so a failed run still leaves its rows.
        assertWithMessage("${model.name}: no warm arm reused the first turn")
            .that(warmHits).isAtLeast(1)
        assertWithMessage("${model.name}: a rewritten turn one came out different")
            .that(rewritten).isEqualTo(0)
        assertWithMessage("${model.name}: a reset arm reused a cache")
            .that(coldReused).isEqualTo(0)
    }

    /**
     * [ReuseBoundary] on llama.cpp, with the same prompt and cases as the ExecuTorch test: a
     * clean end, a budget cut, a cancel and an edited history, each against a reset. Run on a
     * full-attention GGUF the edit is a truncation; on a hybrid (LFM2.5) the cache refuses a
     * partial rollback and the engine's own recovery shows in its kv log lines.
     * `-e gguf <path>`, `-e repeats <n>`; rows go to `eval-results/<stem>.boundary.jsonl`.
     */
    @Test
    fun reuseBoundaries(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val model = File(arguments.getString("gguf") ?: "$EVAL_DIR/none.gguf")
        assumeTrue("no .gguf at $model", model.isFile)
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!.apply { mkdirs() }
        val engine = LlamaCppEngine()
        try {
            engine.load(model, ModelLoadParams(contextLength = CONTEXT))
            ReuseBoundary(
                engine = engine,
                runtime = "llama.cpp",
                model = model.name,
                sink = File(out, model.nameWithoutExtension + ".boundary.jsonl"),
                prompt = longPrompt(),
                opening = PARAMS.copy(maxTokens = TURN_ONE_TOKENS),
                timed = PARAMS.copy(maxTokens = ReuseBoundary.TIMED_TOKENS),
            ).run(arguments.getString("repeats")?.toIntOrNull() ?: ReuseBoundary.REPEATS)
                .check(model.name)
        } finally {
            engine.unload()
            engine.close()
        }
    }

    /** One turn on whatever the engine holds, with the text the app would store as history. */
    private suspend fun LlamaCppEngine.turn(
        messages: List<ChatMessage>,
        params: SamplerParams,
    ): Reply {
        val events = chat(messages, params).toList()
        val done = events.filterIsInstance<GenerationEvent.Completed>().single()
        val raw = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
        return Reply(raw.ifEmpty { done.content }, done.stats, done.reason)
    }

    private fun rowJson(pair: Int, arm: String, order: List<String>, row: TimedRow): String {
        val s = row.reply.stats
        val fields = listOf(
            "pair" to "$pair",
            "arm" to jsonString(arm),
            "order" to jsonString(order.joinToString("-")),
            "snapshot_bytes" to "${row.snapshotBytes}",
            "prompt_tokens" to "${s.promptTokens}",
            "cached_tokens" to "${s.cachedTokens}",
            "prefill_ms" to "${s.prefillMs}",
            "ttft_ms" to "${s.timeToFirstTokenMs}",
            "generated_tokens" to "${s.generatedTokens}",
            "decode_ms" to "${s.decodeMs}",
            "cpu_c_before" to "${row.before.cpuC}",
            "cpu_c_after" to "${row.after.cpuC}",
            "battery_c" to "${row.before.batteryC}",
            "screen_on" to "${row.before.screenOn}",
            "cooled_s" to "${row.before.waitedS}",
            "reply" to jsonString(row.reply.text),
        )
        return fields.joinToString(", ", "{", "}") { (key, value) -> "\"$key\": $value" }
    }

    /**
     * One arm of [aFreshChatReadColdAndRestored], on an engine of its own; null when the
     * model's template leaves no head to warm.
     */
    private suspend fun timedFreshChat(model: File, arm: String): TimedRow? {
        val engine = LlamaCppEngine()
        try {
            engine.load(model, ModelLoadParams(contextLength = CONTEXT))
            var snapshotBytes = 0L
            if (arm == RESTORED) {
                // A null warm is a failure (the engine returns null after a native error); only a
                // warm that succeeded and found almost nothing to read is a template with no head.
                val warm = engine.warm(listOf(system()), params = PARAMS)
                assertWithMessage("${model.name}: the warm failed").that(warm).isNotNull()
                if (warm!!.warmedTokens + warm.reusedTokens < MIN_RESTORED) return null
                snapshotBytes = warm.snapshotBytes
                val first = engine.ask(QUESTION)
                engine.ask(
                    QUESTION,
                    ChatMessage.text(ChatRole.ASSISTANT, first.text),
                    ChatMessage.text(ChatRole.USER, "Now add ten to it."),
                )
            } else {
                engine.chat(
                    listOf(ChatMessage.text(ChatRole.USER, "Say OK.")),
                    PARAMS.copy(maxTokens = 4),
                ).toList()
            }
            val before = DeviceState.read(coolFirst = true)
            val reply = engine.ask(SECOND_QUESTION)
            return TimedRow(reply, snapshotBytes, before, DeviceState.read(coolFirst = false))
        } finally {
            engine.unload()
            engine.close()
        }
    }

    private class TimedRow(
        val reply: Reply,
        val snapshotBytes: Long,
        val before: DeviceState,
        val after: DeviceState,
    )

    @Test
    fun conversationWarmExtendsWithoutTakingTheFreshSnapshot(): Unit = runBlocking {
        val models = EVAL_DIR.listFiles { file -> file.extension == "gguf" }.orEmpty()
        assumeTrue("no .gguf files in $EVAL_DIR", models.isNotEmpty())
        models.sortedBy { it.name }.forEach { checkConversationWarm(it) }
    }

    /**
     * The fold/branch/reopen path: after the head, a whole conversation is warmed with
     * `snapshot = false`. Three claims, per family:
     * 1. The conversation warm *extends* the head instead of re-reading it.
     * 2. The next question reuses the whole warmed conversation — which also proves the
     *    assistant-final render without a generation prompt is a byte prefix of the same
     *    conversation rendered with one, per template, on real weights.
     * 3. The fresh-chat snapshot survives: a new chat afterwards still restores (hybrid)
     *    or rolls back (transformer) to the head, rather than starting cold because a
     *    conversation displaced the snapshot.
     */
    private suspend fun checkConversationWarm(model: File) {
        Log.i(TAG, "conversation warm eval on ${model.name}")
        val engine = LlamaCppEngine()
        try {
            engine.load(model, ModelLoadParams(contextLength = CONTEXT))
            val head = engine.warm(listOf(system()), params = PARAMS)
            assertWithMessage("head warm on ${model.name}").that(head).isNotNull()
            if (head!!.warmedTokens + head.reusedTokens < 100) {
                Log.i(TAG, "${model.name}: template carries no warmable prefix, skipping")
                return
            }

            val conversation = listOf(
                system(),
                ChatMessage.text(ChatRole.USER, QUESTION),
                ChatMessage.text(ChatRole.ASSISTANT, CARRIED_REPLY),
            )
            val warmed = engine.warm(conversation, params = PARAMS, snapshot = false)
            assertWithMessage("conversation warm on ${model.name}").that(warmed).isNotNull()
            assertWithMessage("${model.name}: conversation warm re-read the head")
                .that(warmed!!.reusedTokens).isGreaterThan(200)
            assertWithMessage("${model.name}: conversation warm took the fresh snapshot")
                .that(warmed.snapshotBytes).isEqualTo(head.snapshotBytes)

            val followUp = engine.ask(
                QUESTION,
                ChatMessage.text(ChatRole.ASSISTANT, CARRIED_REPLY),
                ChatMessage.text(ChatRole.USER, "Now add ten to it."),
            )
            val warmedTotal = warmed.reusedTokens + warmed.warmedTokens
            assertWithMessage("${model.name}: the follow-up did not extend the warmed turns")
                .that(followUp.stats.cachedTokens).isAtLeast(warmedTotal - 8)
            Log.i(
                TAG,
                "${model.name}: conversation warmed=${warmed.warmedTokens} " +
                    "reused=${warmed.reusedTokens} in ${warmed.prefillMs}ms; " +
                    "follow-up cached=${followUp.stats.cachedTokens} " +
                    "ttft=${followUp.stats.timeToFirstTokenMs}ms",
            )

            val fresh = engine.ask(SECOND_QUESTION)
            assertWithMessage("${model.name}: the fresh chat after a conversation warm went cold")
                .that(fresh.stats.cachedTokens).isGreaterThan(200)
            assertWithMessage("${model.name}: fresh chat lost the answer")
                .that(fresh.text).contains("Paris")
            Log.i(
                TAG,
                "${model.name}: fresh-after-conversation cached=${fresh.stats.cachedTokens} " +
                    "ttft=${fresh.stats.timeToFirstTokenMs}ms",
            )
        } finally {
            engine.unload()
            engine.close()
        }
    }

    @Test
    fun cancelInterruptsAWarmMidPrefill(): Unit = runBlocking {
        val model = EVAL_DIR.listFiles { file -> file.extension == "gguf" }.orEmpty()
            .minByOrNull { it.name }
        assumeTrue("no .gguf files in $EVAL_DIR", model != null)

        // The incident this pins: a question queued behind a background warm for as long
        // as the warm took, because the interrupt was swallowed. The engine-level half of
        // the contract is here: a cancel landing mid-prefill ends the warm within a graph
        // node or a batch, the partial read is kept, and the next chat reuses it.
        val engine = LlamaCppEngine()
        try {
            engine.load(model!!, ModelLoadParams(contextLength = CONTEXT))
            val warm = async { engine.warm(listOf(system()), params = PARAMS) }
            delay(INTERRUPT_AFTER_MS)
            val asked = System.currentTimeMillis()
            engine.cancel()
            val result = warm.await()
            val interruptMs = System.currentTimeMillis() - asked
            assertWithMessage("${model.name}: cancelled warm returned nothing")
                .that(result).isNotNull()
            assertWithMessage("${model.name}: the warm outlived its cancel by ${interruptMs}ms")
                .that(interruptMs).isLessThan(MAX_INTERRUPT_MS)

            val reply = engine.ask(QUESTION)
            assertThat(reply.text).isNotEmpty()
            assertWithMessage("${model.name}: the turn re-read what the warm had kept")
                .that(reply.stats.cachedTokens).isAtLeast(result!!.warmedTokens)
            Log.i(
                TAG,
                "${model.name}: warm interrupted after ${result.warmedTokens} tokens in " +
                    "${interruptMs}ms; turn cached=${reply.stats.cachedTokens} " +
                    "ttft=${reply.stats.timeToFirstTokenMs}ms",
            )
        } finally {
            engine.unload()
            engine.close()
        }
    }

    private class Reply(
        val text: String,
        val stats: io.github.alpharomercoma.openweights.core.engine.GenerationStats,
        val reason: StopReason? = null,
    )

    /** One greedy turn: the system prefix, the question, and whatever else extends it. */
    private suspend fun LlamaCppEngine.ask(question: String, vararg tail: ChatMessage): Reply {
        val messages = listOf(system(), ChatMessage.text(ChatRole.USER, question)) + tail
        val events = chat(messages, PARAMS).toList()
        val done = events.filterIsInstance<GenerationEvent.Completed>().single()
        val raw = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
        return Reply(raw.ifEmpty { done.content }, done.stats)
    }

    private companion object {
        const val TAG = "WarmPrefixEval"
        const val RESTORED = "restored"
        const val COLD = "cold"
        const val DEFAULT_PAIRS = 6

        /** A restored head is the 1,018-token prefix; anything this small restored nothing. */
        const val MIN_RESTORED = 100

        /** A transformer may keep the BOS and a role opener across the unrelated first turn. */
        const val MAX_COLD_REUSE = 8
        const val CONTEXT = 4096

        /** Deep enough into the prefill to be mid-batch, well short of a full warm. */
        const val INTERRUPT_AFTER_MS = 400L

        /** ggml polls the abort between graph nodes; whole seconds would mean it did not. */
        const val MAX_INTERRUPT_MS = 3_000L
        val EVAL_DIR = File("/data/local/tmp/openweights/eval")

        val PARAMS = SamplerParams(temperature = 0f, topK = 1, seed = 7, maxTokens = 96)

        /**
         * A prefix shaped like the app's: instructions, then a tool-block-sized slab of
         * policy. Size matters more than content here — the reuse arithmetic only shows up
         * when the prefix dwarfs the question.
         */
        fun system(rules: Int = 40): ChatMessage {
            val policy = buildString {
                append("You are a careful assistant on a phone. Answer briefly.\n")
                repeat(rules) { index ->
                    append("Rule ").append(index)
                        .append(": prefer the shortest correct answer, cite nothing, ")
                        .append("never invent a tool, and keep lists to three items.\n")
                }
            }
            return ChatMessage.text(ChatRole.SYSTEM, policy)
        }

        const val QUESTION = "What is 2+2? Reply with just the number."

        const val WARM = "warm"
        const val COLDWARM_PAIRS = 8

        /** Turn one ends on its own marker well inside this; a cut turn one is not reused. */
        const val TURN_ONE_TOKENS = 1100

        /** As in the ExecuTorch test: enough of the reply to compare the arms token for token. */
        const val TIMED_TOKENS = 48
        const val ASK = "In one word, what did I just describe?"

        /** The text `ExecuTorchOnDeviceTest.LONG_PROMPT` sends, so both runtimes read the same. */
        fun longPrompt(sentences: Int = 40) = buildString {
            append("Here is a description of a system I am building. ")
            repeat(sentences) {
                append(
                    "It runs language models on a phone, entirely on device, with no " +
                        "network involved at any point in generation. ",
                )
            }
            append("Acknowledge briefly.")
        }

        /** A reply as it might have been decoded, carried into a branch or reopened chat. */
        const val CARRIED_REPLY = "4"
        const val SECOND_QUESTION = "What is the capital of France? One word."
    }
}
