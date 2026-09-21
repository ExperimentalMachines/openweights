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

/**
 * Renders a conversation the way Qwen3.5's own chat template does.
 *
 * A transcription of `Qwen/Qwen3.5-2B`'s `chat_template.jinja`, held to it byte for byte by
 * `Qwen35PromptTest` against fixtures `tools/executorch/render_reference.py` renders from
 * the real template. The name looks like a point release of Qwen3 and the format is not:
 * the tools go above the system message instead of under it, a call is written as XML
 * parameters instead of a JSON object, every piece of content is trimmed, and the
 * assistant opener always carries a think block, left open when reasoning is on and closed
 * empty when it is off. Rendering it with [Qwen3Prompt] would get all four wrong without
 * failing, which is why the family was refused until this file existed.
 *
 * The template also places pictures and video. The compiled path is text only, so this
 * reads [ChatMessage.text], which leaves attachments out, the same as [Qwen3Prompt].
 *
 * @see ToolCallParser for the other half of this, which reads back the XML call.
 */
object Qwen35Prompt {

    /**
     * The conversation as one string, ready to tokenize.
     *
     * @param thinking whether the model may reason before answering. Off by default because
     * that is what the template does when nobody sets `enable_thinking`.
     * @param verbatimHistory write each assistant turn back as the bytes the runtime was
     * fed for it, instead of applying the template's history rules. See [appendAsGenerated].
     * Off by default, so `Qwen35PromptTest` compares against upstream's own output.
     */
    fun render(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList(),
        thinking: Boolean = false,
        addGenerationPrompt: Boolean = true,
        verbatimHistory: Boolean = false,
    ): String = buildString {
        appendSystem(messages, tools)

        val lastQuery = lastQueryIndex(messages)
        messages.forEachIndexed { index, message ->
            when (message.role) {
                ChatRole.USER -> appendBlock("user", message.text.trim())
                ChatRole.ASSISTANT ->
                    if (verbatimHistory) {
                        appendAsGenerated(message.text)
                    } else {
                        appendAssistant(message.text, keepThinking = index > lastQuery)
                    }

                ChatRole.TOOL -> appendToolResult(messages, index)
                // Upstream raises on a system message that is not first. A conversation
                // that throws mid-chat is worse than one rendered the way Qwen3 renders the
                // same thing, so it goes in place; the first one was hoisted above.
                ChatRole.SYSTEM -> if (index > 0) appendBlock("system", message.text.trim())
            }
        }

        if (addGenerationPrompt) {
            append("<|im_start|>assistant\n")
            append(if (thinking) OPEN_THINK else EMPTY_THINK)
        }
    }

    /**
     * The system turn: the tools first, then whatever the conversation's own system
     * message said, which is the reverse of Qwen3 and easy to transcribe from memory wrong.
     * With neither there is no system turn at all.
     */
    private fun StringBuilder.appendSystem(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
    ) {
        val leading = messages.firstOrNull()?.takeIf { it.role == ChatRole.SYSTEM }
        val system = leading?.text.orEmpty().trim()
        if (tools.isEmpty()) {
            // Present but blank still opens a block upstream; only absence skips it.
            if (leading != null) appendBlock("system", system)
            return
        }

        append("<|im_start|>system\n")
        append(TOOLS_PREAMBLE)
        tools.forEach { append('\n').append(it.asToolJson()) }
        append(TOOLS_EPILOGUE)
        if (system.isNotEmpty()) append("\n\n").append(system)
        append("<|im_end|>\n")
    }

    /**
     * An assistant turn under the template's own history rules.
     *
     * The app stores a turn as the raw text the model wrote, so a call is already in the
     * content in the XML form the template would have built from a structured `tool_calls`
     * field, and the two agree; `Qwen35PromptTest` holds them to it.
     *
     * Reasoning ends at the first close tag and the answer begins after the last one, which
     * is the template's arithmetic and only differs on a malformed reply with two blocks.
     * Unlike Qwen3, a turn after the last question gets its think block written back even
     * when it is empty, so a turn generated with reasoning off reads exactly as it was fed.
     */
    private fun StringBuilder.appendAssistant(text: String, keepThinking: Boolean) {
        val raw = text.trim()
        val firstClose = raw.indexOf(THINK_CLOSE)
        val reasoning = if (firstClose < 0) {
            ""
        } else {
            raw.take(firstClose).trimEnd('\n').substringAfterLast(THINK_OPEN).trimStart('\n')
        }
        val content = if (firstClose < 0) {
            raw
        } else {
            raw.substring(raw.lastIndexOf(THINK_CLOSE) + THINK_CLOSE.length).trimStart('\n')
        }

        append("<|im_start|>assistant\n")
        if (keepThinking) append(OPEN_THINK).append(reasoning.trim()).append("\n</think>\n\n")
        append(content).append("<|im_end|>\n")
    }

