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

package io.github.alpharomercoma.openweights.model

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks replies aloud.
 *
 * Only installed voices advertised by Android as not requiring a network are eligible.
 * This trusts the TTS service's metadata, not a sandbox or proof that an adversarial
 * service stays offline. Reply text is withheld until an eligible voice is selected.
 */
@Singleton
class SpeechReader @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val _isSpeaking = MutableStateFlow(false)

    /** True while starting or reading a reply, so either can be stopped from the UI. */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val mainThread = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var isReady = false
    private var pending: String? = null
    private var generation = 0
    private var utterance = 0L
    private var activeUtterance: String? = null
    private val initTimeout = Runnable { fail("Read aloud could not start. Try again.") }

    /** Called on the main thread, like [stop] and [release]. */
    fun speak(text: String) {
        // Overlong requests are rejected without a progress callback by some engines.
        val spoken = text.forSpeech().take(TextToSpeech.getMaxSpeechInputLength())
        if (spoken.isBlank()) return
        _error.value = null
        _isSpeaking.value = true

        val current = engine
        if (current == null) {
            pending = spoken
            start()
        } else if (!isReady) {
            pending = spoken
        } else {
            speakReady(current, spoken)
        }
    }

    private fun speakReady(current: TextToSpeech, spoken: String) {
        try {
            val locale = Locale.getDefault()
            val voice = current.voices.orEmpty()
                .asSequence()
                .filter { it.isOfflineFor(locale) }
                .maxWithOrNull(
                    compareBy<Voice> { it.locale == locale }
                        .thenBy { it.locale.country == locale.country }
                        .thenBy { it.name },
                )
            // Never setLanguage here: it can replace an offline voice with the engine's
            // network default. Recheck both availability and selection for every reply.
            if (!current.selectOfflineVoice(voice, locale)) {
                fail(
                    "No installed offline voice for this language. " +
                        "Install one in Android's text-to-speech settings. " +
                        "OpenWeights will not use a network voice.",
                )
                return
            }
            val id = "openweights-reply-${++utterance}"
            activeUtterance = id
            if (current.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                fail(
                    "Read aloud could not speak this reply. Check Android's text-to-speech settings.",
                )
            }
        } catch (_: RuntimeException) {
            fail("Read aloud could not speak this reply. Check Android's text-to-speech settings.")
        }
    }

    private fun TextToSpeech.selectOfflineVoice(candidate: Voice?, locale: Locale): Boolean {
        if (candidate == null || setVoice(candidate) != TextToSpeech.SUCCESS) return false
        val selected = voice ?: return false
        return selected.name == candidate.name && selected.isOfflineFor(locale)
    }

    private fun Voice.isOfflineFor(target: Locale): Boolean = !isNetworkConnectionRequired &&
        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty() &&
        target.language.isNotEmpty() &&
        locale.language == target.language

    fun stop() {
        pending = null
        activeUtterance = null
        _isSpeaking.value = false
        mainThread.removeCallbacks(initTimeout)
        runCatching { engine?.stop() }
        // A cancelled startup must not deliver its queued reply or block the next tap.
        if (!isReady) discardEngine()
    }

    /** Releases the synthesiser. The app calls this when it is being torn down. */
    fun release() {
        stop()
        discardEngine()
    }

    private fun discardEngine() {
        generation++
        val previous = engine
        engine = null
        isReady = false
        runCatching { previous?.shutdown() }
    }

    private fun fail(message: String) {
        stop()
        discardEngine()
        _error.value = message
    }

    private fun start() {
        val token = ++generation
        mainThread.postDelayed(initTimeout, INIT_TIMEOUT_MILLIS)
        try {
            engine = TextToSpeech(context) { status ->
                // Init can arrive before the constructor returns, or on a binder thread.
                mainThread.post {
                    if (token != generation) return@post
                    mainThread.removeCallbacks(initTimeout)
                    if (status != TextToSpeech.SUCCESS) {
                        fail("Read aloud could not start. Check Android's text-to-speech settings.")
                        return@post
                    }
                    val current = engine ?: return@post
                    try {
                        if (current.setOnUtteranceProgressListener(listener) !=
                            TextToSpeech.SUCCESS
                        ) {
                            fail(
                                "Read aloud could not start. Check Android's text-to-speech settings.",
                            )
                            return@post
                        }
                        isReady = true
                        val queued = pending
                        pending = null
                        if (queued != null) speakReady(current, queued)
                    } catch (_: RuntimeException) {
                        fail("Read aloud could not start. Check Android's text-to-speech settings.")
                    }
                }
            }
        } catch (_: RuntimeException) {
            fail("Read aloud could not start. Check Android's text-to-speech settings.")
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            mainThread.post {
                if (activeUtterance == null || utteranceId != activeUtterance) return@post
                activeUtterance = null
                _isSpeaking.value = false
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = onDone(utteranceId)

        @Deprecated("Required by the framework; the newer overload delegates to it.")
        override fun onError(utteranceId: String?) {
            mainThread.post {
                if (activeUtterance == null || utteranceId != activeUtterance) return@post
                fail("Read aloud stopped unexpectedly. Check Android's text-to-speech settings.")
            }
        }
    }
}

private const val INIT_TIMEOUT_MILLIS = 10_000L

/**
 * A reply as it should be heard rather than read.
 *
 * Code blocks, link targets and heading marks are visual furniture: read aloud verbatim
 * they turn a short answer into a minute of punctuation. Dropping them is the difference
 * between a usable read-aloud and a novelty.
 */
internal fun String.forSpeech(): String = this
    .replace(FENCED_CODE, " (code sample) ")
    .replace(INLINE_CODE, "$1")
    .replace(LINK, "$1")
    .replace(EMPHASIS, "$1")
    .replace(HEADING, "")
    .replace(BULLET, "")
    .trim()

private val FENCED_CODE = Regex("```[\\s\\S]*?```")
private val INLINE_CODE = Regex("`([^`]*)`")
private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val EMPHASIS = Regex("""\*{1,2}([^*]+)\*{1,2}""")
private val HEADING = Regex("^#{1,6}\\s*", RegexOption.MULTILINE)
private val BULLET = Regex("^\\s*[-*]\\s+", RegexOption.MULTILINE)
