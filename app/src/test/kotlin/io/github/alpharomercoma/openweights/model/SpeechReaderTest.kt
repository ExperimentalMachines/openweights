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
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech
import java.time.Duration
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [SpeechReaderTest.SpeechEngine::class])
@LooperMode(LooperMode.Mode.PAUSED)
class SpeechReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reader = SpeechReader(context)
    private val originalLocale = Locale.getDefault()
    private val main = shadowOf(Looper.getMainLooper())

    @Before
    fun setLocale() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun release() {
        reader.release()
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `a network-only engine never receives the reply`() {
        val network = voice("cloud", network = true)
        ShadowTextToSpeech.addVoice(network)
        val engine = begin()
        engine.selectDefault(network)

        initialize(engine)

        assertThat(engine.requests).isEmpty()
        assertUnavailable()
    }

    @Test
    fun `missing local data and unrelated languages cannot provide fallback`() {
        ShadowTextToSpeech.addVoice(
            voice(
                "not-downloaded",
                features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
            ),
        )
        ShadowTextToSpeech.addVoice(voice("foreign", locale = Locale.FRENCH))
        val engine = begin()

        initialize(engine)

        assertThat(engine.requests).isEmpty()
        assertUnavailable()
    }

    @Test
    fun `an empty voice list reports unavailable instead of leaving reading active`() {
        val engine = begin()

        initialize(engine)

        assertThat(engine.requests).isEmpty()
        assertUnavailable()
    }

    @Test
    fun `an installed exact locale voice replaces the cloud default and completes normally`() {
        val network = voice("cloud", network = true)
        val local = voice("local")
        ShadowTextToSpeech.addVoice(network)
        ShadowTextToSpeech.addVoice(local)
        ShadowTextToSpeech.addVoice(voice("other-region", locale = Locale.UK))
        val engine = begin("A **private** reply")
        engine.selectDefault(network)

        initialize(engine)

        assertThat(engine.requests).containsExactly(Request("A private reply", local))
        assertThat(reader.isSpeaking.value).isTrue()
        assertThat(reader.error.value).isNull()
        engine.utteranceProgressListener.onDone(engine.lastUtterance)
        main.idle()
        assertThat(reader.isSpeaking.value).isFalse()
    }

    @Test
    fun `an installed voice in the same language can read another region`() {
        val local = voice("british", locale = Locale.UK)
        ShadowTextToSpeech.addVoice(local)
        val engine = begin()

        initialize(engine)

        assertThat(engine.requests).containsExactly(Request(REPLY, local))
        assertThat(reader.error.value).isNull()
    }

    @Test
    fun `a service retaining its network default after voice selection receives no reply`() {
        val network = voice("cloud", network = true)
        ShadowTextToSpeech.addVoice(voice("local"))
        val engine = begin()
        engine.selectDefault(network)
        engine.ignoreVoiceSelection = true

        initialize(engine)

        assertThat(engine.requests).isEmpty()
        assertUnavailable()
    }

    @Test
    fun `failed voice loading receives no reply`() {
        ShadowTextToSpeech.addVoice(voice("local"))
        val engine = begin()
        engine.rejectVoiceSelection = true

        initialize(engine)

        assertThat(engine.requests).isEmpty()
        assertUnavailable()
    }

    @Test
    fun `init failure is visible and a later tap can use a working engine`() {
        ShadowTextToSpeech.addVoice(voice("local"))
        val failed = begin()
        initialize(failed, TextToSpeech.ERROR)
        assertThat(failed.requests).isEmpty()
        assertUnavailable()

        val working = begin("Try again")
        initialize(working)

        assertThat(working.requests.map { it.text }).containsExactly("Try again")
        assertThat(reader.isSpeaking.value).isTrue()
        assertThat(reader.error.value).isNull()
    }

    @Test
    fun `stopping before init discards the reply even if the old callback arrives later`() {
        ShadowTextToSpeech.addVoice(voice("local"))
        val cancelled = begin("Cancelled reply")
        assertThat(reader.isSpeaking.value).isTrue()
        reader.stop()
        assertThat(reader.isSpeaking.value).isFalse()

        val current = begin("Current reply")
        initialize(cancelled)
        initialize(current)

        assertThat(cancelled.requests).isEmpty()
        assertThat(current.requests.map { it.text }).containsExactly("Current reply")
        assertThat(reader.error.value).isNull()
    }

    @Test
    fun `an engine that never initializes cannot keep a reply queued forever`() {
        ShadowTextToSpeech.addVoice(voice("local"))
        val engine = begin()

        main.idleFor(Duration.ofSeconds(30))
        assertUnavailable()
        initialize(engine)

        assertThat(engine.requests).isEmpty()
        assertThat(reader.isSpeaking.value).isFalse()
    }

    @Test
    fun `speak rejection clears reading and exposes an error without waiting for callbacks`() {
        ShadowTextToSpeech.addVoice(voice("local"))
        val engine = begin()
        engine.rejectSpeech = true

        initialize(engine)

        assertUnavailable()
    }

    @Test
    fun `stopping works and stale completion cannot stop the next reply`() {
        ShadowTextToSpeech.addVoice(voice("local"))
        val engine = begin()
        initialize(engine)
        val previous = engine.lastUtterance
        reader.stop()
        assertThat(reader.isSpeaking.value).isFalse()

        reader.speak("Next reply")
        engine.utteranceProgressListener.onDone(previous)
        main.idle()
        assertThat(reader.isSpeaking.value).isTrue()
        engine.utteranceProgressListener.onDone(engine.lastUtterance)
        main.idle()
        assertThat(reader.isSpeaking.value).isFalse()
        assertThat(reader.error.value).isNull()
    }

    @Test
    fun `a synthesis error is visible and clears the reading state`() {
        ShadowTextToSpeech.addVoice(voice("local"))
        val engine = begin()
        initialize(engine)

        engine.utteranceProgressListener.onError(engine.lastUtterance, TextToSpeech.ERROR_SYNTHESIS)
        main.idle()

        assertUnavailable()
    }

    private fun begin(text: String = REPLY): SpeechEngine {
        reader.speak(text)
        return Shadow.extract(ShadowTextToSpeech.getLastTextToSpeechInstance())
    }

    private fun initialize(engine: SpeechEngine, status: Int = TextToSpeech.SUCCESS) {
        engine.onInitListener.onInit(status)
        main.idle()
    }

    private fun assertUnavailable() {
        assertThat(reader.isSpeaking.value).isFalse()
        assertThat(reader.error.value).isNotNull()
    }

    private fun voice(
        name: String,
        locale: Locale = Locale.US,
        network: Boolean = false,
        features: Set<String> = emptySet(),
    ) = Voice(name, locale, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, network, features)

    data class Request(val text: String, val voice: Voice?)

    /** Records the service boundary; completion and rejection are controlled by each scenario. */
    @Implements(TextToSpeech::class)
    class SpeechEngine : ShadowTextToSpeech() {
        val requests = mutableListOf<Request>()
        var lastUtterance: String? = null
        var rejectSpeech = false
        var ignoreVoiceSelection = false
        var rejectVoiceSelection = false

        fun selectDefault(voice: Voice) {
            super.setVoice(voice)
        }

        @Implementation
        override fun setVoice(voice: Voice): Int = when {
            rejectVoiceSelection -> TextToSpeech.ERROR
            ignoreVoiceSelection -> TextToSpeech.SUCCESS
            else -> super.setVoice(voice)
        }

        @Implementation
        override fun speak(
            text: CharSequence,
            queueMode: Int,
            params: Bundle?,
            utteranceId: String?,
        ): Int {
            if (rejectSpeech) return TextToSpeech.ERROR
            requests += Request(text.toString(), currentVoice)
            lastUtterance = utteranceId
            return TextToSpeech.SUCCESS
        }
    }

    private companion object {
        const val REPLY = "Private reply"
    }
}
