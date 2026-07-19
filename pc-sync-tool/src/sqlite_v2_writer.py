from __future__ import annotations

import hashlib
import json
import sqlite3
import uuid
from dataclasses import dataclass
from datetime import date, datetime, time
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from paths import AppPaths
from promotion_raw_models import PromotionExtractionResult
from snapshot_normalizer import NormalizedProduct
from snapshot_validator import ValidationIssue
from tool_owned_path import ToolOwnedKind, ToolOwnedPath, unlink_tool_owned
from v2_contract import SCHEMA_VERSION, validate_snapshot_id


SCHEMA_SQL = """
PRAGMA foreign_keys = ON;
CREATE TABLE sync_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE categories (category_code TEXT PRIMARY KEY, category_name TEXT NOT NULL, source_order INTEGER NOT NULL);
CREATE TABLE units (unit_code TEXT PRIMARY KEY, unit_name TEXT NOT NULL, source_order INTEGER NOT NULL);
CREATE TABLE products (
 source_product_key TEXT PRIMARY KEY, gid TEXT NOT NULL, barcode TEXT NOT NULL,
 secondary_barcode TEXT, name TEXT NOT NULL, category_code TEXT, unit_code TEXT,
 sale_price_raw TEXT NOT NULL, sale_price_decimal TEXT NOT NULL,
 simple_price_raw TEXT, simple_price_decimal TEXT, simple_threshold_raw TEXT,
 simple_threshold_decimal TEXT, stop_flag INTEGER NOT NULL, source_update_time_local TEXT,
 FOREIGN KEY (category_code) REFERENCES categories(category_code) ON DELETE RESTRICT,
 FOREIGN KEY (unit_code) REFERENCES units(unit_code) ON DELETE RESTRICT
);
CREATE TABLE promotion_candidates (
 candidate_id TEXT PRIMARY KEY, source_type TEXT NOT NULL, source_key TEXT NOT NULL,
 verification_status TEXT NOT NULL CHECK (verification_status IN ('VERIFIED','UNVERIFIED','INVALID','UNKNOWN_TYPE','INACTIVE')),
 begin_local TEXT, end_local TEXT, normalized_rule_version TEXT, evidence_hash TEXT
);
CREATE TABLE promotion_candidate_products (
 mapping_id TEXT PRIMARY KEY, candidate_id TEXT NOT NULL, source_product_key TEXT,
 source_barcode TEXT, group_code TEXT, mapping_order INTEGER,
 FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE CASCADE,
 FOREIGN KEY (source_product_key) REFERENCES products(source_product_key) ON DELETE RESTRICT
);
CREATE TABLE promotion_raw_rows (
 raw_row_id TEXT PRIMARY KEY, candidate_id TEXT, source_table TEXT NOT NULL,
 source_key TEXT NOT NULL, source_order INTEGER NOT NULL, original_json TEXT NOT NULL,
 canonical_json TEXT NOT NULL,
 FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE CASCADE
);
CREATE TABLE promotion_rules (
 rule_id TEXT PRIMARY KEY, candidate_id TEXT NOT NULL UNIQUE, rule_type TEXT NOT NULL,
 rule_version TEXT NOT NULL, evidence_hash TEXT NOT NULL, parameters_json TEXT NOT NULL,
 priority_order INTEGER, stack_mode TEXT NOT NULL,
 FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE CASCADE
);
CREATE TABLE promotion_rule_tiers (
 tier_id TEXT PRIMARY KEY, rule_id TEXT NOT NULL, threshold_decimal TEXT NOT NULL,
 value_kind TEXT NOT NULL, value_decimal TEXT NOT NULL, tier_order INTEGER NOT NULL,
 FOREIGN KEY (rule_id) REFERENCES promotion_rules(rule_id) ON DELETE CASCADE
);
CREATE TABLE promotion_rule_schedules (
 schedule_id TEXT PRIMARY KEY, rule_id TEXT NOT NULL, begin_date_local TEXT,
 end_date_local TEXT, weekday INTEGER, begin_time_local TEXT, end_time_local TEXT,
 schedule_order INTEGER NOT NULL,
 FOREIGN KEY (rule_id) REFERENCES promotion_rules(rule_id) ON DELETE CASCADE
);
CREATE TABLE promotion_rule_groups (
 group_id TEXT PRIMARY KEY, rule_id TEXT NOT NULL, group_code TEXT NOT NULL,
 required_count_decimal TEXT NOT NULL, group_order INTEGER NOT NULL,
 FOREIGN KEY (rule_id) REFERENCES promotion_rules(rule_id) ON DELETE CASCADE
);
CREATE TABLE validation_issues (
 issue_id TEXT PRIMARY KEY, severity TEXT NOT NULL, issue_code TEXT NOT NULL,
 candidate_id TEXT, source_product_key TEXT, message_code TEXT NOT NULL,
 diagnostic_json TEXT NOT NULL,
 FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE SET NULL,
 FOREIGN KEY (source_product_key) REFERENCES products(source_product_key) ON DELETE SET NULL
);
CREATE INDEX idx_products_barcode ON products(barcode);
CREATE INDEX idx_candidate_products_candidate ON promotion_candidate_products(candidate_id, mapping_order);
CREATE INDEX idx_raw_rows_candidate ON promotion_raw_rows(candidate_id, source_order);
CREATE INDEX idx_tiers_rule ON promotion_rule_tiers(rule_id, tier_order);
CREATE INDEX idx_schedules_rule ON promotion_rule_schedules(rule_id, schedule_order);
CREATE INDEX idx_groups_rule ON promotion_rule_groups(rule_id, group_order);
CREATE INDEX idx_issues_candidate ON validation_issues(candidate_id);
"""

