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

class CancelTicketsTest {

    @Test
    fun `a cancel reaches a request still queued behind another`() {
        // The picker's probe is running; the engine's check is queued behind it; Stop.
        val tickets = CancelTickets()
        val picker = tickets.issue()
        val engine = tickets.issue()

        tickets.cancelIssued()

        assertThat(tickets.cancelled(picker)).isTrue()
        assertThat(tickets.cancelled(engine)).isTrue()
    }

    @Test
    fun `a request made after the cancel runs`() {
        val tickets = CancelTickets()
        tickets.issue()
        tickets.cancelIssued()

        assertThat(tickets.cancelled(tickets.issue())).isFalse()
    }
}
