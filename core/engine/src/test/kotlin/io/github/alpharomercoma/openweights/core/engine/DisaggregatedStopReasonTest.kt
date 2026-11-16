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
import io.github.alpharomercoma.openweights.core.engine.DisaggregatedExecuTorchBridge.Companion.endedOnStopToken
import io.github.alpharomercoma.openweights.core.engine.DisaggregatedExecuTorchBridge.Companion.stopReasonFor
import org.junit.Test

/** The MediaTek runner's reason codes, as tools/npu/mtk_pd_disaggregated_jni.cpp returns them. */
class DisaggregatedStopReasonTest {

    @Test
    fun `only the end-of-turn code is a stop on a stop token`() {
        assertThat(endedOnStopToken(0)).isTrue()
        assertThat(endedOnStopToken(1)).isFalse()
        assertThat(endedOnStopToken(2)).isFalse()
        assertThat(endedOnStopToken(3)).isFalse()
        assertThat(endedOnStopToken(7)).isFalse()
    }

    @Test
    fun `codes map to the engine's reasons, an unknown one to the end of a turn`() {
        assertThat(stopReasonFor(0)).isEqualTo(StopReason.END_OF_TURN)
        assertThat(stopReasonFor(1)).isEqualTo(StopReason.MAX_TOKENS)
        assertThat(stopReasonFor(2)).isEqualTo(StopReason.CANCELLED)
        assertThat(stopReasonFor(7)).isEqualTo(StopReason.END_OF_TURN)
    }
}
