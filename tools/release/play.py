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
are the release. The user-facing text is drafted from them by Claude when ANTHROPIC_API_KEY
is set, or taken verbatim from WHATS_NEW; either way it is shown for approval before
`publish` sends it.
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


def area(path: str) -> str:
    """The part of the tree a file belongs to, coarse enough to tell app code from research."""
    parts = path.split("/")
    if parts[0] in ("core", "docs", "tools") and len(parts) > 2:
        return "/".join(parts[:2])
    return parts[0]


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


def version_name() -> str:
    text = (ROOT / "app" / "build.gradle.kts").read_text()
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

    A completed release is what everyone on the track was given. A staged rollout, or a halted
    one, reached only some of them, so it is used only when nothing on the track completed.
    Production stands in for a testing track that has never had a release.
    """
    for name in (track, "production"):
        state = tracks.get(name)
        if not state:
            continue
        for statuses in (("completed",), ("inProgress", "halted")):
            codes = state.codes(statuses)
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

SYSTEM = """You write the "What's new" text for OpenWeights on Google Play.

OpenWeights is an Android app that runs open-weight language models entirely on the phone:
GGUF files through llama.cpp and compiled .pte exports through ExecuTorch. No account, no
telemetry, no cloud; the only things that leave the phone are model downloads from Hugging
Face and the assistant's web tools, each of which the user can switch off.

You are given every commit since the version users have now. Most commits in this repository
are research, benchmarks, evaluation tooling, documentation or build work that changes nothing
a user can see. Write only about what a person using the app would notice: a new feature, a
fixed bug, something faster or lighter, a model that now works. A commit that adds code which
ships switched off, or only for developers or a test harness, is not a change for users. Its
"areas" say which part of the tree it touched: app and core/* are the app; docs, tools, eval
and play are not shipped; build-logic, gradle and .github are the build.

Rules for the text:
- At most 450 characters in total. Play refuses more than 500.
- One change per line, each line starting with "• ". Three to five lines is typical.
- Plain words a user would use. No class names, file paths, commit hashes or version codes.
- A number only if a commit measured it on a phone, and only as the commit states it.
- No em dashes or en dashes; use a comma, a colon or a full stop. No emoji, no exclamation
  marks, no marketing adjectives.
- If nothing user-visible changed, write one line: "• Fixes and improvements under the hood."

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


def draft_notes(commits: list[Commit], base_code: int, head_code: int) -> str:
    import anthropic

    client = anthropic.Anthropic()
    prompt = commits_prompt(commits, base_code, head_code)
    for _attempt in range(2):
        response = client.beta.messages.create(
            model="claude-opus-5",
            max_tokens=16000,
            system=SYSTEM,
            messages=[{"role": "user", "content": prompt}],
            # A declined request is re-run on Anthropic's recommended fallback model instead of
            # coming back empty. A commit log is not the kind of text that is declined, but a
            # release blocked on it would be a strange way to find out.
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
        )
        if response.stop_reason == "refusal":
            raise ReleaseError("Claude declined to draft the notes. Run again with the text in whats_new.")
        text = clean_notes("".join(block.text for block in response.content if block.type == "text"))
        if text and len(text) <= NOTES_LIMIT:
            return text
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
    head_code = commit_count()
    if args.base_code is not None:
        base_code, top = args.base_code, args.base_code
    else:
        tracks = read_tracks(play_service())
        base_code, top = live_code(tracks, args.track), highest_code(tracks)
        if base_code is None:
            raise ReleaseError("Play has no release on any track to compare with. Give the text in whats_new.")
        if head_code <= top:
            raise ReleaseError(
                f"main is at version code {head_code} and Play already has {top}. Play refuses a "
                "code it has seen; release a newer commit.",
            )

    base, exact = commit_for_code(base_code)
    commits = commits_between(base)
    if not commits:
        raise ReleaseError(f"Nothing has changed since version {base_code}.")

    # A workflow_dispatch text box is one line, so a typed \n is the line break.
    given = os.environ.get("WHATS_NEW", "").replace("\\n", "\n").strip()
    if given:
        text, source = check_notes(given), "given in whats_new"
    elif os.environ.get("ANTHROPIC_API_KEY") or args.draft:
        text, source = check_notes(draft_notes(commits, base_code, head_code)), "drafted by Claude from the commits"
    else:
        raise ReleaseError("No whats_new was given and there is no ANTHROPIC_API_KEY to draft one with.")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "whats-new.txt").write_text(text + "\n")
    head = git("rev-parse", "HEAD")
    listing = "\n".join(
        f"- [`{c.sha[:8]}`]({REPO_URL}/commit/{c.sha}) {c.subject}" for c in commits
    )
    near = "" if exact else " (the nearest commit on main; that code was built inside a merge)"
    changes = (
        f"## Release {version_name()} ({head_code}) to {args.track}\n\n"
        f"Built from [`{head[:8]}`]({REPO_URL}/commit/{head}). The track's users have version "
        f"{base_code}, built from [`{base[:8]}`]({REPO_URL}/commit/{base}){near}.\n\n"
        f"### What's new, {len(text)} of {NOTES_LIMIT} characters, {source}\n\n"
        f"```\n{text}\n```\n\n"
        f"### The {len(commits)} commits since version {base_code}\n\n{listing}\n"
    )
    (out / "changes.md").write_text(changes)
    summary(changes)
    output(version_code=head_code, base_code=base_code)


def publish_command(args: argparse.Namespace) -> None:
    from googleapiclient.http import MediaFileUpload

    notes = check_notes(Path(args.notes).read_text())
    service = play_service()
    edits = service.edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute(num_retries=3)["id"]
    try:
        bundle = edits.bundles().upload(
            packageName=PACKAGE,
            editId=edit_id,
            media_body=MediaFileUpload(args.bundle, mimetype="application/octet-stream", resumable=True),
        ).execute(num_retries=3)
        code = int(bundle["versionCode"])
        print(f"Uploaded the bundle: version code {code}, sha256 {bundle.get('sha256', '?')}")
        if args.mapping:
            # Without it every crash in Android vitals and the pre-launch report reads q90.a().
            edits.deobfuscationfiles().upload(
                packageName=PACKAGE,
                editId=edit_id,
                apkVersionCode=code,
                deobfuscationFileType="proguard",
                media_body=MediaFileUpload(args.mapping, mimetype="application/octet-stream", resumable=True),
            ).execute(num_retries=3)
            print("Uploaded the R8 mapping file")
        body = release_body(args.track, code, f"{version_name()} ({code})", notes, args.rollout)
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
    summary(f"## Released {version_name()} ({code}) to {args.track}{rollout}\n\n```\n{notes}\n```\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)

    notes = commands.add_parser("notes", help="what changed since the version on the track")
    notes.add_argument("--track", choices=TRACKS, default="production")
    notes.add_argument("--out", default="build/release")
    notes.add_argument("--base-code", type=int, help="measure from this version code instead of asking Play")
    notes.add_argument("--draft", action="store_true", help="draft with Claude even without ANTHROPIC_API_KEY set")
    notes.set_defaults(run=notes_command)

    publish = commands.add_parser("publish", help="upload a bundle and release it on a track")
    publish.add_argument("--track", choices=TRACKS, required=True)
    publish.add_argument("--bundle", required=True)
    publish.add_argument("--mapping")
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
