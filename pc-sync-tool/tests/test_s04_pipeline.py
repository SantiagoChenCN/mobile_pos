from __future__ import annotations

import sys
import unittest
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from ms2011_query_catalog import QueryId
from ms2011_raw_models import FULL_READ_QUERY_IDS
from ms2011_reader import DeterministicMs2011Reader
from ms2011_rows import build_rows
from promotion_candidate_extractor import extract_promotion_candidates
from promotion_raw_models import CandidateStatus
from read_only_ms2011_session import ReadOnlyMs2011Session
from snapshot_normalizer import normalize_products


class S04PipelineTest(unittest.TestCase):
    def test_fixture_double_read_normalizes_products_and_only_extracts_unverified_evidence(self):
        source = build_rows()
        batch = DeterministicMs2011Reader(
            ReadOnlyMs2011Session(lambda query_id, parameters: source[query_id])
        ).read_double()
        normalized = normalize_products(
            batch.rows(QueryId.PRODUCTS),
            batch.rows(QueryId.CATEGORIES),
            batch.rows(QueryId.UNITS),
        )
        promotion = extract_promotion_candidates(
            batch.rows(QueryId.PRODUCTS),
            {query_id: batch.rows(query_id) for query_id in FULL_READ_QUERY_IDS},
            datetime(2026, 7, 17, 12),
        )
        self.assertFalse(normalized.rejected)
        self.assertEqual(("ms2011:101", "ms2011:202"), tuple(item.source_product_key for item in normalized.products))
        self.assertTrue(batch.double_read_matched)
        self.assertTrue(all(item.status is CandidateStatus.UNVERIFIED for item in promotion.candidates))
        self.assertEqual((), promotion.normalized_rules)


if __name__ == "__main__":
    unittest.main()
