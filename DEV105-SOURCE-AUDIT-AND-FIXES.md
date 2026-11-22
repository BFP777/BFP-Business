# DEV105 Source Audit and Correction Record

Baseline remains **DEV105 / app version 5.50.0 / versionCode 550**. No Room schema change was made.

## Corrections applied

1. Restored the bounded scroll container for the added invoice-item list. The fixed invoice header and item-entry editor remain outside that list scroller.
2. Put the remaining invoice details (discount, GST, notes, supplier attachment and related controls) in their own lower scroll container.
3. Corrected the sticky invoice verifier so the complete source regression run checks the actual intended layout.
4. Corrected `tools/verify_all.py` to invoke the DEV96 verifier from its actual `tools/` directory.
5. Replaced the obsolete DEV68 ZIP-based signed build workflow with a DEV105 repository-source workflow. It runs source checks, unit tests, lint, builds the signed APK, verifies the APK signature and uploads the artifact.
6. Updated the source-import workflow to accept the exact DEV105 ZIP filename as its manual-dispatch input, extract the project root safely, and commit the imported project.

## Verification

- 63 source regression scripts passed, including DEV74, DEV77, DEV83–DEV105.
- Android Gradle build, Android Lint and device installation are not claimed as completed in this environment. They are configured to run in GitHub Actions.
- The signed DEV105 workflow requires the existing GitHub Actions secrets: `BFP_KEYSTORE_BASE64`, `BFP_KEYSTORE_PASSWORD`, `BFP_KEY_ALIAS`, and `BFP_KEY_PASSWORD`.

## Safety

No database schema version, app version, accounting rules, backup behavior or signing key material was changed. No keystore is included in this source archive.
