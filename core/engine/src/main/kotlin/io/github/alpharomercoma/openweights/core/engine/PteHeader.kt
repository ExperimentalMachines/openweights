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

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What the first forty bytes of a `.pte` can say before the runtime is handed it.
 *
 * Read in Kotlin because every error the runtime raises on a damaged file goes through the
 * fbjni path that aborts the process (see [ExportFacts.unopenable]). The layout is
 * ExecuTorch's extended header: a flatbuffer root offset, the file identifier (`ET` and two
 * digits), then `eh00`, the header's length, the program's size, where the segments start
 * and how many bytes of them there are. On every export checked (Software Mansion's LFM2.5,
 * this project's own 8da4w and NeuroPilot exports, Qwen3-1.7B) the file ends exactly where
 * the segments do, so a shorter file is a download that stopped early.
 */
internal object PteHeader {

    /** Why the file cannot be a whole `.pte`, or null when the header holds together. */
    fun damage(file: File): String? {
        if (!file.isFile) return "the file is missing"
        val length = file.length()
        if (length < IDENTIFIER_END) return "the file is too short to be an ExecuTorch program"
        val head = ByteArray(HEADER_BYTES.coerceAtMost(length.toInt()))
        RandomAccessFile(file, "r").use { it.readFully(head) }
        return damage(head, length)
    }

    /** [damage] over bytes already read, for a test that has no file. */
    fun damage(head: ByteArray, length: Long): String? = when {
        head.size < IDENTIFIER_END -> "the file is too short to be an ExecuTorch program"
        !String(head, IDENTIFIER_AT, IDENTIFIER_END - IDENTIFIER_AT, Charsets.US_ASCII)
            .matches(IDENTIFIER) -> "the file is not an ExecuTorch program"
        // A file written before the extended header existed has nothing more to check.
        head.size < SIZES_END ||
            String(head, MAGIC_AT, MAGIC_LENGTH, Charsets.US_ASCII) != EXTENDED_MAGIC -> null
        else -> sizeDamage(head, length)
    }

    /** Whether the extended header's sizes fit the file. */
    private fun sizeDamage(head: ByteArray, length: Long): String? {
        val bytes = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val headerLength = bytes.getInt(MAGIC_AT + MAGIC_LENGTH)
        val programSize = bytes.getLong(PROGRAM_SIZE_AT)
        val segmentsAt = bytes.getLong(SEGMENT_BASE_AT)
        // The segment byte count joined the header later; a header too short to carry it
        // still bounds the file by where its program ends.
        val segmentBytes = if (headerLength >= LONG_HEADER && head.size >= HEADER_BYTES) {
            bytes.getLong(SEGMENT_SIZE_AT)
        } else {
            0L
        }
        if (programSize <= 0 || segmentsAt < 0 || segmentBytes < 0) {
            return "the file's header is damaged"
        }
        val end = maxOf(programSize, if (segmentBytes > 0) segmentsAt + segmentBytes else 0L)
        return if (length < end) {
            "the file stops at ${length.megabytes()} of ${end.megabytes()}, so the download " +
                "did not finish"
        } else {
            null
        }
    }

    private fun Long.megabytes() = "%.0f MB".format(this / BYTES_PER_MB)

    private const val IDENTIFIER_AT = 4
    private const val IDENTIFIER_END = 8
    private val IDENTIFIER = Regex("ET[0-9]{2}")
    private const val MAGIC_AT = 8
    private const val MAGIC_LENGTH = 4
    private const val EXTENDED_MAGIC = "eh00"
    private const val PROGRAM_SIZE_AT = 16
    private const val SEGMENT_BASE_AT = 24
    private const val SIZES_END = 32
    private const val SEGMENT_SIZE_AT = 32
    private const val LONG_HEADER = 32
    private const val HEADER_BYTES = 40
    private const val BYTES_PER_MB = 1_048_576.0
}
