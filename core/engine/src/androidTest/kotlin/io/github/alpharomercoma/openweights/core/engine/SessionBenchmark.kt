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

import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A chat session paced like a person: the study's conversations, turn by turn, each reply the
 * model's own and kept in the history, with a reading pause between turns. A measurement for the
 * Snapdragon throttle study (`docs/research/snapdragon-pd-matrix.md`): whether ordinary use heats a
 * phone to its hard throttle, which the phone-side logger records while this runs. Nothing is
 * asserted. Run only from outside, with instrumentation arguments:
 * `conversations` (the study's conversations.jsonl), `model`, `threads` (4, the app's default),
 * `maxTokens` (a cap on each reply), `thinkSeconds` (the pause before each next message),
 * `minutes` (when to stop), `contextLength` and `stallGuard` (0 turns the engine's fallback off).
 */
@RunWith(AndroidJUnit4::class)
class SessionBenchmark {
    @Test
    fun paceLikeAPerson() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val conversations = args.getString("conversations")?.let(::File)
        assumeTrue("no conversations given", conversations != null && conversations.isFile)
        val model = File(args.getString("model") ?: DEFAULT_MODEL)
        check(model.isFile) { "no model at ${model.path}" }
        val threads = args.getString("threads")?.toInt() ?: DEFAULT_THREADS
        val maxTokens = args.getString("maxTokens")?.toInt() ?: DEFAULT_MAX_TOKENS
        val thinkMs = (args.getString("thinkSeconds")?.toLong() ?: DEFAULT_THINK_SECONDS) * MS
        val budgetMs = (args.getString("minutes")?.toLong() ?: DEFAULT_MINUTES) * MINUTE_MS
        val contextLength = args.getString("contextLength")?.toInt() ?: DEFAULT_CONTEXT
        args.getString("stallGuard")?.let { Os.setenv("OPENWEIGHTS_STALL_GUARD", it, true) }
        Log.i(
            TAG,
            "SESSION threads=$threads maxTokens=$maxTokens thinkMs=$thinkMs budgetMs=$budgetMs " +
                "stallGuard=${args.getString("stallGuard") ?: "default"}",
        )

        val engine = LlamaCppEngine()
        engine.load(
            model,
            ModelLoadParams(
                contextLength = contextLength,
                threadCount = threads,
                batchThreadCount = threads,
            ),
        )
        val started = SystemClock.elapsedRealtime()
        var turn = 0
        for (line in conversations!!.readLines().filter { it.isNotBlank() }) {
            val conv = JSONObject(line)
            val id = conv.getString("id")
            val messages = conv.getJSONArray("messages")
            val history = mutableListOf<ChatMessage>()
            for (i in 0 until messages.length()) {
                val message = messages.getJSONObject(i)
                if (message.getString("role") != "user") continue
                if (SystemClock.elapsedRealtime() - started > budgetMs) break
                history += ChatMessage.text(ChatRole.USER, message.getString("content"))
                val at = SystemClock.elapsedRealtime() - started
                val events = engine.chat(
                    messages = history.toList(),
                    params = SamplerParams(temperature = 0f, maxTokens = maxTokens, seed = 1),
                ).toList()
                val reply = events
                    .filterIsInstance<GenerationEvent.Token>()
                    .joinToString("") { token -> token.text }
                val done = events.filterIsInstance<GenerationEvent.Completed>().singleOrNull()
                history += ChatMessage.text(ChatRole.ASSISTANT, reply)
                val stats = done?.stats
                val line = TURN_LINE.format(
                    turn,
                    id,
                    at,
                    stats?.promptTokens ?: 0,
                    stats?.prefillTokensPerSecond ?: 0.0,
                    stats?.decodeTokensPerSecond ?: 0.0,
                    stats?.timeToFirstTokenMs ?: 0L,
                    stats?.generatedTokens ?: 0,
                )
                Log.i(TAG, line)
                turn++
                delay(thinkMs)
            }
            if (SystemClock.elapsedRealtime() - started > budgetMs) break
            // The next conversation is a new history on the same loaded engine, as a new chat is in
            // the app; reloading would discard the session's stall guard state and add a load's heat.
        }
        Log.i(TAG, "SESSION done turns=$turn ms=${SystemClock.elapsedRealtime() - started}")
        engine.unload()
    }

    private companion object {
        const val TAG = "OpenWeightsBench"
        const val TURN_LINE =
            "TURN n=%d conv=%s atMs=%d prompt=%d prefill=%.1f decode=%.1f ttft=%d generated=%d"
        const val DEFAULT_MODEL = "/data/local/tmp/openweights/model.gguf"
        const val DEFAULT_THREADS = 4
        const val DEFAULT_MAX_TOKENS = 512
        const val DEFAULT_THINK_SECONDS = 30L
        const val DEFAULT_MINUTES = 40L
        const val DEFAULT_CONTEXT = 8192
        const val MS = 1000L
        const val MINUTE_MS = 60_000L
    }
}
