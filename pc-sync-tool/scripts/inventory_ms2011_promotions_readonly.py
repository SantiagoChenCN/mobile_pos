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
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from types import MappingProxyType
from typing import Any, Callable


ART = timezone(timedelta(hours=-3), name="ART")
ODBC_SQL_MODE_READ_ONLY = 1
CONNECT_TIMEOUT_MIN = 1
CONNECT_TIMEOUT_MAX = 30
QUERY_TIMEOUT_MIN = 1
QUERY_TIMEOUT_MAX = 300
MAX_CLOCK_SKEW_SECONDS = 300

SERVER_PATTERN = re.compile(r"^[A-Za-z0-9_.\\,-]+$")
DATABASE_PATTERN = re.compile(r"^[A-Za-z0-9_]+$")
SQLSTATE_PATTERN = re.compile(r"^[A-Z0-9]{5}$")
BRACKETED_SQLSTATE_PATTERN = re.compile(r"\[([A-Z0-9]{5})\]")
CANONICAL_POSITIVE_INTEGER_TEXT_PATTERN = re.compile(r"^[1-9][0-9]*$")

QUERY_IDS = (
    "DATABASE_IDENTITY",
    "PRODUCT_SIMPLE_CANDIDATES",
    "PROMOTION_MAPPINGS",
    "QUANTITY_PERCENT_MASTERS",
    "QUANTITY_PERCENT_DETAILS",
    "QUANTITY_PERCENT_GLOBAL_RULES",
    "QUANTITY_PERCENT_SCHEDULES",
    "QUANTITY_FIXED_MASTERS",
    "QUANTITY_FIXED_DETAILS",
    "QUANTITY_FIXED_SCHEDULES",
    "MIX_MATCH_MASTERS",
    "MIX_MATCH_PRODUCTS",
)

QUERY_SQL = MappingProxyType(
    {
        "DATABASE_IDENTITY": "SELECT DB_NAME(), @@SERVERNAME, GETDATE()",
        "PRODUCT_SIMPLE_CANDIDATES": (
            "SELECT [GID], [GStopFlag], "
            "CASE WHEN [GHuiPrice] IS NOT NULL AND [GHuiPrice] > 0 THEN 1 ELSE 0 END, "
            "CASE WHEN [GHuiPriceCount] IS NOT NULL AND [GHuiPriceCount] > 0 THEN 1 ELSE 0 END "
            "FROM dbo.[MS_GOODLIST] WITH (NOLOCK) "
            "WHERE ([GHuiPrice] IS NOT NULL AND [GHuiPrice] > 0) "
            "OR ([GHuiPriceCount] IS NOT NULL AND [GHuiPriceCount] > 0) "
            "ORDER BY [GID]"
        ),
        "PROMOTION_MAPPINGS": (
            "SELECT [XID], [XTABLE], [XMASTER], [XGOODID] "
            "FROM dbo.[MS_CUXIAO_GOOD] WITH (NOLOCK) ORDER BY [XID]"
        ),
        "QUANTITY_PERCENT_MASTERS": (
            "SELECT [CXDID], [CXDBeginDate], [CXDEndDate] "
            "FROM dbo.[MS_SALE_CXDAN1] WITH (NOLOCK) ORDER BY [CXDID]"
        ),
        "QUANTITY_PERCENT_DETAILS": (
            "SELECT [CXGID], [CXGGOODID], [CXGDANID] "
            "FROM dbo.[MS_SALE_CXDETAIL1] WITH (NOLOCK) ORDER BY [CXGID]"
        ),
        "QUANTITY_PERCENT_GLOBAL_RULES": (
            "SELECT [CXTID], [CXTDANID] "
            "FROM dbo.[MS_SALE_CXTABLE1] WITH (NOLOCK) ORDER BY [CXTID]"
        ),
        "QUANTITY_PERCENT_SCHEDULES": (
            "SELECT [WD1ID], [WD1BeginDate], [WD1EndDate], [WD1WeekDay], "
            "[WD1BeginTime], [WD1EndTime], [WD1Checked], [WD1MASTER] "
            "FROM dbo.[MS_SALE_WEEKDETAIL1] WITH (NOLOCK) ORDER BY [WD1ID]"
        ),
        "QUANTITY_FIXED_MASTERS": (
            "SELECT [MID], [MBEGINDATE], [MENDDATE] "
            "FROM dbo.[MS_SALE_CXMASTERDING] WITH (NOLOCK) ORDER BY [MID]"
        ),
        "QUANTITY_FIXED_DETAILS": (
            "SELECT [CID], [CGOODID], [CMASTER] "
            "FROM dbo.[MS_SALE_CXTABLEDING] WITH (NOLOCK) ORDER BY [CID]"
        ),
        "QUANTITY_FIXED_SCHEDULES": (
            "SELECT [MDID], [MDBeginDate], [MDEndDate], [MDWeekDay], "
            "[MDBeginTime], [MDEndTime], [MDChecked], [MDMASTER] "
            "FROM dbo.[MS_SALE_WEEKDING] WITH (NOLOCK) ORDER BY [MDID]"
        ),
        "MIX_MATCH_MASTERS": (
            "SELECT [M4ID], [M4BEGINDATE], [M4ENDDATE] "
            "FROM dbo.[MS_SALE_CXMASTERFOUR] WITH (NOLOCK) ORDER BY [M4ID]"
        ),
        "MIX_MATCH_PRODUCTS": (
            "SELECT [C4ID], [C4GOODID], [C4MASTER] "
            "FROM dbo.[MS_SALE_CXTABLEFOUR] WITH (NOLOCK) ORDER BY [C4ID]"
        ),
    }
)
IDENTITY_QUERY_ID = "DATABASE_IDENTITY"
IDENTITY_SQL = QUERY_SQL[IDENTITY_QUERY_ID]
INVENTORY_QUERY_IDS = tuple(query_id for query_id in QUERY_IDS if query_id != IDENTITY_QUERY_ID)

