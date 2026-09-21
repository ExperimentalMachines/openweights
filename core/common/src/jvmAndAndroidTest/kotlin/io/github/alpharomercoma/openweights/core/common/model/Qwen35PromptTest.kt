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
 * Holds [Qwen35Prompt] to what Qwen3.5's own template produces, byte for byte.
 *
 * Every expected string in [Qwen35PromptFixtures] came out of the real Jinja template. The
 * assistant turns here are the raw text a model writes, call markup included, because that
 * is how the app stores them; the fixtures were rendered from the structured `tool_calls`
 * form upstream expects, and the two agreeing is the point.
 */
class Qwen35PromptTest {

    @Test
    fun `renders a bare conversation with reasoning off, as the template defaults to`() {
        val rendered = Qwen35Prompt.render(listOf(user("What is the capital of Japan?")))

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.PLAIN)
        assertThat(rendered).endsWith("<think>\n\n</think>\n\n")
    }

    @Test
    fun `leaves the think block open when reasoning is on`() {
        val rendered = Qwen35Prompt.render(
            listOf(user("What is the capital of Japan?")),
            thinking = true,
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.THINKING_ENABLED)
        assertThat(rendered).endsWith("assistant\n<think>\n")
    }

    @Test
    fun `renders a leading system message`() {
        val rendered = Qwen35Prompt.render(
            listOf(system("You are a terse assistant."), user("What is the capital of Japan?")),
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.WITH_SYSTEM)
    }

    @Test
    fun `trims every piece of content, which Qwen3 does not`() {
        val rendered = Qwen35Prompt.render(
            listOf(
                system("\nYou are a terse assistant.\n"),
                user("  What is the capital of Japan?\n\n"),
            ),
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.TRIMS_CONTENT)
    }

    @Test
    fun `declares tools in a system block of their own, one json object a line`() {
        val rendered = Qwen35Prompt.render(
            listOf(user("What is the weather in Manila?")),
            listOf(SEARCH, WEATHER),
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.WITH_TOOLS)
    }

    @Test
    fun `puts the system message under the tools, the reverse of Qwen3`() {
        val rendered = Qwen35Prompt.render(
            listOf(system("You are a terse assistant."), user("What is the weather in Manila?")),
            listOf(SEARCH),
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.WITH_SYSTEM_AND_TOOLS)
        assertThat(rendered).contains("</IMPORTANT>\n\nYou are a terse assistant.<|im_end|>")
    }

    @Test
    fun `drops thinking from turns before the user's last question`() {
        val rendered = Qwen35Prompt.render(
            listOf(
                user("What is 2+2?"),
                assistant("<think>\nSimple arithmetic.\n</think>\n\nFour."),
                user("And 3+3?"),
            ),
            thinking = true,
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.PRIOR_THINKING_DROPPED)
        assertThat(rendered).doesNotContain("Simple arithmetic")
    }

    @Test
    fun `keeps thinking inside the run that is still going`() {
        val rendered = Qwen35Prompt.render(
            listOf(
                user("What is the weather in Manila?"),
                assistant("<think>\nI should look this up.\n</think>\n\n$MANILA_SEARCH"),
                toolResult("Manila: 31C, humid."),
            ),
            listOf(SEARCH),
            thinking = true,
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.TOOL_RUN)
    }

    @Test
    fun `writes an empty think block back on a running turn that never thought`() {
        val rendered = Qwen35Prompt.render(
            listOf(
                user("What is the weather in Manila?"),
                assistant(MANILA_SEARCH),
                toolResult("Manila: 31C, humid."),
            ),
            listOf(SEARCH),
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.TOOL_RUN_NO_THINKING)
    }

    @Test
    fun `renders a finished tool run as history once a new question arrives`() {
        val rendered = Qwen35Prompt.render(
            listOf(
                user("What is the weather in Manila?"),
                assistant(MANILA_CELSIUS),
                toolResult("Manila: 31C, humid."),
                assistant("It is 31C and humid in Manila."),
                user("And in Tokyo?"),
            ),
            listOf(SEARCH, WEATHER),
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.TOOL_RUN_THEN_FOLLOW_UP)
    }

    @Test
    fun `renders two calls in one turn and their two results in one user turn`() {
        val rendered = Qwen35Prompt.render(
            listOf(
                user("Compare Manila and Tokyo."),
                assistant("Looking both up.\n\n$MANILA_SEARCH\n$TOKYO_SEARCH"),
                toolResult("Manila: 31C."),
                toolResult("Tokyo: 22C."),
            ),
            listOf(SEARCH),
        )

        assertThat(rendered).isEqualTo(Qwen35PromptFixtures.TWO_TOOL_CALLS)
        assertThat(rendered.split("<|im_start|>user").size - 1).isEqualTo(2)
    }

    @Test
    fun `verbatim history is a continuation of what the runtime was fed, reasoning off`() {
        // The engine's cache record is the prompt plus the reply, and the next prompt has
        // to start with it or a hybrid model re-reads the conversation. The history strings
        // are built the way the app builds them: `<think>` restored on the chat path when
        // the opener closed the block, the raw text alone inside the tool loop.
        val asked = listOf(user("What is the weather in Manila?"))
        val fedCall = render(asked) + MANILA_SEARCH
        val called = asked + assistant(assistantHistoryText(MANILA_SEARCH)) +
            toolResult("Manila: 31C, humid.")
        val fedAnswer = render(called) + "It is 31C."
        val next = called + assistant(assistantHistoryText("It is 31C.", true)) + user("Thanks.")

        assertThat(render(called)).startsWith("$fedCall<|im_end|>\n")
        assertThat(render(next)).startsWith("$fedAnswer<|im_end|>\n")
    }

    @Test
    fun `verbatim history is a continuation of what the runtime was fed, reasoning on`() {
        // The opener holds `<think>` and its newline, so the reply starts mid-thought.
        val reply = "Capital cities. Easy.\n</think>\n\nTokyo."
        val asked = listOf(user("What is the capital of Japan?"))
        val fed = render(asked, thinking = true) + reply
        val next = asked + assistant(assistantHistoryText(reply, false)) + user("And of Peru?")

        assertThat(render(next, thinking = true)).startsWith("$fed<|im_end|>\n")
        // Upstream would have dropped it; the cache has it, so it stays.
        assertThat(render(next, thinking = true)).contains("Capital cities. Easy.")
    }

    @Test
    fun `the parser reads back the xml call this template asks for`() {
        val one = ToolCallParser.parse(MANILA_SEARCH).calls.single()
        val two = ToolCallParser.parse("Let me check.\n\n$MANILA_CELSIUS")

        assertThat(one.name).isEqualTo("web_search")
        assertThat(one.argumentsJson).isEqualTo("""{"query": "Manila weather"}""")
        assertThat(two.calls.single().name).isEqualTo("get_weather")
        assertThat(two.calls.single().argumentsJson)
            .isEqualTo("""{"city": "Manila", "unit": "celsius"}""")
        assertThat(two.text).isEqualTo("Let me check.")
    }

    private fun render(messages: List<ChatMessage>, thinking: Boolean = false) =
        Qwen35Prompt.render(messages, listOf(SEARCH), thinking, verbatimHistory = true)

    private fun system(text: String) = ChatMessage.text(ChatRole.SYSTEM, text)
    private fun user(text: String) = ChatMessage.text(ChatRole.USER, text)
    private fun assistant(text: String) = ChatMessage.text(ChatRole.ASSISTANT, text)
    private fun toolResult(text: String) = ChatMessage.toolResult("web_search", text)

    private companion object {
        /**
         * The same tools the reference renderer describes. Compact on purpose: the
         * template writes `tool | tojson`, and [asToolJson] is what has to turn this
         * spelling into that one.
         */
        val SEARCH = ToolDefinition(
            name = "web_search",
            description = "Search the web for current information.",
            parametersJson = """{"type":"object","properties":{"query":{"type":"string",""" +
                """"description":"What to search for"}},"required":["query"]}""",
        )
        val WEATHER = ToolDefinition(
            name = "get_weather",
            description = "Current weather for one city.",
            parametersJson = """{"type":"object","properties":{"city":{"type":"string",""" +
                """"description":"City name"},"unit":{"type":"string","enum":["celsius",""" +
                """"fahrenheit"]}},"required":["city"]}""",
        )

        const val MANILA_SEARCH = "<tool_call>\n<function=web_search>\n<parameter=query>\n" +
            "Manila weather\n</parameter>\n</function>\n</tool_call>"
        const val TOKYO_SEARCH = "<tool_call>\n<function=web_search>\n<parameter=query>\n" +
            "Tokyo weather\n</parameter>\n</function>\n</tool_call>"
        const val MANILA_CELSIUS = "<tool_call>\n<function=get_weather>\n<parameter=city>\n" +
            "Manila\n</parameter>\n<parameter=unit>\ncelsius\n</parameter>\n</function>\n" +
            "</tool_call>"
    }
}
