# Development 117 — Approved Dashboard & Billing UI Refinement

Base: DEV116 source archive (app version 5.60.0 / code 560).

## Implemented
- Updated the global bottom navigation to the approved five destinations: Home, Sale, Purchase, Parties, and Reports.
- Lightened the application canvas while preserving the existing navy BFP brand header and the existing live dashboard accounting cards/actions.
- Added a WhatsApp shortcut beside the selected customer on the billing screen. It opens the selected customer's WhatsApp chat using the saved mobile number; it reports when no mobile number is saved.
- Added an Item List jump control beside Save Item and Save & New. It scrolls to the bill's item list without changing item data.
- Advanced app version to 5.61.0 / versionCode 561.

## Preserved
- Existing invoice repository, ledger posting, stock posting, cash/credit handling, invoice edit/delete, voice entry, final-invoice GST logic, purchase OCR/attachments, Room database entities and schema.
- The approved billing screen's continuous single-scroll layout and selected-customer header.

## Validation
- Static source assertions for DEV117 passed.
- Existing DEV113 dashboard and DEV110 billing verifiers were reviewed; their feature assertions pass, but their historical version assertions intentionally target 5.59.0 and 5.54.0 respectively and are superseded by DEV117's 5.61.0.
- Android Gradle build/device rendering is not certified by this source-only packaging step. Run the repository's Android Build workflow to produce and verify the APK.
