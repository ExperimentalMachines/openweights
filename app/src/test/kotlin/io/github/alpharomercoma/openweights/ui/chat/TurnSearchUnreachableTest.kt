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
import io.github.alpharomercoma.openweights.core.tools.ToolEvidence
import io.github.alpharomercoma.openweights.core.tools.ToolExecution
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
 * A search that no engine answered ends the turn honestly, because the pass after it invents.
 *
 * On the signed release with every engine unreachable, the compiled LFM2.5 1.2B was told to
 * say so rather than guess and wrote "approximately 22 degrees", "about 250,000 yen" and a
 * Bitcoin range, each as a fact. Its first pass had refused honestly.
 */
@RunWith(RobolectricTestRunner::class)
class TurnSearchUnreachableTest {
    private val models: File = Files.createTempDirectory("openweights-unreachable").toFile()
    private val engine = FakeInferenceEngine().apply { supportsTools = true }
    private val nobody = ToolExecution(
        text = "No search provider could answer.",
        successful = false,
        evidence = ToolEvidence.NoProviderAnswered,
    )
    private val hits = ToolExecution(
        text = "[1] Tokyo\nIt is 18 degrees and clear.\nhttps://example.test\n",
        evidence = ToolEvidence.Search(setOf("https://example.test")),
    )
    private val pictures = fake("show_pictures", chain = false)
    private val notes = fake("read_file", chain = true)
    private val fetcher = fake("fetch_url", chain = true)
    private val replaced = mutableListOf<String>()

    @Test
    fun `a refusal whose search no engine answers stands, and nothing is invented after it`() =
        runBlocking<Unit> {
            val refusal = "I don't have real-time weather data at the moment."
            engine.scripted += ScriptedPass(refusal)
            engine.scripted += ScriptedPass("It is about 22 degrees in Tokyo.")

            assertThat(answering("weather in tokyo", nobody)).contains(refusal)
            assertThat(engine.prompts).hasSize(1)
            assertThat(replaced).isEmpty()
        }

    @Test
    fun `an announced search no engine answers is replaced by saying so`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("Let me look that up for you.")
        engine.scripted += ScriptedPass("It is about 22 degrees in Tokyo.")

