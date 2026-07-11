from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from config import SyncConfig
from http_server import SyncHttpService
from network_diagnostics import diagnose_network
from paths import AppPaths


class NetworkDiagnosticsTest(unittest.TestCase):
    def test_running_service_reports_local_health_and_advertised_host(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming", root / "local")
            config = SyncConfig(token="TOKEN", port=0, selected_host="192.168.1.35")
            service = SyncHttpService(paths, config)
            service.start()
            try:
                result = diagnose_network(service, config)
            finally:
                service.stop()

            self.assertTrue(result.service_running)
            self.assertTrue(result.local_health_ok)
            self.assertEqual("0.0.0.0", result.bind_host)
            self.assertEqual("192.168.1.35", result.advertised_host)
            self.assertTrue(result.host_validation.is_valid)
            self.assertEqual("", result.warning_code)
            self.assertFalse(paths.latest_db.exists())

    def test_invalid_advertised_host_is_reported_without_accessing_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = AppPaths(root / "roaming", root / "local")
            config = SyncConfig(token="TOKEN", port=0, selected_host="127.0.0.1")
            service = SyncHttpService(paths, config)
            service.start()
            try:
                result = diagnose_network(service, config)
            finally:
                service.stop()

            self.assertTrue(result.local_health_ok)
            self.assertFalse(result.host_validation.is_valid)
            self.assertEqual("INVALID_ADVERTISED_HOST", result.warning_code)
            self.assertFalse(paths.latest_db.exists())

    def test_stopped_service_is_reported(self):
        config = SyncConfig(token="TOKEN", port=8765, selected_host="192.168.1.35")

        result = diagnose_network(None, config)

        self.assertFalse(result.service_running)
        self.assertFalse(result.local_health_ok)
        self.assertTrue(result.host_validation.is_valid)
        self.assertEqual("SERVICE_NOT_RUNNING", result.warning_code)

    def test_stopped_service_keeps_invalid_host_validation(self):
        config = SyncConfig(token="TOKEN", port=8765, selected_host="127.0.0.1")

        result = diagnose_network(None, config)

        self.assertFalse(result.host_validation.is_valid)
        self.assertEqual("LOOPBACK", result.host_validation.code)
        self.assertEqual("SERVICE_NOT_RUNNING", result.warning_code)


if __name__ == "__main__":
    unittest.main()
