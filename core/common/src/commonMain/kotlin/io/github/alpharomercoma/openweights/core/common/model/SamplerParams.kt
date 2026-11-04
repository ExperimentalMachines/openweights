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

package io.github.alpharomercoma.openweights.core.common.model

/**
 * Per-model generation settings. Saved alongside each model and editable by the user.
 *
 * Defaults follow llama.cpp's own defaults, which are reasonable for most instruction-tuned
 * models; individual model cards often recommend better values.
 */
data class SamplerParams(
    /** Whether the model is allowed to think before answering, where it can. */
    val thinking: Boolean = true,
    /** How much thinking, for the models whose template reads an effort level. */
    val reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topK: Int = DEFAULT_TOP_K,
    val topP: Float = DEFAULT_TOP_P,
    val minP: Float = DEFAULT_MIN_P,
    val repeatPenalty: Float = DEFAULT_REPEAT_PENALTY,
    val repeatLastN: Int = DEFAULT_REPEAT_LAST_N,
    /** `null` means "pick a new random seed for every generation". */
    val seed: Int? = null,
    /** 0 means "generate until the model stops or the context fills". */
    val maxTokens: Int = 0,
    /**
     * The most tokens a thinking block may run to before the engine closes it.
     *
     * [NO_REASONING_BUDGET] leaves the block to the model. At the cap the engine writes
     * the template's own end-of-thinking tag into the reply and the model answers from
     * what it has, which is llama-server's `reasoning_budget_tokens` done on the phone.
     * Measured on the routing matrix with Qwen3-1.7B on 2026-09-02: 128 keeps the
     * unrestricted 31 of 33 while 64 drops to 29 and 32 to 27, so a turn with tools uses 128.
     * Zero closes the block before the model writes anything, for a turn out of rounds. A
     * turn with no tools leaves it to the model. llama.cpp only; the ExecuTorch engine ignores it.
     */
    val reasoningBudget: Int = NO_REASONING_BUDGET,
    /**
     * What the model is made to say just before the engine closes the block at
     * [reasoningBudget]. Null for the engine's default. llama.cpp only.
     */
    val reasoningNote: String? = null,
) {
    init {
        require(temperature >= 0f) { "temperature must be >= 0" }
        require(topP in 0f..1f) { "topP must be within 0..1" }
        require(minP in 0f..1f) { "minP must be within 0..1" }
        require(repeatPenalty > 0f) { "repeatPenalty must be > 0" }
        require(maxTokens >= 0) { "maxTokens must be >= 0" }
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.8f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_MIN_P = 0.05f
        const val DEFAULT_REPEAT_PENALTY = 1.1f
        const val DEFAULT_REPEAT_LAST_N = 64
        const val NO_REASONING_BUDGET = -1
    }
}

/**
 * How hard the model should think, for models whose chat template offers the choice.
 *
 * Reasoning models spend tokens before answering. On a phone that time is measured in
 * tens of seconds, so being able to turn it down is a throughput control as much as a
 * quality one.
 */
