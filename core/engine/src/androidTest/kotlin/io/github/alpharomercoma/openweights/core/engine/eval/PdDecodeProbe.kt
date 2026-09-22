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

package io.github.alpharomercoma.openweights.core.engine.eval

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File

/** Holds the foreground for [PdDecodeProbe]; see the androidTest manifest for why. */
class ProbeActivity : Activity()

/** The probe's entry in libexecutorch_pd_jni.so; see nativeProbeCpuDecode there. */
object PdDecodeProbeNative {
    external fun nativeProbeCpuDecode(
        path: String,
        startPos: Int,
        steps: Int,
        threads: Int,
        holdLifetimeLock: Boolean,
    ): DoubleArray?
}

/**
 * The disaggregated path's CPU decode, measured on phones this repository has no NPU export
 * for.
 *
 * On the Poco the decode half first ran at a tenth of the app's own CPU path, for three
 * reasons: the Android libraries were linked from stale unoptimised archives, the XNNPACK
 * pool was sized and placed without regard to the phone's cores, and the Neuron backend held
 * MediaTek's performance lock for a loaded model's lifetime, which boosted the foreground
 * app and stacked the pool onto two cores. The fixes are meant to hold on every phone, and
 * this checks that on each one it runs on, with the phone's own cores and ROM:
 *
 * - `policy`: the pool as the session sizes it, performant cores minus one.
 * - `all-cores`: ExecuTorch's default of one thread per core, to show whether the policy
 *   earns its place on a chip with efficiency cores.
 * - `policy+lifetime-lock`: the policy with MediaTek's lock held throughout, on MediaTek
 *   phones only, to show whether the old backend behaviour hurts a CPU decode there too.
 * - `aar`: the same file through the prebuilt AAR the app's own path uses, with the same
 *   thread count, as the reference the others should match.
 * - `policy-after-lock`: the policy once more after the lock is released.
 *
 * Every row times [STEPS] decode steps from cache position [START_POS]. It runs on the first
 * LFM2.5 1.2B `.pte` in the eval directory and reports as `pd-decode-probe`.
 */
@RunWith(AndroidJUnit4::class)
class PdDecodeProbe {

    @Test
    fun probe() {
        val model = EVAL_DIR.listFiles { file -> file.extension == "pte" }
            ?.filter { it.name.contains("1.2B") }
            ?.minByOrNull { it.name }
        assumeTrue("no LFM2.5 1.2B .pte in $EVAL_DIR", model != null)
        model!!

        // Loaded directly rather than through DisaggregatedBridge.isAvailable, which also
        // requires MediaTek's adapter: the decode half needs nothing but these two.
        System.loadLibrary("neuron_backend")
        System.loadLibrary("executorch_pd_jni")

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        // Starting an activity earns a launch boost of a few seconds, and a first row timed
        // inside it read 19 ms against 26 for the same settings after; wait it out.
        Thread.sleep(LAUNCH_BOOST_MS)
        val out = JSONObject()
            .put("model", "pd-decode-probe")
            .put("pte", model.name)
            .put("device", Build.MODEL)
            .put("soc", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
            .put("cores", Runtime.getRuntime().availableProcessors())
            .put("start_pos", START_POS)
            .put("steps", STEPS)
            .put("cgroup_while_probing", File("/proc/self/cgroup").readText().trim())
        val runs = JSONArray()
        try {
            // Each configuration twice, interleaved, because a phone warms as it runs and the
            // first rows of a sequence read faster than the same settings later on. The lock
            // rows come last so the boost it leaves behind cannot colour the others, and a
            // plain policy row follows them to show the decode recovering once it is gone.
            var policyThreads = 0
            var lockAvailable = false
            repeat(ROUNDS) {
                val policy = probe(model, "policy", threads = -1, lock = false)
                policyThreads = policy.getInt("pool_threads")
                lockAvailable = policy.getBoolean("lock_available")
                runs.put(policy)
                runs.put(aarReference(model, threads = policyThreads))
                runs.put(probe(model, "all-cores", threads = 0, lock = false))
            }
            if (lockAvailable) {
                repeat(ROUNDS) {
                    runs.put(probe(model, "policy+lifetime-lock", threads = -1, lock = true))
                }
                runs.put(probe(model, "policy-after-lock", threads = -1, lock = false))
            }
        } finally {
            activity.finish()
        }
        out.put("runs", runs)

        val dir = instrumentation.targetContext.getExternalFilesDir(null)!!.resolve("eval-results")
        dir.mkdirs()
        val file = dir.resolve("pd-decode-probe.json")
        file.writeText(out.toString(2))
        Log.i(TAG, "wrote ${file.absolutePath}")
    }

    private fun probe(model: File, label: String, threads: Int, lock: Boolean): JSONObject {
        val result = PdDecodeProbeNative.nativeProbeCpuDecode(
            model.absolutePath,
            START_POS,
            STEPS,
            threads,
            lock,
        )
        checkNotNull(result) { "the probe could not load ${model.name}" }
        val row = JSONObject()
            .put("run", label)
            .put("median_ms", result[RESULT_MEDIAN_MS])
            .put("tok_s", MILLIS / result[RESULT_MEDIAN_MS])
            .put("pool_threads", result[RESULT_POOL_THREADS].toInt())
            .put("performant_cores", result[RESULT_PERFORMANT].toInt())
            .put("cores_busy", result[RESULT_CORES_BUSY])
            .put("lock_available", result[RESULT_LOCK_AVAILABLE] > 0.0)
            .put("effective_uclamp_min", result[RESULT_UCLAMP_MIN].toInt())
        Log.i(TAG, row.toString())
        return row
    }

    /** The same file and steps through the AAR's own runtime, the app's CPU path. */
    private fun aarReference(model: File, threads: Int): JSONObject {
        val module = Module.load(model.absolutePath, Module.LOAD_MODE_FILE, threads)
        try {
            val step = { pos: Long ->
                module.forward(
                    EValue.from(Tensor.fromBlob(longArrayOf(1L), longArrayOf(1, 1))),
                    EValue.from(Tensor.fromBlob(longArrayOf(pos), longArrayOf(1))),
                )
            }
            step(START_POS.toLong())
            val times = (0 until STEPS).map { i ->
                val start = System.nanoTime()
                step(START_POS.toLong() + i)
                (System.nanoTime() - start) / NANOS_PER_MS
            }.sorted()
            val median = times[times.size / 2]
            val row = JSONObject()
                .put("run", "aar")
                .put("median_ms", median)
                .put("tok_s", MILLIS / median)
                .put("pool_threads", threads)
            Log.i(TAG, row.toString())
            return row
        } finally {
            module.destroy()
        }
    }

    private companion object {
        const val TAG = "PdDecodeProbe"
        const val START_POS = 1024
        const val STEPS = 30
        const val ROUNDS = 2
        const val LAUNCH_BOOST_MS = 8_000L
        const val MILLIS = 1000.0
        const val NANOS_PER_MS = 1_000_000.0
        val EVAL_DIR = File("/data/local/tmp/openweights/eval")

        // The layout of the array nativeProbeCpuDecode returns.
        const val RESULT_MEDIAN_MS = 0
        const val RESULT_POOL_THREADS = 1
        const val RESULT_PERFORMANT = 2
        const val RESULT_CORES_BUSY = 3
        const val RESULT_LOCK_AVAILABLE = 4
        const val RESULT_UCLAMP_MIN = 5
    }
}
