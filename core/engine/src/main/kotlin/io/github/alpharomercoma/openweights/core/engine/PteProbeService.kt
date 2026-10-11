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
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log

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
        try {
            bridge.load(path, tokenizer, temperature = 0f, multimodal = multimodal)
            // An NPU graph can open and still fail on its first call, so the trial reads a
            // prompt with it: the app's process opens the half only after this has (codex QA).
            if (bridge.prefillingOnNpu) npu = runCatching { bridge.prefill(TRIAL_PROMPT) }.isSuccess
        } finally {
            bridge.close()
        }
        return facts.toBundle().apply { npu?.let { putBoolean(KEY_NPU_READ, it) } }
    }

    internal companion object {
        const val TAG = "OpenWeightsProbe"
        const val PROBE = 1
        const val VERIFY = 2
        const val KEY_MODEL = "model"
        const val KEY_TOKENIZER = "tokenizer"
        const val KEY_MULTIMODAL = "multimodal"
        const val KEY_FAILED = "failed"

        /** Present when the trial opened an NPU half: whether it read [TRIAL_PROMPT]. */
        const val KEY_NPU_READ = "npuRead"
        private const val TRIAL_PROMPT = "Hello"
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
