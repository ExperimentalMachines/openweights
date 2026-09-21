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

package io.github.alpharomercoma.openweights.core.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidReachabilityTest {
    @Test
    fun `loss pauses tools even while the synchronous network snapshot is stale`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = checkNotNull(manager.activeNetwork)
        val valid = NetworkCapabilities().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(manager).setNetworkCapabilities(network, valid)
        val reachability = AndroidReachability(context)
        val callback = shadowOf(manager).networkCallbacks.single()
        assertThat(reachability.isOnline()).isTrue()

        callback.onLost(network)
        assertThat(reachability.online.value).isFalse()
        assertThat(reachability.isOnline()).isFalse()

        callback.onCapabilitiesChanged(network, valid)
        assertThat(reachability.online.value).isTrue()
        assertThat(reachability.isOnline()).isTrue()

        val captive = NetworkCapabilities().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        callback.onCapabilitiesChanged(network, captive)
        assertThat(reachability.isOnline()).isFalse()
    }
}
