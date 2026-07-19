from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from network_diagnostics import NetworkDiagnosticResult


@dataclass(frozen=True)
class ConnectionPresentation:
    host: str
    port: int
    bind_address: str
    service_text: str
    local_health_text: str
    warning_text: str
    guidance_text: str
    can_copy_connection: bool


@dataclass(frozen=True)
class LiveSyncPresentation:
    phase: str
    reason_code: str
    elapsed_text: str
    consecutive_failures: int
    circuit_state: str
    last_success: str
    snapshot_id: str
    counts_text: str
    can_sync_now: bool
    can_cancel: bool


def present_live_sync(controller: Any) -> LiveSyncPresentation:
    pipeline = getattr(controller, "v2_pipeline", None)
    result = getattr(controller, "latest_v2_result", None)
    state = _coordinator_state(pipeline)
    if state is None:
        phase = "LOCKED" if pipeline is None else "IDLE"
        reason = "G0B_LOCKED" if pipeline is None else "READY"
        elapsed_ms = 0.0
        failures = 0
        circuit = "CLOSED"
    else:
        phase = _enum_text(getattr(state, "phase", "IDLE"))
        reason = str(getattr(state, "reason_code", ""))
        elapsed_ms = float(getattr(state, "elapsed_ms", 0.0) or 0.0)
        failures = int(getattr(state, "consecutive_failures", 0) or 0)
        circuit = _enum_text(getattr(state, "circuit_state", "CLOSED"))

    snapshot_id = "无"
    counts_text = "商品 0 / 候选 0 / 问题 0"
    last_success = "无"
    if result is not None:
        publish = getattr(result, "publish", None)
        snapshot_id = str(getattr(publish, "snapshot_id", "") or "无")
        counts_text = (
            f"商品 {int(getattr(result, 'product_count', 0) or 0)} / "
            f"候选 {int(getattr(result, 'promotion_candidate_count', 0) or 0)} / "
            f"问题 {int(getattr(result, 'validation_issue_count', 0) or 0)}"
        )
        manifest = getattr(publish, "manifest", {}) or {}
        last_success = str(manifest.get("createdAt") or manifest.get("created_at") or "已成功")

    coordinator = getattr(pipeline, "coordinator", None) if pipeline is not None else None
    can_cancel = bool(coordinator and callable(getattr(coordinator, "cancel", None)))
    return LiveSyncPresentation(
        phase=phase,
        reason_code=reason,
        elapsed_text=f"{elapsed_ms / 1000:.2f} 秒",
        consecutive_failures=failures,
        circuit_state=circuit,
        last_success=last_success,
        snapshot_id=snapshot_id,
        counts_text=counts_text,
        can_sync_now=pipeline is not None,
        can_cancel=can_cancel,
    )


def _coordinator_state(pipeline: Any):
    if pipeline is None:
        return None
    coordinator = getattr(pipeline, "coordinator", None)
    if coordinator is not None:
        return getattr(coordinator, "state", None)
    return getattr(pipeline, "state", None)


def _enum_text(value: Any) -> str:
    return str(getattr(value, "value", value))


def present_connection(diagnostic: NetworkDiagnosticResult) -> ConnectionPresentation:
    host_is_valid = diagnostic.host_validation.is_valid
    bind_address = f"{diagnostic.bind_host}:{diagnostic.port}"
    warning_text = _warning_text(diagnostic, host_is_valid)
    return ConnectionPresentation(
        host=diagnostic.advertised_host,
        port=diagnostic.port,
        bind_address=bind_address,
        service_text=_service_text(diagnostic),
        local_health_text=_local_health_text(diagnostic),
        warning_text=warning_text,
        guidance_text=_guidance_text(diagnostic, host_is_valid),
        can_copy_connection=diagnostic.service_running and host_is_valid,
    )


def _service_text(diagnostic: NetworkDiagnosticResult) -> str:
    if not diagnostic.service_running:
        return "HTTP 服务：未运行"
    return "HTTP 服务：运行中"


def _local_health_text(diagnostic: NetworkDiagnosticResult) -> str:
    if not diagnostic.service_running:
        return "本机健康检查：未执行"
    if diagnostic.local_health_ok:
        return "本机健康检查：通过"
    return "本机健康检查：失败"


def _warning_text(diagnostic: NetworkDiagnosticResult, host_is_valid: bool) -> str:
    if not host_is_valid:
        return diagnostic.host_validation.message
    if diagnostic.warning_code == "LOCAL_HEALTH_CHECK_FAILED":
        return "HTTP 服务已启动，但本机健康检查失败。"
    return ""


def _guidance_text(diagnostic: NetworkDiagnosticResult, host_is_valid: bool) -> str:
    if not diagnostic.service_running:
        return "请先启动 HTTP 服务，再在手机端测试连接。"
    if not diagnostic.local_health_ok:
        return "请检查端口设置和 HTTP 服务状态后重试。"
    if not host_is_valid:
        return "请从上方局域网 IP 列表中选择有效的 IPv4 地址。"
    return "如手机仍无法连接，请检查 Windows 防火墙、同一 Wi-Fi 和路由器客户端隔离。"
