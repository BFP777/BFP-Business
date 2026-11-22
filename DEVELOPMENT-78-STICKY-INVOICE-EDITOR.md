# Development 78 — Fixed invoice editor and scrolling item list

- Reworked the invoice dialog layout so the bill header, customer/party fields, item-entry fields, barcode actions, add-item action, and right-aligned item counter remain in the fixed upper area.
- The appended invoice-item summaries now live in their own bounded scroll area. Adding more items scrolls that list rather than moving the entry fields away.
- Remaining invoice details (discount, note, and purchase attachment actions) remain in a separate lower scroll area.
- Dialog height is set to 92% of the available display height to make the fixed entry area practical on phone screens.
- App version advanced to 5.23.0 / versionCode 523; no Room schema change.
- Full offline/source verification suite passes.
- Android Gradle build and on-device layout test have not been run in this environment (project ZIP has no Gradle wrapper and Gradle is not installed).
