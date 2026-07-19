from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from network_diagnostics import NetworkDiagnosticResult
from network import validate_phone_host
from ui.connection_presentation import present_connection, present_live_sync


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

    def test_live_sync_is_visibly_locked_without_pipeline(self):
        controller = type("Controller", (), {"v2_pipeline": None, "latest_v2_result": None})()

        presentation = present_live_sync(controller)

        self.assertEqual("LOCKED", presentation.phase)
        self.assertEqual("G0B_LOCKED", presentation.reason_code)
        self.assertFalse(presentation.can_sync_now)
        self.assertFalse(presentation.can_cancel)

    def test_live_sync_presents_coordinator_and_snapshot_counts(self):
        state = type(
            "State",
            (),
            {
                "phase": "SUCCEEDED",
                "reason_code": "SYNC_SUCCEEDED",
                "elapsed_ms": 1250,
                "consecutive_failures": 0,
                "circuit_state": "CLOSED",
            },
        )()
        coordinator = type("Coordinator", (), {"state": state, "cancel": lambda self: True})()
        pipeline = type("Pipeline", (), {"coordinator": coordinator})()
        publish = type(
            "Publish", (), {"snapshot_id": "snap-1", "manifest": {"createdAt": "2026-07-17T12:00:00Z"}}
        )()
        result = type(
            "Result",
            (),
            {
                "publish": publish,
                "product_count": 12,
                "promotion_candidate_count": 3,
                "validation_issue_count": 1,
            },
        )()
        controller = type(
            "Controller", (), {"v2_pipeline": pipeline, "latest_v2_result": result}
        )()

        presentation = present_live_sync(controller)

        self.assertEqual("SUCCEEDED", presentation.phase)
        self.assertEqual("1.25 秒", presentation.elapsed_text)
        self.assertEqual("snap-1", presentation.snapshot_id)
        self.assertEqual("商品 12 / 候选 3 / 问题 1", presentation.counts_text)
        self.assertTrue(presentation.can_sync_now)
        self.assertTrue(presentation.can_cancel)


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
