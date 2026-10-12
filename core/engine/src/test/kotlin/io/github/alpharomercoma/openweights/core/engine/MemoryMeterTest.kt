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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The paging heuristic against the turns it was drawn from (docs/research/swap-and-peak-memory.md),
 * and the `/proc` formats it reads.
 */
class MemoryMeterTest {
    @Test
    fun `the slowed turns measured on the phone are flagged`() {
        // 4 GB balloon, the turn after an idle gap: 453,179 faults in 36.9 s.
        assertThat(MemoryMeter.pagingDetected(faults = 453_179, activeMs = 36_900)).isTrue()
        // 5 GB balloon, the mildest turn: 417,011 faults in 40.2 s.
        assertThat(MemoryMeter.pagingDetected(faults = 417_011, activeMs = 40_200)).isTrue()
    }

    @Test
    fun `the unslowed turns are not`() {
        // 4 GB balloon, a turn after a gap that paged little: 16,088 faults in 31.4 s.
        assertThat(MemoryMeter.pagingDetected(faults = 16_088, activeMs = 31_400)).isFalse()
        // A compiled model's file read in on its first turn: 61,525 faults in 40.6 s.
        assertThat(MemoryMeter.pagingDetected(faults = 61_525, activeMs = 40_600)).isFalse()
    }

    @Test
    fun `a short pass needs a second of faults, not just a share`() {
        // 45,000 faults is 0.9 s at 20 µs: 45% of a two-second pass, and still not enough.
        assertThat(MemoryMeter.pagingDetected(faults = 45_000, activeMs = 2_000)).isFalse()
        assertThat(MemoryMeter.pagingDetected(faults = 60_000, activeMs = 2_000)).isTrue()
    }

    @Test
    fun `a cold pass is measured and never flagged`() {
        val samples = ArrayDeque(listOf(sample(faults = 0), sample(faults = 2_000_000)))
        var now = 0L
        val meter = MemoryMeter(cold = true, read = { samples.removeFirst() }, clock = { now })
        now = 10_000

        val memory = meter.finish()

        assertThat(memory.majorFaults).isEqualTo(2_000_000)
        assertThat(memory.pagingDetected).isFalse()
    }

    @Test
    fun `the peak is the highest footprint any reading saw`() {
        val samples = ArrayDeque(
            listOf(
                sample(resident = 1_000, swap = 0),
                sample(resident = 1_500, swap = 900),
                sample(resident = 1_200, swap = 100),
            ),
        )
        val meter = MemoryMeter(cold = false, read = { samples.removeFirst() }, clock = { 0L })

        meter.sample()
        val memory = meter.finish()

        assertThat(memory.peakFootprintBytes).isEqualTo(2_400)
        assertThat(memory.swapEndBytes).isEqualTo(100)
    }

    @Test
    fun `an unreadable fault count is unknown, and unknown never flags`() {
        val samples = ArrayDeque(listOf(sample(faults = null), sample(faults = 9_000_000)))
        val meter = MemoryMeter(cold = false, read = { samples.removeFirst() }, clock = { 0L })

        val memory = meter.finish()

        assertThat(memory.majorFaults).isNull()
        assertThat(memory.pagingDetected).isFalse()
    }

    @Test
    fun `major faults are read after the command name, whatever it holds`() {
        // A command name with a space and a bracket in it; majflt is the 12th field overall.
        val stat = "4242 (Open (W) eights) S 1 2 3 4 5 6 777 8 999 10 11 12 13"

        assertThat(ProcSample.parse(stat, null).majorFaults).isEqualTo(999)
    }

    @Test
    fun `sizes are read from status in kilobytes and given in bytes`() {
        val status = "Name:\tapp\nVmHWM:\t  4000 kB\nVmRSS:\t  3000 kB\nVmSwap:\t   512 kB\n"

        val sample = ProcSample.parse(null, status)

        assertThat(sample.residentBytes).isEqualTo(3000L * 1024)
        assertThat(sample.swapBytes).isEqualTo(512L * 1024)
        assertThat(sample.footprintBytes).isEqualTo(3512L * 1024)
    }

    @Test
    fun `a resident size without a swap size is no footprint`() {
        // Passed off as the total, it would hide exactly the part a footprint is for.
        val sample = ProcSample.parse(null, "VmRSS:\t  3000 kB\n")

        assertThat(sample.residentBytes).isEqualTo(3000L * 1024)
        assertThat(sample.footprintBytes).isNull()
    }

    @Test
    fun `nothing readable is all unknown`() {
        val sample = ProcSample.parse(null, null)

        assertThat(sample.majorFaults).isNull()
        assertThat(sample.footprintBytes).isNull()
    }

    private fun sample(faults: Long? = 0, resident: Long? = 0, swap: Long? = 0) =
        ProcSample(majorFaults = faults, residentBytes = resident, swapBytes = swap)
}
