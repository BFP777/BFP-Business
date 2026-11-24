# DEV117 — Approved UI and signed APK workflow

## Included
- Retains the DEV116 Android application and existing database schema.
- Carries the DEV117 billing-screen additions already present in the source: customer WhatsApp action and Item List navigation, while preserving the existing quantity, item name, rate, multi-item, discount, optional final-stage GST and Save Bill logic.
- Uses the five-tab bottom navigation: Home, Sale, Purchase, Parties and Reports.
- Retains the dashboard KPI cards, green Debtors card, red Creditors card, six quick actions and shortcuts.
- Advances Android version to versionCode 561 / versionName 5.61.0.
- Updates the signed release workflow and artifact name to DEV117; signing secrets remain unchanged.

## Validation
- Offline source regression suite: run with `python3 tools/verify_all.py`.
- Signed Android release build: run by GitHub Actions after DEV117 source is committed to `main`.
- This package has not been built into an APK in this environment; GitHub Actions must confirm signing and produce the downloadable artifact.
