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

package io.github.alpharomercoma.openweights.ui.chat

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import coil3.asImage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
class PictureSaveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `successful save publishes only after bytes are written`() = runBlocking {
        val provider = RecordingMedia(File(context.cacheDir, "picture.png"))
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        savePicture(context, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImage())
        assertThat(provider.inserted?.getAsInteger(MediaStore.Images.Media.IS_PENDING)).isEqualTo(1)
        assertThat(provider.inserted?.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
            .isEqualTo("Pictures/OpenWeights")
        assertThat(provider.updated?.getAsInteger(MediaStore.Images.Media.IS_PENDING)).isEqualTo(0)
        assertThat(provider.output.length()).isGreaterThan(0)
        assertThat(provider.deleted).isFalse()
    }

    @Test
    fun `failed save deletes unfinished entry`() = runBlocking {
        val provider = RecordingMedia(File(context.cacheDir, "failed.png"), fail = true)
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val result = runCatching {
            savePicture(context, Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImage())
        }
        assertThat(result.isFailure).isTrue()
        assertThat(provider.deleted).isTrue()
        assertThat(provider.updated).isNull()
    }

    private class RecordingMedia(val output: File, val fail: Boolean = false) : ContentProvider() {
        var inserted: ContentValues? = null
        var updated: ContentValues? = null
        var deleted = false
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            inserted = values?.let(::ContentValues)
            return Uri.parse("content://media/external/images/media/1")
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (fail) throw FileNotFoundException("Storage unavailable")
            return ParcelFileDescriptor.open(
                output,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE,
            )
        }
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            updated = values?.let(::ContentValues)
            return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted = true
            return 1
        }
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
    }
}
