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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Replays recorded no-call replies through the real loop and writes what it did with each:
 * the app's own search, a denial repair, the generic push, or nothing.
 *
 * The loop's text matchers are only as good as the phrasings they were tested on, and the
 * phones have produced thousands more than the unit tests hold. This reads them from
 * `build/replay/replies.json` (`[{"uid", "question", "reply"}]`, extracted from the decision
 * rows) and writes `build/replay/outcomes.jsonl`, which `tools/eval/bench/typesafe_matchers.py`
 * compares with an independent reading. Skipped when there is no input, which is always in CI.
 */
@RunWith(RobolectricTestRunner::class)
class MatcherReplayTest {
    private val search = object : Tool {
        override val defaultsOn: Boolean = true
        override val definition = ToolDefinition(
            name = "web_search",
            description = "Search the web.",
            parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
        )

        override suspend fun run(call: ToolCall): String = "result"
    }

    @Test
    fun replay() = runBlocking<Unit> {
        val input = File("build/replay/replies.json")
        assumeTrue("no replay input", input.exists())
        val rows = JSONArray(input.readText())
        val out = StringBuilder()
        val model = File(input.parentFile, "model.gguf").apply { writeText("x") }
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val question = row.getString("question")
            val engine = FakeInferenceEngine().apply { supportsTools = true }
            engine.load(model, ModelLoadParams(contextLength = 8192))
            engine.scripted += ScriptedPass(row.getString("reply"))
            engine.scripted += ScriptedPass("Final.")
            val runner = TurnRunner(
                engine,
                ToolRegistry(listOf(search)),
                ToolSwitches(ApplicationProvider.getApplicationContext()),
                PlanBoard(),
                AskBoard(),
            ).apply { honoursDoubt = false }
            runner.run(
                conversation = listOf(ChatMessage.text(ChatRole.USER, question)),
                params = SamplerParams(),
                mode = AgentMode.AUTO,
                withTools = true,
                notes = ToolNotes(),
                listener = Ignoring,
                question = question,
            )
            val second = engine.prompts.getOrNull(1)
            val outcome = when {
                second == null -> "none"
                second.any {
                    it.role == ChatRole.ASSISTANT &&
                        it.text.startsWith("Searching the web for: ")
                } -> "app_search"
                second.last().text.startsWith("You do have a working tool") -> "denial_tool"
                second.last().text.startsWith(
                    "Write the complete answer yourself",
                ) -> "denial_prose"
                else -> "push"
            }
            out.append(
                JSONObject().put("uid", row.getInt("uid")).put("outcome", outcome).toString(),
            ).append('\n')
        }
        File(input.parentFile, "outcomes.jsonl").writeText(out.toString())
    }

    private object Ignoring : TurnListener {
        override fun onText(raw: String) = Unit
        override fun onPass(event: GenerationEvent.Completed, raw: String) = Unit
        override fun onSteps(steps: List<AgentStep>) = Unit
        override fun onIntermediate(text: String) = Unit
        override fun onNextPass() = Unit
        override suspend fun onApproval(call: ToolCall): Boolean = true
    }
}
