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

package io.github.alpharomercoma.openweights.ui.models

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.alpharomercoma.openweights.R
import io.github.alpharomercoma.openweights.core.common.model.PromptTemplates
import io.github.alpharomercoma.openweights.core.engine.InferenceEngine
import io.github.alpharomercoma.openweights.core.engine.NpuInstall
import io.github.alpharomercoma.openweights.core.hub.HubFile
import io.github.alpharomercoma.openweights.core.hub.NpuBundle
import io.github.alpharomercoma.openweights.core.hub.Publishers
import io.github.alpharomercoma.openweights.download.ModelDownloadWorker
import io.github.alpharomercoma.openweights.model.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** A model file on disk. */
data class LocalModel(
    val file: File,
    /** The projector paired with this model, when one has been downloaded. */
    val projector: File? = null,
    /**
     * Who published it, when the app was the one that fetched it.
     *
     * Null for a file put here by hand, or downloaded by a build that did not record it.
     * Those group under a heading of their own rather than being guessed at from the
     * filename: "LFM2.5-2.6B" does not say LiquidAI to anybody who does not already know,
     * and a wrong attribution is worse than none.
     */
    val publisher: String? = null,
    /** The window a compiled model was exported with; null for a GGUF or until read. */
    val contextWindow: Int? = null,
    /** Whether a compiled model carries a vision encoder; null until the file has been read. */
    val readsPictures: Boolean? = null,
) {
    val name: String get() = file.nameWithoutExtension

    /** What this model occupies in total, projector included. */
    val sizeBytes: Long get() = file.length() + (projector?.length() ?: 0)

    /**
     * True when this model can read attachments on this device.
     *
     * A GGUF reads pictures through a projector downloaded beside it; a compiled export
     * carries its encoder inside the one file, so its name is the only sign before load.
     */
    val isMultimodal: Boolean
        get() = projector != null || (isCompiled && feedable && (readsPictures ?: namedLikeVlm))

    /** Whether this app knows how to hand this family's export a picture at all. */
    private val feedable: Boolean
        get() = PromptTemplates.forModel(file.name)?.vision != null

    /**
     * Matched with every separator removed, the way the prompt templates match families:
     * Software Mansion's own names are `lfm_2_5_vl_1_6b...`, which `-VL` never finds.
     */
    private val namedLikeVlm: Boolean
        get() {
            val name = file.name.lowercase().filter { it.isLetterOrDigit() }
            return VLM_TOKENS.any { it in name }
        }

    /**
     * True for weights compiled ahead of time, which only the ExecuTorch engine can run.
     *
     * The extension is the whole truth here: the app names every compiled download
     * `<name>.pte`, and which engine loads a file is decided the same way. Shown beside
     * the size wherever a model can be chosen, because two files of the same family and
     * size behave differently — different speed, different context window — and the
     * runtime is the only visible thing that says which one this is.
     */
    val isCompiled: Boolean get() = file.extension.equals("pte", ignoreCase = true)

    /**
     * True when the filename strongly suggests a vision-language model but no projector
     * has been downloaded.
     *
     * Heuristic only — based on common VLM naming conventions. Displayed as a warning chip
     * in the model list and picker so users know why the attachment button stays hidden.
     */
    val looksLikeVlm: Boolean
        get() = !isCompiled && projector == null && namedLikeVlm

    private companion object {
        /** Substrings common to vision-language model filenames across every major family. */
        /** The same families with separators removed, for names spelled `lfm_2_5_vl`. */
        val VLM_TOKENS =
            listOf("vl", "vision", "llava", "pixtral", "internvl", "qwenvl", "gemma3n", "phi4mm")
    }
}