EXPECTED_ROW_LENGTHS = MappingProxyType(
    {
        "DATABASE_IDENTITY": 3,
        "PRODUCT_SIMPLE_CANDIDATES": 4,
        "PROMOTION_MAPPINGS": 4,
        "QUANTITY_PERCENT_MASTERS": 3,
        "QUANTITY_PERCENT_DETAILS": 3,
        "QUANTITY_PERCENT_GLOBAL_RULES": 2,
        "QUANTITY_PERCENT_SCHEDULES": 8,
        "QUANTITY_FIXED_MASTERS": 3,
        "QUANTITY_FIXED_DETAILS": 3,
        "QUANTITY_FIXED_SCHEDULES": 8,
        "MIX_MATCH_MASTERS": 3,
        "MIX_MATCH_PRODUCTS": 3,
    }
)

POSITIVE_INTEGER_POSITIONS = MappingProxyType(
    {
        "PRODUCT_SIMPLE_CANDIDATES": (0,),
        "PROMOTION_MAPPINGS": (0, 2, 3),
        "QUANTITY_PERCENT_MASTERS": (0,),
        "QUANTITY_PERCENT_DETAILS": (0, 1, 2),
        "QUANTITY_PERCENT_GLOBAL_RULES": (0, 1),
        "QUANTITY_PERCENT_SCHEDULES": (0, 7),
        "QUANTITY_FIXED_MASTERS": (0,),
        "QUANTITY_FIXED_DETAILS": (0, 1, 2),
        "QUANTITY_FIXED_SCHEDULES": (0, 7),
        "MIX_MATCH_MASTERS": (0,),
        "MIX_MATCH_PRODUCTS": (0, 1, 2),
    }
)

# EV-02 live schema evidence confirms that these three legacy product-ID
# columns are varchar even though they reference MS_GOODLIST.GID (int).
# Text compatibility is deliberately limited to these fixed query positions.
TEXT_INTEGER_POSITIONS = MappingProxyType(
    {
        "QUANTITY_PERCENT_DETAILS": (1,),
        "QUANTITY_FIXED_DETAILS": (1,),
        "MIX_MATCH_PRODUCTS": (1,),
    }
)

