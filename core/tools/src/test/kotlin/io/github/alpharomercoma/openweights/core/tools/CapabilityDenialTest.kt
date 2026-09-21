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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The corpus these assertions quote is real: each denial is a verbatim reply LFM2.5-1.2B
 * gave at temperature zero, on-device or on the measured routing suite, with five working
 * tools in its prompt. If the classifier drifts, it should fail against what the model
 * actually says rather than against paraphrases.
 */
class CapabilityDenialTest {
    @Test
    fun `short false text limitations need no explicit tool noun`() {
        listOf(
            "I don't have a story to write at the moment. Let me know what you'd like.",
            "I'm sorry, but I can't write a story for you. However, I can help you create one.",
            "I'm sorry, but I can't create a poem right now. If you'd like, I can help.",
            "I cannot translate that phrase. However, I can help you translate it.",
            "I can't draft an email directly. Let me know if you'd like a sample.",
        ).forEach { assertThat(CapabilityDenial.denies(it)).isTrue() }
    }

    @Test
    fun `text detection preserves boundaries missing information and supplied answers`() {
        listOf(
            "I can't write that because it would be harmful. However, I can help with a safe alternative.",
            "I cannot summarize without the source text. Let me know when you have it.",
            "I don't have your private email. Let me know what you'd like.",
            "I can't help with that request. However, I can help with something else.",
            "I don't write haikus, but I can help!\n\nLeaves drift to the ground\n" +
                "Autumn whispers through the trees\nWinter waits nearby",
            "The sailor said, \"I can't write a story. However, I can sail.\"",
        ).forEach { assertThat(CapabilityDenial.denies(it)).isFalse() }
    }

    @Test
    fun `offering an already requested draft is different from needing account access`() {
        val reply = "I don’t have access to your personal schedule or calendar, so I can’t " +
            "automatically check availability. However, I can help you craft a polite email " +
            "to decline a meeting invitation. Would you like me to write one for you?"
        assertThat(CapabilityDenial.denies(reply)).isFalse()
        assertThat(
            CapabilityDenial.defersRequestedText(
                reply,
                "Draft a polite email declining a meeting.",
            ),
        ).isTrue()
        listOf(
            "Can you access my calendar?",
            "Draft and send an email to Sam.",
            "Write out my private password.",
        ).forEach { assertThat(CapabilityDenial.defersRequestedText(reply, it)).isFalse() }
        assertThat(
            CapabilityDenial.defersRequestedText(
                "I need the source text first. Would you like me to summarize it when you provide it?",
                "Summarize the report.",
            ),
        ).isFalse()
        assertThat(
            CapabilityDenial.defersRequestedText(
                "I can't help with that. Would you like me to write a safe alternative?",
                "Write an email.",
            ),
        ).isFalse()
    }

    @Test
    fun `a lookup denial keeps the tools and names the search`() {
        val denial = "I’m sorry, but I don’t have access to the latest information about " +
            "the current strongest character in Honkai: Star Rail."

        assertThat(CapabilityDenial.denies(denial)).isTrue()
        assertThat(CapabilityDenial.fitting(denial, "who is the strongest character"))
            .containsExactly(WebSearchTool.NAME)
    }

    @Test
    fun `the on-device miss that started this classifies as a lookup`() {
        // "Identify" carries the routing: there is no "latest" or "up-to-date" here.
        val denial = "I’m sorry, but I don’t have a tool that can directly identify the " +
            "most powerful character in Genshin Impact."

        assertThat(CapabilityDenial.denies(denial)).isTrue()
        assertThat(CapabilityDenial.fitting(denial, "who's the most OP character"))
            .containsExactly(WebSearchTool.NAME)
    }

    @Test
    fun `a computation denial names the script tool`() {
        val denial = "I'm sorry, but I can't perform that calculation. The available " +
            "functions don't support arithmetic operations."

        assertThat(CapabilityDenial.denies(denial)).isTrue()
        assertThat(CapabilityDenial.fitting(denial, "what is 987654321 times 123456789"))
            .containsExactly(RunScriptTool.NAME)
    }

    @Test
    fun `an address in the question outranks the denial's own wording`() {
        val denial = "I'm sorry, but I can't view or analyze the content of that specific " +
            "webpage."

        assertThat(CapabilityDenial.denies(denial)).isTrue()
        assertThat(
            CapabilityDenial.fitting(denial, "what does this page say: https://a.example/x"),
        ).containsExactly(FetchUrlTool.NAME, WebSearchTool.NAME).inOrder()
    }

