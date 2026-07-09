from __future__ import annotations

import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from backup_worker import BackupWorker
from config import SyncConfig
from paths import AppPaths


def create_product_db(path: Path, marker: str = "one") -> None:
    connection = sqlite3.connect(path)
    try:
        connection.execute("CREATE TABLE CJQ_GOODLIST (GID TEXT)")
        connection.execute("INSERT INTO CJQ_GOODLIST (GID) VALUES (?)", (marker,))
        connection.commit()
    finally:
        connection.close()


class BackupWorkerTest(unittest.TestCase):
    def make_paths(self, tmp: str) -> AppPaths:
        root = Path(tmp)
        return AppPaths(root / "roaming", root / "local")

    def test_backup_writes_latest_manifest_and_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "AGT_MAIN.db"
            create_product_db(source)
            paths = self.make_paths(tmp)
            config = SyncConfig(db_file_path=str(source), token="TOKEN")

            result = BackupWorker(paths, stability_seconds=0).run_once(config)

            self.assertTrue(result.ok)
            self.assertTrue(paths.latest_db.exists())
            self.assertTrue(paths.manifest_file.exists())
            manifest = json.loads(paths.manifest_file.read_text(encoding="utf-8"))
            self.assertEqual(source.name, manifest["fileName"])
            self.assertEqual(paths.latest_db.stat().st_size, manifest["sizeBytes"])
            self.assertEqual(1, len(list(paths.history_dir.glob("*.db"))))

    def test_history_is_pruned_to_retention_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "AGT_MAIN.db"
            paths = self.make_paths(tmp)
            config = SyncConfig(db_file_path=str(source), token="TOKEN", retention_count=5)
            worker = BackupWorker(paths, stability_seconds=0)

            for index in range(7):
                if source.exists():
                    source.unlink()
                create_product_db(source, str(index))
                result = worker.run_once(config)
                self.assertTrue(result.ok)

            self.assertEqual(5, len(list(paths.history_dir.glob("*.db"))))

    def test_failed_backup_does_not_replace_existing_latest_or_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "AGT_MAIN.db"
            create_product_db(source, "good")
            paths = self.make_paths(tmp)
            config = SyncConfig(db_file_path=str(source), token="TOKEN")
            worker = BackupWorker(paths, stability_seconds=0)
            self.assertTrue(worker.run_once(config).ok)
            old_latest = paths.latest_db.read_bytes()
            old_manifest = paths.manifest_file.read_text(encoding="utf-8")

            bad_config = SyncConfig(db_file_path=str(root / "missing.db"), token="TOKEN")
            result = worker.run_once(bad_config)

            self.assertFalse(result.ok)
            self.assertEqual(old_latest, paths.latest_db.read_bytes())
            self.assertEqual(old_manifest, paths.manifest_file.read_text(encoding="utf-8"))

    def test_source_change_during_copy_does_not_publish_latest_or_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "AGT_MAIN.db"
            create_product_db(source, "good")
            paths = self.make_paths(tmp)
            config = SyncConfig(db_file_path=str(source), token="TOKEN")

            class MutatingWorker(BackupWorker):
                def __init__(self, *args, **kwargs):
                    super().__init__(*args, **kwargs)
                    self.snapshot_count = 0

                def _snapshot(self, source_path):
                    self.snapshot_count += 1
                    if self.snapshot_count == 3:
                        with source_path.open("ab") as handle:
                            handle.write(b"changed")
                    return super()._snapshot(source_path)

            result = MutatingWorker(paths, stability_seconds=0).run_once(config)

            self.assertEqual("skipped", result.status)
            self.assertFalse(paths.latest_db.exists())
            self.assertFalse(paths.manifest_file.exists())


if __name__ == "__main__":
    unittest.main()
