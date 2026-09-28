#!/usr/bin/env python3
"""Releases the app to Google Play: what changed since the live version, and the upload.

    python3 tools/release/play.py notes --track production --out build/release
    python3 tools/release/play.py publish --track production --bundle app-release.aab \\
        --mapping mapping.txt --notes build/release/whats-new.txt [--rollout 20]

The release workflow (.github/workflows/release.yml) runs both, and they run from a laptop
just as well. Play is reached with a service account: its JSON key in PLAY_SERVICE_ACCOUNT_JSON,
or whatever Application Default Credentials find. The one-time setup is in docs/play-store.md.

`notes` answers "what changed" from Play itself rather than from a tag somebody has to
remember to push. The version code is the commit count on main (app/build.gradle.kts), so the
code Play is serving on a track names the commit it was built from, and the commits after it
are the release. The user-facing text is drafted from them by Gemini, on the API's free tier,
when GEMINI_API_KEY is set, or taken verbatim from WHATS_NEW; either way it is shown for
approval before `publish` sends it.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

PACKAGE = "io.github.alpharomercoma.openweights"
ROOT = Path(__file__).resolve().parents[2]
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
LANGUAGE = "en-US"
# Play's own limit for a release note, per language. Longer text is refused at upload.
NOTES_LIMIT = 500
TRACKS = ("internal", "alpha", "beta", "production")
# Models on the Gemini API's free tier, best first; the next is asked only when one is still
# busy after its retries. On the first real calls (2026-09-29) every 3.x Flash model answered
# 503 or hung for minutes on the free tier while 2.5 Flash-Lite answered in under a second,
# so the chain ends on the one that was there. Free-tier prompts may be used to improve
# Google's products; what is sent is commit messages from this public repository.
DRAFT_MODELS = ("gemini-3.8-flash", "gemini-3.5-flash", "gemini-2.5-flash-lite")
# Overloaded or over quota: worth another model. Anything else (a bad key) is not.
BUSY = (429, 500, 502, 503, 504)
# A draft of ~6,000 prompt tokens takes seconds when a model is free; a minute is a stall.
DRAFT_TIMEOUT_S = 60
REPO_URL = "https://github.com/ExperimentalMachines/openweights"


class ReleaseError(Exception):
    """A reason to stop, worded for the person reading the workflow log."""


# ---------------------------------------------------------------------------------------------
# git


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=ROOT, check=True, capture_output=True, text=True,
    ).stdout.strip()


def commit_count(rev: str = "HEAD") -> int:
    return int(git("rev-list", "--count", rev))


def commit_for_code(code: int, head: str = "HEAD") -> tuple[str, bool]:
    """The commit on main that built version code `code`, and whether the count matched exactly.

    Walks main's first-parent line, where the count falls as it goes back, and bisects for the
    newest commit whose count is at most `code`. A merge adds the whole branch to the count at
    once, so a code that fell inside one has no first-parent commit of its own; the commit
    just before the merge is returned instead, which errs towards listing more changes, never
    fewer.
    """
    line = git("log", "--first-parent", "--format=%H", head).splitlines()
    if not line:
        raise ReleaseError("There are no commits to release.")
    lo, hi = 0, len(line) - 1
    if commit_count(line[hi]) > code:
        raise ReleaseError(f"Version code {code} is older than the first commit on main.")
    # line[0] is newest. Find the smallest index whose count is <= code.
    while lo < hi:
        mid = (lo + hi) // 2
        if commit_count(line[mid]) <= code:
            hi = mid
        else:
            lo = mid + 1
    return line[lo], commit_count(line[lo]) == code


@dataclass
class Commit:
    sha: str
    subject: str
    body: str
    areas: list[str]

    @property
    def ships(self) -> bool:
        """Whether the commit changed code that goes into the bundle, tests aside."""
        return any(a == "app" or a.startswith("core/") for a in self.areas)


# Source sets that never reach a release bundle, whatever module they sit in: tests, and the
# debug source set (core/engine/src/debug carries the MediaTek NPU libraries, debug builds only).
TEST_SOURCES = re.compile(r"/src/(test|androidTest|jvmTest|commonTest|[a-zA-Z]+Test|debug)/")


def area(path: str) -> str:
    """The part of the tree a file belongs to, coarse enough to tell app code from research."""
    parts = path.split("/")
    name = "/".join(parts[:2]) if parts[0] in ("core", "docs", "tools") and len(parts) > 2 else parts[0]
    return f"tests:{name}" if TEST_SOURCES.search(path) else name


TRAILER = re.compile(r"^(Co-authored-by|Signed-off-by):", re.IGNORECASE)


def commits_between(base: str, head: str = "HEAD") -> list[Commit]:
    raw = git("log", "--no-merges", "--name-only", "--format=%x1e%H%x1f%s%x1f%b%x1f", f"{base}..{head}")
    commits = []
    for record in raw.split("\x1e"):
        if not record.strip():
            continue
        sha, subject, body, files = record.split("\x1f", 3)
        areas = sorted({area(f) for f in files.split("\n") if f.strip()})
        # Attribution trailers say who wrote a commit, which is nothing a release note uses.
        body = "\n".join(line for line in body.split("\n") if not TRAILER.match(line))
        commits.append(Commit(sha, subject.strip(), body.strip(), areas))
    return commits


def version_name(rev: str = "HEAD") -> str:
    text = git("show", f"{rev}:app/build.gradle.kts")
    found = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not found:
        raise ReleaseError("No versionName in app/build.gradle.kts.")
    return found.group(1)


# ---------------------------------------------------------------------------------------------
# Play


def play_service():
    # Imported here so that the git half of this file, and its tests, need nothing installed.
    import google.auth
    import httplib2
    from google.oauth2 import service_account
    from google_auth_httplib2 import AuthorizedHttp
    from googleapiclient.discovery import build

    info = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if info:
        credentials = service_account.Credentials.from_service_account_info(json.loads(info), scopes=[SCOPE])
    else:
        credentials, _ = google.auth.default(scopes=[SCOPE])
    # Google's own advice for bundles.upload is a longer timeout than the client's default;
    # a 29 MB bundle and a 68 MB mapping file do not always finish in one minute.
    http = AuthorizedHttp(credentials, http=httplib2.Http(timeout=900))
    return build("androidpublisher", "v3", http=http, cache_discovery=False)


@dataclass
class TrackState:
    name: str
    releases: list[dict]

    def codes(self, statuses: tuple[str, ...] | None = None) -> list[int]:
        return [
            int(code)
            for release in self.releases
            if statuses is None or release.get("status") in statuses
            for code in release.get("versionCodes", [])
        ]


def read_tracks(service) -> dict[str, TrackState]:
    """Every track's releases, read in an edit that is thrown away afterwards."""
    edits = service.edits()
    edit = edits.insert(packageName=PACKAGE, body={}).execute(num_retries=3)
    try:
        listed = edits.tracks().list(packageName=PACKAGE, editId=edit["id"]).execute(num_retries=3)
    finally:
        edits.delete(packageName=PACKAGE, editId=edit["id"]).execute(num_retries=3)
    return {t["track"]: TrackState(t["track"], t.get("releases", [])) for t in listed.get("tracks", [])}


