from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from network import _is_lan_adapter, candidate_lan_hosts, is_phone_connectable_host, validate_phone_host


class NetworkTest(unittest.TestCase):
    def test_candidate_lan_hosts_excludes_loopback_and_link_local(self):
        addresses = [
            (2, 1, 6, "", ("127.0.0.1", 0)),
            (2, 1, 6, "", ("169.254.10.20", 0)),
            (2, 1, 6, "", ("224.0.0.1", 0)),
            (2, 1, 6, "", ("255.255.255.255", 0)),
            (2, 1, 6, "", ("240.0.0.1", 0)),
            (2, 1, 6, "", ("8.8.8.8", 0)),
            (2, 1, 6, "", ("10.0.0.8", 0)),
            (2, 1, 6, "", ("192.168.1.35", 0)),
        ]
        with patch("network._windows_adapter_ipv4_addresses", return_value=[]), patch(
            "network.socket.gethostname", return_value="checkout-pc"
        ), patch(
            "network.socket.getfqdn", return_value="checkout-pc.local"
        ), patch("network.socket.getaddrinfo", return_value=addresses):
            self.assertEqual(["10.0.0.8", "192.168.1.35"], candidate_lan_hosts())

    def test_phone_connectable_host_accepts_normal_lan_ipv4_addresses(self):
        for host in ("10.0.0.8", "172.16.0.1", "192.168.1.35"):
            with self.subTest(host=host):
                self.assertTrue(is_phone_connectable_host(host))

    def test_phone_connectable_host_rejects_non_connection_addresses(self):
        for host in (
            "127.0.0.1",
            "0.0.0.0",
            "169.254.10.20",
            "224.0.0.1",
            "255.255.255.255",
            "240.0.0.1",
            "8.8.8.8",
            "localhost",
        ):
            with self.subTest(host=host):
                self.assertFalse(is_phone_connectable_host(host))

    def test_host_validation_returns_actionable_reason(self):
        validation = validate_phone_host("127.0.0.1")

        self.assertFalse(validation.is_valid)
        self.assertEqual("LOOPBACK", validation.code)
        self.assertIn("不能供手机连接", validation.message)

    def test_virtual_and_non_lan_adapters_are_not_recommended(self):
        self.assertFalse(_is_lan_adapter(6, "VMware Virtual Ethernet Adapter"))
        self.assertFalse(_is_lan_adapter(131, "Microsoft Tunnel Adapter"))
        self.assertTrue(_is_lan_adapter(71, "Intel Wi-Fi 6 AX201"))


if __name__ == "__main__":
    unittest.main()
