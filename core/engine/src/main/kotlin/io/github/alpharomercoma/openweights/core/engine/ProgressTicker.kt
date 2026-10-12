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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How often the live readout and the memory peak are read: four times a second. */
internal const val PROGRESS_TICK_MS = 250L

/**
 * One engine pass's measurement: its [MemoryMeter], and a ticker beside it that samples the
 * memory peak and sends the pass's progress, on a thread of its own because both engines
 * block their inference thread for the whole of a generation.
 *
 * [close] ends it, synchronously. Each tick reads and sends under the same lock [close]
 * takes, so once [close] returns no reading of this pass and no event from its ticker can
 * follow: not a progress event after the pass's `Completed`, and on the llama.cpp side not
 * a read of a session the next operation is already using or has freed. Ending it without
 * suspending matters there, because a suspension on the single inference thread is a gap a
 * queued load or another generation would run in before this pass had finished.
 *
 * Tokens stay lossless on the same channel; this adds at most four small events a second,
 * and none while nothing moves.
 */
internal class PassMeter(
    private val scope: ProducerScope<GenerationEvent>,
    cold: Boolean,
    private val read: () -> GenerationEvent.Progress?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val memory: MemoryMeter = MemoryMeter(cold),
    /** False only for the device test that measures what the ticker costs. */
    ticks: Boolean = true,
) {
    private val lock = Any()
    private var open = true
    private var last: GenerationEvent.Progress? = null
    private var result: TurnMemory? = null
    private val ticker: Job? = if (!ticks) {
        null
    } else {
        scope.launch(dispatcher) {
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                tick()
            }
        }
    }

    /** One reading, under the lock [close] takes; nothing once the pass has ended. */
    internal fun tick() = synchronized(lock) {
        if (!open) return@synchronized
        memory.sample()
        val now = read() ?: return@synchronized
        if (now != last) {
            scope.trySend(now)
            last = now
        }
    }

    /**
     * The weights were opened during this pass, after it began: its faults are a file read in
     * for the first time, not paging. See [MemoryMeter].
     */
    fun markCold() = memory.markCold()

    /** Ends the pass and returns its memory, the same figures however often it is called. */
    fun close(): TurnMemory = synchronized(lock) {
        result ?: run {
            open = false
            ticker?.cancel()
            memory.finish().also { result = it }
        }
    }
}

/**
 * The pass running now for an engine whose counters live in Kotlin, one at a time under that
 * engine's own turn lock, so code deep in a pass can end it or mark it cold without being
 * handed it.
 */
internal class PassSlot {
    @Volatile private var current: PassMeter? = null

    /**
     * Whether the module was opened and has not run since, so its next run maps the file in.
     * Its own flag rather than the engine's "has run", which a context reset also clears
     * without reopening anything.
     */
    @Volatile private var fresh = false

    /** The module was just opened, by a load or a reopen; a pass running now is cold. */
    fun opened() {
        fresh = true
        current?.markCold()
    }

    /** The module ran, so the file is in and the next pass is not cold for it. */
    fun ran() {
        fresh = false
    }

    /** Runs [block] as one metered pass, closed however it ends. */
    suspend fun run(scope: ProducerScope<GenerationEvent>, block: suspend (LiveCount) -> Unit) {
        val live = LiveCount()
        val pass = PassMeter(scope, cold = fresh, read = live::progress)
        current = pass
        try {
            block(live)
        } finally {
            pass.close()
            current = null
        }
    }

    /** Ends the running pass, if there is one, and returns its memory. See [PassMeter.close]. */
    fun close(): TurnMemory? = current?.close()
}

/**
 * Tokens counted as the runtime hands them over, before the reply's text is held back for
 * markers, for the live readout. This runtime reads a prompt in one call it cannot look
 * inside, so there is no prompt progress and no live prompt rate: only writing is counted.
 *
 * One lock over the three fields, so a reading never pairs one token's count with the next
 * token's time, and a monotonic clock, so a wall-clock correction cannot bend the rate.
 */
internal class LiveCount(private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }) {
    private var generated = 0
    private var firstAt = 0L
    private var lastAt = 0L

    @Synchronized
    fun token() {
        val at = clock()
        if (generated == 0) firstAt = at
        lastAt = at
        generated++
    }

    @Synchronized
    fun progress(): GenerationEvent.Progress = GenerationEvent.Progress(
        reading = generated == 0,
        promptRead = null,
        promptTotal = null,
        prefillMs = null,
        generated = generated,
        decodeMs = (lastAt - firstAt).takeIf { generated > 1 },
    )
}

/**
 * The native snapshot (see `LlamaBridge.nativeProgress`) as an event, or null while idle.
 * A prompt whose length the session could not count (a turn with media) has no total and no
 * rate, rather than a rate over the part it could see.
 */
internal fun progressFrom(snapshot: LongArray): GenerationEvent.Progress? {
    if (snapshot.size < PROGRESS_FIELDS || snapshot[PHASE] == 0L) return null
    val total = snapshot[PROMPT_TOTAL].takeIf { it >= 0 }?.toInt()
    val read = snapshot[PROMPT_READ].toInt().takeIf { total != null }
    val generated = snapshot[GENERATED].toInt()
    return GenerationEvent.Progress(
        reading = snapshot[PHASE] == READING,
        promptRead = read,
        promptTotal = total,
        prefillMs = (snapshot[READ_AT] - snapshot[PREFILL_STARTED]).takeIf { (read ?: 0) > 0 },
        generated = generated,
        decodeMs = (snapshot[LAST_TOKEN] - snapshot[FIRST_TOKEN]).takeIf { generated > 1 },
    )
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val PROGRESS_FIELDS = 8
private const val PHASE = 0
private const val PROMPT_TOTAL = 1
private const val PROMPT_READ = 2
private const val PREFILL_STARTED = 3
private const val READ_AT = 4
private const val GENERATED = 5
private const val FIRST_TOKEN = 6
private const val LAST_TOKEN = 7
private const val READING = 1L
