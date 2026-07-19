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
from enum import Enum
from types import MappingProxyType
from typing import Any, Callable


class QueryId(str, Enum):
    DATABASE_NAME = "DATABASE_NAME"
    PRODUCT_COUNT = "PRODUCT_COUNT"


QUERY_SQL = MappingProxyType(
    {
        QueryId.DATABASE_NAME: "SELECT DB_NAME()",
        QueryId.PRODUCT_COUNT: "SELECT COUNT(*) FROM dbo.MS_GOODLIST",
    }
)

CONNECT_TIMEOUT_MIN = 1
CONNECT_TIMEOUT_MAX = 30
QUERY_TIMEOUT_MIN = 1
QUERY_TIMEOUT_MAX = 300
# ODBC SQL_MODE_READ_ONLY is defined as 1. pyodbc 5.3.0 exposes
# SQL_ATTR_ACCESS_MODE but not this value as a named module constant.
ODBC_SQL_MODE_READ_ONLY = 1

SERVER_PATTERN = re.compile(r"^[A-Za-z0-9_.\\,-]+$")
DATABASE_PATTERN = re.compile(r"^[A-Za-z0-9_]+$")
SQLSTATE_PATTERN = re.compile(r"^[A-Z0-9]{5}$")
BRACKETED_SQLSTATE_PATTERN = re.compile(r"\[([A-Z0-9]{5})\]")


@dataclass(frozen=True)
class ProbeConfig:
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

    def to_json(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "category": self.category,
            "phase": self.phase,
            "message": self.message,
        }
        if self.sqlstate:
            payload["sqlstate"] = self.sqlstate
        return payload


class ProbeFailure(Exception):
    def __init__(self, detail: ErrorDetail):
        super().__init__(detail.message)
        self.detail = detail


class JsonArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        raise ValueError(message)


SAFE_MESSAGES = {
    "PYODBC_UNAVAILABLE": "pyodbc is not installed in this runtime.",
    "NO_DRIVER": "The requested ODBC driver is not visible to this process.",
    "BITNESS_MISMATCH": "The ODBC driver and process architecture are incompatible.",
    "LOGIN_FAILED": "Windows integrated authentication was rejected.",
    "PERMISSION_DENIED": "The integrated identity lacks permission for the fixed read-only query.",
    "CONNECTION_TIMEOUT": "The ODBC connection timed out.",
    "QUERY_TIMEOUT": "A fixed read-only query timed out.",
    "PROTOCOL_FAILED": "The ODBC connection protocol failed.",
    "INVALID_RESPONSE": "A fixed read-only query returned an invalid response.",
    "INVALID_ARGUMENT": "One or more Probe arguments are invalid.",
    "UNKNOWN": "The ODBC Probe failed for an unclassified reason.",
}


def runtime_metadata() -> dict[str, Any]:
    bits = struct.calcsize("P") * 8
    return {
        "pythonVersion": platform.python_version(),
        "pythonBits": bits,
        "processBits": bits,
        "frozen": bool(getattr(sys, "frozen", False)),
    }


def load_pyodbc():
    return importlib.import_module("pyodbc")


def _error_result(detail: ErrorDetail, mode: str) -> dict[str, Any]:
    return {
        "status": "error",
        "mode": mode,
        "runtime": runtime_metadata(),
        "error": detail.to_json(),
    }


def _probe_failure(category: str, phase: str, sqlstate: str | None = None) -> ProbeFailure:
    return ProbeFailure(
        ErrorDetail(
            category=category,
            phase=phase,
            message=SAFE_MESSAGES[category],
            sqlstate=sqlstate,
        )
    )


def _module_or_error(pyodbc_module, loader: Callable[[], Any]):
    if pyodbc_module is not None:
        return pyodbc_module
    try:
        return loader()
    except (ImportError, ModuleNotFoundError) as exc:
        raise _probe_failure("PYODBC_UNAVAILABLE", "load") from exc


def list_drivers(pyodbc_module=None, loader: Callable[[], Any] = load_pyodbc) -> dict[str, Any]:
    try:
        module = _module_or_error(pyodbc_module, loader)
        drivers = [str(driver) for driver in module.drivers()]
        return {
            "status": "ok",
            "mode": "list-drivers",
            **runtime_metadata(),
            "drivers": drivers,
        }
    except ProbeFailure as exc:
        return _error_result(exc.detail, "list-drivers")
    except Exception as exc:
        detail = classify_odbc_error(exc, "list-drivers")
        return _error_result(detail, "list-drivers")


def validate_config(config: ProbeConfig) -> None:
    if not isinstance(config.server, str) or not SERVER_PATTERN.fullmatch(config.server.strip()):
        raise _probe_failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.database, str) or not DATABASE_PATTERN.fullmatch(config.database.strip()):
        raise _probe_failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.driver, str) or not config.driver.strip():
        raise _probe_failure("INVALID_ARGUMENT", "validate")
    if any(character in config.driver for character in ";{}\r\n"):
        raise _probe_failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.connect_timeout_seconds, int) or isinstance(config.connect_timeout_seconds, bool):
        raise _probe_failure("INVALID_ARGUMENT", "validate")
    if not CONNECT_TIMEOUT_MIN <= config.connect_timeout_seconds <= CONNECT_TIMEOUT_MAX:
        raise _probe_failure("INVALID_ARGUMENT", "validate")
    if not isinstance(config.query_timeout_seconds, int) or isinstance(config.query_timeout_seconds, bool):
        raise _probe_failure("INVALID_ARGUMENT", "validate")
    if not QUERY_TIMEOUT_MIN <= config.query_timeout_seconds <= QUERY_TIMEOUT_MAX:
        raise _probe_failure("INVALID_ARGUMENT", "validate")


