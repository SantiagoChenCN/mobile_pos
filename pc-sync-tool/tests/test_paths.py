from __future__ import annotations

import os
import sys
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from paths import AppPaths, SourceReadPath
from tool_owned_path import ToolOwnedPath


SNAPSHOT = "ms2011-20260717T180001Z-abcdef123456"


class PathsTest(unittest.TestCase):
    def test_environment_root_cannot_be_overridden_by_config_or_source_path(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            with patch.dict(
                os.environ,
                {"APPDATA": str(base / "roaming"), "LOCALAPPDATA": str(base / "local")},
            ):
                paths = AppPaths.from_environment()
            source = paths.source_read_path(base.parent / "outside" / "source.db")
            owned = paths.v2_object(SNAPSHOT)
            self.assertIsInstance(source, SourceReadPath)
            self.assertIsInstance(owned, ToolOwnedPath)
            self.assertEqual((base / "local" / "MobilePosSync").resolve(), paths.local_dir.resolve())
            self.assertTrue(owned.path.is_relative_to(paths.local_dir.resolve()))
            self.assertFalse(source.path.is_relative_to(paths.local_dir.resolve()))

    def test_all_v2_capabilities_are_fixed_under_local_appdata(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            capabilities = (
                paths.v2_object(SNAPSHOT),
                paths.v2_manifest(SNAPSHOT),
                paths.v2_tmp(SNAPSHOT),
                paths.v2_unique_tmp(SNAPSHOT, uuid.UUID(int=1).hex),
                paths.v2_active_manifest,
                paths.v2_pending_manifest,
                paths.v2_last_good_manifest,
                paths.v2_publish_lock,
            )
            for capability in capabilities:
                with self.subTest(path=capability.path):
                    self.assertTrue(capability.path.is_relative_to(paths.local_dir.resolve()))
            with self.assertRaises(ValueError):
                paths.v2_manifest("../outside")
            with self.assertRaises(ValueError):
                paths.v2_unique_tmp(SNAPSHOT, "../outside")


if __name__ == "__main__":
    unittest.main()
