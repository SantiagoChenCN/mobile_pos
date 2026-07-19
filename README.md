# Mobile POS

Offline Android emergency POS app for small grocery checkout.

The app is designed for a single phone running independently from the desktop POS system. It can import the exported Ming Sheng / ESpsa product database, search products by barcode or keyword, build a cart, apply line or cart discounts, save detailed sales records, and review/export daily totals.

## Project Layout

- `app/`: native Android app, screens, scanner, database import, local storage.
- `core/`: pure Java business logic for catalog search, pricing, checkout, ledger, and export.
- `scripts/`: Windows scripts for preparing the Android build environment and building a debug APK.
- `docs/`: design document and implementation notes.
- `dist/EmergencyPOS-debug.apk`: latest debug APK build for direct phone installation.

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

## MS2011 Development Status

The MS2011 live product and promotion sync work is currently paused at
`L3/S10`. Offline contracts and the PC-side v2 pipeline are being maintained,
but the live SQL path is not enabled. `MB-07`, `CB-01`, `CB-02`, `CB-03`, and
offline `G4` are recorded as passed; `MF-05` has unverified partial Android
changes and `MF-02` is blocked until stale-warning thresholds are defined.

The production gate remains locked because the tested database identity has
`WRITE_CAPABILITY_PRESENT`. This repository update contains source, tests,
fixtures, plans, and status documents only. It does not include live database
files, production snapshots, probe packages, build output, or Python caches.

## MS2011 Development Status

The MS2011 live product and promotion sync work is currently paused at
`L3/S10`. Offline contracts and the PC-side v2 pipeline are being maintained,
but the live SQL path is not enabled. `MB-07`, `CB-01`, `CB-02`, `CB-03`, and
offline `G4` are recorded as passed; `MF-05` has unverified partial Android
changes and `MF-02` is blocked until stale-warning thresholds are defined.

The production gate remains locked because the tested database identity has
`WRITE_CAPABILITY_PRESENT`. This repository update contains source, tests,
fixtures, plans, and status documents only. It does not include live database
files, production snapshots, probe packages, build output, or Python caches.
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

The current Android acceptance build is `1032530` bytes. The latest release also includes regression coverage for Argentina time handling, cart merging, manual-item separation, and the computer sync client/service.

The current Android acceptance build is `1032530` bytes. The latest release also includes regression coverage for Argentina time handling, cart merging, manual-item separation, and the computer sync client/service.
