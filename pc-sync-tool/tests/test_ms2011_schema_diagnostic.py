from __future__ import annotations

import json
import re
import sys
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

import diagnose_ms2011_schema_readonly as schema_probe


class FakeOdbcError(Exception):
    pass


class FakeCursor:
    def __init__(self, metadata_rows=None, stats_rows=None, failure_on_sql=None, failure=None):
        self.metadata_rows = list(metadata_rows or [])
        self.stats_rows = dict(stats_rows or {})
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
        return list(self.metadata_rows)

    def fetchone(self):
        return self.stats_rows.get(self.current_sql)

    def close(self):
        self.closed = True


class FakeConnection:
    def __init__(self, cursor, timeout_failure=None):
        self._cursor = cursor
        self._timeout = None
        self.timeout_failure = timeout_failure
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
    return schema_probe.SchemaConfig(**values)


def metadata_rows():
    rows = []
    for table in schema_probe.TABLES:
        key = schema_probe.CANDIDATE_KEYS[table][0]
        rows.append((table, 1, key, "int", 4, 10, 0, 0))
    return rows


def successful_fake():
    stats = {
        sql: (3, 3, 3, 1, 3)
        for sql in schema_probe.STATS_SQL.values()
    }
    stats[schema_probe.IDENTITY_SQL] = ("MS2011", "SERVER")
    cursor = FakeCursor(metadata_rows=metadata_rows(), stats_rows=stats)
    connection = FakeConnection(cursor)
    module = FakePyodbc(["SQL Server"], connection)
    return module, connection, cursor


