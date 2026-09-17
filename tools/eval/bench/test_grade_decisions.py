"""The grader's alias matching, on the cases that decide whether a reply is credited.

    python3 -m unittest tools/eval/bench/test_grade_decisions.py
"""
import sys, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from grade_decisions import contains, correct, normalize  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
