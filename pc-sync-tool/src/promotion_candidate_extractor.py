from __future__ import annotations

from dataclasses import replace
from datetime import datetime
from decimal import Decimal
from types import MappingProxyType
from typing import Any, Mapping, Sequence

from decimal_value import DecimalKind, DecimalValue
from ms2011_query_catalog import QueryId
from promotion_raw_models import (
    CandidateStatus,
    PromotionCandidate,
    PromotionExtractionResult,
    PromotionProductMapping,
    PromotionRawRecord,
)
from snapshot_validator import ValidationIssue, issue
from v2_contract import derived_id


SOURCE_TABLE_BY_QUERY = MappingProxyType(
    {
        QueryId.PRODUCT_SIMPLE_CANDIDATES: "MS_GOODLIST",
        QueryId.PROMOTION_MAPPINGS: "MS_CUXIAO_GOOD",
        QueryId.QUANTITY_PERCENT_MASTERS: "MS_SALE_CXDAN1",
        QueryId.QUANTITY_PERCENT_DETAILS: "MS_SALE_CXDETAIL1",
        QueryId.QUANTITY_PERCENT_GLOBAL_RULES: "MS_SALE_CXTABLE1",
        QueryId.QUANTITY_PERCENT_SCHEDULES: "MS_SALE_WEEKDETAIL1",
        QueryId.QUANTITY_FIXED_MASTERS: "MS_SALE_CXMASTERDING",
        QueryId.QUANTITY_FIXED_DETAILS: "MS_SALE_CXTABLEDING",
        QueryId.QUANTITY_FIXED_SCHEDULES: "MS_SALE_WEEKDING",
        QueryId.MIX_MATCH_MASTERS: "MS_SALE_CXMASTERFOUR",
        QueryId.MIX_MATCH_PRODUCTS: "MS_SALE_CXTABLEFOUR",
    }
)

PRIMARY_FIELD_BY_QUERY = MappingProxyType(
    {
        QueryId.PRODUCT_SIMPLE_CANDIDATES: "gid",
        QueryId.PROMOTION_MAPPINGS: "xid",
        QueryId.QUANTITY_PERCENT_MASTERS: "master_id",
        QueryId.QUANTITY_PERCENT_DETAILS: "detail_id",
        QueryId.QUANTITY_PERCENT_GLOBAL_RULES: "rule_id",
        QueryId.QUANTITY_PERCENT_SCHEDULES: "schedule_id",
        QueryId.QUANTITY_FIXED_MASTERS: "master_id",
        QueryId.QUANTITY_FIXED_DETAILS: "detail_id",
        QueryId.QUANTITY_FIXED_SCHEDULES: "schedule_id",
        QueryId.MIX_MATCH_MASTERS: "master_id",
        QueryId.MIX_MATCH_PRODUCTS: "detail_id",
    }
)

COMPLEX_TYPES = (
    (
        "MS_SALE_CXDAN1",
        "QUANTITY_PERCENT",
        QueryId.QUANTITY_PERCENT_MASTERS,
        QueryId.QUANTITY_PERCENT_DETAILS,
        (QueryId.QUANTITY_PERCENT_GLOBAL_RULES, QueryId.QUANTITY_PERCENT_SCHEDULES),
    ),
    (
        "MS_SALE_CXMASTERDING",
        "QUANTITY_FIXED_TOTAL",
        QueryId.QUANTITY_FIXED_MASTERS,
        QueryId.QUANTITY_FIXED_DETAILS,
        (QueryId.QUANTITY_FIXED_SCHEDULES,),
    ),
    (
        "MS_SALE_CXMASTERFOUR",
        "MIX_MATCH_FIXED_TOTAL",
        QueryId.MIX_MATCH_MASTERS,
        QueryId.MIX_MATCH_PRODUCTS,
        (),
    ),
)

KNOWN_COMPLEX_TABLES = frozenset(item[0] for item in COMPLEX_TYPES)