def build_connection_string(config: ProbeConfig) -> str:
    return (
        f"DRIVER={{{config.driver}}};"
        f"SERVER={config.server};"
        f"DATABASE={config.database};"
        "Trusted_Connection=yes;"
        "APP=MS2011ReadOnlyProbe"
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
    if sqlstate in {"IM002", "IM003"}:
        category = "NO_DRIVER"
    elif sqlstate == "IM014":
        category = "BITNESS_MISMATCH"
    elif sqlstate == "28000":
        category = "LOGIN_FAILED"
    elif sqlstate in {"42000", "42501"}:
        category = "PERMISSION_DENIED"
    elif sqlstate in {"HYT00", "HYT01", "S1T00"}:
        category = "QUERY_TIMEOUT" if phase.startswith("query:") else "CONNECTION_TIMEOUT"
    elif sqlstate and sqlstate.startswith("08"):
        category = "PROTOCOL_FAILED"
    else:
        category = "UNKNOWN"
    return ErrorDetail(
        category=category,
        phase=phase,
        message=SAFE_MESSAGES[category],
        sqlstate=sqlstate,
    )


def _validate_database_name(row) -> str:
    if row is None or len(row) != 1 or not isinstance(row[0], str) or not row[0].strip():
        raise _probe_failure("INVALID_RESPONSE", "query:DATABASE_NAME")
    return row[0].strip()


def _validate_product_count(row) -> int:
    if (
        row is None
        or len(row) != 1
        or not isinstance(row[0], int)
        or isinstance(row[0], bool)
        or row[0] < 0
    ):
        raise _probe_failure("INVALID_RESPONSE", "query:PRODUCT_COUNT")
    return row[0]


def run_probe(
    config: ProbeConfig,
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
        visible = [str(driver) for driver in module.drivers()]
        if config.driver not in visible:
            raise _probe_failure("NO_DRIVER", "driver-enumeration")

        try:
            access_mode = module.SQL_ATTR_ACCESS_MODE
        except AttributeError as exc:
            raise _probe_failure("PROTOCOL_FAILED", "connect") from exc

        phase = "connect"
        connection = module.connect(
            build_connection_string(config),
            timeout=config.connect_timeout_seconds,
            autocommit=True,
            attrs_before={access_mode: ODBC_SQL_MODE_READ_ONLY},
        )
        connection.timeout = config.query_timeout_seconds
        cursor = connection.cursor()

        results: dict[str, Any] = {}
        for query_id, sql in QUERY_SQL.items():
            phase = f"query:{query_id.value}"
            cursor.execute(sql)
            row = cursor.fetchone()
            if query_id is QueryId.DATABASE_NAME:
                results[query_id.value] = _validate_database_name(row)
            else:
                results[query_id.value] = _validate_product_count(row)

        return {
            "status": "ok",
            "mode": "probe",
            "runtime": runtime_metadata(),
            "driver": config.driver,
            "server": config.server,
            "database": config.database,
            "results": results,
            "durationMs": round((time.perf_counter() - started) * 1000, 3),
        }
    except ProbeFailure as exc:
        result = _error_result(exc.detail, "probe")
        result["durationMs"] = round((time.perf_counter() - started) * 1000, 3)
        return result
    except Exception as exc:
        result = _error_result(classify_odbc_error(exc, phase), "probe")
        result["durationMs"] = round((time.perf_counter() - started) * 1000, 3)
        return result
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
    if category in {"QUERY_TIMEOUT", "INVALID_RESPONSE"}:
        return 5
    return 1


def build_parser() -> argparse.ArgumentParser:
    parser = JsonArgumentParser(description="Fixed-query MS2011 read-only ODBC compatibility Probe")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--list-drivers", action="store_true")
    mode.add_argument("--probe", action="store_true")
    parser.add_argument("--server")
    parser.add_argument("--database", default="MS2011")
    parser.add_argument("--driver")
    parser.add_argument("--connect-timeout-seconds", type=int, default=5)
    parser.add_argument("--query-timeout-seconds", type=int, default=10)
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        args = build_parser().parse_args(argv)
        if args.list_drivers:
            result = list_drivers()
        else:
            if not args.server or not args.driver:
                raise ValueError("--server and --driver are required with --probe")
            result = run_probe(
                ProbeConfig(
                    server=args.server,
                    database=args.database,
                    driver=args.driver,
                    connect_timeout_seconds=args.connect_timeout_seconds,
                    query_timeout_seconds=args.query_timeout_seconds,
                )
            )
    except (TypeError, ValueError):
        result = _error_result(
            ErrorDetail(
                category="INVALID_ARGUMENT",
                phase="arguments",
                message=SAFE_MESSAGES["INVALID_ARGUMENT"],
            ),
            "arguments",
        )
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return exit_code_for(result)


if __name__ == "__main__":
    raise SystemExit(main())
