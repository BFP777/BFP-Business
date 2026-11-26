# DEV112 — Final Source Hardening

Base: DEV111 source (app version 5.56.0 / 556).

## Changes
- Advanced app version to 5.57.0 / 557 without a Room schema change.
- Whole-invoice deletion now removes only payments linked to the selected invoice, reverses inventory, and deletes invoice lines and invoice atomically in one Room transaction.
- Unlinked party receipts/payments remain untouched.
- Delete confirmation explains that linked cash/bank and party-ledger postings are removed and stock is adjusted.
- Updated historical source-regression version assertions to the current app version so old milestone tests do not falsely fail solely because the app has advanced.
- Hardened the manual source-import workflow: it has no default source ZIP and blocks DEV105 imports to prevent accidental downgrade.

## Validation
- Run `python3 tools/verify_all.py` for offline source checks.
- Android Gradle compilation, signed APK generation, and real-device testing still require GitHub Actions with the required Android SDK and signing secrets. This source ZIP is not an APK and is not represented as a device-tested release.
