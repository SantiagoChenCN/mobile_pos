from __future__ import annotations

import json
import inspect
import re
import sys
import unittest
from datetime import datetime, timezone, timedelta
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

import inventory_ms2011_promotions_readonly as inventory_probe


ART = timezone(timedelta(hours=-3))
AS_OF = datetime(2026, 7, 16, 12, 0, tzinfo=ART)


class FakeOdbcError(Exception):
    pass


class FakeCursor:
    def __init__(self, rows_by_sql=None, failure_on_sql=None, failure=None):
        self.rows_by_sql = dict(rows_by_sql or {})
        self.failure_on_sql = failure_on_sql
        self.failure = failure
        self.executed = []
        self.current_sql = None
        self.closed = False

    def execute(self, sql):
        self.executed.append(sql)
        self.current_sql = sql
        if sql == self.failure_on_sql:
            raise self.failure
        return self

    def fetchall(self):
        return list(self.rows_by_sql.get(self.current_sql, []))

    def fetchone(self):
        rows = self.rows_by_sql.get(self.current_sql, [])
        return rows[0] if rows else None

    def close(self):
        self.closed = True


class FakeConnection:
    def __init__(self, cursor, timeout_failure=None, cursor_failure=None):
        self._cursor = cursor
        self._timeout = None
        self.timeout_failure = timeout_failure
        self.cursor_failure = cursor_failure
        self.closed = False

    @property
    def timeout(self):
        return self._timeout

    @timeout.setter
    def timeout(self, value):
        if self.timeout_failure is not None:
            raise self.timeout_failure
        self._timeout = value

    def cursor(self):
        if self.cursor_failure is not None:
            raise self.cursor_failure
        return self._cursor

    def close(self):
        self.closed = True


class FakePyodbc:
    SQL_ATTR_ACCESS_MODE = 101
    Error = FakeOdbcError

    def __init__(self, drivers=None, connection=None, connect_failure=None):
        self._drivers = list(drivers or [])
        self.connection = connection
        self.connect_failure = connect_failure
        self.connect_calls = []

    def drivers(self):
        return list(self._drivers)

    def connect(self, connection_string, **kwargs):
        self.connect_calls.append((connection_string, kwargs))
        if self.connect_failure is not None:
            raise self.connect_failure
        return self.connection


def config(**overrides):
    values = {
        "server": "SERVER",
        "database": "MS2011",
        "driver": "SQL Server",
        "connect_timeout_seconds": 5,
        "query_timeout_seconds": 10,
    }
    values.update(overrides)
    return inventory_probe.InventoryConfig(**values)


def successful_rows():
    sql = inventory_probe.QUERY_SQL
    return {
        inventory_probe.IDENTITY_SQL: [("MS2011", "CAJA1\\HOME", datetime(2026, 7, 16, 12, 0))],
        sql["PRODUCT_SIMPLE_CANDIDATES"]: [
            (101, 0, 1, 1),
            (102, 0, 1, 0),
            (103, 4, 1, 1),
        ],
        sql["PROMOTION_MAPPINGS"]: [
            (1, "MS_SALE_CXDAN1", 4, 201),
            (2, "MS_SALE_CXMASTERDING", 6, 301),
            (3, "MS_SALE_CXMASTERFOUR", 1, 401),
            (4, "UNEXPECTED_TABLE", 99, 999),
        ],
        sql["QUANTITY_PERCENT_MASTERS"]: [
            (4, datetime(2026, 7, 1), datetime(2026, 7, 31, 23, 59, 59)),
        ],
        sql["QUANTITY_PERCENT_DETAILS"]: [(10, 201, 4)],
        sql["QUANTITY_PERCENT_GLOBAL_RULES"]: [(20, 4)],
        sql["QUANTITY_PERCENT_SCHEDULES"]: [
            (30, datetime(2026, 7, 1), datetime(2026, 7, 31, 23, 59, 59), 4, datetime(1900, 1, 1), datetime(1900, 1, 1, 23, 59, 59), 1, 4),
        ],
        sql["QUANTITY_FIXED_MASTERS"]: [
            (6, datetime(2026, 8, 1), datetime(2026, 8, 31, 23, 59, 59)),
        ],
        sql["QUANTITY_FIXED_DETAILS"]: [(40, 301, 6)],
        sql["QUANTITY_FIXED_SCHEDULES"]: [
            (50, datetime(2026, 8, 1), datetime(2026, 8, 31, 23, 59, 59), 4, datetime(1900, 1, 1), datetime(1900, 1, 1, 23, 59, 59), 1, 6),
        ],
        sql["MIX_MATCH_MASTERS"]: [
            (1, datetime(2026, 6, 1), datetime(2026, 6, 30, 23, 59, 59)),
        ],
        sql["MIX_MATCH_PRODUCTS"]: [(60, 401, 1)],
    }


