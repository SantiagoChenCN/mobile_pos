from __future__ import annotations

import json
import os
import secrets
import string
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict

from paths import AppPaths


ALLOWED_SOURCE_MODES = {"file", "folder"}
ALLOWED_INTERVALS = {0, 5, 15, 30, 60}
DEFAULT_PORT = 8765
DEFAULT_RETENTION_COUNT = 5
DEFAULT_BACKUP_INTERVAL_MINUTES = 15


@dataclass(frozen=True)
class SyncConfig:
    source_mode: str = "file"
    db_file_path: str = ""
    db_folder_path: str = ""
    backup_interval_minutes: int = DEFAULT_BACKUP_INTERVAL_MINUTES
    retention_count: int = DEFAULT_RETENTION_COUNT
    port: int = DEFAULT_PORT
    token: str = ""
    selected_host: str = "127.0.0.1"
    start_on_boot: bool = True

    @classmethod
    def from_json(cls, data: Dict[str, Any]) -> "SyncConfig":
        token = str(data.get("token") or generate_token())
        config = cls(
            source_mode=str(data.get("sourceMode", "file")),
            db_file_path=str(data.get("dbFilePath", "")),
            db_folder_path=str(data.get("dbFolderPath", "")),
            backup_interval_minutes=int(data.get("backupIntervalMinutes", DEFAULT_BACKUP_INTERVAL_MINUTES)),
            retention_count=int(data.get("retentionCount", DEFAULT_RETENTION_COUNT)),
            port=int(data.get("port", DEFAULT_PORT)),
            token=token,
            selected_host=str(data.get("selectedHost", "127.0.0.1")),
            start_on_boot=bool(data.get("startOnBoot", True)),
        )
        return config.validated()

    def to_json(self) -> Dict[str, Any]:
        return {
            "sourceMode": self.source_mode,
            "dbFilePath": self.db_file_path,
            "dbFolderPath": self.db_folder_path,
            "backupIntervalMinutes": self.backup_interval_minutes,
            "retentionCount": self.retention_count,
            "port": self.port,
            "token": self.token,
            "selectedHost": self.selected_host,
            "startOnBoot": self.start_on_boot,
        }

    def validated(self) -> "SyncConfig":
        if self.source_mode not in ALLOWED_SOURCE_MODES:
            raise ValueError("sourceMode must be 'file' or 'folder'")
        if self.backup_interval_minutes not in ALLOWED_INTERVALS:
            raise ValueError("backupIntervalMinutes must be one of 0, 5, 15, 30, 60")
        if self.retention_count < 1:
            raise ValueError("retentionCount must be at least 1")
        if not (1 <= self.port <= 65535):
            raise ValueError("port must be between 1 and 65535")
        if not self.token:
            raise ValueError("token is required")
        return self


def generate_token(length: int = 8) -> str:
    alphabet = string.ascii_uppercase + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def default_config() -> SyncConfig:
    return SyncConfig(token=generate_token())


def load_config(paths: AppPaths) -> SyncConfig:
    paths.ensure()
    if not paths.config_file.exists():
        config = default_config()
        save_config(paths, config)
        return config
    with paths.config_file.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    config = SyncConfig.from_json(data)
    if not data.get("token"):
        save_config(paths, config)
    return config


def save_config(paths: AppPaths, config: SyncConfig) -> None:
    paths.ensure()
    config.validated()
    write_json_atomic(paths.config_file, config.to_json())


def write_json_atomic(path: Path, data: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    os.replace(tmp, path)
