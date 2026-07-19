from __future__ import annotations

import argparse
import importlib
import json
import platform
import re
import struct
import sys
import time
from contextlib import suppress
from dataclasses import dataclass
from decimal import Decimal
from types import MappingProxyType
from typing import Any, Callable


TABLES = (
    "MS_GOODLIST",
    "MS_GOODTYPELIST",
    "MS_UNITLIST",
    "MS_CUXIAO_GOOD",
    "MS_SALE_CXDAN1",
    "MS_SALE_CXDETAIL1",
    "MS_SALE_CXMASTERDING",
    "MS_SALE_CXMASTERFOUR",
    "MS_SALE_CXTABLE1",
    "MS_SALE_CXTABLEDING",
    "MS_SALE_CXTABLEFOUR",
    "MS_SALE_WEEKDETAIL1",
    "MS_SALE_WEEKDING",
)

CANDIDATE_KEYS = MappingProxyType(
    {
        "MS_GOODLIST": ("GID",),
        "MS_GOODTYPELIST": ("RID",),
        "MS_UNITLIST": ("UID",),
        "MS_CUXIAO_GOOD": ("XID",),
        "MS_SALE_CXDAN1": ("CXDID",),
        "MS_SALE_CXDETAIL1": ("CXGID",),
        "MS_SALE_CXMASTERDING": ("MID",),
        "MS_SALE_CXMASTERFOUR": ("M4ID",),
        "MS_SALE_CXTABLE1": ("CXTID",),
        "MS_SALE_CXTABLEDING": ("CID",),
        "MS_SALE_CXTABLEFOUR": ("C4ID",),
        "MS_SALE_WEEKDETAIL1": ("WD1ID",),
        "MS_SALE_WEEKDING": ("MDID",),
    }
)

_TABLE_NAME_LITERALS = ", ".join(f"'{table}'" for table in TABLES)
IDENTITY_SQL = "SELECT DB_NAME(), @@SERVERNAME"
METADATA_SQL = (
    "SELECT o.name, c.colid, c.name, t.name, c.length, c.prec, c.scale, c.isnullable "
    "FROM dbo.sysobjects o "
    "INNER JOIN dbo.syscolumns c ON c.id = o.id "
    "INNER JOIN dbo.systypes t ON t.xusertype = c.xusertype "
    "WHERE o.xtype = 'U' AND o.uid = USER_ID('dbo') "
    f"AND o.name IN ({_TABLE_NAME_LITERALS}) "
    "ORDER BY o.name, c.colid"
)


def _stats_sql(table: str, key: str) -> str:
    return (
        f"SELECT COUNT(*), COUNT([{key}]), COUNT(DISTINCT [{key}]), "
        f"MIN([{key}]), MAX([{key}]) FROM dbo.[{table}] WITH (NOLOCK)"
    )


STATS_SQL = MappingProxyType(
    {table: _stats_sql(table, CANDIDATE_KEYS[table][0]) for table in TABLES}
)

CONNECT_TIMEOUT_MIN = 1
CONNECT_TIMEOUT_MAX = 30
QUERY_TIMEOUT_MIN = 1
QUERY_TIMEOUT_MAX = 300
ODBC_SQL_MODE_READ_ONLY = 1

SERVER_PATTERN = re.compile(r"^[A-Za-z0-9_.\\,-]+$")
DATABASE_PATTERN = re.compile(r"^[A-Za-z0-9_]+$")
SQLSTATE_PATTERN = re.compile(r"^[A-Z0-9]{5}$")
BRACKETED_SQLSTATE_PATTERN = re.compile(r"\[([A-Z0-9]{5})\]")

