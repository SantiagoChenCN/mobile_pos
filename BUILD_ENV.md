# Build Environment

Machine-specific paths are intentionally not committed. Keep the local JDK,
Android SDK, Gradle cache, Python environment, sample-data locations, and any
ASCII-only build mirror in the ignored `.codex/local-workspace.md` file.

## Android Prerequisites

- JDK 17.
- Android SDK compatible with the checked-in Android Gradle Plugin settings.
- A project-root `local.properties` containing the local SDK path.

Example `local.properties` entry:

```properties
sdk.dir=<android-sdk-path>
```

Run the repository build entry point from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug-apk.ps1
```

The script may depend on the current developer machine's local tool layout.
Before using it on a fresh clone, compare its assumptions with the ignored local
workspace notes. Do not commit actual machine paths or generated artifacts.

## PC Tool

See [pc-sync-tool/README.md](pc-sync-tool/README.md) for Python prerequisites,
tests, and packaging entry points. Packaging and release builds run only at the
stage gates defined by the implementation plan.
