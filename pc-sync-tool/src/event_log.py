from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, List

from manifest import utc_now_iso


class EventLog:
    def __init__(self, path: Path, max_entries: int = 200):
        self.path = Path(path)
        self.max_entries = max_entries

    def append(self, message: str, level: str = "INFO") -> Dict[str, Any]:
        entry = {
            "time": utc_now_iso(),
            "level": level,
            "message": message,
        }
        entries = self.read()
        entries.append(entry)
        entries = entries[-self.max_entries :]
        self._write(entries)
        return entry

    def read(self) -> List[Dict[str, Any]]:
        if not self.path.exists():
            return []
        try:
            with self.path.open("r", encoding="utf-8") as handle:
                data = json.load(handle)
            if isinstance(data, list):
                return data
        except Exception:
            return []
        return []

    def _write(self, entries: List[Dict[str, Any]]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_name(self.path.name + ".tmp")
        with tmp.open("w", encoding="utf-8") as handle:
            json.dump(entries, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        os.replace(tmp, self.path)
