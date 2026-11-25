# DEVELOPMENT 123 — Party Details finishing pass

- Added an editable per-party Credit Limit setting stored in app preferences; no Room schema or existing party records are changed.
- Added transaction-card actions for invoice records: PDF/Print and Share Details.
- Existing statement sharing and customer/supplier action routing are retained.
- Simple Purchase remains a supplier PURCHASE invoice with no invoice lines; it does not call stock movement or item quantity update code. Outstanding supplier balance is reflected through the existing unified invoice ledger.
- Version advanced to 5.57.0 (versionCode 557).

## Known limitations / verification
- Credit Limit is informational only; it is not yet enforced as a credit-sale block.
- Transaction Print uses the existing invoice print flow. Share Details shares invoice summary text, not a PDF attachment.
- Bottom actions are moved out of the scroll area into the activity shell so they stay visible while the transaction list scrolls.
- Android build must be run in the configured GitHub Actions environment; this workspace does not include Gradle wrapper or Android SDK.
