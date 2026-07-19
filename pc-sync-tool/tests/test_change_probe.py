from __future__ import annotations

import sys
import unittest
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from change_probe import (
    ChangeProbe,
    ChangeProbeError,
    FastProbeResult,
    FingerprintAction,
    FingerprintPolicy,
)
from ms2011_query_catalog import QueryId, query_catalog
from read_only_ms2011_session import ReadOnlyMs2011Session


class StepClock:
    def __init__(self, values):
        self.values = iter(values)

    def __call__(self):
        return next(self.values)


def rows():
    return {
        QueryId.PRODUCT_CHANGE_SUMMARY: (
            {"product_count": 2, "max_updated_at": datetime(2026, 7, 17, 10), "max_gid": 202},
        ),
        QueryId.PROMOTION_CHANGE_SUMMARY: (
            {"mapping_count": 3, "max_mapping_id": 3, "master_count": 3},
        ),
    }


class ChangeProbeTest(unittest.TestCase):
    def test_fast_queries_are_low_cost_fixed_selects_and_never_prove_completeness(self):
        source = rows()
        calls = []
        probe = ChangeProbe(
            ReadOnlyMs2011Session(lambda query_id, parameters: calls.append(query_id) or source[query_id]),
            StepClock((0.0, 0.010, 0.010, 0.030)),
        ).run()
        self.assertFalse(probe.proves_complete)
        self.assertEqual([QueryId.PRODUCT_CHANGE_SUMMARY, QueryId.PROMOTION_CHANGE_SUMMARY], calls)
        self.assertEqual([10.0, 20.0], [item.max_ms for item in probe.performance])
        for query_id in calls:
            self.assertTrue(query_catalog()[query_id].low_cost)

    def test_invalid_shape_count_and_datetime_fail_closed(self):
        invalid_cases = (
            {QueryId.PRODUCT_CHANGE_SUMMARY: (), QueryId.PROMOTION_CHANGE_SUMMARY: rows()[QueryId.PROMOTION_CHANGE_SUMMARY]},
            {QueryId.PRODUCT_CHANGE_SUMMARY: ({"product_count": -1, "max_updated_at": None, "max_gid": None},), QueryId.PROMOTION_CHANGE_SUMMARY: rows()[QueryId.PROMOTION_CHANGE_SUMMARY]},
            {QueryId.PRODUCT_CHANGE_SUMMARY: ({"product_count": 1, "max_updated_at": "today", "max_gid": 1},), QueryId.PROMOTION_CHANGE_SUMMARY: rows()[QueryId.PROMOTION_CHANGE_SUMMARY]},
        )
        for source in invalid_cases:
            with self.subTest(source=source):
                probe = ChangeProbe(ReadOnlyMs2011Session(lambda query_id, parameters, source=source: source[query_id]))
                with self.assertRaises(ChangeProbeError):
                    probe.run()

    def test_policy_requires_periodic_full_read_and_respects_safety_gate(self):
        source = rows()
        probe = ChangeProbe(ReadOnlyMs2011Session(lambda query_id, parameters: source[query_id])).run()
        policy = FingerprintPolicy(900)
        blocked = policy.evaluate(probe, now=1000.0, full_read_allowed=False)
        self.assertEqual(FingerprintAction.FULL_READ_BLOCKED, blocked.action)
        required = policy.evaluate(probe, now=1000.0, full_read_allowed=True)
        self.assertEqual(FingerprintAction.FULL_READ_REQUIRED, required.action)
        policy.mark_full_read_completed(1000.0)
        unchanged = policy.evaluate(probe, now=1100.0, full_read_allowed=True)
        self.assertEqual(FingerprintAction.FAST_ONLY_NO_CHANGE, unchanged.action)
        due = policy.evaluate(probe, now=1900.0, full_read_allowed=True)
        self.assertEqual("PERIODIC_FULL_FINGERPRINT_DUE", due.reason_code)

    def test_fast_hash_change_requests_full_read_even_without_timestamp_change(self):
        source = rows()
        first = ChangeProbe(ReadOnlyMs2011Session(lambda query_id, parameters: source[query_id])).run()
        policy = FingerprintPolicy(900)
        policy.evaluate(first, 1000.0, True)
        policy.mark_full_read_completed(1000.0)
        changed_rows = rows()
        changed_rows[QueryId.PROMOTION_CHANGE_SUMMARY][0]["mapping_count"] = 4
        second = ChangeProbe(ReadOnlyMs2011Session(lambda query_id, parameters: changed_rows[query_id])).run()
        decision = policy.evaluate(second, 1010.0, True)
        self.assertEqual(FingerprintAction.FULL_READ_REQUIRED, decision.action)
        self.assertEqual("FAST_SUMMARY_CHANGED", decision.reason_code)

    def test_performance_reports_p50_p95_and_max_with_bounded_history(self):
        source = rows()
        clock_values = []
        elapsed = (0.001, 0.003, 0.002)
        current = 0.0
        for duration in elapsed:
            clock_values.extend((current, current + duration, current + duration, current + duration * 2))
            current += 1.0
        probe = ChangeProbe(
            ReadOnlyMs2011Session(lambda query_id, parameters: source[query_id]),
            StepClock(clock_values),
            max_samples=3,
        )
        probe.run(); probe.run(); probe.run()
        performance = probe.performance()[0]
        self.assertEqual(3, performance.sample_count)
        self.assertEqual(2.0, performance.p50_ms)
        self.assertEqual(3.0, performance.p95_ms)
        self.assertEqual(3.0, performance.max_ms)


if __name__ == "__main__":
    unittest.main()
