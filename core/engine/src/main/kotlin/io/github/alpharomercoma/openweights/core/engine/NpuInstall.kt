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

import java.io.File

/**
 * The NPU half of a MediaTek install, kept beside the CPU build that decodes for it.
 *
 * A model `X.pte` with an NPU half has a folder `X.npu/` holding the compiled chunks, the
 * token embedding table, `parts.txt` (each part's name and expected size, chunks in the order
 * the runner takes them, the embedding last) and `runner.json` (the export's runner options,
 * verbatim). Discover writes the two lists when the install starts and the downloads fill the
 * parts in; the engine prefills on the NPU only once every listed part is there at its full
 * size, and reads with the CPU until then. A folder rather than more files beside the model, so
 * the model list, which shows top-level files, never offers a part.
 */
@Suppress("TooManyFunctions")
object NpuInstall {
    const val SUFFIX = ".npu"
    private const val PARTS = "parts.txt"
    private const val RUNNER = "runner.json"
    private const val EMBEDDING_TAG = "embedding"
    private const val CHUNK_TAG = "chunk"
    private const val OPENING = "opening"
    private const val REFUSED = "refused.txt"
    private const val TRIED = "tried"

    /** Tag, name and size. */
    private const val FIELDS = 3

    /** One file of the NPU half and the size it has when complete. */
    data class Part(val name: String, val sizeBytes: Long)

    /** A complete NPU half, ready for the runner. */
    data class Complete(val chunks: List<File>, val embedding: File, val runnerJson: String) {
        /** Every byte the NPU half maps, for the memory estimate. */
        val bytes: Long get() = chunks.sumOf { it.length() } + embedding.length()
    }

    /** Where [model]'s NPU half lives, whether or not it has one. */
    fun directoryFor(model: File): File =
        File(model.parentFile, model.nameWithoutExtension + SUFFIX)

    /** Records what [model]'s NPU half will be, before its parts arrive. */
    fun write(model: File, chunks: List<Part>, embedding: Part, runnerJson: String): File {
        val dir = directoryFor(model)
        dir.mkdirs()
        // A new install is judged afresh.
        File(dir, OPENING).delete()
        File(dir, REFUSED).delete()
        File(dir, TRIED).delete()
        File(dir, RUNNER).writeText(runnerJson)
        File(dir, PARTS).writeText(
            buildString {
                chunks.forEach {
                    append(
                        CHUNK_TAG,
                    ).append('\t').append(it.name).append('\t').append(it.sizeBytes).append('\n')
                }
                append(
                    EMBEDDING_TAG,
                ).append(
                    '\t',
                ).append(embedding.name).append('\t').append(embedding.sizeBytes).append('\n')
            },
        )
        return dir
    }

    /** [model]'s NPU half, or null unless every listed part is present at its full size. */
    fun read(model: File): Complete? {
        val dir = directoryFor(model)
        val listed = File(dir, PARTS).takeIf { it.isFile }?.readText()?.let(::parse)
        val runner = File(dir, RUNNER).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        if (listed == null || runner == null) return null
        fun present(part: Part) = File(dir, part.name).takeIf {
            it.isFile &&
                it.length() == part.sizeBytes
        }
        val chunks = listed.first.map { present(it) ?: return null }
        return present(listed.second)?.let { Complete(chunks, it, runner) }
    }

    /**
     * Marks [model]'s NPU half as being opened, until [opened]. The probe process opens the
     * half first, but the app opens it again in its own process, possibly with less memory
     * free than the trial had, and reopens its last model at start-up, so a chunk that kills
     * the process could kill every launch after it. A launch that finds the mark still there
     * refuses the half instead.
     */
    fun opening(model: File) {
        runCatching { File(directoryFor(model), OPENING).writeText("") }
    }

    /** The NPU half opened and read a prompt: the mark goes. */
    fun opened(model: File) {
        File(directoryFor(model), OPENING).delete()
    }

    /** Whether the last process to open [model]'s NPU half died before it read a prompt. */
    fun diedOpening(model: File): Boolean = File(directoryFor(model), OPENING).exists()

    /** Stops [model]'s NPU half being used until it is downloaded again, and says why. */
    fun refuse(model: File, reason: String) {
        runCatching { File(directoryFor(model), REFUSED).writeText(reason) }
        opened(model)
    }

    /**
     * Records that the probe process opened [model]'s NPU half and read a prompt with it. The
     * app's process opens a half only after that: a trial that fell back to the CPU, for want
     * of memory, said nothing about the chunks, and the app could then open them untried once
     * memory was free (codex QA).
     */
    fun markTried(model: File) {
        runCatching { File(directoryFor(model), TRIED).writeText("") }
    }

