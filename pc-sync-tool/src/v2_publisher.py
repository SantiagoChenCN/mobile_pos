from __future__ import annotations

import json
import threading
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterator, Mapping

from file_hash import sha256_file
from paths import AppPaths
from process_lock import ProcessFileLock
from publish_lock import publish_lock_for
from sqlite_v2_writer import SQLiteV2WriteResult, verify_sqlite_v2
from tool_owned_path import (
    ToolOwnedPath,
    replace_tool_owned,
    unlink_tool_owned,
    write_bytes_atomic,
)
from v2_contract import SCHEMA_VERSION, SOURCE_TYPE, snapshot_download_path, validate_snapshot_id
from v2_manifest import validate_v2_manifest


class V2PublishError(RuntimeError):
    def __init__(self, reason_code: str):
        super().__init__(reason_code)
        self.reason_code = reason_code


@dataclass(frozen=True)
class V2PublishResult:
    snapshot_id: str
    object_path: ToolOwnedPath
    manifest_path: ToolOwnedPath
    manifest: Mapping[str, object]
    cleanup_failures: tuple[str, ...]


class V2ReaderRegistry:
    def __init__(self):
        self._lock = threading.Lock()
        self._counts: dict[str, int] = {}

    @contextmanager
    def hold(self, snapshot_id: str) -> Iterator[None]:
        snapshot = validate_snapshot_id(snapshot_id)
        with self._lock:
            self._counts[snapshot] = self._counts.get(snapshot, 0) + 1
        try:
            yield
        finally:
            with self._lock:
                remaining = self._counts[snapshot] - 1
                if remaining:
                    self._counts[snapshot] = remaining
                else:
                    self._counts.pop(snapshot, None)

    def in_use_ids(self) -> frozenset[str]:
        with self._lock:
            return frozenset(self._counts)


_REGISTRIES: dict[str, V2ReaderRegistry] = {}
_REGISTRIES_LOCK = threading.Lock()


def reader_registry_for(paths: AppPaths) -> V2ReaderRegistry:
    key = str(paths.local_dir.resolve(strict=False))
    with _REGISTRIES_LOCK:
        registry = _REGISTRIES.get(key)
        if registry is None:
            registry = V2ReaderRegistry()
            _REGISTRIES[key] = registry
        return registry


