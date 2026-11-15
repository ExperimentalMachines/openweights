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
 * Renders a conversation the way LFM3's own chat template does.
 *
 * A transcription of `LiquidAI/LFM3-1.7B`'s `chat_template.jinja`, held to it byte for
 * byte by `Lfm3PromptTest` against fixtures `tools/executorch/render_reference.py` renders
 * from the real template. The GGUF carries the same file (compared 2026-11-15), so a
 * compiled LFM3 reads the prompt llama.cpp gives the GGUF. It is not LFM2.5's format: the
 * tools are one JSON object a line inside `<tools>` at the foot of the system turn, without
 * the OpenAI wrapper; a call is XML parameters, the form Qwen3.5 writes; a result is
 * wrapped in `<tool_response>` under a `tool` role; and the assistant opener always carries
 * a think tag, left open when reasoning is on and closed empty when it is off.
 *
 * The template also places pictures, video and tools loaded mid-conversation. The compiled
 * path is text only and its tools are fixed for a turn, so none of that is transcribed.
 *
 * @param verbatimHistory write each assistant turn back as the bytes the runtime was fed
 * for it. The template empties the think block of every turn before the last question, the
 * KV cache still holds it, and LFM3 is a hybrid model that cannot roll back part of its
 * state, so following the template would re-read the conversation on every new question.
 * @param includeBos whether to open with `<|startoftext|>`. The fixtures carry it; the
 * engine renders without and adds it itself where the runtime does not.
 */
object Lfm3Prompt {

    @Suppress("LongParameterList")
    fun render(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList(),
        thinking: Boolean = true,
        addGenerationPrompt: Boolean = true,
        verbatimHistory: Boolean = false,
        includeBos: Boolean = true,
    ): String = buildString {
        if (includeBos) append("<|startoftext|>")

        val leadingSystem = messages.firstOrNull()?.takeIf { it.role == ChatRole.SYSTEM }
        val turns = if (leadingSystem != null) messages.drop(1) else messages

        val system = buildString {
            append(leadingSystem?.text.orEmpty())
            if (tools.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("<tools>")
                tools.forEach { append('\n').append(it.asBareToolJson()) }
                append("\n</tools>")
            }
        }
        if (system.isNotEmpty()) {
            append("<|im_start|>system\n").append(system).append(IM_END)
        }

        val lastUser = turns.indexOfLast { it.role == ChatRole.USER }
        turns.forEachIndexed { index, message ->
            append("<|im_start|>").append(message.role.wireName).append('\n')
            when (message.role) {
                ChatRole.ASSISTANT ->
                    if (verbatimHistory) {
                        appendAsGenerated(message.text)
                    } else {
                        appendAssistant(message.text, keepThinking = index > lastUser)
                    }

                ChatRole.TOOL -> appendToolResult(message.text)
                else -> append(message.text)
            }
            append(IM_END)
        }

        if (addGenerationPrompt) {
            append("<|im_start|>assistant\n").append(if (thinking) THINK_OPEN else EMPTY_THINK)
        }
    }

    /**
     * An assistant turn under the template's own history rules: reasoning is kept after
     * the last question and replaced by an empty block before it, and a turn that never
     * reasoned gets the empty block either way. The app stores a turn as the raw text the
     * model wrote, so a call is already in it in the form the template would have built.
     */
    private fun StringBuilder.appendAssistant(text: String, keepThinking: Boolean) {
        when {
            THINK_CLOSE !in text -> append(EMPTY_THINK).append(text)
            keepThinking -> append(text)
            else -> append(EMPTY_THINK).append(text.substringAfterLast(THINK_CLOSE).trim())
        }
    }

    /**
     * An assistant turn as the bytes the runtime holds for it: the opener it was generated
     * under, then what the model wrote. The opener is never in the reply, so it is rebuilt
     * from the text, the way [Qwen35Prompt] does: a reply holding a close tag was generated
     * under the open tag, one without under the empty block. `assistantHistoryText` may
     * have put the open tag back already, so it is taken off first.
     */
    private fun StringBuilder.appendAsGenerated(text: String) {
        val reply = text.removePrefix(THINK_OPEN)
        append(if (THINK_CLOSE in reply) THINK_OPEN else EMPTY_THINK).append(reply)
    }

    /** A result already wrapped goes in as it is, which is the template's rule. */
    private fun StringBuilder.appendToolResult(text: String) {
        if (RESPONSE_OPEN in text) {
            append(text)
        } else {
            append(RESPONSE_OPEN).append('\n').append(text).append("\n</tool_response>")
        }
    }

    private const val IM_END = "<|im_end|>\n"
    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"
    private const val EMPTY_THINK = "<think></think>"
    private const val RESPONSE_OPEN = "<tool_response>"
}
