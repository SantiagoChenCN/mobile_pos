# App Module

Native Android frontend for the emergency POS app.

Current state:

- Programmatic Java UI, no frontend framework dependency.
- Four main screens: checkout, sales detail, daily summary, settings/import.
- Calls the `core` module through service interfaces.
- `.db` import is connected for Mingsheng `CJQ_GOODLIST` product databases.
- Camera scanning and `.xlsx` import are still pending concrete adapters.

Frontend boundaries:

- `MainActivity`: navigation shell, language switch, file picker entry.
- `app.AppServices`: service wiring and current cart state.
- `ui`: language, visual style, shared view helpers.
- `ui.screens`: screen-level UI only; no direct price calculation.

Design direction:

- Open directly into checkout. No landing page.
- Large totals, high contrast, fast buttons, bilingual labels.
- Quiet supermarket-counter palette: paper white, dark ink, teal confirmation, red void states.
