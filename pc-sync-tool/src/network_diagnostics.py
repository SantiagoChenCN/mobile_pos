from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Optional

from config import SyncConfig
from http_server import HTTP_BIND_HOST, SyncHttpService
from network import PhoneHostValidation, validate_phone_host


@dataclass(frozen=True)
class NetworkDiagnosticResult:
    service_running: bool
    local_health_ok: bool
    bind_host: str
    advertised_host: str
    host_validation: PhoneHostValidation
    port: int
    warning_code: str
    message: str


def diagnose_network(service: Optional[SyncHttpService], config: SyncConfig) -> NetworkDiagnosticResult:
    bind_host = service.bind_host if service is not None else HTTP_BIND_HOST
    port = service.actual_port if service is not None else config.port
    advertised_host = str(config.selected_host or "").strip()
    host_validation = validate_phone_host(advertised_host)
    if service is None:
        return NetworkDiagnosticResult(
            service_running=False,
            local_health_ok=False,
            bind_host=bind_host,
            advertised_host=advertised_host,
            host_validation=host_validation,
            port=port,
            warning_code="SERVICE_NOT_RUNNING",
            message="HTTP 服务未运行",
        )

    local_health_ok = _local_health_ok(port, config.token)
    if not local_health_ok:
        return NetworkDiagnosticResult(
            service_running=True,
            local_health_ok=False,
            bind_host=bind_host,
            advertised_host=advertised_host,
            host_validation=host_validation,
            port=port,
            warning_code="LOCAL_HEALTH_CHECK_FAILED",
            message="HTTP 服务已启动，但本机健康检查失败",
        )
    if not host_validation.is_valid:
        return NetworkDiagnosticResult(
            service_running=True,
            local_health_ok=True,
            bind_host=bind_host,
            advertised_host=advertised_host,
            host_validation=host_validation,
            port=port,
            warning_code="INVALID_ADVERTISED_HOST",
            message=host_validation.message,
        )
    return NetworkDiagnosticResult(
        service_running=True,
        local_health_ok=True,
        bind_host=bind_host,
        advertised_host=advertised_host,
        host_validation=host_validation,
        port=port,
        warning_code="",
        message="电脑端服务运行正常",
    )


def _local_health_ok(port: int, token: str) -> bool:
    query = urllib.parse.urlencode({"token": token})
    request_url = f"http://127.0.0.1:{port}/health?{query}"
    try:
        with urllib.request.urlopen(request_url, timeout=2) as response:
            if response.status != 200:
                return False
            payload = json.loads(response.read().decode("utf-8"))
    except (OSError, ValueError, json.JSONDecodeError, urllib.error.URLError):
        return False
    return payload.get("ok") is True and payload.get("app") == "MobilePosSync"
