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

package io.github.alpharomercoma.openweights.core.common.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Holds [Lfm3Prompt] to what LFM3's own template produces, byte for byte.
 */
class Lfm3PromptTest {

    @Test
    fun `leaves the think block open when reasoning is on`() {
        assertThat(Lfm3Prompt.render(listOf(QUESTION))).isEqualTo(Lfm3PromptFixtures.PLAIN)
    }

    @Test
    fun `closes the think block empty when reasoning is off`() {
        val rendered = Lfm3Prompt.render(listOf(QUESTION), thinking = false)

        assertThat(rendered).isEqualTo(Lfm3PromptFixtures.THINKING_DISABLED)
    }

    @Test
    fun `renders a leading system message`() {
        val rendered = Lfm3Prompt.render(listOf(TERSE, QUESTION))

        assertThat(rendered).isEqualTo(Lfm3PromptFixtures.WITH_SYSTEM)
    }

    @Test
    fun `lists tools one bare function object a line`() {
        val rendered = Lfm3Prompt.render(listOf(WEATHER), TOOLS)

        assertThat(rendered).isEqualTo(Lfm3PromptFixtures.WITH_TOOLS)
        assertThat(rendered).doesNotContain("\"type\": \"function\"")
    }

    @Test
    fun `puts the tools under the system message`() {
        val rendered = Lfm3Prompt.render(listOf(TERSE, WEATHER), TOOLS)

        assertThat(rendered).isEqualTo(Lfm3PromptFixtures.WITH_SYSTEM_AND_TOOLS)
    }

    @Test
    fun `empties the thinking of turns before the last question`() {
        val rendered = Lfm3Prompt.render(HISTORY, addGenerationPrompt = false)

        assertThat(rendered).isEqualTo(Lfm3PromptFixtures.PRIOR_THINKING_DROPPED)
    }

    @Test
    fun `verbatim history keeps every turn as it was generated`() {
        // What the cache holds: the open tag the first turn was generated under, then the
        // reply. `assistantHistoryText` may or may not have put the tag back; both agree.
        val stored = Lfm3Prompt.render(HISTORY.take(3), verbatimHistory = true)
        val bare = Lfm3Prompt.render(
            listOf(HISTORY[0], assistant("Simple arithmetic.</think>Four."), HISTORY[2]),
            verbatimHistory = true,
        )

        assertThat(stored).contains("assistant\n<think>Simple arithmetic.</think>Four.<|im_end|>")
        assertThat(bare).isEqualTo(stored)
    }

    @Test
    fun `verbatim history rebuilds the empty block of a turn that did not reason`() {
        val rendered = Lfm3Prompt.render(
            listOf(HISTORY[0], assistant("Four."), HISTORY[2]),
            thinking = false,
            verbatimHistory = true,
        )

        assertThat(rendered).contains("assistant\n<think></think>Four.<|im_end|>")
        assertThat(rendered).endsWith("assistant\n<think></think>")
    }

    @Test
    fun `renders an XML call and its wrapped result`() {
        val rendered = Lfm3Prompt.render(
            listOf(WEATHER, assistant(CALL), toolResult("Manila: 31C, humid.")),
            TOOLS,
        )

        assertThat(rendered).isEqualTo(Lfm3PromptFixtures.TOOL_RUN)
        // The other half: the call the template writes is one the parser reads.
        val parsed = ToolCallParser.parse(CALL)
        assertThat(parsed.calls.single().name).isEqualTo("web_search")
        assertThat(parsed.calls.single().argumentsJson).contains("Manila weather")
    }

    @Test
    fun `keeps two results as two tool turns`() {
        val rendered = Lfm3Prompt.render(
            listOf(
                user("Compare Manila and Tokyo."),
                assistant("Looking both up."),
                toolResult("Manila: 31C."),
                toolResult("Tokyo: 22C."),
            ),
            TOOLS,
        )

        assertThat(rendered).isEqualTo(Lfm3PromptFixtures.TWO_TOOL_RESULTS)
    }

    private companion object {
        fun user(text: String) = ChatMessage.text(ChatRole.USER, text)
        fun assistant(text: String) = ChatMessage.text(ChatRole.ASSISTANT, text)
        fun toolResult(text: String) = ChatMessage.toolResult("web_search", text)

        val TERSE = ChatMessage.text(ChatRole.SYSTEM, "You are a terse assistant.")
        val QUESTION = user("What is the capital of Japan?")
        val WEATHER = user("What is the weather in Manila?")
        val HISTORY = listOf(
            user("What is 2+2?"),
            assistant("<think>Simple arithmetic.</think>Four."),
            user("And 3+3?"),
            assistant("<think>Also simple.</think>Six."),
        )
        const val CALL = "<tool_call>\n<function=web_search>\n<parameter=query>\n" +
            "Manila weather\n</parameter>\n</function>\n</tool_call>"
        val TOOLS = listOf(
            ToolDefinition(
                name = "web_search",
                description = "Search the web for current information.",
                parametersJson = "{\"type\": \"object\", \"properties\": {\"query\": " +
                    "{\"type\": \"string\", \"description\": \"What to search for\"}}, " +
                    "\"required\": [\"query\"]}",
            ),
        )
    }
}
