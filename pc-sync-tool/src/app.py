from __future__ import annotations

import argparse
import signal
import sys
import time

from backup_worker import BackupWorker
from connection_info import connection_summary
from config import load_config
from event_log import EventLog
from http_server import HTTP_BIND_HOST, SyncHttpService
from paths import AppPaths


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
