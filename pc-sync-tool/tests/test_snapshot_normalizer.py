from __future__ import annotations

import copy
import sys
import unittest
from decimal import Decimal
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from ms2011_query_catalog import QueryId
from ms2011_rows import build_rows
from snapshot_normalizer import normalize_products
from snapshot_validator import SEVERITY_MATRIX, Severity, issue


class SnapshotNormalizerTest(unittest.TestCase):
    def _normalize(self, rows=None):
        source = rows or build_rows()
        return normalize_products(
            source[QueryId.PRODUCTS], source[QueryId.CATEGORIES], source[QueryId.UNITS]
        )

    def test_identity_uses_gid_barcode_change_does_not_change_identity(self):
        first = self._normalize()
        changed = build_rows()
        changed[QueryId.PRODUCTS][0]["barcode"] = "NEW-BARCODE"
        second = self._normalize(changed)
        self.assertEqual("ms2011:101", first.products[0].source_product_key)
        self.assertEqual(first.products[0].source_product_key, second.products[0].source_product_key)

    def test_null_unit_is_preserved_stop_flag_is_raw_and_nonzero_is_unsellable(self):
        result = self._normalize()
        stopped = result.products[1]
        self.assertIsNone(stopped.unit_code)
        self.assertEqual(1, stopped.stop_flag)
        self.assertFalse(stopped.sellable)
        self.assertIn("MISSING_UNIT", [item.code for item in result.issues])

    def test_decimal_is_canonical_and_simple_fields_remain_evidence_only(self):
        product = self._normalize().products[0]
        self.assertEqual("20.5", product.sale_price_decimal)
        self.assertEqual("18", product.simple_price_decimal)
        self.assertEqual("2", product.simple_threshold_decimal)
        self.assertTrue(product.simple_evidence_only)
        self.assertFalse(hasattr(product, "normalized_rule"))

    def test_duplicate_key_and_barcode_use_fixed_rejection_matrix(self):
        duplicate_key = build_rows()
        duplicate_key[QueryId.PRODUCTS] += (copy.deepcopy(duplicate_key[QueryId.PRODUCTS][0]),)
        key_result = self._normalize(duplicate_key)
        self.assertTrue(key_result.rejected)
        self.assertIn("DUPLICATE_PRODUCT_KEY", [item.code for item in key_result.issues])

        duplicate_barcode = build_rows()
        duplicate_barcode[QueryId.PRODUCTS][1]["barcode"] = duplicate_barcode[QueryId.PRODUCTS][0]["barcode"]
        barcode_result = self._normalize(duplicate_barcode)
        self.assertTrue(barcode_result.rejected)
        barcode_issue = next(item for item in barcode_result.issues if item.code == "DUPLICATE_BARCODE")
        self.assertEqual(Severity.ERROR, barcode_issue.severity)
        self.assertTrue(barcode_issue.rejects_snapshot)

    def test_missing_relations_and_empty_barcode_have_explicit_nonfatal_severity(self):
        source = build_rows()
        source[QueryId.PRODUCTS][0]["barcode"] = ""
        source[QueryId.PRODUCTS][0]["category_code"] = "UNKNOWN"
        result = self._normalize(source)
        by_code = {item.code: item for item in result.issues}
        self.assertEqual(Severity.WARNING, by_code["EMPTY_BARCODE"].severity)
        self.assertEqual(Severity.WARNING, by_code["MISSING_CATEGORY"].severity)
        self.assertFalse(by_code["EMPTY_BARCODE"].rejects_snapshot)

    def test_unknown_issue_code_and_matrix_mutation_are_rejected(self):
        with self.assertRaises(ValueError):
            issue("RUNTIME_GUESS", "ms2011:101")
        with self.assertRaises(TypeError):
            SEVERITY_MATRIX["RUNTIME_GUESS"] = SEVERITY_MATRIX["EMPTY_BARCODE"]


if __name__ == "__main__":
    unittest.main()
