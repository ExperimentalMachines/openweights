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

class UnopenableTest {

    private val registered = setOf("XnnpackBackend", "VulkanBackend")

    @Test
    fun `an XNNPACK export with its metadata opens`() {
        val backends = mapOf(
            "forward" to listOf("XnnpackBackend"),
            "get_max_seq_len" to emptyList(),
        )

        assertThat(unopenable(statesPrefill = true, backends, registered)).isNull()
    }

    @Test
    fun `a delegate this build did not link is refused by name`() {
        val backends = mapOf("forward" to listOf("NeuropilotBackend"))

        assertThat(unopenable(statesPrefill = true, backends, registered))
            .contains("NeuropilotBackend")
    }

    @Test
    fun `an NPU chunk without get_max_seq_len is refused`() {
        // The file that aborted the app: half of a disaggregated model, no window methods.
        val backends = mapOf("forward" to listOf("XnnpackBackend"))

        assertThat(unopenable(statesPrefill = false, backends, registered))
            .contains("get_max_seq_len")
    }

    @Test
    fun `a method with no delegate is plain CPU and needs nothing linked`() {
        val backends = mapOf("forward" to emptyList<String>())

        assertThat(unopenable(statesPrefill = true, backends, registered)).isNull()
    }
}
