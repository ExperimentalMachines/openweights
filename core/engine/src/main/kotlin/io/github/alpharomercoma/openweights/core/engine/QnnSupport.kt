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

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.system.Os
import java.io.File

/**
 * Whether this phone can run a Qualcomm NPU (QNN HTP) export, and the setup one needs.
 *
 * Ported from ExecuServe, where it was measured on a Snapdragon 8 Elite Gen 5 (2026-10-06:
 * 1,700 tok/s prefill on a 700-token prompt against the CPU's 340). A QNN file is compiled for
 * one chip and loads only on it, so Discover offers exactly this phone's folder (`qnn/<soc>`,
 * from `Build.SOC_MODEL`) and only on a Qualcomm SoC. The HTP drivers come with the app
 * (Qualcomm's `qnn-runtime`), one skeleton per Hexagon generation; the DSP loads its skeleton
 * by path, so the libraries are extracted at install and [prepare] points `ADSP_LIBRARY_PATH`
 * at them.
 *
 * As with [VulkanSupport], a phone that cannot start the delegate after all says so in the
 * runtime's own words when a model opens, and that is recorded per runtime release, so NPU
 * files stop being offered until an update.
 */
object QnnSupport {
    private const val PREFS = "qnn_support"
    private const val KEY_REFUSED = "refused:${BuildConfig.EXECUTORCH_VERSION}"

    /** The HTP library every QNN model loads; its presence says the app carries the drivers. */
    private const val HTP_LIBRARY = "libQnnHtp.so"

    @Volatile private var nativeDir: String? = null

    @Volatile private var hardware = false

    @Volatile private var refusal: String? = null

    @Volatile private var prefs: SharedPreferences? = null

    /** This phone's chip as Android names it (`SM8850`), or null when it does not say. */
    val soc: String? get() = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }

    /** Reads the chip, the bundled drivers and any earlier refusal. Called once at start-up. */
    fun init(context: Context) {
        val dir = context.applicationInfo.nativeLibraryDir
        nativeDir = dir
        hardware = Build.SOC_MANUFACTURER.equals("QTI", ignoreCase = true) &&
            soc != null &&
            File(dir, HTP_LIBRARY).isFile
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = stored
        refusal = stored.getString(KEY_REFUSED, null)
    }

    /** True when this phone's NPU files should be offered. */
    val usable: Boolean get() = hardware && refusal == null

    /**
     * Environment the QNN runner reads, set before each model opens: where the DSP finds its
     * skeleton, and the model family, which picks the runner's stop tokens (the patched runtime
     * reads it; the stock one assumes Llama 3 for every model).
     */
    fun prepare(modelName: String) {
        val dir = nativeDir ?: return
        Os.setenv(
            "ADSP_LIBRARY_PATH",
            "$dir;/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp",
            true,
        )
        Os.setenv(DECODER_ENV, decoderModel(modelName), true)
    }

    /** Records that the NPU would not start here, so NPU files stop being offered. */
    private fun markUnusable(reason: String) {
        refusal = reason
        prefs?.edit()?.putString(KEY_REFUSED, reason)?.apply()
    }

    /**
     * What the QNN delegate says when this phone cannot run it at all: the backend missing
     * from the build, or the HTP refusing to start. A file for another chip, a full window or a
     * bad file are not on the list; those cost one model, not the phone's NPU.
     */
    private val INCOMPATIBLE = listOf(
        "QnnBackend is not available",
        "QnnBackend is not registered",
        "Failed to get Qnn interface",
        "Fail to create Qnn backend",
        "Failed to create Qnn device",
    )

    /** Records [failure] as a refusal when it is an NPU incompatibility; returns the reason, or null. */
    fun recordIfIncompatible(failure: Throwable?): String? =
        generateSequence(failure) { it.cause }.take(MAX_CAUSES)
            .mapNotNull { it.message }
            .firstNotNullOfOrNull { message ->
                INCOMPATIBLE.firstOrNull {
                    it in message
                }?.let { marker -> message.lineContaining(marker) }
            }
            ?.also(::markUnusable)

    /** The variable the patched JNI layer reads for the decoder family. */
    const val DECODER_ENV = "EXECUTORCH_QNN_DECODER_MODEL"

    /**
     * The family Qualcomm's runner knows (runner.cpp's DecoderModelVersion), read from the file
     * name the way [io.github.alpharomercoma.openweights.core.common.model.PromptTemplates] reads
     * it. Unknown families keep the runtime's own default.
     */
    fun decoderModel(fileName: String): String {
        val name = fileName.lowercase().filter { it.isLetterOrDigit() }
        return when {
            "qwen35" in name -> "llama3"
            "qwen3" in name -> "qwen3"
            "qwen25" in name -> "qwen2_5"
            "llama32" in name -> "llama3"
            "gemma3" in name -> "gemma3"
            "smollm2" in name -> "smollm2_135m"
            "smollm3" in name -> "smollm3"
            "phi4mini" in name -> "phi_4_mini"
            else -> "llama3"
        }
    }

    private const val MAX_CAUSES = 8
    private const val MAX_REASON = 240

    private fun String.lineContaining(marker: String): String =
        lineSequence().firstOrNull { marker in it }?.trim()?.take(MAX_REASON) ?: marker
}
