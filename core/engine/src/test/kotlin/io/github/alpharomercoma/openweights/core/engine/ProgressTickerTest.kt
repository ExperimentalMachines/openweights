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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import java.util.concurrent.CountDownLatch

/** The native progress snapshot read as an event, and the Kotlin-side count. */
class ProgressTickerTest {
    @Test
    fun `an idle session reports nothing`() {
        assertThat(progressFrom(longArrayOf(0, 100, 50, 0, 0, 0, 0, 0))).isNull()
    }

    @Test
    fun `a prompt part read gives its count and the time to the last whole batch`() {
        val progress = progressFrom(longArrayOf(1, 4096, 1024, 1_000, 21_000, 0, 0, 0))!!

        assertThat(progress.reading).isTrue()
        assertThat(progress.promptRead).isEqualTo(1024)
        assertThat(progress.promptTotal).isEqualTo(4096)
        assertThat(progress.prefillMs).isEqualTo(20_000)
        assertThat(progress.decodeMs).isNull()
    }

    @Test
    fun `a prompt with media has no count and no prompt time`() {
        val progress = progressFrom(longArrayOf(1, -1, 0, 1_000, 1_000, 0, 0, 0))!!

        assertThat(progress.promptTotal).isNull()
        assertThat(progress.promptRead).isNull()
        assertThat(progress.prefillMs).isNull()
    }

    @Test
    fun `decode is timed from the first token to the latest`() {
        val progress = progressFrom(longArrayOf(2, 512, 512, 0, 9_000, 17, 10_000, 11_600))!!

        assertThat(progress.reading).isFalse()
        assertThat(progress.generated).isEqualTo(17)
        assertThat(progress.decodeMs).isEqualTo(1_600)
    }

    @Test
    fun `one token has no decode interval yet`() {
        val progress = progressFrom(longArrayOf(2, 512, 512, 0, 9_000, 1, 10_000, 10_000))!!

        assertThat(progress.decodeMs).isNull()
    }

    @Test
    fun `the Kotlin count reads nothing of a prompt and times writing the same way`() {
        var now = 1_000L
        val live = LiveCount(clock = { now })
        assertThat(live.progress().reading).isTrue()

        live.token()
        now = 1_500
        live.token()
        now = 2_000
        live.token()

        val progress = live.progress()
        assertThat(progress.reading).isFalse()
        assertThat(progress.promptRead).isNull()
        assertThat(progress.generated).isEqualTo(3)
        assertThat(progress.decodeMs).isEqualTo(1_000)
    }

    @Test
    fun `nothing from a pass follows its close, however long the ticker had left`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var generated = 0
        val events = channelFlow {
            val pass = PassMeter(
                scope = this,
                cold = false,
                read = { progress(++generated) },
                dispatcher = dispatcher,
                memory = meter(),
            )
            delay(PROGRESS_TICK_MS * 4 + 1)
            pass.close()
            send(
                GenerationEvent.Completed(
                    StopReason.END_OF_TURN,
                    GenerationStats(0, 0, 0, 0, 0, 0, 0),
                ),
            )
            // Long enough for four more ticks, had the ticker outlived the pass.
            delay(PROGRESS_TICK_MS * 4)
        }.toList()

        assertThat(events.last()).isInstanceOf(GenerationEvent.Completed::class.java)
        assertThat(events.filterIsInstance<GenerationEvent.Progress>()).hasSize(4)
    }

    @Test
    fun `an unchanged reading is not sent again`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = channelFlow {
            val pass =
                PassMeter(this, cold = false, read = {
                    progress(5)
                }, dispatcher = dispatcher, memory = meter())
            delay(PROGRESS_TICK_MS * 4 + 1)
            pass.close()
        }.toList()

        assertThat(events).hasSize(1)
    }

    @Test
    fun `closing twice gives the same figures, and a pass opened mid-way is cold`() = runTest {
        val events = channelFlow {
            val faults = ArrayDeque(listOf(0L, 5_000_000L))
            val meter = MemoryMeter(
                cold = false,
                read = { ProcSample(faults.removeFirst(), 1L, 0L) },
                clock = { 0L },
            )
            val pass = PassMeter(
                this,
                cold = false,
                read = { null },
                dispatcher = StandardTestDispatcher(testScheduler),
                memory = meter,
            )
            pass.markCold()
            val first = pass.close()
            assertThat(pass.close()).isSameInstanceAs(first)
            // Five million faults in no time at all would be paging, were the weights not
            // opened during this pass.
            assertThat(first.pagingDetected).isFalse()
        }.toList()

        assertThat(events).isEmpty()
    }

    @Test
    fun `a tick already reading when the pass closes still cannot send after it`() {
        // On real threads: a tick held inside its read while close() is called. close()
        // waits for the tick to finish under the shared lock, so whatever that tick sends
        // lands before close() returns, and nothing can follow the completion sent after it.
        val inRead = CountDownLatch(1)
        val release = CountDownLatch(1)
        val events = runBlocking {
            channelFlow {
                val pass = PassMeter(
                    scope = this,
                    cold = false,
                    read = {
                        inRead.countDown()
                        release.await()
                        progress(1)
                    },
                    dispatcher = Dispatchers.Default,
                    memory = meter(),
                )
                withContext(Dispatchers.IO) { inRead.await() }
                val closing = launch(Dispatchers.IO) { pass.close() }
                // The closer is now waiting on the tick's lock; let the tick finish.
                Thread.sleep(50)
                release.countDown()
                closing.join()
                send(
                    GenerationEvent.Completed(
                        StopReason.END_OF_TURN,
                        GenerationStats(0, 0, 0, 0, 0, 0, 0),
                    ),
                )
                delay(PROGRESS_TICK_MS * 3)
            }.toList()
        }

        assertThat(events.last()).isInstanceOf(GenerationEvent.Completed::class.java)
        assertThat(events.filterIsInstance<GenerationEvent.Progress>()).hasSize(1)
    }

    private fun meter() = MemoryMeter(cold = false, read = {
        ProcSample(0L, 1L, 0L)
    }, clock = { 0L })

    private fun progress(generated: Int) = GenerationEvent.Progress(
        reading = false,
        promptRead = null,
        promptTotal = null,
        prefillMs = null,
        generated = generated,
        decodeMs = null,
    )
}
