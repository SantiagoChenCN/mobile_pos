from __future__ import annotations

from ms2011_query_catalog import QueryId
from ms2011_rows import build_rows


def promotion_rows():
    rows = build_rows()
    return {
        query_id: value
        for query_id, value in rows.items()
        if query_id not in (QueryId.PRODUCTS, QueryId.CATEGORIES, QueryId.UNITS)
    }
