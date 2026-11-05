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
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ToolCall
import io.github.alpharomercoma.openweights.core.common.model.ToolDefinition
import io.github.alpharomercoma.openweights.core.tools.Tool
import io.github.alpharomercoma.openweights.core.tools.ToolEvidence
import io.github.alpharomercoma.openweights.core.tools.ToolExecution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What a turn no search engine answered leaves on screen and in storage. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatSearchUnreachableTest : ChatFixture() {
    private val nobody = object : Tool by StubTool {
        override val definition = ToolDefinition("web_search", "Search the web.", "{}")
        override val defaultsOn = true

        override suspend fun execute(call: ToolCall) = ToolExecution(
            text = "No search provider could answer.",
            successful = false,
            evidence = ToolEvidence.NoProviderAnswered,
        )
    }

    private val call = ToolCall("1", "web_search", """{"query":"weather in tokyo"}""")

    private fun useFailingSearch() {
        viewModel = newViewModel(savedState, listOf(nobody))
        engine.supportsTools = true
    }

    @Test
    fun `the model's own failed call is stored once as the sentence and reopens as it`() =
        runTest(dispatcher) {
            useFailingSearch()
            loadModel()
            engine.scripted += ScriptedPass("", toolCalls = listOf(call))
            engine.scripted += ScriptedPass("It is about 22 degrees in Tokyo.")

            viewModel.send("weather in tokyo")
            settle(steps = FOLD_SETTLE_STEPS)

            val id = requireNotNull(viewModel.uiState.value.activeConversationId)
            val rows = awaitMessages(id, 2).filter { it.role == ChatRole.ASSISTANT.wireName }
            assertThat(rows.map { it.text }).containsExactly(NO_ENGINE_REPLY)
            assertThat(viewModel.uiState.value.transcript.last().answer).isEqualTo(NO_ENGINE_REPLY)
            assertThat(engine.prompts).hasSize(1)

            viewModel.newChat()
            settle()
            viewModel.openConversation(id)
            settle(steps = FOLD_SETTLE_STEPS)
            assertThat(viewModel.uiState.value.transcript.last().text).isEqualTo(NO_ENGINE_REPLY)
        }

    @Test
    fun `the next turn reads the sentence, not what the replaced pass said`() =
        runTest(dispatcher) {
            useFailingSearch()
            loadModel()
            engine.scripted += ScriptedPass("Based on my search, Bitcoin is at 75,000 dollars.")
            engine.scripted += ScriptedPass("Hello.")

            viewModel.send("price of bitcoin")
            settle(steps = FOLD_SETTLE_STEPS)
            viewModel.send("hello")
            settle(steps = FOLD_SETTLE_STEPS)

            val next = engine.prompts.last().joinToString { it.text }
            assertThat(next).contains(NO_ENGINE_REPLY)
            assertThat(next).doesNotContain("75,000")
        }

    @Test
    fun `a replacement after a repaired pass and an early cut is stored once`() =
        runTest(dispatcher) {
            useFailingSearch()
            loadModel()
            // A settled pass first, then a pass the loop cuts before its completion arrives:
            // the one order where the end of the turn and settleTurn could both write it.
            engine.scripted += ScriptedPass("<|tool_call_start|>[no_such_tool()]<|tool_call_end|>")
            engine.scripted += ScriptedPass(
                "I'm fetching the latest information about Killua from the web now. " +
                    "Once I have the results, I'll invent them.",
            )

            viewModel.send("who is killua zoldyck")
            settle(steps = FOLD_SETTLE_STEPS)

            val id = requireNotNull(viewModel.uiState.value.activeConversationId)
            awaitMessages(id, 2)
            // Past the first write, because the second is what this is looking for.
            settle(steps = FOLD_SETTLE_STEPS)
            val rows = database.messages().forConversation(id)
                .filter { it.role == ChatRole.ASSISTANT.wireName }
            assertThat(rows.map { it.text }).containsExactly(NO_ENGINE_REPLY)
            assertThat(engine.prompts).hasSize(2)
            assertThat(viewModel.uiState.value.transcript.last().isStreaming).isFalse()
        }

    @Test
    fun `an honest refusal is what is stored`() = runTest(dispatcher) {
        useFailingSearch()
        loadModel()
        val refusal = "I don't have real-time weather data at the moment."
        engine.scripted += ScriptedPass(refusal)

        viewModel.send("weather in tokyo")
        settle(steps = FOLD_SETTLE_STEPS)

        val id = requireNotNull(viewModel.uiState.value.activeConversationId)
        val rows = awaitMessages(id, 2).filter { it.role == ChatRole.ASSISTANT.wireName }
        assertThat(rows.map { it.text }).containsExactly(refusal)
    }
}
