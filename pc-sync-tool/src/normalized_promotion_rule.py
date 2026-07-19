from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Mapping, Sequence

from decimal_value import DecimalKind, DecimalValue
from v2_contract import validate_derived_id


_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_DATE = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
_TIME = re.compile(r"^(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$")


@dataclass(frozen=True)
class NormalizedPromotionRule:
    rule_id: str
    candidate_id: str
    rule_type: str
    rule_version: str
    evidence_hash: str
    parameters: Mapping[str, Any]
    tiers: tuple[Mapping[str, Any], ...]
    schedules: tuple[Mapping[str, Any], ...]
    groups: tuple[Mapping[str, Any], ...]
    priority_order: int | None
    stack_mode: str

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "NormalizedPromotionRule":
        expected = {
            "ruleId", "candidateId", "ruleType", "ruleVersion", "evidenceHash",
            "parameters", "tiers", "schedules", "groups", "priorityOrder", "stackMode",
        }
        if not isinstance(value, Mapping) or set(value) != expected:
            raise ValueError("normalized rule fields do not match the frozen DTO")
        rule_id = _nonempty(value["ruleId"], "ruleId")
        candidate_id = validate_derived_id("candidate", value["candidateId"])
        rule_type = _nonempty(value["ruleType"], "ruleType")
        rule_version = _nonempty(value["ruleVersion"], "ruleVersion")
        evidence_hash = value["evidenceHash"]
        if not isinstance(evidence_hash, str) or not _SHA256.fullmatch(evidence_hash):
            raise ValueError("invalid evidence hash")
        parameters = value["parameters"]
        if not isinstance(parameters, Mapping):
            raise ValueError("parameters must be an object")
        json.dumps(parameters, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False)
        priority = value["priorityOrder"]
        if priority is not None and (isinstance(priority, bool) or not isinstance(priority, int)):
            raise ValueError("priorityOrder must be an integer or null")
        stack_mode = _nonempty(value["stackMode"], "stackMode")
        tiers = _validate_items(value["tiers"], _validate_tier)
        schedules = _validate_items(value["schedules"], _validate_schedule)
        groups = _validate_items(value["groups"], _validate_group)
        return cls(rule_id, candidate_id, rule_type, rule_version, evidence_hash, dict(parameters), tiers, schedules, groups, priority, stack_mode)


def _validate_items(value: Any, validator) -> tuple[Mapping[str, Any], ...]:
    if not isinstance(value, Sequence) or isinstance(value, (str, bytes)):
        raise ValueError("rule child collection must be an array")
    return tuple(validator(item) for item in value)


def _validate_tier(item: Any) -> Mapping[str, Any]:
    if not isinstance(item, Mapping) or set(item) != {"tierId", "thresholdDecimal", "valueKind", "valueDecimal", "tierOrder"}:
        raise ValueError("invalid tier")
    validate_derived_id("tier", item["tierId"])
    DecimalValue.parse(item["thresholdDecimal"], DecimalKind.QUANTITY)
    DecimalValue.parse(item["valueDecimal"], DecimalKind.MONEY)
    _order(item["tierOrder"])
    return dict(item)


def _validate_schedule(item: Any) -> Mapping[str, Any]:
    expected = {"scheduleId", "beginDateLocal", "endDateLocal", "weekday", "beginTimeLocal", "endTimeLocal", "scheduleOrder"}
    if not isinstance(item, Mapping) or set(item) != expected:
        raise ValueError("invalid schedule")
    validate_derived_id("schedule", item["scheduleId"])
    for key in ("beginDateLocal", "endDateLocal"):
        if item[key] is not None and (not isinstance(item[key], str) or not _DATE.fullmatch(item[key])):
            raise ValueError("invalid local date")
    for key in ("beginTimeLocal", "endTimeLocal"):
        if item[key] is not None and (not isinstance(item[key], str) or not _TIME.fullmatch(item[key])):
            raise ValueError("invalid local time")
    if item["weekday"] is not None:
        raise ValueError("weekday remains null until PV-ORDER evidence freezes its meaning")
    _order(item["scheduleOrder"])
    return dict(item)


def _validate_group(item: Any) -> Mapping[str, Any]:
    if not isinstance(item, Mapping) or set(item) != {"groupId", "groupCode", "requiredCountDecimal", "groupOrder"}:
        raise ValueError("invalid group")
    validate_derived_id("group", item["groupId"])
    _nonempty(item["groupCode"], "groupCode")
    DecimalValue.parse(item["requiredCountDecimal"], DecimalKind.QUANTITY)
    _order(item["groupOrder"])
    return dict(item)


def _order(value: Any) -> None:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError("order must be a non-negative integer")


def _nonempty(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value:
        raise ValueError(f"{field} must be a non-empty string")
    return value
