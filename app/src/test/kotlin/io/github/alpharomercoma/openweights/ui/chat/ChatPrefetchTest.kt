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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * When typing brings the weights back (see TurnRunner.prefetchWeights): on the next
 * question, not on every keystroke, never over a turn, and never in a turn's way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatPrefetchTest : ChatFixture() {

    private var now = 1_000_000L

    private fun TestScope.typedAReply() {
        turns.clock = { now }
        loadModel()
        engine.scripted += ScriptedPass("Here is the answer.")
        viewModel.send("A question")
        settle(steps = FOLD_SETTLE_STEPS)
    }

    @Test
    fun `after weights came back, typing does not walk them again for ten seconds`() =
        runTest(dispatcher) {
            typedAReply()
            engine.prefetchSkips = false

            viewModel.onComposerTyping()
            settle()
            now += 9_000
            viewModel.onComposerTyping()
            settle()
            assertThat(engine.prefetchCalls).isEqualTo(1)

            now += 1_000
            viewModel.onComposerTyping()
            settle()
            assertThat(engine.prefetchCalls).isEqualTo(2)
        }

    @Test
    fun `a look that found the weights in looks again two seconds later`() = runTest(dispatcher) {
        typedAReply()

        viewModel.onComposerTyping()
        settle()
        now += 1_000
        viewModel.onComposerTyping()
        settle()
        assertThat(engine.prefetchCalls).isEqualTo(1)

        now += 1_000
        viewModel.onComposerTyping()
        settle()
        assertThat(engine.prefetchCalls).isEqualTo(2)
    }

    @Test
    fun `typing while a reply is written does not touch the weights`() = runTest(dispatcher) {
        turns.clock = { now }
        loadModel()
        engine.hold = true
        viewModel.send("A question")

        viewModel.onComposerTyping()
        settle()

        assertThat(engine.prefetchCalls).isEqualTo(0)
    }

    @Test
    fun `sending stops a prefetch still running`() = runTest(dispatcher) {
        typedAReply()
        engine.prefetchGate = CompletableDeferred()
        viewModel.onComposerTyping()
        settle()
        assertThat(engine.prefetchCalls).isEqualTo(1)

        engine.scripted += ScriptedPass("A second answer.")
        viewModel.send("The next question")
        settle(steps = FOLD_SETTLE_STEPS)

        assertThat(engine.prefetchesCancelled).isEqualTo(1)
        assertThat(viewModel.uiState.value.transcript.last().text).isEqualTo("A second answer.")
    }
}
