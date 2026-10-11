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
import org.junit.Test

class NpuVariantsTest {

    // The shape experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch publishes in mtk/mt6991.
    private val config = """
        {"runtime":"executorch","backend":"mtk","target":"mt6991","tokenizer":"tokenizer.json",
         "source_model":"LiquidAI/LFM2.5-1.2B-Instruct",
         "variants":[
          {"files":["m-neuropilot-a16w8-512-chunk1of2.pte","m-neuropilot-a16w8-512-chunk2of2.pte"],
           "embedding":"m-neuropilot-embedding-fp32.bin","context":512,
           "runner":{"num_layer":16,"cache_size":512}},
          {"files":["m-neuropilot-a16w8-2k-chunk1of2.pte"],"embedding":"m-neuropilot-embedding-fp32.bin",
           "context":2048},
          {"files":[],"embedding":"e.bin","context":4096,"runner":{}}
         ]}
    """.trimIndent()

    @Test
    fun `only whole windows are offered`() {
        val variants = ExportConfig.npuVariantsIn(config)

        // The 2k window has no runner block and the 4k one no chunks: both would install and not run.
        assertThat(variants.map { it.window }).containsExactly(512)
        assertThat(variants.single().chunks).hasSize(2)
        assertThat(variants.single().embedding).isEqualTo("m-neuropilot-embedding-fp32.bin")
        assertThat(variants.single().runnerJson).contains("\"cache_size\":512")
    }

    @Test
    fun `the source model names the CPU half, and a config without one offers nothing`() {
        assertThat(
            ExportConfig.npuVariantsIn(config).single().model,
        ).isEqualTo("LFM2.5-1.2B-Instruct")
        val unnamed = config.replace("\"source_model\":\"LiquidAI/LFM2.5-1.2B-Instruct\",", "")
        assertThat(ExportConfig.npuVariantsIn(unnamed)).isEmpty()
    }

    @Test
    fun `a CPU export's config offers no NPU windows`() {
        val cpu = """
            {"backend":"xnnpack","variants":[
              {"file":"m.pte","context":2048,"files":["x"],"embedding":"e","runner":{}}]}
        """.trimIndent()
        assertThat(ExportConfig.npuVariantsIn(cpu)).isEmpty()
        assertThat(ExportConfig.npuVariantsIn("not json")).isEmpty()
    }
}
