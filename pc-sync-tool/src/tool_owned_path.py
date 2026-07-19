from __future__ import annotations

import os
from enum import Enum
from pathlib import Path
from typing import Iterable


class ToolOwnedKind(str, Enum):
    OBJECT = "OBJECT"
    MANIFEST = "MANIFEST"
    TMP = "TMP"
    ACTIVE = "ACTIVE"
    PENDING = "PENDING"
    LAST_GOOD = "LAST_GOOD"
    LOCK = "LOCK"


_FACTORY_TOKEN = object()
_DELETE_PROTECTED = frozenset(
    {ToolOwnedKind.ACTIVE, ToolOwnedKind.PENDING, ToolOwnedKind.LAST_GOOD, ToolOwnedKind.LOCK}
)


class ToolOwnedPath:
    __slots__ = ("__path", "__root", "__kind")

    def __init__(self, path: Path, root: Path, kind: ToolOwnedKind, token: object):
        if token is not _FACTORY_TOKEN:
            raise TypeError("ToolOwnedPath can only be created by AppPaths")
        resolved_root = Path(root).resolve(strict=False)
        resolved_path = Path(path).resolve(strict=False)
        try:
            resolved_path.relative_to(resolved_root)
        except ValueError as exc:
            raise ValueError("tool-owned path escaped LocalAppData") from exc
        self.__path = resolved_path
        self.__root = resolved_root
        self.__kind = kind

    @property
    def path(self) -> Path:
        return self.__path

    @property
    def kind(self) -> ToolOwnedKind:
        return self.__kind

    def __fspath__(self) -> str:
        return os.fspath(self.__path)


def _from_app_paths(path: Path, root: Path, kind: ToolOwnedKind) -> ToolOwnedPath:
    return ToolOwnedPath(path, root, kind, _FACTORY_TOKEN)


def write_bytes_atomic(destination: ToolOwnedPath, data: bytes) -> None:
    target = _require_tool_path(destination)
    if not isinstance(data, bytes):
        raise TypeError("data must be bytes")
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".write-tmp")
    temporary.write_bytes(data)
    os.replace(temporary, target)


def replace_tool_owned(source: ToolOwnedPath, destination: ToolOwnedPath) -> None:
    source_path = _require_tool_path(source)
    destination_path = _require_tool_path(destination)
    if source.kind is not ToolOwnedKind.TMP:
        raise ValueError("replace source must be a ToolOwnedPath TMP")
    destination_path.parent.mkdir(parents=True, exist_ok=True)
    os.replace(source_path, destination_path)


def unlink_tool_owned(target: ToolOwnedPath) -> None:
    path = _require_tool_path(target)
    if target.kind in _DELETE_PROTECTED:
        raise ValueError("protected tool-owned state cannot be deleted")
    if path.is_symlink() or path.is_dir():
        raise ValueError("delete rejects reparse points and directories")
    if path.exists():
        path.unlink()


def cleanup_history(targets: Iterable[ToolOwnedPath]) -> None:
    for target in targets:
        unlink_tool_owned(target)


def _require_tool_path(value: ToolOwnedPath) -> Path:
    if not isinstance(value, ToolOwnedPath):
        raise TypeError("operation requires ToolOwnedPath")
    return value.path
