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
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay

/**
 * What the phone was doing when a timed turn started or ended.
 *
 * Every cold-versus-warm row carries one, because the POCO throttles a screen-off run two to
 * five times and a hot phone prefills slower than a cool one: a comparison is only as good as
 * the evidence that both arms started from the same state.
 */
internal data class DeviceState(
    val cpuC: Double,
    val batteryC: Double,
    val screenOn: Boolean,
    val waitedS: Int,
) {
    companion object {
        /** The POCO's gate in `tools/snapdragon/results/2026-11-14-runtime-upgrade/phones-3way/run3.sh`. */
        private const val COOL_BELOW_C = 45.0
        private const val COOL_LIMIT_S = 240
        private const val COOL_POLL_S = 5

        /**
         * The state now; with [coolFirst], after waiting for the CPU to fall under 45 C for
         * at most 240 s. `-e coolBelowC` and `-e coolLimitS` change both, so a Test Lab run on
         * a hotter phone still ends inside its time limit.
         */
        suspend fun read(coolFirst: Boolean): DeviceState {
            val arguments = InstrumentationRegistry.getArguments()
            val below = arguments.getString("coolBelowC")?.toDoubleOrNull() ?: COOL_BELOW_C
            val limit = arguments.getString("coolLimitS")?.toIntOrNull() ?: COOL_LIMIT_S
            var waited = 0
            var cpu = cpuCelsius()
            while (coolFirst && cpu >= below && waited < limit) {
                delay(COOL_POLL_S * 1000L)
                waited += COOL_POLL_S
                cpu = cpuCelsius()
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val battery =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -10)?.div(10.0) ?: -1.0
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return DeviceState(cpu, battery, power.isInteractive, waited)
        }

        /**
         * The CPU temperature from the thermal HAL, read through the shell because the POCO's
         * ROM keeps thermal zones from apps. The hottest sensor of the CPU type, whatever the
         * vendor named it: the POCO has one called CPU, other HALs report one per cluster.
         */
        private fun cpuCelsius(): Double {
            val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("dumpsys thermalservice")
            val text = ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use {
                it.readText()
            }
            val hal = text.substringAfter("Current temperatures from HAL", "")
                .substringBefore("Current cooling devices")
            return Regex("""mValue=([0-9.]+), mType=0,""").findAll(hal)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }.maxOrNull() ?: -1.0
        }
    }
}

/** [text] as a JSON string literal, for result rows written without a JSON library. */
internal fun jsonString(text: String): String = buildString {
    append('"')
    for (c in text) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}
