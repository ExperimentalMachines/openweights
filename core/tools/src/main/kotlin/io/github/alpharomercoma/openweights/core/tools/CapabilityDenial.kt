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

package io.github.alpharomercoma.openweights.core.tools

/**
 * Identifies missing-capability claims so the caller can try one bounded recovery.
 *
 * On the phone (2026-09-22), LFM2.5-1.2B refused ordinary writing with tools present,
 * including claims with no tool noun. A clean retry of the original conversation without
 * the catalogue answered the five initial probes; replaying the denial did not. Lookup
 * denials still need the appropriate tool. Privacy claims, reasoned refusals and missing
 * source material must not be treated as false writing limitations.
 */
object CapabilityDenial {
    /**
     * Whether this reply is a claim of missing capability rather than an answer.
     *
     * Judged on the head of the reply only. Every denial observed opens with one — it is a
     * reflex prefix, not a conclusion — and a match deeper in the text is far more likely
     * to be quoted or legitimate content. A denial needs a capability claim or a narrow
     * text limitation. Ordinary refusals ("I can't help with that") and sympathy
     * ("I'm sorry for your loss") match neither.
     */
    fun denies(reply: String): Boolean {
        val head = reply.head()
        // The refusal check reads only the denial's own sentence. The rest of the head is
        // usually a suggestion ("set a reminder on your phone instead"), and "your" there
        // is the model being helpful, not a privacy boundary being claimed.
        return DENIAL.containsMatchIn(head) &&
            (CAPABILITY.containsMatchIn(head) || deniesText(reply, head)) &&
            !REFUSAL.containsMatchIn(head.substringBefore(". "))
    }

    /** An offer to write text the user already asked for, rather than a request for data. */
    fun defersRequestedText(reply: String, question: String): Boolean {
        val text = reply.trim().lowercase().plainQuotes()
        return reply.length <= TEXT_DENIAL_CHARS &&
            '\n' !in reply &&
            TEXT_REQUEST.containsMatchIn(question.trim().lowercase()) &&
            !TEXT_REQUEST_BOUNDARY.containsMatchIn(question.lowercase()) &&
            !TEXT_REFUSAL_REASON.containsMatchIn(text) &&
            !TEXT_INPUT_MISSING.containsMatchIn(text) &&
            TEXT_PERMISSION.containsMatchIn(text)
    }

    /** A short false writing limitation, without broadening ordinary refusals. */
    private fun deniesText(reply: String, head: String): Boolean {
        val sentence = head.substringBefore(". ")
        // A refusal with a reason, missing input, or a substantive answer is not this
        // failure. In particular, never turn a privacy or safety boundary into a retry.
        if (reply.length > TEXT_DENIAL_CHARS ||
            '\n' in reply ||
            TEXT_BOUNDARY.containsMatchIn(sentence)
        ) {
            return false
        }
        return TEXT_UNAVAILABLE.containsMatchIn(sentence) ||
            (TEXT_ACTION.containsMatchIn(sentence) && TEXT_OFFER.containsMatchIn(head))
    }

    /**
     * Whether this reply claims missing *knowledge* about something the question names,
     * with working search in the prompt.
     *
     * The other way a small model talks itself out of the tools it holds: "I don't have
     * enough information about Alpha Romer Coma", said as the whole answer, by a model
     * whose web_search was one call away. [denies] cannot see it — there is no capability
     * noun in the sentence — but the failure is the same reflex and earns the same push.
     * The evidence for pushing rather than accepting is Mallen et al. 2022
     * (arXiv:2212.10511): parametric memory fails precisely on long-tail entities, and
     * retrieval is the fix for exactly those, so a lament about an unrecognised name is
     * the strongest possible signal that a search was warranted.
     *
     * Kept narrow on purpose. A clarification ("I don't know what you mean") is a real
     * question to the user, not a lament, and "your ..." keeps its standing privacy
     * guard. Judged on the first sentence like every other classification here.
     */
    fun lamentsUnknown(reply: String): Boolean {
        val sentence = reply.head().substringBefore(". ")
        return DENIAL.containsMatchIn(sentence) &&
            KNOWLEDGE.containsMatchIn(sentence) &&
            !CLARIFYING.containsMatchIn(sentence) &&
            !REFUSAL.containsMatchIn(sentence)
    }

    /**
     * The tools the denial itself says are needed, best fit first, empty when the task is
     * something to write rather than to run.
     *
     * Read from the model's own words, not the user's: the denial names the capability the
     * model decided it lacked ("access to the latest information", "perform that
     * calculation"), which is exactly the routing decision it got right before talking
     * itself out of it. An address anywhere in the question outranks that, because a turn
     * holding a URL was given its errand by the user directly.
     */
    fun fitting(denial: String, question: String): List<String> {
        // The first sentence, because that is where the model names the capability it
        // refused; what follows is often a list of what it supposedly can do instead, and
        // that list poisons the match. "I can't write code for functions. My capabilities
        // are focused on searching the web ... and setting up reminders" names writing,
        // and a classifier reading the whole head sent it to the watch tool.
        val refused = denial.head().substringBefore(". ")
        return when {
            question.contains("http", ignoreCase = true) ->
                listOf(FetchUrlTool.NAME, WebSearchTool.NAME)
            COMPUTE_SHAPED.containsMatchIn(refused) -> listOf(RunScriptTool.NAME)
            SCHEDULE_SHAPED.containsMatchIn(refused) -> listOf(WatchTool.NAME)
            LOOKUP_SHAPED.containsMatchIn(refused) -> listOf(WebSearchTool.NAME)
            else -> emptyList()
        }
    }