enum class ReasoningEffort(val wireName: String?, val label: String) {
    /** Leave the template's own default alone. */
    DEFAULT(null, "Default"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    ;

    companion object {
        fun fromName(name: String?): ReasoningEffort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * How a model is loaded. Separate from [SamplerParams] because changing any of these
 * requires reloading the model, while sampler settings apply to the next generation.
 */
data class ModelLoadParams(
    /** KV cache size in tokens. Dominates memory use after the weights themselves. */
    val contextLength: Int = DEFAULT_CONTEXT_LENGTH,
    /**
     * Threads used to generate each token. `null` lets the engine choose.
     *
     * Generation is bandwidth-bound, so it peaks around the number of big cores and gets
     * *slower* when little cores join and everyone waits for them.
     */
    val threadCount: Int? = null,
    /**
     * Threads used to process the prompt. `null` lets the engine choose.
     *
     * Prompt processing is compute-bound and keeps scaling to every core on the phone,
     * which is why it is configured separately from [threadCount].
     */
    val batchThreadCount: Int? = null,
    /** Layers to offload to the GPU. 0 = CPU only, which is the fastest path on most phones. */
    val gpuLayers: Int = 0,
    /**
     * Whether prompt reading may be handed to a GPU while generation stays where it is.
     *
     * The one mechanism that separates the two halves of a turn. A batch is only offloaded
     * when it is large enough to repay the transfer, and generation is always a batch of
     * one, so this reaches prefill and never decode — which is exactly the split worth
     * making, since the GPU reads a prompt faster than the CPU and writes an answer slower.
     *
     * With [gpuLayers] at zero it is the whole of "prefill on the GPU, decode on the CPU".
     * With every layer on the GPU both halves are already there and this changes nothing.
     * On by default, matching llama.cpp, and only reached at all on a device that has a
     * GPU backend to offload to.
     */
    val opOffload: Boolean = true,
    /**
     * How many tokens one picture is turned into, or [AUTOMATIC_IMAGE_TOKENS] for whatever
     * the projector's own metadata asks for.
     *
     * Automatic in the app, and measured before it was left that way. libmtmd exposes a
     * floor and a ceiling on how much of an image the vision transformer is given, and this
     * sets both at once, which is what makes it a budget rather than a cap: a ceiling alone
     * leaves a small picture costing whatever the metadata's floor said, and a ceiling below
     * the model's own floor makes clip refuse the projector outright rather than clamp it.
     *
     * The reason it is not the app's speed control is that on the projector families this
     * app recommends, lowering it makes a turn *slower*. LFM2 tiles an image whose pixel
     * count is more than twice its budget, and each 512-pixel tile is a flat 256 tokens, so
     * asking for a smaller budget asks for more tiles. Measured on a Snapdragon 8 Gen 3
     * against a 461 by 1024 screenshot: automatic, 280 prompt tokens and 8.5 seconds; the
     * same picture at a budget of 128, 677 tokens and 32.8 seconds. What the app moves
     * instead is `ModelPreferences.imageTokens`, which sets the size of the picture the app sends.
     *
     * Kept, and exercised, because it is the knob for the other half of the trade and the
     * only way to reproduce that finding: see `ImageTokenBenchmark` and
     * `docs/research/image-tokens.md`.
     */
    val imageTokens: Int = AUTOMATIC_IMAGE_TOKENS,
    /**
     * How many prompt tokens the engine submits for evaluation at once.
     *
     * 512, which is what this was hard-coded to, and it is here rather than in the code so
     * it can be swept. Prefill is the compute-bound half of a turn and the one that
     * dominates a first answer, so batching more of it at a time is the obvious lever;
     * what it costs is the scratch buffer the graph allocates for intermediate
     * activations, which scales with [microBatchTokens] and is transient but real on a
     * phone that is already holding two gigabytes of weights.
     *
     * Not a user setting and not intended to become one. It is an engine constant whose
     * value should be whatever `BatchSizeBenchmark` says: present, exercised, and moved only
     * by a measurement.
     */
    val batchTokens: Int = DEFAULT_BATCH_TOKENS,
    /**
     * The physical batch: how much of [batchTokens] is computed in one graph.
     *
     * The one that actually sizes the scratch buffer, which is why it is separate. Raising
     * the logical batch without this changes how work is queued and not how it is done.
     */
    val microBatchTokens: Int = DEFAULT_BATCH_TOKENS,
) {
    init {
        require(contextLength > 0) { "contextLength must be > 0" }
        require(imageTokens >= 0) { "imageTokens must be >= 0" }
        require(batchTokens > 0) { "batchTokens must be > 0" }
        require(microBatchTokens > 0) { "microBatchTokens must be > 0" }
        // llama.cpp submits the logical batch in physical chunks, so a physical batch
        // larger than the logical one is a configuration that cannot mean anything.
        require(microBatchTokens <= batchTokens) {
            "microBatchTokens must be <= batchTokens"
        }
        require(threadCount == null || threadCount > 0) { "threadCount must be > 0" }
        require(batchThreadCount == null || batchThreadCount > 0) {
            "batchThreadCount must be > 0"
        }
        require(gpuLayers >= 0) { "gpuLayers must be >= 0" }
    }

    companion object {
        const val DEFAULT_CONTEXT_LENGTH = 4096

        /**
         * The context window the app will let a user ask for.
         *
         * The floor is where a chat stops being able to hold a question and its answer.
         * The ceiling is not a model limit, it is a memory one: the KV cache grows with
         * this number and is what runs a phone out of RAM. Both screens that offer the
         * choice read them from here, so raising the ceiling is one edit rather than
         * three that have to agree.
         */
        const val MIN_CONTEXT_LENGTH = 1024
        const val MAX_CONTEXT_LENGTH = 32_768

        /** Slider stops between the two, giving roughly 1k granularity. */
        const val CONTEXT_STEPS = 30

        /** Leaves the projector's own metadata in charge. See [imageTokens]. */
        const val AUTOMATIC_IMAGE_TOKENS = 0

        /**
         * The batch this engine has always used, kept until something measures otherwise.
         *
         * Both halves, because they were both 512 and moving one without the other is a
         * third configuration nobody has a question about.
         */
        const val DEFAULT_BATCH_TOKENS = 512
    }
}
