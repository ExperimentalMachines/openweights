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

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import io.github.alpharomercoma.openweights.core.common.model.ToolDefinition
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The reasoning budget stops counting when the block ends, however the block ended.
 *
 * A template lists several tags that end its reasoning. LFM3's are `\n</think>`, `</think>`
 * and `<tool_call>`, and the model writes the second one. The budget only watched for the
 * first, so it kept counting into the reply and then wrote the closing tag there. Inside a
 * tool call that goes through a grammar with no way to accept it, and the turn died with
 * "Unexpected empty grammar stack" on 2026-11-04: "Will it rain in Tokyo tomorrow?" failed
 * three times out of three on the POCO, because the tool pass is greedy.
 *
 * The budgets are read off the model's own unbudgeted reply so that they land past the end
 * of the block whatever the file's reasoning happens to say.
 */
@RunWith(AndroidJUnit4::class)
class ReasoningBudgetOnDeviceTest {
    private lateinit var engine: InferenceEngine

    private val search = ToolDefinition(
        name = "web_search",
        description = "Search the web for current information.",
        parametersJson = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "What to search for" }
              },
              "required": ["query"]
            }
        """.trimIndent(),
    )

    @Before
    fun setUp() {
        assumeTrue("no test model at ${MODEL.path}", MODEL.isFile)
        engine = LlamaCppEngine()
    }

    @After
    fun tearDown() {
        if (::engine.isInitialized) runBlocking { engine.close() }
    }

    @Test
    fun aBudgetThatLandsInsideACallLeavesTheCallAlone() = runBlocking {
        engine.load(MODEL, ModelLoadParams(contextLength = CONTEXT))

        val free = ask(SamplerParams.NO_REASONING_BUDGET)
        assumeTrue("the model answered without a call", free.toolCalls.isNotEmpty())

        for (back in 1..TOKENS_BACK) {
            val budget = free.stats.generatedTokens - back
            val budgeted = ask(budget)
            Log.i(TAG, "budget=$budget reason=${budgeted.reason} calls=${budgeted.toolCalls}")
            assertThat(budgeted.toolCalls).isEqualTo(free.toolCalls)
        }
    }

    @Test
    fun aBudgetThatLandsInTheAnswerLeavesTheAnswerAlone() = runBlocking {
        engine.load(MODEL, ModelLoadParams(contextLength = CONTEXT))

        val free = ask(SamplerParams.NO_REASONING_BUDGET, ANSWERED_FROM_MEMORY, tools = emptyList())
        assumeTrue("the model never closed its reasoning", "</think>" in free.content)

        val budget = free.stats.generatedTokens - TOKENS_BACK
        val budgeted = ask(budget, ANSWERED_FROM_MEMORY, tools = emptyList())

        assertThat(budgeted.content).isEqualTo(free.content)
    }

    private suspend fun ask(
        budget: Int,
        question: String = QUESTION,
        tools: List<ToolDefinition> = listOf(search),
    ): GenerationEvent.Completed {
        val completed = engine.chat(
            messages = listOf(ChatMessage.text(ChatRole.USER, question)),
            params = SamplerParams(
                temperature = 0f,
                repeatPenalty = 1f,
                seed = 1,
                maxTokens = MAX_TOKENS,
                reasoningBudget = budget,
            ),
            tools = tools,
        ).filterIsInstance<GenerationEvent.Completed>().toList().single()
        Log.i(
            TAG,
            "budget=$budget generated=${completed.stats.generatedTokens} " +
                "calls=${completed.toolCalls} content=${completed.content.takeLast(160)}",
        )
        return completed
    }

    private companion object {
        const val TAG = "ReasoningBudgetTest"
        const val CONTEXT = 4096
        const val MAX_TOKENS = 400

        // The last tokens of a reply that ends in a call are its closing tags, where the
        // grammar accepts no text at all. Further back the budget could land in the reasoning
        // and rightly change the reply. A one-sentence answer is longer than this too.
        const val TOKENS_BACK = 3
        const val QUESTION = "Will it rain in Tokyo tomorrow?"
        const val ANSWERED_FROM_MEMORY = "What is the capital of France? Answer in one sentence."
        val MODEL = File("/data/local/tmp/openweights/eval/LFM3-1.7B-Q4_K_M.gguf")
    }
}
