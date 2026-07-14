# Implementation Status

Last synchronized: 2026-07-14 (Argentina time)

## Product Status

The emergency POS Android app and the PC read-only sync tool have completed the
current code-level implementation and packaging cycle. The Android app is
offline-first and remains independent from the desktop POS database except for
an explicit, user-confirmed import of a verified database snapshot.

Completed and validated:

- Android 10+ native app with Chinese/Spanish switching.
- Product import, barcode lookup, accent-insensitive keyword search, cart,
  discounts, payments, transaction details, daily summary, and CSV export.
- PC read-only database backup, manifest/hash publication, token-protected LAN
  HTTP API, manual phone connection information, connection diagnostics, and
  Windows PySide6 UI.
- Argentina business timezone for user-visible timestamps and daily boundaries.
- Stable-ID merging for formal products; manual `almacen` products remain
  separate, including same-ID collision protection.
- Current Android debug APK: `1032530` bytes.
- Current PC package includes `tzdata` and has 50 passing tests.

## Plan Status

| Plan | Status | Evidence / next step |
| --- | --- | --- |
| Product editing, search, UI, import, and text-scale plans | Completed | Implemented and included in prior release history. |
| PC sync HTTP tool plan | Completed | PC tool tests, packaging, and safety boundaries validated. |
| Manual token connection plan | Completed | QR setup removed from PC sync flow; manual IP/port/token flow validated. |
| Computer/phone LAN connection fix plan | Completed | LAN address validation, listener separation, diagnostics, and mobile error presentation validated. |
| Argentina time and cart merge plan | Completed | Android regression tests, PC tests, and Android build passed. Real device acceptance remains. |
| MS2011 live product/promotion sync plan | Planning / evidence collection | No live SQL or promotion implementation has been claimed. Requires read-only schema confirmation and black-box promotion evidence. |
| MS2011 live product/promotion implementation plan | Not started | Task breakdown and module boundaries are ready; implementation must wait for the evidence gates in the plan. |

## Remaining Acceptance

- Test the packaged PC tool on the target checkout computer.
- Complete real phone-to-PC LAN validation: health check, manifest check,
  verified download, import confirmation, and product search afterward.
- Manually verify repeated formal-product scans/searches merge into one line and
  manual `almacen` items remain separate.
- For MS2011 live promotion sync, do not implement or enable unverified
  promotion formulas. First obtain read-only schema and black-box evidence for
  priority, stacking, thresholds, date/time boundaries, and rounding.

## Safety Boundary

The project does not modify the Mingsheng program directory, original database,
SQL Server, MDF/LDF files, firewall rules, or external POS records. Published
repositories exclude business databases, product exports, virtual environments,
build caches, and Python bytecode.
