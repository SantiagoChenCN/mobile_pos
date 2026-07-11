from __future__ import annotations

import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from app import main
from config import SyncConfig, save_config
from connection_info import connection_info, connection_summary
from paths import AppPaths
from ui.controller import UiController


class ConnectionInfoTest(unittest.TestCase):
    def test_connection_summary_formats_manual_setup_fields(self):
        config = SyncConfig(token="WSWSBWQM", port=8765, selected_host="192.168.1.35")

        info = connection_info(config)

        self.assertEqual("192.168.1.35", info.host)
        self.assertEqual(8765, info.port)
        self.assertEqual("WSWSBWQM", info.token)
        self.assertEqual(
            "电脑IP：192.168.1.35\n端口：8765\nToken：WSWSBWQM",
            connection_summary(config),
        )

    def test_ui_controller_exposes_connection_info(self):
        with tempfile.TemporaryDirectory() as tmp:
            paths = AppPaths(Path(tmp) / "roaming", Path(tmp) / "local")
            save_config(paths, SyncConfig(token="TOKEN123", port=8765, selected_host="192.168.1.35"))

            controller = UiController(paths)

            self.assertEqual("192.168.1.35", controller.connection_host())
            self.assertEqual(8765, controller.connection_port())
            self.assertEqual("TOKEN123", controller.connection_token())
            self.assertIn("电脑IP：192.168.1.35", controller.connection_summary())

    def test_invalid_address_cannot_generate_copyable_connection_info(self):
        for host in ("127.0.0.1", "0.0.0.0", "169.254.10.20", "224.0.0.1", "255.255.255.255", "240.0.0.1"):
            info = connection_info(SyncConfig(token="TOKEN", port=8765, selected_host=host))
            summary = info.summary()

            self.assertNotIn(host, summary)
            self.assertIn("选择局域网 IPv4", summary)
            self.assertFalse(info.is_copyable)
            self.assertEqual({}, info.as_dict())

    def test_cli_print_connection_info_uses_saved_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming" / "MobilePosSync", root / "local" / "MobilePosSync")
            save_config(paths, SyncConfig(token="TOKEN123", port=8765, selected_host="192.168.1.35"))
            old_appdata = os.environ.get("APPDATA")
            old_localappdata = os.environ.get("LOCALAPPDATA")
            os.environ["APPDATA"] = str(root / "roaming")
            os.environ["LOCALAPPDATA"] = str(root / "local")
            output = io.StringIO()
            try:
                with redirect_stdout(output):
                    exit_code = main(["--print-connection-info"])
            finally:
                _restore_env("APPDATA", old_appdata)
                _restore_env("LOCALAPPDATA", old_localappdata)

            self.assertEqual(0, exit_code)
            self.assertEqual(
                "电脑IP：192.168.1.35\n端口：8765\nToken：TOKEN123",
                output.getvalue().strip(),
            )


def _restore_env(name: str, value) -> None:
    if value is None:
        os.environ.pop(name, None)
    else:
        os.environ[name] = value


if __name__ == "__main__":
    unittest.main()
