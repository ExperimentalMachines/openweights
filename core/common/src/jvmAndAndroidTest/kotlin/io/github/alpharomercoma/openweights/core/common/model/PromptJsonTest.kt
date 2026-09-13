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
 * The tool schema reaches the prompt spelled the way the family's own chat template
 * would spell it: one line, `", "` between members and `": "` after a key, which is what
 * Jinja's `tojson` writes and therefore what every one of these models was trained on.
 *
 * The real tools declare their schemas as indented multi-line literals, and every
 * template spliced that text in verbatim. Measured on the Mac (2026-09-13, LFM2.5 1.2B
 * Q4_K_M, greedy, the phone's own prompt with the named-subject note): the multi-line
 * spelling took the probability of the tool-call token from 0.84 to 0.48 on "What is
 * Hanover the capital of?", and the same on every other row that names a subject.
 */
class PromptJsonTest {

    @Test
    fun `a multi-line schema is rendered on one line as tojson writes it`() {
        val tool = ToolDefinition(
            name = "web_search",
            description = "Search the web.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "What to look up, as you would type it into a search box"
                    }
                  },
                  "required": ["query"]
                }
            """.trimIndent(),
        )

        assertThat(tool.asToolJson()).isEqualTo(
            """{"type": "function", "function": {"name": "web_search", """ +
                """"description": "Search the web.", "parameters": {"type": "object", """ +
                """"properties": {"query": {"type": "string", """ +
                """"description": "What to look up, as you would type it into a search box"}}, """ +
                """"required": ["query"]}}}""",
        )
    }

    @Test
    fun `canonical JSON leaves strings alone and keeps key order`() {
        val json = "{\n \"b\":\"x, y: {z}\",\n\t\"a\" : [1,\n 2 ,3], " +
            "\"c\":{}, \"d\":\"\\\"q\\\"\" }"

        assertThat(json.canonicalJson())
            .isEqualTo("""{"b": "x, y: {z}", "a": [1, 2, 3], "c": {}, "d": "\"q\""}""")
    }

    @Test
    fun `minified JSON gains the template's spacing and nothing else`() {
        val minified = """{"a":[1.5e-3,-0,"x\\\\","\u00e9"],"b":{"c":[]},"d":true}"""

        assertThat(minified.canonicalJson())
            .isEqualTo("""{"a": [1.5e-3, -0, "x\\\\", "\u00e9"], "b": {"c": []}, "d": true}""")
    }

    @Test
    fun `a compact schema is unchanged`() {
        val compact =
            """{"type": "object", "properties": {"q": {"type": "string"}}, "required": ["q"]}"""

        assertThat(compact.canonicalJson()).isEqualTo(compact)
    }
}
