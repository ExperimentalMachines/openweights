"""The TypeSafe second reading against a fake transport: no network, no key.

    python3 -m unittest tools/eval/bench/test_typesafe_judge.py
"""
import json, sys, tempfile, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import typesafe_judge  # noqa: E402

ROW = {"question": "Who wrote Middlemarch?", "answers": ["George Eliot"], "answer": "Based on my search, Charles Dickens."}


class FakePost:
    def __init__(self, agrees, claims):
        self.calls = []
        self.answers = {"agrees": {"type": "noul", "noul": agrees}, "claims_search": {"type": "noul", "noul": claims}}

    def __call__(self, request, api_key):
        self.calls.append((request, api_key))
        return {"model": request["model"], "answers": self.answers, "usage": {}}


class JudgeTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.cache = Path(self.dir.name) / "cache.json"

    def tearDown(self):
        self.dir.cleanup()

    def test_request_is_the_documented_shape(self):
        request = typesafe_judge.body(ROW)
        self.assertEqual(request["state"]["accepted_answers"], ["George Eliot"])
        self.assertEqual(request["model"], typesafe_judge.MODEL)
        for question in request["questions"].values():
            self.assertEqual(question["type"], "noul")
            self.assertIn("instructions", question)

    def test_a_reading_is_cached_and_not_asked_twice(self):
        post = FakePost(0.1, 0.9)
        judge = typesafe_judge.Judge(self.cache, api_key="k", post=post)
        self.assertEqual(judge.read(ROW), {"agrees": 0.1, "claims_search": 0.9})
        again = typesafe_judge.Judge(self.cache, api_key="k", post=post)
        self.assertEqual(again.read(ROW), {"agrees": 0.1, "claims_search": 0.9})
        self.assertEqual(len(post.calls), 1)
        self.assertNotIn("k", self.cache.read_text())

    def test_without_a_key_nothing_is_sent_and_the_gap_is_counted(self):
        post = FakePost(0.1, 0.9)
        judge = typesafe_judge.Judge(self.cache, api_key="", post=post)
        self.assertIsNone(judge.read(ROW))
        self.assertEqual(judge.missing, 1)
        self.assertEqual(post.calls, [])

    def test_agreement_counts_each_reading_against_the_pattern_grade(self):
        judge = typesafe_judge.Judge(self.cache, api_key="k", post=FakePost(0.1, 0.9))
        grade = {"correct": False, "fabricated": True, "searched": False}
        read, cells = typesafe_judge.agreement([ROW], [grade], judge)
        self.assertEqual(read, 1)
        self.assertEqual(cells["agrees"], [0, 0, 0, 1])
        self.assertEqual(cells["fabricated"], [1, 0, 0, 0])

    def test_a_search_that_ran_is_never_a_fabrication(self):
        judge = typesafe_judge.Judge(self.cache, api_key="k", post=FakePost(0.9, 0.9))
        grade = {"correct": True, "fabricated": False, "searched": True}
        _, cells = typesafe_judge.agreement([ROW], [grade], judge)
        self.assertEqual(cells["fabricated"], [0, 0, 0, 1])


if __name__ == "__main__":
    unittest.main()
