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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What a model really costs in memory at its peak, and what happens to it when the phone
 * runs short.
 *
 * Two questions the fit verdict answers by arithmetic and nothing had measured. The first is
 * the peak: weights plus cache plus a flat 450 MB is a steady-state figure, and a load that
 * reads a file into one buffer and repacks it into another can pass through more than that on
 * the way. The second is what the phone does when the model no longer fits in what is free:
 * Android compresses the coldest pages into zram rather than failing, so the model keeps
 * answering and the only outward sign is that it got slower. Whether that slowdown can be
 * seen from inside, and at what rate of pages coming back it starts to cost anything, is the
 * threshold any warning in the app has to be built on.
 *
 * The pressure comes from outside this process: `tools/memory/run_pressure.sh` starts a
 * balloon under the shell that holds a given number of megabytes of incompressible memory
 * and keeps touching it, as an app in active use would, and names the level with
 * `-e label`. Between turns the test sits idle for `-e gap` seconds, because that is when a
 * real conversation's weights go cold and get compressed: while the person reads the answer.
 *
 * Every figure is read from the kernel's own accounting of this process (`/proc/self`), which
 * an ordinary app can read, and from `/proc/meminfo`, which it can too. The system-wide swap
 * counters (`/proc/vmstat`, `/proc/pressure/memory`) are denied to apps on this ROM, so the
 * test leaves them out: a signal the app cannot read is no basis for a warning it shows.
 */
@RunWith(AndroidJUnit4::class)
class MemoryPressureBenchmark {
    private val arguments = InstrumentationRegistry.getArguments()
    private val directory = File("/data/local/tmp/openweights")
    private val models = (arguments.getString("models") ?: "model.gguf")
        .split(',').map { File(directory, it.trim()) }
    private val label = arguments.getString("label") ?: "none"
    private val context = arguments.getString("context")?.toIntOrNull() ?: DEFAULT_CONTEXT
    private val fill = arguments.getString("fill")?.toIntOrNull() ?: DEFAULT_FILL
    private val turns = arguments.getString("turns")?.toIntOrNull() ?: DEFAULT_TURNS
    private val gapMs = (arguments.getString("gap")?.toLongOrNull() ?: DEFAULT_GAP_S) * MILLIS
    private val answerTokens = arguments.getString("answer")?.toIntOrNull() ?: ANSWER_TOKENS

    /**
     * Seconds before a turn to bring the weights back, as typing does in the app, or none.
     * Given, odd turns prefetch and even ones do not, so both arms meet the same phone in the
     * same minutes rather than in two runs that drift apart; every turn starts from the same
     * state, since the turn before it read every weight back in.
     */
    private val leadMs = arguments.getString("lead")?.toLongOrNull()?.times(MILLIS)?.also {
        // Inside the gap, or the prefetching arm would idle longer than the other.
        require(it in 0..gapMs) { "lead must be between 0 and the gap, got ${it / MILLIS} s" }
    }

    @Test
    fun measuresPeakMemoryAndPressure() = runBlocking {
        val present = models.filter { it.isFile }
        assumeTrue("none of $models pushed", present.isNotEmpty())
        for (model in present) {
            runCatching { measure(model) }.onFailure {
                // A load the phone refuses, or a process the low memory killer takes, is a
                // result for that level rather than a broken run; the report says which.
                Log.e(TAG, "${model.name} label=$label failed: ${it.message}", it)
                write(
                    model,
                    """{"model": "${model.name}", "label": "$label", "error": ${quote(
                        it.message,
                    )}}""",
                )
            }
        }
    }

