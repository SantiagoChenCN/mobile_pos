from __future__ import annotations

import threading
from dataclasses import dataclass
from enum import Enum
from typing import Optional


class SyncPhase(str, Enum):
    IDLE = "IDLE"
    PROBING = "PROBING"
    QUIET_WAIT = "QUIET_WAIT"
    READING = "READING"
    PUBLISHING = "PUBLISHING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"
    CIRCUIT_OPEN = "CIRCUIT_OPEN"
    DISABLED = "DISABLED"


class CircuitState(str, Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


class ActivityStatus(str, Enum):
    IDLE = "IDLE"
    BUSY = "BUSY"
    UNAVAILABLE = "UNAVAILABLE"


class SyncOutcome(str, Enum):
    SUCCESS = "SUCCESS"
    TIMEOUT = "TIMEOUT"
    DOUBLE_READ_MISMATCH = "DOUBLE_READ_MISMATCH"
    ERROR = "ERROR"
    CANCELLED = "CANCELLED"


@dataclass(frozen=True)
class ActivityProbeResult:
    status: ActivityStatus
    reason_code: str
    active_request_count: Optional[int] = None


@dataclass(frozen=True)
class SyncTaskResult:
    outcome: SyncOutcome
    reason_code: str = ""


@dataclass(frozen=True)
class LiveSyncState:
    phase: SyncPhase
    reason_code: str
    elapsed_ms: float
    consecutive_failures: int
    circuit_state: CircuitState
    next_allowed_at: Optional[float]
    degraded_activity_probe: bool = False


class CancellationGate:
    def __init__(self):
        self._lock = threading.Lock()
        self._cancelled = False
        self._publishing = False

    @property
    def cancelled(self) -> bool:
        with self._lock:
            return self._cancelled

    @property
    def publishing(self) -> bool:
        with self._lock:
            return self._publishing

    def cancel(self) -> bool:
        with self._lock:
            if self._publishing:
                return False
            self._cancelled = True
            return True

    def begin_publish(self) -> bool:
        with self._lock:
            if self._cancelled:
                return False
            self._publishing = True
            return True


@dataclass(frozen=True)
class SyncTaskContext:
    cancellation: CancellationGate
    double_read_required: bool = True
    query_timeout_seconds: int = 10
    degraded_activity_probe: bool = False
