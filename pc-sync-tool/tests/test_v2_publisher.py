from __future__ import annotations

import json
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from file_hash import sha256_file
from ms2011_query_catalog import QueryId
from ms2011_rows import build_rows
from paths import AppPaths
from process_lock import ProcessFileLock, ProcessLockError
from promotion_candidate_extractor import extract_promotion_candidates
from snapshot_normalizer import normalize_products
from sqlite_v2_writer import SQLiteV2Writer, SnapshotWriteInput
from v2_publisher import V2PublishError, V2Publisher, V2ReaderRegistry, read_active_v2_manifest


SOURCE_HASH = "a" * 64
SNAPSHOTS = (
    "ms2011-20260717T180001Z-aaaaaaaaaaaa",
    "ms2011-20260717T180002Z-bbbbbbbbbbbb",
    "ms2011-20260717T180003Z-cccccccccccc",
)


def make_input(snapshot_id):
    rows = build_rows()
    normalized = normalize_products(rows[QueryId.PRODUCTS], rows[QueryId.CATEGORIES], rows[QueryId.UNITS])
    promotion = extract_promotion_candidates(rows[QueryId.PRODUCTS], rows, datetime(2026, 7, 17, 12))
    return SnapshotWriteInput(snapshot_id, SOURCE_HASH, normalized.products, rows[QueryId.CATEGORIES], rows[QueryId.UNITS], promotion, normalized.issues)


def publisher(paths, **kwargs):
    return V2Publisher(
        paths,
        now_utc=lambda: datetime(2026, 7, 17, 18, tzinfo=timezone.utc),
        **kwargs,
    )


class V2PublisherTest(unittest.TestCase):
    def test_publish_moves_verified_object_then_atomically_activates_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            written = SQLiteV2Writer(paths, lambda: "1" * 32).write(make_input(SNAPSHOTS[0]))
            result = publisher(paths).publish(written)
            self.assertFalse(written.temp_path.path.exists())
            self.assertTrue(result.object_path.path.exists())
            self.assertTrue(result.manifest_path.path.exists())
            active = read_active_v2_manifest(paths)
            self.assertEqual(SNAPSHOTS[0], active["snapshotId"])
            self.assertEqual(sha256_file(result.object_path.path), active["sha256"])
            self.assertEqual(0, active["verifiedPromotionCount"])

    def test_existing_snapshot_is_immutable(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            publisher(paths).publish(SQLiteV2Writer(paths, lambda: "1" * 32).write(make_input(SNAPSHOTS[0])))
            second = SQLiteV2Writer(paths, lambda: "2" * 32).write(make_input(SNAPSHOTS[0]))
            with self.assertRaises(V2PublishError) as captured:
                publisher(paths).publish(second)
            self.assertEqual("IMMUTABLE_SNAPSHOT_ALREADY_EXISTS", captured.exception.reason_code)
            self.assertTrue(second.temp_path.path.exists())

    def test_failure_before_active_replace_preserves_old_active(self):
        class FailingPublisher(V2Publisher):
            def _write_manifest(self, destination, encoded):
                if destination.kind.value == "MANIFEST":
                    raise OSError("fixture failure")
                super()._write_manifest(destination, encoded)

        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            publisher(paths).publish(SQLiteV2Writer(paths, lambda: "1" * 32).write(make_input(SNAPSHOTS[0])))
            written = SQLiteV2Writer(paths, lambda: "2" * 32).write(make_input(SNAPSHOTS[1]))
            with self.assertRaises(OSError):
                FailingPublisher(paths, now_utc=lambda: datetime(2026, 7, 17, 18, tzinfo=timezone.utc)).publish(written)
            self.assertEqual(SNAPSHOTS[0], read_active_v2_manifest(paths)["snapshotId"])
            self.assertTrue(paths.v2_object(SNAPSHOTS[1]).path.exists())

    def test_process_lock_rejects_second_publisher(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            first = ProcessFileLock(paths.v2_publish_lock)
            second = ProcessFileLock(paths.v2_publish_lock)
            first.acquire()
            try:
                with self.assertRaises(ProcessLockError):
                    second.acquire()
            finally:
                first.release()

    def test_cleanup_preserves_active_last_good_and_reader_references(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            registry = V2ReaderRegistry()
            pub = publisher(paths, retention_count=1, reader_registry=registry)
            pub.publish(SQLiteV2Writer(paths, lambda: "1" * 32).write(make_input(SNAPSHOTS[0])))
            with registry.hold(SNAPSHOTS[0]):
                pub.publish(SQLiteV2Writer(paths, lambda: "2" * 32).write(make_input(SNAPSHOTS[1])))
                pub.publish(SQLiteV2Writer(paths, lambda: "3" * 32).write(make_input(SNAPSHOTS[2])))
                self.assertTrue(paths.v2_object(SNAPSHOTS[0]).path.exists())
            pub._cleanup_history(SNAPSHOTS[2])
            self.assertFalse(paths.v2_object(SNAPSHOTS[0]).path.exists())
            self.assertTrue(paths.v2_object(SNAPSHOTS[1]).path.exists())
            self.assertTrue(paths.v2_object(SNAPSHOTS[2]).path.exists())

    def test_unknown_files_and_invalid_protected_state_are_never_deleted(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            paths.v2_objects_dir.mkdir(parents=True)
            unknown = paths.v2_objects_dir / "unknown.db"
            unknown.write_bytes(b"do-not-delete")
            paths.v2_active_manifest.path.parent.mkdir(parents=True, exist_ok=True)
            paths.v2_active_manifest.path.write_text("{}", encoding="utf-8")
            failures = publisher(paths)._cleanup_history(SNAPSHOTS[0])
            self.assertEqual(("PROTECTED_STATE_INVALID",), failures)
            self.assertTrue(unknown.exists())


if __name__ == "__main__":
    unittest.main()
