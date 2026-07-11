from __future__ import annotations

import ipaddress
import os
import socket
import ctypes
from dataclasses import dataclass
from typing import Iterable, Iterator, List, Optional


@dataclass(frozen=True)
class PhoneHostValidation:
    host: str
    is_valid: bool
    code: str
    message: str


def candidate_lan_hosts() -> List[str]:
    """Return recommended private LAN IPv4 addresses for phone connections."""
    hosts = {address for address in _resolved_ipv4_addresses() if is_phone_connectable_host(address)}
    return sorted(hosts, key=_host_sort_key)


def preferred_lan_host() -> Optional[str]:
    hosts = candidate_lan_hosts()
    return hosts[0] if hosts else None


def is_phone_connectable_host(host: str) -> bool:
    return validate_phone_host(host).is_valid


def validate_phone_host(host: str) -> PhoneHostValidation:
    """Validate an IPv4 address before it is shown as a phone connection target."""
    normalized = str(host or "").strip()
    try:
        address = ipaddress.IPv4Address(normalized)
    except ipaddress.AddressValueError:
        return _invalid_host(normalized, "INVALID_IPV4", "请选择电脑的局域网 IPv4。")
    if address.is_loopback:
        return _invalid_host(normalized, "LOOPBACK", "127.0.0.1 仅代表本机，不能供手机连接。")
    if address.is_unspecified:
        return _invalid_host(normalized, "UNSPECIFIED", "0.0.0.0 是监听地址，不能供手机连接。")
    if address.is_link_local:
        return _invalid_host(normalized, "LINK_LOCAL", "自动分配地址不能供手机稳定连接，请选择局域网 IPv4。")
    if address.is_multicast:
        return _invalid_host(normalized, "MULTICAST", "组播地址不能供手机连接，请选择局域网 IPv4。")
    if address.is_reserved or address == ipaddress.IPv4Address("255.255.255.255"):
        return _invalid_host(normalized, "BROADCAST_OR_RESERVED", "广播或保留地址不能供手机连接，请选择局域网 IPv4。")
    if not address.is_private:
        return _invalid_host(normalized, "NOT_PRIVATE_LAN", "请选择电脑的私有局域网 IPv4。")
    return PhoneHostValidation(normalized, True, "", "")


def _resolved_ipv4_addresses() -> Iterable[str]:
    windows_addresses = list(_windows_adapter_ipv4_addresses())
    if windows_addresses:
        yield from windows_addresses
        return

    names = {socket.gethostname()}
    try:
        names.add(socket.getfqdn())
    except OSError:
        pass

    for name in names:
        try:
            for info in socket.getaddrinfo(name, None, socket.AF_INET, socket.SOCK_STREAM):
                address = info[4][0]
                if address:
                    yield address
        except OSError:
            continue


def _windows_adapter_ipv4_addresses() -> Iterator[str]:
    if os.name != "nt":
        return
    try:
        yield from _read_windows_adapter_ipv4_addresses()
    except (AttributeError, OSError):
        return


def _read_windows_adapter_ipv4_addresses() -> Iterator[str]:
    get_adapters_info = ctypes.windll.iphlpapi.GetAdaptersInfo
    buffer_size = ctypes.c_ulong(0)
    if get_adapters_info(None, ctypes.byref(buffer_size)) not in (0, 111):
        return
    if not buffer_size.value:
        return

    buffer = ctypes.create_string_buffer(buffer_size.value)
    if get_adapters_info(buffer, ctypes.byref(buffer_size)) != 0:
        return

    adapter = ctypes.cast(buffer, ctypes.POINTER(_IpAdapterInfo))
    while adapter:
        info = adapter.contents
        description = _decode_c_string(info.Description)
        if _is_lan_adapter(info.Type, description):
            yield from _adapter_ipv4_addresses(info.IpAddressList)
        adapter = info.Next


def _adapter_ipv4_addresses(address: "_IpAddrString") -> Iterator[str]:
    current = ctypes.pointer(address)
    while current:
        host = _decode_c_string(current.contents.IpAddress)
        if host:
            yield host
        current = current.contents.Next


def _is_virtual_adapter(description: str) -> bool:
    normalized = description.casefold()
    virtual_markers = (
        "virtual",
        "vmware",
        "virtualbox",
        "hyper-v",
        "vbox",
        "tap",
        "tun",
        "vpn",
        "loopback",
        "pseudo",
        "docker",
        "wsl",
    )
    return any(marker in normalized for marker in virtual_markers)


def _is_lan_adapter(adapter_type: int, description: str) -> bool:
    return adapter_type in (6, 71) and not _is_virtual_adapter(description)


def _decode_c_string(value) -> str:
    return bytes(value).split(b"\0", 1)[0].decode("mbcs", errors="ignore")


def _host_sort_key(host: str) -> tuple[int, tuple[int, int, int, int]]:
    address = ipaddress.IPv4Address(host)
    return (0, tuple(int(part) for part in host.split(".")))


def _invalid_host(host: str, code: str, message: str) -> PhoneHostValidation:
    return PhoneHostValidation(host, False, code, message)


class _IpAddrString(ctypes.Structure):
    pass


_IpAddrString._fields_ = [
    ("Next", ctypes.POINTER(_IpAddrString)),
    ("IpAddress", ctypes.c_char * 16),
    ("IpMask", ctypes.c_char * 16),
    ("Context", ctypes.c_ulong),
]


class _IpAdapterInfo(ctypes.Structure):
    pass


_IpAdapterInfo._fields_ = [
    ("Next", ctypes.POINTER(_IpAdapterInfo)),
    ("ComboIndex", ctypes.c_ulong),
    ("AdapterName", ctypes.c_char * 260),
    ("Description", ctypes.c_char * 132),
    ("AddressLength", ctypes.c_uint),
    ("Address", ctypes.c_ubyte * 8),
    ("Index", ctypes.c_ulong),
    ("Type", ctypes.c_uint),
    ("DhcpEnabled", ctypes.c_uint),
    ("CurrentIpAddress", ctypes.POINTER(_IpAddrString)),
    ("IpAddressList", _IpAddrString),
    ("GatewayList", _IpAddrString),
    ("DhcpServer", _IpAddrString),
    ("HaveWins", ctypes.c_int),
    ("PrimaryWinsServer", _IpAddrString),
    ("SecondaryWinsServer", _IpAddrString),
    ("LeaseObtained", ctypes.c_longlong),
    ("LeaseExpires", ctypes.c_longlong),
]
