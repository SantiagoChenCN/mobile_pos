# MobilePosSync PC Tool

PC-side read-only Mingsheng database sync tool.

This package copies a selected `.db` file into the tool-owned AppData backup
directory, publishes a manifest, and serves the latest backup over a token
protected LAN HTTP API.

## Development

Install runtime dependencies:

```powershell
cd E:\手机收银软件开发\pc-sync-tool
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
```

Run the desktop tool:

```powershell
.\.venv\Scripts\python src\app.py
```

Run tests:

```powershell
cd E:\手机收银软件开发\pc-sync-tool
python -m unittest discover -s tests
```

Run one backup using the saved config:

```powershell
python src\app.py --backup-once
```

Run the backend HTTP service:

```powershell
python src\app.py --serve
```

Print the phone setup URL:

```powershell
python src\app.py --print-setup-url
```

## Safety

The backend opens the configured source database read-only and never writes to
the Mingsheng software directory. Tool-owned writes are limited to
`%APPDATA%\MobilePosSync` and `%LOCALAPPDATA%\MobilePosSync`.
