from __future__ import annotations

from dataclasses import dataclass
from typing import Dict

from config import SyncConfig
from network import is_phone_connectable_host


@dataclass(frozen=True)
class ConnectionInfo:
    host: str
    port: int
    token: str

    def as_dict(self) -> Dict[str, str]:
        if not self.is_copyable:
            return {}
        return {
            "host": self.host,
            "port": str(self.port),
            "token": self.token,
        }

    @property
    def is_copyable(self) -> bool:
        return is_phone_connectable_host(self.host)

    def summary(self) -> str:
        return "\n".join([
            "电脑IP：" + _display_host(self.host),
            "端口：" + str(self.port),
            "Token：" + self.token,
        ])


def connection_info(config: SyncConfig) -> ConnectionInfo:
    return ConnectionInfo(
        host=connection_host(config),
        port=connection_port(config),
        token=connection_token(config),
    )


def connection_host(config: SyncConfig) -> str:
    return (config.selected_host or "127.0.0.1").strip() or "127.0.0.1"


def connection_port(config: SyncConfig) -> int:
    return int(config.port)


def connection_token(config: SyncConfig) -> str:
    return str(config.token or "").strip()


def connection_summary(config: SyncConfig) -> str:
    return connection_info(config).summary()


def _display_host(host: str) -> str:
    if is_phone_connectable_host(host):
        return host
    return "请在电脑工具中选择局域网 IPv4"