def live_code(tracks: dict[str, TrackState], track: str) -> int | None:
    """The version code most of this track's users have, which is what the notes are measured from.

    A tester is also a production user, and Play gives everyone the highest code they are
    eligible for, so a testing track's users have whichever is higher of its own release and
    production's. That matters here: on 2026-09-29 internal testing still held 201, from long
    before production reached 615, and measuring from 201 would have listed 440 commits.

    A completed release is what everyone on a track was given. A staged rollout, or a halted
    one, reached only some of them, so it counts only when nothing on either track completed.
    """
    names = {track, "production"}
    for statuses in (("completed",), ("inProgress", "halted")):
        codes = [code for name in names if name in tracks for code in tracks[name].codes(statuses)]
        if codes:
            return max(codes)
    return None


def highest_code(tracks: dict[str, TrackState]) -> int:
    """The highest code on any track in any state, drafts included. A new bundle must beat it."""
    return max((code for state in tracks.values() for code in state.codes()), default=0)


def release_body(track: str, code: int, name: str, notes: str, rollout: float) -> dict:
    if not 0 < rollout <= 100:
        raise ReleaseError(f"The rollout is a percentage above 0 and at most 100, not {rollout:g}.")
    release: dict = {
        "name": name,
        "versionCodes": [str(code)],
        "releaseNotes": [{"language": LANGUAGE, "text": notes}],
        "status": "completed",
    }
    if rollout < 100:
        if track == "internal":
            raise ReleaseError("Internal testing has no staged rollout; release it to 100%.")
        release["status"] = "inProgress"
        release["userFraction"] = round(rollout / 100, 4)
    return {"track": track, "releases": [release]}


# ---------------------------------------------------------------------------------------------
# The notes

UNDER_THE_HOOD = "• Fixes and improvements under the hood."

