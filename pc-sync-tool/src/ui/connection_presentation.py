from __future__ import annotations

from dataclasses import dataclass

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
