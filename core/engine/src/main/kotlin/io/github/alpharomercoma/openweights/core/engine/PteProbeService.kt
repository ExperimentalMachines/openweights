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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import io.github.alpharomercoma.openweights.core.common.model.CompiledBackend
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Hands a `.pte` to ExecuTorch in a process of its own, so that what the runtime does to an
 * unknown file happens to this process and not to the app.
 *
 * Two failures cannot be contained in the app's process (docs/research/executorch-open-abort.md):
 * any error the runtime raises while opening aborts through fbjni ('ptr'), and a file whose
 * header is sound but whose program is not makes the loader read out of bounds (SIGSEGV on the
 * Poco, 2026-10-11, from the model picker's probe at launch). Here either one ends this process,
 * [PteProbe] reads the exit reason, and the file is refused with a message.
 *
 * Declared in core/engine's manifest as `android:process=":pte_probe"`. Requests are served one
 * at a time on a single thread, because a trial load holds a whole model in memory.
 */
class PteProbeService : Service() {

    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        // The trial open of a Qualcomm NPU file needs the DSP's library path, which this
        // process sets for itself; the application's start-up does nothing here.
        QnnSupport.init(this)
        worker = HandlerThread("pte-probe").apply { start() }
        messenger = Messenger(Handler(worker.looper, ::serve))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        worker.quitSafely()
        super.onDestroy()
    }

    private fun serve(message: Message): Boolean {
        val request = message.data
        val path = request.getString(KEY_MODEL) ?: return true
        val reply = Message.obtain(null, message.what)
        reply.data = runCatching {
            when (message.what) {
                PROBE -> NativeExecuTorchBridge().probe(path).toBundle()
                VERIFY -> verify(
                    path,
                    request.getString(KEY_TOKENIZER).orEmpty(),
                    request.getBoolean(KEY_MULTIMODAL),
                )
                else -> Bundle()
            }
        }.getOrElse { cause ->
            // A failure the runtime raised as a Java exception rather than a crash: still a
            // refusal, and still one the app can show.
            Bundle().apply {
                putString(KEY_FAILED, cause.message ?: cause::class.java.simpleName)
            }
        }
        runCatching { message.replyTo?.send(reply) }
            .onFailure { Log.w(TAG, "the app went away before the answer", it) }
        return true
    }

    /**
     * Opens the file the way the engine will, then closes it: the runner is the authority. The
     * same bridge the engine is given, so on a phone with the NeuroPilot runtime the staged NPU
     * chunks are opened here too, not only the CPU file (codex QA).
     */
    private fun verify(path: String, tokenizer: String, multimodal: Boolean): Bundle {
        val bridge = ExecuTorchSupport.bridge(trial = true)
        val facts = bridge.probe(path)
        facts.unopenable?.let { return facts.toBundle() }
        var npu: Boolean? = null
        var trialFailed: String? = null
        try {
            bridge.load(path, tokenizer, temperature = 0f, multimodal = multimodal)
            // An NPU graph can open and still fail on its first call, the handoff of its cache
            // to the CPU half runs only when a reply starts, and the first token comes from
            // the NPU's logits, so the trial writes until the CPU half has decoded at least one
            // token: the app's process opens the half only after that (codex QA). A reply that
            // stopped before the CPU ran proves nothing either way and is not a failure.
            if (bridge.prefillingOnNpu) {
                npu = runCatching { bridge.generate(TRIAL_PROMPT, TRIAL_TOKENS) {} }.fold(
                    onSuccess = { reply -> (reply.generatedTokens >= 2).takeIf { it } },
                    onFailure = { false },
                )
            } else if (!multimodal && (facts.offCpu || runsOffTheCpu(path))) {
                // A GPU or Qualcomm NPU graph that opens can still fail natively on its first
                // dispatch, where each shader or HTP graph actually runs, and the app's process
                // would take that crash on the user's first turn. A few tokens here keep it in
                // this one (codex QA of ExecuTorch 1.6.0, whose Vulkan backend was rewritten).
                // The file's own delegates decide, and its name too, so a renamed export is
                // still tried. A failure the runtime raises in Java comes back as the refusal.
                trialFailed = markedTrial(bridge, path)
            }
        } finally {
            bridge.close()
        }
        return facts.toBundle().apply {
            npu?.let { putBoolean(KEY_NPU_READ, it) }
            // Shown and not remembered, so the next open tries again: one trial is not proof
            // that the file is at fault rather than a passing GPU fault (codex QA).
            trialFailed?.let { putString(KEY_FAILED, it) }
        }
    }

    /**
     * [runTrial] with the marker that outlives a native crash, so the app can tell a dispatch
     * that crashed, which may not happen again, from a file the loader cannot read. Without the
     * marker such a crash would be remembered against the file, so a trial that cannot leave
     * one is not run, and the open goes ahead as it did before the trial existed (codex QA).
     *
     * @return why the trial refuses the file, or null.
     */
    private fun markedTrial(bridge: ExecuTorchBridge, path: String): String? {
        val marker = trialMarker(this)
        if (runCatching { marker.writeText(path) }.isFailure) return null
        return try {
            runTrial(bridge).refusal
        } finally {
            marker.delete()
        }
    }

    /**
     * What a trial generation came back with, and how many pieces of text reached Kotlin.
     *
     * @property wroteNothingReadable the runtime counted [TRIAL_TOKENS] or more tokens and not
     * one of them reached the callback. The JNI layer holds text back until everything it has
     * buffered is valid UTF-8, and a byte that cannot start a character never becomes valid,
     * so a graph that computes garbage can write a whole window without a callback (codex QA).
     * An English continuation of [TRIAL_PROMPT] reaches the callback on its first token.
     */
    internal class TrialRun(
        val outcome: ExecuTorchOutcome,
        val callbacks: Int,
        val stoppedByDeadline: Boolean,
    ) {
        val wroteNothingReadable: Boolean
            get() = callbacks == 0 && outcome.generatedTokens >= TRIAL_TOKENS

        /**
         * The deadline stopped the trial before one decode step: the patched QNN runner keeps
         * a stop asked during prefill and never enters its token loop, and the runtime's count
         * leaves out the token prefill writes. The decode graph's first dispatch was not
         * tried, so this is no pass (codex QA). The trial's prompt is six tokens, one call of
         * the prefill graph, which the NPU reads 10 to 16 times as fast as the CPU
         * (docs/research/snapdragon-pd-matrix.md): a prefill past the deadline is an NPU that
         * hung, and the open is refused this time only.
         */
        val decodeUntried: Boolean
            get() = stoppedByDeadline && outcome.generatedTokens == 0

        /** Why this trial cannot pass the file, or null when it did. */
        val refusal: String?
            get() = when {
                wroteNothingReadable -> UNREADABLE
                decodeUntried -> TOO_SLOW_TO_REPLY
                else -> null
            }
    }

    internal companion object {
        /**
         * Generates [TRIAL_TOKENS] and no more. The runtime reads its allowance from the window,
         * not from the requested count (the AAR's generate never reads maxNewTokens, 1.4.0 to
         * 1.6.0), and clears its stop flag when its token loop starts, so the count is held
         * here and the stop asked again on every token past it, as the engine does for a turn.
         * Unbounded, a continuation that never reaches its end ran until the probe timed out
         * (codex QA).
         *
         * Counting needs callbacks, and the runtime can generate without making one (see
         * [TrialRun.wroteNothingReadable]), so past [deadlineMs] a watchdog asks for the stop
         * every [STOP_REPEAT_MS] until the generation returns. Prefill cannot be interrupted
         * and the stock runner clears a stop asked during it, so even an early deadline lets
         * at least one decode step run: the first dispatch of both phases is still tried.
         */
        fun runTrial(bridge: ExecuTorchBridge, deadlineMs: Long = TRIAL_DEADLINE_MS): TrialRun {
            var tokens = 0
            val done = CountDownLatch(1)
            val fired = AtomicBoolean(false)
            val watchdog = thread(name = "pte-trial-watchdog", isDaemon = true) {
                if (!done.await(deadlineMs, TimeUnit.MILLISECONDS)) {
                    fired.set(true)
                    do {
                        bridge.stop()
                    } while (!done.await(STOP_REPEAT_MS, TimeUnit.MILLISECONDS))
                }
            }
            val outcome = try {
                bridge.generate(TRIAL_PROMPT, TRIAL_TOKENS) {
                    tokens++
                    if (tokens >= TRIAL_TOKENS) bridge.stop()
                }
            } finally {
                done.countDown()
                watchdog.join()
            }
            return TrialRun(outcome, tokens, stoppedByDeadline = fired.get())
        }

        /** Present while a trial generates: shared by the app and the probe process. */
        fun trialMarker(context: Context): File = File(context.noBackupFilesDir, TRIAL_MARKER)

        /** True for a file compiled for a GPU or an NPU, read from its name as Discover does. */
        fun runsOffTheCpu(path: String): Boolean =
            CompiledBackend.of(File(path).name).processor != CompiledBackend.Processor.CPU

        const val TAG = "OpenWeightsProbe"
        const val PROBE = 1
        const val VERIFY = 2
        const val KEY_MODEL = "model"
        const val KEY_TOKENIZER = "tokenizer"
        const val KEY_MULTIMODAL = "multimodal"
        const val KEY_FAILED = "failed"

        /** Present when the trial opened an NPU half: whether it answered [TRIAL_PROMPT]. */
        const val KEY_NPU_READ = "npuRead"

        // Text the model continues rather than answers, so it does not stop at once.
        private const val TRIAL_PROMPT = "The capital of France is"
        const val TRIAL_TOKENS = 4

        /**
         * When the trial is stopped whatever it has written. A sound trial takes a few seconds
         * (3.3 s on the emulator's Vulkan, docs/research/llama-cpp-v100-executorch-160.md), so
         * this ends one the callbacks cannot, well inside [PteProbe]'s five-minute ceiling.
         */
        const val TRIAL_DEADLINE_MS = 30_000L
        const val STOP_REPEAT_MS = 250L

        /** The refusal for [TrialRun.wroteNothingReadable], completing "cannot be opened: ". */
        const val UNREADABLE = "it wrote no readable text when tried on this phone; try again"

        /** The refusal for [TrialRun.decodeUntried], completing "cannot be opened: ". */
        const val TOO_SLOW_TO_REPLY = "its trial did not reach a reply in time; try again"
        private const val TRIAL_MARKER = "pte-trial-running"
        private const val KEY_WINDOW = "window"
        private const val KEY_VISION = "vision"
        private const val KEY_PREFILL = "prefill"
        private const val KEY_STATE_RESET = "stateReset"
        private const val KEY_UNOPENABLE = "unopenable"

        fun ExportFacts.toBundle() = Bundle().apply {
            contextLength?.let { putInt(KEY_WINDOW, it) }
            putBoolean(KEY_VISION, hasVision)
            prefillLength?.let { putInt(KEY_PREFILL, it) }
            stateResetAtZero?.let { putBoolean(KEY_STATE_RESET, it) }
            unopenable?.let { putString(KEY_UNOPENABLE, it) }
        }

        fun Bundle.toFacts() = ExportFacts(
            contextLength = takeIf { containsKey(KEY_WINDOW) }?.getInt(KEY_WINDOW),
            hasVision = getBoolean(KEY_VISION),
            prefillLength = takeIf { containsKey(KEY_PREFILL) }?.getInt(KEY_PREFILL),
            stateResetAtZero = takeIf { containsKey(KEY_STATE_RESET) }?.getBoolean(KEY_STATE_RESET),
            unopenable = getString(KEY_UNOPENABLE),
        )
    }
}
