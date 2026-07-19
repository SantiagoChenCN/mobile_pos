from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from decimal_value import DecimalKind, DecimalValue


FIXTURE = Path(__file__).resolve().parent / "fixtures" / "v2_decimal_cases.json"
ANDROID_FIXTURE = (
    ROOT.parent
    / "android-emergency-pos"
    / "core"
    / "src"
    / "test"
    / "resources"
    / "fixtures"
    / "v2_decimal_cases.json"
)


class DecimalValueTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_shared_fixture_is_byte_for_byte_identical(self):
        self.assertEqual(FIXTURE.read_bytes(), ANDROID_FIXTURE.read_bytes())

    def test_valid_cases_use_plain_canonical_text(self):
        for case in self.cases["valid"]:
            with self.subTest(case=case):
                value = DecimalValue.parse(case["raw"], case["kind"])
                self.assertEqual(case["raw"], value.raw_text)
                self.assertEqual(case["canonical"], value.canonical_text)
                self.assertNotIn("e", value.canonical_text.lower())

    def test_invalid_cases_fail_closed(self):
        for case in self.cases["invalid"]:
            with self.subTest(case=case):
                with self.assertRaises(ValueError):
                    DecimalValue.parse(case["raw"], case["kind"])

    def test_exact_multiplication_and_scale_independent_comparison(self):
        case = self.cases["multiplication"]
        left = DecimalValue.parse(case["left"], case["leftKind"])
        right = DecimalValue.parse(case["right"], case["rightKind"])
        product = left.multiply_exact(right, case["resultKind"])
        self.assertEqual(case["canonical"], product.canonical_text)

        one = DecimalValue.parse("1", DecimalKind.QUANTITY)
        one_scaled = DecimalValue.parse("1.0000", DecimalKind.QUANTITY)
        self.assertEqual(0, one.compare_to(one_scaled))


if __name__ == "__main__":
    unittest.main()
