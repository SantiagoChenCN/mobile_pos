from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum
from types import MappingProxyType


class QueryId(str, Enum):
    DATABASE_IDENTITY = "DATABASE_IDENTITY"
    PRODUCT_COUNT = "PRODUCT_COUNT"
    ACTIVITY_HINT = "ACTIVITY_HINT"
    PRODUCTS = "PRODUCTS"
    CATEGORIES = "CATEGORIES"
    UNITS = "UNITS"
    PRODUCT_SIMPLE_CANDIDATES = "PRODUCT_SIMPLE_CANDIDATES"
    PROMOTION_MAPPINGS = "PROMOTION_MAPPINGS"
    QUANTITY_PERCENT_MASTERS = "QUANTITY_PERCENT_MASTERS"
    QUANTITY_PERCENT_DETAILS = "QUANTITY_PERCENT_DETAILS"
    QUANTITY_PERCENT_GLOBAL_RULES = "QUANTITY_PERCENT_GLOBAL_RULES"
    QUANTITY_PERCENT_SCHEDULES = "QUANTITY_PERCENT_SCHEDULES"
    QUANTITY_FIXED_MASTERS = "QUANTITY_FIXED_MASTERS"
    QUANTITY_FIXED_DETAILS = "QUANTITY_FIXED_DETAILS"
    QUANTITY_FIXED_SCHEDULES = "QUANTITY_FIXED_SCHEDULES"
    MIX_MATCH_MASTERS = "MIX_MATCH_MASTERS"
    MIX_MATCH_PRODUCTS = "MIX_MATCH_PRODUCTS"
    PRODUCT_CHANGE_SUMMARY = "PRODUCT_CHANGE_SUMMARY"
    PROMOTION_CHANGE_SUMMARY = "PROMOTION_CHANGE_SUMMARY"


@dataclass(frozen=True)
class QuerySpec:
    sql: str
    parameter_count: int = 0
    low_cost: bool = True


