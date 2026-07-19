from __future__ import annotations

import os
import socket
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from PySide6.QtWidgets import QApplication, QPushButton, QScrollArea

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

    def test_event_log_display_uses_argentina_time(self):
        window = self._window("192.168.1.35")
        try:
            window.controller.event_log._write([
                {
                    "time": "2026-07-11T04:10:00Z",
                    "level": "INFO",
                    "message": "HTTP /health success",
                }
            ])
            window._refresh_log()

            self.assertIn("2026-07-11 01:10:00 ART", window.log_list.item(0).text())
        finally:
            self._close_window(window)

    def test_live_sync_controls_show_locked_safety_boundary(self):
        window = self._window("192.168.1.35")
        try:
            self.assertEqual("legacy_sqlite", window.data_source_combo.currentData())
            self.assertIn("位进程", window.driver_bits_label.text())
            self.assertIn("G0B 未通过", window.readonly_capability_label.text())
            self.assertIn("LOCKED", window.live_phase_label.text())
            self.assertFalse(window.live_sync_now_button.isEnabled())
            self.assertFalse(window.live_cancel_button.isEnabled())
            button_texts = [button.text() for button in window.findChildren(QPushButton)]
            self.assertFalse(any("忽略安全" in text or "强制" in text for text in button_texts))
        finally:
            self._close_window(window)

    def test_live_connection_probe_runs_off_the_qt_thread(self):
        gate = threading.Event()
        probe_thread_ids = []

        def probe():
            probe_thread_ids.append(threading.get_ident())
            gate.wait(2)
            return {"status": "ok", "readOnly": True, "reasonCode": "READ_ONLY_VERIFIED"}

        window = self._window("192.168.1.35", live_connection_probe=probe)
        try:
            main_thread_id = threading.get_ident()
            started = time.monotonic()
            window.test_live_connection_button.click()
            self.app.processEvents()

            self.assertLess(time.monotonic() - started, 0.5)
            self.assertEqual("测试中…", window.live_connection_result_label.text())
            self.assertTrue(_wait_until(lambda: bool(probe_thread_ids), self.app))
            self.assertNotEqual(main_thread_id, probe_thread_ids[0])

            gate.set()
            self.assertTrue(
                _wait_until(lambda: "READ_ONLY_VERIFIED" in window.live_connection_result_label.text(), self.app)
            )
            self.assertTrue(window.test_live_connection_button.isEnabled())
        finally:
            gate.set()
            self._close_window(window)

    def _window(self, host: str, live_connection_probe=None) -> MainWindow:
        root = Path(self._temp_dir.name)
        paths = AppPaths(root / "roaming", root / "local")
        save_config(paths, SyncConfig(token="TOKEN123", port=_free_port(), selected_host=host))
        return MainWindow(UiController(paths), live_connection_probe=live_connection_probe)

    def _close_window(self, window: MainWindow) -> None:
        window.controller.stop_service()
        window.tray.hide()
        window.deleteLater()


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _wait_until(predicate, app: QApplication, timeout_seconds: float = 2.0) -> bool:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        app.processEvents()
        if predicate():
            return True
        time.sleep(0.01)
    app.processEvents()
    return bool(predicate())


if __name__ == "__main__":
    unittest.main()
