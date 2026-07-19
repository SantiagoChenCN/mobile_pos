from __future__ import annotations

import hashlib
import json
import time
from datetime import date, datetime, time as time_value
from decimal import Decimal
from types import MappingProxyType
from typing import Any, Callable, Mapping

from ms2011_query_catalog import QueryId
from ms2011_raw_models import (
    FULL_READ_QUERY_IDS,
    MAX_ROW_COUNTS,
    PRIMARY_KEY_FIELDS,
    RawReadBatch,
    RawTable,
    TableReadMetric,
)
from read_only_ms2011_session import ReadOnlyMs2011Session


class RawReadError(RuntimeError):
    def __init__(self, reason_code: str, query_id: QueryId | None = None):
        super().__init__(reason_code)
        self.reason_code = reason_code
        self.query_id = query_id


class SourceChangedError(RawReadError):
    pass


class DeterministicMs2011Reader:
    def __init__(
        self,
        session: ReadOnlyMs2011Session,
        clock: Callable[[], float] = time.perf_counter,
    ):
        if not isinstance(session, ReadOnlyMs2011Session):
            raise TypeError("session must be ReadOnlyMs2011Session")
        self._session = session
        self._clock = clock

    def read_double(self) -> RawReadBatch:
        first_tables, first_metrics, first_hash = self._read_pass(1)
        second_tables, second_metrics, second_hash = self._read_pass(2)
        if first_hash != second_hash:
            raise SourceChangedError("DOUBLE_READ_MISMATCH")
        return RawReadBatch(
            MappingProxyType(second_tables),
            second_hash,
            first_metrics + second_metrics,
            True,
        )

    def _read_pass(
        self, pass_index: int
    ) -> tuple[dict[QueryId, RawTable], tuple[TableReadMetric, ...], str]:
        tables: dict[QueryId, RawTable] = {}
        metrics: list[TableReadMetric] = []
        try:
            for query_id in FULL_READ_QUERY_IDS:
                started = self._clock()
                rows = self._session.execute(query_id)
                elapsed = max(0.0, round((self._clock() - started) * 1000.0, 3))
                frozen_rows = tuple(MappingProxyType(dict(row)) for row in rows)
                self._validate_table(query_id, frozen_rows)
                table_hash = _hash_value(frozen_rows)
                tables[query_id] = RawTable(query_id, frozen_rows, table_hash)
                metrics.append(TableReadMetric(query_id, pass_index, len(rows), elapsed))
            self._validate_relations(tables)
        except RawReadError:
            raise
        except Exception as exc:
            current = query_id if "query_id" in locals() else None
            raise RawReadError("TABLE_READ_FAILED", current) from exc
        combined = _hash_value(
            tuple((query_id.value, tables[query_id].normalized_hash) for query_id in FULL_READ_QUERY_IDS)
        )
        return tables, tuple(metrics), combined

    @staticmethod
    def _validate_table(query_id: QueryId, rows: tuple[Mapping[str, Any], ...]) -> None:
        if len(rows) > MAX_ROW_COUNTS[query_id]:
            raise RawReadError("ROW_LIMIT_EXCEEDED", query_id)
        primary_key = PRIMARY_KEY_FIELDS[query_id]
        seen: set[int] = set()
        previous = 0
        for row in rows:
            if primary_key not in row:
                raise RawReadError("MISSING_PRIMARY_KEY", query_id)
            value = row[primary_key]
            if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                raise RawReadError("INVALID_PRIMARY_KEY", query_id)
            if value in seen:
                raise RawReadError("DUPLICATE_PRIMARY_KEY", query_id)
            if value <= previous:
                raise RawReadError("UNSTABLE_SORT_ORDER", query_id)
            seen.add(value)
            previous = value

    @staticmethod
    def _validate_relations(tables: Mapping[QueryId, RawTable]) -> None:
        product_ids = _ids(tables, QueryId.PRODUCTS, "gid")
        category_codes = _text_values(tables, QueryId.CATEGORIES, "code")
        unit_codes = _text_values(tables, QueryId.UNITS, "code") | _text_values(
            tables, QueryId.UNITS, "name"
        )
        for row in tables[QueryId.PRODUCTS].rows:
            category = _optional_text(row.get("category_code"))
            unit = _optional_text(row.get("unit_code"))
            if category and category not in category_codes:
                raise RawReadError("MISSING_CATEGORY_RELATION", QueryId.PRODUCTS)
            if unit and unit not in unit_codes:
                raise RawReadError("MISSING_UNIT_RELATION", QueryId.PRODUCTS)
        for row in tables[QueryId.PRODUCT_SIMPLE_CANDIDATES].rows:
            if row["gid"] not in product_ids:
                raise RawReadError("MISSING_PRODUCT_RELATION", QueryId.PRODUCT_SIMPLE_CANDIDATES)

        _validate_master_children(
            tables,
            QueryId.QUANTITY_PERCENT_MASTERS,
            (QueryId.QUANTITY_PERCENT_DETAILS, QueryId.QUANTITY_PERCENT_GLOBAL_RULES, QueryId.QUANTITY_PERCENT_SCHEDULES),
            product_ids,
        )
        _validate_master_children(
            tables,
            QueryId.QUANTITY_FIXED_MASTERS,
            (QueryId.QUANTITY_FIXED_DETAILS, QueryId.QUANTITY_FIXED_SCHEDULES),
            product_ids,
        )
        _validate_master_children(
            tables,
            QueryId.MIX_MATCH_MASTERS,
            (QueryId.MIX_MATCH_PRODUCTS,),
            product_ids,
        )


