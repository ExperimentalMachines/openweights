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
import io.github.alpharomercoma.openweights.core.tools.AdvanceTool
import io.github.alpharomercoma.openweights.core.tools.AgentMode
import io.github.alpharomercoma.openweights.core.tools.AgentStep
import io.github.alpharomercoma.openweights.core.tools.AskBoard
import io.github.alpharomercoma.openweights.core.tools.AskUserTool
import io.github.alpharomercoma.openweights.core.tools.NamedSubject
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
 * The loop's closed questions to the model, each behind its own switch: the gate before the
 * first pass, the follow-up query, and the denial's tool. What every one of them must keep
 * is the path it replaces, wherever the model cannot be asked or its answer cannot be read.
 */
@RunWith(RobolectricTestRunner::class)
class JudgedDecisionsTest {
    private val models: File = Files.createTempDirectory("openweights-judged").toFile()
    private val engine = FakeInferenceEngine().apply { supportsTools = true }
    private val search = object : Tool {
        override val defaultsOn: Boolean = true
        override val definition = ToolDefinition(
            name = "web_search",
            description = "Search the web.",
            parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
        )

        override suspend fun run(call: ToolCall): String =
            "Results for \"killua\" from DuckDuckGo.\n[1] Killua - Wiki\nA character.\nhttps://example.org/killua\n"
    }

    private val fetched = mutableListOf<String>()
    private val fetch = object : Tool {
        override val definition = ToolDefinition(
            name = "fetch_url",
            description = "Read a web page.",
            parametersJson = """{"type":"object","properties":{"url":{"type":"string"}}}""",
        )

        override suspend fun run(call: ToolCall): String {
            fetched += call.argumentsJson
            return "Killua Zoldyck is a character in Hunter x Hunter, from the Zoldyck family."
        }
    }

