from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from paths import AppPaths
from read_only_ms2011_session import QueryId, ReadOnlyMs2011Session
from tool_owned_path import ToolOwnedPath, unlink_tool_owned, write_bytes_atomic


SNAPSHOT = "ms2011-20260717T180001Z-abcdef123456"


class ArchitectureBoundariesTest(unittest.TestCase):
    def test_session_only_accepts_query_id_and_copies_rows(self):
        source = [{"database": "MS2011"}]
        session = ReadOnlyMs2011Session(lambda query_id, parameters: source)
        rows = session.execute(QueryId.DATABASE_IDENTITY)
        source[0]["database"] = "changed"
        self.assertEqual("MS2011", rows[0]["database"])
        with self.assertRaises(TypeError):
            session.execute("DATABASE_IDENTITY")
        self.assertFalse(hasattr(session, "connection"))
        self.assertFalse(hasattr(session, "cursor"))

    def test_only_future_adapter_may_import_pyodbc(self):
        offenders = []
        for path in (ROOT / "src").glob("*.py"):
            if path.name == "ms2011_connection.py":
                continue
            text = path.read_text(encoding="utf-8")
            if "import pyodbc" in text or "from pyodbc" in text:
                offenders.append(path.name)
        self.assertEqual([], offenders)

    def test_app_paths_is_the_only_tool_owned_path_factory(self):
        with self.assertRaises(TypeError):
            ToolOwnedPath(Path("x"))
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            paths = AppPaths(base / "roaming", base / "local")
            target = paths.v2_object(SNAPSHOT)
            write_bytes_atomic(target, b"fixture")
            self.assertEqual(b"fixture", target.path.read_bytes())
            unlink_tool_owned(target)
            self.assertFalse(target.path.exists())
            with self.assertRaises(ValueError):
                unlink_tool_owned(paths.v2_active_manifest)
            with self.assertRaises(ValueError):
                paths.v2_object("../escape")
            with self.assertRaises(TypeError):
                write_bytes_atomic(target.path, b"not-capability")


if __name__ == "__main__":
    unittest.main()
