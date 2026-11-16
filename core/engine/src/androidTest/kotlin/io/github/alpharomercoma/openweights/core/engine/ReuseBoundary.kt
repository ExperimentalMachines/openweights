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
import com.google.common.truth.Truth.assertWithMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import java.io.File

/**
 * What a second turn does after each way a first turn can end or change, against a reset.
 *
 * The talk claims an app has to know how a turn ended and what the runtime committed before it
 * reuses a cache. This puts each boundary in front of the engine on a real phone, through the
 * same [InferenceEngine] calls the app makes, so ExecuTorch and llama.cpp answer the same
 * questions. Four cases, each [REPEATS] times by default:
 * - `end`: turn one ends on its own marker; turn two extends it.
 * - `budget`: turn one is cut by a token budget ([CUT_TOKENS]).
 * - `cancel`: turn one is cancelled after [CANCEL_AFTER] streamed tokens.
 * - `edit`: turn one ends normally, and turn two's first user turn has one phrase changed in
 *   its middle, as a repaired or re-rendered history would.
 *
 * Each case's second turn is sent twice: once on whatever the case left in the cache, and once
 * after a reset, the order alternated so neither arm always runs warmer. The case arm run
 * second regenerates turn one first and records whether it came out the same, which needs
 * greedy decoding; a cancelled turn one can stop a token later and is recorded as different
 * rather than refused. Rows record how turn one ended, what the engine held, what each arm read
 * and kept, and both replies, and go to [sink]. What the runtime itself decided (extend,
 * truncate, restore, reset) is in its own log lines, which the rows do not repeat.
 */