    private suspend fun measure(model: File) {
        val engine: InferenceEngine = if (model.name.endsWith(".pte")) {
            ExecuTorchEngine(NativeExecuTorchBridge())
        } else {
            LlamaCppEngine()
        }
        engine.use {
            val idle = Snapshot.read()
            val sampler = Sampler().also { it.start() }
            val loadStarted = System.nanoTime()
            engine.load(model, ModelLoadParams(contextLength = context))
            val loadMs = (System.nanoTime() - loadStarted) / NANOS_PER_MILLI
            val loadPeak = sampler.finish()
            // Settled, because a load's transient buffers are freed on the way out and the
            // figure a verdict promises is the one that stays.
            delay(SETTLE_MS)
            val loaded = Snapshot.read()
            Log.i(
                TAG,
                "${model.name} label=$label load ${loadMs}ms " +
                    "peak ${loadPeak.peakFootprintKb / KIB}MB " +
                    "settled rss ${loaded.rssKb / KIB}MB " +
                    "swap ${loaded.swapKb / KIB}MB hwm ${loaded.hwmKb / KIB}MB",
            )

            // A turn the model refuses (a prompt past a compiled window) must not take the load
            // measurements with it: those are half of what this is for.
            val rows = mutableListOf<String>()
            fun report(turnError: Throwable?) = write(
                model,
                buildString {
                    append("{\"model\": \"${model.name}\", \"label\": \"$label\", ")
                    append(
                        "\"file_bytes\": ${model.length()}, \"context\": $context, \"fill\": $fill, ",
                    )
                    append("\"gap_s\": ${gapMs / MILLIS}, \"lead_s\": ${leadMs?.div(MILLIS)}, ")
                    append("\"load_ms\": $loadMs, ")
                    append("\"idle\": ${idle.json()}, \"load_peak\": ${loadPeak.json()}, ")
                    append(
                        "\"loaded\": ${loaded.json()}, \"turn_error\": ${turnError?.let {
                            quote(it.message)
                        } ?: "null"},\n \"turns\": [\n  ",
                    )
                    append(rows.joinToString(",\n  "))
                    append("\n]}\n")
                },
            )
            val turnError = runCatching {
                // One short turn first, so the rows below measure throughput rather than the
                // first call's setup.
                engine.turn("Say hello.", WARM_UP_TOKENS)
                // Each reset right after a turn, never just before one: it zeroes the whole KV
                // cache (llama_memory_clear with data), which under pressure drags the unused
                // cache back in and pushes the weights out. Done before Send, it made every
                // turn page whatever the gap had done (2026-10-23, 4 GB: nothing out through
                // the gap, 640 to 1,400 MB out at Send, in both arms).
                engine.resetContext()
                // A scope for the prefetch each gap may start, which its Send always ends.
                coroutineScope {
                    repeat(turns) { index ->
                        val odd = index % 2 == 1
                        val pending = if (index == 0) null else gap(engine, prefetching = odd)
                        // Send. Everything from here to the first token is what the user waits for,
                        // the end of a prefetch still running included.
                        val sentAt = System.nanoTime()
                        val prefetch = pending?.stop()
                        val outAtSend = (engine as? LlamaCppEngine)?.weightsOut()
                        val before = Snapshot.read()
                        val turnSampler = Sampler().also { it.start() }
                        val started = System.nanoTime()
                        val (stats, firstTokenAt) = try {
                            engine.timedTurn(filler(fill), answerTokens)
                        } finally {
                            turnSampler.finish()
                        }
                        val wallMs = (System.nanoTime() - started) / NANOS_PER_MILLI
                        val row = TurnRow(
                            index,
                            wallMs,
                            stats,
                            before,
                            Snapshot.read(),
                            turnSampler.peak(),
                            prefetch,
                            outAtSend,
                            firstTokenAt?.let { (it - sentAt) / NANOS_PER_MILLI },
                        )
                        Log.i(TAG, "${model.name} label=$label ${row.line()}")
                        rows += row.json()
                        // Each turn as it lands, before the reset that presses on memory: a
                        // process the low memory killer takes mid-run keeps the turns it finished.
                        report(null)
                        engine.resetContext()
                    }
                }
            }.exceptionOrNull()
            turnError?.let {
                Log.e(TAG, "${model.name} label=$label turn failed: ${it.message}", it)
            }
            report(turnError)
        }
    }

    /**
     * The idle time between two turns, while the reply would be read; the same length in both
     * arms. With [prefetching] and a lead, the weights start coming back that long before the
     * next turn, and [Pending.stop] at Send cancels and joins whatever is still running, as
     * sending does in the app.
     */
    private suspend fun CoroutineScope.gap(
        engine: InferenceEngine,
        prefetching: Boolean,
    ): Pending? {
        val lead = leadMs
        if (!prefetching || lead == null) {
            delay(gapMs)
            return null
        }
        delay(gapMs - lead)
        val before = Snapshot.read()
        val sampler = Sampler().also { it.start() }
        val startedAt = System.nanoTime()
        val tally = Tally()
        // The app's policy while someone types (TurnRunner.prefetchWeights): look again two
        // seconds after a look that found the weights in, ten after one that brought them
        // back. One look at the start of the window missed weights pushed out in its last
        // seconds (2026-10-23, 3 GB balloon, turn 5: nothing out ten seconds before, 452k
        // faults after Send).
        val job = launch(Dispatchers.Default) {
            while (true) {
                val result = engine.prefetchWeights() ?: break
                tally.add(result)
                Log.i(
                    TAG,
                    "prefetch at ${(System.nanoTime() - startedAt) / NANOS_PER_MILLI}ms: $result",
                )
                delay(if (result.broughtBack) APP_PREFETCH_INTERVAL_MS else APP_PREFETCH_RECHECK_MS)
            }
        }
        try {
            delay(lead)
        } catch (cancelled: CancellationException) {
            job.cancel()
            sampler.finish()
            throw cancelled
        }
        return Pending(job, tally, before, sampler, startedAt)
    }

    /** Every look one gap's prefetching took, as they land. */
    private class Tally {
        @Volatile var looks = 0

        @Volatile var broughtBackBytes = 0L

        @Volatile var unknownLooks = 0

        @Volatile var last: WeightPrefetch? = null

        @Synchronized
        fun add(result: WeightPrefetch) {
            looks++
            val after = result.outAfter
            // A pass whose end could not be read is not counted as having brought it all back.
            if (result.broughtBack && after != null) broughtBackBytes += result.outBefore - after
            if (result.broughtBack && after == null) unknownLooks++
            last = result
        }
    }

    /** The prefetching started in a gap, until the turn after it is sent. */
    private class Pending(
        val job: Job,
        val tally: Tally,
        val before: Snapshot,
        val sampler: Sampler,
        val startedAt: Long,
    ) {
        /** Cancels it and waits out the step in flight; then says what it did. */
        suspend fun stop(): PrefetchRow {
            val joinStarted = System.nanoTime()
            job.cancelAndJoin()
            val joinMs = (System.nanoTime() - joinStarted) / NANOS_PER_MILLI
            // Halted, not joined: the sampler sleeps between reads, and waiting it out here
            // would add up to a sample's interval to this arm's time to first token.
            val peak = sampler.halt()
            val after = Snapshot.read()
            return PrefetchRow(
                looks = tally.looks,
                broughtBackBytes = tally.broughtBackBytes,
                unknownLooks = tally.unknownLooks,
                last = tally.last,
                joinMs = joinMs,
                windowMs = (joinStarted - startedAt) / NANOS_PER_MILLI,
                majorFaults = after.majorFaults - before.majorFaults,
                cpuMs = after.cpuMs - before.cpuMs,
                minAvailableKb = peak.minAvailableKb,
            )
        }
    }

    /**
     * What one prefetch did, and what the process did over the window it ran in: its faults,
     * its CPU and the least memory the system had free, which belong to the prefetch's cost
     * rather than to the turn after it.
     */
    private class PrefetchRow(
        val looks: Int,
        val broughtBackBytes: Long,
        val unknownLooks: Int,
        val last: WeightPrefetch?,
        val joinMs: Long,
        val windowMs: Long,
        val majorFaults: Long,
        val cpuMs: Long,
        val minAvailableKb: Long,
    ) {
        fun json() =
            "{\"looks\": $looks, \"brought_back_mb\": ${broughtBackBytes / (KIB * KIB)}, " +
                "\"unknown_looks\": $unknownLooks, " +
                "\"last\": ${last?.let { "\"$it\"" }}, \"join_ms\": $joinMs, " +
                "\"window_ms\": $windowMs, \"major_faults\": $majorFaults, \"cpu_ms\": $cpuMs, " +
                "\"min_available_kb\": $minAvailableKb}"
    }

    private fun write(model: File, json: String) {
        val out = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("eval-results")!!
        out.mkdirs()
        File(out, "${model.nameWithoutExtension}.$label.mem.json").writeText(json)
    }

    /** Roughly [tokens] tokens of ordinary prose, since that is what a conversation is. */
    private fun filler(tokens: Int): String = buildString {
        append("Here are some notes. Answer the question at the end in two sentences.\n")
        repeat(tokens / TOKENS_PER_BLOCK) {
            append("Ada Lovelace worked with Charles Babbage on the Analytical Engine. ")
        }
        append("\nQuestion: who was Ada Lovelace, and what did she write about the engine?")
    }

    private suspend fun InferenceEngine.turn(prompt: String, maxTokens: Int): GenerationStats =
        timedTurn(prompt, maxTokens).first

    /** [turn], with when the first token reached this side, on [System.nanoTime]. */
    private suspend fun InferenceEngine.timedTurn(
        prompt: String,
        maxTokens: Int,
    ): Pair<GenerationStats, Long?> {
        var firstTokenAt: Long? = null
        val stats = chat(
            messages = listOf(ChatMessage.text(ChatRole.USER, prompt)),
            params = SamplerParams(
                temperature = 0f,
                maxTokens = maxTokens,
                seed = 1,
                thinking = false,
            ),
        ).onEach {
            if (it is GenerationEvent.Token &&
                firstTokenAt == null
            ) {
                firstTokenAt = System.nanoTime()
            }
        }
            .toList().filterIsInstance<GenerationEvent.Completed>().single().stats
        return stats to firstTokenAt
    }

    private class TurnRow(
        val index: Int,
        val wallMs: Long,
        val stats: GenerationStats,
        val before: Snapshot,
        val after: Snapshot,
        val peak: Peak,
        val prefetch: PrefetchRow?,
        val outAtSend: Long?,
        val sendToFirstTokenMs: Long?,
    ) {
        val majorFaults get() = after.majorFaults - before.majorFaults
        val cpuMs get() = after.cpuMs - before.cpuMs
        val decodeRate get() = stats.generatedTokens * MILLIS_D / stats.decodeMs.coerceAtLeast(1)
        val prefillRate get() = stats.promptTokens * MILLIS_D / stats.prefillMs.coerceAtLeast(1)

        fun line() = (
            "turn %d wall %dms prefill %.1f t/s decode %.2f t/s majflt %d swap %d->%dMB " +
                "rss %d->%dMB peak %dMB cpu/wall %.2f avail min %dMB paging %s " +
                "prefetch %s out at send %sMB send to first token %sms"
            ).format(
            index, wallMs, prefillRate, decodeRate, majorFaults,
            before.swapKb / KIB, after.swapKb / KIB, before.rssKb / KIB, after.rssKb / KIB,
            peak.peakFootprintKb / KIB, cpuMs.toDouble() / wallMs.coerceAtLeast(1),
            peak.minAvailableKb / KIB, stats.memory?.pagingDetected,
            prefetch?.json() ?: "none", outAtSend?.div(KIB * KIB), sendToFirstTokenMs,
        )

        fun json() = buildString {
            append("{\"turn\": $index, \"wall_ms\": $wallMs, ")
            append(
                "\"prompt_tokens\": ${stats.promptTokens}, \"generated_tokens\": ${stats.generatedTokens}, ",
            )
            append("\"prefill_ms\": ${stats.prefillMs}, \"decode_ms\": ${stats.decodeMs}, ")
            append("\"ttft_ms\": ${stats.timeToFirstTokenMs}, ")
            append(
                "\"major_faults\": $majorFaults, \"minor_faults\": ${after.minorFaults - before.minorFaults}, ",
            )
            append("\"cpu_ms\": $cpuMs, \"before\": ${before.json()}, \"after\": ${after.json()}, ")
            // What the engine's own meter concluded, beside the test's independent readings.
            stats.memory?.let {
                append(
                    "\"engine_memory\": {\"paging\": ${it.pagingDetected}, \"cold\": ${it.cold}, ",
                )
                append(
                    "\"peak_bytes\": ${it.peakFootprintBytes}, \"major_faults\": ${it.majorFaults}}, ",
                )
            }
            append("\"prefetch\": ${prefetch?.json() ?: "null"}, ")
            append("\"out_at_send_mb\": ${outAtSend?.div(KIB * KIB)}, ")
            append("\"send_to_first_token_ms\": $sendToFirstTokenMs, ")
            append("\"peak\": ${peak.json()}}")
        }
    }

    /**
     * One reading of this process and of the system, every field a level or a running total.
     *
     * `/proc/self/status` rather than `smaps_rollup` for the sizes, because the sampler reads
     * this ten times a second and `smaps_rollup` walks every mapping to answer; the two agree on
     * `VmSwap` and `Swap`. `VmHWM` is the kernel's own high-water mark of the resident set.
     */
    private data class Snapshot(
        val rssKb: Long,
        val swapKb: Long,
        val hwmKb: Long,
        val majorFaults: Long,
        val minorFaults: Long,
        val cpuMs: Long,
        val availableKb: Long,
        val swapFreeKb: Long,
        val swapTotalKb: Long,
    ) {
        fun json() = "{\"rss_kb\": $rssKb, \"swap_kb\": $swapKb, \"hwm_kb\": $hwmKb, " +
            "\"majflt\": $majorFaults, \"minflt\": $minorFaults, \"cpu_ms\": $cpuMs, " +
            "\"mem_available_kb\": $availableKb, \"swap_free_kb\": $swapFreeKb, \"swap_total_kb\": $swapTotalKb}"

        companion object {
            fun read(): Snapshot {
                val status = fields(File("/proc/self/status"))
                val meminfo = fields(File("/proc/meminfo"))
                // After the last ')' because the command name may itself hold spaces; the
                // fields from there are state, ppid, ..., with minflt the 8th, majflt the
                // 10th, utime the 12th and stime the 13th.
                val stat = File(
                    "/proc/self/stat",
                ).readText().substringAfterLast(')').trim().split(' ')
                val ticks = 100L
                return Snapshot(
                    rssKb = status["VmRSS"] ?: -1,
                    swapKb = status["VmSwap"] ?: -1,
                    hwmKb = status["VmHWM"] ?: -1,
                    minorFaults = stat[7].toLong(),
                    majorFaults = stat[9].toLong(),
                    cpuMs = (stat[11].toLong() + stat[12].toLong()) * MILLIS / ticks,
                    availableKb = meminfo["MemAvailable"] ?: -1,
                    swapFreeKb = meminfo["SwapFree"] ?: -1,
                    swapTotalKb = meminfo["SwapTotal"] ?: -1,
                )
            }

            private fun fields(file: File): Map<String, Long> =
                file.readLines().mapNotNull { line ->
                    val name = line.substringBefore(':', "")
                    val value = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                    if (name.isEmpty() || value == null) null else name to value
                }.toMap()
        }
    }

    private data class Peak(
        val peakFootprintKb: Long,
        val peakRssKb: Long,
        val minAvailableKb: Long,
        val samples: Int,
    ) {
        fun json() = "{\"footprint_kb\": $peakFootprintKb, \"rss_kb\": $peakRssKb, " +
            "\"min_available_kb\": $minAvailableKb, \"samples\": $samples}"
    }

    /**
     * The peak between [start] and [finish], sampled on its own thread because both engines
     * block the caller for the whole of a load or a turn.
     *
     * The footprint is resident plus swapped: what the process holds whether or not the
     * kernel has compressed some of it away, which is the figure a fit has to cover.
     */
    private class Sampler : Thread("memory-sampler") {
        @Volatile private var running = true
        private var peakFootprint = 0L
        private var peakRss = 0L
        private var minAvailable = Long.MAX_VALUE
        private var samples = 0

        override fun run() {
            while (running) {
                val snapshot = Snapshot.read()
                peakFootprint = maxOf(peakFootprint, snapshot.rssKb + snapshot.swapKb)
                peakRss = maxOf(peakRss, snapshot.rssKb)
                minAvailable = minOf(minAvailable, snapshot.availableKb)
                samples++
                sleep(SAMPLE_MS)
            }
        }

        fun finish(): Peak {
            running = false
            join()
            return peak()
        }

        /** Stops it without waiting for its last sleep; the peak so far. */
        fun halt(): Peak {
            running = false
            return peak()
        }

        fun peak() = Peak(peakFootprint, peakRss, minAvailable, samples)
    }

    private companion object {
        const val TAG = "OpenWeightsMemory"
        const val KIB = 1024L
        const val MILLIS = 1000L
        const val MILLIS_D = 1000.0
        const val NANOS_PER_MILLI = 1_000_000L
        const val SAMPLE_MS = 100L
        const val SETTLE_MS = 2_000L
        const val WARM_UP_TOKENS = 8
        const val ANSWER_TOKENS = 128
        const val DEFAULT_CONTEXT = 8_192
        const val DEFAULT_FILL = 2_048
        const val DEFAULT_TURNS = 3
        const val DEFAULT_GAP_S = 20L

        /** TurnRunner's PREFETCH_INTERVAL_MS and PREFETCH_RECHECK_MS, which :app keeps private. */
        const val APP_PREFETCH_INTERVAL_MS = 10_000L
        const val APP_PREFETCH_RECHECK_MS = 2_000L

        /** The filler sentence is about twelve tokens, so this is close enough to aim with. */
        const val TOKENS_PER_BLOCK = 12

        fun quote(text: String?) =
            "\"" + (text ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