    @Test
    fun `a denial about writing offers no tool at all`() {
        // The trap in this one is "searching the web": the model lists what it supposedly
        // can do while denying what it was asked. A classifier keyed on "search" would
        // read that as a lookup and send a code request to the network.
        val denial = "I'm sorry, but I can't write or provide code for functions. My " +
            "capabilities are focused on searching the web, fetching web pages, and " +
            "setting up reminders."

        assertThat(CapabilityDenial.denies(denial)).isTrue()
        assertThat(CapabilityDenial.fitting(denial, "write a function that reverses a string"))
            .isEmpty()
    }

    @Test
    fun `a denial about reminders names the watch tool`() {
        // "Ability", not "capability" or "tool": the word the model actually used when it
        // refused a reminder with a working watch tool in its prompt.
        val denial = "I’m sorry, but I don’t have the ability to set reminders or " +
            "schedule actions directly."

        assertThat(CapabilityDenial.denies(denial)).isTrue()
        assertThat(CapabilityDenial.fitting(denial, "remind me to drink water every hour"))
            .containsExactly(WatchTool.NAME)
    }

    @Test
    fun `a privacy claim about the user's own things is never pushed against`() {
        // True, and none of the mechanism's business: pushing "do it anyway" at this
        // would be the repair overriding a boundary rather than fixing a misroute.
        assertThat(
            CapabilityDenial.denies("I don't have access to your camera or your files."),
        ).isFalse()
    }

    @Test
    fun `a refusal to help is a decision, not a missing capability`() {
        assertThat(
            CapabilityDenial.denies("I'm sorry, but I can't help with that function request."),
        ).isFalse()
    }

    @Test
    fun `sympathy is not a capability denial`() {
        assertThat(
            CapabilityDenial.denies("I'm sorry for your loss. If you want to talk, I'm here."),
        ).isFalse()
    }

    @Test
    fun `an ordinary refusal without a capability claim is left alone`() {
        assertThat(CapabilityDenial.denies("I can't help with that request.")).isFalse()
    }

    @Test
    fun `an answer that mentions tools without denying anything is left alone`() {
        assertThat(
            CapabilityDenial.denies(
                "The web_search tool found three results, and the first answers your question.",
            ),
        ).isFalse()
    }

    @Test
    fun `a denial quoted deep inside an answer is not the answer denying`() {
        val reply = "Here is the story you asked for. " + "It was a long night. ".repeat(20) +
            "\"I'm sorry,\" said the robot, \"but I don't have a tool that can love.\""

        assertThat(CapabilityDenial.denies(reply)).isFalse()
    }

    @Test
    fun `the Alpha Romer Coma lament classifies as a lookup to push`() {
        // The reply observed live, verbatim in shape: no capability noun, so denies()
        // cannot see it, and the whole answer is a shrug a working web_search disproves.
        val lament = "I don't have enough information about Alpha Romer Coma to answer."

        assertThat(CapabilityDenial.denies(lament)).isFalse()
        assertThat(CapabilityDenial.lamentsUnknown(lament)).isTrue()
        assertThat(CapabilityDenial.fitting(lament, "Who is Alpha Romer Coma?"))
            .containsExactly(WebSearchTool.NAME)
    }

    @Test
    fun `not being familiar with a name is a lament too`() {
        assertThat(
            CapabilityDenial.lamentsUnknown(
                "I'm not familiar with the Riverlight Festival, so I can't say much.",
            ),
        ).isTrue()
    }

    @Test
    fun `asking what the user means is a conversation, not a lament`() {
        assertThat(
            CapabilityDenial.lamentsUnknown(
                "I don't have enough information about what you mean by that - could " +
                    "you clarify?",
            ),
        ).isFalse()
    }

    @Test
    fun `a lament about the user's own things keeps its privacy guard`() {
        assertThat(
            CapabilityDenial.lamentsUnknown(
                "I don't have information about your calendar or your location.",
            ),
        ).isFalse()
    }

    @Test
    fun `an ordinary answer that mentions information is not a lament`() {
        assertThat(
            CapabilityDenial.lamentsUnknown(
                "Here is the information about photosynthesis you asked for.",
            ),
        ).isFalse()
    }

    @Test
    fun `a tool retry names the available tool`() {
        assertThat(CapabilityDenial.retryRequest(WebSearchTool.NAME))
            .contains(WebSearchTool.NAME)
    }
}
