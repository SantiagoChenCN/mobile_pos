from __future__ import annotations

from types import MappingProxyType


DATABASE_NAME = "MS2011"

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


def require_fixed_table(value: str) -> str:
    if value not in TABLES:
        raise ValueError("table is not present in the EV-02 fixed schema")
    return value


def require_fixed_column(table: str, value: str) -> str:
    require_fixed_table(table)
    if value not in CANDIDATE_KEYS[table]:
        raise ValueError("column is not present in the EV-02 fixed schema")
    return value
