from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import platform
import re
import struct
import sys
import time
from contextlib import suppress
from dataclasses import dataclass
from types import MappingProxyType
from typing import Any, Callable


REQUIRED_TABLES = (
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

SERVER_ROLES = (
    "sysadmin",
    "securityadmin",
    "serveradmin",
    "setupadmin",
    "processadmin",
    "dbcreator",
    "diskadmin",
    "bulkadmin",
)
DATABASE_ROLES = (
    "db_owner",
    "db_accessadmin",
    "db_securityadmin",
    "db_ddladmin",
    "db_backupoperator",
    "db_datareader",
    "db_datawriter",
    "db_denydatareader",
    "db_denydatawriter",
)
ALL_ROLES = SERVER_ROLES + DATABASE_ROLES

DIRECT_WRITE_SERVER_ROLES = (
    "sysadmin",
    "securityadmin",
    "dbcreator",
    "bulkadmin",
)
DIRECT_WRITE_DATABASE_ROLES = (
    "db_owner",
    "db_accessadmin",
    "db_securityadmin",
    "db_ddladmin",
    "db_datawriter",
)
AMBIGUOUS_ELEVATED_ROLES = (
    "serveradmin",
    "setupadmin",
    "processadmin",
    "diskadmin",
    "db_backupoperator",
)

# SQL Server 2000 PERMISSIONS() bitmap values.
SELECT_ALL_BIT = 1
UPDATE_ALL_BIT = 2
INSERT_BIT = 8
DELETE_BIT = 16
EXECUTE_BIT = 32
SELECT_ANY_BIT = 4096
UPDATE_ANY_BIT = 8192

CREATE_TABLE_BIT = 2
CREATE_PROCEDURE_BIT = 4
CREATE_VIEW_BIT = 8
CREATE_RULE_BIT = 16
CREATE_DEFAULT_BIT = 32
BACKUP_DATABASE_BIT = 64
BACKUP_LOG_BIT = 128

OBJECT_WRITE_BITS = UPDATE_ALL_BIT | INSERT_BIT | DELETE_BIT | UPDATE_ANY_BIT
STATEMENT_DDL_BITS = (
    CREATE_TABLE_BIT
    | CREATE_PROCEDURE_BIT
    | CREATE_VIEW_BIT
    | CREATE_RULE_BIT
    | CREATE_DEFAULT_BIT
)
STATEMENT_BACKUP_BITS = BACKUP_DATABASE_BIT | BACKUP_LOG_BIT
KNOWN_STATEMENT_BITS = STATEMENT_DDL_BITS | STATEMENT_BACKUP_BITS

IDENTITY_SQL = "SELECT DB_NAME(), SYSTEM_USER, USER_NAME()"
ROLE_SQL = "SELECT " + ", ".join(
    [f"IS_SRVROLEMEMBER('{role}')" for role in SERVER_ROLES]
    + [f"IS_MEMBER('{role}')" for role in DATABASE_ROLES]
)
STATEMENT_PERMISSIONS_SQL = "SELECT PERMISSIONS()"
REQUIRED_TABLE_PERMISSIONS_SQL = " UNION ALL ".join(
    [
        (
            f"SELECT '{table}', PERMISSIONS(OBJECT_ID('dbo.{table}'))"
            if index
            else f"SELECT '{table}' AS table_name, PERMISSIONS(OBJECT_ID('dbo.{table}')) AS permission_mask"
        )
        for index, table in enumerate(REQUIRED_TABLES)
    ]
)
OBJECT_PERMISSIONS_SQL = (
    "SELECT o.xtype, PERMISSIONS(o.id) "
    "FROM dbo.sysobjects o "
    "WHERE o.xtype IN ('U', 'V', 'P', 'X', 'RF') "
    "AND OBJECTPROPERTY(o.id, 'IsMSShipped') = 0 "
    "ORDER BY o.xtype, o.id"
)
MASTER_IDENTITY_SQL = "SELECT DB_NAME(), SYSTEM_USER"
MASTER_EXTENDED_PERMISSIONS_SQL = (
    "SELECT CASE WHEN o.name = 'xp_cmdshell' THEN 1 ELSE 0 END, PERMISSIONS(o.id) "
    "FROM dbo.sysobjects o "
    "WHERE o.xtype = 'X' "
    "ORDER BY o.id"
)

QUERY_CATALOG = MappingProxyType(
    {
        "DATABASE_IDENTITY": IDENTITY_SQL,
        "ROLE_MEMBERSHIP": ROLE_SQL,
        "STATEMENT_PERMISSIONS": STATEMENT_PERMISSIONS_SQL,
        "REQUIRED_TABLE_PERMISSIONS": REQUIRED_TABLE_PERMISSIONS_SQL,
        "USER_OBJECT_PERMISSIONS": OBJECT_PERMISSIONS_SQL,
        "MASTER_IDENTITY": MASTER_IDENTITY_SQL,
        "MASTER_EXTENDED_PERMISSIONS": MASTER_EXTENDED_PERMISSIONS_SQL,
    }
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
        "PERMISSION_DENIED": "The integrated identity lacks permission for a fixed metadata query.",
        "CONNECTION_TIMEOUT": "The ODBC connection timed out.",
        "QUERY_TIMEOUT": "A fixed read-only metadata query timed out.",
        "PROTOCOL_FAILED": "The ODBC protocol or SQL Server compatibility check failed.",
        "IDENTITY_MISMATCH": "The connected database identity is not the required MS2011 database.",
        "SOURCE_CHANGED": "Permission evidence changed between the two fixed reads.",
        "INVALID_RESPONSE": "A fixed metadata query returned an unexpected response shape.",
        "UNKNOWN": "The read-only permission diagnostic failed.",
    }
)