    /** Whether the probe process has read a prompt with [model]'s NPU half as it is now. */
    fun tried(model: File): Boolean = File(directoryFor(model), TRIED).isFile

    /** When [model]'s install was last started, in epoch milliseconds, or 0 when it never was. */
    fun startedAt(model: File): Long = File(directoryFor(model), PARTS).lastModified()

    /** Why [model]'s NPU half is refused, or null when it is not. */
    fun refusal(model: File): String? =
        File(directoryFor(model), REFUSED).takeIf { it.isFile }?.readText()?.ifBlank { "refused" }

    /** The chunks in order and the embedding, or null when the list is malformed. */
    internal fun parse(text: String): Pair<List<Part>, Part>? {
        val rows = text.lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }.toList()
        if (rows.any { it.size != FIELDS }) return null
        fun part(row: List<String>): Part? {
            val name = row[1].takeIf { it.isNotBlank() && '/' !in it && it != ".." } ?: return null
            return row[2].toLongOrNull()?.takeIf { it > 0 }?.let { Part(name, it) }
        }
        val chunks = rows.filter { it[0] == CHUNK_TAG }.map { part(it) ?: return null }
        val embedding = rows.singleOrNull { it[0] == EMBEDDING_TAG }?.let(::part) ?: return null
        return chunks.takeIf { it.isNotEmpty() }?.let { it to embedding }
    }
}

/**
 * How much free memory a MediaTek install needs to load and answer, estimated before any of it
 * is touched (ported from ExecuServe). A build that does not fit is not refused by ExecuTorch:
 * the low-memory killer closes the phone's other apps one after another and then this one,
 * mid-load or on the first request (Qwen3-0.6B's 4k build on a 12 GB phone, 2026-10-06).
 *
 * Measured on that phone, the Neuron backend holds two buffers the size of the NPU's KV cache
 * (LFM2.5-1.2B 2k: 1.08 GB of graphics memory for a 0.54 GB cache), the files are read in
 * whole, and the CPU half decodes into a cache of its own. The total was 3.54 GB for
 * LFM2.5-1.2B 2k (estimated here at 3.9) and over 6.6 GB for Qwen3-0.6B 4k (6.55). The figure
 * is the whole footprint: nothing else is to be added on top of it.
 */
object NpuMemory {
    /** Kept for the system and the app itself: the low-memory killer starts well above zero. */
    const val RESERVE_BYTES = 1L shl 30

    /**
     * The estimate for an install of [filesBytes] whose runner options are [runnerJson], with a
     * tenth added, or null when the options do not describe the cache.
     */
    fun needBytes(runnerJson: String, filesBytes: Long): Long? {
        val keys = listOf("num_layer", "num_head", "head_dim", "cache_size")
        val shape = keys.mapNotNull { int(runnerJson, it)?.toLong() }
        if (shape.size != keys.size) return null
        val bytes = when (
            Regex(
                "\"cache_type\"\\s*:\\s*\"(\\w+)\"",
            ).find(runnerJson)?.groupValues?.get(1)
        ) {
            "fp16", "int16", "bf16" -> HALF
            "int8" -> 1L
            else -> SINGLE
        }
        // K and V, for every layer, head, position and dimension.
        val cache = 2L * shape.reduce(Long::times) * bytes
        // The CPU half's own cache: half the NPU's at most (its attention layers share heads).
        val estimate = filesBytes + 2 * cache + cache / 2
        return estimate + estimate / MARGIN
    }

    /**
     * The need and what is free when [half] of [model] does not fit now, or null when it fits
     * or the phone will not say.
     */
    fun shortfall(model: File, half: NpuInstall.Complete): Pair<Long, Long>? {
        val need = needBytes(half.runnerJson, half.bytes + model.length()) ?: return null
        val free = available() ?: return null
        return (need to free).takeUnless { fits(need, free) }
    }

    /** Whether [needBytes] fits in [availableBytes] with [RESERVE_BYTES] to spare. */
    fun fits(needBytes: Long, availableBytes: Long): Boolean =
        needBytes + RESERVE_BYTES <= availableBytes

    /** MemAvailable from the kernel: what can be had without killing an app. */
    fun available(): Long? = runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("MemAvailable:") }
        }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()?.times(KIB)
    }.getOrNull()

    private const val SINGLE = 4L
    private const val HALF = 2L
    private const val KIB = 1024L

    /** A tenth on top: the files and the caches are not all the backend allocates. */
    private const val MARGIN = 10

    private fun int(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
}