SYSTEM = f"""You write the "What's new" text for OpenWeights on Google Play.

OpenWeights is an Android app that runs open-weight language models entirely on the phone:
GGUF files through llama.cpp and compiled .pte exports through ExecuTorch. No account, no
telemetry, no cloud; the only things that leave the phone are model downloads from Hugging
Face and the assistant's web tools, each of which the user can switch off.

You are given every commit since the version users have now. Most commits in this repository
are research, benchmarks, evaluation tooling, documentation or build work that changes nothing
a user can see. Write only about what a person using the app would notice: a new feature, a
fixed bug, something faster or lighter, a model that now works. A commit that adds code which
ships switched off, or only for developers or a test harness, is not a change for users. Its
"areas" say which part of the tree it touched: app and core/* are the app, and tests:* is test
code. Only commits that changed the app are given to you, but most of them also carry research
or test work in the same commit; that part is not a change for users either.

Rules for the text:
- At most 450 characters in total. Play refuses more than 500.
- One change per line, each line starting with "• ". Three to five lines is typical.
- Plain words a user would use. No class names, file paths, commit hashes or version codes.
- A number only if a commit measured it on a phone, and only as the commit states it.
- No em dashes or en dashes; use a comma, a colon or a full stop. No emoji, no exclamation
  marks, no marketing adjectives.
- If nothing user-visible changed, write one line: "{UNDER_THE_HOOD}"

Reply with the text only, nothing before or after it."""


def commits_prompt(commits: list[Commit], base_code: int, head_code: int) -> str:
    lines = [f"Users have version {base_code}. This release is version {head_code}.", ""]
    for commit in commits:
        body = commit.body if len(commit.body) <= 1500 else commit.body[:1500] + " [...]"
        lines += [f"## {commit.subject}", f"areas: {', '.join(commit.areas) or 'none'}"]
        if body:
            lines += ["", body]
        lines.append("")
    return "\n".join(lines)


def clean_notes(text: str) -> str:
    """The house style, applied after the model rather than trusted to it."""
    text = text.strip().replace("\r\n", "\n")
    # No em or en dashes anywhere (AGENTS.md). A range reads as "to"; anything else as a comma.
    text = re.sub(r"(\d)\s*[\u2013\u2014]\s*(\d)", r"\1 to \2", text)
    text = re.sub(r"\s*[\u2013\u2014]\s*", ", ", text)
    text = re.sub(r"[ \t]+", " ", text)
    return "\n".join(line.strip() for line in text.split("\n")).strip()


def check_notes(text: str) -> str:
    text = clean_notes(text)
    if not text:
        raise ReleaseError("The release notes are empty.")
    if len(text) > NOTES_LIMIT:
        raise ReleaseError(f"The release notes are {len(text)} characters; Play takes at most {NOTES_LIMIT}.")
    return text


def generate(client, prompt: str) -> tuple[str, str]:
    """The first model in DRAFT_MODELS that answers, and its text."""
    import httpx
    from google.genai import errors, types

    busy = None
    for model in DRAFT_MODELS:
        try:
            response = client.models.generate_content(
                model=model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=SYSTEM,
                    # There are no tools; off, it also stops the SDK warning about them in the log.
                    automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
                ),
            )
        except httpx.TimeoutException:
            busy = f"no answer in {DRAFT_TIMEOUT_S} s"
            print(f"{model}: {busy}, after retries; trying the next model", file=sys.stderr)
            continue
        except errors.APIError as error:
            if error.code in BUSY:
                busy = f"{error.code}: {error.message}"
                print(f"{model} is busy ({error.code}) after retries; trying the next model", file=sys.stderr)
                continue
            raise ReleaseError(f"Gemini refused the request ({error.code}): {error.message}") from error
        text = clean_notes(response.text or "")
        if not text:
            feedback = response.prompt_feedback
            reason = feedback.block_reason if feedback and feedback.block_reason else (
                response.candidates[0].finish_reason if response.candidates else "no candidates"
            )
            raise ReleaseError(f"Gemini returned no draft ({reason}). Run again with the text in whats_new.")
        return model, text
    raise ReleaseError(f"Every Gemini model was busy ({busy}). Run again later, or with the text in whats_new.")


