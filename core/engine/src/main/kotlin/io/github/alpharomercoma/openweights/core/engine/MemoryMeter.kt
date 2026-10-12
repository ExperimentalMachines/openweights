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
 * What this process's memory did while one reply was produced, as the kernel counts it.
 *
 * Read from `/proc/self`, which an ordinary app may read; the system-wide swap counters and
 * the kernel's pressure figure are denied to apps on the test phone's ROM, so nothing here
 * depends on them. Every field is nullable because any of those reads can be refused, and an
 * unreadable figure is unknown, not zero.
 *
 * Measured, on the POCO X8 Pro Max with a balloon of incompressible memory pressing on the
 * model (docs/research/swap-and-peak-memory.md, 2026-10-12): how much of a model sits swapped
 * out costs nothing by itself, because what goes first is cache nobody is reading; what costs
 * time is pages coming back in during a turn, all of it in prefill, and only the major fault
 * count shows it. The swapped size stayed flat at 1.15 GB through turns that paged several
 * times that back in.
 *
 * @param majorFaults faults that had to wait for a page to be read back, from swap or from a
 *   file; null when `/proc/self/stat` could not be read at either end.
 * @param activeMs the wall time the fault count was taken over: the passes themselves, not
 *   the tools between them, so a slow tool cannot dilute what the faults cost.
 * @param peakFootprintBytes the highest resident plus swapped size sampled, which includes
 *   the app itself. Sampled every quarter second, so a brief peak between samples is missed.
 * @param swapEndBytes swapped out when the last pass ended; null when a later pass could not
 *   say, because an earlier pass's figure is not the final one.
 * @param pagingDetected whether any pass met [MemoryMeter]'s paging heuristic.
 * @param cold whether any pass read freshly opened weights in for the first time, whose
 *   faults are not paging and were never counted as such.
 */
data class TurnMemory(
    val majorFaults: Long?,
    val activeMs: Long,
    val peakFootprintBytes: Long?,
    val swapEndBytes: Long?,
    val pagingDetected: Boolean,
    val cold: Boolean = false,
) {
    /** These and [next]'s as one reply: counts and times add, the peak is the higher. */
    fun through(next: TurnMemory): TurnMemory = TurnMemory(
        majorFaults = majorFaults?.let { mine -> next.majorFaults?.let { mine + it } },
        activeMs = activeMs + next.activeMs,
        peakFootprintBytes = listOfNotNull(peakFootprintBytes, next.peakFootprintBytes).maxOrNull(),
        swapEndBytes = next.swapEndBytes,
        pagingDetected = pagingDetected || next.pagingDetected,
        cold = cold || next.cold,
    )

    companion object {
        /**
         * Two passes' memory, either of which may be missing. A pass with no reading at all
         * leaves the reply's fault total and its final swap unknown rather than counting it
         * as none or borrowing an earlier pass's; the peak stays, as the highest seen.
         */
        fun merge(first: TurnMemory?, second: TurnMemory?): TurnMemory? = when {
            first == null && second == null -> null
            first == null -> second?.copy(majorFaults = null)
            second == null -> first.copy(majorFaults = null, swapEndBytes = null)
            else -> first.through(second)
        }
    }
}

/**
 * Takes [TurnMemory] around one engine pass: a reading at the start, one each time [sample]
 * is called while it runs, one at [finish].
 *
 * Both engines use it, sampled from the same quarter-second ticker that sends the live rates,
 * so a pass costs four small reads a second and nothing on the inference thread.
 *
 * @param cold the first pass after a model was opened. Its faults are the weights being read
 *   in for the first time (a compiled model maps its file: Gemma 3 4B's first turn took 61,525
 *   with nothing pressing on it), so it is measured and never flagged.
 */
