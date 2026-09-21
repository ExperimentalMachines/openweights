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

import io.github.alpharomercoma.openweights.core.common.model.CompiledBackend

/**
 * Whether this build can run a model compiled ahead of time. It can: every build carries
 * the ExecuTorch runtime.
 *
 * Until 2026-09-06 this was one of two files, selected by a product flavour, so that a
 * `standard` build could leave the 8.6 MB of native library out. Play takes one bundle per
 * release, so nobody ever got to choose, and the publishers' exports had by then made a
 * `.pte` something downloaded as found rather than compiled by hand. One build, one file.
 * [AVAILABLE] stays because the callers that ask it are the right places to ask, and a
 * constant true costs nothing.
 */
object ExecuTorchSupport {
    const val AVAILABLE: Boolean = true

    /**
     * The delegates this build has linked, and therefore the models it can open.
     *
     * A `.pte` whose backend is missing does not fall back to the CPU — the runtime
     * reports it is not registered and the load fails outright. Checking before offering a
     * download is the difference between refusing a model up front and refusing it after a
     * gigabyte. UNKNOWN is included because most published exports are XNNPACK and do not
     * say so in their name; excluding it would hide nearly all of them.
     */
    val BACKENDS: Set<CompiledBackend> = setOf(CompiledBackend.XNNPACK, CompiledBackend.UNKNOWN)

    /** Whether this build could open a model compiled for [backend]. */
    fun canRun(backend: CompiledBackend): Boolean = backend in BACKENDS

    /**
     * Whether this build could open the file at [path] on a device whose chip is [socModel].
     *
     * The delegate has to be linked in, as [canRun] says, and a chip-locked delegate has a
     * second condition: the binary was compiled for one chip and reports itself unavailable
     * on any other. The chip is in the published path (`qnn/sm8750/...`) and Android reports
     * the device's as `Build.SOC_MODEL`, so the two are compared directly. A chip-locked
     * file whose path names no chip is refused: nothing can tell where it would run, and the
     * cost of guessing wrong is a finished download that will not open.
     *
     * [socModel] is null when the platform does not say. A chip-locked file is refused then
     * too, for the same reason.
     */
    fun canRun(backend: CompiledBackend, path: String, socModel: String?): Boolean {
        if (!canRun(backend)) return false
        if (!backend.isChipLocked) return true
        val compiledFor = CompiledBackend.socIn(path) ?: return false
        return socModel != null && compiledFor.equals(socModel, ignoreCase = true)
    }

    /** A bridge onto the real runtime. */
    fun bridge(): ExecuTorchBridge = NativeExecuTorchBridge()
}
