from __future__ import annotations

from typing import Any, Callable, Iterable, Mapping, Sequence

from ms2011_query_catalog import QueryId


Row = Mapping[str, Any] | Sequence[Any]
QueryRunner = Callable[[QueryId, tuple[Any, ...]], Iterable[Row]]


class ReadOnlyMs2011Session:
    __slots__ = ("__runner",)

    def __init__(self, runner: QueryRunner):
        if not callable(runner):
            raise TypeError("runner must be callable")
        self.__runner = runner

    def execute(self, query_id: QueryId, parameters: Sequence[Any] = ()) -> tuple[Row, ...]:
        if not isinstance(query_id, QueryId):
            raise TypeError("query_id must be a frozen QueryId")
        if isinstance(parameters, (str, bytes)) or not isinstance(parameters, Sequence):
            raise TypeError("parameters must be a sequence")
        rows = self.__runner(query_id, tuple(parameters))
        copied: list[Row] = []
        for row in rows:
            if isinstance(row, Mapping):
                copied.append(dict(row))
            elif isinstance(row, Sequence) and not isinstance(row, (str, bytes)):
                copied.append(tuple(row))
            else:
                raise TypeError("query rows must be mappings or sequences")
        return tuple(copied)
