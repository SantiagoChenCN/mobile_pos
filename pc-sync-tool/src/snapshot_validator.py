from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from types import MappingProxyType


class Severity(str, Enum):
    INFO = "INFO"
    WARNING = "WARNING"
    ERROR = "ERROR"
    FATAL = "FATAL"


@dataclass(frozen=True)
class IssuePolicy:
    severity: Severity
    rejects_snapshot: bool


SEVERITY_MATRIX = MappingProxyType(
    {
        "DUPLICATE_PRODUCT_KEY": IssuePolicy(Severity.FATAL, True),
        "EMPTY_BARCODE": IssuePolicy(Severity.WARNING, False),
        "DUPLICATE_BARCODE": IssuePolicy(Severity.ERROR, True),
        "MISSING_CATEGORY": IssuePolicy(Severity.WARNING, False),
        "MISSING_UNIT": IssuePolicy(Severity.WARNING, False),
        "UNKNOWN_STOP_FLAG": IssuePolicy(Severity.WARNING, False),
        "INVALID_PRODUCT_FIELD": IssuePolicy(Severity.FATAL, True),
        "INVALID_DECIMAL": IssuePolicy(Severity.FATAL, True),
        "MISSING_PRODUCT_NAME": IssuePolicy(Severity.ERROR, True),
        "UNKNOWN_PROMOTION_SOURCE": IssuePolicy(Severity.ERROR, False),
        "MISSING_PROMOTION_MASTER": IssuePolicy(Severity.ERROR, False),
        "MISSING_PROMOTION_DETAIL": IssuePolicy(Severity.ERROR, False),
        "MISSING_PROMOTION_PRODUCT": IssuePolicy(Severity.ERROR, False),
        "DUPLICATE_PROMOTION_KEY": IssuePolicy(Severity.ERROR, False),
        "INCOMPLETE_SIMPLE_FIELDS": IssuePolicy(Severity.WARNING, False),
    }
)


@dataclass(frozen=True)
class ValidationIssue:
    code: str
    severity: Severity
    rejects_snapshot: bool
    entity_key: str
    field: str | None = None


def issue(code: str, entity_key: str, field: str | None = None) -> ValidationIssue:
    try:
        policy = SEVERITY_MATRIX[code]
    except KeyError as exc:
        raise ValueError("validation issue code is not present in the fixed severity matrix") from exc
    return ValidationIssue(code, policy.severity, policy.rejects_snapshot, entity_key, field)


def snapshot_rejected(issues: tuple[ValidationIssue, ...] | list[ValidationIssue]) -> bool:
    return any(item.rejects_snapshot for item in issues)