def draft_notes(commits: list[Commit], base_code: int, head_code: int) -> tuple[str, str]:
    """The notes, and the model that wrote them."""
    from google import genai
    from google.genai import types

    # The SDK retries nothing and never times out unless asked, and a busy free-tier model
    # does both: it answers 503, or holds the request open (3.8 Flash, for over 45 s). Three
    # attempts per model, 5 s then 10 s apart, each given DRAFT_TIMEOUT_S, before the next.
    client = genai.Client(  # GEMINI_API_KEY
        http_options=types.HttpOptions(
            timeout=DRAFT_TIMEOUT_S * 1000,
            retry_options=types.HttpRetryOptions(attempts=3, initial_delay=5.0, max_delay=30.0),
        ),
    )
    prompt = commits_prompt(commits, base_code, head_code)
    for _attempt in range(2):
        model, text = generate(client, prompt)
        if len(text) <= NOTES_LIMIT:
            return model, text
        # One fresh request with the long draft quoted, rather than a second turn: the retry
        # is the same question with one more constraint, and needs none of the first reply's state.
        prompt = f"{prompt}\n\nA first draft was {len(text)} characters, over the limit:\n\n{text}\n\nWrite it again in at most 450."
    raise ReleaseError("The drafted notes stayed over Play's limit. Run again with the text in whats_new.")


# ---------------------------------------------------------------------------------------------
# Commands


def summary(markdown: str) -> None:
    """To the workflow's run page when there is one, and to the terminal either way."""
    print(markdown)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a") as out:
            out.write(markdown + "\n")


def output(**values: object) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a") as out:
            for key, value in values.items():
                out.write(f"{key}={value}\n")


def notes_command(args: argparse.Namespace) -> None:
    promoting = args.version_code is not None
    if promoting:
        # A bundle Play already has, most often one tested on internal testing first. Its
        # notes are the commits up to the one it was built from, not up to main.
        head_code = args.version_code
        head, exact_head = commit_for_code(head_code)
        if not exact_head:
            raise ReleaseError(f"No commit on main has the count {head_code}, so no bundle of main has that version code.")
    else:
        head_code, head = commit_count(), git("rev-parse", "HEAD")
    if args.base_code is not None:
        base_code = args.base_code
    else:
        tracks = read_tracks(play_service())
        base_code = live_code(tracks, args.track)
        if base_code is None:
            raise ReleaseError("Play has no release on any track to compare with. Give the text in whats_new.")
        on_play = {code for state in tracks.values() for code in state.codes()}
        if promoting and head_code not in on_play:
            raise ReleaseError(
                f"Play has no bundle with version code {head_code}. Release it to a testing track first, "
                "or leave version_code empty to build main.",
            )
        if not promoting and head_code <= highest_code(tracks):
            raise ReleaseError(
                f"main is at version code {head_code} and Play already has {highest_code(tracks)}. Play "
                "refuses a code it has seen; release a newer commit, or promote that code with version_code.",
            )
    if head_code <= base_code:
        raise ReleaseError(f"The track's users already have version {base_code}, and this is {head_code}.")

    base, exact = commit_for_code(base_code)
    commits = commits_between(base, head)
    if not commits:
        raise ReleaseError(f"Nothing has changed since version {base_code}.")

    # A workflow_dispatch text box is one line, so a typed \n is the line break.
    given = os.environ.get("WHATS_NEW", "").replace("\\n", "\n").strip()
    # Most commits here are research, evaluation or docs. Dropping them before the draft,
    # rather than asking the model to, is what lets a small free model write it (2026-09-29:
    # 2.5 Flash-Lite, given all 27 commits since 613, listed research-only ones as features).
    shipped = [c for c in commits if c.ships]
    if given:
        text, source = check_notes(given), "given in whats_new"
    elif not shipped:
        text, source = UNDER_THE_HOOD, "fixed text: no commit changed the app's shipped code"
    elif os.environ.get("GEMINI_API_KEY"):
        model, text = draft_notes(shipped, base_code, head_code)
        text = check_notes(text)
        source = f"drafted by {model} from the {len(shipped)} commits that changed the app"
    else:
        raise ReleaseError("No whats_new was given and there is no GEMINI_API_KEY to draft one with.")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "whats-new.txt").write_text(text + "\n")
    def listing(chosen: list[Commit]) -> str:
        return "\n".join(f"- [`{c.sha[:8]}`]({REPO_URL}/commit/{c.sha}) {c.subject}" for c in chosen) or "None."
    near = "" if exact else " (the nearest commit on main; that code was built inside a merge)"
    changes = (
        f"## Release {version_name(head)} ({head_code}) to {args.track}\n\n"
        f"{'Promotes the bundle Play already has, built' if promoting else 'Built'} from "
        f"[`{head[:8]}`]({REPO_URL}/commit/{head}). The track's users have version "
        f"{base_code}, built from [`{base[:8]}`]({REPO_URL}/commit/{base}){near}.\n\n"
        f"### What's new, {len(text)} of {NOTES_LIMIT} characters, {source}\n\n"
        f"```\n{text}\n```\n\n"
        f"### The {len(commits)} commits since version {base_code}\n\n"
        f"Changed the app ({len(shipped)}, the ones the draft is written from):\n\n{listing(shipped)}\n\n"
        f"Research, docs, tests and tooling only ({len(commits) - len(shipped)}):\n\n"
        f"{listing([c for c in commits if not c.ships])}\n"
    )
    (out / "changes.md").write_text(changes)
    summary(changes)
    output(version_code=head_code, base_code=base_code, release_name=f"{version_name(head)} ({head_code})")


