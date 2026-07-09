from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def build_manifest(
    source_path: Path,
    size_bytes: int,
    sha256: str,
    created_at: Optional[str] = None,
) -> Dict[str, Any]:
    timestamp = created_at or utc_now_iso()
    return {
        "ok": True,
        "version": timestamp,
        "fileName": Path(source_path).name,
        "sizeBytes": size_bytes,
        "sha256": sha256,
        "createdAt": timestamp,
        "downloadPath": "/latest.db",
    }


def no_backup_manifest() -> Dict[str, Any]:
    return {"ok": False, "error": "NO_BACKUP_READY"}


def read_manifest(path: Path) -> Dict[str, Any]:
    if not Path(path).exists():
        return no_backup_manifest()
    with Path(path).open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_manifest_atomic(path: Path, data: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    os.replace(tmp, path)