REQUIRED_COLUMNS = {
    "sync_metadata": {"key", "value"},
    "categories": {"category_code", "category_name", "source_order"},
    "units": {"unit_code", "unit_name", "source_order"},
    "products": {"source_product_key", "gid", "barcode", "sale_price_decimal", "stop_flag"},
    "promotion_candidates": {"candidate_id", "verification_status", "source_key"},
    "promotion_candidate_products": {"mapping_id", "candidate_id", "source_product_key"},
    "promotion_raw_rows": {"raw_row_id", "source_table", "source_key", "canonical_json"},
    "promotion_rules": {"rule_id", "candidate_id", "rule_type", "rule_version"},
    "promotion_rule_tiers": {"tier_id", "rule_id", "threshold_decimal"},
    "promotion_rule_schedules": {"schedule_id", "rule_id", "schedule_order"},
    "promotion_rule_groups": {"group_id", "rule_id", "group_order"},
    "validation_issues": {"issue_id", "severity", "issue_code"},
}


class SQLiteV2WriteError(RuntimeError):
    def __init__(self, reason_code: str):
        super().__init__(reason_code)
        self.reason_code = reason_code


@dataclass(frozen=True)
class SnapshotWriteInput:
    snapshot_id: str
    source_hash: str
    products: tuple[NormalizedProduct, ...]
    category_rows: tuple[Mapping[str, Any], ...]
    unit_rows: tuple[Mapping[str, Any], ...]
    promotion: PromotionExtractionResult
    issues: tuple[ValidationIssue, ...]


@dataclass(frozen=True)
class SQLiteV2WriteResult:
    snapshot_id: str
    temp_path: ToolOwnedPath
    size_bytes: int
    counts: Mapping[str, int]


