from __future__ import annotations

import json
import sqlite3
import sys
import tempfile
import unittest
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from app import V2SyncPipeline
from config import SyncConfig
from file_hash import sha256_file
from http_server import SyncHttpService
from ms2011_reader import DeterministicMs2011Reader
from ms2011_rows import build_rows
from paths import AppPaths
from read_only_ms2011_session import ReadOnlyMs2011Session
from sqlite_v2_writer import SQLiteV2Writer
from ui.controller import UiController


class V2PipelineIntegrationTest(unittest.TestCase):
    def test_fake_odbc_double_read_to_http_download_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            rows = build_rows()
            calls = []
            reader = DeterministicMs2011Reader(
                ReadOnlyMs2011Session(lambda query_id, parameters: calls.append(query_id) or rows[query_id])
            )
            now = datetime(2026, 7, 17, 18, 0, 1, tzinfo=timezone.utc)
            pipeline = V2SyncPipeline(
                paths,
                reader,
                now_utc=lambda: now,
                writer=SQLiteV2Writer(paths, lambda: "1" * 32),
            )
            result = pipeline.run_once(datetime(2026, 7, 17, 15, 0, 1))
            self.assertEqual(28, len(calls))
            self.assertEqual(2, result.product_count)
            self.assertEqual(4, result.promotion_candidate_count)
            self.assertEqual(0, result.publish.manifest["verifiedPromotionCount"])

            connection = sqlite3.connect(result.publish.object_path.path)
            try:
                self.assertEqual(0, connection.execute("SELECT COUNT(*) FROM promotion_rules").fetchone()[0])
                self.assertEqual(4, connection.execute("SELECT COUNT(*) FROM promotion_candidates").fetchone()[0])
            finally:
                connection.close()

            service = SyncHttpService(
                paths,
                SyncConfig(token="TOKEN", port=0),
                bind_host="127.0.0.1",
            )
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"
                headers = {"Authorization": "Bearer TOKEN"}
                with urllib.request.urlopen(
                    urllib.request.Request(base + "/v2/manifest.json", headers=headers), timeout=5
                ) as response:
                    manifest = json.loads(response.read().decode("utf-8"))
                with urllib.request.urlopen(
                    urllib.request.Request(base + manifest["downloadPath"], headers=headers), timeout=5
                ) as response:
                    downloaded = response.read()
            finally:
                service.stop()
            downloaded_path = Path(tmp) / "downloaded.db"
            downloaded_path.write_bytes(downloaded)
            self.assertEqual(manifest["sha256"], sha256_file(downloaded_path))
            self.assertEqual(result.publish.object_path.path.read_bytes(), downloaded)

    def test_controller_cannot_run_live_pipeline_when_g0b_is_locked(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            controller = UiController(paths)
            with self.assertRaisesRegex(RuntimeError, "G0B_LOCKED"):
                controller.run_v2_sync_once(datetime(2026, 7, 17, 15))


if __name__ == "__main__":
    unittest.main()