SAFE_MESSAGES = MappingProxyType(
    {
        "INVALID_ARGUMENT": "A command argument is invalid.",
        "PYODBC_UNAVAILABLE": "The packaged ODBC module is unavailable.",
        "NO_DRIVER": "The requested ODBC driver is not visible to this process.",
        "BITNESS_MISMATCH": "The ODBC driver and process architecture are incompatible.",
        "LOGIN_FAILED": "Windows integrated authentication was rejected.",
        "PERMISSION_DENIED": "The integrated identity lacks permission for a fixed read-only query.",
        "CONNECTION_TIMEOUT": "The ODBC connection timed out.",
        "QUERY_TIMEOUT": "A fixed read-only query timed out.",
        "PROTOCOL_FAILED": "The ODBC protocol or SQL Server 2000 compatibility check failed.",
        "IDENTITY_MISMATCH": "The connected database identity is not the required MS2011 database.",
        "SCHEMA_REVIEW_REQUIRED": "Statistics require explicit approval after schema review.",
        "MISSING_TABLES": "One or more required candidate tables are missing.",
        "INVALID_RESPONSE": "A fixed query returned an unexpected response shape.",
        "UNKNOWN": "The read-only schema diagnostic failed.",
    }
)


@dataclass(frozen=True)
class SchemaConfig:
    server: str
    database: str
    driver: str
    connect_timeout_seconds: int
    query_timeout_seconds: int


@dataclass(frozen=True)
class ErrorDetail:
    category: str
    phase: str
    message: str
    sqlstate: str | None = None


class DiagnosticFailure(Exception):
    def __init__(self, detail: ErrorDetail):
        super().__init__(detail.message)
        self.detail = detail


class JsonArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        raise ValueError(message)


def runtime_metadata() -> dict[str, Any]:
    return {
        "frozen": bool(getattr(sys, "frozen", False)),
        "processBits": struct.calcsize("P") * 8,
        "pythonBits": struct.calcsize("P") * 8,
        "pythonVersion": platform.python_version(),
    }


def describe_contract() -> dict[str, Any]:
    return {
        "status": "ok",
        "mode": "describe-contract",
        "runtime": runtime_metadata(),
        "tables": list(TABLES),
        "candidateKeys": {table: list(CANDIDATE_KEYS[table]) for table in TABLES},
        "queryModes": ["schema", "stats"],
        "statsRequiresSchemaReviewed": True,
    }


def _failure(category: str, phase: str, sqlstate: str | None = None) -> DiagnosticFailure:
    return DiagnosticFailure(ErrorDetail(category, phase, SAFE_MESSAGES[category], sqlstate))


def _error_result(detail: ErrorDetail, mode: str, started: float | None = None) -> dict[str, Any]:
    result: dict[str, Any] = {
        "status": "error",
        "mode": mode,
        "runtime": runtime_metadata(),
        "error": {
            "category": detail.category,
            "phase": detail.phase,
            "message": detail.message,
        },
    }
    if detail.sqlstate:
        result["error"]["sqlstate"] = detail.sqlstate
    if started is not None:
        result["durationMs"] = round((time.perf_counter() - started) * 1000, 3)
    return result


def load_pyodbc():
    try:
        return importlib.import_module("pyodbc")
    except ImportError as exc:
        raise _failure("PYODBC_UNAVAILABLE", "import") from exc


def _module_or_error(pyodbc_module, loader: Callable[[], Any]):
    if pyodbc_module is not None:
        return pyodbc_module
    try:
        return loader()
    except DiagnosticFailure:
        raise
    except ImportError as exc:
        raise _failure("PYODBC_UNAVAILABLE", "import") from exc


def validate_config(config: SchemaConfig) -> None:
    if not isinstance(config.server, str) or not SERVER_PATTERN.fullmatch(config.server.strip()):
        raise _failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.database, str) or not DATABASE_PATTERN.fullmatch(config.database.strip()):
        raise _failure("INVALID_ARGUMENT", "validate")
    if config.database.strip().upper() != "MS2011":
        raise _failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.driver, str) or not config.driver.strip():
        raise _failure("INVALID_ARGUMENT", "validate")
    if any(character in config.driver for character in ";{}\r\n"):
        raise _failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.connect_timeout_seconds, int) or isinstance(config.connect_timeout_seconds, bool):
        raise _failure("INVALID_ARGUMENT", "validate")
    if not CONNECT_TIMEOUT_MIN <= config.connect_timeout_seconds <= CONNECT_TIMEOUT_MAX:
        raise _failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.query_timeout_seconds, int) or isinstance(config.query_timeout_seconds, bool):
        raise _failure("INVALID_ARGUMENT", "validate")
    if not QUERY_TIMEOUT_MIN <= config.query_timeout_seconds <= QUERY_TIMEOUT_MAX:
        raise _failure("INVALID_ARGUMENT", "validate")


