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
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PteHeaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a whole file passes`() {
        // The numbers of Software Mansion's LFM2.5 1.2B xnnpack export, which ends exactly
        // where its segments do.
        val head = header(program = 127_656, segmentsAt = 127_744, segmentBytes = 795_551_360)

        assertThat(PteHeader.damage(head, length = 795_679_104)).isNull()
    }

    @Test
    fun `a file that stops before its segments end is an unfinished download`() {
        val head = header(program = 13_552, segmentsAt = 13_568, segmentBytes = 271_636_096)

        assertThat(PteHeader.damage(head, length = 100_000_000)).contains("did not finish")
    }

    @Test
    fun `something that is not an ExecuTorch program says so`() {
        val head = "<!DOCTYPE html><html><head><title>404".toByteArray()

        assertThat(PteHeader.damage(head, length = 4_096)).contains("not an ExecuTorch program")
    }

    @Test
    fun `a program without the extended header has nothing more to check`() {
        val head = ByteArray(40).also { "ET12".toByteArray().copyInto(it, destinationOffset = 4) }

        assertThat(PteHeader.damage(head, length = 40)).isNull()
    }

    @Test
    fun `a header with impossible sizes is damaged`() {
        val head = header(program = 0, segmentsAt = 0, segmentBytes = 0)

        assertThat(PteHeader.damage(head, length = 1_000)).contains("damaged")
    }

    @Test
    fun `reads the file it is given`() {
        val head = header(program = 64, segmentsAt = 64, segmentBytes = 936)
        val whole = File(folder.root, "whole.pte").apply { writeBytes(head + ByteArray(960)) }
        val cut = File(folder.root, "cut.pte").apply { writeBytes(head + ByteArray(100)) }

        assertThat(PteHeader.damage(whole)).isNull()
        assertThat(PteHeader.damage(cut)).contains("did not finish")
        assertThat(PteHeader.damage(File(folder.root, "absent.pte"))).contains("missing")
    }

    private fun header(program: Long, segmentsAt: Long, segmentBytes: Long): ByteArray =
        ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x40)
            .put("ET12".toByteArray())
            .put("eh00".toByteArray())
            .putInt(32)
            .putLong(program)
            .putLong(segmentsAt)
            .putLong(segmentBytes)
            .array()
}