class SQLiteV2Writer:
    def __init__(self, paths: AppPaths, nonce_factory: Callable[[], str] | None = None):
        self._paths = paths
        self._nonce_factory = nonce_factory or (lambda: uuid.uuid4().hex)

    def write(self, data: SnapshotWriteInput) -> SQLiteV2WriteResult:
        validate_snapshot_id(data.snapshot_id)
        if len(data.source_hash) != 64 or any(char not in "0123456789abcdef" for char in data.source_hash):
            raise ValueError("source_hash must be lowercase SHA-256")
        temp = self._paths.v2_unique_tmp(data.snapshot_id, self._nonce_factory())
        if temp.kind is not ToolOwnedKind.TMP:
            raise TypeError("writer requires a ToolOwnedPath TMP")
        temp.path.parent.mkdir(parents=True, exist_ok=True)
        connection = None
        counts = _expected_counts(data)
        try:
            connection = sqlite3.connect(temp.path)
            connection.execute("PRAGMA foreign_keys = ON")
            connection.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
            connection.executescript(SCHEMA_SQL)
            connection.execute("BEGIN IMMEDIATE")
            self._insert_all(connection, data)
            connection.commit()
            connection.close()
            connection = None
            verify_sqlite_v2(temp.path, counts)
            return SQLiteV2WriteResult(data.snapshot_id, temp, temp.path.stat().st_size, counts)
        except Exception as exc:
            if connection is not None:
                try:
                    connection.rollback()
                finally:
                    connection.close()
            try:
                unlink_tool_owned(temp)
            except Exception:
                pass
            if isinstance(exc, SQLiteV2WriteError):
                raise
            raise SQLiteV2WriteError("SQLITE_V2_WRITE_FAILED") from exc

    @staticmethod
    def _insert_all(connection: sqlite3.Connection, data: SnapshotWriteInput) -> None:
        connection.executemany(
            "INSERT INTO sync_metadata(key,value) VALUES (?,?)",
            (
                ("schemaVersion", str(SCHEMA_VERSION)),
                ("snapshotId", data.snapshot_id),
                ("sourceHash", data.source_hash),
            ),
        )
        categories = _category_records(data.category_rows)
        units = _unit_records(data.unit_rows)
        connection.executemany(
            "INSERT INTO categories(category_code,category_name,source_order) VALUES (?,?,?)",
            categories,
        )
        connection.executemany(
            "INSERT INTO units(unit_code,unit_name,source_order) VALUES (?,?,?)",
            units,
        )
        product_keys = {product.source_product_key for product in data.products}
        for product in data.products:
            if (
                product.barcode is None
                or not product.name
                or product.sale_price_decimal is None
                or product.stop_flag is None
            ):
                raise SQLiteV2WriteError("PRODUCT_REQUIRED_FIELD_MISSING")
            connection.execute(
                "INSERT INTO products(source_product_key,gid,barcode,secondary_barcode,name,category_code,unit_code,"
                "sale_price_raw,sale_price_decimal,simple_price_raw,simple_price_decimal,simple_threshold_raw,"
                "simple_threshold_decimal,stop_flag,source_update_time_local) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (
                    product.source_product_key,
                    str(product.gid),
                    product.barcode,
                    None,
                    product.name,
                    product.category_code,
                    product.unit_code,
                    product.sale_price_decimal,
                    product.sale_price_decimal,
                    product.simple_price_decimal,
                    product.simple_price_decimal,
                    product.simple_threshold_decimal,
                    product.simple_threshold_decimal,
                    product.stop_flag,
                    None,
                ),
            )

        candidate_by_source = {}
        for candidate in data.promotion.candidates:
            candidate_by_source[candidate.source_key] = candidate.candidate_id
            connection.execute(
                "INSERT INTO promotion_candidates(candidate_id,source_type,source_key,verification_status,"
                "begin_local,end_local,normalized_rule_version,evidence_hash) VALUES (?,?,?,?,?,?,?,?)",
                (
                    candidate.candidate_id,
                    candidate.candidate_type,
                    candidate.source_key,
                    candidate.status.value,
                    _local_text(candidate.raw_fields.get("begin_at")),
                    _local_text(candidate.raw_fields.get("end_at")),
                    None,
                    None,
                ),
            )
        for order, mapping in enumerate(data.promotion.mappings):
            source_product_key = f"ms2011:{mapping.product_id}"
            if source_product_key not in product_keys:
                raise SQLiteV2WriteError("MAPPING_PRODUCT_MISSING")
            connection.execute(
                "INSERT INTO promotion_candidate_products(mapping_id,candidate_id,source_product_key,"
                "source_barcode,group_code,mapping_order) VALUES (?,?,?,?,?,?)",
                (
                    mapping.mapping_id,
                    mapping.candidate_id,
                    source_product_key,
                    None,
                    None if mapping.group_code_raw is None else str(mapping.group_code_raw),
                    order,
                ),
            )
        for order, record in enumerate(data.promotion.raw_records):
            raw_id = "raw-" + hashlib.sha256(record.source_key.encode("utf-8")).hexdigest()[:24]
            canonical = _canonical_json(record.raw_fields)
            connection.execute(
                "INSERT INTO promotion_raw_rows(raw_row_id,candidate_id,source_table,source_key,source_order,"
                "original_json,canonical_json) VALUES (?,?,?,?,?,?,?)",
                (
                    raw_id,
                    candidate_by_source.get(record.source_key),
                    record.source_table,
                    record.source_key,
                    order,
                    canonical,
                    canonical,
                ),
            )
        all_issues = tuple(data.issues) + tuple(data.promotion.issues)
        candidate_ids = {item.candidate_id for item in data.promotion.candidates}
        for order, item in enumerate(all_issues):
            digest = hashlib.sha256(
                f"{order}\n{item.code}\n{item.entity_key}\n{item.field or ''}".encode("utf-8")
            ).hexdigest()[:24]
            candidate_id = candidate_by_source.get(item.entity_key)
            product_key = item.entity_key if item.entity_key in product_keys else None
            if candidate_id not in candidate_ids:
                candidate_id = None
            connection.execute(
                "INSERT INTO validation_issues(issue_id,severity,issue_code,candidate_id,source_product_key,"
                "message_code,diagnostic_json) VALUES (?,?,?,?,?,?,?)",
                (
                    "issue-" + digest,
                    item.severity.value,
                    item.code,
                    candidate_id,
                    product_key,
                    item.code,
                    "{}",
                ),
            )


