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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The files this session's model has itself created, which changes what needs asking.
 *
 * The write tool asks before replacing a file and the delete tool asks before removing
 * one, because those destroy something of the user's that nothing here can put back. A
 * file the model created ten seconds ago in this same session is not that: rewriting
 * style.css eight times is the loop an agent building a site *is*, and asking eight times
 * teaches the user to stop reading the question. So creations are remembered here, and
 * the asking rules wave through what the session made while still guarding what it found.
 *
 * A session is one conversation on screen in one run of the process, and it ends at
 * whichever comes first. In memory only, on purpose: after a restart everything on disk
 * is the user's again. And [cleared] when the chat changes, because this object outlives
 * the conversation it was filled in. Left standing, a file the model made in one chat
 * stayed silently overwritable in the next one for as long as the process lived, though
 * nobody in that chat had watched it being made.
 */
@Singleton
class SessionArtifacts @Inject constructor(private val workspace: Workspace) {
    private val created = mutableMapOf<String, ArtifactIdentity>()
    private var revision = 0L

    /** A completion from an old chat or folder must not grant ownership in the new one. */
    internal data class Scope(val sessionRevision: Long, val workspace: WorkspaceScope?)
    internal data class Check(val scope: Scope, val identity: ArtifactIdentity?, val own: Boolean)

    @Synchronized
    internal fun scope(): Scope = Scope(revision, workspace.ownershipScope())

    @Synchronized
    internal fun created(path: String, started: Scope) {
        if (started != scope()) return
        val identity = workspace.artifactIdentity(path) ?: return
        if (identity.scope == started.workspace) created[path] = identity
    }

    fun isOwn(path: String): Boolean = check(path).own

    @Synchronized
    internal fun check(path: String): Check {
        val started = scope()
        val identity = workspace.artifactIdentity(path)
        val own = identity != null && created[path] == identity
        // A missing or changed document is not ours even if the old name returns later.
        if (!own) created.remove(path)
        return Check(started, identity, own)
    }

    @Synchronized
    internal fun isCurrent(check: Check): Boolean = check.scope == scope()

    /** Deleting a directory also retires ownership of every file beneath it. */
    @Synchronized
    internal fun deleted(path: String) {
        val descendants = "$path/"
        created.keys.removeAll { it == path || it.startsWith(descendants) }
    }

    /** Forgets everything: from here on every file on disk is the user's until made again. */
    @Synchronized
    fun cleared() {
        revision++
        created.clear()
    }
}
