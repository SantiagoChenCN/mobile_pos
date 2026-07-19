# Mobile POS

Mobile POS is an offline-first Android emergency checkout application for small
grocery stores. It is designed to keep essential lookup and checkout workflows
available when the desktop POS or local network is unavailable.

## Overview

The Android app imports or receives verified product snapshots, searches by
barcode or text, builds an order with fixed pricing inputs, records payments,
and keeps a local sales ledger. The optional Windows companion exposes
user-approved, read-only snapshots over a token-protected LAN connection.

The phone remains independently usable with its last accepted local data. Live
database access and production synchronization are controlled by explicit
safety gates.

## Architecture

- `app/`: native Android UI, import adapters, local persistence, and sync client.
- `core/`: pure Java domain logic for catalog, pricing, cart, checkout, ledger,
  and export.
- `pc-sync-tool/`: Python/PySide6 Windows companion for read-only extraction,
  validated snapshot publication, and LAN delivery.
- `docs/`: current state, durable plans, historical log, and design references.
- `scripts/`: local build and repository validation entry points.

## Stable Features

- Android 10+ native Java application with Chinese and Spanish UI.
- Offline product import and persistent local catalog.
- Barcode lookup and accent-insensitive multi-keyword search.
- Formal-product cart-line merging with separate manual `almacen` items.
- Exact cart pricing, manual price changes, line/cart discounts, and multiple
  payment methods.
- Transaction detail, daily summary, voiding, and CSV export.
- Argentina business-time handling for user-visible timestamps and day bounds.
- Product editing and import rollback for phone-local products.
- PC companion with explicit source selection, connection diagnostics,
  token-protected HTTP delivery, immutable manifests, and hash validation.
- Versioned snapshot validation and order-boundary activation on Android.

## Safety Model

- Android never connects directly to SQL Server.
- The PC database adapter is limited to approved fixed read-only QueryIds.
- Arbitrary SQL, database writes, DDL, service control, MDF/LDF manipulation,
  and automatic firewall changes are prohibited.
- Candidate snapshots are validated before publication and again before phone
  activation; failure preserves the last accepted snapshot.
- An active cart keeps its original product/pricing snapshot until the order is
  completed or cancelled.
- Only promotion rules backed by verified black-box evidence may calculate
  automatically.
- Production access remains disabled until every applicable safety gate passes.

## Repository Layout

```text
app/                 Android application
core/                Java domain layer
pc-sync-tool/        Windows synchronization companion
docs/plans/          Product and implementation plans
docs/archive/        Preserved historical status snapshots
scripts/             Build and consistency checks
```

## Build

Prerequisites are JDK 17 and an Android SDK compatible with the checked-in
Gradle configuration. Store machine-specific SDK paths in `local.properties`
and local workspace notes outside version control.

Android debug build entry point:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug-apk.ps1
```

PC development and test instructions are in
[pc-sync-tool/README.md](pc-sync-tool/README.md). Machine-independent setup
guidance is in [BUILD_ENV.md](BUILD_ENV.md).

## Documentation

- [Active iteration](docs/ACTIVE_ITERATION.md): only current task, pause point,
  blockers, and next exact action.
- [Implementation status](docs/IMPLEMENTATION_STATUS.md): stable capabilities,
  stage gates, remaining acceptance, and accepted artifact status.
- [Project dashboard](docs/PROJECT_STATUS.md): short Chinese project overview.
- [Product plan](docs/plans/ms2011_live_product_promotion_sync_plan.md): product
  behavior and hard safety baseline.
- [Implementation plan](docs/plans/ms2011_live_product_promotion_sync_implementation_plan.md):
  task contracts, dependencies, ownership, and acceptance gates.
- [Project log](docs/PROJECT_LOG.md): append-only historical record.
- [Plan index](docs/plans/README.md): plan inventory and current stage.

## Current Development

Current implementation and gate status:
[docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).
