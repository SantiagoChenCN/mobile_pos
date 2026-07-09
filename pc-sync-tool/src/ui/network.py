from __future__ import annotations

import socket
from typing import List


def candidate_lan_hosts() -> List[str]:
    hosts = {"127.0.0.1"}
    names = {socket.gethostname()}
    try:
        names.add(socket.getfqdn())
    except OSError:
        pass

    for name in names:
        try:
            for info in socket.getaddrinfo(name, None, socket.AF_INET, socket.SOCK_STREAM):
                address = info[4][0]
                if address and not address.startswith("169.254."):
                    hosts.add(address)
        except OSError:
            continue

    return sorted(hosts, key=lambda item: (item.startswith("127."), item))

