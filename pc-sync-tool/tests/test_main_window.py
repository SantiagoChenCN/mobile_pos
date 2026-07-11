from __future__ import annotations

import os
import socket
import sys
import tempfile
import unittest
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from PySide6.QtWidgets import QApplication, QScrollArea

from config import SyncConfig, save_config
from paths import AppPaths
from ui.controller import UiController
from ui.main_window import MainWindow


class MainWindowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        self._temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self._temp_dir.cleanup()

    def test_connection_card_separates_phone_ip_and_listener(self):
        window = self._window("192.168.1.35")
        try:
            self.assertEqual("192.168.1.35", window.connection_host_edit.text())
            self.assertEqual("0.0.0.0:" + str(window.controller.actual_port), window.connection_binding_card_label.text())
            self.assertEqual("本机健康检查：通过", window.connection_health_status_label.text())
            self.assertTrue(window.copy_connection_button.isEnabled())
            self.assertIn("Windows 防火墙", window.connection_guidance_label.text())
            self.assertIsInstance(window.centralWidget(), QScrollArea)
        finally:
            self._close_window(window)

    def test_invalid_phone_address_disables_connection_copy(self):
        window = self._window("127.0.0.1")
        try:
            self.assertFalse(window.copy_connection_button.isEnabled())
            self.assertFalse(window.copy_host_button.isEnabled())
            self.assertIn("不能供手机连接", window.connection_warning_label.text())
        finally:
            self._close_window(window)

    def _window(self, host: str) -> MainWindow:
        root = Path(self._temp_dir.name)
        paths = AppPaths(root / "roaming", root / "local")
        save_config(paths, SyncConfig(token="TOKEN123", port=_free_port(), selected_host=host))
        return MainWindow(UiController(paths))

    def _close_window(self, window: MainWindow) -> None:
        window.controller.stop_service()
        window.tray.hide()
        window.deleteLater()


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


if __name__ == "__main__":
    unittest.main()