/** A download in flight, keyed by the file it is fetching. */
data class ActiveDownload(
    val repoId: String,
    val path: String,
    /** The filename being written; downloads are identified by destination, not source. */
    val key: String,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val isVerifying: Boolean = false,
    val error: String? = null,
    /**
     * How many times this download has already failed and been put back on the queue.
     *
     * Zero for the ordinary case. Above zero the work is not running at all: it is
     * sitting in WorkManager's backoff, which starts at thirty seconds and doubles. That
     * state used to be indistinguishable from a download in progress — observed live as
     * **"Downloading 0%" on a job that had failed four times and moved no bytes** — so it
     * is carried here and said out loud rather than left looking like progress.
     */
    val attemptsFailed: Int = 0,
    /** The failure that caused the current backoff, available before work is terminal. */
    val lastError: String? = null,
    /** When WorkManager says the next retry will run, or null while it is running. */
    val nextAttemptAtMillis: Long? = null,
    val maxRetries: Int = ModelDownloadWorker.MAX_RETRIES,
) {
    val fraction: Float get() = if (bytesTotal > 0) bytesDone.toFloat() / bytesTotal else 0f

    /** True when nothing is being transferred and the next attempt is still pending. */
    val isRetrying: Boolean get() = attemptsFailed > 0 &&
        nextAttemptAtMillis != null &&
        error == null
    val remainingRetries: Int get() = (maxRetries - attemptsFailed).coerceAtLeast(0)
}

