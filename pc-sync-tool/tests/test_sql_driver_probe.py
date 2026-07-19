from __future__ import annotations

import json
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

import diagnose_ms2011_readonly as probe


class FakeOdbcError(Exception):
    pass


class FakeCursor:
    def __init__(self, rows=None, fail_on_sql=None, failure=None):
        self.rows = rows or {}
        self.fail_on_sql = fail_on_sql
        self.failure = failure
        self.executed = []
        self.current_sql = None
        self.closed = False

    def execute(self, sql):
        self.executed.append(sql)
        self.current_sql = sql
        if sql == self.fail_on_sql:
            raise self.failure
        return self

    def fetchone(self):
        return self.rows.get(self.current_sql)

    def close(self):
        self.closed = True


class FakeConnection:
    def __init__(self, cursor):
        self._cursor = cursor
        self.timeout = None
        self.closed = False

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


def successful_fake():
    rows = {
        probe.QUERY_SQL[probe.QueryId.DATABASE_NAME]: ("MS2011",),
        probe.QUERY_SQL[probe.QueryId.PRODUCT_COUNT]: (11168,),
    }
    cursor = FakeCursor(rows=rows)
    connection = FakeConnection(cursor)
    module = FakePyodbc(["SQL Server"], connection)
    return module, connection, cursor