_CATALOG = MappingProxyType(
    {
        QueryId.DATABASE_IDENTITY: QuerySpec(
            "SELECT DB_NAME() AS database_name, @@SERVERNAME AS server_name"
        ),
        QueryId.PRODUCT_COUNT: QuerySpec(
            "SELECT COUNT(*) AS product_count FROM dbo.MS_GOODLIST WITH (NOLOCK)"
        ),
        QueryId.ACTIVITY_HINT: QuerySpec(
            "SELECT COUNT(*) AS active_request_count "
            "FROM master.dbo.sysprocesses WITH (NOLOCK) "
            "WHERE dbid = DB_ID() AND spid <> @@SPID "
            "AND status NOT IN ('sleeping', 'background')"
        ),
        QueryId.PRODUCTS: QuerySpec(
            "SELECT [GID] AS gid, [GBarcode] AS barcode, [GNameX] AS name_short, "
            "[GNameC] AS name_full, [GNameJian] AS name_search, [GPrice] AS cost_price, "
            "[GSalePrice] AS sale_price, [GType] AS category_code, [GUnit] AS unit_code, "
            "[GStopFlag] AS stop_flag, [GUpdateTime] AS updated_at, "
            "[GHuiPrice] AS simple_price, [GHuiPriceCount] AS simple_threshold "
            "FROM dbo.[MS_GOODLIST] WITH (NOLOCK) ORDER BY [GID]",
            low_cost=False,
        ),
        QueryId.CATEGORIES: QuerySpec(
            "SELECT [RID] AS rid, [RUpType] AS parent_id, [RTypeName] AS name, "
            "[RTypeCode] AS code FROM dbo.[MS_GOODTYPELIST] WITH (NOLOCK) ORDER BY [RID]",
            low_cost=False,
        ),
        QueryId.UNITS: QuerySpec(
            "SELECT [UID] AS uid, [UNumCode] AS code, [UName] AS name, "
            "[UStopFlag] AS stop_flag FROM dbo.[MS_UNITLIST] WITH (NOLOCK) ORDER BY [UID]",
            low_cost=False,
        ),
        QueryId.PRODUCT_SIMPLE_CANDIDATES: QuerySpec(
            "SELECT [GID] AS gid, [GStopFlag] AS stop_flag, [GHuiPrice] AS simple_price, "
            "[GHuiPriceCount] AS simple_threshold FROM dbo.[MS_GOODLIST] WITH (NOLOCK) "
            "WHERE ([GHuiPrice] IS NOT NULL AND [GHuiPrice] > 0) "
            "OR ([GHuiPriceCount] IS NOT NULL AND [GHuiPriceCount] > 0) ORDER BY [GID]",
            low_cost=False,
        ),
        QueryId.PROMOTION_MAPPINGS: QuerySpec(
            "SELECT [XID] AS xid, [XRULER] AS group_code, [XTABLE] AS source_table, "
            "[XMASTER] AS master_id, [XGOODID] AS product_id "
            "FROM dbo.[MS_CUXIAO_GOOD] WITH (NOLOCK) ORDER BY [XID]",
            low_cost=False,
        ),
        QueryId.QUANTITY_PERCENT_MASTERS: QuerySpec(
            "SELECT [CXDID] AS master_id, [CXDBeginDate] AS begin_at, [CXDEndDate] AS end_at "
            "FROM dbo.[MS_SALE_CXDAN1] WITH (NOLOCK) ORDER BY [CXDID]",
            low_cost=False,
        ),
        QueryId.QUANTITY_PERCENT_DETAILS: QuerySpec(
            "SELECT [CXGID] AS detail_id, [CXGGOODID] AS product_id, [CXGDANID] AS master_id "
            "FROM dbo.[MS_SALE_CXDETAIL1] WITH (NOLOCK) ORDER BY [CXGID]",
            low_cost=False,
        ),
        QueryId.QUANTITY_PERCENT_GLOBAL_RULES: QuerySpec(
            "SELECT [CXTID] AS rule_id, [CXTDANID] AS master_id "
            "FROM dbo.[MS_SALE_CXTABLE1] WITH (NOLOCK) ORDER BY [CXTID]",
            low_cost=False,
        ),
        QueryId.QUANTITY_PERCENT_SCHEDULES: QuerySpec(
            "SELECT [WD1ID] AS schedule_id, [WD1BeginDate] AS begin_at, "
            "[WD1EndDate] AS end_at, [WD1WeekDay] AS weekday_raw, "
            "[WD1BeginTime] AS begin_time, [WD1EndTime] AS end_time, "
            "[WD1Checked] AS enabled_raw, [WD1MASTER] AS master_id "
            "FROM dbo.[MS_SALE_WEEKDETAIL1] WITH (NOLOCK) ORDER BY [WD1ID]",
            low_cost=False,
        ),
        QueryId.QUANTITY_FIXED_MASTERS: QuerySpec(
            "SELECT [MID] AS master_id, [MBEGINDATE] AS begin_at, [MENDDATE] AS end_at "
            "FROM dbo.[MS_SALE_CXMASTERDING] WITH (NOLOCK) ORDER BY [MID]",
            low_cost=False,
        ),
        QueryId.QUANTITY_FIXED_DETAILS: QuerySpec(
            "SELECT [CID] AS detail_id, [CGOODID] AS product_id, [CMASTER] AS master_id "
            "FROM dbo.[MS_SALE_CXTABLEDING] WITH (NOLOCK) ORDER BY [CID]",
            low_cost=False,
        ),
        QueryId.QUANTITY_FIXED_SCHEDULES: QuerySpec(
            "SELECT [MDID] AS schedule_id, [MDBeginDate] AS begin_at, "
            "[MDEndDate] AS end_at, [MDWeekDay] AS weekday_raw, "
            "[MDBeginTime] AS begin_time, [MDEndTime] AS end_time, "
            "[MDChecked] AS enabled_raw, [MDMASTER] AS master_id "
            "FROM dbo.[MS_SALE_WEEKDING] WITH (NOLOCK) ORDER BY [MDID]",
            low_cost=False,
        ),
        QueryId.MIX_MATCH_MASTERS: QuerySpec(
            "SELECT [M4ID] AS master_id, [M4BEGINDATE] AS begin_at, [M4ENDDATE] AS end_at "
            "FROM dbo.[MS_SALE_CXMASTERFOUR] WITH (NOLOCK) ORDER BY [M4ID]",
            low_cost=False,
        ),
        QueryId.MIX_MATCH_PRODUCTS: QuerySpec(
            "SELECT [C4ID] AS detail_id, [C4GOODID] AS product_id, [C4MASTER] AS master_id "
            "FROM dbo.[MS_SALE_CXTABLEFOUR] WITH (NOLOCK) ORDER BY [C4ID]",
            low_cost=False,
        ),
        QueryId.PRODUCT_CHANGE_SUMMARY: QuerySpec(
            "SELECT COUNT(*) AS product_count, MAX([GUpdateTime]) AS max_updated_at, "
            "MAX([GID]) AS max_gid FROM dbo.[MS_GOODLIST] WITH (NOLOCK)"
        ),
        QueryId.PROMOTION_CHANGE_SUMMARY: QuerySpec(
            "SELECT (SELECT COUNT(*) FROM dbo.[MS_CUXIAO_GOOD] WITH (NOLOCK)) AS mapping_count, "
            "(SELECT MAX([XID]) FROM dbo.[MS_CUXIAO_GOOD] WITH (NOLOCK)) AS max_mapping_id, "
            "(SELECT COUNT(*) FROM dbo.[MS_SALE_CXDAN1] WITH (NOLOCK)) + "
            "(SELECT COUNT(*) FROM dbo.[MS_SALE_CXMASTERDING] WITH (NOLOCK)) + "
            "(SELECT COUNT(*) FROM dbo.[MS_SALE_CXMASTERFOUR] WITH (NOLOCK)) AS master_count"
        ),
    }
)

_FORBIDDEN = re.compile(
    r"\b(INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|TRUNCATE|EXEC(?:UTE)?|"
    r"SELECT\s+INTO|OPENROWSET|OPENDATASOURCE|BULK\s+INSERT|DBCC|BACKUP|RESTORE)\b",
    re.IGNORECASE,
)


def query_spec(query_id: QueryId) -> QuerySpec:
    if not isinstance(query_id, QueryId):
        raise TypeError("query_id must be a frozen QueryId")
    return _CATALOG[query_id]


def query_catalog() -> MappingProxyType:
    return _CATALOG


def validate_catalog() -> None:
    for spec in _CATALOG.values():
        normalized = spec.sql.strip()
        if not normalized.upper().startswith("SELECT "):
            raise ValueError("all MS2011 queries must be SELECT statements")
        if ";" in normalized or "--" in normalized or "/*" in normalized:
            raise ValueError("query catalog forbids batches and comments")
        if _FORBIDDEN.search(normalized):
            raise ValueError("query catalog contains a side-effect-capable statement")


validate_catalog()
