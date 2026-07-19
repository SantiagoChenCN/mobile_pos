from __future__ import annotations

import msvcrt
from typing import BinaryIO

from tool_owned_path import ToolOwnedKind, ToolOwnedPath


class ProcessLockError(RuntimeError):
    pass


class ProcessFileLock:
    def __init__(self, path: ToolOwnedPath):
        if not isinstance(path, ToolOwnedPath) or path.kind is not ToolOwnedKind.LOCK:
            raise TypeError("process lock requires a ToolOwnedPath LOCK")
        self._path = path
        self._handle: BinaryIO | None = None

    def acquire(self) -> None:
        if self._handle is not None:
            raise ProcessLockError("PROCESS_LOCK_ALREADY_HELD")
        self._path.path.parent.mkdir(parents=True, exist_ok=True)
        handle = self._path.path.open("a+b")
        try:
            if handle.seek(0, 2) == 0:
                handle.write(b"\0")
                handle.flush()
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError as exc:
            handle.close()
            raise ProcessLockError("PROCESS_LOCK_BUSY") from exc
        self._handle = handle

    def release(self) -> None:
        handle = self._handle
        if handle is None:
            return
        try:
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        finally:
            handle.close()
            self._handle = None

    def __enter__(self) -> "ProcessFileLock":
        self.acquire()
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        self.release()
