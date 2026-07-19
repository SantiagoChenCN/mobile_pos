from __future__ import annotations

import sys
import threading
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from live_sync_coordinator import CoordinatorSettings, LiveSyncCoordinator
from live_sync_models import (
    ActivityProbeResult,
    ActivityStatus,
    CancellationGate,
    CircuitState,
    SyncOutcome,
    SyncPhase,
    SyncTaskResult,
)
from ms2011_activity_probe import Ms2011ActivityProbe
from read_only_ms2011_session import ReadOnlyMs2011Session


class FakeClock:
    def __init__(self):
        self.value = 1000.0

    def __call__(self):
        return self.value

    def advance(self, seconds):
        self.value += seconds


def idle_probe():
    return ActivityProbeResult(ActivityStatus.IDLE, "MS2011_APPEARS_IDLE", 0)


class LiveSyncCoordinatorTest(unittest.TestCase):
    def test_busy_probe_observes_quiet_window_without_starting_read(self):
        calls = []
        clock = FakeClock()
        coordinator = LiveSyncCoordinator(
            CoordinatorSettings(quiet_window_seconds=10),
            lambda: ActivityProbeResult(ActivityStatus.BUSY, "MS2011_ACTIVITY_DETECTED", 2),
            lambda context: calls.append(context) or SyncTaskResult(SyncOutcome.SUCCESS),
            clock,
        )
        state = coordinator.run_once(manual=True)
        self.assertEqual(SyncPhase.QUIET_WAIT, state.phase)
        self.assertEqual(1010.0, state.next_allowed_at)
        self.assertEqual([], calls)
        retry = coordinator.run_once(manual=True)
        self.assertEqual("QUIET_WINDOW_ACTIVE", retry.reason_code)
        self.assertEqual([], calls)
        clock.advance(10)
        coordinator = LiveSyncCoordinator(
            CoordinatorSettings(quiet_window_seconds=10),
            idle_probe,
            lambda context: calls.append(context) or SyncTaskResult(SyncOutcome.SUCCESS),
            clock,
        )
        self.assertEqual(SyncPhase.SUCCEEDED, coordinator.run_once(manual=True).phase)

    def test_unavailable_activity_hint_degrades_to_short_timeout_and_double_read(self):
        contexts = []
        coordinator = LiveSyncCoordinator(
            CoordinatorSettings(degraded_query_timeout_seconds=3),
            lambda: ActivityProbeResult(ActivityStatus.UNAVAILABLE, "NO_PERMISSION"),
            lambda context: contexts.append(context) or SyncTaskResult(SyncOutcome.SUCCESS),
        )
        state = coordinator.run_once(manual=True)
        self.assertEqual(SyncPhase.SUCCEEDED, state.phase)
        self.assertTrue(state.degraded_activity_probe)
        self.assertEqual(3, contexts[0].query_timeout_seconds)
        self.assertTrue(contexts[0].double_read_required)

    def test_timeout_and_double_read_failures_open_circuit(self):
        clock = FakeClock()
        outcomes = iter(
            (
                SyncTaskResult(SyncOutcome.TIMEOUT, "QUERY_TIMEOUT"),
                SyncTaskResult(SyncOutcome.DOUBLE_READ_MISMATCH, "DOUBLE_READ_MISMATCH"),
                SyncTaskResult(SyncOutcome.SUCCESS),
            )
        )
        probes = []
        coordinator = LiveSyncCoordinator(
            CoordinatorSettings(failure_threshold=2, cooldown_seconds=5),
            lambda: probes.append("probe") or idle_probe(),
            lambda context: next(outcomes),
            clock,
        )
        self.assertEqual(SyncPhase.FAILED, coordinator.run_once(manual=True).phase)
        opened = coordinator.run_once(manual=True)
        self.assertEqual(SyncPhase.CIRCUIT_OPEN, opened.phase)
        self.assertEqual(CircuitState.OPEN, opened.circuit_state)
        self.assertEqual(1005.0, opened.next_allowed_at)

        blocked = coordinator.run_once(manual=False)
        self.assertEqual("CIRCUIT_COOLDOWN_ACTIVE", blocked.reason_code)
        self.assertEqual(2, len(probes) - 1)  # two full-run probes plus one probe-only call

        clock.advance(5)
        automatic = coordinator.run_once(manual=False)
        self.assertEqual("MANUAL_RETRY_REQUIRED", automatic.reason_code)
        recovered = coordinator.run_once(manual=True)
        self.assertEqual(SyncPhase.SUCCEEDED, recovered.phase)
        self.assertEqual(CircuitState.CLOSED, recovered.circuit_state)

    def test_one_running_task_rejects_parallel_and_manual_queue_is_bounded(self):
        entered = threading.Event()
        release = threading.Event()

        def task(context):
            entered.set()
            release.wait(2)
            return SyncTaskResult(SyncOutcome.SUCCESS)

        coordinator = LiveSyncCoordinator(CoordinatorSettings(), idle_probe, task)
        worker = threading.Thread(target=lambda: coordinator.run_once(manual=True))
        worker.start()
        self.assertTrue(entered.wait(1))
        self.assertEqual("SYNC_ALREADY_RUNNING", coordinator.run_once(manual=True).reason_code)
        self.assertFalse(coordinator.request_manual_sync())
        release.set()
        worker.join(2)

    def test_cancellation_stops_pre_publish_but_not_publish_phase(self):
        gate = CancellationGate()
        self.assertTrue(gate.cancel())
        self.assertFalse(gate.begin_publish())
        publishing = CancellationGate()
        self.assertTrue(publishing.begin_publish())
        self.assertFalse(publishing.cancel())

    def test_automatic_zero_is_disabled(self):
        coordinator = LiveSyncCoordinator(
            CoordinatorSettings(detection_interval_seconds=0),
            idle_probe,
            lambda context: SyncTaskResult(SyncOutcome.SUCCESS),
        )
        self.assertEqual(SyncPhase.DISABLED, coordinator.run_automatic_if_due().phase)

    def test_activity_probe_never_disguises_missing_permission_as_idle(self):
        unavailable = Ms2011ActivityProbe(
            ReadOnlyMs2011Session(lambda query_id, parameters: (_ for _ in ()).throw(PermissionError()))
        ).run()
        self.assertEqual(ActivityStatus.UNAVAILABLE, unavailable.status)
        busy = Ms2011ActivityProbe(
            ReadOnlyMs2011Session(lambda query_id, parameters: ({"active_request_count": 2},))
        ).run()
        self.assertEqual(ActivityStatus.BUSY, busy.status)


if __name__ == "__main__":
    unittest.main()