def build_connection_string(config: SchemaConfig) -> str:
    return (
        f"DRIVER={{{config.driver}}};"
        f"SERVER={config.server};"
        f"DATABASE={config.database};"
        "Trusted_Connection=yes;"
        "APP=MS2011SchemaReadOnlyProbe"
    )


def _extract_sqlstate(error: BaseException) -> str | None:
    for value in getattr(error, "args", ()):
        text = str(value).upper()
        if SQLSTATE_PATTERN.fullmatch(text):
            return text
        match = BRACKETED_SQLSTATE_PATTERN.search(text)
        if match:
            return match.group(1)
    return None


def classify_odbc_error(error: BaseException, phase: str) -> ErrorDetail:
    sqlstate = _extract_sqlstate(error)
    text = " ".join(str(value) for value in getattr(error, "args", ())).upper()
    if sqlstate in {"IM002", "IM003"}:
        category = "NO_DRIVER"
    elif sqlstate in {"IM014"} or "ARCHITECTURE MISMATCH" in text:
        category = "BITNESS_MISMATCH"
    elif sqlstate == "28000":
        category = "LOGIN_FAILED"
    elif sqlstate in {"42000", "37000"}:
        category = "PERMISSION_DENIED"
    elif sqlstate in {"HYT00", "HYT01", "S1T00"}:
        category = "QUERY_TIMEOUT" if phase.startswith("query:") else "CONNECTION_TIMEOUT"
    elif sqlstate and sqlstate.startswith("08"):
        category = "PROTOCOL_FAILED"
    else:
        category = "UNKNOWN"
    return ErrorDetail(category, phase, SAFE_MESSAGES[category], sqlstate)


def _connect(config: SchemaConfig, module):
    visible = [str(driver) for driver in module.drivers()]
    if config.driver not in visible:
        raise _failure("NO_DRIVER", "driver-enumeration")
    try:
        access_mode = module.SQL_ATTR_ACCESS_MODE
    except AttributeError as exc:
        raise _failure("PROTOCOL_FAILED", "connect") from exc
    connection = module.connect(
        build_connection_string(config),
        timeout=config.connect_timeout_seconds,
        autocommit=True,
        attrs_before={access_mode: ODBC_SQL_MODE_READ_ONLY},
    )
    try:
        connection.timeout = config.query_timeout_seconds
        return connection
    except Exception:
        with suppress(Exception):
            connection.close()
        raise


def _database_identity(row: Any, phase: str) -> dict[str, str]:
    if row is None or len(row) != 2:
        raise _failure("INVALID_RESPONSE", phase)
    database, server = row
    if not isinstance(database, str) or database.upper() != "MS2011":
        raise _failure("IDENTITY_MISMATCH", phase)
    if not isinstance(server, str) or not server.strip():
        raise _failure("INVALID_RESPONSE", phase)
    return {"database": database, "server": server}


def _column_from_row(row: Any) -> tuple[str, dict[str, Any]]:
    if row is None or len(row) != 8:
        raise _failure("INVALID_RESPONSE", "query:schema")
    table, ordinal, name, type_name, length, precision, scale, nullable = row
    if table not in TABLES:
        raise _failure("INVALID_RESPONSE", "query:schema")
    if not isinstance(ordinal, int) or isinstance(ordinal, bool) or ordinal <= 0:
        raise _failure("INVALID_RESPONSE", "query:schema")
    if not isinstance(name, str) or not name:
        raise _failure("INVALID_RESPONSE", "query:schema")
    if not isinstance(type_name, str) or not type_name:
        raise _failure("INVALID_RESPONSE", "query:schema")
    for value in (length, precision, scale):
        if value is not None and (not isinstance(value, int) or isinstance(value, bool)):
            raise _failure("INVALID_RESPONSE", "query:schema")
    if nullable not in (0, 1, False, True):
        raise _failure("INVALID_RESPONSE", "query:schema")
    return str(table), {
        "ordinal": ordinal,
        "name": name,
        "type": type_name,
        "length": length,
        "precision": precision,
        "scale": scale,
        "nullable": bool(nullable),
    }