@dataclass(frozen=True)
class PermissionConfig:
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
        "databaseRequired": "MS2011",
        "authentication": "windows-integrated",
        "odbcReadOnlyAccessModeRequested": True,
        "metadataDatabases": ["MS2011", "master"],
        "doubleReadRequired": True,
        "queryIds": list(QUERY_CATALOG),
        "requiredTables": list(REQUIRED_TABLES),
        "serverRolesChecked": list(SERVER_ROLES),
        "databaseRolesChecked": list(DATABASE_ROLES),
        "assessmentStatuses": [
            "READ_ONLY_PROVEN",
            "WRITE_CAPABILITY_PRESENT",
            "UNKNOWN",
        ],
        "neverTestsWrites": True,
    }


def _failure(category: str, phase: str, sqlstate: str | None = None) -> DiagnosticFailure:
    return DiagnosticFailure(ErrorDetail(category, phase, SAFE_MESSAGES[category], sqlstate))


def _error_result(detail: ErrorDetail, started: float | None = None) -> dict[str, Any]:
    result: dict[str, Any] = {
        "status": "error",
        "mode": "permissions",
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


def validate_config(config: PermissionConfig) -> None:
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


def build_connection_string(config: PermissionConfig, database: str | None = None) -> str:
    target_database = config.database if database is None else database
    return (
        f"DRIVER={{{config.driver}}};"
        f"SERVER={config.server};"
        f"DATABASE={target_database};"
        "Trusted_Connection=yes;"
        "APP=MS2011PermissionReadOnlyProbe"
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
    elif sqlstate == "IM014" or "ARCHITECTURE MISMATCH" in text:
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


def _connect(config: PermissionConfig, module, database: str | None = None):
    visible = [str(driver) for driver in module.drivers()]
    if config.driver not in visible:
        raise _failure("NO_DRIVER", "driver-enumeration")
    try:
        access_mode = module.SQL_ATTR_ACCESS_MODE
    except AttributeError as exc:
        raise _failure("PROTOCOL_FAILED", "connect") from exc
    connection = module.connect(
        build_connection_string(config, database),
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


def _permission_mask(value: Any, phase: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise _failure("INVALID_RESPONSE", phase)
    return value


def _identity(row: Any) -> dict[str, str]:
    phase = "query:DATABASE_IDENTITY"
    if row is None or len(row) != 3:
        raise _failure("INVALID_RESPONSE", phase)
    database, login, database_user = row
    if not isinstance(database, str) or database.upper() != "MS2011":
        raise _failure("IDENTITY_MISMATCH", phase)
    if not isinstance(login, str) or not login.strip():
        raise _failure("INVALID_RESPONSE", phase)
    if not isinstance(database_user, str) or not database_user.strip():
        raise _failure("INVALID_RESPONSE", phase)
    return {"database": database, "login": login, "databaseUser": database_user}


def _roles(row: Any) -> dict[str, int | None]:
    phase = "query:ROLE_MEMBERSHIP"
    if row is None or len(row) != len(ALL_ROLES):
        raise _failure("INVALID_RESPONSE", phase)
    result: dict[str, int | None] = {}
    for role, value in zip(ALL_ROLES, row, strict=True):
        if value not in (0, 1, None, False, True):
            raise _failure("INVALID_RESPONSE", phase)
        result[role] = None if value is None else int(value)
    return result


def _required_permissions(rows: list[Any]) -> list[dict[str, Any]]:
    phase = "query:REQUIRED_TABLE_PERMISSIONS"
    result = []
    seen: set[str] = set()
    for row in rows:
        if row is None or len(row) != 2:
            raise _failure("INVALID_RESPONSE", phase)
        table, mask = row
        if table not in REQUIRED_TABLES or table in seen:
            raise _failure("INVALID_RESPONSE", phase)
        seen.add(table)
        result.append({"table": table, "permissionMask": _permission_mask(mask, phase)})
    if seen != set(REQUIRED_TABLES):
        raise _failure("INVALID_RESPONSE", phase)
    return sorted(result, key=lambda item: REQUIRED_TABLES.index(item["table"]))


def _object_permissions(rows: list[Any]) -> list[dict[str, Any]]:
    phase = "query:USER_OBJECT_PERMISSIONS"
    allowed = {"U", "V", "P", "X", "RF"}
    result = []
    for row in rows:
        if row is None or len(row) != 2:
            raise _failure("INVALID_RESPONSE", phase)
        object_type, mask = row
        if not isinstance(object_type, str) or object_type.strip().upper() not in allowed:
            raise _failure("INVALID_RESPONSE", phase)
        result.append(
            {
                "objectType": object_type.strip().upper(),
                "permissionMask": _permission_mask(mask, phase),
            }
        )
    return result


def _read_snapshot(cursor) -> dict[str, Any]:
    cursor.execute(IDENTITY_SQL)
    identity = _identity(cursor.fetchone())
    cursor.execute(ROLE_SQL)
    roles = _roles(cursor.fetchone())
    cursor.execute(STATEMENT_PERMISSIONS_SQL)
    statement_row = cursor.fetchone()
    if statement_row is None or len(statement_row) != 1:
        raise _failure("INVALID_RESPONSE", "query:STATEMENT_PERMISSIONS")
    statement_mask = _permission_mask(statement_row[0], "query:STATEMENT_PERMISSIONS")
    cursor.execute(REQUIRED_TABLE_PERMISSIONS_SQL)
    required = _required_permissions(list(cursor.fetchall()))
    cursor.execute(OBJECT_PERMISSIONS_SQL)
    objects = _object_permissions(list(cursor.fetchall()))
    return {
        "identity": identity,
        "roles": roles,
        "statementPermissionMask": statement_mask,
        "requiredTables": required,
        "objects": objects,
    }


def _read_master_snapshot(cursor, expected_login: str) -> list[dict[str, Any]]:
    cursor.execute(MASTER_IDENTITY_SQL)
    identity_row = cursor.fetchone()
    if identity_row is None or len(identity_row) != 2:
        raise _failure("INVALID_RESPONSE", "query:MASTER_IDENTITY")
    database, login = identity_row
    if not isinstance(database, str) or database.lower() != "master":
        raise _failure("IDENTITY_MISMATCH", "query:MASTER_IDENTITY")
    if not isinstance(login, str) or login != expected_login:
        raise _failure("IDENTITY_MISMATCH", "query:MASTER_IDENTITY")
    cursor.execute(MASTER_EXTENDED_PERMISSIONS_SQL)
    objects = []
    for row in cursor.fetchall():
        if row is None or len(row) != 2:
            raise _failure("INVALID_RESPONSE", "query:MASTER_EXTENDED_PERMISSIONS")
        sentinel, mask = row
        if sentinel not in (0, 1, False, True):
            raise _failure("INVALID_RESPONSE", "query:MASTER_EXTENDED_PERMISSIONS")
        objects.append(
            {
                "xpCmdShellSentinel": bool(sentinel),
                "permissionMask": _permission_mask(mask, "query:MASTER_EXTENDED_PERMISSIONS"),
            }
        )
    if not objects or sum(item["xpCmdShellSentinel"] for item in objects) != 1:
        raise _failure("INVALID_RESPONSE", "query:MASTER_EXTENDED_PERMISSIONS")
    return objects


def _snapshot_hash(snapshot: dict[str, Any]) -> str:
    payload = json.dumps(snapshot, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest().upper()


def _has_mask(mask: int, bits: int) -> bool:
    grantable = bits << 16
    return bool(mask & bits or mask & grantable)


def _assess(snapshot: dict[str, Any]) -> dict[str, Any]:
    reasons: set[str] = set()
    known_write = False
    indeterminate = False
    roles = snapshot["roles"]

    for role, membership in roles.items():
        if membership is None:
            indeterminate = True
            reasons.add(f"ROLE_MEMBERSHIP_INDETERMINATE:{role}")
        elif membership == 1 and role in DIRECT_WRITE_SERVER_ROLES + DIRECT_WRITE_DATABASE_ROLES:
            known_write = True
            reasons.add(f"ROLE:{role}")
        elif membership == 1 and role in AMBIGUOUS_ELEVATED_ROLES:
            indeterminate = True
            reasons.add(f"ELEVATED_ROLE:{role}")

    statement_mask = snapshot["statementPermissionMask"]
    if _has_mask(statement_mask, STATEMENT_DDL_BITS):
        known_write = True
        reasons.add("STATEMENT_DDL_PERMISSION")
    if _has_mask(statement_mask, STATEMENT_BACKUP_BITS):
        indeterminate = True
        reasons.add("DATABASE_BACKUP_PERMISSION")
    lower_statement_bits = statement_mask & 0xFFFF
    grantable_statement_bits = (statement_mask >> 16) & 0xFFFF
    if (lower_statement_bits | grantable_statement_bits) & ~KNOWN_STATEMENT_BITS:
        indeterminate = True
        reasons.add("UNKNOWN_STATEMENT_PERMISSION")

    required_full_select = 0
    for item in snapshot["requiredTables"]:
        mask = item["permissionMask"]
        if mask & SELECT_ALL_BIT:
            required_full_select += 1
        else:
            indeterminate = True
            reasons.add("REQUIRED_SELECT_NOT_PROVEN")
        if _has_mask(mask, OBJECT_WRITE_BITS):
            known_write = True
            reasons.add("REQUIRED_TABLE_WRITE_PERMISSION")

    write_capable_objects = 0
    executable_user_objects = 0
    for item in snapshot["objects"]:
        object_type = item["objectType"]
        mask = item["permissionMask"]
        if object_type in {"U", "V"} and _has_mask(mask, OBJECT_WRITE_BITS):
            write_capable_objects += 1
            known_write = True
            reasons.add("OBJECT_WRITE_PERMISSION")
        if object_type in {"P", "X", "RF"} and _has_mask(mask, EXECUTE_BIT):
            executable_user_objects += 1
            indeterminate = True
            reasons.add("EXECUTABLE_USER_OBJECT")

    executable_master_extended = sum(
        _has_mask(item["permissionMask"], EXECUTE_BIT)
        for item in snapshot["masterExtendedProcedures"]
    )
    if executable_master_extended:
        indeterminate = True
        reasons.add("EXECUTABLE_MASTER_EXTENDED_PROCEDURE")

    if known_write:
        assessment = "WRITE_CAPABILITY_PRESENT"
    elif indeterminate:
        assessment = "UNKNOWN"
    else:
        assessment = "READ_ONLY_PROVEN"
    return {
        "permissionAssessment": assessment,
        "reasons": sorted(reasons),
        "requiredTableCount": len(REQUIRED_TABLES),
        "requiredTablesWithFullSelect": required_full_select,
        "userObjectCount": len(snapshot["objects"]),
        "writeCapableObjectCount": write_capable_objects,
        "executableUserObjectCount": executable_user_objects,
        "masterExtendedProcedureCount": len(snapshot["masterExtendedProcedures"]),
        "executableMasterExtendedProcedureCount": executable_master_extended,
    }


def run_permission_diagnostic(
    config: PermissionConfig,
    pyodbc_module=None,
    loader: Callable[[], Any] = load_pyodbc,
) -> dict[str, Any]:
    connection = None
    cursor = None
    master_connection = None
    master_cursor = None
    started = time.perf_counter()
    phase = "validate"
    try:
        validate_config(config)
        module = _module_or_error(pyodbc_module, loader)
        phase = "connect"
        connection = _connect(config, module)
        cursor = connection.cursor()
        phase = "query:first-pass"
        first = _read_snapshot(cursor)
        phase = "query:second-pass"
        second = _read_snapshot(cursor)
        if _snapshot_hash(first) != _snapshot_hash(second):
            raise _failure("SOURCE_CHANGED", "query:double-read")
        phase = "connect:master"
        master_connection = _connect(config, module, "master")
        master_cursor = master_connection.cursor()
        phase = "query:master-first-pass"
        first["masterExtendedProcedures"] = _read_master_snapshot(
            master_cursor, first["identity"]["login"]
        )
        phase = "query:master-second-pass"
        second["masterExtendedProcedures"] = _read_master_snapshot(
            master_cursor, second["identity"]["login"]
        )
        first_hash = _snapshot_hash(first)
        if first_hash != _snapshot_hash(second):
            raise _failure("SOURCE_CHANGED", "query:double-read")
        assessment = _assess(first)
        return {
            "status": "ok",
            "mode": "permissions",
            "runtime": runtime_metadata(),
            "databaseIdentity": {"database": first["identity"]["database"]},
            "connectionIdentity": {
                "login": first["identity"]["login"],
                "databaseUser": first["identity"]["databaseUser"],
            },
            "authentication": "windows-integrated",
            "odbcReadOnlyAccessModeRequested": True,
            "odbcReadOnlyAccessModeAccepted": True,
            "doubleReadMatched": True,
            "sourceHash": first_hash,
            "roleMembership": first["roles"],
            "statementPermissionMask": first["statementPermissionMask"],
            **assessment,
            "durationMs": round((time.perf_counter() - started) * 1000, 3),
        }
    except DiagnosticFailure as exc:
        return _error_result(exc.detail, started)
    except Exception as exc:
        return _error_result(classify_odbc_error(exc, phase), started)
    finally:
        if master_cursor is not None:
            with suppress(Exception):
                master_cursor.close()
        if master_connection is not None:
            with suppress(Exception):
                master_connection.close()
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
    if category in {"LOGIN_FAILED", "PERMISSION_DENIED", "CONNECTION_TIMEOUT", "PROTOCOL_FAILED"}:
        return 4
    if category in {"QUERY_TIMEOUT", "IDENTITY_MISMATCH", "SOURCE_CHANGED", "INVALID_RESPONSE"}:
        return 5
    return 1


def build_parser() -> argparse.ArgumentParser:
    parser = JsonArgumentParser(description="Fixed-query MS2011 read-only permission diagnostic")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--describe-contract", action="store_true")
    mode.add_argument("--permissions", action="store_true")
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
            result = run_permission_diagnostic(
                PermissionConfig(
                    server=args.server,
                    database=args.database,
                    driver=args.driver,
                    connect_timeout_seconds=args.connect_timeout_seconds,
                    query_timeout_seconds=args.query_timeout_seconds,
                )
            )
    except (TypeError, ValueError):
        result = _error_result(
            ErrorDetail("INVALID_ARGUMENT", "arguments", SAFE_MESSAGES["INVALID_ARGUMENT"])
        )
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return exit_code_for(result)


if __name__ == "__main__":
    raise SystemExit(main())
