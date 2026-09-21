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
 * Prompts rendered by the `chat_template` that Qwen/Qwen3.5-2B ships with.
 *
 * Generated, not written. `tools/executorch/render_reference.py` fetches the template from
 * the Hub and renders it with Jinja over the same conversations `Qwen35PromptTest` builds,
 * so these strings are what upstream produces rather than anybody's idea of what it should
 * produce. Do not edit by hand: rerun the script and read the diff.
 */
internal object Qwen35PromptFixtures {
    const val PLAIN: String =
        "<|im_start|>user\n" +
            "What is the capital of Japan?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"

    const val THINKING_ENABLED: String =
        "<|im_start|>user\n" +
            "What is the capital of Japan?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n"

    const val WITH_SYSTEM: String =
        "<|im_start|>system\n" +
            "You are a terse assistant.<|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the capital of Japan?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"

    const val TRIMS_CONTENT: String =
        "<|im_start|>system\n" +
            "You are a terse assistant.<|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the capital of Japan?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"

    const val WITH_TOOLS: String =
        "<|im_start|>system\n" +
            "# Tools\n" +
            "\n" +
            "You have access to the following functions:\n" +
            "\n" +
            "<tools>\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"web_search\", \"description" +
            "\": \"Search the web for current information.\", \"parameters\": {\"type\": \"ob" +
            "ject\", \"properties\": {\"query\": {\"type\": \"string\", \"description\": \"Wh" +
            "at to search for\"}}, \"required\": [\"query\"]}}}\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"get_weather\", \"description" +
            "\": \"Current weather for one city.\", \"parameters\": {\"type\": \"object\", \"" +
            "properties\": {\"city\": {\"type\": \"string\", \"description\": \"City name\"}," +
            " \"unit\": {\"type\": \"string\", \"enum\": [\"celsius\", \"fahrenheit\"]}}, \"r" +
            "equired\": [\"city\"]}}}\n" +
            "</tools>\n" +
            "\n" +
            "If you choose to call a function ONLY reply in the following format with NO suff" +
            "ix:\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=example_function_name>\n" +
            "<parameter=example_parameter_1>\n" +
            "value_1\n" +
            "</parameter>\n" +
            "<parameter=example_parameter_2>\n" +
            "This is the value for the second parameter\n" +
            "that can span\n" +
            "multiple lines\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call>\n" +
            "\n" +
            "<IMPORTANT>\n" +
            "Reminder:\n" +
            "- Function calls MUST follow the specified format: an inner <function=...></func" +
            "tion> block must be nested within <tool_call></tool_call> XML tags\n" +
            "- Required parameters MUST be specified\n" +
            "- You may provide optional reasoning for your function call in natural language " +
            "BEFORE the function call, but NOT after\n" +
            "- If there is no function call available, answer the question like normal with y" +
            "our current knowledge and do not tell the user about function calls\n" +
            "</IMPORTANT><|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"

    const val WITH_SYSTEM_AND_TOOLS: String =
        "<|im_start|>system\n" +
            "# Tools\n" +
            "\n" +
            "You have access to the following functions:\n" +
            "\n" +
            "<tools>\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"web_search\", \"description" +
            "\": \"Search the web for current information.\", \"parameters\": {\"type\": \"ob" +
            "ject\", \"properties\": {\"query\": {\"type\": \"string\", \"description\": \"Wh" +
            "at to search for\"}}, \"required\": [\"query\"]}}}\n" +
            "</tools>\n" +
            "\n" +
            "If you choose to call a function ONLY reply in the following format with NO suff" +
            "ix:\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=example_function_name>\n" +
            "<parameter=example_parameter_1>\n" +
            "value_1\n" +
            "</parameter>\n" +
            "<parameter=example_parameter_2>\n" +
            "This is the value for the second parameter\n" +
            "that can span\n" +
            "multiple lines\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call>\n" +
            "\n" +
            "<IMPORTANT>\n" +
            "Reminder:\n" +
            "- Function calls MUST follow the specified format: an inner <function=...></func" +
            "tion> block must be nested within <tool_call></tool_call> XML tags\n" +
            "- Required parameters MUST be specified\n" +
            "- You may provide optional reasoning for your function call in natural language " +
            "BEFORE the function call, but NOT after\n" +
            "- If there is no function call available, answer the question like normal with y" +
            "our current knowledge and do not tell the user about function calls\n" +
            "</IMPORTANT>\n" +
            "\n" +
            "You are a terse assistant.<|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"

