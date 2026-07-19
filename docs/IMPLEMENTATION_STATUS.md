# Implementation Status

Last synchronized: 2026-07-19 (Argentina time)

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
- Current Android build-output debug APK: `1059398` bytes, SHA-256 `15E299DC44F9FA7475E5FFE678533AD1F8255461F728E22176FDB06477178F36`; the `dist` and release-copy APK remain older and are not release-accepted.
- Current PC source regression has 226 passing tests; existing packaged artifacts were not rebuilt by the current contract task.

## Plan Status

| Plan | Status | Evidence / next step |
| --- | --- | --- |
| Product editing, search, UI, import, and text-scale plans | Completed | Implemented and included in prior release history. |
| PC sync HTTP tool plan | Completed | PC tool tests, packaging, and safety boundaries validated. |
| Manual token connection plan | Completed | QR setup removed from PC sync flow; manual IP/port/token flow validated. |
| Computer/phone LAN connection fix plan | Completed | LAN address validation, listener separation, diagnostics, and mobile error presentation validated. |
| Argentina time and cart merge plan | Completed | Android regression tests, PC tests, and Android build passed. Real device acceptance remains. |
| MS2011 live product/promotion sync plan | Evidence gates in progress | EV-01 through EV-04 passed. EV-05 target evidence is complete with `WRITE_CAPABILITY_PRESENT`; per-type black-box semantics remain. |
| MS2011 live product/promotion implementation plan | S10/L3 in progress / MB-07+CB-01+CB-02+CB-03+offline G4 PASS / user paused / no live SQL | Android now has a foreground single-flight v2 sync coordinator, immutable cart pricing snapshots, verified active/pending switching, and durable crash/recovery coverage. Main verification for CB-02 was Gradle 21/21 plus 11/11 direct tests; CB-03 was Gradle 20/20 plus 5/5 tests and 188 assertions. Independent stable-cluster review gave MB-07, CB-01, CB-02, CB-03 and host-side offline G4 PASS with High/Medium 0. MF-05 has unverified partial edits in `MainActivity.java` and a new lifecycle contract test; no MF-05 compile/test has been accepted. MF-02 is blocked before writes because the stale-age and consecutive-failure warning thresholds are not defined; MF-03 waits for MF-02. Current PC regression is 226/226 with compileall pass. No S10 APK has been built, copied or published. Real Android process-kill/SQLite/filesystem/lifecycle/UI and LAN/MS2011 remain unverified; G0B remains locked and the target identity remains `WRITE_CAPABILITY_PRESENT`. |

## Remaining Acceptance

- Test the packaged PC tool on the target checkout computer.
- Complete real phone-to-PC LAN validation: health check, manifest check,
  verified download, import confirmation, and product search afterward.
- Manually verify repeated formal-product scans/searches merge into one line and
  manual `almacen` items remain separate.
- For MS2011 live promotion sync, schema, candidate, and target permission
  evidence are captured. The tested administrator identity is not read-only.
  Continue only with offline contracts and per-type black-box evidence for
  priority, stacking, thresholds, date/time boundaries, and rounding. Any
  future least-privilege identity must be provisioned and reviewed outside the
  tool before a new production authorization attempt.

## Safety Boundary

The project does not modify the Mingsheng program directory, original database,
SQL Server, MDF/LDF files, firewall rules, or external POS records. Published
repositories exclude business databases, product exports, virtual environments,
build caches, and Python bytecode.
