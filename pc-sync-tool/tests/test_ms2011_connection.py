from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from ms2011_connection import ConnectionSettings, Ms2011Connection, ReadOnlyConnectionError
from ms2011_query_catalog import QueryId, query_catalog, query_spec
from ms2011_schema import CANDIDATE_KEYS, TABLES, require_fixed_column, require_fixed_table


class FakeCursor:
    def __init__(self, rows=(("MS2011", "SERVER"),), failure=None):
        self.rows = rows
        self.failure = failure
        self.executed = []
        self.description = (("database_name",), ("server_name",))
        self.closed = False

    def execute(self, sql, parameters):
        self.executed.append((sql, parameters))
        if self.failure:
            raise self.failure

    def fetchall(self):
        return self.rows

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


class FakeOdbc:
    SQL_ATTR_ACCESS_MODE = 101

    def __init__(self, connection=None, failure=None):
        self.connection = connection or FakeConnection(FakeCursor())
        self.failure = failure
        self.calls = []

    def connect(self, connection_string, **kwargs):
        self.calls.append((connection_string, kwargs))
        if self.failure:
            raise self.failure
        return self.connection


class Ms2011ConnectionTest(unittest.TestCase):
    def test_catalog_is_fixed_select_only_and_has_no_side_effect_extensions(self):
        forbidden = (
            "SELECT INTO", "OPENROWSET", "OPENDATASOURCE", " INSERT ", " UPDATE ",
            " DELETE ", " DROP ", " ALTER ", " EXEC ", ";",
        )
        for query_id, spec in query_catalog().items():
            with self.subTest(query_id=query_id):
                upper = " " + spec.sql.upper() + " "
                self.assertTrue(spec.sql.startswith("SELECT "))
                self.assertFalse(any(value in upper for value in forbidden))
        with self.assertRaises(TypeError):
            query_spec("DATABASE_IDENTITY")

    def test_external_input_cannot_select_identifiers_or_database(self):
        self.assertEqual(13, len(TABLES))
        self.assertEqual(("GID",), CANDIDATE_KEYS["MS_GOODLIST"])
        with self.assertRaises(ValueError):
            require_fixed_table("MS_GOODLIST; DROP TABLE x")
        with self.assertRaises(ValueError):
            require_fixed_column("MS_GOODLIST", "GID DESC")
        with self.assertRaises(ValueError):
            ConnectionSettings("SERVER;DATABASE=master", "SQL Server").validated()
        with self.assertRaises(ValueError):
            ConnectionSettings("SERVER", "SQL Server", database="master").validated()

    def test_execute_requests_read_only_and_closes_all_resources(self):
        cursor = FakeCursor()
        connection = FakeConnection(cursor)
        module = FakeOdbc(connection)
        adapter = Ms2011Connection(ConnectionSettings("SERVER", "SQL Server"), module)
        rows = adapter.execute(QueryId.DATABASE_IDENTITY)
        self.assertEqual("MS2011", rows[0]["database_name"])
        self.assertEqual({101: 1}, module.calls[0][1]["attrs_before"])
        self.assertIn("DATABASE=MS2011", module.calls[0][0])
        self.assertEqual(10, connection.timeout)
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

    def test_failure_closes_resources_and_read_only_capability_is_explicit(self):
        cursor = FakeCursor(failure=RuntimeError("HYT00 timeout"))
        connection = FakeConnection(cursor)
        with self.assertRaises(ReadOnlyConnectionError) as captured:
            Ms2011Connection(ConnectionSettings("SERVER", "SQL Server"), FakeOdbc(connection)).execute(
                QueryId.PRODUCT_COUNT
            )
        self.assertEqual("QUERY_OR_CONNECT_TIMEOUT", captured.exception.reason_code)
        self.assertTrue(cursor.closed)
        self.assertTrue(connection.closed)

        class NoReadOnlyAttribute:
            pass

        with self.assertRaises(ReadOnlyConnectionError) as missing:
            Ms2011Connection(ConnectionSettings("SERVER", "SQL Server"), NoReadOnlyAttribute()).execute(
                QueryId.DATABASE_IDENTITY
            )
        self.assertEqual("READ_ONLY_ACCESS_MODE_UNAVAILABLE", missing.exception.reason_code)

    def test_parameters_and_unknown_query_ids_are_rejected_before_connect(self):
        module = FakeOdbc()
        adapter = Ms2011Connection(ConnectionSettings("SERVER", "SQL Server"), module)
        with self.assertRaises(TypeError):
            adapter.execute("DATABASE_IDENTITY")
        with self.assertRaises(ValueError):
            adapter.execute(QueryId.DATABASE_IDENTITY, ("external",))
        self.assertEqual([], module.calls)


if __name__ == "__main__":
    unittest.main()
