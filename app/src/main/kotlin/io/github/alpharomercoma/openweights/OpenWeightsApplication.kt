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

import android.app.Application
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.svg.SvgDecoder
import dagger.hilt.android.HiltAndroidApp
import io.github.alpharomercoma.openweights.core.engine.PTE_PROBE_PROCESS
import io.github.alpharomercoma.openweights.core.engine.VulkanSupport
import io.github.alpharomercoma.openweights.core.tools.PublicOnlyDns
import io.github.alpharomercoma.openweights.core.tools.SEARCH_USER_AGENT
import io.github.alpharomercoma.openweights.core.tools.isPrivateLiteral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import javax.inject.Inject

/**
 * The application, WorkManager's configuration, and the image loader.
 *
 * The download worker takes an injected downloader, which the default worker factory cannot
 * construct: it only knows how to call a two argument constructor. Handing WorkManager
 * Hilt's factory instead is what lets a worker have dependencies, and the manifest removes
 * WorkManager's own startup initializer so this configuration is the one that is used.
 */
@HiltAndroidApp
class OpenWeightsApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Start-up, which in the probe process is nothing at all.
     *
     * On a background scope, the clean-up of what watches left behind: `onCreate` blocks the
     * first frame, and WorkManager's database is not worth one.
     */
    override fun onCreate() {
        super.onCreate()
        // The model checker (PteProbeService) runs in a process of its own, and Android runs
        // this there too. Nothing below belongs in it.
        if (getProcessName().endsWith(PTE_PROBE_PROCESS)) return
        // Before any screen asks what this phone can run: a SharedPreferences read and one
        // PackageManager feature check.
        VulkanSupport.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { retireWatches() }
                .onFailure { android.util.Log.w("OpenWeights", "watch clean-up failed", it) }
        }
    }

    /**
     * Takes away what the watch feature left on a phone that ran it, once it is gone.
     *
     * Watches were removed. A watch's periodic check is persisted by WorkManager under its
     * worker's class name, and with that class gone WorkManager would fail to build it at
     * every period, indefinitely; its alert channel would stay in the system settings. Both
     * are cleared on every start, which costs nothing once there is nothing left. The watches
     * table goes with schema 21. Alarms a watch was waiting on name a receiver that no longer
     * exists, and the system drops them when they fire.
     */
    private fun retireWatches() {
        WorkManager.getInstance(this).cancelAllWorkByTag(RETIRED_WATCH_WORKER)
        getSystemService(
            NotificationManager::class.java,
        )?.deleteNotificationChannel(RETIRED_WATCH_CHANNEL)
    }

    /**
     * The image loader, with SVG decoding named rather than discovered.
     *
     * Coil finds its decoders through a service locator, which works until R8 runs: the
     * release build minifies and shrinks, and a component nothing references by name is
     * exactly what that is for. An avatar that decodes in debug and silently does not in
     * the build people install is a bug that only shows up after shipping, so the one
     * decoder this app depends on is added by hand.
     *
     * The network fetcher is named for the same reason and not because the service
     * locator would miss it. Every image here is an https URL, so a build where that
     * component went missing shows no avatars at all, and one line is a cheap price for
     * never having to work out whether the locator survived a given R8 pass.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .components {
                // Search results and avatars are untrusted URLs. Reuse the same DNS
                // boundary as fetch_url so a public hostname cannot resolve into a LAN,
                // loopback, link-local, or cloud-metadata address. Numeric IP literals can
                // bypass OkHttp's Dns implementation, so untrusted redirects are refused:
                // following one would otherwise reopen the private-network boundary.
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = OkHttpClient.Builder()
                            .dns(PublicOnlyDns())
                            // The resolver never sees a host written as digits, so a
                            // page could name the router or the metadata address in an
                            // image tag and have it dialled. fetch_url refuses the same
                            // literals; this is the same refusal for pictures.
                            .addInterceptor { chain ->
                                val request = chain.request()
                                if (request.url.host.isPrivateLiteral()) {
                                    throw IOException("Refused an image from a private address")
                                }
                                // Wikimedia rejects generic HTTP-library user agents with 403.
                                chain.proceed(
                                    request.newBuilder().header(
                                        "User-Agent",
                                        SEARCH_USER_AGENT,
                                    ).build(),
                                )
                            }
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .build(),
                    ),
                )
                add(SvgDecoder.Factory())
            }
            .build()
}

/** 100 MB of thumbnails and avatars: large enough for history, bounded on the phone. */
private const val IMAGE_CACHE_MAX_BYTES = 100L * 1024L * 1024L

/** The class WorkManager tagged every watch's periodic check with. */
private const val RETIRED_WATCH_WORKER = "io.github.alpharomercoma.openweights.watch.WatchWorker"

/** The channel watch alerts were posted on. */
private const val RETIRED_WATCH_CHANNEL = "watch-alert"