def verify_sqlite_v2(path: Path, expected_counts: Mapping[str, int]) -> None:
    connection = sqlite3.connect(f"file:{Path(path).resolve().as_posix()}?mode=ro", uri=True)
    try:
        connection.execute("PRAGMA foreign_keys = ON")
        version = connection.execute("PRAGMA user_version").fetchone()[0]
        if version != SCHEMA_VERSION:
            raise SQLiteV2WriteError("SCHEMA_VERSION_MISMATCH")
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise SQLiteV2WriteError("INTEGRITY_CHECK_FAILED")
        if connection.execute("PRAGMA foreign_key_check").fetchall():
            raise SQLiteV2WriteError("FOREIGN_KEY_CHECK_FAILED")
        tables = {
            row[0]
            for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()
        }
        if tables != set(REQUIRED_COLUMNS):
            raise SQLiteV2WriteError("REQUIRED_TABLES_MISMATCH")
        for table, required in REQUIRED_COLUMNS.items():
            columns = {row[1] for row in connection.execute(f"PRAGMA table_info([{table}])").fetchall()}
            if not required.issubset(columns):
                raise SQLiteV2WriteError("REQUIRED_COLUMNS_MISSING")
        for table, expected in expected_counts.items():
            actual = connection.execute(f"SELECT COUNT(*) FROM [{table}]").fetchone()[0]
            if actual != expected:
                raise SQLiteV2WriteError("ROW_COUNT_MISMATCH")
    finally:
        connection.close()


def _expected_counts(data: SnapshotWriteInput) -> dict[str, int]:
    return {
        "categories": len(_category_records(data.category_rows)),
        "units": len(_unit_records(data.unit_rows)),
        "products": len(data.products),
        "promotion_candidates": len(data.promotion.candidates),
        "promotion_candidate_products": len(data.promotion.mappings),
        "promotion_raw_rows": len(data.promotion.raw_records),
        "promotion_rules": 0,
        "promotion_rule_tiers": 0,
        "promotion_rule_schedules": 0,
        "promotion_rule_groups": 0,
        "validation_issues": len(data.issues) + len(data.promotion.issues),
    }


def _category_records(rows: Sequence[Mapping[str, Any]]) -> tuple[tuple[str, str, int], ...]:
    records = []
    for order, row in enumerate(rows):
        code = _required_text(row.get("code"), "CATEGORY_CODE_MISSING")
        name = _required_text(row.get("name"), "CATEGORY_NAME_MISSING")
        records.append((code, name, order))
    return tuple(records)


def _unit_records(rows: Sequence[Mapping[str, Any]]) -> tuple[tuple[str, str, int], ...]:
    records = []
    for order, row in enumerate(rows):
        code = _required_text(row.get("code") or row.get("name"), "UNIT_CODE_MISSING")
        name = _required_text(row.get("name") or row.get("code"), "UNIT_NAME_MISSING")
        records.append((code, name, order))
    return tuple(records)


def _required_text(value: Any, reason_code: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise SQLiteV2WriteError(reason_code)
    return value.strip()


def _local_text(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, (datetime, date, time)):
        return value.isoformat()
    raise SQLiteV2WriteError("INVALID_LOCAL_TIME_VALUE")


def _canonical_json(value: Any) -> str:
    return json.dumps(
        _canonical(value), sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    )


def _canonical(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {str(key): _canonical(item) for key, item in value.items()}
    if isinstance(value, (tuple, list)):
        return [_canonical(item) for item in value]
    if isinstance(value, Decimal):
        return {"decimal": format(value, "f")}
    if isinstance(value, (datetime, date, time)):
        return {"local": value.isoformat()}
    if value is None or isinstance(value, (str, int, bool)):
        return value
    raise SQLiteV2WriteError("UNSUPPORTED_RAW_VALUE")
