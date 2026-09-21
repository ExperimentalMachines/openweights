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

package io.github.alpharomercoma.openweights.core.hub

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Test

/** The shapes are real: trimmed from the configs Software Mansion publishes. */
class ExportConfigTest {

    @Test
    fun `reads the window each export was compiled with`() {
        val config = """
            {"model": "lfm2_5_text_1_2b", "backend": "xnnpack", "variants": [
              {"file": "lfm_2_5_1_2b_xnnpack_8da4w.pte", "precision": "8da4w",
               "methods": {"forward": {"inputs": [{"shape": [1, 2047], "dtype": "int64"}]},
                           "get_max_context_len": 2048, "get_max_seq_len": 2048}},
              {"file": "lfm_2_5_1_2b_xnnpack_fp16.pte", "precision": "fp16",
               "methods": {"forward": {"inputs": [{"shape": [1, 2047], "dtype": "int64"}]},
                           "get_max_context_len": 2048, "get_max_seq_len": 2048}}
            ]}
        """.trimIndent()

        assertThat(ExportConfig.windowsIn(config)).containsExactly(
            "lfm_2_5_1_2b_xnnpack_8da4w.pte",
            2048,
            "lfm_2_5_1_2b_xnnpack_fp16.pte",
            2048,
        )
    }

    @Test
    fun `the context length outranks the prompt length where they differ`() {
        // Llama 3.2: a 4096 window that takes at most 2048 tokens in one prompt. The window
        // is what the runtime enforces across a conversation, so it is the number shown.
        val config = """
            {"variants": [{"file": "llama_3_2_3b_xnnpack_spinquant.pte",
              "methods": {"get_max_context_len": 4096, "get_max_seq_len": 2048}}]}
        """.trimIndent()

        assertThat(ExportConfig.windowsIn(config))
            .containsExactly("llama_3_2_3b_xnnpack_spinquant.pte", 4096)
    }

    @Test
    fun `falls back to the forward input's shape`() {
        val config = """
            {"variants": [
              {"file": "static.pte", "methods": {"forward": {"inputs": [{"shape": [1, 2047]}]}}},
              {"file": "dynamic.pte", "methods": {"forward": {"inputs": [{"shape": [1, {"min": 1, "max": 8192}]}]}}},
              {"file": "silent.pte", "methods": {}}
            ]}
        """.trimIndent()

        assertThat(ExportConfig.windowsIn(config))
            .containsExactly("static.pte", 2048, "dynamic.pte", 8192)
    }

    @Test
    fun `a transformers config is not an export config`() {
        // Same file name, different meaning: max_position_embeddings is what the model was
        // trained to, and claiming it as the exported window would overstate a 2048 export
        // sixty-fold.
        val config = """{"architectures": ["Lfm2ForCausalLM"], "max_position_embeddings": 128000}"""

        assertThat(ExportConfig.windowsIn(config)).isEmpty()
        assertThat(ExportConfig.windowsIn("not json")).isEmpty()
    }

    @Test
    fun `opening a compiled repository bounds optional config bytes without hiding its model`() =
        runBlocking {
            var bytesRead = 0L
            var closed = false
            val oversized = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val count = minOf(byteCount, 2L * 1024 * 1024 - bytesRead)
                    if (count == 0L) return -1
                    sink.write(ByteArray(count.toInt()) { ' '.code.toByte() })
                    bytesRead += count
                    return count
                }

                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {
                    closed = true
                }
            }.buffer()
            val config = object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength(): Long = -1
                override fun source() = oversized
            }
            val client = configClient(config)

            val detail = client.detail("owner/model")

            assertThat(detail.compiled.map { it.path }).containsExactly("model.pte")
            assertThat(detail.compiled.single().contextWindow).isNull()
            assertThat(bytesRead).isAtMost(1024L * 1024 + 8192)
            assertThat(closed).isTrue()
        }

    @Test
    fun `opening a compiled repository still reads a normal optional config`() = runBlocking {
        val config = """
            {"variants":[{"file":"model.pte","methods":{"get_max_context_len":4096}}]}
        """.trimIndent().toResponseBody("application/json".toMediaType())

        assertThat(configClient(config).detail("owner/model").compiled.single().contextWindow)
            .isEqualTo(4096)
    }

    private fun configClient(config: ResponseBody): HuggingFaceClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("/config.json")) {
                config
            } else {
                """
                {"id":"owner/model","siblings":[
                  {"rfilename":"model.pte"},{"rfilename":"config.json"}
                ]}
                """.trimIndent().toResponseBody("application/json".toMediaType())
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }.build()
        return HuggingFaceClient(client, HubTokenSource { null })
    }
}
