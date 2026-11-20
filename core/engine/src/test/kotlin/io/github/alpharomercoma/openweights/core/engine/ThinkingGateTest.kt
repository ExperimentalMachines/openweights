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
 * The reasoning budget on the ExecuTorch path, tested where no device is needed.
 *
 * The gate mirrors llama.cpp's budget sampler (`engine_session.cpp`), and every rule below is
 * one of that loop's, so a test failing here means a turn would spend its output budget
 * thinking instead of answering.
 */
class ThinkingGateTest {

    private fun gate(
        budget: Int,
        openedByTemplate: Boolean,
        openTag: String = "<think>",
        closeTag: String = "</think>",
    ) = ThinkingGate(budget, openTag, closeTag, openedByTemplate)

    @Test
    fun `a block the template left open is counted from the first token`() {
        val gate = gate(budget = 3, openedByTemplate = true)
        gate.accept("the")
        assertThat(gate.tripped).isFalse()
        gate.accept(" answer")
        assertThat(gate.tripped).isFalse()
        gate.accept(" is")
        assertThat(gate.tripped).isTrue()
    }

    @Test
    fun `a template that closed an empty block counts nothing`() {
        val gate = gate(budget = 3, openedByTemplate = false)
        repeat(50) { gate.accept("thinking out loud") }
        assertThat(gate.tripped).isFalse()
        assertThat(gate.open).isFalse()
    }

    @Test
    fun `a block the model opened itself is found across fragments`() {
        val gate = gate(budget = 2, openedByTemplate = false)
        gate.accept("<thi")
        gate.accept("nk>")
        assertThat(gate.open).isTrue()
        gate.accept("a")
        gate.accept("b")
        assertThat(gate.tripped).isTrue()
    }

    @Test
    fun `the token that closes the block counts, and nothing after it does`() {
        val gate = gate(budget = 2, openedByTemplate = true)
        gate.accept("one")
        gate.accept("two</think>")
        assertThat(gate.open).isFalse()
        assertThat(gate.tripped).isFalse()
        repeat(10) { gate.accept("answer") }
        assertThat(gate.tripped).isFalse()
    }

    @Test
    fun `a closed block that reopens trips at once, because the count never resets`() {
        val gate = gate(budget = 2, openedByTemplate = true)
        gate.accept("one")
        gate.accept("two")
        assertThat(gate.tripped).isTrue()
        gate.closedByEngine()
        assertThat(gate.tripped).isFalse()
        gate.accept("I will think again <think>")
        assertThat(gate.open).isTrue()
        assertThat(gate.tripped).isTrue()
    }

    @Test
    fun `a budget of zero trips before the model writes anything`() {
        val gate = gate(budget = 0, openedByTemplate = true)
        assertThat(gate.tripped).isTrue()
    }

    @Test
    fun `a negative budget leaves the block to the model`() {
        val gate = gate(budget = -1, openedByTemplate = true)
        repeat(100) { gate.accept("thinking") }
        assertThat(gate.tripped).isFalse()
    }

    @Test
    fun `a pass cannot be handed the window's last token`() {
        // 4096 positions, 4000 held, a 400-character tail at four characters a token, one
        // token of slack for the runtime's own count: nothing left.
        assertThat(passRoom(contextSize = 4096, held = 4000, tailChars = 400)).isLessThan(1)
        assertThat(passRoom(contextSize = 4096, held = 0, tailChars = 0)).isEqualTo(4095)
    }
}
