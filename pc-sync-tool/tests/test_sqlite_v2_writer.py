from __future__ import annotations

import sqlite3
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from ms2011_query_catalog import QueryId
from ms2011_rows import build_rows
from paths import AppPaths
from promotion_candidate_extractor import extract_promotion_candidates
from snapshot_normalizer import normalize_products
from sqlite_v2_writer import (
    SQLiteV2WriteError,
    SQLiteV2Writer,
    SnapshotWriteInput,
    verify_sqlite_v2,
)


SNAPSHOT = "ms2011-20260717T180001Z-abcdef123456"
SOURCE_HASH = "a" * 64


def write_input():
    rows = build_rows()
    normalized = normalize_products(rows[QueryId.PRODUCTS], rows[QueryId.CATEGORIES], rows[QueryId.UNITS])
    promotion = extract_promotion_candidates(rows[QueryId.PRODUCTS], rows, datetime(2026, 7, 17, 12))
    return SnapshotWriteInput(
        SNAPSHOT,
        SOURCE_HASH,
        normalized.products,
        rows[QueryId.CATEGORIES],
        rows[QueryId.UNITS],
        promotion,
        normalized.issues,
    )


class SQLiteV2WriterTest(unittest.TestCase):
    def test_single_transaction_writes_and_reopens_verified_v2_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            result = SQLiteV2Writer(paths, lambda: "1" * 32).write(write_input())
            self.assertTrue(result.temp_path.path.exists())
            self.assertTrue(result.temp_path.path.is_relative_to(paths.v2_tmp_dir.resolve()))
            self.assertFalse(any(paths.v2_objects_dir.glob("*.db")))
            self.assertEqual(2, result.counts["products"])
            self.assertEqual(0, result.counts["promotion_rules"])
            verify_sqlite_v2(result.temp_path.path, result.counts)
            connection = sqlite3.connect(result.temp_path.path)
            try:
                self.assertEqual(2, connection.execute("PRAGMA user_version").fetchone()[0])
                self.assertEqual(0, connection.execute("SELECT COUNT(*) FROM promotion_rules").fetchone()[0])
            finally:
                connection.close()

    def test_each_round_uses_unique_temp_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            nonces = iter(("1" * 32, "2" * 32))
            writer = SQLiteV2Writer(paths, lambda: next(nonces))
            first = writer.write(write_input())
            second = writer.write(write_input())
            self.assertNotEqual(first.temp_path.path, second.temp_path.path)
            self.assertTrue(first.temp_path.path.exists())
            self.assertTrue(second.temp_path.path.exists())

    def test_failure_rolls_back_and_deletes_temp(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            data = write_input()
            bad = SnapshotWriteInput(
                data.snapshot_id,
                data.source_hash,
                data.products + (data.products[0],),
                data.category_rows,
                data.unit_rows,
                data.promotion,
                data.issues,
            )
            with self.assertRaises(SQLiteV2WriteError):
                SQLiteV2Writer(paths, lambda: "3" * 32).write(bad)
            self.assertFalse(paths.v2_unique_tmp(SNAPSHOT, "3" * 32).path.exists())

    def test_reopen_detects_count_tampering(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            result = SQLiteV2Writer(paths, lambda: "4" * 32).write(write_input())
            connection = sqlite3.connect(result.temp_path.path)
            try:
                connection.execute("DELETE FROM promotion_raw_rows")
                connection.commit()
            finally:
                connection.close()
            with self.assertRaises(SQLiteV2WriteError) as captured:
                verify_sqlite_v2(result.temp_path.path, result.counts)
            self.assertEqual("ROW_COUNT_MISMATCH", captured.exception.reason_code)


if __name__ == "__main__":
    unittest.main()
