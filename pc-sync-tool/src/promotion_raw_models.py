from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Mapping

from snapshot_validator import ValidationIssue


class CandidateStatus(str, Enum):
    UNVERIFIED = "UNVERIFIED"
    INACTIVE = "INACTIVE"


@dataclass(frozen=True)
class PromotionRawRecord:
    source_key: str
    source_table: str
    source_id: int
    raw_fields: Mapping[str, Any]


@dataclass(frozen=True)
class PromotionCandidate:
    candidate_id: str
    candidate_type: str
    status: CandidateStatus
    source_key: str
    source_table: str
    source_id: int
    associated_product_ids: tuple[int, ...]
    raw_fields: Mapping[str, Any]


@dataclass(frozen=True)
class PromotionProductMapping:
    mapping_id: str
    candidate_id: str
    source_key: str
    source_table: str
    source_id: int
    product_id: int
    group_code_raw: Any = None


@dataclass(frozen=True)
class PromotionExtractionResult:
    candidates: tuple[PromotionCandidate, ...]
    mappings: tuple[PromotionProductMapping, ...]
    raw_records: tuple[PromotionRawRecord, ...]
    issues: tuple[ValidationIssue, ...]
    normalized_rules: tuple[Any, ...] = ()
