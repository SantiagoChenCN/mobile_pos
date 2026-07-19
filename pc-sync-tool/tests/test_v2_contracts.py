from __future__ import annotations

import copy
import json
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from v2_contract import (
    derived_id,
    require_download_space,
    snapshot_download_path,
    snapshot_id,
    source_product_key,
    validate_derived_id,
    validate_snapshot_id,
    validate_soft_limits,
)
from v2_manifest import validate_v2_manifest


FIXTURES = Path(__file__).resolve().parent / "fixtures"
ANDROID_FIXTURES = ROOT.parent / "android-emergency-pos" / "core" / "src" / "test" / "resources" / "fixtures"


class V2ContractTest(unittest.TestCase):
    def test_id_vectors_and_path_whitelist(self):
        vectors = json.loads((FIXTURES / "v2_id_cases.json").read_text(encoding="utf-8"))
        self.assertEqual("ms2011:7318", source_product_key(7318))
        for vector in vectors["derived"]:
            actual = derived_id(vector["kind"], vector["sourceType"], vector["canonicalSourceKey"])
            self.assertEqual(vector["id"], actual)
            self.assertEqual(actual, validate_derived_id(vector["kind"], actual))
        snapshot = snapshot_id(
            datetime(2026, 7, 17, 18, 0, 1, tzinfo=timezone.utc),
            "abcdef123456" + "0" * 52,
        )
        self.assertEqual("ms2011-20260717T180001Z-abcdef123456", snapshot)
        self.assertEqual(snapshot, validate_snapshot_id(snapshot))
        self.assertEqual(f"/v2/snapshots/{snapshot}.db", snapshot_download_path(snapshot))
        for invalid in vectors["invalidSnapshotIds"]:
            with self.assertRaises(ValueError):
                validate_snapshot_id(invalid)

    def test_limits_only_reduce_and_disk_preflight_reserves_three_versions(self):
        self.assertEqual(1024, validate_soft_limits({"manifestBytes": 1024})["manifestBytes"])
        with self.assertRaises(ValueError):
            validate_soft_limits({"manifestBytes": 1024 * 1024 + 1})
        require_download_space(100, 25, 25, 25, 25)
        with self.assertRaises(ValueError):
            require_download_space(99, 25, 25, 25, 25)

    def test_manifest_fixture_and_all_invalid_mutations(self):
        valid_path = FIXTURES / "v2_manifest.json"
        data = json.loads(valid_path.read_text(encoding="utf-8"))
        self.assertEqual(data, validate_v2_manifest(data, len(valid_path.read_bytes())))
        invalid_cases = json.loads((FIXTURES / "v2_manifest_invalid_cases.json").read_text(encoding="utf-8"))
        for case in invalid_cases:
            mutated = copy.deepcopy(data)
            if case["operation"] == "set":
                mutated[case["field"]] = case["value"]
            elif case["operation"] == "remove":
                mutated.pop(case["field"])
            elif case["operation"] == "add":
                mutated[case["field"]] = case["value"]
            with self.subTest(case=case["name"]):
                with self.assertRaises(ValueError):
                    validate_v2_manifest(mutated)

    def test_manifest_int32_boundaries(self):
        data = json.loads((FIXTURES / "v2_manifest.json").read_text(encoding="utf-8"))
        max_values = copy.deepcopy(data)
        max_values.update(
            {
                "categoryCount": 2_147_483_647,
                "unitCount": 2_147_483_647,
                "minimumAppVersion": 2_147_483_647,
            }
        )
        self.assertEqual(max_values, validate_v2_manifest(max_values))

        lower_values = copy.deepcopy(data)
        lower_values.update({"categoryCount": 0, "unitCount": 0, "minimumAppVersion": 1})
        self.assertEqual(lower_values, validate_v2_manifest(lower_values))

    def test_all_shared_fixtures_are_byte_identical(self):
        for name in ("v2_id_cases.json", "v2_manifest.json", "v2_manifest_invalid_cases.json"):
            self.assertEqual((FIXTURES / name).read_bytes(), (ANDROID_FIXTURES / name).read_bytes())


if __name__ == "__main__":
    unittest.main()
