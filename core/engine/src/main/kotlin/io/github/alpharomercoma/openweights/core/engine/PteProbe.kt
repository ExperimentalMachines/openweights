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

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.util.Log
import io.github.alpharomercoma.openweights.core.engine.PteProbeService.Companion.toFacts
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** The name the probe process runs under, as a suffix of the app's own process name. */
const val PTE_PROBE_PROCESS = ":pte_probe"

/**
 * The app's side of [PteProbeService]: asks it about a file and reads what happened.
 *
 * Three outcomes, and only one of them is the file's fault. An answer is an answer. A process
 * that died of a native crash while holding the file refuses it, and the refusal is remembered
 * for that file (and its tokenizer, and this runtime release), so a file that crashes the
 * runtime is not handed to it again on every launch. A process that died of anything else, or
 * did not answer in time, says nothing about the file: it is asked once more, and then refused
 * for now without being remembered.
 *
 * Never answered by reading the file here instead. That fallback was what every earlier build
 * did, and it is the crash this class exists to contain (codex QA).
 *
 * One request at a time across the whole app, because the probe process serves one at a time:
 * with two outstanding, a crash on one file was blamed on both, and the shorter timeout of one
 * killed the other's trial (codex QA). Blocking, so never on the main thread.
 */
class PteProbe(context: Context) {

    private val app = context.applicationContext
    private val remembered = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /**
     * What the file says about itself, read in the probe process.
     *
     * @throws IllegalStateException when no answer came, so the model picker asks again on its
     * next refresh and the engine refuses the open with the reason.
     */
    fun probe(path: String): ExportFacts {
        val key = keyFor(PROBE_KEY, File(path), tokenizer = null)
        remembered.getString(key, null)?.let { return refused(it) }
        return when (
            val answer = askTwice(
                PteProbeService.PROBE,
                request(path),
                PROBE_TIMEOUT_MS,
            )
        ) {
            is Answer.Read -> {
                answer.bundle.getString(PteProbeService.KEY_FAILED)?.let { error(it) }
                answer.bundle.toFacts()
            }
            is Answer.Crashed -> refused(remember(key, answer.why))
            is Answer.Inconclusive -> error(answer.why)
        }
    }

    /**
     * Opens the file in the probe process exactly as the engine is about to, once per file and
     * tokenizer.
     *
     * @return why the engine must not open it, or null to go ahead.
     */
    fun verify(path: String, tokenizer: String, multimodal: Boolean): String? {
        val file = File(path)
        // The trial opens a MediaTek install's NPU half as well, so the half is part of what
        // was verified: one that lands, changes or is refused later is verified again before
        // the app opens it (codex QA).
        val key = keyFor("$VERIFY_KEY:$multimodal", file, File(tokenizer)) + npuIdentity(file)
        val known = remembered.getString(key, null)
        // The CPU build passed and the NPU half was not tried, for want of memory: tried again
        // once it would fit, and the CPU goes ahead until then.
        val retry = known == VERIFIED_CPU && npuWouldOpen(file)
        if (known != null &&
            !retry
        ) {
            return known.takeUnless { it == VERIFIED || it == VERIFIED_CPU }
        }
        val data = request(path).apply {
            putString(PteProbeService.KEY_TOKENIZER, tokenizer)
            putBoolean(PteProbeService.KEY_MULTIMODAL, multimodal)
        }
        val started = System.nanoTime()
        val answer = askTwice(PteProbeService.VERIFY, data, VERIFY_TIMEOUT_MS)
        Log.i(
            PteProbeService.TAG,
            "verified ${file.name} in ${(System.nanoTime() - started) / NANOS_PER_MS} ms: $answer",
        )
        // A half that crashed the trial or could not read its prompt is refused, and the CPU
        // build is judged again on its own.
        val again = { why: String ->
            NpuInstall.refuse(file, why)
            verify(path, tokenizer, multimodal)
        }
        return when (answer) {
            is Answer.Read -> passed(answer.bundle, file, key) ?: run {
                if (npuFailed(
                        answer.bundle,
                    )
                ) {
                    again("it could not answer its trial prompt")
                } else {
                    null
                }
            }
            // The trial opened the pair, and either half can be what crashed it: a garbage
            // tokenizer beside a sound model ends the process the same way (on the Poco). The
            // NPU half opens in the same trial and a bad chunk crashes it the same way.
            is Answer.Crashed -> if (npuUsable(file)) {
                again("it crashed the trial open")
            } else {
                remember(key, PAIR_CRASHED)
            }
            is Answer.Inconclusive -> answer.why
        }
    }

