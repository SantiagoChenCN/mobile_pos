from __future__ import annotations

from urllib.parse import urlencode

from config import SyncConfig


def setup_url(config: SyncConfig) -> str:
    query = urlencode({
        "host": config.selected_host,
        "port": str(config.port),
        "token": config.token,
    })
    return "mobilepos-sync://setup?" + query
