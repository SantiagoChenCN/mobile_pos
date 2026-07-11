# PC Sync Tool Backend Design

## Scope

Build phase A backend modules for `pc-sync-tool`. This work creates the Python backend only: configuration, AppData paths, source database discovery, read-only validation, atomic backup, manifest generation, HTTP download API, event log, startup registry wrapper, and tests.

This work does not build the PySide6 GUI, tray menu, QR display UI, Android sync client, or Android import screen changes.

## Safety Boundary

The tool only reads the selected Mingsheng `.db` source file. It never writes, renames, moves, deletes, repairs, decrypts, or repacks anything in the Mingsheng software directory.

All tool-owned writes go under:

- `%APPDATA%\MobilePosSync\config.json`
- `%LOCALAPPDATA%\MobilePosSync\backups\`
- `%LOCALAPPDATA%\MobilePosSync\logs\`

Tests use temporary directories so they do not touch real AppData or real Mingsheng files.

## Modules

- `paths.py`: Resolves roaming/local AppData paths and creates tool-owned directories.
- `config.py`: Loads/saves JSON config, applies defaults, generates tokens, and validates backup interval/retention settings.
- `source_locator.py`: Resolves either a configured `.db` file or a folder scan. Folder scan prefers `AGT_MAIN.db`, then `AGT_MAIN_*.db`, then other `.db` files, and validates candidates by opening SQLite read-only and checking for `CJQ_GOODLIST`.
- `file_hash.py`: Streams SHA-256 for large `.db` files.
- `manifest.py`: Builds success and no-backup manifest dictionaries and writes JSON atomically.
- `event_log.py`: Appends event entries and keeps the most recent 200.
- `backup_worker.py`: Performs stability check, copies source to `latest.tmp`, hashes it, atomically replaces `latest.db` and `manifest.json`, writes timestamped history, and prunes history to the retention count.
- `http_server.py`: Runs a threaded local HTTP server exposing `/health`, `/manifest.json`, and `/latest.db`, all protected by `token` query param.
- `startup.py`: Encapsulates HKCU Run registry enable/disable/status operations for later GUI use.
- `app.py`: Minimal command-line entry point for backend smoke use.

## Data Flow

1. Load config and resolve the source database.
2. Read source file size and mtime, wait for the stability interval, then read them again.
3. If size or mtime changed, skip this backup without replacing existing output.
4. Copy source into the tool backup directory as `latest.tmp`.
5. Hash `latest.tmp`.
6. Atomically replace `latest.db`.
7. Write `manifest.tmp`, then atomically replace `manifest.json`.
8. Copy the same stable backup into `history/`.
9. Prune history to the latest five backups by modification time.
10. Write an event log entry.

If any copy/hash/manifest step fails, the existing `latest.db` and `manifest.json` remain available.

## HTTP API

All endpoints require `?token=<token>`. Invalid token returns `403 Forbidden`.

- `GET /health`: returns app name, version, configured host, and port.
- `GET /manifest.json`: returns current manifest or `{ "ok": false, "error": "NO_BACKUP_READY" }`.
- `GET /latest.db`: returns the binary backup with `Content-Type: application/octet-stream`, `Content-Length`, and `X-File-Sha256`.

Missing backup for `/latest.db` returns `404` with the no-backup JSON body.

## Tests

Use `unittest` and temporary directories. Coverage includes:

- Valid `.db` files are detected by `CJQ_GOODLIST`.
- Invalid `.db` files are rejected.
- Folder scan chooses the preferred valid candidate.
- SHA-256 and manifest output are correct.
- Backup writes `latest.db`, `manifest.json`, and history, then prunes history.
- A failed backup does not replace existing latest/manifest.
- HTTP token enforcement, manifest response, and latest download headers.
