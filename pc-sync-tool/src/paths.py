from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path

from tool_owned_path import ToolOwnedKind, ToolOwnedPath, _from_app_paths
from v2_contract import validate_snapshot_id


APP_NAME = "MobilePosSync"
_TMP_NONCE = re.compile(r"^[0-9a-f]{32}$")


@dataclass(frozen=True)
class SourceReadPath:
    path: Path

    def __post_init__(self) -> None:
        object.__setattr__(self, "path", Path(self.path).expanduser().resolve(strict=False))

    def __fspath__(self) -> str:
        return os.fspath(self.path)


@dataclass(frozen=True)
class AppPaths:
    roaming_dir: Path
    local_dir: Path

    @classmethod
    def from_environment(cls, app_name: str = APP_NAME) -> "AppPaths":
        roaming_base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
        local_base = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
        return cls(roaming_base / app_name, local_base / app_name)

    @property
    def config_file(self) -> Path:
        return self.roaming_dir / "config.json"

    @property
    def backups_dir(self) -> Path:
        return self.local_dir / "backups"

    @property
    def history_dir(self) -> Path:
        return self.backups_dir / "history"

    @property
    def logs_dir(self) -> Path:
        return self.local_dir / "logs"

    @property
    def event_log_file(self) -> Path:
        return self.logs_dir / "events.json"

    @property
    def latest_db(self) -> Path:
        return self.backups_dir / "latest.db"

    @property
    def latest_tmp(self) -> Path:
        return self.backups_dir / "latest.tmp"

    @property
    def manifest_file(self) -> Path:
        return self.backups_dir / "manifest.json"

    @property
    def manifest_tmp(self) -> Path:
        return self.backups_dir / "manifest.tmp"

    @property
    def v2_objects_dir(self) -> Path:
        return self.local_dir / "snapshots-v2" / "objects"

    @property
    def v2_manifests_dir(self) -> Path:
        return self.local_dir / "snapshots-v2" / "manifests"

    @property
    def v2_tmp_dir(self) -> Path:
        return self.local_dir / "snapshots-v2" / "tmp"

    def source_read_path(self, value: str | os.PathLike[str]) -> SourceReadPath:
        return SourceReadPath(Path(value))

    def v2_object(self, snapshot_id: str) -> ToolOwnedPath:
        snapshot = validate_snapshot_id(snapshot_id)
        return _from_app_paths(self.v2_objects_dir / f"{snapshot}.db", self.local_dir, ToolOwnedKind.OBJECT)

    def v2_manifest(self, snapshot_id: str) -> ToolOwnedPath:
        snapshot = validate_snapshot_id(snapshot_id)
        return _from_app_paths(self.v2_manifests_dir / f"{snapshot}.json", self.local_dir, ToolOwnedKind.MANIFEST)

    def v2_tmp(self, snapshot_id: str) -> ToolOwnedPath:
        snapshot = validate_snapshot_id(snapshot_id)
        return _from_app_paths(self.v2_tmp_dir / f"{snapshot}.tmp", self.local_dir, ToolOwnedKind.TMP)

    def v2_unique_tmp(self, snapshot_id: str, nonce: str) -> ToolOwnedPath:
        snapshot = validate_snapshot_id(snapshot_id)
        if not isinstance(nonce, str) or not _TMP_NONCE.fullmatch(nonce):
            raise ValueError("temporary nonce must be 32 lowercase hexadecimal characters")
        return _from_app_paths(
            self.v2_tmp_dir / f"{snapshot}-{nonce}.tmp",
            self.local_dir,
            ToolOwnedKind.TMP,
        )

    @property
    def v2_active_manifest(self) -> ToolOwnedPath:
        return _from_app_paths(
            self.local_dir / "snapshots-v2" / "active-manifest.json",
            self.local_dir,
            ToolOwnedKind.ACTIVE,
        )

    @property
    def v2_pending_manifest(self) -> ToolOwnedPath:
        return _from_app_paths(
            self.local_dir / "snapshots-v2" / "pending-manifest.json",
            self.local_dir,
            ToolOwnedKind.PENDING,
        )

    @property
    def v2_last_good_manifest(self) -> ToolOwnedPath:
        return _from_app_paths(
            self.local_dir / "snapshots-v2" / "last-good-manifest.json",
            self.local_dir,
            ToolOwnedKind.LAST_GOOD,
        )

    @property
    def v2_publish_lock(self) -> ToolOwnedPath:
        return _from_app_paths(
            self.local_dir / "snapshots-v2" / "publish.lock",
            self.local_dir,
            ToolOwnedKind.LOCK,
        )

    def ensure(self) -> None:
        self.roaming_dir.mkdir(parents=True, exist_ok=True)
        self.backups_dir.mkdir(parents=True, exist_ok=True)
        self.history_dir.mkdir(parents=True, exist_ok=True)
        self.logs_dir.mkdir(parents=True, exist_ok=True)