class Ms2011SchemaDiagnosticTest(unittest.TestCase):
    def test_contract_is_immutable_and_contains_exact_thirteen_tables(self):
        expected_tables = (
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
        expected_keys = {
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
        self.assertEqual(expected_tables, schema_probe.TABLES)
        self.assertEqual(expected_keys, dict(schema_probe.CANDIDATE_KEYS))
        self.assertEqual(set(expected_tables), set(schema_probe.STATS_SQL))
        with self.assertRaises(TypeError):
            schema_probe.CANDIDATE_KEYS["MS_GOODLIST"] = ("OTHER",)
        with self.assertRaises(TypeError):
            schema_probe.STATS_SQL["MS_GOODLIST"] = "SELECT 1"

    def test_describe_contract_never_loads_or_connects_odbc(self):
        result = schema_probe.describe_contract()

        self.assertEqual("ok", result["status"])
        self.assertEqual("describe-contract", result["mode"])
        self.assertEqual(list(schema_probe.TABLES), result["tables"])
        self.assertNotIn("connectionString", json.dumps(result))

    def test_metadata_sql_is_fixed_sql2000_catalog_query(self):
        sql = schema_probe.METADATA_SQL.upper()

        self.assertTrue(sql.lstrip().startswith("SELECT "))
        self.assertIn("DBO.SYSOBJECTS", sql)
        self.assertIn("DBO.SYSCOLUMNS", sql)
        self.assertIn("DBO.SYSTYPES", sql)
        self.assertIn("C.PREC", sql)
        self.assertIn("C.SCALE", sql)
        self.assertNotIn("SYS.COLUMNS", sql)
        self.assertNotIn("INSERT", sql)
        self.assertNotIn("UPDATE", sql)
        self.assertNotIn("DELETE", sql)

    def test_stats_sql_is_fixed_low_cost_single_key_aggregate(self):
        for table, sql in schema_probe.STATS_SQL.items():
            with self.subTest(table=table):
                key = schema_probe.CANDIDATE_KEYS[table][0]
                upper = sql.upper()
                self.assertTrue(upper.startswith("SELECT COUNT(*)"))
                self.assertIn(f"COUNT([{key}])".upper(), upper)
                self.assertIn(f"COUNT(DISTINCT [{key}])".upper(), upper)
                self.assertIn(f"MIN([{key}])".upper(), upper)
                self.assertIn(f"MAX([{key}])".upper(), upper)
                self.assertIn(f"FROM DBO.[{table}] WITH (NOLOCK)".upper(), upper)
                self.assertNotIn("GROUP BY", upper)

    def test_every_sql_is_select_only_without_side_effect_tokens(self):
        statements = [schema_probe.IDENTITY_SQL, schema_probe.METADATA_SQL, *schema_probe.STATS_SQL.values()]
        forbidden = re.compile(r"\b(INTO|INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|EXEC(?:UTE)?)\b")
        for sql in statements:
            with self.subTest(sql=sql):
                upper = " ".join(sql.upper().split())
                self.assertTrue(upper.startswith("SELECT "))
                self.assertIsNone(forbidden.search(upper))

    def test_schema_rejects_injection_before_connecting(self):
        module = FakePyodbc(["SQL Server"])
        invalid = [
            config(server="SERVER;UID=sa"),
            config(database="MS2011;PWD=x"),
            config(driver="SQL Server};UID=sa"),
        ]

        for item in invalid:
            with self.subTest(item=item):
                result = schema_probe.run_schema(item, module)
                self.assertEqual("INVALID_ARGUMENT", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_non_ms2011_database_is_rejected_before_connecting(self):
        module = FakePyodbc(["SQL Server"])

        result = schema_probe.run_schema(config(database="master"), module)

        self.assertEqual("INVALID_ARGUMENT", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_invisible_driver_fails_without_connecting(self):
        module = FakePyodbc(["Other Driver"])

        result = schema_probe.run_schema(config(), module)

        self.assertEqual("NO_DRIVER", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_schema_uses_read_only_connection_timeout_and_exact_query(self):
        module, connection, cursor = successful_fake()

        result = schema_probe.run_schema(config(query_timeout_seconds=12), module)

        self.assertEqual("ok", result["status"])
        self.assertEqual(13, len(result["tables"]))
        self.assertEqual([schema_probe.IDENTITY_SQL, schema_probe.METADATA_SQL], cursor.executed)
        self.assertEqual("MS2011", result["databaseIdentity"]["database"])
        self.assertEqual("SERVER", result["databaseIdentity"]["server"])
        self.assertEqual(12, connection.timeout)
        connection_string, kwargs = module.connect_calls[0]
        self.assertIn("Trusted_Connection=yes", connection_string)
        self.assertNotIn("UID=", connection_string.upper())
        self.assertNotIn("PWD=", connection_string.upper())
        self.assertEqual(5, kwargs["timeout"])
        self.assertTrue(kwargs["autocommit"])
        self.assertEqual(
            {module.SQL_ATTR_ACCESS_MODE: schema_probe.ODBC_SQL_MODE_READ_ONLY},
            kwargs["attrs_before"],
        )
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_timeout_setter_failure_closes_new_connection(self):
        cursor = FakeCursor()
        connection = FakeConnection(cursor, timeout_failure=FakeOdbcError("HYT00"))
        module = FakePyodbc(["SQL Server"], connection)

        result = schema_probe.run_schema(config(), module)

        self.assertEqual("CONNECTION_TIMEOUT", result["error"]["category"])
        self.assertTrue(connection.closed)

    def test_missing_read_only_attribute_fails_before_connecting(self):
        class MissingAccessModeOdbc:
            def __init__(self):
                self.connect_calls = []

            def drivers(self):
                return ["SQL Server"]

            def connect(self, *args, **kwargs):
                self.connect_calls.append((args, kwargs))

        module = MissingAccessModeOdbc()

        result = schema_probe.run_schema(config(), module)

        self.assertEqual("PROTOCOL_FAILED", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_connect_timeout_is_sanitized(self):
        module = FakePyodbc(
            ["SQL Server"],
            connect_failure=FakeOdbcError("HYT00", "secret PWD=value"),
        )

        result = schema_probe.run_schema(config(), module)

        dumped = json.dumps(result)
        self.assertEqual("CONNECTION_TIMEOUT", result["error"]["category"])
        self.assertNotIn("secret", dumped)
        self.assertNotIn("PWD", dumped.upper())

    def test_database_identity_mismatch_stops_before_metadata(self):
        module, connection, cursor = successful_fake()
        cursor.stats_rows[schema_probe.IDENTITY_SQL] = ("master", "SERVER")

        result = schema_probe.run_schema(config(), module)

        self.assertEqual("IDENTITY_MISMATCH", result["error"]["category"])
        self.assertEqual([schema_probe.IDENTITY_SQL], cursor.executed)
        self.assertTrue(connection.closed)

    def test_schema_preserves_exact_column_metadata_shape(self):
        module, _, _ = successful_fake()

        result = schema_probe.run_schema(config(), module)

        first = result["tables"][0]["columns"][0]
        self.assertEqual(
            {
                "ordinal": 1,
                "name": schema_probe.CANDIDATE_KEYS[schema_probe.TABLES[0]][0],
                "type": "int",
                "length": 4,
                "precision": 10,
                "scale": 0,
                "nullable": False,
            },
            first,
        )

    def test_schema_fails_when_any_candidate_table_is_missing(self):
        rows = metadata_rows()[1:]
        cursor = FakeCursor(
            metadata_rows=rows,
            stats_rows={schema_probe.IDENTITY_SQL: ("MS2011", "SERVER")},
        )
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = schema_probe.run_schema(config(), module)

        self.assertEqual("MISSING_TABLES", result["error"]["category"])
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_stats_runs_only_fixed_queries_in_table_order(self):
        module, connection, cursor = successful_fake()

        result = schema_probe.run_stats(config(), module, schema_reviewed=True)

        self.assertEqual("ok", result["status"])
        self.assertEqual([schema_probe.IDENTITY_SQL, *schema_probe.STATS_SQL.values()], cursor.executed)
        self.assertEqual(13, len(result["tables"]))
        self.assertTrue(all(row["keyStatus"] == "VERIFIED_SINGLE_KEY" for row in result["tables"]))
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_stats_requires_explicit_schema_review_before_connecting(self):
        module, _, _ = successful_fake()

        result = schema_probe.run_stats(config(), module)

        self.assertEqual("SCHEMA_REVIEW_REQUIRED", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_duplicate_or_null_candidate_key_requires_composite_key_evidence(self):
        module, _, cursor = successful_fake()
        first_table = schema_probe.TABLES[0]
        cursor.stats_rows[schema_probe.STATS_SQL[first_table]] = (3, 2, 2, 1, 2)

        result = schema_probe.run_stats(config(), module, schema_reviewed=True)

        first = result["tables"][0]
        self.assertEqual("NEEDS_COMPOSITE_KEY", first["keyStatus"])
        self.assertTrue(result["requiresCompositeKeyEvidence"])

    def test_invalid_stats_shape_closes_resources(self):
        module, connection, cursor = successful_fake()
        cursor.stats_rows[schema_probe.STATS_SQL[schema_probe.TABLES[0]]] = (1, 1)

        result = schema_probe.run_stats(config(), module, schema_reviewed=True)

        self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_numeric_min_max_are_json_safe(self):
        module, _, cursor = successful_fake()
        table = schema_probe.TABLES[0]
        cursor.stats_rows[schema_probe.STATS_SQL[table]] = (
            3,
            3,
            3,
            Decimal("1"),
            Decimal("3"),
        )

        result = schema_probe.run_stats(config(), module, schema_reviewed=True)

        json.dumps(result)
        self.assertEqual("1", result["tables"][0]["minKey"])
        self.assertEqual("3", result["tables"][0]["maxKey"])

    def test_query_timeout_is_classified_and_closes_resources(self):
        sql = schema_probe.STATS_SQL[schema_probe.TABLES[0]]
        cursor = FakeCursor(
            stats_rows={schema_probe.IDENTITY_SQL: ("MS2011", "SERVER")},
            failure_on_sql=sql,
            failure=FakeOdbcError("HYT00", "secret PWD=value"),
        )
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = schema_probe.run_stats(config(), module, schema_reviewed=True)

        dumped = json.dumps(result)
        self.assertEqual("QUERY_TIMEOUT", result["error"]["category"])
        self.assertNotIn("secret", dumped)
        self.assertNotIn("PWD", dumped.upper())
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_middle_stats_failure_stops_remaining_queries_and_closes(self):
        module, connection, cursor = successful_fake()
        failing_table = schema_probe.TABLES[4]
        failing_sql = schema_probe.STATS_SQL[failing_table]
        cursor.failure_on_sql = failing_sql
        cursor.failure = FakeOdbcError("HYT00")

        result = schema_probe.run_stats(config(), module, schema_reviewed=True)

        expected = [schema_probe.IDENTITY_SQL, *list(schema_probe.STATS_SQL.values())[:5]]
        self.assertEqual("QUERY_TIMEOUT", result["error"]["category"])
        self.assertEqual(expected, cursor.executed)
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_output_fields_are_metadata_and_approved_aggregates_only(self):
        module, _, _ = successful_fake()
        schema_result = schema_probe.run_schema(config(), module)
        module, _, _ = successful_fake()
        stats_result = schema_probe.run_stats(config(), module, schema_reviewed=True)

        self.assertEqual(
            {"ordinal", "name", "type", "length", "precision", "scale", "nullable"},
            set(schema_result["tables"][0]["columns"][0]),
        )
        self.assertEqual(
            {
                "table",
                "candidateKey",
                "rowCount",
                "nonNullKeyCount",
                "distinctKeyCount",
                "minKey",
                "maxKey",
                "keyStatus",
            },
            set(stats_result["tables"][0]),
        )
        dumped = json.dumps({"schema": schema_result, "stats": stats_result})
        self.assertNotIn("connectionString", dumped)
        self.assertNotIn("password", dumped.lower())

    def test_schema_query_failure_closes_resources(self):
        cursor = FakeCursor(
            stats_rows={schema_probe.IDENTITY_SQL: ("MS2011", "SERVER")},
            failure_on_sql=schema_probe.METADATA_SQL,
            failure=FakeOdbcError("42000"),
        )
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = schema_probe.run_schema(config(), module)

        self.assertEqual("PERMISSION_DENIED", result["error"]["category"])
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)


if __name__ == "__main__":
    unittest.main()