    private fun npuFailed(bundle: Bundle): Boolean =
        bundle.containsKey(PteProbeService.KEY_NPU_READ) &&
            !bundle.getBoolean(PteProbeService.KEY_NPU_READ)

    /**
     * Why a trial that ran to the end refuses the file, remembered when it is the file's, or
     * null when it passed or its NPU half failed, which [verify] answers itself.
     */
    private fun passed(bundle: Bundle, file: File, key: String): String? {
        if (npuFailed(bundle)) return null
        val npuRead = bundle.getBoolean(PteProbeService.KEY_NPU_READ, false)
        if (npuRead) NpuInstall.markTried(file)
        val verdict = if (npuRead || !npuUsable(file)) VERIFIED else VERIFIED_CPU
        // A Java-level failure is shown and not remembered: it is not known to be the file's,
        // and the next open should be allowed to try again.
        return bundle.getString(PteProbeService.KEY_FAILED)
            ?: bundle.toFacts().unopenable?.let { remember(key, it) }
            ?: null.also { remember(key, verdict) }
    }

    /**
     * One retry for what was not the file's doing: a process the system took, a slow bind. Not
     * after a timeout, which would hold every model operation for a second full wait, and not
     * after [abandon].
     */
    private fun askTwice(what: Int, data: Bundle, timeoutMs: Long): Answer {
        // Numbered before the queue, so a cancel issued while this waits behind another
        // request still reaches it; cleared after the lock, it used to start its whole wait
        // after the user had already pressed Stop (codex QA).
        val ticket = TICKETS.issue()
        return synchronized(ONE_AT_A_TIME) {
            if (TICKETS.cancelled(
                    ticket,
                )
            ) {
                return@synchronized Answer.Inconclusive(CANCELLED, retry = false)
            }
            val first = ask(what, data, timeoutMs, ticket)
            if (first is Answer.Inconclusive &&
                first.retry
            ) {
                ask(what, data, timeoutMs, ticket)
            } else {
                first
            }
        }
    }

    /**
     * Gives up on every request made so far, the one in flight and any still queued, and ends
     * the probe process: the engine's cancel reaches here, so a trial that hangs does not hold
     * the engine for its whole wait. A request made after this is not affected.
     */
    fun abandon() {
        TICKETS.cancelIssued()
    }

    private sealed interface Answer {
        data class Read(val bundle: Bundle) : Answer
        data class Crashed(val why: String) : Answer

        /** [retry]: worth asking once more; not after a timeout or a cancel (codex QA). */
        data class Inconclusive(val why: String, val retry: Boolean = true) : Answer
    }

    private fun ask(what: Int, data: Bundle, timeoutMs: Long, ticket: Long): Answer {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "PteProbe blocks; call it off the main thread"
        }
        val since = System.currentTimeMillis()
        val events = LinkedBlockingQueue<Any>()
        val replies = HandlerThread("pte-probe-reply").apply { start() }
        val replyTo = Messenger(
            Handler(replies.looper) { message ->
                events.offer(Bundle(message.data))
                true
            },
        )
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                events.offer(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                events.offer(DIED)
            }

            override fun onBindingDied(name: ComponentName?) {
                events.offer(DIED)
            }

