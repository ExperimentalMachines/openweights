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
 * Reading a compiled model's target silicon off its name.
 *
 * A guess, and unavoidably so: a `.pte` carries no metadata the app can inspect, and the
 * runtime has no API that reports which delegates a loaded file contains. It matters
 * because a model compiled for a backend this build has not linked does not fall back to
 * the CPU — it fails to load — so this is what stops a user spending a gigabyte to find out.
 *
 * The names here are real repositories on the Hub.
 */
class CompiledBackendTest {

    @Test
    fun `reads the backend publishers put in the name`() {
        assertThat(CompiledBackend.of("larryliu0820/Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK"))
            .isEqualTo(CompiledBackend.XNNPACK)
        assertThat(CompiledBackend.of("l3utterfly/Qwen-3-1.7B-qnn-executorch"))
            .isEqualTo(CompiledBackend.QNN)
        assertThat(CompiledBackend.of("someone/Llama-3.2-1B-vulkan"))
            .isEqualTo(CompiledBackend.VULKAN)
        assertThat(CompiledBackend.of("someone/Llama-3.2-1B-mediatek-mdla"))
            .isEqualTo(CompiledBackend.NEUROPILOT)
        // Published beside the XNNPACK export in the same repository, for Apple silicon.
        assertThat(
            CompiledBackend.of(
                "software-mansion/react-native-executorch-lfm-2.51_2b/mlx/lfm_2_5_1_2b_mlx_int4.pte",
            ),
        ).isEqualTo(CompiledBackend.MLX)
    }

    @Test
    fun `says nothing when the name says nothing`() {
        // Deliberately not a guess of XNNPACK. Callers treat unknown as worth trying, which
        // is a decision for them to make rather than one smuggled in here.
        assertThat(CompiledBackend.of("executorch-community/SmolLM2-135M"))
            .isEqualTo(CompiledBackend.UNKNOWN)
    }

    @Test
    fun `knows which silicon each delegate runs on`() {
        // The reason a control that offers CPU, GPU and NPU for one file would be a lie:
        // the file already decided, when somebody exported it.
        assertThat(CompiledBackend.XNNPACK.processor).isEqualTo(CompiledBackend.Processor.CPU)
        assertThat(CompiledBackend.VULKAN.processor).isEqualTo(CompiledBackend.Processor.GPU)
        assertThat(CompiledBackend.QNN.processor).isEqualTo(CompiledBackend.Processor.NPU)
        assertThat(CompiledBackend.NEUROPILOT.processor).isEqualTo(CompiledBackend.Processor.NPU)
    }

    @Test
    fun `is not fooled by case`() {
        assertThat(
            CompiledBackend.of("Someone/MODEL-XNNPack.pte"),
        ).isEqualTo(CompiledBackend.XNNPACK)
    }

    @Test
    fun `reads the two delegates added for the NPU and GPU matrix`() {
        assertThat(
            CompiledBackend.of("experimentalmachines/Qwen3-1.7B-ExecuTorch/enn/e9955/model.pte"),
        )
            .isEqualTo(CompiledBackend.ENN)
        assertThat(
            CompiledBackend.of("someone/Llama-3.2-1B-exynos.pte"),
        ).isEqualTo(CompiledBackend.ENN)
        assertThat(
            CompiledBackend.of("experimentalmachines/Qwen3-1.7B-ExecuTorch/vgf/model-vgf-2k.pte"),
        )
            .isEqualTo(CompiledBackend.VGF)
    }

    @Test
    fun `three letters inside a word are not the Samsung delegate`() {
        // The reason "enn" is matched on segment boundaries: it falls inside ordinary words,
        // and reading one as a Samsung binary would hide a file that runs anywhere.
        assertThat(CompiledBackend.of("kennedy/Qwen3-1.7B-ExecuTorch-XNNPACK.pte"))
            .isEqualTo(CompiledBackend.XNNPACK)
        assertThat(
            CompiledBackend.of("someone/perennial-model.pte"),
        ).isEqualTo(CompiledBackend.UNKNOWN)
    }

    @Test
    fun `knows which delegates are compiled for one chip`() {
        assertThat(CompiledBackend.QNN.isChipLocked).isTrue()
        assertThat(CompiledBackend.NEUROPILOT.isChipLocked).isTrue()
        assertThat(CompiledBackend.ENN.isChipLocked).isTrue()
        // Compiled once, run wherever the delegate is.
        assertThat(CompiledBackend.XNNPACK.isChipLocked).isFalse()
        assertThat(CompiledBackend.VULKAN.isChipLocked).isFalse()
        assertThat(CompiledBackend.VGF.isChipLocked).isFalse()
    }

    @Test
    fun `reads the chip a locked file was compiled for from its path`() {
        // The two strings measured on real devices: Build.SOC_MODEL is SM8750 on a Galaxy
        // S25 Ultra and MT6991 on a Poco X8 Pro, and the exporter publishes under both.
        assertThat(
            CompiledBackend.socIn("qnn/sm8750/Qwen3-0.6B-qnn-hybrid-2k.pte"),
        ).isEqualTo("sm8750")
        assertThat(
            CompiledBackend.socIn("mtk/mt6991/Qwen3-0.6B-neuropilot-a16w4-512-chunk1of4.pte"),
        )
            .isEqualTo("mt6991")
        assertThat(CompiledBackend.socIn("enn/e9955/model.pte")).isEqualTo("e9955")
        // A portable file names no chip, and neither does a bare file name.
        assertThat(CompiledBackend.socIn("xnnpack/Qwen3-0.6B-8da4w-2k.pte")).isNull()
        assertThat(CompiledBackend.socIn("model.pte")).isNull()
    }
}
