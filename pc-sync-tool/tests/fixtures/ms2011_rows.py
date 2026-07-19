from __future__ import annotations

from datetime import datetime
from decimal import Decimal

from ms2011_query_catalog import QueryId


def build_rows():
    return {
        QueryId.PRODUCTS: (
            {
                "gid": 101,
                "barcode": "779000000101",
                "name_short": "Product 101",
                "name_full": "Product 101",
                "name_search": "P101",
                "cost_price": Decimal("10.0000"),
                "sale_price": Decimal("20.5000"),
                "category_code": "CAT",
                "unit_code": "UN",
                "stop_flag": 0,
                "updated_at": datetime(2026, 7, 17, 10, 0, 0),
                "simple_price": Decimal("18.0000"),
                "simple_threshold": Decimal("2.0000"),
            },
            {
                "gid": 202,
                "barcode": "779000000202",
                "name_short": "Product 202",
                "name_full": "Product 202",
                "name_search": "P202",
                "cost_price": Decimal("12.0000"),
                "sale_price": Decimal("25.0000"),
                "category_code": "CAT",
                "unit_code": None,
                "stop_flag": 1,
                "updated_at": None,
                "simple_price": None,
                "simple_threshold": None,
            },
        ),
        QueryId.CATEGORIES: (
            {"rid": 1, "parent_id": None, "name": "Category", "code": "CAT"},
        ),
        QueryId.UNITS: (
            {"uid": 1, "code": "UN", "name": "Unidad", "stop_flag": 0},
        ),
        QueryId.PRODUCT_SIMPLE_CANDIDATES: (
            {
                "gid": 101,
                "stop_flag": 0,
                "simple_price": Decimal("18.0000"),
                "simple_threshold": Decimal("2.0000"),
            },
        ),
        QueryId.PROMOTION_MAPPINGS: (
            {"xid": 1, "group_code": 1, "source_table": "MS_SALE_CXDAN1", "master_id": 10, "product_id": 101},
            {"xid": 2, "group_code": 1, "source_table": "MS_SALE_CXMASTERDING", "master_id": 20, "product_id": 202},
            {"xid": 3, "group_code": 2, "source_table": "MS_SALE_CXMASTERFOUR", "master_id": 30, "product_id": 101},
        ),
        QueryId.QUANTITY_PERCENT_MASTERS: (
            {"master_id": 10, "begin_at": datetime(2026, 7, 1), "end_at": datetime(2026, 8, 1)},
        ),
        QueryId.QUANTITY_PERCENT_DETAILS: (
            {"detail_id": 11, "product_id": "101", "master_id": 10},
        ),
        QueryId.QUANTITY_PERCENT_GLOBAL_RULES: (
            {"rule_id": 12, "master_id": 10},
        ),
        QueryId.QUANTITY_PERCENT_SCHEDULES: (
            {"schedule_id": 13, "begin_at": datetime(2026, 7, 1), "end_at": datetime(2026, 8, 1), "weekday_raw": "1,2", "begin_time": "08:00", "end_time": "20:00", "enabled_raw": 1, "master_id": 10},
        ),
        QueryId.QUANTITY_FIXED_MASTERS: (
            {"master_id": 20, "begin_at": datetime(2026, 7, 1), "end_at": datetime(2026, 8, 1)},
        ),
        QueryId.QUANTITY_FIXED_DETAILS: (
            {"detail_id": 21, "product_id": "202", "master_id": 20},
        ),
        QueryId.QUANTITY_FIXED_SCHEDULES: (
            {"schedule_id": 22, "begin_at": datetime(2026, 7, 1), "end_at": datetime(2026, 8, 1), "weekday_raw": "1", "begin_time": "00:00", "end_time": "23:59", "enabled_raw": 1, "master_id": 20},
        ),
        QueryId.MIX_MATCH_MASTERS: (
            {"master_id": 30, "begin_at": datetime(2026, 7, 1), "end_at": datetime(2026, 8, 1)},
        ),
        QueryId.MIX_MATCH_PRODUCTS: (
            {"detail_id": 31, "product_id": "101", "master_id": 30},
        ),
    }