            override fun onNullBinding(name: ComponentName?) {
                events.offer(DIED)
            }
        }
        return try {
            val bound = app.bindService(
                Intent(app, PteProbeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
            val binder = events.takeIf { bound }?.poll(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (binder !is IBinder) return Answer.Inconclusive(NOT_CHECKED)
            Messenger(binder).send(
                Message.obtain(null, what).apply {
                    this.data = data
                    this.replyTo = replyTo
                },
            )
            when (val event = awaitAnswer(events, timeoutMs, ticket)) {
                is Bundle -> Answer.Read(event)
                DIED -> crashSince(since)?.let(Answer::Crashed) ?: Answer.Inconclusive(NOT_CHECKED)
                ABANDONED -> {
                    killProbe()
                    Answer.Inconclusive(CANCELLED, retry = false)
                }
                else -> {
                    // Out of time with the file still in the runtime's hands: the process is
                    // ended so the next request starts clean, and the file is not judged.
                    killProbe()
                    Answer.Inconclusive(TOO_SLOW, retry = false)
                }
            }
        } catch (gone: RemoteException) {
            Log.w(PteProbeService.TAG, "the probe went away mid-request", gone)
            crashSince(since)?.let(Answer::Crashed) ?: Answer.Inconclusive(NOT_CHECKED)
        } finally {
            runCatching { app.unbindService(connection) }
            replies.quitSafely()
        }
    }

    /** The next event, in slices so [abandon] is seen within one; null when out of time. */
    private fun awaitAnswer(events: LinkedBlockingQueue<Any>, timeoutMs: Long, ticket: Long): Any? {
        val deadline = System.nanoTime() + timeoutMs * NANOS_PER_MS
        while (true) {
            if (TICKETS.cancelled(ticket)) return ABANDONED
            val left = (deadline - System.nanoTime()) / NANOS_PER_MS
            if (left <= 0) return null
            events.poll(minOf(left, ABANDON_POLL_MS), TimeUnit.MILLISECONDS)?.let { return it }
        }
    }

    /**
     * Why the probe process last ended, if it ended after [since] in a way that blames the
     * file. The system writes the record a moment after the death is reported, so it is
     * waited for briefly.
     */
    private fun crashSince(since: Long): String? {
        val manager = app.getSystemService(ActivityManager::class.java) ?: return null
        repeat(EXIT_POLLS) {
            val exit = manager.getHistoricalProcessExitReasons(app.packageName, 0, EXIT_RECORDS)
                .firstOrNull { it.processName == probeProcess && it.timestamp >= since }
            if (exit != null) {
                Log.i(
                    PteProbeService.TAG,
                    "probe ended: reason ${exit.reason}, ${exit.description}",
                )
                // Only a crash in the runtime is the file's doing. A SIGKILL is a task killer,
                // the system or this class's own timeout, and an uncaught Java exception in the
                // service is a bug in it; neither may cost the file its place.
                val signalled = exit.reason == ApplicationExitInfo.REASON_SIGNALED
                val crashed = exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    signalled &&
                    exit.status in CRASH_SIGNALS
                return if (crashed) {
                    "the ExecuTorch runtime crashed reading it, so the file is damaged or was " +
                        "made for a different runtime"
                } else {
                    null
                }
            }
            Thread.sleep(EXIT_POLL_MS)
        }
        return null
    }

    private fun killProbe() {
        val manager = app.getSystemService(ActivityManager::class.java) ?: return
        manager.runningAppProcesses.orEmpty()
            .firstOrNull { it.processName == probeProcess }
            ?.let { Process.killProcess(it.pid) }
    }

    private val probeProcess get() = "${app.packageName}$PTE_PROBE_PROCESS"

    private fun request(path: String) = Bundle().apply {
        putString(PteProbeService.KEY_MODEL, path)
    }

    // Written before returning, not queued: a process ended right after an answer (the user
    // swiping the app away, or the system) lost a queued write, and the file was checked again
    // on the next open (measured on the Poco).
    private fun remember(key: String, why: String): String {
        remembered.edit().putString(key, why).commit()
        return why
    }

    private fun refused(why: String) =
        ExportFacts(contextLength = null, hasVision = false, unopenable = why)

    // Size and time are in the key so a file replaced by a good download is asked again; the
    // tokenizer's too, because the runner opens the pair; and the runtime release, so an update
    // that changes what the runtime opens asks again.
    private fun keyFor(kind: String, file: File, tokenizer: File?): String = buildString {
        append(kind).append('|').append(BuildConfig.EXECUTORCH_VERSION)
        append('|').append(file.identity())
        tokenizer?.let { append('|').append(it.identity()) }
    }

    private fun File.identity() = "$absolutePath:${length()}:${lastModified()}"

    /** Whether [model] has an NPU half the bridge would consider: complete and not refused. */
    private fun npuUsable(model: File): Boolean =
        NpuInstall.read(model) != null && NpuInstall.refusal(model) == null

    /** Whether the bridge would open [model]'s NPU half now, memory permitting. */
    private fun npuWouldOpen(model: File): Boolean {
        val half =
            NpuInstall.read(model)?.takeIf { NpuInstall.refusal(model) == null } ?: return false
        return NpuMemory.shortfall(model, half) == null
    }

    private fun npuIdentity(model: File): String {
        val half = NpuInstall.read(model) ?: return ""
        val refused = NpuInstall.refusal(model)?.let { "|refused" }.orEmpty()
        return (half.chunks + half.embedding).joinToString("") { "|" + it.identity() } +
            "|" + half.runnerJson.hashCode() + refused
    }

    private companion object {
        val ONE_AT_A_TIME = Any()
        val ABANDONED = Any()

        val TICKETS = CancelTickets()
        const val ABANDON_POLL_MS = 250L
        const val CANCELLED = "checking it was cancelled"
        const val STORE = "pte_checks"
        const val PAIR_CRASHED = "the ExecuTorch runtime crashed opening it with its tokenizer, " +
            "so one of the two is damaged or was made for a different runtime"
        const val NOT_CHECKED = "it could not be checked just now; try again"
        const val TOO_SLOW = "checking it took too long; try again, with fewer apps open"
        const val PROBE_KEY = "probe"
        const val VERIFY_KEY = "verify"
        const val VERIFIED = "ok"
        const val VERIFIED_CPU = "ok-cpu"
        val DIED = Any()
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val PROBE_TIMEOUT_MS = 30_000L

        // A trial load maps the weights and packs them for the CPU: 1.6 s for a 759 MB export
        // on the Poco, so this is a ceiling for a large model on a slow phone, not a budget.
        const val VERIFY_TIMEOUT_MS = 300_000L
        const val EXIT_POLLS = 20
        const val EXIT_POLL_MS = 100L
        const val EXIT_RECORDS = 5
        const val NANOS_PER_MS = 1_000_000L

        /** SIGILL, SIGABRT, SIGBUS, SIGFPE, SIGSEGV: the signals a crash raises. */
        val CRASH_SIGNALS = setOf(4, 6, 7, 8, 11)
    }
}

/**
 * A bridge whose reads of an unknown file happen in [PteProbeService]'s process. Everything
 * else goes to [inner] in the app's process, after [verify] has said the file opens.
 */
class IsolatedProbeBridge(private val checks: PteProbe, private val inner: ExecuTorchBridge) :
    ExecuTorchBridge by inner {

    override fun probe(modelPath: String): ExportFacts = checks.probe(modelPath)

    override fun verify(modelPath: String, tokenizerPath: String, multimodal: Boolean): String? =
        checks.verify(modelPath, tokenizerPath, multimodal)

    override fun stop() {
        checks.abandon()
        inner.stop()
    }
}

/**
 * Which requests a cancel reaches: every one numbered before it, running or still queued, and
 * none numbered after. A plain flag cleared by the next request lost the cancel of a request
 * queued behind another (codex QA).
 */
internal class CancelTickets {
    private val issued = AtomicLong()

    @Volatile
    private var cancelledThrough = 0L

    fun issue(): Long = issued.incrementAndGet()

    fun cancelIssued() {
        cancelledThrough = issued.get()
    }

    fun cancelled(ticket: Long): Boolean = ticket <= cancelledThrough
}
