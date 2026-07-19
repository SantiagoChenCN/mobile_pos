from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from config import SQL_DATABASE, SyncConfig, load_config, save_config
from connection_info import connection_summary
from event_log import EventLog
from paths import AppPaths
from startup import runtime_startup_command, startup_command


class ConfigAndEventLogTest(unittest.TestCase):
    def test_load_config_creates_defaults_with_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")

            config = load_config(paths)

            self.assertEqual("file", config.source_mode)
            self.assertEqual(8765, config.port)
            self.assertEqual(15, config.backup_interval_minutes)
            self.assertEqual(8, len(config.token))
            self.assertEqual("legacy_sqlite", config.data_source)
            self.assertEqual(SQL_DATABASE, config.sql_database)
            saved = json.loads(paths.config_file.read_text(encoding="utf-8"))
            self.assertEqual(config.token, saved["token"])
            self.assertNotIn("sqlDatabase", saved)

    def test_save_config_and_connection_summary(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            config = load_config(paths)
            save_config(paths, config)

            summary = connection_summary(config)

            self.assertIn("电脑IP：", summary)
            self.assertIn("端口：8765", summary)
            self.assertIn("Token：" + config.token, summary)

    def test_event_log_keeps_recent_200_entries(self):
        with tempfile.TemporaryDirectory() as tmp:
            log = EventLog(Path(tmp) / "events.json", max_entries=200)

            for index in range(205):
                log.append("event " + str(index))

            entries = log.read()
            self.assertEqual(200, len(entries))
            self.assertEqual("event 5", entries[0]["message"])
            self.assertEqual("event 204", entries[-1]["message"])

    def test_old_config_migrates_to_legacy_without_credentials(self):
        config = SyncConfig.from_json({"token": "TOKEN123"})
        self.assertEqual("legacy_sqlite", config.data_source)
        self.assertEqual(15, config.live_detection_interval_seconds)
        self.assertEqual("MS2011", config.sql_database)
        saved = config.to_json()
        self.assertNotIn("sqlDatabase", saved)
        self.assertFalse(any("password" in key.lower() for key in saved))

    def test_v2_config_bounds_and_enums_are_strict(self):
        valid = SyncConfig.from_json(
            {
                "token": "TOKEN123",
                "dataSource": "ms2011_live",
                "liveDetectionIntervalSeconds": 0,
                "v2MaxSnapshotBytes": 1024 * 1024 * 1024,
            }
        )
        self.assertEqual("ms2011_live", valid.data_source)
        for mutation in (
            {"dataSource": "arbitrary"},
            {"liveDetectionIntervalSeconds": 4},
            {"liveDetectionIntervalSeconds": True},
            {"fullFingerprintIntervalSeconds": 59},
            {"circuitFailureThreshold": 0},
            {"v2RetentionCount": 101},
            {"v2MaxSnapshotBytes": 1024 * 1024 * 1024 + 1},
            {"sqlDatabase": "master"},
            {"sqlPassword": "secret"},
            {"username": "sa"},
        ):
            with self.subTest(mutation=mutation):
                with self.assertRaises(ValueError):
                    SyncConfig.from_json({"token": "TOKEN123", **mutation})

    def test_startup_command_distinguishes_source_and_packaged_runtime(self):
        python = Path(r"C:\Python314\python.exe")
        script = Path(r"E:\手机收银软件开发\pc-sync-tool\src\app.py")
        exe = Path(r"E:\MobilePosSync\MobilePosSync.exe")

        self.assertEqual(
            r'"C:\Python314\python.exe" "E:\手机收银软件开发\pc-sync-tool\src\app.py" --gui',
            runtime_startup_command(python, script, is_frozen=False),
        )
        self.assertEqual(
            r'"E:\MobilePosSync\MobilePosSync.exe"',
            runtime_startup_command(exe, script, is_frozen=True),
        )
        self.assertEqual(r'"C:\Python314\python.exe"', startup_command(python))


if __name__ == "__main__":
    unittest.main()
