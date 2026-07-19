from __future__ import annotations

import json
import re
from datetime import datetime, timezone
from typing import Any, Mapping

from v2_contract import SCHEMA_VERSION, SOURCE_TYPE, SOFT_LIMITS, snapshot_download_path, validate_snapshot_id


FIELDS = frozenset(
    {
        "ok",
        "schemaVersion",
        "snapshotId",
        "sourceType",
        "createdAtUtc",
        "sizeBytes",
        "sha256",
        "minimumAppVersion",
        "productCount",
        "categoryCount",
        "unitCount",
        "promotionCandidateCount",
        "verifiedPromotionCount",
        "validationIssueCount",
        "downloadPath",
    }
)
_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_INT32_MAX = 2_147_483_647


def validate_v2_manifest(data: Mapping[str, Any], encoded_size: int | None = None) -> dict[str, Any]:
    if not isinstance(data, Mapping) or set(data) != FIELDS:
        raise ValueError("v2 manifest fields do not match the frozen schema")
    if data["ok"] is not True:
        raise ValueError("ok must be JSON boolean true")
    if data["schemaVersion"] != SCHEMA_VERSION:
        raise ValueError("unsupported schemaVersion")
    snapshot = validate_snapshot_id(data["snapshotId"])
    if data["sourceType"] != SOURCE_TYPE:
        raise ValueError("invalid sourceType")
    _validate_utc_instant(data["createdAtUtc"])
    if not isinstance(data["sha256"], str) or not _SHA256.fullmatch(data["sha256"]):
        raise ValueError("sha256 must be lowercase hexadecimal")

    integer_fields = (
        "sizeBytes",
        "minimumAppVersion",
        "productCount",
        "categoryCount",
        "unitCount",
        "promotionCandidateCount",
        "verifiedPromotionCount",
        "validationIssueCount",
    )
    for field in integer_fields:
        value = data[field]
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            raise ValueError(f"{field} must be a non-negative JSON integer")
    if data["minimumAppVersion"] <= 0:
        raise ValueError("minimumAppVersion must be a positive versionCode")
    for field in ("categoryCount", "unitCount"):
        if data[field] > _INT32_MAX:
            raise ValueError(f"{field} exceeds the frozen int32 manifest limit")
    if data["minimumAppVersion"] > _INT32_MAX:
        raise ValueError("minimumAppVersion exceeds the frozen int32 manifest limit")
    if data["sizeBytes"] > SOFT_LIMITS["snapshotBytes"]:
        raise ValueError("snapshot exceeds soft limit")
    if data["productCount"] > SOFT_LIMITS["productCount"]:
        raise ValueError("product count exceeds soft limit")
    if data["promotionCandidateCount"] > SOFT_LIMITS["promotionCandidateCount"]:
        raise ValueError("promotion candidate count exceeds soft limit")
    if data["validationIssueCount"] > SOFT_LIMITS["validationIssueCount"]:
        raise ValueError("validation issue count exceeds soft limit")
    if data["verifiedPromotionCount"] > data["promotionCandidateCount"]:
        raise ValueError("verified promotion count exceeds candidate count")
    if data["downloadPath"] != snapshot_download_path(snapshot):
        raise ValueError("downloadPath does not match snapshotId")
    actual_size = encoded_size
    if actual_size is None:
        actual_size = len(json.dumps(dict(data), separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
    if actual_size > SOFT_LIMITS["manifestBytes"]:
        raise ValueError("manifest exceeds soft limit")
    return dict(data)


def _validate_utc_instant(value: Any) -> None:
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ValueError("createdAtUtc must be a UTC instant")
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError as exc:
        raise ValueError("createdAtUtc must be a UTC instant") from exc
    if parsed.utcoffset() != timezone.utc.utcoffset(None):
        raise ValueError("createdAtUtc must be UTC")
