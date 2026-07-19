from __future__ import annotations

import json
import os
import secrets
import string
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict

from network import preferred_lan_host
from paths import AppPaths


ALLOWED_SOURCE_MODES = {"file", "folder"}
ALLOWED_INTERVALS = {0, 5, 15, 30, 60}
ALLOWED_DATA_SOURCES = {"legacy_sqlite", "ms2011_live"}
DEFAULT_PORT = 8765
DEFAULT_RETENTION_COUNT = 5
DEFAULT_BACKUP_INTERVAL_MINUTES = 15
DEFAULT_LIVE_DETECTION_INTERVAL_SECONDS = 15
DEFAULT_SQL_QUIET_WINDOW_SECONDS = 10
DEFAULT_FULL_FINGERPRINT_INTERVAL_SECONDS = 15 * 60
DEFAULT_CIRCUIT_FAILURE_THRESHOLD = 3
DEFAULT_CIRCUIT_COOLDOWN_SECONDS = 5 * 60
DEFAULT_V2_RETENTION_COUNT = 5
DEFAULT_V2_MAX_SNAPSHOT_BYTES = 256 * 1024 * 1024
MAX_V2_SNAPSHOT_BYTES = 1024 * 1024 * 1024
SQL_DATABASE = "MS2011"

_SECRET_CONFIG_FIELDS = frozenset(
    {
        "password",
        "pwd",
        "sqlpassword",
        "sqlpwd",
        "sqlusername",
        "sqluser",
        "username",
        "uid",
        "user",
    }
)


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
    data_source: str = "legacy_sqlite"
    live_detection_interval_seconds: int = DEFAULT_LIVE_DETECTION_INTERVAL_SECONDS
    sql_server: str = "SERVER"
    sql_driver: str = "SQL Server"
    sql_quiet_window_seconds: int = DEFAULT_SQL_QUIET_WINDOW_SECONDS
    full_fingerprint_interval_seconds: int = DEFAULT_FULL_FINGERPRINT_INTERVAL_SECONDS
    circuit_failure_threshold: int = DEFAULT_CIRCUIT_FAILURE_THRESHOLD
    circuit_cooldown_seconds: int = DEFAULT_CIRCUIT_COOLDOWN_SECONDS
    v2_retention_count: int = DEFAULT_V2_RETENTION_COUNT
    v2_max_snapshot_bytes: int = DEFAULT_V2_MAX_SNAPSHOT_BYTES

    @classmethod
    def from_json(cls, data: Dict[str, Any]) -> "SyncConfig":
        if not isinstance(data, dict):
            raise ValueError("config must be a JSON object")
        secret_fields = {str(key).replace("_", "").lower() for key in data} & _SECRET_CONFIG_FIELDS
        if secret_fields:
            raise ValueError("SQL usernames and passwords must not be stored in config")
        configured_database = data.get("sqlDatabase")
        if configured_database not in (None, SQL_DATABASE):
            raise ValueError("sqlDatabase is fixed to MS2011")
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
            data_source=str(data.get("dataSource", "legacy_sqlite")),
            live_detection_interval_seconds=_config_int(
                data, "liveDetectionIntervalSeconds", DEFAULT_LIVE_DETECTION_INTERVAL_SECONDS
            ),
            sql_server=str(data.get("sqlServer", "SERVER")),
            sql_driver=str(data.get("sqlDriver", "SQL Server")),
            sql_quiet_window_seconds=_config_int(
                data, "sqlQuietWindowSeconds", DEFAULT_SQL_QUIET_WINDOW_SECONDS
            ),
            full_fingerprint_interval_seconds=_config_int(
                data, "fullFingerprintIntervalSeconds", DEFAULT_FULL_FINGERPRINT_INTERVAL_SECONDS
            ),
            circuit_failure_threshold=_config_int(
                data, "circuitFailureThreshold", DEFAULT_CIRCUIT_FAILURE_THRESHOLD
            ),
            circuit_cooldown_seconds=_config_int(
                data, "circuitCooldownSeconds", DEFAULT_CIRCUIT_COOLDOWN_SECONDS
            ),
            v2_retention_count=_config_int(data, "v2RetentionCount", DEFAULT_V2_RETENTION_COUNT),
            v2_max_snapshot_bytes=_config_int(
                data, "v2MaxSnapshotBytes", DEFAULT_V2_MAX_SNAPSHOT_BYTES
            ),
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
            "dataSource": self.data_source,
            "liveDetectionIntervalSeconds": self.live_detection_interval_seconds,
            "sqlServer": self.sql_server,
            "sqlDriver": self.sql_driver,
            "sqlQuietWindowSeconds": self.sql_quiet_window_seconds,
            "fullFingerprintIntervalSeconds": self.full_fingerprint_interval_seconds,
            "circuitFailureThreshold": self.circuit_failure_threshold,
            "circuitCooldownSeconds": self.circuit_cooldown_seconds,
            "v2RetentionCount": self.v2_retention_count,
            "v2MaxSnapshotBytes": self.v2_max_snapshot_bytes,
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
        if self.data_source not in ALLOWED_DATA_SOURCES:
            raise ValueError("dataSource must be 'legacy_sqlite' or 'ms2011_live'")
        _range_or_disabled(
            "liveDetectionIntervalSeconds", self.live_detection_interval_seconds, 5, 86400
        )
        _bounded_int("sqlQuietWindowSeconds", self.sql_quiet_window_seconds, 0, 300)
        _bounded_int(
            "fullFingerprintIntervalSeconds", self.full_fingerprint_interval_seconds, 60, 86400
        )
        _bounded_int("circuitFailureThreshold", self.circuit_failure_threshold, 1, 100)
        _bounded_int("circuitCooldownSeconds", self.circuit_cooldown_seconds, 5, 86400)
        _bounded_int("v2RetentionCount", self.v2_retention_count, 1, 100)
        _bounded_int("v2MaxSnapshotBytes", self.v2_max_snapshot_bytes, 1, MAX_V2_SNAPSHOT_BYTES)
        if not self.sql_server or len(self.sql_server) > 128:
            raise ValueError("sqlServer must contain 1..128 characters")
        if not self.sql_driver or len(self.sql_driver) > 128:
            raise ValueError("sqlDriver must contain 1..128 characters")
        if any(character in self.sql_driver for character in ";{}\r\n\x00"):
            raise ValueError("sqlDriver contains unsafe connection-string characters")
        return self

    @property
    def sql_database(self) -> str:
        return SQL_DATABASE


def generate_token(length: int = 8) -> str:
    alphabet = string.ascii_uppercase + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def default_config() -> SyncConfig:
    return SyncConfig(token=generate_token(), selected_host=preferred_lan_host() or "127.0.0.1")


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


def _config_int(data: Dict[str, Any], key: str, default: int) -> int:
    value = data.get(key, default)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{key} must be an integer")
    return value


def _bounded_int(name: str, value: int, minimum: int, maximum: int) -> None:
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")


def _range_or_disabled(name: str, value: int, minimum: int, maximum: int) -> None:
    if value == 0:
        return
    _bounded_int(name, value, minimum, maximum)