TYPE_CONTRACTS = MappingProxyType(
    {
        "MS_SALE_CXDAN1": MappingProxyType(
            {
                "candidateType": "QUANTITY_PERCENT",
                "masterQuery": "QUANTITY_PERCENT_MASTERS",
                "detailQuery": "QUANTITY_PERCENT_DETAILS",
                "scheduleQuery": "QUANTITY_PERCENT_SCHEDULES",
                "globalQuery": "QUANTITY_PERCENT_GLOBAL_RULES",
                "requiresSchedule": True,
            }
        ),
        "MS_SALE_CXMASTERDING": MappingProxyType(
            {
                "candidateType": "QUANTITY_FIXED_TOTAL",
                "masterQuery": "QUANTITY_FIXED_MASTERS",
                "detailQuery": "QUANTITY_FIXED_DETAILS",
                "scheduleQuery": "QUANTITY_FIXED_SCHEDULES",
                "globalQuery": None,
                "requiresSchedule": True,
            }
        ),
        "MS_SALE_CXMASTERFOUR": MappingProxyType(
            {
                "candidateType": "MIX_MATCH_FIXED_TOTAL",
                "masterQuery": "MIX_MATCH_MASTERS",
                "detailQuery": "MIX_MATCH_PRODUCTS",
                "scheduleQuery": None,
                "globalQuery": None,
                "requiresSchedule": False,
            }
        ),
    }
)

MISSING_EVIDENCE = MappingProxyType(
    {
        "PRODUCT_SIMPLE": (
            "threshold-boundary",
            "over-threshold",
            "fractional-quantity",
            "rounding",
            "priority-and-stacking",
        ),
        "QUANTITY_PERCENT": (
            "discount-field-semantics",
            "global-scope-semantics",
            "tier-selection",
            "repeat-application",
            "remainder",
            "priority-and-stacking",
            "rounding",
        ),
        "QUANTITY_FIXED_TOTAL": (
            "fixed-total-semantics",
            "tier-selection",
            "repeat-groups",
            "remainder",
            "allocation",
            "rounding",
        ),
        "MIX_MATCH_FIXED_TOTAL": (
            "combination-selection",
            "same-product-multiple-units",
            "repeat-groups",
            "remainder",
            "allocation",
            "priority-and-stacking",
        ),
        "UNKNOWN": ("source-type-identification",),
    }
)

CLASSIFICATIONS = (
    "currentEnabled",
    "futureConfigured",
    "historicalExpired",
    "unableToDetermine",
)
CLASSIFICATION_ORDER = MappingProxyType(
    {
        "unableToDetermine": 0,
        "futureConfigured": 1,
        "currentEnabled": 2,
        "historicalExpired": 3,
    }
)

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
        "PROTOCOL_FAILED": "The ODBC protocol check failed.",
        "IDENTITY_MISMATCH": "The connected database identity is not the required MS2011 database.",
        "CLOCK_REVIEW_REQUIRED": "The database clock differs from the packaged runtime clock.",
        "SOURCE_CHANGED": "The two fixed-query reads returned different normalized data.",
        "INVALID_RESPONSE": "A fixed query returned an unexpected response shape or value type.",
        "UNKNOWN": "The read-only promotion inventory failed.",
    }
)


@dataclass(frozen=True)
class InventoryConfig:
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


class InventoryFailure(Exception):
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
        "queryIds": list(QUERY_IDS),
        "inventoryQueryIds": list(INVENTORY_QUERY_IDS),
        "classifications": list(CLASSIFICATIONS),
        "classificationClock": "database-getdate-assumed-art",
        "doubleReadRequired": True,
        "databaseRequired": "MS2011",
        "outputsProductNames": False,
        "outputsBarcodes": False,
        "outputsFormulaValues": False,
        "requiresBlackBoxEvidence": True,
    }


def _failure(category: str, phase: str, sqlstate: str | None = None) -> InventoryFailure:
    return InventoryFailure(ErrorDetail(category, phase, SAFE_MESSAGES[category], sqlstate))


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
    except InventoryFailure:
        raise
    except ImportError as exc:
        raise _failure("PYODBC_UNAVAILABLE", "import") from exc


