# BFP Business Pro 5.0

Android business accounting source for BHAJAN FASHION POINT.

## Build and verification

The repository CI workflow (`.github/workflows/android-build.yml`) runs the offline source regression suite, Android unit tests, Android Lint, and a debug APK build. It uploads the APK and generated Room schema snapshots as workflow artifacts.

For local source-level checks with Python 3:

```bash
python3 tools/verify_all.py
```

The Android build requires JDK 17, Gradle 8.9, and Android SDK Platform 35. Open the project in Android Studio or run the Gradle tasks from a configured Android build environment.

## Database

Room schema snapshots are configured to export to `app/schemas`. Database migrations are explicit; destructive migration fallback is disabled.

## Important

A successful source-level regression suite is not the same as a successful Android build or device test. Confirm the GitHub Actions run and install the generated APK on a test device before production use. GST invoice compliance should be reviewed by a qualified tax professional.


## Development 45 (version 5.1.0)

- Optional supplier bill image/PDF attachments on purchase entries.
- Attachments are stored privately in the app and use portable relative paths.
- Local ZIP backup contains the SQLite database and purchase attachments.
- Restore accepts new ZIP backups and legacy SQLite `.db` backups.
- See `DEVELOPMENT-45-NOTES.md` for implementation and validation details.


## Development 46 (version 5.2.0)

- Employee master and monthly salary ledger.
- Partial salary payments through Cash, UPI, or named Bank accounts.
- Salary outflows affect balances, Day Book, and expense reports.
- See `DEVELOPMENT-46-NOTES.md` for details.


## Latest source update — Development 53 (5.8.0)
- Customer Intelligence screen: customer sales summary, transparent VIP marker, recent bill history, preferred designs and optional birthday list.
- Optional birthday field is stored as `YYYY-MM-DD` and added using a non-destructive Room migration from schema 8 to 9.
- Customer intelligence is read-only; no automatic WhatsApp messages or accounting postings.
- Run `python3 tools/verify_all.py` for the complete offline/source-level regression suite.


## Latest development

- App version: 5.11.0 (versionCode 511)
- Development 55: full customer detail screen with separate SALE and RECEIPT tabs, customer-specific totals, and preselected party in new sale/receipt forms.
- No database migration in this release.
- Run offline checks with `python3 tools/verify_all.py`.


## Development 113 (5.58.0)

- Main dashboard refreshed to the approved BFP card-based visual direction, with navy brand header, live sales/purchase/cash/outstanding figures, receivables/payables and quick actions.
- No database schema migration. Billing workflows and accounting repositories retained.
- See `DEVELOPMENT-113-DASHBOARD-UI-REDESIGN.md`.
