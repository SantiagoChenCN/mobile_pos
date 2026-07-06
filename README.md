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
