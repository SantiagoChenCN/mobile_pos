from __future__ import annotations

import copy
import sys
import unittest
from datetime import datetime
from decimal import Decimal
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from ms2011_query_catalog import QueryId, query_catalog
from ms2011_reader import DeterministicMs2011Reader, RawReadError, SourceChangedError
from ms2011_raw_models import FULL_READ_QUERY_IDS
from ms2011_rows import build_rows
from read_only_ms2011_session import ReadOnlyMs2011Session


class Ms2011ReaderTest(unittest.TestCase):
    def test_double_read_is_deterministic_and_preserves_source_types(self):
        rows = build_rows()
        calls = []
        reader = DeterministicMs2011Reader(
            ReadOnlyMs2011Session(lambda query_id, parameters: calls.append(query_id) or rows[query_id])
        )
        result = reader.read_double()
        self.assertTrue(result.double_read_matched)
        self.assertEqual(len(FULL_READ_QUERY_IDS) * 2, len(result.metrics))
        product = result.rows(QueryId.PRODUCTS)[0]
        self.assertIsInstance(product["sale_price"], Decimal)
        self.assertIsInstance(product["updated_at"], datetime)
        self.assertEqual(list(FULL_READ_QUERY_IDS) * 2, calls)
        for metric in result.metrics:
            self.assertGreaterEqual(metric.elapsed_ms, 0)
            self.assertNotIn("rows", metric.__dict__)

    def test_all_full_queries_are_fixed_non_low_cost_selects_with_order(self):
        for query_id in FULL_READ_QUERY_IDS:
            spec = query_catalog()[query_id]
            self.assertFalse(spec.low_cost)
            self.assertTrue(spec.sql.startswith("SELECT "))
            self.assertIn("ORDER BY", spec.sql)

    def test_duplicate_key_unsorted_rows_and_missing_relation_fail_closed(self):
        mutations = []
        duplicate = build_rows()
        duplicate[QueryId.PRODUCTS] += (dict(duplicate[QueryId.PRODUCTS][0]),)
        mutations.append((duplicate, "DUPLICATE_PRIMARY_KEY"))
        unsorted = build_rows()
        unsorted[QueryId.PRODUCTS] = tuple(reversed(unsorted[QueryId.PRODUCTS]))
        mutations.append((unsorted, "UNSTABLE_SORT_ORDER"))
        missing = build_rows()
        missing[QueryId.PRODUCTS][0]["category_code"] = "MISSING"
        mutations.append((missing, "MISSING_CATEGORY_RELATION"))
        relation = build_rows()
        relation[QueryId.QUANTITY_FIXED_DETAILS][0]["master_id"] = 999
        mutations.append((relation, "MISSING_MASTER_RELATION"))
        for rows, reason in mutations:
            with self.subTest(reason=reason):
                reader = DeterministicMs2011Reader(
                    ReadOnlyMs2011Session(lambda query_id, parameters, rows=rows: rows[query_id])
                )
                with self.assertRaises(RawReadError) as captured:
                    reader.read_double()
                self.assertEqual(reason, captured.exception.reason_code)

    def test_any_query_failure_returns_no_partial_batch(self):
        rows = build_rows()

        def runner(query_id, parameters):
            if query_id is QueryId.QUANTITY_PERCENT_DETAILS:
                raise TimeoutError("fixture timeout")
            return rows[query_id]

        with self.assertRaises(RawReadError) as captured:
            DeterministicMs2011Reader(ReadOnlyMs2011Session(runner)).read_double()
        self.assertEqual("TABLE_READ_FAILED", captured.exception.reason_code)
        self.assertEqual(QueryId.QUANTITY_PERCENT_DETAILS, captured.exception.query_id)

    def test_second_read_change_is_rejected(self):
        rows = build_rows()
        call_count = {query_id: 0 for query_id in FULL_READ_QUERY_IDS}

        def runner(query_id, parameters):
            call_count[query_id] += 1
            current = copy.deepcopy(rows[query_id])
            if query_id is QueryId.PRODUCTS and call_count[query_id] == 2:
                current[0]["sale_price"] = Decimal("20.6000")
            return current

        with self.assertRaises(SourceChangedError) as captured:
            DeterministicMs2011Reader(ReadOnlyMs2011Session(runner)).read_double()
        self.assertEqual("DOUBLE_READ_MISMATCH", captured.exception.reason_code)


if __name__ == "__main__":
    unittest.main()
