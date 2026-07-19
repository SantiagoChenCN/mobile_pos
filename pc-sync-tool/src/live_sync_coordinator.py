from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from typing import Callable, Optional

from live_sync_models import (
    ActivityProbeResult,
    ActivityStatus,
    CancellationGate,
    CircuitState,
    LiveSyncState,
    SyncOutcome,
    SyncPhase,
    SyncTaskContext,
    SyncTaskResult,
)


ActivityProbe = Callable[[], ActivityProbeResult]
FullSyncTask = Callable[[SyncTaskContext], SyncTaskResult]


@dataclass(frozen=True)
class CoordinatorSettings:
    detection_interval_seconds: int = 15
    quiet_window_seconds: int = 10
    failure_threshold: int = 3
    cooldown_seconds: int = 300
    normal_query_timeout_seconds: int = 10
    degraded_query_timeout_seconds: int = 5

    def validated(self) -> "CoordinatorSettings":
        _disabled_or_range("detection_interval_seconds", self.detection_interval_seconds, 5, 86400)
        _range("quiet_window_seconds", self.quiet_window_seconds, 0, 300)
        _range("failure_threshold", self.failure_threshold, 1, 100)
        _range("cooldown_seconds", self.cooldown_seconds, 5, 86400)
        _range("normal_query_timeout_seconds", self.normal_query_timeout_seconds, 1, 300)
        _range("degraded_query_timeout_seconds", self.degraded_query_timeout_seconds, 1, 30)
        return self


