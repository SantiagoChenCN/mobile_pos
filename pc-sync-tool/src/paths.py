from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


APP_NAME = "MobilePosSync"


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

    def ensure(self) -> None:
        self.roaming_dir.mkdir(parents=True, exist_ok=True)
        self.backups_dir.mkdir(parents=True, exist_ok=True)
        self.history_dir.mkdir(parents=True, exist_ok=True)
        self.logs_dir.mkdir(parents=True, exist_ok=True)