def extract_promotion_candidates(
    product_rows: Sequence[Mapping[str, Any]],
    rows_by_query: Mapping[QueryId, Sequence[Mapping[str, Any]]],
    as_of_local: datetime,
) -> PromotionExtractionResult:
    if not isinstance(as_of_local, datetime):
        raise TypeError("as_of_local must be a datetime")
    product_ids = {
        int(row["gid"])
        for row in product_rows
        if isinstance(row.get("gid"), int) and not isinstance(row.get("gid"), bool) and row["gid"] > 0
    }
    issues: list[ValidationIssue] = []
    raw_records = _raw_records(rows_by_query, issues)
    candidates: list[PromotionCandidate] = []
    mappings: list[PromotionProductMapping] = []
    candidate_by_source: dict[tuple[str, int], PromotionCandidate] = {}

    for row in rows_by_query.get(QueryId.PRODUCT_SIMPLE_CANDIDATES, ()):
        gid = _positive_int(row.get("gid"))
        entity = f"MS_GOODLIST:{gid}" if gid else "MS_GOODLIST:unknown"
        if gid is None or gid not in product_ids:
            issues.append(issue("MISSING_PROMOTION_PRODUCT", entity, "gid"))
            continue
        price = _positive_decimal(row.get("simple_price"), DecimalKind.MONEY)
        threshold = _positive_decimal(row.get("simple_threshold"), DecimalKind.QUANTITY)
        if price is None or threshold is None:
            issues.append(issue("INCOMPLETE_SIMPLE_FIELDS", entity))
            continue
        candidate_id = derived_id("candidate", "MS_GOODLIST", str(gid))
        candidate = PromotionCandidate(
            candidate_id,
            "PRODUCT_SIMPLE",
            CandidateStatus.UNVERIFIED,
            entity,
            "MS_GOODLIST",
            gid,
            (gid,),
            MappingProxyType(
                {
                    "simple_price_decimal": price,
                    "simple_threshold_decimal": threshold,
                    "stop_flag_raw": row.get("stop_flag"),
                }
            ),
        )
        candidates.append(candidate)
        mappings.append(
            PromotionProductMapping(
                derived_id("mapping", "MS_GOODLIST", f"{gid}:simple"),
                candidate_id,
                f"MS_GOODLIST:{gid}:simple",
                "MS_GOODLIST",
                gid,
                gid,
            )
        )

    detail_query_by_table: dict[str, QueryId] = {}
    for source_table, candidate_type, master_query, detail_query, child_queries in COMPLEX_TYPES:
        detail_query_by_table[source_table] = detail_query
        detail_rows = rows_by_query.get(detail_query, ())
        detail_by_master = _group_by_master(detail_rows)
        child_by_master = {
            query_id: _group_by_master(rows_by_query.get(query_id, ())) for query_id in child_queries
        }
        for master in rows_by_query.get(master_query, ()):
            master_id = _positive_int(master.get("master_id"))
            if master_id is None:
                issues.append(issue("DUPLICATE_PROMOTION_KEY", f"{source_table}:unknown", "master_id"))
                continue
            source_key = f"{source_table}:{master_id}"
            candidate_id = derived_id("candidate", source_table, str(master_id))
            details = detail_by_master.get(master_id, ())
            if not details:
                issues.append(issue("MISSING_PROMOTION_DETAIL", source_key))
            associated = set()
            for detail in details:
                product_id = _positive_int(detail.get("product_id"))
                if product_id is None or product_id not in product_ids:
                    issues.append(issue("MISSING_PROMOTION_PRODUCT", source_key, "product_id"))
                    continue
                associated.add(product_id)
                detail_id = _positive_int(detail.get("detail_id"))
                if detail_id is not None:
                    detail_table = SOURCE_TABLE_BY_QUERY[detail_query]
                    detail_source_key = f"{detail_table}:{detail_id}"
                    mappings.append(
                        PromotionProductMapping(
                            derived_id("mapping", detail_table, str(detail_id)),
                            candidate_id,
                            detail_source_key,
                            detail_table,
                            detail_id,
                            product_id,
                        )
                    )
            status = _status_from_end(master.get("end_at"), as_of_local)
            raw_fields = {
                "begin_at": master.get("begin_at"),
                "end_at": master.get("end_at"),
                "detail_row_count": len(details),
                "child_row_counts": MappingProxyType(
                    {query_id.value: len(rows.get(master_id, ())) for query_id, rows in child_by_master.items()}
                ),
            }
            candidate = PromotionCandidate(
                candidate_id,
                candidate_type,
                status,
                source_key,
                source_table,
                master_id,
                tuple(sorted(associated)),
                MappingProxyType(raw_fields),
            )
            candidates.append(candidate)
            candidate_by_source[(source_table, master_id)] = candidate

    for row in rows_by_query.get(QueryId.PROMOTION_MAPPINGS, ()):
        xid = _positive_int(row.get("xid"))
        source_table = row.get("source_table")
        master_id = _positive_int(row.get("master_id"))
        product_id = _positive_int(row.get("product_id"))
        source_key = f"MS_CUXIAO_GOOD:{xid}" if xid else "MS_CUXIAO_GOOD:unknown"
        if not isinstance(source_table, str) or source_table not in KNOWN_COMPLEX_TABLES:
            issues.append(issue("UNKNOWN_PROMOTION_SOURCE", source_key, "source_table"))
            continue
        candidate = candidate_by_source.get((source_table, master_id or -1))
        if candidate is None:
            issues.append(issue("MISSING_PROMOTION_MASTER", source_key, "master_id"))
            continue
        if product_id is None or product_id not in product_ids:
            issues.append(issue("MISSING_PROMOTION_PRODUCT", source_key, "product_id"))
            continue
        if xid is None:
            issues.append(issue("DUPLICATE_PROMOTION_KEY", source_key, "xid"))
            continue
        mappings.append(
            PromotionProductMapping(
                derived_id("mapping", "MS_CUXIAO_GOOD", str(xid)),
                candidate.candidate_id,
                source_key,
                "MS_CUXIAO_GOOD",
                xid,
                product_id,
                row.get("group_code"),
            )
        )
        if product_id not in candidate.associated_product_ids:
            associated = tuple(sorted(set(candidate.associated_product_ids) | {product_id}))
            updated = replace(candidate, associated_product_ids=associated)
            candidates[candidates.index(candidate)] = updated
            candidate_by_source[(source_table, master_id)] = updated

    return PromotionExtractionResult(
        tuple(candidates), tuple(mappings), raw_records, tuple(issues), ()
    )


