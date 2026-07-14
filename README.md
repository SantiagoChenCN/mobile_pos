# Mobile POS

Offline Android emergency POS app for small grocery checkout, with an optional read-only LAN sync tool for the desktop Mingsheng database.

The phone app is designed for one phone running independently from the desktop POS system. It imports an ESpsa `AGT_MAIN` database into local storage, searches by barcode or keyword, builds a cart, applies line or cart discounts, saves detailed sales records, and shows daily totals. The optional PC tool copies the source database read-only into its own backup area and serves a verified copy over a token-protected LAN HTTP API.

## Project Layout

- `app/`: native Android app, screens, scanner, database import, local storage.
- `core/`: pure Java business logic for catalog search, pricing, checkout, ledger, and export.
- `scripts/`: Windows scripts for preparing the Android build environment and building a debug APK.
- `docs/`: design document and implementation notes.
- `dist/EmergencyPOS-debug.apk`: latest debug APK build for direct phone installation.
- `pc-sync-tool/`: Windows PySide6 desktop sync tool and its tests.
- `docs/plans/`: detailed implementation and modification plans.

## Current Features

- Android 10+ target.
- Chinese / Spanish language switch.
- Offline product import from the ESpsa `AGT_MAIN` SQLite database.
- Product lookup by barcode or keyword.
- Keyword search returns all matching products, with accent-insensitive and connector-word tolerant matching.
- Cart checkout with cash, card, QR, and transferencia payment methods.
- Manual almacen item entry for emergency/weighed products.
- Line-level and cart-level percent or fixed discounts, with undo/clear controls.
- Daily summary and transaction detail views.
- CSV export for later reconciliation.
- Argentina business timezone (`America/Argentina/Buenos_Aires`) for user-visible timestamps and daily boundaries.
- Formal products with the same stable product ID merge into one cart line; manual `almacen` items remain separate.
- Computer sync with LAN health checks, manifest/hash validation, confirmed import, and structured connection errors.
- Manual phone connection using the PC tool's displayed private IPv4 address, port, and token; QR setup is no longer required for PC sync.

## PC Sync Tool

The PC tool listens on all local interfaces while showing a validated private LAN IPv4 address for the phone. It does not modify the Mingsheng directory or the source database. It writes only to its own application data directories, keeps recent backups, and serves `/health`, `/manifest.json`, and `/latest.db` after token validation.

The phone and PC must be on the same LAN. If the phone cannot connect after the PC health check succeeds, check Windows Firewall, Wi-Fi isolation, and the router's client isolation settings.

For the packaged Windows build, extract the complete `MobilePosSync` folder from the ZIP and run `MobilePosSync.exe`; do not copy only the EXE.
- Argentina business timezone (`America/Argentina/Buenos_Aires`) for user-visible timestamps and daily boundaries.
- Formal products with the same stable product ID merge into one cart line; manual `almacen` items remain separate.
- Computer sync with LAN health checks, manifest/hash validation, confirmed import, and structured connection errors.
- Manual phone connection using the PC tool's displayed private IPv4 address, port, and token; QR setup is no longer required for PC sync.

## Build

This project uses a portable Windows build environment under `E:\AndroidBuildEnv`.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug-apk.ps1
```

The generated APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

If building on another machine, install Android SDK and JDK 17, then create a local `local.properties` file with:

```properties
sdk.dir=C\:\\path\\to\\android-sdk
```

`local.properties` is intentionally not committed because it is machine-specific.

The current release has passed the Android smoke/build checks and 50 PC tests. Real phone-to-PC LAN end-to-end validation remains a device-level acceptance step.

The current Android acceptance build is `1032530` bytes. The latest release also includes regression coverage for Argentina time handling, cart merging, manual-item separation, and the computer sync client/service.
