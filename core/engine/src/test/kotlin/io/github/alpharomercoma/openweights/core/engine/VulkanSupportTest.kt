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

package io.github.alpharomercoma.openweights.core.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VulkanSupportTest {
    @Test
    fun aShaderTheDriverCannotRunIsAnIncompatibility() {
        val failure = LlamaException(
            "ExecuTorch could not open this model: Error 0x1\n" +
                "E vk_api: Shader q_8w_linear not compatible with device. " +
                "Missing support for extension or physical device feature: VK_KHR_8bit_storage",
        )
        assertThat(VulkanSupport.incompatibility(failure)).contains("not compatible with device")
    }

    @Test
    fun aRuntimeWithoutAVulkanDeviceIsAnIncompatibility() {
        val cause = RuntimeException("Backend VulkanBackend is not available")
        assertThat(
            VulkanSupport.incompatibility(LlamaException("load failed").initCause(cause)),
        ).isNotNull()
    }

    @Test
    fun ordinaryFailuresNeverCostThePhoneItsGpuBuilds() {
        // A model too large for this GPU, a full window and a broken file are this model's
        // problems, not the GPU's: a smaller or different Vulkan export may still run.
        listOf(
            "vmaCreateBuffer failed: VK_ERROR_OUT_OF_DEVICE_MEMORY",
            "num_prompt_tokens 4100 >= max_context_len 4096, Max seq length exceeded",
            "Failed to load tokenizer",
            "Program verification failed",
        ).forEach { message ->
            assertThat(VulkanSupport.incompatibility(LlamaException(message))).isNull()
        }
        assertThat(VulkanSupport.incompatibility(null)).isNull()
    }
}
