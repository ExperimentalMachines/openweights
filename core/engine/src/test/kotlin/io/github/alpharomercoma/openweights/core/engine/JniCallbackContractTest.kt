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
import org.junit.Test
import java.io.File
import java.lang.reflect.Method

/**
 * The native library finds the token and reply callbacks by name and JNI signature, and
 * nothing links the two sides: a Kotlin signature that drifts from the C++ string, or a
 * keep rule that stops matching, compiles and passes every host test.
 *
 * On 2026-09-10 `onToken` gained a log-probability argument on both sides, and the keep
 * rule in consumer-rules.pro still named the one-argument method. R8 dropped the method,
 * and every release from version code 604 aborted on the first token of every GGUF reply.
 * No debug build could show it. These tests read the C++ source, the Kotlin interfaces and
 * the keep rules together, so the drift fails here, on every push, before a release exists;
 * `verifyJniSymbols` then checks the built dex itself.
 */
class JniCallbackContractTest {
    private val lookups: Map<String, String> =
        Regex("""GetMethodID\(\s*[^,]+,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)""")
            .findAll(File("src/main/cpp/llama_jni.cpp").readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { (name, _) -> name != "<init>" }
            .toMap()

    private val callbacks: List<Method> =
        listOf(LlamaBridge.TokenSink::class.java, LlamaBridge.ReplySink::class.java)
            .flatMap { it.declaredMethods.toList() }

    @Test
    fun `the native side looks up both callbacks`() {
        assertThat(lookups.keys).containsExactly("onToken", "onReply")
    }

    @Test
    fun `every native lookup matches a Kotlin callback's signature`() {
        lookups.forEach { (name, signature) ->
            val kotlin = callbacks.filter { it.name == name }.map(::descriptor)
            assertThat(kotlin).containsExactly(signature)
        }
    }

    @Test
    fun `the keep rules hold each callback by name, whatever its arguments`() {
        val rules = File("consumer-rules.pro").readLines()
            .map(String::trim)
            .filterNot { it.startsWith("#") }
            .joinToString("\n")
        lookups.keys.forEach { name ->
            // Once on the interface and once on its implementations. A spelled-out argument
            // list is a copy of the signature that has already gone stale once.
            val kept = Regex("""\*\*\*\s+${Regex.escape(name)}\(\.\.\.\);""").findAll(rules)
            assertThat(kept.count()).isEqualTo(2)
        }
    }

    private fun descriptor(method: Method): String =
        method.parameterTypes.joinToString("", "(", ")") { jvm(it) } + jvm(method.returnType)

    private fun jvm(type: Class<*>): String = when {
        type.isArray -> "[" + jvm(type.componentType)
        type == java.lang.Boolean.TYPE -> "Z"
        type == java.lang.Float.TYPE -> "F"
        type == java.lang.Integer.TYPE -> "I"
        type == java.lang.Long.TYPE -> "J"
        type == java.lang.Double.TYPE -> "D"
        type == Void.TYPE -> "V"
        else -> "L" + type.name.replace('.', '/') + ";"
    }
}