data class ModelsUiState(
    val models: List<LocalModel> = emptyList(),
    /**
     * Whether the directory has actually been read. "No models" is a real answer and it
     * is byte-identical to the state this screen is seeded with, so without this flag a
     * pipeline that never spoke was indistinguishable from an empty phone — on screen
     * and, worse, in the test meant to catch exactly that.
     */
    val listed: Boolean = false,
    val downloads: List<ActiveDownload> = emptyList(),
    val storageUsedBytes: Long = 0,
    /**
     * Publisher name to logo, filled in as the lookups come back.
     *
     * Best effort and often empty. The lookup needs the network and this app is used
     * offline, so a heading with no logo beside it is the normal case rather than a
     * failure, and nothing here waits for one.
     */
    val avatars: Map<String, String> = emptyMap(),
    /**
     * One line about something the screen refused to do, or null.
     *
     * Deleting the model that is currently loaded is the case this exists for. Refusing was
     * right and refusing in silence was not: the row stayed exactly where it was, with no
     * dialog, no error and nothing to read, which reads as a broken button rather than as a
     * rule. Cleared by the next refresh, so it does not outlive the tap that caused it.
     */
    val notice: String? = null,
) {
    /** The installed models under the name of whoever published them. */
    val grouped: List<PublisherGroup> get() = models.byPublisher()
}

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val modelStore: ModelStore,
    private val publishers: Publishers,
    private val engine: InferenceEngine,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val local = MutableStateFlow(ModelsUiState())

    /**
     * Downloads the user has waved away, which WorkManager has no concept of.
     *
     * A failed download stays in its database until something replaces it, so without this
     * a failure would sit on the screen with no way to clear it but a retry.
     */
    private val dismissed = MutableStateFlow(emptySet<String>())

    /** Finished work already folded into the model list, so it is folded in once. */
    private val absorbed = mutableSetOf<UUID>()

    /**
     * The download queue, made to say "nothing yet" before it says anything else.
     *
     * On a phone that has never downloaded a model this query emits nothing at all, rather
     * than an empty list, and a combine stays silent until every source has spoken once. The
     * models already on disk therefore never reached the screen: a fresh install showed "no
     * models yet" with gigabytes sitting in its own directory, and the list only appeared
     * after the first download created a row to report. Seeding an empty list costs nothing
     * when there is work, because the real value replaces it immediately.
     */
    private val downloadWork = workManager
        .getWorkInfosByTagFlow(ModelDownloadWorker.TAG_DOWNLOAD)
        .onStart { emit(emptyList()) }

    val uiState: StateFlow<ModelsUiState> = combine(
        local,
        downloadWork,
        dismissed,
    ) { state, work, hidden ->
        state.copy(downloads = work.toDownloads(hidden))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_GRACE_MILLIS),
        initialValue = ModelsUiState(),
    )

    init {
        // Off the main thread: a phone that still has a pre-revert Sana/SD bundle deletes up
        // to 1.6 GB recursively here, and Hilt constructs this view model synchronously
        // during composition, so doing it inline would be a near-guaranteed ANR the first
        // time this build opens the Models screen on such a phone.
        viewModelScope.launch(Dispatchers.IO) {
            modelStore.deleteLegacyGenerationBundles()
            refresh()
        }
        // Last run's finished rows, which are of no interest now: their files are either on
        // disk, where refresh finds them, or they are not, and a failure the user cannot
        // act on any more is just clutter.
        workManager.pruneWork()

        // A second collector rather than a side effect inside the combine above, because
        // this has to keep running when nothing is looking at the screen. A download that
        // completes while the user is in a chat still has to appear in the model list when
        // they come back.
        viewModelScope.launch {
            downloadWork.collect { work ->
                val finished = work.any {
                    it.state == WorkInfo.State.SUCCEEDED && absorbed.add(it.id)
                }
                if (finished) refresh()
            }
        }
    }

    /**
     * Fills in the exported window of each compiled model the store has not read yet.
     *
     * Off the main thread, because reading it maps the file and runs one tiny method of
     * the program, and the list is shown before the answers arrive rather than after.
     * The store remembers each answer for the life of the process, so this is one read
     * per file per launch.
     */
    private fun readWindows(models: List<LocalModel>) {
        val unread = models.filter { it.isCompiled && it.readsPictures == null }
        if (unread.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val read = unread.associate { it.file to modelStore.exportFacts(it.file) }
            local.update { state ->
                state.copy(
                    models = state.models.map { model ->
                        read[model.file]?.let {
                            model.copy(
                                contextWindow = it.contextLength,
                                readsPictures = it.hasVision,
                            )
                        } ?: model
                    },
                )
            }
        }
    }

    fun refresh() {
        val models = modelStore.availableModels().map { file ->
            LocalModel(
                file,
                modelStore.projectorFor(file),
                modelStore.publisherOf(file.name),
                contextWindow = modelStore.knownFacts(file)?.contextLength,
                readsPictures = modelStore.knownFacts(file)?.hasVision,
            )
        }
        readWindows(models)
        local.update {
            it.copy(
                models = models,
                listed = true,
                storageUsedBytes = models.sumOf { model -> model.sizeBytes },
                notice = null,
            )
        }
        resolveAvatars(models)
    }

    /**
     * Fetches a logo per publisher, once per process, without holding anything up.
     *
     * The same directory Discover uses, so a session that has browsed already has these and
     * pays nothing. A failure is silent on purpose: an offline phone showing headings with
     * no logos is the app working, and a message about it would be noise on the one screen
     * a person opens when they are trying to free up space.
     */
    private fun resolveAvatars(models: List<LocalModel>) {
        models.mapNotNull { it.publisher }
            .filter { it !in local.value.avatars }
            .distinct()
            .forEach { owner ->
                viewModelScope.launch {
                    val url = runCatching { publishers.lookUp(owner) }.getOrNull()?.avatarUrl
                        ?: return@launch
                    local.update { it.copy(avatars = it.avatars + (owner to url)) }
                }
            }
    }

    /**
     * Starts a download, or does nothing if that destination is already being fetched.
     *
     * Keyed by the file it writes rather than the remote path: two repositories can offer
     * the same filename, and letting both run would have them overwrite each other's bytes
     * in the same partial file. That key is also the unique work name, so the "already
     * running" check is WorkManager's rather than ours, and it holds across a restart.
     */
    fun download(repoId: String, path: String, sizeBytes: Long, sha256: String?) {
        val file = HubFile(path, sizeBytes, sha256)
        // Blank means the repository offered a name that is not one, and `HubFile.fileName`
        // refused it rather than let it decide a path: `File(directory, "")` is the
        // directory, and `File(directory, "..")` is its parent. Nothing is started.
        //
        // No error is surfaced, and that is a considered choice rather than an oversight.
        // Errors here belong to a download row, and there is no row until a download
        // starts; inventing a second error channel for a case that needs a malformed
        // repository listing to reach would be more machinery than the case is worth. It is
        // logged, which is what the next person debugging a missing download will look at.
        val name = file.fileName
        if (name.isBlank()) {
            Log.w("OpenWeights", "refused to download $repoId/$path: unusable file name")
            return
        }
        start(repoId, file, File(modelStore.directory, name))
    }

    /**
     * Fetches a model's projector, saved under a name derived from the model file.
     *
     * Renamed on the way in because upstream names only loosely relate to the model
     * different quantization, different capitalisation, sometimes a bare `mmproj-F16`
     * and a guess at load time means a wrong encoder loaded without complaint.
     */
    fun downloadProjector(repoId: String, projector: HubFile, modelFileName: String) {
        start(repoId, projector, modelStore.projectorDestination(modelFileName))
    }

    /**
     * Installs an ExecuTorch model: its compiled weights, and the tokenizer they need.
     *
     * Two downloads for one install, and both are renamed on the way in. The weights take
     * the repository's name because every `.pte` repository calls the file `model.pte`, and
     * the name is the only thing that says which family it is — a `.pte` carries no
     * metadata to read, so `PromptTemplates` has nothing else to go on.
     *
     * The tokenizer goes first. Neither file is usable alone and the two land
     * independently, so the small one is started first and `ModelStore` shows the model
     * only once both are present. The row therefore appears when it starts working rather
     * than when it starts downloading, and a tokenizer that fails looks like a model that
     * never arrived rather than one that is quietly broken.
     */
    fun downloadCompiled(repoId: String, weights: HubFile, tokenizer: HubFile) {
        val destination = modelStore.compiledDestination(repoId, weights.path)
        val group = destination.name
        start(repoId, tokenizer, modelStore.tokenizerFor(destination.name), group = group)
        start(repoId, weights, destination, group = group)
    }

    /**
     * Installs a MediaTek NPU build: the CPU build and its tokenizer as an ordinary compiled
     * install, and the NPU half (chunks and embedding) in the model's `.npu` folder.
     *
     * The list of NPU parts is written first, so the engine knows what a complete half is
     * and reads with the CPU until every part has arrived at its full size. The parts are
     * keyed by folder and name: every window's embedding table has the same file name, and
     * keyed by name alone a second window's would never start.
     */
    fun downloadNpu(repoId: String, bundle: NpuBundle, tokenizer: HubFile) {
        val destination = modelStore.compiledDestination(repoId, bundle.cpu.path)
        val dir = NpuInstall.write(
            destination,
            chunks = bundle.chunks.map { NpuInstall.Part(it.fileName, it.sizeBytes) },
            embedding = NpuInstall.Part(bundle.embedding.fileName, bundle.embedding.sizeBytes),
            runnerJson = bundle.runnerJson,
        )
        // One install, so one row in the list: every part is grouped under the model's name.
        val group = destination.name
        start(repoId, tokenizer, modelStore.tokenizerFor(destination.name), group = group)
        start(repoId, bundle.cpu, destination, group = group)
        bundle.npuFiles.forEach { part ->
            start(
                repoId,
                part,
                File(dir, part.fileName),
                key = "${dir.name}/${part.fileName}",
                group = group,
            )
        }
    }

    private fun start(
        repoId: String,
        file: HubFile,
        destination: File,
        key: String = destination.name,
        group: String? = null,
    ) {
        // The one moment the app knows where a file came from. Once it has landed it is a
        // .gguf in a folder and the name says nothing about who published it.
        modelStore.rememberPublisher(key, repoId.substringBefore('/', missingDelimiterValue = ""))

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_REPO_ID to repoId,
                    ModelDownloadWorker.KEY_PATH to file.path,
                    ModelDownloadWorker.KEY_DESTINATION to destination.absolutePath,
                    ModelDownloadWorker.KEY_SIZE_BYTES to file.sizeBytes,
                    ModelDownloadWorker.KEY_SHA256 to file.sha256,
                ),
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(ModelDownloadWorker.TAG_DOWNLOAD)
            .addTag(ModelDownloadWorker.TAG_KEY + key)
            .addTag(ModelDownloadWorker.TAG_REPO + repoId)
            .addTag(ModelDownloadWorker.TAG_PATH + file.path)
            .addTag(ModelDownloadWorker.TAG_SIZE + file.sizeBytes)
            .addTag(ModelDownloadWorker.TAG_SHA256 + file.sha256.orEmpty())
            .apply { group?.let { addTag(ModelDownloadWorker.TAG_GROUP + it) } }
            .build()

        // KEEP, so tapping download twice does not start a second writer on the same file.
        // A run that has already finished is not kept, which is what makes a failed download
        // retryable from the same button.
        workManager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request)
        dismissed.update { it - key }
    }

    fun cancel(key: String) {
        workManager.cancelUniqueWork(key)
        // A row that stands for an install cancels every part of it: what is left of a
        // cancelled install is gigabytes nothing can open.
        workManager.cancelAllWorkByTag(ModelDownloadWorker.TAG_GROUP + key)
        // Also covers dismissing a failure, which has nothing left to cancel.
        dismissed.update { it + key }
    }

    /**
     * Replaces a failed or backoff job with a fresh attempt using its persisted request; for a
     * row that stands for an install, every part of it that has not finished.
     */
    fun retry(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parts = runCatching {
                workManager.getWorkInfosByTag(ModelDownloadWorker.TAG_GROUP + key).get()
                    .filter { it.state == WorkInfo.State.FAILED || it.runAttemptCount > 0 }
                    .mapNotNull { it.tagValue(ModelDownloadWorker.TAG_KEY) }
            }.getOrDefault(emptyList())
            dismissed.update { it - key }
            (parts + key).distinct().forEach { retryOne(it) }
        }
    }

    private fun retryOne(key: String) {
        val previous = runCatching {
            workManager.getWorkInfosForUniqueWork(key).get()
                .firstOrNull {
                    it.state != WorkInfo.State.CANCELLED &&
                        it.state != WorkInfo.State.SUCCEEDED
                }
        }.getOrNull() ?: return

        val repoId = previous.tagValue(ModelDownloadWorker.TAG_REPO).orEmpty()
        val path = previous.tagValue(ModelDownloadWorker.TAG_PATH).orEmpty()
        val name = previous.tagValue(ModelDownloadWorker.TAG_KEY).orEmpty()
        if (repoId.isBlank() || path.isBlank() || name.isBlank()) return

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_REPO_ID to repoId,
                    ModelDownloadWorker.KEY_PATH to path,
                    ModelDownloadWorker.KEY_DESTINATION to
                        File(modelStore.directory, name).absolutePath,
                    ModelDownloadWorker.KEY_SIZE_BYTES to
                        (previous.tagValue(ModelDownloadWorker.TAG_SIZE)?.toLongOrNull() ?: 0L),
                    ModelDownloadWorker.KEY_SHA256 to
                        previous.tagValue(ModelDownloadWorker.TAG_SHA256)
                            ?.takeIf { it.isNotBlank() },
                ),
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .apply { previous.tags.forEach { addTag(it) } }
            .build()

        dismissed.update { it - key }
        workManager.enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, request)
    }

    fun delete(model: LocalModel) {
        // Off the main thread, as the sweep in init already is and for the reason given
        // there: unlinking gigabytes and re-listing the directory from a button's onClick
        // held the frame for as long as the filesystem took.
        viewModelScope.launch(Dispatchers.IO) { deleteNow(model) }
    }

    private fun deleteNow(model: LocalModel) {
        // A mapped model can survive an unlink on some filesystems but not all engines
        // tolerate the backing file disappearing while a generation is in flight, so the
        // loaded model is not deleted out from under the engine. Said out loud, with the
        // way out named: the picker has an unload action and nothing else on screen would
        // have told the user that.
        if (engine.loadedModel?.modelPath == model.file.absolutePath) {
            local.update {
                it.copy(
                    notice = context.getString(R.string.model_delete_loaded, model.name),
                )
            }
            return
        }
        // Taken before anything is removed: an install started while this one was being
        // deleted wrote its part list after this moment, and the late sweep spares it (codex QA).
        val deletedAt = System.currentTimeMillis()
        if (!removeCompanions(model)) {
            local.update {
                it.copy(notice = context.getString(R.string.model_delete_failed, model.name))
            }
            return
        }
        model.projector?.sourceSidecar?.delete()
        if (!model.file.delete()) {
            local.update {
                it.copy(notice = context.getString(R.string.model_delete_failed, model.name))
            }
            return
        }
        modelStore.forgetPublisher(model.file.name)
        model.file.sourceSidecar.delete()
        // WorkManager reports a cancel before the worker's thread has let go, so a part that
        // was being verified as the cancel came can still land: swept once more, unless the
        // model has been asked for again since. A new install writes its part list before any
        // part, and a worker never does, so a list newer than this delete is a new install
        // whose CPU build has not landed yet; the model file alone was not proof (codex QA).
        viewModelScope.launch(Dispatchers.IO) {
            delay(STRAGGLER_MILLIS)
            if (!model.file.exists() && NpuInstall.startedAt(model.file) <= deletedAt) {
                NpuInstall.directoryFor(model.file).deleteRecursively()
            }
        }
        // A compiled model's tokenizer is useless without it and invisible in the list,
        // so leaving it behind would quietly keep megabytes with no way to reclaim them.
        modelStore.tokenizerFor(model.file.name).delete()
        // The projector is useless without its model and is often the larger of the two,
        // so leaving it behind would quietly keep hundreds of megabytes.
        refresh()
    }

    /**
     * Everything that goes with [model] before the model itself, so a part that cannot be
     * removed leaves the row there to try again; false when one could not be.
     */
    private fun removeCompanions(model: LocalModel): Boolean {
        // The rest of its install first: the model row appears once the CPU build and its
        // tokenizer land, while NPU parts can still be downloading, and a worker left running
        // would create the folder again and fill it with gigabytes no row can delete (codex QA).
        if (!stopInstall(model.file.name)) return false
        // A MediaTek NPU half is gigabytes that nothing else can use (codex QA).
        val npu = NpuInstall.directoryFor(model.file)
        if (npu.exists() && !npu.deleteRecursively()) return false
        // The projector is often the larger of the two.
        return model.projector?.let { !it.exists() || it.delete() } ?: true
    }

    /**
     * Cancels every download of the install named [group] and waits for its workers to stop;
     * false when one still runs, or WorkManager would not say, so nothing is deleted under it.
     */
    private fun stopInstall(group: String): Boolean {
        val tag = ModelDownloadWorker.TAG_GROUP + group
        if (runCatching { workManager.cancelAllWorkByTag(tag).result.get() }.isFailure) return false
        repeat(STOP_POLLS) {
            val running = runCatching { workManager.getWorkInfosByTag(tag).get() }.getOrNull()
                ?.any { it.state == WorkInfo.State.RUNNING }
                ?: return false
            if (!running) return true
            Thread.sleep(STOP_POLL_MILLIS)
        }
        return false
    }

    /**
     * Turns WorkManager's view of the queue into rows.
     *
     * Everything the row needs before the worker has run once comes from tags, because
     * input data is not readable from a [WorkInfo] and the row has to say which model it is
     * fetching while the work is still waiting for a network.
     */
    private fun List<WorkInfo>.toDownloads(hidden: Set<String>): List<ActiveDownload> {
        val parts = mapNotNull { it.toPart() }
            // Replacing a finished run leaves its record behind for a moment, so the same file
            // can appear twice. The one still going is the one worth showing.
            .groupBy { it.row.key }
            .map { (_, runs) -> runs.minBy { it.state.showOrder() } }
        val singles = parts.filter { it.group == null }
            .filter { it.state != WorkInfo.State.SUCCEEDED && it.state != WorkInfo.State.CANCELLED }
            .map { it.row }
            .filter { it.key !in hidden }
        val installs = parts.filter { it.group != null }
            .groupBy { it.group!! }
            .filter { (group, _) -> group !in hidden }
            .mapNotNull { (group, members) -> merged(group, members) }
        return installs + singles
    }

    /**
     * One row for an install of several files. Gone once every part has landed, because the
     * model is then in the list above, or once any part is cancelled, because the install was.
     */
    private fun merged(group: String, members: List<Part>): ActiveDownload? {
        if (members.any { it.state == WorkInfo.State.CANCELLED }) return null
        if (members.all { it.state == WorkInfo.State.SUCCEEDED }) return null
        val lead =
            members.firstOrNull { it.row.key == group } ?: members.maxBy { it.row.bytesTotal }
        val pending = members.filter { it.state != WorkInfo.State.SUCCEEDED }.map { it.row }
        return lead.row.copy(
            key = group,
            bytesDone = members.sumOf {
                if (it.state ==
                    WorkInfo.State.SUCCEEDED
                ) {
                    it.row.bytesTotal
                } else {
                    it.row.bytesDone
                }
            },
            bytesTotal = members.sumOf { it.row.bytesTotal },
            isVerifying = pending.all { it.isVerifying },
            error = pending.firstNotNullOfOrNull { it.error },
            attemptsFailed = pending.maxOf { it.attemptsFailed },
            lastError = pending.firstNotNullOfOrNull { it.lastError },
            nextAttemptAtMillis = pending.mapNotNull { it.nextAttemptAtMillis }.minOrNull(),
        )
    }

    /** One download's row, the install it belongs to, and where its work stands. */
    private data class Part(val row: ActiveDownload, val group: String?, val state: WorkInfo.State)

    private fun WorkInfo.toPart(): Part? {
        val key = tagValue(ModelDownloadWorker.TAG_KEY) ?: return null
        val declared = tagValue(ModelDownloadWorker.TAG_SIZE)?.toLongOrNull() ?: 0L
        val base = ActiveDownload(
            repoId = tagValue(ModelDownloadWorker.TAG_REPO).orEmpty(),
            path = tagValue(ModelDownloadWorker.TAG_PATH).orEmpty(),
            key = key,
            bytesTotal = declared,
        )
        val row = when (state) {
            WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> base

            WorkInfo.State.FAILED -> base.copy(
                error = outputData.getString(ModelDownloadWorker.KEY_ERROR)
                    ?: "The download could not be completed.",
                attemptsFailed = runAttemptCount,
                lastError = outputData.getString(ModelDownloadWorker.KEY_ERROR),
            )

            else -> base.copy(
                attemptsFailed = runAttemptCount,
                lastError = progress.getString(ModelDownloadWorker.KEY_ERROR),
                nextAttemptAtMillis = nextScheduleTimeMillis.takeIf {
                    state == WorkInfo.State.ENQUEUED &&
                        runAttemptCount > 0 &&
                        it > 0L &&
                        it != Long.MAX_VALUE
                },
                bytesDone = progress.getLong(ModelDownloadWorker.KEY_BYTES_DONE, 0L),
                // The declared size until the worker reports its own, so a queued download
                // still shows what it is about to cost.
                bytesTotal = progress.getLong(ModelDownloadWorker.KEY_BYTES_TOTAL, declared),
                isVerifying = progress.getBoolean(ModelDownloadWorker.KEY_VERIFYING, false),
            )
        }
        return Part(row, tagValue(ModelDownloadWorker.TAG_GROUP), state)
    }

    /** Which of two runs of the same file to show: the live one, then a landed one. */
    private fun WorkInfo.State.showOrder(): Int = when (this) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> 0
        WorkInfo.State.SUCCEEDED -> 1
        else -> if (this == WorkInfo.State.FAILED) 2 else Int.MAX_VALUE
    }

    private fun WorkInfo.tagValue(prefix: String): String? =
        tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

    private companion object {
        /** Long enough that a rotation does not tear down the query and rebuild it. */
        const val SUBSCRIBE_GRACE_MILLIS = 5_000L

        /** A dropped connection is usually back well inside a minute. */
        const val BACKOFF_SECONDS = 30L

        /** A cancelled download stops at its next buffer; five seconds is many of those. */
        const val STOP_POLLS = 50
        const val STOP_POLL_MILLIS = 100L

        /** Long enough for a part being verified at the cancel to be renamed and swept. */
        const val STRAGGLER_MILLIS = 30_000L
    }
}

/** Downloader provenance belongs to the model and must not survive its deletion. */
private val File.sourceSidecar: File get() = File(parentFile, name + ".source")
