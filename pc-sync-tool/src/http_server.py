from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Optional
from urllib.parse import parse_qs, urlparse

from config import SyncConfig
from event_log import EventLog
from file_hash import sha256_file
from manifest import no_backup_manifest, read_manifest
from paths import AppPaths
from publish_lock import publish_lock_for


APP_VERSION = "1.0"


class SyncHttpService:
    def __init__(
        self,
        paths: AppPaths,
        config: SyncConfig,
        bind_host: Optional[str] = None,
        event_log: Optional[EventLog] = None,
    ):
        self.paths = paths
        self.config = config
        self.bind_host = bind_host or config.selected_host or "0.0.0.0"
        self.event_log = event_log or EventLog(paths.event_log_file)
        self.publish_lock = publish_lock_for(paths)
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
        token = query.get("token", [""])[0]
        if token != self.config.token:
            self._send_text(handler, 403, "Forbidden")
            return

        if parsed.path == "/health":
            self._send_json(handler, 200, {
                "ok": True,
                "app": "MobilePosSync",
                "version": APP_VERSION,
                "host": self.config.selected_host,
                "port": self.actual_port,
            })
            return

        if parsed.path == "/manifest.json":
            self._send_json(handler, 200, read_manifest(self.paths.manifest_file))
            return

        if parsed.path == "/latest.db":
            self._send_latest_db(handler)
            return

        self._send_json(handler, 404, {"ok": False, "error": "NOT_FOUND"})

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

    def _send_json(self, handler: BaseHTTPRequestHandler, status: int, data) -> None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        handler.send_response(status)
        handler.send_header("Content-Type", "application/json; charset=utf-8")
        handler.send_header("Content-Length", str(len(body)))
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
