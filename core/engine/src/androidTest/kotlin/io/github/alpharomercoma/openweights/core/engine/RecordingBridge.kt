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

/**
 * A bridge that passes everything through and remembers how the last generation ended.
 *
 * The engine decides whether a reply finished by looking for an end marker in the text the
 * runtime hands it, and holds back anything that might grow into one, so a test reading the
 * engine's events cannot see what the runtime actually sent last. This keeps the raw
 * fragments as the callback received them: on 2026-11-15 Qwen3 lost its cache record after
 * every reply the runtime ended itself, and the question was what text those replies ended on.
 */
internal class RecordingBridge(private val inner: ExecuTorchBridge) : ExecuTorchBridge by inner {
    private val tail = ArrayDeque<String>()

    /** How many fragments the last [generate] delivered. */
    var fragments = 0
        private set

    /** The last few raw fragments of the last [generate], oldest first. */
    fun lastFragments(): List<String> = synchronized(tail) { tail.toList() }

    override fun generate(
        prompt: String,
        maxNewTokens: Int,
        onToken: (String) -> Unit,
    ): ExecuTorchOutcome {
        synchronized(tail) { tail.clear() }
        fragments = 0
        return inner.generate(prompt, maxNewTokens) { fragment ->
            fragments++
            synchronized(tail) {
                tail.addLast(fragment)
                if (tail.size > KEPT) tail.removeFirst()
            }
            onToken(fragment)
        }
    }

    private companion object {
        const val KEPT = 6
    }
}