internal class ReuseBoundary(
    private val engine: InferenceEngine,
    private val runtime: String,
    private val model: String,
    private val sink: File,
    private val prompt: String,
    private val opening: SamplerParams,
    private val timed: SamplerParams,
    /** For an engine that can say whether its record extends the next render; empty if not. */
    private val recordNote: (List<ChatMessage>, SamplerParams) -> String = { _, _ -> "" },
) {

    /** What the run saw, judged after every row is written. */
    class Outcome {
        var endReused = 0
        var resetReused = 0
        var rewritten = 0
        var unexercised = 0

        fun check(model: String) {
            // A first turn that ended some other way than its case names (a reply short enough
            // to finish inside the budget, a cancel that landed after the end) would put a row
            // under the wrong label; the rows record it, and the run fails.
            assertWithMessage("$model: a turn one did not end the way its case names")
                .that(unexercised).isEqualTo(0)
            assertWithMessage("$model: no second turn after a clean end reused the first")
                .that(endReused).isAtLeast(1)
            assertWithMessage("$model: a reset arm reused a cache")
                .that(resetReused).isEqualTo(0)
            assertWithMessage("$model: a deterministic turn one came out different")
                .that(rewritten).isEqualTo(0)
        }
    }

    suspend fun run(repeats: Int): Outcome {
        sink.writeText("")
        val outcome = Outcome()
        for (case in CASES) {
            repeat(repeats) { rep -> runCase(case, rep, outcome) }
        }
        Log.i(TAG, "boundary $model done: ${sink.absolutePath}")
        return outcome
    }

    private suspend fun runCase(case: String, rep: Int, outcome: Outcome) {
        engine.resetContext()
        val one = turnOne(case)
        if (one.done.reason != expected(case)) outcome.unexercised++
        val history = if (case == EDIT) edited(prompt) else prompt
        val sent = listOf(
            ChatMessage.text(ChatRole.USER, history),
            ChatMessage.text(ChatRole.ASSISTANT, one.raw.ifEmpty { "Understood." }),
            ChatMessage.text(ChatRole.USER, ASK),
        )
        Log.i(
            TAG,
            "boundary $model case=$case rep=$rep turn one: reason=${one.done.reason} " +
                "generated=${one.stats.generatedTokens} held=${one.stats.contextUsed}",
        )
        val order = if (rep % 2 == 0) listOf(case, RESET) else listOf(RESET, case)
        val replies = mutableMapOf<String, String>()
        for (arm in order) {
            var sameTurnOne = true
            var again: Turn? = null
            var note = ""
            if (arm == RESET) {
                engine.resetContext()
            } else {
                if (order.first() == RESET) {
                    engine.resetContext()
                    val rewrite = turnOne(case)
                    again = rewrite
                    if (rewrite.done.reason != expected(case)) outcome.unexercised++
                    // Text, reason and what the engine holds: equal text alone does not say the
                    // cache is the one turn two's history describes, around a cancel least of all.
                    sameTurnOne = rewrite.raw == one.raw &&
                        rewrite.done.reason == one.done.reason &&
                        rewrite.stats.contextUsed == one.stats.contextUsed
                    // A cancel can land a token apart from one run to the next; that is recorded
                    // on the row and the pair is left out of comparisons, not counted as a failure.
                    if (!sameTurnOne && case != CANCEL) outcome.rewritten++
                }
                note = recordNote(sent, timed)
            }
            val before = DeviceState.read(coolFirst = true)
            val two = turn(sent, timed)
            val after = DeviceState.read(coolFirst = false)
            val s = two.stats
            replies[arm] = two.raw
            if (arm == RESET && s.cachedTokens > MAX_RESET_REUSE) outcome.resetReused++
            if (arm == END && s.cachedTokens > MAX_RESET_REUSE) outcome.endReused++
            val fields = listOf(
                "runtime" to jsonString(runtime),
                "model" to jsonString(model),
                "case" to jsonString(case),
                "rep" to "$rep",
                "arm" to jsonString(arm),
                "order" to jsonString(order.joinToString("-")),
                "same_turn_one" to "$sameTurnOne",
                "exercised" to "${one.done.reason == expected(case)}",
                "regenerated_reason" to jsonString(again?.let { "${it.done.reason}" } ?: ""),
                "regenerated_tokens" to "${again?.stats?.generatedTokens ?: -1}",
                "regenerated_held" to "${again?.stats?.contextUsed ?: -1}",
                "turn_one_reason" to jsonString("${one.done.reason}"),
                "turn_one_tokens" to "${one.stats.generatedTokens}",
                "turn_one_held" to "${one.stats.contextUsed}",
                "turn_one" to jsonString(one.raw),
                "record" to jsonString(note),
                "prompt_tokens" to "${s.promptTokens}",
                "cached_tokens" to "${s.cachedTokens}",
                "prefill_ms" to "${s.prefillMs}",
                "ttft_ms" to "${s.timeToFirstTokenMs}",
                "generated_tokens" to "${s.generatedTokens}",
                "held_after" to "${s.contextUsed}",
                "reason" to jsonString("${two.done.reason}"),
                "cpu_c_before" to "${before.cpuC}",
                "cpu_c_after" to "${after.cpuC}",
                "battery_c" to "${before.batteryC}",
                "screen_on" to "${before.screenOn}",
                "cooled_s" to "${before.waitedS}",
                "reply" to jsonString(two.raw),
            )
            sink.appendText(fields.joinToString(", ", "{", "}") { (k, v) -> "\"$k\": $v" } + "\n")
            Log.i(
                TAG,
                "boundary $model case=$case rep=$rep arm=$arm read=${s.promptTokens} " +
                    "kept=${s.cachedTokens} prefill=${s.prefillMs}ms record=$note",
            )
        }
        Log.i(
            TAG,
            "boundary $model case=$case rep=$rep same reply=${replies[case] == replies[RESET]}",
        )
    }

    /** How a turn one under [case] has to end for its rows to mean what their label says. */
    private fun expected(case: String): StopReason = when (case) {
        BUDGET -> StopReason.MAX_TOKENS
        CANCEL -> StopReason.CANCELLED
        else -> StopReason.END_OF_TURN
    }

    private suspend fun turnOne(case: String): Turn {
        val first = listOf(ChatMessage.text(ChatRole.USER, prompt))
        return when (case) {
            BUDGET -> turn(first, opening.copy(maxTokens = CUT_TOKENS))
            CANCEL -> turn(first, opening, cancelAfter = CANCEL_AFTER)
            else -> turn(first, opening)
        }
    }

    /**
     * One turn, with the streamed text the app stores as history; cancelled after
     * [cancelAfter] streamed tokens when that is positive.
     */
    private suspend fun turn(
        messages: List<ChatMessage>,
        params: SamplerParams,
        cancelAfter: Int = 0,
    ): Turn {
        val text = StringBuilder()
        var seen = 0
        var done: GenerationEvent.Completed? = null
        engine.chat(messages, params).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    text.append(event.text)
                    if (cancelAfter > 0 && ++seen == cancelAfter) engine.cancel()
                }
                is GenerationEvent.Completed -> done = event
                else -> Unit
            }
        }
        val completed = checkNotNull(done) { "$model: a turn ended without a Completed event" }
        return Turn(completed, text.toString().ifEmpty { completed.content })
    }

    private class Turn(val done: GenerationEvent.Completed, val raw: String) {
        val stats get() = done.stats
    }

    companion object {
        const val TAG = "ReuseBoundary"
        const val REPEATS = 4
        const val END = "end"
        const val BUDGET = "budget"
        const val CANCEL = "cancel"
        const val EDIT = "edit"
        const val RESET = "reset"
        val CASES = listOf(END, BUDGET, CANCEL, EDIT)

        /** Shorter than any first reply measured here (12 tokens, Llama 3.2 1B, the shortest). */
        const val CUT_TOKENS = 8

        /**
         * Cancel is asked on the fourth streamed fragment; the runtime may finish a token more
         * (ExecuTorch reported 5 generated, llama.cpp 4).
         */
        const val CANCEL_AFTER = 4

        /** Long enough that two replies can part somewhere other than the first word. */
        const val TIMED_TOKENS = 64
        const val ASK =
            "In two sentences, what did I describe, and what is one risk of building it?"

        /** A transformer may keep a BOS across a reset; anything more is reuse. */
        const val MAX_RESET_REUSE = 2
        const val EDIT_FROM = "entirely on device"
        const val EDIT_TO = "mostly on device"

        /** [prompt] with the middle [EDIT_FROM] changed, so the edit lands mid-turn. */
        fun edited(prompt: String): String {
            val parts = prompt.split(EDIT_FROM)
            if (parts.size < 2) return "$prompt Edited."
            val middle = parts.size / 2
            return parts.withIndex().joinToString("") { (i, part) ->
                when {
                    i == 0 -> part
                    i == middle -> EDIT_TO + part
                    else -> EDIT_FROM + part
                }
            }
        }
    }
}
