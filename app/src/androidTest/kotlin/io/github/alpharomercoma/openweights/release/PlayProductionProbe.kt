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

package io.github.alpharomercoma.openweights.release

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Google Play actually hands a phone, and whether it answers "hi".
 *
 * Written after the 2026-09-14 report of a production install that still aborted on the
 * first reply while the listing already showed the fixed release's notes. A debug build or
 * a locally built bundle cannot answer that; only the store can. On a Test Lab phone, which
 * is signed in to Play, this installs the production package through the Play Store app
 * exactly as a user would, logs the version code and installer Play recorded, copies the
 * installed APKs to [OUT] for the host to read the native library's build ID, then opens
 * the model given as the `model` argument in that install, sends the `prompt` argument
 * ("hi" if none), waits for the reply to finish, logs the screen with its tok/s line, and
 * fails if the process dies or the crash buffer names the package. Driven by
 * `tools/release/probe_play_ftl.sh`; everything it learns is logged on tag [TAG].
 */
@RunWith(AndroidJUnit4::class)
class PlayProductionProbe {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun theBuildPlayServesAnswersHi() {
        val model = InstrumentationRegistry.getArguments().getString("model")
        sh("mkdir -p $OUT")
        installFromPlay()

        val info = sh("dumpsys package $PKG")
        info.lines().map(String::trim)
            .filter { it.startsWith("versionCode=") || it.startsWith("installerPackageName=") }
            .distinct()
            .forEach { Log.i(TAG, "INSTALLED $it") }
        installedApks().forEach { path ->
            Log.i(TAG, "APK $path")
            sh("cp $path $OUT/")
        }
        if (model == null) return

        val prompt = InstrumentationRegistry.getArguments().getString("prompt") ?: "hi"
        sh("logcat -b crash -c")
        sh("am start -W -n $PKG/.MainActivity --es $EXTRA_OPEN_MODEL $model")
        send(prompt)
        // Until Send is back, which is the reply finished; a slow decode is the thing a
        // run may be looking for, so the wait is long and the stats line says the rate.
        val deadline = SystemClock.uptimeMillis() + REPLY_MS
        SystemClock.sleep(POLL_MS)
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && find(root) { it.labelled("Stop generating") } == null) break
            SystemClock.sleep(POLL_MS)
        }
        sh("screencap -p $OUT/reply.png")
        val texts = mutableListOf<String>()
        automation.rootInActiveWindow?.let { collect(it, texts) }
        Log.i(TAG, "SCREEN ${texts.joinToString(" | ")}")
        val crash = sh("logcat -b crash -d")
        val alive = sh("pidof $PKG").isNotBlank()
        Log.i(TAG, "RESULT alive=$alive crash=${crash.contains(PKG)}")
        assertWithMessage("crash buffer after \"hi\"").that(crash).doesNotContain(PKG)
        assertWithMessage("$PKG still running after \"hi\"").that(alive).isTrue()
    }

    /** Opens the Play listing and taps Install until the package manager has the app. */
    private fun installFromPlay() {
        sh("am start -a android.intent.action.VIEW -d market://details?id=$PKG -p $PLAY")
        // Play's install sheet is a window of its own, which the active-window root misses.
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val deadline = SystemClock.uptimeMillis() + INSTALL_MS
        var shot = 0
        while (installedApks().isEmpty()) {
            if (shot < MAX_SHOTS) sh("screencap -p $OUT/play-${shot++}.png")
            check(SystemClock.uptimeMillis() < deadline) { "Play did not install $PKG" }
            val roots = automation.windows.mapNotNull { it.root } +
                listOfNotNull(automation.rootInActiveWindow)
            // A lab phone's Play first asks for its terms. The label is matched whole, on
            // text or description, because "Install" is also inside "Uninstall".
            val button = PLAY_BUTTONS.firstNotNullOfOrNull { label ->
                roots.firstNotNullOfOrNull { root -> find(root) { it.labelled(label) } }
            }
            if (button != null) {
                Log.i(TAG, "TAP ${button.text ?: button.contentDescription}")
                tap(button)
            } else {
                val seen = mutableListOf<String>()
                roots.forEach { collect(it, seen) }
                Log.i(TAG, "NO BUTTON among ${seen.joinToString(" | ").take(LOG_CHARS)}")
            }
            SystemClock.sleep(POLL_MS)
        }
        Log.i(TAG, "Play installed $PKG")
    }

    /** Waits for the model to load, which is when Send enables, then sends [prompt]. */
    private fun send(prompt: String) {
        val deadline = SystemClock.uptimeMillis() + LOAD_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                // A first launch may ask for notifications; either answer lets the chat on.
                root.findAccessibilityNodeInfosByText("Allow")
                    .firstOrNull { it.text?.toString() == "Allow" }
                    ?.let(::tap)
                val field = find(root) { it.className?.toString() == "android.widget.EditText" }
                val send = find(root) { it.contentDescription?.toString() == "Send message" }
                if (field != null && field.text?.toString() != prompt) {
                    field.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                prompt,
                            )
                        },
                    )
                } else if (send != null && send.isEnabled) {
                    sh("screencap -p $OUT/loaded.png")
                    tap(send)
                    Log.i(TAG, "SENT $prompt")
                    return
                }
            }
            SystemClock.sleep(POLL_MS)
        }
        sh("screencap -p $OUT/never-loaded.png")
        error("the model never loaded; Send stayed disabled")
    }

    private fun installedApks(): List<String> = sh("pm path $PKG").lines()
        .map { it.removePrefix("package:").trim() }
        .filter(String::isNotEmpty)

    private fun tap(node: AccessibilityNodeInfo) {
        val bounds = Rect().also(node::getBoundsInScreen)
        sh("input tap ${bounds.centerX()} ${bounds.centerY()}")
    }

    private fun find(
        node: AccessibilityNodeInfo,
        match: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val hit = node.getChild(i)?.let { find(it, match) }
            if (hit != null) return hit
        }
        return null
    }

    private fun AccessibilityNodeInfo.labelled(label: String): Boolean =
        text?.toString().equals(label, ignoreCase = true) ||
            contentDescription?.toString().equals(label, ignoreCase = true)

    private fun collect(node: AccessibilityNodeInfo, into: MutableList<String>) {
        (node.text ?: node.contentDescription)?.toString()?.takeIf(String::isNotBlank)
            ?.let(into::add)
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, into) }
    }

    private fun sh(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val TAG = "OpenWeightsPlayProbe"
        const val PKG = "io.github.alpharomercoma.openweights"
        const val PLAY = "com.android.vending"
        val PLAY_BUTTONS = listOf("Accept", "Install", "Continue", "Got it")
        const val EXTRA_OPEN_MODEL = "io.github.alpharomercoma.openweights.extra.OPEN_MODEL"
        const val OUT = "/sdcard/probe"
        const val INSTALL_MS = 6 * 60_000L
        const val LOAD_MS = 4 * 60_000L
        const val REPLY_MS = 5 * 60_000L
        const val POLL_MS = 5_000L
        const val MAX_SHOTS = 24
        const val LOG_CHARS = 3_000
    }
}
