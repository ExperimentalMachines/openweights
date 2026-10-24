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

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import io.github.alpharomercoma.openweights.core.common.model.ToolCall
import io.github.alpharomercoma.openweights.core.common.model.ToolDefinition
import io.github.alpharomercoma.openweights.core.engine.GenerationEvent
import io.github.alpharomercoma.openweights.core.tools.AgentMode
import io.github.alpharomercoma.openweights.core.tools.AgentStep
import io.github.alpharomercoma.openweights.core.tools.AskBoard
import io.github.alpharomercoma.openweights.core.tools.PlanBoard
import io.github.alpharomercoma.openweights.core.tools.Tool
import io.github.alpharomercoma.openweights.core.tools.ToolNotes
import io.github.alpharomercoma.openweights.core.tools.ToolRegistry
import io.github.alpharomercoma.openweights.core.tools.ToolSwitches
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

/**
 * What a tool round puts back in the conversation, which the cache is compared against.
 *
 * LFM2.5 2.6B's template opens `<think>` in its generation prompt; with thinking off the
 * session closes it before the model writes, so the streamed text never has it. Put back
 * without it, the round was not what the cache held, and the hybrid read the whole
 * conversation again on every round: PrefetchMatrixBenchmark on the Poco (2026-10-24) saw
 * cached tokens at 0 on every round, 30 to 50 s of prefill each.
 */
@RunWith(RobolectricTestRunner::class)
class ToolRoundHistoryTest {
    private val models: File = Files.createTempDirectory("openweights-history").toFile()
    private val engine = FakeInferenceEngine().apply { supportsTools = true }

    @Test
    fun `a tool round puts back the think block the session prefilled`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass(
            "Looking.",
            toolCalls = listOf(ToolCall(id = "1", name = "web_search", argumentsJson = QUERY)),
            thinkingPrefilled = true,
        )
        engine.scripted += ScriptedPass("Kotlin 2.4.")

        run()

        val asked = engine.prompts[1].last { it.role == ChatRole.ASSISTANT }
        assertThat(asked.text).startsWith("<think>")
    }

    @Test
    fun `a round with no prefilled block goes back as it was decoded`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass(
            "Looking.",
            toolCalls = listOf(ToolCall(id = "1", name = "web_search", argumentsJson = QUERY)),
        )
        engine.scripted += ScriptedPass("Kotlin 2.4.")

        run()

        val asked = engine.prompts[1].last { it.role == ChatRole.ASSISTANT }
        assertThat(asked.text).doesNotContain("<think>")
    }

    private suspend fun run() {
        engine.load(
            File(models, "model.gguf").apply {
                writeText("not a real model")
            },
            ModelLoadParams(),
        )
        TurnRunner(
            engine = engine,
            tools = ToolRegistry(listOf(Search)),
            switches = ToolSwitches(ApplicationProvider.getApplicationContext()),
            plans = PlanBoard(),
            asks = AskBoard(),
        ).run(
            conversation = listOf(ChatMessage.text(ChatRole.USER, QUESTION)),
            params = SamplerParams(),
            mode = AgentMode.AUTO,
            withTools = true,
            notes = ToolNotes(),
            listener = Ignoring,
            question = QUESTION,
        )
    }

    private object Search : Tool {
        override val defaultsOn: Boolean = true
        override val definition = ToolDefinition(
            name = "web_search",
            description = "Search the web.",
            parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
        )

        override suspend fun run(call: ToolCall): String = "Kotlin 2.4.0 is out."
    }

    private object Ignoring : TurnListener {
        override fun onText(raw: String) = Unit
        override fun onPass(event: GenerationEvent.Completed, raw: String) = Unit
        override fun onSteps(steps: List<AgentStep>) = Unit
        override fun onIntermediate(text: String) = Unit
        override fun onNextPass() = Unit
        override suspend fun onApproval(call: ToolCall): Boolean = true
    }

    private companion object {
        const val QUESTION = "What version of Kotlin came out this month?"
        const val QUERY = """{"query":"kotlin"}"""
    }
}
