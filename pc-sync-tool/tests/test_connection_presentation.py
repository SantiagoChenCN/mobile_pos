from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from network_diagnostics import NetworkDiagnosticResult
from network import validate_phone_host
from ui.connection_presentation import present_connection


class ConnectionPresentationTest(unittest.TestCase):
    def test_valid_running_service_is_ready_to_copy(self):
        presentation = present_connection(_diagnostic())

        self.assertEqual("192.168.1.35", presentation.host)
        self.assertEqual("0.0.0.0:8765", presentation.bind_address)
        self.assertEqual("HTTP 服务：运行中", presentation.service_text)
        self.assertEqual("本机健康检查：通过", presentation.local_health_text)
        self.assertTrue(presentation.can_copy_connection)
        self.assertIn("Windows 防火墙", presentation.guidance_text)

    def test_loopback_address_is_not_copyable(self):
        presentation = present_connection(_diagnostic(advertised_host="127.0.0.1"))

        self.assertFalse(presentation.can_copy_connection)
        self.assertIn("不能供手机连接", presentation.warning_text)

    def test_stopped_service_explains_next_action(self):
        presentation = present_connection(_diagnostic(service_running=False, local_health_ok=False))

        self.assertFalse(presentation.can_copy_connection)
        self.assertEqual("HTTP 服务：未运行", presentation.service_text)
        self.assertIn("先启动 HTTP 服务", presentation.guidance_text)


def _diagnostic(
    service_running: bool = True,
    local_health_ok: bool = True,
    advertised_host: str = "192.168.1.35",
) -> NetworkDiagnosticResult:
    return NetworkDiagnosticResult(
        service_running=service_running,
        local_health_ok=local_health_ok,
        bind_host="0.0.0.0",
        advertised_host=advertised_host,
        host_validation=validate_phone_host(advertised_host),
        port=8765,
        warning_code="",
        message="",
    )


if __name__ == "__main__":
    unittest.main()
