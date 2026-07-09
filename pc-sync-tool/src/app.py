from __future__ import annotations

import argparse
import signal
import sys
import time

from backup_worker import BackupWorker
from config import load_config
from event_log import EventLog
from http_server import SyncHttpService
from paths import AppPaths
from qr_code import setup_url


def main(argv=None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    if not argv:
        from ui.main_window import launch_gui

        return launch_gui()

    parser = argparse.ArgumentParser(description="MobilePosSync PC backend")
    parser.add_argument("--gui", action="store_true", help="Run the PySide6 desktop tool")
    parser.add_argument("--backup-once", action="store_true", help="Run one backup and exit")
    parser.add_argument("--serve", action="store_true", help="Run the HTTP service until interrupted")
    parser.add_argument("--print-setup-url", action="store_true", help="Print the mobile setup URL")
    args = parser.parse_args(argv)

    if args.gui:
        from ui.main_window import launch_gui

        return launch_gui()

    paths = AppPaths.from_environment()
    config = load_config(paths)
    event_log = EventLog(paths.event_log_file)

    if args.print_setup_url:
        print(setup_url(config))
        return 0

    if args.backup_once:
        result = BackupWorker(paths, event_log).run_once(config)
        print(result.message)
        return 0 if result.ok else 1

    if args.serve:
        service = SyncHttpService(paths, config, bind_host=config.selected_host, event_log=event_log)
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
