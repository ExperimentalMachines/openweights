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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.designsystem.theme.OpenWeightsTheme
import io.github.alpharomercoma.openweights.core.tools.FoundPicture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PictureCarouselTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `swiping leaves reply text fixed and a picture opens and closes in place`() {
        compose.setContent {
            OpenWeightsTheme(dynamicColor = false) {
                Column {
                    PictureCarousel(
                        List(8) { FoundPicture("", "https://example.com/$it") },
                    )
                    Text("Reply after pictures")
                }
            }
        }
        val before = compose.onNodeWithText(
            "Reply after pictures",
        ).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Picture 1 of 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Picture 2 of 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Picture 1 of 8").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(
            compose.onNodeWithText("Reply after pictures").fetchSemanticsNode().boundsInRoot,
        ).isEqualTo(before)
        compose.onNodeWithContentDescription("Picture 2 of 8").performClick()
        compose.onNodeWithText("Picture 2 of 8").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Picture 3 of 8").assertIsDisplayed()
        compose.onNodeWithText("Previous").performClick()
        compose.onNodeWithText("Picture 2 of 8").assertIsDisplayed()
        compose.onNodeWithText("Open source page").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Reply after pictures").assertIsDisplayed()
    }

    @Test
    fun `pinch is clamped and leaving page resets its zoom`() {
        val active = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(300.dp)) {
                ZoomablePicture(active.value) { modifier ->
                    Box(modifier.testTag("zoom-image"))
                }
            }
        }
        val image = compose.onNodeWithTag("zoom-image")
        val original = image.fetchSemanticsNode().boundsInRoot
        image.performTouchInput {
            down(0, Offset(center.x - 20f, center.y))
            down(1, Offset(center.x + 20f, center.y))
            repeat(12) { step ->
                updatePointerTo(0, Offset(center.x - 20f - step * 6f, center.y))
                updatePointerTo(1, Offset(center.x + 20f + step * 6f, center.y))
                move(delayMillis = 16)
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        val zoomed = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription),
        )
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription].removeSuffix(
            "%",
        ).toInt()
        assertThat(zoomed).isGreaterThan(100)
        assertThat(zoomed).isAtMost(500)
        compose.runOnIdle { active.value = false }
        compose.waitForIdle()
        assertThat(image.fetchSemanticsNode().boundsInRoot).isEqualTo(original)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100%"))
            .assertIsDisplayed()
    }
}
