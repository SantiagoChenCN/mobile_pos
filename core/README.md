# Core Module

Pure Java core logic for the emergency POS app. It avoids Android UI dependencies so it can be tested separately and later called from Kotlin or Java Android screens.

Packages:

- `catalog`: product lookup repository interfaces.
- `checkout`: cart and checkout orchestration.
- `exporter`: sales export port.
- `importer`: product import port.
- `ledger`: sales records and daily summaries.
- `model`: shared domain objects.
- `pricing`: price and discount calculations.

Important rule: UI code must not calculate prices directly. It should call `pricing` and `checkout` services.