def _normalize_schema(rows: list[Any]) -> tuple[list[dict[str, Any]], bool]:
    grouped: dict[str, list[dict[str, Any]]] = {table: [] for table in TABLES}
    for raw in rows:
        table, column = _column_from_row(raw)
        grouped[table].append(column)

    missing = [table for table in TABLES if not grouped[table]]
    if missing:
        raise _failure("MISSING_TABLES", "query:schema")

    result = []
    requires_mapping_review = False
    for table in TABLES:
        columns = sorted(grouped[table], key=lambda item: item["ordinal"])
        ordinals = [column["ordinal"] for column in columns]
        names = [column["name"] for column in columns]
        if len(ordinals) != len(set(ordinals)) or len(names) != len(set(names)):
            raise _failure("INVALID_RESPONSE", "query:schema")
        candidate = CANDIDATE_KEYS[table]
        candidate_present = all(key in names for key in candidate)
        if not candidate_present:
            requires_mapping_review = True
        result.append(
            {
                "table": table,
                "candidateKey": list(candidate),
                "candidateKeyPresent": candidate_present,
                "columns": columns,
            }
        )
    return result, requires_mapping_review


def _base_success(mode: str, config: SchemaConfig, started: float) -> dict[str, Any]:
    return {
        "status": "ok",
        "mode": mode,
        "runtime": runtime_metadata(),
        "server": config.server,
        "database": config.database,
        "driver": config.driver,
        "durationMs": round((time.perf_counter() - started) * 1000, 3),
    }


def run_schema(
    config: SchemaConfig,
    pyodbc_module=None,
    loader: Callable[[], Any] = load_pyodbc,
) -> dict[str, Any]:
    connection = None
    cursor = None
    started = time.perf_counter()
    phase = "validate"
    try:
        validate_config(config)
        module = _module_or_error(pyodbc_module, loader)
        phase = "connect"
        connection = _connect(config, module)
        cursor = connection.cursor()
        phase = "query:identity"
        cursor.execute(IDENTITY_SQL)
        identity = _database_identity(cursor.fetchone(), phase)
        phase = "query:schema"
        cursor.execute(METADATA_SQL)
        tables, review = _normalize_schema(list(cursor.fetchall()))
        result = _base_success("schema", config, started)
        result["databaseIdentity"] = identity
        result["tables"] = tables
        result["requiresMappingReview"] = review
        return result
    except DiagnosticFailure as exc:
        return _error_result(exc.detail, "schema", started)
    except Exception as exc:
        return _error_result(classify_odbc_error(exc, phase), "schema", started)
    finally:
        if cursor is not None:
            with suppress(Exception):
                cursor.close()
        if connection is not None:
            with suppress(Exception):
                connection.close()


def _stats_from_row(table: str, row: Any) -> dict[str, Any]:
    if row is None or len(row) != 5:
        raise _failure("INVALID_RESPONSE", f"query:stats:{table}")
    row_count, non_null_count, distinct_count, minimum, maximum = row
    counts = (row_count, non_null_count, distinct_count)
    if any(not isinstance(value, int) or isinstance(value, bool) or value < 0 for value in counts):
        raise _failure("INVALID_RESPONSE", f"query:stats:{table}")
    if not distinct_count <= non_null_count <= row_count:
        raise _failure("INVALID_RESPONSE", f"query:stats:{table}")
    if row_count == 0:
        key_status = "EMPTY_TABLE"
    elif row_count == non_null_count == distinct_count:
        key_status = "VERIFIED_SINGLE_KEY"
    else:
        key_status = "NEEDS_COMPOSITE_KEY"

    def json_safe_key(value: Any) -> Any:
        if value is None or isinstance(value, (str, int, float, bool)):
            return value
        if isinstance(value, Decimal):
            return format(value, "f")
        raise _failure("INVALID_RESPONSE", f"query:stats:{table}")

    return {
        "table": table,
        "candidateKey": list(CANDIDATE_KEYS[table]),
        "rowCount": row_count,
        "nonNullKeyCount": non_null_count,
        "distinctKeyCount": distinct_count,
        "minKey": json_safe_key(minimum),
        "maxKey": json_safe_key(maximum),
        "keyStatus": key_status,
    }


