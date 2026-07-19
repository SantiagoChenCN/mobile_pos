from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from datetime import datetime, timezone


SCHEMA_VERSION = 2
SOURCE_TYPE = "ms2011_live"

SOFT_LIMITS = {
    "manifestBytes": 256 * 1024,
    "snapshotBytes": 256 * 1024 * 1024,
    "productCount": 250_000,
    "promotionCandidateCount": 50_000,
    "validationIssueCount": 10_000,
}
HARD_LIMITS = {
    "manifestBytes": 1024 * 1024,
    "snapshotBytes": 1024 * 1024 * 1024,
    "productCount": 1_000_000,
    "promotionCandidateCount": 250_000,
    "validationIssueCount": 50_000,
}

_SOURCE_PRODUCT_KEY = re.compile(r"^ms2011:[1-9][0-9]*$")
_SNAPSHOT_ID = re.compile(r"^ms2011-([0-9]{8}T[0-9]{6}Z)-([0-9a-f]{12})$")
_DERIVED_IDS = {
    "candidate": re.compile(r"^pc-[0-9a-f]{24}$"),
    "mapping": re.compile(r"^map-[0-9a-f]{24}$"),
    "tier": re.compile(r"^tier-[0-9a-f]{24}$"),
    "schedule": re.compile(r"^schedule-[0-9a-f]{24}$"),
    "group": re.compile(r"^group-[0-9a-f]{24}$"),
}
_PREFIXES = {
    "candidate": "pc",
    "mapping": "map",
    "tier": "tier",
    "schedule": "schedule",
    "group": "group",
}


def source_product_key(gid: int) -> str:
    if isinstance(gid, bool) or not isinstance(gid, int) or gid <= 0:
        raise ValueError("GID must be a positive integer")
    return f"ms2011:{gid}"


def validate_source_product_key(value: str) -> str:
    if not isinstance(value, str) or not _SOURCE_PRODUCT_KEY.fullmatch(value):
        raise ValueError("invalid sourceProductKey")
    return value


def snapshot_id(created_at_utc: datetime, source_hash: str) -> str:
    if created_at_utc.tzinfo is None or created_at_utc.utcoffset() != timezone.utc.utcoffset(None):
        raise ValueError("snapshot time must be UTC")
    if not re.fullmatch(r"[0-9a-f]{64}", source_hash):
        raise ValueError("source hash must be lowercase SHA-256")
    timestamp = created_at_utc.replace(microsecond=0).strftime("%Y%m%dT%H%M%SZ")
    return f"ms2011-{timestamp}-{source_hash[:12]}"


def validate_snapshot_id(value: str) -> str:
    if not isinstance(value, str):
        raise ValueError("invalid snapshotId")
    match = _SNAPSHOT_ID.fullmatch(value)
    if match is None:
        raise ValueError("invalid snapshotId")
    try:
        datetime.strptime(match.group(1), "%Y%m%dT%H%M%SZ")
    except ValueError as exc:
        raise ValueError("invalid snapshot timestamp") from exc
    return value


def derived_id(kind: str, source_type: str, canonical_source_key: str) -> str:
    if kind not in _PREFIXES:
        raise ValueError("unknown derived ID kind")
    if not source_type or not canonical_source_key:
        raise ValueError("derived ID inputs cannot be empty")
    digest = hashlib.sha256(f"{source_type}\n{canonical_source_key}".encode("utf-8")).hexdigest()
    return f"{_PREFIXES[kind]}-{digest[:24]}"


def validate_derived_id(kind: str, value: str) -> str:
    pattern = _DERIVED_IDS.get(kind)
    if pattern is None or not isinstance(value, str) or not pattern.fullmatch(value):
        raise ValueError("invalid derived ID")
    return value


def snapshot_download_path(value: str) -> str:
    validate_snapshot_id(value)
    return f"/v2/snapshots/{value}.db"


def validate_soft_limits(overrides: dict[str, int] | None = None) -> dict[str, int]:
    effective = dict(SOFT_LIMITS)
    if overrides:
        for key, value in overrides.items():
            if key not in HARD_LIMITS or isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                raise ValueError("invalid soft limit override")
            if value > HARD_LIMITS[key]:
                raise ValueError("soft limit cannot exceed the compiled hard limit")
            effective[key] = value
    return effective


def require_download_space(
    available_bytes: int,
    incoming_bytes: int,
    active_bytes: int,
    pending_bytes: int,
    rollback_bytes: int,
) -> None:
    values = (available_bytes, incoming_bytes, active_bytes, pending_bytes, rollback_bytes)
    if any(isinstance(value, bool) or not isinstance(value, int) or value < 0 for value in values):
        raise ValueError("disk-space values must be non-negative integers")
    required = incoming_bytes + active_bytes + pending_bytes + rollback_bytes
    if available_bytes < required:
        raise ValueError("insufficient disk space for active, pending, and rollback snapshots")
