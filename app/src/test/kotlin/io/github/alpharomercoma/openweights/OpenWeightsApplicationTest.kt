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

package io.github.alpharomercoma.openweights

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OpenWeightsApplicationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `image loader configures disk cache in cacheDir and enables disk cache policy`() {
        val app = OpenWeightsApplication()
        val imageLoader = app.newImageLoader(context)

        assertThat(imageLoader.defaults.diskCachePolicy).isEqualTo(CachePolicy.ENABLED)
        val diskCache = imageLoader.diskCache
        assertThat(diskCache).isNotNull()
        assertThat(diskCache?.directory?.toFile()).isEqualTo(
            File(context.cacheDir, "image_cache"),
        )
        assertThat(diskCache?.maxSize).isEqualTo(100L * 1024L * 1024L)
    }
}