def successful_fake(rows=None):
    cursor = FakeCursor(rows_by_sql=rows or successful_rows())
    connection = FakeConnection(cursor)
    module = FakePyodbc(["SQL Server"], connection)
    return module, connection, cursor


def walk_keys(value):
    if isinstance(value, dict):
        for key, item in value.items():
            yield key
            yield from walk_keys(item)


def walk_values(value):
    if isinstance(value, dict):
        for item in value.values():
            yield item
            yield from walk_values(item)
    elif isinstance(value, list):
        for item in value:
            yield item
            yield from walk_values(item)
    elif isinstance(value, list):
        for item in value:
            yield from walk_keys(item)


class PromotionInventoryReadonlyTest(unittest.TestCase):
    def test_fixed_query_contract_is_exact_and_immutable(self):
        expected_ids = (
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
        self.assertEqual(expected_ids, inventory_probe.QUERY_IDS)
        self.assertEqual(expected_ids, tuple(inventory_probe.QUERY_SQL))
        self.assertEqual(
            {
                "QUANTITY_PERCENT_DETAILS": (1,),
                "QUANTITY_FIXED_DETAILS": (1,),
                "MIX_MATCH_PRODUCTS": (1,),
            },
            dict(inventory_probe.TEXT_INTEGER_POSITIONS),
        )
        with self.assertRaises(TypeError):
            inventory_probe.QUERY_SQL["OTHER"] = "SELECT 1"
        with self.assertRaises(TypeError):
            inventory_probe.TEXT_INTEGER_POSITIONS["PROMOTION_MAPPINGS"] = (3,)

    def test_cursor_execute_exists_only_in_query_catalog_gate(self):
        source = inspect.getsource(inventory_probe)

        self.assertEqual(1, source.count("cursor.execute("))
        self.assertIn("cursor.execute(QUERY_SQL[query_id])", source)

    def test_describe_contract_is_zero_connection_and_records_safety_policy(self):
        result = inventory_probe.describe_contract()

        self.assertEqual("ok", result["status"])
        self.assertEqual(list(inventory_probe.QUERY_IDS), result["queryIds"])
        self.assertEqual("database-getdate-assumed-art", result["classificationClock"])
        self.assertFalse(result["outputsProductNames"])
        self.assertFalse(result["outputsBarcodes"])
        self.assertFalse(result["outputsFormulaValues"])

    def test_all_sql_is_select_only_nolock_and_stably_ordered(self):
        forbidden = re.compile(
            r"\b(INTO|INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|EXEC(?:UTE)?|TRUNCATE|DBCC|"
            r"BACKUP|RESTORE|ATTACH|DETACH|KILL|OPENROWSET|OPENDATASOURCE)\b"
        )
        statements = list(inventory_probe.QUERY_SQL.values())
        for sql in statements:
            with self.subTest(sql=sql):
                upper = " ".join(sql.upper().split())
                self.assertTrue(upper.startswith("SELECT "))
                self.assertIsNone(forbidden.search(upper))
        for query_id in inventory_probe.INVENTORY_QUERY_IDS:
            sql = inventory_probe.QUERY_SQL[query_id]
            with self.subTest(query_id=query_id):
                self.assertIn("WITH (NOLOCK)", sql.upper())
                self.assertIn("ORDER BY", sql.upper())

    def test_simple_query_returns_flags_not_raw_prices_or_names(self):
        sql = inventory_probe.QUERY_SQL["PRODUCT_SIMPLE_CANDIDATES"].upper()

        self.assertIn("CASE WHEN", sql)
        self.assertNotIn("GNAME", sql)
        self.assertNotIn("GBARCODE", sql)
        self.assertNotRegex(sql, r"SELECT\s+[^;]*\[GHUIPRICE\]\s*(?:,|FROM)")

    def test_inventory_rejects_injection_and_non_ms2011_before_connecting(self):
        module = FakePyodbc(["SQL Server"])
        invalid = [
            config(server="SERVER;UID=sa"),
            config(database="master"),
            config(driver="SQL Server};PWD=x"),
        ]

        for item in invalid:
            with self.subTest(item=item):
                result = inventory_probe.run_inventory(item, module, now_art=AS_OF)
                self.assertEqual("INVALID_ARGUMENT", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_inventory_uses_read_only_connection_timeouts_and_fixed_order(self):
        module, connection, cursor = successful_fake()

        result = inventory_probe.run_inventory(config(query_timeout_seconds=12), module, now_art=AS_OF)

        self.assertEqual("ok", result["status"])
        self.assertEqual(
            [
                inventory_probe.IDENTITY_SQL,
                *(inventory_probe.QUERY_SQL[query_id] for query_id in inventory_probe.INVENTORY_QUERY_IDS),
                *(inventory_probe.QUERY_SQL[query_id] for query_id in inventory_probe.INVENTORY_QUERY_IDS),
            ],
            cursor.executed,
        )
        self.assertLessEqual(set(cursor.executed), set(inventory_probe.QUERY_SQL.values()))
        self.assertEqual(12, connection.timeout)
        connection_string, kwargs = module.connect_calls[0]
        self.assertIn("Trusted_Connection=yes", connection_string)
        self.assertNotIn("UID=", connection_string.upper())
        self.assertNotIn("PWD=", connection_string.upper())
        self.assertEqual(5, kwargs["timeout"])
        self.assertTrue(kwargs["autocommit"])
        self.assertEqual(
            {module.SQL_ATTR_ACCESS_MODE: inventory_probe.ODBC_SQL_MODE_READ_ONLY},
            kwargs["attrs_before"],
        )
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_inventory_classifies_all_four_required_buckets(self):
        module, _, _ = successful_fake()

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual(
            {
                "currentEnabled": 2,
                "futureConfigured": 1,
                "historicalExpired": 1,
                "unableToDetermine": 3,
            },
            result["classificationCounts"],
        )
        self.assertEqual(7, len(result["candidates"]))

    def test_double_read_change_fails_closed_without_partial_inventory(self):
        rows = successful_rows()
        target_sql = inventory_probe.QUERY_SQL["PRODUCT_SIMPLE_CANDIDATES"]

        class ChangingCursor(FakeCursor):
            def fetchall(self):
                values = list(self.rows_by_sql.get(self.current_sql, []))
                if self.current_sql == target_sql and self.executed.count(target_sql) == 2:
                    return values + [(104, 0, 1, 1)]
                return values

        cursor = ChangingCursor(rows_by_sql=rows)
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("error", result["status"])
        self.assertEqual("SOURCE_CHANGED", result["error"]["category"])
        self.assertNotIn("candidates", result)
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_second_pass_query_failure_releases_resources_without_partial_inventory(self):
        rows = successful_rows()
        target_sql = inventory_probe.QUERY_SQL["QUANTITY_FIXED_DETAILS"]

        class SecondPassFailureCursor(FakeCursor):
            def execute(self, sql):
                if sql == target_sql and self.executed.count(target_sql) == 1:
                    self.executed.append(sql)
                    self.current_sql = sql
                    raise FakeOdbcError("HYT00", "secret second pass")
                return super().execute(sql)

        cursor = SecondPassFailureCursor(rows_by_sql=rows)
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("QUERY_TIMEOUT", result["error"]["category"])
        self.assertNotIn("candidates", result)
        self.assertNotIn("secret", json.dumps(result))
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_duplicate_stable_key_fails_closed(self):
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["PROMOTION_MAPPINGS"]] = [
            (1, "MS_SALE_CXDAN1", 4, 201),
            (1, "MS_SALE_CXDAN1", 4, 202),
        ]
        module, connection, cursor = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
        self.assertNotIn("candidates", result)
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_mapping_detail_product_mismatch_is_unable(self):
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["QUANTITY_PERCENT_DETAILS"]] = [(10, 202, 4)]
        module, _, _ = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
        candidate = next(item for item in result["candidates"] if item["candidateType"] == "QUANTITY_PERCENT")

        self.assertEqual("unableToDetermine", candidate["classification"])
        self.assertIn("MAPPING_DETAIL_MISMATCH", candidate["anomalies"])

    def test_orphan_child_rows_are_reported_as_unable_candidates(self):
        cases = (
            ("QUANTITY_PERCENT_DETAILS", (11, 998, 99), "ORPHAN_DETAIL"),
            (
                "QUANTITY_PERCENT_SCHEDULES",
                (31, datetime(2026, 7, 1), datetime(2026, 7, 31), 4, datetime(1900, 1, 1), datetime(1900, 1, 1, 23, 0), 1, 99),
                "ORPHAN_SCHEDULE",
            ),
            ("QUANTITY_PERCENT_GLOBAL_RULES", (21, 99), "ORPHAN_GLOBAL_RULE"),
        )

        for query_id, orphan_row, anomaly in cases:
            with self.subTest(query_id=query_id):
                rows = successful_rows()
                sql = inventory_probe.QUERY_SQL[query_id]
                rows[sql] = [*rows[sql], orphan_row]
                module, _, _ = successful_fake(rows)

                result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
                orphan = next(
                    item
                    for item in result["candidates"]
                    if item["sourceTable"] == "MS_SALE_CXDAN1" and item["sourceKey"] == 99
                )

                self.assertEqual("unableToDetermine", orphan["classification"])
                self.assertIn(anomaly, orphan["anomalies"])

    def test_invalid_schedule_shape_never_classifies_current(self):
        invalid_rows = (
            (30, "not-a-date", datetime(2026, 7, 31), 4, datetime(1900, 1, 1), datetime(1900, 1, 1, 23), 1, 4),
            (30, datetime(2026, 7, 1), datetime(2026, 7, 31), 999, datetime(1900, 1, 1), datetime(1900, 1, 1, 23), 1, 4),
            (30, datetime(2026, 7, 1), datetime(2026, 7, 31), 4, "bad", datetime(1900, 1, 1, 23), 1, 4),
            (30, datetime(2026, 7, 1), datetime(2026, 7, 31), 4, datetime(1900, 1, 1, 23), datetime(1900, 1, 1), 1, 4),
        )

        for invalid_row in invalid_rows:
            with self.subTest(invalid_row=invalid_row):
                rows = successful_rows()
                rows[inventory_probe.QUERY_SQL["QUANTITY_PERCENT_SCHEDULES"]] = [invalid_row]
                module, _, _ = successful_fake(rows)

                result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
                candidate = next(item for item in result["candidates"] if item["candidateType"] == "QUANTITY_PERCENT")

                self.assertEqual("unableToDetermine", candidate["classification"])
                self.assertTrue(any(code.startswith("INVALID_SCHEDULE_") for code in candidate["anomalies"]))

    def test_unknown_source_table_value_is_never_echoed(self):
        marker = "SECRET_DSN_AND_TABLE_NAME"
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["PROMOTION_MAPPINGS"]][-1] = (4, marker, 99, 999)
        module, _, _ = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertNotIn(marker, json.dumps(result))
        unknown = next(item for item in result["candidates"] if item["candidateType"] == "UNKNOWN")
        self.assertEqual("UNKNOWN_SOURCE_TABLE", unknown["sourceTable"])

    def test_zero_or_negative_source_keys_foreign_keys_and_gids_fail_closed(self):
        cases = (
            ("PRODUCT_SIMPLE_CANDIDATES", (0, 0, 1, 1)),
            ("PROMOTION_MAPPINGS", (1, "MS_SALE_CXDAN1", 0, 201)),
            ("PROMOTION_MAPPINGS", (1, "MS_SALE_CXDAN1", 4, -1)),
            ("QUANTITY_PERCENT_DETAILS", (10, 0, 4)),
            ("QUANTITY_PERCENT_DETAILS", (10, 201, -1)),
            ("QUANTITY_PERCENT_GLOBAL_RULES", (20, 0)),
            (
                "QUANTITY_PERCENT_SCHEDULES",
                (30, datetime(2026, 7, 1), datetime(2026, 7, 31), 4, datetime(1900, 1, 1), datetime(1900, 1, 1, 23), 1, 0),
            ),
        )

        for query_id, invalid_row in cases:
            with self.subTest(query_id=query_id, invalid_row=invalid_row):
                rows = successful_rows()
                rows[inventory_probe.QUERY_SQL[query_id]] = [invalid_row]
                module, _, _ = successful_fake(rows)

                result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

                self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
                self.assertNotIn("candidates", result)

    def test_legacy_varchar_product_ids_are_normalized_only_in_fixed_detail_columns(self):
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["QUANTITY_PERCENT_DETAILS"]] = [(10, "201", 4)]
        rows[inventory_probe.QUERY_SQL["QUANTITY_FIXED_DETAILS"]] = [(40, "301", 6)]
        rows[inventory_probe.QUERY_SQL["MIX_MATCH_PRODUCTS"]] = [(60, "401", 1)]
        module, _, _ = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("ok", result["status"])
        by_type = {item["candidateType"]: item for item in result["candidates"]}
        self.assertEqual([201], by_type["QUANTITY_PERCENT"]["associatedProductIds"])
        self.assertEqual([301], by_type["QUANTITY_FIXED_TOTAL"]["associatedProductIds"])
        self.assertEqual([401], by_type["MIX_MATCH_FIXED_TOTAL"]["associatedProductIds"])
        self.assertTrue(all(isinstance(value, int) for item in by_type.values() for value in item["associatedProductIds"]))

    def test_legacy_varchar_product_ids_reject_noncanonical_text(self):
        cases = (
            ("QUANTITY_PERCENT_DETAILS", (10, " 201", 4)),
            ("QUANTITY_PERCENT_DETAILS", (10, "+201", 4)),
            ("QUANTITY_FIXED_DETAILS", (40, "0201", 6)),
            ("QUANTITY_FIXED_DETAILS", (40, "201.0", 6)),
            ("QUANTITY_FIXED_DETAILS", (40, "0", 6)),
            ("MIX_MATCH_PRODUCTS", (60, "", 1)),
            ("MIX_MATCH_PRODUCTS", (60, "product-401", 1)),
            ("MIX_MATCH_PRODUCTS", (60, "４０１", 1)),
            ("MIX_MATCH_PRODUCTS", (60, "٤٠١", 1)),
        )

        for query_id, invalid_row in cases:
            with self.subTest(query_id=query_id, invalid_row=invalid_row):
                rows = successful_rows()
                rows[inventory_probe.QUERY_SQL[query_id]] = [invalid_row]
                module, _, _ = successful_fake(rows)

                result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

                self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
                self.assertNotIn("candidates", result)

    def test_text_integer_compatibility_does_not_expand_to_keys_or_master_ids(self):
        cases = (
            ("PROMOTION_MAPPINGS", (1, "MS_SALE_CXDAN1", 4, "201")),
            ("QUANTITY_PERCENT_DETAILS", ("10", "201", 4)),
            ("QUANTITY_PERCENT_DETAILS", (10, "201", "4")),
            ("QUANTITY_FIXED_DETAILS", (40, "301", "6")),
            ("MIX_MATCH_PRODUCTS", (60, "401", "1")),
        )

        for query_id, invalid_row in cases:
            with self.subTest(query_id=query_id, invalid_row=invalid_row):
                rows = successful_rows()
                rows[inventory_probe.QUERY_SQL[query_id]] = [invalid_row]
                module, _, _ = successful_fake(rows)

                result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

                self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
                self.assertNotIn("candidates", result)

    def test_global_rule_count_is_a_structural_gate(self):
        for global_rows in ([], [(20, 4), (21, 4)]):
            with self.subTest(global_rows=global_rows):
                rows = successful_rows()
                rows[inventory_probe.QUERY_SQL["QUANTITY_PERCENT_GLOBAL_RULES"]] = global_rows
                module, _, _ = successful_fake(rows)

                result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
                candidate = next(item for item in result["candidates"] if item["candidateType"] == "QUANTITY_PERCENT")

                self.assertEqual("unableToDetermine", candidate["classification"])
                self.assertIn("GLOBAL_RULE_COUNT_MISMATCH", candidate["anomalies"])
                self.assertFalse(candidate["associatedProductsComplete"])

    def test_source_hash_and_output_are_stable_when_driver_row_order_changes(self):
        ordered_rows = successful_rows()
        shuffled_rows = {sql: list(reversed(rows)) for sql, rows in ordered_rows.items()}
        ordered_module, _, _ = successful_fake(ordered_rows)
        shuffled_module, _, _ = successful_fake(shuffled_rows)

        ordered = inventory_probe.run_inventory(config(), ordered_module, now_art=AS_OF)
        shuffled = inventory_probe.run_inventory(config(), shuffled_module, now_art=AS_OF)

        self.assertEqual(ordered["sourceHash"], shuffled["sourceHash"])
        self.assertEqual(ordered["candidates"], shuffled["candidates"])

    def test_types_come_from_source_tables_not_names(self):
        module, _, _ = successful_fake()

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
        by_source = {(item["sourceTable"], item["sourceKey"]): item for item in result["candidates"]}

        self.assertEqual("QUANTITY_PERCENT", by_source[("MS_SALE_CXDAN1", 4)]["candidateType"])
        self.assertEqual("QUANTITY_FIXED_TOTAL", by_source[("MS_SALE_CXMASTERDING", 6)]["candidateType"])
        self.assertEqual("MIX_MATCH_FIXED_TOTAL", by_source[("MS_SALE_CXMASTERFOUR", 1)]["candidateType"])
        unknown = by_source[("UNKNOWN_SOURCE_TABLE", 99)]
        self.assertEqual("UNKNOWN", unknown["candidateType"])
        self.assertEqual("unableToDetermine", unknown["classification"])

    def test_associated_products_are_sorted_ids_only(self):
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["PROMOTION_MAPPINGS"]] = [
            (2, "MS_SALE_CXDAN1", 4, 203),
            (1, "MS_SALE_CXDAN1", 4, 201),
            (3, "MS_SALE_CXDAN1", 4, 202),
        ]
        module, _, _ = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
        candidate = next(item for item in result["candidates"] if item["candidateType"] == "QUANTITY_PERCENT")

        self.assertEqual([201, 202, 203], candidate["associatedProductIds"])

    def test_output_excludes_names_barcodes_and_formula_values(self):
        module, _, _ = successful_fake()

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
        keys = {key.lower() for key in walk_keys(result)}

        self.assertFalse({"name", "barcode", "discount", "price", "amount", "memo", "server", "driver"} & keys)
        self.assertTrue(result["requiresBlackBoxEvidence"])

    def test_current_complex_candidate_without_enabled_schedule_is_unable(self):
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["QUANTITY_PERCENT_SCHEDULES"]] = []
        module, _, _ = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
        candidate = next(item for item in result["candidates"] if item["candidateType"] == "QUANTITY_PERCENT")

        self.assertEqual("unableToDetermine", candidate["classification"])
        self.assertIn("MISSING_ENABLED_SCHEDULE", candidate["anomalies"])

    def test_invalid_master_dates_are_unable(self):
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["QUANTITY_PERCENT_MASTERS"]] = [(4, "not-a-date", None)]
        module, _, _ = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)
        candidate = next(item for item in result["candidates"] if item["candidateType"] == "QUANTITY_PERCENT")

        self.assertEqual("unableToDetermine", candidate["classification"])
        self.assertIn("INVALID_DATE_RANGE", candidate["anomalies"])

    def test_database_identity_mismatch_stops_before_inventory_queries(self):
        rows = successful_rows()
        rows[inventory_probe.IDENTITY_SQL] = [("master", "CAJA1\\HOME", datetime(2026, 7, 16, 12, 0))]
        module, connection, cursor = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("IDENTITY_MISMATCH", result["error"]["category"])
        self.assertEqual([inventory_probe.IDENTITY_SQL], cursor.executed)
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_query_failure_stops_remaining_queries_and_sanitizes_error(self):
        rows = successful_rows()
        failure_sql = inventory_probe.QUERY_SQL["PROMOTION_MAPPINGS"]
        cursor = FakeCursor(rows, failure_sql, FakeOdbcError("HYT00", "secret DSN"))
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("QUERY_TIMEOUT", result["error"]["category"])
        self.assertNotIn("secret", json.dumps(result))
        self.assertEqual(
            [inventory_probe.IDENTITY_SQL, inventory_probe.QUERY_SQL["PRODUCT_SIMPLE_CANDIDATES"], failure_sql],
            cursor.executed,
        )
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_timeout_setter_failure_closes_connection(self):
        cursor = FakeCursor(successful_rows())
        connection = FakeConnection(cursor, FakeOdbcError("HYT00", "secret"))
        module = FakePyodbc(["SQL Server"], connection)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("CONNECTION_TIMEOUT", result["error"]["category"])
        self.assertTrue(connection.closed)

    def test_cursor_creation_failure_closes_connection(self):
        connection = FakeConnection(None, cursor_failure=FakeOdbcError("08001", "secret server"))
        module = FakePyodbc(["SQL Server"], connection)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("PROTOCOL_FAILED", result["error"]["category"])
        self.assertNotIn("secret", json.dumps(result))
        self.assertTrue(connection.closed)

    def test_invalid_row_shape_fails_closed_and_releases_resources(self):
        rows = successful_rows()
        rows[inventory_probe.QUERY_SQL["PROMOTION_MAPPINGS"]] = [(1, "MS_SALE_CXDAN1")]
        module, connection, cursor = successful_fake(rows)

        result = inventory_probe.run_inventory(config(), module, now_art=AS_OF)

        self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_invisible_driver_and_missing_readonly_attribute_never_query(self):
        invisible = FakePyodbc(["Other Driver"])
        result = inventory_probe.run_inventory(config(), invisible, now_art=AS_OF)
        self.assertEqual("NO_DRIVER", result["error"]["category"])
        self.assertEqual([], invisible.connect_calls)

        class MissingReadonly(FakePyodbc):
            SQL_ATTR_ACCESS_MODE = None

        missing = MissingReadonly(["SQL Server"])
        result = inventory_probe.run_inventory(config(), missing, now_art=AS_OF)
        self.assertEqual("PROTOCOL_FAILED", result["error"]["category"])
        self.assertEqual([], missing.connect_calls)

    def test_now_must_be_timezone_aware_art(self):
        module, _, _ = successful_fake()

        result = inventory_probe.run_inventory(config(), module, now_art=datetime(2026, 7, 16, 12, 0))

        self.assertEqual("INVALID_ARGUMENT", result["error"]["category"])


if __name__ == "__main__":
    unittest.main()
