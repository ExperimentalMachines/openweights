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

package io.github.alpharomercoma.openweights.core.hub

/**
 * One MediaTek NPU build of one context window: everything that has to be on the phone for
 * the NPU to read a prompt and the CPU to write the reply.
 *
 * The NPU half is [chunks] and [embedding], compiled for [soc] and passed [runnerJson]; the
 * CPU half is [cpu], the same model's CPU build of the same window, which decodes. Offered
 * and installed as one unit, because none of the parts runs alone (2026-10-11: offered one
 * chunk at a time, the parts were sixteen downloads that each closed the app when opened).
 */
data class NpuBundle(
    val soc: String,
    val window: Int,
    val chunks: List<HubFile>,
    val embedding: HubFile,
    val runnerJson: String,
    val cpu: HubFile,
) {
    /** Every byte the install downloads: the NPU half and the CPU build together. */
    val totalBytes: Long get() = chunks.sumOf { it.sizeBytes } + embedding.sizeBytes + cpu.sizeBytes

    /** The NPU half's files, in the order the runner takes them. */
    val npuFiles: List<HubFile> get() = chunks + embedding
}