    /** Every tool [fitting] can name, which is every tool a denial can be repaired towards. */
    val REPAIRABLE: List<String> =
        listOf(WebSearchTool.NAME, FetchUrlTool.NAME, RunScriptTool.NAME, WatchTool.NAME)

    /** The corrective line when the retry needs an available tool. */
    fun retryRequest(fitting: String): String =
        "You do have a working tool for exactly this: $fitting. Call it now, with no " +
            "apology and no explanation."

    /** Lowercased with curly apostrophes straightened, which is how this model writes. */
    private fun String.head(): String = take(HEAD_CHARS).lowercase().plainQuotes()

    private val DENIAL = Regex(
        "\\b(don't|do not|doesn't|does not|can't|cannot|unable to|not able to|no way to)\\b",
    )

    private const val TEXT_DENIAL_CHARS = 500
    private val TEXT_REQUEST = Regex(
        "^(?:please )?(?:write|draft|compose|translate|rewrite|summarize)\\b",
    )
    private val TEXT_REQUEST_BOUNDARY = Regex(
        "https?://|password|credential|private|confidential|\\b(?:send|schedule|publish)\\b",
    )
    private val TEXT_REFUSAL_REASON = Regex(
        "harmful|unsafe|illegal|copyright|safe alternative|can't help|cannot help|won't|will not",
    )
    private val TEXT_INPUT_MISSING = Regex(
        "need the|need your|without|missing|not provided|not supplied",
    )
    private val TEXT_PERMISSION = Regex(
        "(?:would you like|do you want) me to " +
            "(?:write|draft|compose|translate|rewrite|summarize|provide)\\b[^?]*\\?\\s*$",
    )
    private val TEXT_ACTION = Regex(
        "^i(?:'m sorry,? but i| am sorry,? but i| am sorry,? i|\\s+sorry,? but i)? " +
            "(?:can't|cannot|don't|do not|am unable to) " +
            "(?:write|create|generate|compose|draft|translate|rewrite|summarize)\\b",
    )
    private val TEXT_UNAVAILABLE = Regex(
        "^i (?:don't|do not) have (?:a |an |any )?" +
            "(?:story|poem|haiku|draft|translation|summary|email)\\b.*" +
            "(?:ready|to (?:write|share)|at the moment)",
    )
    private val TEXT_OFFER = Regex("however,? i can|but i can|if you'd like|let me know")
    private val TEXT_BOUNDARY = Regex(
        "because|unsafe|harmful|illegal|copyright|private|privacy|personal|confidential|" +
            "missing|without|not provided|not supplied|need more|need the|need you",
    )

    // "Webpage" is here for the denial that names no tool at all: "I can't view or
    // analyze the content of that specific webpage", said with a working fetch_url in the
    // prompt and the address in the question. Present tense only throughout: a "couldn't"
    // is a report of something genuinely tried and failed, and rewriting those would hide
    // real failures.
    // "abilit" covers both "the ability to" and "my capabilities"; both are said.
    private val CAPABILITY = Regex("tool|function|abilit|access|way to|web ?page")

    /**
     * Refusals this must never push against. "Access to your camera" is a privacy claim
     * and true; "I can't help with that" is a decision, not a capability; and a reply
     * that names the user's own things ("your files", "your location") is talking about
     * what it should not touch rather than what it cannot do. A retry that argued with
     * any of these would be the mechanism overriding a refusal it has no business
     * judging, so all of them read as not-a-denial and the reply stands as written.
     */
    private val REFUSAL = Regex("your |(can't|cannot|won't|will not) (help|assist|do that)")

    /**
     * Denials whose missing capability is a lookup. "Identify", "look up" and "information
     * about" are here because the on-device misses used exactly those words ("a tool that
     * can directly identify the most powerful character"); "search" deliberately is not,
     * because a write-shaped denial lists searching among the things it supposedly can do
     * ("my capabilities are focused on searching the web") and would flip class.
     */
    private val LOOKUP_SHAPED = Regex(
        "latest|up.?to.?date|real.?time|current |meta data|in stock|price" +
            // "biograph": the mid-conversation lament for an unrecognised person names
            // the genre, not the act of looking up — "a tool that can instantly provide
            // a biography" — and without this it fell through to the prose-only push,
            // which is a request to write the biography the model just said it lacks.
            "|identify|look up|find out|information about|biograph",
    )

    /**
     * A lament's knowledge nouns. "information about|on" overlaps [LOOKUP_SHAPED] by
     * design: once the gate opens, [fitting] classifies the same sentence and lands on
     * web_search through the overlap, so the push names the right tool with no second
     * classifier to keep in step.
     */
    private val KNOWLEDGE = Regex(
        "enough information|information (about|on)|familiar with|details (about|on)" +
            "|public information|record of|not aware of",
    )

    /** A model asking what was meant is conversing, not lamenting; never push against it. */
    private val CLARIFYING = Regex("you mean|you're asking|you are asking|clarify")

    private val COMPUTE_SHAPED = Regex("calculat|arithmetic|comput|perform that")

    private val SCHEDULE_SHAPED = Regex("remind|schedul|monitor|alert|notif")

    /**
     * Two sentences of apology, roughly. Longer than any observed denial prefix and short
     * enough that a match inside pasted or quoted content later in a reply stays unread.
     */
    private const val HEAD_CHARS = 220
}