    const val PRIOR_THINKING_DROPPED: String =
        "<|im_start|>user\n" +
            "What is 2+2?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "Four.<|im_end|>\n" +
            "<|im_start|>user\n" +
            "And 3+3?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n"

    const val TOOL_RUN: String =
        "<|im_start|>system\n" +
            "# Tools\n" +
            "\n" +
            "You have access to the following functions:\n" +
            "\n" +
            "<tools>\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"web_search\", \"description" +
            "\": \"Search the web for current information.\", \"parameters\": {\"type\": \"ob" +
            "ject\", \"properties\": {\"query\": {\"type\": \"string\", \"description\": \"Wh" +
            "at to search for\"}}, \"required\": [\"query\"]}}}\n" +
            "</tools>\n" +
            "\n" +
            "If you choose to call a function ONLY reply in the following format with NO suff" +
            "ix:\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=example_function_name>\n" +
            "<parameter=example_parameter_1>\n" +
            "value_1\n" +
            "</parameter>\n" +
            "<parameter=example_parameter_2>\n" +
            "This is the value for the second parameter\n" +
            "that can span\n" +
            "multiple lines\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call>\n" +
            "\n" +
            "<IMPORTANT>\n" +
            "Reminder:\n" +
            "- Function calls MUST follow the specified format: an inner <function=...></func" +
            "tion> block must be nested within <tool_call></tool_call> XML tags\n" +
            "- Required parameters MUST be specified\n" +
            "- You may provide optional reasoning for your function call in natural language " +
            "BEFORE the function call, but NOT after\n" +
            "- If there is no function call available, answer the question like normal with y" +
            "our current knowledge and do not tell the user about function calls\n" +
            "</IMPORTANT><|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "I should look this up.\n" +
            "</think>\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=web_search>\n" +
            "<parameter=query>\n" +
            "Manila weather\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call><|im_end|>\n" +
            "<|im_start|>user\n" +
            "<tool_response>\n" +
            "Manila: 31C, humid.\n" +
            "</tool_response><|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n"

    const val TOOL_RUN_NO_THINKING: String =
        "<|im_start|>system\n" +
            "# Tools\n" +
            "\n" +
            "You have access to the following functions:\n" +
            "\n" +
            "<tools>\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"web_search\", \"description" +
            "\": \"Search the web for current information.\", \"parameters\": {\"type\": \"ob" +
            "ject\", \"properties\": {\"query\": {\"type\": \"string\", \"description\": \"Wh" +
            "at to search for\"}}, \"required\": [\"query\"]}}}\n" +
            "</tools>\n" +
            "\n" +
            "If you choose to call a function ONLY reply in the following format with NO suff" +
            "ix:\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=example_function_name>\n" +
            "<parameter=example_parameter_1>\n" +
            "value_1\n" +
            "</parameter>\n" +
            "<parameter=example_parameter_2>\n" +
            "This is the value for the second parameter\n" +
            "that can span\n" +
            "multiple lines\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call>\n" +
            "\n" +
            "<IMPORTANT>\n" +
            "Reminder:\n" +
            "- Function calls MUST follow the specified format: an inner <function=...></func" +
            "tion> block must be nested within <tool_call></tool_call> XML tags\n" +
            "- Required parameters MUST be specified\n" +
            "- You may provide optional reasoning for your function call in natural language " +
            "BEFORE the function call, but NOT after\n" +
            "- If there is no function call available, answer the question like normal with y" +
            "our current knowledge and do not tell the user about function calls\n" +
            "</IMPORTANT><|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=web_search>\n" +
            "<parameter=query>\n" +
            "Manila weather\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call><|im_end|>\n" +
            "<|im_start|>user\n" +
            "<tool_response>\n" +
            "Manila: 31C, humid.\n" +
            "</tool_response><|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"