    /**
     * An assistant turn as the bytes the runtime holds for it: the opener it was generated
     * under, then what the model wrote.
     *
     * A deliberate divergence from upstream, for a runtime whose KV cache holds what was
     * generated. The template drops the think block from every turn before the last
     * question, the cache still has it, and Qwen3.5 is a hybrid model that cannot roll back
     * part of its state, so following the template would re-read the whole conversation on
     * every new question.
     *
     * The opener is never in the reply, so it is rebuilt from the text. A reply holding a
     * close tag was generated with reasoning on, under [OPEN_THINK]; one without was
     * generated with reasoning off, under [EMPTY_THINK]. `assistantHistoryText` may have put
     * a bare open tag back on either, without the newline the opener had, so it is taken off
     * first. A reply cut off mid-thought has no close tag and reads as the second kind; the
     * runtime gives up its cache on a cut-off reply, so there is nothing for it to mismatch.
     */
    private fun StringBuilder.appendAsGenerated(text: String) {
        val reply = text.removePrefix(THINK_OPEN)
        append("<|im_start|>assistant\n")
        append(if (THINK_CLOSE in reply) OPEN_THINK else EMPTY_THINK)
        append(reply).append("<|im_end|>\n")
    }

    /**
     * A run of results is one user turn holding several responses, which is what the
     * template does and why this looks at its neighbours instead of only at itself.
     */
    private fun StringBuilder.appendToolResult(messages: List<ChatMessage>, index: Int) {
        if (index == 0 || messages[index - 1].role != ChatRole.TOOL) append("<|im_start|>user")
        append("\n<tool_response>\n").append(messages[index].text.trim())
        append("\n</tool_response>")
        if (index == messages.lastIndex || messages[index + 1].role != ChatRole.TOOL) {
            append("<|im_end|>\n")
        }
    }

    /**
     * Where the user last actually asked something. A user turn that is only a wrapped
     * tool response is looked past, as the template does; with no question anywhere the
     * template raises, and this falls back to the last index so nothing keeps its thinking.
     */
    private fun lastQueryIndex(messages: List<ChatMessage>): Int {
        for (index in messages.indices.reversed()) {
            val message = messages[index]
            if (message.role != ChatRole.USER) continue
            val text = message.text.trim()
            val wrapped = text.startsWith(RESPONSE_OPEN) && text.endsWith(RESPONSE_CLOSE)
            if (!wrapped) return index
        }
        return messages.size - 1
    }

    private fun StringBuilder.appendBlock(role: String, content: String) {
        append("<|im_start|>").append(role).append('\n').append(content).append("<|im_end|>\n")
    }

    private const val RESPONSE_OPEN = "<tool_response>"
    private const val RESPONSE_CLOSE = "</tool_response>"

    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"

    /** The opener's think block with reasoning on: open, for the model to write into. */
    private const val OPEN_THINK = "<think>\n"

    /** The same with reasoning off: closed with nothing in it. */
    private const val EMPTY_THINK = "<think>\n\n</think>\n\n"

    private const val TOOLS_PREAMBLE =
        "# Tools\n\nYou have access to the following functions:\n\n<tools>"

    private const val TOOLS_EPILOGUE =
        "\n</tools>\n\nIf you choose to call a function ONLY reply in the following format " +
            "with NO suffix:\n\n<tool_call>\n<function=example_function_name>\n" +
            "<parameter=example_parameter_1>\nvalue_1\n</parameter>\n" +
            "<parameter=example_parameter_2>\nThis is the value for the second parameter\n" +
            "that can span\nmultiple lines\n</parameter>\n</function>\n</tool_call>\n\n" +
            "<IMPORTANT>\nReminder:\n- Function calls MUST follow the specified format: an " +
            "inner <function=...></function> block must be nested within " +
            "<tool_call></tool_call> XML tags\n- Required parameters MUST be specified\n" +
            "- You may provide optional reasoning for your function call in natural language " +
            "BEFORE the function call, but NOT after\n- If there is no function call " +
            "available, answer the question like normal with your current knowledge and do " +
            "not tell the user about function calls\n</IMPORTANT>"
}
