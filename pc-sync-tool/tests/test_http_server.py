from __future__ import annotations

import json
import sqlite3
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from backup_worker import BackupWorker
from config import SyncConfig
from manifest import build_manifest, write_manifest_atomic
from http_server import SyncHttpService
from paths import AppPaths
from publish_lock import publish_lock_for


def create_product_db(path: Path) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.execute("CREATE TABLE CJQ_GOODLIST (GID TEXT)")
        connection.execute("INSERT INTO CJQ_GOODLIST (GID) VALUES ('1')")
        connection.commit()
    finally:
        connection.close()


class HttpServerTest(unittest.TestCase):
    def test_backup_and_http_share_publish_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            config = SyncConfig(token="TOKEN", port=0, selected_host="192.168.1.35")
            service = SyncHttpService(paths, config)
            worker = BackupWorker(paths, stability_seconds=0)
            self.assertIs(publish_lock_for(paths), service.publish_lock)
            self.assertIs(worker.publish_lock, service.publish_lock)

    def test_default_bind_host_listens_on_all_interfaces_and_advertises_selected_host(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming", root / "local")
            config = SyncConfig(token="TOKEN", port=0, selected_host="192.168.1.35")
            service = SyncHttpService(paths, config)
            service.start()
            try:
                self.assertEqual("0.0.0.0", service.bind_host)
                health = self.read_json(f"http://127.0.0.1:{service.actual_port}/health?token=TOKEN")
                self.assertEqual("192.168.1.35", health["host"])
            finally:
                service.stop()

    def test_http_api_token_manifest_and_latest_download(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "AGT_MAIN.db"
            create_product_db(source)
            paths = AppPaths(root / "roaming", root / "local")
            backup_config = SyncConfig(db_file_path=str(source), token="TOKEN")
            self.assertTrue(BackupWorker(paths, stability_seconds=0).run_once(backup_config).ok)

            http_config = SyncConfig(db_file_path=str(source), token="TOKEN", port=0, selected_host="127.0.0.1")
            service = SyncHttpService(paths, http_config, bind_host="127.0.0.1")
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"

                with self.assertRaises(urllib.error.HTTPError) as context:
                    urllib.request.urlopen(base + "/health?token=BAD", timeout=5)
                self.assertEqual(403, context.exception.code)
                context.exception.close()

                health = self.read_json(base + "/health?token=TOKEN")
                self.assertTrue(health["ok"])
                self.assertEqual("MobilePosSync", health["app"])

                manifest = self.read_json(base + "/manifest.json?token=TOKEN")
                self.assertTrue(manifest["ok"])

                response = urllib.request.urlopen(base + "/latest.db?token=TOKEN", timeout=5)
                try:
                    body = response.read()
                    self.assertEqual(paths.latest_db.read_bytes(), body)
                    self.assertEqual(manifest["sha256"], response.headers["X-File-Sha256"])
                    self.assertEqual(str(len(body)), response.headers["Content-Length"])
                finally:
                    response.close()
            finally:
                service.stop()

    def test_health_never_returns_the_all_interfaces_bind_address(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming", root / "local")
            config = SyncConfig(token="TOKEN", port=0, selected_host="0.0.0.0")
            service = SyncHttpService(paths, config)
            service.start()
            try:
                health = self.read_json(f"http://127.0.0.1:{service.actual_port}/health?token=TOKEN")
            finally:
                service.stop()

            self.assertEqual("0.0.0.0", service.bind_host)
            self.assertNotEqual("0.0.0.0", health["host"])

    def test_latest_download_waits_for_publish_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "AGT_MAIN.db"
            create_product_db(source)
            paths = AppPaths(root / "roaming", root / "local")
            backup_config = SyncConfig(db_file_path=str(source), token="TOKEN")
            self.assertTrue(BackupWorker(paths, stability_seconds=0).run_once(backup_config).ok)

            http_config = SyncConfig(db_file_path=str(source), token="TOKEN", port=0, selected_host="127.0.0.1")
            service = SyncHttpService(paths, http_config, bind_host="127.0.0.1")
            service.start()
            result = {}
            lock = publish_lock_for(paths)
            lock.acquire()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"

                def download_latest():
                    try:
                        response = urllib.request.urlopen(base + "/latest.db?token=TOKEN", timeout=5)
                        try:
                            result["body"] = response.read()
                        finally:
                            response.close()
                    except Exception as exc:  # pragma: no cover - surfaced by assertions below
                        result["error"] = exc

                thread = threading.Thread(target=download_latest)
                thread.start()
                time.sleep(0.2)
                self.assertTrue(thread.is_alive())
                self.assertEqual({}, result)
            finally:
                lock.release()

            thread.join(timeout=5)
            try:
                self.assertFalse(thread.is_alive())
                self.assertEqual(paths.latest_db.read_bytes(), result.get("body"))
                self.assertNotIn("error", result)
            finally:
                service.stop()

    def test_latest_download_requires_matching_manifest_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "AGT_MAIN.db"
            create_product_db(source)
            paths = AppPaths(root / "roaming", root / "local")
            paths.ensure()
            paths.latest_db.write_bytes(b"actual")
            bad_manifest = build_manifest(source, len(b"actual"), "not-the-real-hash", "2026-07-09T10:00:00Z")
            write_manifest_atomic(paths.manifest_file, bad_manifest)

            config = SyncConfig(token="TOKEN", port=0, selected_host="127.0.0.1")
            service = SyncHttpService(paths, config, bind_host="127.0.0.1")
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"

                with self.assertRaises(urllib.error.HTTPError) as context:
                    urllib.request.urlopen(base + "/latest.db?token=TOKEN", timeout=5)
                self.assertEqual(409, context.exception.code)
                body = json.loads(context.exception.read().decode("utf-8"))
                context.exception.close()
                self.assertEqual("LATEST_HASH_MISMATCH", body["error"])
            finally:
                service.stop()

    def test_latest_download_requires_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming", root / "local")
            paths.ensure()
            paths.latest_db.write_bytes(b"actual")
            config = SyncConfig(token="TOKEN", port=0, selected_host="127.0.0.1")
            service = SyncHttpService(paths, config, bind_host="127.0.0.1")
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"

                with self.assertRaises(urllib.error.HTTPError) as context:
                    urllib.request.urlopen(base + "/latest.db?token=TOKEN", timeout=5)
                self.assertEqual(404, context.exception.code)
                body = json.loads(context.exception.read().decode("utf-8"))
                context.exception.close()
                self.assertEqual("NO_BACKUP_READY", body["error"])
            finally:
                service.stop()

    def test_http_request_log_does_not_include_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming", root / "local")
            config = SyncConfig(token="SECRET_TOKEN", port=0, selected_host="192.168.1.35")
            service = SyncHttpService(paths, config)
            service.start()
            try:
                base = f"http://127.0.0.1:{service.actual_port}"
                self.read_json(base + "/health?token=SECRET_TOKEN")
                with self.assertRaises(urllib.error.HTTPError) as context:
                    urllib.request.urlopen(base + "/health?token=BAD_TOKEN", timeout=5)
                context.exception.close()
            finally:
                service.stop()

            messages = [entry["message"] for entry in service.event_log.read()]
            self.assertIn("HTTP /health success from 127.0.0.1", messages)
            self.assertIn("HTTP request rejected: invalid token", messages)
            self.assertNotIn("SECRET_TOKEN", "\n".join(messages))
            self.assertNotIn("BAD_TOKEN", "\n".join(messages))

    def read_json(self, url: str):
        response = urllib.request.urlopen(url, timeout=5)
        try:
            return json.loads(response.read().decode("utf-8"))
        finally:
            response.close()


if __name__ == "__main__":
    unittest.main()
