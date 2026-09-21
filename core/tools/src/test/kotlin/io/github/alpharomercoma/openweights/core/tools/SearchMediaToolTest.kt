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

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.common.model.ToolCall
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchMediaToolTest {
    private val call = ToolCall("pictures", "show_pictures", """{"query":"cats"}""")

    @Test
    fun `a refused primary falls back to Commons and keeps source attribution`() = runTest {
        val result = tool(commonsAnswers = true).execute(call)
        assertThat(result.successful).isTrue()
        assertThat(result.text).contains("Wikimedia Commons")
        assertThat(SearchMediaTool.picturesIn(result.text).single().source)
            .isEqualTo("https://commons.wikimedia.org/wiki/File:Cat.jpg")
    }

    @Test
    fun `failed providers are not reported as a successful picture search`() = runTest {
        val result = tool(commonsAnswers = false).execute(call)
        assertThat(result.successful).isFalse()
        assertThat(result.text).contains("No pictures were returned")
        assertThat(SearchMediaTool.picturesIn(result.text)).isEmpty()
    }

    @Test
    fun `Commons parser refuses private thumbnails and non-image files`() {
        val provider = CommonsMediaProvider(OkHttpClient())
        assertThat(provider.parse(RESPONSE.replace("image/jpeg", "application/pdf"), 8)).isNull()
        assertThat(
            provider.parse(RESPONSE.replace("upload.wikimedia.org", "127.0.0.1"), 8),
        ).isNull()
        assertThat(provider.parse("""{"error":{"code":"ratelimited"}}""", 8)).isNull()
    }

    private fun tool(commonsAnswers: Boolean): SearchMediaTool {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val answers = commonsAnswers && chain.request().url.host == "commons.wikimedia.org"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (answers) 200 else 403).message("test")
                .body(
                    (if (answers) RESPONSE else "denied").toResponseBody(
                        "application/json".toMediaType(),
                    ),
                )
                .build()
        }.build()
        return SearchMediaTool(
            client,
            SearchSettings(ApplicationProvider.getApplicationContext(), SecretSealer.Unavailable),
            Reachability { true },
        )
    }

    private companion object {
        val RESPONSE = """
            {"query":{"pages":[{"title":"File:Cat.jpg","imageinfo":[{
                "mime":"image/jpeg", "thumburl":"https://upload.wikimedia.org/cat.jpg",
                "url":"https://upload.wikimedia.org/original.jpg",
                "descriptionurl":"https://commons.wikimedia.org/wiki/File:Cat.jpg"
            }]}]}}
        """.trimIndent()
    }
}
