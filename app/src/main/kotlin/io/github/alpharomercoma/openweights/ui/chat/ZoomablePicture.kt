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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

@Composable
internal fun ZoomablePicture(
    active: Boolean,
    aspectRatio: Float = 1f,
    content: @Composable (Modifier) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun bounded(value: Offset, zoom: Float): Offset {
        val fittedWidth = min(size.width.toFloat(), size.height * aspectRatio)
        val fittedHeight = fittedWidth / aspectRatio
        val x = ((fittedWidth * zoom - size.width) / 2f).coerceAtLeast(0f)
        val y = ((fittedHeight * zoom - size.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    val state = rememberTransformableState { centroid, zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        val focus = if (centroid.isSpecified) {
            centroid - Offset(size.width / 2f, size.height / 2f)
        } else {
            Offset.Zero
        }
        offset = bounded((offset - focus) * (next / scale) + focus + pan, next)
        scale = next
    }
    // A page leaving the viewport relinquishes its transform, including when kept composed.
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(size, aspectRatio) { offset = bounded(offset, scale) }
    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
            .semantics { stateDescription = "${(scale * 100).toInt()}%" }
            .transformable(
                state = state,
                canPan = { pan ->
                    val next = bounded(offset + pan, scale)
                    scale > 1f &&
                        if (abs(pan.x) > abs(pan.y)) {
                            next.x != offset.x
                        } else {
                            next.y != offset.y
                        }
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2f
                    offset = Offset.Zero
                })
            },
    ) {
        content(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}
