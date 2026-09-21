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

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** The documented Commons API keeps pictures usable when the primary provider refuses. */
internal class CommonsMediaProvider(private val client: OkHttpClient) {
    suspend fun search(query: String, limit: Int): List<MediaHit>? {
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", query)
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrlimit", limit.toString())
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|mime")
            .addQueryParameter("iiurlwidth", "512")
            .build()
        val request = Request.Builder().url(url)
            .header(
                "User-Agent",
                SEARCH_USER_AGENT,
            )
            .build()
        return try {
            client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    parse(
                        response.peekBody(MAX_BYTES).string(),
                        limit,
                    )
                } else {
                    null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            Log.i("OpenWeights", "Commons image search failed", failure)
            null
        }
    }

    internal fun parse(body: String, limit: Int): List<MediaHit>? = runCatching {
        val pages = Json.parseToJsonElement(body).jsonObject["query"]
            ?.jsonObject?.get("pages")?.jsonArray ?: return@runCatching null
        pages.mapNotNull { page ->
            runCatching row@{
                val row = page.jsonObject
                val info = row["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@row null
                fun field(key: String) = info[key]?.jsonPrimitive?.content.orEmpty()
                val thumbnail = field("thumburl")
                val source = field("descriptionurl")
                if (!field("mime").startsWith("image/") ||
                    !thumbnail.isDrawable() ||
                    !source.isDrawable()
                ) {
                    null
                } else {
                    MediaHit(
                        title = row["title"]?.jsonPrimitive?.content.orEmpty().removePrefix(
                            "File:",
                        ),
                        thumbnailUrl = thumbnail,
                        targetUrl = field("url"),
                        sourceUrl = source,
                        kind = MediaResultKind.IMAGE,
                    )
                }
            }.getOrNull()
        }.take(limit).ifEmpty { null }
    }.getOrNull()

    private companion object {
        const val MAX_BYTES = 1L shl 20
    }
}
