from __future__ import annotations

import json
import re
import threading
import time
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Optional
from urllib.parse import parse_qs, urlparse

from config import SyncConfig
from event_log import EventLog
from file_hash import sha256_file
from manifest import no_backup_manifest, read_manifest
from network import is_phone_connectable_host, preferred_lan_host
from paths import AppPaths
from publish_lock import publish_lock_for
from v2_contract import validate_snapshot_id
from v2_manifest import validate_v2_manifest
from v2_publisher import read_active_v2_manifest, reader_registry_for


APP_VERSION = "1.0"
HTTP_BIND_HOST = "0.0.0.0"
_V2_SNAPSHOT_PATH = re.compile(r"^/v2/snapshots/([^/]+)\.db$")


class SyncHttpService:
    def __init__(
        self,
        paths: AppPaths,
        config: SyncConfig,
        bind_host: Optional[str] = None,
        event_log: Optional[EventLog] = None,
        max_concurrent_v2_downloads: int = 2,
        max_v2_requests_per_minute: int = 60,
    ):
        self.paths = paths
        self.config = config
        self.bind_host = bind_host or HTTP_BIND_HOST
        self.event_log = event_log or EventLog(paths.event_log_file)
        self.publish_lock = publish_lock_for(paths)
        if not 1 <= max_concurrent_v2_downloads <= 32:
            raise ValueError("max_concurrent_v2_downloads must be between 1 and 32")
        if not 1 <= max_v2_requests_per_minute <= 10_000:
            raise ValueError("max_v2_requests_per_minute must be between 1 and 10000")
        self.reader_registry = reader_registry_for(paths)
        self._download_slots = threading.BoundedSemaphore(max_concurrent_v2_downloads)
        self._rate_limit = max_v2_requests_per_minute
        self._request_times = defaultdict(deque)
        self._request_times_lock = threading.Lock()
        self._server: Optional[ThreadingHTTPServer] = None
        self._thread: Optional[threading.Thread] = None

    @property
    def actual_port(self) -> int:
        if self._server is None:
            return self.config.port
        return int(self._server.server_address[1])

    def start(self) -> None:
        if self._server is not None:
            return
        handler = self._handler_class()
        self._server = ThreadingHTTPServer((self.bind_host, self.config.port), handler)
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()
        self._log(f"HTTP service started on {self.bind_host}:{self.actual_port}")

    def stop(self) -> None:
        if self._server is None:
            return
        self._server.shutdown()
        self._server.server_close()
        if self._thread is not None:
            self._thread.join(timeout=5)
        self._server = None
        self._thread = None
        self._log("HTTP service stopped")

    def _handler_class(self):
        service = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                service._handle_get(self)

            def log_message(self, format, *args):  # noqa: A002
                return

        return Handler

    def _handle_get(self, handler: BaseHTTPRequestHandler) -> None:
        parsed = urlparse(handler.path)
        query = parse_qs(parsed.query)
        if not self._authorized(handler, parsed.path, query):
            self._log("HTTP request rejected: invalid token")
            self._send_text(handler, 403, "Forbidden")
            return

        if parsed.path.startswith("/v2/") and not self._rate_allowed(handler.client_address[0]):
            self._send_json(handler, 429, {"ok": False, "error": "RATE_LIMITED"}, no_store=True)
            return

        if parsed.path == "/health":
            self._log(f"HTTP /health success from {handler.client_address[0]}")
            self._send_json(handler, 200, {
                "ok": True,
                "app": "MobilePosSync",
                "version": APP_VERSION,
                "host": self._health_host(),
                "port": self.actual_port,
            })
            return

        if parsed.path == "/manifest.json":
            self._send_json(handler, 200, read_manifest(self.paths.manifest_file))
            return

        if parsed.path == "/latest.db":
            self._send_latest_db(handler)
            return


        if parsed.path == "/v2/manifest.json":
            self._send_v2_manifest(handler)
            return

        snapshot_match = _V2_SNAPSHOT_PATH.fullmatch(parsed.path)
        if snapshot_match:
            self._send_v2_snapshot(handler, snapshot_match.group(1))
            return

        self._send_json(handler, 404, {"ok": False, "error": "NOT_FOUND"})

    def _authorized(self, handler, path: str, query) -> bool:
        if path.startswith("/v2/"):
            authorization = str(handler.headers.get("Authorization") or "")
            if authorization:
                prefix = "Bearer "
                return authorization.startswith(prefix) and authorization[len(prefix):] == self.config.token
        return query.get("token", [""])[0] == self.config.token

    def _rate_allowed(self, client_ip: str) -> bool:
        now = time.monotonic()
        with self._request_times_lock:
            samples = self._request_times[client_ip]
            while samples and now - samples[0] >= 60.0:
                samples.popleft()
            if len(samples) >= self._rate_limit:
                return False
            samples.append(now)
            return True

    def _send_v2_manifest(self, handler: BaseHTTPRequestHandler) -> None:
        try:
            manifest = read_active_v2_manifest(self.paths)
        except Exception:
            self._send_json(handler, 409, {"ok": False, "error": "V2_ACTIVE_INVALID"}, no_store=True)
            return
        if manifest is None:
            self._send_json(handler, 404, {"ok": False, "error": "NO_V2_SNAPSHOT"}, no_store=True)
            return
        self._send_json(handler, 200, manifest, no_store=True)

    def _send_v2_snapshot(self, handler: BaseHTTPRequestHandler, raw_snapshot_id: str) -> None:
        try:
            snapshot_id = validate_snapshot_id(raw_snapshot_id)
        except ValueError:
            self._send_json(handler, 404, {"ok": False, "error": "NOT_FOUND"}, no_store=True)
            return
        if not self._download_slots.acquire(blocking=False):
            self._send_json(handler, 429, {"ok": False, "error": "DOWNLOAD_LIMITED"}, no_store=True)
            return
        try:
            with self.reader_registry.hold(snapshot_id):
                manifest_path = self.paths.v2_manifest(snapshot_id).path
                object_path = self.paths.v2_object(snapshot_id).path
                if not manifest_path.exists() or not object_path.exists():
                    self._send_json(handler, 404, {"ok": False, "error": "NOT_FOUND"}, no_store=True)
                    return
                try:
                    encoded_manifest = manifest_path.read_bytes()
                    manifest = validate_v2_manifest(
                        json.loads(encoded_manifest.decode("utf-8")), len(encoded_manifest)
                    )
                except Exception:
                    self._send_json(handler, 409, {"ok": False, "error": "V2_MANIFEST_INVALID"}, no_store=True)
                    return
                if manifest["snapshotId"] != snapshot_id:
                    self._send_json(handler, 404, {"ok": False, "error": "NOT_FOUND"}, no_store=True)
                    return
                actual_size = object_path.stat().st_size
                actual_sha = sha256_file(object_path)
                if actual_size != manifest["sizeBytes"] or actual_sha != manifest["sha256"]:
                    self._send_json(handler, 409, {"ok": False, "error": "V2_OBJECT_MISMATCH"}, no_store=True)
                    return
                handler.send_response(200)
                handler.send_header("Content-Type", "application/octet-stream")
                handler.send_header("Content-Length", str(actual_size))
                handler.send_header("X-File-Sha256", actual_sha)
                handler.send_header("X-Snapshot-Id", snapshot_id)
                handler.send_header("Cache-Control", "no-store")
                handler.end_headers()
                try:
                    with object_path.open("rb") as handle:
                        while True:
                            chunk = handle.read(1024 * 1024)
                            if not chunk:
                                break
                            handler.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    self._log("HTTP v2 snapshot download interrupted")
                    return
            self._log("HTTP v2 snapshot downloaded: " + snapshot_id)
        finally:
            self._download_slots.release()

    def _send_latest_db(self, handler: BaseHTTPRequestHandler) -> None:
        with self.publish_lock:
            validation = self._validated_latest()
            if not validation["ok"]:
                status = 404 if validation["error"] == "NO_BACKUP_READY" else 409
                self._send_json(handler, status, validation)
                return
            latest = self.paths.latest_db
            sha = validation["sha256"]
            size = latest.stat().st_size
            handler.send_response(200)
            handler.send_header("Content-Type", "application/octet-stream")
            handler.send_header("Content-Length", str(size))
            handler.send_header("X-File-Sha256", str(sha))
            handler.end_headers()
            with latest.open("rb") as handle:
                while True:
                    chunk = handle.read(1024 * 1024)
                    if not chunk:
                        break
                    handler.wfile.write(chunk)
        self._log("HTTP latest.db downloaded")

    def _validated_latest(self):
        latest = self.paths.latest_db
        manifest_path = self.paths.manifest_file
        if not latest.exists() or not manifest_path.exists():
            return no_backup_manifest()

        manifest = read_manifest(manifest_path)
        if not manifest.get("ok"):
            return no_backup_manifest()

        expected_sha = str(manifest.get("sha256") or "")
        expected_size = manifest.get("sizeBytes")
        if not expected_sha or not isinstance(expected_size, int):
            return {"ok": False, "error": "INVALID_MANIFEST"}

        actual_size = latest.stat().st_size
        if actual_size != expected_size:
            return {"ok": False, "error": "LATEST_SIZE_MISMATCH"}

        actual_sha = sha256_file(latest)
        if actual_sha != expected_sha:
            return {"ok": False, "error": "LATEST_HASH_MISMATCH"}

        return {
            "ok": True,
            "sha256": expected_sha,
            "sizeBytes": actual_size,
        }

    def _health_host(self) -> str:
        selected_host = str(self.config.selected_host or "").strip()
        if is_phone_connectable_host(selected_host):
            return selected_host
        return preferred_lan_host() or ""

    def _send_json(self, handler: BaseHTTPRequestHandler, status: int, data, no_store: bool = False) -> None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        handler.send_response(status)
        handler.send_header("Content-Type", "application/json; charset=utf-8")
        handler.send_header("Content-Length", str(len(body)))
        if no_store:
            handler.send_header("Cache-Control", "no-store")
        handler.end_headers()
        handler.wfile.write(body)

    def _send_text(self, handler: BaseHTTPRequestHandler, status: int, body: str) -> None:
        payload = body.encode("utf-8")
        handler.send_response(status)
        handler.send_header("Content-Type", "text/plain; charset=utf-8")
        handler.send_header("Content-Length", str(len(payload)))
        handler.end_headers()
        handler.wfile.write(payload)

    def _log(self, message: str) -> None:
        try:
            self.event_log.append(message)
        except Exception:
            pass
