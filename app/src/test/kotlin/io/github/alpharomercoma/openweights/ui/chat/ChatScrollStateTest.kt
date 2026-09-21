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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.designsystem.theme.OpenWeightsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatScrollStateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `new chat removes jump button and first transcript follows again`() {
        val state = showLongChat()
        detach()
        compose.onNodeWithContentDescription("New chat").performClick()
        compose.onNodeWithText("Where shall we start?").assertIsDisplayed()
        jump().assertDoesNotExist()
        compose.runOnIdle { state.value = longChat(2, "New") }
        compose.onNodeWithText("New message 59").assertIsDisplayed()
        jump().assertDoesNotExist()
    }

    @Test
    fun `switching chats resets position but updates in the same chat preserve reading`() {
        val state = showLongChat()
        detach()
        compose.runOnIdle {
            state.value =
                state.value.copy(transcript = state.value.transcript + entry(60, "Appended"))
        }
        jump().assertIsDisplayed()
        compose.onNodeWithText("Appended message 60").assertDoesNotExist()
        compose.runOnIdle { state.value = longChat(2, "Other") }
        compose.onNodeWithText("Other message 59").assertIsDisplayed()
        jump().assertDoesNotExist()
        detach()
        jump().performClick()
        compose.onNodeWithText("Other message 59").assertIsDisplayed()
        jump().assertDoesNotExist()
    }

    @Test
    fun `cleared and shortened transcripts discard obsolete scrolling state`() {
        val state = showLongChat()
        detach()
        compose.runOnIdle { state.value = state.value.copy(transcript = emptyList()) }
        jump().assertDoesNotExist()
        compose.runOnIdle { state.value = longChat(1, "Restored") }
        compose.onNodeWithText("Restored message 59").assertIsDisplayed()
        detach()
        compose.runOnIdle { state.value = state.value.copy(transcript = listOf(entry(0, "Short"))) }
        compose.onNodeWithText("Short message 0").assertIsDisplayed()
        jump().assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.runOnIdle { state.value = longChat(1, "Grown") }
        compose.onNodeWithText("Grown message 59").assertIsDisplayed()
        jump().assertDoesNotExist()
    }

    private fun showLongChat(): MutableState<ChatUiState> {
        val state = mutableStateOf(longChat(1, "Original"))
        compose.setContent {
            OpenWeightsTheme(dynamicColor = false) {
                ChatScreen(
                    state = state.value,
                    onSend = { true },
                    onStop = {},
                    onRegenerate = {},
                    onCompact = {},
                    onNewChat = { state.value = ChatUiState(modelName = "Test model") },
                )
            }
        }
        compose.onNodeWithText("Original message 59").assertIsDisplayed()
        return state
    }

    private fun detach() {
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
        compose.waitForIdle()
        jump().assertIsDisplayed()
    }

    private fun jump() = compose.onNodeWithContentDescription("Jump to the latest message")

    private fun longChat(id: Long, prefix: String) = ChatUiState(
        activeConversationId = id,
        modelName = "Test model",
        transcript = List(60) { entry(it, prefix) },
    )

    private fun entry(index: Int, prefix: String) = TranscriptEntry(
        id = index.toLong(),
        role = ChatRole.USER,
        text = "$prefix message $index",
    )
}