class V2Publisher:
    def __init__(
        self,
        paths: AppPaths,
        retention_count: int = 5,
        minimum_app_version: int = 1,
        now_utc: Callable[[], datetime] | None = None,
        reader_registry: V2ReaderRegistry | None = None,
    ):
        if isinstance(retention_count, bool) or not isinstance(retention_count, int) or not 1 <= retention_count <= 100:
            raise ValueError("retention_count must be between 1 and 100")
        if isinstance(minimum_app_version, bool) or not isinstance(minimum_app_version, int) or minimum_app_version <= 0:
            raise ValueError("minimum_app_version must be positive")
        self.paths = paths
        self.retention_count = retention_count
        self.minimum_app_version = minimum_app_version
        self.now_utc = now_utc or (lambda: datetime.now(timezone.utc))
        self.reader_registry = reader_registry or reader_registry_for(paths)
        self.publish_lock = publish_lock_for(paths)

    def publish(self, write_result: SQLiteV2WriteResult) -> V2PublishResult:
        snapshot_id = validate_snapshot_id(write_result.snapshot_id)
        verify_sqlite_v2(write_result.temp_path.path, write_result.counts)
        digest = sha256_file(write_result.temp_path.path)
        object_path = self.paths.v2_object(snapshot_id)
        manifest_path = self.paths.v2_manifest(snapshot_id)
        if object_path.path.exists() or manifest_path.path.exists():
            raise V2PublishError("IMMUTABLE_SNAPSHOT_ALREADY_EXISTS")
        manifest = self._build_manifest(write_result, digest)
        encoded = _manifest_bytes(manifest)
        validate_v2_manifest(manifest, len(encoded))

        with self.publish_lock:
            with ProcessFileLock(self.paths.v2_publish_lock):
                previous_active = _read_manifest_bytes_if_valid(self.paths.v2_active_manifest)
                replace_tool_owned(write_result.temp_path, object_path)
                self._write_manifest(manifest_path, encoded)
                if previous_active is not None:
                    self._write_manifest(self.paths.v2_last_good_manifest, previous_active)
                self._write_manifest(self.paths.v2_active_manifest, encoded)
                cleanup_failures = self._cleanup_history(snapshot_id)
        return V2PublishResult(snapshot_id, object_path, manifest_path, manifest, cleanup_failures)

    def _write_manifest(self, destination: ToolOwnedPath, encoded: bytes) -> None:
        write_bytes_atomic(destination, encoded)

    def _build_manifest(
        self, write_result: SQLiteV2WriteResult, digest: str
    ) -> dict[str, object]:
        now = self.now_utc()
        if now.tzinfo is None or now.utcoffset() != timezone.utc.utcoffset(None):
            raise V2PublishError("PUBLISH_CLOCK_NOT_UTC")
        counts = write_result.counts
        return {
            "ok": True,
            "schemaVersion": SCHEMA_VERSION,
            "snapshotId": write_result.snapshot_id,
            "sourceType": SOURCE_TYPE,
            "createdAtUtc": now.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
            "sizeBytes": write_result.size_bytes,
            "sha256": digest,
            "minimumAppVersion": self.minimum_app_version,
            "productCount": counts["products"],
            "categoryCount": counts["categories"],
            "unitCount": counts["units"],
            "promotionCandidateCount": counts["promotion_candidates"],
            "verifiedPromotionCount": counts["promotion_rules"],
            "validationIssueCount": counts["validation_issues"],
            "downloadPath": snapshot_download_path(write_result.snapshot_id),
        }

    def _cleanup_history(self, active_snapshot_id: str) -> tuple[str, ...]:
        try:
            protected = self._protected_ids() | {active_snapshot_id} | set(self.reader_registry.in_use_ids())
        except Exception:
            return ("PROTECTED_STATE_INVALID",)
        objects = []
        if self.paths.v2_objects_dir.exists():
            for path in self.paths.v2_objects_dir.glob("*.db"):
                if not path.is_file() or path.is_symlink():
                    continue
                try:
                    snapshot_id = validate_snapshot_id(path.stem)
                except ValueError:
                    continue
                objects.append((path.stat().st_mtime_ns, snapshot_id))
        objects.sort(reverse=True)
        keep = protected | {snapshot for _, snapshot in objects[: self.retention_count]}
        failures = []
        for _, snapshot_id in objects:
            if snapshot_id in keep:
                continue
            for capability in (self.paths.v2_object(snapshot_id), self.paths.v2_manifest(snapshot_id)):
                try:
                    unlink_tool_owned(capability)
                except OSError:
                    failures.append(snapshot_id)
                    break
        return tuple(sorted(set(failures)))

    def _protected_ids(self) -> set[str]:
        protected = set()
        for capability in (
            self.paths.v2_active_manifest,
            self.paths.v2_pending_manifest,
            self.paths.v2_last_good_manifest,
        ):
            if not capability.path.exists():
                continue
            data = json.loads(capability.path.read_text(encoding="utf-8"))
            validate_v2_manifest(data, capability.path.stat().st_size)
            protected.add(data["snapshotId"])
        return protected


def read_active_v2_manifest(paths: AppPaths) -> dict[str, object] | None:
    capability = paths.v2_active_manifest
    if not capability.path.exists():
        return None
    data = json.loads(capability.path.read_text(encoding="utf-8"))
    return validate_v2_manifest(data, capability.path.stat().st_size)


def _read_manifest_bytes_if_valid(capability: ToolOwnedPath) -> bytes | None:
    if not capability.path.exists():
        return None
    encoded = capability.path.read_bytes()
    validate_v2_manifest(json.loads(encoded.decode("utf-8")), len(encoded))
    return encoded


def _manifest_bytes(manifest: Mapping[str, object]) -> bytes:
    return (json.dumps(dict(manifest), ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
