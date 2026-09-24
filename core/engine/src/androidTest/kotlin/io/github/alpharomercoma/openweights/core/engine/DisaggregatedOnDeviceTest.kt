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
import androidx.test.platform.app.InstrumentationRegistry
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
 * The disaggregated path end to end, against the CPU path on the same `.pte`: MediaTek's NPU
 * reads the prompt, the state crosses to the CPU export, the CPU writes the reply.
 *
 * The long prompt is the one that matters. The engine feeds it in pieces of about two hundred
 * tokens, the NPU runtime right-pads every piece that is not a multiple of its batch, and a
 * conv layer has no mask: until the export took a conv_valid and a conv_select input, each
 * right-padded piece handed the next one a conv state made of padding.
 *
 * Needs the NPU half staged the way DisaggregatedExecuTorchBridge finds it, first among the
 * directories under /data/local/tmp/mtk_models that hold chunks:
 * `<dir>/mtk/<soc>/` with the chunks, the embedding table, the exporter's config.json and an
 * `enable-pd` marker. The CPU export and its tokenizer come from `-e pte` and `-e tokenizer`,
 * the long prompt from `-e long` (a file in the model's chat template, as the MediaTek phone
 * rig writes them) with `-e long_expect` the pattern its answer must contain. Skips without them.
 */
@RunWith(AndroidJUnit4::class)
class DisaggregatedOnDeviceTest {

    @Test
    fun answersLikeTheCpuPath(): Unit = runBlocking {
        assumeTrue("no .pte at ${MODEL.path}", MODEL.isFile)
        assumeTrue("no tokenizer at ${TOKENIZER.path}", TOKENIZER.isFile)
        assumeTrue("no disaggregated libraries in this build", DisaggregatedBridge.isAvailable)

        val npuBridge = DisaggregatedExecuTorchBridge()
        val npu = ExecuTorchEngine(npuBridge, temperature = 0f)
        npu.load(MODEL, PARAMS)
        assumeTrue("no NPU half staged with enable-pd", npuBridge.prefillingOnNpu)
        val npuReplies = PROMPTS.map { (prompt, _) -> ask(npu, prompt) }
        npu.unload()

        val cpu = ExecuTorchEngine(NativeExecuTorchBridge(), temperature = 0f)
        cpu.load(MODEL, PARAMS)
        val cpuReplies = PROMPTS.map { (prompt, _) -> ask(cpu, prompt) }
        cpu.unload()

        var npuRight = 0
        var cpuRight = 0
        var same = 0
        PROMPTS.forEachIndexed { i, (prompt, expect) ->
            val n = npuReplies[i]
            val c = cpuReplies[i]
            val nOk = expect.containsMatchIn(n.content)
            val cOk = expect.containsMatchIn(c.content)
            npuRight += if (nOk) 1 else 0
            cpuRight += if (cOk) 1 else 0
            same += if (n.content.trim() == c.content.trim()) 1 else 0
            Log.i(
                TAG,
                "q$i ${prompt.take(40).replace('\n', ' ')} | npu ${if (nOk) "ok" else "FAIL"} " +
                    "(${n.stats.promptTokens}t, prefill ${n.stats.prefillMs} ms) " +
                    "${n.content.take(70).replace('\n', ' ')} | cpu ${if (cOk) "ok" else "FAIL"} " +
                    c.content.take(70).replace('\n', ' '),
            )
        }
        Log.i(TAG, "npu $npuRight/${PROMPTS.size}, cpu $cpuRight/${PROMPTS.size}, identical $same")

        // The NPU half is quantized, so it may word a reply differently; it may not know less.
        assertThat(npuRight).isAtLeast(cpuRight - 1)
        if (LONG != null) {
            assertThat(expectLong!!.containsMatchIn(npuReplies.last().content)).isTrue()
        }
    }

    private suspend fun ask(engine: ExecuTorchEngine, prompt: String): GenerationEvent.Completed =
        engine.chat(
            listOf(ChatMessage.text(ChatRole.USER, prompt)),
            SamplerParams(maxTokens = 48, thinking = false),
        ).toList().filterIsInstance<GenerationEvent.Completed>().single()

    private companion object {
        const val TAG = "DisaggregatedOnDevice"
        private val arguments = InstrumentationRegistry.getArguments()
        val MODEL = File(
            arguments.getString("pte")
                ?: "/data/local/tmp/openweights/eval/LFM2.5-1.2B-Instruct-8da4w-2k.pte",
        )
        val TOKENIZER = File(
            arguments.getString("tokenizer")
                ?: "/data/local/tmp/openweights/eval/LFM2.5-1.2B-Instruct-8da4w-2k.tokenizer.json",
        )
        private val CONTEXT = arguments.getString("context")?.toIntOrNull() ?: 2048
        val PARAMS = ModelLoadParams(contextLength = CONTEXT)

        /** The user turn out of a prompt file the phone rig wrote in LFM2.5's chat template. */
        val LONG: String? = arguments.getString("long")
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?.readText()
            ?.removePrefix("<|im_start|>user\n")
            ?.removeSuffix("<|im_end|>\n<|im_start|>assistant\n")
        val expectLong: Regex? =
            arguments.getString("long_expect")?.toRegex(RegexOption.IGNORE_CASE)

        /** The rig's twelve short questions (phone-rig/reference.py) and what a right answer holds. */
        val PROMPTS: List<Pair<String, Regex>> = listOf(
            "What is the capital of Japan? Answer briefly." to "tokyo",
            "What is the capital of France? Answer briefly." to "paris",
            "What is the capital of Australia? Answer briefly." to "canberra",
            "What is 17 times 24? Answer with just the number." to "\\b408\\b",
            "What is 12 plus 35? Answer with just the number." to "\\b47\\b",
            "What is 9 times 8? Answer with just the number." to "\\b72\\b",
            "What is 100 minus 37? Answer with just the number." to "\\b63\\b",
            "How many days are in a week? Answer briefly." to "\\b(7|seven)\\b",
            "What is the largest planet in the solar system? Answer briefly." to "jupiter",
            "What is the chemical symbol for gold? Answer briefly." to "\\bau\\b",
            "Who wrote Romeo and Juliet? Answer briefly." to "shakespeare",
            "What color do you get by mixing blue and yellow? Answer briefly." to "green",
        ).map { (q, p) -> q to p.toRegex(RegexOption.IGNORE_CASE) } +
            listOfNotNull(LONG?.let { it to (expectLong ?: Regex(".")) })
    }
}
