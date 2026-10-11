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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NpuInstallTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val runner =
        """{"num_layer":16,"num_head":8,"head_dim":64,"cache_size":2048,"cache_type":"fp32"}"""

    @Test
    fun `a half is used only once every part is there at its full size`() {
        val model = folder.newFile("LFM2.5-1.2B-Instruct-8da4w-gptq-2k.pte")
        val dir = NpuInstall.write(
            model,
            chunks = listOf(
                NpuInstall.Part("c-chunk1of2.pte", 10),
                NpuInstall.Part("c-chunk2of2.pte", 20),
            ),
            embedding = NpuInstall.Part("embedding-fp32.bin", 30),
            runnerJson = runner,
        )
        File(dir, "c-chunk1of2.pte").writeBytes(ByteArray(10))
        File(dir, "embedding-fp32.bin").writeBytes(ByteArray(30))
        assertThat(NpuInstall.read(model)).isNull()

        File(dir, "c-chunk2of2.pte").writeBytes(ByteArray(5))
        assertThat(NpuInstall.read(model)).isNull()

        File(dir, "c-chunk2of2.pte").writeBytes(ByteArray(20))
        val complete = NpuInstall.read(model)
        assertThat(
            complete?.chunks?.map {
                it.name
            },
        ).containsExactly("c-chunk1of2.pte", "c-chunk2of2.pte").inOrder()
        assertThat(complete?.runnerJson).isEqualTo(runner)
        assertThat(complete?.bytes).isEqualTo(60)
    }

    @Test
    fun `a model with no half has none`() {
        assertThat(NpuInstall.read(folder.newFile("plain.pte"))).isNull()
    }

    @Test
    fun `a part list that names a path outside the folder is refused`() {
        assertThat(NpuInstall.parse("chunk\t../escape.pte\t10\nembedding\te.bin\t5\n")).isNull()
        assertThat(NpuInstall.parse("chunk\t..\t10\nembedding\te.bin\t5\n")).isNull()
        assertThat(NpuInstall.parse("chunk\ta.pte\t0\nembedding\te.bin\t5\n")).isNull()
        assertThat(NpuInstall.parse("embedding\te.bin\t5\n")).isNull()
    }

    @Test
    fun `the memory estimate counts the files and both halves' caches`() {
        // 2 * 16 * 8 * 64 * 2048 * 4 bytes = 128 MiB of NPU cache, its double and the CPU's half.
        val need = NpuMemory.needBytes(runner, filesBytes = 1_000_000_000L)
        val cache = 2L * 16 * 8 * 64 * 2048 * 4
        val estimate = 1_000_000_000L + 2 * cache + cache / 2
        assertThat(need).isEqualTo(estimate + estimate / 10)
        assertThat(NpuMemory.needBytes("""{"num_layer":16}""", 1)).isNull()
        assertThat(NpuMemory.fits(need!!, need + NpuMemory.RESERVE_BYTES)).isTrue()
        assertThat(NpuMemory.fits(need, need)).isFalse()
    }

    @Test
    fun `each family gets the decoder Qualcomm's runner knows`() {
        assertThat(
            QnnSupport.decoderModel("Qwen3-1.7B-ExecuTorch-qnn-sm8850-qwen3-1.7b-4k.pte"),
        ).isEqualTo("qwen3")
        assertThat(QnnSupport.decoderModel("Qwen3.5-2B-qnn.pte")).isEqualTo("llama3")
        assertThat(QnnSupport.decoderModel("Qwen2.5-1.5B-Instruct-qnn.pte")).isEqualTo("qwen2_5")
        assertThat(QnnSupport.decoderModel("Llama-3.2-1B-Instruct-qnn.pte")).isEqualTo("llama3")
        assertThat(QnnSupport.decoderModel("unknown-qnn.pte")).isEqualTo("llama3")
    }

    @Test
    fun `a launch that died opening the NPU half refuses it until it is installed again`() {
        val model = folder.newFile("m-2k.pte")
        val write = {
            NpuInstall.write(
                model,
                listOf(NpuInstall.Part("c.pte", 1)),
                NpuInstall.Part("e.bin", 1),
                runner,
            )
        }
        write()
        assertThat(NpuInstall.diedOpening(model)).isFalse()

        // Opened and read a prompt: nothing is held against it.
        NpuInstall.opening(model)
        NpuInstall.opened(model)
        assertThat(NpuInstall.diedOpening(model)).isFalse()

        // The process died between the two: the next launch finds the mark.
        NpuInstall.opening(model)
        assertThat(NpuInstall.diedOpening(model)).isTrue()
        NpuInstall.refuse(model, "the app closed while its NPU half was opening")
        assertThat(NpuInstall.diedOpening(model)).isFalse()
        assertThat(NpuInstall.refusal(model)).contains("closed")

        // Downloading it again is the user asking for it again.
        write()
        assertThat(NpuInstall.refusal(model)).isNull()
    }

    @Test
    fun `a half is tried once per install, and a new install is told from a straggler`() {
        val model = folder.newFile("m-2k.pte")
        val tokenizer = folder.newFile("m-2k.tokenizer.json")
        assertThat(NpuInstall.startedAt(model)).isEqualTo(0L)
        val write = {
            NpuInstall.write(
                model,
                listOf(NpuInstall.Part("c.pte", 1)),
                NpuInstall.Part("e.bin", 1),
                runner,
            )
            File(NpuInstall.directoryFor(model), "c.pte").writeText("c")
            File(NpuInstall.directoryFor(model), "e.bin").writeText("e")
        }
        write()
        assertThat(NpuInstall.startedAt(model)).isGreaterThan(0L)
        // Not tried until the probe process has answered a prompt with it.
        assertThat(NpuInstall.tried(model, tokenizer)).isFalse()
        NpuInstall.markTried(model, tokenizer)
        assertThat(NpuInstall.tried(model, tokenizer)).isTrue()

        // A replaced CPU build is a pair nobody tried.
        model.writeText("a different build")
        assertThat(NpuInstall.tried(model, tokenizer)).isFalse()
        NpuInstall.markTried(model, tokenizer)
        assertThat(NpuInstall.tried(model, tokenizer)).isTrue()

        // Downloaded again, it is new chunks, which no trial has read.
        write()
        assertThat(NpuInstall.tried(model, tokenizer)).isFalse()
    }
}
