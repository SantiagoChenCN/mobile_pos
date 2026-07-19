from __future__ import annotations

import json
import sys
import tempfile
import unittest
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))
sys.path.insert(0, str(Path(__file__).resolve().parent / "fixtures"))

from config import SyncConfig
from file_hash import sha256_file
from http_server import SyncHttpService
from ms2011_query_catalog import QueryId
from ms2011_rows import build_rows
from paths import AppPaths
from promotion_candidate_extractor import extract_promotion_candidates
from snapshot_normalizer import normalize_products
from sqlite_v2_writer import SQLiteV2Writer, SnapshotWriteInput
from v2_publisher import V2Publisher


SNAPSHOT = "ms2011-20260717T180001Z-aaaaaaaaaaaa"


def publish_fixture(paths):
    rows = build_rows()
    normalized = normalize_products(rows[QueryId.PRODUCTS], rows[QueryId.CATEGORIES], rows[QueryId.UNITS])
    promotion = extract_promotion_candidates(rows[QueryId.PRODUCTS], rows, datetime(2026, 7, 17, 12))
    data = SnapshotWriteInput(SNAPSHOT, "a" * 64, normalized.products, rows[QueryId.CATEGORIES], rows[QueryId.UNITS], promotion, normalized.issues)
    written = SQLiteV2Writer(paths, lambda: "1" * 32).write(data)
    return V2Publisher(paths, now_utc=lambda: datetime(2026, 7, 17, 18, tzinfo=timezone.utc)).publish(written)


def request(url, token="TOKEN"):
    return urllib.request.Request(url, headers={"Authorization": "Bearer " + token})


class HttpServerV2Test(unittest.TestCase):
    def test_bearer_manifest_and_fixed_snapshot_download_headers(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            published = publish_fixture(paths)
            service = SyncHttpService(paths, SyncConfig(token="TOKEN", port=0), bind_host="127.0.0.1")
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"
                with urllib.request.urlopen(request(base + "/v2/manifest.json"), timeout=5) as response:
                    manifest = json.loads(response.read().decode("utf-8"))
                    self.assertEqual("no-store", response.headers["Cache-Control"])
                self.assertEqual(SNAPSHOT, manifest["snapshotId"])
                with urllib.request.urlopen(request(base + manifest["downloadPath"]), timeout=5) as response:
                    body = response.read()
                    self.assertEqual(SNAPSHOT, response.headers["X-Snapshot-Id"])
                    self.assertEqual(manifest["sha256"], response.headers["X-File-Sha256"])
                    self.assertEqual("no-store", response.headers["Cache-Control"])
                self.assertEqual(published.object_path.path.read_bytes(), body)
            finally:
                service.stop()

    def test_query_token_fallback_and_invalid_bearer_does_not_fallback(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            publish_fixture(paths)
            service = SyncHttpService(paths, SyncConfig(token="TOKEN", port=0), bind_host="127.0.0.1")
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"
                with urllib.request.urlopen(base + "/v2/manifest.json?token=TOKEN", timeout=5) as response:
                    self.assertEqual(200, response.status)
                bad = urllib.request.Request(
                    base + "/v2/manifest.json?token=TOKEN",
                    headers={"Authorization": "Bearer BAD"},
                )
                with self.assertRaises(urllib.error.HTTPError) as captured:
                    urllib.request.urlopen(bad, timeout=5)
                self.assertEqual(403, captured.exception.code)
                captured.exception.close()
            finally:
                service.stop()

    def test_invalid_unretained_and_tampered_objects_are_not_downloaded(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            published = publish_fixture(paths)
            service = SyncHttpService(paths, SyncConfig(token="TOKEN", port=0), bind_host="127.0.0.1")
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"
                for path in ("/v2/snapshots/../x.db", "/v2/snapshots/ms2011-20260717T180009Z-dddddddddddd.db"):
                    with self.assertRaises(urllib.error.HTTPError) as captured:
                        urllib.request.urlopen(request(base + path), timeout=5)
                    self.assertEqual(404, captured.exception.code)
                    captured.exception.close()
                published.object_path.path.write_bytes(b"tampered")
                with self.assertRaises(urllib.error.HTTPError) as captured:
                    urllib.request.urlopen(request(base + published.manifest["downloadPath"]), timeout=5)
                self.assertEqual(409, captured.exception.code)
                captured.exception.close()
            finally:
                service.stop()

    def test_rate_and_concurrent_download_limits_return_429(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            published = publish_fixture(paths)
            service = SyncHttpService(
                paths,
                SyncConfig(token="TOKEN", port=0),
                bind_host="127.0.0.1",
                max_concurrent_v2_downloads=1,
                max_v2_requests_per_minute=2,
            )
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"
                self.assertTrue(service._download_slots.acquire(blocking=False))
                try:
                    with self.assertRaises(urllib.error.HTTPError) as limited:
                        urllib.request.urlopen(request(base + published.manifest["downloadPath"]), timeout=5)
                    self.assertEqual(429, limited.exception.code)
                    limited.exception.close()
                finally:
                    service._download_slots.release()
                with urllib.request.urlopen(request(base + "/v2/manifest.json"), timeout=5) as response:
                    self.assertEqual(200, response.status)
                with self.assertRaises(urllib.error.HTTPError) as rate:
                    urllib.request.urlopen(request(base + "/v2/manifest.json"), timeout=5)
                self.assertEqual(429, rate.exception.code)
                rate.exception.close()
            finally:
                service.stop()

    def test_logs_never_include_bearer_or_query_tokens(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            service = SyncHttpService(paths, SyncConfig(token="SECRET", port=0), bind_host="127.0.0.1")
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"
                with self.assertRaises(urllib.error.HTTPError) as captured:
                    urllib.request.urlopen(base + "/v2/manifest.json?token=BAD_QUERY", timeout=5)
                captured.exception.close()
                with self.assertRaises(urllib.error.HTTPError) as captured:
                    urllib.request.urlopen(request(base + "/v2/manifest.json", "BAD_BEARER"), timeout=5)
                captured.exception.close()
            finally:
                service.stop()
            log = "\n".join(item["message"] for item in service.event_log.read())
            self.assertNotIn("BAD_QUERY", log)
            self.assertNotIn("BAD_BEARER", log)
            self.assertNotIn("SECRET", log)


if __name__ == "__main__":
    unittest.main()
