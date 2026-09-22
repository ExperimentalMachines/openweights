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

/**
 * JNI wrapper for the Prefill/Decode Disaggregated runtime: the MediaTek NPU for prefill, the
 * CPU's XNNPACK delegate for decode, sized and placed the way the app's own ExecuTorch path is.
 */
class DisaggregatedBridge {

    /**
     * @param runnerOptionsJson the exporter's `runner` block, verbatim. Nothing in a
     *   compiled chunk declares how many heads it has or what type its inputs are, so
     *   these come from the manifest written beside it; an empty string falls back to
     *   MediaTek's own runner defaults.
     */
    external fun nativeLoad(
        runnerOptionsJson: String,
        promptModelPaths: String,
        tokenEmbeddingPath: String,
        cpuModelPath: String,
        tokenizerPath: String,
        temperature: Float,
    ): Long

    external fun nativePrefill(handle: Long, prompt: String): Int

    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        callback: DisaggregatedCallback,
    ): LongArray?

    external fun nativeResetContext(handle: Long)

    external fun nativeStop(handle: Long)

    external fun nativeClose(handle: Long)

    companion object {
        private const val TAG = "OpenWeightsPD"

        /**
         * Whether this phone can run the NPU half at all, answered by loading the
         * libraries rather than by asking what chip this is.
         *
         * The adapter is the device's own copy, from `/system_ext/lib64`, reached because
         * the manifest names it in `uses-native-library`; MediaTek lists it in
         * `/system/etc/public.libraries-mtk.txt` for exactly this. It is deliberately not
         * bundled in the APK: MediaTek's SDK build of the same library runs its
         * constructors in the app's linker namespace and dies on a null call, measured as
         * a SIGSEGV inside `.init_array` before any of our code ran.
         *
         * It is also deliberately not wrapped in `runCatching`. Without the adapter the
         * Neuron backend cannot allocate shared weights and calls `ET_CHECK`, which aborts
         * the process rather than returning an error, so a phone that cannot load it must
         * never reach the NPU path.
         */
        val isAvailable: Boolean by lazy {
            try {
                System.loadLibrary("neuronusdk_adapter.mtk")
                System.loadLibrary("neuron_backend")
                System.loadLibrary("executorch_pd_jni")
                Log.i(TAG, "NeuroPilot NPU runtime available")
                true
            } catch (t: Throwable) {
                Log.i(TAG, "No NeuroPilot NPU on this device: ${t.message}")
                false
            }
        }
    }
}
