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

import io.github.alpharomercoma.openweights.core.engine.GenerationEvent
import io.github.alpharomercoma.openweights.core.engine.GenerationStats

/**
 * The reply being written's two speeds, live, for the line under it.
 *
 * The same arithmetic as the figure that replaces it when the reply finishes, folded with
 * every pass already done this turn, so a tool turn's readout ends near the number its row
 * is about to show instead of jumping to it: a reply's row is the whole turn's throughput,
 * and a readout of the current pass alone would not be. Near rather than on: the live
 * decode interval ends at the latest token and the final one after the step that found the
 * end of the reply (see [GenerationEvent.Progress]).
 *
 * Null rates are unknown, not slow. ExecuTorch reads a prompt in one call it cannot look
 * inside, so its prompt rate is null until the reply's own stats arrive.
 *
 * @param percentRead how much of this pass's prompt has been read, while it is being read
 *   and its length is known.
 */
data class LiveReadout(
    val reading: Boolean,
    val percentRead: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
) {
    /** Whether there is a figure to show at all. */
    val hasRate: Boolean get() = prefillTokensPerSecond != null || decodeTokensPerSecond != null

    companion object {
        /** [now] folded with [done], the passes of this turn already finished. */
        fun of(done: GenerationStats?, now: GenerationEvent.Progress): LiveReadout {
            // The current pass's prompt is either counted or not; mixing the finished passes'
            // reading with a pass that cannot say would be a rate over part of the work.
            val prefill = now.promptRead?.let { read ->
                rate(
                    tokens = (done?.promptTokens ?: 0) + read,
                    millis = (done?.prefillMs ?: 0) + (now.prefillMs ?: 0),
                )
            }
            val decode = rate(
                tokens = (done?.let { it.generatedTokens - it.firstTokens } ?: 0) +
                    (now.generated - 1).coerceAtLeast(0),
                millis = (done?.decodeMs ?: 0) + (now.decodeMs ?: 0),
            )
            return LiveReadout(
                reading = now.reading,
                percentRead = percentRead(now),
                prefillTokensPerSecond = prefill,
                decodeTokensPerSecond = decode,
            )
        }

        private fun percentRead(now: GenerationEvent.Progress): Int? {
            val total = now.promptTotal?.takeIf { it > 0 } ?: return null
            val read = now.promptRead ?: return null
            return if (now.reading) (read * PERCENT / total).coerceIn(0, PERCENT) else null
        }

        private fun rate(tokens: Int, millis: Long): Double? =
            if (tokens > 0 && millis > 0) tokens * MILLIS_PER_SECOND / millis else null

        private const val PERCENT = 100
        private const val MILLIS_PER_SECOND = 1000.0
    }
}
