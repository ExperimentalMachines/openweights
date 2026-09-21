"""The grader's alias matching, on the cases that decide whether a reply is credited.

    python3 -m unittest tools/eval/bench/test_grade_decisions.py
"""
import json, sys, tempfile, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from grade_decisions import contains, correct, load, normalize  # noqa: E402


class AliasTest(unittest.TestCase):
    def test_an_alias_inside_a_word_is_not_credited(self):
        # The phone rows that found the defect.
        self.assertFalse(correct("Zec Petawaga is located in the Democratic Republic of the Congo.", ["CA", "ca"]))
        self.assertFalse(correct("Monitor House is located in the United Kingdom, specifically London.", ["US", "U.S."]))
        self.assertFalse(correct("Columbia is not a capital; Bogota serves as its capital city.", ["S. C."]))
        self.assertFalse(correct("Sheep with eight teeth are aged, typically over 2024 standards here.", ["2"]))

    def test_whole_words_are_still_credited(self):
        self.assertTrue(correct("Zec Petawaga is in Canada, in the province of Quebec.", ["Canada", "CA"]))
        self.assertTrue(correct("The USS Constitution is a ship of the US Navy.", ["US"]))
        self.assertTrue(correct("Columbia is the capital of South Carolina.", ["South Carolina", "SC"]))

    def test_numbers_decimals_and_dates(self):
        self.assertTrue(correct("A full-mouth sheep has 2 teeth left to grow, the answer is 2.", ["2"]))
        self.assertTrue(correct("The answer is 3.5 percent according to the report.", ["3.5"]))
        self.assertFalse(correct("The answer is 13.5 percent according to the report.", ["3.5"]))
        self.assertTrue(correct("It was declared on 2023-05-05 by the WHO.", ["2023-05-05"]))

    def test_punctuated_aliases_normalise_the_same_way_on_both_sides(self):
        self.assertTrue(correct("Jean-Luc Godard directed the film in 1960.", ["Jean-Luc Godard"]))
        self.assertTrue(correct("The company later became AT&T Corporation.", ["AT&T"]))
        self.assertTrue(correct("It is written in C++ for speed, mostly.", ["C++"]))

    def test_scripts_without_spaces_keep_containment(self):
        self.assertTrue(contains(normalize("答えは東京都です"), normalize("東京")))
        self.assertTrue(contains(normalize("เมืองหลวงคือกรุงเทพมหานคร"), normalize("กรุงเทพ")))

    def test_korean_is_spaced_and_matched_as_words(self):
        self.assertTrue(contains(normalize("수도는 서울 입니다"), normalize("서울")))
        self.assertFalse(contains(normalize("수도는 서울특별시 입니다"), normalize("서울")))

    def test_short_replies_still_need_to_be_the_alias(self):
        self.assertTrue(correct("Canada", ["Canada"]))
        self.assertFalse(correct("Congo", ["Canada"]))





class PhoneLabelTest(unittest.TestCase):
    """Which phone a run is filed under.

    The QDC Snapdragon 8 Elite writes unprefixed files, and the old fallback called every
    unprefixed file "poco", relabelling eighty graded rows as a phone they never ran on.
    The header identifies unprefixed runs; explicit run prefixes remain authoritative.
    """

    def write(self, directory, name, soc):
        directory.mkdir(parents=True, exist_ok=True)
        header = {"header": True, "model": "M", "arm": "driven-search", "soc": soc,
                  "tools": ["web_search"]}
        row = {"id": "q1", "need": True, "searched": True, "reply": "x",
               "aliases": ["x"], "seconds": 1.0}
        (directory / name).write_text(json.dumps(header) + "\n" + json.dumps(row) + "\n")

    def phone_for(self, name, soc):
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            self.write(d, name, soc)
            (phone, _, _), _ = next(iter(load(d).items()))
            return phone

    def test_an_unprefixed_file_is_named_by_the_chip_it_reported(self):
        self.assertEqual(self.phone_for("decisions-M-driven-search.jsonl", "SM8750"), "elite")

    def test_an_explicit_prefix_still_wins(self):
        self.assertEqual(self.phone_for("tensor-decisions-M-driven-search.jsonl", "SM8750"), "tensor")

    def test_an_unrecorded_or_unknown_chip_still_falls_back(self):
        self.assertEqual(self.phone_for("decisions-M-driven-search.jsonl", ""), "poco")
        self.assertEqual(self.phone_for("decisions-M-driven-search.jsonl", "SM9999"), "poco")

    def test_void_window_capture_is_not_a_quality_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            self.write(directory, "VOID-window-too-small-poco-decisions-M-driven-full.jsonl", "MT6991")
            self.write(directory, "poco-decisions-M-driven-search.jsonl", "MT6991")
            self.assertEqual(list(load(directory)), [("poco", "M", "driven-search")])


if __name__ == "__main__":
    unittest.main()
