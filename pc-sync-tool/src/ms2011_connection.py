from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Mapping, Sequence

import pyodbc

from ms2011_query_catalog import QueryId, query_spec
from ms2011_schema import DATABASE_NAME


ODBC_SQL_MODE_READ_ONLY = 1
_SERVER = re.compile(r"^[A-Za-z0-9._\\,-]{1,128}$")


class ReadOnlyConnectionError(RuntimeError):
    def __init__(self, reason_code: str, message: str):
        super().__init__(message)
        self.reason_code = reason_code


@dataclass(frozen=True)
class ConnectionSettings:
    server: str
    driver: str
    connect_timeout_seconds: int = 5
    query_timeout_seconds: int = 10
    database: str = DATABASE_NAME

    def validated(self) -> "ConnectionSettings":
        if self.database != DATABASE_NAME:
            raise ValueError("database is fixed to MS2011")
        if not isinstance(self.server, str) or not _SERVER.fullmatch(self.server):
            raise ValueError("server contains unsupported characters")
        if (
            not isinstance(self.driver, str)
            or not 1 <= len(self.driver) <= 128
            or any(character in self.driver for character in ";{}\r\n\x00")
        ):
            raise ValueError("driver contains unsupported characters")
        _timeout("connect_timeout_seconds", self.connect_timeout_seconds, 1, 30)
        _timeout("query_timeout_seconds", self.query_timeout_seconds, 1, 300)
        return self


class Ms2011Connection:
    __slots__ = ("__settings", "__odbc")

    def __init__(self, settings: ConnectionSettings, odbc_module=None):
        self.__settings = settings.validated()
        self.__odbc = odbc_module or pyodbc

    def execute(
        self, query_id: QueryId, parameters: Sequence[Any] = ()
    ) -> tuple[Mapping[str, Any], ...]:
        spec = query_spec(query_id)
        if isinstance(parameters, (str, bytes)) or not isinstance(parameters, Sequence):
            raise TypeError("parameters must be a sequence")
        bound = tuple(parameters)
        if len(bound) != spec.parameter_count:
            raise ValueError("parameter count does not match the fixed QueryId")

        module = self.__odbc
        try:
            access_mode = module.SQL_ATTR_ACCESS_MODE
        except AttributeError as exc:
            raise ReadOnlyConnectionError(
                "READ_ONLY_ACCESS_MODE_UNAVAILABLE",
                "ODBC driver manager does not expose the read-only access-mode attribute",
            ) from exc

        connection = None
        cursor = None
        try:
            connection = module.connect(
                _connection_string(self.__settings),
                timeout=self.__settings.connect_timeout_seconds,
                attrs_before={access_mode: ODBC_SQL_MODE_READ_ONLY},
            )
            connection.timeout = self.__settings.query_timeout_seconds
            cursor = connection.cursor()
            cursor.execute(spec.sql, bound)
            description = cursor.description or ()
            column_names = tuple(str(column[0]) for column in description)
            rows = cursor.fetchall()
            return tuple(
                {name: value for name, value in zip(column_names, row)} for row in rows
            )
        except ReadOnlyConnectionError:
            raise
        except Exception as exc:
            reason = _classify_error(exc)
            raise ReadOnlyConnectionError(reason, "MS2011 read-only query failed") from exc
        finally:
            if cursor is not None:
                try:
                    cursor.close()
                except Exception:
                    pass
            if connection is not None:
                try:
                    connection.close()
                except Exception:
                    pass


def _connection_string(settings: ConnectionSettings) -> str:
    return (
        f"DRIVER={{{settings.driver}}};SERVER={settings.server};DATABASE={DATABASE_NAME};"
        "Trusted_Connection=yes;APP=MobilePosSync"
    )


def _timeout(name: str, value: int, minimum: int, maximum: int) -> None:
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")


def _classify_error(exc: Exception) -> str:
    text = " ".join(str(part) for part in getattr(exc, "args", (exc,))).upper()
    if "HYT00" in text or "HYT01" in text or "TIMEOUT" in text:
        return "QUERY_OR_CONNECT_TIMEOUT"
    if "HYC00" in text or "IM001" in text or "ACCESS MODE" in text:
        return "READ_ONLY_ACCESS_MODE_REJECTED"
    return "ODBC_QUERY_FAILED"
