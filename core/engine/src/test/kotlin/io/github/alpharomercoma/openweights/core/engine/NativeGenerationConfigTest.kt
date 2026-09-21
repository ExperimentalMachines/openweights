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

class NativeGenerationConfigTest {
    @Test
    fun `generation preserves greedy and sampled load temperatures instead of the AAR default`() {
        for (temperature in listOf(0f, 0.1f, 0.8f)) {
            val config = generationConfig(256, temperature)
            assertThat(config.temperature).isEqualTo(temperature)
            assertThat(config.echo).isFalse()
            assertThat(config.numEos).isEqualTo(0)
            assertThat(config.maxNewTokens).isEqualTo(256)
        }
    }
}
