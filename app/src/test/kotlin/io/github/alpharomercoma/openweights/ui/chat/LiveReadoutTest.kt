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

package io.github.alpharomercoma.openweights.ui.chat

import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.engine.GenerationEvent
import io.github.alpharomercoma.openweights.core.engine.GenerationStats
import org.junit.Test

class LiveReadoutTest {
    @Test
    fun `a first pass reads at its own rates`() {
        val live = LiveReadout.of(
            done = null,
            now = progress(
                read = 1024,
                total = 4096,
                prefillMs = 20_000,
                generated = 0,
                decodeMs = null,
            ),
        )

        assertThat(live.reading).isTrue()
        assertThat(live.percentRead).isEqualTo(25)
        assertThat(live.prefillTokensPerSecond).isEqualTo(1024 * 1000.0 / 20_000)
        assertThat(live.decodeTokensPerSecond).isNull()
    }

    @Test
    fun `writing is timed after the first token, as the finished figure is`() {
        val live = LiveReadout.of(
            done = null,
            now = progress(
                read = 512,
                total = 512,
                prefillMs = 9_000,
                generated = 17,
                decodeMs = 1_600,
            ),
        )

        assertThat(live.decodeTokensPerSecond).isEqualTo(16 * 1000.0 / 1_600)
        // Past reading, a percentage would describe a phase that is over.
        assertThat(live.percentRead).isNull()
    }

    @Test
    fun `a later pass folds in the passes already finished, so the readout ends on the row`() {
        val done = GenerationStats(
            promptTokens = 3000,
            generatedTokens = 20,
            prefillMs = 30_000,
            decodeMs = 1_000,
            timeToFirstTokenMs = 0,
            contextUsed = 0,
            contextSize = 8192,
        )
        val now =
            progress(read = 300, total = 300, prefillMs = 3_000, generated = 101, decodeMs = 4_000)

        val live = LiveReadout.of(done, now)
        val finished = done.through(
            GenerationStats(300, 101, 3_000, 4_000, 0, 0, 8192),
        )

        assertThat(live.prefillTokensPerSecond).isEqualTo(finished.prefillTokensPerSecond)
        assertThat(live.decodeTokensPerSecond).isEqualTo(finished.decodeTokensPerSecond)
    }

    @Test
    fun `an engine that cannot count its prompt has no live prompt rate`() {
        val live = LiveReadout.of(
            done = null,
            now = GenerationEvent.Progress(
                reading = false,
                promptRead = null,
                promptTotal = null,
                prefillMs = null,
                generated = 11,
                decodeMs = 1_000,
            ),
        )

        assertThat(live.prefillTokensPerSecond).isNull()
        assertThat(live.decodeTokensPerSecond).isEqualTo(10.0)
        assertThat(live.hasRate).isTrue()
    }

    @Test
    fun `nothing measured yet is nothing to show`() {
        val live = LiveReadout.of(
            done = null,
            now = progress(
                read = 0,
                total = 4096,
                prefillMs = null,
                generated = 0,
                decodeMs = null,
            ),
        )

        assertThat(live.hasRate).isFalse()
        assertThat(live.percentRead).isEqualTo(0)
    }

    private fun progress(read: Int, total: Int, prefillMs: Long?, generated: Int, decodeMs: Long?) =
        GenerationEvent.Progress(
            reading = generated == 0,
            promptRead = read,
            promptTotal = total,
            prefillMs = prefillMs,
            generated = generated,
            decodeMs = decodeMs,
        )
}
