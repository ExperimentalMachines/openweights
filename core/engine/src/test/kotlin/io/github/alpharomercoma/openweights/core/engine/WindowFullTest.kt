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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowFullTest {
    // As the Snapdragon 8 Elite showed it on 2026-10-11, when a conversation outgrew a QNN export.
    private val qualcommFull = "[ExecuTorch Error 0x12] Invalid argument: Failed to generate"

    @Test
    fun `the text runner's own diagnostics are a full window on any runtime`() {
        assertTrue(
            isWindowFull(
                "num_prompt_tokens 4100 >= max_context_len 4096, Max seq length exceeded",
                false,
            ),
        )
        assertTrue(isWindowFull("Prompt exceeds KV cache capacity", false))
    }

    @Test
    fun `Qualcomm's bare invalid-argument code is a full window only on Qualcomm's runner`() {
        assertTrue(isWindowFull(qualcommFull, qualcomm = true))
        assertFalse(isWindowFull(qualcommFull, qualcomm = false))
    }

    @Test
    fun `other failures are not, on either runner`() {
        val vulkan = "[ExecuTorch Error 0x14] Not supported: Failed to load. max_context_len 2048"
        assertFalse(isWindowFull(vulkan, qualcomm = true))
        assertFalse(isWindowFull(vulkan, qualcomm = false))
    }
}