def _raw_records(
    rows_by_query: Mapping[QueryId, Sequence[Mapping[str, Any]]],
    issues: list[ValidationIssue],
) -> tuple[PromotionRawRecord, ...]:
    records = []
    seen = set()
    for query_id, source_table in SOURCE_TABLE_BY_QUERY.items():
        primary_field = PRIMARY_FIELD_BY_QUERY[query_id]
        for row in rows_by_query.get(query_id, ()):
            source_id = _positive_int(row.get(primary_field))
            if source_id is None:
                issues.append(issue("DUPLICATE_PROMOTION_KEY", f"{source_table}:unknown", primary_field))
                continue
            source_key = f"{source_table}:{source_id}"
            if source_key in seen:
                issues.append(issue("DUPLICATE_PROMOTION_KEY", source_key, primary_field))
            seen.add(source_key)
            records.append(
                PromotionRawRecord(source_key, source_table, source_id, MappingProxyType(dict(row)))
            )
    return tuple(records)


def _group_by_master(rows: Sequence[Mapping[str, Any]]) -> dict[int, tuple[Mapping[str, Any], ...]]:
    grouped: dict[int, list[Mapping[str, Any]]] = {}
    for row in rows:
        master_id = _positive_int(row.get("master_id"))
        if master_id is not None:
            grouped.setdefault(master_id, []).append(row)
    return {key: tuple(value) for key, value in grouped.items()}


def _positive_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int) and value > 0:
        return value
    if isinstance(value, str) and value.isascii() and value.isdigit() and not value.startswith("0"):
        return int(value)
    return None


def _positive_decimal(value: Any, kind: DecimalKind) -> str | None:
    if isinstance(value, bool) or not isinstance(value, (Decimal, int)):
        return None
    raw = format(value, "f") if isinstance(value, Decimal) else str(value)
    try:
        parsed = DecimalValue.parse(raw, kind)
    except ValueError:
        return None
    return parsed.canonical_text if parsed.value > 0 else None


def _status_from_end(value: Any, as_of_local: datetime) -> CandidateStatus:
    if isinstance(value, datetime):
        value_aware = value.tzinfo is not None and value.utcoffset() is not None
        as_of_aware = as_of_local.tzinfo is not None and as_of_local.utcoffset() is not None
        if value_aware == as_of_aware and value < as_of_local:
            return CandidateStatus.INACTIVE
    return CandidateStatus.UNVERIFIED
