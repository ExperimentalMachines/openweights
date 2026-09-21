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

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.Image
import coil3.compose.SubcomposeAsyncImage
import io.github.alpharomercoma.openweights.R
import io.github.alpharomercoma.openweights.core.tools.FoundPicture
import kotlinx.coroutines.launch

/** Fixed bounds keep swiping and image loading from moving the reply beneath the gallery. */
@Composable
internal fun PictureCarousel(pictures: List<FoundPicture>, modifier: Modifier = Modifier) {
    if (pictures.isEmpty()) return
    var selected by rememberSaveable(pictures) { mutableStateOf<Int?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(144.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pictures) { index, picture ->
                Picture(
                    picture,
                    index,
                    pictures.size,
                    Modifier.size(144.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { selected = index },
                )
            }
        }
        Text(stringResource(R.string.picture_swipe), style = MaterialTheme.typography.labelSmall)
    }
    selected?.let { index ->
        PictureViewer(pictures, index.coerceIn(pictures.indices)) { selected = null }
    }
}

@Composable
private fun PictureViewer(pictures: List<FoundPicture>, initial: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = initial, pageCount = { pictures.size })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        ) {
            val loaded = remember(pictures) { mutableMapOf<Int, Image>() }
            var currentImage by remember(pager.currentPage) {
                mutableStateOf(loaded[pager.currentPage])
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.picture_position, pager.currentPage + 1, pictures.size),
                    modifier = Modifier.weight(1f),
                )
                PictureSaveButton(
                    currentImage.takeUnless { pager.isScrollInProgress },
                    pictures[pager.currentPage].thumbnail,
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                var aspect by remember(pictures[index].thumbnail) { mutableFloatStateOf(1f) }
                ZoomablePicture(
                    aspectRatio = aspect,
                    active = pager.settledPage == index && !pager.isScrollInProgress,
                ) { zoom ->
                    Picture(
                        pictures[index],
                        index,
                        pictures.size,
                        zoom,
                        ContentScale.Fit,
                        onLoaded = { image ->
                            aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
                            loaded[index] = image
                            if (pager.currentPage == index) currentImage = image
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    enabled = pager.currentPage > 0 && !pager.isScrollInProgress,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                ) { Text(stringResource(R.string.picture_previous)) }
                TextButton(onClick = {
                    val source = pictures[pager.currentPage].source
                    if (isWebAddress(source)) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, source.toUri()))
                        }
                    }
                }) { Text(stringResource(R.string.picture_source)) }
                TextButton(
                    enabled = pager.currentPage < pictures.lastIndex && !pager.isScrollInProgress,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                ) { Text(stringResource(R.string.picture_next)) }
            }
        }
    }
}

@Composable
private fun Picture(
    picture: FoundPicture,
    index: Int,
    count: Int,
    modifier: Modifier,
    scale: ContentScale = ContentScale.Crop,
    onLoaded: (Image) -> Unit = {},
) {
    val description = stringResource(R.string.picture_position, index + 1, count)
    SubcomposeAsyncImage(
        model = picture.thumbnail,
        contentDescription = null,
        onSuccess = { onLoaded(it.result.image) },
        onError = { Log.i("OpenWeights", "Picture preview failed", it.result.throwable) },
        contentScale = scale,
        modifier = modifier.semantics { contentDescription = description }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("…", style = MaterialTheme.typography.titleLarge)
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.picture_unavailable),
                    modifier = Modifier.padding(8.dp),
                )
            }
        },
    )
}
