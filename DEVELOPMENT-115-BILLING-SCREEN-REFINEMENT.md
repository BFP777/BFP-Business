# DEV115 — Billing Screen Refinement

Base: DEV114 source.

## Changes
- Added a clear `New Bill (Sale)` / `New Purchase` title row with the current date to the invoice editor.
- Improved the added-item card contrast and text color for easier reading.
- Preserved the existing single continuous scroll area, separate Qty / Item Name / Rate editor fields, Save Item, Save & New, voice entry, item edit/delete, optional invoice-level GST, discount, credit-default settlement, invoice preview/save, stock, and ledger flows.
- Incremented Android version to 5.60.0 (560).

## Validation
- Source patch assertions passed.
- ZIP integrity checked after packaging.
- Android Gradle build and real-device visual/payment/voice testing remain pending; do not treat this source ZIP as a verified APK.
