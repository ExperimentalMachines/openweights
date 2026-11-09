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
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Sweeps thread counts on the device under test and logs throughput for each.
 *
 * This is a measurement, not an assertion: phones vary enormously in how many threads help
 * before the little cores start holding everyone up. Run it on a new device, read the log,
 * and record the result in `docs/CONTEXT.md`.
 *
 * Instrumentation arguments narrow it for a run driven from outside, as the Snapdragon
 * throttle study does with the CPU pause set by hand (`docs/research/snapdragon-pd-matrix.md`):
 * `threads` (a comma list), `maxTokens` and `model`. With `threads` given, prompts are read
 * with the same count, as `ThermalPolicy` plans them in the app. Every live progress event is
 * logged with the time since the run started, so a run stopped before it ends keeps its last
 * count and when it was reached.
 */
@RunWith(AndroidJUnit4::class)
class ThreadCountBenchmark {
    @Test
    fun sweepThreadCounts() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val model = args.getString("model")?.let(::File) ?: MODEL
        // A run that names its model must not pass as a skip when the file is missing.
        if (args.getString("model") != null) {
            check(model.isFile) { "no model at ${model.path}" }
        } else {
            assumeTrue("no test model at ${model.path}", model.isFile)
        }

        val cores = Runtime.getRuntime().availableProcessors()
        Log.i(TAG, "device reports $cores cores")

        val asked = args.getString("threads")?.split(',')?.map { it.trim().toInt() }
        val maxTokens = args.getString("maxTokens")?.toInt() ?: DEFAULT_MAX_TOKENS
        for (threads in asked ?: THREAD_COUNTS.filter { it <= cores }) {
            val engine = LlamaCppEngine()
            engine.load(
                model,
                ModelLoadParams(
                    contextLength = CONTEXT_LENGTH,
                    threadCount = threads,
                    batchThreadCount = if (asked != null) threads else null,
                ),
            )
            Log.i(TAG, "START threads=$threads maxTokens=$maxTokens")

            val started = SystemClock.elapsedRealtime()
            val completed = engine.chat(
                messages = listOf(
                    ChatMessage.text(
                        ChatRole.USER,
                        "Write a short paragraph about tide pools. No preamble.",
                    ),
                ),
                params = SamplerParams(temperature = 0.2f, maxTokens = maxTokens, seed = 1),
            ).onEach { event ->
                if (event is GenerationEvent.Progress) {
                    val elapsed = SystemClock.elapsedRealtime() - started
                    Log.i(
                        TAG,
                        "PROGRESS threads=$threads elapsedMs=$elapsed reading=${event.reading} " +
                            "promptRead=${event.promptRead} prefillMs=${event.prefillMs} " +
                            "generated=${event.generated} decodeMs=${event.decodeMs}",
                    )
                }
            }.toList().filterIsInstance<GenerationEvent.Completed>().single()

            Log.i(
                TAG,
                "BENCH threads=%d prefill=%.1f tok/s decode=%.1f tok/s ttft=%d ms".format(
                    threads,
                    completed.stats.prefillTokensPerSecond ?: 0.0,
                    completed.stats.decodeTokensPerSecond ?: 0.0,
                    completed.stats.timeToFirstTokenMs,
                ),
            )
            engine.unload()
        }
    }

    private companion object {
        const val TAG = "OpenWeightsBench"
        const val CONTEXT_LENGTH = 2048
        const val DEFAULT_MAX_TOKENS = 64
        val THREAD_COUNTS = listOf(2, 3, 4, 5, 6, 8)
        val MODEL = File("/data/local/tmp/openweights/model.gguf")
    }
}
