from __future__ import annotations

import fnmatch
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List

from config import SyncConfig


PRODUCT_TABLE = "CJQ_GOODLIST"


class SourceLocatorError(Exception):
    pass


@dataclass(frozen=True)
class SourceDatabase:
    path: Path
    mode: str
    reason: str


def resolve_source(config: SyncConfig) -> SourceDatabase:
    if config.source_mode == "file":
        path = Path(config.db_file_path)
        if not path.exists() or not path.is_file():
            raise SourceLocatorError("Configured database file does not exist")
        if not validate_mingsheng_db(path):
            raise SourceLocatorError("Configured database does not contain CJQ_GOODLIST")
        return SourceDatabase(path.resolve(), "file", "configured file")

    folder = Path(config.db_folder_path)
    return find_source_in_folder(folder)


def find_source_in_folder(folder: Path) -> SourceDatabase:
    if not folder.exists() or not folder.is_dir():
        raise SourceLocatorError("Configured database folder does not exist")
    candidates = sorted(folder.glob("*.db"), key=_candidate_rank)
    valid: List[Path] = []
    for candidate in candidates:
        if validate_mingsheng_db(candidate):
            valid.append(candidate)
    if not valid:
        raise SourceLocatorError("No .db containing CJQ_GOODLIST was found")
    return SourceDatabase(valid[0].resolve(), "folder", "auto discovered")


def validate_mingsheng_db(path: Path) -> bool:
    path = Path(path)
    if not path.exists() or not path.is_file():
        return False
    try:
        connection = sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)
        try:
            cursor = connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                (PRODUCT_TABLE,),
            )
            return cursor.fetchone() is not None
        finally:
            connection.close()
    except sqlite3.Error:
        return False


def _candidate_rank(path: Path):
    name = path.name
    lowered = name.lower()
    if lowered == "agt_main.db":
        priority = 0
    elif fnmatch.fnmatch(lowered, "agt_main_*.db"):
        priority = 1
    else:
        priority = 2
    stat = path.stat()
    return (priority, -stat.st_mtime, -stat.st_size, lowered)


def candidate_names(paths: Iterable[Path]) -> List[str]:
    return [Path(path).name for path in sorted(paths, key=_candidate_rank)]
