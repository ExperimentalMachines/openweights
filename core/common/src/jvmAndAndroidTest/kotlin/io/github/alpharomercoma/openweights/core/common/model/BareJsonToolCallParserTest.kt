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

class BareJsonToolCallParserTest {
    @Test
    fun `llama's bare json object is a call`() {
        val raw = """{"name": "web_search", "parameters": {"query": "Manila weather"}}"""

        val parsed = ToolCallParser.parse(raw)

        assertThat(parsed.calls.single().name).isEqualTo("web_search")
        assertThat(parsed.calls.single().argumentsJson).contains("Manila weather")
        assertThat(parsed.text).isEmpty()
    }

    @Test
    fun `json inside an ordinary answer stays prose`() {
        // The bare form has no markers, so only a reply that is nothing but the object
        // can be read as a call; explaining JSON must not trigger one.
        val raw = """Llama calls look like {"name": "f", "parameters": {}} in text."""

        assertThat(ToolCallParser.parse(raw).calls).isEmpty()
    }

    @Test
    fun `llama's python tag does not hide the call behind it`() {
        // Verbatim from a Poco X8 Pro Max run: Llama-3.2-3B-Instruct wrote exactly this
        // and the call inside was correct; only the tag kept it from being parsed.
        val raw = """<|python_tag|>{"name": "get_weather", "parameters": {"city": "Manila"}}"""

        val parsed = ToolCallParser.parse(raw)

        assertThat(parsed.calls.single().name).isEqualTo("get_weather")
        assertThat(parsed.calls.single().argumentsJson).contains("Manila")

        // Verbatim from a Galaxy S25 Ultra run, Llama 3.2 1B: the fp32 model writes this
        // shape on 22 of 30 BFCL prompts and the parser read every one as prose.
        val declaredRaw =
            """<|python_tag|>{"type": "function", "function": "get_boiling_melting_points", """ +
                """"parameters": {"substance": "water", "sea_level": "5000"}}"""

        val declaredParsed = ToolCallParser.parse(declaredRaw)

        assertThat(declaredParsed.calls.single().name).isEqualTo("get_boiling_melting_points")
        assertThat(declaredParsed.calls.single().argumentsJson).contains("5000")

        // And in either shape, an argument that is itself called "name" is not the function's.
        val plain = """{"name": "add_contact", "parameters": {"name": "Ada", "phone": "555"}}"""
        val declared =
            """{"type": "function", "function": "add_contact", "parameters": {"name": "Ada"}}"""
        assertThat(ToolCallParser.parse(plain).calls.single().name).isEqualTo("add_contact")
        assertThat(ToolCallParser.parse(declared).calls.single().name).isEqualTo("add_contact")
    }

    @Test
    fun `root call fields can follow arguments with their own name`() {
        val plain = """{"parameters":{"name":"Ada"},"name":"add_contact"}"""
        val declared =
            """{"parameters":{"name":"Ada"},"function":"add_contact","type":"function"}"""

        for (raw in listOf(plain, declared)) {
            val call = ToolCallParser.parse(raw).calls.single()
            assertThat(call.name).isEqualTo("add_contact")
            assertThat(call.argumentsJson).isEqualTo("""{"name":"Ada"}""")
        }
    }

    @Test
    fun `nested call fields cannot turn an ordinary object into a call`() {
        val examples = listOf(
            """{"parameters":{"name":"delete_file","path":"notes.txt"}}""",
            """{"example":{"name":"delete_file","parameters":{"path":"notes.txt"}}}""",
            """{"type":"function","parameters":{"function":"delete_file","path":"notes.txt"}}""",
        )

        for (raw in examples) {
            val parsed = ToolCallParser.parse(raw)
            assertThat(parsed.calls).isEmpty()
            assertThat(parsed.text).isEqualTo(raw)
        }
    }

    @Test
    fun `bare calls require one complete object and object-valued parameters`() {
        val examples = listOf(
            """{"name":"delete_file","parameters":{}} {"explanation":"example only"}""",
            """{"name":"delete_file","parameters":"example","other":{"path":"notes.txt"}}""",
            """{"name":"delete_file","parameters":{},"name":"web_search"}""",
        )

        for (raw in examples) {
            assertThat(ToolCallParser.parse(raw).calls).isEmpty()
        }
    }

    @Test
    fun `escaped quotes and braces in arguments do not hide later root fields`() {
        val arguments = """{"text":"say \"hi\", then } {","name":"Ada"}"""
        val raw = """{"parameters":$arguments,"name":"note"}"""

        val call = ToolCallParser.parse(raw).calls.single()

        assertThat(call.name).isEqualTo("note")
        assertThat(call.argumentsJson).isEqualTo(arguments)
    }

    @Test
    fun `a bare object without parameters stays prose`() {
        val raw = """{"name": "Alice", "age": 30}"""

        assertThat(ToolCallParser.parse(raw).calls).isEmpty()
    }
}
