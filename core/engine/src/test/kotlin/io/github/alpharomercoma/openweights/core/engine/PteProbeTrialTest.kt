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
import java.util.concurrent.atomic.AtomicInteger

class PteProbeTrialTest {
    @Test
    fun `the trial ends itself at its budget when the runtime would run to the window`() {
        // As the AAR does: the requested count is never read, and only a stop ends the loop.
        val stops = AtomicInteger()
        var emitted = 0
        val endless = object : ExecuTorchBridge by FakeExecuTorchBridge() {
            override fun stop() {
                stops.incrementAndGet()
            }

            override fun generate(
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
            ): ExecuTorchOutcome {
                while (stops.get() == 0 && emitted < WINDOW) {
                    emitted++
                    onToken(" Paris")
                }
                return ExecuTorchOutcome(StopReason.END_OF_TURN, generatedTokens = emitted)
            }
        }

        val trial = PteProbeService.runTrial(endless)

        assertThat(emitted).isEqualTo(PteProbeService.TRIAL_TOKENS)
        assertThat(stops.get()).isAtLeast(1)
        assertThat(trial.wroteNothingReadable).isFalse()
    }

    @Test
    fun `a runtime that clears its stop flag is asked again on the next token`() {
        // The token loop clears a stop requested before it starts, so a stop asked once can be
        // lost; the trial asks again for every token past its budget.
        val stops = AtomicInteger()
        var emitted = 0
        val forgetful = object : ExecuTorchBridge by FakeExecuTorchBridge() {
            override fun stop() {
                stops.incrementAndGet()
            }

            override fun generate(
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
            ): ExecuTorchOutcome {
                while (emitted < WINDOW) {
                    emitted++
                    onToken(" Paris")
                    // The first stop is forgotten; only a later one is honoured.
                    if (stops.get() >= 2) break
                }
                return ExecuTorchOutcome(StopReason.END_OF_TURN, generatedTokens = emitted)
            }
        }

        PteProbeService.runTrial(forgetful)

        assertThat(emitted).isEqualTo(PteProbeService.TRIAL_TOKENS + 1)
    }

    @Test
    fun `a runtime that generates without calling back is stopped by the deadline and refused`() {
        // The JNI layer holds text until its buffer is valid UTF-8; a garbage graph can write
        // a window of tokens with no callback at all, so counting callbacks never stops it.
        val stops = AtomicInteger()
        val generated = AtomicInteger()
        val silent = object : ExecuTorchBridge by FakeExecuTorchBridge() {
            override fun stop() {
                stops.incrementAndGet()
            }

            override fun generate(
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
            ): ExecuTorchOutcome {
                // As the stock runner does, the stop asked first is cleared when decoding
                // starts; only a later one ends the loop.
                while (stops.get() < 2 && generated.get() < WINDOW) {
                    generated.incrementAndGet()
                    Thread.sleep(1)
                }
                return ExecuTorchOutcome(StopReason.END_OF_TURN, generatedTokens = generated.get())
            }
        }

        val trial = PteProbeService.runTrial(silent, deadlineMs = 20)

        assertThat(generated.get()).isLessThan(WINDOW)
        assertThat(stops.get()).isAtLeast(2)
        assertThat(trial.callbacks).isEqualTo(0)
        assertThat(trial.wroteNothingReadable).isTrue()
        assertThat(trial.refusal).isEqualTo(PteProbeService.UNREADABLE)
    }

    @Test
    fun `a stop kept through a slow prefill leaves decode untried, which is no pass`() {
        // The patched QNN runner keeps a stop asked while it reads the prompt, writes the
        // prompt's own token and never enters its token loop; its count leaves that token out.
        val stops = AtomicInteger()
        val qnn = object : ExecuTorchBridge by FakeExecuTorchBridge() {
            override fun stop() {
                stops.incrementAndGet()
            }

            override fun generate(
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
            ): ExecuTorchOutcome {
                while (stops.get() == 0) Thread.sleep(1) // a prefill longer than the deadline
                onToken(" Paris")
                return ExecuTorchOutcome(StopReason.END_OF_TURN, generatedTokens = 0)
            }
        }

        val trial = PteProbeService.runTrial(qnn, deadlineMs = 20)

        assertThat(trial.stoppedByDeadline).isTrue()
        assertThat(trial.wroteNothingReadable).isFalse()
        assertThat(trial.decodeUntried).isTrue()
        assertThat(trial.refusal).isEqualTo(PteProbeService.TOO_SLOW_TO_REPLY)
    }

    @Test
    fun `a slow prefill on the stock runner still decodes and passes`() {
        // The stock runner clears a stop asked during prefill when its token loop starts, so
        // the watchdog's next request ends it after a decode step: decode was tried.
        val stops = AtomicInteger()
        var emitted = 0
        val stock = object : ExecuTorchBridge by FakeExecuTorchBridge() {
            override fun stop() {
                stops.incrementAndGet()
            }

            override fun generate(
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
            ): ExecuTorchOutcome {
                while (stops.get() == 0) Thread.sleep(1) // a prefill longer than the deadline
                onToken(" Paris")
                val cleared = stops.get()
                while (stops.get() == cleared) {
                    emitted++
                    onToken(",")
                    Thread.sleep(1)
                }
                return ExecuTorchOutcome(StopReason.END_OF_TURN, generatedTokens = emitted)
            }
        }

        val trial = PteProbeService.runTrial(stock, deadlineMs = 20)

        assertThat(trial.stoppedByDeadline).isTrue()
        assertThat(trial.decodeUntried).isFalse()
        assertThat(trial.refusal).isNull()
    }

    @Test
    fun `a trial that ends on its own leaves no watchdog asking for stops`() {
        val stops = AtomicInteger()
        val quick = object : ExecuTorchBridge by FakeExecuTorchBridge() {
            override fun stop() {
                stops.incrementAndGet()
            }

            override fun generate(
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
            ): ExecuTorchOutcome {
                onToken(" Paris")
                return ExecuTorchOutcome(StopReason.END_OF_TURN, generatedTokens = 1)
            }
        }

        val trial = PteProbeService.runTrial(quick, deadlineMs = 20)
        Thread.sleep(100)

        assertThat(stops.get()).isEqualTo(0)
        assertThat(trial.stoppedByDeadline).isFalse()
        assertThat(trial.refusal).isNull()
    }

    private companion object {
        const val WINDOW = 2048
    }
}
