from __future__ import annotations

import os
import shutil
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from config import SyncConfig
from event_log import EventLog
from file_hash import sha256_file
from manifest import build_manifest, utc_now_iso, write_manifest_atomic
from paths import AppPaths
from publish_lock import publish_lock_for
from source_locator import SourceDatabase, resolve_source


@dataclass(frozen=True)
class BackupResult:
    status: str
    message: str
    source_path: Optional[Path] = None
    sha256: str = ""
    size_bytes: int = 0

    @property
    def ok(self) -> bool:
        return self.status == "success"


@dataclass(frozen=True)
class SourceSnapshot:
    size: int
    mtime_ns: int


class BackupWorker:
    def __init__(
        self,
        paths: AppPaths,
        event_log: Optional[EventLog] = None,
        stability_seconds: float = 2.0,
    ):
        self.paths = paths
        self.event_log = event_log or EventLog(paths.event_log_file)
        self.stability_seconds = stability_seconds
        self.publish_lock = publish_lock_for(paths)

    def run_once(self, config: SyncConfig) -> BackupResult:
        self.paths.ensure()
        try:
            source = resolve_source(config)
            snapshot = self._stable_snapshot(source.path)
            if snapshot is None:
                result = BackupResult("skipped", "Source database is changing", source.path)
                self._log(result.message, "WARN")
                return result
            return self._copy_stable_source(source, snapshot, config.retention_count)
        except Exception as exc:
            result = BackupResult("failed", str(exc))
            self._log("Backup failed: " + str(exc), "ERROR")
            self._cleanup_temps()
            return result

    def _stable_snapshot(self, source_path: Path) -> Optional[SourceSnapshot]:
        first = self._snapshot(source_path)
        if self.stability_seconds > 0:
            time.sleep(self.stability_seconds)
        second = self._snapshot(source_path)
        if first == second:
            return second
        return None

    def _snapshot(self, source_path: Path) -> SourceSnapshot:
        stat = source_path.stat()
        return SourceSnapshot(stat.st_size, stat.st_mtime_ns)

    def _copy_stable_source(
        self,
        source: SourceDatabase,
        stable_snapshot: SourceSnapshot,
        retention_count: int,
    ) -> BackupResult:
        self._cleanup_temps()
        shutil.copyfile(source.path, self.paths.latest_tmp)
        if self._snapshot(source.path) != stable_snapshot:
            self._cleanup_temps()
            result = BackupResult("skipped", "Source database changed during copy", source.path)
            self._log(result.message, "WARN")
            return result

        size_bytes = self.paths.latest_tmp.stat().st_size
        digest = sha256_file(self.paths.latest_tmp)
        created_at = utc_now_iso()
        manifest = build_manifest(source.path, size_bytes, digest, created_at)
        write_manifest_atomic(self.paths.manifest_tmp, manifest)

        history_path = self._history_path(source.path, created_at, digest)
        shutil.copyfile(self.paths.latest_tmp, history_path)

        with self.publish_lock:
            os.replace(self.paths.latest_tmp, self.paths.latest_db)
            os.replace(self.paths.manifest_tmp, self.paths.manifest_file)
            self._prune_history(retention_count)

        message = "Backup succeeded: " + source.path.name + " sha256=" + digest
        self._log(message)
        return BackupResult("success", message, source.path, digest, size_bytes)

    def _history_path(self, source_path: Path, created_at: str, digest: str) -> Path:
        safe_time = created_at.replace(":", "").replace("-", "")
        return self.paths.history_dir / f"{safe_time}_{digest[:12]}_{source_path.name}"

    def _prune_history(self, retention_count: int) -> None:
        entries = [path for path in self.paths.history_dir.glob("*.db") if path.is_file()]
        entries.sort(key=lambda path: path.stat().st_mtime, reverse=True)
        for extra in entries[retention_count:]:
            extra.unlink()

    def _cleanup_temps(self) -> None:
        for path in (self.paths.latest_tmp, self.paths.manifest_tmp):
            try:
                if path.exists():
                    path.unlink()
            except OSError:
                pass

    def _log(self, message: str, level: str = "INFO") -> None:
        try:
            self.event_log.append(message, level)
        except Exception:
            pass
