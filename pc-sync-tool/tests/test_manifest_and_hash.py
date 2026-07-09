from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from file_hash import sha256_file
from manifest import build_manifest, no_backup_manifest, write_manifest_atomic


class ManifestAndHashTest(unittest.TestCase):
    def test_sha256_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "sample.db"
            path.write_bytes(b"abc")

            self.assertEqual(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                sha256_file(path),
            )

    def test_manifest_shape_and_atomic_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / "AGT_MAIN.db"
            db.write_bytes(b"abc")
            manifest = build_manifest(db, 3, "hash", "2026-07-09T10:00:00Z")
            target = Path(tmp) / "manifest.json"

            write_manifest_atomic(target, manifest)

            saved = json.loads(target.read_text(encoding="utf-8"))
            self.assertTrue(saved["ok"])
            self.assertEqual("AGT_MAIN.db", saved["fileName"])
            self.assertEqual("/latest.db", saved["downloadPath"])
            self.assertEqual({"ok": False, "error": "NO_BACKUP_READY"}, no_backup_manifest())


if __name__ == "__main__":
    unittest.main()