class SqlDriverProbeTest(unittest.TestCase):
    def test_query_map_is_immutable(self):
        with self.assertRaises(TypeError):
            probe.QUERY_SQL[probe.QueryId.DATABASE_NAME] = "SELECT 1"

    def test_list_drivers_never_connects(self):
        module = FakePyodbc(["SQL Server", "ODBC Driver 18 for SQL Server"])

        result = probe.list_drivers(module)

        self.assertEqual("ok", result["status"])
        self.assertEqual(["SQL Server", "ODBC Driver 18 for SQL Server"], result["drivers"])
        self.assertEqual([], module.connect_calls)
        self.assertEqual(struct.calcsize("P") * 8, result["processBits"])

    def test_missing_pyodbc_is_structured(self):
        result = probe.list_drivers(None, loader=lambda: (_ for _ in ()).throw(ImportError("missing")))

        self.assertEqual("error", result["status"])
        self.assertEqual("PYODBC_UNAVAILABLE", result["error"]["category"])
        self.assertNotIn("traceback", json.dumps(result).lower())

    def test_probe_rejects_invisible_driver_without_connecting(self):
        module = FakePyodbc(["Other Driver"])

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 10), module)

        self.assertEqual("NO_DRIVER", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_probe_rejects_connection_string_injection(self):
        module = FakePyodbc(["SQL Server"])
        invalid = [
            probe.ProbeConfig("SERVER;UID=sa", "MS2011", "SQL Server", 5, 10),
            probe.ProbeConfig("SERVER", "MS2011;PWD=x", "SQL Server", 5, 10),
            probe.ProbeConfig("SERVER", "MS2011", "SQL Server};UID=sa", 5, 10),
        ]

        for config in invalid:
            with self.subTest(config=config):
                result = probe.run_probe(config, module)
                self.assertEqual("INVALID_ARGUMENT", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_probe_rejects_invalid_timeouts(self):
        module = FakePyodbc(["SQL Server"])

        for connect_timeout, query_timeout in [(0, 10), (31, 10), (5, 0), (5, 301)]:
            with self.subTest(connect_timeout=connect_timeout, query_timeout=query_timeout):
                result = probe.run_probe(
                    probe.ProbeConfig("SERVER", "MS2011", "SQL Server", connect_timeout, query_timeout),
                    module,
                )
                self.assertEqual("INVALID_ARGUMENT", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_success_uses_fixed_queries_timeouts_and_read_only_attribute(self):
        module, connection, cursor = successful_fake()

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 12), module)

        self.assertEqual("ok", result["status"])
        self.assertEqual("MS2011", result["results"]["DATABASE_NAME"])
        self.assertEqual(11168, result["results"]["PRODUCT_COUNT"])
        self.assertEqual(list(probe.QUERY_SQL.values()), cursor.executed)
        self.assertEqual(12, connection.timeout)
        connection_string, kwargs = module.connect_calls[0]
        self.assertIn("Trusted_Connection=yes", connection_string)
        self.assertNotIn("UID=", connection_string.upper())
        self.assertNotIn("PWD=", connection_string.upper())
        self.assertEqual(5, kwargs["timeout"])
        self.assertTrue(kwargs["autocommit"])
        self.assertEqual(
            {module.SQL_ATTR_ACCESS_MODE: probe.ODBC_SQL_MODE_READ_ONLY},
            kwargs["attrs_before"],
        )
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_empty_database_name_is_invalid_response(self):
        module, connection, cursor = successful_fake()
        cursor.rows[probe.QUERY_SQL[probe.QueryId.DATABASE_NAME]] = ("",)

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 10), module)

        self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_negative_product_count_is_invalid_response(self):
        module, connection, cursor = successful_fake()
        cursor.rows[probe.QUERY_SQL[probe.QueryId.PRODUCT_COUNT]] = (-1,)

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 10), module)

        self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_query_failure_closes_resources_and_marks_query_timeout(self):
        rows = {probe.QUERY_SQL[probe.QueryId.DATABASE_NAME]: ("MS2011",)}
        failure = FakeOdbcError("HYT00", "query timeout")
        cursor = FakeCursor(
            rows=rows,
            fail_on_sql=probe.QUERY_SQL[probe.QueryId.PRODUCT_COUNT],
            failure=failure,
        )
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 10), module)

        self.assertEqual("QUERY_TIMEOUT", result["error"]["category"])
        self.assertEqual("query:PRODUCT_COUNT", result["error"]["phase"])
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_first_query_failure_closes_resources(self):
        failure = FakeOdbcError("42000", "permission denied")
        cursor = FakeCursor(
            fail_on_sql=probe.QUERY_SQL[probe.QueryId.DATABASE_NAME],
            failure=failure,
        )
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 10), module)

        self.assertEqual("PERMISSION_DENIED", result["error"]["category"])
        self.assertEqual("query:DATABASE_NAME", result["error"]["phase"])
        self.assertEqual([probe.QUERY_SQL[probe.QueryId.DATABASE_NAME]], cursor.executed)
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_connect_timeout_is_distinct_from_query_timeout(self):
        module = FakePyodbc(
            ["SQL Server"],
            connect_failure=FakeOdbcError("HYT01", "connection timeout"),
        )

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 10), module)

        self.assertEqual("CONNECTION_TIMEOUT", result["error"]["category"])
        self.assertEqual("connect", result["error"]["phase"])

    def test_sqlstate_categories(self):
        cases = [
            ("IM002", "connect", "NO_DRIVER"),
            ("IM014", "connect", "BITNESS_MISMATCH"),
            ("28000", "connect", "LOGIN_FAILED"),
            ("42000", "query:DATABASE_NAME", "PERMISSION_DENIED"),
            ("08001", "connect", "PROTOCOL_FAILED"),
            ("ZZZZZ", "connect", "UNKNOWN"),
        ]

        for sqlstate, phase, expected in cases:
            with self.subTest(sqlstate=sqlstate, phase=phase):
                detail = probe.classify_odbc_error(FakeOdbcError(sqlstate, "unsafe detail"), phase)
                self.assertEqual(expected, detail.category)
                self.assertEqual(sqlstate, detail.sqlstate)
                self.assertNotIn("unsafe detail", detail.message)

    def test_result_does_not_expose_connection_string_or_credentials(self):
        module = FakePyodbc(
            ["SQL Server"],
            connect_failure=FakeOdbcError("28000", "PWD=secret;UID=sa"),
        )

        result = probe.run_probe(probe.ProbeConfig("SERVER", "MS2011", "SQL Server", 5, 10), module)
        serialized = json.dumps(result)

        self.assertNotIn("secret", serialized)
        self.assertNotIn("UID=", serialized)
        self.assertNotIn("PWD=", serialized)
        self.assertNotIn("Trusted_Connection", serialized)


if __name__ == "__main__":
    unittest.main()
