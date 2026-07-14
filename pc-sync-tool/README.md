# MobilePosSync PC Tool

PC-side read-only Mingsheng database sync tool for the offline Android POS.

This package copies a selected `.db` file into the tool-owned AppData backup
directory, publishes a manifest, and serves the latest backup over a token
protected LAN HTTP API.

The HTTP service listens on `0.0.0.0`, while the UI displays a validated private
LAN IPv4 address for the phone to use. The phone and PC must be on the same LAN.
The phone connects with the displayed IP, port, and Token. The PC tool no
longer depends on a QR code for this connection flow.

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

Print the phone connection information:

```powershell
python src\app.py --print-connection-info
```

Enter the displayed IP, port, and Token manually in the phone app.

The dependency list includes `tzdata` because Windows does not always provide
the IANA timezone database required for `America/Argentina/Buenos_Aires`.

Build the packaged desktop tool from the project environment:

```powershell
cd E:\手机收银软件开发\pc-sync-tool
E:\手机收银软件开发\python_envs\pyside6_qrcode\.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --onedir --windowed --name MobilePosSync --paths .\src .\src\app.py
```

The output is `dist\MobilePosSync\`. Distribute the complete folder as a ZIP;
the EXE alone is not a complete package.

## Safety

The backend opens the configured source database read-only and never writes to
the Mingsheng software directory. Tool-owned writes are limited to
`%APPDATA%\MobilePosSync` and `%LOCALAPPDATA%\MobilePosSync`.

The current validated release has 50 passing tests and a successful
`compileall` check. Real phone-to-PC LAN testing is still required on the
target checkout computer and phone.
