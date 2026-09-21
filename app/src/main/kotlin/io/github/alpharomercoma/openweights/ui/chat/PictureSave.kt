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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.Image
import coil3.toBitmap
import io.github.alpharomercoma.openweights.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

@Composable
internal fun PictureSaveButton(image: Image?, identity: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val saved = remember { mutableStateListOf<String>() }
    val alreadySaved = identity in saved
    TextButton(
        enabled = image != null && !saving && !alreadySaved,
        onClick = {
            val selected = image ?: return@TextButton
            val selectedIdentity = identity
            saving = true
            scope.launch {
                try {
                    savePicture(context, selected)
                    saved.add(selectedIdentity)
                    Toast.makeText(
                        context,
                        R.string.picture_saved_location,
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.picture_save_failed, Toast.LENGTH_LONG).show()
                } finally {
                    saving = false
                }
            }
        },
    ) {
        Text(
            stringResource(
                if (saving) {
                    R.string.picture_saving
                } else if (alreadySaved) {
                    R.string.picture_saved
                } else {
                    R.string.save
                },
            ),
        )
    }
}

/** Saves the displayed image without another network request or broad storage permission. */
internal suspend fun savePicture(context: Context, image: Image) = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "OpenWeights-${UUID.randomUUID()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OpenWeights")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Could not create picture")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            check(image.toBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
        } ?: throw IOException("Could not open picture")
        check(
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            ) == 1,
        )
    } catch (failure: Exception) {
        resolver.delete(uri, null, null)
        throw failure
    }
}

private const val PNG_QUALITY = 100
