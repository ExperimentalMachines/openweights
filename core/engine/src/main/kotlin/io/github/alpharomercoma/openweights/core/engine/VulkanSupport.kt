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
import android.content.pm.PackageManager

/**
 * Whether this phone can run a Vulkan export, decided twice.
 *
 * Up front from what Android reports: ExecuTorch's Vulkan delegate needs a Vulkan 1.1 device,
 * and a phone without one is never offered a GPU file. That is necessary and not sufficient.
 * Each shader declares the device features it needs (8- and 16-bit storage, int8 arithmetic,
 * integer dot products), and a driver that lacks one makes the runtime refuse with "not
 * compatible with device" when the model loads or first runs, not when the download starts.
 * Android has no API that reports Vulkan extensions short of native code, so the second
 * decision is learned: the first Vulkan model that fails to open or to run on this phone
 * records why, and from then on GPU files are not offered and the CPU build is named instead.
 *
 * The record is kept per runtime release, because a newer runtime can carry shaders that
 * this driver does run; an update gets one fresh attempt.
 */
object VulkanSupport {
    /** ExecuTorch's Vulkan delegate creates a Vulkan 1.1 instance. */
    private const val VULKAN_1_1 = (1 shl 22) or (1 shl 12)

    /** The runtime release the record belongs to; see the class comment. */
    private const val RUNTIME = "1.5.1"
    private const val PREFS = "vulkan_support"
    private const val KEY_REFUSED = "refused:$RUNTIME"

    @Volatile private var hardware = false

    @Volatile private var refusal: String? = null

    @Volatile private var prefs: SharedPreferences? = null

    /** Reads the device's Vulkan level and any earlier refusal. Called once at startup. */
    fun init(context: Context) {
        hardware = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1)
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = stored
        refusal = stored.getString(KEY_REFUSED, null)
    }

    /** True when GPU files should be offered: Vulkan 1.1 hardware and no refusal on record. */
    val usable: Boolean get() = hardware && refusal == null

    /** Why this phone stopped being offered GPU files, or null if it has not. */
    val refusedBecause: String? get() = refusal

    /** Records that a Vulkan model would not open or run here, so GPU files stop being offered. */
    fun markUnusable(reason: String) {
        refusal = reason
        prefs?.edit()?.putString(KEY_REFUSED, reason)?.apply()
    }

    /**
     * What ExecuTorch's Vulkan runtime says when this device cannot run its shaders at all:
     * a shader needing a feature or extension the driver lacks (vk_api/Exception.cpp), or no
     * usable Vulkan device. Matched in the failure's message chain, which the Android wrapper
     * fills with the runtime's recent log. Out of device memory is deliberately absent: that
     * is this model being too large for this GPU, and a smaller one may run.
     */
    private val INCOMPATIBLE = listOf(
        "not compatible with device",
        "physical device feature",
        "VulkanBackend is not available",
        "VulkanBackend is not registered",
        "VK_ERROR_INITIALIZATION_FAILED",
        "VK_ERROR_INCOMPATIBLE_DRIVER",
        "VK_ERROR_FEATURE_NOT_PRESENT",
        "VK_ERROR_EXTENSION_NOT_PRESENT",
    )

    /** The incompatibility [failure] reports, or null when it is any other kind of failure. */
    fun incompatibility(failure: Throwable?): String? =
        generateSequence(failure) { it.cause }.take(MAX_CAUSES)
            .mapNotNull { it.message }
            .firstNotNullOfOrNull { message ->
                INCOMPATIBLE.firstOrNull { it in message }?.let { message.lineContaining(it) }
            }

    /**
     * Records [failure] as a refusal when it is a Vulkan incompatibility; any other failure
     * changes nothing, so a bad file, a full window or an allocation cannot cost the phone
     * its GPU builds. Returns the recorded reason, or null.
     */
    fun recordIfIncompatible(failure: Throwable?): String? =
        incompatibility(failure)?.also(::markUnusable)

    private const val MAX_CAUSES = 8

    private fun String.lineContaining(marker: String): String =
        lineSequence().firstOrNull { marker in it }?.trim()?.take(MAX_REASON) ?: marker

    private const val MAX_REASON = 240
}
