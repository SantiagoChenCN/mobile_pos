from __future__ import annotations

import json
import sqlite3
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from normalized_promotion_rule import NormalizedPromotionRule


FIXTURES = Path(__file__).resolve().parent / "fixtures"
ANDROID_FIXTURES = ROOT.parent / "android-emergency-pos" / "core" / "src" / "test" / "resources" / "fixtures"


class SqliteV2ContractTest(unittest.TestCase):
    def test_schema_has_exact_tables_text_decimals_and_valid_foreign_keys(self):
        schema_path = FIXTURES / "sqlite_v2_schema.sql"
        schema = schema_path.read_text(encoding="utf-8")
        self.assertNotRegex(schema.upper(), r"\bREAL\b")
        connection = sqlite3.connect(":memory:")
        try:
            connection.executescript(schema)
            tables = {
                row[0]
                for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")
            }
            self.assertEqual(
                {
                    "sync_metadata", "products", "categories", "units",
                    "promotion_candidates", "promotion_candidate_products",
                    "promotion_raw_rows", "promotion_rules", "promotion_rule_tiers",
                    "promotion_rule_schedules", "promotion_rule_groups", "validation_issues",
                },
                tables,
            )
            connection.execute("INSERT INTO categories VALUES ('A', 'Almacen', 0)")
            connection.execute("INSERT INTO units VALUES ('UN', 'Unidad', 0)")
            connection.execute(
                "INSERT INTO products VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ("ms2011:7318", "7318", "7790000000000", None, "Fixture", "A", "UN",
                 "2099.9900", "2099.99", None, None, None, None, 0, None),
            )
            connection.execute(
                "INSERT INTO promotion_candidates VALUES (?,?,?,?,?,?,?,?)",
                ("pc-1f03e4ee190b6df903dc7ddf", "MS_GOODLIST", "7318", "UNVERIFIED", None, None, None, None),
            )
            connection.execute(
                "INSERT INTO promotion_raw_rows VALUES (?,?,?,?,?,?,?)",
                ("raw-1", "pc-1f03e4ee190b6df903dc7ddf", "MS_GOODLIST", "7318", 0,
                 '{"GHuiPrice":"2099.9900"}', '{"GHuiPrice":"2099.99"}'),
            )
            self.assertEqual("ok", connection.execute("PRAGMA integrity_check").fetchone()[0])
            self.assertEqual([], connection.execute("PRAGMA foreign_key_check").fetchall())
        finally:
            connection.close()

    def test_normalized_rule_fixture_is_generic_and_weekday_unfrozen(self):
        fixture = json.loads((FIXTURES / "v2_normalized_rule.json").read_text(encoding="utf-8"))
        rule = NormalizedPromotionRule.from_mapping(fixture)
        self.assertEqual("pc-1f03e4ee190b6df903dc7ddf", rule.candidate_id)
        self.assertEqual((), rule.schedules)

    def test_shared_sqlite_fixtures_are_byte_identical(self):
        for name in ("sqlite_v2_schema.sql", "v2_normalized_rule.json"):
            self.assertEqual((FIXTURES / name).read_bytes(), (ANDROID_FIXTURES / name).read_bytes())


if __name__ == "__main__":
    unittest.main()
