from __future__ import annotations

import copy
import sys
import unittest
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from ms2011_query_catalog import QueryId
from ms2011_rows import build_rows
from promotion_candidate_extractor import extract_promotion_candidates
from promotion_raw_models import CandidateStatus
from promotion_raw_rows import promotion_rows


AS_OF = datetime(2026, 7, 17, 12, 0, 0)


class PromotionCandidateExtractorTest(unittest.TestCase):
    def _extract(self, rows=None):
        source = build_rows()
        return extract_promotion_candidates(
            source[QueryId.PRODUCTS], rows or promotion_rows(), AS_OF
        )

    def test_extracts_four_candidate_types_but_never_outputs_rules(self):
        result = self._extract()
        self.assertEqual(
            {
                "PRODUCT_SIMPLE",
                "QUANTITY_PERCENT",
                "QUANTITY_FIXED_TOTAL",
                "MIX_MATCH_FIXED_TOTAL",
            },
            {candidate.candidate_type for candidate in result.candidates},
        )
        self.assertTrue(all(candidate.status is CandidateStatus.UNVERIFIED for candidate in result.candidates))
        self.assertEqual((), result.normalized_rules)
        self.assertTrue(all("formula" not in candidate.raw_fields for candidate in result.candidates))

    def test_simple_candidate_requires_both_positive_fields_and_keeps_raw_decimal_evidence(self):
        result = self._extract()
        simple = next(item for item in result.candidates if item.candidate_type == "PRODUCT_SIMPLE")
        self.assertEqual("18", simple.raw_fields["simple_price_decimal"])
        self.assertEqual("2", simple.raw_fields["simple_threshold_decimal"])
        incomplete = promotion_rows()
        incomplete[QueryId.PRODUCT_SIMPLE_CANDIDATES][0]["simple_threshold"] = None
        result = self._extract(incomplete)
        self.assertNotIn("PRODUCT_SIMPLE", {item.candidate_type for item in result.candidates})
        self.assertIn("INCOMPLETE_SIMPLE_FIELDS", [item.code for item in result.issues])

    def test_duplicate_product_mappings_are_not_collapsed_when_source_keys_differ(self):
        rows = promotion_rows()
        rows[QueryId.PROMOTION_MAPPINGS] += (
            {"xid": 4, "group_code": 9, "source_table": "MS_SALE_CXDAN1", "master_id": 10, "product_id": 101},
        )
        result = self._extract(rows)
        mappings = [
            item for item in result.mappings
            if item.source_table == "MS_CUXIAO_GOOD" and item.product_id == 101
        ]
        self.assertEqual(3, len(mappings))  # percent, mix-match, and repeated percent mapping
        self.assertEqual(3, len({item.mapping_id for item in mappings}))
        self.assertIn("MS_CUXIAO_GOOD:1", {item.source_key for item in mappings})
        self.assertIn("MS_CUXIAO_GOOD:4", {item.source_key for item in mappings})

    def test_dates_weekday_times_groups_and_every_raw_source_key_are_preserved(self):
        result = self._extract()
        schedule = next(
            item for item in result.raw_records if item.source_table == "MS_SALE_WEEKDETAIL1"
        )
        self.assertEqual("1,2", schedule.raw_fields["weekday_raw"])
        self.assertEqual("08:00", schedule.raw_fields["begin_time"])
        mapping = next(item for item in result.mappings if item.source_key == "MS_CUXIAO_GOOD:1")
        self.assertEqual(1, mapping.group_code_raw)
        self.assertEqual(len(result.raw_records), len({item.source_key for item in result.raw_records}))

    def test_unknown_source_missing_master_product_and_detail_become_issues(self):
        rows = promotion_rows()
        rows[QueryId.PROMOTION_MAPPINGS] += (
            {"xid": 4, "group_code": None, "source_table": "UNKNOWN", "master_id": 1, "product_id": 101},
            {"xid": 5, "group_code": None, "source_table": "MS_SALE_CXDAN1", "master_id": 999, "product_id": 101},
            {"xid": 6, "group_code": None, "source_table": "MS_SALE_CXDAN1", "master_id": 10, "product_id": 999},
        )
        rows[QueryId.QUANTITY_FIXED_DETAILS] = ()
        result = self._extract(rows)
        codes = {item.code for item in result.issues}
        self.assertTrue(
            {"UNKNOWN_PROMOTION_SOURCE", "MISSING_PROMOTION_MASTER", "MISSING_PROMOTION_PRODUCT", "MISSING_PROMOTION_DETAIL"}.issubset(codes)
        )

    def test_expired_master_is_inactive_not_guessed_active(self):
        rows = promotion_rows()
        rows[QueryId.MIX_MATCH_MASTERS][0]["end_at"] = datetime(2026, 7, 1)
        result = self._extract(rows)
        candidate = next(item for item in result.candidates if item.candidate_type == "MIX_MATCH_FIXED_TOTAL")
        self.assertEqual(CandidateStatus.INACTIVE, candidate.status)

    def test_timezone_mismatch_is_not_guessed_historical(self):
        rows = promotion_rows()
        rows[QueryId.MIX_MATCH_MASTERS][0]["end_at"] = datetime(2026, 7, 1)
        source = build_rows()
        result = extract_promotion_candidates(
            source[QueryId.PRODUCTS],
            rows,
            datetime(2026, 7, 17, tzinfo=timezone(timedelta(hours=-3))),
        )
        candidate = next(item for item in result.candidates if item.candidate_type == "MIX_MATCH_FIXED_TOTAL")
        self.assertEqual(CandidateStatus.UNVERIFIED, candidate.status)

    def test_duplicate_source_key_is_reported(self):
        rows = promotion_rows()
        rows[QueryId.QUANTITY_PERCENT_DETAILS] += (
            copy.deepcopy(rows[QueryId.QUANTITY_PERCENT_DETAILS][0]),
        )
        result = self._extract(rows)
        self.assertIn("DUPLICATE_PROMOTION_KEY", [item.code for item in result.issues])


if __name__ == "__main__":
    unittest.main()
