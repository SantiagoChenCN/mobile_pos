from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Mapping, Sequence

from decimal_value import DecimalKind, DecimalValue
from snapshot_validator import ValidationIssue, issue, snapshot_rejected
from v2_contract import source_product_key


@dataclass(frozen=True)
class NormalizedProduct:
    source_product_key: str
    gid: int
    barcode: str | None
    name: str
    search_name: str | None
    category_code: str | None
    unit_code: str | None
    stop_flag: int | None
    sellable: bool
    cost_price_decimal: str | None
    sale_price_decimal: str | None
    simple_price_decimal: str | None
    simple_threshold_decimal: str | None
    simple_evidence_only: bool = True


@dataclass(frozen=True)
class ProductNormalizationResult:
    products: tuple[NormalizedProduct, ...]
    issues: tuple[ValidationIssue, ...]
    rejected: bool


def normalize_products(
    product_rows: Sequence[Mapping[str, Any]],
    category_rows: Sequence[Mapping[str, Any]],
    unit_rows: Sequence[Mapping[str, Any]],
) -> ProductNormalizationResult:
    category_codes = _known_text(category_rows, ("code",))
    unit_codes = _known_text(unit_rows, ("code", "name"))
    issues: list[ValidationIssue] = []
    products: list[NormalizedProduct] = []
    seen_gid: set[int] = set()
    seen_barcode: dict[str, str] = {}

    for row in product_rows:
        gid = row.get("gid")
        if isinstance(gid, bool) or not isinstance(gid, int) or gid <= 0:
            issues.append(issue("INVALID_PRODUCT_FIELD", "product:unknown", "gid"))
            continue
        entity_key = source_product_key(gid)
        if gid in seen_gid:
            issues.append(issue("DUPLICATE_PRODUCT_KEY", entity_key, "gid"))
            continue
        seen_gid.add(gid)

        barcode = _optional_text(row.get("barcode"), issues, entity_key, "barcode")
        if barcode is None:
            issues.append(issue("EMPTY_BARCODE", entity_key, "barcode"))
        elif barcode in seen_barcode:
            issues.append(issue("DUPLICATE_BARCODE", entity_key, "barcode"))
        else:
            seen_barcode[barcode] = entity_key

        name = _first_text(row.get("name_short"), row.get("name_full"), row.get("name_search"))
        if name is None:
            issues.append(issue("MISSING_PRODUCT_NAME", entity_key, "name"))
            name = ""
        search_name = _optional_text(row.get("name_search"), issues, entity_key, "name_search")
        category = _optional_text(row.get("category_code"), issues, entity_key, "category_code")
        unit = _optional_text(row.get("unit_code"), issues, entity_key, "unit_code")
        if category is None or category not in category_codes:
            issues.append(issue("MISSING_CATEGORY", entity_key, "category_code"))
            category = None
        if unit is None or unit not in unit_codes:
            issues.append(issue("MISSING_UNIT", entity_key, "unit_code"))
            unit = None

        stop_flag = row.get("stop_flag")
        if stop_flag is not None and (isinstance(stop_flag, bool) or not isinstance(stop_flag, int)):
            issues.append(issue("INVALID_PRODUCT_FIELD", entity_key, "stop_flag"))
            stop_flag = None
        if stop_flag is None:
            issues.append(issue("UNKNOWN_STOP_FLAG", entity_key, "stop_flag"))
        sellable = stop_flag == 0

        cost = _decimal(row.get("cost_price"), DecimalKind.MONEY, issues, entity_key, "cost_price")
        sale = _decimal(row.get("sale_price"), DecimalKind.MONEY, issues, entity_key, "sale_price")
        simple_price = _decimal(
            row.get("simple_price"), DecimalKind.MONEY, issues, entity_key, "simple_price"
        )
        simple_threshold = _decimal(
            row.get("simple_threshold"), DecimalKind.QUANTITY, issues, entity_key, "simple_threshold"
        )
        products.append(
            NormalizedProduct(
                entity_key,
                gid,
                barcode,
                name,
                search_name,
                category,
                unit,
                stop_flag,
                sellable,
                cost,
                sale,
                simple_price,
                simple_threshold,
                True,
            )
        )
    frozen_issues = tuple(issues)
    return ProductNormalizationResult(tuple(products), frozen_issues, snapshot_rejected(frozen_issues))


def _known_text(rows: Sequence[Mapping[str, Any]], fields: tuple[str, ...]) -> set[str]:
    result = set()
    for row in rows:
        for field in fields:
            value = row.get(field)
            if isinstance(value, str) and value.strip():
                result.add(value.strip())
    return result


def _first_text(*values: Any) -> str | None:
    for value in values:
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def _optional_text(
    value: Any,
    issues: list[ValidationIssue],
    entity_key: str,
    field: str,
) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        issues.append(issue("INVALID_PRODUCT_FIELD", entity_key, field))
        return None
    stripped = value.strip()
    return stripped or None


def _decimal(
    value: Any,
    kind: DecimalKind,
    issues: list[ValidationIssue],
    entity_key: str,
    field: str,
) -> str | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (Decimal, int)):
        issues.append(issue("INVALID_DECIMAL", entity_key, field))
        return None
    raw = format(value, "f") if isinstance(value, Decimal) else str(value)
    try:
        return DecimalValue.parse(raw, kind).canonical_text
    except ValueError:
        issues.append(issue("INVALID_DECIMAL", entity_key, field))
        return None