def validate_config(config: InventoryConfig) -> None:
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


def build_connection_string(config: InventoryConfig) -> str:
    return (
        f"DRIVER={{{config.driver}}};"
        f"SERVER={config.server};"
        f"DATABASE={config.database};"
        "Trusted_Connection=yes;"
        "APP=MS2011PromotionInventoryReadOnly"
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


def _connect(config: InventoryConfig, module):
    visible = [str(driver) for driver in module.drivers()]
    if config.driver not in visible:
        raise _failure("NO_DRIVER", "driver-enumeration")
    access_mode = getattr(module, "SQL_ATTR_ACCESS_MODE", None)
    if access_mode is None:
        raise _failure("PROTOCOL_FAILED", "connect")
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


def _as_art(value: Any) -> datetime | None:
    if not isinstance(value, datetime):
        return None
    if value.tzinfo is None:
        return value.replace(tzinfo=ART)
    return value.astimezone(ART)


def _normalize_now(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if not isinstance(value, datetime) or value.tzinfo is None:
        raise _failure("INVALID_ARGUMENT", "validate")
    normalized = value.astimezone(ART)
    if normalized.utcoffset() != timedelta(hours=-3):
        raise _failure("INVALID_ARGUMENT", "validate")
    return normalized


def _database_identity(row: Any) -> tuple[datetime, dict[str, str]]:
    if row is None or len(row) != 3:
        raise _failure("INVALID_RESPONSE", "query:identity")
    database, server, database_time = row
    if not isinstance(database, str) or database.upper() != "MS2011":
        raise _failure("IDENTITY_MISMATCH", "query:identity")
    if not isinstance(server, str) or not server.strip():
        raise _failure("INVALID_RESPONSE", "query:identity")
    captured = _as_art(database_time)
    if captured is None:
        raise _failure("INVALID_RESPONSE", "query:identity")
    return captured, {"database": database}


def _integer(value: Any, phase: str) -> int:
    if isinstance(value, bool):
        raise _failure("INVALID_RESPONSE", phase)
    if isinstance(value, int):
        return value
    if isinstance(value, Decimal) and value == value.to_integral_value():
        return int(value)
    raise _failure("INVALID_RESPONSE", phase)


def _positive_integer(value: Any, phase: str, *, allow_text: bool = False) -> int:
    if allow_text and isinstance(value, str):
        if CANONICAL_POSITIVE_INTEGER_TEXT_PATTERN.fullmatch(value) is None:
            raise _failure("INVALID_RESPONSE", phase)
        normalized = int(value)
    else:
        normalized = _integer(value, phase)
    if normalized <= 0:
        raise _failure("INVALID_RESPONSE", phase)
    return normalized


def _normalized_rows(query_id: str, rows: list[Any]) -> list[tuple[Any, ...]]:
    phase = f"query:{query_id}"
    expected = EXPECTED_ROW_LENGTHS[query_id]
    normalized: list[tuple[Any, ...]] = []
    keys: set[int] = set()
    for row in rows:
        if row is None or len(row) != expected:
            raise _failure("INVALID_RESPONSE", phase)
        values = list(row)
        key = _positive_integer(values[0], phase)
        if key in keys:
            raise _failure("INVALID_RESPONSE", phase)
        keys.add(key)
        text_positions = TEXT_INTEGER_POSITIONS.get(query_id, ())
        for position in POSITIVE_INTEGER_POSITIONS[query_id]:
            values[position] = _positive_integer(
                values[position],
                phase,
                allow_text=position in text_positions,
            )
        if query_id == "PROMOTION_MAPPINGS":
            if not isinstance(values[1], str) or not values[1].strip():
                raise _failure("INVALID_RESPONSE", phase)
        normalized.append(tuple(values))
    return sorted(normalized, key=lambda item: item[0])


def _execute_query(cursor, query_id: str):
    if query_id not in QUERY_SQL:
        raise _failure("INVALID_ARGUMENT", "query-catalog")
    return cursor.execute(QUERY_SQL[query_id])


def _read_pass(cursor) -> dict[str, list[tuple[Any, ...]]]:
    result: dict[str, list[tuple[Any, ...]]] = {}
    for query_id in INVENTORY_QUERY_IDS:
        _execute_query(cursor, query_id)
        result[query_id] = _normalized_rows(query_id, list(cursor.fetchall()))
    return result


def _canonical(value: Any) -> Any:
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, datetime):
        normalized = _as_art(value)
        return normalized.isoformat(timespec="milliseconds") if normalized else None
    if isinstance(value, tuple):
        return [_canonical(item) for item in value]
    if isinstance(value, list):
        return [_canonical(item) for item in value]
    if isinstance(value, dict):
        return {str(key): _canonical(item) for key, item in value.items()}
    raise _failure("INVALID_RESPONSE", "normalize")


def _source_hash(read: dict[str, list[tuple[Any, ...]]]) -> str:
    rendered = json.dumps(_canonical(read), ensure_ascii=True, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(rendered.encode("utf-8")).hexdigest().upper()


def _date_text(value: datetime | None) -> str | None:
    return value.isoformat(timespec="seconds") if value else None


def _classify_dates(
    begin_raw: Any,
    end_raw: Any,
    captured: datetime,
    anomalies: list[str],
) -> tuple[str, datetime | None, datetime | None]:
    begin = _as_art(begin_raw)
    end = _as_art(end_raw)
    if begin is None or end is None or begin >= end:
        anomalies.append("INVALID_DATE_RANGE")
        return "unableToDetermine", begin, end
    if anomalies:
        return "unableToDetermine", begin, end
    if captured == begin or captured == end:
        anomalies.append("UNVERIFIED_DATE_ENDPOINT")
        return "unableToDetermine", begin, end
    if captured < begin:
        return "futureConfigured", begin, end
    if captured > end:
        return "historicalExpired", begin, end
    return "currentEnabled", begin, end


def _candidate(
    candidate_type: str,
    source_table: str,
    source_key: int,
    classification: str,
    product_ids: list[int],
    anomalies: list[str],
    **extra: Any,
) -> dict[str, Any]:
    result = {
        "candidateType": candidate_type,
        "sourceTable": source_table,
        "sourceKey": source_key,
        "classification": classification,
        "associatedProductIds": sorted(set(product_ids)),
        "associatedProductCount": len(set(product_ids)),
        "anomalies": sorted(set(anomalies)),
        "missingEvidence": list(MISSING_EVIDENCE[candidate_type]),
    }
    result.update(extra)
    return result


def _rows_for_master(rows: list[tuple[Any, ...]], master_index: int, master: int) -> list[tuple[Any, ...]]:
    return [row for row in rows if _positive_integer(row[master_index], "normalize") == master]


def _products(rows: list[tuple[Any, ...]], product_index: int) -> list[int]:
    return sorted({_positive_integer(row[product_index], "normalize") for row in rows})


def _schedule_anomalies(schedule: tuple[Any, ...]) -> list[str]:
    anomalies: list[str] = []
    begin = _as_art(schedule[1])
    end = _as_art(schedule[2])
    if begin is None or end is None or begin >= end:
        anomalies.append("INVALID_SCHEDULE_DATE_RANGE")
    weekday = _integer(schedule[3], "normalize")
    if weekday not in range(7):
        anomalies.append("INVALID_SCHEDULE_WEEKDAY")
    begin_time = _as_art(schedule[4])
    end_time = _as_art(schedule[5])
    if begin_time is None or end_time is None or begin_time.time() >= end_time.time():
        anomalies.append("INVALID_SCHEDULE_TIME_RANGE")
    checked = _integer(schedule[6], "normalize")
    if checked not in (0, 1):
        anomalies.append("INVALID_SCHEDULE_FLAG")
    return anomalies


def _build_inventory(
    read: dict[str, list[tuple[Any, ...]]],
    captured: datetime,
    source_hash: str,
    database_identity: dict[str, str],
    clock_skew_seconds: float,
) -> dict[str, Any]:
    candidates: list[dict[str, Any]] = []

    for row in read["PRODUCT_SIMPLE_CANDIDATES"]:
        gid = _positive_integer(row[0], "normalize")
        stop_flag = _integer(row[1], "normalize")
        price_positive = _integer(row[2], "normalize")
        count_positive = _integer(row[3], "normalize")
        if price_positive not in (0, 1) or count_positive not in (0, 1):
            raise _failure("INVALID_RESPONSE", "normalize")
        anomalies: list[str] = []
        if not (price_positive == 1 and count_positive == 1):
            anomalies.append("INCOMPLETE_SIMPLE_FIELDS")
        if stop_flag != 0:
            anomalies.append("STOPPED_PRODUCT")
        classification = "currentEnabled" if not anomalies else "unableToDetermine"
        candidates.append(
            _candidate(
                "PRODUCT_SIMPLE",
                "MS_GOODLIST",
                gid,
                classification,
                [gid],
                anomalies,
                associatedProductsComplete=True,
                mappingRowCount=0,
                detailRowCount=0,
                scheduleRowCount=0,
                enabledScheduleRowCount=0,
            )
        )

    mappings = read["PROMOTION_MAPPINGS"]
    consumed_mapping_keys: set[int] = set()
    orphan_children: dict[tuple[str, int], dict[str, list[tuple[Any, ...]]]] = {}

    def child_bucket(source_table: str, master: int) -> dict[str, list[tuple[Any, ...]]]:
        return orphan_children.setdefault(
            (source_table, master),
            {"details": [], "schedules": [], "globals": []},
        )

    for source_table, contract in TYPE_CONTRACTS.items():
        masters = read[str(contract["masterQuery"])]
        master_keys = {_positive_integer(row[0], "normalize") for row in masters}
        detail_rows = read[str(contract["detailQuery"])]
        schedule_rows = read[str(contract["scheduleQuery"])] if contract["scheduleQuery"] else []
        global_rows = read[str(contract["globalQuery"])] if contract["globalQuery"] else []

        for detail in detail_rows:
            detail_master = _positive_integer(detail[2], "normalize")
            if detail_master not in master_keys:
                child_bucket(source_table, detail_master)["details"].append(detail)
        for schedule in schedule_rows:
            schedule_master = _positive_integer(schedule[7], "normalize")
            if schedule_master not in master_keys:
                child_bucket(source_table, schedule_master)["schedules"].append(schedule)
        for global_row in global_rows:
            global_master = _positive_integer(global_row[1], "normalize")
            if global_master not in master_keys:
                child_bucket(source_table, global_master)["globals"].append(global_row)

        for master_row in masters:
            master = _positive_integer(master_row[0], "normalize")
            mapping_rows = [
                row
                for row in mappings
                if str(row[1]) == source_table and _positive_integer(row[2], "normalize") == master
            ]
            consumed_mapping_keys.update(_positive_integer(row[0], "normalize") for row in mapping_rows)
            linked_details = _rows_for_master(detail_rows, 2, master)
            linked_schedules = _rows_for_master(schedule_rows, 7, master) if schedule_rows else []
            linked_globals = _rows_for_master(global_rows, 1, master) if global_rows else []
            mapping_products = _products(mapping_rows, 3)
            detail_products = _products(linked_details, 1)
            anomalies: list[str] = []
            if not mapping_rows:
                anomalies.append("MISSING_MAPPING")
            if mapping_products != detail_products:
                anomalies.append("MAPPING_DETAIL_MISMATCH")
            if len(mapping_products) != len(mapping_rows):
                anomalies.append("DUPLICATE_PRODUCT_MAPPING")
            if contract["globalQuery"] and len(linked_globals) != 1:
                anomalies.append("GLOBAL_RULE_COUNT_MISMATCH")
            enabled_schedules = 0
            if contract["requiresSchedule"]:
                for schedule in linked_schedules:
                    checked = _integer(schedule[6], "normalize")
                    anomalies.extend(_schedule_anomalies(schedule))
                    if checked == 1:
                        enabled_schedules += 1
                if enabled_schedules == 0:
                    anomalies.append("MISSING_ENABLED_SCHEDULE")
            classification, begin, end = _classify_dates(
                master_row[1], master_row[2], captured, anomalies
            )
            candidates.append(
                _candidate(
                    str(contract["candidateType"]),
                    source_table,
                    master,
                    classification,
                    mapping_products,
                    anomalies,
                    associatedProductsComplete=(
                        str(contract["candidateType"]) != "QUANTITY_PERCENT"
                        and bool(mapping_rows)
                        and mapping_products == detail_products
                    ),
                    beginArt=_date_text(begin),
                    endArt=_date_text(end),
                    mappingRowCount=len(mapping_rows),
                    detailRowCount=len(linked_details),
                    globalRuleRowCount=len(linked_globals),
                    scheduleRowCount=len(linked_schedules),
                    enabledScheduleRowCount=enabled_schedules,
                )
            )

    unresolved_known: dict[tuple[str, int], list[tuple[Any, ...]]] = {}
    unresolved_unknown: dict[tuple[str, int], list[tuple[Any, ...]]] = {}
    for mapping in mappings:
        mapping_key = _positive_integer(mapping[0], "normalize")
        if mapping_key in consumed_mapping_keys:
            continue
        source_table = str(mapping[1])
        source_key = _positive_integer(mapping[2], "normalize")
        target = unresolved_known if source_table in TYPE_CONTRACTS else unresolved_unknown
        target.setdefault((source_table, source_key), []).append(mapping)

    known_orphan_keys = set(orphan_children) | set(unresolved_known)
    for source_table, source_key in sorted(known_orphan_keys):
        contract = TYPE_CONTRACTS[source_table]
        mapping_rows = unresolved_known.get((source_table, source_key), [])
        children = orphan_children.get(
            (source_table, source_key),
            {"details": [], "schedules": [], "globals": []},
        )
        anomalies = ["ORPHAN_MASTER"]
        if mapping_rows:
            anomalies.append("ORPHAN_MAPPING")
        if children["details"]:
            anomalies.append("ORPHAN_DETAIL")
        if children["schedules"]:
            anomalies.append("ORPHAN_SCHEDULE")
            for schedule in children["schedules"]:
                anomalies.extend(_schedule_anomalies(schedule))
        if children["globals"]:
            anomalies.append("ORPHAN_GLOBAL_RULE")
        product_ids = _products(mapping_rows, 3) + _products(children["details"], 1)
        candidates.append(
            _candidate(
                str(contract["candidateType"]),
                source_table,
                source_key,
                "unableToDetermine",
                product_ids,
                anomalies,
                associatedProductsComplete=False,
                mappingRowCount=len(mapping_rows),
                detailRowCount=len(children["details"]),
                globalRuleRowCount=len(children["globals"]),
                scheduleRowCount=len(children["schedules"]),
                enabledScheduleRowCount=0,
            )
        )

    for (raw_source_table, source_key), rows in sorted(unresolved_unknown.items()):
        source_reference_hash = hashlib.sha256(raw_source_table.encode("utf-8")).hexdigest()[:16].upper()
        candidates.append(
            _candidate(
                "UNKNOWN",
                "UNKNOWN_SOURCE_TABLE",
                source_key,
                "unableToDetermine",
                _products(rows, 3),
                ["UNKNOWN_SOURCE_TABLE"],
                associatedProductsComplete=False,
                sourceReferenceHash=source_reference_hash,
                mappingRowCount=len(rows),
                detailRowCount=0,
                globalRuleRowCount=0,
                scheduleRowCount=0,
                enabledScheduleRowCount=0,
            )
        )

    candidates.sort(
        key=lambda item: (
            CLASSIFICATION_ORDER[item["classification"]],
            item["candidateType"],
            item["sourceTable"],
            item["sourceKey"],
            item.get("sourceReferenceHash", ""),
        )
    )
    classification_counts = {
        category: sum(item["classification"] == category for item in candidates)
        for category in CLASSIFICATIONS
    }
    type_names = sorted({item["candidateType"] for item in candidates})
    type_summary = []
    for candidate_type in type_names:
        subset = [item for item in candidates if item["candidateType"] == candidate_type]
        type_summary.append(
            {
                "candidateType": candidate_type,
                "candidateCount": len(subset),
                "classificationCounts": {
                    category: sum(item["classification"] == category for item in subset)
                    for category in CLASSIFICATIONS
                },
                "missingEvidence": list(MISSING_EVIDENCE[candidate_type]),
            }
        )

    unable = [item for item in candidates if item["classification"] == "unableToDetermine"]
    return {
        "status": "ok",
        "mode": "inventory",
        "runtime": runtime_metadata(),
        "databaseIdentity": database_identity,
        "capturedAtArt": captured.isoformat(timespec="seconds"),
        "classificationClock": "database-getdate-assumed-art",
        "clockSkewSeconds": round(clock_skew_seconds, 3),
        "doubleReadMatched": True,
        "sourceHash": source_hash,
        "queryRowCounts": {query_id: len(read[query_id]) for query_id in INVENTORY_QUERY_IDS},
        "classificationCounts": classification_counts,
        "candidateCount": len(candidates),
        "currentEnabledTypes": sorted(
            {
                item["candidateType"]
                for item in candidates
                if item["classification"] == "currentEnabled" and item["candidateType"] != "UNKNOWN"
            }
        ),
        "typeSummary": type_summary,
        "candidates": candidates,
        "requiresInventoryReview": bool(unable),
        "requiresBlackBoxEvidence": True,
    }


def run_inventory(
    config: InventoryConfig,
    pyodbc_module=None,
    loader: Callable[[], Any] = load_pyodbc,
    now_art: datetime | None = None,
) -> dict[str, Any]:
    connection = None
    cursor = None
    started = time.perf_counter()
    phase = "validate"
    try:
        validate_config(config)
        requested_now = _normalize_now(now_art)
        module = _module_or_error(pyodbc_module, loader)
        phase = "connect"
        connection = _connect(config, module)
        cursor = connection.cursor()
        phase = "query:identity"
        _execute_query(cursor, IDENTITY_QUERY_ID)
        database_time, identity = _database_identity(cursor.fetchone())
        runtime_now = requested_now or datetime.now(ART)
        clock_skew = abs((runtime_now - database_time).total_seconds())
        if requested_now is None and clock_skew > MAX_CLOCK_SKEW_SECONDS:
            raise _failure("CLOCK_REVIEW_REQUIRED", "query:identity")
        captured = requested_now or database_time
        phase = "query:first-pass"
        first = _read_pass(cursor)
        phase = "query:second-pass"
        second = _read_pass(cursor)
        first_hash = _source_hash(first)
        second_hash = _source_hash(second)
        if first_hash != second_hash:
            raise _failure("SOURCE_CHANGED", "query:double-read")
        result = _build_inventory(first, captured, first_hash, identity, clock_skew)
        result["durationMs"] = round((time.perf_counter() - started) * 1000, 3)
        return result
    except InventoryFailure as exc:
        return _error_result(exc.detail, "inventory", started)
    except Exception as exc:
        return _error_result(classify_odbc_error(exc, phase), "inventory", started)
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
    if category in {"LOGIN_FAILED", "PERMISSION_DENIED", "CONNECTION_TIMEOUT", "PROTOCOL_FAILED"}:
        return 4
    if category in {
        "QUERY_TIMEOUT",
        "IDENTITY_MISMATCH",
        "CLOCK_REVIEW_REQUIRED",
        "SOURCE_CHANGED",
        "INVALID_RESPONSE",
    }:
        return 5
    return 1


def build_parser() -> argparse.ArgumentParser:
    parser = JsonArgumentParser(description="Fixed-query MS2011 read-only promotion inventory")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--describe-contract", action="store_true")
    mode.add_argument("--inventory", action="store_true")
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
            result = run_inventory(
                InventoryConfig(
                    server=args.server,
                    database=args.database,
                    driver=args.driver,
                    connect_timeout_seconds=args.connect_timeout_seconds,
                    query_timeout_seconds=args.query_timeout_seconds,
                )
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
