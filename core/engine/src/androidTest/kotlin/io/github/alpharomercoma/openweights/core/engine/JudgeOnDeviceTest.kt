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

package io.github.alpharomercoma.openweights.core.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.common.model.ChatMessage
import io.github.alpharomercoma.openweights.core.common.model.ChatRole
import io.github.alpharomercoma.openweights.core.common.model.ModelLoadParams
import io.github.alpharomercoma.openweights.core.common.model.SamplerParams
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What `Session::judge` claims, on the phone: a question put to the conversation leaves the
 * turn after it generating exactly what it would have without the question, reading only
 * the few tokens where the two prompts part, however many questions and turns alternate.
 *
 * Measured on the Mac first (2026-09-17, `docs/research/judged-decisions.md`): on LFM2.5
 * 1.2B QAD Q4_0 seven judged rounds matched seven clean ones token for token, every token's
 * log-probability equal, each judged turn reading 5 tokens. Qwen3.5 2B did not match, and a
 * control with no judgement showed why: its output depends on where a prompt read is split,
 * which every follow-up turn on that model already varies. So the equality is asserted
 * only for the model this app recommends, the first `LFM2.5*.gguf` under the fixtures.
 *
 * ```
 * adb shell am instrument -w -e class io.github.alpharomercoma.openweights.core.engine.JudgeOnDeviceTest \
 *   io.github.alpharomercoma.openweights.core.engine.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class JudgeOnDeviceTest {
    private val modelDir = File("/data/local/tmp/openweights")

    @Test
    fun aJudgedTurnGeneratesWhatACleanTurnDoesAndReadsOnlyThePartingTokens() = runBlocking {
        val model = modelDir.listFiles { file ->
            file.name.startsWith("LFM2.5") &&
                file.name.endsWith(".gguf") &&
                !file.name.contains("mmproj")
        }?.minByOrNull { it.name }
        assumeTrue("no LFM2.5 .gguf in $modelDir", model != null)
        requireNotNull(model)

        LlamaCppEngine().use { clean ->
            LlamaCppEngine().use { judged ->
                clean.load(model, ModelLoadParams(contextLength = CONTEXT))
                judged.load(model, ModelLoadParams(contextLength = CONTEXT))
                var cleanConversation = listOf(
                    ChatMessage.text(ChatRole.SYSTEM, "You are a helpful assistant."),
                    ChatMessage.text(ChatRole.USER, FOLLOW_UPS.first()),
                )
                var judgedConversation = cleanConversation
                for ((round, next) in (FOLLOW_UPS.drop(1) + "Thanks.").withIndex()) {
                    val baseline = clean.reply(cleanConversation)
                    val judgement = judged.judge(judgedConversation, QUESTION, listOf("Yes", "No"))
                    requireNotNull(judgement) { "the engine declined to judge" }
                    val answer = judged.reply(judgedConversation)
                    Log.i(
                        TAG,
                        "round ${round + 1}: judge read ${judgement.promptTokens} in " +
                            "${judgement.prefillMs} ms, options held ${judgement.optionMass}; " +
                            "clean read ${baseline.stats.promptTokens}, judged read " +
                            "${answer.stats.promptTokens}",
                    )
                    assertThat(judgement.optionMass).isGreaterThan(MIN_MASS)
                    // Token for token and probability for probability: text alone can agree
                    // while the state behind it has drifted.
                    assertThat(answer.pieces).isEqualTo(baseline.pieces)
                    assertThat(answer.logprobs).isEqualTo(baseline.logprobs)
                    assertThat(answer.stats.promptTokens).isEqualTo(PARTING_TOKENS)
                    cleanConversation = cleanConversation +
                        ChatMessage.text(ChatRole.ASSISTANT, baseline.text) +
                        ChatMessage.text(ChatRole.USER, next)
                    judgedConversation = judgedConversation +
                        ChatMessage.text(ChatRole.ASSISTANT, answer.text) +
                        ChatMessage.text(ChatRole.USER, next)
                }
            }
        }
    }

    @Test
    fun anOptionThatIsNotOneTokenIsRefusedAndTheNextTurnIsUntouched() = runBlocking {
        val model = modelDir.listFiles { file ->
            file.name.startsWith("LFM2.5") &&
                file.name.endsWith(".gguf") &&
                !file.name.contains("mmproj")
        }?.minByOrNull { it.name }
        assumeTrue("no LFM2.5 .gguf in $modelDir", model != null)
        requireNotNull(model)

        LlamaCppEngine().use { engine ->
            engine.load(model, ModelLoadParams(contextLength = CONTEXT))
            val conversation = listOf(ChatMessage.text(ChatRole.USER, FOLLOW_UPS.first()))
            val first = engine.reply(conversation)
            // LFM2.5 spells SEARCH as SE + ARCH; the first piece's probability is not the word's.
            assertThat(engine.judge(conversation, QUESTION, listOf("SEARCH", "ANSWER"))).isNull()
            val again = engine.reply(conversation)
            // Refused before anything was read: the same prompt again reads one token, the
            // one generation always keeps back, as a regenerate does.
            assertThat(again.pieces).isEqualTo(first.pieces)
            assertThat(again.stats.promptTokens).isEqualTo(1)
        }
    }

    private class Reply(
        val pieces: List<String>,
        val logprobs: List<Float?>,
        val stats: GenerationStats,
    ) {
        val text: String get() = pieces.joinToString("")
    }

    private suspend fun InferenceEngine.reply(messages: List<ChatMessage>): Reply {
        val events = chat(
            messages,
            SamplerParams(temperature = 0f, maxTokens = BUDGET, seed = 7),
        ).toList()
        val done = events.filterIsInstance<GenerationEvent.Completed>().single()
        val tokens = events.filterIsInstance<GenerationEvent.Token>()
        return Reply(tokens.map { it.text }, tokens.map { it.logprob }, done.stats)
    }

    private companion object {
        const val TAG = "OpenWeights"
        const val CONTEXT = 4096
        const val BUDGET = 24
        const val MIN_MASS = 0.9f

        /**
         * What a judged turn reads after restoring the point: the end of the user's turn and
         * the assistant header. Five for LFM2.5's template on the Mac, and the template is in
         * the file, so the phone must read the same five.
         */
        const val PARTING_TOKENS = 5
        const val QUESTION =
            "Before you reply: are you sure you know the correct answer to my last message " +
                "without looking anything up? Reply with one word, Yes or No."
        val FOLLOW_UPS = listOf(
            "Who wrote the novel Middlemarch?",
            "When was it published?",
            "Name another book by the same author.",
            "Where is it set?",
            "Who is the main character?",
            "Summarise it in one line.",
        )
    }
}