        assertThat(answering("weather in tokyo", nobody)).isEqualTo(NO_ENGINE_REPLY)
        assertThat(engine.prompts).hasSize(1)
        assertThat(replaced).containsExactly(NO_ENGINE_REPLY)
    }

    @Test
    fun `a search it claims and no engine answers does not survive as the reply`() =
        runBlocking<Unit> {
            engine.scripted += ScriptedPass("Based on my search, Bitcoin is at 75,000 dollars.")

            assertThat(answering("price of bitcoin", nobody)).isEqualTo(NO_ENGINE_REPLY)
            assertThat(replaced).containsExactly(NO_ENGINE_REPLY)
        }

    @Test
    fun `the model's own call no engine answers is answered by saying so`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("It is about 22 degrees in Tokyo.")

        assertThat(answering("weather in tokyo", nobody)).isEqualTo(NO_ENGINE_REPLY)
        assertThat(engine.prompts).hasSize(1)
    }

    @Test
    fun `an answer the model only doubted stands when no engine answers`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("Canberra is the capital of Australia.", logprob = UNSURE)

        assertThat(answering("capital of australia", nobody)).contains("Canberra")
        assertThat(engine.prompts).hasSize(1)
        assertThat(replaced).isEmpty()
    }

    @Test
    fun `results already in hand are answered from when a later search fails`() =
        runBlocking<Unit> {
            engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
            engine.scripted += ScriptedPass("", toolCalls = listOf(call("2")))
            engine.scripted += ScriptedPass("It is 18 degrees and clear in Tokyo.")

            assertThat(answering("weather in tokyo", hits, nobody)).contains("18 degrees")
            assertThat(engine.prompts).hasSize(3)
        }

    @Test
    fun `a tool that worked without the web does not let an invention through`() =
        runBlocking<Unit> {
            engine.scripted += ScriptedPass(
                "",
                toolCalls = listOf(ToolCall("1", "show_pictures", "{}"), call("2")),
            )
            engine.scripted += ScriptedPass("It is about 22 degrees in Tokyo.")

            assertThat(answering("weather in tokyo", nobody, also = listOf(pictures)))
                .isEqualTo(NO_ENGINE_REPLY)
        }

    @Test
    fun `a file tool on offer is a way on, so the model is asked`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("The notes say Tokyo was cold last week.")

        assertThat(answering("what do my notes say", nobody, also = listOf(notes)))
            .contains("cold last week")
    }

    @Test
    fun `an address the fetch tool could open is a way on`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("I would read that page next.")

        assertThat(
            answering("see https://example.test/page", nobody, also = listOf(fetcher)),
        ).contains("read that")
    }

    @Test
    fun `an address with nothing to open it is no way on`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("That page says it is 22 degrees.")

        assertThat(answering("see https://example.test/page", nobody)).isEqualTo(NO_ENGINE_REPLY)
    }

    @Test
    fun `a long message earlier in the chat is no way on`() = runBlocking<Unit> {
        // The shape a follow-up really has: the last message carries the notes, well over any
        // length, while what the person typed is a few words.
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("It is about 22 degrees in Tokyo.")

        val earlier = ChatMessage.text(ChatRole.USER, "an old paste ".repeat(100))

        assertThat(answering("weather in tokyo", nobody, before = listOf(earlier)))
            .isEqualTo(NO_ENGINE_REPLY)
    }

    @Test
    fun `results from an earlier turn do not let an invention through`() = runBlocking<Unit> {
        // Tokyo was looked up last turn and worked. This question is about something else.
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("Bitcoin is at about 75,000 dollars.")
        val found = AgentStep.Ran(call("0"), "It is 18 degrees in Tokyo.", millis = 1)

        val notes = ToolNotes().withSteps(listOf(found)) { null }

        assertThat(answering("price of bitcoin", nobody, notes = notes)).isEqualTo(NO_ENGINE_REPLY)
        assertThat(engine.prompts).hasSize(1)
    }

    @Test
    fun `a false denial of the search does not stand beside the search that just ran`() =
        runBlocking<Unit> {
            // The compiled LFM2.5 1.2B's own words on the signed release, on Manila.
            engine.scripted += ScriptedPass(
                "I don't have a tool that can directly pull the current weather for Tokyo.",
            )
            engine.scripted += ScriptedPass("It is about 22 degrees in Tokyo.")

            assertThat(answering("what is the weather in tokyo", nobody))
                .isEqualTo(NO_ENGINE_REPLY)
            assertThat(engine.prompts).hasSize(1)
            assertThat(replaced).containsExactly(NO_ENGINE_REPLY)
        }

    @Test
    fun `a paste the person gave is a way on, so the model is asked`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("The passage is about tides.")

        assertThat(answering("summarise this: " + "tides ".repeat(100), nobody)).contains("tides")
    }

    @Test
    fun `a call this search could still fix goes back to the model`() = runBlocking<Unit> {
        engine.scripted += ScriptedPass("", toolCalls = listOf(call("1")))
        engine.scripted += ScriptedPass("Which city do you mean?")

        assertThat(answering("weather", ToolExecution.failure("No query was given.")))
            .contains("Which city")
        assertThat(engine.prompts).hasSize(2)
    }

    private fun call(id: String) = ToolCall(id, "web_search", """{"query":"weather in tokyo"}""")

    private suspend fun answering(
        question: String,
        vararg answers: ToolExecution,
        also: List<Tool> = emptyList(),
        before: List<ChatMessage> = emptyList(),
        notes: ToolNotes = ToolNotes(),
    ): String {
        engine.load(
            File(models, "model.gguf").apply { writeText("not a real model") },
            ModelLoadParams(contextLength = CONTEXT),
        )
        val replies = answers.iterator()
        val search = object : Tool {
            // A fake stands in for a tool the person switched on.
            override val defaultsOn: Boolean = true
            override val definition = ToolDefinition(
                name = "web_search",
                description = "Search the web.",
                parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
            )

            override suspend fun run(call: ToolCall): String = execute(call).text

            override suspend fun execute(call: ToolCall): ToolExecution = replies.next()
        }
        val runner = TurnRunner(
            engine = engine,
            tools = ToolRegistry(listOf(search) + also),
            switches = ToolSwitches(ApplicationProvider.getApplicationContext()),
            plans = PlanBoard(),
            asks = AskBoard(),
        ).apply {
            honoursIntent = true
            honoursDoubt = true
        }
        return runner.run(
            conversation = before + ChatMessage.text(ChatRole.USER, question),
            params = SamplerParams(),
            mode = AgentMode.AUTO,
            withTools = true,
            notes = notes,
            listener = recording,
            question = question,
        )
    }

    private val recording = object : TurnListener {
        override fun onText(raw: String) = Unit
        override fun onPass(event: GenerationEvent.Completed, raw: String) = Unit
        override fun onSteps(steps: List<AgentStep>) = Unit
        override fun onIntermediate(text: String) = Unit
        override fun onNextPass() = Unit
        override fun onReplyReplaced(text: String) {
            replaced += text
        }

        override suspend fun onApproval(call: ToolCall): Boolean = true
    }

    private fun fake(name: String, chain: Boolean) = object : Tool {
        override val defaultsOn: Boolean = true
        override val chains: Boolean = chain
        override val definition = ToolDefinition(
            name = name,
            description = name,
            parametersJson = """{"type":"object","properties":{}}""",
        )

        override suspend fun run(call: ToolCall): String = "It worked."
    }

    private companion object {
        const val CONTEXT = 4096
        const val UNSURE = -2.5f
    }
}
