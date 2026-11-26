# BFP Business Pro — DEV112 Final Source Audit

**App version:** 5.57.0 (versionCode 557)  
**Base:** DEV111 approved single-screen billing source  
**Database:** Room schema remains version 10; no migration added.

## Finalized behavior

- Single continuous billing screen with the customer selector/header pinned above the scrolling bill body.
- Manual item editor has separate quantity, item name, and rate fields, plus `Save Item` and `Save & New`.
- Added item rows show quantity, rate, and line amount separately; the list auto-scrolls to the newest row and its item counter updates.
- Voice entry parses item name, quantity, and rate separately. A complete spoken item is added to the current unsaved bill draft; the user still presses `Save Bill` to post the invoice.
- Sales GST is optional through the checkbox; new sale bills default to enabled at 5%. Unchecked means 0% GST. Purchase GST remains item-wise.
- Whole-bill Edit/Delete remains available. Delete now removes only the selected invoice's linked payment rows, reverses the invoice's stock movement, and removes its lines and invoice inside one Room transaction. Unlinked party payments are preserved. The UI confirmation describes the cash/bank, ledger, and stock effects.
- The source-import workflow no longer defaults to DEV105 and rejects DEV105 import filenames, preventing accidental downgrade.
- A dedicated GitHub Actions workflow runs source regression checks, Android unit tests, lint, and creates a debug APK artifact.

## Checks performed here

- DEV112 whole-invoice delete regression checks: PASS.
- DEV111/DEV110 billing and voice source checks updated to the final layout: PASS.
- Optional sales GST and item-wise purchase GST source checks: PASS.
- Room DAO SQL checks (62 queries): PASS.
- Package hygiene, invoice-number SQL, posting invariants, and build-readiness checks: PASS.
- A large historical all-in-one regression run did not complete within the available execution window; later individual regression scripts were run separately and passed. Do not interpret this report as a complete successful execution of every historical check.

## Build and device-test status

No Gradle executable, Gradle wrapper, or configured Android SDK is included in this execution environment. Therefore an Android APK was **not** compiled here, and real-device testing of microphone recognition, billing, payment, GST, and deletion remains required. The included GitHub Actions workflow is configured to perform unit tests, lint, and a debug APK build after the source is pushed to GitHub.
