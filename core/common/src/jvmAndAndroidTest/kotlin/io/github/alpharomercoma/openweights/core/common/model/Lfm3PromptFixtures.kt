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
 * Prompts rendered by the `chat_template` that LiquidAI/LFM3-1.7B ships with.
 *
 * Generated, not written. `tools/executorch/render_reference.py` fetches the template from
 * the Hub and renders it with Jinja over the same conversations `Lfm3PromptTest` builds,
 * so these strings are what upstream produces rather than anybody's idea of what it should
 * produce. Do not edit by hand: rerun the script and read the diff.
 */
internal object Lfm3PromptFixtures {
    const val PLAIN: String =
        "<|startoftext|><|im_start|>user\n" +
            "What is the capital of Japan?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>"

    const val THINKING_DISABLED: String =
        "<|startoftext|><|im_start|>user\n" +
            "What is the capital of Japan?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think></think>"

    const val WITH_SYSTEM: String =
        "<|startoftext|><|im_start|>system\n" +
            "You are a terse assistant.<|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the capital of Japan?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>"

    const val WITH_TOOLS: String =
        "<|startoftext|><|im_start|>system\n" +
            "<tools>\n" +
            "{\"name\": \"web_search\", \"description\": \"Search the web for current informa" +
            "tion.\", \"parameters\": {\"type\": \"object\", \"properties\": {\"query\": {\"t" +
            "ype\": \"string\", \"description\": \"What to search for\"}}, \"required\": [\"q" +
            "uery\"]}}\n" +
            "</tools><|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>"

    const val WITH_SYSTEM_AND_TOOLS: String =
        "<|startoftext|><|im_start|>system\n" +
            "You are a terse assistant.\n" +
            "\n" +
            "<tools>\n" +
            "{\"name\": \"web_search\", \"description\": \"Search the web for current informa" +
            "tion.\", \"parameters\": {\"type\": \"object\", \"properties\": {\"query\": {\"t" +
            "ype\": \"string\", \"description\": \"What to search for\"}}, \"required\": [\"q" +
            "uery\"]}}\n" +
            "</tools><|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>"

    const val PRIOR_THINKING_DROPPED: String =
        "<|startoftext|><|im_start|>user\n" +
            "What is 2+2?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think></think>Four.<|im_end|>\n" +
            "<|im_start|>user\n" +
            "And 3+3?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>Also simple.</think>Six.<|im_end|>\n"

    const val TOOL_RUN: String =
        "<|startoftext|><|im_start|>system\n" +
            "<tools>\n" +
            "{\"name\": \"web_search\", \"description\": \"Search the web for current informa" +
            "tion.\", \"parameters\": {\"type\": \"object\", \"properties\": {\"query\": {\"t" +
            "ype\": \"string\", \"description\": \"What to search for\"}}, \"required\": [\"q" +
            "uery\"]}}\n" +
            "</tools><|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think></think><tool_call>\n" +
            "<function=web_search>\n" +
            "<parameter=query>\n" +
            "Manila weather\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call><|im_end|>\n" +
            "<|im_start|>tool\n" +
            "<tool_response>\n" +
            "Manila: 31C, humid.\n" +
            "</tool_response><|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>"

    const val TWO_TOOL_RESULTS: String =
        "<|startoftext|><|im_start|>system\n" +
            "<tools>\n" +
            "{\"name\": \"web_search\", \"description\": \"Search the web for current informa" +
            "tion.\", \"parameters\": {\"type\": \"object\", \"properties\": {\"query\": {\"t" +
            "ype\": \"string\", \"description\": \"What to search for\"}}, \"required\": [\"q" +
            "uery\"]}}\n" +
            "</tools><|im_end|>\n" +
            "<|im_start|>user\n" +
            "Compare Manila and Tokyo.<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think></think>Looking both up.<|im_end|>\n" +
            "<|im_start|>tool\n" +
            "<tool_response>\n" +
            "Manila: 31C.\n" +
            "</tool_response><|im_end|>\n" +
            "<|im_start|>tool\n" +
            "<tool_response>\n" +
            "Tokyo: 22C.\n" +
            "</tool_response><|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>"
}