def _validate_master_children(
    tables: Mapping[QueryId, RawTable],
    master_query: QueryId,
    child_queries: tuple[QueryId, ...],
    product_ids: set[int],
) -> None:
    master_ids = _ids(tables, master_query, "master_id")
    for query_id in child_queries:
        for row in tables[query_id].rows:
            master_id = _canonical_positive_int(row.get("master_id"))
            if master_id not in master_ids:
                raise RawReadError("MISSING_MASTER_RELATION", query_id)
            if "product_id" in row:
                product_id = _canonical_positive_int(row.get("product_id"))
                if product_id not in product_ids:
                    raise RawReadError("MISSING_PRODUCT_RELATION", query_id)


def _ids(tables: Mapping[QueryId, RawTable], query_id: QueryId, field: str) -> set[int]:
    return {int(row[field]) for row in tables[query_id].rows}


def _text_values(tables: Mapping[QueryId, RawTable], query_id: QueryId, field: str) -> set[str]:
    return {
        value
        for row in tables[query_id].rows
        if (value := _optional_text(row.get(field))) is not None
    }


def _optional_text(value: Any) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise RawReadError("INVALID_TEXT_VALUE")
    stripped = value.strip()
    return stripped or None


def _canonical_positive_int(value: Any) -> int:
    if isinstance(value, bool):
        raise RawReadError("INVALID_RELATION_KEY")
    if isinstance(value, int) and value > 0:
        return value
    if isinstance(value, str) and value.isascii() and value.isdigit() and not value.startswith("0"):
        return int(value)
    raise RawReadError("INVALID_RELATION_KEY")


def _hash_value(value: Any) -> str:
    encoded = json.dumps(
        _canonical(value),
        ensure_ascii=True,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _canonical(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {str(key): _canonical(item) for key, item in value.items()}
    if isinstance(value, (tuple, list)):
        return [_canonical(item) for item in value]
    if isinstance(value, Decimal):
        return {"$decimal": str(value)}
    if isinstance(value, datetime):
        return {"$datetime": value.isoformat(timespec="microseconds")}
    if isinstance(value, date):
        return {"$date": value.isoformat()}
    if isinstance(value, time_value):
        return {"$time": value.isoformat(timespec="microseconds")}
    if value is None or isinstance(value, (str, int, bool)):
        return value
    raise RawReadError("UNSUPPORTED_SOURCE_TYPE")
