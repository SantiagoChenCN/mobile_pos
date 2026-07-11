from __future__ import annotations

import sys
import socket
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from config import SyncConfig, save_config
from paths import AppPaths
from ui.controller import UiController


class UiControllerTest(unittest.TestCase):
    def test_start_service_listens_on_all_interfaces(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming", root / "local")
            config = SyncConfig(token="TOKEN", port=_free_port(), selected_host="127.0.0.1")
            save_config(paths, config)
            controller = UiController(paths)

            controller.start_service()
            try:
                self.assertEqual("0.0.0.0", controller.actual_bind_host)
                self.assertEqual("0.0.0.0", controller.service.bind_host)
                self.assertIn("0.0.0.0:", controller.service_binding_text())
            finally:
                controller.stop_service()


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


if __name__ == "__main__":
    unittest.main()