internal class MemoryMeter(
    @Volatile private var cold: Boolean,
    private val read: () -> ProcSample = ProcSample::read,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private val startedAt = clock()
    private val start = read()
    private var peak: Long? = start.footprintBytes

    /** The weights were opened after this pass began; see the class's `cold`. */
    fun markCold() {
        cold = true
    }

    /** One reading while the pass runs, for the peak. Called from the ticker's thread. */
    @Synchronized
    fun sample() {
        read().footprintBytes?.let { now -> peak = maxOf(peak ?: now, now) }
    }

    /** The pass's figures, and whether its faults meet the paging heuristic. */
    @Synchronized
    fun finish(): TurnMemory {
        val end = read()
        val activeMs = (clock() - startedAt).coerceAtLeast(0)
        end.footprintBytes?.let { now -> peak = maxOf(peak ?: now, now) }
        val faults = start.majorFaults?.let { first ->
            end.majorFaults?.let { (it - first).coerceAtLeast(0) }
        }
        return TurnMemory(
            majorFaults = faults,
            activeMs = activeMs,
            peakFootprintBytes = peak,
            swapEndBytes = end.swapBytes,
            pagingDetected = !cold && faults != null && pagingDetected(faults, activeMs),
            cold = cold,
        )
    }

    companion object {
        /**
         * Wall time one major fault cost, on average, on the test phone's zram: the extra
         * seconds of each slowed turn over the unpressed baseline, divided by its faults, came
         * to 15 to 27 µs (docs/research/swap-and-peak-memory.md). An empirical relation for a
         * heuristic, not a stall timer: a fault also counts a file read, and readahead brings
         * in more than one page for one fault.
         */
        const val FAULT_COST_MICROS = 20L

        /**
         * The share of a pass its faults must account for to count as paging. Every
         * unpressed turn measured came to 3% or less (the cold Gemma turn), every slowed one
         * to 21% or more, so the line sits in a wide gap rather than on a knife edge.
         */
        const val PAGING_SHARE = 0.10

        /**
         * A floor under the share, so a short pass and the app's own handful of faults cannot
         * trip it: half a second of faults in a two-second pass is not worth a warning.
         */
        const val MIN_PAGING_MS = 1_000L

        private const val MICROS_PER_MILLI = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000L

        /** Whether [faults] over [activeMs] meet the heuristic, by the constants above. */
        fun pagingDetected(faults: Long, activeMs: Long): Boolean {
            val pagingMs = faults * FAULT_COST_MICROS / MICROS_PER_MILLI
            return pagingMs >= MIN_PAGING_MS && pagingMs > activeMs * PAGING_SHARE
        }
    }
}

/** One reading of this process's own counters; each null where the kernel refused it. */
internal data class ProcSample(
    val majorFaults: Long?,
    val residentBytes: Long?,
    val swapBytes: Long?,
) {
    /**
     * Resident plus swapped: what the process holds, whether or not some is compressed. Both
     * or nothing: a resident size alone passed off as the total would hide exactly the part
     * this is for.
     */
    val footprintBytes: Long? get() = residentBytes?.let { resident ->
        swapBytes?.let {
            resident +
                it
        }
    }

    companion object {
        private const val BYTES_PER_KB = 1024L

        /** The 10th field after the command name, which may itself hold spaces and brackets. */
        private const val MAJFLT_AFTER_COMM = 9

        fun read(): ProcSample {
            val status = runCatching { File("/proc/self/status").readText() }.getOrNull()
            val stat = runCatching { File("/proc/self/stat").readText() }.getOrNull()
            return parse(stat, status)
        }

        /** Parsed apart from [read] so the formats can be tested without a kernel. */
        fun parse(stat: String?, status: String?): ProcSample = ProcSample(
            majorFaults = stat?.substringAfterLast(')', "")
                ?.trim()
                ?.split(' ')
                ?.getOrNull(MAJFLT_AFTER_COMM)
                ?.toLongOrNull(),
            residentBytes = status?.kilobytes("VmRSS")?.times(BYTES_PER_KB),
            swapBytes = status?.kilobytes("VmSwap")?.times(BYTES_PER_KB),
        )

        private fun String.kilobytes(field: String): Long? = lineSequence()
            .firstOrNull { it.startsWith("$field:") }
            ?.substringAfter(':')
            ?.trim()
            ?.substringBefore(' ')
            ?.toLongOrNull()
    }
}
