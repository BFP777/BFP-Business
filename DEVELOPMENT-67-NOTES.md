# Development 67 — Sale Customer Picker

## Implemented
- Replaced the Sale invoice party spinner with an in-flow customer picker.
- Added customer search by name or mobile number.
- Customer rows show the saved mobile number and calculated outstanding amount.
- Outstanding is calculated from unpaid Sale invoice balances less unallocated incoming advances, matching the existing party-balance accounting rule.
- Added `+ Add New Party` inside the Sale picker. The inline form validates the name, optional GSTIN, and optional birthday; the saved customer is immediately selected in the open Sale invoice.
- Sale invoices can now be started when no customer exists, but saving is blocked until a customer is selected or added.
- Purchase continues to use its existing supplier spinner and supplier-bill OCR GSTIN matching flow. Purchase attachment/OCR behavior was not intentionally changed.
- Display Language remains English-only; the Hindi/English voice input selector is unchanged.

## Verification
- `tools/verify_dev67_sale_customer_picker.py`: PASS.
- The full legacy `tools/verify_all.py` suite does not complete cleanly because an existing unrelated Dev52 supplier-insights source check fails (`rate comparison orders latest purchase first and compares prior record`). The Dev67-specific checks pass, as do earlier checks through Dev51 and the Dev50 OCR checks after updating their selector reference for the nullable Purchase-only spinner.
- No Gradle wrapper or installed Gradle executable is present in this source package/runtime, so an Android Kotlin/Gradle build and APK generation could not be verified here.

## Database and compatibility
- No Room entity, DAO schema, or database migration changes were made.
- No Purchase flow changes were intended beyond safely referencing its nullable Purchase-only spinner.
