# DEVELOPMENT 108 — Continuous Voice + Manual Billing + Item Editor

Version: 5.53.0 (versionCode 553)
Base: DEV107 source archive.

## Changes
- Replaced the invoice item's external Google `RecognizerIntent` popup flow with an in-app compact voice control row backed by Android `SpeechRecognizer`.
- Added runtime microphone permission handling and SpeechRecognizer service visibility query.
- Kept continuous listening active after each recognized result; no-match and speech-timeout errors retry while the user has not stopped Voice.
- Added explicit Voice stop control and recognizer cleanup when the invoice dialog closes.
- Added Edit and Delete actions for every draft invoice line.
- Editing an existing line now updates its original row and preserves its item ID instead of removing it and silently converting it into a new line.
- Manual keyboard entry remains available for item name, quantity, rate, item code, GST and HSN; Add Item saves a new line and Save Changes updates an edited line.
- Item summaries now display name, quantity, rate and calculated line total.
- Added per-item list auto-scroll while the header and editor remain outside the scrolling item-list viewport.
- Preserved DEV107 features and bumped Android version to 5.53.0 / 553.

## Validation status
- Source-level checks: targeted checks confirm the SALE-only persistence hook, prefix filtering, selection fill, 300-entry cap, and separation from stock Item Master.
- Android Kotlin compilation was not run in this environment because this source archive does not contain `gradlew`; GitHub Actions build is required before device installation.
- A full Android Gradle build must run in GitHub Actions; this working environment does not have Gradle installed.
- Device testing is still required for microphone permission, continuous recognition on the target phone, edit/delete, and item-list scrolling.
- Added a mandatory pre-save invoice review dialog showing invoice number, party, each line's name/quantity/rate/amount, subtotal and discount; posting occurs only after explicit final confirmation. Back/Edit returns to the draft without saving.
