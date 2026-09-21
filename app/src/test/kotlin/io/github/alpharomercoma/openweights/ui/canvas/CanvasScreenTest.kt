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

package io.github.alpharomercoma.openweights.ui.canvas

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.designsystem.theme.OpenWeightsTheme
import io.github.alpharomercoma.openweights.core.tools.CanvasBoard
import io.github.alpharomercoma.openweights.core.tools.CanvasKind
import io.github.alpharomercoma.openweights.core.tools.CanvasServer
import io.github.alpharomercoma.openweights.core.tools.Workspace
import io.github.alpharomercoma.openweights.core.tools.WorkspaceGrant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-night-xxhdpi")
class CanvasScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val board = CanvasBoard()
    private val server = CanvasServer(Workspace(context, WorkspaceGrant(context)), board, context)

    @After
    fun stopServer() {
        server.stop()
    }

    @Test
    fun `leaving the local preview needs explicit consent on every launch`() {
        board.show(CanvasKind.SITE, "site/index.html", "Site")
        val viewModel = CanvasViewModel(board, server)
        compose.setContent { OpenWeightsTheme { CanvasScreen(onBack = {}, viewModel = viewModel) } }

        compose.onNodeWithContentDescription("Open in browser").performClick()
        assertThat(shadowOf(context).nextStartedActivity).isNull()
        compose.onNodeWithText("Cancel").performClick()
        assertThat(shadowOf(context).nextStartedActivity).isNull()

        compose.onNodeWithContentDescription("Open in browser").performClick()
        compose.onNode(hasText("Open in browser") and hasClickAction()).performClick()
        val launched = shadowOf(context).nextStartedActivity
        assertThat(launched.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(launched.dataString).isEqualTo(server.urlFor("site/index.html"))

        compose.onNodeWithContentDescription("Open in browser").performClick()
        assertThat(shadowOf(context).nextStartedActivity).isNull()
        compose.onNodeWithText("Cancel").performClick()
    }
}
