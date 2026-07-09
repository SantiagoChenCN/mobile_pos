from __future__ import annotations

import sqlite3
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from source_locator import find_source_in_folder, validate_mingsheng_db


def create_db(path: Path, with_product_table: bool = True) -> None:
    connection = sqlite3.connect(path)
    try:
        if with_product_table:
            connection.execute("CREATE TABLE CJQ_GOODLIST (GID TEXT)")
        else:
            connection.execute("CREATE TABLE OTHER_TABLE (ID TEXT)")
        connection.commit()
    finally:
        connection.close()


class SourceLocatorTest(unittest.TestCase):
    def test_validate_mingsheng_db_requires_product_table(self):
        with tempfile.TemporaryDirectory() as tmp:
            valid = Path(tmp) / "AGT_MAIN.db"
            invalid = Path(tmp) / "AGT_REPORT.db"
            create_db(valid, True)
            create_db(invalid, False)

            self.assertTrue(validate_mingsheng_db(valid))
            self.assertFalse(validate_mingsheng_db(invalid))

    def test_folder_scan_prefers_agt_main_when_valid(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            other = folder / "ZZZ.db"
            agt = folder / "AGT_MAIN.db"
            newer = folder / "AGT_MAIN_20260705.db"
            create_db(other, True)
            time.sleep(0.01)
            create_db(agt, True)
            time.sleep(0.01)
            create_db(newer, True)

            result = find_source_in_folder(folder)

            self.assertEqual(agt.resolve(), result.path)

    def test_folder_scan_skips_invalid_preferred_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            create_db(folder / "AGT_MAIN.db", False)
            valid = folder / "AGT_MAIN_20260705.db"
            create_db(valid, True)

            result = find_source_in_folder(folder)

            self.assertEqual(valid.resolve(), result.path)


if __name__ == "__main__":
    unittest.main()