def publish_command(args: argparse.Namespace) -> None:
    if (args.bundle is None) == (args.version_code is None):
        raise ReleaseError("Give either --bundle to upload, or --version-code for a bundle Play already has.")
    notes = check_notes(Path(args.notes).read_text())
    service = play_service()
    edits = service.edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute(num_retries=3)["id"]
    try:
        if args.version_code is not None:
            code = args.version_code
            print(f"Releasing version code {code}, which Play already has; nothing is uploaded")
        else:
            code = upload(edits, edit_id, args.bundle, args.mapping)
        name = args.name or f"{version_name()} ({code})"
        body = release_body(args.track, code, name, notes, args.rollout)
        edits.tracks().update(packageName=PACKAGE, editId=edit_id, track=args.track, body=body).execute(num_retries=3)
        # The default would cancel whatever is already in review, a listing change made in the
        # Console included, and resubmit it with this release. Stop and say so instead.
        edits.commit(
            packageName=PACKAGE, editId=edit_id, changesInReviewBehavior="ERROR_IF_IN_REVIEW",
        ).execute(num_retries=3)
        edit_id = None
    finally:
        if edit_id:
            edits.delete(packageName=PACKAGE, editId=edit_id).execute(num_retries=3)
    rollout = "" if args.rollout >= 100 else f", staged to {args.rollout:g}% of users"
    summary(f"## Released {name} to {args.track}{rollout}\n\n```\n{notes}\n```\n")


def upload(edits, edit_id: str, bundle_path: str, mapping_path: str | None) -> int:
    """Uploads the bundle, and its R8 mapping when given, into the edit. Returns the version code."""
    from googleapiclient.http import MediaFileUpload

    bundle = edits.bundles().upload(
        packageName=PACKAGE,
        editId=edit_id,
        media_body=MediaFileUpload(bundle_path, mimetype="application/octet-stream", resumable=True),
    ).execute(num_retries=3)
    code = int(bundle["versionCode"])
    print(f"Uploaded the bundle: version code {code}, sha256 {bundle.get('sha256', '?')}")
    if mapping_path:
        # Without it every crash in Android vitals and the pre-launch report reads q90.a().
        edits.deobfuscationfiles().upload(
            packageName=PACKAGE,
            editId=edit_id,
            apkVersionCode=code,
            deobfuscationFileType="proguard",
            media_body=MediaFileUpload(mapping_path, mimetype="application/octet-stream", resumable=True),
        ).execute(num_retries=3)
        print("Uploaded the R8 mapping file")
    return code


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)

    notes = commands.add_parser("notes", help="what changed since the version on the track")
    notes.add_argument("--track", choices=TRACKS, default="production")
    notes.add_argument("--out", default="build/release")
    notes.add_argument("--base-code", type=int, help="measure from this version code instead of asking Play")
    notes.add_argument("--version-code", type=int, help="a bundle Play already has, to promote instead of main")
    notes.set_defaults(run=notes_command)

    publish = commands.add_parser("publish", help="release a bundle on a track, uploading it first if new")
    publish.add_argument("--track", choices=TRACKS, required=True)
    publish.add_argument("--bundle", help="the .aab to upload")
    publish.add_argument("--mapping")
    publish.add_argument("--version-code", type=int, help="a bundle Play already has, released without uploading")
    publish.add_argument("--name", help="the release's name in the Console; default versionName (code)")
    publish.add_argument("--notes", required=True, help="file holding the What's new text")
    publish.add_argument("--rollout", type=float, default=100, help="percent of the track's users")
    publish.set_defaults(run=publish_command)

    args = parser.parse_args(argv)
    try:
        args.run(args)
    except ReleaseError as error:
        print(f"::error::{error}" if os.environ.get("GITHUB_ACTIONS") else f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
