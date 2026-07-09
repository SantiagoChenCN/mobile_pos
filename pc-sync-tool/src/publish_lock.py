from __future__ import annotations

import threading
from pathlib import Path
from typing import Dict

from paths import AppPaths


_LOCKS: Dict[str, threading.RLock] = {}
_LOCKS_GUARD = threading.Lock()


def publish_lock_for(paths: AppPaths) -> threading.RLock:
    key = _lock_key(paths.backups_dir)
    with _LOCKS_GUARD:
        lock = _LOCKS.get(key)
        if lock is None:
            lock = threading.RLock()
            _LOCKS[key] = lock
        return lock


def _lock_key(path: Path) -> str:
    return str(Path(path).absolute()).lower()