    @Test
    fun `with the gate off nothing is asked`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0f, 1f) }

        answering("who is killua zoldyck")

        assertThat(engine.judgeCalls).isEmpty()
    }

    @Test
    fun `a model that is not sure is searched for before it answers`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0.1f, 0.9f) }
        engine.scripted += ScriptedPass("Killua Zoldyck is from Hunter x Hunter.")

        val reply = answering("who is killua zoldyck", gate = true)

        // Asked about the question as typed, before the name note is attached.
        val asked = engine.judgeCalls.single()
        assertThat(asked.instruction).isEqualTo(JudgeQuestions.UNSURE)
        assertThat(asked.options).containsExactly("Yes", "No").inOrder()
        assertThat(asked.messages.last().text).isEqualTo("who is killua zoldyck")
        // One pass, and it already has the results; the note is moot and not added.
        assertThat(engine.prompts).hasSize(1)
        assertThat(engine.prompts[0].last().role).isEqualTo(ChatRole.TOOL)
        assertThat(engine.prompts[0].none { it.text.contains("This question names") }).isTrue()
        assertThat(reply).contains("Hunter x Hunter")
    }

    @Test
    fun `a model that is sure answers, and the name note still goes on`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0.9f, 0.1f) }
        engine.scripted += ScriptedPass("Killua Zoldyck is from Hunter x Hunter.")

        answering("who is killua zoldyck", gate = true)

        assertThat(engine.prompts).hasSize(1)
        assertThat(engine.prompts[0].last().role).isEqualTo(ChatRole.USER)
        assertThat(engine.prompts[0].last().text)
            .contains(
                NamedSubject.trailer(requireNotNull(NamedSubject.of("who is killua zoldyck"))),
            )
    }

    @Test
    fun `an answer the options barely held is not read`() = runBlocking<Unit> {
        // The failure the mass floor exists for: renormalised, 0.9 for No; in fact the model
        // wanted to say something else entirely.
        engine.judgeAnswer = { listOf(0.1f, 0.9f) }
        engine.judgeMass = 0.001f
        engine.scripted += ScriptedPass("Killua Zoldyck is from Hunter x Hunter.")

        answering("who is killua zoldyck", gate = true)

        assertThat(engine.prompts[0].last().role).isEqualTo(ChatRole.USER)
    }

    @Test
    fun `an engine that cannot judge leaves the turn as it was`() = runBlocking<Unit> {
        engine.judgeAnswer = { null }
        engine.scripted += ScriptedPass("Killua Zoldyck is from Hunter x Hunter.")

        answering("who is killua zoldyck", gate = true)

        assertThat(engine.judgeCalls).hasSize(1)
        assertThat(engine.prompts).hasSize(1)
        assertThat(engine.prompts[0].last().text).contains("This question names")
    }

    @Test
    fun `the gate asks nothing about the user's own things or in plan mode`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0f, 1f) }

        answering("what is my wifi password", gate = true)
        answering("who is killua zoldyck", gate = true, mode = AgentMode.PLAN)

        assertThat(engine.judgeCalls).isEmpty()
    }

    @Test
    fun `a pronoun question the model says stands alone is searched alone`() = runBlocking<Unit> {
        engine.judgeAnswer = { call ->
            if (call.instruction == JudgeQuestions.FOLLOW_UP) listOf(0.1f, 0.9f) else null
        }
        engine.scripted += ScriptedPass("Let me look that up.")
        engine.scripted += ScriptedPass("Rain is likely.")

        answering(
            "is it going to rain today",
            earlier = "who directed The Last Word",
            followUps = true,
        )

        assertThat(searchedFor()).isEqualTo("is it going to rain today")
    }

    @Test
    fun `a pronoun question the model says leans on the last one is joined to it`() =
        runBlocking<Unit> {
            engine.judgeAnswer = { listOf(0.9f, 0.1f) }
            engine.scripted += ScriptedPass("Let me look that up.")
            engine.scripted += ScriptedPass("In 2014.")

            answering(
                "when was it released",
                earlier = "who directed The Last Word",
                followUps = true,
            )

            assertThat(searchedFor()).isEqualTo("who directed The Last Word when was it released")
        }

    @Test
    fun `with follow-ups off the pronoun joins as it always did`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0.1f, 0.9f) }
        engine.scripted += ScriptedPass("Let me look that up.")
        engine.scripted += ScriptedPass("Rain is likely.")

        answering("is it going to rain today", earlier = "who directed The Last Word")

        assertThat(engine.judgeCalls).isEmpty()
        assertThat(searchedFor()).isEqualTo("who directed The Last Word is it going to rain today")
    }

    @Test
    fun `a denial is repaired towards the tool the model chooses`() = runBlocking<Unit> {
        // The keywords read "write" and would withhold the tools; the model says search.
        engine.judgeAnswer = { listOf(0.8f, 0.2f) }
        engine.scripted += ScriptedPass("I can't write that because I don't have a tool for poems.")
        engine.scripted += ScriptedPass("Here it is.")

        answering("write a poem about today's headlines", denials = true)

        val asked = engine.judgeCalls.single()
        assertThat(asked.options).containsExactly("A", "B").inOrder()
        assertThat(asked.instruction).contains("A: web_search, Search the web.")
        assertThat(asked.instruction).contains("B: none of them")
        assertThat(asked.messages.last().role).isEqualTo(ChatRole.ASSISTANT)
        assertThat(
            engine.prompts[1].last().text,
        ).contains("You do have a working tool for exactly this: web_search")
    }

    @Test
    fun `a denial the model says no tool fits is written in prose`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0.1f, 0.9f) }
        engine.scripted += ScriptedPass("I can't write that because I don't have a tool for poems.")
        engine.scripted += ScriptedPass("Roses are red.")

        answering("write a poem about roses", denials = true)

        assertThat(engine.prompts[1].last().text).startsWith("Write the complete answer yourself")
    }

    @Test
    fun `a denial choice that cannot be read falls back to the keywords`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0.8f, 0.2f) }
        engine.judgeMass = 0.01f
        engine.scripted += ScriptedPass("I can't write that because I don't have a tool for poems.")
        engine.scripted += ScriptedPass("Roses are red.")

        answering("write a poem about roses", denials = true)

        assertThat(engine.prompts[1].last().text).startsWith("Write the complete answer yourself")
    }

    @Test
    fun `results the model says lack the answer have their top page read`() = runBlocking<Unit> {
        engine.judgeAnswer =
            { call ->
                if (call.instruction ==
                    JudgeQuestions.SUFFICIENT
                ) {
                    listOf(0.2f, 0.8f)
                } else {
                    null
                }
            }
        engine.scripted += searching()
        engine.scripted += ScriptedPass("Killua is from Hunter x Hunter.")

        answering("who is killua zoldyck", reads = true, fetchOn = true)

        assertThat(
            org.json.JSONObject(fetched.single()).getString("url"),
        ).isEqualTo("https://example.org/killua")
        val prompt = engine.prompts[1]
        assertThat(
            prompt.any {
                it.text == "Reading the top result: https://example.org/killua"
            },
        ).isTrue()
        assertThat(prompt.last().role).isEqualTo(ChatRole.TOOL)
        // Asked after the results, about the prompt the answering pass is about to read.
        assertThat(
            engine.judgeCalls.single {
                it.instruction == JudgeQuestions.SUFFICIENT
            }.messages.last().role,
        )
            .isEqualTo(ChatRole.TOOL)
    }

    @Test
    fun `results the model says answer the question are not read further`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0.9999f, 0.0001f) }
        engine.scripted += searching()
        engine.scripted += ScriptedPass("Killua is from Hunter x Hunter.")

        answering("who is killua zoldyck", reads = true, fetchOn = true)

        assertThat(fetched).isEmpty()
    }

    @Test
    fun `no page is read with the fetch switch off or the reading off`() = runBlocking<Unit> {
        engine.judgeAnswer = { listOf(0.1f, 0.9f) }
        engine.scripted += searching()
        engine.scripted += ScriptedPass("Killua is from Hunter x Hunter.")
        answering("who is killua zoldyck", reads = true, fetchOn = false)

        engine.scripted += searching()
        engine.scripted += ScriptedPass("Killua is from Hunter x Hunter.")
        answering("who is killua zoldyck", reads = false, fetchOn = true)

        assertThat(fetched).isEmpty()
    }

    private fun searching() = ScriptedPass(
        text = "",
        toolCalls = listOf(
            ToolCall(
                id = "c1",
                name = "web_search",
                argumentsJson = """{"query":"killua zoldyck"}""",
            ),
        ),
    )

    /** The query the app's own search ran with, from the line the loop writes before results. */
    private fun searchedFor(): String = engine.prompts.last()
        .last { it.role == ChatRole.ASSISTANT && it.text.startsWith("Searching the web for: ") }
        .text.removePrefix("Searching the web for: ")

    @Suppress("LongParameterList")
    private suspend fun answering(
        question: String,
        gate: Boolean = false,
        followUps: Boolean = false,
        denials: Boolean = false,
        reads: Boolean = false,
        fetchOn: Boolean = false,
        mode: AgentMode = AgentMode.AUTO,
        earlier: String? = null,
    ): String {
        engine.load(modelFile(), ModelLoadParams(contextLength = CONTEXT))
        val plans = PlanBoard()
        val asks = AskBoard()
        val runner = TurnRunner(
            engine = engine,
            tools = ToolRegistry(listOf(search, fetch, AdvanceTool(plans), AskUserTool(asks))),
            switches = ToolSwitches(
                ApplicationProvider.getApplicationContext<android.app.Application>(),
            )
                .apply { setEnabled("fetch_url", fetchOn) },
            plans = plans,
            asks = asks,
        ).apply {
            honoursGate = gate
            judgesFollowUps = followUps
            judgesDenials = denials
            readsTopResult = reads
            // The doubt gate reads probabilities the fake does not give; left on, as in the app.
        }
        val history = earlier?.let {
            listOf(
                ChatMessage.text(ChatRole.USER, it),
                ChatMessage.text(ChatRole.ASSISTANT, "Somebody directed it."),
            )
        }.orEmpty()
        return runner.run(
            conversation = history + ChatMessage.text(ChatRole.USER, question),
            params = SamplerParams(),
            mode = mode,
            withTools = true,
            notes = ToolNotes(),
            listener = Ignoring,
            question = question,
        )
    }

    private fun modelFile(): File =
        File(models, "model.gguf").apply { writeText("not a real model") }

    private object Ignoring : TurnListener {
        override fun onText(raw: String) = Unit
        override fun onPass(event: GenerationEvent.Completed, raw: String) = Unit
        override fun onSteps(steps: List<AgentStep>) = Unit
        override fun onIntermediate(text: String) = Unit
        override fun onNextPass() = Unit
        override suspend fun onApproval(call: ToolCall): Boolean = true
    }

    private companion object {
        const val CONTEXT = 4096
    }
}
