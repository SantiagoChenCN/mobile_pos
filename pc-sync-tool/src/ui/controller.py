from __future__ import annotations

import sys
from dataclasses import replace
from pathlib import Path
from typing import Any, Dict, List, Optional

from backup_worker import BackupResult, BackupWorker
from config import SyncConfig, generate_token, load_config, save_config
from event_log import EventLog
from http_server import SyncHttpService
from manifest import no_backup_manifest, read_manifest
from paths import AppPaths
from qr_code import setup_url
from source_locator import SourceDatabase, resolve_source
from startup import StartupRegistrationError, set_enabled


class UiController:
    def __init__(self, paths: AppPaths):
        self.paths = paths
        self.config = load_config(paths)
        self.event_log = EventLog(paths.event_log_file)
        self.service: Optional[SyncHttpService] = None

    @property
    def service_running(self) -> bool:
        return self.service is not None

    @property
    def actual_port(self) -> int:
        if self.service is None:
            return self.config.port
        return self.service.actual_port

    @property
    def actual_bind_host(self) -> str:
        if self.service is None:
            return self.config.selected_host
        return self.service.bind_host

    def start_service(self) -> None:
        if self.service is not None:
            return
        self.service = SyncHttpService(
            self.paths,
            self.config,
            bind_host=self.config.selected_host,
            event_log=self.event_log,
        )
        try:
            self.service.start()
        except Exception:
            self.service = None
            raise

    def stop_service(self) -> None:
        if self.service is None:
            return
        self.service.stop()
        self.service = None

    def restart_service(self) -> None:
        was_running = self.service_running
        self.stop_service()
        if was_running:
            self.start_service()

    def save_from_form(
        self,
        source_mode: str,
        db_file_path: str,
        db_folder_path: str,
        backup_interval_minutes: int,
        port: int,
        selected_host: str,
        start_on_boot: bool,
    ) -> SyncConfig:
        old_port = self.config.port
        old_host = self.config.selected_host
        self.config = replace(
            self.config,
            source_mode=source_mode,
            db_file_path=db_file_path.strip(),
            db_folder_path=db_folder_path.strip(),
            backup_interval_minutes=backup_interval_minutes,
            port=port,
            selected_host=selected_host.strip() or "127.0.0.1",
            start_on_boot=start_on_boot,
        ).validated()
        save_config(self.paths, self.config)
        self._apply_startup()
        if self.service_running and (old_port != self.config.port or old_host != self.config.selected_host):
            self.restart_service()
        return self.config

    def regenerate_token(self) -> SyncConfig:
        self.config = replace(self.config, token=generate_token()).validated()
        save_config(self.paths, self.config)
        if self.service_running:
            self.restart_service()
        return self.config

    def run_backup_once(self) -> BackupResult:
        return BackupWorker(self.paths, self.event_log).run_once(self.config)

    def detect_source(self) -> SourceDatabase:
        return resolve_source(self.config)

    def setup_url(self) -> str:
        return setup_url(self.config)

    def service_binding_text(self) -> str:
        if self.service_running:
            return f"{self.actual_bind_host}:{self.actual_port}"
        return f"未绑定（保存为 {self.config.selected_host}:{self.config.port}）"

    def qr_status_text(self) -> str:
        qr_target = f"{self.config.selected_host}:{self.config.port}"
        if not self.service_running:
            return f"已生成，指向 {qr_target}；HTTP 未运行"
        service_target = f"{self.actual_bind_host}:{self.actual_port}"
        if service_target == qr_target:
            return f"可用，指向 {qr_target}"
        return f"需检查：二维码 {qr_target}，服务 {service_target}"

    def read_manifest(self) -> Dict[str, Any]:
        if not self.paths.manifest_file.exists():
            return no_backup_manifest()
        return read_manifest(self.paths.manifest_file)

    def read_events(self) -> List[Dict[str, Any]]:
        return self.event_log.read()

    def latest_request_text(self) -> str:
        for entry in reversed(self.read_events()):
            message = str(entry.get("message", ""))
            if "HTTP" in message or "downloaded" in message:
                return self._format_event(entry)
        return "无"

    def latest_backup_text(self) -> str:
        manifest = self.read_manifest()
        if manifest.get("ok"):
            file_name = manifest.get("fileName", "")
            created = manifest.get("createdAt", "")
            size = int(manifest.get("sizeBytes", 0) or 0)
            return f"{created} 成功 {file_name} ({_format_size(size)})"
        return "还没有可同步备份"

    def _apply_startup(self) -> None:
        try:
            app_script = Path(__file__).resolve().parents[1] / "app.py"
            set_enabled(
                self.config.start_on_boot,
                Path(sys.executable),
                app_script=app_script,
                is_frozen=bool(getattr(sys, "frozen", False)),
            )
        except StartupRegistrationError as exc:
            self.event_log.append("Startup registration failed: " + str(exc), "WARN")

    def _format_event(self, entry: Dict[str, Any]) -> str:
        return f"{entry.get('time', '')} {entry.get('message', '')}".strip()


def _format_size(size_bytes: int) -> str:
    if size_bytes >= 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.1f} MB"
    if size_bytes >= 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes} B"
