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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.common.model.ToolCall
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the one tool that dials an address of the model's choosing may be talked into.
 *
 * [PublicOnlyDns] covers the case where a name resolves somewhere it should not. These are
 * the cases that never reach a resolver at all, which is the gap that makes the resolver an
 * incomplete defence on its own. Save-race tests return HTTP bodies from an application
 * interceptor; all other fetches are refused before a socket opens.
 */
@RunWith(RobolectricTestRunner::class)
class FetchUrlToolTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val grant = WorkspaceGrant(context)
    private val workspace = Workspace(context, grant)
    private val artifacts = SessionArtifacts(workspace)

    private val tool =
        FetchUrlTool(OkHttpClient(), Reachability { true }, workspace, artifacts)

    private suspend fun fetch(url: String): String =
        tool.run(ToolCall(id = "1", name = "fetch_url", argumentsJson = """{"url":"$url"}"""))

    @Test
    fun `tainted yolo asks before saving a fetch but not before read-only fetches`() = runTest {
        val runner = AgentRunner(ToolRegistry(listOf(tool)), carriesUntrustedText = true)
        val save = ToolCall(
            id = "save",
            name = "fetch_url",
            argumentsJson = """{"url":"https://127.0.0.1/","save_to":"page.txt"}""",
        )
        var asked = 0
        val decision = runner.step(listOf(save), 0, AgentMode.YOLO, approve = {
            asked++
            false
        }) as AgentDecision.Continue
        assertThat(asked).isEqualTo(1)
        assertThat(decision.steps.single()).isInstanceOf(AgentStep.Skipped::class.java)

        // Loopback fails before a socket opens, but reaching the tool proves the policy
        // did not mistake a read-only request for a durable write.
        val read = save.copy(id = "read", argumentsJson = """{"url":"https://127.0.0.1/"}""")
        val readDecision = runner.step(listOf(read), 1, AgentMode.YOLO, approve = {
            error("Yolo waives read-only egress approval")
        }) as AgentDecision.Continue
        assertThat(readDecision.steps.single()).isInstanceOf(AgentStep.Ran::class.java)
    }

    @Test
    fun `read-only fetch still asks on tainted auto egress`() = runTest {
        val runner = AgentRunner(ToolRegistry(listOf(tool)), carriesUntrustedText = true)
        val decision = runner.step(
            listOf(ToolCall("read", "fetch_url", """{"url":"https://127.0.0.1/"}""")),
            0,
            AgentMode.AUTO,
            approve = { false },
        ) as AgentDecision.Continue
        assertThat(decision.steps.single()).isInstanceOf(AgentStep.Skipped::class.java)
    }

    @Test
    fun `find takes precedence over save without requiring durable approval`() = runTest {
        val runner = AgentRunner(ToolRegistry(listOf(tool)), carriesUntrustedText = true)
        val decision = runner.step(
            listOf(
                ToolCall(
                    "find",
                    "fetch_url",
                    """{"url":"https://127.0.0.1/","save_to":"page.txt","find":"price"}""",
                ),
            ),
            0,
            AgentMode.YOLO,
            approve = { error("find does not save") },
        ) as AgentDecision.Continue
        assertThat(decision.steps.single()).isInstanceOf(AgentStep.Ran::class.java)
    }

    @Test
    fun `save authorization cannot move to a folder selected in the approval callback`() = runTest {
        val other = writableWorkspace()
        val saving = savingTool()
        val runner = AgentRunner(ToolRegistry(listOf(saving)), carriesUntrustedText = true)
        val decision = runner.step(listOf(saveCall()), 0, AgentMode.YOLO, approve = {
            grant.remember(other)
            true
        }) as AgentDecision.Continue

        val step = decision.steps.single() as AgentStep.Ran
        assertThat(step.successful).isFalse()
        assertThat(workspace.resolve("page.txt")).isNull()
        grant.remember(FakeDocumentsProvider.TREE)
        assertThat(workspace.resolve("page.txt")).isNull()
    }

    @Test
    fun `save authorization stays in its original folder while HTTP is pending`() = runTest {
        val other = writableWorkspace()
        val saving = savingTool { grant.remember(other) }

        val execution = saving.execute(saveCall())

        assertThat(execution.successful).isFalse()
        assertThat(workspace.resolve("page.txt")).isNull()
        grant.remember(FakeDocumentsProvider.TREE)
        assertThat(workspace.resolve("page.txt")).isNull()
    }

    @Test
    fun `a successful save remains the session's file for iterative edits`() = runTest {
        writableWorkspace()
        val saving = savingTool()
        val call = saveCall()
        saving.asksInAuto(call)

        assertThat(saving.execute(call).successful).isTrue()
        val entry = requireNotNull(workspace.resolve("page.txt"))
        assertThat(workspace.readText(entry, skip = 0, take = 1_000)).isEqualTo("downloaded page")
        val writer = WriteFileTool(workspace, artifacts, CanvasBoard(), CanvasGrader.none())
        assertThat(
            writer.asksInAuto(
                ToolCall(
                    "edit",
                    "write_file",
                    """{"path":"page.txt","content":"edited","replace":true}""",
                ),
            ),
        ).isFalse()
    }

    @Test
    fun `find remains read-only when the folder changes during HTTP`() = runTest {
        val other = writableWorkspace()
        val saving = savingTool { grant.remember(other) }
        val call = ToolCall(
            "find",
            "fetch_url",
            """{"url":"https://example.com/page","save_to":"page.txt","find":"page"}""",
        )
        saving.asksInAuto(call)

        val execution = saving.execute(call)

        assertThat(execution.successful).isTrue()
        assertThat(execution.text).contains("downloaded page")
        assertThat(workspace.resolve("page.txt")).isNull()
    }

    @Test
    fun `a fetch started without a writable folder does not save to a newly selected one`() =
        runTest {
            FakeDocumentsProvider.register()
            val saving = savingTool { grant.remember(FakeDocumentsProvider.TREE) }
            val call = saveCall()
            saving.asksInAuto(call)

            val execution = saving.execute(call)

            assertThat(execution.successful).isTrue()
            assertThat(execution.text).isEqualTo("downloaded page")
            assertThat(workspace.resolve("page.txt")).isNull()
        }

    private suspend fun writableWorkspace(): Uri {
        FakeDocumentsProvider.register()
        grant.remember(FakeDocumentsProvider.TREE)
        check(workspace.put("other/keep.txt", "user").successful)
        return DocumentsContract.buildTreeDocumentUri(
            FakeDocumentsProvider.TREE.authority,
            "root/other",
        )
    }

    private fun saveCall() = ToolCall(
        "save",
        "fetch_url",
        """{"url":"https://example.com/page","save_to":"page.txt"}""",
    )

    private fun savingTool(beforeResponse: () -> Unit = {}): FetchUrlTool {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            beforeResponse()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("downloaded page".toResponseBody("text/plain".toMediaType()))
                .build()
        }.build()
        return FetchUrlTool(client, Reachability { true }, workspace, artifacts)
    }

    @Test
    fun `successful page read carries requested and final addresses as typed evidence`() {
        val execution = FetchUrlTool.fetchedPageSuccess(
            body = "The article.",
            requestedUrl = "https://example.test/article",
            finalUrl = "https://www.example.test/article",
        )

        assertThat(execution.successful).isTrue()
        assertThat(execution.text).isEqualTo("The article.")
        assertThat(execution.evidence).isEqualTo(
            ToolEvidence.Fetch(
                requestedUrl = "https://example.test/article",
                finalUrl = "https://www.example.test/article",
            ),
        )
    }

    @Test
    fun `a json body is handed over exactly as it came`() {
        // Every textual type went through the HTML cleaner, which cut anything shaped like a
        // tag, decoded entities and folded newlines into spaces. A script reading the file
        // save_to wrote then opened a document the server never sent.
        val body = "{\n  \"check\": \"a < b && b > c\",\n  \"note\": \"x &amp; y\"\n}"

        assertThat(FetchUrlTool.pageText(body, "application/json")).isEqualTo(body)
        assertThat(FetchUrlTool.pageText(body, "text/plain")).isEqualTo(body)
        assertThat(FetchUrlTool.pageText("<a>\n<b>", "application/xml")).isEqualTo("<a>\n<b>")
    }

    @Test
    fun `an html body is still cleaned`() {
        val body = "<html><body><script>var x = 1</script><p>Hello &amp;\nworld</p></body></html>"

        assertThat(FetchUrlTool.pageText(body, "text/html")).isEqualTo("Hello & world")
        assertThat(FetchUrlTool.pageText(body, null)).isEqualTo("Hello & world")
    }

    @Test
    fun `a page reaches the model as prose, and is searched as prose`() {
        // Two questions in one: what does the model actually receive from a real page, and
        // does a pattern it writes match that or the markup underneath. Both have to be
        // prose, or a model told to find "battery" matches a CSS class called battery.
        val html = """
            <html><head><title>Phone</title>
            <style>.spec { color: red }</style>
            <script>var battery = "fake";</script></head>
            <body><nav>Home About Contact</nav>
            <article><h1>Specifications</h1>
            <p>The battery is <b>5000</b> mAh and charges at 67 W.</p>
            <p>The screen is 6.7 inches.</p></article>
            <footer>Copyright 2026</footer></body></html>
        """.trimIndent()

        val text = FetchUrlTool.pageText(html, "text/html")

        // Structured, clean content: no tags, no stylesheet, no script, and the furniture
        // around the article gone with them.
        assertThat(text).doesNotContain("<")
        assertThat(text).doesNotContain("color: red")
        assertThat(text).doesNotContain("var battery")
        assertThat(text).doesNotContain("Home About Contact")
        assertThat(text).contains("The battery is 5000 mAh")

        // And it arrives with the shape the author gave it. The heading is a heading and
        // the two sentences are two paragraphs, which is the difference between a document
        // a model can navigate and the single unbroken run this used to hand over.
        assertThat(text).startsWith("# Specifications")
        assertThat(text).contains("mAh and charges at 67 W.\n\nThe screen is 6.7 inches.")

        // And the pattern matches the sentence rather than the script's variable, which is
        // the reason the search runs after the cleaning rather than before it.
        val found = PageSearch.search(text, "battery is [0-9]+ mAh")
            as PageSearch.Result.Found
        assertThat(found.count).isEqualTo(1)
        assertThat(found.windows.single()).contains("5000 mAh")
    }

    @Test
    fun `find is the parameter the model is told about`() {
        // The declaration is what a model can act on: a search that works and is not
        // offered is a search that never happens.
        val schema = tool.definition.parametersJson

        assertThat(schema).contains("\"find\"")
        assertThat(tool.definition.description).contains("find")
    }

    @Test
    fun `refused page is an unsuccessful typed execution with no evidence`() = runTest {
        val execution = tool.execute(
            ToolCall(
                id = "1",
                name = "fetch_url",
                argumentsJson = """{"url":"https://127.0.0.1/private"}""",
            ),
        )

        assertThat(execution.successful).isFalse()
        assertThat(execution.evidence).isNull()
    }

    @Test
    fun `an address written as a bare loopback literal is refused`() = runTest {
        // OkHttp routes a hostname through Dns and an IP literal straight to a socket, so
        // PublicOnlyDns is never consulted about this one and cannot refuse it. A page that
        // said "now read https://127.0.0.1:8080/" reached whatever was listening.
        assertThat(fetch("https://127.0.0.1:8080/admin")).contains("not on the public internet")
    }

    @Test
    fun `the home router written as a literal is refused`() = runTest {
        assertThat(fetch("https://192.168.1.1/")).contains("not on the public internet")
    }

    @Test
    fun `a public looking name in front of a private literal is refused`() = runTest {
        // The host is the literal; everything before the @ is userinfo and goes nowhere. A
        // check on the string rather than on the parsed host reads this as example.com.
        assertThat(fetch("https://example.com@10.0.0.1/")).contains("not on the public internet")
    }

    @Test
    fun `link local metadata addresses are refused`() = runTest {
        assertThat(fetch("https://169.254.169.254/latest/meta-data/"))
            .contains("not on the public internet")
    }

    @Test
    fun `an https address in capitals is not refused for its scheme`() = runTest {
        // Schemes are case insensitive, and the check was startsWith("https://"). A model
        // writing HTTPS was told the app only reads https, which reads as nonsense.
        assertThat(fetch("HTTPS://example.invalid/")).doesNotContain("Only https")
    }

    @Test
    fun `plain http is refused for its scheme`() = runTest {
        assertThat(fetch("http://example.invalid/")).contains("Only https")
    }

    @Test
    fun `a file address is not an address that can be read`() = runTest {
        // Not an http address at all, so it never becomes a request. The app's own database
        // lives under a path like this one.
        assertThat(fetch("file:///data/data/io.github.alpharomercoma.openweights/databases"))
            .contains("not an address that can be read")
    }

    @Test
    fun `something that is not an address at all is refused rather than dialled`() = runTest {
        assertThat(fetch("not a url")).isNotEmpty()
    }

    @Test
    fun `a redirect to a private literal is refused, not followed`() {
        // The whole vulnerability, as a rule. The checks used to run once, on the address
        // the user approved, and the client followed redirects by itself: a public page
        // answering "302 Location: https://192.168.1.1/admin" reached the router on the
        // user's own network and the reply summarised what it found there. Every hop is
        // now put through the same guard, so the second address is refused like the first.
        val from = "https://example.com/page".toHttpUrl()

        val target = from.resolve("https://192.168.1.1/admin")!!

        assertThat(refuseAddress(target)).contains("not on the public internet")
    }

    @Test
    fun `a redirect to the metadata address is refused`() {
        val from = "https://example.com/page".toHttpUrl()

        val target = from.resolve("https://169.254.169.254/latest/meta-data/")!!

        assertThat(refuseAddress(target)).contains("not on the public internet")
    }

    @Test
    fun `a redirect that drops to plain http is refused`() {
        // followSslRedirects is off for the same reason the scheme is checked at all: a hop
        // that downgrades is a hop that can be read on the way past.
        val from = "https://example.com/page".toHttpUrl()

        val target = from.resolve("http://example.com/page")!!

        assertThat(refuseAddress(target)).contains("Only https")
    }

    @Test
    fun `an ordinary redirect to another public page is allowed`() {
        // The counterweight. Redirects are how the web works, and refusing them all would
        // break every canonical link and every shortened one.
        val from = "https://example.com/page".toHttpUrl()

        val target = from.resolve("https://www.example.org/moved")!!

        assertThat(refuseAddress(target)).isNull()
    }

    @Test
    fun `a relative redirect stays on the host it came from`() {
        val from = "https://example.com/a/b".toHttpUrl()

        val target = from.resolve("/c")!!

        assertThat(target.host).isEqualTo("example.com")
        assertThat(refuseAddress(target)).isNull()
    }

    @Test
    fun `a linkedin profile is refused before it is dialled`() = runTest {
        // Reproduces a device transcript: LinkedIn answers HTTP 200 with a real page full of
        // prose, and every other check here waves it through. What was inside was the same
        // sign-in prompt repeated once per gated section, and the model wrote a fluent, wrong
        // biography out of it. No request in this test's flow means no boilerplate to
        // misread.
        assertThat(fetch("https://ph.linkedin.com/in/someone")).contains("requires signing in")
    }

    @Test
    fun `a subdomain of a walled garden is refused the same as the bare domain`() {
        assertThat(walledGardenRefusal("https://www.linkedin.com/in/someone".toHttpUrl()))
            .contains("requires signing in")
        assertThat(walledGardenRefusal("https://m.facebook.com/someone".toHttpUrl()))
            .contains("requires signing in")
    }

    @Test
    fun `a host that only contains a walled garden's name is not refused`() {
        // "notlinkedin.com" ends in "linkedin.com" as a string but is not the site, and
        // "linkedin.com.evil.test" is the reverse trick. Neither is a subdomain of it.
        assertThat(walledGardenRefusal("https://notlinkedin.com/".toHttpUrl())).isNull()
        assertThat(walledGardenRefusal("https://linkedin.com.evil.test/".toHttpUrl())).isNull()
    }

    @Test
    fun `an ordinary page is not refused as a walled garden`() {
        assertThat(walledGardenRefusal("https://example.com/article".toHttpUrl())).isNull()
    }

    @Test
    fun `a walled garden's public documentation subdomain is read, not refused`() {
        // The blog and API docs are served whole to a logged-out request; refusing them
        // claimed a sign-in wall on pages that have none. The profile subdomains stay
        // refused — that is the test above this section, on ph.linkedin.com.
        assertThat(
            walledGardenRefusal("https://engineering.linkedin.com/blog/topic".toHttpUrl()),
        ).isNull()
        assertThat(
            walledGardenRefusal("https://developers.facebook.com/docs/graph-api".toHttpUrl()),
        ).isNull()
        assertThat(walledGardenRefusal("https://about.instagram.com/blog".toHttpUrl())).isNull()
    }
}