    const val TOOL_RUN_THEN_FOLLOW_UP: String =
        "<|im_start|>system\n" +
            "# Tools\n" +
            "\n" +
            "You have access to the following functions:\n" +
            "\n" +
            "<tools>\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"web_search\", \"description" +
            "\": \"Search the web for current information.\", \"parameters\": {\"type\": \"ob" +
            "ject\", \"properties\": {\"query\": {\"type\": \"string\", \"description\": \"Wh" +
            "at to search for\"}}, \"required\": [\"query\"]}}}\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"get_weather\", \"description" +
            "\": \"Current weather for one city.\", \"parameters\": {\"type\": \"object\", \"" +
            "properties\": {\"city\": {\"type\": \"string\", \"description\": \"City name\"}," +
            " \"unit\": {\"type\": \"string\", \"enum\": [\"celsius\", \"fahrenheit\"]}}, \"r" +
            "equired\": [\"city\"]}}}\n" +
            "</tools>\n" +
            "\n" +
            "If you choose to call a function ONLY reply in the following format with NO suff" +
            "ix:\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=example_function_name>\n" +
            "<parameter=example_parameter_1>\n" +
            "value_1\n" +
            "</parameter>\n" +
            "<parameter=example_parameter_2>\n" +
            "This is the value for the second parameter\n" +
            "that can span\n" +
            "multiple lines\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call>\n" +
            "\n" +
            "<IMPORTANT>\n" +
            "Reminder:\n" +
            "- Function calls MUST follow the specified format: an inner <function=...></func" +
            "tion> block must be nested within <tool_call></tool_call> XML tags\n" +
            "- Required parameters MUST be specified\n" +
            "- You may provide optional reasoning for your function call in natural language " +
            "BEFORE the function call, but NOT after\n" +
            "- If there is no function call available, answer the question like normal with y" +
            "our current knowledge and do not tell the user about function calls\n" +
            "</IMPORTANT><|im_end|>\n" +
            "<|im_start|>user\n" +
            "What is the weather in Manila?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<tool_call>\n" +
            "<function=get_weather>\n" +
            "<parameter=city>\n" +
            "Manila\n" +
            "</parameter>\n" +
            "<parameter=unit>\n" +
            "celsius\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call><|im_end|>\n" +
            "<|im_start|>user\n" +
            "<tool_response>\n" +
            "Manila: 31C, humid.\n" +
            "</tool_response><|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "It is 31C and humid in Manila.<|im_end|>\n" +
            "<|im_start|>user\n" +
            "And in Tokyo?<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"

    const val TWO_TOOL_CALLS: String =
        "<|im_start|>system\n" +
            "# Tools\n" +
            "\n" +
            "You have access to the following functions:\n" +
            "\n" +
            "<tools>\n" +
            "{\"type\": \"function\", \"function\": {\"name\": \"web_search\", \"description" +
            "\": \"Search the web for current information.\", \"parameters\": {\"type\": \"ob" +
            "ject\", \"properties\": {\"query\": {\"type\": \"string\", \"description\": \"Wh" +
            "at to search for\"}}, \"required\": [\"query\"]}}}\n" +
            "</tools>\n" +
            "\n" +
            "If you choose to call a function ONLY reply in the following format with NO suff" +
            "ix:\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=example_function_name>\n" +
            "<parameter=example_parameter_1>\n" +
            "value_1\n" +
            "</parameter>\n" +
            "<parameter=example_parameter_2>\n" +
            "This is the value for the second parameter\n" +
            "that can span\n" +
            "multiple lines\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call>\n" +
            "\n" +
            "<IMPORTANT>\n" +
            "Reminder:\n" +
            "- Function calls MUST follow the specified format: an inner <function=...></func" +
            "tion> block must be nested within <tool_call></tool_call> XML tags\n" +
            "- Required parameters MUST be specified\n" +
            "- You may provide optional reasoning for your function call in natural language " +
            "BEFORE the function call, but NOT after\n" +
            "- If there is no function call available, answer the question like normal with y" +
            "our current knowledge and do not tell the user about function calls\n" +
            "</IMPORTANT><|im_end|>\n" +
            "<|im_start|>user\n" +
            "Compare Manila and Tokyo.<|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n" +
            "Looking both up.\n" +
            "\n" +
            "<tool_call>\n" +
            "<function=web_search>\n" +
            "<parameter=query>\n" +
            "Manila weather\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call>\n" +
            "<tool_call>\n" +
            "<function=web_search>\n" +
            "<parameter=query>\n" +
            "Tokyo weather\n" +
            "</parameter>\n" +
            "</function>\n" +
            "</tool_call><|im_end|>\n" +
            "<|im_start|>user\n" +
            "<tool_response>\n" +
            "Manila: 31C.\n" +
            "</tool_response>\n" +
            "<tool_response>\n" +
            "Tokyo: 22C.\n" +
            "</tool_response><|im_end|>\n" +
            "<|im_start|>assistant\n" +
            "<think>\n" +
            "\n" +
            "</think>\n" +
            "\n"
}