class LiveSyncCoordinator:
    def __init__(
        self,
        settings: CoordinatorSettings,
        activity_probe: ActivityProbe,
        full_sync_task: FullSyncTask,
        clock: Callable[[], float] = time.monotonic,
    ):
        self._settings = settings.validated()
        if not callable(activity_probe) or not callable(full_sync_task):
            raise TypeError("coordinator dependencies must be callable")
        self._activity_probe = activity_probe
        self._full_sync_task = full_sync_task
        self._clock = clock
        self._state_lock = threading.Lock()
        self._run_lock = threading.Lock()
        self._condition = threading.Condition(self._state_lock)
        self._thread: Optional[threading.Thread] = None
        self._stop_requested = False
        self._manual_pending = False
        self._current_gate: Optional[CancellationGate] = None
        self._consecutive_failures = 0
        self._circuit_state = CircuitState.CLOSED
        self._next_allowed_at: Optional[float] = None
        self._next_automatic_at = self._clock() + settings.detection_interval_seconds
        self._state = LiveSyncState(SyncPhase.IDLE, "READY", 0.0, 0, CircuitState.CLOSED, None)

    @property
    def state(self) -> LiveSyncState:
        with self._state_lock:
            return self._state

    def start(self) -> None:
        with self._condition:
            if self._thread and self._thread.is_alive():
                return
            self._stop_requested = False
            self._thread = threading.Thread(target=self._run_loop, name="MobilePosLiveSync", daemon=True)
            self._thread.start()

    def stop(self, timeout_seconds: float = 5.0) -> None:
        with self._condition:
            self._stop_requested = True
            self._condition.notify_all()
            thread = self._thread
        if thread:
            thread.join(timeout=max(0.0, timeout_seconds))

    def request_manual_sync(self) -> bool:
        with self._condition:
            if self._manual_pending or self._run_lock.locked():
                return False
            self._manual_pending = True
            self._condition.notify_all()
            return True

    def cancel(self) -> bool:
        with self._condition:
            if self._manual_pending:
                self._manual_pending = False
                self._set_state_locked(SyncPhase.CANCELLED, "PENDING_REQUEST_CANCELLED", 0.0)
                return True
            gate = self._current_gate
        return gate.cancel() if gate else False

    def run_once(self, manual: bool = False) -> LiveSyncState:
        if not self._run_lock.acquire(blocking=False):
            return self._snapshot_state(SyncPhase.FAILED, "SYNC_ALREADY_RUNNING", 0.0)
        started = self._clock()
        try:
            now = self._clock()
            if (
                self._state.phase is SyncPhase.QUIET_WAIT
                and self._next_allowed_at is not None
                and now < self._next_allowed_at
            ):
                return self._snapshot_state(
                    SyncPhase.QUIET_WAIT,
                    "QUIET_WINDOW_ACTIVE",
                    _elapsed_ms(started, self._clock()),
                )
            if self._state.phase is SyncPhase.QUIET_WAIT:
                self._next_allowed_at = None
            if self._circuit_state is CircuitState.OPEN:
                if self._next_allowed_at is not None and now < self._next_allowed_at:
                    self._probe_only()
                    return self._snapshot_state(
                        SyncPhase.CIRCUIT_OPEN,
                        "CIRCUIT_COOLDOWN_ACTIVE",
                        _elapsed_ms(started, self._clock()),
                    )
                if not manual:
                    self._probe_only()
                    return self._snapshot_state(
                        SyncPhase.CIRCUIT_OPEN,
                        "MANUAL_RETRY_REQUIRED",
                        _elapsed_ms(started, self._clock()),
                    )
                self._circuit_state = CircuitState.HALF_OPEN

            self._snapshot_state(SyncPhase.PROBING, "CHECKING_MS2011_ACTIVITY", 0.0)
            probe = self._activity_probe()
            if probe.status is ActivityStatus.BUSY:
                self._next_allowed_at = self._clock() + self._settings.quiet_window_seconds
                return self._snapshot_state(
                    SyncPhase.QUIET_WAIT,
                    probe.reason_code,
                    _elapsed_ms(started, self._clock()),
                )

            degraded = probe.status is ActivityStatus.UNAVAILABLE
            gate = CancellationGate()
            with self._condition:
                self._current_gate = gate
            context = SyncTaskContext(
                cancellation=gate,
                double_read_required=True,
                query_timeout_seconds=(
                    self._settings.degraded_query_timeout_seconds
                    if degraded
                    else self._settings.normal_query_timeout_seconds
                ),
                degraded_activity_probe=degraded,
            )
            self._snapshot_state(
                SyncPhase.READING,
                "ACTIVITY_HINT_UNAVAILABLE_DEGRADED" if degraded else "SAFE_READ_STARTED",
                _elapsed_ms(started, self._clock()),
                degraded,
            )
            result = self._full_sync_task(context)
            if gate.cancelled or result.outcome is SyncOutcome.CANCELLED:
                return self._snapshot_state(
                    SyncPhase.CANCELLED,
                    result.reason_code or "SYNC_CANCELLED_BEFORE_PUBLISH",
                    _elapsed_ms(started, self._clock()),
                    degraded,
                )
            if result.outcome is SyncOutcome.SUCCESS:
                self._consecutive_failures = 0
                self._circuit_state = CircuitState.CLOSED
                self._next_allowed_at = None
                return self._snapshot_state(
                    SyncPhase.SUCCEEDED,
                    result.reason_code or "SYNC_SUCCEEDED",
                    _elapsed_ms(started, self._clock()),
                    degraded,
                )
            return self._record_failure(result, started, degraded)
        except Exception:
            return self._record_failure(
                SyncTaskResult(SyncOutcome.ERROR, "UNEXPECTED_SYNC_ERROR"), started, False
            )
        finally:
            with self._condition:
                self._current_gate = None
            self._run_lock.release()

    def run_automatic_if_due(self) -> LiveSyncState:
        now = self._clock()
        if self._settings.detection_interval_seconds == 0:
            return self._snapshot_state(SyncPhase.DISABLED, "AUTOMATIC_SYNC_DISABLED", 0.0)
        if now < self._next_automatic_at:
            return self.state
        self._next_automatic_at = now + self._settings.detection_interval_seconds
        return self.run_once(manual=False)

    def _record_failure(
        self, result: SyncTaskResult, started: float, degraded: bool
    ) -> LiveSyncState:
        self._consecutive_failures += 1
        if self._consecutive_failures >= self._settings.failure_threshold:
            self._circuit_state = CircuitState.OPEN
            self._next_allowed_at = self._clock() + self._settings.cooldown_seconds
            phase = SyncPhase.CIRCUIT_OPEN
            reason = "CIRCUIT_OPEN_AFTER_" + result.outcome.value
        else:
            phase = SyncPhase.FAILED
            reason = result.reason_code or result.outcome.value
        return self._snapshot_state(
            phase, reason, _elapsed_ms(started, self._clock()), degraded
        )

    def _probe_only(self) -> ActivityProbeResult:
        try:
            return self._activity_probe()
        except Exception:
            return ActivityProbeResult(ActivityStatus.UNAVAILABLE, "ACTIVITY_HINT_UNAVAILABLE")

    def _snapshot_state(
        self,
        phase: SyncPhase,
        reason_code: str,
        elapsed_ms: float,
        degraded: bool = False,
    ) -> LiveSyncState:
        with self._state_lock:
            self._set_state_locked(phase, reason_code, elapsed_ms, degraded)
            return self._state

    def _set_state_locked(
        self,
        phase: SyncPhase,
        reason_code: str,
        elapsed_ms: float,
        degraded: bool = False,
    ) -> None:
        self._state = LiveSyncState(
            phase,
            reason_code,
            elapsed_ms,
            self._consecutive_failures,
            self._circuit_state,
            self._next_allowed_at,
            degraded,
        )

    def _run_loop(self) -> None:
        while True:
            with self._condition:
                if self._stop_requested:
                    return
                manual = self._manual_pending
                if manual:
                    self._manual_pending = False
                interval = self._settings.detection_interval_seconds
                due = interval != 0 and self._clock() >= self._next_automatic_at
                if not manual and not due:
                    wait_seconds = 60.0 if interval == 0 else max(
                        0.01, min(60.0, self._next_automatic_at - self._clock())
                    )
                    self._condition.wait(wait_seconds)
                    continue
            if due:
                self._next_automatic_at = self._clock() + interval
            self.run_once(manual=manual)


def _range(name: str, value: int, minimum: int, maximum: int) -> None:
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")


def _disabled_or_range(name: str, value: int, minimum: int, maximum: int) -> None:
    if value == 0:
        return
    _range(name, value, minimum, maximum)


def _elapsed_ms(started: float, ended: float) -> float:
    return max(0.0, round((ended - started) * 1000.0, 3))
