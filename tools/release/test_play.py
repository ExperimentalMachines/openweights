"""The release script's decisions that run before anything reaches Play.

    python3 -m unittest tools/release/test_play.py

Nothing here talks to Play or to Claude. What is covered is what those calls are given: the
commit a version code names, the text that goes out as the release notes, and the track body.
"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import play  # noqa: E402


class VersionCodeToCommitTest(unittest.TestCase):
    """A repository shaped like main: a line of commits with one merged branch in it."""

    @classmethod
    def setUpClass(cls):
        cls.dir = tempfile.TemporaryDirectory()
        cls.root = Path(cls.dir.name)
        cls.saved_root, play.ROOT = play.ROOT, cls.root

        def run(*args):
            return subprocess.run(
                ["git", *args], cwd=cls.root, check=True, capture_output=True, text=True,
            ).stdout.strip()

        def commit(path, message):
            (cls.root / path).parent.mkdir(parents=True, exist_ok=True)
            (cls.root / path).write_text(message)
            run("add", path)
            run("commit", "-q", "-m", message)
            return run("rev-parse", "HEAD")

        run("init", "-q", "-b", "main")
        run("config", "user.email", "test@example.com")
        run("config", "user.name", "test")
        cls.c1 = commit("app/a.kt", "one")
        cls.c2 = commit("docs/research/b.md", "two")
        cls.c3 = commit("core/engine/src/c.kt", "three")
        run("checkout", "-q", "-b", "side")
        commit("tools/eval/d.py", "side one")
        commit("tools/eval/e.py", "side two")
        commit("tools/eval/f.py", "side three")
        run("checkout", "-q", "main")
        run("merge", "-q", "--no-ff", "-m", "merge", "side")
        cls.merge = run("rev-parse", "HEAD")
        cls.c4 = commit("app/g.kt", "four\n\nWhy it changed.\n\nCo-Authored-By: Someone <someone@example.com>")
        cls.c5 = commit("app/h.kt", "five")

    @classmethod
    def tearDownClass(cls):
        play.ROOT = cls.saved_root
        cls.dir.cleanup()

    def test_a_code_on_main_names_its_own_commit(self):
        self.assertEqual(play.commit_count(), 9)
        self.assertEqual(play.commit_for_code(9), (self.c5, True))
        self.assertEqual(play.commit_for_code(8), (self.c4, True))
        self.assertEqual(play.commit_for_code(7), (self.merge, True))
        self.assertEqual(play.commit_for_code(1), (self.c1, True))

    def test_a_code_inside_a_merge_falls_back_to_the_commit_before_it(self):
        # 4 to 6 were only ever counted on the side branch; the notes then start earlier,
        # listing more changes rather than fewer.
        for code in (4, 5, 6):
            self.assertEqual(play.commit_for_code(code), (self.c3, False))

    def test_a_code_older_than_the_repository_is_refused(self):
        with self.assertRaises(play.ReleaseError):
            play.commit_for_code(0)

    def test_the_changes_are_every_commit_after_the_live_one_without_the_merge(self):
        commits = play.commits_between(self.c3)
        self.assertEqual([c.subject for c in commits], ["five", "four", "side three", "side two", "side one"])
        self.assertEqual(commits[0].areas, ["app"])
        self.assertEqual(commits[1].body, "Why it changed.")
        self.assertEqual(commits[-1].areas, ["tools/eval"])


class TracksTest(unittest.TestCase):
    TRACKS = {
        "production": play.TrackState("production", [
            {"status": "completed", "versionCodes": ["615"]},
            {"status": "inProgress", "versionCodes": ["620"], "userFraction": 0.2},
        ]),
        "internal": play.TrackState("internal", [{"status": "completed", "versionCodes": ["630"]}]),
        "alpha": play.TrackState("alpha", [{"status": "draft", "versionCodes": ["633"]}]),
    }

    def test_the_notes_start_from_what_everyone_on_the_track_has(self):
        self.assertEqual(play.live_code(self.TRACKS, "production"), 615)
        self.assertEqual(play.live_code(self.TRACKS, "internal"), 630)

    def test_a_track_with_nothing_live_is_measured_from_production(self):
        self.assertEqual(play.live_code(self.TRACKS, "alpha"), 615)
        self.assertEqual(play.live_code(self.TRACKS, "beta"), 615)

    def test_a_staged_rollout_counts_only_when_nothing_completed(self):
        tracks = {"production": play.TrackState("production", [{"status": "inProgress", "versionCodes": ["620"]}])}
        self.assertEqual(play.live_code(tracks, "production"), 620)
        self.assertIsNone(play.live_code({}, "production"))

    def test_a_new_bundle_must_beat_every_code_drafts_included(self):
        self.assertEqual(play.highest_code(self.TRACKS), 633)
        self.assertEqual(play.highest_code({}), 0)


class ReleaseBodyTest(unittest.TestCase):
    def test_a_full_release_completes(self):
        body = play.release_body("production", 639, "2.0.0 (639)", "• Faster.", 100)
        release = body["releases"][0]
        self.assertEqual(body["track"], "production")
        self.assertEqual(release["status"], "completed")
        self.assertEqual(release["versionCodes"], ["639"])
        self.assertEqual(release["releaseNotes"], [{"language": "en-US", "text": "• Faster."}])
        self.assertNotIn("userFraction", release)

    def test_a_partial_release_is_a_staged_rollout(self):
        release = play.release_body("production", 639, "n", "t", 20)["releases"][0]
        self.assertEqual(release["status"], "inProgress")
        self.assertEqual(release["userFraction"], 0.2)

    def test_impossible_rollouts_are_refused(self):
        for track, rollout in (("internal", 50), ("production", 0), ("production", 101)):
            with self.assertRaises(play.ReleaseError):
                play.release_body(track, 639, "n", "t", rollout)


class NotesTest(unittest.TestCase):
    def test_dashes_become_the_house_style(self):
        self.assertEqual(play.clean_notes("• Faster replies \u2014 up to 3\u20135 times"), "• Faster replies, up to 3 to 5 times")
        self.assertEqual(play.clean_notes("  • One  \r\n•   Two \n"), "• One\n• Two")

    def test_play_limit_is_enforced(self):
        self.assertEqual(play.check_notes("x" * 500), "x" * 500)
        with self.assertRaises(play.ReleaseError):
            play.check_notes("x" * 501)
        with self.assertRaises(play.ReleaseError):
            play.check_notes("  \n ")

    def test_the_prompt_carries_each_commit_and_what_it_touched(self):
        commits = [play.Commit("a" * 40, "chat: a fix", "Measured on the Poco." * 200, ["app", "core/engine"])]
        prompt = play.commits_prompt(commits, 615, 639)
        self.assertIn("Users have version 615. This release is version 639.", prompt)
        self.assertIn("## chat: a fix\nareas: app, core/engine", prompt)
        self.assertTrue(prompt.rstrip().endswith("[...]"))


if __name__ == "__main__":
    unittest.main()
