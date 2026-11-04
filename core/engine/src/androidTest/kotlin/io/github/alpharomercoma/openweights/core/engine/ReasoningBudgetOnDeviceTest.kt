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

    private val noTools = emptyList<ToolDefinition>()

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
        assumeWritesTheBareTag(free)

        for (back in 1..TOKENS_BACK) {
            val budgeted = ask(free.stats.generatedTokens - back)
            assertThat(budgeted.reason).isEqualTo(StopReason.END_OF_TURN)
            assertThat(budgeted.toolCalls).isEqualTo(free.toolCalls)
            assertThat(budgeted.content).isEqualTo(free.content)
        }
    }

    @Test
    fun aBudgetThatLandsInTheAnswerLeavesTheAnswerAlone() = runBlocking {
        engine.load(MODEL, ModelLoadParams(contextLength = CONTEXT))

        val free = ask(SamplerParams.NO_REASONING_BUDGET, ANSWERED_FROM_MEMORY, noTools)
        assumeWritesTheBareTag(free)

        val budgeted = ask(free.stats.generatedTokens - TOKENS_BACK, ANSWERED_FROM_MEMORY, noTools)

        assertThat(budgeted.reason).isEqualTo(StopReason.END_OF_TURN)
        assertThat(budgeted.content).isEqualTo(free.content)
    }

    /** The control for the two above: they would all pass with no budget at all. */
    @Test
    fun aBudgetInsideTheReasoningStillClosesIt() = runBlocking {
        engine.load(MODEL, ModelLoadParams(contextLength = CONTEXT))

        val free = ask(SamplerParams.NO_REASONING_BUDGET, ANSWERED_FROM_MEMORY, noTools)
        assumeWritesTheBareTag(free)

        val budgeted = ask(TOKENS_INTO_THE_REASONING, ANSWERED_FROM_MEMORY, noTools)

        assertThat(budgeted.content.split(CLOSE)).hasSize(2)
        assertThat(budgeted.content.substringBefore(CLOSE).length)
            .isLessThan(free.content.substringBefore(CLOSE).length)
    }

    /**
     * The tag belongs to the reasoning, so it must not reach the reader of the answer's opening
     * tokens: one unlikely tag would look like doubt in the answer and start a search.
     */
    @Test
    fun theClosingTagCarriesNoConfidenceAndTheAnswerDoes() = runBlocking {
        engine.load(MODEL, ModelLoadParams(contextLength = CONTEXT))

        val tokens = engine.chat(
            messages = listOf(ChatMessage.text(ChatRole.USER, ANSWERED_FROM_MEMORY)),
            params = deciding(BUDGET_NEVER_REACHED),
        ).filterIsInstance<GenerationEvent.Token>().toList()
        val closing = tokens.indexOfFirst { CLOSE in it.text }
        assumeTrue("the model never closed its reasoning", closing >= 0)

        assertThat(tokens[closing].logprob).isNull()
        // The very next token, because the reader takes the least likely of the opening ones.
        assertThat(tokens.getOrNull(closing + 1)?.logprob).isNotNull()
    }

    private fun assumeWritesTheBareTag(free: GenerationEvent.Completed) {
        // The first tag the template lists always worked, so only the bare one proves anything.
        assumeTrue("the model never closed its reasoning", CLOSE in free.content)
        assumeTrue("the model wrote the template's first tag", "\n$CLOSE" !in free.content)
    }

    private suspend fun ask(
        budget: Int,
        question: String = QUESTION,
        tools: List<ToolDefinition> = listOf(search),
    ): GenerationEvent.Completed {
        val completed = engine.chat(
            messages = listOf(ChatMessage.text(ChatRole.USER, question)),
            params = deciding(budget),
            tools = tools,
        ).filterIsInstance<GenerationEvent.Completed>().toList().single()
        Log.i(
            TAG,
            "budget=$budget generated=${completed.stats.generatedTokens} " +
                "reason=${completed.reason} calls=${completed.toolCalls} " +
                "content=${completed.content.takeLast(LOGGED_CHARS)}",
        )
        return completed
    }

    private fun deciding(budget: Int) = SamplerParams(
        temperature = 0f,
        repeatPenalty = 1f,
        seed = 1,
        maxTokens = MAX_TOKENS,
        reasoningBudget = budget,
    )

    private companion object {
        const val TAG = "ReasoningBudgetTest"
        const val CONTEXT = 4096
        const val MAX_TOKENS = 400
        const val LOGGED_CHARS = 160
        const val CLOSE = "</think>"
        const val TOKENS_INTO_THE_REASONING = 16

        // Past the token cap, so the budget is on but cannot fire: the engine writes a tag of its
        // own at the budget, and that path never carried a logprob, so it would prove nothing.
        const val BUDGET_NEVER_REACHED = MAX_TOKENS + 1

        // The last tokens of a reply that ends in a call are its closing tags, where the
        // grammar accepts no text at all. Further back the budget could land in the reasoning
        // and rightly change the reply. A one-sentence answer is longer than this too.
        const val TOKENS_BACK = 3
        const val QUESTION = "Will it rain in Tokyo tomorrow?"
        const val ANSWERED_FROM_MEMORY = "What is the capital of France? Answer in one sentence."
        val MODEL = File("/data/local/tmp/openweights/eval/LFM3-1.7B-Q4_K_M.gguf")
    }
}
