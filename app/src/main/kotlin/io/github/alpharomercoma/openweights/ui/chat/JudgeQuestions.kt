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

package io.github.alpharomercoma.openweights.ui.chat

import io.github.alpharomercoma.openweights.core.common.model.ToolDefinition
import io.github.alpharomercoma.openweights.core.engine.Judgement

/**
 * The closed questions the loop may put to the model instead of reading its prose, and how
 * their answers are read. See `InferenceEngine.judge` for how a question is asked.
 *
 * Every answer is one token by construction: `Session::judge` refuses an option that is
 * not, because the probability of a longer option's first piece is not the option's (LFM2.5
 * spells SEARCH as SE + ARCH). Yes and No are one token in every tokenizer measured, and so
 * are the capital letters the tool choice is labelled with.
 *
 * Every question here is off in the app. Offline on 160 public rows the model's answer to
 * [UNSURE] separated a wrong bare answer from a right one at AUROC 0.62 on LFM2.5 1.2B QAD
 * Q4_0 (0.61 on 160 held-out rows), against 0.76 (0.69) for the doubt gate's token
 * probability on the same rows, and the "do you need to look it up" wording scored 0.40
 * and said yes to all forty-eight asks that need no search
 * (`docs/research/judged-decisions.md`). The others have no public set
 * to be measured on and no phone run yet. The switches exist so the decision suite can
 * price each one on a phone before any of them decides anything for a user.
 */
internal object JudgeQuestions {
    const val YES = "Yes"
    const val NO = "No"
    val YES_NO = listOf(YES, NO)

    /**
     * Below this share of the model's probability the options were not what it wanted to
     * say, and their renormalised split is noise. On the offline rows the Yes/No wording held
     * between 0.96 and 1.00 of it on LFM2.5 (both seeds) and from 0.95 on Qwen3.5 2B; the
     * answer/search wording held 0.0008 on a test question, which is the failure this is for.
     */
    const val MIN_OPTION_MASS = 0.9f

    /** Asked before the first pass: whether the model trusts its own knowledge here. */
    const val UNSURE =
        "Before you reply: are you sure you know the correct answer to my last message " +
            "without looking anything up? Reply with one word, Yes or No."

    /**
     * The probability of No above which the gate searches first. At 0.5 the seed-7 rows sent
     * 9 of 160, 7 of them answers the model had got wrong, and the held-out seed-8 rows sent
     * 7, all wrong; none of the 48 asks that need no search on either. Precise and nearly
     * blind on its own, but what it sends is mostly what the doubt gate misses: 6 of 7 and
     * 6 of 6 of its rows were wrong answers the doubt gate let stand. So it is measured as
     * an addition to the doubt gate (the decision suite's `gate-*` arms), not a replacement.
     *
     * It is LFM2.5's cutoff and no other model's. On Qwen3.5 2B the same wording ranks
     * about as well as the doubt gate (AUROC 0.68 against 0.70), but 0.5 sends 124 of the
     * 160 rows and 24 of the 48 asks that need no search; 0.7 sends 23, 21 of them wrong,
     * and 3 of the 48. A switch that turned this on for every model would be wrong for one.
     */
    const val GATE_PROBABILITY = 0.5f

    /** Asked after a search's results are in: whether they carry the answer. */
    const val SUFFICIENT =
        "Do the search results above state the answer to my question, clearly enough to " +
            "answer from them alone? Reply with one word, Yes or No."

    /**
     * The probability of No above which the top result's page is read.
     *
     * Set on the phone, not offline. Offline, with the results in a user turn, LFM2.5 1.2B QAD
     * Q4_0 gave a median P(No) of 0.016 over 1,558 recorded searched rows and ranked well
     * (AUROC 0.78 against TypeSafe's reading of whether the results state the answer), and
     * 0.05 was chosen there. On the Poco, with the results in the tool turn the app uses,
     * the same model's median was 0.000 and its 90th percentile 0.003: at 0.05 nothing was
     * ever read. The phone readings still rank (AUROC 0.74 for a wrong reply, 0.70 for the
     * answer being absent from the results, 84 searched rows), and above 0.0005 they pick 30%
     * of searches, 23 of whose 25 replies were wrong. That line was fitted on those rows, so
     * the suite's rerun is a check of the mechanism, not an unbiased measure of the gain.
     * That rerun (160 rows, Poco): 66 correct against 60, paired 6 to 0, 36 pages read, 15
     * more seconds on the rows that read one. Off until a held-out seed repeats it.
     */
    const val LACKING_PROBABILITY = 0.0005f

    /** Asked when a short query leans on a pronoun and an earlier question exists. */
    const val FOLLOW_UP =
        "Before you reply: does my last message only make sense together with something I " +
            "asked earlier in this conversation? Reply with one word, Yes or No."

    /** Asked after a check that gave no verdict line, with both findings in front of it. */
    fun changed(previous: String, current: String): String =
        "The previous check found: \"$previous\". This check found: \"$current\". Is there a " +
            "difference between them that the user would care about? Reply with one word, " +
            "Yes or No."

    /**
     * Asked after a reply that denied a capability: which offered tool would do what was
     * asked, or none. Each candidate is described by its own definition, because a name
     * alone does not say whether the tool can do the job (Codex, reviewing the design).
     *
     * @return the question and its options, the last of which means none.
     */
    fun fitting(candidates: List<ToolDefinition>): Pair<String, List<String>> {
        val labels = LETTERS.take(candidates.size + 1).map(Char::toString)
        val question = buildString {
            append("You said you could not do that. Which of these would let you do what I ")
            append("asked? ")
            candidates.forEachIndexed { index, tool ->
                append(
                    "${labels[index]}: ${tool.name}, ${tool.description.lineSequence().first()} ",
                )
            }
            append("${labels.last()}: none of them, it is something to write yourself. ")
            append("Reply with one letter.")
        }
        return question to labels
    }

    /** The tool a [fitting] answer chose, null for none, or no reading at all. */
    fun chosenTool(judgement: Judgement, candidates: List<ToolDefinition>): Choice {
        if (judgement.optionMass < MIN_OPTION_MASS) return Choice.Unread
        val index = judgement.options.indexOf(judgement.choice)
        return if (index in candidates.indices) Choice.Tool(candidates[index].name) else Choice.None
    }

    /** What a tool choice came to. */
    sealed interface Choice {
        data class Tool(val name: String) : Choice
        data object None : Choice
        data object Unread : Choice
    }

    /** Whether a Yes/No answer can be read at all, and if so the probability of [option]. */
    fun Judgement.readable(option: String): Float? =
        probabilityOf(option).takeIf { optionMass >= MIN_OPTION_MASS }

    private const val LETTERS = "ABCDEFGH"
}
