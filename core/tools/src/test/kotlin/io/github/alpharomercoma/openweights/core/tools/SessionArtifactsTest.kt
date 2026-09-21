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
import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alpharomercoma.openweights.core.common.model.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the session made, it may rework without asking; what it found, it asks about.
 */
@RunWith(RobolectricTestRunner::class)
class SessionArtifactsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = FakeDocumentsProvider.register()
    private val grant = WorkspaceGrant(context).apply { remember(FakeDocumentsProvider.TREE) }
    private val workspace = Workspace(context, grant)
    private val artifacts = SessionArtifacts(workspace)
    private val writer = WriteFileTool(workspace, artifacts, CanvasBoard(), CanvasGrader.none())
    private val deleter = DeleteFileTool(workspace, artifacts)

    @Test
    fun `a session can keep editing and delete the file it created`() = runTest {
        assertThat(writer.execute(writeCall("site/index.html", "first")).successful).isTrue()
        assertThat(writer.asksInAuto(writeCall("site/index.html", "second", replace = true)))
            .isFalse()

        assertThat(writer.execute(writeCall("site/index.html", "second")).successful).isTrue()
        assertThat(contents("site/index.html")).isEqualTo("second")
        assertThat(deleter.asksInAuto(deleteCall("site/index.html"))).isFalse()
        assertThat(deleter.execute(deleteCall("site/index.html")).successful).isTrue()
        assertThat(workspace.resolve("site/index.html")).isNull()
    }

    @Test
    fun `a case-distinct user file is not owned or implicitly overwritten`() = runTest {
        writer.execute(writeCall("Index.html", "agent"))
        workspace.put("user-file.html", "user")
        // Distinct provider documents even when the host filesystem is case insensitive.
        provider.displayNames["root/user-file.html"] = "index.html"

        assertThat(writer.asksInAuto(writeCall("index.html", "replacement", replace = true)))
            .isTrue()
        assertThat(deleter.asksInAuto(deleteCall("index.html"))).isTrue()
        assertThat(writer.execute(writeCall("index.html", "replacement")).successful).isFalse()
        assertThat(contents("index.html")).isEqualTo("user")
        assertThat(contents("Index.html")).isEqualTo("agent")
    }

    @Test
    fun `the same relative name in a different shared folder remains protected`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        workspace.put("other/notes.txt", "user")
        grant.remember(
            DocumentsContract.buildTreeDocumentUri(
                FakeDocumentsProvider.TREE.authority,
                "root/other",
            ),
        )

        assertThat(
            writer.asksInAuto(writeCall("notes.txt", "replacement", replace = true)),
        ).isTrue()
        assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
        assertThat(writer.execute(writeCall("notes.txt", "replacement")).successful).isFalse()
        assertThat(contents("notes.txt")).isEqualTo("user")
    }

    @Test
    fun `reselecting the original folder does not revive ownership`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        workspace.put("other/notes.txt", "user")
        grant.remember(
            DocumentsContract.buildTreeDocumentUri(
                FakeDocumentsProvider.TREE.authority,
                "root/other",
            ),
        )
        grant.remember(FakeDocumentsProvider.TREE)

        assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
    }

    @Test
    fun `an observed loss of permission retires ownership even if restored externally`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.releasePersistableUriPermission(FakeDocumentsProvider.TREE, flags)
        assertThat(grant.state()).isEqualTo(GrantState.LOST)
        context.contentResolver.takePersistableUriPermission(FakeDocumentsProvider.TREE, flags)

        assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
    }

    @Test
    fun `deleting a folder retires ownership of files recreated beneath it`() = runTest {
        writer.execute(writeCall("site/index.html", "same"))
        assertThat(deleter.execute(deleteCall("site")).successful).isTrue()
        workspace.put("site/index.html", "same")

        assertThat(deleter.asksInAuto(deleteCall("site/index.html"))).isTrue()
        assertThat(writer.execute(writeCall("site/index.html", "replacement")).successful).isFalse()
        assertThat(contents("site/index.html")).isEqualTo("same")
    }

    @Test
    fun `a changed document behind a reused provider id is not owned`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        workspace.delete("notes.txt")
        workspace.put("notes.txt", "the user's replacement")

        assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
        assertThat(writer.execute(writeCall("notes.txt", "replacement")).successful).isFalse()
        assertThat(contents("notes.txt")).isEqualTo("the user's replacement")
    }

    @Test
    fun `a missing document loses ownership even if it returns under the same id`() = runTest {
        writer.execute(writeCall("notes.txt", "same"))
        workspace.delete("notes.txt")
        assertThat(artifacts.isOwn("notes.txt")).isFalse()
        workspace.put("notes.txt", "same")

        assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
    }

    @Test
    fun `an approved overwrite does not transfer the user's file to the session`() = runTest {
        workspace.put("notes.txt", "user")
        assertThat(writer.execute(writeCall("notes.txt", "approved", replace = true)).successful)
            .isTrue()

        assertThat(writer.asksInAuto(writeCall("notes.txt", "again", replace = true))).isTrue()
        assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
    }

    @Test
    fun `changing chat clears ownership and rejects a late creation from the old chat`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        val oldScope = artifacts.scope()
        artifacts.cleared()
        artifacts.created("notes.txt", oldScope)

        assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
        assertThat(writer.execute(writeCall("notes.txt", "replacement")).successful).isFalse()
        assertThat(contents("notes.txt")).isEqualTo("agent")
    }

    @Test
    fun `a creation finishing after the folder changes cannot claim the new folder's file`() =
        runTest {
            val oldScope = artifacts.scope()
            workspace.put("other/notes.txt", "user")
            grant.remember(
                DocumentsContract.buildTreeDocumentUri(
                    FakeDocumentsProvider.TREE.authority,
                    "root/other",
                ),
            )
            artifacts.created("notes.txt", oldScope)

            assertThat(deleter.asksInAuto(deleteCall("notes.txt"))).isTrue()
        }

    @Test
    fun `an ownership decision cannot authorize a different folder before execution`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        workspace.put("other/notes.txt", "user")
        val overwrite = writeCall("notes.txt", "replacement", replace = true)
        val delete = deleteCall("notes.txt")
        assertThat(writer.asksInAuto(overwrite)).isFalse()
        assertThat(deleter.asksInAuto(delete)).isFalse()
        grant.remember(
            DocumentsContract.buildTreeDocumentUri(
                FakeDocumentsProvider.TREE.authority,
                "root/other",
            ),
        )

        assertThat(writer.execute(overwrite).successful).isFalse()
        assertThat(deleter.execute(delete).successful).isFalse()
        assertThat(contents("notes.txt")).isEqualTo("user")
        grant.remember(FakeDocumentsProvider.TREE)
        assertThat(contents("notes.txt")).isEqualTo("agent")
    }

    @Test
    fun `an ownership decision cannot authorize a recreated document before execution`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        val overwrite = writeCall("notes.txt", "replacement", replace = true)
        val delete = deleteCall("notes.txt")
        assertThat(writer.asksInAuto(overwrite)).isFalse()
        assertThat(deleter.asksInAuto(delete)).isFalse()
        workspace.delete("notes.txt")
        workspace.put("notes.txt", "the user's new document")

        assertThat(writer.execute(overwrite).successful).isFalse()
        assertThat(deleter.execute(delete).successful).isFalse()
        assertThat(contents("notes.txt")).isEqualTo("the user's new document")
    }

    @Test
    fun `a folder switch while staging aborts before deleting the original`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        workspace.put("other/notes.txt", "user")
        val other = DocumentsContract.buildTreeDocumentUri(
            FakeDocumentsProvider.TREE.authority,
            "root/other",
        )
        provider.onOpen = { id, mode ->
            if (id.endsWith(".tmp") && 'w' in mode) grant.remember(other)
        }

        assertThat(writer.execute(writeCall("notes.txt", "replacement")).successful).isFalse()
        assertThat(contents("notes.txt")).isEqualTo("user")
        grant.remember(FakeDocumentsProvider.TREE)
        assertThat(contents("notes.txt")).isEqualTo("agent")
        assertThat(workspace.root().map { it.name }).containsExactly("notes.txt", "other")
    }

    @Test
    fun `rename fallback never creates the replacement in a newly selected folder`() = runTest {
        writer.execute(writeCall("notes.txt", "agent"))
        workspace.put("other/keep.txt", "user")
        val other = DocumentsContract.buildTreeDocumentUri(
            FakeDocumentsProvider.TREE.authority,
            "root/other",
        )
        provider.renames = false
        provider.onDelete = { id -> if (id == "root/notes.txt") grant.remember(other) }

        assertThat(writer.execute(writeCall("notes.txt", "replacement")).successful).isFalse()
        assertThat(workspace.resolve("notes.txt")).isNull()
        assertThat(contents("keep.txt")).isEqualTo("user")
        grant.remember(FakeDocumentsProvider.TREE)
        val staged = workspace.root().single { it.name.endsWith(".tmp") }
        assertThat(workspace.readText(staged, skip = 0, take = 1_000)).isEqualTo("replacement")
    }

    private suspend fun contents(path: String): String? =
        workspace.readText(requireNotNull(workspace.resolve(path)), skip = 0, take = 1_000)

    private fun writeCall(path: String, content: String, replace: Boolean = false) = ToolCall(
        id = "1",
        name = "write_file",
        argumentsJson = """{"path":"$path","content":"$content","replace":$replace}""",
    )

    private fun deleteCall(path: String) = ToolCall(
        id = "1",
        name = "delete_file",
        argumentsJson = """{"path":"$path"}""",
    )
}
