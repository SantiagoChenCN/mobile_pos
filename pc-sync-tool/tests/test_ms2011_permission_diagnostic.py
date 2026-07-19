from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

import diagnose_ms2011_permissions_readonly as permission_probe


EXPECTED_TABLES = (
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
EXPECTED_SERVER_ROLES = (
    "sysadmin",
    "securityadmin",
    "serveradmin",
    "setupadmin",
    "processadmin",
    "dbcreator",
    "diskadmin",
    "bulkadmin",
)
EXPECTED_DATABASE_ROLES = (
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
EXPECTED_ROLES = EXPECTED_SERVER_ROLES + EXPECTED_DATABASE_ROLES
EXPECTED_QUERY_IDS = (
    "DATABASE_IDENTITY",
    "ROLE_MEMBERSHIP",
    "STATEMENT_PERMISSIONS",
    "REQUIRED_TABLE_PERMISSIONS",
    "USER_OBJECT_PERMISSIONS",
    "MASTER_IDENTITY",
    "MASTER_EXTENDED_PERMISSIONS",
)


class FakeOdbcError(Exception):
    pass


class FakeCursor:
    def __init__(self, responses=None, failure_on_sql=None, failure=None):
        self.responses = {key: list(value) for key, value in (responses or {}).items()}
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

    def _next(self):
        values = self.responses.get(self.current_sql, [])
        if not values:
            return None
        return values.pop(0)

    def fetchone(self):
        return self._next()

    def fetchall(self):
        value = self._next()
        return [] if value is None else list(value)

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

    def __init__(self, drivers=None, connection=None, connections=None, connect_failure=None):
        self._drivers = list(drivers or [])
        self.connection = connection
        self.connections = list(connections or ([] if connection is None else [connection]))
        self._connection_index = 0
        self.connect_failure = connect_failure
        self.connect_calls = []

    def drivers(self):
        return list(self._drivers)

    def connect(self, connection_string, **kwargs):
        self.connect_calls.append((connection_string, kwargs))
        if self.connect_failure is not None:
            raise self.connect_failure
        if self._connection_index >= len(self.connections):
            return self.connection
        connection = self.connections[self._connection_index]
        self._connection_index += 1
        return connection


def config(**overrides):
    values = {
        "server": "SERVER",
        "database": "MS2011",
        "driver": "SQL Server",
        "connect_timeout_seconds": 5,
        "query_timeout_seconds": 10,
    }
    values.update(overrides)
    return permission_probe.PermissionConfig(**values)


def role_row(**overrides):
    values = {role: 0 for role in EXPECTED_ROLES}
    values["db_datareader"] = 1
    values.update(overrides)
    return tuple(values[role] for role in EXPECTED_ROLES)


def required_rows(mask=permission_probe.SELECT_ALL_BIT):
    return [(table, mask) for table in EXPECTED_TABLES]


def object_rows(mask=permission_probe.SELECT_ALL_BIT):
    return [("U", mask) for _ in EXPECTED_TABLES]


def successful_responses(*, roles=None, statement_mask=0, required=None, objects=None):
    identity = ("MS2011", "DOMAIN\\sync_reader", "sync_reader")
    roles = role_row() if roles is None else roles
    required = required_rows() if required is None else required
    objects = object_rows() if objects is None else objects
    return {
        permission_probe.IDENTITY_SQL: [identity, identity],
        permission_probe.ROLE_SQL: [roles, roles],
        permission_probe.STATEMENT_PERMISSIONS_SQL: [(statement_mask,), (statement_mask,)],
        permission_probe.REQUIRED_TABLE_PERMISSIONS_SQL: [required, required],
        permission_probe.OBJECT_PERMISSIONS_SQL: [objects, objects],
    }


def successful_master_responses(*, masks=None, login="DOMAIN\\sync_reader"):
    identity = ("master", login)
    masks = [0] if masks is None else masks
    values = [(1 if index == 0 else 0, mask) for index, mask in enumerate(masks)]
    return {
        permission_probe.MASTER_IDENTITY_SQL: [identity, identity],
        permission_probe.MASTER_EXTENDED_PERMISSIONS_SQL: [values, values],
    }


def successful_fake(master_masks=None, **kwargs):
    cursor = FakeCursor(successful_responses(**kwargs))
    connection = FakeConnection(cursor)
    master_cursor = FakeCursor(successful_master_responses(masks=master_masks))
    master_connection = FakeConnection(master_cursor)
    module = FakePyodbc(["SQL Server"], connections=[connection, master_connection])
    module.master_connection = master_connection
    module.master_cursor = master_cursor
    return module, connection, cursor


class Ms2011PermissionDiagnosticTest(unittest.TestCase):
    def test_contract_is_zero_connection_and_frozen(self):
        result = permission_probe.describe_contract()

        self.assertEqual("ok", result["status"])
        self.assertEqual("describe-contract", result["mode"])
        self.assertEqual("MS2011", result["databaseRequired"])
        self.assertTrue(result["doubleReadRequired"])
        self.assertEqual(
            ["READ_ONLY_PROVEN", "WRITE_CAPABILITY_PRESENT", "UNKNOWN"],
            result["assessmentStatuses"],
        )
        self.assertEqual(list(EXPECTED_TABLES), result["requiredTables"])
        self.assertEqual(list(EXPECTED_SERVER_ROLES), result["serverRolesChecked"])
        self.assertEqual(list(EXPECTED_DATABASE_ROLES), result["databaseRolesChecked"])
        self.assertEqual(list(EXPECTED_QUERY_IDS), result["queryIds"])

    def test_queries_are_fixed_select_only_sql2000_compatible(self):
        forbidden = re.compile(r"\b(INTO|INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|EXEC(?:UTE)?|GRANT|REVOKE)\b")
        self.assertEqual(set(EXPECTED_QUERY_IDS), set(permission_probe.QUERY_CATALOG))
        for sql in permission_probe.QUERY_CATALOG.values():
            with self.subTest(sql=sql):
                compact = " ".join(sql.upper().split())
                self.assertTrue(compact.startswith("SELECT "))
                self.assertIsNone(forbidden.search(compact))
                self.assertNotIn("FN_MY_PERMISSIONS", compact)
                self.assertNotIn("HAS_PERMS_BY_NAME", compact)
        self.assertIn("PERMISSIONS()", permission_probe.STATEMENT_PERMISSIONS_SQL.upper())
        self.assertIn("DBO.SYSOBJECTS", permission_probe.OBJECT_PERMISSIONS_SQL.upper())
        self.assertIn("IS_SRVROLEMEMBER", permission_probe.ROLE_SQL.upper())
        self.assertIn("IS_MEMBER", permission_probe.ROLE_SQL.upper())

    def test_permission_bitmap_constants_match_microsoft_contract(self):
        self.assertEqual(1, permission_probe.SELECT_ALL_BIT)
        self.assertEqual(2, permission_probe.UPDATE_ALL_BIT)
        self.assertEqual(8, permission_probe.INSERT_BIT)
        self.assertEqual(16, permission_probe.DELETE_BIT)
        self.assertEqual(32, permission_probe.EXECUTE_BIT)
        self.assertEqual(4096, permission_probe.SELECT_ANY_BIT)
        self.assertEqual(8192, permission_probe.UPDATE_ANY_BIT)
        self.assertEqual(64, permission_probe.BACKUP_DATABASE_BIT)
        self.assertEqual(128, permission_probe.BACKUP_LOG_BIT)

    def test_query_catalog_is_immutable(self):
        with self.assertRaises(TypeError):
            permission_probe.QUERY_CATALOG["OTHER"] = "SELECT 1"

    def test_invalid_arguments_fail_before_connection(self):
        module = FakePyodbc(["SQL Server"])
        for item in (
            config(server="SERVER;UID=sa"),
            config(database="OTHER"),
            config(driver="SQL Server};PWD=x"),
            config(connect_timeout_seconds=0),
            config(query_timeout_seconds=301),
        ):
            with self.subTest(item=item):
                result = permission_probe.run_permission_diagnostic(item, module)
                self.assertEqual("INVALID_ARGUMENT", result["error"]["category"])
        self.assertEqual([], module.connect_calls)

    def test_read_only_evidence_returns_proven_and_closes_resources(self):
        module, connection, cursor = successful_fake()

        result = permission_probe.run_permission_diagnostic(config(), module)

        self.assertEqual("ok", result["status"])
        self.assertEqual("READ_ONLY_PROVEN", result["permissionAssessment"])
        self.assertTrue(result["doubleReadMatched"])
        self.assertEqual("MS2011", result["databaseIdentity"]["database"])
        self.assertEqual("DOMAIN\\sync_reader", result["connectionIdentity"]["login"])
        self.assertEqual("windows-integrated", result["authentication"])
        self.assertEqual(13, result["requiredTableCount"])
        self.assertEqual(0, result["writeCapableObjectCount"])
        self.assertEqual([], result["reasons"])
        self.assertTrue(result["odbcReadOnlyAccessModeAccepted"])
        self.assertTrue(connection.closed)
        self.assertTrue(cursor.closed)
        self.assertTrue(module.master_connection.closed)
        self.assertTrue(module.master_cursor.closed)
        self.assertEqual(10, connection.timeout)
        self.assertEqual(2, len(module.connect_calls))
        connection_string, kwargs = module.connect_calls[0]
        self.assertIn("Trusted_Connection=yes", connection_string)
        self.assertNotIn("UID=", connection_string)
        self.assertEqual(5, kwargs["timeout"])
        self.assertTrue(kwargs["autocommit"])
        self.assertEqual({101: 1}, kwargs["attrs_before"])
        master_connection_string, master_kwargs = module.connect_calls[1]
        self.assertIn("DATABASE=master", master_connection_string)
        self.assertEqual({101: 1}, master_kwargs["attrs_before"])

    def test_output_does_not_echo_server_driver_or_connection_string(self):
        module, _, _ = successful_fake()
        result = permission_probe.run_permission_diagnostic(config(server="SECRET-SERVER"), module)
        text = json.dumps(result, ensure_ascii=False)

        self.assertNotIn("SECRET-SERVER", text)
        self.assertNotIn("SQL Server", text)
        self.assertNotIn("Trusted_Connection", text)
        self.assertNotIn("connectionString", text)

    def test_direct_write_database_roles_are_write_capability(self):
        for role in ("db_owner", "db_accessadmin", "db_securityadmin", "db_ddladmin", "db_datawriter"):
            with self.subTest(role=role):
                module, _, _ = successful_fake(roles=role_row(**{role: 1}))
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("WRITE_CAPABILITY_PRESENT", result["permissionAssessment"])
                self.assertIn(f"ROLE:{role}", result["reasons"])

    def test_direct_write_server_roles_are_write_capability(self):
        for role in ("sysadmin", "securityadmin", "dbcreator", "bulkadmin"):
            with self.subTest(role=role):
                module, _, _ = successful_fake(roles=role_row(**{role: 1}))
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("WRITE_CAPABILITY_PRESENT", result["permissionAssessment"])
                self.assertIn(f"ROLE:{role}", result["reasons"])

    def test_ambiguous_elevated_roles_are_unknown(self):
        for role in ("serveradmin", "setupadmin", "processadmin", "diskadmin", "db_backupoperator"):
            with self.subTest(role=role):
                module, _, _ = successful_fake(roles=role_row(**{role: 1}))
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("UNKNOWN", result["permissionAssessment"])
                self.assertIn(f"ELEVATED_ROLE:{role}", result["reasons"])

    def test_null_role_membership_is_unknown(self):
        module, _, _ = successful_fake(roles=role_row(sysadmin=None))
        result = permission_probe.run_permission_diagnostic(config(), module)
        self.assertEqual("UNKNOWN", result["permissionAssessment"])
        self.assertIn("ROLE_MEMBERSHIP_INDETERMINATE:sysadmin", result["reasons"])

    def test_statement_ddl_and_grantable_ddl_are_write_capability(self):
        for mask in (2, 8 << 16):
            with self.subTest(mask=mask):
                module, _, _ = successful_fake(statement_mask=mask)
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("WRITE_CAPABILITY_PRESENT", result["permissionAssessment"])
                self.assertIn("STATEMENT_DDL_PERMISSION", result["reasons"])

    def test_backup_and_unknown_statement_bits_are_unknown(self):
        for mask, reason in (
            (64, "DATABASE_BACKUP_PERMISSION"),
            (128, "DATABASE_BACKUP_PERMISSION"),
            (64 << 16, "DATABASE_BACKUP_PERMISSION"),
            (256, "UNKNOWN_STATEMENT_PERMISSION"),
        ):
            with self.subTest(mask=mask):
                module, _, _ = successful_fake(statement_mask=mask)
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("UNKNOWN", result["permissionAssessment"])
                self.assertIn(reason, result["reasons"])

    def test_table_and_view_write_bits_are_write_capability(self):
        for object_type, mask in (
            ("U", permission_probe.INSERT_BIT),
            ("U", permission_probe.UPDATE_ANY_BIT),
            ("V", permission_probe.DELETE_BIT << 16),
        ):
            with self.subTest(object_type=object_type, mask=mask):
                rows = object_rows() + [(object_type, mask)]
                module, _, _ = successful_fake(objects=rows)
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("WRITE_CAPABILITY_PRESENT", result["permissionAssessment"])
                self.assertIn("OBJECT_WRITE_PERMISSION", result["reasons"])

    def test_executable_user_procedure_is_unknown(self):
        for object_type in ("P", "X", "RF"):
            with self.subTest(object_type=object_type):
                module, _, _ = successful_fake(objects=object_rows() + [(object_type, permission_probe.EXECUTE_BIT)])
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("UNKNOWN", result["permissionAssessment"])
                self.assertIn("EXECUTABLE_USER_OBJECT", result["reasons"])

    def test_executable_master_extended_procedure_is_unknown(self):
        for mask in (32, 32 << 16):
            with self.subTest(mask=mask):
                module, _, _ = successful_fake(master_masks=[mask])
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("UNKNOWN", result["permissionAssessment"])
                self.assertIn("EXECUTABLE_MASTER_EXTENDED_PROCEDURE", result["reasons"])
                self.assertEqual(1, result["executableMasterExtendedProcedureCount"])

    def test_empty_master_extended_procedure_evidence_fails_closed(self):
        module, _, _ = successful_fake(master_masks=[])
        result = permission_probe.run_permission_diagnostic(config(), module)
        self.assertEqual("error", result["status"])
        self.assertEqual("INVALID_RESPONSE", result["error"]["category"])
        self.assertNotIn("permissionAssessment", result)

    def test_missing_or_duplicate_xp_cmdshell_sentinel_fails_closed(self):
        for values in (
            [(0, 0)],
            [(1, 0), (1, 0)],
        ):
            with self.subTest(values=values):
                ms_cursor = FakeCursor(successful_responses())
                master_responses = successful_master_responses()
                master_responses[permission_probe.MASTER_EXTENDED_PERMISSIONS_SQL] = [values, values]
                master_cursor = FakeCursor(master_responses)
                module = FakePyodbc(
                    ["SQL Server"],
                    connections=[FakeConnection(ms_cursor), FakeConnection(master_cursor)],
                )
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("INVALID_RESPONSE", result["error"]["category"])

    def test_master_second_read_change_fails_closed(self):
        ms_cursor = FakeCursor(successful_responses())
        master_responses = successful_master_responses()
        master_responses[permission_probe.MASTER_EXTENDED_PERMISSIONS_SQL] = [
            [(1, 0)],
            [(1, 32)],
        ]
        master_cursor = FakeCursor(master_responses)
        module = FakePyodbc(
            ["SQL Server"],
            connections=[FakeConnection(ms_cursor), FakeConnection(master_cursor)],
        )
        result = permission_probe.run_permission_diagnostic(config(), module)
        self.assertEqual("SOURCE_CHANGED", result["error"]["category"])
        self.assertNotIn("permissionAssessment", result)

    def test_missing_full_select_on_required_table_is_unknown(self):
        rows = required_rows()
        rows[0] = (EXPECTED_TABLES[0], permission_probe.SELECT_ANY_BIT)
        module, _, _ = successful_fake(required=rows)
        result = permission_probe.run_permission_diagnostic(config(), module)
        self.assertEqual("UNKNOWN", result["permissionAssessment"])
        self.assertIn("REQUIRED_SELECT_NOT_PROVEN", result["reasons"])

    def test_required_table_write_permission_is_write_capability(self):
        rows = required_rows()
        rows[0] = (EXPECTED_TABLES[0], permission_probe.SELECT_ALL_BIT | permission_probe.INSERT_BIT)
        module, _, _ = successful_fake(required=rows)
        result = permission_probe.run_permission_diagnostic(config(), module)
        self.assertEqual("WRITE_CAPABILITY_PRESENT", result["permissionAssessment"])
        self.assertIn("REQUIRED_TABLE_WRITE_PERMISSION", result["reasons"])

    def test_duplicate_or_missing_required_table_rows_fail_closed(self):
        for rows in (required_rows()[:-1], required_rows() + [required_rows()[0]]):
            with self.subTest(rows=len(rows)):
                module, _, _ = successful_fake(required=rows)
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("error", result["status"])
                self.assertEqual("INVALID_RESPONSE", result["error"]["category"])

    def test_invalid_permission_masks_fail_closed(self):
        cases = [
            {"statement_mask": -1},
            {"statement_mask": True},
            {"required": [(EXPECTED_TABLES[0], None), *required_rows()[1:]]},
            {"objects": [("U", "8")]},
        ]
        for kwargs in cases:
            with self.subTest(kwargs=kwargs):
                module, _, _ = successful_fake(**kwargs)
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual("INVALID_RESPONSE", result["error"]["category"])

    def test_double_read_change_fails_without_partial_assessment(self):
        responses = successful_responses()
        responses[permission_probe.STATEMENT_PERMISSIONS_SQL] = [(0,), (permission_probe.CREATE_TABLE_BIT,)]
        cursor = FakeCursor(responses)
        connection = FakeConnection(cursor)
        module = FakePyodbc(["SQL Server"], connection)

        result = permission_probe.run_permission_diagnostic(config(), module)

        self.assertEqual("SOURCE_CHANGED", result["error"]["category"])
        self.assertNotIn("permissionAssessment", result)
        self.assertTrue(connection.closed)
        self.assertTrue(cursor.closed)

    def test_identity_mismatch_fails_closed(self):
        responses = successful_responses()
        responses[permission_probe.IDENTITY_SQL] = [("OTHER", "x", "x"), ("OTHER", "x", "x")]
        cursor = FakeCursor(responses)
        module = FakePyodbc(["SQL Server"], FakeConnection(cursor))
        result = permission_probe.run_permission_diagnostic(config(), module)
        self.assertEqual("IDENTITY_MISMATCH", result["error"]["category"])

    def test_timeout_and_permission_errors_are_sanitized(self):
        for sqlstate, expected in (("HYT00", "QUERY_TIMEOUT"), ("42000", "PERMISSION_DENIED")):
            with self.subTest(sqlstate=sqlstate):
                error = FakeOdbcError(sqlstate, "secret server detail")
                cursor = FakeCursor(successful_responses(), permission_probe.IDENTITY_SQL, error)
                module = FakePyodbc(["SQL Server"], FakeConnection(cursor))
                result = permission_probe.run_permission_diagnostic(config(), module)
                self.assertEqual(expected, result["error"]["category"])
                self.assertNotIn("secret", json.dumps(result))

    def test_query_timeout_assignment_failure_closes_connection(self):
        error = FakeOdbcError("HYT00", "secret")
        cursor = FakeCursor(successful_responses())
        connection = FakeConnection(cursor, timeout_failure=error)
        module = FakePyodbc(["SQL Server"], connection)
        result = permission_probe.run_permission_diagnostic(config(), module)
        self.assertEqual("CONNECTION_TIMEOUT", result["error"]["category"])
        self.assertTrue(connection.closed)

    def test_exit_codes_are_stable(self):
        self.assertEqual(0, permission_probe.exit_code_for({"status": "ok"}))
        self.assertEqual(2, permission_probe.exit_code_for({"status": "error", "error": {"category": "INVALID_ARGUMENT"}}))
        self.assertEqual(5, permission_probe.exit_code_for({"status": "error", "error": {"category": "SOURCE_CHANGED"}}))
        self.assertEqual(1, permission_probe.exit_code_for({"status": "error", "error": {"category": "UNKNOWN"}}))


if __name__ == "__main__":
    unittest.main()