def run_stats(
    config: SchemaConfig,
    pyodbc_module=None,
    loader: Callable[[], Any] = load_pyodbc,
    schema_reviewed: bool = False,
) -> dict[str, Any]:
    connection = None
    cursor = None
    started = time.perf_counter()
    phase = "validate"
    try:
        if schema_reviewed is not True:
            raise _failure("SCHEMA_REVIEW_REQUIRED", "validate")
        validate_config(config)
        module = _module_or_error(pyodbc_module, loader)
        phase = "connect"
        connection = _connect(config, module)
        cursor = connection.cursor()
        phase = "query:identity"
        cursor.execute(IDENTITY_SQL)
        identity = _database_identity(cursor.fetchone(), phase)
        tables = []
        for table, sql in STATS_SQL.items():
            phase = f"query:stats:{table}"
            cursor.execute(sql)
            tables.append(_stats_from_row(table, cursor.fetchone()))
        result = _base_success("stats", config, started)
        result["databaseIdentity"] = identity
        result["tables"] = tables
        result["requiresCompositeKeyEvidence"] = any(
            table["keyStatus"] == "NEEDS_COMPOSITE_KEY" for table in tables
        )
        return result
    except DiagnosticFailure as exc:
        return _error_result(exc.detail, "stats", started)
    except Exception as exc:
        return _error_result(classify_odbc_error(exc, phase), "stats", started)
    finally:
        if cursor is not None:
            with suppress(Exception):
                cursor.close()
        if connection is not None:
            with suppress(Exception):
                connection.close()


def exit_code_for(result: dict[str, Any]) -> int:
    if result.get("status") == "ok":
        return 0
    category = str(result.get("error", {}).get("category", "UNKNOWN"))
    if category == "INVALID_ARGUMENT":
        return 2
    if category in {"PYODBC_UNAVAILABLE", "NO_DRIVER", "BITNESS_MISMATCH"}:
        return 3
    if category in {
        "LOGIN_FAILED",
        "PERMISSION_DENIED",
        "CONNECTION_TIMEOUT",
        "PROTOCOL_FAILED",
    }:
        return 4
    if category in {
        "QUERY_TIMEOUT",
        "IDENTITY_MISMATCH",
        "SCHEMA_REVIEW_REQUIRED",
        "MISSING_TABLES",
        "INVALID_RESPONSE",
    }:
        return 5
    return 1


def build_parser() -> argparse.ArgumentParser:
    parser = JsonArgumentParser(description="Fixed-query MS2011 read-only schema diagnostic")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--describe-contract", action="store_true")
    mode.add_argument("--schema", action="store_true")
    mode.add_argument("--stats", action="store_true")
    parser.add_argument("--schema-reviewed", action="store_true")
    parser.add_argument("--server")
    parser.add_argument("--database", default="MS2011")
    parser.add_argument("--driver")
    parser.add_argument("--connect-timeout-seconds", type=int, default=5)
    parser.add_argument("--query-timeout-seconds", type=int, default=10)
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        args = build_parser().parse_args(argv)
        if args.describe_contract:
            result = describe_contract()
        else:
            if not args.server or not args.driver:
                raise ValueError("--server and --driver are required")
            config = SchemaConfig(
                server=args.server,
                database=args.database,
                driver=args.driver,
                connect_timeout_seconds=args.connect_timeout_seconds,
                query_timeout_seconds=args.query_timeout_seconds,
            )
            result = (
                run_schema(config)
                if args.schema
                else run_stats(config, schema_reviewed=args.schema_reviewed)
            )
    except (TypeError, ValueError):
        result = _error_result(
            ErrorDetail("INVALID_ARGUMENT", "arguments", SAFE_MESSAGES["INVALID_ARGUMENT"]),
            "arguments",
        )
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return exit_code_for(result)


if __name__ == "__main__":
    raise SystemExit(main())
