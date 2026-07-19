from __future__ import annotations

import hashlib
import json
import math
import time
from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Any, Callable, Mapping

from ms2011_query_catalog import QueryId
from read_only_ms2011_session import ReadOnlyMs2011Session


FAST_QUERY_IDS = (QueryId.PRODUCT_CHANGE_SUMMARY, QueryId.PROMOTION_CHANGE_SUMMARY)


class ChangeProbeError(RuntimeError):
    def __init__(self, reason_code: str):
        super().__init__(reason_code)
        self.reason_code = reason_code


@dataclass(frozen=True)
class QueryPerformance:
    query_id: QueryId
    sample_count: int
    p50_ms: float
    p95_ms: float
    max_ms: float


@dataclass(frozen=True)
class FastProbeResult:
    summary_hash: str
    product_summary: Mapping[str, Any]
    promotion_summary: Mapping[str, Any]
    performance: tuple[QueryPerformance, ...]
    proves_complete: bool = False


class FingerprintAction(str, Enum):
    FULL_READ_REQUIRED = "FULL_READ_REQUIRED"
    FAST_ONLY_NO_CHANGE = "FAST_ONLY_NO_CHANGE"
    FULL_READ_BLOCKED = "FULL_READ_BLOCKED"


@dataclass(frozen=True)
class FingerprintDecision:
    action: FingerprintAction
    reason_code: str


class ChangeProbe:
    def __init__(
        self,
        session: ReadOnlyMs2011Session,
        clock: Callable[[], float] = time.perf_counter,
        max_samples: int = 256,
    ):
        if not isinstance(session, ReadOnlyMs2011Session):
            raise TypeError("session must be ReadOnlyMs2011Session")
        if isinstance(max_samples, bool) or not isinstance(max_samples, int) or not 1 <= max_samples <= 10_000:
            raise ValueError("max_samples must be between 1 and 10000")
        self._session = session
        self._clock = clock
        self._max_samples = max_samples
        self._samples = {query_id: [] for query_id in FAST_QUERY_IDS}

    def run(self) -> FastProbeResult:
        rows_by_query = {}
        for query_id in FAST_QUERY_IDS:
            started = self._clock()
            try:
                rows = self._session.execute(query_id)
            except Exception as exc:
                raise ChangeProbeError("FAST_PROBE_QUERY_FAILED") from exc
            elapsed_ms = max(0.0, round((self._clock() - started) * 1000.0, 3))
            samples = self._samples[query_id]
            samples.append(elapsed_ms)
            del samples[:-self._max_samples]
            if len(rows) != 1 or not isinstance(rows[0], Mapping):
                raise ChangeProbeError("FAST_PROBE_INVALID_SHAPE")
            rows_by_query[query_id] = dict(rows[0])

        product = rows_by_query[QueryId.PRODUCT_CHANGE_SUMMARY]
        promotion = rows_by_query[QueryId.PROMOTION_CHANGE_SUMMARY]
        _validate_nonnegative(product, ("product_count", "max_gid"), allow_none=("max_gid",))
        _validate_nonnegative(
            promotion,
            ("mapping_count", "max_mapping_id", "master_count"),
            allow_none=("max_mapping_id",),
        )
        updated = product.get("max_updated_at")
        if updated is not None and not isinstance(updated, datetime):
            raise ChangeProbeError("FAST_PROBE_INVALID_DATETIME")
        digest = _summary_hash(product, promotion)
        return FastProbeResult(
            digest,
            product,
            promotion,
            tuple(self.performance()),
            False,
        )

    def performance(self) -> tuple[QueryPerformance, ...]:
        return tuple(
            QueryPerformance(
                query_id,
                len(self._samples[query_id]),
                _percentile(self._samples[query_id], 0.50),
                _percentile(self._samples[query_id], 0.95),
                max(self._samples[query_id], default=0.0),
            )
            for query_id in FAST_QUERY_IDS
        )


class FingerprintPolicy:
    def __init__(self, full_interval_seconds: int = 900):
        if (
            isinstance(full_interval_seconds, bool)
            or not isinstance(full_interval_seconds, int)
            or not 60 <= full_interval_seconds <= 86400
        ):
            raise ValueError("full fingerprint interval must be between 60 and 86400 seconds")
        self._interval = full_interval_seconds
        self._last_fast_hash: str | None = None
        self._last_full_at: float | None = None

    def evaluate(
        self, probe: FastProbeResult, now: float, full_read_allowed: bool
    ) -> FingerprintDecision:
        if not isinstance(probe, FastProbeResult) or probe.proves_complete:
            raise ValueError("a fast probe must remain explicitly non-authoritative")
        changed = self._last_fast_hash != probe.summary_hash
        due = self._last_full_at is None or now - self._last_full_at >= self._interval
        self._last_fast_hash = probe.summary_hash
        if changed or due:
            reason = "FAST_SUMMARY_CHANGED" if changed else "PERIODIC_FULL_FINGERPRINT_DUE"
            if not full_read_allowed:
                return FingerprintDecision(FingerprintAction.FULL_READ_BLOCKED, reason)
            return FingerprintDecision(FingerprintAction.FULL_READ_REQUIRED, reason)
        return FingerprintDecision(FingerprintAction.FAST_ONLY_NO_CHANGE, "FAST_SUMMARY_UNCHANGED")

    def mark_full_read_completed(self, now: float) -> None:
        self._last_full_at = now


def _validate_nonnegative(
    row: Mapping[str, Any], fields: tuple[str, ...], allow_none: tuple[str, ...] = ()
) -> None:
    for field in fields:
        if field not in row:
            raise ChangeProbeError("FAST_PROBE_MISSING_FIELD")
        value = row[field]
        if value is None and field in allow_none:
            continue
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            raise ChangeProbeError("FAST_PROBE_INVALID_COUNT")


def _summary_hash(product: Mapping[str, Any], promotion: Mapping[str, Any]) -> str:
    normalized = {
        "product": {
            key: value.isoformat(timespec="microseconds") if isinstance(value, datetime) else value
            for key, value in product.items()
        },
        "promotion": dict(promotion),
    }
    payload = json.dumps(normalized, sort_keys=True, separators=(",", ":"), allow_nan=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _percentile(samples: list[float], fraction: float) -> float:
    if not samples:
        return 0.0
    ordered = sorted(samples)
    index = max(0, math.ceil(fraction * len(ordered)) - 1)
    return ordered[index]
