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
 * What the live readout costs, and whether it says what the finished figures say.
 *
 * The readout runs a ticker beside every pass: four reads of the session's progress and of
 * `/proc/self` a second, and an event when anything moved. That should cost nothing that
 * shows, and "should" is not a measurement, so this runs the same model twice, once with the
 * ticker and once without, turns interleaved and the order swapped every pair, so heat and
 * background noise fall on both sides alike.
 *
 *   CLASS=LiveReadoutOnDeviceTest MODELS=<file in /data/local/tmp/openweights>
 *   tools/memory/run_pressure.sh   (PAIRS and FILL pass through; LEVELS=0 by default)
 */
@RunWith(AndroidJUnit4::class)
class LiveReadoutOnDeviceTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val model = File(
        "/data/local/tmp/openweights",
        arguments.getString("models") ?: "LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf",
    )
    private val pairs = arguments.getString("pairs")?.toIntOrNull() ?: DEFAULT_PAIRS
    private val fill = arguments.getString("fill")?.toIntOrNull() ?: DEFAULT_FILL

    @Test
    fun readoutCostsNothingMeasurableAndEndsNearTheFinishedFigure() = runBlocking {
        assumeTrue("${model.name} not pushed", model.isFile)
        val with = engine(liveProgress = true)
        val without = engine(liveProgress = false)
        val rows = mutableListOf<String>()
        try {
            for (engine in listOf(with, without)) {
                engine.load(model, ModelLoadParams(contextLength = CONTEXT))
                engine.turn("Say hello.", WARM_UP_TOKENS)
            }
            repeat(pairs) { pair ->
                val order = if (pair % 2 == 0) listOf(with, without) else listOf(without, with)
                for (engine in order) {
                    val label = if (engine === with) "with" else "without"
                    engine.resetContext()
                    val row = engine.measured(label, pair)
                    Log.i(TAG, row.line)
                    rows += row.json
                }
            }
        } finally {
            with.unload()
            without.unload()
        }
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!
        out.mkdirs()
        File(out, "${model.nameWithoutExtension}.readout.json").writeText(
            "{\"model\": \"${model.name}\", \"fill\": $fill, \"rows\": [\n  " +
                rows.joinToString(",\n  ") + "\n]}\n",
        )
    }

    private class Row(val line: String, val json: String)

    private suspend fun InferenceEngine.measured(label: String, pair: Int): Row {
        val arrivals = mutableListOf<Long>()
        val events = chat(
            messages = listOf(ChatMessage.text(ChatRole.USER, filler(fill))),
            params = SamplerParams(
                temperature = 0f,
                maxTokens = ANSWER_TOKENS,
                seed = 1,
                thinking = false,
            ),
        ).onEach { if (it is GenerationEvent.Progress) arrivals += System.nanoTime() }.toList()
        val stats = events.filterIsInstance<GenerationEvent.Completed>().single().stats
        val last = events.filterIsInstance<GenerationEvent.Progress>().lastOrNull()
        val liveDecode = last?.decodeMs?.takeIf {
            it > 0
        }?.let { (last.generated - 1) * MILLIS / it }
        val livePrefill = last?.let { p ->
            p.promptRead?.let { read -> p.prefillMs?.takeIf { it > 0 }?.let { read * MILLIS / it } }
        }
        val spanS = if (arrivals.size >
            1
        ) {
            (arrivals.last() - arrivals.first()) / NANOS_PER_SECOND
        } else {
            0.0
        }
        val hz = if (spanS > 0) (arrivals.size - 1) / spanS else 0.0
        val line = (
            "%s pair %d prefill %.1f t/s decode %.2f t/s events %d (%.1f Hz) " +
                "live prefill %s decode %s paging %s cold %s peak %s MB"
            ).format(
            label, pair, stats.prefillTokensPerSecond ?: 0.0, stats.decodeTokensPerSecond ?: 0.0,
            arrivals.size, hz,
            livePrefill?.let {
                "%.1f".format(it)
            },
            liveDecode?.let { "%.2f".format(it) },
            stats.memory?.pagingDetected, stats.memory?.cold,
            stats.memory?.peakFootprintBytes?.div(
                MIB,
            ),
        )
        val json = "{\"label\": \"$label\", \"pair\": $pair, " +
            "\"prompt_tokens\": ${stats.promptTokens}, \"prefill_ms\": ${stats.prefillMs}, " +
            "\"generated_tokens\": ${stats.generatedTokens}, \"decode_ms\": ${stats.decodeMs}, " +
            "\"progress_events\": ${arrivals.size}, \"progress_hz\": $hz, " +
            "\"live_prefill_tps\": $livePrefill, \"live_decode_tps\": $liveDecode, " +
            "\"final_prefill_tps\": ${stats.prefillTokensPerSecond}, " +
            "\"final_decode_tps\": ${stats.decodeTokensPerSecond}, " +
            "\"paging\": ${stats.memory?.pagingDetected}, \"cold\": ${stats.memory?.cold}, " +
            "\"peak_bytes\": ${stats.memory?.peakFootprintBytes}, \"major_faults\": ${stats.memory?.majorFaults}}"
        return Row(line, json)
    }

    private fun engine(liveProgress: Boolean) = LlamaCppEngine(liveProgress = liveProgress)

    private suspend fun InferenceEngine.turn(prompt: String, maxTokens: Int) {
        chat(
            messages = listOf(ChatMessage.text(ChatRole.USER, prompt)),
            params = SamplerParams(
                temperature = 0f,
                maxTokens = maxTokens,
                seed = 1,
                thinking = false,
            ),
        ).toList()
    }

    /** Roughly [tokens] tokens of ordinary prose, as MemoryPressureBenchmark uses. */
    private fun filler(tokens: Int): String = buildString {
        append("Here are some notes. Answer the question at the end in two sentences.\n")
        repeat(tokens / TOKENS_PER_BLOCK) {
            append("Ada Lovelace worked with Charles Babbage on the Analytical Engine. ")
        }
        append("\nQuestion: who was Ada Lovelace, and what did she write about the engine?")
    }

    private companion object {
        const val TAG = "OpenWeightsReadout"
        const val CONTEXT = 8_192
        const val DEFAULT_PAIRS = 6
        const val DEFAULT_FILL = 1_024
        const val WARM_UP_TOKENS = 8
        const val ANSWER_TOKENS = 128
        const val TOKENS_PER_BLOCK = 12
        const val MILLIS = 1000.0
        const val NANOS_PER_SECOND = 1e9
        const val MIB = 1024L * 1024
    }
}
