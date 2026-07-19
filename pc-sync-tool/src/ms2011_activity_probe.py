from __future__ import annotations

from live_sync_models import ActivityProbeResult, ActivityStatus
from ms2011_query_catalog import QueryId
from read_only_ms2011_session import ReadOnlyMs2011Session


class Ms2011ActivityProbe:
    def __init__(self, session: ReadOnlyMs2011Session):
        if not isinstance(session, ReadOnlyMs2011Session):
            raise TypeError("session must be ReadOnlyMs2011Session")
        self._session = session

    def run(self) -> ActivityProbeResult:
        try:
            rows = self._session.execute(QueryId.ACTIVITY_HINT)
            if len(rows) != 1 or not isinstance(rows[0], dict):
                return ActivityProbeResult(ActivityStatus.UNAVAILABLE, "ACTIVITY_HINT_INVALID_SHAPE")
            count = rows[0].get("active_request_count")
            if isinstance(count, bool) or not isinstance(count, int) or count < 0:
                return ActivityProbeResult(ActivityStatus.UNAVAILABLE, "ACTIVITY_HINT_INVALID_VALUE")
            if count:
                return ActivityProbeResult(ActivityStatus.BUSY, "MS2011_ACTIVITY_DETECTED", count)
            return ActivityProbeResult(ActivityStatus.IDLE, "MS2011_APPEARS_IDLE", 0)
        except Exception:
            return ActivityProbeResult(ActivityStatus.UNAVAILABLE, "ACTIVITY_HINT_UNAVAILABLE")
