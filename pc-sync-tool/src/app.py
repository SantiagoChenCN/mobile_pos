from __future__ import annotations

import argparse
import signal
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable

from backup_worker import BackupWorker
from connection_info import connection_summary
from config import load_config
from event_log import EventLog
from http_server import HTTP_BIND_HOST, SyncHttpService
from paths import AppPaths
from ms2011_query_catalog import QueryId
from ms2011_reader import DeterministicMs2011Reader
from promotion_candidate_extractor import extract_promotion_candidates
from snapshot_normalizer import normalize_products
from sqlite_v2_writer import SQLiteV2Writer, SnapshotWriteInput
from v2_contract import snapshot_id
from v2_publisher import V2PublishResult, V2Publisher


class V2PipelineError(RuntimeError):
    def __init__(self, reason_code: str):
        super().__init__(reason_code)
        self.reason_code = reason_code


@dataclass(frozen=True)
class V2PipelineResult:
    publish: V2PublishResult
    source_hash: str
    product_count: int
    promotion_candidate_count: int
    validation_issue_count: int


class V2SyncPipeline:
    def __init__(
        self,
        paths: AppPaths,
        reader: DeterministicMs2011Reader,
        now_utc: Callable[[], datetime] | None = None,
        writer: SQLiteV2Writer | None = None,
        publisher_factory: Callable[[AppPaths, Callable[[], datetime]], V2Publisher] | None = None,
    ):
        if not isinstance(reader, DeterministicMs2011Reader):
            raise TypeError("reader must be DeterministicMs2011Reader")
        self.paths = paths
        self.reader = reader
        self.now_utc = now_utc or (lambda: datetime.now(timezone.utc))
        self.writer = writer or SQLiteV2Writer(paths)
        self.publisher_factory = publisher_factory or (
            lambda app_paths, clock: V2Publisher(app_paths, now_utc=clock)
        )

    def run_once(self, as_of_local: datetime) -> V2PipelineResult:
        batch = self.reader.read_double()
        normalized = normalize_products(
            batch.rows(QueryId.PRODUCTS),
            batch.rows(QueryId.CATEGORIES),
            batch.rows(QueryId.UNITS),
        )
        if normalized.rejected:
            raise V2PipelineError("PRODUCT_NORMALIZATION_REJECTED")
        promotion = extract_promotion_candidates(
            batch.rows(QueryId.PRODUCTS),
            {query_id: batch.rows(query_id) for query_id in batch.tables},
            as_of_local,
        )
        if promotion.normalized_rules:
            raise V2PipelineError("UNVERIFIED_RULE_OUTPUT_FORBIDDEN")
        created_at = self.now_utc()
        if created_at.tzinfo is None or created_at.utcoffset() != timezone.utc.utcoffset(None):
            raise V2PipelineError("PIPELINE_CLOCK_NOT_UTC")
        identifier = snapshot_id(created_at, batch.source_hash)
        write_input = SnapshotWriteInput(
            identifier,
            batch.source_hash,
            normalized.products,
            batch.rows(QueryId.CATEGORIES),
            batch.rows(QueryId.UNITS),
            promotion,
            normalized.issues,
        )
        written = self.writer.write(write_input)
        published = self.publisher_factory(self.paths, lambda: created_at).publish(written)
        manifest = published.manifest
        if manifest["verifiedPromotionCount"] != 0:
            raise V2PipelineError("UNVERIFIED_RULE_PUBLISHED")
        return V2PipelineResult(
            published,
            batch.source_hash,
            int(manifest["productCount"]),
            int(manifest["promotionCandidateCount"]),
            int(manifest["validationIssueCount"]),
        )


def main(argv=None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    if not argv:
        from ui.main_window import launch_gui

        return launch_gui()

    parser = argparse.ArgumentParser(description="MobilePosSync PC backend")
    parser.add_argument("--gui", action="store_true", help="Run the PySide6 desktop tool")
    parser.add_argument("--backup-once", action="store_true", help="Run one backup and exit")
    parser.add_argument("--serve", action="store_true", help="Run the HTTP service until interrupted")
    parser.add_argument("--print-connection-info", action="store_true", help="Print IP, port, and token for manual mobile setup")
    args = parser.parse_args(argv)

    if args.gui:
        from ui.main_window import launch_gui

        return launch_gui()

    paths = AppPaths.from_environment()
    config = load_config(paths)
    event_log = EventLog(paths.event_log_file)

    if args.print_connection_info:
        print(connection_summary(config))
        return 0

    if args.backup_once:
        result = BackupWorker(paths, event_log).run_once(config)
        print(result.message)
        return 0 if result.ok else 1

    if args.serve:
        service = SyncHttpService(paths, config, bind_host=HTTP_BIND_HOST, event_log=event_log)
        service.start()
        stop = False

        def handle_stop(signum, frame):  # noqa: ARG001
            nonlocal stop
            stop = True

        signal.signal(signal.SIGINT, handle_stop)
        signal.signal(signal.SIGTERM, handle_stop)
        print(f"Serving MobilePosSync on port {service.actual_port}")
        try:
            while not stop:
                time.sleep(0.25)
        finally:
            service.stop()
        return 0

    parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
