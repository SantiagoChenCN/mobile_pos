from __future__ import annotations

from dataclasses import dataclass
from types import MappingProxyType
from typing import Any, Mapping

from ms2011_query_catalog import QueryId


FULL_READ_QUERY_IDS = (
    QueryId.PRODUCTS,
    QueryId.CATEGORIES,
    QueryId.UNITS,
    QueryId.PRODUCT_SIMPLE_CANDIDATES,
    QueryId.PROMOTION_MAPPINGS,
    QueryId.QUANTITY_PERCENT_MASTERS,
    QueryId.QUANTITY_PERCENT_DETAILS,
    QueryId.QUANTITY_PERCENT_GLOBAL_RULES,
    QueryId.QUANTITY_PERCENT_SCHEDULES,
    QueryId.QUANTITY_FIXED_MASTERS,
    QueryId.QUANTITY_FIXED_DETAILS,
    QueryId.QUANTITY_FIXED_SCHEDULES,
    QueryId.MIX_MATCH_MASTERS,
    QueryId.MIX_MATCH_PRODUCTS,
)

PRIMARY_KEY_FIELDS = MappingProxyType(
    {
        QueryId.PRODUCTS: "gid",
        QueryId.CATEGORIES: "rid",
        QueryId.UNITS: "uid",
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

MAX_ROW_COUNTS = MappingProxyType(
    {
        QueryId.PRODUCTS: 1_000_000,
        QueryId.CATEGORIES: 100_000,
        QueryId.UNITS: 100_000,
        QueryId.PRODUCT_SIMPLE_CANDIDATES: 250_000,
        QueryId.PROMOTION_MAPPINGS: 250_000,
        QueryId.QUANTITY_PERCENT_MASTERS: 250_000,
        QueryId.QUANTITY_PERCENT_DETAILS: 1_000_000,
        QueryId.QUANTITY_PERCENT_GLOBAL_RULES: 1_000_000,
        QueryId.QUANTITY_PERCENT_SCHEDULES: 1_000_000,
        QueryId.QUANTITY_FIXED_MASTERS: 250_000,
        QueryId.QUANTITY_FIXED_DETAILS: 1_000_000,
        QueryId.QUANTITY_FIXED_SCHEDULES: 1_000_000,
        QueryId.MIX_MATCH_MASTERS: 250_000,
        QueryId.MIX_MATCH_PRODUCTS: 1_000_000,
    }
)


@dataclass(frozen=True)
class TableReadMetric:
    query_id: QueryId
    pass_index: int
    row_count: int
    elapsed_ms: float


@dataclass(frozen=True)
class RawTable:
    query_id: QueryId
    rows: tuple[Mapping[str, Any], ...]
    normalized_hash: str


@dataclass(frozen=True)
class RawReadBatch:
    tables: Mapping[QueryId, RawTable]
    source_hash: str
    metrics: tuple[TableReadMetric, ...]
    double_read_matched: bool

    def rows(self, query_id: QueryId) -> tuple[Mapping[str, Any], ...]:
        return self.tables[query_id].rows
